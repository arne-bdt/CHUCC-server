package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for cherry-pick operation.
 * Returns information about the newly created commit.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CherryPickResponse {

  private String newCommit;
  private String branch;
  private String sourceCommit;

  /**
   * Default constructor for JSON deserialization.
   */
  public CherryPickResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param newCommit the newly created commit ID
   * @param branch the target branch name
   * @param sourceCommit the source commit ID that was cherry-picked
   */
  public CherryPickResponse(String newCommit, String branch, String sourceCommit) {
    this.newCommit = newCommit;
    this.branch = branch;
    this.sourceCommit = sourceCommit;
  }

  public String getNewCommit() {
    return newCommit;
  }

  public void setNewCommit(String newCommit) {
    this.newCommit = newCommit;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getSourceCommit() {
    return sourceCommit;
  }

  public void setSourceCommit(String sourceCommit) {
    this.sourceCommit = sourceCommit;
  }
}
