# Task 06: Implement Fail-Fast Projection Errors

**Status:** Not Started
**Priority:** High (Data Integrity & Consistency)
**Category:** Reliability & Correctness
**Estimated Time:** 2-3 hours
**Complexity:** Low-Medium
**Prerequisites:** None (can be done independently)

---

## Overview

Implement fail-fast behavior for materialized view projection errors to ensure data consistency. Currently, when a patch fails to apply to a materialized graph, the error is logged but swallowed, causing silent data corruption.

**Problem:**
- Current behavior: Patch application failures are caught and logged, but Kafka offset is still committed
- Result: Materialized graph becomes out-of-sync with commit history
- Impact: Queries return stale/incorrect data until manual rebuild
- Detection: Only discovered during periodic health checks (every 5 minutes)

**Solution:**
- Re-throw exceptions on patch application failures
- Kafka will retry event processing automatically
- System maintains consistency at the cost of availability (fails fast, not silently)
- Projector pauses until issue resolved (manual intervention or Kafka retry succeeds)

**Benefits:**
- ✅ **Consistency guaranteed:** Materialized views always match commit history
- ✅ **No silent corruption:** Failures are immediately visible
- ✅ **Automatic retry:** Kafka handles retry logic (configurable backoff)
- ✅ **Fail-fast principle:** System refuses to serve incorrect data

**Trade-off:**
- ⚠️ Availability: Projector blocks on errors (requires resolution before continuing)
- ⚠️ Operations: May need intervention for persistent errors (bad event data, bugs)
- ✅ This trade-off favors consistency (correct choice for event sourcing)

---

## Current State

### Current Implementation (Silent Failure)

**File:** [ReadModelProjector.java:297-302](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L297-L302)

```java
try {
  materializedBranchRepo.applyPatchToBranch(event.dataset(), event.branch(), patch);
} catch (Exception e) {
  logger.error("Failed to apply patch to materialized view for commit {}", event.commitId(), e);
  // ❌ ERROR SWALLOWED - Kafka offset committed, graph now corrupted!
}
```

**Problem:**
1. Exception caught but not rethrown
2. Event marked as "processed" (Kafka offset committed)
3. Materialized graph now out-of-sync with CommitRepository
4. Subsequent queries return incorrect data
5. Only detected by periodic health checks (5-minute delay)

### Why This Pattern Was Used

**Original intent:** Prioritize availability over consistency
- Goal: Keep projector running even if one graph fails
- Assumption: Manual rebuild can fix issues later

**Why it's wrong for event sourcing:**
- Event sourcing requires **strong consistency**
- Materialized views are derived state (must match events)
- Silent corruption violates eventual consistency guarantee
- Better to fail loudly than serve incorrect data

---

## Requirements

### Functional Requirements

1. **Fail-Fast on Patch Application Errors**
   - Re-throw exception when `applyPatchToBranch()` fails
   - Do NOT commit Kafka offset for failed events
   - Kafka will retry event with exponential backoff

2. **Enhanced Error Context**
   - Include dataset, branch, commit ID in exception message
   - Log full stack trace for debugging
   - Include patch summary (add/delete counts) if available

3. **Kafka Retry Configuration**
   - Max retries: 10 (configurable)
   - Initial backoff: 1 second
   - Max backoff: 60 seconds
   - Exponential multiplier: 2.0

4. **Dead Letter Queue (DLQ)**
   - After max retries exceeded, send to DLQ topic
   - DLQ topic: `vc.{dataset}.events.dlq`
   - Include error metadata (exception message, stack trace, retry count)

5. **Circuit Breaker (Future Enhancement)**
   - Stop projector after N consecutive failures
   - Require manual intervention to resume
   - Prevents infinite retry loops

### Non-Functional Requirements

1. **Data Integrity**
   - Materialized views MUST match commit history at all times
   - No silent corruption tolerated
   - Eventual consistency guarantee preserved

2. **Observability**
   - Metrics: `chucc.projection.errors.total` - Counter for projection failures
   - Metrics: `chucc.projection.retries.total` - Counter for Kafka retries
   - Logs: ERROR level for immediate alerting
   - Alerts: Trigger PagerDuty/email on projection errors

3. **Operational Recovery**
   - Manual rebuild endpoint available ([Task 04](./04-add-monitoring-and-recovery.md))
   - Health check endpoint shows projector status
   - Clear error messages for operators

---

## Implementation Steps

### Step 1: Remove Exception Swallowing in ReadModelProjector

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Locate method:** `handleCommitCreated()` (around line 248)

**Current code:**
```java
private void handleCommitCreated(CommitCreatedEvent event) {
  logger.debug("Processing CommitCreatedEvent: commit={}, dataset={}",
      event.commitId(), event.dataset());

  // Save commit metadata
  Commit commit = new Commit(
      new CommitId(event.commitId()),
      event.parentIds().stream().map(CommitId::new).toList(),
      event.author(),
      event.message(),
      event.timestamp(),
      event.patchSize()
  );
  commitRepository.save(event.dataset(), commit);

  // Save patch
  RDFPatch patch = RDFPatchOps.read(new StringReader(event.patchString()));
  commitRepository.savePatch(event.dataset(), new CommitId(event.commitId()), patch);

  // Update branch HEAD if branch exists
  branchRepository.findByDatasetAndName(event.dataset(), event.branch())
      .ifPresent(branch -> {
        branch.setCommitId(new CommitId(event.commitId()));
        branchRepository.save(event.dataset(), branch);
      });

  // ❌ PROBLEM: Exception swallowed here
  try {
    materializedBranchRepo.applyPatchToBranch(event.dataset(), event.branch(), patch);
  } catch (Exception e) {
    logger.error("Failed to apply patch to materialized view for commit {}", event.commitId(), e);
    // Offset is committed, graph now corrupt!
  }

  // Notify DatasetService for cache management
  datasetService.handleCommitCreated(event.dataset(), new CommitId(event.commitId()));
}
```

**NEW code (fail-fast):**
```java
private void handleCommitCreated(CommitCreatedEvent event) {
  logger.debug("Processing CommitCreatedEvent: commit={}, dataset={}",
      event.commitId(), event.dataset());

  try {
    // Save commit metadata
    Commit commit = new Commit(
        new CommitId(event.commitId()),
        event.parentIds().stream().map(CommitId::new).toList(),
        event.author(),
        event.message(),
        event.timestamp(),
        event.patchSize()
    );
    commitRepository.save(event.dataset(), commit);

    // Save patch
    RDFPatch patch = RDFPatchOps.read(new StringReader(event.patchString()));
    commitRepository.savePatch(event.dataset(), new CommitId(event.commitId()), patch);

    // Update branch HEAD if branch exists
    branchRepository.findByDatasetAndName(event.dataset(), event.branch())
        .ifPresent(branch -> {
          branch.setCommitId(new CommitId(event.commitId()));
          branchRepository.save(event.dataset(), branch);
        });

    // ✅ NEW: Apply patch to materialized view (fail-fast)
    materializedBranchRepo.applyPatchToBranch(event.dataset(), event.branch(), patch);

    // Record success metric
    meterRegistry.counter("chucc.projection.success.total",
        "event_type", "commit_created",
        "dataset", event.dataset()
    ).increment();

    // Notify DatasetService for cache management
    datasetService.handleCommitCreated(event.dataset(), new CommitId(event.commitId()));

  } catch (Exception e) {
    // Record error metric
    meterRegistry.counter("chucc.projection.errors.total",
        "event_type", "commit_created",
        "dataset", event.dataset(),
        "error_type", e.getClass().getSimpleName()
    ).increment();

    // Enhanced error message with context
    String errorMsg = String.format(
        "CRITICAL: Failed to project CommitCreatedEvent for dataset=%s, branch=%s, commit=%s. " +
        "Materialized view may be inconsistent. Event will be retried by Kafka.",
        event.dataset(), event.branch(), event.commitId()
    );

    logger.error(errorMsg, e);

    // ✅ RE-THROW: Kafka will NOT commit offset, will retry event
    throw new ProjectionException(errorMsg, e);
  }
}
```

**Key changes:**
1. ✅ Wrap entire method in try-catch
2. ✅ Record metrics (success and failure counters)
3. ✅ Enhanced error message with context
4. ✅ **Re-throw exception** - This is the critical change!

### Step 2: Create Custom Exception for Projection Failures

**File:** `src/main/java/org/chucc/vcserver/exception/ProjectionException.java`

**Check if exists:**
```bash
grep -r "class ProjectionException" src/
```

If doesn't exist, create:

```java
package org.chucc.vcserver.exception;

/**
 * Exception thrown when event projection fails.
 *
 * <p>This exception indicates a critical failure in the read model projection
 * process. When thrown, Kafka will NOT commit the consumer offset, triggering
 * automatic retry with exponential backoff.
 *
 * <p>This fail-fast approach ensures materialized views remain consistent
 * with the event log, at the cost of availability (projector blocks until
 * error resolved).
 */
public class ProjectionException extends RuntimeException {

  /**
   * Constructs exception with message.
   *
   * @param message error message
   */
  public ProjectionException(String message) {
    super(message);
  }

  /**
   * Constructs exception with message and cause.
   *
   * @param message error message
   * @param cause underlying cause
   */
  public ProjectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

### Step 3: Update All Projection Handlers

Apply same fail-fast pattern to other event handlers:

**Events to update:**
1. `BranchCreatedEvent` (line ~326)
2. `BranchResetEvent` (line ~371)
3. `BranchDeletedEvent` (line ~409)
4. `BranchRebasedEvent`
5. `RevertCreatedEvent`
6. `CherryPickedEvent`
7. `CommitsSquashedEvent`
8. `SnapshotCreatedEvent`
9. `DatasetDeletedEvent` (line ~463)

**Pattern to apply:**
```java
private void handleEventType(EventType event) {
  logger.debug("Processing {}: dataset={}", event.getClass().getSimpleName(), event.dataset());

  try {
    // ... event projection logic ...

    // Record success
    meterRegistry.counter("chucc.projection.success.total",
        "event_type", event.getClass().getSimpleName(),
        "dataset", event.dataset()
    ).increment();

  } catch (Exception e) {
    // Record error
    meterRegistry.counter("chucc.projection.errors.total",
        "event_type", event.getClass().getSimpleName(),
        "dataset", event.dataset(),
        "error_type", e.getClass().getSimpleName()
    ).increment();

    // Enhanced error message
    String errorMsg = String.format(
        "CRITICAL: Failed to project %s for dataset=%s. Event will be retried by Kafka.",
        event.getClass().getSimpleName(), event.dataset()
    );

    logger.error(errorMsg, e);

    // Re-throw (fail-fast)
    throw new ProjectionException(errorMsg, e);
  }
}
```

### Step 4: Configure Kafka Retry Policy

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`

**Add error handling configuration:**
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent>
    kafkaListenerContainerFactory(
        ConsumerFactory<String, VersionControlEvent> consumerFactory,
        KafkaProperties kafkaProperties) {

  ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
  factory.setConsumerFactory(consumerFactory);
  factory.setConcurrency(1);  // Single consumer (no parallelism yet)

  // ✅ NEW: Configure error handler with retry and DLQ
  DefaultErrorHandler errorHandler = new DefaultErrorHandler(
      new DeadLetterPublishingRecoverer(kafkaTemplate,
          (record, ex) -> {
            // Send to DLQ topic: vc.{dataset}.events.dlq
            String topic = record.topic() + ".dlq";
            return new TopicPartition(topic, record.partition());
          }),
      new FixedBackOff(1000L, 10L)  // 1 second interval, 10 retries
  );

  // Retry on all exceptions (default behavior)
  errorHandler.addNotRetryableExceptions(/* none - retry everything */);

  // Log retry attempts
  errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
    logger.warn("Retrying event projection (attempt {}/10): topic={}, partition={}, offset={}",
        deliveryAttempt, record.topic(), record.partition(), record.offset());

    // Record retry metric
    meterRegistry.counter("chucc.projection.retries.total",
        "topic", record.topic(),
        "attempt", String.valueOf(deliveryAttempt)
    ).increment();
  });

  factory.setCommonErrorHandler(errorHandler);

  return factory;
}
```

**Add properties:**

**File:** `src/main/resources/application.yml`

```yaml
spring:
  kafka:
    consumer:
      # Retry configuration
      max-poll-records: 100  # Batch size
      enable-auto-commit: false  # Manual commit only on success

chucc:
  projection:
    retry:
      max-attempts: 10
      initial-interval: 1000  # 1 second
      multiplier: 2.0
      max-interval: 60000  # 60 seconds
```

**Create configuration properties:**

**File:** `src/main/java/org/chucc/vcserver/config/ProjectionRetryProperties.java` (NEW)

```java
package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for projection retry policy.
 */
@Configuration
@ConfigurationProperties(prefix = "chucc.projection.retry")
public class ProjectionRetryProperties {

  private int maxAttempts = 10;
  private long initialInterval = 1000;
  private double multiplier = 2.0;
  private long maxInterval = 60000;

  // Getters and setters

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getInitialInterval() {
    return initialInterval;
  }

  public void setInitialInterval(long initialInterval) {
    this.initialInterval = initialInterval;
  }

  public double getMultiplier() {
    return multiplier;
  }

  public void setMultiplier(double multiplier) {
    this.multiplier = multiplier;
  }

  public long getMaxInterval() {
    return maxInterval;
  }

  public void setMaxInterval(long maxInterval) {
    this.maxInterval = maxInterval;
  }
}
```

### Step 5: Add DLQ Topic Creation

**File:** `src/main/java/org/chucc/vcserver/service/DatasetCreationService.java`

**Update `createDataset()` method:**
```java
public void createDataset(String name) {
  // ... existing topic creation ...

  // ✅ NEW: Create DLQ topic
  String dlqTopicName = topicTemplate.replace("{dataset}", name) + ".dlq";
  createTopicIfNotExists(dlqTopicName);

  logger.info("Created DLQ topic: {}", dlqTopicName);
}
```

### Step 6: Update Health Indicator

**File:** `src/main/java/org/chucc/vcserver/health/MaterializedViewsHealthIndicator.java`

**Add projection error tracking:**
```java
@Override
public Health health() {
  try {
    int graphCount = materializedBranchRepo.getGraphCount();

    Map<String, Object> details = new HashMap<>();
    details.put("graphCount", graphCount);

    // ✅ NEW: Check for recent projection errors
    long errorCount = meterRegistry.counter("chucc.projection.errors.total").count();
    details.put("projectionErrors", errorCount);

    if (errorCount > 0) {
      details.put("status", "Projection errors detected - check logs");
      return Health.down()
          .withDetails(details)
          .build();
    }

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
```

### Step 7: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ProjectionErrorHandlingIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for fail-fast projection error handling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = {
    "projector.kafka-listener.enabled=true",
    "chucc.projection.retry.max-attempts=3"  // Low for testing
})
class ProjectionErrorHandlingIT extends IntegrationTestFixture {

  @SpyBean
  private MaterializedBranchRepository materializedBranchRepo;

  @Autowired
  private MeterRegistry meterRegistry;

  @Test
  void projectionError_shouldTriggerRetry() throws Exception {
    String dataset = "test-dataset";
    String branch = "main";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Simulate patch application failure (once, then succeed)
    doThrow(new RuntimeException("Simulated patch error"))
        .doCallRealMethod()  // Second attempt succeeds
        .when(materializedBranchRepo)
        .applyPatchToBranch(anyString(), anyString(), any(RDFPatch.class));

    // Trigger commit creation
    putGraph(dataset, "http://example.org/graph", branch,
        "@prefix ex: <http://example.org/> . ex:s ex:p \"value\" .");

    // Wait for retry and eventual success
    await().atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofSeconds(1))
        .untilAsserted(() -> {
          // Verify retry metric incremented
          double retries = meterRegistry.counter("chucc.projection.retries.total").count();
          assertThat(retries).isGreaterThan(0);

          // Verify eventual success
          double errors = meterRegistry.counter("chucc.projection.errors.total").count();
          double successes = meterRegistry.counter("chucc.projection.success.total").count();
          assertThat(successes).isGreaterThan(0);
        });
  }

  @Test
  void projectionError_shouldUpdateHealthCheck() throws Exception {
    String dataset = "test-dataset";
    String branch = "main";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Simulate persistent failure
    doThrow(new RuntimeException("Persistent error"))
        .when(materializedBranchRepo)
        .applyPatchToBranch(anyString(), anyString(), any(RDFPatch.class));

    // Trigger commit creation
    putGraph(dataset, "http://example.org/graph", branch,
        "@prefix ex: <http://example.org/> . ex:s ex:p \"value\" .");

    // Wait for error to be recorded
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          double errors = meterRegistry.counter("chucc.projection.errors.total").count();
          assertThat(errors).isGreaterThan(0);
        });

    // Check health endpoint reflects error
    ResponseEntity<String> health = restTemplate.getForEntity(
        "/actuator/health/materializedViews",
        String.class
    );

    assertThat(health.getBody()).contains("DOWN");
    assertThat(health.getBody()).contains("projectionErrors");
  }
}
```

### Step 8: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=ProjectionErrorHandlingIT 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Projection errors re-thrown (fail-fast)
- ✅ Kafka retry configured with exponential backoff
- ✅ DLQ topic created for failed events
- ✅ Metrics track projection errors and retries
- ✅ Health check shows projector status
- ✅ Integration tests verify retry behavior
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`
- ✅ Documentation updated

---

## Testing Strategy

### Unit Tests

**ReadModelProjectorTest:**
- Verify exception rethrown on projection failure
- Verify metrics recorded on success/failure

### Integration Tests (Projector Enabled)

**ProjectionErrorHandlingIT:**
- Simulate patch application failure
- Verify Kafka retry mechanism triggered
- Verify eventual success after transient error
- Verify health check reflects error state

---

## Files to Create

### Production Code
- `src/main/java/org/chucc/vcserver/exception/ProjectionException.java` (NEW, if doesn't exist)
- `src/main/java/org/chucc/vcserver/config/ProjectionRetryProperties.java` (NEW)

### Files to Modify
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java` (remove exception swallowing, add fail-fast)
- `src/main/java/org/chucc/vcserver/config/KafkaConfig.java` (add error handler)
- `src/main/java/org/chucc/vcserver/service/DatasetCreationService.java` (create DLQ topics)
- `src/main/java/org/chucc/vcserver/health/MaterializedViewsHealthIndicator.java` (add error tracking)
- `src/main/resources/application.yml` (add retry config)

### Test Code
- `src/test/java/org/chucc/vcserver/integration/ProjectionErrorHandlingIT.java` (NEW)

**Total:** 2 new production files, 5 modified files, 1 new integration test

---

## Operational Runbook

### Scenario 1: Transient Projection Error

**Symptoms:** Logs show "Retrying event projection" messages

**Cause:** Temporary issue (network glitch, resource contention)

**Action:** None required - Kafka will retry automatically

**Resolution:** Event eventually succeeds after retry

### Scenario 2: Persistent Projection Error

**Symptoms:**
- Logs show repeated retry failures
- Health check returns DOWN
- Metric `chucc.projection.errors.total` increases

**Causes:**
- Bad event data (malformed patch)
- Bug in projector code
- Resource exhaustion (OOM, disk full)

**Actions:**
1. Check logs for root cause
2. If bad event data: Manually skip or fix event in Kafka
3. If bug: Deploy hotfix
4. If resource issue: Scale up or clear space
5. Manual rebuild if corruption occurred

### Scenario 3: Event in DLQ

**Symptoms:** Events appear in `vc.{dataset}.events.dlq` topic

**Cause:** Max retries exceeded (10 attempts failed)

**Actions:**
1. Inspect DLQ event metadata (exception, stack trace)
2. Fix underlying issue
3. Replay event from DLQ to main topic
4. Verify successful projection

**DLQ Inspection:**
```bash
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic vc.mydata.events.dlq \
  --from-beginning \
  --property print.headers=true
```

---

## Metrics Dashboard

Using Prometheus + Grafana:

```promql
# Projection error rate (per minute)
rate(chucc_projection_errors_total[5m])

# Retry rate (should be low)
rate(chucc_projection_retries_total[5m])

# Success rate (should be high)
rate(chucc_projection_success_total[5m])

# DLQ event count (should be zero)
kafka_consumer_records_consumed_total{topic=~".*\\.dlq"}
```

**Alert rules:**
```yaml
- alert: ProjectionErrorsDetected
  expr: rate(chucc_projection_errors_total[5m]) > 0
  for: 1m
  annotations:
    summary: "Projection errors detected in dataset {{ $labels.dataset }}"
    severity: critical

- alert: EventsInDLQ
  expr: kafka_consumer_records_consumed_total{topic=~".*\\.dlq"} > 0
  for: 5m
  annotations:
    summary: "Events in DLQ topic {{ $labels.topic }}"
    severity: warning
```

---

## Consistency Guarantees

### Before This Task (Silent Failure)

- ❌ Materialized view may diverge from commit history
- ❌ Queries return stale/incorrect data
- ❌ Detection delayed (5-minute health check)
- ❌ Manual rebuild required

### After This Task (Fail-Fast)

- ✅ Materialized view ALWAYS matches commit history
- ✅ Failures detected immediately
- ✅ Automatic retry with exponential backoff
- ✅ System refuses to serve incorrect data (blocks on error)

### CAP Theorem Analysis

**Before:** AP system (Availability + Partition tolerance)
- Continues serving queries even when projector fails
- Data may be inconsistent

**After:** CP system (Consistency + Partition tolerance)
- Projector blocks on failures (reduced availability)
- Data always consistent

**Why CP is correct for event sourcing:**
- Event log is source of truth
- Materialized views are derived state
- Consistency is non-negotiable
- Availability can be restored by fixing errors

---

## References

- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - Current implementation
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Kafka Consumer Retry](https://www.confluent.io/blog/error-handling-patterns-in-kafka/)
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)

---

## Next Steps

After completing this task:
1. ✅ Delete this task file
2. ✅ Update `.tasks/README.md`
3. ✅ Monitor production metrics for projection errors
4. 🎉 Celebrate improved data consistency!

---

**Estimated Completion Time:** 2-3 hours
**Complexity:** Low-Medium (straightforward refactoring with well-established pattern)
**Impact:** High (ensures data integrity and consistency)
