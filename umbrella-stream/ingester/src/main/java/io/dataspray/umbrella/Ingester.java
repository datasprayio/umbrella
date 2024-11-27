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

import com.google.common.base.Strings;
import io.dataspray.runner.DynamoProvider;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponseException;
import io.dataspray.umbrella.stream.common.store.HealthStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.impl.HealthStoreImpl;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;

import java.util.Optional;

public class Ingester implements Processor {

    OrganizationStore organizationStore = new OrganizationStoreImpl(
            SingleTableProvider.get(),
            DynamoProvider.get());
    HealthStore healthStore = new HealthStoreImpl(
            SingleTableProvider.get(),
            DynamoProvider.get());

    public HttpResponse webNodePing(
            PingRequest pingRequest,
            String authorizationHeader,
            HttpResponseBuilder<PingResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = authorize(authorizationHeader, responseBuilder);

        healthStore.ping(organization.getOrganizationName(), pingRequest.getNodeId());

        return responseBuilder.ok(PingResponse.builder()
                .withConfig(Config.builder()
                        .withMode(Config.Mode.fromValue(organization.getMode().name()))
                        .build()).build()).build();
    }

    public HttpResponse webHttpEvent(
            HttpEventRequest httpEventRequest,
            String authorizationHeader,
            HttpResponseBuilder<HttpEventResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = authorize(authorizationHeader, responseBuilder);

        coordinator.sendToHttpEvents("", httpEventRequest);

        organization.getRules().forEach(rule -> {
            // TODO
        });

        return responseBuilder.ok(HttpEventResponse.builder()
                .withAction(Action.builder()
                        .withRequestProcess(Action.RequestProcess.ALLOW)
                        .build()).build()).build();
    }

    private Organization authorize(
            String authorizationHeader,
            HttpResponseBuilder<?> responseBuilder
    ) throws HttpResponseException {
        return Optional.ofNullable(Strings.emptyToNull(authorizationHeader))
                .filter(h -> h.startsWith("apikey "))
                .map(h -> h.substring("apikey ".length()))
                .flatMap(organizationStore::getByApiKey)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));
    }
}
