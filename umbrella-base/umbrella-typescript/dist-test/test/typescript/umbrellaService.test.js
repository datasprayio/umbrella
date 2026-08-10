"use strict";
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
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
var assert = require("node:assert/strict");
var node_test_1 = require("node:test");
var client_1 = require("../../main/typescript/client");
var umbrellaService_1 = require("../../main/typescript/umbrellaService");
function jsonResponse(body, status) {
    if (status === void 0) { status = 200; }
    return new Response(JSON.stringify(body), {
        status: status,
        headers: { 'Content-Type': 'application/json' },
    });
}
function serviceWith(responses) {
    var _this = this;
    var calls = [];
    var index = 0;
    var service = new umbrellaService_1.UmbrellaService({
        org: 'org1',
        apiKey: 'key',
        nodeId: 'node1',
        basePath: 'https://umbrella.test',
        onError: function () { return undefined; },
        fetchApi: (function (input) { return __awaiter(_this, void 0, void 0, function () {
            var next;
            return __generator(this, function (_a) {
                calls.push(String(input));
                next = responses[Math.min(index++, responses.length - 1)];
                return [2 /*return*/, next()];
            });
        }); }),
    });
    return { service: service, calls: calls };
}
(0, node_test_1.test)('starts disabled and adopts the mode from the ping', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([function () { return jsonResponse({ config: { mode: client_1.OperationMode.Blocking, timeoutMs: 500 } }); }]).service;
                assert.equal(service.getMode(), client_1.OperationMode.Disabled);
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                assert.equal(service.getMode(), client_1.OperationMode.Blocking);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('a failed ping leaves the service disabled rather than throwing', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([function () { return jsonResponse({ message: 'nope' }, 403); }]).service;
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                assert.equal(service.getMode(), client_1.OperationMode.Disabled);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('returns the server action in blocking mode', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service, action;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([
                    function () { return jsonResponse({ config: { mode: client_1.OperationMode.Blocking } }); },
                    function () { return jsonResponse({ action: { requestProcess: client_1.RequestProcess.Block, responseStatus: 403 } }); },
                ]).service;
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                return [4 /*yield*/, service.httpEvent({})];
            case 2:
                action = _a.sent();
                assert.equal(action.requestProcess, client_1.RequestProcess.Block);
                assert.equal(action.responseStatus, 403);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('fails open when the event call errors in blocking mode', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service, action;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([
                    function () { return jsonResponse({ config: { mode: client_1.OperationMode.Blocking } }); },
                    function () { return jsonResponse({ message: 'boom' }, 500); },
                ]).service;
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                return [4 /*yield*/, service.httpEvent({})];
            case 2:
                action = _a.sent();
                assert.equal(action.requestProcess, client_1.RequestProcess.Allow);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('a 429 disables the service until the next ping', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service, action;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([
                    function () { return jsonResponse({ config: { mode: client_1.OperationMode.Blocking } }); },
                    function () { return jsonResponse({ message: 'slow down' }, 429); },
                ]).service;
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                return [4 /*yield*/, service.httpEvent({})];
            case 2:
                action = _a.sent();
                assert.equal(action.requestProcess, client_1.RequestProcess.Allow);
                assert.equal(service.getMode(), client_1.OperationMode.Disabled);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('monitor mode allows immediately without waiting for the server', function () { return __awaiter(void 0, void 0, void 0, function () {
    var service, action;
    return __generator(this, function (_a) {
        switch (_a.label) {
            case 0:
                service = serviceWith([
                    function () { return jsonResponse({ config: { mode: client_1.OperationMode.Monitor } }); },
                    function () { return jsonResponse({ action: { requestProcess: client_1.RequestProcess.Block } }); },
                ]).service;
                return [4 /*yield*/, service.start()];
            case 1:
                _a.sent();
                service.stop();
                return [4 /*yield*/, service.httpEvent({})];
            case 2:
                action = _a.sent();
                assert.equal(action.requestProcess, client_1.RequestProcess.Allow);
                return [2 /*return*/];
        }
    });
}); });
(0, node_test_1.test)('disabled mode makes no event call at all', function () { return __awaiter(void 0, void 0, void 0, function () {
    var _a, service, calls, callsAfterPing, action;
    return __generator(this, function (_b) {
        switch (_b.label) {
            case 0:
                _a = serviceWith([function () { return jsonResponse({ config: { mode: client_1.OperationMode.Disabled } }); }]), service = _a.service, calls = _a.calls;
                return [4 /*yield*/, service.start()];
            case 1:
                _b.sent();
                service.stop();
                callsAfterPing = calls.length;
                return [4 /*yield*/, service.httpEvent({})];
            case 2:
                action = _b.sent();
                assert.equal(action.requestProcess, client_1.RequestProcess.Allow);
                assert.equal(calls.length, callsAfterPing);
                return [2 /*return*/];
        }
    });
}); });
