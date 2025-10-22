# Task: Fix Partition Key Strategy (Aggregate-ID Pattern)

**Status:** Not Started
**Priority:** 🔴 **CRITICAL**
**Estimated Time:** 3-4 hours
**Dependencies:** None

---

## Context

**CRITICAL ISSUE:** The current partition key strategy **violates CQRS/Event Sourcing best practices** by not using aggregate IDs as partition keys.

### Current Implementation Problem

[EventPublisher.java:126-141](src/main/java/org/chucc/vcserver/event/EventPublisher.java#L126-L141):

```java
private String getPartitionKey(VersionControlEvent event) {
  return switch (event) {
    case BranchCreatedEvent e -> e.branchName();      // ❌ Branch name only
    case BranchResetEvent e -> e.branchName();        // ❌ Branch name only
    case CommitCreatedEvent e -> event.dataset();     // ❌ Dataset only (no branch!)
    case SnapshotCreatedEvent e -> e.branchName();    // ❌ Branch name only
    // ...
  };
}
```

### Why This Is Wrong

From the German Kafka CQRS/ES best practices:

> **"Key = Aggregate-ID" ⇒ alle Events einer Aggregate-Instanz landen in genau einer Partition (notwendige Ordnung).**

**Translation:** All events for a single aggregate instance MUST land in the same partition to guarantee ordering.

**Current Problems:**

1. **CommitCreatedEvent uses `dataset` as key**
   - Commits for branch "main" and "feature-x" in same dataset go to **different partitions**
   - **No ordering guarantee** for commits on the same branch
   - Branch updates may be processed **out of order**

2. **Branch events use `branchName` without dataset**
   - Branch "main" in "dataset-A" and "dataset-B" go to **same partition**
   - Unnecessary coupling between datasets
   - Partition skew if one branch name is used across many datasets

3. **No consistent aggregate identity**
   - Different events for the same logical aggregate use different keys
   - Violates Event Sourcing principle: "aggregate instance = single stream"

### Impact

**Ordering Violations:**
```
Dataset: default, Branch: main

Event 1: CommitCreated (key=default) → Partition 0
Event 2: BranchReset (key=main)      → Partition 1  ❌ Different partition!
Event 3: CommitCreated (key=default) → Partition 0

Result: Event 2 may be processed BEFORE Event 1 (race condition)
```

**Real-World Failure Scenario:**
```
T1: User creates commit on main        → Partition 0
T2: User resets main to earlier commit → Partition 1 (processed first!)
T3: Projector processes reset          → Branch head = old commit
T4: Projector processes commit         → Branch head = new commit
Result: Branch in inconsistent state!
```

---

## Goal

Implement **consistent aggregate-ID based partition keys** to guarantee event ordering per aggregate instance.

**Design Principle:**

```
Aggregate Type          | Aggregate ID                  | Partition Key
------------------------|-------------------------------|---------------------------
Branch                  | dataset + branchName          | "dataset:branchName"
Commit (on branch)      | dataset + branchName          | "dataset:branchName"
Commit (detached)       | dataset + commitId            | "dataset:commitId"
Dataset                 | dataset                       | "dataset"
Snapshot                | dataset + branchName          | "dataset:branchName"
```

---

## Design Decisions

### 1. Aggregate Identity Model

**Chosen Model: Composite Keys**

For CHUCC Server, we have these aggregate types:

1. **Branch Aggregate** = `dataset + branchName`
   - All branch operations (create, reset, rebase) for same branch → same partition
   - All commits created on that branch → same partition

2. **Dataset Aggregate** = `dataset`
   - Dataset-level operations (delete dataset, create dataset) → dataset partition

3. **Detached Commit Aggregate** = `dataset + commitId`
   - Commits not associated with a branch (rare, future use)

**Partition Key Format:** `"{dataset}:{aggregateId}"`

Examples:
- Branch "main" in "default": `"default:main"`
- Branch "feature-x" in "my-dataset": `"my-dataset:feature-x"`
- Dataset "default": `"default"`

### 2. Key Separator Choice

**Chosen: Colon (`:`)** - Common in aggregate IDs, clear separation

**Alternatives considered:**
- `/` - Confusing (looks like path)
- `#` - URL fragment semantics
- `_` - Less clear separation

### 3. Backward Compatibility

**Breaking Change:** This IS a breaking change for Kafka partitioning.

**Migration Strategy:**

**Option A: Fresh Start (Recommended for Dev/Test)**
- Delete existing topics
- Restart with new partition keys
- Clean slate, no migration issues

**Option B: Dual-Write Migration (Production)**
1. Deploy new code (writes to new topics: `vc.{dataset}.events.v2`)
2. Replay old topics into new topics with correct keys
3. Switch consumers to new topics
4. Deprecate old topics after validation period

**For this task:** Implement Option A (fresh start). Document Option B for production.

---

## Implementation Plan

### Step 1: Define Aggregate ID Abstraction (45 min)

**Create new interface:** `src/main/java/org/chucc/vcserver/event/AggregateIdentity.java`

```java
package org.chucc.vcserver.event;

/**
 * Represents the identity of an aggregate for event partitioning.
 * All events for the same aggregate instance should use the same partition key
 * to ensure ordered processing.
 */
public interface AggregateIdentity {

  /**
   * Returns the aggregate type (e.g., "Branch", "Dataset", "Commit").
   * Used for logging and debugging.
   */
  String getAggregateType();

  /**
   * Returns the partition key for this aggregate instance.
   * Format: "{dataset}:{aggregateId}" or "{dataset}" for dataset-level aggregates.
   *
   * @return the partition key
   */
  String getPartitionKey();

  /**
   * Returns the dataset this aggregate belongs to.
   *
   * @return the dataset name
   */
  String getDataset();

  /**
   * Factory method: Create branch aggregate identity.
   */
  static AggregateIdentity branch(String dataset, String branchName) {
    return new BranchAggregate(dataset, branchName);
  }

  /**
   * Factory method: Create dataset aggregate identity.
   */
  static AggregateIdentity dataset(String dataset) {
    return new DatasetAggregate(dataset);
  }

  /**
   * Factory method: Create commit aggregate identity (for detached commits).
   */
  static AggregateIdentity commit(String dataset, String commitId) {
    return new CommitAggregate(dataset, commitId);
  }

  // Inner record implementations
  record BranchAggregate(String dataset, String branchName) implements AggregateIdentity {
    @Override
    public String getAggregateType() { return "Branch"; }

    @Override
    public String getPartitionKey() { return dataset + ":" + branchName; }

    @Override
    public String getDataset() { return dataset; }
  }

  record DatasetAggregate(String dataset) implements AggregateIdentity {
    @Override
    public String getAggregateType() { return "Dataset"; }

    @Override
    public String getPartitionKey() { return dataset; }

    @Override
    public String getDataset() { return dataset; }
  }

  record CommitAggregate(String dataset, String commitId) implements AggregateIdentity {
    @Override
    public String getAggregateType() { return "Commit"; }

    @Override
    public String getPartitionKey() { return dataset + ":" + commitId; }

    @Override
    public String getDataset() { return dataset; }
  }
}
```

**Why this design:**
- ✅ Type-safe aggregate identity
- ✅ Encapsulates partition key logic
- ✅ Clear factory methods
- ✅ Easy to test and understand

---

### Step 2: Update VersionControlEvent Interface (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/VersionControlEvent.java`

**Add method:**
```java
public interface VersionControlEvent {
  String dataset();

  /**
   * Returns the aggregate identity for partitioning.
   * All events for the same aggregate instance must return the same partition key
   * to ensure ordered processing in Kafka.
   *
   * @return the aggregate identity
   */
  AggregateIdentity getAggregateIdentity();
}
```

---

### Step 3: Implement getAggregateIdentity() in All Event Records (60 min)

Update all 12 event types to implement `getAggregateIdentity()`:

**Example: BranchCreatedEvent**
```java
public record BranchCreatedEvent(
    String dataset,
    String branchName,
    String commitId,
    Instant timestamp
) implements VersionControlEvent {

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
```

**Example: CommitCreatedEvent**
```java
public record CommitCreatedEvent(
    String dataset,
    String commitId,
    List<String> parents,
    String branch,  // Nullable
    String author,
    String message,
    Instant timestamp,
    String rdfPatch
) implements VersionControlEvent {

  @Override
  public AggregateIdentity getAggregateIdentity() {
    // If branch is specified, use branch aggregate
    if (branch != null) {
      return AggregateIdentity.branch(dataset, branch);
    }
    // Otherwise, use commit aggregate (detached commit)
    return AggregateIdentity.commit(dataset, commitId);
  }
}
```

**All Event Types to Update:**
1. ✅ **BranchCreatedEvent** → `branch(dataset, branchName)`
2. ✅ **BranchResetEvent** → `branch(dataset, branchName)`
3. ✅ **BranchRebasedEvent** → `branch(dataset, branch)`
4. ✅ **BranchDeletedEvent** → `branch(dataset, branchName)`
5. ✅ **CommitCreatedEvent** → `branch(dataset, branch)` if branch != null, else `commit(dataset, commitId)`
6. ✅ **TagCreatedEvent** → `dataset(dataset)` (tag is dataset-level)
7. ✅ **RevertCreatedEvent** → `branch(dataset, branch)`
8. ✅ **SnapshotCreatedEvent** → `branch(dataset, branchName)`
9. ✅ **CherryPickedEvent** → `branch(dataset, branch)`
10. ✅ **CommitsSquashedEvent** → `branch(dataset, branch)`
11. ✅ **BatchGraphsCompletedEvent** → `dataset(dataset)` (batch is dataset-level)
12. ✅ **DatasetDeletedEvent** → `dataset(dataset)`

---

### Step 4: Update EventPublisher to Use getAggregateIdentity() (20 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

**Replace getPartitionKey() method:**

```java
/**
 * Determines the partition key for an event using aggregate identity.
 * Events for the same aggregate instance use the same partition key
 * to ensure ordered processing.
 *
 * @param event the event
 * @return the partition key
 */
private String getPartitionKey(VersionControlEvent event) {
  AggregateIdentity aggregateId = event.getAggregateIdentity();
  String partitionKey = aggregateId.getPartitionKey();

  logger.debug("Event {} for aggregate {} (type={}) using partition key: {}",
      event.getClass().getSimpleName(),
      aggregateId.getAggregateType(),
      aggregateId.getDataset(),
      partitionKey);

  return partitionKey;
}
```

**Delete old switch statement** (lines 127-141).

---

### Step 5: Update Kafka Headers with Aggregate Info (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

**Update addHeaders() method to include aggregate information:**

```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  AggregateIdentity aggregateId = event.getAggregateIdentity();

  // Always add dataset and event type
  headers.add(new RecordHeader(EventHeaders.DATASET,
      event.dataset().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.EVENT_TYPE,
      event.getClass().getSimpleName()
          .replace("Event", "")
          .getBytes(StandardCharsets.UTF_8)));

  // Add aggregate metadata (NEW)
  headers.add(new RecordHeader("aggregateType",
      aggregateId.getAggregateType().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader("aggregateId",
      aggregateId.getPartitionKey().getBytes(StandardCharsets.UTF_8)));

  // ... existing event-specific headers
}
```

---

### Step 6: Write Unit Tests (45 min)

**Create:** `src/test/java/org/chucc/vcserver/event/AggregateIdentityTest.java`

```java
package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AggregateIdentityTest {

  @Test
  void branchAggregate_shouldUseDatasetAndBranchAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.branch("default", "main");

    assertThat(aggregate.getAggregateType()).isEqualTo("Branch");
    assertThat(aggregate.getPartitionKey()).isEqualTo("default:main");
    assertThat(aggregate.getDataset()).isEqualTo("default");
  }

  @Test
  void datasetAggregate_shouldUseDatasetAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.dataset("my-dataset");

    assertThat(aggregate.getAggregateType()).isEqualTo("Dataset");
    assertThat(aggregate.getPartitionKey()).isEqualTo("my-dataset");
    assertThat(aggregate.getDataset()).isEqualTo("my-dataset");
  }

  @Test
  void commitAggregate_shouldUseDatasetAndCommitIdAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.commit("default", "commit-123");

    assertThat(aggregate.getAggregateType()).isEqualTo("Commit");
    assertThat(aggregate.getPartitionKey()).isEqualTo("default:commit-123");
    assertThat(aggregate.getDataset()).isEqualTo("default");
  }

  @Test
  void sameBranchInDifferentDatasets_shouldHaveDifferentKeys() {
    AggregateIdentity aggregate1 = AggregateIdentity.branch("dataset-A", "main");
    AggregateIdentity aggregate2 = AggregateIdentity.branch("dataset-B", "main");

    assertThat(aggregate1.getPartitionKey()).isNotEqualTo(aggregate2.getPartitionKey());
    assertThat(aggregate1.getPartitionKey()).isEqualTo("dataset-A:main");
    assertThat(aggregate2.getPartitionKey()).isEqualTo("dataset-B:main");
  }
}
```

**Update:** `src/test/java/org/chucc/vcserver/event/EventPublisherTest.java`

```java
@Test
void publish_shouldUseAggregateIdAsPartitionKey() {
  // Arrange
  BranchCreatedEvent event = new BranchCreatedEvent(
      "default", "main", "commit-123", Instant.now());

  // Act
  eventPublisher.publish(event);

  // Assert
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  ProducerRecord<String, VersionControlEvent> record = captor.getValue();
  assertThat(record.key()).isEqualTo("default:main");  // Aggregate ID
}

@Test
void commitCreatedEvent_withBranch_shouldUseAggregateIdAsPartitionKey() {
  // Arrange
  CommitCreatedEvent event = new CommitCreatedEvent(
      "default",
      "commit-456",
      List.of("commit-123"),
      "feature-x",  // Branch specified
      "alice",
      "Test commit",
      Instant.now(),
      "A <s> <p> <o> ."
  );

  // Act
  eventPublisher.publish(event);

  // Assert
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  ProducerRecord<String, VersionControlEvent> record = captor.getValue();
  assertThat(record.key()).isEqualTo("default:feature-x");  // Branch aggregate
}

@Test
void datasetDeletedEvent_shouldUseDatasetAsPartitionKey() {
  // Arrange
  DatasetDeletedEvent event = new DatasetDeletedEvent(
      "my-dataset",
      List.of("main", "feature"),
      50,
      Instant.now(),
      true
  );

  // Act
  eventPublisher.publish(event);

  // Assert
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  ProducerRecord<String, VersionControlEvent> record = captor.getValue();
  assertThat(record.key()).isEqualTo("my-dataset");  // Dataset aggregate
}
```

---

### Step 7: Integration Tests with New Partition Strategy (30 min)

**Update:** `src/test/java/org/chucc/vcserver/event/EventPublisherKafkaIT.java`

Add test to verify partition assignment:

```java
@Test
void eventsForSameBranch_shouldGoToSamePartition() throws Exception {
  // Arrange: Create multiple events for same branch
  BranchCreatedEvent event1 = new BranchCreatedEvent(
      "default", "main", "commit-1", Instant.now());

  CommitCreatedEvent event2 = new CommitCreatedEvent(
      "default", "commit-2", List.of("commit-1"), "main",
      "alice", "Commit 2", Instant.now(), "A <s> <p> <o> .");

  BranchResetEvent event3 = new BranchResetEvent(
      "default", "main", "commit-1", "commit-2", Instant.now());

  // Act: Publish all events
  SendResult<String, VersionControlEvent> result1 = eventPublisher.publish(event1).get();
  SendResult<String, VersionControlEvent> result2 = eventPublisher.publish(event2).get();
  SendResult<String, VersionControlEvent> result3 = eventPublisher.publish(event3).get();

  // Assert: All go to same partition
  int partition1 = result1.getRecordMetadata().partition();
  int partition2 = result2.getRecordMetadata().partition();
  int partition3 = result3.getRecordMetadata().partition();

  assertThat(partition1).isEqualTo(partition2);
  assertThat(partition2).isEqualTo(partition3);

  logger.info("All events for branch 'main' went to partition {}", partition1);
}

@Test
void eventsForDifferentBranches_mayGoToDifferentPartitions() throws Exception {
  // Arrange: Create events for different branches
  BranchCreatedEvent mainEvent = new BranchCreatedEvent(
      "default", "main", "commit-1", Instant.now());

  BranchCreatedEvent featureEvent = new BranchCreatedEvent(
      "default", "feature-x", "commit-2", Instant.now());

  // Act: Publish events
  SendResult<String, VersionControlEvent> mainResult =
      eventPublisher.publish(mainEvent).get();
  SendResult<String, VersionControlEvent> featureResult =
      eventPublisher.publish(featureEvent).get();

  // Assert: Partition assignment is deterministic (hash-based)
  // Same branch = same partition, different branches = possibly different
  int mainPartition = mainResult.getRecordMetadata().partition();
  int featurePartition = featureResult.getRecordMetadata().partition();

  logger.info("Branch 'main' → partition {}, branch 'feature-x' → partition {}",
      mainPartition, featurePartition);

  // Both events should have consistent keys
  assertThat(mainResult.getProducerRecord().key()).isEqualTo("default:main");
  assertThat(featureResult.getProducerRecord().key()).isEqualTo("default:feature-x");
}
```

---

### Step 8: Update Documentation (20 min)

**Update:** `docs/architecture/cqrs-event-sourcing.md`

Add section on partition strategy:

```markdown
### Event Partitioning Strategy

**Principle:** All events for a single aggregate instance use the same partition key to guarantee ordering.

**Aggregate Types:**

| Aggregate Type | Partition Key Format | Example |
|----------------|----------------------|---------|
| Branch | `dataset:branchName` | `default:main` |
| Dataset | `dataset` | `default` |
| Commit (detached) | `dataset:commitId` | `default:commit-123` |

**Event-to-Aggregate Mapping:**

| Event Type | Aggregate | Partition Key |
|------------|-----------|---------------|
| BranchCreatedEvent | Branch | `dataset:branchName` |
| BranchResetEvent | Branch | `dataset:branchName` |
| CommitCreatedEvent (with branch) | Branch | `dataset:branch` |
| CommitCreatedEvent (no branch) | Commit | `dataset:commitId` |
| DatasetDeletedEvent | Dataset | `dataset` |

**Ordering Guarantees:**

✅ All operations on branch "main" in dataset "default" are processed in order
✅ Commits created on the same branch are processed in order
✅ Branch creation, reset, and deletion for same branch are ordered
❌ Operations on different branches may be processed in parallel (no ordering guarantee)

**Why This Matters:**

Without aggregate-based partitioning, events for the same branch could be processed out of order:

```
Bad (old strategy):
  Event 1: CommitCreated (key=dataset)     → Partition 0
  Event 2: BranchReset (key=branchName)    → Partition 1
  Result: Events may process out of order! ❌

Good (new strategy):
  Event 1: CommitCreated (key=dataset:branch) → Partition 0
  Event 2: BranchReset (key=dataset:branch)   → Partition 0
  Result: Events always process in order ✅
```
```

**Update:** `docs/operations/kafka-storage-guide.md`

Update the "Event Ordering Guarantees" section (line 390):

```markdown
### Event Ordering Guarantees

**Kafka partitioning** ensures ordered processing per aggregate:

**Partition Key Strategy (Aggregate-ID Pattern):**
```java
// All events for same aggregate use same partition key
BranchCreatedEvent("default", "main", ...)   → key="default:main" → Partition 0
CommitCreatedEvent(..., branch="main", ...)  → key="default:main" → Partition 0
BranchResetEvent("default", "main", ...)     → key="default:main" → Partition 0
// All processed in order on Partition 0 ✅
```

**Ordering Guarantees:**
- ✅ Events within same partition: **Strictly ordered**
- ✅ Events for same aggregate (branch/dataset): **Strictly ordered**
- ❌ Events across different partitions: **No ordering guarantee**
- ❌ Events for different aggregates: **No ordering guarantee**

**Example: Branch Operations**
```
Branch "main" in "default":
  T1: Create commit-1 → Partition 0 (key=default:main)
  T2: Create commit-2 → Partition 0 (key=default:main)
  T3: Reset to commit-1 → Partition 0 (key=default:main)

  Processing order: ALWAYS T1 → T2 → T3 ✅
```

**Example: Different Branches**
```
Branch "main" → Partition 0 (key=default:main)
Branch "feature" → Partition 1 (key=default:feature)

No ordering guarantee between branches (intentional - they're independent)
```
```

---

### Step 9: Migration Guide (15 min)

**Create:** `docs/operations/partition-key-migration.md`

```markdown
# Partition Key Strategy Migration Guide

## Overview

In version X.Y.Z, CHUCC Server changed the Kafka partition key strategy to follow CQRS/Event Sourcing best practices.

**Old Strategy:** Inconsistent keys (branch name OR dataset)
**New Strategy:** Aggregate-ID based keys (`dataset:aggregateId`)

## Migration Paths

### Option A: Fresh Start (Development/Testing)

**When to use:** Development, testing, or when data loss is acceptable

**Steps:**
1. Stop CHUCC Server
2. Delete all Kafka topics: `vc.*.events`
   ```bash
   kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic 'vc\..*\.events'
   ```
3. Upgrade CHUCC Server to new version
4. Start CHUCC Server (new topics created with new partition strategy)

**Pros:** Simple, clean
**Cons:** **Data loss** (all history deleted)

---

### Option B: Dual-Write Migration (Production)

**When to use:** Production, when data preservation is required

**Steps:**

**Phase 1: Deploy New Version (Dual-Write)**
1. Update `application.yml` to use new topic name:
   ```yaml
   kafka:
     topic-template: vc.{dataset}.events.v2  # New topic pattern
   ```
2. Deploy new CHUCC Server version
3. Verify events writing to `*.events.v2` topics

**Phase 2: Replay Old Topics**
1. Create replay script to read from old topics and publish to new topics:
   ```bash
   kafka-console-consumer.sh --bootstrap-server localhost:9092 \
     --topic vc.default.events \
     --from-beginning \
     --property print.key=true | \
   # Process and republish to new topic with correct partition keys
   ```
2. Run replay for all datasets

**Phase 3: Validate**
1. Compare old and new topics (same number of events)
2. Verify projector state matches production state

**Phase 4: Cutover**
1. Update consumer to read from `*.events.v2`
2. Stop writing to old topics
3. Monitor for 1-2 weeks
4. Delete old topics

**Pros:** No data loss
**Cons:** Complex, requires downtime or dual-read support

---

### Option C: In-Place Migration (Advanced)

**Not Recommended:** Kafka doesn't support re-partitioning existing topics

---

## Verification

After migration, verify partition assignment:

```bash
# Check events for same branch go to same partition
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic vc.default.events.v2 \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true

# Expected output:
# Partition:0  default:main  {"eventType":"BranchCreated",...}
# Partition:0  default:main  {"eventType":"CommitCreated",...}
# Partition:0  default:main  {"eventType":"BranchReset",...}
```

All events for `default:main` should show same partition.
```

---

### Step 10: Build and Quality Checks (30 min)

**Phase 1: Static Analysis**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check
```

**Phase 2: Unit Tests**
```bash
mvn -q test -Dtest=AggregateIdentityTest,EventPublisherTest
```

**Phase 3: Integration Tests**
```bash
mvn -q test -Dtest=EventPublisherKafkaIT
```

**Phase 4: Full Build**
```bash
mvn -q clean install
```

**Expected Results:**
- ✅ All 913+ tests pass
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations

---

## Testing Strategy

### Unit Tests

1. **AggregateIdentity**
   - ✅ Branch aggregate creates correct partition key
   - ✅ Dataset aggregate creates correct partition key
   - ✅ Commit aggregate creates correct partition key
   - ✅ Same aggregate ID = same partition key
   - ✅ Different aggregate IDs = different partition keys

2. **VersionControlEvent.getAggregateIdentity()**
   - ✅ All 12 event types return correct aggregate identity
   - ✅ CommitCreatedEvent with branch → branch aggregate
   - ✅ CommitCreatedEvent without branch → commit aggregate

3. **EventPublisher.getPartitionKey()**
   - ✅ Uses event.getAggregateIdentity().getPartitionKey()
   - ✅ Logs aggregate information
   - ✅ Returns consistent key for same aggregate

### Integration Tests

1. **Same Branch → Same Partition**
   - Publish multiple events for same branch
   - Verify all go to same partition
   - Verify partition key = `dataset:branch`

2. **Different Branches → Possibly Different Partitions**
   - Publish events for different branches
   - Verify deterministic partition assignment
   - Verify different partition keys

3. **Event Replay**
   - Publish events, stop consumer
   - Restart consumer
   - Verify events processed in correct order

### Manual Testing

```bash
# 1. Start Kafka and CHUCC Server
docker-compose up -d

# 2. Create branch and commits
curl -X POST http://localhost:3030/version/branches \
  -H "Content-Type: application/json" \
  -d '{"name": "main", "startPoint": null}'

curl -X PUT http://localhost:3030/data?default=true&branch=main \
  -H "Content-Type: text/turtle" \
  -d "<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> ."

# 3. Check Kafka partition assignment
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic vc.default.events \
  --from-beginning \
  --property print.key=true \
  --property print.partition=true

# Expected: All events for "main" branch have same partition
```

---

## Success Criteria

- [ ] AggregateIdentity interface created with factory methods
- [ ] VersionControlEvent.getAggregateIdentity() method added
- [ ] All 12 event types implement getAggregateIdentity() correctly
- [ ] EventPublisher.getPartitionKey() uses getAggregateIdentity()
- [ ] Kafka headers include aggregateType and aggregateId
- [ ] Unit tests verify partition key logic (15+ tests)
- [ ] Integration tests verify same-aggregate-same-partition (3+ tests)
- [ ] Documentation updated (architecture guide, Kafka guide, migration guide)
- [ ] All 913+ tests pass
- [ ] Zero quality violations (Checkstyle, SpotBugs, PMD)

---

## Rollback Plan

**If issues arise:**

1. **Revert Code Changes**
   ```bash
   git revert <commit-hash>
   ```

2. **Restore Old Topics** (if deleted)
   - Use Kafka topic backups
   - Or: Use fresh start (data loss)

3. **Rollback Consumer Offsets**
   ```bash
   kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group read-model-projector \
     --topic vc.default.events \
     --reset-offsets --to-earliest --execute
   ```

**Minimal Risk:** This change is internal to Kafka partitioning, no API changes.

---

## Future Enhancements

After this task:

1. **Multi-Dataset Event Replay**
   - Partition events by dataset for faster replay
   - Only replay relevant datasets on startup

2. **Consumer Group Scaling**
   - Scale consumers to match partition count
   - One consumer per partition for maximum parallelism

3. **Partition Rebalancing**
   - Monitor partition skew (uneven load)
   - Adjust partitioning strategy if needed

4. **Event Compaction for Snapshots**
   - Use separate compacted topics for snapshots
   - Partition snapshots by branch aggregate

---

## References

- German Kafka CQRS/ES Best Practices (provided checklist)
- [Martin Fowler - Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Kafka Partitioning Best Practices](https://kafka.apache.org/documentation/#design_partitioning)
- [EventPublisher.java](src/main/java/org/chucc/vcserver/event/EventPublisher.java)
- [CQRS + Event Sourcing Guide](docs/architecture/cqrs-event-sourcing.md)

---

## Notes

**Complexity Level:** Medium-High
- Architectural change (partition strategy)
- Multiple files affected (13 event records + EventPublisher)
- Requires careful testing
- Breaking change for Kafka (migration needed)

**Estimated Time Breakdown:**
- Define AggregateIdentity: 45 min
- Update VersionControlEvent: 15 min
- Implement in all events: 60 min
- Update EventPublisher: 20 min
- Update headers: 15 min
- Unit tests: 45 min
- Integration tests: 30 min
- Documentation: 35 min
- Build & quality: 30 min
- **Total: ~4 hours**

**Risk Level:** Medium
- Breaking change for Kafka topics
- Requires migration strategy
- Well-defined pattern (low implementation risk)
- No API changes (low client risk)

**This is the MOST CRITICAL task** for Kafka CQRS/ES compliance. Must be done before other improvements.
