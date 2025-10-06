package org.chucc.vcserver.dto;

/**
 * Request DTO for resetting a branch to a different commit.
 * Used by POST /version/reset endpoint per SPARQL 1.2 Protocol §3.4.
 */
public class ResetRequest {

  private String branch;
  private String to;
  private ResetMode mode;

  /**
   * Default constructor for JSON deserialization.
   */
  public ResetRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch name to reset
   * @param to the target commit ID
   * @param mode the reset mode (HARD, SOFT, or MIXED)
   */
  public ResetRequest(String branch, String to, ResetMode mode) {
    this.branch = branch;
    this.to = to;
    this.mode = mode;
  }

  /**
   * Validates that all required fields are present.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (branch == null) {
      throw new IllegalArgumentException("Branch name cannot be null");
    }
    if (to == null) {
      throw new IllegalArgumentException("Target commit ID cannot be null");
    }
    if (mode == null) {
      throw new IllegalArgumentException("Reset mode cannot be null");
    }

    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (to.isBlank()) {
      throw new IllegalArgumentException("Target commit ID cannot be blank");
    }
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getTo() {
    return to;
  }

  public void setTo(String to) {
    this.to = to;
  }

  public ResetMode getMode() {
    return mode;
  }

  public void setMode(ResetMode mode) {
    this.mode = mode;
  }
}
