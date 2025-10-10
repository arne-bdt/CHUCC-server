# Task: Implement Snapshot Recovery on Application Startup

**Status:** Not Started
**Priority:** High
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** None (snapshots already created)

---

## Context

Currently, snapshots are created every N commits (configurable via `vc.snapshot-interval`) and stored as `SnapshotCreatedEvent` in Kafka. However, **they are not used for recovery**. On startup, the application replays ALL events from the beginning, which is slow for large datasets.

**Current behavior:**
```
Startup → Replay ALL events (1-N) → Ready
Time: O(N) where N = total events
```

**Desired behavior:**
```
Startup → Find latest snapshot → Load snapshot → Replay events since snapshot → Ready
Time: O(M) where M = events since snapshot (M << N)
```

---

## Goals

1. Load latest snapshot for each branch on startup
2. Replay only events since snapshot
3. Fallback to full replay if no snapshot exists
4. Maintain correctness (snapshot + incremental events = current state)

---

## Implementation Plan

### Step 1: Modify SnapshotService to Store Snapshots

**Current:** Snapshots are only published as events and logged.

**Change:** Add in-memory storage for snapshots.

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotService.java`

```java
// Add field
private final Map<String, Map<String, Snapshot>> latestSnapshots = new ConcurrentHashMap<>();

// Add method
public Optional<Snapshot> getLatestSnapshot(String dataset, String branch) {
  return Optional.ofNullable(latestSnapshots.get(dataset))
    .map(branches -> branches.get(branch));
}

// Modify handleSnapshotCreated in ReadModelProjector
void handleSnapshotCreated(SnapshotCreatedEvent event) {
  // Parse N-Quads into DatasetGraph
  DatasetGraph graph = parseNquads(event.nquads());

  // Store snapshot
  Snapshot snapshot = new Snapshot(
    event.commitId(),
    event.branchName(),
    event.timestamp(),
    graph
  );

  snapshotService.storeSnapshot(event.dataset(), snapshot);

  logger.info("Stored snapshot for {}/{} at commit {}",
    event.dataset(), event.branchName(), event.commitId());
}
```

**Create new domain class:**
```java
// src/main/java/org/chucc/vcserver/domain/Snapshot.java
public record Snapshot(
  CommitId commitId,
  String branchName,
  Instant timestamp,
  DatasetGraph graph
) {}
```

---

### Step 2: Modify ReadModelProjector to Use Snapshots

**Current:** Always replays from offset 0.

**Change:** Track "snapshot checkpoint" per dataset/branch.

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
// Add field to track which events to skip
private final Map<String, Map<String, CommitId>> snapshotCheckpoints = new ConcurrentHashMap<>();

@KafkaListener(...)
public void handleEvent(VersionControlEvent event) {
  // Skip events before snapshot checkpoint
  if (shouldSkipEvent(event)) {
    logger.debug("Skipping event before snapshot: {}", event);
    return;
  }

  // Process event normally
  switch (event) {
    case CommitCreatedEvent e -> handleCommitCreated(e);
    // ... other handlers
  }
}

private boolean shouldSkipEvent(VersionControlEvent event) {
  CommitId checkpoint = snapshotCheckpoints
    .getOrDefault(event.dataset(), Map.of())
    .get(getBranchForEvent(event));

  if (checkpoint == null) {
    return false;  // No snapshot, process all events
  }

  // Skip if event commit <= snapshot commit
  return isCommitBeforeOrEqual(getCommitIdForEvent(event), checkpoint);
}
```

---

### Step 3: Load Snapshots on Startup

**Add initialization logic:**

```java
@Service
public class ReadModelProjector {

  @PostConstruct
  public void initializeFromSnapshots() {
    logger.info("Checking for available snapshots...");

    // This will be populated as we consume SnapshotCreatedEvents
    // No action needed here - snapshots loaded during event replay

    logger.info("Snapshot initialization complete");
  }

  void handleSnapshotCreated(SnapshotCreatedEvent event) {
    // Parse and store snapshot
    DatasetGraph graph = parseNquads(event.nquads());
    Snapshot snapshot = new Snapshot(
      CommitId.of(event.commitId()),
      event.branchName(),
      event.timestamp(),
      graph
    );

    // Store in SnapshotService
    snapshotService.storeSnapshot(event.dataset(), snapshot);

    // Cache the materialized graph
    datasetService.cacheDatasetGraph(
      event.dataset(),
      CommitId.of(event.commitId()),
      graph
    );

    // Set checkpoint to skip earlier events
    snapshotCheckpoints
      .computeIfAbsent(event.dataset(), k -> new ConcurrentHashMap<>())
      .put(event.branchName(), CommitId.of(event.commitId()));

    logger.info("Loaded snapshot for {}/{} at commit {} (skipping earlier events)",
      event.dataset(), event.branchName(), event.commitId());
  }
}
```

---

### Step 4: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/projection/SnapshotRecoveryIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class SnapshotRecoveryIT extends IntegrationTestFixture {

  @Test
  void startup_withSnapshot_shouldSkipEarlierEvents() throws Exception {
    String dataset = "test-dataset";
    String branch = "main";

    // Publish 10 commits
    List<CommitCreatedEvent> commits = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      CommitCreatedEvent event = createTestCommit(dataset, branch);
      eventPublisher.publish(event).get();
      commits.add(event);
    }

    // Publish snapshot at commit 5
    SnapshotCreatedEvent snapshot = new SnapshotCreatedEvent(
      dataset,
      commits.get(4).commitId(),  // 5th commit
      branch,
      Instant.now(),
      materializeAsNquads(dataset, commits.get(4).commitId())
    );
    eventPublisher.publish(snapshot).get();

    // Publish 5 more commits
    for (int i = 0; i < 5; i++) {
      CommitCreatedEvent event = createTestCommit(dataset, branch);
      eventPublisher.publish(event).get();
      commits.add(event);
    }

    // Restart projector (simulate application restart)
    // In real scenario, would restart Spring context

    // Wait for processing
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        Branch br = branchRepository.findByDatasetAndName(dataset, branch).orElseThrow();
        assertThat(br.getCommitId()).isEqualTo(CommitId.of(commits.get(14).commitId()));
      });

    // Verify snapshot was used (check logs or add metric)
    // This is hard to assert directly - rely on logs for now
  }
}
```

---

## Testing Strategy

1. **Unit tests:** Test `shouldSkipEvent()` logic
2. **Integration tests:**
   - Test with snapshot (events skipped)
   - Test without snapshot (full replay)
   - Test with multiple branches and snapshots
3. **Manual testing:**
   - Create 10k commits
   - Observe startup time before/after snapshot optimization

---

## Success Criteria

- [ ] Snapshots loaded from Kafka on startup
- [ ] Events before snapshot are skipped during replay
- [ ] Events after snapshot are processed normally
- [ ] Startup time reduced (e.g., 10k events: 30s → 3s with snapshot every 1000)
- [ ] All existing tests pass
- [ ] New integration tests added

---

## Rollback Plan

If issues arise:
1. Set `vc.snapshots-enabled: false` to disable snapshot creation
2. Snapshots will be ignored, full replay will happen (current behavior)
3. No data loss (events still in Kafka)

---

## Notes

- **Memory consideration:** Snapshots stored in-memory. Each snapshot = full dataset graph (could be large).
- **Future optimization:** Store only latest snapshot per branch, discard older ones.
- **Alternative approach:** Use external storage (S3, disk) for snapshots instead of in-memory.
