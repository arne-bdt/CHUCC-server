package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 * Used for all error responses in the SPARQL 1.2 Protocol Version Control Extension.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetail {

  private String type = "about:blank";
  private String title;
  private int status;
  private String code;

  public ProblemDetail() {
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
   * Constructor with all fields.
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

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }
}
