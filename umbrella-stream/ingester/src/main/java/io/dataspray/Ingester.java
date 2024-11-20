package io.dataspray;

import io.dataspray.autogen.*;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;

import java.util.Optional;

public class Ingester implements Processor {

    public HttpResponse webNodePing(
            PingRequest pingRequest,
            String authorizationHeader,
            HttpResponseBuilder<PingResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Optional<HttpResponse> authorizeResult = authorize(authorizationHeader, responseBuilder);
        if (authorizeResult.isPresent()) {
            return authorizeResult.get();
        }

        // TODO

        return responseBuilder
                .ok(PingResponse.builder()
                        .withConfig(Config.builder()
                                .withMode(Config.Mode.MONITOR)
                                .build())
                        .build())
                .build();
    }

    public HttpResponse webHttpEvent(
            HttpEventRequest httpEventRequest,
            String authorizationHeader,
            HttpResponseBuilder<HttpEventResponse> responseBuilder,
            WebCoordinator coordinator
    ) {
        Optional<HttpResponse> authorizeResult = authorize(authorizationHeader, responseBuilder);
        if (authorizeResult.isPresent()) {
            return authorizeResult.get();
        }

        // TODO

        return responseBuilder
                .ok(HttpEventResponse.builder()
                        .withAction(Action.builder()
                                .withRequestProcess(Action.RequestProcess.ALLOW)
                                .build())
                        .build())
                .build();
    }

    private Optional<HttpResponse> authorize(
            String authorizationHeader,
            HttpResponseBuilder<?> responseBuilder
    ) {
        if (authorizationHeader == null
                || !authorizationHeader.substring(0, "apikey ".length()).equalsIgnoreCase("apikey ")) {
            return Optional.of(responseBuilder.forbidden().build());
        }

        // TODO

        return Optional.empty();
    }
}
