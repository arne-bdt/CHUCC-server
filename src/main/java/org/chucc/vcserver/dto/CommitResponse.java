package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for commit metadata.
 * Returned when creating or retrieving commits.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommitResponse {

  private String id;
  private List<String> parents;
  private String author;
  private String message;
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
