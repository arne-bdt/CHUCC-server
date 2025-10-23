package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a new tag.
 */
public record TagCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("tagName") String tagName,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new TagCreatedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param tagName the tag name (must be non-null and non-blank)
   * @param commitId the commit ID the tag points to (must be non-null)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public TagCreatedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(tagName, "Tag name cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (tagName.isBlank()) {
      throw new IllegalArgumentException("Tag name cannot be blank");
    }
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param tagName the tag name
   * @param commitId the commit ID the tag points to
   * @param timestamp the event timestamp
   */
  public TagCreatedEvent(
      String dataset,
      String tagName,
      String commitId,
      Instant timestamp) {
    this(null, dataset, tagName, commitId, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.dataset(dataset);
  }
}
