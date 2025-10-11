package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the deletion of a branch.
 * Records the last commit ID for audit trail purposes.
 */
public record BranchDeletedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("lastCommitId") String lastCommitId,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BranchDeletedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param lastCommitId the last commit ID the branch pointed to (must be non-null)
   * @param author the author of the deletion (must be non-null and non-blank)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchDeletedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(lastCommitId, "Last commit ID cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
