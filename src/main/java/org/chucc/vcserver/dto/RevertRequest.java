package org.chucc.vcserver.dto;

/**
 * Request DTO for reverting a commit.
 * Used by POST /version/revert endpoint per SPARQL 1.2 Protocol §3.4.
 */
public class RevertRequest {

  private String commit;
  private String branch;

  /**
   * Default constructor for JSON deserialization.
   */
  public RevertRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param commit the commit ID to revert
   * @param branch the target branch name for the revert commit
   */
  public RevertRequest(String commit, String branch) {
    this.commit = commit;
    this.branch = branch;
  }

  /**
   * Validates that all required fields are present.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (commit == null) {
      throw new IllegalArgumentException("Commit ID cannot be null");
    }
    if (branch == null) {
      throw new IllegalArgumentException("Branch name cannot be null");
    }

    if (commit.isBlank()) {
      throw new IllegalArgumentException("Commit ID cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
  }

  public String getCommit() {
    return commit;
  }

  public void setCommit(String commit) {
    this.commit = commit;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }
}
