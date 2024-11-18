package io.dataspray;

import io.dataspray.autogen.AbstractTest;
import io.dataspray.autogen.IncomingRequest;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.MockHttpRequest;
import org.junit.Assert;
import org.junit.Test;

public class IngesterTest extends AbstractTest {

    @Test(timeout = 10_000)
    public void testWebHandleIncomingRequest() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webHandleIncomingRequest(
                Mockito.mock(IncomingRequest.class),
            null,
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
        coordinator.assertSentNoneIncomingRequest();
    }
}
