/*
 * Copyright 2024 Matus Faro
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
import io.dataspray.umbrella.client.model.HttpData;
import io.dataspray.umbrella.client.model.RequestProcess;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class UmbrellaFilter implements Filter {

    private static final Logger log = Logger.getLogger(UmbrellaFilter.class.getCanonicalName());
    private final UmbrellaService umbrellaService;
    boolean enabled = true;

    public UmbrellaFilter() {
        this(UmbrellaService.create());
    }

    UmbrellaFilter(UmbrellaService umbrellaService) {
        this.umbrellaService = umbrellaService;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);

        // Enabled property
        String enabledStr = filterConfig.getInitParameter("enabled");
        if ("false".equalsIgnoreCase(enabledStr) || "0".equals(enabledStr)) {
            log.log(Level.INFO, "Umbrella Filter is disabled via configuration");
            enabled = false;
            return;
        }

        // Api key property
        String apiKey = filterConfig.getInitParameter("apiKey");
        if (apiKey == null || apiKey.isEmpty() || "APIKEY".equals(apiKey)) {
            throw new ServletException("Umbrella API key is missing");
        }

        // Endpoint URL property
        Optional<String> endpointUrlOpt = Optional.ofNullable(filterConfig.getInitParameter("endpointUrl"))
                .filter(s -> !s.isEmpty());
        endpointUrlOpt.ifPresent(endpointUrl -> log.log(Level.INFO, "Umbrella using endpoint: {0}", endpointUrl));

        umbrellaService.init(
                apiKey,
                getServerIdentifierParts(filterConfig.getServletContext()),
                endpointUrlOpt);

        log.log(Level.FINE, "Umbrella initialized");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        if (!enabled) {
            log.log(Level.FINEST, "Skipping due to filter being disabled");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (!(servletRequest instanceof HttpServletRequest)) {
            log.log(Level.FINE, "Skipping non-HTTP request");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;

        if (!(servletResponse instanceof HttpServletResponse)) {
            log.fine("Skipping non-HTTP response");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        // Prepare request
        HttpData data = new HttpData();
        data.setTs(Instant.now());
        data.setUri(httpServletRequest.getRequestURI());
        data.setMethod(httpServletRequest.getMethod());
        data.setProto(httpServletRequest.getScheme());
        data.setIp(httpServletRequest.getRemoteAddr());
        data.sethXFwdProto(httpServletRequest.getHeader("X-Forwarded-Proto"));
        data.sethTrueClientIp(httpServletRequest.getHeader("True-Client-IP"));
        data.sethXRealIp(httpServletRequest.getHeader("X-Real-IP"));
        data.sethXFwdFor(httpServletRequest.getHeader("X-Forwarded-For"));
        data.sethVia(httpServletRequest.getHeader("Via"));
        data.setPort((long) httpServletRequest.getRemotePort());
        data.sethXFwdPort(httpServletRequest.getHeader("X-Forwarded-Port"));
        data.sethXFwdHost(httpServletRequest.getHeader("X-Forwarded-Host"));
        data.sethXReqWith(httpServletRequest.getHeader("X-Requested-With"));
        data.sethUserAgent(httpServletRequest.getHeader("User-Agent"));
        String headerAuthorization = httpServletRequest.getHeader("Authorization");
        if (headerAuthorization != null) {
            String[] headerAuthorizationSplit = headerAuthorization.split(" +");
            if (headerAuthorizationSplit.length > 1) {
                data.sethAuthPrefix(headerAuthorizationSplit[0]);
            }
            data.sethAuthSize((long) headerAuthorization.length());
        }
        data.sethXReqId(httpServletRequest.getHeader("X-Request-ID"));
        data.sethAccept(httpServletRequest.getHeader("Accept"));
        data.sethAcceptLanguage(httpServletRequest.getHeader("Accept-Language"));
        data.sethAcceptCharset(httpServletRequest.getHeader("Accept-Charset"));
        data.sethAcceptEncoding(httpServletRequest.getHeader("Accept-Encoding"));
        data.sethConnection(httpServletRequest.getHeader("Connection"));
        data.sethContentType(httpServletRequest.getHeader("Content-Type"));
        data.sethFrom(httpServletRequest.getHeader("From"));
        data.sethHost(httpServletRequest.getHeader("Host"));
        data.sethOrigin(httpServletRequest.getHeader("Origin"));
        data.setContentLength(httpServletRequest.getContentLengthLong());
        data.sethPragma(httpServletRequest.getHeader("Pragma"));
        data.sethReferer(httpServletRequest.getHeader("Referer"));
        data.sethSecChDevMem(httpServletRequest.getHeader("Sec-CH-Device-Memory"));
        data.sethSecChUa(httpServletRequest.getHeader("Sec-CH-UA"));
        data.sethSecChUaModel(httpServletRequest.getHeader("Sec-CH-UA-Model"));
        data.sethSecChUaFull(httpServletRequest.getHeader("Sec-CH-UA-Full-Version"));
        data.sethSecChUaMobile(httpServletRequest.getHeader("Sec-CH-UA-Mobile"));
        data.sethSecChUaPlatform(httpServletRequest.getHeader("Sec-CH-UA-Platform"));
        data.sethSecChUaArch(httpServletRequest.getHeader("Sec-CH-UA-Arch"));
        data.sethSecFetchDest(httpServletRequest.getHeader("Sec-Fetch-Dest"));
        data.sethSecFetchMode(httpServletRequest.getHeader("Sec-Fetch-Mode"));
        data.sethSecFetchSite(httpServletRequest.getHeader("Sec-Fetch-Site"));
        data.sethSecFetchUser(httpServletRequest.getHeader("Sec-Fetch-User"));
        Object sslSessionAttr = httpServletRequest.getAttribute("jakarta.servlet.request.ssl_session");
        if (sslSessionAttr == null) {
            sslSessionAttr = httpServletRequest.getAttribute("javax.servlet.request.ssl_session");
        }
        if (sslSessionAttr instanceof SSLSession) {
            SSLSession sslSession = (SSLSession) sslSessionAttr;
            data.setTlsCipher(sslSession.getProtocol());
            data.setTlsProto(sslSession.getCipherSuite());
        }
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        if (headerNames != null) {
            data.setHeaderNames(Collections.list(headerNames));
        }
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            data.setCookieNames(Arrays.stream(cookies)
                    .map(Cookie::getName)
                    .collect(Collectors.toList()));
        }
        if (!umbrellaService.additionalHeadersToCollect().isEmpty()) {
            Map<String, String> additionalHeaders = new HashMap<>();
            data.additionalHeaders(additionalHeaders);
            umbrellaService.additionalHeadersToCollect().forEach(header -> {
                String value = httpServletRequest.getHeader(header);
                if (value != null) {
                    additionalHeaders.put(header, value);
                }
            });
        }

        // Perform check
        HttpAction httpAction = umbrellaService.httpEvent(data);

        // Apply action
        if (httpAction.getRequestMetadata() != null) {
            httpAction.getRequestMetadata().forEach(httpServletRequest::setAttribute);
        }
        if (httpAction.getResponseHeaders() != null) {
            httpAction.getResponseHeaders().forEach(httpServletResponse::setHeader);
        }
        if (httpAction.getResponseCookies() != null) {
            httpAction.getResponseCookies().forEach(cookie -> {
                Cookie servletCookie = new Cookie(cookie.getName(), cookie.getValue());
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
                    servletCookie.setAttribute("SameSite", cookie.getSameSite());
                }
                httpServletResponse.addCookie(servletCookie);
            });
        }
        if (httpAction.getResponseStatus() != null) {
            httpServletResponse.setStatus(httpAction.getResponseStatus().intValue());
        }

        // Continue processing if allowed
        if (RequestProcess.ALLOW.equals(httpAction.getRequestProcess())) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
        umbrellaService.shutdown();
    }

    private List<String> getServerIdentifierParts(ServletContext context) {
        List<String> uniqueIdentifierParts = new ArrayList<>();

        // Host name of the server
        try {
            uniqueIdentifierParts.add(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ignored) {
        }

        // Display name of the web application as defined in web.xml (e.g. <display-name>MyApp</display-name>)
        String servletContextName = context.getServletContextName();
        if (servletContextName != null) {
            uniqueIdentifierParts.add(servletContextName);
        }

        // Tomcat server info (e.g. Apache Tomcat/8.5.23)
        uniqueIdentifierParts.add(context.getServerInfo());

        // Virtual server name (e.g. example.com)
        // Only available from Servlet API 3.1.
        try {
            uniqueIdentifierParts.add(context.getVirtualServerName());
        } catch (NoSuchMethodError ignored) {
        }

        return uniqueIdentifierParts;
    }
}
