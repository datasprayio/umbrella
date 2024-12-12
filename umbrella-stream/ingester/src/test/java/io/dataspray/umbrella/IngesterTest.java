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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.runner.DynamoProvider;
import io.dataspray.runner.Message;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.util.GsonUtil;
import io.dataspray.umbrella.stream.common.store.HealthStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.ApiKey;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Rule;
import io.dataspray.umbrella.stream.common.store.impl.HealthStoreImpl;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.dataspray.umbrella.stream.common.store.OrganizationStore.WEB_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

public class IngesterTest extends AbstractTest {

    OrganizationStore organizationStore;
    Organization org;
    ApiKey apiKey;

    HealthStore healthStore;
    RuleRunner ruleRunner;

    @BeforeEach
    public void setUp() {
        organizationStore = new OrganizationStoreImpl(
                SingleTableProvider.get(),
                DynamoProvider.get());
        healthStore = new HealthStoreImpl(
                SingleTableProvider.get(),
                DynamoProvider.get());
        ruleRunner = new RuleRunnerImpl();

        org = organizationStore.create("org1");
        apiKey = organizationStore.createApiKeyForIngester(org.getOrgName(), "apikey1", ImmutableSet.of())
                .getApiKeysByName().get("apikey1");
    }

    @Test
    public void testWebNodePing() {
        TestCoordinator coordinator = TestCoordinator.createForWeb();
        String nodeId = "node1";
        HttpResponse response = processor.webNodePing(
                PingRequest.builder()
                        .withNodeId(nodeId)
                        .build(),
                org.getOrgName(),
                "apikey " + apiKey.getApiKeyValue(),
                HttpResponse.builder(),
                coordinator);

        assertEquals(200, response.getStatusCode());
        assertEquals(Set.of(nodeId), healthStore.listForOrg(org.getOrgName()).stream()
                .map(HealthStore.NodeHealth::getId)
                .collect(Collectors.toSet()));
    }

    private static final List<Arguments> testWebHttpEvent = List.of(
            argumentSet("allow", "out.process = 'ALLOW'", true, RequestProcess.ALLOW),
            argumentSet("block", "out.process = 'BLOCK'", true, RequestProcess.BLOCK)
    );

    @ParameterizedTest(name = "testWebHttpEvent ''{0}''")
    @FieldSource
    public void testWebHttpEvent(String source, boolean ruleEnabled, RequestProcess expectRequestProcess) {
        organizationStore.setRules(org.getOrgName(), Strings.isNullOrEmpty(source)
                ? ImmutableMap.of()
                : ImmutableMap.of(
                "rule1", Rule.builder()
                        .source(source)
                        .priority(100L)
                        .description("my rule")
                        .enabled(ruleEnabled)
                        .eventTypes(ImmutableSet.of(WEB_EVENT_TYPE))
                        .build()
        ));

        TestCoordinator coordinator = TestCoordinator.createForWeb();
        HttpEventRequest request = HttpEventRequest.builder()
                .withNodeId("node1")
                .withHttpMetadata(HttpMetadata.builder().withIp("127.0.0.1").build())
                .withCurrentMode(OperationMode.BLOCKING)
                .build();
        HttpResponse response = processor.webHttpEvent(
                request,
                org.getOrgName(),
                "apikey " + apiKey.getApiKeyValue(),
                HttpResponse.builder(),
                coordinator);

        assertEquals(200, response.getStatusCode());
        assertEquals(List.of(request), coordinator.getSentHttpEvent().stream().map(Message::getData).map(HttpEvent::getRequest).toList());
        HttpEventResponse httpEventResponse = GsonUtil.get().fromJson(response.getBody(), HttpEventResponse.class);
        assertEquals(expectRequestProcess, httpEventResponse.getAction().getRequestProcess());

        coordinator.assertSentNoneCustomEvent();
    }
}
