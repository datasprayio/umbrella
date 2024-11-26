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

package io.dataspray.umbrella.stream.common.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import lombok.*;

import java.util.Optional;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface OrganizationStore {

    Optional<Organization> getByApiKey(String apiKey);

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "organizationName", rangePrefix = "organization")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = "apiKey", rangePrefix = "organizationByApiKey")
    class Organization {

        @NonNull
        @ToString.Exclude
        String apiKey;

        @NonNull
        String organizationName;

        Mode mode;

        Long timeoutMs;

        ImmutableSet<String> collectAdditionalHeaders;

        String keyMapper;

        String endpointMapper;

        @NonNull
        ImmutableList<String> rules;
    }

    /**
     * Matches enum io.dataspray.umbrella.autogen.Config.Mode.
     */
    enum Mode {
        BLOCKING,
        MONITOR,
        DISABLED
    }
}
