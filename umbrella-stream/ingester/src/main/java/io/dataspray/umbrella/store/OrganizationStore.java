package io.dataspray.umbrella.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.DynamoTable;
import io.dataspray.umbrella.autogen.Config;
import io.quarkus.runtime.annotations.RegisterForReflection;
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
    @RegisterForReflection
    class Organization {

        @NonNull
        @ToString.Exclude
        String apiKey;

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
