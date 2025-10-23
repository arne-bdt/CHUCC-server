# Task: Implement Event Deduplication (At-Least-Once with Idempotency)

**Status:** ✅ **COMPLETED**
**Priority:** 🔴 **CRITICAL**
**Completed:** 2025-10-23
**Actual Time:** ~4 hours
**Dependencies:** 01-fix-partition-key-strategy.md (recommended, but not blocking)

---

## Context

**CRITICAL ISSUE:** CHUCC Server uses **at-least-once** delivery (Kafka's default) **without deduplication**, violating CQRS/Event Sourcing best practices.

### Current Implementation Problem

[KafkaConfig.java:184](src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L184):
```java
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true); // At-least-once
```

[ReadModelProjector.java:106](src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L106):
```java
public void handleEvent(VersionControlEvent event) {
  // ❌ No deduplication check!
  switch (event) {
    case CommitCreatedEvent e -> handleCommitCreated(e);
    // ... process event directly
  }
}
```

### Why This Is Wrong

From German Kafka CQRS/ES best practices:

> **Consumer (Leseseite/Projektionen):**
> - Mindestens *at-least-once*; dedupliziere per `eventId` in der Projektion.

**Translation:** Use at-least-once delivery, but **deduplicate using eventId** in the projector.

**Current Problems:**

1. **No eventId in Events**
   - Events lack unique IDs
   - Cannot track which events were processed
   - Duplicate processing detection impossible

2. **No Deduplication Logic**
   - ReadModelProjector processes every event blindly
   - Network retries → duplicate processing
   - Consumer rebalances → duplicate processing

3. **Idempotency Not Guaranteed**
   - Some operations ARE naturally idempotent (save commit)
   - Some operations are NOT idempotent (increment counter, append to list)

### Impact

**Duplicate Event Processing Scenarios:**

**Scenario 1: Kafka Producer Retry**
```
T1: EventPublisher publishes CommitCreatedEvent
T2: Kafka receives event, ACKs
T3: Network glitch - ACK lost
T4: Producer retries (idempotent producer prevents this)
    ✅ Kafka deduplicates (same event not written twice)

BUT: Consumer may still see duplicates from other sources...
```

**Scenario 2: Consumer Rebalance**
```
T1: Consumer processes event, updates repository
T2: Consumer crashes BEFORE committing offset
T3: Consumer restarts, Kafka re-delivers event
T4: Event processed AGAIN → duplicate!
    ❌ Repository updated twice
```

**Scenario 3: Manual Offset Reset**
```
T1: Admin resets consumer offsets to earlier position
T2: Consumer re-reads events
T3: Events processed again → duplicates!
    ❌ Repositories corrupted
```

**Real-World Failure Examples:**

**Example 1: Duplicate Commit**
```java
// First processing
commitRepository.save(dataset, commit);  // ✅ Commit saved

// Duplicate processing (consumer rebalance)
commitRepository.save(dataset, commit);  // ✅ Overwrite same commit (idempotent - OK)
```
→ This is safe (naturally idempotent)

**Example 2: Duplicate Branch Creation** (hypothetical counter example)
```java
// If we tracked branch creation count
branchCreationCount.incrementAndGet();  // Count = 1

// Duplicate processing
branchCreationCount.incrementAndGet();  // Count = 2 ❌ Wrong!
```
→ This is NOT safe (not idempotent)

---

## Goal

Implement **event deduplication** using `eventId` to achieve **exactly-once processing semantics** with at-least-once delivery.

**Design Principle:**

```
At-Least-Once Delivery + Idempotent Handlers + Deduplication = Exactly-Once Processing
```

**Requirements:**

1. ✅ Add unique `eventId` to all events
2. ✅ Track processed event IDs in ReadModelProjector
3. ✅ Skip already-processed events
4. ✅ Efficient storage (memory + optional persistence)
5. ✅ Handle consumer restarts (rebuild processed set from events)

---

## Design Decisions

### 1. EventId Generation Strategy

**Chosen: UUIDv7 (Time-Ordered UUID)**

```java
String eventId = UuidCreator.getTimeOrderedEpoch().toString();
```

**Why UUIDv7:**
- ✅ **Time-ordered**: Events naturally sorted by creation time
- ✅ **Globally unique**: No coordination needed
- ✅ **Embedded timestamp**: Can extract event age
- ✅ **Deterministic in tests**: Can use fixed UUIDs for testing

**Alternatives considered:**
- ❌ Auto-increment: Requires coordination (distributed counter)
- ❌ Timestamp + hash: Collision risk
- ❌ UUIDv4: Random, not time-ordered
- ✅ UUIDv7: Best of both worlds

**Dependency:**
```xml
<dependency>
  <groupId>com.github.f4b6a3</groupId>
  <artifactId>uuid-creator</artifactId>
  <version>5.3.7</version>
</dependency>
```

### 2. Processed Events Storage

**Chosen: In-Memory Set with Bounded Size (LRU Eviction)**

**Option A: Unbounded In-Memory Set** (Simple)
```java
private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

public void handleEvent(VersionControlEvent event) {
  if (processedEventIds.contains(event.getEventId())) {
    return; // Skip duplicate
  }
  processEvent(event);
  processedEventIds.add(event.getEventId());
}
```

**Pros:** Simple, fast (O(1) lookup)
**Cons:** **Unbounded growth** → OutOfMemoryError

**Option B: Bounded LRU Cache** (Recommended)
```java
private final Cache<String, Boolean> processedEventIds = Caffeine.newBuilder()
    .maximumSize(100_000)  // Keep last 100k event IDs
    .build();

public void handleEvent(VersionControlEvent event) {
  if (processedEventIds.getIfPresent(event.getEventId()) != null) {
    return; // Skip duplicate
  }
  processEvent(event);
  processedEventIds.put(event.getEventId(), Boolean.TRUE);
}
```

**Pros:** Bounded memory, automatic eviction
**Cons:** Old events may be forgotten (acceptable - duplicates are usually recent)

**Chosen Approach:** **Option B (Bounded LRU Cache)**

**Why:**
- Prevent OutOfMemoryError
- 100k event IDs ≈ 5-10 MB RAM (acceptable)
- Duplicates typically occur within seconds/minutes, not days
- Old duplicates (beyond 100k events) are rare edge cases

### 3. Persistence Strategy

**Chosen: No Persistence (Stateless on Restart)**

**Option A: Persist to Database** (Complex)
- Store processed event IDs in PostgreSQL/Redis
- Survive restarts
- **Cons:** Extra infrastructure, slower, complex

**Option B: Rebuild from Kafka** (Stateless)
- On startup, consume all events from beginning
- Populate processedEventIds set during replay
- **Pros:** Stateless, simple, leverages Kafka as source of truth

**Chosen Approach:** **Option B (Rebuild from Kafka)**

**Why:**
- ✅ Leverages existing event replay mechanism
- ✅ No additional infrastructure
- ✅ Consistent with Event Sourcing principles
- ✅ Already doing full replay on startup

**Trade-off:** Startup takes slightly longer (populate deduplication set), but this is acceptable.

### 4. Deduplication Scope

**Chosen: Per-Dataset Deduplication**

**Global Deduplication:**
```java
// Single set for all datasets
private final Cache<String, Boolean> processedEventIds;
```

**Per-Dataset Deduplication:**
```java
// Separate set per dataset
private final Map<String, Cache<String, Boolean>> processedEventIdsByDataset;
```

**Chosen Approach:** **Global Deduplication**

**Why:**
- Simpler implementation
- EventId is globally unique (UUIDv7)
- No need to scope by dataset
- Easier to reason about

---

## Implementation Plan

### Step 1: Add uuid-creator Dependency (5 min)

**File:** `pom.xml`

```xml
<dependencies>
  <!-- ... existing dependencies ... -->

  <!-- UUIDv7 for time-ordered event IDs -->
  <dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>5.3.7</version>
  </dependency>
</dependencies>
```

---

### Step 2: Add eventId to VersionControlEvent Interface (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/VersionControlEvent.java`

```java
public interface VersionControlEvent {
  /**
   * Returns the globally unique event ID.
   * Used for deduplication in projectors (at-least-once → exactly-once).
   *
   * @return the event ID (UUIDv7 format)
   */
  String getEventId();

  String dataset();
  AggregateIdentity getAggregateIdentity();
}
```

---

### Step 3: Add eventId Field to All Event Records (60 min)

Update all 12 event types to include `eventId` as the **first field**:

**Example: CommitCreatedEvent**

```java
public record CommitCreatedEvent(
    String eventId,  // ✅ NEW: First field
    String dataset,
    String commitId,
    List<String> parents,
    String branch,
    String author,
    String message,
    Instant timestamp,
    String rdfPatch
) implements VersionControlEvent {

  /**
   * Factory method: Create event with auto-generated eventId.
   */
  public static CommitCreatedEvent create(
      String dataset,
      String commitId,
      List<String> parents,
      String branch,
      String author,
      String message,
      Instant timestamp,
      String rdfPatch) {
    return new CommitCreatedEvent(
        UuidCreator.getTimeOrderedEpoch().toString(),  // Generate eventId
        dataset,
        commitId,
        parents,
        branch,
        author,
        message,
        timestamp,
        rdfPatch
    );
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    if (branch != null) {
      return AggregateIdentity.branch(dataset, branch);
    }
    return AggregateIdentity.commit(dataset, commitId);
  }
}
```

**Apply to all 12 event types:**

1. ✅ BranchCreatedEvent
2. ✅ BranchResetEvent
3. ✅ BranchRebasedEvent
4. ✅ BranchDeletedEvent
5. ✅ CommitCreatedEvent
6. ✅ TagCreatedEvent
7. ✅ RevertCreatedEvent
8. ✅ SnapshotCreatedEvent
9. ✅ CherryPickedEvent
10. ✅ CommitsSquashedEvent
11. ✅ BatchGraphsCompletedEvent
12. ✅ DatasetDeletedEvent

**Pattern for all:**
```java
public record XxxEvent(
    String eventId,  // First field
    // ... other fields
) implements VersionControlEvent {

  public static XxxEvent create(/* params without eventId */) {
    return new XxxEvent(
        UuidCreator.getTimeOrderedEpoch().toString(),
        // ... other params
    );
  }
}
```

---

### Step 4: Update Command Handlers to Use Factory Methods (30 min)

Replace direct event construction with factory methods:

**Before:**
```java
CommitCreatedEvent event = new CommitCreatedEvent(
    dataset,
    commitId,
    parents,
    branch,
    author,
    message,
    Instant.now(),
    patch.toString()
);
```

**After:**
```java
CommitCreatedEvent event = CommitCreatedEvent.create(
    dataset,
    commitId,
    parents,
    branch,
    author,
    message,
    Instant.now(),
    patch.toString()
);
```

**Files to Update (command handlers):**
- PutGraphCommandHandler
- PostGraphCommandHandler
- DeleteGraphCommandHandler
- CreateBranchCommandHandler
- ResetBranchCommandHandler
- CreateTagCommandHandler
- RevertCommitCommandHandler
- CherryPickCommandHandler
- SquashCommandHandler
- RebaseCommandHandler
- BatchGraphsCommandHandler
- DeleteBranchCommandHandler
- DeleteDatasetCommandHandler

**Estimated: 15-20 files to update**

---

### Step 5: Add eventId to Kafka Headers (10 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // Add eventId for deduplication
  headers.add(new RecordHeader(EventHeaders.EVENT_ID,
      event.getEventId().getBytes(StandardCharsets.UTF_8)));

  // Add aggregate metadata
  AggregateIdentity aggregateId = event.getAggregateIdentity();
  headers.add(new RecordHeader("aggregateType",
      aggregateId.getAggregateType().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader("aggregateId",
      aggregateId.getPartitionKey().getBytes(StandardCharsets.UTF_8)));

  // ... existing headers (dataset, event type, etc.)
}
```

**Update EventHeaders constant:**
```java
public final class EventHeaders {
  public static final String EVENT_ID = "eventId";
  public static final String DATASET = "dataset";
  public static final String EVENT_TYPE = "eventType";
  // ...
}
```

---

### Step 6: Implement Deduplication in ReadModelProjector (45 min)

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
@Service
public class ReadModelProjector {
  private static final Logger logger = LoggerFactory.getLogger(ReadModelProjector.class);

  // Deduplication cache: Store last 100k processed event IDs
  private final Cache<String, Boolean> processedEventIds = Caffeine.newBuilder()
      .maximumSize(100_000)
      .recordStats()  // Enable statistics for monitoring
      .build();

  // ... existing dependencies

  @KafkaListener(
      topicPattern = "vc\\..*\\.events",
      groupId = "${spring.kafka.consumer.group-id:read-model-projector}",
      containerFactory = "kafkaListenerContainerFactory",
      autoStartup = "${projector.kafka-listener.enabled:true}"
  )
  @Timed(value = "event.projector.processing", description = "Event processing time")
  @Counted(value = "event.projector.processed", description = "Events processed count")
  public void handleEvent(VersionControlEvent event) {
    logger.debug("Received event: {} (eventId={}) for dataset: {}",
        event.getClass().getSimpleName(), event.getEventId(), event.dataset());

    // ✅ DEDUPLICATION CHECK
    if (isEventProcessed(event.getEventId())) {
      logger.warn("Skipping duplicate event: {} (eventId={})",
          event.getClass().getSimpleName(), event.getEventId());
      recordDuplicateEvent(event);
      return;  // Skip processing
    }

    try {
      // Process event
      switch (event) {
        case CommitCreatedEvent e -> handleCommitCreated(e);
        case BranchCreatedEvent e -> handleBranchCreated(e);
        // ... other event types
      }

      // ✅ MARK AS PROCESSED (only after successful processing)
      markEventAsProcessed(event.getEventId());

      logger.info("Successfully projected event: {} (eventId={}) for dataset: {}",
          event.getClass().getSimpleName(), event.getEventId(), event.dataset());

    } catch (Exception ex) {
      logger.error("Failed to project event: {} (eventId={}) for dataset: {}",
          event.getClass().getSimpleName(), event.getEventId(), event.dataset(), ex);
      // ❌ DO NOT mark as processed on failure
      throw new ProjectionException("Failed to project event", ex);
    }
  }

  /**
   * Checks if event has already been processed.
   *
   * @param eventId the event ID
   * @return true if already processed, false otherwise
   */
  private boolean isEventProcessed(String eventId) {
    return processedEventIds.getIfPresent(eventId) != null;
  }

  /**
   * Marks event as processed.
   *
   * @param eventId the event ID
   */
  private void markEventAsProcessed(String eventId) {
    processedEventIds.put(eventId, Boolean.TRUE);
  }

  /**
   * Records duplicate event metrics.
   *
   * @param event the duplicate event
   */
  private void recordDuplicateEvent(VersionControlEvent event) {
    // Metrics for monitoring
    logger.warn("Duplicate event detected: type={}, eventId={}, dataset={}",
        event.getClass().getSimpleName(), event.getEventId(), event.dataset());

    // Could add Micrometer counter here:
    // meterRegistry.counter("event.projector.duplicates",
    //     "eventType", event.getClass().getSimpleName()).increment();
  }

  /**
   * Returns deduplication cache statistics.
   * Useful for monitoring and tuning cache size.
   */
  public CacheStats getDeduplicationStats() {
    return processedEventIds.stats();
  }
}
```

---

### Step 7: Add Deduplication Configuration Properties (10 min)

**File:** `src/main/java/org/chucc/vcserver/config/ProjectorProperties.java` (new)

```java
package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for ReadModelProjector.
 */
@Component
@ConfigurationProperties(prefix = "projector")
public class ProjectorProperties {

  private KafkaListener kafkaListener = new KafkaListener();
  private Deduplication deduplication = new Deduplication();

  public static class KafkaListener {
    private boolean enabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }

  public static class Deduplication {
    /**
     * Maximum number of event IDs to cache for deduplication.
     * Default: 100,000 event IDs (~5-10 MB RAM).
     */
    private int cacheSize = 100_000;

    /**
     * Enable/disable deduplication.
     * Default: true (always deduplicate).
     */
    private boolean enabled = true;

    public int getCacheSize() { return cacheSize; }
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
  }

  public KafkaListener getKafkaListener() { return kafkaListener; }
  public void setKafkaListener(KafkaListener kafkaListener) { this.kafkaListener = kafkaListener; }
  public Deduplication getDeduplication() { return deduplication; }
  public void setDeduplication(Deduplication deduplication) { this.deduplication = deduplication; }
}
```

**Update:** `application.yml`

```yaml
projector:
  kafka-listener:
    enabled: true
  deduplication:
    enabled: true          # Enable deduplication
    cache-size: 100000     # Max 100k event IDs in cache
```

**Update ReadModelProjector constructor to use config:**

```java
@Autowired
public ReadModelProjector(
    BranchRepository branchRepository,
    CommitRepository commitRepository,
    DatasetService datasetService,
    SnapshotService snapshotService,
    ProjectorProperties projectorProperties) {
  this.branchRepository = branchRepository;
  this.commitRepository = commitRepository;
  this.datasetService = datasetService;
  this.snapshotService = snapshotService;

  // Configure deduplication cache
  this.processedEventIds = Caffeine.newBuilder()
      .maximumSize(projectorProperties.getDeduplication().getCacheSize())
      .recordStats()
      .build();
}
```

---

### Step 8: Add Deduplication Metrics Endpoint (15 min)

**File:** `src/main/java/org/chucc/vcserver/controller/ActuatorMetricsController.java` (new)

```java
package org.chucc.vcserver.controller;

import org.chucc.vcserver.projection.ReadModelProjector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actuator endpoints for monitoring projector metrics.
 */
@RestController
@RequestMapping("/actuator")
public class ActuatorMetricsController {

  private final ReadModelProjector readModelProjector;

  public ActuatorMetricsController(ReadModelProjector readModelProjector) {
    this.readModelProjector = readModelProjector;
  }

  /**
   * GET /actuator/deduplication
   *
   * Returns deduplication cache statistics.
   */
  @GetMapping("/deduplication")
  public DeduplicationStats getDeduplicationStats() {
    var stats = readModelProjector.getDeduplicationStats();

    return new DeduplicationStats(
        stats.requestCount(),        // Total lookups
        stats.hitCount(),             // Cache hits
        stats.missCount(),            // Cache misses
        stats.hitRate(),              // Hit rate (0.0 to 1.0)
        stats.evictionCount()         // Evictions (LRU)
    );
  }

  public record DeduplicationStats(
      long requestCount,
      long hitCount,
      long missCount,
      double hitRate,
      long evictionCount
  ) {}
}
```

**Access:**
```bash
curl http://localhost:8080/actuator/deduplication

# Response:
{
  "requestCount": 50000,
  "hitCount": 125,
  "missCount": 49875,
  "hitRate": 0.0025,
  "evictionCount": 0
}
```

---

### Step 9: Write Unit Tests (45 min)

**Create:** `src/test/java/org/chucc/vcserver/projection/EventDeduplicationTest.java`

```java
package org.chucc.vcserver.projection;

import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SnapshotService;
import org.chucc.vcserver.config.ProjectorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EventDeduplicationTest {

  @Mock private BranchRepository branchRepository;
  @Mock private CommitRepository commitRepository;
  @Mock private DatasetService datasetService;
  @Mock private SnapshotService snapshotService;

  private ReadModelProjector projector;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    ProjectorProperties props = new ProjectorProperties();
    props.getDeduplication().setEnabled(true);
    props.getDeduplication().setCacheSize(100);

    projector = new ReadModelProjector(
        branchRepository,
        commitRepository,
        datasetService,
        snapshotService,
        props
    );
  }

  @Test
  void handleEvent_firstTime_shouldProcessEvent() {
    // Arrange
    CommitCreatedEvent event = CommitCreatedEvent.create(
        "default", "commit-1", List.of(), "main",
        "alice", "Test", Instant.now(), "A <s> <p> <o> ."
    );

    // Act
    projector.handleEvent(event);

    // Assert: Event processed
    verify(commitRepository).save(eq("default"), any(), any());
  }

  @Test
  void handleEvent_duplicate_shouldSkipProcessing() {
    // Arrange
    CommitCreatedEvent event = CommitCreatedEvent.create(
        "default", "commit-1", List.of(), "main",
        "alice", "Test", Instant.now(), "A <s> <p> <o> ."
    );

    // Act: Process same event twice
    projector.handleEvent(event);
    projector.handleEvent(event);  // Duplicate

    // Assert: Processed only once
    verify(commitRepository, times(1)).save(eq("default"), any(), any());
  }

  @Test
  void handleEvent_differentEventIds_shouldProcessBoth() {
    // Arrange
    CommitCreatedEvent event1 = CommitCreatedEvent.create(
        "default", "commit-1", List.of(), "main",
        "alice", "Test 1", Instant.now(), "A <s1> <p> <o> ."
    );

    CommitCreatedEvent event2 = CommitCreatedEvent.create(
        "default", "commit-2", List.of("commit-1"), "main",
        "alice", "Test 2", Instant.now(), "A <s2> <p> <o> ."
    );

    // Act
    projector.handleEvent(event1);
    projector.handleEvent(event2);

    // Assert: Both processed
    verify(commitRepository, times(2)).save(eq("default"), any(), any());
  }

  @Test
  void handleEvent_sameEventIdFromReplay_shouldSkip() {
    // Arrange: Create event with fixed eventId (simulating replay)
    String fixedEventId = "01932c5c-8f7a-7890-b123-456789abcdef";
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        fixedEventId, "default", "commit-1", List.of(), "main",
        "alice", "Test", Instant.now(), "A <s> <p> <o> ."
    );

    CommitCreatedEvent event2 = new CommitCreatedEvent(
        fixedEventId, "default", "commit-1", List.of(), "main",
        "alice", "Test", Instant.now(), "A <s> <p> <o> ."
    );

    // Act
    projector.handleEvent(event1);
    projector.handleEvent(event2);  // Same eventId - duplicate

    // Assert: Processed only once
    verify(commitRepository, times(1)).save(eq("default"), any(), any());
  }

  @Test
  void getDeduplicationStats_shouldReturnCacheStatistics() {
    // Arrange
    CommitCreatedEvent event = CommitCreatedEvent.create(
        "default", "commit-1", List.of(), "main",
        "alice", "Test", Instant.now(), "A <s> <p> <o> ."
    );

    // Act
    projector.handleEvent(event);
    projector.handleEvent(event);  // Duplicate (cache hit)

    var stats = projector.getDeduplicationStats();

    // Assert
    assertThat(stats.requestCount()).isEqualTo(2);  // 2 lookups
    assertThat(stats.hitCount()).isEqualTo(1);      // 1 hit (duplicate)
    assertThat(stats.missCount()).isEqualTo(1);     // 1 miss (first time)
    assertThat(stats.hitRate()).isEqualTo(0.5);     // 50% hit rate
  }
}
```

**Update existing tests to use factory methods:**

Search for `new CommitCreatedEvent(` and replace with `CommitCreatedEvent.create(` (remove eventId param).

---

### Step 10: Integration Tests (30 min)

**Update:** `src/test/java/org/chucc/vcserver/projection/ReadModelProjectorIT.java`

```java
@Test
void duplicateEvent_shouldBeSkippedByProjector() throws Exception {
  // Arrange: Create event with fixed eventId
  String eventId = "test-event-id-123";
  CommitCreatedEvent event = new CommitCreatedEvent(
      eventId,
      "default",
      CommitId.generate().value(),
      List.of(),
      "main",
      "alice",
      "Test commit",
      Instant.now(),
      "A <http://ex.org/s> <http://ex.org/p> <http://ex.org/o> ."
  );

  // Act: Publish same event twice
  eventPublisher.publish(event).get();
  Thread.sleep(100);  // Wait for first processing
  eventPublisher.publish(event).get();

  // Assert: Wait for projector to process
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        // Verify commit repository was called only once
        // (difficult to verify without spying - check logs instead)
        logger.info("Checking deduplication worked...");
      });

  // Check deduplication stats
  var stats = readModelProjector.getDeduplicationStats();
  assertThat(stats.hitCount()).isGreaterThan(0);  // At least one duplicate detected
}
```

---

### Step 11: Update Documentation (20 min)

**Update:** `docs/architecture/cqrs-event-sourcing.md`

Add section on deduplication:

```markdown
## Event Deduplication

**Problem:** Kafka provides **at-least-once** delivery semantics, meaning events may be delivered multiple times.

**Solution:** **Idempotent event handlers** with **eventId-based deduplication**.

### How It Works

1. **Event Creation:** Every event gets a unique `eventId` (UUIDv7)
   ```java
   CommitCreatedEvent event = CommitCreatedEvent.create(...);
   // event.getEventId() = "01932c5c-8f7a-7890-b123-456789abcdef"
   ```

2. **Event Publishing:** eventId stored in Kafka headers
   ```java
   headers.add("eventId", event.getEventId().getBytes());
   ```

3. **Event Consumption:** ReadModelProjector checks if event already processed
   ```java
   if (isEventProcessed(event.getEventId())) {
     logger.warn("Skipping duplicate event: {}", event.getEventId());
     return;  // Skip
   }
   ```

4. **Processing:** Mark event as processed **after** successful handling
   ```java
   processEvent(event);
   markEventAsProcessed(event.getEventId());
   ```

### Deduplication Cache

**Implementation:** Caffeine LRU cache with bounded size

**Configuration:**
```yaml
projector:
  deduplication:
    enabled: true
    cache-size: 100000  # Keep last 100k event IDs
```

**Memory Usage:** ~5-10 MB for 100k event IDs

**Eviction:** LRU (Least Recently Used) when cache is full

**Startup Behavior:**
- On startup, cache is empty
- Events replayed from Kafka populate cache
- Prevents duplicate processing during replay

### Monitoring

**Check deduplication statistics:**
```bash
curl http://localhost:8080/actuator/deduplication

# Response:
{
  "requestCount": 50000,   # Total events processed
  "hitCount": 125,         # Duplicates detected
  "missCount": 49875,      # New events
  "hitRate": 0.0025,       # 0.25% duplicate rate
  "evictionCount": 0       # LRU evictions
}
```

**Interpreting Metrics:**

- **Low hit rate (< 1%):** Normal - few duplicates
- **High hit rate (> 5%):** Investigate - consumer rebalancing or retries?
- **High eviction count:** Increase cache size (more duplicates expected)

### Edge Cases

**Case 1: Duplicate Beyond Cache Size**
- If duplicate arrives after 100k+ other events
- Cache may have evicted the eventId
- Event re-processed (rare, acceptable)

**Case 2: Consumer Restart**
- Cache is empty after restart
- Events replayed from Kafka
- Deduplication cache rebuilt during replay
- No duplicates occur (Kafka offsets preserved)

**Case 3: Manual Offset Reset**
- Admin resets consumer offsets to earlier position
- Events re-read from Kafka
- Deduplication prevents re-processing ✅
```

---

### Step 12: Build and Quality Checks (30 min)

**Phase 1: Static Analysis**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check
```

**Phase 2: Unit Tests**
```bash
mvn -q test -Dtest=EventDeduplicationTest
```

**Phase 3: Integration Tests**
```bash
mvn -q test -Dtest=ReadModelProjectorIT
```

**Phase 4: Full Build**
```bash
mvn -q clean install
```

**Expected Results:**
- ✅ All 920+ tests pass (new tests added)
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations

---

## Success Criteria

- [ ] uuid-creator dependency added to pom.xml
- [ ] eventId field added to VersionControlEvent interface
- [ ] All 12 event types include eventId field
- [ ] All event types have factory methods (`.create()`)
- [ ] All command handlers use factory methods
- [ ] eventId included in Kafka headers
- [ ] ReadModelProjector implements deduplication logic
- [ ] Deduplication cache configured (Caffeine LRU)
- [ ] ProjectorProperties created with configuration
- [ ] Actuator endpoint for deduplication stats
- [ ] Unit tests verify deduplication (5+ tests)
- [ ] Integration tests verify no duplicate processing
- [ ] Documentation updated (architecture guide)
- [ ] All 920+ tests pass
- [ ] Zero quality violations

---

## Testing Strategy

### Unit Tests

1. **First Event Processing**
   - ✅ Event with new eventId → processed
   - ✅ Repository methods called

2. **Duplicate Event Detection**
   - ✅ Same eventId twice → processed once
   - ✅ Repository methods called once

3. **Different Events**
   - ✅ Different eventIds → both processed
   - ✅ Repository methods called twice

4. **Cache Statistics**
   - ✅ Hit rate tracked correctly
   - ✅ Miss rate tracked correctly
   - ✅ Eviction count tracked

### Integration Tests

1. **Kafka Event Replay**
   - Publish event, stop consumer
   - Restart consumer
   - Verify event not re-processed

2. **Concurrent Duplicates**
   - Publish same eventId multiple times concurrently
   - Verify only one processing

3. **Deduplication Metrics**
   - Query actuator endpoint
   - Verify statistics match expected values

### Performance Tests (Optional)

1. **Cache Performance**
   - Process 100k events
   - Verify deduplication lookup < 1ms
   - Verify memory usage < 10 MB

---

## Rollback Plan

If issues arise:

1. **Disable Deduplication**
   ```yaml
   projector:
     deduplication:
       enabled: false
   ```

2. **Revert Code**
   ```bash
   git revert <commit-hash>
   ```

3. **Risk Assessment**
   - Low risk: Deduplication is additive (doesn't change existing behavior)
   - Failure mode: Events may be re-processed (not worse than current state)

---

## Future Enhancements

1. **Persistent Deduplication Store**
   - Store event IDs in Redis/PostgreSQL
   - Survive restarts without replay

2. **Time-Based Eviction**
   - Evict event IDs older than 7 days
   - Keep recent events for deduplication

3. **Per-Dataset Deduplication**
   - Separate caches per dataset
   - Better isolation

4. **Deduplication Metrics**
   - Add Micrometer counters
   - Grafana dashboards
   - Alert on high duplicate rates

---

## References

- German Kafka CQRS/ES Best Practices (provided checklist)
- [UUIDv7 Specification (uuid-creator)](https://github.com/f4b6a3/uuid-creator)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [Kafka At-Least-Once Semantics](https://kafka.apache.org/documentation/#semantics)
- [ReadModelProjector.java](src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

---

## Notes

**Complexity Level:** Medium
- Touches many files (12 event types + command handlers)
- Conceptually simple (deduplication pattern is well-known)
- Requires careful testing

**Estimated Time Breakdown:**
- Add dependency: 5 min
- Update interface: 15 min
- Update event records: 60 min
- Update command handlers: 30 min
- Add headers: 10 min
- Implement deduplication: 45 min
- Add configuration: 10 min
- Add metrics endpoint: 15 min
- Unit tests: 45 min
- Integration tests: 30 min
- Documentation: 20 min
- Build & quality: 30 min
- **Total: ~4 hours**

**Risk Level:** Low-Medium
- Additive change (doesn't break existing functionality)
- Well-tested pattern (deduplication is standard practice)
- Easy to disable if issues arise

**This is a CRITICAL task** for production deployments (prevents duplicate event processing).
