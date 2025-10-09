package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * Used for all error responses in the SPARQL 1.2 Protocol Version Control Extension.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

  private String type = "about:blank";
  private String title;
  private int status;
  private String detail;
  private String instance;
  private String code;
  private Map<String, Object> extras;

  /**
   * Default constructor for JSON deserialization.
   */
  public ProblemDetail() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with title, status, and code.
   *
   * @param title human-readable summary
   * @param status HTTP status code
   * @param code canonical error code
   */
  public ProblemDetail(String title, int status, String code) {
    this.title = title;
    this.status = status;
    this.code = code;
  }

  /**
   * Constructor with all basic fields.
   *
   * @param type URI reference for the problem type
   * @param title human-readable summary
   * @param status HTTP status code
   * @param code canonical error code
   */
  public ProblemDetail(String type, String title, int status, String code) {
    this.type = type;
    this.title = title;
    this.status = status;
    this.code = code;
  }

  /**
   * Constructor with all fields including detail and instance.
   *
   * @param type URI reference for the problem type
   * @param title human-readable summary
   * @param status HTTP status code
   * @param detail detailed explanation of the problem
   * @param instance URI reference identifying the specific occurrence
   * @param code canonical error code
   */
  public ProblemDetail(String type, String title, int status, String detail,
      String instance, String code) {
    this.type = type;
    this.title = title;
    this.status = status;
    this.detail = detail;
    this.instance = instance;
    this.code = code;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getInstance() {
    return instance;
  }

  public void setInstance(String instance) {
    this.instance = instance;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  /**
   * Sets additional properties that will be serialized as top-level fields.
   *
   * @param extras map of additional properties
   */
  public void setExtras(Map<String, Object> extras) {
    this.extras = extras != null ? new HashMap<>(extras) : null;
  }

  /**
   * Gets additional properties for serialization as top-level fields.
   *
   * @return map of additional properties
   */
  @JsonAnyGetter
  public Map<String, Object> getExtras() {
    return extras != null ? extras : new HashMap<>();
  }
}
