package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a cherry-picked commit.
 * A cherry-picked commit applies changes from a source commit to a target branch.
 */
public record CherryPickedEvent(
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
   *
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
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(newCommitId, "New commit ID cannot be null");
    Objects.requireNonNull(sourceCommitId, "Source commit ID cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
