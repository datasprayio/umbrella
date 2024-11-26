package io.dataspray.umbrella.stream.common.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import io.dataspray.umbrella.autogen.Config;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.*;

import java.util.Optional;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface HealthStore {

    void ping(String organization, String nodeId);

    @Value
    @AllArgsConstructor
    @EqualsAndHashCode
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "organizationName", rangePrefix = "organization")
    class Organization {

        @NonNull
        String organizationName;

        @NonNull
        String nodeId;

        @NonNull
        String organizationName;

        Config.Mode mode;

        Long timeoutMs;

        ImmutableSet<String> collectAdditionalHeaders;

        String keyMapper;

        String endpointMapper;

        @NonNull
        ImmutableList<String> rules;
    }
}
