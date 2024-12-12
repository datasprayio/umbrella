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

import com.google.common.collect.ImmutableMap;
import io.dataspray.umbrella.stream.common.store.OrganizationStore.Organization;
import lombok.Builder;
import lombok.Value;

import java.util.Optional;

public interface RuleRunner {

    /**
     * Run rules against a Http event.
     *
     * @return Action to be taken in response to the rules.
     */
    Response<Action> run(Organization org, HttpMetadata httpMetadata);

    /**
     * Run rules against a custom event.
     *
     * @return Custom response to be handled by caller.
     */
    Response<ImmutableMap<String, String>> run(Organization org, ImmutableMap<String, String> metadata, String eventType);

    @Value
    @Builder
    class Response<T> {
        Optional<String> keyOpt;
        T action;
    }
}
