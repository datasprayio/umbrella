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
Object.defineProperty(exports, "__esModule", { value: true });
exports.UmbrellaService = exports.umbrella = exports.SPENT_TIME_PROPERTY = exports.DEFAULT_BLOCK_STATUS = void 0;
const umbrella_client_1 = require("umbrella-client");
exports.DEFAULT_BLOCK_STATUS = 403;
/**
 * Request property holding the milliseconds spent inside Umbrella, matching the servlet integrations.
 */
exports.SPENT_TIME_PROPERTY = 'umbrellaSpentTimeMs';
function umbrella(options) {
    const service = new umbrella_client_1.UmbrellaService(options);
    const { excludePath, includePath } = options;
    const middleware = ((request, response, next) => {
        const path = request.originalUrl ?? request.url ?? '';
        if ((excludePath && excludePath.test(path)) || (includePath && !includePath.test(path))) {
            next();
            return;
        }
        const startedMs = Date.now();
        service.httpEvent(collect(request, path, service.additionalHeadersToCollect()))
            .then(action => {
            request[exports.SPENT_TIME_PROPERTY] = Date.now() - startedMs;
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
                response.setHeader('Set-Cookie', action.responseCookies.map(serializeCookie));
            }
            // Only an explicit block stops the request; anything else continues, so that an unrecognised
            // response cannot take the application offline
            if (action.requestProcess !== umbrella_client_1.RequestProcess.Block) {
                if (action.responseStatus) {
                    response.status(action.responseStatus);
                }
                next();
                return;
            }
            response.status(action.responseStatus ?? exports.DEFAULT_BLOCK_STATUS).end();
        })
            .catch(next);
    });
    middleware.start = () => service.start();
    middleware.stop = () => service.stop();
    Object.defineProperty(middleware, 'service', { value: service });
    return middleware;
}
exports.umbrella = umbrella;
function header(request, name) {
    const value = request.headers[name.toLowerCase()];
    return Array.isArray(value) ? value.join(', ') : value;
}
function collect(request, path, additionalHeadersToCollect) {
    const contentLength = header(request, 'Content-Length');
    const authorization = header(request, 'Authorization');
    const cookieHeader = header(request, 'Cookie');
    const data = {
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
        const additionalHeaders = {};
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
function serializeCookie(cookie) {
    const parts = [`${cookie.name}=${cookie.value}`];
    if (cookie.maxAge !== undefined)
        parts.push(`Max-Age=${cookie.maxAge}`);
    if (cookie.domain)
        parts.push(`Domain=${cookie.domain}`);
    if (cookie.path)
        parts.push(`Path=${cookie.path}`);
    if (cookie.secure)
        parts.push('Secure');
    if (cookie.httpOnly)
        parts.push('HttpOnly');
    if (cookie.sameSite)
        parts.push(`SameSite=${cookie.sameSite}`);
    return parts.join('; ');
}
var umbrella_client_2 = require("umbrella-client");
Object.defineProperty(exports, "UmbrellaService", { enumerable: true, get: function () { return umbrella_client_2.UmbrellaService; } });
