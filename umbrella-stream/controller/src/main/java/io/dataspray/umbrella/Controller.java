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

package io.dataspray.umbrella;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.runner.DynamoProvider;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponseException;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;

public class Controller implements Processor {

    private final OrganizationStore organizationStore = new OrganizationStoreImpl(
            SingleTableProvider.get(),
            DynamoProvider.get());

    @Override
    public HttpResponse webOrgCreate(
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<ApiKey> responseBuilder,
            WebCoordinator coordinator
    ) {
        if (!organizationStore.checkIfAuthorizedForSuperAdmin(authorizationHeader)) {
            return responseBuilder.forbidden().build();
        }

        Organization organization = organizationStore.create(orgName);
        OrganizationStore.ApiKey apiKey = organizationStore.createApiKeyForAdmin(organization.getOrgName(), "default");

        return responseBuilder.ok(ApiKey.builder()
                .withApiKeyValue(apiKey.getApiKeyValue()).build()).build();
    }

    @Override
    public HttpResponse webApiKeyCreate(
            ApiKeyCreateRequest request,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<ApiKey> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        if (Type.ADMIN.equals(request.getType()) && request.getAllowedEventTypes().isPresent()) {
            return responseBuilder.statusCode(400).body("Admin API key cannot be restricted to event types").build();
        }

        OrganizationStore.ApiKey apiKey = switch (request.getType()) {
            case ADMIN -> organizationStore.createApiKeyForAdmin(
                    organization.getOrgName(), request.getName());
            case INGESTER -> organizationStore.createApiKeyForIngester(
                    organization.getOrgName(), request.getName(), request.getAllowedEventTypes().map(ImmutableSet::copyOf).orElse(ImmutableSet.of()));
        };


        return responseBuilder.ok(ApiKey.builder()
                .withApiKeyValue(apiKey.getApiKeyValue()).build()).build();
    }

    @Override
    public HttpResponse webApiKeyDelete(
            ApiKeyDeleteRequest request,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<Void> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        organizationStore.removeApiKey(organization.getOrgName(), request.getName());

        return responseBuilder.ok().build();
    }

    @Override
    public HttpResponse webRulesList(
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<Rules> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        return responseBuilder.ok(transform(organization.getRulesLastUpdated(), organization.getRulesByName())).build();
    }

    @Override
    public HttpResponse webRulesSet(
            Rules rules,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<Void> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        try {
            organizationStore.setRules(orgName, transform(rules), rules.getLastUpdated());
        } catch (ConditionalCheckFailedException ex) {
            return responseBuilder
                    .statusCode(409)
                    .body("Conflict: rules were updated by another request; expected lastUpdated to be " + organization.getRulesLastUpdated())
                    .build();
        }
        return responseBuilder.ok().build();
    }

    @VisibleForTesting
    Rules transform(Instant rulesLastUpdated, ImmutableMap<String, OrganizationStore.Rule> rulesByName) {
        return Rules.builder()
                .withLastUpdated(rulesLastUpdated)
                .withItems(rulesByName.entrySet().stream()
                        .map(e -> new Rule(
                                e.getKey(),
                                e.getValue().getDescription(),
                                e.getValue().getPriority(),
                                e.getValue().getEnabled(),
                                e.getValue().getSource(),
                                e.getValue().getEventTypes().asList()))
                        .collect(ImmutableList.toImmutableList()))
                .build();
    }

    @VisibleForTesting
    ImmutableMap<String, OrganizationStore.Rule> transform(Rules rules) {
        return rules.getItems().stream()
                .collect(ImmutableMap.toImmutableMap(
                        Rule::getName,
                        r -> new OrganizationStore.Rule(
                                r.getDescription().orElse(null),
                                r.getPriority(),
                                r.getEnabled(),
                                r.getSource(),
                                ImmutableSet.copyOf(r.getEventTypes()))));
    }
}
