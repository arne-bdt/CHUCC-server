package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for history listing endpoint.
 * Contains a list of commits with pagination metadata.
 */
@Schema(
    description = "Response for commit history listing with pagination",
    example = "{\"commits\":[{\"id\":\"01933e4a-9d4e-7000-8000-000000000003\","
        + "\"message\":\"Add new feature\","
        + "\"author\":\"Alice <alice@example.org>\","
        + "\"timestamp\":\"2025-10-24T15:30:00Z\","
        + "\"parents\":[\"01933e4a-8c3d-7000-8000-000000000002\"],"
        + "\"patchSize\":42}],"
        + "\"pagination\":{\"limit\":100,\"offset\":0,\"hasMore\":true}}"
)
public record HistoryResponse(
    @Schema(
        description = "List of commits in the history",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<CommitHistoryInfo> commits,

    @Schema(
        description = "Pagination metadata",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    PaginationInfo pagination
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param commits list of commit history information
   * @param pagination pagination metadata
   */
  public HistoryResponse {
    commits = commits != null ? List.copyOf(commits) : List.of();
  }
}
