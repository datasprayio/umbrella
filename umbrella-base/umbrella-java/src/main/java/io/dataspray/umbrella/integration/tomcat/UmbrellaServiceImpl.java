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

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.dataspray.umbrella.client.ApiClient;
import io.dataspray.umbrella.client.ApiException;
import io.dataspray.umbrella.client.HealthApi;
import io.dataspray.umbrella.client.IngestApi;
import io.dataspray.umbrella.client.JSON;
import io.dataspray.umbrella.client.model.Config;
import io.dataspray.umbrella.client.model.HttpAction;
import io.dataspray.umbrella.client.model.HttpEventRequest;
import io.dataspray.umbrella.client.model.HttpEventResponse;
import io.dataspray.umbrella.client.model.HttpMetadata;
import io.dataspray.umbrella.client.model.OperationMode;
import io.dataspray.umbrella.client.model.PingRequest;
import io.dataspray.umbrella.client.model.PingResponse;
import io.dataspray.umbrella.client.model.RequestProcess;
import okhttp3.OkHttpClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
    private String orgName;
    private HealthApi healthApi;
    private IngestApi ingestApi;
    private String nodeIdentifier;
    volatile Config config = new Config()
            .mode(OperationMode.DISABLED);
    /**
     * Used for:
     * - Background pinging
     * - Async events (in MONITOR mode)
     */
    ScheduledExecutorService executor;

    @Override
    public void init(
            String orgName,
            String apiKey,
            List<String> nodeIdentifierParts,
            Optional<String> endpointUrl) {

        this.orgName = orgName;
        this.nodeIdentifier = constructNodeIdentifier(nodeIdentifierParts);
        ApiClient apiClient = initApiClient(apiKey, endpointUrl);
        this.healthApi = new HealthApi(apiClient);
        this.ingestApi = new IngestApi(apiClient);

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

        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("Umbrella Service");
            thread.setDaemon(true);
            return thread;
        });
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

    private ApiClient initApiClient(String apiKey, Optional<String> endpointUrl) {
        // Add Gson adapter for Instant since we are using it instead of OffsetDateTime
        JSON.setGson(JSON.getGson().newBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeConverter())
                .create());
        ApiClient apiClient = new ApiClient();
        apiClient.setApiKeyPrefix("apikey");
        apiClient.setApiKey(apiKey);
        endpointUrl.ifPresent(apiClient::setBasePath);
        return apiClient;
    }

    @Override
    public HttpAction httpEvent(HttpMetadata data) {
        OperationMode currentMode = config.getMode();
        switch (currentMode) {
            case BLOCKING:
                try {
                    return doHttpEvent(data, currentMode).getAction();
                } catch (Exception ex) {
                    log.log(Level.SEVERE, "Failed to validate http event", ex);
                    return DEFAULT_ALLOW_ACTION;
                }
            case MONITOR:
                executor.execute(() -> {
                    try {
                        doHttpEvent(data, currentMode);
                    } catch (Exception ex) {
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
        if (this.executor != null) {
            this.executor.shutdown();
        }
    }

    private HttpEventResponse doHttpEvent(HttpMetadata data, OperationMode currentMode) throws ApiException {
        try {
            HttpEventResponse httpEventResponse = ingestApi.httpEvent(orgName, new HttpEventRequest()
                    .httpMetadata(data)
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
        PingResponse nodeInitializeResponse = healthApi.nodePing(orgName, new PingRequest()
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
            healthApi.getApiClient().setHttpClient(new OkHttpClient.Builder()
                    .callTimeout(callTimeout, TimeUnit.MILLISECONDS)
                    .build());
            ingestApi.getApiClient().setHttpClient(new OkHttpClient.Builder()
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
