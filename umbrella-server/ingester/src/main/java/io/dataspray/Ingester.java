package io.dataspray;

import io.dataspray.autogen.IncomingRequest;
import io.dataspray.autogen.WebCoordinator;
import io.dataspray.runner.dto.web.HttpResponse;
import jakarta.annotation.Nullable;

public class Ingester implements Processor {

    public HttpResponse webHandleIncomingRequest(
        IncomingRequest incomingRequest,
        @Nullable String accountIdPath,
        HttpResponse.HttpResponseBuilder responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }
}
