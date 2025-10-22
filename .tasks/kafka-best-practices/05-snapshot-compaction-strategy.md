# Task: Implement Snapshot Compaction Strategy

**Status:** Not Started
**Priority:** 🟡 **LOW-MEDIUM**
**Estimated Time:** 2-3 hours
**Dependencies:** None

---

## Context

**IMPROVEMENT OPPORTUNITY:** Snapshot events are stored in the same append-only event log, but should use a separate compacted topic for efficient storage.

### Current Implementation

[application.yml:85-89](src/main/resources/application.yml#L85-L89):
```yaml
kafka:
  topic-template: vc.{dataset}.events  # Single topic for all events
  retention-ms: -1
  compaction: false  # Append-only
```

**Current Behavior:**
- Snapshot events published to same topic as regular events
- All snapshots kept forever (no compaction)
- Inefficient storage (old snapshots never removed)

### From German Checklist

> **Snapshots**: Extra **kompaktiertes Topic** (z. B. `orders-snapshots`, `cleanup.policy=compact`) mit Key=AggregateId, Value=aktueller Zustand.

**Translation:** Separate **compacted topic** for snapshots with Key=AggregateId.

**Why Separate Topic:**
- ✅ Keep only latest snapshot per branch (discard old)
- ✅ Faster recovery (don't scan through all old snapshots)
- ✅ Reduced storage (compaction removes old snapshots)
- ✅ Better separation of concerns (events vs snapshots)

---

## Goal

Create separate compacted topic for snapshots with tombstone-based cleanup:

1. ✅ Separate topic: `vc.{dataset}.snapshots`
2. ✅ Log compaction enabled (`cleanup.policy=compact`)
3. ✅ Key = `dataset:branch` (aggregate ID)
4. ✅ Tombstones for snapshot deletion

---

## Design Decisions

### 1. Topic Naming

**Chosen:** `vc.{dataset}.snapshots`

**Examples:**
- `vc.default.snapshots`
- `vc.my-dataset.snapshots`

**Separation:**
- Events: `vc.{dataset}.events` (append-only)
- Snapshots: `vc.{dataset}.snapshots` (compacted)

### 2. Partition Key

**Chosen:** `dataset:branch` (aggregate ID)

**Why:**
- Same key for all snapshots of same branch
- Log compaction keeps only latest snapshot per branch
- Old snapshots automatically removed

**Example:**
```
Key: "default:main"
  Value 1: Snapshot at commit-100 (timestamp: T1)
  Value 2: Snapshot at commit-200 (timestamp: T2)  ← Kept
  Value 3: Snapshot at commit-300 (timestamp: T3)  ← Kept

After compaction:
  Only Value 3 kept (latest for "default:main")
```

### 3. Compaction Configuration

**Chosen Settings:**
```yaml
cleanup.policy: compact
min.cleanable.dirty.ratio: 0.5     # Compact when 50% dirty
delete.retention.ms: 86400000      # Keep tombstones for 1 day
segment.ms: 3600000                # Roll segment every hour
min.compaction.lag.ms: 60000       # Wait 1 min before compacting
```

**Why:**
- Compact frequently enough (snapshots change daily/weekly)
- Keep tombstones long enough for consumers to see deletions
- Small segments for faster compaction

---

## Implementation Plan

### Step 1: Update KafkaProperties (15 min)

**File:** `src/main/java/org/chucc/vcserver/config/KafkaProperties.java`

```java
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

  private String bootstrapServers;
  private String topicTemplate = "vc.{dataset}.events";
  private String snapshotTopicTemplate = "vc.{dataset}.snapshots";  // ✅ NEW
  private int partitions = 3;
  private int replicationFactor = 1;
  private long retentionMs = -1;
  private boolean compaction = false;

  // Snapshot-specific config
  private SnapshotTopicConfig snapshotTopicConfig = new SnapshotTopicConfig();

  public static class SnapshotTopicConfig {
    private double minCleanableDirtyRatio = 0.5;
    private long deleteRetentionMs = 86400000L;  // 1 day
    private long segmentMs = 3600000L;           // 1 hour
    private long minCompactionLagMs = 60000L;    // 1 minute

    // Getters/setters
  }

  /**
   * Get snapshot topic name for a dataset.
   */
  public String getSnapshotTopicName(String dataset) {
    return snapshotTopicTemplate.replace("{dataset}", dataset);
  }

  // ... existing methods
}
```

---

### Step 2: Create Snapshot Topic Bean (20 min)

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`

```java
/**
 * Creates a template topic configuration for snapshots.
 * Snapshots use log compaction to keep only the latest snapshot per branch.
 */
@Bean
public NewTopic snapshotTopicTemplate() {
  TopicBuilder builder = TopicBuilder
      .name(kafkaProperties.getSnapshotTopicTemplate())
      .partitions(kafkaProperties.getPartitions())
      .replicas(kafkaProperties.getReplicationFactor());

  var snapshotConfig = kafkaProperties.getSnapshotTopicConfig();

  // Enable log compaction
  builder.config(TopicConfig.CLEANUP_POLICY_CONFIG,
      TopicConfig.CLEANUP_POLICY_COMPACT);

  // Compaction tuning
  builder.config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG,
      String.valueOf(snapshotConfig.getMinCleanableDirtyRatio()));
  builder.config(TopicConfig.DELETE_RETENTION_MS_CONFIG,
      String.valueOf(snapshotConfig.getDeleteRetentionMs()));
  builder.config(TopicConfig.SEGMENT_MS_CONFIG,
      String.valueOf(snapshotConfig.getSegmentMs()));
  builder.config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG,
      String.valueOf(snapshotConfig.getMinCompactionLagMs()));

  // Additional settings
  builder.config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "1");
  builder.config(TopicConfig.COMPRESSION_TYPE_CONFIG, "snappy");

  return builder.build();
}
```

---

### Step 3: Update SnapshotService to Publish to Separate Topic (30 min)

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotService.java`

**Create separate snapshot publisher:**

```java
@Service
public class SnapshotService {

  private final KafkaTemplate<String, SnapshotCreatedEvent> snapshotKafkaTemplate;
  private final KafkaProperties kafkaProperties;

  /**
   * Publishes snapshot to compacted snapshot topic.
   */
  public CompletableFuture<Void> createSnapshotAsync(
      String dataset,
      String branch,
      CommitId commitId) {

    return CompletableFuture.runAsync(() -> {
      try {
        // 1. Materialize dataset graph
        DatasetGraph graph = datasetService.getDataset(
            new DatasetRef(dataset, commitId));

        // 2. Serialize to N-Quads
        String nquads = serializeToNquads(graph);

        // 3. Create snapshot event
        SnapshotCreatedEvent event = SnapshotCreatedEvent.create(
            dataset,
            commitId.value(),
            branch,
            Instant.now(),
            nquads
        );

        // 4. Publish to SNAPSHOT topic (not event topic)
        String snapshotTopic = kafkaProperties.getSnapshotTopicName(dataset);
        String key = dataset + ":" + branch;  // Aggregate ID as key

        ProducerRecord<String, SnapshotCreatedEvent> record =
            new ProducerRecord<>(snapshotTopic, key, event);

        snapshotKafkaTemplate.send(record).get();

        logger.info("Snapshot created for {}/{} at commit {} (topic: {})",
            dataset, branch, commitId, snapshotTopic);

      } catch (Exception e) {
        logger.error("Failed to create snapshot for {}/{} at {}",
            dataset, branch, commitId, e);
      }
    }, snapshotExecutor);
  }

  /**
   * Deletes snapshots for a branch by publishing a tombstone.
   */
  public void deleteSnapshots(String dataset, String branch) {
    String snapshotTopic = kafkaProperties.getSnapshotTopicName(dataset);
    String key = dataset + ":" + branch;

    // Publish tombstone (null value)
    ProducerRecord<String, SnapshotCreatedEvent> tombstone =
        new ProducerRecord<>(snapshotTopic, key, null);

    snapshotKafkaTemplate.send(tombstone);

    logger.info("Published tombstone for snapshots: dataset={}, branch={}",
        dataset, branch);
  }
}
```

---

### Step 4: Update SnapshotKafkaStore to Read from Snapshot Topic (30 min)

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotKafkaStore.java`

**Read from snapshot topic instead of event topic:**

```java
public Optional<SnapshotInfo> getLatestSnapshot(String dataset, String branch) {
  String snapshotTopic = kafkaProperties.getSnapshotTopicName(dataset);
  String key = dataset + ":" + branch;

  // Query Kafka for latest snapshot (compacted topic keeps only latest)
  try (KafkaConsumer<String, SnapshotCreatedEvent> consumer =
           createSnapshotConsumer()) {

    // Seek to specific key (if Kafka supports key-based queries)
    // For now, scan topic (small due to compaction)
    consumer.subscribe(List.of(snapshotTopic));

    SnapshotInfo latestSnapshot = null;
    ConsumerRecords<String, SnapshotCreatedEvent> records =
        consumer.poll(Duration.ofSeconds(5));

    for (ConsumerRecord<String, SnapshotCreatedEvent> record : records) {
      if (record.key().equals(key)) {
        SnapshotCreatedEvent event = record.value();

        if (event != null) {  // Not a tombstone
          latestSnapshot = new SnapshotInfo(
              CommitId.of(event.commitId()),
              event.timestamp(),
              record.offset()
          );
        }
      }
    }

    return Optional.ofNullable(latestSnapshot);
  }
}
```

---

### Step 5: Update Configuration (10 min)

**application.yml:**
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  topic-template: vc.{dataset}.events
  snapshot-topic-template: vc.{dataset}.snapshots  # ✅ NEW
  partitions: 3
  replication-factor: 1
  retention-ms: -1
  compaction: false

  # Snapshot topic configuration
  snapshot-topic-config:
    min-cleanable-dirty-ratio: 0.5     # Compact when 50% dirty
    delete-retention-ms: 86400000      # Keep tombstones 1 day
    segment-ms: 3600000                # Roll segment every hour
    min-compaction-lag-ms: 60000       # Wait 1 min before compacting
```

---

### Step 6: Testing (30 min)

**Integration Test:**
```java
@SpringBootTest
@Testcontainers
class SnapshotCompactionIT {

  @Test
  void snapshotTopic_shouldCompactOldSnapshots() throws Exception {
    // Arrange: Create 3 snapshots for same branch
    snapshotService.createSnapshotAsync("default", "main", commit1).get();
    Thread.sleep(1000);
    snapshotService.createSnapshotAsync("default", "main", commit2).get();
    Thread.sleep(1000);
    snapshotService.createSnapshotAsync("default", "main", commit3).get();

    // Act: Trigger compaction (wait for Kafka to compact)
    Thread.sleep(10000);

    // Assert: Only latest snapshot should be retrievable
    Optional<SnapshotInfo> snapshot =
        snapshotKafkaStore.getLatestSnapshot("default", "main");

    assertThat(snapshot).isPresent();
    assertThat(snapshot.get().commitId()).isEqualTo(commit3);

    // Verify topic contains only 1 record (compacted)
    // (Difficult to verify directly - check Kafka topic records count)
  }

  @Test
  void deleteSnapshots_shouldPublishTombstone() {
    // Arrange
    snapshotService.createSnapshotAsync("default", "main", commit1).get();

    // Act: Delete snapshots
    snapshotService.deleteSnapshots("default", "main");

    // Assert: Snapshot no longer retrievable
    Thread.sleep(2000);  // Wait for tombstone processing
    Optional<SnapshotInfo> snapshot =
        snapshotKafkaStore.getLatestSnapshot("default", "main");

    assertThat(snapshot).isEmpty();
  }
}
```

---

### Step 7: Documentation (20 min)

**Update:** `docs/operations/kafka-storage-guide.md`

```markdown
## Snapshot Topic Strategy

**Separate Topics:**
- **Events:** `vc.{dataset}.events` (append-only, infinite retention)
- **Snapshots:** `vc.{dataset}.snapshots` (compacted, keeps only latest)

**Why Separate:**
- Events: Keep all history forever (audit trail)
- Snapshots: Keep only latest per branch (efficiency)

**Log Compaction:**

Before compaction (vc.default.snapshots):
```
Key: "default:main"
  Offset 0: Snapshot at commit-100 (10 MB)
  Offset 1: Snapshot at commit-200 (12 MB)
  Offset 2: Snapshot at commit-300 (15 MB)

Total: 37 MB
```

After compaction:
```
Key: "default:main"
  Offset 2: Snapshot at commit-300 (15 MB)  ← Only latest kept

Total: 15 MB (60% reduction!)
```

**Tombstones:**

When branch is deleted:
```
1. Publish tombstone: key="default:feature", value=null
2. Kafka marks snapshots for deletion
3. After delete.retention.ms (1 day), snapshots removed permanently
```

**Recovery:**

Fast recovery using snapshots:
```
1. Check snapshot topic for latest snapshot
2. If found: Load snapshot (full graph at commit-X)
3. Replay events from commit-X to HEAD (fast - only recent events)
4. If not found: Replay all events from beginning (slow)
```

**Configuration:**
```yaml
kafka:
  snapshot-topic-config:
    min-cleanable-dirty-ratio: 0.5     # Compact when 50% dirty
    delete-retention-ms: 86400000      # Keep tombstones 1 day
    segment-ms: 3600000                # Roll segment every hour
```
```

---

## Success Criteria

- [ ] KafkaProperties updated with snapshot topic config
- [ ] Snapshot topic bean created with compaction enabled
- [ ] SnapshotService publishes to separate snapshot topic
- [ ] SnapshotKafkaStore reads from snapshot topic
- [ ] Tombstone support for snapshot deletion
- [ ] Integration tests verify compaction behavior
- [ ] Configuration added to application.yml
- [ ] Documentation updated
- [ ] All tests pass

---

## References

- German Kafka CQRS/ES Checklist
- [Kafka Log Compaction](https://kafka.apache.org/documentation/#compaction)
- [SnapshotService.java](src/main/java/org/chucc/vcserver/service/SnapshotService.java)

---

## Notes

**Complexity:** Medium
**Time:** 2-3 hours
**Risk:** Low (separate topic, doesn't affect existing events)

This optimization reduces storage and improves recovery time for large datasets.
