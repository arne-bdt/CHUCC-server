# Task 01: Enable Eager Cache Updates for Branch HEADs

**Status:** Not Started
**Priority:** High (Foundation for instant queries)
**Category:** Architecture Enhancement
**Estimated Time:** 2-3 hours
**Complexity:** Low-Medium

---

## Overview

Add eager cache update capability to `DatasetService` so that branch HEAD graphs can be updated immediately when commits arrive, instead of being rebuilt on every query. This transforms the cache from **lazy/read-through** to **eager/write-through** for branch HEADs.

**Current Problem:**
- Cache is **lazy**: graphs built on first query (50-200ms latency)
- Even with `keepLatestPerBranch=true`, first query or cache eviction triggers rebuild
- No way to update cached graphs when new commits arrive

**Solution:**
- Add `applyPatchToBranchCache()` method to DatasetService
- ReadModelProjector calls this when processing CommitCreatedEvent
- Branch HEADs always up-to-date, queries instant (<10ms)

---

## Current State

### Existing Cache Infrastructure

**File:** [DatasetService.java:252-271](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L252-L271)

```java
private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    CacheKey key = new CacheKey(datasetName, commitId);

    // For latest commits, use get() which ensures they stay in cache
    if (cacheProperties.isKeepLatestPerBranch() && isLatestCommit(datasetName, commitId)) {
        return datasetCache.get(key, k -> buildDatasetGraph(datasetName, commitId));
    }

    // For non-latest commits, use regular get (subject to LRU eviction)
    DatasetGraphInMemory graph = datasetCache.getIfPresent(key);

    if (graph == null) {
        // Cache miss - build graph
        graph = buildDatasetGraph(datasetName, commitId);
        datasetCache.put(key, graph);
    }

    return graph;
}
```

**What Works:**
- ✅ Branch HEADs pinned in cache (never evicted)
- ✅ Historical commits use LRU eviction
- ✅ Snapshot optimization for faster builds

**What's Missing:**
- ❌ Graphs built **on-demand** (query time, not write time)
- ❌ No way to update cached graph when commit arrives
- ❌ Rebuilds entire graph even for simple patches

---

## Requirements

### Functional Requirements

1. **Eager Patch Application API**
   - New method: `applyPatchToBranchCache(dataset, branch, commitId, patch)`
   - Applies patch to cached graph (if present)
   - Creates new graph if not cached yet
   - Thread-safe, transactional using Jena's ReadWrite interface

2. **Cache Invalidation on Branch Update**
   - When branch HEAD moves, old commit can be evicted from pinned set
   - New commit becomes pinned
   - Maintains `keepLatestPerBranch` semantics

3. **Backward Compatibility**
   - Existing lazy cache still works (fallback if eager update fails)
   - No API changes to public methods
   - All existing tests pass

### Non-Functional Requirements

1. **Performance**
   - Patch application: <10ms (Jena in-memory operation)
   - No blocking of query threads
   - Thread-safe concurrent access

2. **Reliability**
   - Transactional patch application (commit or rollback)
   - Graceful degradation: if eager update fails, lazy rebuild still works
   - Logging for debugging

---

## Implementation Steps

### Step 1: Add Eager Patch Application Method

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

```java
/**
 * Eagerly apply a patch to a branch's cached graph.
 *
 * <p>This method updates the cached graph for a branch HEAD when a new commit
 * arrives, enabling instant query performance. If the graph isn't cached yet,
 * it's built first.
 *
 * <p>This is called by ReadModelProjector when processing CommitCreatedEvent,
 * transforming the cache from lazy (query-time) to eager (write-time) updates.
 *
 * @param datasetName the dataset name
 * @param branch the branch name
 * @param newCommitId the new HEAD commit ID
 * @param patch the RDF patch to apply
 * @throws IllegalArgumentException if branch doesn't exist
 */
public void applyPatchToBranchCache(String datasetName, String branch,
    CommitId newCommitId, RDFPatch patch) {

  logger.debug("Eagerly applying patch to cached graph for {}/{}", datasetName, branch);

  // Get or create graph for the branch's *old* commit
  // (We'll apply the patch to move it to the new commit)
  Branch branchObj = branchRepository.findByDatasetAndName(datasetName, branch)
      .orElseThrow(() -> new IllegalArgumentException(
          "Branch not found: " + branch + " in dataset: " + datasetName));

  CacheKey oldKey = new CacheKey(datasetName, branchObj.getCommitId());
  CacheKey newKey = new CacheKey(datasetName, newCommitId);

  // Get existing graph or build from previous state
  DatasetGraphInMemory graph = datasetCache.get(oldKey,
      k -> buildDatasetGraph(datasetName, branchObj.getCommitId()));

  // Apply patch within transaction
  graph.begin(ReadWrite.WRITE);
  try {
    RDFPatchOps.applyChange(graph, patch);
    graph.commit();

    logger.debug("Successfully applied patch to cached graph {}/{}", datasetName, branch);
  } catch (Exception e) {
    graph.abort();
    logger.error("Failed to apply patch to cached graph {}/{}. Graph will be rebuilt on next query.",
        datasetName, branch, e);
    // Don't throw - graceful degradation
    // Next query will rebuild the graph
    return;
  } finally {
    graph.end();
  }

  // Update cache with new commit ID
  // Remove old key (if different)
  if (!oldKey.equals(newKey)) {
    datasetCache.put(newKey, graph);
    // Old key will be evicted by LRU if not pinned elsewhere
  }

  // Update latest commit tracking
  updateLatestCommit(datasetName, branch, newCommitId);
}
```

**Key Design Decisions:**
- Reuses existing cache infrastructure (no new store)
- Transactional patch application (safe)
- Graceful degradation (doesn't fail if patch fails)
- Integrates with existing `keepLatestPerBranch` pinning

### Step 2: Make updateLatestCommit Public

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Change visibility from package-private to public (if needed):

```java
/**
 * Update the latest commit cache for a branch.
 *
 * <p>Called by ReadModelProjector when branch HEAD changes.
 *
 * @param dataset the dataset name
 * @param branch the branch name
 * @param newCommit the new HEAD commit ID
 */
public void updateLatestCommit(String dataset, String branch, CommitId newCommit) {
  Map<String, CommitId> branches = latestCommits.get(dataset);
  if (branches == null) {
    branches = new ConcurrentHashMap<>();
    latestCommits.put(dataset, branches);
  }
  branches.put(branch, newCommit);
  logger.debug("Updated latest commit for {}/{} to {}", dataset, branch, newCommit);
}
```

### Step 3: Add Cache Invalidation for Branch Deletion

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Enhance existing `evictBranchCache()` method:

```java
/**
 * Evict all cached graphs for a branch.
 *
 * <p>Called when a branch is deleted to free memory.
 *
 * @param datasetName the dataset name
 * @param branchName the branch name
 */
public void evictBranchCache(String datasetName, String branchName) {
  // Remove from latest commits tracking
  Map<String, CommitId> branches = latestCommits.get(datasetName);
  if (branches != null) {
    CommitId removed = branches.remove(branchName);
    if (removed != null) {
      CacheKey key = new CacheKey(datasetName, removed);
      datasetCache.invalidate(key);
      logger.debug("Evicted cached graph for deleted branch {}/{}", datasetName, branchName);
    }
  }
}
```

### Step 4: Add Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/DatasetServiceTest.java`

```java
@Test
void applyPatchToBranchCache_shouldUpdateCachedGraph() {
  // Arrange
  String dataset = "test-dataset";
  String branch = "main";
  CommitId oldCommit = CommitId.generate();
  CommitId newCommit = CommitId.generate();

  Branch branchObj = new Branch(branch, oldCommit, Instant.now(), Instant.now());
  when(branchRepository.findByDatasetAndName(dataset, branch))
      .thenReturn(Optional.of(branchObj));

  // Mock graph building for old commit
  DatasetGraphInMemory graph = new DatasetGraphInMemory();
  mockGraphBuilding(dataset, oldCommit, graph);

  // Create a simple patch
  String patchStr = """
      TX .
      A <http://example.org/g> <http://example.org/s> <http://example.org/p> "value" .
      TC .
      """;
  RDFPatch patch = parsePatch(patchStr);

  // Act
  datasetService.applyPatchToBranchCache(dataset, branch, newCommit, patch);

  // Assert: Graph should contain the new triple
  graph.begin(ReadWrite.READ);
  try {
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI("http://example.org/s");
    Node p = NodeFactory.createURI("http://example.org/p");
    Node o = NodeFactory.createLiteral("value");
    Quad quad = Quad.create(g, s, p, o);

    assertThat(graph.contains(quad)).isTrue();
  } finally {
    graph.end();
  }

  // Assert: Cache updated with new commit ID
  CacheKey newKey = new CacheKey(dataset, newCommit);
  assertThat(datasetCache.getIfPresent(newKey)).isSameAs(graph);
}

@Test
void applyPatchToBranchCache_shouldHandleFailuresGracefully() {
  // Arrange: Invalid patch
  String dataset = "test-dataset";
  String branch = "main";
  CommitId oldCommit = CommitId.generate();
  CommitId newCommit = CommitId.generate();

  Branch branchObj = new Branch(branch, oldCommit, Instant.now(), Instant.now());
  when(branchRepository.findByDatasetAndName(dataset, branch))
      .thenReturn(Optional.of(branchObj));

  DatasetGraphInMemory graph = new DatasetGraphInMemory();
  mockGraphBuilding(dataset, oldCommit, graph);

  // Invalid patch (missing TC)
  String invalidPatch = "TX .\nA <s> <p> <o> .\n";
  RDFPatch patch = parsePatch(invalidPatch);

  // Act: Should not throw exception
  assertThatCode(() ->
      datasetService.applyPatchToBranchCache(dataset, branch, newCommit, patch)
  ).doesNotThrowAnyException();

  // Assert: Error logged, but service continues
  // Next query will rebuild the graph (lazy fallback)
}
```

### Step 5: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run new tests
mvn -q test -Dtest=DatasetServiceTest

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ `applyPatchToBranchCache()` method added to DatasetService
- ✅ Method updates cached graphs transactionally using Jena transactions
- ✅ Graceful error handling (doesn't fail on patch errors)
- ✅ Cache invalidation works for deleted branches
- ✅ All existing tests pass (no regressions)
- ✅ New unit tests verify eager update behavior
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Unit Tests (No Integration Needed)

All tests in this phase are unit tests:
- Mock BranchRepository
- Mock graph building
- Test patch application
- Test error handling
- Test cache key management

**No projector needed** - this is just adding infrastructure that will be used later.

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/service/DatasetService.java`

### Test Code
- `src/test/java/org/chucc/vcserver/service/DatasetServiceTest.java`

**Total:** 2 files modified

**No new classes created** - enhancing existing infrastructure!

---

## Comparison: This Approach vs. Original Plan

### Original Plan (MaterializedBranchRepository)
- ❌ New repository class (~300 LOC)
- ❌ Duplicate storage (cache + repository)
- ❌ 2x memory usage
- ❌ Complex integration
- ❌ Expensive branch cloning
- ✅ Clean separation of concerns

### This Approach (Eager Cache Updates)
- ✅ One method added to existing class (~50 LOC)
- ✅ Single storage (existing cache)
- ✅ Same memory usage
- ✅ Simple integration
- ✅ Reuses snapshot optimization
- ✅ Pragmatic, minimal changes

**Both achieve the same goal: 10-20x faster queries!**

---

## Design Rationale

### Why Enhance Cache Instead of New Repository?

1. **Already Have Pinning:** `keepLatestPerBranch=true` already prevents eviction
2. **Only Missing Eager Updates:** Just need write-time update capability
3. **Less Duplication:** One graph store, not two
4. **Simpler Code:** Fewer classes, clearer responsibilities
5. **Better Memory:** No duplicate storage overhead

### Why This is Still CQRS Compliant

- ✅ DatasetService is query-side (read model)
- ✅ Cache is a read model optimization
- ✅ ReadModelProjector updates read models (including cache)
- ✅ Events remain source of truth in CommitRepository
- ✅ Same eventual consistency guarantees

**Pattern:** Projector maintains multiple read models (CommitRepository + BranchRepository + DatasetService cache)

---

## Next Steps

After completing this task:
1. ✅ Move to Task 02: Update ReadModelProjector to call `applyPatchToBranchCache()`
2. ✅ Update `.tasks/README.md` to track progress

---

## References

- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Current implementation
- [CacheProperties.java](../../src/main/java/org/chucc/vcserver/config/CacheProperties.java) - Cache configuration
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - Will use this in Task 02

---

## Notes

### Why Not Just Make Cache Write-Through?

Caffeine cache doesn't support eager updates directly. We're adding application-level logic to update cached values when events arrive, effectively implementing a **projector-driven cache update pattern**.

### Memory Impact

**Before:** 100 branches pinned in cache ~= 200 MB

**After:** 100 branches pinned in cache ~= 200 MB (same!)

**Benefit:** Faster access (no rebuild), same memory.
