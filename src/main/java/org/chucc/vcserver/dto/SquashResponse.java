package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for squash operation.
 * Returns information about the squashed commit and the commits that were combined.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SquashResponse {

  private String branch;
  private String newCommit;
  private List<String> squashedCommits;
  private String previousHead;

  /**
   * Default constructor for JSON deserialization.
   */
  public SquashResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param branch the branch that was modified
   * @param newCommit the ID of the newly created squashed commit
   * @param squashedCommits the list of original commit IDs that were squashed
   * @param previousHead the branch HEAD before the squash operation
   */
  public SquashResponse(String branch, String newCommit, List<String> squashedCommits,
      String previousHead) {
    this.branch = branch;
    this.newCommit = newCommit;
    this.squashedCommits = squashedCommits != null
        ? new ArrayList<>(squashedCommits)
        : new ArrayList<>();
    this.previousHead = previousHead;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getNewCommit() {
    return newCommit;
  }

  public void setNewCommit(String newCommit) {
    this.newCommit = newCommit;
  }

  /**
   * Gets the list of squashed commit IDs.
   *
   * @return a defensive copy of the squashed commits list
   */
  public List<String> getSquashedCommits() {
    return squashedCommits != null ? new ArrayList<>(squashedCommits) : new ArrayList<>();
  }

  /**
   * Sets the list of squashed commit IDs.
   *
   * @param squashedCommits the list of original commit IDs that were squashed
   */
  public void setSquashedCommits(List<String> squashedCommits) {
    this.squashedCommits = squashedCommits != null
        ? new ArrayList<>(squashedCommits)
        : new ArrayList<>();
  }

  public String getPreviousHead() {
    return previousHead;
  }

  public void setPreviousHead(String previousHead) {
    this.previousHead = previousHead;
  }
}
