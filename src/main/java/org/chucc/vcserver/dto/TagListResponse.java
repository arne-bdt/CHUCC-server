package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for listing tags with pagination.
 * Contains a list of tag information and pagination metadata.
 */
@Schema(description = "Paginated list of tags")
public record TagListResponse(
    @Schema(description = "List of tags for current page")
    List<TagInfo> tags,

    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param tags list of tag information
   * @param pagination pagination metadata
   */
  public TagListResponse {
    tags = tags != null ? List.copyOf(tags) : List.of();
  }
}
