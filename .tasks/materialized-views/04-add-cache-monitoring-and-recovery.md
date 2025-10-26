# Task 04: Add Cache Monitoring and Recovery Mechanisms

**Status:** Not Started
**Priority:** Medium (Operational Excellence)
**Category:** Observability & Reliability
**Estimated Time:** 2-3 hours
**Complexity:** Low-Medium
**Prerequisites:** Tasks 01-03 completed (eager cache updates working)

---

## Overview

Add monitoring, health checks, and recovery mechanisms for the eager cache to ensure operational reliability. This makes the system production-ready by providing visibility into cache state and tools to recover from failures.

**Goals:**
1. **Observability:** Enhanced metrics for cache operations (already started in Task 03)
2. **Health Checks:** Detect cache issues and eager update failures
3. **Recovery:** Manual cache rebuild endpoint for troubleshooting
4. **Logging:** Enhanced logging for debugging projection failures

---

## Current State

### Existing Infrastructure

**Metrics (from Task 03):**
- `chucc.cache.size` - Number of cached graphs
- `chucc.cache.hit_rate` - Cache hit rate
- `chucc.cache.miss_rate` - Cache miss rate

**Missing:**
- Metrics for eager update operations
- Health checks for cache state
- Recovery mechanism for corrupted cache entries

---

## Requirements

### Functional Requirements

1. **Additional Metrics**
   - `chucc.cache.eager_updates.total` - Counter: Eager updates attempted
   - `chucc.cache.eager_updates.errors` - Counter: Eager update failures
   - `chucc.cache.eager_updates.duration` - Timer: Eager update duration
   - `chucc.cache.evictions.total` - Counter: Cache evictions

2. **Health Checks**
   - Endpoint: `/actuator/health/dataset-cache`
   - Status: UP / DOWN
   - Details: Cache size, hit rate, recent errors

3. **Cache Rebuild Endpoint**
   - Endpoint: `POST /actuator/cache/rebuild?dataset={dataset}&branch={branch}&confirm=true`
   - Rebuilds cached graph from CommitRepository event log
   - Returns rebuild stats

4. **Enhanced Logging**
   - Log eager update successes/failures
   - Log cache evictions for branch HEADs (shouldn't happen!)
   - Warn on low hit rates

### Non-Functional Requirements

1. **Performance**
   - Metrics collection <1ms overhead
   - Health checks <100ms

2. **Reliability**
   - Rebuild atomic (replace only if successful)
   - Fallback to on-demand if rebuild fails

---

## Implementation Steps

### Step 1: Add Eager Update Metrics to DatasetService

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Update `applyPatchToBranchCache()` to record metrics:

```java
public void applyPatchToBranchCache(String datasetName, String branch,
    CommitId newCommitId, RDFPatch patch) {

  logger.debug("Eagerly applying patch to cached graph for {}/{}", datasetName, branch);

  // ✅ Start timer
  Timer.Sample sample = Timer.start(meterRegistry);

  try {
    // ... existing logic ...

    // Apply patch within transaction
    graph.begin(ReadWrite.WRITE);
    try {
      RDFPatchOps.applyChange(graph, patch);
      graph.commit();

      // ✅ Record success
      sample.stop(meterRegistry.timer("chucc.cache.eager_updates.duration",
          "dataset", datasetName,
          "branch", branch,
          "status", "success"
      ));

      meterRegistry.counter("chucc.cache.eager_updates.total",
          "dataset", datasetName,
          "branch", branch,
          "status", "success"
      ).increment();

      logger.debug("Successfully applied patch to cached graph {}/{}", datasetName, branch);
    } catch (Exception e) {
      graph.abort();

      // ✅ Record error
      sample.stop(meterRegistry.timer("chucc.cache.eager_updates.duration",
          "dataset", datasetName,
          "branch", branch,
          "status", "error"
      ));

      meterRegistry.counter("chucc.cache.eager_updates.errors",
          "dataset", datasetName,
          "branch", branch
      ).increment();

      logger.error("Failed to apply patch to cached graph {}/{}. Graph will be rebuilt on next query.",
          datasetName, branch, e);
      return;
    } finally {
      graph.end();
    }

    // ... rest of method ...
  } catch (Exception e) {
    // Record outer errors too
    sample.stop(meterRegistry.timer("chucc.cache.eager_updates.duration",
        "status", "error"
    ));
    throw e;
  }
}
```

### Step 2: Add Cache Eviction Metrics

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Update cache builder to track evictions:

```java
this.datasetCache = Caffeine.newBuilder()
    .maximumSize(cacheProperties.getMaxSize())
    .recordStats()
    .evictionListener((CacheKey key, DatasetGraphInMemory graph, RemovalCause cause) -> {
      // Log evictions
      logger.debug("Cache eviction: {} (cause: {})", key, cause);

      // ✅ Record eviction metric
      meterRegistry.counter("chucc.cache.evictions.total",
          "dataset", key.datasetName(),
          "cause", cause.name()
      ).increment();

      // ✅ Warn if evicting a branch HEAD (shouldn't happen with keepLatestPerBranch!)
      if (isLatestCommit(key.datasetName(), key.commitId())) {
        logger.warn("WARNING: Evicted branch HEAD from cache: {} (cause: {}). " +
            "This may indicate cache size is too small!", key, cause);

        meterRegistry.counter("chucc.cache.branch_head_evictions.total",
            "dataset", key.datasetName()
        ).increment();
      }
    })
    .build();
```

### Step 3: Create Health Indicator

**File:** `src/main/java/org/chucc/vcserver/health/DatasetCacheHealthIndicator.java` (NEW)

```java
package org.chucc.vcserver.health;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.HashMap;
import java.util.Map;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.service.DatasetService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for dataset cache.
 *
 * <p>Reports the status of the eager cache, including:
 * - Cache size and hit rate
 * - Recent errors (if any)
 * - Overall health status
 */
@Component("datasetCache")
public class DatasetCacheHealthIndicator implements HealthIndicator {

  private final DatasetService datasetService;

  /**
   * Constructs the health indicator.
   *
   * @param datasetService the service to monitor
   */
  public DatasetCacheHealthIndicator(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @Override
  public Health health() {
    try {
      CacheStats stats = datasetService.getCacheStats();

      Map<String, Object> details = new HashMap<>();
      details.put("size", datasetService.getCacheSize());
      details.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
      details.put("missRate", String.format("%.2f%%", stats.missRate() * 100));
      details.put("evictionCount", stats.evictionCount());

      // Check health criteria
      if (stats.hitRate() < 0.50) {
        // Low hit rate indicates cache not being used effectively
        return Health.degraded()
            .withDetail("warning", "Low cache hit rate (<50%). Consider increasing cache size.")
            .withDetails(details)
            .build();
      }

      return Health.up()
          .withDetails(details)
          .build();

    } catch (Exception e) {
      return Health.down()
          .withException(e)
          .build();
    }
  }
}
```

**Note:** Add public methods to DatasetService if needed:
```java
public CacheStats getCacheStats() {
  return datasetCache.stats();
}

public long getCacheSize() {
  return datasetCache.estimatedSize();
}
```

### Step 4: Create Cache Rebuild Service

**File:** `src/main/java/org/chucc/vcserver/service/CacheRebuildService.java` (NEW)

```java
package org.chucc.vcserver.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.DatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for rebuilding cache entries from commit history.
 *
 * <p>Provides recovery when cached graphs become corrupted or out of sync.
 */
@Service
public class CacheRebuildService {

  private static final Logger logger = LoggerFactory.getLogger(CacheRebuildService.class);

  private final DatasetService datasetService;
  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;
  private final DatasetRepository datasetRepository;
  private final MeterRegistry meterRegistry;

  /**
   * Constructor.
   */
  public CacheRebuildService(
      DatasetService datasetService,
      CommitRepository commitRepository,
      BranchRepository branchRepository,
      DatasetRepository datasetRepository,
      MeterRegistry meterRegistry) {
    this.datasetService = datasetService;
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
    this.datasetRepository = datasetRepository;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Rebuild cache entry for a branch from commit history.
   *
   * <p>This operation:
   * 1. Validates dataset and branch exist
   * 2. Builds graph from commit history
   * 3. Replaces existing cache entry
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return rebuild statistics
   */
  public RebuildResult rebuildBranch(String dataset, String branch) {
    logger.info("Starting cache rebuild for {}/{}", dataset, branch);

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      // Validate dataset exists
      if (!datasetRepository.exists(dataset)) {
        throw new DatasetNotFoundException(dataset);
      }

      // Get branch and HEAD commit
      Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
          .orElseThrow(() -> new BranchNotFoundException(dataset, branch));

      CommitId headCommitId = branchObj.getCommitId();

      // Build commit chain
      List<Commit> commitChain = buildCommitChain(dataset, headCommitId);
      logger.info("Rebuilding {} commits for {}/{}", commitChain.size(), dataset, branch);

      // Build graph from scratch
      DatasetGraphInMemory newGraph = new DatasetGraphInMemory();

      for (Commit commit : commitChain) {
        Optional<RDFPatch> patchOpt = commitRepository.findPatchByDatasetAndId(
            dataset, commit.id());

        if (patchOpt.isEmpty()) {
          throw new IllegalStateException(
              String.format("Missing patch for commit %s in dataset %s", commit.id(), dataset));
        }

        RDFPatch patch = patchOpt.get();

        newGraph.begin(ReadWrite.WRITE);
        try {
          org.apache.jena.rdfpatch.RDFPatchOps.applyChange(newGraph, patch);
          newGraph.commit();
        } catch (Exception e) {
          newGraph.abort();
          throw new RuntimeException("Failed to apply patch for commit " + commit.id(), e);
        } finally {
          newGraph.end();
        }
      }

      // Replace cache entry
      datasetService.putInCache(dataset, headCommitId, newGraph);

      // Record metrics
      long durationMs = sample.stop(meterRegistry.timer("chucc.cache.rebuild.duration",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      )).toMillis();

      meterRegistry.counter("chucc.cache.rebuild.total",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ).increment();

      logger.info("Successfully rebuilt cache for {}/{} ({} commits, {} ms)",
          dataset, branch, commitChain.size(), durationMs);

      return new RebuildResult(commitChain.size(), durationMs);

    } catch (Exception e) {
      sample.stop(meterRegistry.timer("chucc.cache.rebuild.duration",
          "status", "error"
      ));

      meterRegistry.counter("chucc.cache.rebuild.total",
          "status", "error"
      ).increment();

      logger.error("Failed to rebuild cache for {}/{}", dataset, branch, e);
      throw e;
    }
  }

  private List<Commit> buildCommitChain(String dataset, CommitId targetCommitId) {
    List<Commit> chain = new ArrayList<>();
    CommitId currentId = targetCommitId;

    while (currentId != null) {
      Commit commit = commitRepository.findByDatasetAndId(dataset, currentId)
          .orElseThrow(() -> new IllegalStateException("Missing commit: " + currentId));

      chain.add(0, commit);  // Add at beginning (reverse order)

      // Move to parent (single parent for now)
      currentId = commit.parents().isEmpty() ? null : commit.parents().get(0);
    }

    return chain;
  }

  /**
   * Rebuild result.
   */
  public record RebuildResult(int commitsProcessed, long durationMs) { }
}
```

**Note:** Add `putInCache()` method to DatasetService:
```java
/**
 * Put a graph directly into the cache.
 * Used by rebuild service for recovery.
 */
public void putInCache(String dataset, CommitId commitId, DatasetGraphInMemory graph) {
  CacheKey key = new CacheKey(dataset, commitId);
  datasetCache.put(key, graph);
  logger.debug("Manually inserted graph into cache: {}", key);
}
```

### Step 5: Create Rebuild Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/CacheManagementController.java` (NEW)

```java
package org.chucc.vcserver.controller;

import org.chucc.vcserver.service.CacheRebuildService;
import org.chucc.vcserver.service.CacheRebuildService.RebuildResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for cache management endpoints.
 *
 * <p>Administrative endpoints for monitoring and recovering cache state.
 */
@RestController
@RequestMapping("/actuator/cache")
public class CacheManagementController {

  private final CacheRebuildService rebuildService;

  /**
   * Constructor.
   */
  public CacheManagementController(CacheRebuildService rebuildService) {
    this.rebuildService = rebuildService;
  }

  /**
   * Rebuild cache entry for a branch from commit history.
   *
   * <p><b>Warning:</b> This is a recovery operation. Use only when cache is corrupted.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param confirm must be "true" to proceed
   * @return rebuild statistics
   */
  @PostMapping("/rebuild")
  public ResponseEntity<RebuildResponse> rebuildBranch(
      @RequestParam("dataset") String dataset,
      @RequestParam("branch") String branch,
      @RequestParam(value = "confirm", defaultValue = "false") boolean confirm) {

    if (!confirm) {
      return ResponseEntity.badRequest()
          .body(new RebuildResponse(
              "error",
              "Rebuild requires confirm=true parameter",
              0,
              0
          ));
    }

    RebuildResult result = rebuildService.rebuildBranch(dataset, branch);

    return ResponseEntity.ok(new RebuildResponse(
        "success",
        String.format("Rebuilt %d commits in %d ms", result.commitsProcessed(), result.durationMs()),
        result.commitsProcessed(),
        result.durationMs()
    ));
  }

  /**
   * Rebuild response DTO.
   */
  public record RebuildResponse(
      String status,
      String message,
      int commitsProcessed,
      long durationMs
  ) { }
}
```

### Step 6: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/CacheRebuildIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for cache rebuild functionality.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class CacheRebuildIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void rebuildEndpoint_shouldRebuildCacheFromCommitHistory() throws Exception {
    // Arrange: Create dataset with commits
    String dataset = "test-dataset";
    String branch = "main";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Add 5 commits
    for (int i = 0; i < 5; i++) {
      putGraph(dataset, "http://example.org/graph" + i, branch,
          "@prefix ex: <http://example.org/> . ex:s" + i + " ex:p \"value\" .");
    }

    // Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> true);

    // Act: Trigger rebuild
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/cache/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        dataset, branch
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("success");
    assertThat(response.getBody()).contains("commitsProcessed");
  }

  @Test
  void rebuildEndpoint_shouldRequireConfirmation() {
    // Act: Try without confirm=true
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/cache/rebuild?dataset=test&branch=main",
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("confirm=true");
  }

  @Test
  void healthEndpoint_shouldReportCacheStatus() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health/datasetCache",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
    assertThat(response.getBody()).contains("hitRate");
  }

  // Helper methods (omitted for brevity)
}
```

### Step 7: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=CacheRebuildIT 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Eager update metrics recorded (successes, errors, duration)
- ✅ Cache eviction metrics tracked
- ✅ Health indicator reports cache status
- ✅ Rebuild service can reconstruct graphs from commit history
- ✅ Rebuild endpoint requires confirmation
- ✅ Integration tests verify rebuild and health checks
- ✅ Zero quality violations
- ✅ Full build passes: `mvn -q clean install`

---

## Files to Create/Modify

### Production Code
- `src/main/java/org/chucc/vcserver/service/DatasetService.java` (add metrics)
- `src/main/java/org/chucc/vcserver/health/DatasetCacheHealthIndicator.java` (NEW)
- `src/main/java/org/chucc/vcserver/service/CacheRebuildService.java` (NEW)
- `src/main/java/org/chucc/vcserver/controller/CacheManagementController.java` (NEW)

### Test Code
- `src/test/java/org/chucc/vcserver/integration/CacheRebuildIT.java` (NEW)

**Total:** 1 modified, 3 new production files, 1 new test file

---

## Operational Runbook

### Scenario 1: Cache Entry Corrupted

**Symptoms:** Queries return incorrect data for a branch

**Diagnosis:**
```bash
# Check health
curl http://localhost:8080/actuator/health/datasetCache

# Check metrics
curl http://localhost:8080/actuator/metrics/chucc.cache.eager_updates.errors
```

**Recovery:**
```bash
curl -X POST "http://localhost:8080/actuator/cache/rebuild?dataset=mydata&branch=main&confirm=true"
```

### Scenario 2: Low Hit Rate

**Symptoms:** `chucc.cache.hit_rate < 0.50`

**Diagnosis:**
1. Check cache size: `chucc.cache.size`
2. Check eviction count: `chucc.cache.evictions.total`
3. Check if branch HEADs being evicted (shouldn't happen!)

**Actions:**
1. Increase `vc.cache.max-size` in application.yml
2. Review query patterns (many historical queries?)
3. Check for cache thrashing

### Scenario 3: Eager Update Failures

**Symptoms:** `chucc.cache.eager_updates.errors > 0`

**Diagnosis:**
1. Check logs for error messages
2. Review commit events for malformed patches

**Actions:**
1. Fix underlying issue (invalid patch, transaction error)
2. Rebuild affected cache entries
3. Queries will fallback to on-demand building

---

## Metrics Dashboard (Grafana Example)

```promql
# Cache hit rate
chucc_cache_hit_rate

# Eager update success rate
rate(chucc_cache_eager_updates_total{status="success"}[5m]) /
rate(chucc_cache_eager_updates_total[5m])

# Cache size
chucc_cache_size

# Evictions
rate(chucc_cache_evictions_total[5m])

# Branch HEAD evictions (should be 0!)
rate(chucc_cache_branch_head_evictions_total[5m])
```

---

## Next Steps

After completing this task:
1. ✅ **Feature Complete!** All materialized views (eager cache) functionality implemented
2. ✅ Update `.tasks/README.md` to mark completion
3. ✅ Delete task files
4. 🎉 Celebrate! Branch HEADs now 10-20x faster!

---

## References

- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Cache implementation
- [Micrometer Documentation](https://micrometer.io/docs) - Metrics
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) - Health endpoints
- [Caffeine Cache](https://github.com/ben-manes/caffeine) - Cache implementation

---

## Summary

This task completes the eager cache update feature:
- **Task 01:** Added `applyPatchToBranchCache()` method
- **Task 02:** Integrated with ReadModelProjector
- **Task 03:** Added benchmarks and optimizations
- **Task 04:** Added monitoring and recovery

**Result:** Branch HEAD queries are now **10-20x faster** (<10ms vs 100-200ms) with production-ready monitoring and recovery mechanisms!
