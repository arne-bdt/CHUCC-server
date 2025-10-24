package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a new branch.
 * Includes metadata for Git-like branch management: source ref, protection status, and author.
 */
public record BranchCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("sourceRef") String sourceRef,
    @JsonProperty("protected") boolean isProtected,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BranchCreatedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branchName the branch name (must be non-null and non-blank)
   * @param commitId the commit ID the branch points to (must be non-null)
   * @param sourceRef the source ref (branch name or commit ID) (must be non-null and non-blank)
   * @param isProtected whether the branch is protected from deletion/force-push
   * @param author the author who created the branch (must be non-null and non-blank)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchCreatedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(sourceRef, "Source ref cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (sourceRef.isBlank()) {
      throw new IllegalArgumentException("Source ref cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   * @param commitId the commit ID the branch points to
   * @param sourceRef the source ref (branch name or commit ID)
   * @param isProtected whether the branch is protected
   * @param author the author who created the branch
   * @param timestamp the event timestamp
   */
  public BranchCreatedEvent(
      String dataset,
      String branchName,
      String commitId,
      String sourceRef,
      boolean isProtected,
      String author,
      Instant timestamp) {
    this(null, dataset, branchName, commitId, sourceRef, isProtected, author, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
