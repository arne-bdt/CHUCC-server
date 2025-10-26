# Task 03: Update DatasetService to Use Materialized Views

**Status:** Not Started
**Priority:** High (Query Performance)
**Category:** Architecture Enhancement
**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Prerequisites:** Tasks 01 & 02 completed (MaterializedBranchRepository created and integrated with projector)

---

## Overview

Modify `DatasetService` to use pre-materialized branch graphs from `MaterializedBranchRepository` instead of building them on-demand. This provides instant query response times for branch HEAD queries while maintaining backward compatibility for historical commit queries.

**Current Behavior:**
- All queries trigger on-demand graph building
- Graphs built from scratch or nearest snapshot
- Results cached (LRU eviction)
- Variable latency based on commit history depth

**New Behavior:**
- **Branch HEAD queries:** Return pre-materialized graphs (instant, O(1))
- **Historical commit queries:** Build on-demand (existing behavior)
- **Cache strategy:** Keep historical commits in LRU cache, branch HEADs always available

---

## Current State

### DatasetService - getDataset Method

**File:** [DatasetService.java:124-159](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L124-L159)

```java
public DatasetGraphInMemory getDataset(String datasetName, SelectorType selectorType,
    String selectorValue) throws DatasetNotFoundException {

  validateDatasetExists(datasetName);

  return switch (selectorType) {
    case BRANCH -> {
      // Resolve branch to commit
      Branch branch = branchRepository.findByDatasetAndName(datasetName, selectorValue)
          .orElseThrow(() -> new BranchNotFoundException(datasetName, selectorValue));

      // Build graph for branch HEAD commit
      yield getOrCreateDatasetGraph(datasetName, branch.getCommitId());
    }
    case COMMIT -> {
      CommitId commitId = CommitId.of(selectorValue);
      yield getOrCreateDatasetGraph(datasetName, commitId);
    }
    case TAG -> {
      Tag tag = tagRepository.findByDatasetAndName(datasetName, selectorValue)
          .orElseThrow(() -> new TagNotFoundException(datasetName, selectorValue));
      yield getOrCreateDatasetGraph(datasetName, tag.commitId());
    }
    default -> throw new IllegalArgumentException("Unknown selector type: " + selectorType);
  };
}
```

**Problem:** All paths go through `getOrCreateDatasetGraph()` which builds on-demand.

### getOrCreateDatasetGraph - On-Demand Building

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

**Problem:** Even branch HEADs are built on-demand (first query or cache eviction).

---

## Requirements

### Functional Requirements

1. **Branch HEAD Fast Path**
   - `BRANCH` selector → return materialized graph from `MaterializedBranchRepository`
   - **No graph building** for branch HEADs
   - O(1) lookup time

2. **Historical Commit Path**
   - `COMMIT` selector (non-HEAD) → build on-demand (existing behavior)
   - `TAG` selector → build on-demand (existing behavior)
   - Use cache for frequently accessed historical commits

3. **Backward Compatibility**
   - All existing query interfaces unchanged
   - Existing tests continue to pass
   - No API breaking changes

4. **Transactional Safety**
   - Return graphs in READ mode for queries
   - Prevent query-time modifications
   - Maintain Jena transaction semantics

### Non-Functional Requirements

1. **Performance**
   - Branch HEAD queries: <5ms (instant lookup)
   - Historical queries: Similar to current (50-500ms depending on depth)
   - No performance regressions

2. **Memory**
   - Remove branch HEADs from LRU cache (now in MaterializedBranchRepository)
   - Cache only historical commits
   - Reduced total memory footprint

3. **Reliability**
   - Fallback to on-demand building if materialized graph unavailable
   - Graceful degradation
   - Logging for debugging

---

## Implementation Steps

### Step 1: Add MaterializedBranchRepository Dependency

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**Add field:**
```java
private final MaterializedBranchRepository materializedBranchRepo;
```

**Update constructor:**
```java
public DatasetService(
    DatasetRepository datasetRepository,
    CommitRepository commitRepository,
    BranchRepository branchRepository,
    TagRepository tagRepository,
    SnapshotRepository snapshotRepository,
    CacheProperties cacheProperties,
    MaterializedBranchRepository materializedBranchRepo  // ← NEW
) {
    this.datasetRepository = datasetRepository;
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
    this.tagRepository = tagRepository;
    this.snapshotRepository = snapshotRepository;
    this.cacheProperties = cacheProperties;
    this.materializedBranchRepo = materializedBranchRepo;  // ← NEW

    // Initialize cache
    this.datasetCache = Caffeine.newBuilder()
        .maximumSize(cacheProperties.getMaxSize())
        .recordStats()
        .build();
}
```

### Step 2: Update getDataset for Branch Queries

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java:124-159`

```java
public DatasetGraphInMemory getDataset(String datasetName, SelectorType selectorType,
    String selectorValue) throws DatasetNotFoundException {

  validateDatasetExists(datasetName);

  return switch (selectorType) {
    case BRANCH -> {
      // ✅ NEW: Return materialized graph for branch HEAD
      yield getMaterializedBranchGraph(datasetName, selectorValue);
    }
    case COMMIT -> {
      // Existing: Build on-demand or use cache
      CommitId commitId = CommitId.of(selectorValue);
      yield getOrCreateDatasetGraph(datasetName, commitId);
    }
    case TAG -> {
      // Existing: Build on-demand or use cache
      Tag tag = tagRepository.findByDatasetAndName(datasetName, selectorValue)
          .orElseThrow(() -> new TagNotFoundException(datasetName, selectorValue));
      yield getOrCreateDatasetGraph(datasetName, tag.commitId());
    }
    default -> throw new IllegalArgumentException("Unknown selector type: " + selectorType);
  };
}
```

### Step 3: Implement getMaterializedBranchGraph

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java` (new method)

```java
/**
 * Get the materialized graph for a branch HEAD.
 *
 * <p>This method provides instant access to pre-materialized branch graphs
 * maintained by the ReadModelProjector. If the materialized graph is unavailable
 * for any reason, it falls back to on-demand building.
 *
 * @param datasetName the dataset name
 * @param branchName the branch name
 * @return the materialized DatasetGraph for the branch HEAD
 * @throws BranchNotFoundException if the branch doesn't exist
 */
private DatasetGraphInMemory getMaterializedBranchGraph(String datasetName, String branchName) {
  // Validate branch exists and get HEAD commit
  Branch branch = branchRepository.findByDatasetAndName(datasetName, branchName)
      .orElseThrow(() -> new BranchNotFoundException(datasetName, branchName));

  // Try to get materialized graph
  if (materializedBranchRepo.exists(datasetName, branchName)) {
    logger.debug("Using materialized graph for branch {}/{}", datasetName, branchName);
    DatasetGraph graph = materializedBranchRepo.getBranchGraph(datasetName, branchName);

    // Return as DatasetGraphInMemory (required by interface)
    // Note: MaterializedBranchRepository already returns DatasetGraphInMemory
    return (DatasetGraphInMemory) graph;
  } else {
    // Fallback: Materialized graph not available, build on-demand
    logger.warn("Materialized graph not available for branch {}/{}. Building on-demand.",
        datasetName, branchName);
    return getOrCreateDatasetGraph(datasetName, branch.getCommitId());
  }
}
```

### Step 4: Optimize getOrCreateDatasetGraph (Optional)

Since branch HEADs are now in `MaterializedBranchRepository`, we can simplify cache logic:

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java:252-271`

```java
private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    CacheKey key = new CacheKey(datasetName, commitId);

    // ✅ SIMPLIFIED: No special handling for latest commits
    // (Branch HEADs now served by MaterializedBranchRepository)

    // Check cache
    DatasetGraphInMemory graph = datasetCache.getIfPresent(key);

    if (graph == null) {
        // Cache miss - build graph
        logger.debug("Building graph for dataset={}, commit={}", datasetName, commitId);
        graph = buildDatasetGraph(datasetName, commitId);
        datasetCache.put(key, graph);
    } else {
        logger.debug("Cache hit for dataset={}, commit={}", datasetName, commitId);
    }

    return graph;
}
```

**Removed:**
- `isLatestCommit()` check
- `cacheProperties.isKeepLatestPerBranch()` logic

**Rationale:**
- Branch HEADs no longer need cache pinning
- Cache now only stores historical commits (LRU eviction is fine)
- Simpler code

### Step 5: Remove isLatestCommit Method (Optional Cleanup)

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

If `isLatestCommit()` is only used in `getOrCreateDatasetGraph()`, it can be removed:

```java
// ❌ DELETE THIS METHOD (no longer needed)
private boolean isLatestCommit(String datasetName, CommitId commitId) {
    // ... check if commit is HEAD of any branch ...
}
```

**Check usage first:** Search for `isLatestCommit` to ensure it's not used elsewhere.

### Step 6: Update updateLatestCommit Method

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

This method is called by projector to notify cache updates. Since branch HEADs are no longer cached, this method can be simplified or removed:

**Option A:** Simplify (just clear cache for this branch's old commits)
```java
public void updateLatestCommit(String dataset, String branch, CommitId newCommit) {
  // Branch HEAD is now in MaterializedBranchRepository, no cache update needed
  // Just log for debugging
  logger.debug("Branch {}/{} updated to commit {}", dataset, branch, newCommit);
}
```

**Option B:** Remove entirely
- If not needed for other purposes, delete this method
- Update `ReadModelProjector` to remove this call

**For this task:** Keep method but simplify (Option A).

### Step 7: Update Tests

**File:** `src/test/java/org/chucc/vcserver/service/DatasetServiceTest.java`

Add mock for `MaterializedBranchRepository`:

```java
@Mock
private MaterializedBranchRepository materializedBranchRepo;

@BeforeEach
void setUp() {
  datasetService = new DatasetService(
      datasetRepository,
      commitRepository,
      branchRepository,
      tagRepository,
      snapshotRepository,
      cacheProperties,
      materializedBranchRepo  // ← NEW
  );
}
```

Add tests for materialized graph fast path:

```java
@Test
void getDataset_withBranch_shouldUseMaterializedGraph() {
  // Arrange
  String dataset = "test-dataset";
  String branch = "main";
  CommitId commitId = CommitId.generate();

  Branch branchObj = new Branch(branch, commitId, Instant.now(), Instant.now());
  when(branchRepository.findByDatasetAndName(dataset, branch))
      .thenReturn(Optional.of(branchObj));

  when(materializedBranchRepo.exists(dataset, branch))
      .thenReturn(true);

  DatasetGraphInMemory mockGraph = new DatasetGraphInMemory();
  when(materializedBranchRepo.getBranchGraph(dataset, branch))
      .thenReturn(mockGraph);

  // Act
  DatasetGraphInMemory result = datasetService.getDataset(
      dataset, SelectorType.BRANCH, branch);

  // Assert
  assertThat(result).isSameAs(mockGraph);
  verify(materializedBranchRepo).getBranchGraph(dataset, branch);
  verifyNoInteractions(commitRepository);  // No on-demand building
}

@Test
void getDataset_withBranch_shouldFallbackIfMaterializedGraphUnavailable() {
  // Arrange
  String dataset = "test-dataset";
  String branch = "main";
  CommitId commitId = CommitId.generate();

  Branch branchObj = new Branch(branch, commitId, Instant.now(), Instant.now());
  when(branchRepository.findByDatasetAndName(dataset, branch))
      .thenReturn(Optional.of(branchObj));

  // Materialized graph doesn't exist
  when(materializedBranchRepo.exists(dataset, branch))
      .thenReturn(false);

  // Mock on-demand building
  mockOnDemandGraphBuilding(dataset, commitId);

  // Act
  DatasetGraphInMemory result = datasetService.getDataset(
      dataset, SelectorType.BRANCH, branch);

  // Assert
  assertThat(result).isNotNull();
  verify(commitRepository).findByDatasetAndId(dataset, commitId);  // On-demand building
}
```

### Step 8: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MaterializedViewQueryIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for querying materialized views.
 *
 * <p>Verifies that branch HEAD queries use pre-materialized graphs
 * for instant response times.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable projector!
class MaterializedViewQueryIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void queryBranchHead_shouldReturnMaterializedGraph() throws Exception {
    // Arrange: Create dataset, branch, and data
    String dataset = "test-dataset";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "materialized value" .
        """;

    putGraph(dataset, graphUri, branch, turtle);

    // Wait for materialization
    await().atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> true);  // Simple wait for projection

    // Act: Query branch HEAD via SPARQL
    String sparql = """
        SELECT ?o
        WHERE {
          GRAPH <http://example.org/graph> {
            <http://example.org/subject> <http://example.org/predicate> ?o .
          }
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/sparql-query"));

    HttpEntity<String> request = new HttpEntity<>(sparql, headers);

    // Measure response time
    long startTime = System.currentTimeMillis();

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        request,
        String.class,
        dataset, branch
    );

    long responseTime = System.currentTimeMillis() - startTime;

    // Assert: Response is fast (materialized graph used)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("materialized value");

    // Response should be fast (<100ms for instant lookup)
    assertThat(responseTime)
        .as("Branch HEAD query should be fast (using materialized graph)")
        .isLessThan(100);
  }

  @Test
  void queryHistoricalCommit_shouldBuildOnDemand() throws Exception {
    // Arrange: Create dataset, branch, and multiple commits
    String dataset = "test-dataset";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    // First commit
    putGraph(dataset, graphUri, branch, "@prefix ex: <http://example.org/> . ex:s ex:p \"v1\" .");

    // Wait for first commit
    await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(200)).until(() -> true);

    // Get first commit ID from response
    String firstCommitId = getLatestCommitId(dataset, branch);

    // Second commit
    putGraph(dataset, graphUri, branch, "@prefix ex: <http://example.org/> . ex:s ex:p \"v2\" .");

    // Wait for second commit
    await().atMost(Duration.ofSeconds(5)).pollDelay(Duration.ofMillis(200)).until(() -> true);

    // Act: Query historical commit (first commit, not HEAD)
    String sparql = "SELECT ?o WHERE { GRAPH <http://example.org/graph> { <http://example.org/s> <http://example.org/p> ?o . } }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(sparql, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/datasets/{dataset}?commit={commit}",
        request,
        String.class,
        dataset, firstCommitId
    );

    // Assert: Historical query still works (built on-demand)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("v1");  // First commit value, not v2
  }

  // Helper methods
  private void createDataset(String dataset) {
    restTemplate.postForEntity("/version/datasets/{dataset}", null, Void.class, dataset);
  }

  private void createBranch(String dataset, String branch) {
    restTemplate.postForEntity(
        "/version/branches?dataset={dataset}&name={branch}",
        null, Void.class, dataset, branch
    );
  }

  private void putGraph(String dataset, String graphUri, String branch, String turtle) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/turtle"));
    headers.set("X-Author", "test-author");
    headers.set("X-Commit-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request, Void.class, dataset, graphUri, branch
    );
  }

  private String getLatestCommitId(String dataset, String branch) {
    // Use refs endpoint to get latest commit
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset={dataset}",
        String.class, dataset
    );
    // Parse JSON to extract commit ID (simplified, actual implementation would use JSON parser)
    // For now, return placeholder
    return "commit-id-placeholder";  // TODO: Implement proper parsing
  }
}
```

### Step 9: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=DatasetServiceTest
mvn -q test -Dtest=MaterializedViewQueryIT 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ `MaterializedBranchRepository` dependency added to `DatasetService`
- ✅ Branch HEAD queries use `getMaterializedBranchGraph()` (fast path)
- ✅ Historical commit queries use existing on-demand building (backward compatible)
- ✅ Fallback mechanism works if materialized graph unavailable
- ✅ Simplified cache logic (no branch HEAD pinning)
- ✅ All existing tests pass (no regressions)
- ✅ New tests verify fast path and fallback
- ✅ Integration tests demonstrate performance improvement
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Unit Tests

**Focus:** Verify routing logic
- Branch queries → MaterializedBranchRepository
- Commit queries → On-demand building
- Tag queries → On-demand building
- Fallback when materialized graph unavailable

**Pattern:**
```java
@Test
void getDataset_shouldUseMaterializedGraphForBranch() {
    // Mock MaterializedBranchRepository
    when(materializedBranchRepo.exists(...)).thenReturn(true);
    when(materializedBranchRepo.getBranchGraph(...)).thenReturn(mockGraph);

    // Act
    DatasetGraphInMemory result = datasetService.getDataset(..., BRANCH, ...);

    // Assert
    verify(materializedBranchRepo).getBranchGraph(...);
    verifyNoInteractions(commitRepository);  // No on-demand building
}
```

### Integration Tests (Projector Enabled)

**Focus:** End-to-end performance
- Measure query response time for branch HEADs
- Verify historical queries still work
- Demonstrate performance improvement

**CRITICAL:** Must enable projector:
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/service/DatasetService.java`

### Test Code
- `src/test/java/org/chucc/vcserver/service/DatasetServiceTest.java` (add mock + tests)
- `src/test/java/org/chucc/vcserver/integration/MaterializedViewQueryIT.java` (NEW)

**Total:** 1 modified, 1 new file

---

## Performance Comparison

### Before (On-Demand Building)

**Branch HEAD Query:**
```
1. Resolve branch → commit ID (5ms)
2. Check cache (miss) (1ms)
3. Find nearest snapshot (10ms)
4. Load commits from snapshot to HEAD (20ms)
5. Apply patches (50-200ms, depends on history depth)
6. Cache result (5ms)
Total: 91-241ms
```

### After (Materialized Views)

**Branch HEAD Query:**
```
1. Resolve branch → commit ID (5ms)
2. Lookup materialized graph (O(1)) (<1ms)
Total: <10ms
```

**Performance Improvement:** **10-20x faster** for branch HEAD queries!

---

## Design Decisions

### Decision 1: Fallback Strategy

**Question:** If materialized graph unavailable, should we:
1. Fail with error?
2. Build on-demand as fallback?

**Chosen:** Option 2 (fallback)

**Rationale:**
- Graceful degradation (system still works)
- Materialized graph might be missing during startup or after failures
- Fallback ensures queries always succeed (eventual consistency promise)

### Decision 2: Return Type

**Question:** Should `getMaterializedBranchGraph()` return:
1. `DatasetGraph` (generic interface)?
2. `DatasetGraphInMemory` (specific type)?

**Chosen:** Option 2 (`DatasetGraphInMemory`)

**Rationale:**
- Matches existing `getDataset()` return type
- No changes needed to calling code
- Type safety (callers expect in-memory graphs)

### Decision 3: Cache Simplification

**Question:** Should we keep cache pinning for branch HEADs?

**Chosen:** No, remove it

**Rationale:**
- Branch HEADs now in MaterializedBranchRepository (always available)
- No need to cache them separately
- Simpler code, less memory usage
- Cache can focus on historical commits (true LRU)

---

## Memory Impact

### Before
- LRU cache holds ~1000 graphs (mixed: branch HEADs + historical)
- Pinned graphs: All branch HEADs (if `keep-latest-per-branch=true`)
- Total: ~500 MB (assuming 100 branches + 900 historical)

### After
- MaterializedBranchRepository: All branch HEADs (~100 branches)
- LRU cache: Only historical commits (~1000 graphs, pure LRU)
- Total: ~500 MB (similar, but better organized)

**Memory benefit:** More predictable memory usage, better cache efficiency.

---

## CQRS Compliance Check

| Aspect | Compliance |
|--------|------------|
| **DatasetService is query-side-only** | ✅ Yes - only reads from repositories |
| **No command execution** | ✅ Yes - pure read service |
| **Reads from read models** | ✅ Yes - MaterializedBranchRepository + CommitRepository |
| **No direct event handling** | ✅ Yes - only projector handles events |

---

## References

- [DatasetService.java:124-159](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L124-L159) - getDataset method
- [DatasetService.java:252-271](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L252-L271) - getOrCreateDatasetGraph
- [MaterializedBranchRepository.java](../../src/main/java/org/chucc/vcserver/repository/MaterializedBranchRepository.java) - Task 01
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - Task 02

---

## Next Steps

After completing this task:
1. ✅ Mark this task file as complete
2. ✅ Move to Task 04: Add monitoring and recovery mechanisms
3. ✅ Update `.tasks/README.md` to track progress

---

## Notes

### Query Performance Validation

To validate performance improvement, add benchmarks:

```java
@Test
void benchmark_branchHeadQueries() {
    // Create dataset with 1000 commits
    // Measure query time before and after materialized views
    // Assert <10ms response time
}
```

### Configuration Option (Future)

Consider adding configuration to disable materialized views:

```yaml
chucc:
  materialized-views:
    enabled: true  # Default: true, set false to use old behavior
```

Useful for:
- Debugging
- A/B testing
- Resource-constrained environments

**For this task:** Not needed, can add in Task 04 if desired.
