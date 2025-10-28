package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;

/**
 * Base interface for all version control events.
 * Events are immutable and represent state changes in the version control system.
 * All events include a timestamp for ordering and audit purposes.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BranchCreatedEvent.class, name = "BranchCreated"),
    @JsonSubTypes.Type(value = CommitCreatedEvent.class, name = "CommitCreated"),
    @JsonSubTypes.Type(value = TagCreatedEvent.class, name = "TagCreated"),
    @JsonSubTypes.Type(value = BranchResetEvent.class, name = "BranchReset"),
    @JsonSubTypes.Type(value = RevertCreatedEvent.class, name = "RevertCreated"),
    @JsonSubTypes.Type(value = SnapshotCreatedEvent.class, name = "SnapshotCreated"),
    @JsonSubTypes.Type(value = CherryPickedEvent.class, name = "CherryPicked"),
    @JsonSubTypes.Type(value = BranchRebasedEvent.class, name = "BranchRebased"),
    @JsonSubTypes.Type(value = CommitsSquashedEvent.class, name = "CommitsSquashed"),
    @JsonSubTypes.Type(value = BatchGraphsCompletedEvent.class, name = "BatchGraphsCompleted"),
    @JsonSubTypes.Type(value = BranchDeletedEvent.class, name = "BranchDeleted"),
    @JsonSubTypes.Type(value = DatasetDeletedEvent.class, name = "DatasetDeleted"),
    @JsonSubTypes.Type(value = DatasetCreatedEvent.class, name = "DatasetCreated"),
    @JsonSubTypes.Type(value = BranchMergedEvent.class, name = "BranchMerged")
})
public sealed interface VersionControlEvent
    permits BranchCreatedEvent,
        CommitCreatedEvent,
        TagCreatedEvent,
        BranchResetEvent,
        RevertCreatedEvent,
        SnapshotCreatedEvent,
        CherryPickedEvent,
        BranchRebasedEvent,
        CommitsSquashedEvent,
        BatchGraphsCompletedEvent,
        BranchDeletedEvent,
        DatasetDeletedEvent,
        DatasetCreatedEvent,
        BranchMergedEvent {

  /**
   * Returns the globally unique event ID.
   * Used for deduplication in projectors to achieve exactly-once processing
   * semantics with at-least-once delivery.
   *
   * <p>Event IDs are UUIDv7 (time-ordered) generated at event creation time.
   * The projector uses this ID to detect and skip duplicate events that may
   * be re-delivered by Kafka due to consumer rebalancing or retries.
   *
   * @return the event ID (UUIDv7 format)
   */
  String eventId();

  /**
   * Gets the timestamp when this event occurred.
   *
   * @return the event timestamp
   */
  Instant timestamp();

  /**
   * Gets the dataset name this event applies to.
   *
   * @return the dataset name
   */
  String dataset();

  /**
   * Returns the aggregate identity for partitioning.
   * All events for the same aggregate instance must return the same partition key
   * to ensure ordered processing in Kafka.
   *
   * <p>This method implements the CQRS/Event Sourcing best practice:
   * "Key = Aggregate-ID" - ensuring all events for an aggregate instance land
   * in the same partition for guaranteed ordering.
   *
   * @return the aggregate identity
   */
  AggregateIdentity getAggregateIdentity();
}
