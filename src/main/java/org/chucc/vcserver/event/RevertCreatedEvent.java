package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a revert commit.
 * A revert commit undoes the changes from a previous commit.
 */
public record RevertCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("revertCommitId") String revertCommitId,
    @JsonProperty("revertedCommitId") String revertedCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize)
    implements VersionControlEvent {

  /**
   * Creates a new RevertCreatedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param revertCommitId the ID of the new revert commit (must be non-null)
   * @param revertedCommitId the ID of the commit being reverted (must be non-null)
   * @param branch the branch name where the revert commit will be created (must be non-null)
   * @param message the commit message (must be non-null and non-blank)
   * @param author the commit author (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @param rdfPatch the RDF Patch representing the revert changes (must be non-null)
   * @param patchSize the number of operations in the patch (must be non-negative)
   * @throws IllegalArgumentException if any validation fails
   */
  public RevertCreatedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(revertCommitId, "Revert commit ID cannot be null");
    Objects.requireNonNull(revertedCommitId, "Reverted commit ID cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    EventValidation.requireNonBlank(dataset, "Dataset");
    EventValidation.requireNonBlank(branch, "Branch");
    EventValidation.requireNonBlank(message, "Message");
    EventValidation.requireNonBlank(author, "Author");

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param revertCommitId the ID of the new revert commit
   * @param revertedCommitId the ID of the commit being reverted
   * @param branch the branch name where the revert commit will be created
   * @param message the commit message
   * @param author the commit author
   * @param timestamp the commit timestamp
   * @param rdfPatch the RDF Patch representing the revert changes
   * @param patchSize the number of operations in the patch
   */
  public RevertCreatedEvent(
      String dataset,
      String revertCommitId,
      String revertedCommitId,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {
    this(null, dataset, revertCommitId, revertedCommitId, branch, message, author, timestamp,
        rdfPatch, patchSize);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branch);
  }
}
