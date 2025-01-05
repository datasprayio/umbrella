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
import io.dataspray.umbrella.client.model.HttpAction;
import io.dataspray.umbrella.client.model.HttpMetadata;
import io.dataspray.umbrella.client.model.RequestProcess;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Umbrella filter for Tomcat.
 * <p>
 * Sends all requests to Umbrella for processing.
 * <p>
 * Supports both jakarta and javax.
 */
public class UmbrellaFilter implements jakarta.servlet.Filter, javax.servlet.Filter {

    private static final Logger log = Logger.getLogger(UmbrellaFilter.class.getCanonicalName());
    private final UmbrellaService umbrellaService;
    boolean enabled = true;

    /**
     * Default constructor.
     */
    public UmbrellaFilter() {
        this(UmbrellaService.create());
    }

    /**
     * Constructor for testing.
     */
    UmbrellaFilter(UmbrellaService umbrellaService) {
        this.umbrellaService = umbrellaService;
    }

    @Override
    public void init(javax.servlet.FilterConfig filterConfig) throws javax.servlet.ServletException {
        try {
            init(filterConfig::getInitParameter, getServerIdentifierParts(
                    filterConfig.getServletContext()::getServletContextName,
                    filterConfig.getServletContext()::getServerInfo,
                    filterConfig.getServletContext()::getVirtualServerName));
        } catch (Exception ex) {
            throw new javax.servlet.ServletException(ex);
        }
    }

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) throws jakarta.servlet.ServletException {
        try {
            init(filterConfig::getInitParameter, getServerIdentifierParts(
                    filterConfig.getServletContext()::getServletContextName,
                    filterConfig.getServletContext()::getServerInfo,
                    filterConfig.getServletContext()::getVirtualServerName));
        } catch (Exception ex) {
            throw new jakarta.servlet.ServletException(ex);
        }
    }

    private void init(Function<String, String> filterConfigGetInitParameter, List<String> serverIdentifier) throws Exception {

        // Enabled property
        enabled = getProperty("enabled", "umbrella.enabled", "UMBRELLA_ENABLED", filterConfigGetInitParameter)
                .map(enabledStr -> !"false".equalsIgnoreCase(enabledStr) && !"0".equals(enabledStr))
                .orElse(true);
        if (!enabled) {
            log.log(Level.INFO, "Umbrella Filter is disabled via configuration");
            return;
        }

        // Organization key property
        String orgName = getProperty("org", "umbrella.org", "UMBRELLA_ORG", filterConfigGetInitParameter)
                .orElseThrow(() -> new Exception("Umbrella Organization name property is missing"));

        // Api key property
        String apiKey = getProperty("api-key", "umbrella.api.key", "UMBRELLA_API_KEY", filterConfigGetInitParameter)
                .orElseThrow(() -> new Exception("Umbrella API key property is missing"));

        // Endpoint URL property
        Optional<String> endpointUrlOpt = getProperty("endpoint-url", "umbrella.endpoint.url", "UMBRELLA_ENDPOINT_URL", filterConfigGetInitParameter);
        endpointUrlOpt.ifPresent(endpointUrl -> log.log(Level.INFO, "Umbrella using endpoint: {0}", endpointUrl));

        umbrellaService.init(
                orgName,
                apiKey,
                serverIdentifier,
                endpointUrlOpt);

        log.log(Level.FINE, "Umbrella initialized");
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest servletRequest, javax.servlet.ServletResponse servletResponse, javax.servlet.FilterChain filterChain) throws IOException, javax.servlet.ServletException {

        if (!enabled) {
            log.log(Level.FINEST, "Skipping due to filter being disabled");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (!(servletRequest instanceof javax.servlet.http.HttpServletRequest)) {
            log.log(Level.FINE, "Skipping non-HTTP request");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        javax.servlet.http.HttpServletRequest httpServletRequest = (javax.servlet.http.HttpServletRequest) servletRequest;

        if (!(servletResponse instanceof javax.servlet.http.HttpServletResponse)) {
            log.fine("Skipping non-HTTP response");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        javax.servlet.http.HttpServletResponse httpServletResponse = (javax.servlet.http.HttpServletResponse) servletResponse;

        boolean canContinue = doFilter(
                httpServletRequest::getRequestURI,
                httpServletRequest::getMethod,
                httpServletRequest::getScheme,
                httpServletRequest::getRemoteAddr,
                httpServletRequest::getHeader,
                httpServletRequest::getRemotePort,
                httpServletRequest::getContentLengthLong,
                httpServletRequest::getAttribute,
                httpServletRequest::getHeaderNames,
                () -> {
                    javax.servlet.http.Cookie[] cookies = httpServletRequest.getCookies();
                    return cookies == null ? Collections.emptyList() : Arrays.stream(cookies)
                            .map(javax.servlet.http.Cookie::getName)
                            .collect(Collectors.toList());
                },
                httpServletRequest::setAttribute,
                httpServletResponse::setHeader,
                cookie -> {
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
                        log.warning("SameSite attribute is not supported in Servlet API");
                    }
                    httpServletResponse.addCookie(servletCookie);
                },
                httpServletResponse::setStatus
        );

        if (canContinue) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void doFilter(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse, jakarta.servlet.FilterChain filterChain) throws IOException, jakarta.servlet.ServletException {

        if (!enabled) {
            log.log(Level.FINEST, "Skipping due to filter being disabled");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        if (!(servletRequest instanceof jakarta.servlet.http.HttpServletRequest)) {
            log.log(Level.FINE, "Skipping non-HTTP request");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        jakarta.servlet.http.HttpServletRequest httpServletRequest = (jakarta.servlet.http.HttpServletRequest) servletRequest;

        if (!(servletResponse instanceof jakarta.servlet.http.HttpServletResponse)) {
            log.fine("Skipping non-HTTP response");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        jakarta.servlet.http.HttpServletResponse httpServletResponse = (jakarta.servlet.http.HttpServletResponse) servletResponse;

        boolean canContinue = doFilter(
                httpServletRequest::getRequestURI,
                httpServletRequest::getMethod,
                httpServletRequest::getScheme,
                httpServletRequest::getRemoteAddr,
                httpServletRequest::getHeader,
                httpServletRequest::getRemotePort,
                httpServletRequest::getContentLengthLong,
                httpServletRequest::getAttribute,
                httpServletRequest::getHeaderNames,
                () -> {
                    jakarta.servlet.http.Cookie[] cookies = httpServletRequest.getCookies();
                    return cookies == null ? Collections.emptyList() : Arrays.stream(cookies)
                            .map(jakarta.servlet.http.Cookie::getName)
                            .collect(Collectors.toList());
                },
                httpServletRequest::setAttribute,
                httpServletResponse::setHeader,
                cookie -> {
                    jakarta.servlet.http.Cookie servletCookie = new jakarta.servlet.http.Cookie(cookie.getName(), cookie.getValue());
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
                },
                httpServletResponse::setStatus
        );

        if (canContinue) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    private boolean doFilter(
            Supplier<String> getRequestURI,
            Supplier<String> getMethod,
            Supplier<String> getScheme,
            Supplier<String> getRemoteAddr,
            Function<String, String> getHeader,
            IntSupplier getRemotePort,
            LongSupplier getContentLengthLong,
            Function<String, Object> getAttribute,
            Supplier<Enumeration<String>> getHeaderNames,
            Supplier<List<String>> getCookieNames,
            BiConsumer<String, String> setAttribute,
            BiConsumer<String, String> setHeader,
            Consumer<Cookie> setCookie,
            IntConsumer setStatus
    ) throws IOException {

        // Prepare request
        HttpMetadata data = new HttpMetadata();
        data.setTs(Instant.now());
        data.setUri(getRequestURI.get());
        data.setMethod(getMethod.get());
        data.setProto(getScheme.get());
        data.setIp(getRemoteAddr.get());
        data.sethXFwdProto(getHeader.apply("X-Forwarded-Proto"));
        data.sethCfConnIp(getHeader.apply("CF-Connecting-IP"));
        data.sethTrueClientIp(getHeader.apply("True-Client-IP"));
        data.sethXRealIp(getHeader.apply("X-Real-IP"));
        data.sethFwd(getHeader.apply("Forwarded"));
        data.sethXFwdFor(getHeader.apply("X-Forwarded-For"));
        data.sethVia(getHeader.apply("Via"));
        data.setPort((long) getRemotePort.getAsInt());
        data.sethXFwdPort(getHeader.apply("X-Forwarded-Port"));
        data.sethXFwdHost(getHeader.apply("X-Forwarded-Host"));
        data.sethXReqWith(getHeader.apply("X-Requested-With"));
        data.sethUserAgent(getHeader.apply("User-Agent"));
        String headerAuthorization = getHeader.apply("Authorization");
        if (headerAuthorization != null) {
            String[] headerAuthorizationSplit = headerAuthorization.split(" +");
            if (headerAuthorizationSplit.length > 1) {
                data.sethAuthPrefix(headerAuthorizationSplit[0]);
            }
            data.sethAuthSize((long) headerAuthorization.length());
        }
        data.sethXReqId(getHeader.apply("X-Request-ID"));
        data.sethAccept(getHeader.apply("Accept"));
        data.sethAcceptLanguage(getHeader.apply("Accept-Language"));
        data.sethAcceptCharset(getHeader.apply("Accept-Charset"));
        data.sethAcceptEncoding(getHeader.apply("Accept-Encoding"));
        data.sethConnection(getHeader.apply("Connection"));
        data.sethContentType(getHeader.apply("Content-Type"));
        data.sethFrom(getHeader.apply("From"));
        data.sethHost(getHeader.apply("Host"));
        data.sethOrigin(getHeader.apply("Origin"));
        data.setContentLength(getContentLengthLong.getAsLong());
        data.sethPragma(getHeader.apply("Pragma"));
        data.sethReferer(getHeader.apply("Referer"));
        data.sethSecChDevMem(getHeader.apply("Sec-CH-Device-Memory"));
        data.sethSecChUa(getHeader.apply("Sec-CH-UA"));
        data.sethSecChUaModel(getHeader.apply("Sec-CH-UA-Model"));
        data.sethSecChUaFull(getHeader.apply("Sec-CH-UA-Full-Version"));
        data.sethSecChUaMobile(getHeader.apply("Sec-CH-UA-Mobile"));
        data.sethSecChUaPlatform(getHeader.apply("Sec-CH-UA-Platform"));
        data.sethSecChUaArch(getHeader.apply("Sec-CH-UA-Arch"));
        data.sethSecFetchDest(getHeader.apply("Sec-Fetch-Dest"));
        data.sethSecFetchMode(getHeader.apply("Sec-Fetch-Mode"));
        data.sethSecFetchSite(getHeader.apply("Sec-Fetch-Site"));
        data.sethSecFetchUser(getHeader.apply("Sec-Fetch-User"));
        // Note Jakarta uses javax SSLSession as well
        Object sslSessionAttr = getAttribute.apply("javax.servlet.request.ssl_session");
        if (sslSessionAttr instanceof javax.net.ssl.SSLSession) {
            javax.net.ssl.SSLSession sslSession = (javax.net.ssl.SSLSession) sslSessionAttr;
            data.setTlsCipher(sslSession.getProtocol());
            data.setTlsProto(sslSession.getCipherSuite());
        }
        Enumeration<String> headerNames = getHeaderNames.get();
        if (headerNames != null) {
            data.setHeaderNames(Collections.list(headerNames));
        }
        data.setCookieNames(getCookieNames.get());
        if (!umbrellaService.additionalHeadersToCollect().isEmpty()) {
            Map<String, String> additionalHeaders = new HashMap<>();
            data.additionalHeaders(additionalHeaders);
            umbrellaService.additionalHeadersToCollect().forEach(header -> {
                String value = getHeader.apply(header);
                if (value != null) {
                    additionalHeaders.put(header, value);
                }
            });
        }

        // Perform check
        HttpAction httpAction = umbrellaService.httpEvent(data);

        // Apply action
        if (httpAction.getRequestMetadata() != null) {
            httpAction.getRequestMetadata().forEach(setAttribute);
        }
        if (httpAction.getResponseHeaders() != null) {
            httpAction.getResponseHeaders().forEach(setHeader);
        }
        if (httpAction.getResponseCookies() != null) {
            httpAction.getResponseCookies().forEach(setCookie);
        }
        if (httpAction.getResponseStatus() != null) {
            setStatus.accept(httpAction.getResponseStatus().intValue());
        }

        // Continue processing if allowed
        return RequestProcess.ALLOW.equals(httpAction.getRequestProcess());
    }

    @Override
    public void destroy() {
        umbrellaService.shutdown();
    }

    private List<String> getServerIdentifierParts(
            Supplier<String> contextGetServletContextName,
            Supplier<String> contextGetServerInfo,
            Supplier<String> contextGetVirtualServerName
    ) {
        List<String> uniqueIdentifierParts = new ArrayList<>();

        // Host name of the server
        try {
            uniqueIdentifierParts.add(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ignored) {
        }

        // Display name of the web application as defined in web.xml (e.g. <display-name>MyApp</display-name>)
        String servletContextName = contextGetServletContextName.get();
        if (servletContextName != null) {
            uniqueIdentifierParts.add(servletContextName);
        }

        // Tomcat server info (e.g. Apache Tomcat/8.5.23)
        uniqueIdentifierParts.add(contextGetServerInfo.get());

        // Virtual server name (e.g. example.com)
        // Only available from Servlet API 3.1.
        try {
            uniqueIdentifierParts.add(contextGetVirtualServerName.get());
        } catch (NoSuchMethodError ignored) {
        }

        return uniqueIdentifierParts;
    }

    private Optional<String> getProperty(
            String nameFromFilter,
            String nameFromProperty,
            String nameFromEnv,
            Function<String, String> filterConfigGetInitParameter) {
        Optional<String> valueOpt = Optional.ofNullable(filterConfigGetInitParameter.apply(nameFromFilter))
                .filter(Predicate.not(String::isBlank));

        if (valueOpt.isEmpty()) {
            valueOpt = Optional.ofNullable(System.getProperty(nameFromProperty))
                    .filter(Predicate.not(String::isBlank));
        }

        if (valueOpt.isEmpty()) {
            valueOpt = Optional.ofNullable(System.getenv(nameFromEnv))
                    .filter(Predicate.not(String::isBlank));
        }

        return valueOpt;
    }
}
