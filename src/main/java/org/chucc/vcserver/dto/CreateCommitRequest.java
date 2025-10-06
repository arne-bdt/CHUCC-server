package org.chucc.vcserver.dto;

import java.util.Objects;

/**
 * Request DTO for creating a commit via POST /version/commits.
 * Contains RDF Patch content, target selector (branch or commit),
 * and optional metadata.
 */
public class CreateCommitRequest {

  private String patch;
  private String branch;
  private String commit;
  private String asOf;
  private String author;
  private String message;

  /**
   * Default constructor for JSON deserialization.
   */
  public CreateCommitRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor for creating a commit on a branch.
   *
   * @param patch the RDF Patch content
   * @param branch the target branch name
   * @param author the commit author
   * @param message the commit message
   */
  public CreateCommitRequest(String patch, String branch, String author, String message) {
    this.patch = patch;
    this.branch = branch;
    this.author = author;
    this.message = message;
  }

  /**
   * Validates that the request has valid selectors.
   * Either branch OR commit must be provided, but not both.
   * asOf is only allowed with branch selector.
   */
  public void validate() {
    if (branch == null && commit == null) {
      throw new IllegalArgumentException(
          "Either 'branch' or 'commit' selector must be provided");
    }
    if (branch != null && commit != null) {
      throw new IllegalArgumentException(
          "Cannot provide both 'branch' and 'commit' selectors");
    }
    if (asOf != null && commit != null) {
      throw new IllegalArgumentException(
          "'asOf' selector can only be used with 'branch', not 'commit'");
    }
    Objects.requireNonNull(patch, "Patch content cannot be null");
  }

  public String getPatch() {
    return patch;
  }

  public void setPatch(String patch) {
    this.patch = patch;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getCommit() {
    return commit;
  }

  public void setCommit(String commit) {
    this.commit = commit;
  }

  public String getAsOf() {
    return asOf;
  }

  public void setAsOf(String asOf) {
    this.asOf = asOf;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
