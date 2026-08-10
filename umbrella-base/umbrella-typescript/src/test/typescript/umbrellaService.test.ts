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
import {OperationMode, RequestProcess} from '../../main/typescript/client';
import {UmbrellaService} from '../../main/typescript/umbrellaService';

function jsonResponse(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), {
        status,
        headers: {'Content-Type': 'application/json'},
    });
}

function serviceWith(responses: Array<() => Response>) {
    const calls: string[] = [];
    let index = 0;
    const service = new UmbrellaService({
        org: 'org1',
        apiKey: 'key',
        nodeId: 'node1',
        basePath: 'https://umbrella.test',
        onError: () => undefined,
        fetchApi: (async (input: any) => {
            calls.push(String(input));
            const next = responses[Math.min(index++, responses.length - 1)];
            return next();
        }) as any,
    });
    return {service, calls};
}

test('starts disabled and adopts the mode from the ping', async () => {
    const {service} = serviceWith([() => jsonResponse({config: {mode: OperationMode.Blocking, timeoutMs: 500}})]);
    assert.equal(service.getMode(), OperationMode.Disabled);

    await service.start();
    service.stop();

    assert.equal(service.getMode(), OperationMode.Blocking);
});

test('a failed ping leaves the service disabled rather than throwing', async () => {
    const {service} = serviceWith([() => jsonResponse({message: 'nope'}, 403)]);

    await service.start();
    service.stop();

    assert.equal(service.getMode(), OperationMode.Disabled);
});

test('returns the server action in blocking mode', async () => {
    const {service} = serviceWith([
        () => jsonResponse({config: {mode: OperationMode.Blocking}}),
        () => jsonResponse({action: {requestProcess: RequestProcess.Block, responseStatus: 403}}),
    ]);
    await service.start();
    service.stop();

    const action = await service.httpEvent({});

    assert.equal(action.requestProcess, RequestProcess.Block);
    assert.equal(action.responseStatus, 403);
});

test('fails open when the event call errors in blocking mode', async () => {
    const {service} = serviceWith([
        () => jsonResponse({config: {mode: OperationMode.Blocking}}),
        () => jsonResponse({message: 'boom'}, 500),
    ]);
    await service.start();
    service.stop();

    const action = await service.httpEvent({});

    assert.equal(action.requestProcess, RequestProcess.Allow);
});

test('a 429 disables the service until the next ping', async () => {
    const {service} = serviceWith([
        () => jsonResponse({config: {mode: OperationMode.Blocking}}),
        () => jsonResponse({message: 'slow down'}, 429),
    ]);
    await service.start();
    service.stop();

    const action = await service.httpEvent({});

    assert.equal(action.requestProcess, RequestProcess.Allow);
    assert.equal(service.getMode(), OperationMode.Disabled);
});

test('monitor mode allows immediately without waiting for the server', async () => {
    const {service} = serviceWith([
        () => jsonResponse({config: {mode: OperationMode.Monitor}}),
        () => jsonResponse({action: {requestProcess: RequestProcess.Block}}),
    ]);
    await service.start();
    service.stop();

    const action = await service.httpEvent({});

    assert.equal(action.requestProcess, RequestProcess.Allow);
});

test('disabled mode makes no event call at all', async () => {
    const {service, calls} = serviceWith([() => jsonResponse({config: {mode: OperationMode.Disabled}})]);
    await service.start();
    service.stop();
    const callsAfterPing = calls.length;

    const action = await service.httpEvent({});

    assert.equal(action.requestProcess, RequestProcess.Allow);
    assert.equal(calls.length, callsAfterPing);
});
