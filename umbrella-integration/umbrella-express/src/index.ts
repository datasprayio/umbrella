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

import {HttpMetadata, RequestProcess, UmbrellaService, UmbrellaServiceOptions} from 'umbrella-client';

/**
 * The subset of Express' request and response this middleware relies on, declared structurally so that the package
 * does not need express as a dependency.
 */
export interface UmbrellaRequest {
    method: string;
    originalUrl?: string;
    url?: string;
    protocol?: string;
    headers: Record<string, string | string[] | undefined>;
    socket?: { remoteAddress?: string; remotePort?: number };
    [key: string]: unknown;
}

export interface UmbrellaResponse {
    setHeader(name: string, value: string): unknown;
    status(code: number): UmbrellaResponse;
    end(body?: unknown): unknown;
}

export type NextFunction = (error?: unknown) => void;

export const DEFAULT_BLOCK_STATUS = 403;

/**
 * Request property holding the milliseconds spent inside Umbrella, matching the servlet integrations.
 */
export const SPENT_TIME_PROPERTY = 'umbrellaSpentTimeMs';

export interface UmbrellaMiddlewareOptions extends UmbrellaServiceOptions {
    /**
     * Skip requests whose path matches, typically static assets. Checked before the inclusion pattern.
     */
    excludePath?: RegExp;
    /**
     * When set, only requests whose path matches are checked.
     */
    includePath?: RegExp;
}

export interface UmbrellaMiddleware {
    (request: UmbrellaRequest, response: UmbrellaResponse, next: NextFunction): void;

    /**
     * Performs the initial ping and starts the background config refresh. Safe to await during application startup;
     * it never rejects.
     */
    start(): Promise<void>;

    /**
     * Stops the background refresh, for a clean shutdown or between tests.
     */
    stop(): void;

    readonly service: UmbrellaService;
}

export function umbrella(options: UmbrellaMiddlewareOptions): UmbrellaMiddleware {
    const service = new UmbrellaService(options);
    const {excludePath, includePath} = options;

    const middleware = ((request: UmbrellaRequest, response: UmbrellaResponse, next: NextFunction) => {
        const path = request.originalUrl ?? request.url ?? '';

        if ((excludePath && excludePath.test(path)) || (includePath && !includePath.test(path))) {
            next();
            return;
        }

        const startedMs = Date.now();
        service.httpEvent(collect(request, path, service.additionalHeadersToCollect()))
            .then(action => {
                request[SPENT_TIME_PROPERTY] = Date.now() - startedMs;

                if (action.requestMetadata) {
                    Object.entries(action.requestMetadata).forEach(([key, value]) => {
                        request[key] = value;
                    });
                }
                if (action.responseHeaders) {
                    Object.entries(action.responseHeaders).forEach(([name, value]) => {
                        response.setHeader(name, value);
                    });
                }
                if (action.responseCookies?.length) {
                    response.setHeader('Set-Cookie', action.responseCookies.map(serializeCookie) as unknown as string);
                }

                // Only an explicit block stops the request; anything else continues, so that an unrecognised
                // response cannot take the application offline
                if (action.requestProcess !== RequestProcess.Block) {
                    if (action.responseStatus) {
                        response.status(action.responseStatus);
                    }
                    next();
                    return;
                }

                response.status(action.responseStatus ?? DEFAULT_BLOCK_STATUS).end();
            })
            .catch(next);
    }) as UmbrellaMiddleware;

    middleware.start = () => service.start();
    middleware.stop = () => service.stop();
    Object.defineProperty(middleware, 'service', {value: service});

    return middleware;
}

function header(request: UmbrellaRequest, name: string): string | undefined {
    const value = request.headers[name.toLowerCase()];
    return Array.isArray(value) ? value.join(', ') : value;
}

function collect(request: UmbrellaRequest, path: string, additionalHeadersToCollect: string[]): HttpMetadata {
    const contentLength = header(request, 'Content-Length');
    const authorization = header(request, 'Authorization');
    const cookieHeader = header(request, 'Cookie');

    const data: HttpMetadata = {
        ts: new Date(),
        uri: path,
        method: request.method,
        proto: request.protocol,
        ip: request.socket?.remoteAddress,
        port: request.socket?.remotePort,
        contentLength: contentLength === undefined ? undefined : Number(contentLength),
        hXFwdProto: header(request, 'X-Forwarded-Proto'),
        hCfConnIp: header(request, 'CF-Connecting-IP'),
        hTrueClientIp: header(request, 'True-Client-IP'),
        hXRealIp: header(request, 'X-Real-IP'),
        hFwd: header(request, 'Forwarded'),
        hXFwdFor: header(request, 'X-Forwarded-For'),
        hVia: header(request, 'Via'),
        hXFwdPort: header(request, 'X-Forwarded-Port'),
        hXFwdHost: header(request, 'X-Forwarded-Host'),
        hXReqWith: header(request, 'X-Requested-With'),
        hUserAgent: header(request, 'User-Agent'),
        hXReqId: header(request, 'X-Request-ID'),
        hAccept: header(request, 'Accept'),
        hAcceptLanguage: header(request, 'Accept-Language'),
        hAcceptCharset: header(request, 'Accept-Charset'),
        hAcceptEncoding: header(request, 'Accept-Encoding'),
        hConnection: header(request, 'Connection'),
        hContentType: header(request, 'Content-Type'),
        hFrom: header(request, 'From'),
        hHost: header(request, 'Host'),
        hOrigin: header(request, 'Origin'),
        hPragma: header(request, 'Pragma'),
        hReferer: header(request, 'Referer'),
        hSecChDevMem: header(request, 'Sec-CH-Device-Memory'),
        hSecChUa: header(request, 'Sec-CH-UA'),
        hSecChUaModel: header(request, 'Sec-CH-UA-Model'),
        hSecChUaFull: header(request, 'Sec-CH-UA-Full-Version'),
        hSecChUaMobile: header(request, 'Sec-CH-UA-Mobile'),
        hSecChUaPlatform: header(request, 'Sec-CH-UA-Platform'),
        hSecChUaArch: header(request, 'Sec-CH-UA-Arch'),
        hSecFetchDest: header(request, 'Sec-Fetch-Dest'),
        hSecFetchMode: header(request, 'Sec-Fetch-Mode'),
        hSecFetchSite: header(request, 'Sec-Fetch-Site'),
        hSecFetchUser: header(request, 'Sec-Fetch-User'),
        headerNames: Object.keys(request.headers),
    };

    if (authorization) {
        const parts = authorization.split(/ +/);
        if (parts.length > 1) {
            data.hAuthPrefix = parts[0];
        }
        data.hAuthSize = authorization.length;
    }

    if (cookieHeader) {
        data.cookieNames = cookieHeader
            .split(';')
            .map(cookie => cookie.split('=')[0].trim())
            .filter(name => name.length > 0);
    }

    if (additionalHeadersToCollect.length) {
        const additionalHeaders: Record<string, string> = {};
        additionalHeadersToCollect.forEach(name => {
            const value = header(request, name);
            if (value !== undefined) {
                additionalHeaders[name] = value;
            }
        });
        data.additionalHeaders = additionalHeaders;
    }

    return data;
}

function serializeCookie(cookie: {
    name: string;
    value: string;
    maxAge?: number;
    domain?: string;
    path?: string;
    secure?: boolean;
    httpOnly?: boolean;
    sameSite?: string;
}): string {
    const parts = [`${cookie.name}=${cookie.value}`];
    if (cookie.maxAge !== undefined) parts.push(`Max-Age=${cookie.maxAge}`);
    if (cookie.domain) parts.push(`Domain=${cookie.domain}`);
    if (cookie.path) parts.push(`Path=${cookie.path}`);
    if (cookie.secure) parts.push('Secure');
    if (cookie.httpOnly) parts.push('HttpOnly');
    if (cookie.sameSite) parts.push(`SameSite=${cookie.sameSite}`);
    return parts.join('; ');
}

export {UmbrellaService} from 'umbrella-client';
