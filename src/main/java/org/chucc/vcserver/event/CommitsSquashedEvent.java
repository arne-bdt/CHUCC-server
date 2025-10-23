package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Event representing a squash operation where multiple commits are combined into one.
 * The squashed commits are replaced by a single new commit with combined changes.
 */
public record CommitsSquashedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branch") String branch,
    @JsonProperty("newCommitId") String newCommitId,
    @JsonProperty("squashedCommitIds") List<String> squashedCommitIds,
    @JsonProperty("author") String author,
    @JsonProperty("message") String message,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("previousHead") String previousHead)
    implements VersionControlEvent {

  /**
   * Creates a new CommitsSquashedEvent with validation.
   * If eventId is null, a UUIDv7 will be auto-generated.
   *
   * @param eventId the globally unique event ID (null to auto-generate)
   * @param dataset the dataset name (must be non-null and non-blank)
   * @param branch the branch that was modified (must be non-null and non-blank)
   * @param newCommitId the ID of the newly created squashed commit (must be non-null)
   * @param squashedCommitIds the list of original commit IDs that were squashed
   *     (must be non-null)
   * @param author the author of the squashed commit (must be non-null and non-blank)
   * @param message the commit message for the squashed commit (must be non-null and non-blank)
   * @param timestamp the squash timestamp (must be non-null)
   * @param previousHead the branch HEAD before the squash operation (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public CommitsSquashedEvent {
    // Auto-generate eventId if null
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(newCommitId, "New commit ID cannot be null");
    Objects.requireNonNull(squashedCommitIds, "Squashed commit IDs cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(previousHead, "Previous head cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }

    // Create defensive copy of the list
    squashedCommitIds = Collections.unmodifiableList(new ArrayList<>(squashedCommitIds));
  }

  /**
   * Convenience constructor that auto-generates eventId.
   *
   * @param dataset the dataset name
   * @param branch the branch that was modified
   * @param newCommitId the ID of the newly created squashed commit
   * @param squashedCommitIds the list of original commit IDs that were squashed
   * @param author the author of the squashed commit
   * @param message the commit message for the squashed commit
   * @param timestamp the squash timestamp
   * @param previousHead the branch HEAD before the squash operation
   */
  public CommitsSquashedEvent(
      String dataset,
      String branch,
      String newCommitId,
      List<String> squashedCommitIds,
      String author,
      String message,
      Instant timestamp,
      String previousHead) {
    this(null, dataset, branch, newCommitId, squashedCommitIds, author, message, timestamp,
        previousHead);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branch);
  }
}
