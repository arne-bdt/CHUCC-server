package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pagination metadata for paginated endpoints.
 * Provides information about the current page and whether more results are available.
 */
@Schema(
    description = "Pagination metadata",
    example = "{\"limit\":100,\"offset\":0,\"hasMore\":true}"
)
public record PaginationInfo(
    @Schema(
        description = "Maximum number of results per page",
        example = "100",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    int limit,

    @Schema(
        description = "Offset for pagination (number of results to skip)",
        example = "0",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    int offset,

    @Schema(
        description = "Whether more results are available",
        example = "true",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    boolean hasMore
) {
  /**
   * Compact constructor with validation.
   *
   * @param limit maximum number of results per page
   * @param offset offset for pagination
   * @param hasMore whether more results are available
   */
  public PaginationInfo {
    if (limit < 0) {
      throw new IllegalArgumentException("Limit cannot be negative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset cannot be negative");
    }
  }
}
