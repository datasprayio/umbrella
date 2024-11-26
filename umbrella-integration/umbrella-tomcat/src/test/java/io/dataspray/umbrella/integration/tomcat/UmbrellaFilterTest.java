package io.dataspray.umbrella.integration.tomcat;

import io.dataspray.umbrella.client.model.Cookie;
import io.dataspray.umbrella.client.model.HttpAction;
import io.dataspray.umbrella.client.model.HttpData;
import io.dataspray.umbrella.client.model.RequestProcess;
import jakarta.annotation.Nullable;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
            init(null, null, null);
        });
        verify(umbrellaService, times(0)).init(any(), any(), any());
    }

    @Test
    void testInitDisabled() throws Exception {
        init("apikey", "false", null);
        assertFalse(umbrellaFilter.enabled);
        verify(umbrellaService, times(0)).init(any(), any(), any());
    }

    @Test
    void testInitSuccess() throws Exception {
        init("apikey", "true", null);
        assertTrue(umbrellaFilter.enabled);
        verify(umbrellaService, times(1)).init(
                eq("apikey"),
                eq(Arrays.asList(InetAddress.getLocalHost().getHostName(), "ServletContextName", "ServerInfo", "VirtualServerName")),
                eq(Optional.empty()));
    }

    @Test
    void testInitCustomEndpoint() throws Exception {
        init("apikey", null, "https://example.com");
        assertTrue(umbrellaFilter.enabled);
        verify(umbrellaService, times(1)).init(
                eq("apikey"),
                eq(Arrays.asList(InetAddress.getLocalHost().getHostName(), "ServletContextName", "ServerInfo", "VirtualServerName")),
                eq(Optional.of("https://example.com")));
    }

    private void init(
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
        when(filterConfig.getInitParameter("apiKey")).thenReturn(apiKey);
        when(filterConfig.getInitParameter("enabled")).thenReturn(enabled);
        when(filterConfig.getInitParameter("endpointUrl")).thenReturn(endpointUrl);

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

        ArgumentCaptor<HttpData> dataCaptor = ArgumentCaptor.forClass(HttpData.class);
        verify(umbrellaService, times(1)).httpEvent(dataCaptor.capture());
        verify(chain, times(1)).doFilter(eq(request), eq(response));

        assertEquals(12343L, dataCaptor.getValue().getPort());
        assertEquals(543L, dataCaptor.getValue().getContentLength());
    }

    @Test
    void testDoFilterBlock() throws Exception {
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
        verify(response, times(1)).addCookie(eq(new jakarta.servlet.http.Cookie("cookieName", "cookieValue")));
        verify(response, times(1)).setHeader(eq("headerName"), eq("headerValue"));
    }

    @Test
    void testDestroy() throws Exception {
        umbrellaFilter.destroy();
        verify(umbrellaService, times(1)).shutdown();
    }
}