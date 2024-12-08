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

import com.google.common.base.Enums;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.runner.util.GsonUtil;
import io.dataspray.umbrella.Action.RequestProcess;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Rule;
import io.idml.Idml;
import io.idml.IdmlJson;
import io.idml.IdmlObject;
import io.idml.Mapping;
import io.idml.jackson.DefaultIdmlJackson;
import io.idml.lang.DocumentParseException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map.Entry;

@Slf4j
public class RuleRunner {

    private static final Duration COMPILED_CACHE_TTL = Duration.ofMinutes(5);
    private final Cache<String, CompiledRules> compiledCache = CacheBuilder.newBuilder()
            .expireAfterAccess(COMPILED_CACHE_TTL)
            .build();
    private final IdmlJson idmlJson = new DefaultIdmlJackson();
    private final Idml idml = Idml.staticBuilderWithDefaults(idmlJson).build();

    @SneakyThrows
    Action run(Organization org, Object event) {

        // TODO: extend IDML to convert Java object to IDML object
        IdmlObject idmlObject = idmlJson.parseObject(GsonUtil.get().toJson(event));

        CompiledRules compiledRules = compile(org);
        if (compiledRules.getRules().isEmpty()) {
            log.info("No rules to evaluate");
            return Action.builder()
                    .withRequestProcess(RequestProcess.ALLOW)
                    .build();
        }

        RuleResult result = RuleResult.ALLOW;
        for (CompiledRule rule : compiledRules.getRules()) {

            // Evaluate rule
            IdmlObject resultObj;
            try {
                resultObj = rule.getMapping()
                        .run(idmlObject);
            } catch (Exception ex) {
                log.error("Error evaluating rule {}", rule.getRuleName(), ex);
                continue;
            }

            // Extract result
            // TODO tag along custom headers, status code, etc... from resultobj
            result = Enums.getIfPresent(RuleResult.class,
                            resultObj
                                    .get("action")
                                    .toStringValue()
                                    .toUpperCase())
                    .or(RuleResult.ALLOW);
            log.info("Evaluated rule {} with {}", rule.getRuleName(), result);

            // For block, stop processing
            if (result == RuleResult.BLOCK) {
                break;
            }
        }
        return Action.builder()
                .withRequestProcess(result.getRequestProcess())
                .build();
    }

    @Getter
    @AllArgsConstructor
    enum RuleResult {
        ALLOW(RequestProcess.ALLOW),
        BLOCK(RequestProcess.BLOCK);
        private final RequestProcess requestProcess;
    }

    private CompiledRules compile(Organization org) {
        CompiledRules compiledRules = compiledCache.getIfPresent(org.getOrgName());
        if (compiledRules != null && compiledRules.getRulesLastUpdated().equals(org.getRulesLastUpdated())) {
            return compiledRules;
        }

        compiledRules = new CompiledRules(
                org.getRulesLastUpdated(),
                org.getOrgName(),
                compile(org.getRulesByName()));
        compiledCache.put(org.getOrgName(), compiledRules);
        return compiledRules;
    }

    private ImmutableList<CompiledRule> compile(ImmutableMap<String, Rule> rulesByName) {
        return rulesByName.entrySet().stream()
                .filter(e -> e.getValue().getEnabled())
                .sorted(Comparator.<Entry<String, Rule>>comparingInt(e -> e.getValue().getPriority()).reversed())
                .map(e -> {
                    Mapping mapping;
                    try {
                        mapping = idml.compile(e.getValue().getSource());
                    } catch (DocumentParseException ex) {
                        throw new RuntimeException(ex);
                    }
                    return new CompiledRule(
                            e.getKey(),
                            mapping);
                })
                .collect(ImmutableList.toImmutableList());
    }

    @Value
    static class CompiledRules {
        Instant rulesLastUpdated;
        String orgName;
        ImmutableList<CompiledRule> rules;
    }

    @Value
    static class CompiledRule {
        String ruleName;
        Mapping mapping;
    }
}
