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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.dataspray.runner.DynamoProvider;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponseException;
import io.dataspray.stream.ingest.client.ApiException;
import io.dataspray.umbrella.RuleRunner.Response;
import io.dataspray.umbrella.stream.common.store.HealthStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.impl.HealthStoreImpl;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;

import java.util.Optional;

import static io.dataspray.umbrella.stream.common.store.OrganizationStore.WEB_HTTP_EVENT_TYPE;

public class Ingester implements Processor {

    private final OrganizationStore organizationStore = new OrganizationStoreImpl(
            SingleTableProvider.get(),
            DynamoProvider.get());
    private final HealthStore healthStore = new HealthStoreImpl(
            SingleTableProvider.get(),
            DynamoProvider.get());
    private final RuleRunner ruleRunner = new RuleRunnerImpl();

    public HttpResponse webNodePing(
            PingRequest pingRequest,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<PingResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForIngestPing(orgName, authorizationHeader)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        healthStore.ping(organization.getOrgName(), pingRequest.getNodeId());

        return responseBuilder.ok(PingResponse.builder()
                        .withConfig(Config.builder()
                                .withMode(OperationMode.fromValue(organization.getMode().name()))
                                .build())
                        .build())
                .build();
    }

    public HttpResponse webHttpEvent(
            HttpEventRequest httpEventRequest,
            String orgName,
            String authorizationHeader,
            HttpResponseBuilder<HttpEventResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForIngestEvent(orgName, authorizationHeader, WEB_HTTP_EVENT_TYPE)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        Response<Action> response = ruleRunner.run(organization, httpEventRequest.getHttpMetadata());

        try {
            coordinator.sendToHttpEvents(
                    response.getKeyOpt().orElseGet(() -> extractIp(httpEventRequest)),
                    new HttpEvent(httpEventRequest, response.getAction()));
        } catch (Exception ex) {
            if (ex.getCause() instanceof ApiException
                    && ((ApiException) ex.getCause()).getCode() == 429) {
                return responseBuilder
                        .statusCode(429)
                        .body("Too many requests, please slow down")
                        .build();
            }
            throw ex;
        }

        return responseBuilder.ok(HttpEventResponse.builder()
                        .withAction(response.getAction())
                        // TODO only send if config is changed, need to receive last updated time from client
                        .withConfigRefresh(Config.builder()
                                .withMode(OperationMode.fromValue(organization.getMode().name()))
                                .build())
                        .build())
                .build();
    }

    @Override
    public HttpResponse webEvent(
            EventRequest eventRequest,
            String orgName,
            String eventType,
            String authorizationHeader,
            HttpResponseBuilder<EventResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Organization organization = organizationStore.getIfAuthorizedForIngestEvent(orgName, authorizationHeader, eventType)
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));

        Response<ImmutableMap<String, String>> response = ruleRunner.run(organization, eventRequest.getMetadata(), eventType);

        coordinator.sendToCustomEvents(
                response.getKeyOpt().orElse(eventRequest.getKey()),
                new CustomEvent(eventRequest, response.getAction()));

        return responseBuilder.ok(EventResponse.builder()
                        .withAction(response.getAction())
                        // TODO only send if config is changed, need to receive last updated time from client
                        .withConfigRefresh(Config.builder()
                                .withMode(OperationMode.fromValue(organization.getMode().name()))
                                .build())
                        .build())
                .build();
    }

    private Organization authorize(
            String orgName,
            String authorizationHeader,
            Optional<String> eventTypeOpt,
            HttpResponseBuilder<?> responseBuilder
    ) throws HttpResponseException {
        return Optional.ofNullable(Strings.emptyToNull(authorizationHeader))
                .filter(h -> h.startsWith("apikey "))
                .map(h -> h.substring("apikey ".length()))
                .flatMap(apiKeyValue -> eventTypeOpt.isPresent()
                        ? organizationStore.getIfAuthorizedForIngestEvent(orgName, apiKeyValue, eventTypeOpt.get())
                        : organizationStore.getIfAuthorizedForIngestPing(orgName, apiKeyValue))
                .orElseThrow(() -> new HttpResponseException(responseBuilder.forbidden().build()));
    }

    private String extractIp(HttpEventRequest request) {
        HttpMetadata metadata = request.getHttpMetadata();
        return metadata.getIp()
                .or(metadata::gethXRealIp)
                .or(metadata::gethFwd)
                .or(metadata::gethXFwdFor)
                .or(metadata::gethCfConnIp)
                .or(metadata::gethTrueClientIp)
                .orElseGet(request::getNodeId);
    }
}
