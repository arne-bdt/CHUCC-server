# ADR-0003: Projector Fail-Fast Exception Handling

## Status

**Implemented** (2025-10-23)

## Context

The `ReadModelProjector` consumes events from Kafka and updates in-memory repositories (branches, commits, graphs).
When event projection fails, we must choose between:

1. **Swallow exception** + commit offset → Silent failure, permanent inconsistency
2. **Rethrow exception** + no commit → Kafka retry/DLQ, eventual consistency preserved

## Decision

**Rethrow all exceptions** to trigger Kafka retry/DLQ mechanisms.

## Rationale

### Why Rethrowing is Critical

1. **Prevents Silent Failures:**
   - If exception is swallowed and offset commits, event is lost forever
   - Read model becomes inconsistent with event stream
   - No recovery mechanism (event cannot be replayed)
   - Queries return incorrect results with no error indication

2. **Leverages Kafka Retry/DLQ:**
   - Transient failures (network, resources) → automatic retry
   - Poison events (bugs, malformed data) → dead letter queue
   - Operations team can investigate and fix DLQ entries

3. **Maintains Eventual Consistency:**
   - Retried events are deduplicated (by event ID)
   - System eventually consistent after transient failures resolve
   - Strong consistency guarantee: **read model = event stream**

### Alternatives Considered

#### ❌ Alternative 1: Swallow + Log

```java
catch (Exception ex) {
  logger.error("Failed to process event", ex);
  // Offset commits → event lost!
}
```

**Rejected:** Creates permanent inconsistency. Event lost forever, read model diverges from event stream.

#### ❌ Alternative 2: Swallow + Dead Letter Queue

```java
catch (Exception ex) {
  sendToDeadLetterQueue(event);
  // Offset commits → read model missing this event
}
```

**Rejected:** Read model still inconsistent (missing event). Better than Alternative 1, but still wrong.

#### ✅ Alternative 3: Rethrow (Current Implementation)

```java
catch (Exception ex) {
  logger.error("Failed to process event", ex);
  throw new ProjectionException("Failed to project event", ex);
  // Offset NOT committed → Kafka retries
}
```

**Accepted:** Prevents inconsistency, leverages Kafka retry/DLQ, eventual consistency guaranteed.

## Consequences

### Positive

- ✅ No silent failures (all errors visible in logs)
- ✅ Automatic retry for transient issues (network, temporary resource exhaustion)
- ✅ DLQ for poison events (investigation required)
- ✅ Read model consistency guaranteed (read model = event stream)
- ✅ Deduplication handles retried events correctly (idempotent projection)

### Negative

- ⚠️ Poison event blocks consumer until moved to DLQ
- ⚠️ Requires proper Kafka retry/DLQ configuration
- ⚠️ Transient failures cause temporary delays in projection

### Mitigation

1. **Deduplication:** Events are deduplicated by `eventId` to handle Kafka retries
   - LRU cache of processed event IDs (configurable size)
   - Retried events skip processing if already seen

2. **DLQ Configuration:** Spring Kafka `DefaultErrorHandler` configured for retry + DLQ
   - Poison events automatically sent to DLQ after max retries
   - Operations team alerted on DLQ entries

3. **Monitoring:**
   - Log all projection failures with full context (event ID, dataset, exception)
   - Alert on DLQ entries for investigation
   - Track deduplication cache hit rate

## Implementation

### Code Location

**File:** [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

**Key Methods:**
- `handleEvent()` - Kafka listener that catches and rethrows exceptions (lines 162-215)
- `ProjectionException` - Custom exception for projection failures (inner class, lines 707-716)

### Exception Handling Flow

```java
@KafkaListener(topicPattern = "vc\\..*\\.events", ...)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  try {
    // 1. Extract event and headers
    VersionControlEvent event = record.value();

    // 2. Deduplication check
    if (alreadyProcessed(event.eventId())) {
      logger.warn("Skipping duplicate event: {}", event.eventId());
      return;  // Idempotent - skip retried event
    }

    // 3. Process event (switch on event type)
    switch (event) {
      case CommitCreatedEvent e -> handleCommitCreated(e);
      case BranchCreatedEvent e -> handleBranchCreated(e);
      // ... other event types
    }

    // 4. Mark as processed
    processedEventIds.put(event.eventId(), Boolean.TRUE);

  } catch (Exception ex) {
    // 5. Log error with full context
    logger.error("Failed to project event: {} (id={}) for dataset: {}",
        event.getClass().getSimpleName(), event.eventId(), event.dataset(), ex);

    // 6. CRITICAL: Rethrow to prevent offset commit
    throw new ProjectionException("Failed to project event", ex);
  }
}
```

### Kafka Configuration

**File:** [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java)

**Consumer Configuration (lines 183-191):**
```java
// Manual commit mode: Offsets are committed ONLY after successful event processing.
// Combined with ReadModelProjector's exception rethrowing, this ensures:
// - Failed events: offset NOT committed → Kafka retries delivery
// - Successful events: offset committed → event acknowledged
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
```

**Listener Factory Configuration (lines 213-216):**
```java
// Manual commit mode: commit offset after each successful event processing
// This ensures failed events trigger retry instead of being lost
factory.getContainerProperties()
    .setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
```

**Deduplication Configuration:**

**File:** [ProjectorProperties.java](../../src/main/java/org/chucc/vcserver/config/ProjectorProperties.java)

```java
@ConfigurationProperties(prefix = "projector")
public class ProjectorProperties {
  private Deduplication deduplication = new Deduplication();

  public static class Deduplication {
    private boolean enabled = true;
    private int cacheSize = 10000;  // LRU eviction
  }
}
```


## Testing Strategy

**Unit Tests:**

**File:** [ReadModelProjectorExceptionHandlingTest.java](../../src/test/java/org/chucc/vcserver/projection/ReadModelProjectorExceptionHandlingTest.java)

Tests that verify exception propagation:
- `handleEvent_whenRepositoryFails_shouldRethrowAsProjectionException` - Repository exceptions wrapped and rethrown
- `handleEvent_whenMalformedRdfPatch_shouldRethrowAsProjectionException` - Invalid RDF syntax triggers ProjectionException
- `handleEvent_whenNullPointerException_shouldRethrowAsProjectionException` - Unexpected errors properly wrapped

**Integration Tests:**
- `ReadModelProjectorIT` - Verifies basic projection works with manual commit
- `GraphEventProjectorIT` - Tests GSP event handlers end-to-end
- `VersionControlProjectorIT` - Tests VC event handlers with async verification
- `ReadModelProjectorKillRestartIT` - Tests recovery after projector restart

**Test Results:**
- ✅ 1018 tests pass (712 unit + 306 integration)
- ✅ Zero Checkstyle, SpotBugs, PMD violations

## Related Decisions

- ADR-0001: CQRS + Event Sourcing Architecture (to be created)
- ADR-0002: Kafka as Event Store (to be created)

## References

- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - Implementation with fail-fast exception handling
- [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java) - Manual commit configuration
- [ReadModelProjectorExceptionHandlingTest.java](../../src/test/java/org/chucc/vcserver/projection/ReadModelProjectorExceptionHandlingTest.java) - Unit tests
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html) - Official docs
- [CQRS + Event Sourcing Guide](../cqrs-event-sourcing.md) - Architecture overview
- [Task: Fix Kafka Manual Commit](../../../.tasks/projector-exception-handling/01-fix-kafka-manual-commit.md) - Implementation task

## Date

2025-10-23

## Implementation History

- **2025-10-23:** Initial ADR draft based on existing code analysis
- **2025-10-23:** Implemented manual commit configuration (commit `5df63e0`)
  - Disabled auto-commit in consumer configuration
  - Added AckMode.RECORD in listener factory
  - Created unit tests for exception propagation
  - Updated documentation

## Authors

- Claude (implementation and documentation)
