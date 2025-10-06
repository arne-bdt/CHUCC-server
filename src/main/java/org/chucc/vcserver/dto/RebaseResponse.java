package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for rebase operation.
 * Returns information about the rebased commits.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RebaseResponse {

  private String branch;
  private String newHead;
  private List<String> newCommits;
  private int rebasedCount;

  /**
   * Default constructor for JSON deserialization.
   */
  public RebaseResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch that was rebased
   * @param newHead the final commit ID after rebase
   * @param newCommits the list of all new commit IDs created
   * @param rebasedCount the number of commits rebased
   */
  public RebaseResponse(String branch, String newHead, List<String> newCommits,
      int rebasedCount) {
    this.branch = branch;
    this.newHead = newHead;
    this.newCommits = newCommits != null ? new ArrayList<>(newCommits) : new ArrayList<>();
    this.rebasedCount = rebasedCount;
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

  public List<String> getNewCommits() {
    return newCommits != null ? new ArrayList<>(newCommits) : new ArrayList<>();
  }

  public void setNewCommits(List<String> newCommits) {
    this.newCommits = newCommits != null ? new ArrayList<>(newCommits) : new ArrayList<>();
  }

  public int getRebasedCount() {
    return rebasedCount;
  }

  public void setRebasedCount(int rebasedCount) {
    this.rebasedCount = rebasedCount;
  }
}
