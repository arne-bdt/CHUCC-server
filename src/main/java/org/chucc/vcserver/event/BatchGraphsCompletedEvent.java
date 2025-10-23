package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the completion of a batch graph operation.
 * Contains the list of commits created by the batch.
 */
public record BatchGraphsCompletedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commits") List<CommitCreatedEvent> commits,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new BatchGraphsCompletedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param commits the list of commits created (must be non-null, can be empty)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public BatchGraphsCompletedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

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
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param commits the list of commits created
   * @param timestamp the event timestamp
   */
  public BatchGraphsCompletedEvent(
      String dataset,
      List<CommitCreatedEvent> commits,
      Instant timestamp) {
    this(null, dataset, commits, timestamp);
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

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.dataset(dataset);
  }
}
