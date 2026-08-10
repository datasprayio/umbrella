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
import {SPENT_TIME_PROPERTY, umbrella, UmbrellaRequest, UmbrellaResponse} from '../src';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
}

function fakeRequest(overrides: Partial<UmbrellaRequest> = {}): UmbrellaRequest {
    return {
        method: 'GET',
        originalUrl: '/api/thing',
        protocol: 'https',
        headers: {'user-agent': 'test-agent', host: 'example.com', cookie: 'a=1; b=2'},
        socket: {remoteAddress: '203.0.113.4', remotePort: 51234},
        ...overrides,
    };
}

function fakeResponse() {
    const state = {status: undefined as number | undefined, headers: {} as Record<string, string>, ended: false};
    const response: UmbrellaResponse = {
        setHeader(name: string, value: string) {
            state.headers[name] = value;
            return response;
        },
        status(code: number) {
            state.status = code;
            return response;
        },
        end() {
            state.ended = true;
            return response;
        },
    };
    return {response, state};
}

function middlewareWith(responses: Array<() => Response>, options: Record<string, unknown> = {}) {
    let index = 0;
    return umbrella({
        org: 'org1',
        apiKey: 'key',
        nodeId: 'node1',
        basePath: 'https://umbrella.test',
        onError: () => undefined,
        fetchApi: (async () => responses[Math.min(index++, responses.length - 1)]()) as any,
        ...options,
    });
}

function run(mw: ReturnType<typeof umbrella>, request: UmbrellaRequest, response: UmbrellaResponse): Promise<unknown> {
    return new Promise(resolve => mw(request, response, resolve));
}

test('calls next and records spent time when allowed', async () => {
    const mw = middlewareWith([
        () => jsonResponse({config: {mode: 'BLOCKING'}}),
        () => jsonResponse({action: {requestProcess: 'ALLOW'}}),
    ]);
    await mw.start();
    mw.stop();

    const request = fakeRequest();
    const {response, state} = fakeResponse();
    await run(mw, request, response);

    assert.equal(state.ended, false);
    assert.equal(typeof request[SPENT_TIME_PROPERTY], 'number');
});

test('ends the response without calling next when blocked', async () => {
    const mw = middlewareWith([
        () => jsonResponse({config: {mode: 'BLOCKING'}}),
        () => jsonResponse({action: {requestProcess: 'BLOCK', responseStatus: 429}}),
    ]);
    await mw.start();
    mw.stop();

    const request = fakeRequest();
    const {response, state} = fakeResponse();
    let nextCalled = false;
    await new Promise<void>(resolve => {
        mw(request, response, () => {
            nextCalled = true;
            resolve();
        });
        setTimeout(resolve, 50);
    });

    assert.equal(nextCalled, false);
    assert.equal(state.status, 429);
    assert.equal(state.ended, true);
});

test('blocking without a status falls back to 403', async () => {
    const mw = middlewareWith([
        () => jsonResponse({config: {mode: 'BLOCKING'}}),
        () => jsonResponse({action: {requestProcess: 'BLOCK'}}),
    ]);
    await mw.start();
    mw.stop();

    const {response, state} = fakeResponse();
    await new Promise<void>(resolve => {
        mw(fakeRequest(), response, () => resolve());
        setTimeout(resolve, 50);
    });

    assert.equal(state.status, 403);
});

test('continues when the API errors', async () => {
    const mw = middlewareWith([
        () => jsonResponse({config: {mode: 'BLOCKING'}}),
        () => jsonResponse({message: 'boom'}, 500),
    ]);
    await mw.start();
    mw.stop();

    const {response, state} = fakeResponse();
    await run(mw, fakeRequest(), response);

    assert.equal(state.ended, false);
});

test('skips excluded paths without contacting the API', async () => {
    let calls = 0;
    const mw = umbrella({
        org: 'org1',
        apiKey: 'key',
        basePath: 'https://umbrella.test',
        onError: () => undefined,
        excludePath: /\.(css|js|png)$/,
        fetchApi: (async () => {
            calls++;
            return jsonResponse({config: {mode: 'BLOCKING'}});
        }) as any,
    });
    await mw.start();
    mw.stop();
    const callsAfterPing = calls;

    const {response} = fakeResponse();
    await run(mw, fakeRequest({originalUrl: '/static/app.css'}), response);

    assert.equal(calls, callsAfterPing);
});

test('applies response headers and cookies', async () => {
    const mw = middlewareWith([
        () => jsonResponse({config: {mode: 'BLOCKING'}}),
        () => jsonResponse({
            action: {
                requestProcess: 'ALLOW',
                responseHeaders: {'X-Check': 'passed'},
                responseCookies: [{name: 'sid', value: 'abc', path: '/', httpOnly: true, sameSite: 'Lax'}],
            },
        }),
    ]);
    await mw.start();
    mw.stop();

    const {response, state} = fakeResponse();
    await run(mw, fakeRequest(), response);

    assert.equal(state.headers['X-Check'], 'passed');
    assert.deepEqual(state.headers['Set-Cookie'], ['sid=abc; Path=/; HttpOnly; SameSite=Lax']);
});
