package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the completion of a batch graph operation.
 * Contains the list of commits created by the batch.
 */
public record BatchGraphsCompletedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commits") List<CommitCreatedEvent> commits,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BatchGraphsCompletedEvent with validation.
   *
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param commits the list of commits created (must be non-null, can be empty)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BatchGraphsCompletedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(commits, "Commits cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }

    // Defensive copy
    commits = new ArrayList<>(commits);
  }

  /**
   * Returns a defensive copy of the commits list.
   *
   * @return a new list containing the commits
   */
  @Override
  public List<CommitCreatedEvent> commits() {
    return new ArrayList<>(commits);
  }
}
