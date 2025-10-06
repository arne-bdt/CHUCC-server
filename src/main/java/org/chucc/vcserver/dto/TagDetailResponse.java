package org.chucc.vcserver.dto;

/**
 * Response DTO for tag detail endpoint.
 * Contains tag metadata and target commit information.
 */
public class TagDetailResponse {

  private String name;
  private String target;        // Commit ID (UUIDv7)
  private String message;       // Optional annotation
  private String author;        // Optional
  private String timestamp;     // When tag was created (RFC 3339)

  /**
   * Default constructor for JSON deserialization.
   */
  public TagDetailResponse() {
    // Default constructor required by Jackson
  }

  /**
   * Constructor with required fields.
   *
   * @param name the tag name
   * @param target the target commit ID
   */
  public TagDetailResponse(String name, String target) {
    this.name = name;
    this.target = target;
  }

  /**
   * Constructor with all fields.
   *
   * @param name the tag name
   * @param target the target commit ID
   * @param message the tag message
   * @param author the tag author
   * @param timestamp the creation timestamp
   */
  public TagDetailResponse(String name, String target, String message,
      String author, String timestamp) {
    this.name = name;
    this.target = target;
    this.message = message;
    this.author = author;
    this.timestamp = timestamp;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }
}
