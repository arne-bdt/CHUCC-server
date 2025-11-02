package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Blame information for a single quad.
 * Identifies the commit and author who last added this quad to the graph.
 */
@Schema(
    description = "Blame information for a single quad",
    example = """
        {
          "subject": "http://example.org/Alice",
          "predicate": "http://xmlns.com/foaf/0.1/name",
          "object": "\\"Alice\\"",
          "lastModifiedBy": "Bob <bob@example.org>",
          "lastModifiedAt": "2025-10-24T12:00:00Z",
          "commitId": "01933e4a-8c3d-7000-8000-000000000002"
        }
        """
)
public record QuadBlameInfo(
    @Schema(
        description = "Subject node (URI, blank node, or literal)",
        example = "http://example.org/Alice",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String subject,

    @Schema(
        description = "Predicate URI",
        example = "http://xmlns.com/foaf/0.1/name",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String predicate,

    @Schema(
        description = "Object node (URI, blank node, or literal)",
        example = "\"Alice\"",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String object,

    @Schema(
        description = "Author who last added this quad",
        example = "Bob <bob@example.org>",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String lastModifiedBy,

    @Schema(
        description = "Timestamp when this quad was last added",
        example = "2025-10-24T12:00:00Z",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Instant lastModifiedAt,

    @Schema(
        description = "Commit ID that last added this quad",
        example = "01933e4a-8c3d-7000-8000-000000000002",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String commitId
) {
  /**
   * Compact constructor with validation.
   *
   * @param subject the subject node
   * @param predicate the predicate URI
   * @param object the object node
   * @param lastModifiedBy the author who last modified
   * @param lastModifiedAt the timestamp of last modification
   * @param commitId the commit ID
   */
  public QuadBlameInfo {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject cannot be null or blank");
    }
    if (predicate == null || predicate.isBlank()) {
      throw new IllegalArgumentException("Predicate cannot be null or blank");
    }
    if (object == null || object.isBlank()) {
      throw new IllegalArgumentException("Object cannot be null or blank");
    }
    if (lastModifiedBy == null || lastModifiedBy.isBlank()) {
      throw new IllegalArgumentException("LastModifiedBy cannot be null or blank");
    }
    if (lastModifiedAt == null) {
      throw new IllegalArgumentException("LastModifiedAt cannot be null");
    }
    if (commitId == null || commitId.isBlank()) {
      throw new IllegalArgumentException("CommitId cannot be null or blank");
    }
  }
}
