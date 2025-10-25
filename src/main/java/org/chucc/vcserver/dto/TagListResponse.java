package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for listing tags.
 *
 * <p>Wrapper used for consistency with Branch API and potential future pagination
 * support (e.g., totalCount, nextPageToken).
 */
@Schema(description = "List of tags in the dataset")
public record TagListResponse(
    @Schema(description = "Array of tags")
    List<TagInfo> tags
) {
  /**
   * Compact constructor with defensive copying.
   *
   * @param tags list of tags
   */
  public TagListResponse {
    tags = tags != null ? List.copyOf(tags) : List.of();
  }
}
