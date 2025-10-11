# Task: Remove In-Memory Snapshot Storage

**Status:** Not Started
**Priority:** Critical
**Estimated Time:** 1 session (2-3 hours)
**Dependencies:** None

---

## Problem

Currently, `SnapshotService` stores **all snapshots** in an unbounded in-memory map:

```java
// In SnapshotService.java
private final Map<String, Map<String, Snapshot>> latestSnapshots = new ConcurrentHashMap<>();
```

**Critical Issues:**

1. **Unbounded Growth:** One snapshot per branch per dataset, stored forever
2. **Memory Explosion:** Each `Snapshot` contains a full `DatasetGraph` (potentially GB in size!)
3. **Wrong Priority:** We're optimizing for rare historical queries at the expense of current work
4. **OOM Risk:** With 10 datasets × 5 branches × 1GB each = 50GB of snapshots in RAM!

**Example Scenario:**
- 20 datasets
- 5 branches each
- Snapshots every 100 commits
- Each snapshot = 500 MB
- **Total: 50 GB of RAM for snapshots alone!**

---

## Design Principle

> **Performance for current work (latest commits) is critical.**
> **Performance for historical queries can be slower - they're rare.**

Therefore:
- ✅ Keep latest commit graphs in cache (see task 01)
- ❌ Do NOT keep snapshots in memory
- ✅ Fetch snapshots from Kafka on-demand when needed

---

## Solution: Fetch Snapshots from Kafka On-Demand

### Architecture

**Current (BAD):**
```
SnapshotCreatedEvent → Kafka → ReadModelProjector → latestSnapshots Map (RAM)
                                                              ↓
                                            DatasetService reads from RAM
```

**Proposed (GOOD):**
```
SnapshotCreatedEvent → Kafka (persisted, compacted topic)
                            ↓
            DatasetService queries Kafka directly when needed
            (rare - only for historical commit materialization)
```

### Key Insight

Snapshots are ONLY used when:
- Materializing a historical commit (not the latest)
- The commit history is deep (>100 commits from root)
- A snapshot exists in the ancestry

This is **rare** compared to normal operations (query/update on latest commits).

---

## Implementation Plan

### Step 1: Remove In-Memory Snapshot Storage

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotService.java`

**Remove:**
```java
// DELETE THIS:
private final Map<String, Map<String, Snapshot>> latestSnapshots = new ConcurrentHashMap<>();

public void storeSnapshot(String datasetName, Snapshot snapshot) { ... }
public Optional<Snapshot> getLatestSnapshot(String datasetName, String branchName) { ... }
public Map<String, Snapshot> getAllSnapshots(String datasetName) { ... }
public void clearSnapshotsForDataset(String datasetName) { ... }
```

**Replace with Kafka query methods:**
```java
/**
 * Finds the most recent snapshot for a dataset that is an ancestor of the target commit.
 * Queries Kafka snapshot topic to find snapshots on-demand (not stored in memory).
 *
 * @param datasetName the dataset name
 * @param targetCommit the commit we're trying to materialize
 * @return Optional containing the best snapshot to use, or empty if none found
 */
public Optional<SnapshotInfo> findBestSnapshot(String datasetName, CommitId targetCommit) {
  // Query Kafka for snapshots of this dataset
  // Filter to snapshots that are ancestors of targetCommit
  // Return the most recent one

  // Implementation will use KafkaConsumer to read snapshot topic
  // This is OK because it's rare (only for historical queries)
}

/**
 * Metadata about a snapshot (without the actual graph data).
 */
public record SnapshotInfo(
    CommitId commitId,
    String branchName,
    Instant timestamp,
    String topicPartition,
    long offset
) {}

/**
 * Fetches the actual snapshot data from Kafka.
 *
 * @param info the snapshot metadata
 * @return the snapshot with materialized graph
 */
public Snapshot fetchSnapshot(SnapshotInfo info) {
  // Fetch the actual SnapshotCreatedEvent from Kafka
  // Deserialize the N-Quads
  // Materialize into DatasetGraph
  // Return Snapshot
}
```

---

### Step 2: Update DatasetService to Query Kafka

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**Before:**
```java
private Optional<Snapshot> findNearestSnapshot(String datasetName, CommitId targetCommit) {
  // Get all snapshots from in-memory map
  Map<String, Snapshot> branchSnapshots = snapshotService.getAllSnapshots(datasetName);

  // ... filter and return
}
```

**After:**
```java
private Optional<Snapshot> findNearestSnapshot(String datasetName, CommitId targetCommit) {
  // Find best snapshot by querying Kafka
  Optional<SnapshotInfo> snapshotInfo =
      snapshotService.findBestSnapshot(datasetName, targetCommit);

  if (snapshotInfo.isEmpty()) {
    return Optional.empty();
  }

  // Fetch the actual snapshot data from Kafka (only if we need it)
  return Optional.of(snapshotService.fetchSnapshot(snapshotInfo.get()));
}
```

**Note:** We could optimize further by caching the metadata (SnapshotInfo) since it's small, but NOT the actual graph.

---

### Step 3: Implement Kafka Snapshot Queries

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotService.java`

```java
@Service
public class SnapshotService {

  private final CommitRepository commitRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final KafkaProperties kafkaProperties;

  // Optional: Cache ONLY metadata (tiny), not graphs
  private final Cache<String, List<SnapshotInfo>> snapshotMetadataCache;

  /**
   * Finds the best snapshot by querying Kafka snapshot topic.
   */
  public Optional<SnapshotInfo> findBestSnapshot(String datasetName, CommitId targetCommit) {
    // Check cache first (metadata only - very small)
    List<SnapshotInfo> metadata = snapshotMetadataCache.get(datasetName,
        key -> loadSnapshotMetadataFromKafka(datasetName));

    // Find the best snapshot that's an ancestor of targetCommit
    return metadata.stream()
        .filter(info -> isAncestor(datasetName, targetCommit, info.commitId()))
        .max(Comparator.comparing(SnapshotInfo::timestamp));
  }

  /**
   * Loads snapshot metadata from Kafka (without deserializing graphs).
   */
  private List<SnapshotInfo> loadSnapshotMetadataFromKafka(String datasetName) {
    List<SnapshotInfo> metadata = new ArrayList<>();

    // Create a consumer to read snapshot events
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaProperties.getBootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        JsonDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG,
        "snapshot-query-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, SnapshotCreatedEvent> consumer =
        new KafkaConsumer<>(props)) {

      String topic = "vc.events." + datasetName;
      consumer.subscribe(List.of(topic));

      // Read all snapshot events (until caught up)
      ConsumerRecords<String, SnapshotCreatedEvent> records =
          consumer.poll(Duration.ofSeconds(5));

      for (ConsumerRecord<String, SnapshotCreatedEvent> record : records) {
        SnapshotCreatedEvent event = record.value();

        // Create metadata (no graph data)
        metadata.add(new SnapshotInfo(
            CommitId.of(event.commitId()),
            event.branchName(),
            event.timestamp(),
            record.topic(),
            record.offset()
        ));
      }
    }

    logger.debug("Loaded {} snapshot metadata entries from Kafka for dataset {}",
        metadata.size(), datasetName);

    return metadata;
  }

  /**
   * Fetches actual snapshot data from Kafka.
   */
  public Snapshot fetchSnapshot(SnapshotInfo info) {
    // Fetch the event from Kafka at specific partition/offset
    SnapshotCreatedEvent event = fetchEventFromKafka(
        info.topicPartition(),
        info.offset()
    );

    // Deserialize N-Quads into DatasetGraph
    DatasetGraph graph = deserializeNquads(event.nquads());

    return new Snapshot(
        info.commitId(),
        info.branchName(),
        info.timestamp(),
        graph
    );
  }

  /**
   * Checks if a commit is an ancestor (reuse from DatasetService).
   */
  private boolean isAncestor(String datasetName, CommitId descendant,
      CommitId ancestorCandidate) {
    // Same implementation as DatasetService.isAncestor()
    // Could be extracted to a utility class
  }
}
```

---

### Step 4: Remove ReadModelProjector Snapshot Handling

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Remove:**
```java
// DELETE THIS:
@KafkaListener(topics = "#{kafkaTopics.getAllTopics()}", ...)
public void handleEvent(ConsumerRecord<String, Object> record) {
  // ...
  if (event instanceof SnapshotCreatedEvent snapshot) {
    handleSnapshotCreated(snapshot);  // DELETE
  }
}

private void handleSnapshotCreated(SnapshotCreatedEvent event) {
  // DELETE THIS ENTIRE METHOD
  // We don't need to store snapshots in memory anymore
}
```

**Why?** Snapshots stay in Kafka. We query them on-demand.

---

### Step 5: Optional Optimization - Metadata Cache

**Keep a small cache of snapshot metadata** (NOT graphs):

```java
// In SnapshotService
private final Cache<String, List<SnapshotInfo>> snapshotMetadataCache;

public SnapshotService(...) {
  // Cache metadata for 10 minutes
  this.snapshotMetadataCache = Caffeine.newBuilder()
      .maximumSize(100)  // 100 datasets
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .build();
}
```

**Memory savings:**
- Before: 50 GB of snapshot graphs in RAM
- After: ~100 KB of metadata (commitId + timestamp + offset)
- **Savings: 99.9998%**

---

## Configuration

**application.yml:**
```yaml
vc:
  snapshots:
    enabled: true
    interval: 100           # Create snapshot every 100 commits
    metadata-cache-ttl: 600 # Cache metadata for 10 min (optional)
```

**Kafka topic:**
- Snapshots stored in same topic as events (compacted)
- Or separate `vc.snapshots.{dataset}` topic (compacted by commitId)

---

## Performance Analysis

### Memory Usage

**Before:**
- 10 datasets × 5 branches = 50 snapshots
- Each snapshot = 1 GB
- **Total: 50 GB RAM**

**After:**
- 10 datasets × metadata = ~10 KB
- **Total: 10 KB RAM**
- **Savings: 50 GB → 10 KB (99.99998% reduction!)**

### Query Performance

**Latest commit (most common):**
- Before: No snapshot used (100% cache hit from dataset cache)
- After: No snapshot used (100% cache hit from dataset cache)
- **No change** ✅

**Historical commit with snapshot:**
- Before: Fetch snapshot from RAM (1ms)
- After: Fetch snapshot from Kafka (50-100ms)
- **Slower, but acceptable** (rare operation) ✅

**Historical commit without snapshot:**
- Before: Build from scratch (500ms)
- After: Build from scratch (500ms)
- **No change** ✅

### Trade-off

- 🎉 **Save 50GB+ of RAM**
- 💰 **50-100ms slower for historical queries** (which are rare)
- ✅ **No impact on current work performance**

This is an **excellent trade-off** for production systems!

---

## Testing Strategy

### Unit Tests

```java
@Test
void findBestSnapshot_withMultipleSnapshots_shouldReturnMostRecent() {
  // Create snapshots in Kafka
  publishSnapshot(dataset, commit1, timestamp1);
  publishSnapshot(dataset, commit5, timestamp5);
  publishSnapshot(dataset, commit10, timestamp10);

  // Find best snapshot for commit 12
  Optional<SnapshotInfo> result = snapshotService.findBestSnapshot(
      dataset,
      CommitId.of("commit12")
  );

  // Should return commit 10 (most recent ancestor)
  assertThat(result).isPresent();
  assertThat(result.get().commitId()).isEqualTo(CommitId.of("commit10"));
}

@Test
void fetchSnapshot_shouldDeserializeFromKafka() {
  // Publish snapshot event
  SnapshotInfo info = publishSnapshot(dataset, commitId, timestamp);

  // Fetch snapshot
  Snapshot snapshot = snapshotService.fetchSnapshot(info);

  // Verify graph is materialized
  assertThat(snapshot.graph()).isNotNull();
  assertThat(snapshot.commitId()).isEqualTo(commitId);
}
```

### Integration Tests

```java
@SpringBootTest
@EmbeddedKafka
class SnapshotServiceIntegrationTest {

  @Test
  void materializeHistoricalCommit_shouldFetchSnapshotFromKafka() {
    // Create 200 commits
    List<Commit> commits = createCommitChain(dataset, 200);

    // Snapshot created at commit 100 (via SnapshotService)
    // Wait for Kafka to persist
    await().until(() -> snapshotExistsInKafka(dataset, commits.get(100).id()));

    // Clear all in-memory caches
    datasetService.clearAllCaches();

    // Materialize commit 150 (should use snapshot 100 from Kafka)
    DatasetGraph result = datasetService.materializeCommit(
        dataset,
        commits.get(150).id()
    );

    assertThat(result).isNotNull();
    // Verify snapshot was fetched (check logs or metrics)
  }
}
```

---

## Migration Path

### Phase 1: Add Kafka Query (No Breaking Changes)
- Implement `findBestSnapshot()` and `fetchSnapshot()`
- Keep old in-memory map as fallback
- Log when Kafka queries happen

### Phase 2: Enable Kafka Query (Default)
- Switch to Kafka queries by default
- Keep in-memory map for 1 release as backup
- Monitor logs/metrics

### Phase 3: Remove In-Memory Map (Breaking)
- Delete `latestSnapshots` map
- Delete `storeSnapshot()` method
- Remove ReadModelProjector snapshot handling
- **50GB RAM freed!** 🎉

---

## Success Criteria

- [x] In-memory snapshot map removed
- [x] Snapshots queried from Kafka on-demand
- [x] Metadata cache (optional) working
- [x] Historical queries work correctly (with snapshots)
- [x] Latest commit queries unaffected (still fast)
- [x] Memory usage reduced by ~50GB
- [x] All tests pass

---

## Rollback Plan

If issues arise:
1. Re-enable in-memory map temporarily
2. Use feature flag: `vc.snapshots.use-memory-cache: true`
3. Monitor and debug Kafka query issues

---

## Future Enhancements

- **Snapshot Pruning:** Delete old snapshots from Kafka (keep only last N per branch)
- **Compression:** Compress N-Quads before storing in Kafka
- **Distributed Cache:** Use Redis for snapshot metadata (across nodes)
- **Pre-warming:** Pre-fetch snapshot metadata on startup (async)
