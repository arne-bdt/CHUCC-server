package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for commit metadata.
 * Returned when creating or retrieving commits.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Commit metadata in the version control system. "
        + "Each commit represents an immutable snapshot of the RDF dataset state.",
    example = "{\"id\":\"01936c7f-8a2e-7890-abcd-ef1234567890\","
        + "\"parents\":[\"01936c7e-1234-5678-9abc-def012345678\"],"
        + "\"author\":\"Alice\","
        + "\"message\":\"Update graph content\","
        + "\"timestamp\":\"2025-10-09T12:00:00Z\"}"
)
public class CommitResponse {

  @Schema(
      description = "Commit ID (UUIDv7)",
      example = "01936c7f-8a2e-7890-abcd-ef1234567890",
      required = true
  )
  private String id;

  @Schema(
      description = "Parent commit IDs (empty for initial commit, multiple for merge commits)",
      example = "[\"01936c7e-1234-5678-9abc-def012345678\"]"
  )
  private List<String> parents;

  @Schema(
      description = "Commit author",
      example = "Alice",
      required = true
  )
  private String author;

  @Schema(
      description = "Commit message describing the changes",
      example = "Update graph content",
      required = true
  )
  private String message;

  @Schema(
      description = "Commit timestamp in RFC 3339 format",
      example = "2025-10-09T12:00:00Z",
      required = true
  )
  private String timestamp;

  /**
   * Default constructor for JSON deserialization.
   */
  public CommitResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param id the commit ID (UUIDv7)
   * @param parents the parent commit IDs
   * @param author the commit author
   * @param message the commit message
   * @param timestamp the commit timestamp in RFC 3339 format
   */
  public CommitResponse(
      String id,
      List<String> parents,
      String author,
      String message,
      String timestamp) {
    this.id = id;
    this.parents = parents != null ? new ArrayList<>(parents) : new ArrayList<>();
    this.author = author;
    this.message = message;
    this.timestamp = timestamp;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<String> getParents() {
    return parents != null ? new ArrayList<>(parents) : new ArrayList<>();
  }

  public void setParents(List<String> parents) {
    this.parents = parents != null ? new ArrayList<>(parents) : new ArrayList<>();
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}
