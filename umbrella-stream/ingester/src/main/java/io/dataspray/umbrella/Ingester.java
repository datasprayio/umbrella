package io.dataspray.umbrella;

import com.google.common.base.Strings;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponseException;
import io.dataspray.umbrella.autogen.*;
import io.dataspray.umbrella.store.HealthStore;
import io.dataspray.umbrella.store.OrganizationStore;
import io.dataspray.umbrella.store.OrganizationStore.Organization;
import java.util.Optional;

public class Ingester implements Processor {

    OrganizationStore organizationStore;
    HealthStore healthStore;

    public Ingester() {
    }

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
                        .withMode(organization.getMode())
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
        // TODO

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
