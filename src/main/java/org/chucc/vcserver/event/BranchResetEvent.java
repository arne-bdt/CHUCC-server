package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing a branch reset operation.
 * A reset changes what commit a branch points to (potentially non-fast-forward).
 */
public record BranchResetEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("fromCommitId") String fromCommitId,
    @JsonProperty("toCommitId") String toCommitId,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BranchResetEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param fromCommitId the previous commit ID (must be non-null)
   * @param toCommitId the new commit ID (must be non-null)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchResetEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(fromCommitId, "From commit ID cannot be null");
    Objects.requireNonNull(toCommitId, "To commit ID cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
