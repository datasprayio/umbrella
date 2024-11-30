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

package io.dataspray.umbrella.stream.common.store.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.singletable.builder.UpdateBuilder;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.util.KeygenUtil;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class OrganizationStoreImpl implements OrganizationStore {

    private final DynamoDbClient dynamo;
    private final TableSchema<Organization> schemaOrganization;

    public OrganizationStoreImpl(SingleTable singleTable, DynamoDbClient dynamo) {
        this.dynamo = checkNotNull(dynamo);

        this.schemaOrganization = singleTable.parseTableSchema(Organization.class);
    }

    @Override
    public Organization create(String orgName) {
        log.info("Creating new organization: {}", orgName);
        return schemaOrganization.put()
                .conditionNotExists()
                .item(new Organization(
                        orgName,
                        ImmutableMap.of(),
                        ImmutableMap.of(),
                        Instant.now(),
                        DEFAULT_MODE,
                        DEFAULT_AWAIT_TIMEOUT_MS,
                        ImmutableSet.of(),
                        null,
                        null
                ))
                .executeGetNew(dynamo);
    }

    @Override
    public Optional<Organization> get(String orgName) {
        return schemaOrganization.get()
                .key(ImmutableMap.of("orgName", orgName))
                .executeGet(dynamo);
    }

    @Override
    public Optional<Organization> getIfAuthorized(String orgName, String apiKeyValue, String eventType) {
        return get(orgName).filter(org -> org.getApiKeysByName().values().stream()
                .anyMatch(apiKey ->
                        Boolean.TRUE.equals(apiKey.getEnabled())
                                && apiKey.getApiKeyValue().equals(apiKeyValue)
                                && (apiKey.getAllowedEventTypes().isEmpty()
                                || apiKey.getAllowedEventTypes().contains(eventType))));
    }

    @Override
    public Organization setMode(String orgName, Mode mode) {
        log.info("Changing mode to {} for {}", mode, orgName);
        return schemaOrganization.update()
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .set("mode", mode)
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setAwaitTimeoutMs(String orgName, long timeoutMs) {
        log.info("Changing await timeout to {} for {}", timeoutMs, orgName);
        return schemaOrganization.update()
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .set("awaitTimeoutMs", timeoutMs)
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setCollectAdditionalHeaders(String orgName, ImmutableSet<String> collectAdditionalHeaders) {
        log.info("Changing header collection to {} for {}", collectAdditionalHeaders, orgName);
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        if (collectAdditionalHeaders.isEmpty()) {
            updateBuilder.remove("collectAdditionalHeaders");
        } else {
            updateBuilder.set("collectAdditionalHeaders", collectAdditionalHeaders);
        }
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setKeyMapperSource(String orgName, Optional<String> keyMapperSource) {
        log.info("Changing key mapper for {}", orgName);
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        if (keyMapperSource.isPresent()) {
            updateBuilder.set("keyMapperSource", keyMapperSource.get());
        } else {
            updateBuilder.remove("keyMapperSource");
        }
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setEndpointMapperSource(String orgName, Optional<String> endpointMapperSource) {
        log.info("Changing endpoint mapper for {}", orgName);
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        if (endpointMapperSource.isPresent()) {
            updateBuilder.set("endpointMapperSource", endpointMapperSource.get());
        } else {
            updateBuilder.remove("endpointMapperSource");
        }
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization createApiKey(String orgName, String apiKeyName, ImmutableSet<String> allowedEventTypes) {
        log.info("Creating api key {} for {}", apiKeyName, orgName);
        checkArgument(!apiKeyName.isEmpty(), "apiKeyName cannot be empty");
        apiKeyName = apiKeyName.replace("[^a-zA-Z]", "_");
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .set(ImmutableList.of("apiKeysByName", apiKeyName),
                        new ApiKey(
                                KeygenUtil.generateSecureApiKey(),
                                true,
                                ImmutableSet.of()))
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization removeApiKey(String orgName, String apiKeyName) {
        log.info("Removing api key {} for {}", apiKeyName, orgName);
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .remove(ImmutableList.of("apiKeysByName", apiKeyName))
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setApiKeyEnabled(String orgName, String apiKeyName, boolean enabled) {
        log.info("{} api key {} for {}", enabled ? "Enabling" : "Disabling", apiKeyName, orgName);
        return updateApiKey(orgName, apiKeyName, Optional.of(enabled), Optional.empty());
    }

    @Override
    public Organization setApiKeyAllowedEventTypes(String orgName, String apiKeyName, ImmutableSet<String> allowedEventTypes) {
        log.info("Changing allowed event types to {} api key {} org {}", allowedEventTypes, apiKeyName, orgName);
        return updateApiKey(orgName, apiKeyName, Optional.empty(), Optional.of(allowedEventTypes));
    }

    private Organization updateApiKey(String orgName, String apiKeyName, Optional<Boolean> booleanOpt, Optional<ImmutableSet<String>> allowedEventTypesOpt) {
        Optional<Organization> organizationOpt = get(orgName);
        if (!organizationOpt.isPresent()) {
            throw new IllegalArgumentException("Cannot update api key " + apiKeyName + " for org " + orgName + " as the org does not exist");
        }
        Organization organization = organizationOpt.get();
        if (!booleanOpt.isPresent() && !allowedEventTypesOpt.isPresent()) {
            return organization;
        }
        ApiKey.ApiKeyBuilder apiKeyBuilder = checkNotNull(organization.getApiKeysByName().get(apiKeyName), "Cannot update api key " + apiKeyName + " for org " + orgName + " as the api key does not exist")
                .toBuilder();
        booleanOpt.ifPresent(apiKeyBuilder::enabled);
        allowedEventTypesOpt.ifPresent(apiKeyBuilder::allowedEventTypes);
        return schemaOrganization.update()
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .set(ImmutableList.of("apiKeysByName", apiKeyName), apiKeyBuilder.build())
                .executeGetUpdated(dynamo);
    }

    @Override
    public void delete(String orgName) {
        schemaOrganization.delete()
                .key(ImmutableMap.of("orgName", orgName))
                .execute(dynamo);
    }

    @Override
    public Organization setRules(String orgName, ImmutableMap<String, Rule> rulesByName, Instant expectedLastUpdated) {
        log.info("Changing rules for {}", orgName);
        UpdateBuilder<Organization> updateBuilder = schemaOrganization.update();
        if (rulesByName.isEmpty()) {
            updateBuilder.remove("rulesByName");
        } else {
            updateBuilder.set("rulesByName", rulesByName);
        }
        return updateBuilder
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .conditionFieldEquals("rulesLastUpdated", expectedLastUpdated)
                .set("rulesLastUpdated", Instant.now())
                .executeGetUpdated(dynamo);
    }

    @Override
    public Organization setRuleEnabled(String orgName, String ruleName, boolean enabled) {
        log.info("{} rule {} org {}", enabled ? "Enabling" : "Disabling", ruleName, orgName);
        Optional<Organization> organizationOpt = get(orgName);
        if (!organizationOpt.isPresent()) {
            throw new IllegalArgumentException("Cannot update api key " + ruleName + " for org " + orgName + " as the org does not exist");
        }
        Organization organization = organizationOpt.get();
        Rule rule = checkNotNull(organization.getRulesByName().get(ruleName), "Cannot update rule " + ruleName + " for org " + orgName + " as the rule does not exist");
        if (rule.getEnabled() == enabled) {
            return organization;
        }
        Rule updatedRule = rule.toBuilder().enabled(enabled).build();
        return schemaOrganization.update()
                .key(ImmutableMap.of("orgName", orgName))
                .conditionExists()
                .set(ImmutableList.of("rulesByName", ruleName), updatedRule)
                .set("rulesLastUpdated", Instant.now())
                .executeGetUpdated(dynamo);
    }
}
