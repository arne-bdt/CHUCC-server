package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response DTO for dataset creation.
 * Returns information about the created dataset including the Kafka topic name.
 */
public record DatasetCreationResponse(
    String name,
    String description,
    String mainBranch,
    String initialCommitId,
    String kafkaTopic,
    @JsonProperty("createdAt") Instant timestamp
) {}
