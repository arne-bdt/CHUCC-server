package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for branch reset operation.
 * Returns information about the branch state before and after reset.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResetResponse {

  private String branch;
  private String newHead;
  private String previousHead;

  /**
   * Default constructor for JSON deserialization.
   */
  public ResetResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch name that was reset
   * @param newHead the commit ID after reset
   * @param previousHead the commit ID before reset
   */
  public ResetResponse(String branch, String newHead, String previousHead) {
    this.branch = branch;
    this.newHead = newHead;
    this.previousHead = previousHead;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getNewHead() {
    return newHead;
  }

  public void setNewHead(String newHead) {
    this.newHead = newHead;
  }

  public String getPreviousHead() {
    return previousHead;
  }

  public void setPreviousHead(String previousHead) {
    this.previousHead = previousHead;
  }
}
