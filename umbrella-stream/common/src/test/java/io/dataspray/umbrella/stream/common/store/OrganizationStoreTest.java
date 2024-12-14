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
import io.dataspray.umbrella.stream.common.AbstractDynamoTest;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Mode;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Rule;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.Optional;

import static io.dataspray.umbrella.stream.common.store.OrganizationStore.WEB_HTTP_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class OrganizationStoreTest extends AbstractDynamoTest {

    private OrganizationStore store;

    @BeforeEach
    void setUp() {
        store = new OrganizationStoreImpl(
                singleTable,
                dynamo
        );
    }

    @Test
    void testCreateDelete() {
        Organization org = store.create("my org");
        assertEquals(Optional.of(org), store.get("my org", false));
        store.delete("my org");
        assertEquals(Optional.empty(), store.get("my org", false));
    }

    @Test
    void testApiKeys() {
        store.create("my org");

        // Test create api key
        Organization org = store.createApiKeyForIngester("my org", "my api key", ImmutableSet.of());
        assertNotNull(org.getApiKeysByName().get("my api key"));
        assertTrue(org.getApiKeysByName().get("my api key").getEnabled());
        assertEquals(org.getApiKeysByName().get("my api key").getAllowedEventTypes(), ImmutableSet.of());

        // Test auth without event types
        String apiKeyValue = org.getApiKeysByName().get("my api key").getApiKeyValue();
        assertTrue(store.getIfAuthorizedForIngestPing("my org", apiKeyValue).isPresent());
        assertTrue(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "event type").isPresent());
        assertTrue(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "other event type").isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my other org", apiKeyValue, "event type").isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my org", "my other api key value", "event type").isPresent());

        // Test adding event types
        org = store.setApiKeyAllowedEventTypes("my org", "my api key", ImmutableSet.of("event type"));
        assertTrue(store.getIfAuthorizedForIngestPing("my org", apiKeyValue).isPresent());
        assertTrue(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "event type").isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "other event type").isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my other org", apiKeyValue, "event type").isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my org", "my other other api key value", "event type").isPresent());

        // Test enabling disabling
        org = store.setApiKeyEnabled("my org", "my api key", false);
        assertFalse(store.getIfAuthorizedForIngestPing("my org", apiKeyValue).isPresent());
        assertFalse(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "event type").isPresent());
        org = store.setApiKeyEnabled("my org", "my api key", true);
        assertTrue(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "event type").isPresent());
        assertTrue(store.getIfAuthorizedForIngestPing("my org", apiKeyValue).isPresent());

        // Test removing event types
        org = store.setApiKeyAllowedEventTypes("my org", "my api key", ImmutableSet.of());
        assertTrue(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "other event type").isPresent());

        // Test removing key
        org = store.removeApiKey("my org", "my api key");
        assertFalse(store.getIfAuthorizedForIngestEvent("my org", apiKeyValue, "event type").isPresent());
    }

    @Test
    void testMode() {
        Organization org = store.create("my org");
        assertEquals(OrganizationStore.DEFAULT_MODE, org.getMode());
        for (Mode mode : Mode.values()) {
            org = store.setMode("my org", mode);
            assertEquals(mode, org.getMode());
        }
    }

    @Test
    void testAwaitTimeout() {
        Organization org = store.create("my org");
        assertEquals(OrganizationStore.DEFAULT_AWAIT_TIMEOUT_MS, org.getAwaitTimeoutMs());
        org = store.setAwaitTimeoutMs("my org", 3_456);
        assertEquals(3_456, org.getAwaitTimeoutMs());
    }

    @Test
    void testCollectAdditionalHeaders() {
        Organization org = store.create("my org");
        assertEquals(ImmutableSet.of(), org.getCollectAdditionalHeaders());
        org = store.setCollectAdditionalHeaders("my org", ImmutableSet.of("Abc", "Def"));
        assertEquals(ImmutableSet.of("Abc", "Def"), org.getCollectAdditionalHeaders());
        org = store.setCollectAdditionalHeaders("my org", ImmutableSet.of());
        assertEquals(ImmutableSet.of(), org.getCollectAdditionalHeaders());
    }

    @Test
    void testMappers() {
        Organization org = store.create("my org");

        assertNull(org.getEndpointMapperSource());
        org = store.setEndpointMapperSource("my org", Optional.of("endpoint mapper source"));
        assertEquals("endpoint mapper source", org.getEndpointMapperSource());
        org = store.setEndpointMapperSource("my org", Optional.empty());
        assertNull(org.getEndpointMapperSource());

        assertNull(org.getKeyMapperSource());
        org = store.setKeyMapperSource("my org", Optional.of("key mapper source"));
        assertEquals("key mapper source", org.getKeyMapperSource());
        org = store.setKeyMapperSource("my org", Optional.empty());
        assertNull(org.getKeyMapperSource());
    }

    @Test
    void testRules() {
        Organization org = store.create("my org");
        assertEquals(ImmutableMap.of(), org.getRulesByName());
        Instant lastUpdatedAtCreation = org.getRulesLastUpdated();

        org = store.setRules("my org", ImmutableMap.of(
                        "my rule 1", Rule.builder()
                                .description("description 1")
                                .priority(42L)
                                .enabled(true)
                                .source("rule 1 source")
                                .eventTypes(ImmutableSet.of(WEB_HTTP_EVENT_TYPE, "custom"))
                                .build(),
                        "my rule 2", Rule.builder()
                                .description("description 2")
                                .priority(43L)
                                .enabled(true)
                                .source("rule 2 source")
                                .eventTypes(ImmutableSet.of("custom"))
                                .build()),
                Optional.of(org.getRulesLastUpdated()));
        assertTrue(org.getRulesLastUpdated().isAfter(lastUpdatedAtCreation));

        try {
            store.setRules("my org", ImmutableMap.of(), Optional.of(lastUpdatedAtCreation));
            fail();
        } catch (ConditionalCheckFailedException ignored) {
        }

        org = store.setRuleEnabled("my org", "my rule 1", false);
        assertFalse(org.getRulesByName().get("my rule 1").getEnabled());
        assertTrue(org.getRulesByName().get("my rule 2").getEnabled());
        org = store.setRuleEnabled("my org", "my rule 1", true);
        assertTrue(org.getRulesByName().get("my rule 1").getEnabled());

        org = store.setRules("my org", ImmutableMap.of(), Optional.empty());
        assertEquals(ImmutableMap.of(), org.getRulesByName());
    }
}