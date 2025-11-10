package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for listing branches with pagination.
 * Contains a list of branch information with full metadata and pagination metadata.
 */
@Schema(description = "Paginated list of branches")
public record BranchListResponse(
    @Schema(description = "List of branches for current page")
    List<BranchInfo> branches,

    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param branches list of branch information
   * @param pagination pagination metadata
   */
  public BranchListResponse {
    branches = branches != null ? List.copyOf(branches) : List.of();
  }
}
