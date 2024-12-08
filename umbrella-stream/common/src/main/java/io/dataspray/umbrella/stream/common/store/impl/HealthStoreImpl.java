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

package io.dataspray.umbrella.stream.common.store.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.ShardedIndexSchema;
import io.dataspray.singletable.ShardedTableSchema;
import io.dataspray.singletable.SingleTable;
import io.dataspray.umbrella.stream.common.store.HealthStore;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class HealthStoreImpl implements HealthStore {

    private static final Duration PING_TTL = Duration.ofDays(1);
    private final DynamoDbClient dynamo;
    private final ShardedTableSchema<NodeHealth> schemaHealthByOrg;
    private final ShardedIndexSchema<NodeHealth> schemaHealthAll;

    public HealthStoreImpl(SingleTable singleTable, DynamoDbClient dynamo) {
        this.dynamo = checkNotNull(dynamo);

        this.schemaHealthByOrg = singleTable.parseShardedTableSchema(NodeHealth.class);
        this.schemaHealthAll = singleTable.parseShardedGlobalSecondaryIndexSchema(1, NodeHealth.class);
    }

    @Override
    public PingResult ping(String organizationName, String nodeId) {
        NodeHealth current = new NodeHealth(organizationName, nodeId, Instant.now(),
                Instant.now().plus(PING_TTL).getEpochSecond());
        Optional<NodeHealth> previous = schemaHealthByOrg.put()
                .item(current)
                .executeGetPrevious(dynamo);
        return new PingResult(previous, current);
    }

    @Override
    public ImmutableSet<NodeHealth> listForOrg(String organizationName) {
        return deduplicateShardedStream(schemaHealthByOrg.querySharded()
                .keyConditionValues(ImmutableMap.of("organizationName", organizationName))
                .executeStream(dynamo));
    }

    @Override
    public ImmutableSet<NodeHealth> listAll() {
        return deduplicateShardedStream(schemaHealthAll.querySharded().executeStream(dynamo));
    }

    /**
     * Ensure a change in number of shards removes duplicates.
     * E.g. When shardCount=2, item A has shard=0, but when shardCount=20, item A has shard=14 so a duplicate is present
     */
    private ImmutableSet<NodeHealth> deduplicateShardedStream(Stream<NodeHealth> healths) {
        return ImmutableSet.copyOf(healths
                .collect(ImmutableMap.toImmutableMap(
                        NodeHealth::getId,
                        Function.identity(),
                        (left, right) -> left.getLastPing().isAfter(right.getLastPing()) ? left : right
                ))
                .values());
    }
}
