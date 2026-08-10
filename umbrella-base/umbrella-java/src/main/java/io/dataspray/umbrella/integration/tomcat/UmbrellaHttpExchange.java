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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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

    // Caps on collected values, so that an oversized request cannot produce an oversized API payload
    static final int MAX_URI_BYTES = 2048;
    static final int MAX_USER_AGENT_BYTES = 768;
    static final int MAX_REFERER_BYTES = 1024;
    static final int MAX_LONG_HEADER_BYTES = 512;
    static final int MAX_SHORT_HEADER_BYTES = 256;
    static final int MAX_TINY_HEADER_BYTES = 128;
    static final int MAX_NAME_LIST_BYTES = 512;

    public static HttpMetadata collect(HttpExchangeAdapter.Request request, List<String> additionalHeadersToCollect) {
        HttpMetadata data = new HttpMetadata();
        data.setTs(Instant.now());
        data.setUri(StringTruncator.truncate(request.getRequestUri(), MAX_URI_BYTES));
        data.setMethod(request.getMethod());
        data.setProto(request.getScheme());
        data.setIp(request.getRemoteAddr());
        data.sethXFwdProto(request.getHeader("X-Forwarded-Proto"));
        data.sethCfConnIp(request.getHeader("CF-Connecting-IP"));
        data.sethTrueClientIp(request.getHeader("True-Client-IP"));
        data.sethXRealIp(request.getHeader("X-Real-IP"));
        data.sethFwd(StringTruncator.truncate(request.getHeader("Forwarded"), MAX_LONG_HEADER_BYTES));
        data.sethXFwdFor(StringTruncator.truncateKeepingEnd(request.getHeader("X-Forwarded-For"), MAX_LONG_HEADER_BYTES));
        data.sethVia(StringTruncator.truncate(request.getHeader("Via"), MAX_SHORT_HEADER_BYTES));
        data.setPort((long) request.getRemotePort());
        data.sethXFwdPort(request.getHeader("X-Forwarded-Port"));
        data.sethXFwdHost(StringTruncator.truncate(request.getHeader("X-Forwarded-Host"), MAX_LONG_HEADER_BYTES));
        data.sethXReqWith(request.getHeader("X-Requested-With"));
        data.sethUserAgent(StringTruncator.truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_BYTES));
        String headerAuthorization = request.getHeader("Authorization");
        if (headerAuthorization != null) {
            String[] headerAuthorizationSplit = headerAuthorization.split(" +");
            if (headerAuthorizationSplit.length > 1) {
                data.sethAuthPrefix(headerAuthorizationSplit[0]);
            }
            data.sethAuthSize((long) headerAuthorization.length());
        }
        data.sethXReqId(request.getHeader("X-Request-ID"));
        data.sethAccept(StringTruncator.truncate(request.getHeader("Accept"), MAX_LONG_HEADER_BYTES));
        data.sethAcceptLanguage(StringTruncator.truncate(request.getHeader("Accept-Language"), MAX_SHORT_HEADER_BYTES));
        data.sethAcceptCharset(StringTruncator.truncate(request.getHeader("Accept-Charset"), MAX_TINY_HEADER_BYTES));
        data.sethAcceptEncoding(StringTruncator.truncate(request.getHeader("Accept-Encoding"), MAX_TINY_HEADER_BYTES));
        data.sethConnection(request.getHeader("Connection"));
        data.sethContentType(StringTruncator.truncate(request.getHeader("Content-Type"), MAX_TINY_HEADER_BYTES));
        data.sethFrom(request.getHeader("From"));
        data.sethHost(StringTruncator.truncate(request.getHeader("Host"), MAX_LONG_HEADER_BYTES));
        data.sethOrigin(StringTruncator.truncate(request.getHeader("Origin"), MAX_LONG_HEADER_BYTES));
        data.setContentLength(request.getContentLengthLong());
        data.sethPragma(request.getHeader("Pragma"));
        data.sethReferer(StringTruncator.truncate(request.getHeader("Referer"), MAX_REFERER_BYTES));
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
            data.setHeaderNames(capNameList(headerNames));
        }
        List<String> cookieNames = request.getCookieNames();
        if (cookieNames != null) {
            data.setCookieNames(capNameList(cookieNames));
        }
        if (!additionalHeadersToCollect.isEmpty()) {
            Map<String, String> additionalHeaders = new HashMap<>();
            data.additionalHeaders(additionalHeaders);
            for (String header : additionalHeadersToCollect) {
                String value = request.getHeader(header);
                if (value != null) {
                    additionalHeaders.put(header, StringTruncator.truncate(value, MAX_SHORT_HEADER_BYTES));
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

    /**
     * Keeps the combined length of a name list within the payload cap, dropping trailing names.
     */
    private static List<String> capNameList(List<String> names) {
        List<String> capped = new ArrayList<>(names.size());
        int budget = MAX_NAME_LIST_BYTES;
        for (String name : names) {
            int cost = name.getBytes(StandardCharsets.UTF_8).length + 1;
            if (cost > budget) {
                break;
            }
            budget -= cost;
            capped.add(name);
        }
        return capped;
    }

    private UmbrellaHttpExchange() {
        // Disable ctor
    }
}
