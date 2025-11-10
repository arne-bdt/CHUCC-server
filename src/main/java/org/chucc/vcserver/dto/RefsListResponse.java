package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for listing refs with pagination.
 * Contains a list of refs (branches and tags) and pagination metadata.
 */
@Schema(description = "Paginated list of refs (branches and tags)")
public record RefsListResponse(
    @Schema(description = "List of refs for current page")
    List<RefResponse> refs,

    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param refs list of ref information
   * @param pagination pagination metadata
   */
  public RefsListResponse {
    refs = refs != null ? List.copyOf(refs) : List.of();
  }
}
