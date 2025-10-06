package org.chucc.vcserver.dto;

import java.util.Objects;

/**
 * Request DTO for cherry-picking a commit to a branch.
 * Used by POST /version/cherry-pick endpoint per SPARQL 1.2 Protocol §3.4.
 */
public class CherryPickRequest {

  private String commit;
  private String onto;

  /**
   * Default constructor for JSON deserialization.
   */
  public CherryPickRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param commit the source commit ID to cherry-pick
   * @param onto the target branch name
   */
  public CherryPickRequest(String commit, String onto) {
    this.commit = commit;
    this.onto = onto;
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
    if (onto == null) {
      throw new IllegalArgumentException("Target branch cannot be null");
    }

    if (commit.isBlank()) {
      throw new IllegalArgumentException("Commit ID cannot be blank");
    }
    if (onto.isBlank()) {
      throw new IllegalArgumentException("Target branch cannot be blank");
    }
  }

  public String getCommit() {
    return commit;
  }

  public void setCommit(String commit) {
    this.commit = commit;
  }

  public String getOnto() {
    return onto;
  }

  public void setOnto(String onto) {
    this.onto = onto;
  }
}
