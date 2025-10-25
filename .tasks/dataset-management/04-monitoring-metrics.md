# Task: Add Monitoring and Metrics for Dataset/Topic Management

**Status:** Not Started
**Priority:** Important
**Category:** Observability / Operations
**Estimated Time:** 3-4 hours

---

## Overview

Implement comprehensive monitoring and metrics for dataset and Kafka topic management:
- Dataset creation/deletion metrics
- Topic creation/deletion metrics
- Kafka health and connectivity metrics
- Partition and consumer lag metrics
- Alerting rules for operational issues

**Problem:** Without metrics, operators cannot detect issues like topic creation failures, consumer lag, or quota exhaustion.

---

## Current State

**Existing Metrics:**
- ✅ Basic event publishing metrics ([EventPublisher.java:55-58](../../src/main/java/org/chucc/vcserver/event/EventPublisher.java#L55-L58))
  ```java
  @Counted(value = "event.published", description = "Events published count")
  ```
- ❌ No dataset lifecycle metrics
- ❌ No Kafka admin operation metrics
- ❌ No topic health metrics
- ❌ No consumer lag tracking

---

## Metrics to Implement

### 1. Dataset Lifecycle Metrics

**Counters:**
- `dataset.created` - Total datasets created
- `dataset.deleted` - Total datasets deleted
- `dataset.creation.failures` - Failed dataset creations
- `dataset.deletion.failures` - Failed dataset deletions

**Timers:**
- `dataset.creation.time` - Time to create dataset (including topic creation)
- `dataset.deletion.time` - Time to delete dataset (including topic deletion)

**Gauges:**
- `dataset.total` - Current number of datasets
- `dataset.size.bytes` - Total size of all datasets (if tracked)

**Implementation:**

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommandHandler.java`

```java
@Timed(
    value = "dataset.creation.time",
    description = "Time to create dataset",
    histogram = true
)
public DatasetCreatedEvent handle(CreateDatasetCommand command) {
  try {
    // Create dataset logic
    meterRegistry.counter("dataset.created",
        "dataset", command.dataset()
    ).increment();

    return event;

  } catch (Exception e) {
    meterRegistry.counter("dataset.creation.failures",
        "dataset", command.dataset(),
        "reason", e.getClass().getSimpleName()
    ).increment();
    throw e;
  }
}
```

### 2. Kafka Topic Metrics

**Counters:**
- `kafka.topic.created` - Topics created
- `kafka.topic.deleted` - Topics deleted
- `kafka.topic.creation.failures` - Failed topic creations
- `kafka.topic.deletion.failures` - Failed topic deletions

**Timers:**
- `kafka.topic.creation.time` - Time to create topic
- `kafka.topic.deletion.time` - Time to delete topic

**Gauges:**
- `kafka.topic.count` - Total Kafka topics managed by app
- `kafka.partition.count` - Total partitions across all topics

**Implementation:**

```java
private void createKafkaTopic(String dataset) {
  Timer.Sample sample = Timer.start(meterRegistry);

  try {
    String topicName = kafkaProperties.getTopicName(dataset);

    try (AdminClient adminClient = AdminClient.create(...)) {
      // Topic creation logic
    }

    sample.stop(meterRegistry.timer("kafka.topic.creation.time",
        "dataset", dataset
    ));

    meterRegistry.counter("kafka.topic.created",
        "dataset", dataset
    ).increment();

  } catch (Exception e) {
    meterRegistry.counter("kafka.topic.creation.failures",
        "dataset", dataset,
        "reason", e.getClass().getSimpleName()
    ).increment();
    throw e;
  }
}
```

### 3. Kafka Health Metrics

**Gauges:**
- `kafka.brokers.available` - Number of available Kafka brokers
- `kafka.brokers.total` - Total configured brokers
- `kafka.connection.status` - Connection status (1=up, 0=down)

**Timers:**
- `kafka.admin.operation.time` - Time for admin operations (describeCluster, etc.)

**Implementation:**

**File:** `src/main/java/org/chucc/vcserver/metrics/KafkaMetrics.java`

```java
@Component
public class KafkaMetrics {

  private final KafkaAdmin kafkaAdmin;
  private final MeterRegistry meterRegistry;

  public KafkaMetrics(KafkaAdmin kafkaAdmin, MeterRegistry meterRegistry) {
    this.kafkaAdmin = kafkaAdmin;
    this.meterRegistry = meterRegistry;

    // Register gauges
    Gauge.builder("kafka.connection.status", this, KafkaMetrics::checkConnectionStatus)
        .description("Kafka connection status (1=up, 0=down)")
        .register(meterRegistry);

    Gauge.builder("kafka.brokers.available", this, KafkaMetrics::getAvailableBrokers)
        .description("Number of available Kafka brokers")
        .register(meterRegistry);
  }

  private double checkConnectionStatus() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
      return 1.0;  // Up
    } catch (Exception e) {
      return 0.0;  // Down
    }
  }

  private double getAvailableBrokers() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Collection<Node> nodes = adminClient.describeCluster()
          .nodes()
          .get(5, TimeUnit.SECONDS);
      return nodes.size();
    } catch (Exception e) {
      return 0.0;
    }
  }

  @Scheduled(fixedRate = 60000)  // Every minute
  public void updateKafkaMetrics() {
    updateTopicCount();
    updatePartitionCount();
    updateConsumerLag();
  }

  private void updateTopicCount() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      ListTopicsResult topics = adminClient.listTopics();
      Set<String> topicNames = topics.names().get();

      // Filter only our topics (vc.*.events)
      long managedTopics = topicNames.stream()
          .filter(name -> name.startsWith("vc.") && name.endsWith(".events"))
          .count();

      meterRegistry.gauge("kafka.topic.count", managedTopics);
    } catch (Exception e) {
      logger.error("Failed to update topic count metric", e);
    }
  }

  private void updatePartitionCount() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      ListTopicsResult topics = adminClient.listTopics();
      Set<String> topicNames = topics.names().get().stream()
          .filter(name -> name.startsWith("vc.") && name.endsWith(".events"))
          .collect(Collectors.toSet());

      DescribeTopicsResult describe = adminClient.describeTopics(topicNames);
      Map<String, TopicDescription> descriptions = describe.all().get();

      int totalPartitions = descriptions.values().stream()
          .mapToInt(desc -> desc.partitions().size())
          .sum();

      meterRegistry.gauge("kafka.partition.count", totalPartitions);
    } catch (Exception e) {
      logger.error("Failed to update partition count metric", e);
    }
  }
}
```

### 4. Consumer Lag Metrics

**Gauges:**
- `kafka.consumer.lag` - Consumer lag per partition (tagged by topic, partition)
- `kafka.consumer.offset.current` - Current consumer offset
- `kafka.consumer.offset.end` - End offset (log end offset)

**Implementation:**

```java
@Scheduled(fixedRate = 30000)  // Every 30 seconds
public void updateConsumerLag() {
  String groupId = "read-model-projector";

  try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    // Get consumer group offsets
    ListConsumerGroupOffsetsResult offsets =
        adminClient.listConsumerGroupOffsets(groupId);
    Map<TopicPartition, OffsetAndMetadata> consumerOffsets =
        offsets.partitionsToOffsetAndMetadata().get();

    // Get log end offsets
    Map<TopicPartition, Long> endOffsets = getLogEndOffsets(adminClient, consumerOffsets.keySet());

    // Calculate lag per partition
    for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : consumerOffsets.entrySet()) {
      TopicPartition tp = entry.getKey();
      long consumerOffset = entry.getValue().offset();
      long endOffset = endOffsets.getOrDefault(tp, 0L);
      long lag = endOffset - consumerOffset;

      meterRegistry.gauge("kafka.consumer.lag",
          Tags.of(
              "topic", tp.topic(),
              "partition", String.valueOf(tp.partition()),
              "group", groupId
          ),
          lag
      );
    }
  } catch (Exception e) {
    logger.error("Failed to update consumer lag metrics", e);
  }
}
```

### 5. Event Processing Metrics

Enhance existing event publisher metrics:

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

```java
@Counted(
    value = "event.published",
    description = "Events published count"
)
@Timed(
    value = "event.publish.time",
    description = "Event publish time",
    histogram = true
)
public CompletableFuture<SendResult<String, VersionControlEvent>> publish(
    VersionControlEvent event) {

  Timer.Sample sample = Timer.start(meterRegistry);

  return kafkaTemplate.send(record)
      .whenComplete((result, ex) -> {
        if (ex != null) {
          meterRegistry.counter("event.publish.failures",
              "event", event.getClass().getSimpleName(),
              "dataset", event.dataset()
          ).increment();
        } else {
          sample.stop(meterRegistry.timer("event.publish.time",
              "event", event.getClass().getSimpleName(),
              "dataset", event.dataset()
          ));

          meterRegistry.counter("event.published",
              "event", event.getClass().getSimpleName(),
              "dataset", event.dataset()
          ).increment();
        }
      });
}
```

---

## Implementation Steps

### Step 1: Add Micrometer Dependencies

**File:** `pom.xml`

```xml
<!-- Micrometer core (likely already present) -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
</dependency>

<!-- Micrometer registry for Prometheus (example) -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Spring Boot Actuator (for metrics endpoint) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Step 2: Create KafkaMetrics Component

**File:** `src/main/java/org/chucc/vcserver/metrics/KafkaMetrics.java`

Implement scheduled metrics collection (see code above).

### Step 3: Update Command Handlers

Add `@Timed` and `@Counted` annotations to:
- `CreateDatasetCommandHandler`
- `DeleteDatasetCommandHandler`
- Other command handlers

### Step 4: Add Custom Metrics Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/MetricsController.java`

```java
@RestController
@RequestMapping("/actuator/kafka")
public class KafkaMetricsController {

  private final KafkaAdmin kafkaAdmin;

  @GetMapping("/health")
  public ResponseEntity<KafkaHealthResponse> getKafkaHealth() {
    // Return Kafka cluster health
  }

  @GetMapping("/topics")
  public ResponseEntity<List<TopicInfo>> getManagedTopics() {
    // Return list of managed topics with partition counts
  }

  @GetMapping("/consumer-lag")
  public ResponseEntity<Map<String, ConsumerLagInfo>> getConsumerLag() {
    // Return consumer lag per topic/partition
  }
}
```

### Step 5: Configure Actuator

**File:** `src/main/resources/application.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,kafka
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active}
    export:
      prometheus:
        enabled: true
  endpoint:
    health:
      show-details: when-authorized
```

---

## Alerting Rules

### Prometheus Alert Rules

**File:** `prometheus-alerts.yml` (for ops team)

```yaml
groups:
  - name: chucc-dataset-management
    interval: 30s
    rules:
      # Dataset creation failures
      - alert: HighDatasetCreationFailureRate
        expr: |
          rate(dataset_creation_failures_total[5m]) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High dataset creation failure rate"
          description: "{{ $value }} dataset creations failing per second"

      # Kafka connectivity
      - alert: KafkaConnectionDown
        expr: kafka_connection_status == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Kafka cluster unreachable"
          description: "Cannot connect to Kafka cluster"

      # Consumer lag
      - alert: HighConsumerLag
        expr: kafka_consumer_lag > 1000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High consumer lag on {{ $labels.topic }}"
          description: "Lag is {{ $value }} messages on partition {{ $labels.partition }}"

      # Topic creation failures
      - alert: TopicCreationFailures
        expr: |
          increase(kafka_topic_creation_failures_total[1h]) > 5
        labels:
          severity: warning
        annotations:
          summary: "Multiple topic creation failures"
          description: "{{ $value }} topics failed to create in last hour"

      # Broker availability
      - alert: InsufficientKafkaBrokers
        expr: kafka_brokers_available < 2
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Insufficient Kafka brokers"
          description: "Only {{ $value }} brokers available (need 3 for RF=3)"
```

### Grafana Dashboard

Create dashboard with panels:

1. **Dataset Overview**
   - Total datasets (gauge)
   - Dataset creation rate (graph)
   - Dataset deletion rate (graph)

2. **Kafka Health**
   - Broker availability (gauge)
   - Connection status (status panel)
   - Topic count (gauge)
   - Partition count (gauge)

3. **Consumer Lag**
   - Lag per topic (graph)
   - Lag heatmap by partition
   - Max lag (gauge)

4. **Error Rates**
   - Dataset creation failures (graph)
   - Topic creation failures (graph)
   - Event publish failures (graph)

5. **Latency**
   - Dataset creation time (histogram)
   - Topic creation time (histogram)
   - Event publish time (histogram)

**Example Grafana queries:**
```promql
# Dataset creation rate
rate(dataset_created_total[5m])

# Consumer lag by topic
kafka_consumer_lag{group="read-model-projector"}

# P95 dataset creation time
histogram_quantile(0.95, rate(dataset_creation_time_bucket[5m]))

# Error rate
rate(dataset_creation_failures_total[5m])
```

---

## Testing

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/metrics/KafkaMetricsTest.java`

```java
@Test
void kafkaMetrics_shouldTrackTopicCount() {
  // Create 3 datasets
  // Trigger metric update
  // Assert: kafka.topic.count == 3
}

@Test
void kafkaMetrics_shouldTrackConsumerLag() {
  // Publish 100 events
  // Don't consume them
  // Trigger metric update
  // Assert: kafka.consumer.lag > 0
}
```

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/metrics/DatasetMetricsIT.java`

```java
@Test
void createDataset_shouldIncrementMetric() {
  // Get current metric value
  double before = meterRegistry.get("dataset.created").counter().count();

  // Create dataset
  restTemplate.postForEntity("/version/datasets/test", ...);

  // Assert: metric incremented
  double after = meterRegistry.get("dataset.created").counter().count();
  assertThat(after).isEqualTo(before + 1);
}
```

---

## Documentation

### 1. Metrics Reference

**File:** `docs/operations/metrics.md`

Document all metrics:
- Metric name
- Type (counter, gauge, timer)
- Description
- Tags
- Example values

### 2. Alerting Runbook

**File:** `docs/operations/alerting-runbook.md`

For each alert:
- Alert name and severity
- What it means
- How to investigate
- How to resolve
- Escalation path

Example:
```markdown
## Alert: HighDatasetCreationFailureRate

**Severity:** Warning
**Threshold:** >10% failure rate over 5 minutes

**What it means:**
Dataset creation is failing at a high rate. Users cannot create new datasets.

**Investigation:**
1. Check Kafka cluster health: `kubectl get pods -n kafka`
2. Check application logs: `kubectl logs -n chucc deployment/chucc-server --tail=100`
3. Check Prometheus for specific error types: `kafka_topic_creation_failures_total{reason="..."}`

**Common causes:**
- Kafka cluster down → Check Kafka cluster health
- Authorization issue → Check Kafka ACLs
- Quota exceeded → Check Kafka storage quotas

**Resolution:**
- If Kafka down: Restart Kafka cluster
- If authorization: Update Kafka ACLs
- If quota: Increase quota or delete old topics

**Escalation:**
If not resolved in 30 minutes, escalate to Platform team.
```

---

## Acceptance Criteria

- [ ] Dataset creation/deletion metrics implemented
- [ ] Kafka topic creation/deletion metrics implemented
- [ ] Kafka health metrics (broker count, connection status) implemented
- [ ] Consumer lag metrics implemented
- [ ] Event publish metrics enhanced with timing and failures
- [ ] Metrics exposed via `/actuator/metrics` and `/actuator/prometheus`
- [ ] Custom Kafka health endpoint at `/actuator/kafka/health`
- [ ] Prometheus alert rules defined
- [ ] Grafana dashboard created
- [ ] Metrics reference documentation written
- [ ] Alerting runbook created
- [ ] Unit and integration tests for metrics
- [ ] All tests pass

---

## Related Tasks

- **Previous:** [03-error-handling.md](./03-error-handling.md)
- **Next:** [05-per-dataset-tuning.md](./05-per-dataset-tuning.md)
- **Depends on:** Tasks 01-03 (dataset lifecycle must exist)

---

## References

- [Micrometer Documentation](https://micrometer.io/docs)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus Alerting](https://prometheus.io/docs/alerting/latest/overview/)
- [Grafana Provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Kafka Monitoring](https://kafka.apache.org/documentation/#monitoring)
