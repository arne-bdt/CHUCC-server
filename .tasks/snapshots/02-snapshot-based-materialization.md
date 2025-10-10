# Task: Use Snapshots for Faster Dataset Materialization

**Status:** Not Started
**Priority:** High
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** Task 01 (snapshot recovery on startup)

---

## Context

Currently, when materializing a dataset at a specific commit (e.g., for time-travel queries or branch queries), the system:

1. Walks commit history backwards to find all ancestors
2. Applies patches sequentially from oldest to newest

**Problem:** For deep histories (1000+ commits), this is slow.

**Current algorithm:**
```
Query commit N
  → Find ancestors [0, 1, 2, ..., N]
  → Apply patch 0 → Apply patch 1 → ... → Apply patch N
  → Time: O(N)
```

**Optimized algorithm (with snapshots):**
```
Query commit N
  → Find nearest snapshot S (S <= N)
  → Load snapshot S
  → Apply patches [S+1, S+2, ..., N]
  → Time: O(N - S) where (N - S) << N
```

---

## Goals

1. When materializing a commit, find nearest snapshot
2. Load snapshot as starting point
3. Apply only incremental patches
4. Maintain correctness (snapshot + patches = correct state)

---

## Implementation Plan

### Step 1: Add Snapshot Lookup to DatasetService

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**Current method:**
```java
private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
  DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
  Commit commit = commitRepository.findByDatasetAndId(datasetName, commitId).orElseThrow();
  applyPatchHistory(datasetName, commit, datasetGraph);
  return datasetGraph;
}
```

**Optimized method:**
```java
private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
  // Try to find nearest snapshot
  Optional<Snapshot> snapshotOpt = findNearestSnapshot(datasetName, commitId);

  DatasetGraphInMemory datasetGraph;
  CommitId startCommit;

  if (snapshotOpt.isPresent()) {
    Snapshot snapshot = snapshotOpt.get();
    logger.debug("Using snapshot at commit {} for dataset {} (target: {})",
      snapshot.commitId(), datasetName, commitId);

    // Clone snapshot graph as starting point
    datasetGraph = cloneDatasetGraph(snapshot.graph());
    startCommit = snapshot.commitId();

    // If snapshot IS the target commit, we're done
    if (snapshot.commitId().equals(commitId)) {
      return datasetGraph;
    }
  } else {
    logger.debug("No snapshot found for dataset {} at commit {}, building from scratch",
      datasetName, commitId);
    datasetGraph = new DatasetGraphInMemory();
    startCommit = null;  // Start from beginning
  }

  // Apply patches from snapshot (or beginning) to target commit
  Commit targetCommit = commitRepository.findByDatasetAndId(datasetName, commitId).orElseThrow();
  applyPatchHistorySince(datasetName, targetCommit, datasetGraph, startCommit);

  return datasetGraph;
}

/**
 * Finds the nearest snapshot at or before the target commit.
 */
private Optional<Snapshot> findNearestSnapshot(String datasetName, CommitId targetCommit) {
  // Get all snapshots for this dataset
  Map<String, Snapshot> branchSnapshots = snapshotService.getAllSnapshots(datasetName);

  if (branchSnapshots.isEmpty()) {
    return Optional.empty();
  }

  // Find snapshots in the commit history of targetCommit
  Set<CommitId> commitHistory = getCommitHistory(datasetName, targetCommit);

  // Find latest snapshot that's in the history
  return branchSnapshots.values().stream()
    .filter(snapshot -> commitHistory.contains(snapshot.commitId()))
    .max(Comparator.comparing(Snapshot::timestamp));
}

/**
 * Gets all commits in the history of a commit (ancestors).
 */
private Set<CommitId> getCommitHistory(String datasetName, CommitId commitId) {
  Set<CommitId> history = new LinkedHashSet<>();
  collectCommitHistory(datasetName, commitId, history);
  return history;
}

private void collectCommitHistory(String datasetName, CommitId commitId, Set<CommitId> history) {
  if (history.contains(commitId)) {
    return;  // Already visited (cycle detection)
  }

  history.add(commitId);

  Commit commit = commitRepository.findByDatasetAndId(datasetName, commitId).orElse(null);
  if (commit != null) {
    for (CommitId parent : commit.parents()) {
      collectCommitHistory(datasetName, parent, history);
    }
  }
}

/**
 * Applies patches from a starting commit to target commit.
 * If startCommit is null, applies from beginning.
 */
private void applyPatchHistorySince(String datasetName, Commit targetCommit,
    DatasetGraphInMemory datasetGraph, CommitId startCommit) {

  // Get commit chain from startCommit to targetCommit
  List<Commit> commits = getCommitChain(datasetName, targetCommit, startCommit);

  // Apply patches in order
  for (Commit commit : commits) {
    commitRepository.findPatchByDatasetAndId(datasetName, commit.id())
      .ifPresent(patch -> RDFPatchOps.applyChange(datasetGraph, patch));
  }
}

/**
 * Gets the chain of commits from startCommit (exclusive) to targetCommit (inclusive).
 */
private List<Commit> getCommitChain(String datasetName, Commit targetCommit, CommitId startCommit) {
  List<Commit> chain = new ArrayList<>();
  collectCommitChain(datasetName, targetCommit, startCommit, chain);
  Collections.reverse(chain);  // Oldest first
  return chain;
}

private void collectCommitChain(String datasetName, Commit current, CommitId stopAt, List<Commit> chain) {
  if (stopAt != null && current.id().equals(stopAt)) {
    return;  // Reached snapshot commit (don't include it)
  }

  chain.add(current);

  // Recursively process parents
  for (CommitId parentId : current.parents()) {
    Commit parent = commitRepository.findByDatasetAndId(datasetName, parentId).orElse(null);
    if (parent != null) {
      collectCommitChain(datasetName, parent, stopAt, chain);
    }
  }
}

/**
 * Clones a DatasetGraph (deep copy).
 */
private DatasetGraphInMemory cloneDatasetGraph(DatasetGraph source) {
  DatasetGraphInMemory clone = new DatasetGraphInMemory();

  // Copy default graph
  source.getDefaultGraph().find().forEachRemaining(triple -> {
    clone.getDefaultGraph().add(triple);
  });

  // Copy named graphs
  source.listGraphNodes().forEachRemaining(graphNode -> {
    source.getGraph(graphNode).find().forEachRemaining(triple -> {
      clone.getGraph(graphNode).add(triple);
    });
  });

  return clone;
}
```

---

### Step 2: Add Snapshot Management to SnapshotService

**File:** `src/main/java/org/chucc/vcserver/service/SnapshotService.java`

```java
// Add method to get all snapshots for a dataset
public Map<String, Snapshot> getAllSnapshots(String datasetName) {
  return latestSnapshots.getOrDefault(datasetName, Map.of());
}

// Add method to store snapshot (called by ReadModelProjector)
public void storeSnapshot(String datasetName, Snapshot snapshot) {
  latestSnapshots
    .computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
    .put(snapshot.branchName(), snapshot);

  logger.info("Stored snapshot for {}/{} at commit {}",
    datasetName, snapshot.branchName(), snapshot.commitId());
}
```

---

### Step 3: Add Metrics for Monitoring

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

```java
@Service
public class DatasetService {

  // Add counters
  private final Counter snapshotHits;
  private final Counter snapshotMisses;

  public DatasetService(BranchRepository branchRepository,
      CommitRepository commitRepository,
      MeterRegistry meterRegistry,
      SnapshotService snapshotService) {

    this.snapshotService = snapshotService;

    // Register metrics
    this.snapshotHits = Counter.builder("dataset.snapshot.hits")
      .description("Number of times snapshot was used for materialization")
      .register(meterRegistry);

    this.snapshotMisses = Counter.builder("dataset.snapshot.misses")
      .description("Number of times no snapshot was found")
      .register(meterRegistry);
  }

  private Optional<Snapshot> findNearestSnapshot(String datasetName, CommitId targetCommit) {
    Optional<Snapshot> snapshot = /* ... */;

    if (snapshot.isPresent()) {
      snapshotHits.increment();
    } else {
      snapshotMisses.increment();
    }

    return snapshot;
  }
}
```

---

### Step 4: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/service/DatasetServiceSnapshotTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class DatasetServiceSnapshotTest {

  @Autowired
  private DatasetService datasetService;

  @Autowired
  private SnapshotService snapshotService;

  @Autowired
  private CommitRepository commitRepository;

  @Test
  void materializeCommit_withSnapshot_shouldApplyOnlyIncrementalPatches() {
    String dataset = "test";
    String branch = "main";

    // Create commit chain: C0 → C1 → C2 → C3 → C4 → C5
    List<Commit> commits = createCommitChain(dataset, 6);

    // Create snapshot at C3
    DatasetGraph snapshotGraph = materializeCommit(dataset, commits.get(3).id());
    Snapshot snapshot = new Snapshot(
      commits.get(3).id(),
      branch,
      Instant.now(),
      snapshotGraph
    );
    snapshotService.storeSnapshot(dataset, snapshot);

    // Materialize C5 (should use snapshot at C3, apply C4 and C5)
    DatasetGraph result = datasetService.materializeCommit(dataset, commits.get(5).id());

    // Verify correctness
    assertThat(result).isNotNull();

    // Verify snapshot was used (check metrics)
    assertThat(getSnapshotHitCount()).isEqualTo(1);
  }

  @Test
  void materializeCommit_withoutSnapshot_shouldBuildFromScratch() {
    String dataset = "test";

    // Create commit chain without snapshot
    List<Commit> commits = createCommitChain(dataset, 5);

    // Materialize C4 (no snapshot, build from scratch)
    DatasetGraph result = datasetService.materializeCommit(dataset, commits.get(4).id());

    assertThat(result).isNotNull();

    // Verify snapshot miss
    assertThat(getSnapshotMissCount()).isEqualTo(1);
  }

  @Test
  void materializeCommit_snapshotIsTarget_shouldReturnSnapshotDirectly() {
    String dataset = "test";
    String branch = "main";

    // Create commit chain
    List<Commit> commits = createCommitChain(dataset, 3);

    // Create snapshot at C2
    DatasetGraph snapshotGraph = materializeCommit(dataset, commits.get(2).id());
    Snapshot snapshot = new Snapshot(
      commits.get(2).id(),
      branch,
      Instant.now(),
      snapshotGraph
    );
    snapshotService.storeSnapshot(dataset, snapshot);

    // Materialize C2 (snapshot IS the target)
    DatasetGraph result = datasetService.materializeCommit(dataset, commits.get(2).id());

    // Verify no patches applied
    assertThat(result).isNotNull();
    // Would need to verify no patches were applied (check logs or add counter)
  }
}
```

---

## Performance Impact

**Before (without snapshots):**
- Materialize commit 1000: Apply 1000 patches (~500ms)
- Materialize commit 5000: Apply 5000 patches (~2.5s)

**After (with snapshots every 100 commits):**
- Materialize commit 1000: Load snapshot at 900 + apply 100 patches (~50ms) = **10x faster**
- Materialize commit 5000: Load snapshot at 4900 + apply 100 patches (~50ms) = **50x faster**

---

## Testing Strategy

1. **Unit tests:**
   - Test `findNearestSnapshot()` logic
   - Test `getCommitChain()` with various histories
   - Test `cloneDatasetGraph()`

2. **Integration tests:**
   - Materialize with snapshot
   - Materialize without snapshot
   - Materialize when snapshot IS target
   - Verify correctness (compare with full materialization)

3. **Performance tests:**
   - Create 1000 commits
   - Measure materialization time with/without snapshots
   - Verify speedup

---

## Success Criteria

- [ ] Nearest snapshot found correctly
- [ ] Only incremental patches applied
- [ ] Correctness maintained (same result as full materialization)
- [ ] Metrics show snapshot hit/miss rates
- [ ] Performance improvement measured (10x+ for deep histories)
- [ ] All tests pass

---

## Rollback Plan

If issues arise:
1. Disable snapshot usage via feature flag: `vc.use-snapshots-for-materialization: false`
2. Fall back to full patch replay
3. No data loss

---

## Future Enhancements

- **Snapshot compression:** Store snapshots in compressed format
- **Snapshot pruning:** Keep only latest N snapshots per branch
- **Background snapshot warming:** Pre-create snapshots for frequently accessed commits
