package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event representing a branch rebase operation.
 * A rebase reapplies commits from one branch onto another, creating new commits.
 *
 * <p>This event contains all data needed to recreate the rebased commits, enabling
 * proper event replay and eventual consistency. The rebase operation is atomic - all
 * commits are created together or none at all.
 */
public record BranchRebasedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branch") String branch,
    @JsonProperty("newHead") String newHead,
    @JsonProperty("previousHead") String previousHead,
    @JsonProperty("rebasedCommits") List<RebasedCommitData> rebasedCommits,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Represents a single commit created during rebase with all necessary data.
   *
   * @param commitId the new commit ID
   * @param parents the parent commit IDs
   * @param author the commit author
   * @param message the commit message
   * @param rdfPatch the RDF Patch containing the changes
   * @param patchSize the number of operations in the patch
   */
  public record RebasedCommitData(
      @JsonProperty("commitId") String commitId,
      @JsonProperty("parents") List<String> parents,
      @JsonProperty("author") String author,
      @JsonProperty("message") String message,
      @JsonProperty("rdfPatch") String rdfPatch,
      @JsonProperty("patchSize") int patchSize) {

    /**
     * Creates a new RebasedCommitData with validation.
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public RebasedCommitData {
      Objects.requireNonNull(commitId, "Commit ID cannot be null");
      Objects.requireNonNull(parents, "Parents cannot be null");
      Objects.requireNonNull(author, "Author cannot be null");
      Objects.requireNonNull(message, "Message cannot be null");
      Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

      if (patchSize < 0) {
        throw new IllegalArgumentException("Patch size cannot be negative");
      }

      // Create defensive copy
      parents = Collections.unmodifiableList(new ArrayList<>(parents));
    }
  }

  /**
   * Creates a new BranchRebasedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branch the branch that was rebased (must be non-null and non-blank)
   * @param newHead the final commit ID after rebase (must be non-null)
   * @param previousHead the commit ID before rebase (must be non-null)
   * @param rebasedCommits the list of rebased commits with full data (must be non-null)
   * @param author the author of the rebase operation (must be non-null and non-blank)
   * @param timestamp the rebase timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BranchRebasedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(newHead, "New head cannot be null");
    Objects.requireNonNull(previousHead, "Previous head cannot be null");
    Objects.requireNonNull(rebasedCommits, "Rebased commits cannot be null");
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
    rebasedCommits = Collections.unmodifiableList(new ArrayList<>(rebasedCommits));
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param branch the branch that was rebased
   * @param newHead the final commit ID after rebase
   * @param previousHead the commit ID before rebase
   * @param rebasedCommits the list of rebased commits with full data
   * @param author the author of the rebase operation
   * @param timestamp the rebase timestamp
   */
  public BranchRebasedEvent(
      String dataset,
      String branch,
      String newHead,
      String previousHead,
      List<RebasedCommitData> rebasedCommits,
      String author,
      Instant timestamp) {
    this(null, dataset, branch, newHead, previousHead, rebasedCommits, author, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branch);
  }
}
