package io.dataspray.umbrella;

import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.dto.web.MockHttpRequest;
import io.dataspray.umbrella.AbstractTest;
import io.dataspray.umbrella.Rule;
import io.dataspray.umbrella.Rules;
import io.dataspray.umbrella.TestCoordinator;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class ControllerTest extends AbstractTest {

    @Test(timeout = 10_000)
    public void testWebRuleList() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webRuleList(
                "authorizationHeader",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
    }

    @Test(timeout = 10_000)
    public void testWebRuleSet() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webRuleSet(
                Mockito.mock(Rule.class),
            null,
            "authorizationHeader",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
    }

    @Test(timeout = 10_000)
    public void testWebRuleDelete() {

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpResponse response = processor.webRuleDelete(
                "authorizationHeader",
                HttpResponse.builder(),
                coordinator);

        Assert.assertEquals(204, response.getStatusCode());
    }
}
