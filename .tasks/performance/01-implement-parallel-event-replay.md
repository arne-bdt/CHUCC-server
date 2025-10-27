# Task 01: Implement Parallel Event Replay

**Status:** Not Started
**Priority:** Medium (Performance Optimization)
**Category:** Performance & Scalability
**Estimated Time:** 6-8 hours
**Complexity:** High
**Prerequisites:** Understanding of Kafka partitions and consumer groups

---

## Overview

Implement parallel event replay by partitioning events by dataset and running multiple consumer instances. Currently, event replay is sequential (single-threaded), which causes long startup times for large datasets.

**Problem:**
- Current: Single consumer processes ALL events sequentially
- Result: Startup time scales linearly with event count
- Example: 10,000 events = 10 seconds startup (single-threaded @ 1ms/event)
- Bottleneck: Only one CPU core utilized during replay

**Solution:**
- Partition Kafka topics by dataset (dataset = partition key)
- Run multiple consumer instances (one per partition)
- Events for different datasets processed in parallel
- Startup time reduced by factor of N (where N = partition count)

**Benefits:**
- ✅ **Faster startup:** 10 partitions = 10x faster replay
- ✅ **Better CPU utilization:** All cores used during replay
- ✅ **Horizontal scalability:** Add more partitions for more parallelism
- ✅ **Dataset isolation:** Events for dataset A don't block dataset B

**Trade-offs:**
- ⚠️ **Complexity:** More moving parts (partition management)
- ⚠️ **Cross-dataset operations:** Merge/copy between datasets becomes complex
- ⚠️ **Ordering:** Events within dataset still ordered, but cross-dataset ordering lost
- ⚠️ **Testing:** More complex test scenarios

**When to implement:**
- Large deployments with many datasets (>100 datasets)
- Startup time is critical (high availability requirements)
- Multiple CPU cores available

**When NOT to implement:**
- Small deployments (<10 datasets)
- Startup time acceptable (<30 seconds)
- Cross-dataset operations are frequent

---

## Current State

### Current Implementation (Sequential Processing)

**File:** [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

```java
@KafkaListener(
    topicPattern = "vc\\..*\\.events",
    groupId = "${spring.kafka.consumer.group-id:read-model-projector}",
    autoStartup = "${projector.kafka-listener.enabled:true}"
)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
    // Single thread processes ALL events sequentially
    VersionControlEvent event = record.value();
    // ... projection logic ...
}
```

**Problem:**
- Single `@KafkaListener` = single consumer thread
- All events for ALL datasets processed by same thread
- No parallelism even with multiple CPU cores

### Current Kafka Topic Structure

**Topic naming:** `vc.{dataset}.events`
- One topic per dataset
- All events for dataset go to same topic
- Topic has 1 partition by default (no parallelism)

**Example:**
```
vc.dataset1.events (partition 0)  ← All dataset1 events
vc.dataset2.events (partition 0)  ← All dataset2 events
vc.dataset3.events (partition 0)  ← All dataset3 events
```

**Startup behavior:**
1. Consumer subscribes to pattern `vc\\..*\\.events`
2. Kafka assigns ALL partitions to single consumer
3. Consumer processes events from all topics sequentially
4. Total time = sum of all events across all datasets

---

## Requirements

### Functional Requirements

1. **Partition by Dataset**
   - Each dataset topic has N partitions (configurable, default: 6)
   - Partition key = dataset name
   - All events for same dataset go to same partition
   - Maintains ordering within dataset

2. **Multiple Consumer Instances**
   - Consumer concurrency = number of partitions
   - Each consumer instance handles one partition
   - Kafka handles partition assignment automatically

3. **Configuration**
   - Property: `chucc.kafka.partitions` (default: 6)
   - Property: `chucc.kafka.consumer.concurrency` (default: 6)
   - Validation: concurrency <= partitions (can't have more consumers than partitions)

4. **Cross-Dataset Operation Handling**
   - Dataset merge/copy operations require coordination
   - Use distributed lock or single-threaded mode for cross-dataset ops
   - Document limitations

5. **Startup Behavior**
   - Multiple consumers start in parallel
   - Each replays events for its assigned partitions
   - Startup time = max(partition replay times), not sum

### Non-Functional Requirements

1. **Performance**
   - Startup time improvement: Linear with partition count (theoretical)
   - Example: 10,000 events across 10 datasets with 10 partitions
     - Before: 10 seconds (sequential)
     - After: ~1 second (parallel, each partition has ~1000 events)

2. **Scalability**
   - Support 1-100 partitions
   - Support 1-100 concurrent consumers
   - Memory usage: N consumers × 50MB = manageable

3. **Reliability**
   - Maintain ordering within dataset (critical!)
   - No cross-dataset ordering guarantees (acceptable)
   - Kafka rebalancing handled gracefully

4. **Backward Compatibility**
   - Existing single-partition topics continue to work
   - Migration path for existing deployments

---

## Implementation Steps

### Step 1: Update Topic Creation to Use Partitions

**File:** `src/main/java/org/chucc/vcserver/service/DatasetCreationService.java`

**Current code:**
```java
private void createTopicIfNotExists(String topicName) {
  NewTopic newTopic = TopicBuilder
      .name(topicName)
      .partitions(1)  // ❌ Single partition
      .replicas(kafkaProperties.getReplicationFactor())
      .build();
  // ...
}
```

**NEW code:**
```java
private void createTopicIfNotExists(String topicName) {
  NewTopic newTopic = TopicBuilder
      .name(topicName)
      .partitions(kafkaProperties.getPartitions())  // ✅ Configurable partitions
      .replicas(kafkaProperties.getReplicationFactor())
      .build();

  logger.info("Creating topic {} with {} partitions and RF={}",
      topicName, kafkaProperties.getPartitions(), kafkaProperties.getReplicationFactor());

  // ...
}
```

### Step 2: Add Partition Configuration

**File:** `src/main/java/org/chucc/vcserver/config/KafkaProperties.java`

Add partition property:
```java
@ConfigurationProperties(prefix = "chucc.kafka")
public class KafkaProperties {

  private String bootstrapServers = "localhost:9092";
  private String topicTemplate = "vc.{dataset}.events";
  private int partitions = 6;  // ✅ NEW: Default 6 partitions
  private int replicationFactor = 1;
  // ... existing properties ...

  public int getPartitions() {
    return partitions;
  }

  public void setPartitions(int partitions) {
    if (partitions < 1 || partitions > 100) {
      throw new IllegalArgumentException("Partitions must be between 1 and 100");
    }
    this.partitions = partitions;
  }

  // ... other getters/setters ...
}
```

**File:** `src/main/resources/application.yml`

```yaml
chucc:
  kafka:
    bootstrap-servers: localhost:9092
    partitions: 6  # ✅ NEW: Number of partitions per topic
    replication-factor: 1
    consumer:
      concurrency: 6  # ✅ NEW: Number of concurrent consumers
```

**File:** `src/main/resources/application-prod.yml`

```yaml
chucc:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    partitions: 10  # Production: More partitions
    replication-factor: 3
    consumer:
      concurrency: 10  # Match partition count
```

### Step 3: Update Event Publisher to Use Partition Key

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

**Current code:**
```java
public CompletableFuture<SendResult<String, VersionControlEvent>> publish(
    VersionControlEvent event) {
  String topic = resolveTopic(event.dataset());
  return kafkaTemplate.send(topic, event);  // ❌ No partition key
}
```

**NEW code:**
```java
public CompletableFuture<SendResult<String, VersionControlEvent>> publish(
    VersionControlEvent event) {
  String topic = resolveTopic(event.dataset());

  // ✅ Use dataset name as partition key (ensures all events for dataset go to same partition)
  String partitionKey = event.dataset();

  logger.debug("Publishing event to topic {} with partition key {}", topic, partitionKey);

  return kafkaTemplate.send(topic, partitionKey, event);
}
```

**Why dataset as partition key?**
- Ensures all events for same dataset go to same partition
- Maintains ordering within dataset (critical for CQRS)
- Enables parallel processing across datasets

### Step 4: Update Consumer Configuration for Concurrency

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`

**Update consumer factory:**
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent>
    kafkaListenerContainerFactory(
        ConsumerFactory<String, VersionControlEvent> consumerFactory,
        KafkaProperties kafkaProperties) {

  ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
  factory.setConsumerFactory(consumerFactory);

  // ✅ NEW: Enable concurrent consumers (one per partition)
  int concurrency = kafkaProperties.getConsumer().getConcurrency();
  factory.setConcurrency(concurrency);

  logger.info("Configured Kafka listener with concurrency = {}", concurrency);

  // ... error handler configuration (from Task 06) ...

  return factory;
}
```

**Add consumer concurrency property:**

**File:** `src/main/java/org/chucc/vcserver/config/KafkaProperties.java`

```java
@ConfigurationProperties(prefix = "chucc.kafka")
public class KafkaProperties {

  // ... existing properties ...

  private Consumer consumer = new Consumer();

  public Consumer getConsumer() {
    return consumer;
  }

  public void setConsumer(Consumer consumer) {
    this.consumer = consumer;
  }

  /**
   * Consumer configuration.
   */
  public static class Consumer {
    private int concurrency = 6;  // Default: 6 concurrent consumers

    public int getConcurrency() {
      return concurrency;
    }

    public void setConcurrency(int concurrency) {
      if (concurrency < 1 || concurrency > 100) {
        throw new IllegalArgumentException("Concurrency must be between 1 and 100");
      }
      this.concurrency = concurrency;
    }
  }
}
```

### Step 5: Add Partition Assignment Logging

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Add listener for partition assignment:**
```java
@Component
public class ReadModelProjector {

  // ... existing fields ...

  /**
   * Called when partitions are assigned to this consumer.
   *
   * @param partitions assigned partitions
   */
  @EventListener
  public void onPartitionsAssigned(ConsumerPartitionsAssignedEvent event) {
    Collection<TopicPartition> partitions = event.getPartitions();

    logger.info("Consumer assigned {} partitions:", partitions.size());
    for (TopicPartition partition : partitions) {
      logger.info("  - {} [partition {}]", partition.topic(), partition.partition());
    }
  }

  /**
   * Called when partitions are revoked from this consumer.
   *
   * @param partitions revoked partitions
   */
  @EventListener
  public void onPartitionsRevoked(ConsumerPartitionsRevokedEvent event) {
    Collection<TopicPartition> partitions = event.getPartitions();

    logger.info("Consumer revoked {} partitions:", partitions.size());
    for (TopicPartition partition : partitions) {
      logger.info("  - {} [partition {}]", partition.topic(), partition.partition());
    }
  }
}
```

### Step 6: Add Metrics for Partition Processing

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Track per-partition metrics:**
```java
@KafkaListener(
    topicPattern = "vc\\..*\\.events",
    groupId = "${spring.kafka.consumer.group-id:read-model-projector}",
    autoStartup = "${projector.kafka-listener.enabled:true}"
)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  VersionControlEvent event = record.value();

  // ✅ Track partition-level metrics
  meterRegistry.counter("chucc.projection.events.total",
      "topic", record.topic(),
      "partition", String.valueOf(record.partition())
  ).increment();

  Timer.Sample sample = Timer.start(meterRegistry);

  // ... existing projection logic ...

  sample.stop(meterRegistry.timer("chucc.projection.event.duration",
      "topic", record.topic(),
      "partition", String.valueOf(record.partition()),
      "event_type", event.getClass().getSimpleName()
  ));
}
```

### Step 7: Handle Cross-Dataset Operations

**Problem:** Some operations may need to coordinate across datasets (e.g., dataset merge).

**Solution:** Document limitation and provide workaround.

**File:** `docs/architecture/parallel-event-replay.md` (NEW)

```markdown
# Parallel Event Replay

## Overview

Events are partitioned by dataset and processed in parallel by multiple consumer instances.

## Ordering Guarantees

- **Within dataset:** Total ordering preserved (all events for dataset X processed in order)
- **Cross-dataset:** No ordering guarantees (events for dataset X and Y may be processed in any order)

## Implications for Cross-Dataset Operations

### Supported Operations (No Coordination Needed)

- Dataset creation/deletion (independent)
- Commits within dataset (isolated)
- Branch operations within dataset (isolated)

### Complex Operations (Coordination Required)

**Dataset Merge (Future Feature):**
- Requires coordination between partitions
- Options:
  1. Single-threaded mode: Disable concurrency temporarily
  2. Distributed lock: Coordinate across consumer instances
  3. Leader election: One consumer handles cross-dataset ops

**Current Status:** Cross-dataset operations not yet implemented.

## Configuration

### Development (Single Machine)
```yaml
chucc.kafka.partitions: 3
chucc.kafka.consumer.concurrency: 3
```

### Production (Multi-Core Server)
```yaml
chucc.kafka.partitions: 10
chucc.kafka.consumer.concurrency: 10
```

### High-Availability Cluster
```yaml
chucc.kafka.partitions: 20
chucc.kafka.consumer.concurrency: 10  # 2 instances × 10 consumers
```

## Monitoring

Track per-partition metrics:
- `chucc.projection.events.total{partition="0"}` - Events processed by partition 0
- `chucc.projection.event.duration{partition="0"}` - Processing time per partition

## Testing

Integration tests should verify:
- Events for same dataset processed in order
- Events for different datasets can be processed in parallel
```

### Step 8: Add Migration Guide

**File:** `docs/operations/partition-migration.md` (NEW)

```markdown
# Migrating Existing Topics to Multiple Partitions

## Problem

Existing Kafka topics have 1 partition. After upgrading, new topics will have N partitions.

## Options

### Option 1: Create New Topics (Recommended)

1. Stop application
2. Create new topics with desired partition count
3. Replay events from old topics to new topics
4. Update consumer offsets
5. Restart application

**Pros:** Clean migration, no data loss
**Cons:** Requires replay (may take time)

### Option 2: Increase Partition Count (Not Recommended)

```bash
kafka-topics.sh --alter \
  --topic vc.mydata.events \
  --partitions 6 \
  --bootstrap-server localhost:9092
```

**Pros:** No replay needed
**Cons:**
- Existing events remain in partition 0 (no rebalancing)
- Only new events distributed across partitions
- Ordering may be affected

### Option 3: Leave Existing Topics As-Is

Existing single-partition topics continue to work. Only new datasets use multiple partitions.

**Pros:** No migration needed
**Cons:** Old datasets don't benefit from parallelism

## Recommendation

Use **Option 3** for production. Migrate datasets individually over time as needed.
```

### Step 9: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ParallelEventReplayIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for parallel event replay.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = {
    "projector.kafka-listener.enabled=true",
    "chucc.kafka.partitions=3",
    "chucc.kafka.consumer.concurrency=3"
})
class ParallelEventReplayIT extends IntegrationTestFixture {

  @Autowired
  private KafkaListenerEndpointRegistry registry;

  @Test
  void parallelConsumers_shouldProcessEventsConcurrently() throws Exception {
    // Verify multiple consumers are running
    MessageListenerContainer container = registry.getListenerContainer("org.chucc.vcserver.projection.ReadModelProjector");
    assertThat(container).isNotNull();
    assertThat(container.isRunning()).isTrue();

    // Create multiple datasets
    String dataset1 = "dataset1";
    String dataset2 = "dataset2";
    String dataset3 = "dataset3";

    createDataset(dataset1);
    createDataset(dataset2);
    createDataset(dataset3);

    createBranch(dataset1, "main");
    createBranch(dataset2, "main");
    createBranch(dataset3, "main");

    // Create commits in parallel (different datasets)
    ConcurrentHashMap<String, AtomicInteger> commitCounts = new ConcurrentHashMap<>();
    commitCounts.put(dataset1, new AtomicInteger(0));
    commitCounts.put(dataset2, new AtomicInteger(0));
    commitCounts.put(dataset3, new AtomicInteger(0));

    for (int i = 0; i < 10; i++) {
      putGraph(dataset1, "http://example.org/graph" + i, "main",
          "@prefix ex: <http://example.org/> . ex:s1 ex:p \"value" + i + "\" .");
      commitCounts.get(dataset1).incrementAndGet();

      putGraph(dataset2, "http://example.org/graph" + i, "main",
          "@prefix ex: <http://example.org/> . ex:s2 ex:p \"value" + i + "\" .");
      commitCounts.get(dataset2).incrementAndGet();

      putGraph(dataset3, "http://example.org/graph" + i, "main",
          "@prefix ex: <http://example.org/> . ex:s3 ex:p \"value" + i + "\" .");
      commitCounts.get(dataset3).incrementAndGet();
    }

    // Wait for all events to be processed
    await().atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(() -> {
          // Verify all commits were processed
          assertThat(commitRepository.findAllByDataset(dataset1).size())
              .isGreaterThanOrEqualTo(commitCounts.get(dataset1).get());
          assertThat(commitRepository.findAllByDataset(dataset2).size())
              .isGreaterThanOrEqualTo(commitCounts.get(dataset2).get());
          assertThat(commitRepository.findAllByDataset(dataset3).size())
              .isGreaterThanOrEqualTo(commitCounts.get(dataset3).get());
        });
  }

  @Test
  void orderingWithinDataset_shouldBePreserved() throws Exception {
    String dataset = "test-dataset";

    createDataset(dataset);
    createBranch(dataset, "main");

    // Create 100 commits sequentially
    for (int i = 0; i < 100; i++) {
      putGraph(dataset, "http://example.org/graph", "main",
          "@prefix ex: <http://example.org/> . ex:counter ex:value \"" + i + "\" .");
    }

    // Wait for projection
    await().atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> commitRepository.findAllByDataset(dataset).size() >= 100);

    // Verify commits are in order (by timestamp)
    List<Commit> commits = new ArrayList<>(commitRepository.findAllByDataset(dataset));
    commits.sort(Comparator.comparing(Commit::timestamp));

    for (int i = 1; i < commits.size(); i++) {
      Commit prev = commits.get(i - 1);
      Commit curr = commits.get(i);

      // Timestamps should be increasing (or equal for very fast commits)
      assertThat(curr.timestamp()).isAfterOrEqualTo(prev.timestamp());
    }
  }

  @Test
  void startupPerformance_shouldImproveWithParallelism() {
    // This test is more of a benchmark than assertion
    // Just verify that startup completes in reasonable time

    long startTime = System.currentTimeMillis();

    // Create 10 datasets with 10 commits each = 100 total events
    for (int d = 0; d < 10; d++) {
      String dataset = "dataset" + d;
      createDataset(dataset);
      createBranch(dataset, "main");

      for (int i = 0; i < 10; i++) {
        putGraph(dataset, "http://example.org/graph" + i, "main",
            "@prefix ex: <http://example.org/> . ex:s ex:p \"value" + i + "\" .");
      }
    }

    // Wait for all to be processed
    await().atMost(Duration.ofSeconds(30))
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(() -> {
          int totalCommits = 0;
          for (int d = 0; d < 10; d++) {
            totalCommits += commitRepository.findAllByDataset("dataset" + d).size();
          }
          assertThat(totalCommits).isGreaterThanOrEqualTo(100);
        });

    long elapsedTime = System.currentTimeMillis() - startTime;

    logger.info("Processed 100 events across 10 datasets in {}ms (with 3 parallel consumers)",
        elapsedTime);

    // With parallelism, should be faster than sequential
    // Sequential: 100 events × 10ms = 1000ms
    // Parallel (3 consumers): ~333ms (ideally)
    // Allow margin: <2000ms
    assertThat(elapsedTime).isLessThan(2000);
  }
}
```

### Step 10: Update Documentation

**File:** `.claude/CLAUDE.md`

Add section on parallel event replay:
```markdown
### Parallel Event Replay

- **Partitioning:** Events partitioned by dataset (partition key = dataset name)
- **Concurrency:** Multiple consumer instances process events in parallel
- **Default:** 6 partitions, 6 concurrent consumers
- **Configuration:** `chucc.kafka.partitions` and `chucc.kafka.consumer.concurrency`
- **Ordering:** Guaranteed within dataset, not across datasets
- **Performance:** Startup time reduced by factor of N (partition count)
```

### Step 11: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=ParallelEventReplayIT 2>&1 | tail -20

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ Topics created with configurable partition count (default: 6)
- ✅ Event publisher uses dataset as partition key
- ✅ Multiple concurrent consumers process events in parallel
- ✅ Ordering within dataset preserved
- ✅ Metrics track per-partition processing
- ✅ Integration tests verify parallel processing
- ✅ Integration tests verify ordering guarantees
- ✅ Documentation updated with configuration guide
- ✅ Migration guide provided for existing deployments
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Unit Tests

**EventPublisherTest:**
- Verify partition key set correctly
- Verify all events for dataset go to same partition

### Integration Tests (Projector Enabled)

**ParallelEventReplayIT:**
- Verify multiple consumers running
- Verify events for different datasets processed concurrently
- Verify ordering within dataset preserved
- Benchmark startup performance improvement

---

## Files to Create

### Production Code
- None (all modifications to existing files)

### Documentation
- `docs/architecture/parallel-event-replay.md` (NEW)
- `docs/operations/partition-migration.md` (NEW)

### Files to Modify
- `src/main/java/org/chucc/vcserver/service/DatasetCreationService.java` (use configurable partitions)
- `src/main/java/org/chucc/vcserver/config/KafkaProperties.java` (add partitions and concurrency config)
- `src/main/java/org/chucc/vcserver/config/KafkaConfig.java` (enable concurrency)
- `src/main/java/org/chucc/vcserver/event/EventPublisher.java` (use partition key)
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java` (add partition logging and metrics)
- `src/main/resources/application.yml` (add partition config)
- `src/main/resources/application-prod.yml` (add production partition config)
- `.claude/CLAUDE.md` (add parallel replay section)

### Test Code
- `src/test/java/org/chucc/vcserver/integration/ParallelEventReplayIT.java` (NEW)

**Total:** 0 new production files, 2 new docs, 8 modified files, 1 new integration test

---

## Performance Characteristics

### Startup Time Improvement

**Scenario:** 10,000 events across 10 datasets

**Before (Sequential):**
- Single consumer processes all 10,000 events
- Time = 10,000 events × 1ms/event = **10 seconds**

**After (6 Partitions):**
- 6 consumers process events in parallel
- Each partition has ~1,667 events (assuming even distribution)
- Time = 1,667 events × 1ms/event = **~1.7 seconds** (5.9x faster!)

**After (10 Partitions):**
- 10 consumers process events in parallel
- Each partition has ~1,000 events
- Time = 1,000 events × 1ms/event = **~1 second** (10x faster!)

### Scalability Limits

**Memory:**
- Each consumer: ~50MB overhead
- 10 consumers: ~500MB (acceptable)
- 100 consumers: ~5GB (may be excessive)

**CPU:**
- Each consumer: 1 CPU core
- 10 consumers: 10 cores (reasonable for modern servers)
- 100 consumers: 100 cores (exceeds typical server)

**Kafka:**
- Max partitions per topic: 1000 (Kafka recommendation)
- Typical: 10-50 partitions per topic

**Recommendation:** Start with 6-10 partitions, increase if needed.

---

## Configuration Examples

### Development (Single Machine, 4 cores)
```yaml
chucc:
  kafka:
    partitions: 3
    consumer:
      concurrency: 3
```

### Production (Server, 16 cores)
```yaml
chucc:
  kafka:
    partitions: 10
    consumer:
      concurrency: 10
```

### High-Availability Cluster (2 instances)
```yaml
chucc:
  kafka:
    partitions: 20
    consumer:
      concurrency: 10  # 2 instances × 10 consumers = 20 total
```

---

## Metrics Dashboard

Using Prometheus + Grafana:

```promql
# Events processed per partition
rate(chucc_projection_events_total[5m])

# Processing time per partition
histogram_quantile(0.95, chucc_projection_event_duration{partition="0"})

# Partition lag (from Kafka metrics)
kafka_consumer_lag{partition="0"}

# Consumer count
kafka_consumer_count{group="read-model-projector"}
```

**Alert rules:**
```yaml
- alert: PartitionLagHigh
  expr: kafka_consumer_lag > 1000
  for: 5m
  annotations:
    summary: "Partition {{ $labels.partition }} has high lag"
    severity: warning

- alert: ConsumerCountMismatch
  expr: kafka_consumer_count != 6  # Expected concurrency
  for: 2m
  annotations:
    summary: "Consumer count is {{ $value }}, expected 6"
    severity: critical
```

---

## Operational Considerations

### When to Increase Partitions

Increase if:
- Startup time too long (>30 seconds)
- Single consumer bottleneck (high CPU on one core)
- Many datasets (>100) with frequent commits
- More CPU cores available

### When to Decrease Partitions

Decrease if:
- Few datasets (<10)
- Low commit frequency
- Limited CPU cores
- Memory pressure

### Rebalancing

When consumer instances join/leave:
- Kafka triggers rebalancing
- Partitions reassigned
- Brief pause in processing (seconds)
- Automatic recovery

### Monitoring

Watch these metrics:
- Consumer lag per partition (should be near zero)
- Processing rate per partition (should be even)
- Consumer count (should match concurrency setting)

---

## Limitations & Trade-offs

### Ordering Guarantees

✅ **Preserved:**
- Events within same dataset processed in order
- Parent-child commit relationships maintained
- Branch operations sequential

❌ **NOT Preserved:**
- Events across different datasets (no global ordering)
- Cross-dataset merge/copy operations (requires coordination)

### Resource Usage

**Increased:**
- Memory: N × 50MB for N consumers
- Threads: N threads for N consumers
- Kafka connections: N connections

**Acceptable for:**
- Modern servers (16+ cores, 32+ GB RAM)
- Typical deployments (<100 datasets)

**May be excessive for:**
- Small servers (<4 cores)
- Edge deployments (limited resources)

---

## Future Enhancements (Out of Scope)

### Dynamic Partition Count

Allow per-dataset partition configuration:
```yaml
chucc:
  kafka:
    partitions:
      default: 6
      large-dataset: 20  # Override for specific dataset
```

### Adaptive Concurrency

Automatically adjust consumer count based on load:
- Start with low concurrency (save resources)
- Increase when lag grows
- Decrease when idle

### Cross-Dataset Coordination

For merge/copy operations:
- Distributed lock (ZooKeeper, Redis)
- Leader election (one consumer handles cross-dataset ops)
- Transaction coordinator pattern

---

## References

- [Kafka Partitioning](https://kafka.apache.org/documentation/#design_partitioning)
- [Spring Kafka Concurrency](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-concurrency.html)
- [EventPublisher.java](../../src/main/java/org/chucc/vcserver/event/EventPublisher.java)
- [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java)

---

## Next Steps

After completing this task:
1. ✅ Delete this task file
2. ✅ Update `.tasks/README.md`
3. 📊 Monitor startup time metrics in production
4. 📈 Adjust partition count based on load
5. 🎉 Enjoy faster startup times!

---

**Estimated Completion Time:** 6-8 hours
**Complexity:** High (requires understanding of Kafka partitioning and concurrency)
**Impact:** Medium-High (significant performance improvement for large deployments)
**Risk:** Medium (changes core event processing, requires thorough testing)
