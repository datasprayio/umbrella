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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.runner.util.GsonUtil;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Rule;
import io.idml.*;
import io.idml.jackson.DefaultIdmlJackson;
import io.idml.lang.DocumentParseException;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Optional;

import static io.dataspray.umbrella.stream.common.store.OrganizationStore.WEB_HTTP_EVENT_TYPE;

@Slf4j
public class RuleRunnerImpl implements RuleRunner {

    private static final Response<Action> RESPONSE_WEB_DEFAULT = Response.<Action>builder()
            .keyOpt(Optional.empty())
            .action(Action.builder()
                    .withRequestProcess(RequestProcess.ALLOW)
                    .build())
            .build();
    private static final Response<ImmutableMap<String, String>> RESPONSE_CUSTOM_DEFAULT = Response.<ImmutableMap<String, String>>builder()
            .keyOpt(Optional.empty())
            .action(ImmutableMap.of())
            .build();
    private static final Duration COMPILED_CACHE_TTL = Duration.ofMinutes(5);
    private final Cache<String, CompiledRules> compiledCache = CacheBuilder.newBuilder()
            .expireAfterAccess(COMPILED_CACHE_TTL)
            .build();
    private final IdmlJson idmlJson = new DefaultIdmlJackson();
    private final Idml idml = Idml.staticBuilderWithDefaults(idmlJson).build();
    private final IdmlUtil idmlUtil = new IdmlUtil();

    @Override
    public Response<Action> run(Organization org, HttpMetadata httpMetadata) {
        return runInternal(org, WEB_HTTP_EVENT_TYPE, httpMetadata)
                .map(output -> Response.<Action>builder()
                        .keyOpt(output.getKey())
                        .action(Action.builder()
                                .withRequestProcess("block".equalsIgnoreCase(idmlUtil.optionOrNull(output.getOut().get("process").toStringOption()))
                                        ? RequestProcess.BLOCK : RequestProcess.ALLOW)
                                .withResponseStatus(idmlUtil.<Long>optionOrNull(output.getOut().get("status").toLongOption()))
                                .withResponseHeaders(idmlUtil.parseMap(output.getOut().get("headers"), IdmlValue::toStringValue))
                                .withRequestMetadata(idmlUtil.parseMap(output.getOut().get("metadata"), IdmlValue::toStringValue))
                                .withResponseCookies(idmlUtil.parseList(output.getOut().get("cookies"), value -> new Cookie(
                                        value.get("name").toStringValue(),
                                        value.get("value").toStringValue(),
                                        idmlUtil.<Long>optionOrNull(value.get("maxAge").toLongOption()),
                                        idmlUtil.optionOrNull(value.get("domain").toStringOption()),
                                        idmlUtil.optionOrNull(value.get("path").toStringOption()),
                                        idmlUtil.<Boolean>optionOrNull(value.get("secure").toBoolOption()),
                                        idmlUtil.<Boolean>optionOrNull(value.get("httpOnly").toBoolOption()),
                                        idmlUtil.optionOrNull(value.get("sameSite").toStringOption())
                                )))
                                .build())
                        .build())
                .orElse(RESPONSE_WEB_DEFAULT);
    }

    @Override
    public Response<ImmutableMap<String, String>> run(Organization org, ImmutableMap<String, String> metadata, String eventType) {
        return runInternal(org, eventType, metadata)
                .map(output -> Response.<ImmutableMap<String, String>>builder()
                        .keyOpt(output.getKey())
                        .action(idmlUtil.parseMap(output.getOut(), IdmlValue::toStringValue))
                        .build())
                .orElse(RESPONSE_CUSTOM_DEFAULT);
    }

    @SneakyThrows
    private Optional<RuleOutput> runInternal(Organization org, String eventType, Object event) {

        if (org.getRulesByName().isEmpty()) {
            log.info("No rules to evaluate");
            return Optional.empty();
        }

        CompiledRules compiledRules = compile(org);

        RuleInput input = buildInput(eventType, event);
        Optional<RuleOutput> output = Optional.empty();
        for (CompiledRule rule : compiledRules.getRules()) {

            if (!rule.isEnabled()) {
                log.debug("Skipping rule {}, disabled", rule.getRuleName());
                continue;
            }

            if (!rule.getEventTypes().contains(eventType)) {
                log.debug("Skipping rule {}, unrelated event {}", rule.getRuleName(), eventType);
                continue;
            }

            // Compile
            Mapping mapping;
            try {
                mapping = rule.getMapping();
            } catch (Exception ex) {
                log.error("Error compiling rule {}", rule.getRuleName(), ex);
                continue;
            }

            // Parse input
            IdmlValue inputRaw = parseInput(input);

            // Evaluate
            IdmlObject outputRaw;
            try {
                outputRaw = mapping.run(inputRaw);
            } catch (Exception ex) {
                log.error("Error evaluating rule {}", rule.getRuleName(), ex);
                continue;
            }
            log.debug("Executed rule {}", rule.getRuleName());
            log.trace("Output of rule {}: {}", rule.getRuleName(), outputRaw);

            // Parse output
            try {
                output = Optional.of(parseOutput(outputRaw, output));
            } catch (Exception ex) {
                log.error("Error parsing output for rule {}", rule.getRuleName(), ex);
                continue;
            }

            if (output.get().isStop()) {
                log.debug("Stopping after rule {} signalled stop", rule.getRuleName());
                break;
            }

            // Build next input
            input = buildInput(input, output.get());
        }

        log.debug("All rules executed");
        return output;
    }

    @SneakyThrows
    private RuleInput buildInput(String eventType, Object event) {
        return new RuleInput(
                eventType,
                event,
                null,
                null);
    }

    @SneakyThrows
    private RuleInput buildInput(RuleInput previousInput, RuleOutput previousOutput) {
        return previousInput.toBuilder()
                .state(previousOutput.getState())
                .out(previousOutput.getOut())
                .build();
    }


    @SneakyThrows
    private IdmlValue parseInput(RuleInput ruleInput) {
        // TODO: extend IDML to convert Java object to IDML object
        return idmlJson.parseObject(GsonUtil.get().toJson(ruleInput));
    }

    @SneakyThrows
    private RuleOutput parseOutput(IdmlValue output, Optional<RuleOutput> previousOutput) {
        return new RuleOutput(
                output.get("state"),
                output.get("out"),
                output.get("stop").isTrueValue(),
                idmlUtil.<String>optionToOptional(output.get("key").toStringOption())
                        .or(() -> previousOutput.flatMap(RuleOutput::getKey)));
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
                .sorted(Comparator.<Entry<String, Rule>>comparingLong(e -> e.getValue().getPriority()).reversed())
                .map(e -> new CompiledRule(
                        e.getValue().getEnabled(),
                        e.getValue().getEventTypes(),
                        e.getKey(),
                        e.getValue().getSource()))
                .collect(ImmutableList.toImmutableList());
    }

    private Mapping compile(String source) {
        try {
            return idml.compile(source);
        } catch (DocumentParseException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Value
    static class CompiledRules {
        Instant rulesLastUpdated;
        String orgName;
        ImmutableList<CompiledRule> rules;
    }

    @Value
    class CompiledRule {
        boolean enabled;
        ImmutableSet<String> eventTypes;
        String ruleName;
        String source;
        @Getter(lazy = true)
        Mapping mapping = compile(source);
    }

    @Value
    @Builder(toBuilder = true)
    static class RuleInput {

        /**
         * Http type {@code WEB_HTTP_EVENT_TYPE} or custom type.
         */
        @NonNull
        String eventType;

        /**
         * Event metadata {@link HttpMetadata} or {@link ImmutableMap}{@code <String, String>}.
         */
        @NonNull
        Object event;

        /**
         * State from previous rule
         */
        IdmlValue state;

        /**
         * Out result from previous rule
         */
        Object out;
    }

    @Value
    static class RuleOutput {
        /**
         * If set, overwrites state in next rule. Otherwise previous state is passed along.
         */
        IdmlValue state;

        /**
         * Out result will be passed either to next rule or returned to caller.
         */
        IdmlValue out;

        /**
         * Whether to stop processing next rules.
         * Default is false.
         */
        boolean stop;

        /**
         * Override key for this event.
         * This should be a user identifier such as an IP address, username, email.
         */
        Optional<String> key;
    }
}
