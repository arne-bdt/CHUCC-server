package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * DTO for commit metadata response.
 * Contains core commit information without branches/tags (use /version/refs for that).
 * Commits are immutable, making this response perfect for HTTP caching.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Commit metadata including message, author, timestamp, parents, "
    + "and patch size")
public record CommitMetadataDto(
    @Schema(description = "Commit ID (UUIDv7)", example = "01933e4a-9d4e-7000-8000-000000000003")
    String id,

    @Schema(description = "Commit message", example = "Add new feature")
    String message,

    @Schema(description = "Author in Git format", example = "Alice <alice@example.org>")
    String author,

    @Schema(description = "Commit timestamp (ISO 8601)", example = "2025-10-24T15:30:00Z")
    Instant timestamp,

    @Schema(description = "List of parent commit IDs (empty for initial commit)")
    List<String> parents,

    @Schema(description = "Number of RDF operations in patch (for monitoring/optimization)",
        example = "42")
    Integer patchSize
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param id commit ID
   * @param message commit message
   * @param author commit author
   * @param timestamp commit timestamp
   * @param parents list of parent commit IDs
   * @param patchSize patch size
   */
  public CommitMetadataDto {
    parents = parents != null ? List.copyOf(parents) : List.of();
  }
}
