package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the creation of a new commit.
 * Contains the commit metadata and the RDF Patch representing the changes.
 */
public record CommitCreatedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("parents") List<String> parents,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch)
    implements VersionControlEvent {

  /**
   * Creates a new CommitCreatedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param commitId the commit ID (must be non-null)
   * @param parents the list of parent commit IDs (must be non-null, can be empty)
   * @param branch the branch name (nullable for backward compatibility with old events)
   * @param message the commit message (must be non-null and non-blank)
   * @param author the commit author (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @param rdfPatch the RDF Patch representing the changes (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public CommitCreatedEvent {
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

    // Create defensive copy of parents list to ensure immutability
    parents = List.copyOf(parents);
  }
}
