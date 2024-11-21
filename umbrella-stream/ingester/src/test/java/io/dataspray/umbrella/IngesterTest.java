package io.dataspray.umbrella;

import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.MockHttpRequest;
import io.dataspray.umbrella.autogen.AbstractTest;
import io.dataspray.umbrella.autogen.HttpEventRequest;
import io.dataspray.umbrella.autogen.HttpEventResponse;
import io.dataspray.umbrella.autogen.PingRequest;
import io.dataspray.umbrella.autogen.PingResponse;
import io.dataspray.umbrella.autogen.TestCoordinator;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class IngesterTest extends AbstractTest {

    @Test(timeout = 10_000)
    public void testWebNodePing() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webNodePing(
                Mockito.mock(PingRequest.class),
            "authorizationHeader",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
        coordinator.assertSentNoneHttpEventRequest();
    }

    @Test(timeout = 10_000)
    public void testWebHttpEvent() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webHttpEvent(
                Mockito.mock(HttpEventRequest.class),
            "authorizationHeader",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
        coordinator.assertSentNoneHttpEventRequest();
    }
}
