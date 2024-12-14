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

package io.dataspray.umbrella.stream.common.store;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import lombok.*;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

import static io.dataspray.singletable.TableType.Primary;

public interface OrganizationStore {

    String WEB_HTTP_EVENT_TYPE = "web";
    Mode DEFAULT_MODE = Mode.MONITOR;
    long DEFAULT_AWAIT_TIMEOUT_MS = 2_000;

    Organization create(String orgName);

    Optional<Organization> get(String orgName, boolean useCache);

    Optional<Organization> getIfAuthorizedForIngestPing(String orgName, @Nullable String apiKeyValueOrAuthHeader);

    Optional<Organization> getIfAuthorizedForIngestEvent(String orgName, @Nullable String apiKeyValueOrAuthHeader, String eventType);

    Optional<Organization> getIfAuthorizedForAdmin(String orgName, @Nullable String apiKeyValueOrAuthHeader);

    Organization setMode(String orgName, Mode mode);

    Organization setAwaitTimeoutMs(String orgName, long timeoutMs);

    Organization setCollectAdditionalHeaders(String orgName, ImmutableSet<String> collectAdditionalHeaders);

    Organization setKeyMapperSource(String orgName, Optional<String> keyMapperSource);

    Organization setEndpointMapperSource(String orgName, Optional<String> endpointMapperSource);

    Organization createApiKeyForAdmin(String orgName, String apiKeyName);

    Organization createApiKeyForIngester(String orgName, String apiKeyName, ImmutableSet<String> allowedEventTypes);

    Organization removeApiKey(String orgName, String apiKeyName);

    Organization setApiKeyEnabled(String orgName, String apiKeyName, boolean enabled);

    Organization setApiKeyAllowedEventTypes(String orgName, String apiKeyName, ImmutableSet<String> allowedEventTypes);

    void delete(String orgName);

    Organization setRules(String orgName, ImmutableMap<String, Rule> rulesByName, Optional<Instant> expectedLastUpdatedOpt);

    Organization setRuleEnabled(String orgName, String ruleName, boolean enabled) throws ConditionalCheckFailedException;

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "orgName", rangePrefix = "org")
    class Organization {

        @NonNull
        String orgName;

        @NonNull
        ImmutableMap<String, ApiKey> apiKeysByName;

        @NonNull
        ImmutableMap<String, Rule> rulesByName;

        @NonNull
        Instant rulesLastUpdated;

        @NonNull
        Mode mode;

        @NonNull
        Long awaitTimeoutMs;

        @NonNull
        ImmutableSet<String> collectAdditionalHeaders;

        String keyMapperSource;

        String endpointMapperSource;
    }

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    class Rule {

        String description;

        @NonNull
        Long priority;

        @NonNull
        Boolean enabled;

        @NonNull
        String source;

        @NonNull
        ImmutableSet<String> eventTypes;
    }

    /**
     * Matches enum {@code OperationMode}.
     */
    enum Mode {
        BLOCKING,
        MONITOR,
        DISABLED
    }

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    class ApiKey {

        @NonNull
        @ToString.Exclude
        String apiKeyValue;

        @NonNull
        Boolean enabled;

        @NonNull
        Boolean isAdmin;

        /**
         * Access to emit specific events. If empty, has access to emit all.
         */
        @NonNull
        ImmutableSet<String> allowedEventTypes;
    }
}
