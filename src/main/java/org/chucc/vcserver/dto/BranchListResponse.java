package org.chucc.vcserver.dto;

import java.util.List;

/**
 * Response DTO for listing branches.
 * Contains a list of branch information with full metadata.
 */
public record BranchListResponse(
    List<BranchInfo> branches
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param branches list of branch information
   */
  public BranchListResponse {
    branches = branches != null ? List.copyOf(branches) : List.of();
  }
}
