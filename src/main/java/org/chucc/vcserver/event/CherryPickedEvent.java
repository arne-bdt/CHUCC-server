package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a cherry-picked commit.
 * A cherry-picked commit applies changes from a source commit to a target branch.
 */
public record CherryPickedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("newCommitId") String newCommitId,
    @JsonProperty("sourceCommitId") String sourceCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch)
    implements VersionControlEvent {

  /**
   * Creates a new CherryPickedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param newCommitId the ID of the new cherry-picked commit (must be non-null)
   * @param sourceCommitId the ID of the source commit being cherry-picked (must be non-null)
   * @param branch the target branch name (must be non-null and non-blank)
   * @param message the commit message (must be non-null and non-blank)
   * @param author the commit author (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @param rdfPatch the RDF Patch representing the cherry-picked changes (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public CherryPickedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(newCommitId, "New commit ID cannot be null");
    Objects.requireNonNull(sourceCommitId, "Source commit ID cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    EventValidation.requireNonBlank(dataset, "Dataset");
    EventValidation.requireNonBlank(branch, "Branch");
    EventValidation.requireNonBlank(message, "Message");
    EventValidation.requireNonBlank(author, "Author");
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param newCommitId the ID of the new cherry-picked commit
   * @param sourceCommitId the ID of the source commit being cherry-picked
   * @param branch the target branch name
   * @param message the commit message
   * @param author the commit author
   * @param timestamp the commit timestamp
   * @param rdfPatch the RDF Patch representing the cherry-picked changes
   */
  public CherryPickedEvent(
      String dataset,
      String newCommitId,
      String sourceCommitId,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch) {
    this(null, dataset, newCommitId, sourceCommitId, branch, message, author, timestamp, rdfPatch);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branch);
  }
}
