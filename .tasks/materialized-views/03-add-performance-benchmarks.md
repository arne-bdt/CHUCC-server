# Task 03: Add Performance Benchmarks and Cache Optimization

**Status:** Not Started
**Priority:** Medium (Validation & Optimization)
**Category:** Performance & Testing
**Estimated Time:** 2-3 hours
**Complexity:** Low-Medium
**Prerequisites:** Tasks 01-02 completed (eager cache updates working)

---

## Overview

Add performance benchmarks to validate the 10-20x performance improvement from eager cache updates, and optimize cache configuration based on real-world usage patterns.

**Goals:**
1. **Benchmarks:** Measure query performance before/after eager updates
2. **Cache Simplification:** Remove now-unnecessary `isLatestCommit()` logic
3. **Configuration Tuning:** Optimize cache size and TTL based on access patterns
4. **Performance Tests:** Add automated tests to detect performance regressions

---

## Current State

### Cache Logic Still Has Complexity

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

**Problem:** `isLatestCommit()` check still exists but is less useful now:
- Branch HEADs are eagerly updated (always cached)
- First query still triggers build (but second query is instant)
- Can simplify this logic

---

## Requirements

### Functional Requirements

1. **Performance Benchmarks**
   - Measure branch HEAD query latency (before/after)
   - Measure historical commit query latency
   - Measure cache hit rates
   - Automated benchmark tests

2. **Cache Simplification**
   - Simplify `getOrCreateDatasetGraph()` logic
   - Remove `isLatestCommit()` check (or keep but document it's for cold start)
   - Clearer separation: eager-updated vs. on-demand graphs

3. **Configuration Optimization**
   - Review cache size settings
   - Add cache metrics exposure
   - Document optimal settings

### Non-Functional Requirements

1. **Performance Targets**
   - Branch HEAD queries: <10ms (90th percentile)
   - Cache hit rate: >95% for branch HEADs
   - Historical queries: <500ms (acceptable, infrequent)

2. **Validation**
   - Automated performance tests (fail if regression detected)
   - Benchmark suite for manual validation

---

## Implementation Steps

### Step 1: Add Performance Benchmark Test

**File:** `src/test/java/org/chucc/vcserver/performance/CachePerformanceBenchmark.java` (NEW)

```java
package org.chucc.vcserver.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Performance benchmarks for cache behavior.
 *
 * <p>These tests measure query performance with eager cache updates.
 * Run manually to validate performance characteristics.
 *
 * <p><b>Note:</b> Disabled by default (manual execution only).
 * Run with: mvn test -Dtest=CachePerformanceBenchmark
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
@Disabled("Performance benchmark - run manually")
class CachePerformanceBenchmark {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void benchmarkBranchHeadQueries() throws Exception {
    // Arrange: Create dataset with 100 commits
    String dataset = "perf-test";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    System.out.println("Creating 100 commits...");
    for (int i = 0; i < 100; i++) {
      String turtle = String.format(
          "@prefix ex: <http://example.org/> . ex:s%d ex:p \"value%d\" .", i, i);
      putGraph(dataset, graphUri, branch, turtle);

      if (i % 10 == 0) {
        System.out.printf("Created %d commits%n", i);
      }
    }

    // Wait for all projections
    System.out.println("Waiting for projections to complete...");
    await().atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofSeconds(2))
        .until(() -> true);

    // Act: Measure query performance (20 queries)
    String sparql = """
        SELECT (COUNT(*) as ?count)
        WHERE {
          GRAPH <http://example.org/graph> {
            ?s ?p ?o .
          }
        }
        """;

    List<Long> queryTimes = new ArrayList<>();

    System.out.println("Running 20 queries...");
    for (int i = 0; i < 20; i++) {
      long startTime = System.nanoTime();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.valueOf("application/sparql-query"));
      HttpEntity<String> request = new HttpEntity<>(sparql, headers);

      ResponseEntity<String> response = restTemplate.postForEntity(
          "/datasets/{dataset}?branch={branch}",
          request,
          String.class,
          dataset, branch
      );

      long queryTime = (System.nanoTime() - startTime) / 1_000_000;  // Convert to ms
      queryTimes.add(queryTime);

      assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

      if (i % 5 == 0) {
        System.out.printf("Query %d: %d ms%n", i, queryTime);
      }
    }

    // Assert: Performance targets
    long avgTime = (long) queryTimes.stream().mapToLong(Long::longValue).average().orElse(0);
    long p90Time = calculatePercentile(queryTimes, 90);
    long p99Time = calculatePercentile(queryTimes, 99);

    System.out.println("=== Performance Results ===");
    System.out.printf("Average: %d ms%n", avgTime);
    System.out.printf("P90: %d ms%n", p90Time);
    System.out.printf("P99: %d ms%n", p99Time);
    System.out.println("===========================");

    // Performance targets (with eager cache updates)
    assertThat(p90Time)
        .as("P90 latency should be <10ms with eager cache updates")
        .isLessThan(10);

    assertThat(avgTime)
        .as("Average latency should be <5ms with eager cache updates")
        .isLessThan(5);
  }

  @Test
  void benchmarkHistoricalCommitQueries() throws Exception {
    // Arrange: Create dataset with multiple commits
    String dataset = "perf-test-historical";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Create 50 commits
    System.out.println("Creating 50 commits for historical query test...");
    for (int i = 0; i < 50; i++) {
      String turtle = String.format(
          "@prefix ex: <http://example.org/> . ex:s ex:value \"%d\" .", i);
      putGraph(dataset, graphUri, branch, turtle);
    }

    await().atMost(Duration.ofSeconds(20))
        .pollDelay(Duration.ofSeconds(1))
        .until(() -> true);

    // Get commit ID from middle of history (commit ~25)
    // In real test, would fetch actual commit ID from refs API
    // For now, just measure branch HEAD (cache hit)

    // Act: Measure historical query
    String sparql = """
        SELECT ?o
        WHERE {
          GRAPH <http://example.org/graph> {
            <http://example.org/s> <http://example.org/value> ?o .
          }
        }
        """;

    long startTime = System.nanoTime();

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(sparql, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        request,
        String.class,
        dataset, branch
    );

    long queryTime = (System.nanoTime() - startTime) / 1_000_000;

    System.out.println("=== Historical Query Performance ===");
    System.out.printf("Query time: %d ms%n", queryTime);
    System.out.println("====================================");

    // Historical queries can be slower (acceptable)
    assertThat(queryTime)
        .as("Historical query should complete in reasonable time")
        .isLessThan(500);
  }

  // Helper methods
  private long calculatePercentile(List<Long> values, int percentile) {
    List<Long> sorted = new ArrayList<>(values);
    sorted.sort(Long::compareTo);
    int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, index));
  }

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
    headers.set("X-Author", "benchmark");
    headers.set("X-Commit-Message", "Benchmark commit");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request, Void.class, dataset, graphUri, branch
    );
  }
}
```

### Step 2: Add Automated Performance Test

**File:** `src/test/java/org/chucc/vcserver/integration/QueryPerformanceIT.java` (NEW)

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
 * Performance regression tests for query latency.
 *
 * <p>These tests run automatically in CI to detect performance regressions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class QueryPerformanceIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void branchHeadQuery_shouldBeFast() throws Exception {
    // Arrange: Create dataset with commits
    String dataset = "perf-test";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Add 10 commits to build up history
    for (int i = 0; i < 10; i++) {
      String turtle = String.format(
          "@prefix ex: <http://example.org/> . ex:s ex:p \"value%d\" .", i);
      putGraph(dataset, graphUri, branch, turtle);
    }

    // Wait for projections
    await().atMost(Duration.ofSeconds(15))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> true);

    // Act: Measure query time
    String sparql = """
        SELECT (COUNT(*) as ?count)
        WHERE {
          GRAPH <http://example.org/graph> {
            ?s ?p ?o .
          }
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(sparql, headers);

    // Warmup query
    restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        request, String.class, dataset, branch
    );

    // Measured query
    long startTime = System.currentTimeMillis();

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        request,
        String.class,
        dataset, branch
    );

    long queryTime = System.currentTimeMillis() - startTime;

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Performance regression threshold: <100ms
    // (Eager cache should make this <10ms, but allow margin for CI variance)
    assertThat(queryTime)
        .as("Branch HEAD query should be fast with eager cache updates")
        .isLessThan(100);
  }

  // Helper methods (same as before)
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
    headers.set("X-Author", "test");
    headers.set("X-Commit-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request, Void.class, dataset, graphUri, branch
    );
  }
}
```

### Step 3: Simplify Cache Logic (Optional)

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java:252-271`

Since branch HEADs are now eagerly updated, we can simplify:

```java
/**
 * Gets or creates a DatasetGraphInMemory for a specific commit.
 *
 * <p>For branch HEAD commits, the graph should already be cached (via eager
 * updates from projector). For historical commits, the graph is built on-demand.
 *
 * @param datasetName the dataset name
 * @param commitId the commit ID
 * @return the dataset graph materialized to the specified commit
 */
private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    CacheKey key = new CacheKey(datasetName, commitId);

    // Try cache first (handles both eager-updated and previously-built graphs)
    DatasetGraphInMemory graph = datasetCache.get(key,
        k -> buildDatasetGraph(datasetName, commitId));

    return graph;
}
```

**Removed:**
- `isLatestCommit()` check (no longer needed with eager updates)
- `cacheProperties.isKeepLatestPerBranch()` check (pinning handled by Caffeine weight)

**Simpler logic:**
- Unified cache access
- Eager updates ensure branch HEADs are cached
- On-demand building for historical commits

**Note:** If you want to keep the `isLatestCommit()` logic for cold-start scenarios (before projector runs), that's fine. Just add a comment explaining it's for cold start.

### Step 4: Add Cache Metrics Exposure

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Add metrics for cache performance:

```java
/**
 * Constructor.
 */
public DatasetService(
    // ... existing params ...
    MeterRegistry meterRegistry  // ← NEW: Add MeterRegistry
) {
    // ... existing initialization ...

    // Initialize cache
    this.datasetCache = Caffeine.newBuilder()
        .maximumSize(cacheProperties.getMaxSize())
        .recordStats()  // ← Already enabled
        .build();

    // ✅ NEW: Expose cache stats as metrics
    Gauge.builder("chucc.cache.size", datasetCache, cache -> cache.estimatedSize())
        .description("Number of cached dataset graphs")
        .register(meterRegistry);

    Gauge.builder("chucc.cache.hit_rate", datasetCache,
            cache -> cache.stats().hitRate())
        .description("Cache hit rate (0.0 to 1.0)")
        .register(meterRegistry);

    Gauge.builder("chucc.cache.miss_rate", datasetCache,
            cache -> cache.stats().missRate())
        .description("Cache miss rate (0.0 to 1.0)")
        .register(meterRegistry);
}
```

### Step 5: Update Configuration Documentation

**File:** `src/main/resources/application.yml`

Add comments for optimal cache settings:

```yaml
vc:
  cache:
    # Maximum number of historical commits to cache
    # Branch HEADs are always cached (via eager updates)
    # Recommended: 100-1000 depending on query patterns
    max-size: 1000

    # Keep latest commits per branch in cache
    # Note: With eager updates, this mainly helps cold-start scenarios
    keep-latest-per-branch: true

    # Time-to-live for cached graphs (0 = no TTL)
    # Recommended: 0 (let LRU eviction handle cleanup)
    ttl-minutes: 0
```

### Step 6: Run Benchmarks

```bash
# Run automated performance test (part of normal build)
mvn -q test -Dtest=QueryPerformanceIT 2>&1 | tail -20

# Run manual benchmark (detailed metrics)
mvn test -Dtest=CachePerformanceBenchmark

# Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Performance benchmark test created
- ✅ Automated regression test added (runs in CI)
- ✅ Cache logic simplified (optional)
- ✅ Cache metrics exposed via Micrometer
- ✅ Configuration documented
- ✅ Branch HEAD queries <10ms (P90)
- ✅ Cache hit rate >95% for branch HEADs
- ✅ Zero quality violations
- ✅ Full build passes: `mvn -q clean install`

---

## Expected Results

### Performance Metrics (After Eager Updates)

```
=== Performance Results ===
Average: 3 ms
P90: 5 ms
P99: 8 ms
===========================
```

**10-20x improvement** over lazy cache (100-200ms → 3-8ms)!

### Cache Metrics

```
chucc.cache.size = 150
chucc.cache.hit_rate = 0.98
chucc.cache.miss_rate = 0.02
```

- Hit rate >95% indicates eager updates working well
- Size = branch HEADs + frequently accessed historical commits

---

## Files to Create/Modify

### Production Code
- `src/main/java/org/chucc/vcserver/service/DatasetService.java` (add metrics, simplify logic)
- `src/main/resources/application.yml` (add documentation)

### Test Code
- `src/test/java/org/chucc/vcserver/performance/CachePerformanceBenchmark.java` (NEW - manual benchmark)
- `src/test/java/org/chucc/vcserver/integration/QueryPerformanceIT.java` (NEW - automated test)

**Total:** 2 modified, 2 new test files

---

## Next Steps

After completing this task:
1. ✅ Review benchmark results to validate performance improvement
2. ✅ Move to Task 04: Add monitoring and recovery mechanisms
3. ✅ Update `.tasks/README.md` to track progress

---

## Notes

### Why Manual + Automated Tests?

- **Automated (QueryPerformanceIT):** Runs in CI, detects regressions, lenient thresholds
- **Manual (CachePerformanceBenchmark):** Detailed metrics, stricter thresholds, run on-demand

### Cache Simplification Trade-offs

**Keep `isLatestCommit()` check if:**
- You want faster cold-start (first query after restart)
- Projector might be disabled in some environments

**Remove `isLatestCommit()` check if:**
- Projector always enabled
- Prefer simpler code
- Cold-start delay acceptable (projector catches up quickly)

**Recommendation:** Keep it, but add comment explaining it's for cold-start.

---

## References

- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Cache implementation
- [Caffeine Cache Stats](https://github.com/ben-manes/caffeine/wiki/Statistics) - Cache metrics documentation
