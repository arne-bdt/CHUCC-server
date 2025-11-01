package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Minimal commit metadata for history listings.
 * Contains essential information about a commit without the full patch data.
 */
@Schema(
    description = "Minimal commit metadata for history listings",
    example = "{\"id\":\"01933e4a-9d4e-7000-8000-000000000003\","
        + "\"message\":\"Add new feature\","
        + "\"author\":\"Alice <alice@example.org>\","
        + "\"timestamp\":\"2025-10-24T15:30:00Z\","
        + "\"parents\":[\"01933e4a-8c3d-7000-8000-000000000002\"],"
        + "\"patchSize\":42}"
)
public record CommitHistoryInfo(
    @Schema(
        description = "Commit ID (UUIDv7)",
        example = "01933e4a-9d4e-7000-8000-000000000003",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String id,

    @Schema(
        description = "Commit message describing the changes",
        example = "Add new feature",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String message,

    @Schema(
        description = "Commit author (name and email)",
        example = "Alice <alice@example.org>",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String author,

    @Schema(
        description = "Commit timestamp in RFC 3339 format",
        example = "2025-10-24T15:30:00Z",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Instant timestamp,

    @Schema(
        description = "Parent commit IDs (empty for initial commit, multiple for merge commits)",
        example = "[\"01933e4a-8c3d-7000-8000-000000000002\"]",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<String> parents,

    @Schema(
        description = "Number of operations in the RDFPatch",
        example = "42",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    int patchSize
) {
  /**
   * Compact constructor with validation and defensive copying.
   *
   * @param id the commit ID
   * @param message the commit message
   * @param author the commit author
   * @param timestamp the commit timestamp
   * @param parents the parent commit IDs
   * @param patchSize the number of operations in the patch
   */
  public CommitHistoryInfo {
    Objects.requireNonNull(id, "Commit id cannot be null");
    Objects.requireNonNull(message, "Commit message cannot be null");
    Objects.requireNonNull(author, "Commit author cannot be null");
    Objects.requireNonNull(timestamp, "Commit timestamp cannot be null");
    Objects.requireNonNull(parents, "Commit parents cannot be null");

    if (id.isBlank()) {
      throw new IllegalArgumentException("Commit id cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Commit message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Commit author cannot be blank");
    }
    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }

    // Create defensive copy of parents list to ensure immutability
    parents = List.copyOf(parents);
  }
}
