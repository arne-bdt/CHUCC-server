package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing a branch reset operation.
 * A reset changes what commit a branch points to (potentially non-fast-forward).
 */
public record BranchResetEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("fromCommitId") String fromCommitId,
    @JsonProperty("toCommitId") String toCommitId,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BranchResetEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param fromCommitId the previous commit ID (must be non-null)
   * @param toCommitId the new commit ID (must be non-null)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchResetEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

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

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   * @param fromCommitId the previous commit ID
   * @param toCommitId the new commit ID
   * @param timestamp the event timestamp
   */
  public BranchResetEvent(
      String dataset,
      String branchName,
      String fromCommitId,
      String toCommitId,
      Instant timestamp) {
    this(null, dataset, branchName, fromCommitId, toCommitId, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
