package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the creation of a new commit.
 * Contains the commit metadata, the RDF Patch representing the changes, and the patch size.
 */
public record CommitCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("parents") List<String> parents,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize)
    implements VersionControlEvent {

  /**
   * Creates a new CommitCreatedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param commitId the commit ID (must be non-null)
   * @param parents the list of parent commit IDs (must be non-null, can be empty)
   * @param branch the branch name (nullable for backward compatibility with old events)
   * @param message the commit message (must be non-null and non-blank)
   * @param author the commit author (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @param rdfPatch the RDF Patch representing the changes (must be non-null)
   * @param patchSize the number of operations in the patch (must be non-negative)
   * @throws IllegalArgumentException if any validation fails
   */
  public CommitCreatedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(parents, "Parents cannot be null");
    // branch is nullable for backward compatibility with old events
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }

    // Create defensive copy of parents list to ensure immutability
    parents = List.copyOf(parents);
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param commitId the commit ID
   * @param parents the list of parent commit IDs
   * @param branch the branch name
   * @param message the commit message
   * @param author the commit author
   * @param timestamp the commit timestamp
   * @param rdfPatch the RDF Patch representing the changes
   * @param patchSize the number of operations in the patch
   */
  public CommitCreatedEvent(
      String dataset,
      String commitId,
      List<String> parents,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {
    this(null, dataset, commitId, parents, branch, message, author, timestamp, rdfPatch,
        patchSize);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    // If branch is specified, use branch aggregate
    if (branch != null && !branch.isBlank()) {
      return AggregateIdentity.branch(dataset, branch);
    }
    // Otherwise, use commit aggregate (detached commit)
    return AggregateIdentity.commit(dataset, commitId);
  }
}
