package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for a single ref (branch or tag).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefResponse {

  private String type;
  private String name;
  private String targetCommit;
  private String message;

  /**
   * Default constructor for JSON deserialization.
   */
  public RefResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor for refs without messages (branches and lightweight tags).
   *
   * @param type the ref type ("branch" or "tag")
   * @param name the ref name
   * @param targetCommit the target commit ID (UUIDv7)
   */
  public RefResponse(String type, String name, String targetCommit) {
    this.type = type;
    this.name = name;
    this.targetCommit = targetCommit;
  }

  /**
   * Constructor for annotated tags with messages.
   *
   * @param type the ref type ("branch" or "tag")
   * @param name the ref name
   * @param targetCommit the target commit ID (UUIDv7)
   * @param message the tag message (for annotated tags)
   */
  public RefResponse(String type, String name, String targetCommit, String message) {
    this.type = type;
    this.name = name;
    this.targetCommit = targetCommit;
    this.message = message;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTargetCommit() {
    return targetCommit;
  }

  public void setTargetCommit(String targetCommit) {
    this.targetCommit = targetCommit;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
