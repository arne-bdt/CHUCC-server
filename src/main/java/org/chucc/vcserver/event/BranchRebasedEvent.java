package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event representing a branch rebase operation.
 * A rebase reapplies commits from one branch onto another, creating new commits.
 */
public record BranchRebasedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branch") String branch,
    @JsonProperty("newHead") String newHead,
    @JsonProperty("previousHead") String previousHead,
    @JsonProperty("newCommits") List<String> newCommits,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BranchRebasedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branch the branch that was rebased (must be non-null and non-blank)
   * @param newHead the final commit ID after rebase (must be non-null)
   * @param previousHead the commit ID before rebase (must be non-null)
   * @param newCommits the list of new commit IDs created (must be non-null)
   * @param author the author of the rebase operation (must be non-null and non-blank)
   * @param timestamp the rebase timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchRebasedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(newHead, "New head cannot be null");
    Objects.requireNonNull(previousHead, "Previous head cannot be null");
    Objects.requireNonNull(newCommits, "New commits cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    // Create defensive copy of the list
    newCommits = Collections.unmodifiableList(new ArrayList<>(newCommits));
  }
}
