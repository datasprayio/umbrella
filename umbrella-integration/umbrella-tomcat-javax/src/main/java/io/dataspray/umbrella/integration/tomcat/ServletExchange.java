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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Adapts the javax.servlet API to the servlet-agnostic contract shared by all Umbrella integrations.
 */
final class ServletExchange {

    private static final Logger log = Logger.getLogger(ServletExchange.class.getCanonicalName());

    static final class Request implements HttpExchangeAdapter.Request {

        private final HttpServletRequest request;

        Request(HttpServletRequest request) {
            this.request = request;
        }

        @Override
        public String getRequestUri() {
            return request.getRequestURI();
        }

        @Override
        public String getMethod() {
            return request.getMethod();
        }

        @Override
        public String getScheme() {
            return request.getScheme();
        }

        @Override
        public String getRemoteAddr() {
            return request.getRemoteAddr();
        }

        @Override
        public int getRemotePort() {
            return request.getRemotePort();
        }

        @Override
        public long getContentLengthLong() {
            return request.getContentLengthLong();
        }

        @Override
        public String getHeader(String name) {
            return request.getHeader(name);
        }

        @Override
        public List<String> getHeaderNames() {
            Enumeration<String> headerNames = request.getHeaderNames();
            return headerNames == null ? null : Collections.list(headerNames);
        }

        @Override
        public List<String> getCookieNames() {
            javax.servlet.http.Cookie[] cookies = request.getCookies();
            return cookies == null ? null : Arrays.stream(cookies)
                    .map(javax.servlet.http.Cookie::getName)
                    .collect(Collectors.toList());
        }

        @Override
        public Object getAttribute(String name) {
            return request.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            request.setAttribute(name, value);
        }

        @Override
        public String getTlsCipherSuite() {
            SSLSession session = sslSession();
            if (session != null) {
                return session.getCipherSuite();
            }
            Object cipherSuite = firstAttribute("javax.servlet.request.cipher_suite");
            return cipherSuite instanceof String ? (String) cipherSuite : null;
        }

        @Override
        public String getTlsProtocol() {
            SSLSession session = sslSession();
            if (session != null) {
                return session.getProtocol();
            }
            Object protocol = firstAttribute("org.apache.tomcat.util.net.secure_protocol_version");
            return protocol instanceof String ? (String) protocol : null;
        }

        private SSLSession sslSession() {
            Object attr = firstAttribute("javax.servlet.request.ssl_session", "javax.servlet.request.ssl_session");
            return attr instanceof SSLSession ? (SSLSession) attr : null;
        }

        private Object firstAttribute(String... names) {
            for (String name : names) {
                Object value = request.getAttribute(name);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
    }

    static final class Response implements HttpExchangeAdapter.Response {

        private final HttpServletResponse response;

        Response(HttpServletResponse response) {
            this.response = response;
        }

        @Override
        public void setHeader(String name, String value) {
            response.setHeader(name, value);
        }

        @Override
        public void addCookie(Cookie cookie) {
            javax.servlet.http.Cookie servletCookie = new javax.servlet.http.Cookie(cookie.getName(), cookie.getValue());
            if (cookie.getDomain() != null) {
                servletCookie.setDomain(cookie.getDomain());
            }
            if (cookie.getPath() != null) {
                servletCookie.setPath(cookie.getPath());
            }
            if (cookie.getMaxAge() != null) {
                servletCookie.setMaxAge(cookie.getMaxAge().intValue());
            }
            if (cookie.getSecure() != null) {
                servletCookie.setSecure(cookie.getSecure());
            }
            if (cookie.getHttpOnly() != null) {
                servletCookie.setHttpOnly(cookie.getHttpOnly());
            }
            if (cookie.getSameSite() != null) {
                log.log(Level.FINE, "SameSite cookie attribute is not supported by javax.servlet, ignoring value {0}",
                        cookie.getSameSite());
            }
            response.addCookie(servletCookie);
        }

        @Override
        public void setStatus(int status) {
            response.setStatus(status);
        }

        @Override
        public void sendError(int status) throws IOException {
            response.sendError(status);
        }
    }

    private ServletExchange() {
        // Disable ctor
    }
}
