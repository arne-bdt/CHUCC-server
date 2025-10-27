# Task 05: Add LRU Eviction for Memory Management

**Status:** Not Started
**Priority:** High (Scalability & Production Readiness)
**Category:** Performance & Resource Management
**Estimated Time:** 4-5 hours
**Complexity:** Medium
**Prerequisites:** Task 04 completed (Monitoring & Recovery in place)

---

## Overview

Implement LRU (Least Recently Used) eviction for materialized branch graphs to prevent OutOfMemoryError in deployments with many branches. Currently, all materialized graphs are kept in memory indefinitely, which doesn't scale for large deployments.

**Problem:**
- Current implementation: Unbounded ConcurrentHashMap stores ALL materialized graphs
- Example risk: 1,000 branches × 100,000 triples × 200 bytes/triple = **20GB memory**
- No eviction policy → OutOfMemoryError inevitable at scale

**Solution:**
- Replace ConcurrentHashMap with Caffeine LRU cache
- Configure maximum number of branches (default: 25)
- Rebuild evicted graphs on-demand when accessed
- Add metrics for cache hits/misses/evictions

**Benefits:**
- ✅ Bounded memory usage (predictable resource consumption)
- ✅ Automatic eviction of least-used branches
- ✅ On-demand rebuild for evicted branches (~1 second typical)
- ✅ Configurable limit based on deployment size
- ✅ Production-ready for large-scale deployments

---

## Current State

### Existing Implementation

**File:** [InMemoryMaterializedBranchRepository.java](../../src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java)

```java
private final ConcurrentHashMap<String, DatasetGraph> branchGraphs
    = new ConcurrentHashMap<>();  // ❌ Unbounded - keeps ALL graphs forever
```

**Problem:** No eviction mechanism. Memory grows indefinitely as branches are created.

### Existing Infrastructure

**Caffeine Cache:** Already used in DatasetService for historical graph caching.

**File:** [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java)

```java
this.cache = Caffeine.newBuilder()
    .maximumSize(100)
    .expireAfterAccess(Duration.ofMinutes(30))
    .evictionListener((key, value, cause) -> {
        logger.debug("Evicted dataset graph: {}", key);
    })
    .build();
```

**Pattern to follow:** Use Caffeine with maximumSize, evictionListener, and stats recording.

### Existing Rebuild Infrastructure

**File:** [MaterializedViewRebuildService.java](../../src/main/java/org/chucc/vcserver/service/MaterializedViewRebuildService.java)

We have `rebuildBranch()` method that can reconstruct graphs from commit history. We'll reuse this for on-demand rebuild.

---

## Requirements

### Functional Requirements

1. **LRU Cache Implementation**
   - Replace `ConcurrentHashMap<String, DatasetGraph>` with `Cache<String, DatasetGraph>`
   - Maximum size: Configurable (default 25 branches)
   - Eviction policy: LRU (Least Recently Used)
   - On cache miss: Rebuild graph from commit history automatically

2. **Configuration**
   - Property: `chucc.materialized-views.max-branches` (default: 25)
   - Property: `chucc.materialized-views.cache-stats-enabled` (default: true)
   - Validation: max-branches must be >= 1

3. **On-Demand Rebuild**
   - When `getBranchGraph()` called for evicted branch:
     - Check cache first (O(1) lookup)
     - If miss: Rebuild from CommitRepository automatically
     - Cache rebuilt graph for future queries
   - Rebuild should be transparent to callers

4. **Metrics**
   - `chucc.materialized_views.cache.hits` - Counter: Cache hit count
   - `chucc.materialized_views.cache.misses` - Counter: Cache miss count
   - `chucc.materialized_views.cache.evictions` - Counter: Eviction count
   - `chucc.materialized_views.cache.load_success` - Counter: Successful rebuilds
   - `chucc.materialized_views.cache.load_failure` - Counter: Failed rebuilds
   - `chucc.materialized_views.cache.load_time` - Timer: Rebuild duration

5. **Logging**
   - INFO: Log evictions (dataset, branch, reason)
   - DEBUG: Log cache hits/misses
   - WARN: Log rebuild failures

### Non-Functional Requirements

1. **Performance**
   - Cache hit: <5ms (same as current)
   - Cache miss (rebuild): <1 second for typical branch (100 commits, 1000 triples)
   - Eviction: <10ms (non-blocking)

2. **Concurrency**
   - Thread-safe cache access
   - Prevent duplicate rebuilds (cache loading should be synchronized per key)
   - Use Caffeine's built-in loading cache pattern

3. **Memory**
   - Memory usage bounded: `max-branches * avg-triples-per-branch * 200 bytes`
   - Example: 25 branches × 10,000 triples × 200 bytes = **50MB** (reasonable!)

---

## Implementation Steps

### Step 1: Add Caffeine Dependency

**File:** `pom.xml`

**Check if already exists:**
```bash
grep -i "caffeine" pom.xml
```

If not present, add:
```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.1.8</version>
</dependency>
```

**Note:** Caffeine is likely already present (used in DatasetService).

### Step 2: Update Configuration Properties

**File:** `src/main/java/org/chucc/vcserver/config/MaterializedViewsProperties.java`

Add new properties:
```java
@Configuration
@ConfigurationProperties(prefix = "chucc.materialized-views")
public class MaterializedViewsProperties {

  private boolean enabled = true;
  private long memoryWarningThresholdMb = 1000;
  private boolean periodicLoggingEnabled = true;

  // ✅ NEW: LRU cache properties
  private int maxBranches = 25;  // Default: 25 branches
  private boolean cacheStatsEnabled = true;

  // Getters and setters

  public int getMaxBranches() {
    return maxBranches;
  }

  public void setMaxBranches(int maxBranches) {
    if (maxBranches < 1) {
      throw new IllegalArgumentException("maxBranches must be >= 1");
    }
    this.maxBranches = maxBranches;
  }

  public boolean isCacheStatsEnabled() {
    return cacheStatsEnabled;
  }

  public void setCacheStatsEnabled(boolean cacheStatsEnabled) {
    this.cacheStatsEnabled = cacheStatsEnabled;
  }
}
```

**File:** `src/main/resources/application.yml`

Add configuration:
```yaml
chucc:
  materialized-views:
    enabled: true
    max-branches: 25  # ← NEW: Maximum branches to keep in memory
    cache-stats-enabled: true  # ← NEW: Enable Caffeine cache stats
    memory-warning-threshold-mb: 1000
    periodic-logging-enabled: true
```

### Step 3: Replace ConcurrentHashMap with Caffeine Cache

**File:** `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java`

**Import Caffeine:**
```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
```

**Replace HashMap with Cache:**
```java
// OLD:
// private final ConcurrentHashMap<String, DatasetGraph> branchGraphs
//     = new ConcurrentHashMap<>();

// NEW:
private final Cache<String, DatasetGraph> branchGraphsCache;
private final MaterializedViewsProperties properties;
private final CommitRepository commitRepository;
private final BranchRepository branchRepository;

/**
 * Constructs the repository with LRU eviction.
 *
 * @param meterRegistry metrics registry
 * @param properties configuration properties
 * @param commitRepository for rebuilding evicted graphs
 * @param branchRepository for branch lookups
 */
public InMemoryMaterializedBranchRepository(
    MeterRegistry meterRegistry,
    MaterializedViewsProperties properties,
    CommitRepository commitRepository,
    BranchRepository branchRepository) {

  this.meterRegistry = meterRegistry;
  this.properties = properties;
  this.commitRepository = commitRepository;
  this.branchRepository = branchRepository;

  // Build LRU cache with eviction listener
  Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder()
      .maximumSize(properties.getMaxBranches())
      .evictionListener(this::onEviction);

  if (properties.isCacheStatsEnabled()) {
    cacheBuilder.recordStats();
  }

  this.branchGraphsCache = cacheBuilder.build();

  // Register gauges
  Gauge.builder("chucc.materialized_views.count",
      branchGraphsCache, cache -> cache.estimatedSize())
      .description("Number of materialized branch graphs in cache")
      .register(meterRegistry);

  Gauge.builder("chucc.materialized_views.memory_bytes",
      this, this::estimateMemoryUsage)
      .description("Estimated memory usage of materialized graphs in bytes")
      .register(meterRegistry);

  // Register cache metrics
  if (properties.isCacheStatsEnabled()) {
    Gauge.builder("chucc.materialized_views.cache.hit_rate",
        branchGraphsCache, cache -> cache.stats().hitRate())
        .description("Cache hit rate")
        .register(meterRegistry);
  }
}

/**
 * Eviction listener for logging and metrics.
 *
 * @param key cache key (dataset:branch)
 * @param graph the evicted graph
 * @param cause eviction reason
 */
private void onEviction(String key, DatasetGraph graph, RemovalCause cause) {
  logger.info("Evicted materialized graph: {} (reason: {})", key, cause);

  meterRegistry.counter("chucc.materialized_views.cache.evictions",
      "reason", cause.name()
  ).increment();

  // Close the graph to release resources
  if (graph != null) {
    try {
      graph.close();
    } catch (Exception e) {
      logger.warn("Error closing evicted graph {}", key, e);
    }
  }
}
```

### Step 4: Implement On-Demand Rebuild in getBranchGraph()

**Replace synchronous get with loading pattern:**

```java
@Override
public DatasetGraph getBranchGraph(String dataset, String branch) {
  String key = buildKey(dataset, branch);

  // Try to get from cache first
  DatasetGraph graph = branchGraphsCache.getIfPresent(key);

  if (graph != null) {
    // ✅ Cache hit
    logger.debug("Cache hit for materialized graph: {}/{}", dataset, branch);
    meterRegistry.counter("chucc.materialized_views.cache.hits",
        "dataset", dataset,
        "branch", branch
    ).increment();
    return graph;
  }

  // ❌ Cache miss - rebuild from commit history
  logger.info("Cache miss for materialized graph: {}/{}, rebuilding...", dataset, branch);
  meterRegistry.counter("chucc.materialized_views.cache.misses",
      "dataset", dataset,
      "branch", branch
  ).increment();

  Timer.Sample sample = Timer.start(meterRegistry);

  try {
    // Rebuild graph from commit history
    DatasetGraph rebuiltGraph = rebuildGraphFromCommitHistory(dataset, branch);

    // Cache for future access
    branchGraphsCache.put(key, rebuiltGraph);

    sample.stop(meterRegistry.timer("chucc.materialized_views.cache.load_time",
        "dataset", dataset,
        "branch", branch,
        "status", "success"
    ));

    meterRegistry.counter("chucc.materialized_views.cache.load_success",
        "dataset", dataset,
        "branch", branch
    ).increment();

    logger.info("Successfully rebuilt materialized graph: {}/{}", dataset, branch);
    return rebuiltGraph;

  } catch (Exception e) {
    sample.stop(meterRegistry.timer("chucc.materialized_views.cache.load_time",
        "dataset", dataset,
        "branch", branch,
        "status", "error"
    ));

    meterRegistry.counter("chucc.materialized_views.cache.load_failure",
        "dataset", dataset,
        "branch", branch
    ).increment();

    String errorMsg = String.format(
        "Failed to rebuild materialized graph for %s/%s", dataset, branch);
    logger.error(errorMsg, e);
    throw new MaterializedViewLoadException(errorMsg, e);
  }
}

/**
 * Rebuild graph from commit history.
 *
 * <p>This is called when a graph is evicted from cache and needs to be
 * reconstructed on-demand.
 *
 * @param dataset the dataset name
 * @param branch the branch name
 * @return rebuilt graph with all commits applied
 */
private DatasetGraph rebuildGraphFromCommitHistory(String dataset, String branch) {
  // 1. Get branch and HEAD commit
  Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
      .orElseThrow(() -> new BranchNotFoundException(dataset, branch));

  CommitId headCommitId = branchObj.getCommitId();

  // 2. Build commit chain from root to HEAD
  List<Commit> commitChain = buildCommitChain(dataset, headCommitId);

  logger.debug("Rebuilding {} commits for {}/{}", commitChain.size(), dataset, branch);

  // 3. Create new empty graph
  DatasetGraph graph = DatasetGraphFactory.createTxnMem();

  // 4. Apply all patches in order
  for (Commit commit : commitChain) {
    Optional<RDFPatch> patchOpt = commitRepository.findPatchByDatasetAndId(
        dataset, commit.id());

    if (patchOpt.isEmpty()) {
      String errorMsg = String.format(
          "Missing patch for commit %s in dataset %s", commit.id(), dataset);
      logger.error(errorMsg);
      throw new IllegalStateException(errorMsg);
    }

    RDFPatch patch = patchOpt.get();

    graph.begin(ReadWrite.WRITE);
    try {
      RDFPatchOps.applyChange(graph, patch);
      graph.commit();
    } catch (Exception e) {
      graph.abort();
      throw new RuntimeException("Failed to apply patch for commit " + commit.id(), e);
    } finally {
      graph.end();
    }
  }

  return graph;
}

/**
 * Build commit chain from root to target commit.
 *
 * @param dataset the dataset name
 * @param targetCommitId the target commit ID
 * @return list of commits from root to target (in order)
 */
private List<Commit> buildCommitChain(String dataset, CommitId targetCommitId) {
  List<Commit> chain = new ArrayList<>();
  CommitId currentId = targetCommitId;

  // Walk backward from HEAD to root
  while (currentId != null) {
    Commit commit = commitRepository.findByDatasetAndId(dataset, currentId)
        .orElseThrow(() -> new IllegalStateException("Missing commit: " + currentId));

    chain.add(0, commit);  // Add at beginning (reverse order)

    // Move to parent
    currentId = commit.parents().isEmpty() ? null : commit.parents().get(0);
  }

  return chain;
}
```

### Step 5: Update All Methods to Use Cache

**Update `createBranch()` method:**
```java
@Override
public void createBranch(String dataset, String branch, Optional<String> sourceDataset) {
  String key = buildKey(dataset, branch);

  // Create new empty graph or clone from source
  DatasetGraph graph;
  if (sourceDataset.isPresent()) {
    DatasetGraph sourceGraph = getBranchGraph(dataset, sourceDataset.get());
    graph = cloneGraph(sourceGraph);
  } else {
    graph = DatasetGraphFactory.createTxnMem();
  }

  // Put in cache
  branchGraphsCache.put(key, graph);

  logger.debug("Created materialized graph for {}/{}", dataset, branch);
}
```

**Update `deleteBranch()` method:**
```java
@Override
public void deleteBranch(String dataset, String branch) {
  String key = buildKey(dataset, branch);

  // Remove from cache and close
  DatasetGraph graph = branchGraphsCache.getIfPresent(key);
  if (graph != null) {
    branchGraphsCache.invalidate(key);
    graph.close();
  }

  logger.debug("Deleted materialized graph for {}/{}", dataset, branch);
}
```

**Update `getGraphCount()` method:**
```java
@Override
public int getGraphCount() {
  return (int) branchGraphsCache.estimatedSize();
}
```

**Update `estimateMemoryUsage()` method:**
```java
private long estimateMemoryUsage() {
  long totalTriples = 0;

  // Iterate only over cached graphs
  for (DatasetGraph graph : branchGraphsCache.asMap().values()) {
    graph.begin(ReadWrite.READ);
    try {
      // Count triples in all named graphs
      graph.listGraphNodes().forEachRemaining(graphName -> {
        Graph g = graph.getGraph(graphName);
        totalTriples += g.size();
      });

      // Count default graph triples
      totalTriples += graph.getDefaultGraph().size();
    } finally {
      graph.end();
    }
  }

  // Estimate: ~200 bytes per triple
  return totalTriples * 200;
}
```

### Step 6: Create Custom Exception

**File:** `src/main/java/org/chucc/vcserver/exception/MaterializedViewLoadException.java` (NEW)

```java
package org.chucc.vcserver.exception;

/**
 * Exception thrown when materialized view cannot be loaded from cache.
 *
 * <p>This typically occurs when rebuilding a graph from commit history fails.
 */
public class MaterializedViewLoadException extends RuntimeException {

  /**
   * Constructs exception with message and cause.
   *
   * @param message error message
   * @param cause underlying cause
   */
  public MaterializedViewLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

### Step 7: Update Monitor to Report Cache Stats

**File:** `src/main/java/org/chucc/vcserver/monitoring/MaterializedViewsMonitor.java`

```java
@Scheduled(fixedRate = 300000)  // 5 minutes
public void logStatistics() {
  int graphCount = materializedBranchRepo.getGraphCount();

  logger.info("Materialized Views Stats: {} graphs in cache (max: {})",
      graphCount, properties.getMaxBranches());

  // Log cache stats if enabled
  if (properties.isCacheStatsEnabled()) {
    CacheStats stats = ((InMemoryMaterializedBranchRepository) materializedBranchRepo)
        .getCacheStats();

    logger.info("Cache Stats: hits={}, misses={}, evictions={}, hit_rate={:.2f}%",
        stats.hitCount(), stats.missCount(), stats.evictionCount(),
        stats.hitRate() * 100);
  }
}
```

**Add method to InMemoryMaterializedBranchRepository:**
```java
/**
 * Get cache statistics.
 *
 * @return cache stats
 */
public CacheStats getCacheStats() {
  return branchGraphsCache.stats();
}
```

### Step 8: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MaterializedViewEvictionIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for LRU eviction of materialized views.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = {
    "projector.kafka-listener.enabled=true",
    "chucc.materialized-views.max-branches=3"  // Low limit for testing
})
class MaterializedViewEvictionIT extends IntegrationTestFixture {

  @Autowired
  private MaterializedBranchRepository materializedBranchRepo;

  @Test
  void lruEviction_shouldEvictLeastRecentlyUsedBranch() throws Exception {
    String dataset = "test-dataset";

    // Create 4 branches (exceeds max of 3)
    createBranch(dataset, "branch1");
    createBranch(dataset, "branch2");
    createBranch(dataset, "branch3");
    createBranch(dataset, "branch4");

    // Wait for projection
    await().atMost(Duration.ofSeconds(10)).until(() ->
        materializedBranchRepo.branchExists(dataset, "branch4"));

    // Cache should contain at most 3 branches
    assertThat(materializedBranchRepo.getGraphCount()).isLessThanOrEqualTo(3);

    // Access branch1 (should trigger rebuild if evicted)
    DatasetGraph graph1 = materializedBranchRepo.getBranchGraph(dataset, "branch1");
    assertThat(graph1).isNotNull();
  }

  @Test
  void cacheHit_shouldReturnExistingGraph() {
    String dataset = "test-dataset";
    String branch = "main";

    createBranch(dataset, branch);
    putGraph(dataset, "http://example.org/graph", branch,
        "@prefix ex: <http://example.org/> . ex:s ex:p \"value\" .");

    // First access
    DatasetGraph graph1 = materializedBranchRepo.getBranchGraph(dataset, branch);

    // Second access (should be cache hit)
    DatasetGraph graph2 = materializedBranchRepo.getBranchGraph(dataset, branch);

    // Should return same instance
    assertThat(graph1).isSameAs(graph2);
  }

  @Test
  void cacheMiss_shouldRebuildFromCommitHistory() throws Exception {
    String dataset = "test-dataset";
    String branch = "main";

    // Create branch with data
    createBranch(dataset, branch);
    putGraph(dataset, "http://example.org/graph", branch,
        "@prefix ex: <http://example.org/> . ex:s ex:p \"value\" .");

    // Wait for projection
    await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(500)).until(() -> true);

    // Manually evict from cache
    materializedBranchRepo.deleteBranch(dataset, branch);

    // Re-create branch (simulates eviction)
    createBranch(dataset, branch);

    // Access should rebuild from commit history
    DatasetGraph graph = materializedBranchRepo.getBranchGraph(dataset, branch);
    assertThat(graph).isNotNull();

    // Verify data was rebuilt correctly
    graph.begin(ReadWrite.READ);
    try {
      Graph g = graph.getGraph(NodeFactory.createURI("http://example.org/graph"));
      assertThat(g.size()).isGreaterThan(0);
    } finally {
      graph.end();
    }
  }
}
```

### Step 9: Add Unit Tests

**File:** `src/test/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepositoryTest.java`

Add new tests:
```java
@Test
void cacheEviction_shouldRespectMaxBranches() {
  // Create max+1 branches
  for (int i = 0; i < properties.getMaxBranches() + 1; i++) {
    repository.createBranch("dataset", "branch" + i, Optional.empty());
  }

  // Cache size should not exceed max
  assertThat(repository.getGraphCount()).isLessThanOrEqualTo(properties.getMaxBranches());
}

@Test
void onDemandRebuild_shouldRestoreEvictedGraph() {
  // Setup: Create branch and populate with data
  repository.createBranch("dataset", "branch", Optional.empty());

  // Simulate commit history (mock commitRepository)
  when(branchRepository.findByDatasetAndName("dataset", "branch"))
      .thenReturn(Optional.of(new Branch("branch", commitId, ...)));
  when(commitRepository.findByDatasetAndId("dataset", commitId))
      .thenReturn(Optional.of(commit));
  when(commitRepository.findPatchByDatasetAndId("dataset", commitId))
      .thenReturn(Optional.of(patch));

  // Force eviction
  repository.deleteBranch("dataset", "branch");

  // Access should trigger rebuild
  DatasetGraph graph = repository.getBranchGraph("dataset", "branch");

  assertThat(graph).isNotNull();
  verify(commitRepository).findPatchByDatasetAndId("dataset", commitId);
}
```

### Step 10: Update Documentation

**File:** `.claude/CLAUDE.md`

Add note about memory management:
```markdown
### Materialized View Memory Management

- **LRU Eviction:** Materialized graphs automatically evicted when max-branches reached
- **Default Limit:** 25 branches in cache
- **On-Demand Rebuild:** Evicted graphs rebuilt from commit history on first access
- **Configuration:** `chucc.materialized-views.max-branches` in application.yml
- **Typical Rebuild Time:** <1 second for branches with <100 commits
```

### Step 11: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=MaterializedViewEvictionIT 2>&1 | tail -20
mvn -q test -Dtest=InMemoryMaterializedBranchRepositoryTest 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Caffeine LRU cache replaces ConcurrentHashMap
- ✅ Maximum branches configurable (default: 25)
- ✅ On-demand rebuild for evicted graphs
- ✅ Cache metrics (hits, misses, evictions, load time)
- ✅ Eviction logging with reason
- ✅ Integration tests verify eviction behavior
- ✅ Unit tests verify cache limits
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`
- ✅ Documentation updated

---

## Testing Strategy

### Unit Tests

**InMemoryMaterializedBranchRepositoryTest:**
- Verify cache respects max-branches limit
- Verify eviction triggers when limit exceeded
- Verify rebuild logic reconstructs correct graph
- Verify metrics recorded correctly

### Integration Tests (Projector Enabled)

**MaterializedViewEvictionIT:**
- Create > max-branches branches, verify eviction
- Verify cache hit returns same instance
- Verify cache miss rebuilds from commit history
- Verify rebuilt graph has correct data

---

## Files to Create

### Production Code
- `src/main/java/org/chucc/vcserver/exception/MaterializedViewLoadException.java` (NEW)

### Files to Modify
- `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java` (major refactoring)
- `src/main/java/org/chucc/vcserver/config/MaterializedViewsProperties.java` (add max-branches config)
- `src/main/java/org/chucc/vcserver/monitoring/MaterializedViewsMonitor.java` (add cache stats logging)
- `src/main/resources/application.yml` (add max-branches config)
- `.claude/CLAUDE.md` (add memory management note)

### Test Code
- `src/test/java/org/chucc/vcserver/integration/MaterializedViewEvictionIT.java` (NEW)
- `src/test/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepositoryTest.java` (add eviction tests)

**Total:** 1 new production file, 1 new exception, 5 modified files, 1 new integration test, 1 modified unit test

---

## Performance Characteristics

### Memory Usage (Before vs After)

**Before (Unbounded):**
- 1,000 branches × 10,000 triples × 200 bytes = **2GB** (all in memory forever)

**After (LRU with max=25):**
- 25 branches × 10,000 triples × 200 bytes = **50MB** (bounded!)

### Query Performance

**Cache Hit (95% of queries in production):**
- Latency: <5ms (same as before)
- No rebuild overhead

**Cache Miss (5% of queries):**
- Latency: <1 second (typical branch with 100 commits)
- Acceptable for infrequently accessed branches

### Eviction Overhead

- Eviction operation: <10ms (non-blocking)
- Graph close: <5ms
- Total impact: Negligible

---

## Configuration Examples

### Small Deployment (10 branches)
```yaml
chucc:
  materialized-views:
    max-branches: 10  # Keep all in memory
```

### Medium Deployment (50 branches)
```yaml
chucc:
  materialized-views:
    max-branches: 25  # Keep 25 most active
```

### Large Deployment (1000+ branches)
```yaml
chucc:
  materialized-views:
    max-branches: 100  # Keep 100 most active (200MB typical)
```

---

## Metrics Dashboard Example

Using Prometheus + Grafana:

```promql
# Cache hit rate (should be >90%)
chucc_materialized_views_cache_hit_rate

# Eviction rate (per minute)
rate(chucc_materialized_views_cache_evictions[5m])

# Average rebuild time (should be <1s)
histogram_quantile(0.95, chucc_materialized_views_cache_load_time)

# Cache misses (should be low)
rate(chucc_materialized_views_cache_misses[5m])
```

---

## Operational Considerations

### When to Increase max-branches

Increase if:
- Cache hit rate drops below 90%
- Frequent rebuild messages in logs
- Many active branches accessed regularly

### When to Decrease max-branches

Decrease if:
- Memory pressure (approaching JVM max heap)
- Most branches rarely accessed
- Cost of rebuild acceptable (<1s)

### Monitoring

Watch these metrics:
- `chucc_materialized_views_cache_hit_rate` - Target: >90%
- `chucc_materialized_views_cache_evictions` - Trend: Should be stable
- `chucc_materialized_views_cache_load_time` - Target: <1s p95

---

## References

- [Caffeine Cache](https://github.com/ben-manes/caffeine) - High-performance Java caching library
- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Existing Caffeine usage pattern
- [MaterializedViewRebuildService.java](../../src/main/java/org/chucc/vcserver/service/MaterializedViewRebuildService.java) - Rebuild logic to reuse

---

## Next Steps

After completing this task:
1. ✅ Delete this task file
2. ✅ Update `.tasks/README.md`
3. 🚀 System ready for large-scale production deployments!

---

**Estimated Completion Time:** 4-5 hours
**Complexity:** Medium (refactoring existing code with well-established pattern)
**Impact:** High (enables production scalability)
