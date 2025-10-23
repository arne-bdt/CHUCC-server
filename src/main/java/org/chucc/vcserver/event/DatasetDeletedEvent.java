package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the deletion of an entire dataset.
 * Records audit information about what was deleted.
 */
public record DatasetDeletedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("deletedBranches") List<String> deletedBranches,
    @JsonProperty("deletedCommitCount") int deletedCommitCount,
    @JsonProperty("kafkaTopicDeleted") boolean kafkaTopicDeleted)
    implements VersionControlEvent {

  /**
   * Creates a new DatasetDeletedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param author the author of the deletion (must be non-null and non-blank)
   * @param timestamp the event timestamp (must be non-null)
   * @param deletedBranches the list of deleted branch names (must be non-null)
   * @param deletedCommitCount the number of commits deleted
   * @param kafkaTopicDeleted whether the Kafka topic was deleted
   * @throws IllegalArgumentException if any validation fails
   */
  public DatasetDeletedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(deletedBranches, "Deleted branches cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    // Defensive copy
    deletedBranches = List.copyOf(deletedBranches);
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param author the author of the deletion
   * @param timestamp the event timestamp
   * @param deletedBranches the list of deleted branch names
   * @param deletedCommitCount the number of commits deleted
   * @param kafkaTopicDeleted whether the Kafka topic was deleted
   */
  public DatasetDeletedEvent(
      String dataset,
      String author,
      Instant timestamp,
      List<String> deletedBranches,
      int deletedCommitCount,
      boolean kafkaTopicDeleted) {
    this(null, dataset, author, timestamp, deletedBranches, deletedCommitCount, kafkaTopicDeleted);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.dataset(dataset);
  }
}
