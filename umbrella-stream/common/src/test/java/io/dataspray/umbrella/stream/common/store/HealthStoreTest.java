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

import com.google.common.collect.ImmutableSet;
import io.dataspray.umbrella.stream.common.AbstractDynamoTest;
import io.dataspray.umbrella.stream.common.store.impl.HealthStoreImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class HealthStoreTest extends AbstractDynamoTest {

    private HealthStore store;

    @BeforeEach
    void setUp() {
        store = new HealthStoreImpl(
                singleTable,
                dynamo
        );
    }

    @Test
    void testSameNode() {
        HealthStore.PingResult result1 = store.ping("my org 1", "node 1");
        assertFalse(result1.getPrevious().isPresent());

        HealthStore.PingResult result2 = store.ping("my org 1", "node 1");
        assertTrue(result2.getPrevious().isPresent());
        assertEquals(result1.getCurrent(), result2.getPrevious().get());
        assertTrue(result1.getCurrent().getLastPing().isBefore(result2.getCurrent().getLastPing()));

        HealthStore.PingResult result3 = store.ping("my org 1", "node 1");
        assertTrue(result3.getPrevious().isPresent());
        assertEquals(result2.getCurrent(), result3.getPrevious().get());
    }

    @Test
    void testDifferentNodes() {
        HealthStore.PingResult result1 = store.ping("my org 1", "node 1");
        assertFalse(result1.getPrevious().isPresent());

        HealthStore.PingResult result2 = store.ping("my org 1", "node 2");
        assertFalse(result2.getPrevious().isPresent());

        HealthStore.PingResult result3 = store.ping("my org 2", "node 3");
        assertFalse(result3.getPrevious().isPresent());

    }

    @Test
    void testList() {
        store.ping("my org 1", "node 1");
        store.ping("my org 1", "node 1");
        HealthStore.PingResult result1 = store.ping("my org 1", "node 1");
        HealthStore.PingResult result2 = store.ping("my org 1", "node 2");
        HealthStore.PingResult result3 = store.ping("my org 2", "node 3");

        assertEquals(ImmutableSet.of("node 1", "node 2"), ImmutableSet.copyOf(store.listForOrg("my org 1").stream().map(HealthStore.NodeHealth::getId).collect(Collectors.toSet())));
        assertEquals(ImmutableSet.of("node 3"), ImmutableSet.copyOf(store.listForOrg("my org 2").stream().map(HealthStore.NodeHealth::getId).collect(Collectors.toSet())));
        assertEquals(ImmutableSet.of(), ImmutableSet.copyOf(store.listForOrg("my org 3").stream().map(HealthStore.NodeHealth::getId).collect(Collectors.toSet())));

        assertEquals(ImmutableSet.of("node 1", "node 2", "node 3"), ImmutableSet.copyOf(store.listAll().stream().map(HealthStore.NodeHealth::getId).collect(Collectors.toSet())));
    }
}
