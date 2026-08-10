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

import * as assert from 'node:assert/strict';
import {test} from 'node:test';
import {UmbrellaClient} from '../../main/typescript/umbrellaClient';

interface CapturedRequest {
    url: string;
    headers: Record<string, string>;
}

function clientWithCapture(apiKey?: string): { client: UmbrellaClient, captured: CapturedRequest[] } {
    const captured: CapturedRequest[] = [];
    const fetchApi = async (input: any, init?: any) => {
        captured.push({
            url: String(input),
            headers: {...(init?.headers ?? {})},
        });
        return new Response(JSON.stringify({config: {mode: 'DISABLED'}}), {
            status: 200,
            headers: {'Content-Type': 'application/json'},
        });
    };
    const client = UmbrellaClient.get({
        apiKey,
        basePath: 'https://umbrella.test',
        fetchApi: fetchApi as any,
    });
    return {client, captured};
}

test('sends the api key as an apikey Authorization header', async () => {
    const {client, captured} = clientWithCapture('secret-key');

    await client.health().nodePing({org: 'org1', pingRequest: {nodeId: 'node1'}});

    assert.equal(captured.length, 1);
    assert.equal(captured[0].headers['Authorization'], 'apikey secret-key');
    assert.ok(captured[0].url.startsWith('https://umbrella.test/org/org1/node/ping'));
});

test('sends no Authorization header when no api key is set', async () => {
    const {client, captured} = clientWithCapture();

    await client.health().nodePing({org: 'org1', pingRequest: {nodeId: 'node1'}});

    assert.equal(captured[0].headers['Authorization'], undefined);
});

test('setApiKey and unsetAuth take effect on already created clients', async () => {
    const {client, captured} = clientWithCapture('first-key');
    const health = client.health();

    await health.nodePing({org: 'org1', pingRequest: {nodeId: 'node1'}});
    client.setApiKey('second-key');
    await health.nodePing({org: 'org1', pingRequest: {nodeId: 'node1'}});
    client.unsetAuth();
    await health.nodePing({org: 'org1', pingRequest: {nodeId: 'node1'}});

    assert.equal(captured[0].headers['Authorization'], 'apikey first-key');
    assert.equal(captured[1].headers['Authorization'], 'apikey second-key');
    assert.equal(captured[2].headers['Authorization'], undefined);
});

test('returns the same api instance across calls', () => {
    const {client} = clientWithCapture('key');
    assert.equal(client.health(), client.health());
    assert.equal(client.ingest(), client.ingest());
});
