package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing a snapshot of a dataset at a specific commit.
 * Snapshots are periodic checkpoints published to vc.{dataset}.snapshots
 * to speed up recovery by avoiding replay of all events from the beginning.
 *
 * <p>The snapshot contains the complete state of the dataset at the specified commit,
 * serialized as an N-Quads dump.
 */
public record SnapshotCreatedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("nquads") String nquads)
    implements VersionControlEvent {

  /**
   * Creates a new SnapshotCreatedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param commitId the commit ID at which the snapshot was taken (must be non-null)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param timestamp the snapshot creation timestamp (must be non-null)
   * @param nquads the N-Quads serialization of the dataset (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public SnapshotCreatedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(nquads, "N-Quads cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
  }
}
