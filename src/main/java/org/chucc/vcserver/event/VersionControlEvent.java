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
    @JsonSubTypes.Type(value = SnapshotCreatedEvent.class, name = "SnapshotCreated")
})
public sealed interface VersionControlEvent
    permits BranchCreatedEvent,
        CommitCreatedEvent,
        TagCreatedEvent,
        BranchResetEvent,
        RevertCreatedEvent,
        SnapshotCreatedEvent {

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
}
