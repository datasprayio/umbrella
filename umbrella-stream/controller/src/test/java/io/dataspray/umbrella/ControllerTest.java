/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.umbrella;

import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.umbrella.autogen.AbstractTest;
import io.dataspray.umbrella.autogen.Rule;
import io.dataspray.umbrella.autogen.TestCoordinator;
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
