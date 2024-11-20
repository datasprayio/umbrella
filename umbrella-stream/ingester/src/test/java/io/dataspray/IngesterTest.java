package io.dataspray;

import io.dataspray.autogen.AbstractTest;
import io.dataspray.autogen.HttpEventRequest;
import io.dataspray.autogen.PingRequest;
import io.dataspray.autogen.TestCoordinator;
import io.dataspray.runner.dto.web.HttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class IngesterTest extends AbstractTest {

    @Test(timeout = 10_000)
    public void testWebNodePing() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webNodePing(
                Mockito.mock(PingRequest.class),
                "apikey someKey",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(200, response.getStatusCode());
        coordinator.assertSentNoneHttpEventRequest();
    }

    @Test(timeout = 10_000)
    public void testWebHttpEvent() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webHttpEvent(
                Mockito.mock(HttpEventRequest.class),
                "apikey someKey",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(200, response.getStatusCode());
        coordinator.assertSentNoneHttpEventRequest();
    }
}
