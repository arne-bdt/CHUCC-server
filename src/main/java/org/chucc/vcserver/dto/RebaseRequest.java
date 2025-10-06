package org.chucc.vcserver.dto;

/**
 * Request DTO for rebasing a branch onto another reference.
 * Used by POST /version/rebase endpoint per SPARQL 1.2 Protocol §3.4.
 */
public class RebaseRequest {

  private String branch;
  private String onto;
  private String from;

  /**
   * Default constructor for JSON deserialization.
   */
  public RebaseRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch to rebase
   * @param onto the target ref (branch or commit) to rebase onto
   * @param from the base commit (exclusive) - commits after this will be rebased
   */
  public RebaseRequest(String branch, String onto, String from) {
    this.branch = branch;
    this.onto = onto;
    this.from = from;
  }

  /**
   * Validates that all required fields are present.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (branch == null) {
      throw new IllegalArgumentException("Branch cannot be null");
    }
    if (onto == null) {
      throw new IllegalArgumentException("Onto reference cannot be null");
    }
    if (from == null) {
      throw new IllegalArgumentException("From commit cannot be null");
    }

    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (onto.isBlank()) {
      throw new IllegalArgumentException("Onto reference cannot be blank");
    }
    if (from.isBlank()) {
      throw new IllegalArgumentException("From commit cannot be blank");
    }
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getOnto() {
    return onto;
  }

  public void setOnto(String onto) {
    this.onto = onto;
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(String from) {
    this.from = from;
  }
}
