/*
 * Copyright 2025 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import {Config, HttpAction, HttpMetadata, OperationMode, RequestProcess} from './client';
import {UmbrellaClient, UmbrellaClientConfig} from './umbrellaClient';

export const DEFAULT_ALLOW_ACTION: HttpAction = {requestProcess: RequestProcess.Allow};

const PING_INTERVAL_MS = 10 * 60 * 1000;
/**
 * Applied when the server has not supplied a timeout, so that a degraded API cannot hold a request open
 * indefinitely.
 */
const DEFAULT_TIMEOUT_MS = 2_000;
/**
 * Bounds the MONITOR mode backlog; beyond this, events are dropped rather than accumulating without limit.
 */
const MONITOR_MAX_IN_FLIGHT = 1_000;

export interface UmbrellaServiceOptions extends UmbrellaClientConfig {
    org: string;
    apiKey: string;
    /**
     * Identifies this process to the server; defaults to hostname and a random session id.
     */
    nodeId?: string;
    /**
     * Receives errors that are otherwise swallowed to keep the integration fail-open.
     */
    onError?: (message: string, error: unknown) => void;
}

/**
 * Mirrors the Java client: server-driven operation modes, a background ping, and fail-open behaviour on every error
 * path so that Umbrella can never take the host application down.
 */
export class UmbrellaService {

    private readonly client: UmbrellaClient;
    private readonly org: string;
    private readonly nodeId: string;
    private readonly onError: (message: string, error: unknown) => void;

    private config: Config = {mode: OperationMode.Disabled};
    private pingTimer?: ReturnType<typeof setInterval>;
    private inFlightMonitorEvents = 0;
    private droppedMonitorEvents = 0;

    constructor(options: UmbrellaServiceOptions) {
        this.org = options.org;
        this.nodeId = options.nodeId ?? defaultNodeId();
        this.onError = options.onError ?? ((message, error) => console.warn(`[umbrella] ${message}`, error));
        this.client = UmbrellaClient.get({
            apiKey: options.apiKey,
            basePath: options.basePath,
            fetchApi: options.fetchApi,
        });
    }

    /**
     * Performs the initial ping and starts the background refresh. Never rejects: a failure here, including a rejected
     * API key, leaves the service disabled until the next ping rather than failing application startup.
     */
    async start(): Promise<void> {
        await this.ping();
        this.pingTimer = setInterval(() => {
            void this.ping();
        }, PING_INTERVAL_MS);
        // Do not hold the event loop open on account of Umbrella
        this.pingTimer.unref?.();
    }

    stop(): void {
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
            this.pingTimer = undefined;
        }
    }

    getMode(): OperationMode {
        return this.config.mode ?? OperationMode.Disabled;
    }

    additionalHeadersToCollect(): string[] {
        return this.config.collectAdditionalHeaders ?? [];
    }

    getDroppedMonitorEventCount(): number {
        return this.droppedMonitorEvents;
    }

    async httpEvent(httpMetadata: HttpMetadata): Promise<HttpAction> {
        const mode = this.getMode();
        switch (mode) {
            case OperationMode.Blocking:
                try {
                    const response = await this.sendHttpEvent(httpMetadata, mode);
                    return response.action ?? DEFAULT_ALLOW_ACTION;
                } catch (error) {
                    this.onError('Failed to validate http event, allowing request', error);
                    return DEFAULT_ALLOW_ACTION;
                }
            case OperationMode.Monitor:
                if (this.inFlightMonitorEvents >= MONITOR_MAX_IN_FLIGHT) {
                    this.droppedMonitorEvents++;
                    return DEFAULT_ALLOW_ACTION;
                }
                this.inFlightMonitorEvents++;
                void this.sendHttpEvent(httpMetadata, mode)
                    .catch(error => this.onError('Failed to publish http event', error))
                    .finally(() => {
                        this.inFlightMonitorEvents--;
                    });
                return DEFAULT_ALLOW_ACTION;
            default:
                return DEFAULT_ALLOW_ACTION;
        }
    }

    private async sendHttpEvent(httpMetadata: HttpMetadata, currentMode: OperationMode) {
        try {
            const response = await this.client.ingest().httpEvent(
                {
                    org: this.org,
                    httpEventRequest: {httpMetadata, nodeId: this.nodeId, currentMode},
                },
                {signal: this.timeoutSignal()});
            if (response.configRefresh) {
                this.config = response.configRefresh;
            }
            return response;
        } catch (error) {
            if (isStatus(error, 429)) {
                this.onError('Rate limited by Umbrella, disabling until next ping', error);
                this.config = {...this.config, mode: OperationMode.Disabled};
            }
            throw error;
        }
    }

    private async ping(): Promise<void> {
        try {
            const response = await this.client.health().nodePing(
                {org: this.org, pingRequest: {nodeId: this.nodeId}},
                {signal: this.timeoutSignal()});
            if (response.config) {
                this.config = response.config;
            }
        } catch (error) {
            // Including a rejected key: retry at the next interval rather than disabling until restart
            this.onError('Failed to ping Umbrella, retrying at the next interval', error);
        }
    }

    private timeoutSignal(): AbortSignal {
        const timeoutMs = this.config.timeoutMs && this.getMode() === OperationMode.Blocking
            ? this.config.timeoutMs
            : DEFAULT_TIMEOUT_MS;
        return AbortSignal.timeout(timeoutMs);
    }
}

function isStatus(error: unknown, status: number): boolean {
    return typeof error === 'object' && error !== null && (error as { response?: Response }).response?.status === status;
}

function defaultNodeId(): string {
    let hostname = 'unknown';
    try {
        // Avoids a hard dependency on node:os for non-node runtimes
        hostname = require('node:os').hostname();
    } catch {
        // Keep the default
    }
    return `${hostname}; sid=${Math.random().toString(36).slice(2)}${Date.now().toString(36)}`;
}
