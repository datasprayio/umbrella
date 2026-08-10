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
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

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

        // Enabled property
        enabled = getProperty("enabled", "umbrella.enabled", "UMBRELLA_ENABLED", filterConfig)
                .map(enabledStr -> !"false".equalsIgnoreCase(enabledStr) && !"0".equals(enabledStr))
                .orElse(true);
        if (!enabled) {
            log.log(Level.INFO, "Umbrella Filter is disabled via configuration");
            return;
        }

        // Organization key property
        String orgName = getProperty("org", "umbrella.org", "UMBRELLA_ORG", filterConfig)
                .orElseThrow(() -> new ServletException("Umbrella Organization name property is missing"));

        // Api key property
        String apiKey = getProperty("api-key", "umbrella.api.key", "UMBRELLA_API_KEY", filterConfig)
                .orElseThrow(() -> new ServletException("Umbrella API key property is missing"));

        // Endpoint URL property
        Optional<String> endpointUrlOpt = getProperty("endpoint-url", "umbrella.endpoint.url", "UMBRELLA_ENDPOINT_URL", filterConfig);
        endpointUrlOpt.ifPresent(endpointUrl -> log.log(Level.INFO, "Umbrella using endpoint: {0}", endpointUrl));

        umbrellaService.init(
                orgName,
                apiKey,
                getServerIdentifierParts(filterConfig.getServletContext()),
                endpointUrlOpt);

        log.log(Level.INFO, "Umbrella enabled successfully");
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
            log.log(Level.FINE, "Skipping non-HTTP response");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;

        ServletExchange.Request request = new ServletExchange.Request(httpServletRequest);
        ServletExchange.Response response = new ServletExchange.Response(httpServletResponse);

        long startedNs = System.nanoTime();
        HttpMetadata data = UmbrellaHttpExchange.collect(request, umbrellaService.additionalHeadersToCollect());
        HttpAction httpAction = umbrellaService.httpEvent(data);
        httpServletRequest.setAttribute(
                UmbrellaHttpExchange.ATTRIBUTE_SPENT_TIME_MS,
                (System.nanoTime() - startedNs) / 1_000_000L);

        if (UmbrellaHttpExchange.applyAndShouldContinue(httpAction, request, response)) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {
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

    private Optional<String> getProperty(
            String nameFromFilter,
            String nameFromProperty,
            String nameFromEnv,
            FilterConfig filterConfig) {
        Optional<String> valueOpt = Optional.ofNullable(filterConfig.getInitParameter(nameFromFilter))
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
