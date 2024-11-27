package io.dataspray.umbrella;

import io.dataspray.runner.dto.web.HttpResponse.HttpResponseBuilder;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.umbrella.Processor;
import io.dataspray.umbrella.Rule;
import io.dataspray.umbrella.Rules;
import io.dataspray.umbrella.WebCoordinator;
import jakarta.annotation.Nullable;

public class Controller implements Processor {

    public HttpResponse webRuleList(
        String authorizationHeader,
        HttpResponseBuilder<Rules> responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }

    public HttpResponse webRuleSet(
        Rule rule,
        @Nullable String priorityPath,
        String authorizationHeader,
        HttpResponseBuilder<Object> responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }

    public HttpResponse webRuleDelete(
        String authorizationHeader,
        HttpResponseBuilder<Object> responseBuilder,
        WebCoordinator coordinator
    ) {

        // TODO
        return responseBuilder.ok().build();
    }
}
