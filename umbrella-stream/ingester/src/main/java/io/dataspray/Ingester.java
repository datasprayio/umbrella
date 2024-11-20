package io.dataspray;

import io.dataspray.autogen.HttpEventRequest;
import io.dataspray.autogen.HttpEventResponse;
import io.dataspray.autogen.PingRequest;
import io.dataspray.autogen.PingResponse;
import io.dataspray.autogen.Processor;
import io.dataspray.autogen.WebCoordinator;
import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponse;
import jakarta.annotation.Nullable;

public class Ingester implements Processor {

    public HttpResponse webNodePing(
        PingRequest pingRequest,
        String authorizationHeader,
        HttpResponseBuilder<PingResponse> responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }

    public HttpResponse webHttpEvent(
        HttpEventRequest httpEventRequest,
        String authorizationHeader,
        HttpResponseBuilder<HttpEventResponse> responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }
}
