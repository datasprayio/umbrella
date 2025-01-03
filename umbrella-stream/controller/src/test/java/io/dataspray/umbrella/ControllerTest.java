/*
 * Copyright 2025 Matus Faro
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.runner.DynamoProvider;
import io.dataspray.runner.dto.web.HttpResponse;
import io.dataspray.runner.util.GsonUtil;
import io.dataspray.umbrella.stream.common.store.OrganizationStore;
import io.dataspray.umbrella.stream.common.store.impl.OrganizationStoreImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

public class ControllerTest extends AbstractTest {

    OrganizationStore organizationStore;
    OrganizationStore.Organization org;
    OrganizationStore.ApiKey apiKey;

    @BeforeEach
    public void setUp() {
        organizationStore = new OrganizationStoreImpl(
                SingleTableProvider.get(),
                DynamoProvider.get());

        org = organizationStore.create("org1");
        apiKey = organizationStore.createApiKeyForAdmin(org.getOrgName(), "apikey1");
    }

    @SuppressWarnings("unused")
    private static final List<Arguments> testWebRuleList = List.of(
            argumentSet("empty", ImmutableMap.of()),
            argumentSet("full", ImmutableMap.of(
                    "rule 1", new OrganizationStore.Rule(
                            "some description",
                            231412214L,
                            false,
                            "gdfagfda",
                            ImmutableSet.of()),
                    "rule 2", new OrganizationStore.Rule(
                            null,
                            123L,
                            true,
                            "asdf",
                            ImmutableSet.of("adsfasf"))))
    );

    @ParameterizedTest
    @FieldSource
    public void testWebRuleList(ImmutableMap<String, OrganizationStore.Rule> rulesByName) {
        organizationStore.setRules(org.getOrgName(), rulesByName, Optional.empty());
        TestCoordinator coordinator = TestCoordinator.createForWeb();

        HttpResponse response = processor.webRulesList(
                org.getOrgName(),
                "apikey " + apiKey.getApiKeyValue(),
                HttpResponse.builder(),
                coordinator);

        assertEquals(200, response.getStatusCode());
        Rules actualRules = GsonUtil.get().fromJson(response.getBody(), Rules.class);
        ImmutableMap<String, OrganizationStore.Rule> actualRulesByName = ((Controller) processor).transform(actualRules);
        assertEquals(rulesByName, actualRulesByName);
    }

    @Test
    public void testWebRuleSet() {
        ImmutableMap<String, OrganizationStore.Rule> rulesByName = ImmutableMap.of(
                "rule 1", new OrganizationStore.Rule(
                        "some description",
                        231412214L,
                        false,
                        "gdfagfda",
                        ImmutableSet.of()),
                "rule 2", new OrganizationStore.Rule(
                        null,
                        123L,
                        true,
                        "asdf",
                        ImmutableSet.of("adsfasf")));
        Rules rules = ((Controller) processor).transform(org.getRulesLastUpdated(), rulesByName);
        TestCoordinator coordinator = TestCoordinator.createForWeb();

        HttpResponse response = processor.webRulesSet(
                rules,
                org.getOrgName(),
                "apikey " + apiKey.getApiKeyValue(),
                HttpResponse.builder(),
                coordinator);

        assertEquals(204, response.getStatusCode());
        assertEquals(rulesByName, organizationStore.get(org.getOrgName(), false).orElseThrow().getRulesByName());
    }
}
