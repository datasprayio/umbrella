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

package io.dataspray.umbrella.stream.common;

import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import io.dataspray.singletable.SingleTable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.UUID;

public class AbstractDynamoTest {

    protected static AmazonDynamoDBLocal dynamoServer;
    protected DynamoDbClient dynamo;
    protected SingleTable singleTable;

    @BeforeAll
    public static void beforeClassAbstractTest() {
        dynamoServer = DynamoDBEmbedded.create();
    }

    @BeforeEach
    public void beforeAbstractTest() {
        dynamo = dynamoServer.dynamoDbClient();
        singleTable = SingleTable.builder()
                .tableName(UUID.randomUUID().toString())
                .build();
        singleTable.createTableIfNotExists(
                dynamo,
                1,
                0);
    }

    @AfterAll
    public static void afterClassAbstractTest() {
        dynamoServer.shutdown();
    }
}
