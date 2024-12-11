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

import io.dataspray.umbrella.client.JSON;
import io.dataspray.umbrella.client.model.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class UmbrellaServiceTest {

    private UmbrellaServiceImpl umbrellaService;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        umbrellaService = (UmbrellaServiceImpl) UmbrellaService.create();
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testInitShutdown() throws Exception {
        mockPingServerEndpoint(OperationMode.BLOCKING, 3000L);
        umbrellaService.init(
                "org_name",
                "api_key",
                Collections.singletonList("nodeIdentifier"),
                Optional.of(mockWebServer.url("/").toString()));
        assertFalse(umbrellaService.executor.isShutdown());
        assertEquals(OperationMode.BLOCKING, umbrellaService.config.getMode());
        assertEquals(3000L, umbrellaService.config.getTimeoutMs());

        umbrellaService.shutdown();
        assertTrue(umbrellaService.executor.isShutdown());
    }

    @Test
    void testHttpEventBlock() throws Exception {
        mockPingServerEndpoint(OperationMode.BLOCKING, 3000L);
        umbrellaService.init(
                "org_name",
                "api_key",
                Collections.singletonList("nodeIdentifier"),
                Optional.of(mockWebServer.url("/").toString()));
        HttpAction actionExpected = new HttpAction()
                .requestProcess(RequestProcess.BLOCK)
                .requestMetadata(Collections.singletonMap("attrKey", "attrVal"));
        mockHttpEventEndpoint(actionExpected, OperationMode.MONITOR, 0L);

        HttpAction actionActual = umbrellaService.httpEvent(new HttpMetadata());

        assertEquals(actionExpected, actionActual);
        assertEquals(OperationMode.MONITOR, umbrellaService.config.getMode());
    }

    @Test
    void testHttpEventBlockTimeout() throws Exception {
        mockPingServerEndpoint(OperationMode.BLOCKING, 200L);
        umbrellaService.init(
                "org_name",
                "api_key",
                Collections.singletonList("nodeIdentifier"),
                Optional.of(mockWebServer.url("/").toString()));
        HttpAction actionReturned = new HttpAction()
                .requestProcess(RequestProcess.BLOCK);
        mockHttpEventEndpoint(actionReturned, OperationMode.MONITOR, 300L);

        HttpAction actionActual = umbrellaService.httpEvent(new HttpMetadata());

        assertEquals(UmbrellaServiceImpl.DEFAULT_ALLOW_ACTION, actionActual);
        assertEquals(OperationMode.BLOCKING, umbrellaService.config.getMode());
    }

    @Test
    void testHttpEventMonitor() throws Exception {
        mockPingServerEndpoint(OperationMode.MONITOR, 1L);
        umbrellaService.init(
                "org_name",
                "api_key",
                Collections.singletonList("nodeIdentifier"),
                Optional.of(mockWebServer.url("/").toString()));
        HttpAction actionReturned = new HttpAction()
                .requestProcess(RequestProcess.BLOCK);
        mockHttpEventEndpoint(actionReturned, OperationMode.BLOCKING, 100L);

        HttpAction actionActual = umbrellaService.httpEvent(new HttpMetadata());

        assertEquals(UmbrellaServiceImpl.DEFAULT_ALLOW_ACTION, actionActual);
        assertEquals(OperationMode.MONITOR, umbrellaService.config.getMode());
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(OperationMode.BLOCKING, umbrellaService.config.getMode()));
    }

    @Test
    void testHttpEventDisabled() throws Exception {
        mockPingServerEndpoint(OperationMode.DISABLED, 3000L);
        umbrellaService.init(
                "org_name",
                "api_key",
                Collections.singletonList("nodeIdentifier"),
                Optional.of(mockWebServer.url("/").toString()));
        HttpAction actionExpected = UmbrellaServiceImpl.DEFAULT_ALLOW_ACTION;

        HttpAction actionActual = umbrellaService.httpEvent(new HttpMetadata());

        assertEquals(actionExpected, actionActual);
        assertEquals(OperationMode.DISABLED, umbrellaService.config.getMode());
    }

    private void mockPingServerEndpoint(OperationMode mode, long timeoutMs) {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(JSON.getGson().toJson(new PingResponse()
                        .config(new Config()
                                .mode(mode)
                                .timeoutMs(timeoutMs)))));
    }

    private void mockHttpEventEndpoint(HttpAction returnAction, OperationMode newMode, long latency) {
        mockWebServer.enqueue(new MockResponse()
                .setBodyDelay(latency, TimeUnit.MILLISECONDS)
                .setResponseCode(200)
                .setBody(JSON.getGson().toJson(new HttpEventResponse()
                        .action(returnAction)
                        .configRefresh(new Config()
                                .mode(newMode)))));
    }
}
