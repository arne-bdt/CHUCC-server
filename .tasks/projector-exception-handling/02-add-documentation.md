# Task 02: Add Documentation for Exception Handling Strategy

## Objective

Document the exception handling strategy to ensure future developers understand why exceptions are rethrown and how to maintain this critical behavior.

## Current State

- Exception rethrowing is implemented but not explicitly documented
- No architectural decision record (ADR) explaining the choice
- CQRS guide doesn't explain failure handling strategy
- Code comments don't explain the "why" behind rethrowing

## Implementation Steps

### 1. Enhance Code Documentation

Update `ReadModelProjector.java`:

```java
/**
 * Read-model projector that rebuilds in-memory graphs by consuming
 * version control events from Kafka and applying RDF Patches in order.
 *
 * <h2>Exception Handling Strategy</h2>
 * <p>This projector uses a <strong>fail-fast</strong> approach to maintain
 * read model consistency with the event stream:
 * <ul>
 *   <li><strong>Exceptions are rethrown</strong> to trigger Kafka retry/DLQ</li>
 *   <li><strong>Kafka offset is NOT committed</strong> on failure</li>
 *   <li><strong>Silent failures are prevented</strong> (no inconsistent state)</li>
 * </ul>
 *
 * <p><strong>Why rethrowing matters:</strong>
 * <ul>
 *   <li>Silent failures create permanent read model inconsistencies</li>
 *   <li>If offset commits after failure, event is lost forever</li>
 *   <li>Read model diverges from event stream with no recovery</li>
 * </ul>
 *
 * <h2>Failure Scenarios</h2>
 * <ul>
 *   <li><strong>Transient failures:</strong> Kafka retries (network, temporary resource issues)</li>
 *   <li><strong>Poison events:</strong> Dead Letter Queue (malformed data, bugs)</li>
 *   <li><strong>Deduplication:</strong> Retried events are detected and skipped</li>
 * </ul>
 *
 * <p>On startup, consumes from earliest offset to build state.
 * Maintains (dataset, branch) → commitId mappings and materialized graphs.
 * Applies patches to the branch's dataset in order.
 *
 * @see ProjectionException
 * @see ProjectorProperties
 */
@Service
public class ReadModelProjector {
  // ...
}
```

Add to `handleEvent()` method:

```java
/**
 * Kafka listener for version control events.
 * Consumes events from all dataset topics matching the pattern.
 * Extracts correlation ID from event headers for distributed tracing.
 *
 * <p><strong>Exception Handling:</strong>
 * Any exception during projection is caught, logged, and <strong>rethrown</strong>
 * as {@link ProjectionException}. This triggers Kafka's retry/DLQ mechanism and
 * prevents offset commit, ensuring no event is silently lost.
 *
 * <p><strong>Deduplication:</strong>
 * Events are deduplicated by event ID to handle Kafka retries correctly.
 * If an event fails and is retried, it will be skipped if already processed.
 *
 * @param record the Kafka consumer record containing event and headers
 * @throws ProjectionException if event projection fails (triggers Kafka retry)
 */
@KafkaListener(...)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  // ...
}
```

### 2. Create Architectural Decision Record (ADR)

Create `docs/architecture/decisions/0003-projector-fail-fast-exception-handling.md`:

```markdown
# ADR-0003: Projector Fail-Fast Exception Handling

## Status

Accepted

## Context

The `ReadModelProjector` consumes events from Kafka and updates in-memory repositories.
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

2. **Leverages Kafka Retry/DLQ:**
   - Transient failures (network, resources) → automatic retry
   - Poison events (bugs, malformed data) → dead letter queue
   - Operations team can investigate and fix

3. **Maintains Eventual Consistency:**
   - Retried events are deduplicated (by event ID)
   - System eventually consistent after transient failures resolve
   - Strong consistency guarantee: read model = event stream

### Alternatives Considered

❌ **Swallow + Log:**
```java
catch (Exception ex) {
  logger.error("Failed to process event", ex);
  // Offset commits → event lost!
}
```
**Rejected:** Creates permanent inconsistency.

❌ **Swallow + Dead Letter Queue:**
```java
catch (Exception ex) {
  sendToDeadLetterQueue(event);
  // Offset commits → read model missing this event
}
```
**Rejected:** Read model still inconsistent (missing event).

✅ **Rethrow (Current):**
```java
catch (Exception ex) {
  logger.error("Failed to process event", ex);
  throw new ProjectionException("Failed to project event", ex);
  // Offset NOT committed → Kafka retries
}
```
**Accepted:** Prevents inconsistency, leverages Kafka mechanisms.

## Consequences

### Positive

- ✅ No silent failures (all errors visible)
- ✅ Automatic retry for transient issues
- ✅ DLQ for poison events
- ✅ Read model consistency guaranteed

### Negative

- ⚠️ Poison event blocks consumer until moved to DLQ
- ⚠️ Requires proper Kafka retry/DLQ configuration
- ⚠️ Transient failures cause temporary delays

### Mitigation

1. **Deduplication:** Events are deduplicated by ID to handle retries
2. **DLQ Configuration:** Poison events automatically sent to DLQ after max retries
3. **Monitoring:** Alert on DLQ entries for investigation

## Implementation

- `ReadModelProjector.handleEvent()` catches and rethrows as `ProjectionException`
- Spring Kafka `DefaultErrorHandler` handles retry logic
- Deduplication cache prevents duplicate processing of retried events

## References

- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [CQRS + Event Sourcing Guide](../cqrs-event-sourcing.md)
```

### 3. Update CQRS Guide

Add section to `docs/architecture/cqrs-event-sourcing.md`:

```markdown
## Projector Exception Handling

### Fail-Fast Strategy

The `ReadModelProjector` uses a **fail-fast** approach to maintain read model consistency:

1. **Exceptions are rethrown** (not swallowed)
2. **Kafka offset is NOT committed** on failure
3. **No silent failures** (all errors visible)

### Why This Matters

**Silent failures create permanent inconsistencies:**
```java
// ❌ DANGEROUS: Silent failure
catch (Exception ex) {
  logger.error("Failed", ex);
  // Offset commits → event lost forever!
}
```

**Rethrowing enables recovery:**
```java
// ✅ CORRECT: Rethrow for retry
catch (Exception ex) {
  logger.error("Failed", ex);
  throw new ProjectionException("...", ex);
  // Offset NOT committed → Kafka retries
}
```

### Failure Scenarios

| Failure Type | Kafka Behavior | Read Model Impact |
|--------------|---------------|-------------------|
| Transient (network) | Automatic retry | Eventually consistent |
| Poison event (bug) | Dead Letter Queue | Operations alerted |
| Duplicate event | Deduplication skips | No impact (idempotent) |

### Configuration

Retry/DLQ behavior configured in `application.yml`:

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Manual commit only on success
    listener:
      ack-mode: record           # Commit per-record (fail-fast)

projector:
  deduplication:
    enabled: true
    cache-size: 10000  # LRU cache for retried events
```

See [ADR-0003](./decisions/0003-projector-fail-fast-exception-handling.md) for details.
```

### 4. Add Configuration Comments

Update `application.yml`:

```yaml
spring:
  kafka:
    consumer:
      # CRITICAL: Disable auto-commit to prevent silent failures
      # Offset only commits when event processing succeeds
      # Failed events trigger retry (offset NOT committed)
      enable-auto-commit: false

    listener:
      # CRITICAL: Per-record ACK for fail-fast behavior
      # If event processing throws exception:
      #   1. Exception logged
      #   2. Offset NOT committed
      #   3. Kafka retries event (or sends to DLQ)
      # This prevents silent read model inconsistencies
      ack-mode: record

projector:
  deduplication:
    # Enable deduplication to handle retried events correctly
    # Kafka may retry events after transient failures
    # This cache prevents duplicate processing
    enabled: true
    cache-size: 10000  # LRU cache of processed event IDs
```

## Acceptance Criteria

- [ ] `ReadModelProjector.java` class Javadoc explains exception handling strategy
- [ ] `handleEvent()` method Javadoc documents rethrowing behavior
- [ ] ADR-0003 created explaining decision rationale
- [ ] CQRS guide updated with failure handling section
- [ ] `application.yml` commented explaining Kafka configuration
- [ ] All documentation accurate and consistent
- [ ] Zero quality violations

## Files to Modify/Create

- Modify: `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`
- Create: `docs/architecture/decisions/0003-projector-fail-fast-exception-handling.md`
- Modify: `docs/architecture/cqrs-event-sourcing.md`
- Modify: `src/main/resources/application.yml`
- Create: `docs/architecture/decisions/` (directory if doesn't exist)

## Estimated Time

1-2 hours
