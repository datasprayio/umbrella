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

import com.google.gson.*;
import io.dataspray.umbrella.client.ApiClient;
import io.dataspray.umbrella.client.ApiException;
import io.dataspray.umbrella.client.DefaultApi;
import io.dataspray.umbrella.client.JSON;
import io.dataspray.umbrella.client.model.*;
import okhttp3.OkHttpClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class UmbrellaServiceImpl implements UmbrellaService {

    private static final Logger log = Logger.getLogger(UmbrellaServiceImpl.class.getCanonicalName());
    private static final long PING_INTERVAL_MINUTES = 10L;
    static final HttpAction DEFAULT_ALLOW_ACTION = new HttpAction()
            .requestProcess(RequestProcess.ALLOW);
    private DefaultApi api;
    private String nodeIdentifier;
    volatile Config config = new Config()
            .mode(OperationMode.DISABLED);
    ScheduledExecutorService executor;

    @Override
    public void init(
            String apiKey,
            List<String> nodeIdentifierParts,
            Optional<String> endpointUrl) {

        this.nodeIdentifier = constructNodeIdentifier(nodeIdentifierParts);
        this.api = initApi(apiKey, endpointUrl);

        try {
            doPing();
        } catch (ApiException ex) {
            if (ex.getCode() == 403) {
                log.log(Level.SEVERE, "Api key is invalid, continuing with Umbrella Filter disabled", ex);
                return;
            } else {
                log.log(Level.SEVERE, "Failed to initialize Umbrella, continuing with Umbrella Filter disabled but will retry later", ex);
            }
        }

        // Background pinging
        this.executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                doPing();
            } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to ping Umbrella", ex);
            }
        }, PING_INTERVAL_MINUTES, PING_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public List<String> additionalHeadersToCollect() {
        return config.getCollectAdditionalHeaders() == null ? Collections.emptyList() : config.getCollectAdditionalHeaders();
    }

    private DefaultApi initApi(String apiKey, Optional<String> endpointUrl) {
        // Add Gson adapter for Instant since we are using it instead of OffsetDateTime
        JSON.setGson(JSON.getGson().newBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                .create());
        ApiClient apiClient = new ApiClient();
        apiClient.setApiKeyPrefix("apikey");
        apiClient.setApiKey(apiKey);
        endpointUrl.ifPresent(apiClient::setBasePath);
        return new DefaultApi(apiClient);
    }

    @Override
    public HttpAction httpEvent(HttpData data) {
        OperationMode currentMode = config.getMode();
        switch (currentMode) {
            case BLOCKING:
                try {
                    return doHttpEvent(data, currentMode).getAction();
                } catch (ApiException ex) {
                    log.log(Level.SEVERE, "Failed to validate http event", ex);
                    return DEFAULT_ALLOW_ACTION;
                }
            case MONITOR:
                executor.execute(() -> {
                    try {
                        onNewConfig(api.httpEvent(new HttpEventRequest()
                                .data(data)
                                .nodeId(this.nodeIdentifier)
                                .currentMode(currentMode)));
                    } catch (ApiException ex) {
                        log.log(Level.WARNING, "Failed to publish http event", ex);
                    }
                });
                return DEFAULT_ALLOW_ACTION;
            case DISABLED:
            default:
                return DEFAULT_ALLOW_ACTION;
        }
    }

    @Override
    public void shutdown() {
        this.executor.shutdown();
    }

    private HttpEventResponse doHttpEvent(HttpData data, OperationMode currentMode) throws ApiException {
        try {
            HttpEventResponse httpEventResponse = api.httpEvent(new HttpEventRequest()
                    .data(data)
                    .nodeId(nodeIdentifier)
                    .currentMode(currentMode));
            onNewConfig(httpEventResponse);
            return httpEventResponse;
        } catch (ApiException exception) {
            if (exception.getCode() == 429) {
                log.log(Level.SEVERE, "Rate limited by Umbrella, disabling mode until next ping");
                config.setMode(OperationMode.DISABLED);
            }
            throw exception;
        }
    }

    private void doPing() throws ApiException {
        PingResponse nodeInitializeResponse = api.nodePing(new PingRequest()
                .nodeId(this.nodeIdentifier));
        log.log(Level.FINEST, "Successfully pinged Umbrella");
        onNewConfig(nodeInitializeResponse.getConfig());
    }

    private void onNewConfig(HttpEventResponse response) {
        if (response.getConfigRefresh() != null) {
            onNewConfig(response.getConfigRefresh());
        }
    }

    private void onNewConfig(Config newConfig) {
        if (!Objects.equals(config.getTimeoutMs(), newConfig.getTimeoutMs())) {
            // Call timeout only set if requested and in blocking mode
            long callTimeout = newConfig.getTimeoutMs() == null || newConfig.getMode() != OperationMode.BLOCKING
                    ? 0L
                    : newConfig.getTimeoutMs();
            api.getApiClient().setHttpClient(new OkHttpClient.Builder()
                    .callTimeout(callTimeout, TimeUnit.MILLISECONDS)
                    .build());
        }
        config = newConfig;
    }

    private String constructNodeIdentifier(List<String> nodeIdentifierParts) {
        return Stream.concat(
                        nodeIdentifierParts.stream(),
                        Stream.of("sid=" + UUID.randomUUID()))
                .collect(Collectors.joining("; "));
    }

    private static class InstantTypeConverter
            implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type srcType, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            return Instant.parse(json.getAsString());
        }
    }
}
