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
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.net.ssl.SSLSession;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UmbrellaFilterTest {

    private UmbrellaFilter umbrellaFilter;
    private UmbrellaService umbrellaService;

    @BeforeEach
    void setUp() {
        umbrellaService = mock(UmbrellaService.class);
        umbrellaFilter = new UmbrellaFilter(umbrellaService);
    }

    @Test
    void testInitNoApiKey() throws Exception {
        assertThrows(ServletException.class, () -> {
            init("org1", null, null, null);
        });
        verify(umbrellaService, times(0)).init(any(), any(), any(), any());
    }

    @Test
    void testInitNoOrg() throws Exception {
        assertThrows(ServletException.class, () -> {
            init(null, "apiKey", null, null);
        });
        verify(umbrellaService, times(0)).init(any(), any(), any(), any());
    }

    @Test
    void testInitDisabled() throws Exception {
        init("org1", "apikey", "false", null);
        assertFalse(umbrellaFilter.enabled);
        verify(umbrellaService, times(0)).init(any(), any(), any(), any());
    }

    @Test
    void testInitSuccess() throws Exception {
        init("org1", "apikey", "true", null);
        assertTrue(umbrellaFilter.enabled);
        verify(umbrellaService, times(1)).init(
                eq("org1"),
                eq("apikey"),
                eq(Arrays.asList(InetAddress.getLocalHost().getHostName(), "ServletContextName", "ServerInfo", "VirtualServerName")),
                eq(Optional.empty()));
    }

    @Test
    void testInitCustomEndpoint() throws Exception {
        init("org1", "apikey", null, "https://example.com");
        assertTrue(umbrellaFilter.enabled);
        verify(umbrellaService, times(1)).init(
                eq("org1"),
                eq("apikey"),
                eq(Arrays.asList(InetAddress.getLocalHost().getHostName(), "ServletContextName", "ServerInfo", "VirtualServerName")),
                eq(Optional.of("https://example.com")));
    }

    private void init(
            @Nullable String orgName,
            @Nullable String apiKey,
            @Nullable String enabled,
            @Nullable String endpointUrl
    ) throws ServletException {
        ServletContext servletContext = mock(ServletContext.class);
        when(servletContext.getServletContextName()).thenReturn("ServletContextName");
        when(servletContext.getServerInfo()).thenReturn("ServerInfo");
        when(servletContext.getVirtualServerName()).thenReturn("VirtualServerName");

        FilterConfig filterConfig = mock(FilterConfig.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(filterConfig.getInitParameter("org")).thenReturn(orgName);
        when(filterConfig.getInitParameter("api-key")).thenReturn(apiKey);
        when(filterConfig.getInitParameter("enabled")).thenReturn(enabled);
        when(filterConfig.getInitParameter("endpoint-url")).thenReturn(endpointUrl);

        umbrellaFilter.init(filterConfig);
    }

    @Test
    void testDoFilterDisabled() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        umbrellaFilter.enabled = false;
        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(umbrellaService, times(0)).httpEvent(any());
    }

    @Test
    void testDoFilterWrongRequestClass() throws Exception {
        ServletRequest request = mock(ServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(umbrellaService, times(0)).httpEvent(any());
    }

    @Test
    void testDoFilterWrongResponseClass() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(umbrellaService, times(0)).httpEvent(any());
    }

    @Test
    void testDoFilterAllEmpty() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.ALLOW));
        when(request.getRemotePort()).thenReturn(12343);
        when(request.getContentLengthLong()).thenReturn(543L);

        umbrellaFilter.doFilter(request, response, chain);

        ArgumentCaptor<HttpMetadata> dataCaptor = ArgumentCaptor.forClass(HttpMetadata.class);
        verify(umbrellaService, times(1)).httpEvent(dataCaptor.capture());
        verify(chain, times(1)).doFilter(eq(request), eq(response));

        assertEquals(12343L, dataCaptor.getValue().getPort());
        assertEquals(543L, dataCaptor.getValue().getContentLength());
    }

    @Test
    void testDoFilterAllowAppliesAction() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.ALLOW)
                .requestMetadata(Collections.singletonMap("attrK", "attrV"))
                .responseStatus(301L)
                .responseHeaders(Collections.singletonMap("headerName", "headerValue"))
                .responseCookies(Collections.singletonList(new Cookie()
                        .name("cookieName")
                        .value("cookieValue"))));
        when(request.getRemotePort()).thenReturn(12343);
        when(request.getContentLengthLong()).thenReturn(543L);

        umbrellaFilter.doFilter(request, response, chain);

        verify(umbrellaService, times(1)).httpEvent(any());
        verify(chain, times(1)).doFilter(eq(request), eq(response));

        verify(request, times(1)).setAttribute("attrK", "attrV");
        verify(response, times(1)).setStatus(301);
        verify(response, times(1)).addCookie(any());
        verify(response, times(1)).setHeader(eq("headerName"), eq("headerValue"));
    }

    @Test
    void testDoFilterBlockStopsChain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.BLOCK)
                .responseStatus(429L));

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response, times(1)).sendError(429);
    }

    @Test
    void testDoFilterBlockWithoutStatusSendsForbidden() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.BLOCK));

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(response, times(1)).sendError(403);
    }

    @Test
    void testDoFilterUnknownProcessFailsOpen() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction());

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(response, never()).sendError(anyInt());
    }

    @Test
    void testDoFilterCollectsTlsFromSslSession() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getCipherSuite()).thenReturn("TLS_AES_256_GCM_SHA384");
        when(sslSession.getProtocol()).thenReturn("TLSv1.3");
        when(request.getAttribute("javax.servlet.request.ssl_session")).thenReturn(sslSession);
        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.ALLOW));

        umbrellaFilter.doFilter(request, response, chain);

        ArgumentCaptor<HttpMetadata> dataCaptor = ArgumentCaptor.forClass(HttpMetadata.class);
        verify(umbrellaService, times(1)).httpEvent(dataCaptor.capture());
        assertEquals("TLS_AES_256_GCM_SHA384", dataCaptor.getValue().getTlsCipher());
        assertEquals("TLSv1.3", dataCaptor.getValue().getTlsProto());
    }

    @Test
    void testDoFilterRecordsSpentTime() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(umbrellaService.httpEvent(any())).thenReturn(new HttpAction()
                .requestProcess(RequestProcess.ALLOW));

        umbrellaFilter.doFilter(request, response, chain);

        verify(request, times(1)).setAttribute(eq(UmbrellaHttpExchange.ATTRIBUTE_SPENT_TIME_MS), any(Long.class));
    }

    @Test
    void testDoFilterSkipsExcludedPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/static/app.css");
        umbrellaFilter.requestFilterConfig = new RequestFilterConfig(
                Optional.empty(), Optional.of("\\.css$"), Optional.empty());

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(umbrellaService, times(0)).httpEvent(any());
    }

    @Test
    void testDoFilterSkipsTrustedIp() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/thing");
        when(request.getRemoteAddr()).thenReturn("10.1.2.3");
        umbrellaFilter.requestFilterConfig = new RequestFilterConfig(
                Optional.empty(), Optional.empty(), Optional.of("10.0.0.0/8"));

        umbrellaFilter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(eq(request), eq(response));
        verify(umbrellaService, times(0)).httpEvent(any());
    }

    @Test
    void testDestroy() throws Exception {
        umbrellaFilter.destroy();
        verify(umbrellaService, times(1)).shutdown();
    }
}