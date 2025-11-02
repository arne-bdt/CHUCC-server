package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Response for the blame endpoint.
 * Contains blame information for all quads in a specific graph, with pagination support.
 */
@Schema(
    description = "Blame response with attribution information for quads",
    example = """
        {
          "commit": "01933e4a-9d4e-7000-8000-000000000003",
          "graph": "http://example.org/metadata",
          "quads": [
            {
              "subject": "http://example.org/Alice",
              "predicate": "http://xmlns.com/foaf/0.1/name",
              "object": "\\"Alice\\"",
              "lastModifiedBy": "Bob <bob@example.org>",
              "lastModifiedAt": "2025-10-24T12:00:00Z",
              "commitId": "01933e4a-8c3d-7000-8000-000000000002"
            }
          ],
          "pagination": {
            "offset": 0,
            "limit": 100,
            "hasMore": false
          }
        }
        """
)
public record BlameResponse(
    @Schema(
        description = "Target commit ID",
        example = "01933e4a-9d4e-7000-8000-000000000003",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String commit,

    @Schema(
        description = "Graph IRI (or 'default' for default graph)",
        example = "http://example.org/metadata",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String graph,

    @Schema(
        description = "Blame information for quads (paginated)",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<QuadBlameInfo> quads,

    @Schema(
        description = "Pagination metadata",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    PaginationInfo pagination
) {
  /**
   * Compact constructor with validation and defensive copying.
   *
   * @param commit the target commit ID
   * @param graph the graph IRI
   * @param quads the blame information for quads
   * @param pagination the pagination metadata
   */
  public BlameResponse {
    if (commit == null || commit.isBlank()) {
      throw new IllegalArgumentException("Commit cannot be null or blank");
    }
    if (graph == null || graph.isBlank()) {
      throw new IllegalArgumentException("Graph cannot be null or blank");
    }
    if (quads == null) {
      throw new IllegalArgumentException("Quads cannot be null");
    }
    if (pagination == null) {
      throw new IllegalArgumentException("Pagination cannot be null");
    }

    // Defensive copy
    quads = new ArrayList<>(quads);
  }

  /**
   * Returns a defensive copy of the quads list.
   *
   * @return a copy of the quads list
   */
  @Override
  public List<QuadBlameInfo> quads() {
    return new ArrayList<>(quads);
  }
}
