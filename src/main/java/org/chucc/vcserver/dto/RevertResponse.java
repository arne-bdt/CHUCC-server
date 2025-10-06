package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for revert operation.
 * Returns information about the newly created revert commit.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RevertResponse {

  private String newCommit;
  private String branch;
  private String revertedCommit;

  /**
   * Default constructor for JSON deserialization.
   */
  public RevertResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param newCommit the newly created revert commit ID
   * @param branch the target branch name
   * @param revertedCommit the original commit ID that was reverted
   */
  public RevertResponse(String newCommit, String branch, String revertedCommit) {
    this.newCommit = newCommit;
    this.branch = branch;
    this.revertedCommit = revertedCommit;
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

  public String getRevertedCommit() {
    return revertedCommit;
  }

  public void setRevertedCommit(String revertedCommit) {
    this.revertedCommit = revertedCommit;
  }
}
