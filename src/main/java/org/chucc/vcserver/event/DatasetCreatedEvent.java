package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the creation of a new dataset.
 * Includes the initial commit, main branch, and optional description.
 */
public record DatasetCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("mainBranch") String mainBranch,
    @JsonProperty("initialCommitId") String initialCommitId,
    @JsonProperty("description") String description,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  /**
   * Creates a new DatasetCreatedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param mainBranch the main branch name (must be non-null and non-blank)
   * @param initialCommitId the initial commit ID (must be non-null)
   * @param description optional description of the dataset (can be null)
   * @param author the author who created the dataset (must be non-null and non-blank)
   * @param timestamp the event timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public DatasetCreatedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(mainBranch, "Main branch cannot be null");
    Objects.requireNonNull(initialCommitId, "Initial commit ID cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (mainBranch.isBlank()) {
      throw new IllegalArgumentException("Main branch cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param mainBranch the main branch name
   * @param initialCommitId the initial commit ID
   * @param description optional description
   * @param author the author who created the dataset
   * @param timestamp the event timestamp
   */
  public DatasetCreatedEvent(
      String dataset,
      String mainBranch,
      String initialCommitId,
      String description,
      String author,
      Instant timestamp) {
    this(null, dataset, mainBranch, initialCommitId, description, author, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.dataset(dataset);
  }
}
