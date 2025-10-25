package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Tag information DTO.
 * Used for both tag list responses and tag creation responses.
 * Tags are immutable once created, making this response suitable for HTTP caching.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Tag metadata including name, target commit, message, author, and timestamp")
public record TagInfo(
    @Schema(description = "Tag name", example = "v1.0.0")
    String name,

    @Schema(description = "Target commit ID (UUIDv7)",
        example = "01933e4a-9d4e-7000-8000-000000000003")
    String target,

    @Schema(description = "Optional annotation message", example = "Release version 1.0.0")
    String message,

    @Schema(description = "Author in Git format", example = "Alice <alice@example.org>")
    String author,

    @Schema(description = "Tag creation timestamp (ISO 8601)", example = "2025-10-24T15:30:00Z")
    Instant createdAt
) {
}
