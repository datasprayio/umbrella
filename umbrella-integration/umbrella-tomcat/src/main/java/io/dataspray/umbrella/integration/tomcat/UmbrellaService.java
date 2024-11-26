package io.dataspray.umbrella.integration.tomcat;

import io.dataspray.umbrella.client.ApiException;
import io.dataspray.umbrella.client.model.HttpAction;
import io.dataspray.umbrella.client.model.HttpData;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface UmbrellaService {

    static UmbrellaService create() {
        return new UmbrellaServiceImpl();
    }

    void init(
            String apiKey,
            List<String> nodeIdentifierParts,
            Optional<String> endpointUrl);

    List<String> additionalHeadersToCollect();

    HttpAction httpEvent(HttpData data);

    void shutdown();
}
