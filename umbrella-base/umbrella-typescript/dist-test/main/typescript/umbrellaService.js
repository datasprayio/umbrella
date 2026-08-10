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
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
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
exports.UmbrellaService = exports.DEFAULT_ALLOW_ACTION = void 0;
var client_1 = require("./client");
var umbrellaClient_1 = require("./umbrellaClient");
exports.DEFAULT_ALLOW_ACTION = { requestProcess: client_1.RequestProcess.Allow };
var PING_INTERVAL_MS = 10 * 60 * 1000;
/**
 * Applied when the server has not supplied a timeout, so that a degraded API cannot hold a request open
 * indefinitely.
 */
var DEFAULT_TIMEOUT_MS = 2000;
/**
 * Bounds the MONITOR mode backlog; beyond this, events are dropped rather than accumulating without limit.
 */
var MONITOR_MAX_IN_FLIGHT = 1000;
/**
 * Mirrors the Java client: server-driven operation modes, a background ping, and fail-open behaviour on every error
 * path so that Umbrella can never take the host application down.
 */
var UmbrellaService = /** @class */ (function () {
    function UmbrellaService(options) {
        var _a, _b;
        this.config = { mode: client_1.OperationMode.Disabled };
        this.inFlightMonitorEvents = 0;
        this.droppedMonitorEvents = 0;
        this.org = options.org;
        this.nodeId = (_a = options.nodeId) !== null && _a !== void 0 ? _a : defaultNodeId();
        this.onError = (_b = options.onError) !== null && _b !== void 0 ? _b : (function (message, error) { return console.warn("[umbrella] ".concat(message), error); });
        this.client = umbrellaClient_1.UmbrellaClient.get({
            apiKey: options.apiKey,
            basePath: options.basePath,
            fetchApi: options.fetchApi,
        });
    }
    /**
     * Performs the initial ping and starts the background refresh. Never rejects: a failure here, including a rejected
     * API key, leaves the service disabled until the next ping rather than failing application startup.
     */
    UmbrellaService.prototype.start = function () {
        var _a, _b;
        return __awaiter(this, void 0, void 0, function () {
            var _this = this;
            return __generator(this, function (_c) {
                switch (_c.label) {
                    case 0: return [4 /*yield*/, this.ping()];
                    case 1:
                        _c.sent();
                        this.pingTimer = setInterval(function () {
                            void _this.ping();
                        }, PING_INTERVAL_MS);
                        // Do not hold the event loop open on account of Umbrella
                        (_b = (_a = this.pingTimer).unref) === null || _b === void 0 ? void 0 : _b.call(_a);
                        return [2 /*return*/];
                }
            });
        });
    };
    UmbrellaService.prototype.stop = function () {
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
            this.pingTimer = undefined;
        }
    };
    UmbrellaService.prototype.getMode = function () {
        var _a;
        return (_a = this.config.mode) !== null && _a !== void 0 ? _a : client_1.OperationMode.Disabled;
    };
    UmbrellaService.prototype.additionalHeadersToCollect = function () {
        var _a;
        return (_a = this.config.collectAdditionalHeaders) !== null && _a !== void 0 ? _a : [];
    };
    UmbrellaService.prototype.getDroppedMonitorEventCount = function () {
        return this.droppedMonitorEvents;
    };
    UmbrellaService.prototype.httpEvent = function (httpMetadata) {
        var _a;
        return __awaiter(this, void 0, void 0, function () {
            var mode, _b, response, error_1;
            var _this = this;
            return __generator(this, function (_c) {
                switch (_c.label) {
                    case 0:
                        mode = this.getMode();
                        _b = mode;
                        switch (_b) {
                            case client_1.OperationMode.Blocking: return [3 /*break*/, 1];
                            case client_1.OperationMode.Monitor: return [3 /*break*/, 4];
                        }
                        return [3 /*break*/, 5];
                    case 1:
                        _c.trys.push([1, 3, , 4]);
                        return [4 /*yield*/, this.sendHttpEvent(httpMetadata, mode)];
                    case 2:
                        response = _c.sent();
                        return [2 /*return*/, (_a = response.action) !== null && _a !== void 0 ? _a : exports.DEFAULT_ALLOW_ACTION];
                    case 3:
                        error_1 = _c.sent();
                        this.onError('Failed to validate http event, allowing request', error_1);
                        return [2 /*return*/, exports.DEFAULT_ALLOW_ACTION];
                    case 4:
                        if (this.inFlightMonitorEvents >= MONITOR_MAX_IN_FLIGHT) {
                            this.droppedMonitorEvents++;
                            return [2 /*return*/, exports.DEFAULT_ALLOW_ACTION];
                        }
                        this.inFlightMonitorEvents++;
                        void this.sendHttpEvent(httpMetadata, mode)
                            .catch(function (error) { return _this.onError('Failed to publish http event', error); })
                            .finally(function () {
                            _this.inFlightMonitorEvents--;
                        });
                        return [2 /*return*/, exports.DEFAULT_ALLOW_ACTION];
                    case 5: return [2 /*return*/, exports.DEFAULT_ALLOW_ACTION];
                }
            });
        });
    };
    UmbrellaService.prototype.sendHttpEvent = function (httpMetadata, currentMode) {
        return __awaiter(this, void 0, void 0, function () {
            var response, error_2;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        return [4 /*yield*/, this.client.ingest().httpEvent({
                                org: this.org,
                                httpEventRequest: { httpMetadata: httpMetadata, nodeId: this.nodeId, currentMode: currentMode },
                            }, { signal: this.timeoutSignal() })];
                    case 1:
                        response = _a.sent();
                        if (response.configRefresh) {
                            this.config = response.configRefresh;
                        }
                        return [2 /*return*/, response];
                    case 2:
                        error_2 = _a.sent();
                        if (isStatus(error_2, 429)) {
                            this.onError('Rate limited by Umbrella, disabling until next ping', error_2);
                            this.config = __assign(__assign({}, this.config), { mode: client_1.OperationMode.Disabled });
                        }
                        throw error_2;
                    case 3: return [2 /*return*/];
                }
            });
        });
    };
    UmbrellaService.prototype.ping = function () {
        return __awaiter(this, void 0, void 0, function () {
            var response, error_3;
            return __generator(this, function (_a) {
                switch (_a.label) {
                    case 0:
                        _a.trys.push([0, 2, , 3]);
                        return [4 /*yield*/, this.client.health().nodePing({ org: this.org, pingRequest: { nodeId: this.nodeId } }, { signal: this.timeoutSignal() })];
                    case 1:
                        response = _a.sent();
                        if (response.config) {
                            this.config = response.config;
                        }
                        return [3 /*break*/, 3];
                    case 2:
                        error_3 = _a.sent();
                        // Including a rejected key: retry at the next interval rather than disabling until restart
                        this.onError('Failed to ping Umbrella, retrying at the next interval', error_3);
                        return [3 /*break*/, 3];
                    case 3: return [2 /*return*/];
                }
            });
        });
    };
    UmbrellaService.prototype.timeoutSignal = function () {
        var timeoutMs = this.config.timeoutMs && this.getMode() === client_1.OperationMode.Blocking
            ? this.config.timeoutMs
            : DEFAULT_TIMEOUT_MS;
        return AbortSignal.timeout(timeoutMs);
    };
    return UmbrellaService;
}());
exports.UmbrellaService = UmbrellaService;
function isStatus(error, status) {
    var _a;
    return typeof error === 'object' && error !== null && ((_a = error.response) === null || _a === void 0 ? void 0 : _a.status) === status;
}
function defaultNodeId() {
    var hostname = 'unknown';
    try {
        // Avoids a hard dependency on node:os for non-node runtimes
        hostname = require('node:os').hostname();
    }
    catch (_a) {
        // Keep the default
    }
    return "".concat(hostname, "; sid=").concat(Math.random().toString(36).slice(2)).concat(Date.now().toString(36));
}
