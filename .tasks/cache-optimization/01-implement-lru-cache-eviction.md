# Task: Implement LRU Cache Eviction for Dataset Graphs

**Status:** Not Started
**Priority:** High
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** Snapshot tasks (01, 02) - for rebuilding evicted graphs efficiently

---

## Context

Currently, `DatasetService` uses unbounded `ConcurrentHashMap` for caching materialized datasets:

```java
private final Map<String, Map<CommitId, DatasetGraphInMemory>> datasetCache =
    new ConcurrentHashMap<>();
```

**Problem:** Memory grows indefinitely. Each cached graph can be 1-50 MB.

**Example scenario:**
- 1000 commits queried over time
- Each graph = 5 MB
- Total cache size = 5 GB (!!)
- OutOfMemoryError

---

## Goals

1. Replace unbounded cache with **LRU (Least Recently Used) cache**
2. Keep only **latest commit per branch** + **N most recently used** older commits
3. Auto-evict least recently used entries when limit reached
4. Rebuild evicted graphs on-demand (using snapshots for speed)

---

## Design Decisions

### Cache Strategy

**Keep in cache:**
- ✅ Latest commit for each branch (never evict - hot data)
- ✅ N most recently used commits (configurable, default: 100)

**Evict from cache:**
- ❌ Older commits not accessed recently
- ❌ Commits for deleted branches

### Cache Size Limits

**Configuration:**
```yaml
vc:
  cache:
    max-size: 100                    # Max cached graphs (excluding latest)
    keep-latest-per-branch: true     # Always cache latest commits
    ttl-minutes: 60                  # Optional TTL (time-to-live)
```

**Example:**
- 5 branches (main, develop, feature-1, feature-2, feature-3)
- Latest commits: 5 graphs (never evicted)
- LRU cache: 100 additional graphs
- **Total max: 105 graphs**

---

## Implementation Plan

### Step 1: Add Caffeine Dependency

**File:** `pom.xml`

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
  <version>3.1.8</version>
</dependency>
```

**Why Caffeine?**
- High-performance caching library
- Built-in LRU, TTL, size-based eviction
- Thread-safe
- Excellent Spring Boot integration

---

### Step 2: Create Cache Configuration Properties

**File:** `src/main/java/org/chucc/vcserver/config/CacheProperties.java`

```java
package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for dataset graph caching.
 */
@Component
@ConfigurationProperties(prefix = "vc.cache")
public class CacheProperties {

  /**
   * Maximum number of cached dataset graphs (excluding latest per branch).
   */
  private int maxSize = 100;

  /**
   * Whether to always keep latest commit per branch in cache (never evict).
   */
  private boolean keepLatestPerBranch = true;

  /**
   * Time-to-live for cached graphs in minutes (0 = no TTL).
   */
  private int ttlMinutes = 0;

  // Getters and setters
  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  public boolean isKeepLatestPerBranch() {
    return keepLatestPerBranch;
  }

  public void setKeepLatestPerBranch(boolean keepLatestPerBranch) {
    this.keepLatestPerBranch = keepLatestPerBranch;
  }

  public int getTtlMinutes() {
    return ttlMinutes;
  }

  public void setTtlMinutes(int ttlMinutes) {
    this.ttlMinutes = ttlMinutes;
  }
}
```

**application.yml:**
```yaml
vc:
  cache:
    max-size: 100
    keep-latest-per-branch: true
    ttl-minutes: 0  # No TTL by default
```

---

### Step 3: Replace Cache with Caffeine

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import java.util.concurrent.TimeUnit;

@Service
public class DatasetService {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final SnapshotService snapshotService;
  private final CacheProperties cacheProperties;

  // Replace ConcurrentHashMap with Caffeine Cache
  private final Cache<CacheKey, DatasetGraphInMemory> datasetCache;

  // Track latest commits per branch (pinned in cache)
  private final Map<String, Map<String, CommitId>> latestCommits = new ConcurrentHashMap<>();

  /**
   * Cache key combining dataset and commit ID.
   */
  private record CacheKey(String dataset, CommitId commitId) {}

  public DatasetService(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      SnapshotService snapshotService,
      CacheProperties cacheProperties,
      MeterRegistry meterRegistry) {

    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.snapshotService = snapshotService;
    this.cacheProperties = cacheProperties;

    // Build Caffeine cache
    Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder()
        .maximumSize(cacheProperties.getMaxSize())
        .recordStats()  // Enable statistics
        .removalListener(new CacheRemovalListener());

    // Optional TTL
    if (cacheProperties.getTtlMinutes() > 0) {
      cacheBuilder.expireAfterWrite(cacheProperties.getTtlMinutes(), TimeUnit.MINUTES);
    }

    this.datasetCache = cacheBuilder.build();

    // Register metrics
    registerCacheMetrics(meterRegistry);
  }

  /**
   * Listener for cache evictions (for logging/debugging).
   */
  private class CacheRemovalListener
      implements RemovalListener<CacheKey, DatasetGraphInMemory> {

    @Override
    public void onRemoval(CacheKey key, DatasetGraphInMemory graph, RemovalCause cause) {
      if (cause.wasEvicted()) {
        logger.debug("Evicted dataset graph from cache: {} at commit {} (reason: {})",
            key.dataset(), key.commitId(), cause);
      }
    }
  }

  /**
   * Registers cache metrics with Micrometer.
   */
  private void registerCacheMetrics(MeterRegistry meterRegistry) {
    // Cache size
    Gauge.builder("dataset.cache.size", datasetCache, cache -> cache.estimatedSize())
        .description("Number of cached dataset graphs")
        .register(meterRegistry);

    // Hit rate
    Gauge.builder("dataset.cache.hit.rate", datasetCache,
        cache -> cache.stats().hitRate())
        .description("Cache hit rate (0-1)")
        .register(meterRegistry);

    // Eviction count
    Gauge.builder("dataset.cache.evictions", datasetCache,
        cache -> cache.stats().evictionCount())
        .description("Total number of cache evictions")
        .register(meterRegistry);
  }

  /**
   * Gets or creates a DatasetGraphInMemory for a specific commit.
   * Uses Caffeine cache with LRU eviction.
   */
  private DatasetGraphInMemory getOrCreateDatasetGraph(
      String datasetName, CommitId commitId) {

    CacheKey key = new CacheKey(datasetName, commitId);

    // Check if this is a latest commit (should be pinned)
    if (cacheProperties.isKeepLatestPerBranch() && isLatestCommit(datasetName, commitId)) {
      // For latest commits, use computeIfAbsent to ensure they stay in cache
      return datasetCache.get(key,
          k -> buildDatasetGraph(datasetName, commitId));
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

  /**
   * Checks if a commit is the latest for any branch.
   */
  private boolean isLatestCommit(String datasetName, CommitId commitId) {
    Map<String, CommitId> branches = latestCommits.get(datasetName);
    if (branches == null) {
      // Populate on first access
      branches = new ConcurrentHashMap<>();
      for (Branch branch : branchRepository.findAllByDataset(datasetName)) {
        branches.put(branch.getName(), branch.getCommitId());
      }
      latestCommits.put(datasetName, branches);
    }

    return branches.containsValue(commitId);
  }

  /**
   * Updates tracking of latest commits.
   * Called by ReadModelProjector when branches are updated.
   */
  public void updateLatestCommit(String dataset, String branchName, CommitId commitId) {
    latestCommits
        .computeIfAbsent(dataset, k -> new ConcurrentHashMap<>())
        .put(branchName, commitId);

    logger.debug("Updated latest commit tracking: {}/{} -> {}",
        dataset, branchName, commitId);
  }

  /**
   * Clears cache for a specific dataset.
   */
  public void clearCache(String datasetName) {
    // Remove all entries for this dataset
    datasetCache.asMap().keySet().removeIf(key -> key.dataset().equals(datasetName));

    // Clear latest commit tracking
    latestCommits.remove(datasetName);

    logger.info("Cleared cache for dataset: {}", datasetName);
  }

  /**
   * Clears all caches.
   */
  public void clearAllCaches() {
    datasetCache.invalidateAll();
    latestCommits.clear();
    logger.info("Cleared all caches");
  }
}
```

---

### Step 4: Update ReadModelProjector to Track Latest Commits

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
void handleCommitCreated(CommitCreatedEvent event) {
  // ... existing code to save commit

  // Update branch HEAD if branch is specified
  if (event.branch() != null) {
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.commitId())
    );

    // Notify DatasetService of latest commit update
    datasetService.updateLatestCommit(
        event.dataset(),
        event.branch(),
        CommitId.of(event.commitId())
    );
  }
}

void handleBranchReset(BranchResetEvent event) {
  // ... existing code

  // Notify DatasetService
  datasetService.updateLatestCommit(
      event.dataset(),
      event.branchName(),
      CommitId.of(event.toCommitId())
  );
}

// Similar updates for handleBranchRebased, handleCherryPicked, handleCommitsSquashed
```

---

### Step 5: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/service/DatasetServiceCacheTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "vc.cache.max-size=10",  // Small cache for testing
    "vc.cache.keep-latest-per-branch=true"
})
class DatasetServiceCacheTest {

  @Autowired
  private DatasetService datasetService;

  @Test
  void cache_exceedingMaxSize_shouldEvictLRU() {
    String dataset = "test";

    // Create 15 commits (cache max = 10)
    List<Commit> commits = createCommitChain(dataset, 15);

    // Access first 5 commits (should be cached)
    for (int i = 0; i < 5; i++) {
      datasetService.materializeCommit(dataset, commits.get(i).id());
    }

    // Access next 10 commits (total 15, but cache max = 10)
    for (int i = 5; i < 15; i++) {
      datasetService.materializeCommit(dataset, commits.get(i).id());
    }

    // Verify cache size <= 10
    assertThat(getCacheSize()).isLessThanOrEqualTo(10);

    // Access first commit again (should be evicted, needs rebuild)
    datasetService.materializeCommit(dataset, commits.get(0).id());

    // Verify it was a cache miss (check metrics)
    assertThat(getCacheMissCount()).isGreaterThan(0);
  }

  @Test
  void latestCommit_shouldNeverBeEvicted() {
    String dataset = "test";
    String branch = "main";

    // Create branch and set latest commit
    Commit latest = Commit.create(List.of(), "author", "latest");
    commitRepository.save(dataset, latest, RDFPatchOps.emptyPatch());
    branchRepository.save(dataset, new Branch(branch, latest.id()));

    // Update tracking
    datasetService.updateLatestCommit(dataset, branch, latest.id());

    // Materialize latest commit
    datasetService.materializeCommit(dataset, latest.id());

    // Fill cache with other commits
    for (int i = 0; i < 100; i++) {
      Commit c = Commit.create(List.of(), "author", "commit " + i);
      commitRepository.save(dataset, c, RDFPatchOps.emptyPatch());
      datasetService.materializeCommit(dataset, c.id());
    }

    // Verify latest commit still in cache (no rebuild needed)
    datasetService.materializeCommit(dataset, latest.id());

    // Should be a cache hit (not rebuilt)
    // Verify via metrics or logs
  }
}
```

---

## Configuration Examples

**Development (generous cache):**
```yaml
vc:
  cache:
    max-size: 500
    keep-latest-per-branch: true
    ttl-minutes: 0  # No TTL
```

**Production (memory-constrained):**
```yaml
vc:
  cache:
    max-size: 100
    keep-latest-per-branch: true
    ttl-minutes: 30  # Evict after 30 min inactivity
```

**High-traffic (aggressive eviction):**
```yaml
vc:
  cache:
    max-size: 50
    keep-latest-per-branch: true
    ttl-minutes: 10
```

---

## Monitoring

**Metrics to track:**

```bash
# Cache size
curl http://localhost:8080/actuator/metrics/dataset.cache.size

# Hit rate (should be > 0.8 for good performance)
curl http://localhost:8080/actuator/metrics/dataset.cache.hit.rate

# Eviction count
curl http://localhost:8080/actuator/metrics/dataset.cache.evictions
```

**Alerting:**
- Alert if hit rate < 0.5 (too much eviction, increase cache size)
- Alert if eviction count growing rapidly (memory pressure)

---

## Success Criteria

- [ ] Caffeine cache integrated
- [ ] LRU eviction working (oldest entries evicted first)
- [ ] Latest commits never evicted (when `keep-latest-per-branch: true`)
- [ ] Cache metrics available via Actuator
- [ ] Performance maintained (no slower queries)
- [ ] Memory usage bounded (no OutOfMemoryError)
- [ ] All tests pass

---

## Performance Impact

**Before (unbounded cache):**
- Memory: Grows indefinitely (OutOfMemoryError risk)
- Hit rate: 100% (everything cached)

**After (LRU cache, max-size=100):**
- Memory: Bounded (max 100 graphs ~= 5 GB)
- Hit rate: 80-95% (depends on access patterns)
- Rebuild time for evicted graphs: ~50ms (with snapshots)

**Trade-off:** Small performance cost (rebuilding evicted graphs) for bounded memory.

---

## Rollback Plan

If issues arise:
1. Set `vc.cache.max-size: 10000` (effectively unbounded for most workloads)
2. Caffeine will still work, just won't evict
3. Monitor memory and adjust

---

## Future Enhancements

- **Two-level cache:** Local (Caffeine) + distributed (Redis)
- **Smart eviction:** Evict by size (large graphs first) not just LRU
- **Pre-warming:** Pre-cache frequently accessed commits
- **Dynamic sizing:** Adjust cache size based on available heap
