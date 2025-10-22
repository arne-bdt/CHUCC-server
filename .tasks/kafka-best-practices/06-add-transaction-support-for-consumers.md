# Task: Add Transaction Support for Consumers

**Status:** Not Started
**Priority:** 🟡 **MEDIUM**
**Estimated Time:** 2 hours
**Dependencies:** 01-fix-partition-key-strategy.md (recommended)

---

## Context

**MISSING BEST PRACTICE:** Consumers should use `isolation.level=read_committed` to only read committed transactions, preventing reading uncommitted events.

### Current Implementation

[KafkaConfig.java:153-192](src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L153-L192):

```java
public ConsumerFactory<String, VersionControlEvent> consumerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  // ...
  configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

  // ❌ Missing: isolation.level=read_committed
  return new DefaultKafkaConsumerFactory<>(configProps);
}
```

**Current State:**
- Producer supports transactions (`transactional.id` available)
- Producer has idempotence enabled ✅
- Consumer does NOT have `isolation.level=read_committed` ❌

### From German Checklist

> **Consumer (Leseseite/Projektionen):**
> - Für atomare Multi-Topic-Writes (z. B. Event + Outbox): **Transaktionen** nutzen (`transactional.id`), Consumer dann `isolation.level=read_committed`.

**Translation:** Use transactions for atomic multi-topic writes, consumers should use `isolation.level=read_committed`.

**Why This Matters:**

**Without `read_committed`:**
```
T1: Producer starts transaction
T2: Producer writes Event 1 to Kafka (uncommitted)
T3: Consumer reads Event 1 ❌ (reads uncommitted!)
T4: Producer transaction fails, rollback
T5: Event 1 never committed → Consumer processed invalid event!
```

**With `read_committed`:**
```
T1: Producer starts transaction
T2: Producer writes Event 1 to Kafka (uncommitted)
T3: Consumer tries to read → Blocked until commit
T4: Producer commits transaction
T5: Consumer reads Event 1 ✅ (only reads committed)
```

---

## Goal

Configure consumers to only read committed messages:
1. ✅ Set `isolation.level=read_committed`
2. ✅ Enable transactional producer (optional, already available)
3. ✅ Test transaction rollback behavior

---

## Design Decisions

### 1. Isolation Level

**Chosen:** `read_committed`

**Options:**
| Level | Behavior | Use Case |
|-------|----------|----------|
| `read_uncommitted` | Read all messages (default) | ❌ Not safe with transactions |
| `read_committed` | Only read committed messages | ✅ Safe with transactions |

**Chosen:** `read_committed` (safe default)

### 2. Transaction Scope

**Current:** Transactions already configured but optional

**EventPublisher has two methods:**
- `publish(event)` - Non-transactional (default)
- `publishTransactional(event)` - Transactional

**Decision:** Keep both methods, but default consumers to `read_committed` for safety.

---

## Implementation Plan

### Step 1: Update Consumer Configuration (15 min)

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`

```java
@Bean
public ConsumerFactory<String, VersionControlEvent> consumerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.getBootstrapServers());
  configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "read-model-projector");
  configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
      StringDeserializer.class);
  configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
      JsonDeserializer.class);

  // ... existing config

  // ✅ NEW: Only read committed transactions
  configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

  // Auto-commit enabled (at-least-once delivery)
  configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
  configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);

  return new DefaultKafkaConsumerFactory<>(configProps);
}
```

---

### Step 2: Update application.yml (10 min)

**application.yml:**
```yaml
spring:
  kafka:
    consumer:
      properties:
        # Only read committed transactions
        isolation.level: read_committed
```

**application-prod.yml:**
```yaml
spring:
  kafka:
    consumer:
      properties:
        isolation.level: read_committed

    producer:
      # Enable transactions for critical operations
      transaction-id-prefix: vc-server-tx-
```

---

### Step 3: Document When to Use Transactional Publishing (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

Add Javadoc:

```java
/**
 * Publishes a version control event to Kafka (non-transactional).
 *
 * <p>Use this method for:
 * <ul>
 *   <li>Single event publishing (most common)</li>
 *   <li>Standard operations (commit, branch, merge)</li>
 *   <li>Best performance (no transaction overhead)</li>
 * </ul>
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Idempotent (no duplicates from retries)</li>
 *   <li>At-least-once delivery</li>
 *   <li>Consumers read immediately (no transaction delay)</li>
 * </ul>
 *
 * @param event the event to publish
 * @return CompletableFuture with send result
 */
public CompletableFuture<SendResult<String, VersionControlEvent>> publish(
    VersionControlEvent event) {
  // ... existing implementation
}

/**
 * Publishes a version control event within a Kafka transaction.
 * Ensures exactly-once semantics and atomic publishing.
 *
 * <p>Use this method for:
 * <ul>
 *   <li>Multi-event atomic operations (e.g., batch commits)</li>
 *   <li>Coordinated writes across multiple topics</li>
 *   <li>Critical operations requiring rollback capability</li>
 * </ul>
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Exactly-once semantics (all-or-nothing)</li>
 *   <li>Atomic commits (transaction commit/rollback)</li>
 *   <li>Consumers (with read_committed) only see committed events</li>
 * </ul>
 *
 * <p><b>Important:</b> Consumers must use {@code isolation.level=read_committed}
 * to benefit from transaction guarantees.
 *
 * <p><b>Performance:</b> Slightly slower than non-transactional due to
 * transaction overhead (~5-10ms per transaction).
 *
 * @param event the event to publish
 * @return CompletableFuture with send result
 */
public CompletableFuture<SendResult<String, VersionControlEvent>> publishTransactional(
    VersionControlEvent event) {
  // ... existing implementation
}
```

---

### Step 4: Add Transaction Example for Batch Operations (20 min)

**Use Case:** Batch graph operations should be atomic

**File:** `src/main/java/org/chucc/vcserver/command/BatchGraphsCommandHandler.java`

```java
@Component
public class BatchGraphsCommandHandler {

  private final EventPublisher eventPublisher;

  public BatchGraphsCompletedEvent handle(PutGraphsCommand command) {
    List<CommitCreatedEvent> commitEvents = new ArrayList<>();

    // Create all commit events
    for (GraphOperation op : command.operations()) {
      CommitCreatedEvent commitEvent = createCommitEvent(op);
      commitEvents.add(commitEvent);
    }

    // Create batch completion event
    BatchGraphsCompletedEvent batchEvent = BatchGraphsCompletedEvent.create(
        command.dataset(),
        commitEvents,
        Instant.now()
    );

    // ✅ USE TRANSACTION: Ensure all-or-nothing semantics
    // If any event fails to publish, entire batch is rolled back
    eventPublisher.publishTransactional(batchEvent);

    logger.info("Published batch of {} commits transactionally",
        commitEvents.size());

    return batchEvent;
  }
}
```

---

### Step 5: Testing (40 min)

**Test 1: Consumer Blocks Until Transaction Commits**

```java
@SpringBootTest
@Testcontainers
class TransactionIT {

  @Test
  void consumer_shouldNotReadUncommittedEvents() throws Exception {
    // Arrange: Start transaction (don't commit yet)
    kafkaTemplate.executeInTransaction(ops -> {
      CommitCreatedEvent event = CommitCreatedEvent.create(...);

      // Act: Publish event (uncommitted)
      ops.send("vc.default.events", event);

      // Wait to allow consumer to try reading
      Thread.sleep(2000);

      // Assert: Consumer should NOT have processed event yet
      Optional<Commit> commit = commitRepository.findById(...);
      assertThat(commit).isEmpty();  // ✅ Not processed (uncommitted)

      // Do NOT commit transaction (return null = rollback)
      return null;
    });

    // Assert: Event never committed → Consumer never sees it
    Thread.sleep(2000);
    Optional<Commit> commit = commitRepository.findById(...);
    assertThat(commit).isEmpty();  // ✅ Still not processed
  }

  @Test
  void consumer_shouldReadCommittedEvents() throws Exception {
    // Arrange & Act: Publish transactionally and COMMIT
    kafkaTemplate.executeInTransaction(ops -> {
      CommitCreatedEvent event = CommitCreatedEvent.create(...);
      ops.send("vc.default.events", event).get();

      // Commit transaction (return non-null)
      return true;
    });

    // Assert: Consumer should process event after commit
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> commit = commitRepository.findById(...);
          assertThat(commit).isPresent();  // ✅ Processed after commit
        });
  }

  @Test
  void batchOperation_shouldBeAtomic() throws Exception {
    // Arrange: Create batch with 3 operations
    List<GraphOperation> operations = List.of(op1, op2, op3);
    PutGraphsCommand command = new PutGraphsCommand(..., operations);

    // Act: Publish batch transactionally
    batchGraphsCommandHandler.handle(command);

    // Assert: Wait for all events to be processed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // All 3 commits should be present (atomic - all or nothing)
          assertThat(commitRepository.findById(commit1)).isPresent();
          assertThat(commitRepository.findById(commit2)).isPresent();
          assertThat(commitRepository.findById(commit3)).isPresent();
        });
  }
}
```

---

### Step 6: Update Documentation (20 min)

**Update:** `docs/architecture/cqrs-event-sourcing.md`

```markdown
## Transaction Support

### Consumer Isolation Level

**Configuration:** Consumers use `isolation.level=read_committed`

**Why:**
- ✅ Only read committed events (no uncommitted data)
- ✅ Protects against aborted transactions
- ✅ Safe default for event sourcing

**Behavior:**

Without `read_committed`:
```
Producer starts transaction
Producer writes Event 1 (uncommitted)
Consumer reads Event 1 ❌ (uncommitted!)
Producer aborts transaction
Consumer processed invalid event!
```

With `read_committed`:
```
Producer starts transaction
Producer writes Event 1 (uncommitted)
Consumer waits... (blocked until commit)
Producer commits transaction
Consumer reads Event 1 ✅ (committed)
```

### Transactional Publishing

**Use `publishTransactional()` for:**
- ✅ Batch operations (all-or-nothing)
- ✅ Multi-topic writes (coordinated)
- ✅ Critical operations requiring rollback

**Use `publish()` for:**
- ✅ Single event publishing (most common)
- ✅ Best performance (no transaction overhead)

**Example: Batch Operations**
```java
// ✅ Transactional: All commits published atomically
eventPublisher.publishTransactional(batchEvent);

// All commits succeed or all fail (no partial batch)
```

**Performance Impact:**
- Non-transactional: ~5-10ms per event
- Transactional: ~10-20ms per event (~2x slower)
- Use transactions only when atomicity is required
```

---

## Success Criteria

- [ ] Consumer configured with `isolation.level=read_committed`
- [ ] application.yml updated with consumer isolation config
- [ ] EventPublisher Javadoc updated (when to use transactions)
- [ ] Batch operations use transactional publishing
- [ ] Integration tests verify transaction behavior (3+ tests)
- [ ] Documentation updated
- [ ] All tests pass

---

## Performance Impact

**Throughput:**
- Non-transactional: ~100,000 events/sec
- Transactional: ~50,000 events/sec (~50% reduction)

**Latency:**
- Non-transactional: ~5-10ms
- Transactional: ~10-20ms (~2x slower)

**Recommendation:** Use transactions only when atomicity is required.

---

## References

- German Kafka CQRS/ES Checklist
- [Kafka Transactions](https://kafka.apache.org/documentation/#transactions)
- [EventPublisher.java](src/main/java/org/chucc/vcserver/event/EventPublisher.java)

---

## Notes

**Complexity:** Low
**Time:** 2 hours
**Risk:** Low (safe configuration change)

This ensures consumers never read uncommitted events - critical for data integrity.
