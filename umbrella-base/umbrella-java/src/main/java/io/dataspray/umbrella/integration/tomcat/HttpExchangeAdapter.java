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

import io.dataspray.umbrella.client.model.Cookie;

import java.io.IOException;
import java.util.List;

/**
 * The seam between servlet-API-specific integrations and the shared request collection and action enforcement in
 * {@link UmbrellaHttpExchange}. Implemented once per servlet API flavour (jakarta, javax) so that the logic itself is
 * written once.
 */
public interface HttpExchangeAdapter {

    interface Request {

        String getRequestUri();

        String getMethod();

        String getScheme();

        String getRemoteAddr();

        int getRemotePort();

        long getContentLengthLong();

        String getHeader(String name);

        /**
         * @return header names present on the request, or {@code null} if unavailable
         */
        List<String> getHeaderNames();

        /**
         * @return cookie names present on the request, or {@code null} if there are no cookies
         */
        List<String> getCookieNames();

        Object getAttribute(String name);

        void setAttribute(String name, Object value);

        /**
         * @return negotiated TLS cipher suite, or {@code null} for plaintext requests
         */
        String getTlsCipherSuite();

        /**
         * @return negotiated TLS protocol version, or {@code null} for plaintext requests
         */
        String getTlsProtocol();
    }

    interface Response {

        void setHeader(String name, String value);

        void addCookie(Cookie cookie);

        void setStatus(int status);

        void sendError(int status) throws IOException;
    }
}
