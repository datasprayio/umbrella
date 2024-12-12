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

package io.dataspray.umbrella;

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

    public HttpResponse webRulesList(
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<Rules> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        // TODO
        return responseBuilder.ok(responseBuilder.ok(Rules.builder()
                .with
                .build())).build();
    }

    public HttpResponse webRulesSet(
            Rules rules,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<Object> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForAdmin(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        try {
            organizationStore.setRules(orgName, rules., rules.getLastUpdated());
        } catch (ConditionalCheckFailedException ex) {
            return responseBuilder
                    .statusCode(409)
                    .body("Conflict: rules were updated by another request; expected lastUpdated to be " + organization.getRulesLastUpdated())
                    .build();
        }
        return responseBuilder.ok().build();
    }
}
