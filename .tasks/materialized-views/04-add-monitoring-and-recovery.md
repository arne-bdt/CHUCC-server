# Task 04: Add Monitoring and Recovery Mechanisms

**Status:** Not Started
**Priority:** Medium (Operational Excellence)
**Category:** Observability & Reliability
**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Prerequisites:** Tasks 01-03 completed (MaterializedBranchRepository fully integrated)

---

## Overview

Add monitoring, metrics, health checks, and recovery mechanisms for materialized views to ensure operational reliability and debugging capabilities. This makes the system production-ready by providing visibility into materialized graph state and tools to recover from failures.

**Goals:**
1. **Observability:** Metrics for materialized graphs (count, memory usage, operations)
2. **Health Checks:** Detect inconsistencies between CommitRepository and MaterializedBranchRepository
3. **Recovery:** Manual rebuild endpoint to reconstruct corrupted graphs from event log
4. **Logging:** Enhanced logging for debugging projection failures

---

## Current State

### Existing Metrics Infrastructure

**File:** [KafkaMetricsConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaMetricsConfig.java)

The project already has Micrometer metrics for Kafka operations.

**Existing Metrics:**
- `chucc.kafka.topic.created` - Counter
- `chucc.kafka.topic.deleted` - Counter
- `chucc.kafka.topic.health_check.total` - Counter
- `chucc.kafka.topic.creation.errors` - Counter

**Pattern to Follow:**
```java
@Configuration
public class MetricsConfig {
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    // Metrics defined here or in components using @Autowired MeterRegistry
}
```

### Existing Health Endpoint

**Actuator enabled:** `management.endpoints.web.exposure.include=health,info,kafka`

**URL:** `/actuator/health`

We can add custom health indicators for materialized views.

---

## Requirements

### Functional Requirements

1. **Metrics (Micrometer)**
   - `chucc.materialized_views.count` - Gauge: Number of materialized graphs
   - `chucc.materialized_views.memory_bytes` - Gauge: Estimated memory usage
   - `chucc.materialized_views.patch_applied.total` - Counter: Patches applied
   - `chucc.materialized_views.patch_applied.errors` - Counter: Patch failures
   - `chucc.materialized_views.rebuild.total` - Counter: Manual rebuilds triggered
   - `chucc.materialized_views.rebuild.duration` - Timer: Rebuild duration

2. **Health Checks**
   - Endpoint: `/actuator/health/materialized-views`
   - Status: UP / DOWN
   - Details: Number of graphs, memory usage, last error (if any)

3. **Rebuild Endpoint**
   - Endpoint: `POST /actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}`
   - Requires confirmation: `&confirm=true`
   - Rebuilds materialized graph from CommitRepository event log
   - Returns rebuild stats (commits processed, duration)

4. **Memory Monitoring**
   - Periodic logging of graph sizes
   - Warnings if memory usage exceeds threshold

### Non-Functional Requirements

1. **Performance**
   - Metrics collection should not impact query performance (<1ms overhead)
   - Health checks should be fast (<100ms)

2. **Reliability**
   - Rebuild should be atomic (replace graph only if successful)
   - Fallback to existing graph if rebuild fails

3. **Security**
   - Rebuild endpoint requires admin privileges (future: add security)
   - For now: document that it's an admin operation

---

## Implementation Steps

### Step 1: Add Metrics to MaterializedBranchRepository

**File:** `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java`

**Add MeterRegistry dependency:**
```java
private final MeterRegistry meterRegistry;

public InMemoryMaterializedBranchRepository(MeterRegistry meterRegistry) {
  this.meterRegistry = meterRegistry;

  // Register gauges
  Gauge.builder("chucc.materialized_views.count", branchGraphs, ConcurrentHashMap::size)
      .description("Number of materialized branch graphs")
      .register(meterRegistry);

  Gauge.builder("chucc.materialized_views.memory_bytes", this, this::estimateMemoryUsage)
      .description("Estimated memory usage of materialized graphs in bytes")
      .register(meterRegistry);
}
```

**Add metrics to operations:**
```java
@Override
public void applyPatchToBranch(String dataset, String branch, RDFPatch patch) {
    DatasetGraph graph = getBranchGraph(dataset, branch);

    graph.begin(ReadWrite.WRITE);
    try {
      RDFPatchOps.applyChange(graph, patch);
      graph.commit();

      // ✅ Increment success counter
      meterRegistry.counter("chucc.materialized_views.patch_applied.total",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ).increment();

      logger.debug("Applied patch to materialized graph {}/{}", dataset, branch);
    } catch (Exception e) {
      graph.abort();

      // ✅ Increment error counter
      meterRegistry.counter("chucc.materialized_views.patch_applied.errors",
          "dataset", dataset,
          "branch", branch
      ).increment();

      String errorMsg = String.format(
          "Failed to apply patch to materialized graph %s/%s", dataset, branch);
      logger.error(errorMsg, e);
      throw new PatchApplicationException(errorMsg, e);
    } finally {
      graph.end();
    }
}
```

**Add memory estimation method:**
```java
/**
 * Estimate total memory usage of all materialized graphs.
 *
 * <p>This is a rough estimate based on Jena's in-memory storage.
 * Assumes ~200 bytes per triple (conservative estimate).
 *
 * @return estimated memory usage in bytes
 */
private long estimateMemoryUsage() {
  long totalTriples = 0;

  for (DatasetGraph graph : branchGraphs.values()) {
    graph.begin(ReadWrite.READ);
    try {
      // Count triples in all graphs
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

### Step 2: Create Health Indicator

**File:** `src/main/java/org/chucc/vcserver/health/MaterializedViewsHealthIndicator.java` (NEW)

```java
package org.chucc.vcserver.health;

import java.util.HashMap;
import java.util.Map;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for materialized views.
 *
 * <p>Reports the status of the MaterializedBranchRepository, including:
 * - Number of materialized graphs
 * - Memory usage estimate
 * - Overall health status
 */
@Component("materializedViews")
public class MaterializedViewsHealthIndicator implements HealthIndicator {

  private final MaterializedBranchRepository materializedBranchRepo;

  /**
   * Constructs a new health indicator.
   *
   * @param materializedBranchRepo the repository to monitor
   */
  public MaterializedViewsHealthIndicator(
      MaterializedBranchRepository materializedBranchRepo) {
    this.materializedBranchRepo = materializedBranchRepo;
  }

  @Override
  public Health health() {
    try {
      int graphCount = materializedBranchRepo.getGraphCount();

      Map<String, Object> details = new HashMap<>();
      details.put("graphCount", graphCount);
      details.put("status", "Materialized views operational");

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

**Access via:** `GET /actuator/health/materializedViews`

### Step 3: Create Rebuild Service

**File:** `src/main/java/org/chucc/vcserver/service/MaterializedViewRebuildService.java` (NEW)

```java
package org.chucc.vcserver.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.DatasetRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for rebuilding materialized views from event log.
 *
 * <p>This service provides recovery mechanisms when materialized graphs
 * become corrupted or out of sync with the commit history.
 */
@Service
public class MaterializedViewRebuildService {

  private static final Logger logger =
      LoggerFactory.getLogger(MaterializedViewRebuildService.class);

  private final MaterializedBranchRepository materializedBranchRepo;
  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;
  private final DatasetRepository datasetRepository;
  private final MeterRegistry meterRegistry;

  /**
   * Constructs the rebuild service.
   */
  public MaterializedViewRebuildService(
      MaterializedBranchRepository materializedBranchRepo,
      CommitRepository commitRepository,
      BranchRepository branchRepository,
      DatasetRepository datasetRepository,
      MeterRegistry meterRegistry) {
    this.materializedBranchRepo = materializedBranchRepo;
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
    this.datasetRepository = datasetRepository;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Rebuild materialized graph for a branch from commit history.
   *
   * <p>This operation:
   * 1. Validates dataset and branch exist
   * 2. Creates new empty graph
   * 3. Applies all patches from commit history in order
   * 4. Replaces existing materialized graph atomically
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return rebuild statistics
   * @throws DatasetNotFoundException if dataset doesn't exist
   * @throws BranchNotFoundException if branch doesn't exist
   */
  public RebuildResult rebuildBranch(String dataset, String branch) {
    logger.info("Starting rebuild of materialized graph for {}/{}", dataset, branch);

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
      // 1. Validate dataset exists
      if (!datasetRepository.exists(dataset)) {
        throw new DatasetNotFoundException(dataset);
      }

      // 2. Get branch and HEAD commit
      Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
          .orElseThrow(() -> new BranchNotFoundException(dataset, branch));

      CommitId headCommitId = branchObj.getCommitId();

      // 3. Build commit chain from root to HEAD
      List<Commit> commitChain = buildCommitChain(dataset, headCommitId);

      logger.info("Rebuilding {} commits for {}/{}", commitChain.size(), dataset, branch);

      // 4. Create new graph and apply all patches
      DatasetGraph newGraph = DatasetGraphFactory.createTxnMem();

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

      // 5. Replace existing graph atomically
      // (In current implementation, we can't truly atomic replace, but we can minimize window)
      materializedBranchRepo.deleteBranch(dataset, branch);
      materializedBranchRepo.createBranch(dataset, branch, Optional.empty());

      // Copy new graph to materialized repository
      DatasetGraph targetGraph = materializedBranchRepo.getBranchGraph(dataset, branch);
      copyGraph(newGraph, targetGraph);

      // Close temporary graph
      newGraph.close();

      // 6. Record metrics
      sample.stop(meterRegistry.timer("chucc.materialized_views.rebuild.duration",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ));

      meterRegistry.counter("chucc.materialized_views.rebuild.total",
          "dataset", dataset,
          "branch", branch,
          "status", "success"
      ).increment();

      logger.info("Successfully rebuilt materialized graph for {}/{} ({} commits)",
          dataset, branch, commitChain.size());

      return new RebuildResult(commitChain.size(), sample.stop(Timer.noop()).toMillis());

    } catch (Exception e) {
      sample.stop(meterRegistry.timer("chucc.materialized_views.rebuild.duration",
          "dataset", dataset,
          "branch", branch,
          "status", "error"
      ));

      meterRegistry.counter("chucc.materialized_views.rebuild.total",
          "dataset", dataset,
          "branch", branch,
          "status", "error"
      ).increment();

      logger.error("Failed to rebuild materialized graph for {}/{}", dataset, branch, e);
      throw e;
    }
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

      // Move to parent (assume single parent for now, multi-parent handled in merge)
      currentId = commit.parents().isEmpty() ? null : commit.parents().get(0);
    }

    return chain;
  }

  /**
   * Copy all data from source graph to target graph.
   *
   * @param source the source graph
   * @param target the target graph
   */
  private void copyGraph(DatasetGraph source, DatasetGraph target) {
    source.begin(ReadWrite.READ);
    target.begin(ReadWrite.WRITE);
    try {
      // Copy all named graphs
      source.listGraphNodes().forEachRemaining(graphName -> {
        org.apache.jena.graph.Graph sourceGraph = source.getGraph(graphName);
        org.apache.jena.graph.Graph targetGraph = target.getGraph(graphName);

        targetGraph.getPrefixMapping().setNsPrefixes(sourceGraph.getPrefixMapping());
        sourceGraph.find().forEachRemaining(targetGraph::add);
      });

      // Copy default graph
      org.apache.jena.graph.Graph defaultSource = source.getDefaultGraph();
      org.apache.jena.graph.Graph defaultTarget = target.getDefaultGraph();
      defaultTarget.getPrefixMapping().setNsPrefixes(defaultSource.getPrefixMapping());
      defaultSource.find().forEachRemaining(defaultTarget::add);

      source.commit();
      target.commit();
    } finally {
      source.end();
      target.end();
    }
  }

  /**
   * Result of a rebuild operation.
   *
   * @param commitsProcessed number of commits processed
   * @param durationMs rebuild duration in milliseconds
   */
  public record RebuildResult(int commitsProcessed, long durationMs) { }
}
```

### Step 4: Create Rebuild Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/MaterializedViewsController.java` (NEW)

```java
package org.chucc.vcserver.controller;

import org.chucc.vcserver.service.MaterializedViewRebuildService;
import org.chucc.vcserver.service.MaterializedViewRebuildService.RebuildResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for materialized view management endpoints.
 *
 * <p>These are administrative endpoints for monitoring and maintaining
 * materialized views.
 */
@RestController
@RequestMapping("/actuator/materialized-views")
public class MaterializedViewsController {

  private final MaterializedViewRebuildService rebuildService;

  /**
   * Constructs the controller.
   *
   * @param rebuildService the rebuild service
   */
  public MaterializedViewsController(MaterializedViewRebuildService rebuildService) {
    this.rebuildService = rebuildService;
  }

  /**
   * Rebuild materialized graph for a branch from commit history.
   *
   * <p>This is a manual recovery operation. It rebuilds the materialized
   * graph by replaying all commits from the branch's history.
   *
   * <p><b>Warning:</b> This operation may take time for branches with
   * long commit histories.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param confirm must be "true" to proceed (safety check)
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
   * Response DTO for rebuild endpoint.
   *
   * @param status "success" or "error"
   * @param message human-readable message
   * @param commitsProcessed number of commits processed
   * @param durationMs rebuild duration in milliseconds
   */
  public record RebuildResponse(
      String status,
      String message,
      int commitsProcessed,
      long durationMs
  ) { }
}
```

### Step 5: Add Periodic Memory Logging

**File:** `src/main/java/org/chucc/vcserver/monitoring/MaterializedViewsMonitor.java` (NEW)

```java
package org.chucc.vcserver.monitoring;

import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Monitors materialized views and logs statistics periodically.
 */
@Component
public class MaterializedViewsMonitor {

  private static final Logger logger = LoggerFactory.getLogger(MaterializedViewsMonitor.class);

  private final MaterializedBranchRepository materializedBranchRepo;

  /**
   * Constructs the monitor.
   *
   * @param materializedBranchRepo the repository to monitor
   */
  public MaterializedViewsMonitor(MaterializedBranchRepository materializedBranchRepo) {
    this.materializedBranchRepo = materializedBranchRepo;
  }

  /**
   * Log materialized view statistics every 5 minutes.
   */
  @Scheduled(fixedRate = 300000)  // 5 minutes
  public void logStatistics() {
    int graphCount = materializedBranchRepo.getGraphCount();

    logger.info("Materialized Views Stats: {} graphs", graphCount);

    // TODO: Add memory warning if exceeds threshold
  }
}
```

**Enable scheduling:**

**File:** `src/main/java/org/chucc/vcserver/VcServerApplication.java`

Add `@EnableScheduling`:
```java
@SpringBootApplication
@EnableScheduling  // ← Add this
public class VcServerApplication {
    // ...
}
```

### Step 6: Update Configuration

**File:** `src/main/resources/application.yml`

Add configuration for monitoring:

```yaml
chucc:
  materialized-views:
    enabled: true
    memory-warning-threshold-mb: 1000  # Warn if exceeds 1GB
    periodic-logging-enabled: true
```

Create configuration class:

**File:** `src/main/java/org/chucc/vcserver/config/MaterializedViewsProperties.java` (NEW)

```java
package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for materialized views.
 */
@Configuration
@ConfigurationProperties(prefix = "chucc.materialized-views")
public class MaterializedViewsProperties {

  private boolean enabled = true;
  private long memoryWarningThresholdMb = 1000;
  private boolean periodicLoggingEnabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getMemoryWarningThresholdMb() {
    return memoryWarningThresholdMb;
  }

  public void setMemoryWarningThresholdMb(long memoryWarningThresholdMb) {
    this.memoryWarningThresholdMb = memoryWarningThresholdMb;
  }

  public boolean isPeriodicLoggingEnabled() {
    return periodicLoggingEnabled;
  }

  public void setPeriodicLoggingEnabled(boolean periodicLoggingEnabled) {
    this.periodicLoggingEnabled = periodicLoggingEnabled;
  }
}
```

### Step 7: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MaterializedViewRebuildIT.java` (NEW)

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
 * Integration tests for materialized view rebuild functionality.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedViewRebuildIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void rebuildEndpoint_shouldRebuildBranchFromCommitHistory() throws Exception {
    // Arrange: Create dataset and branch with data
    String dataset = "test-dataset";
    String branch = "main";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Add multiple commits
    for (int i = 0; i < 5; i++) {
      putGraph(dataset, "http://example.org/graph" + i, branch,
          "@prefix ex: <http://example.org/> . ex:s" + i + " ex:p \"value" + i + "\" .");
    }

    // Wait for projection
    await().atMost(Duration.ofSeconds(10)).pollDelay(Duration.ofMillis(500)).until(() -> true);

    // Act: Trigger rebuild
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
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
    // Act: Try to rebuild without confirm=true
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset=test&branch=main",
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("confirm=true");
  }

  @Test
  void healthEndpoint_shouldReportMaterializedViewsStatus() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health/materializedViews",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
    assertThat(response.getBody()).contains("graphCount");
  }

  // Helper methods omitted for brevity (same as other tests)
}
```

### Step 8: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=MaterializedViewRebuildIT 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Micrometer metrics added for graph count, memory, operations
- ✅ Health indicator reports materialized views status
- ✅ Rebuild service can reconstruct graphs from commit history
- ✅ Rebuild endpoint requires confirmation and returns stats
- ✅ Periodic logging of graph statistics
- ✅ Configuration properties for monitoring thresholds
- ✅ Integration tests verify rebuild and health checks
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Unit Tests

**RebuildService:**
- Test commit chain building
- Test graph copying
- Test error handling

### Integration Tests (Projector Enabled)

**Rebuild Endpoint:**
- Verify rebuild from commit history
- Verify confirmation requirement
- Verify metrics recorded

**Health Endpoint:**
- Verify status reporting
- Verify graph count accuracy

---

## Files to Create

### Production Code
- `src/main/java/org/chucc/vcserver/health/MaterializedViewsHealthIndicator.java` (NEW)
- `src/main/java/org/chucc/vcserver/service/MaterializedViewRebuildService.java` (NEW)
- `src/main/java/org/chucc/vcserver/controller/MaterializedViewsController.java` (NEW)
- `src/main/java/org/chucc/vcserver/monitoring/MaterializedViewsMonitor.java` (NEW)
- `src/main/java/org/chucc/vcserver/config/MaterializedViewsProperties.java` (NEW)

### Files to Modify
- `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java` (add metrics)
- `src/main/java/org/chucc/vcserver/VcServerApplication.java` (add @EnableScheduling)
- `src/main/resources/application.yml` (add config)

### Test Code
- `src/test/java/org/chucc/vcserver/integration/MaterializedViewRebuildIT.java` (NEW)
- `src/test/java/org/chucc/vcserver/service/MaterializedViewRebuildServiceTest.java` (NEW)

**Total:** 5 new production files, 2 modified files, 2 new test files

---

## Metrics Dashboard Example

Using Prometheus + Grafana:

```promql
# Number of materialized graphs
chucc_materialized_views_count

# Memory usage
chucc_materialized_views_memory_bytes / 1024 / 1024  # Convert to MB

# Patch success rate
rate(chucc_materialized_views_patch_applied_total{status="success"}[5m]) /
rate(chucc_materialized_views_patch_applied_total[5m])

# Rebuild operations
rate(chucc_materialized_views_rebuild_total[5m])
```

---

## Operational Runbook

### Scenario 1: Graph Out of Sync

**Symptoms:** Queries return stale data for a branch

**Diagnosis:**
1. Check logs for patch application errors
2. Query `/actuator/health/materializedViews`
3. Compare branch HEAD commit with last patch applied

**Recovery:**
```bash
curl -X POST "http://localhost:8080/actuator/materialized-views/rebuild?dataset=mydata&branch=main&confirm=true"
```

### Scenario 2: High Memory Usage

**Symptoms:** Memory usage exceeds threshold

**Diagnosis:**
1. Check metric: `chucc_materialized_views_memory_bytes`
2. Check metric: `chucc_materialized_views_count`
3. Identify branches with large graphs

**Actions:**
1. Delete unused branches
2. Consider archiving old branches
3. Increase memory limit or switch to TDB2 backend (future)

---

## References

- [KafkaMetricsConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaMetricsConfig.java) - Existing metrics pattern
- [InMemoryMaterializedBranchRepository.java](../../src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java) - Task 01
- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

## Next Steps

After completing this task:
1. ✅ Mark this task file as complete
2. ✅ Update `.tasks/README.md` to track completion
3. ✅ **Feature Complete!** All materialized views functionality implemented
4. 🎉 Celebrate successful implementation!

---

## Future Enhancements (Out of Scope)

### TDB2 Backend (Persistent Storage)

For production deployments with many branches:
- Replace `DatasetGraphFactory.createTxnMem()` with TDB2
- Persist graphs to disk
- Reduce memory footprint
- Faster startup (no rebuild needed)

**Implementation:** New task in future

### Automatic Rebuild on Errors

Add automatic retry/rebuild when patch application fails:
- Detect failures via metrics
- Trigger rebuild automatically
- Alert operators

**Implementation:** New task in future

### Branch Archival

Archive old/inactive branches to reduce memory:
- Move to snapshot storage
- Rebuild on-demand if accessed
- Automatic archival policy

**Implementation:** New task in future
