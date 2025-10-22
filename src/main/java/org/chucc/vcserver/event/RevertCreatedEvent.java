package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a revert commit.
 * A revert commit undoes the changes from a previous commit.
 */
public record RevertCreatedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("revertCommitId") String revertCommitId,
    @JsonProperty("revertedCommitId") String revertedCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch)
    implements VersionControlEvent {

  /**
   * Creates a new RevertCreatedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param revertCommitId the ID of the new revert commit (must be non-null)
   * @param revertedCommitId the ID of the commit being reverted (must be non-null)
   * @param branch the branch name where the revert commit will be created (must be non-null)
   * @param message the commit message (must be non-null and non-blank)
   * @param author the commit author (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @param rdfPatch the RDF Patch representing the revert changes (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public RevertCreatedEvent {
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
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branch);
  }
}
