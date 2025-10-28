package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event published when a merge operation creates a new merge commit.
 *
 * <p>Note: Fast-forward merges use BranchResetEvent instead.
 * This event is only for three-way merges that create a new commit with two parents.
 */
public record BranchMergedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("sourceRef") String sourceRef,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("parents") List<String> parents,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize)
    implements VersionControlEvent {

  /**
   * Creates a new BranchMergedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branchName target branch that was updated (must be non-null and non-blank)
   * @param sourceRef source ref that was merged (must be non-null and non-blank)
   * @param commitId ID of created merge commit (must be non-null)
   * @param parents parent commit IDs (must contain exactly 2 for merge commits)
   * @param message merge commit message (must be non-null and non-blank)
   * @param author author of merge (must be non-null and non-blank)
   * @param timestamp event timestamp (must be non-null)
   * @param rdfPatch RDF Patch applied in merge (must be non-null)
   * @param patchSize number of quads changed (must be non-negative)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchMergedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(sourceRef, "Source ref cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(parents, "Parents cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (sourceRef.isBlank()) {
      throw new IllegalArgumentException("Source ref cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
    if (parents.size() != 2) {
      throw new IllegalArgumentException("Merge commits must have exactly 2 parents");
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
   * @param branchName target branch that was updated
   * @param sourceRef source ref that was merged
   * @param commitId ID of created merge commit
   * @param parents parent commit IDs (exactly 2)
   * @param message merge commit message
   * @param author author of merge
   * @param timestamp event timestamp
   * @param rdfPatch RDF Patch applied in merge
   * @param patchSize number of quads changed
   */
  public BranchMergedEvent(
      String dataset,
      String branchName,
      String sourceRef,
      String commitId,
      List<String> parents,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {
    this(null, dataset, branchName, sourceRef, commitId, parents, message, author,
        timestamp, rdfPatch, patchSize);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
