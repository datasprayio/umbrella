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

package io.dataspray.umbrella.integration.tomcat;

import io.dataspray.umbrella.client.model.HttpAction;
import io.dataspray.umbrella.client.model.HttpMetadata;
import io.dataspray.umbrella.client.model.RequestProcess;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request metadata collection and action enforcement, shared by every servlet integration.
 */
public final class UmbrellaHttpExchange {

    /**
     * Request attribute holding the milliseconds spent inside Umbrella, for applications that want to monitor the
     * overhead this filter adds.
     */
    public static final String ATTRIBUTE_SPENT_TIME_MS = "umbrella.spent_time_ms";

    /**
     * Status returned when the server asks to block without naming a status of its own.
     */
    static final int DEFAULT_BLOCK_STATUS = 403;

    public static HttpMetadata collect(HttpExchangeAdapter.Request request, List<String> additionalHeadersToCollect) {
        HttpMetadata data = new HttpMetadata();
        data.setTs(Instant.now());
        data.setUri(request.getRequestUri());
        data.setMethod(request.getMethod());
        data.setProto(request.getScheme());
        data.setIp(request.getRemoteAddr());
        data.sethXFwdProto(request.getHeader("X-Forwarded-Proto"));
        data.sethCfConnIp(request.getHeader("CF-Connecting-IP"));
        data.sethTrueClientIp(request.getHeader("True-Client-IP"));
        data.sethXRealIp(request.getHeader("X-Real-IP"));
        data.sethFwd(request.getHeader("Forwarded"));
        data.sethXFwdFor(request.getHeader("X-Forwarded-For"));
        data.sethVia(request.getHeader("Via"));
        data.setPort((long) request.getRemotePort());
        data.sethXFwdPort(request.getHeader("X-Forwarded-Port"));
        data.sethXFwdHost(request.getHeader("X-Forwarded-Host"));
        data.sethXReqWith(request.getHeader("X-Requested-With"));
        data.sethUserAgent(request.getHeader("User-Agent"));
        String headerAuthorization = request.getHeader("Authorization");
        if (headerAuthorization != null) {
            String[] headerAuthorizationSplit = headerAuthorization.split(" +");
            if (headerAuthorizationSplit.length > 1) {
                data.sethAuthPrefix(headerAuthorizationSplit[0]);
            }
            data.sethAuthSize((long) headerAuthorization.length());
        }
        data.sethXReqId(request.getHeader("X-Request-ID"));
        data.sethAccept(request.getHeader("Accept"));
        data.sethAcceptLanguage(request.getHeader("Accept-Language"));
        data.sethAcceptCharset(request.getHeader("Accept-Charset"));
        data.sethAcceptEncoding(request.getHeader("Accept-Encoding"));
        data.sethConnection(request.getHeader("Connection"));
        data.sethContentType(request.getHeader("Content-Type"));
        data.sethFrom(request.getHeader("From"));
        data.sethHost(request.getHeader("Host"));
        data.sethOrigin(request.getHeader("Origin"));
        data.setContentLength(request.getContentLengthLong());
        data.sethPragma(request.getHeader("Pragma"));
        data.sethReferer(request.getHeader("Referer"));
        data.sethSecChDevMem(request.getHeader("Sec-CH-Device-Memory"));
        data.sethSecChUa(request.getHeader("Sec-CH-UA"));
        data.sethSecChUaModel(request.getHeader("Sec-CH-UA-Model"));
        data.sethSecChUaFull(request.getHeader("Sec-CH-UA-Full-Version"));
        data.sethSecChUaMobile(request.getHeader("Sec-CH-UA-Mobile"));
        data.sethSecChUaPlatform(request.getHeader("Sec-CH-UA-Platform"));
        data.sethSecChUaArch(request.getHeader("Sec-CH-UA-Arch"));
        data.sethSecFetchDest(request.getHeader("Sec-Fetch-Dest"));
        data.sethSecFetchMode(request.getHeader("Sec-Fetch-Mode"));
        data.sethSecFetchSite(request.getHeader("Sec-Fetch-Site"));
        data.sethSecFetchUser(request.getHeader("Sec-Fetch-User"));
        data.setTlsCipher(request.getTlsCipherSuite());
        data.setTlsProto(request.getTlsProtocol());

        List<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            data.setHeaderNames(headerNames);
        }
        List<String> cookieNames = request.getCookieNames();
        if (cookieNames != null) {
            data.setCookieNames(cookieNames);
        }
        if (!additionalHeadersToCollect.isEmpty()) {
            Map<String, String> additionalHeaders = new HashMap<>();
            data.additionalHeaders(additionalHeaders);
            for (String header : additionalHeadersToCollect) {
                String value = request.getHeader(header);
                if (value != null) {
                    additionalHeaders.put(header, value);
                }
            }
        }
        return data;
    }

    /**
     * Applies the server's action to the exchange.
     *
     * @return whether the filter chain should continue. Only an explicit {@link RequestProcess#BLOCK} stops it; any
     * other or absent value continues, so that an unrecognised response cannot take the application offline.
     */
    public static boolean applyAndShouldContinue(
            HttpAction action,
            HttpExchangeAdapter.Request request,
            HttpExchangeAdapter.Response response) throws IOException {

        if (action.getRequestMetadata() != null) {
            action.getRequestMetadata().forEach(request::setAttribute);
        }
        if (action.getResponseHeaders() != null) {
            action.getResponseHeaders().forEach(response::setHeader);
        }
        if (action.getResponseCookies() != null) {
            action.getResponseCookies().forEach(response::addCookie);
        }

        if (!RequestProcess.BLOCK.equals(action.getRequestProcess())) {
            if (action.getResponseStatus() != null) {
                response.setStatus(action.getResponseStatus().intValue());
            }
            return true;
        }

        response.sendError(action.getResponseStatus() != null
                ? action.getResponseStatus().intValue()
                : DEFAULT_BLOCK_STATUS);
        return false;
    }

    private UmbrellaHttpExchange() {
        // Disable ctor
    }
}
