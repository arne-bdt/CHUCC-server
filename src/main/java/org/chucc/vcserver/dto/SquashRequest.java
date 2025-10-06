package org.chucc.vcserver.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for squashing multiple commits into one.
 * Used by POST /version/squash endpoint per SPARQL 1.2 Protocol §3.4.
 */
public class SquashRequest {

  private String branch;
  private List<String> commits;
  private String message;
  private String author;

  /**
   * Default constructor for JSON deserialization.
   */
  public SquashRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch containing the commits
   * @param commits the commit IDs to squash (in order, must be contiguous)
   * @param message the commit message for the squashed commit
   * @param author optional author override for the squashed commit
   */
  public SquashRequest(String branch, List<String> commits, String message, String author) {
    this.branch = branch;
    this.commits = commits != null ? new ArrayList<>(commits) : new ArrayList<>();
    this.message = message;
    this.author = author;
  }

  /**
   * Validates that all required fields are present and valid.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (branch == null) {
      throw new IllegalArgumentException("Branch cannot be null");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }

    if (commits == null || commits.isEmpty()) {
      throw new IllegalArgumentException("Commits list cannot be null or empty");
    }
    if (commits.size() < 2) {
      throw new IllegalArgumentException("Must specify at least 2 commits to squash");
    }

    for (int i = 0; i < commits.size(); i++) {
      String commit = commits.get(i);
      if (commit == null || commit.isBlank()) {
        throw new IllegalArgumentException("Commit at index " + i + " cannot be null or blank");
      }
    }

    if (message == null) {
      throw new IllegalArgumentException("Message cannot be null");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public List<String> getCommits() {
    return commits != null ? new ArrayList<>(commits) : new ArrayList<>();
  }

  public void setCommits(List<String> commits) {
    this.commits = commits != null ? new ArrayList<>(commits) : new ArrayList<>();
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }
}
