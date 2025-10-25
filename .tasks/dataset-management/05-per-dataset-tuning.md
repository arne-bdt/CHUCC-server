# Task: Per-Dataset Kafka Topic Configuration

**Status:** Not Started
**Priority:** Future / Nice-to-Have
**Category:** Scalability / Flexibility
**Estimated Time:** 4-5 hours

---

## Overview

Allow per-dataset customization of Kafka topic configuration:
- Custom partition count (override default)
- Custom retention period (override infinite default)
- Custom replication factor (within cluster limits)
- Custom compaction policy
- Dataset-specific performance tuning

**Use Cases:**
- **Archive datasets:** Finite retention (30 days), lower partition count
- **Hot datasets:** Higher partition count (12+), more replicas
- **Compliance datasets:** Infinite retention, higher replication
- **Test datasets:** Lower partition count, shorter retention

**Problem:** All datasets currently use the same Kafka configuration. One size doesn't fit all use cases.

---

## Current State

**Global Configuration** ([KafkaProperties.java](../../src/main/java/org/chucc/vcserver/config/KafkaProperties.java)):
```java
private int partitions = 3;
private short replicationFactor = 1;
private long retentionMs = -1;
private boolean compaction = false;
```

**All datasets use these defaults:**
- ❌ Cannot create a dataset with 12 partitions (high-throughput)
- ❌ Cannot create a dataset with 7-day retention (temporary data)
- ❌ Cannot create a dataset with RF=3 while others use RF=1 (compliance)

---

## Requirements

### 1. Extended Dataset Creation Request

**API:** `POST /version/datasets/{name}`

**Request Body:**
```json
{
  "description": "High-throughput product catalog",
  "kafka": {
    "partitions": 12,
    "replicationFactor": 3,
    "retentionMs": 2592000000,
    "compaction": false,
    "minInSyncReplicas": 2,
    "segmentMs": 604800000,
    "compressionType": "lz4"
  }
}
```

**Default behavior:**
- If `kafka` object omitted → use global defaults
- If `kafka.partitions` omitted → use global default
- Etc.

### 2. Validation Rules

**Partition Count:**
- Min: 1
- Max: 100 (configurable limit)
- Default: Global config (6)

**Replication Factor:**
- Min: 1
- Max: `min(available_brokers, 5)`
- Default: Global config (3 in prod, 1 in dev)

**Retention:**
- Min: 3600000 (1 hour)
- Max: -1 (infinite)
- Default: -1 (infinite)

**Compaction:**
- `false` (delete) or `true` (compact)
- Default: `false` (append-only for event sourcing)

**Implementation:**
```java
private void validateKafkaConfig(CreateDatasetRequest.KafkaConfig config) {
  // Validate partitions
  if (config.partitions() < 1 || config.partitions() > maxPartitionsPerTopic) {
    throw new IllegalArgumentException(
        "Partition count must be between 1 and " + maxPartitionsPerTopic
    );
  }

  // Validate replication factor
  int availableBrokers = getAvailableBrokerCount();
  if (config.replicationFactor() > availableBrokers) {
    throw new IllegalArgumentException(
        "Replication factor " + config.replicationFactor() +
        " exceeds available brokers: " + availableBrokers
    );
  }

  // Validate retention
  if (config.retentionMs() != -1 && config.retentionMs() < 3600000) {
    throw new IllegalArgumentException(
        "Retention must be at least 1 hour or -1 (infinite)"
    );
  }
}
```

### 3. Store Dataset Configuration

**Option A: In Dataset Entity**

Extend `Dataset` domain model to store Kafka config:
```java
public class Dataset {
  private String name;
  private String description;
  private KafkaTopicConfig kafkaConfig;
  // ...

  public record KafkaTopicConfig(
      int partitions,
      short replicationFactor,
      long retentionMs,
      boolean compaction,
      short minInSyncReplicas,
      long segmentMs,
      String compressionType
  ) {
    public static KafkaTopicConfig defaults(KafkaProperties props) {
      return new KafkaTopicConfig(
          props.getPartitions(),
          props.getReplicationFactor(),
          props.getRetentionMs(),
          props.isCompaction(),
          (short) 2,
          604800000L,
          "snappy"
      );
    }
  }
}
```

**Option B: In Separate Configuration Repository**

Create `DatasetConfigRepository` to store per-dataset settings.

**Recommendation:** Option A (simpler, config travels with dataset metadata).

### 4. Update Topic Creation Logic

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommandHandler.java`

```java
private void createKafkaTopic(String dataset, KafkaTopicConfig config) {
  String topicName = kafkaProperties.getTopicName(dataset);

  try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    NewTopic newTopic = new NewTopic(
        topicName,
        config.partitions(),            // ← Use dataset-specific config
        config.replicationFactor()      // ← Use dataset-specific config
    );

    // Build topic config from dataset settings
    Map<String, String> topicConfig = new HashMap<>();
    topicConfig.put("retention.ms", String.valueOf(config.retentionMs()));
    topicConfig.put("cleanup.policy", config.compaction() ? "compact" : "delete");
    topicConfig.put("compression.type", config.compressionType());
    topicConfig.put("segment.ms", String.valueOf(config.segmentMs()));

    if (config.replicationFactor() > 1) {
      topicConfig.put("min.insync.replicas",
          String.valueOf(config.minInSyncReplicas()));
    }

    newTopic.configs(topicConfig);
    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
  }
}
```

### 5. GET /datasets/{name} - Return Configuration

**Response:**
```json
{
  "name": "mydata",
  "description": "Product catalog",
  "mainBranch": "main",
  "createdAt": "2025-10-25T12:00:00Z",
  "kafka": {
    "topic": "vc.mydata.events",
    "partitions": 12,
    "replicationFactor": 3,
    "retentionMs": 2592000000,
    "compaction": false,
    "minInSyncReplicas": 2
  }
}
```

### 6. Update Topic Configuration (Advanced)

**API:** `PATCH /version/datasets/{name}/config`

**Request:**
```json
{
  "kafka": {
    "partitions": 16,
    "retentionMs": 5184000000
  }
}
```

**Constraints:**
- ✅ **Can increase** partitions (cannot decrease without recreation)
- ✅ **Can change** retention, compression, compaction
- ❌ **Cannot change** replication factor (requires partition reassignment)

**Implementation:**
```java
@PatchMapping("/{dataset}/config")
public ResponseEntity<Void> updateDatasetConfig(
    @PathVariable String dataset,
    @RequestBody UpdateConfigRequest request) {

  // Validate new config
  validateConfigUpdate(dataset, request);

  // Update topic via AdminClient
  updateKafkaTopicConfig(dataset, request.kafka());

  // Update dataset metadata
  datasetRepository.updateConfig(dataset, request.kafka());

  return ResponseEntity.noContent().build();
}

private void updateKafkaTopicConfig(String dataset, KafkaConfig newConfig) {
  String topicName = kafkaProperties.getTopicName(dataset);

  try (AdminClient adminClient = AdminClient.create(...)) {
    // Option 1: Increase partitions (if requested)
    if (newConfig.partitions() != null) {
      Map<String, NewPartitions> newPartitions = Map.of(
          topicName, NewPartitions.increaseTo(newConfig.partitions())
      );
      adminClient.createPartitions(newPartitions).all().get();
    }

    // Option 2: Update topic config (retention, compression, etc.)
    Map<ConfigResource, Collection<AlterConfigOp>> configs = new HashMap<>();
    ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

    List<AlterConfigOp> ops = new ArrayList<>();
    if (newConfig.retentionMs() != null) {
      ops.add(new AlterConfigOp(
          new ConfigEntry("retention.ms", String.valueOf(newConfig.retentionMs())),
          AlterConfigOp.OpType.SET
      ));
    }

    configs.put(resource, ops);
    adminClient.incrementalAlterConfigs(configs).all().get();
  }
}
```

---

## Implementation Steps

### Step 1: Extend Request/Response DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/CreateDatasetRequest.java`

```java
public record CreateDatasetRequest(
    String description,
    Optional<String> initialGraph,
    Optional<KafkaTopicConfig> kafka
) {
  public record KafkaTopicConfig(
      Optional<Integer> partitions,
      Optional<Short> replicationFactor,
      Optional<Long> retentionMs,
      Optional<Boolean> compaction,
      Optional<Short> minInSyncReplicas,
      Optional<Long> segmentMs,
      Optional<String> compressionType
  ) { }
}
```

### Step 2: Add Configuration Properties

**File:** `src/main/resources/application.yml`

```yaml
kafka:
  limits:
    max-partitions-per-topic: 100
    max-replication-factor: 5
    min-retention-ms: 3600000
```

**File:** `src/main/java/org/chucc/vcserver/config/KafkaProperties.java`

```java
public static class Limits {
  private int maxPartitionsPerTopic = 100;
  private short maxReplicationFactor = 5;
  private long minRetentionMs = 3600000;

  // Getters and setters
}
```

### Step 3: Update Domain Model

**File:** `src/main/java/org/chucc/vcserver/domain/Dataset.java`

Add `KafkaTopicConfig` field (see above).

### Step 4: Update Command

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommand.java`

```java
public record CreateDatasetCommand(
    String dataset,
    String description,
    String author,
    Optional<String> initialGraph,
    KafkaTopicConfig kafkaConfig  // ← Add config
) implements VersionControlCommand { }
```

### Step 5: Update Command Handler

Modify `CreateDatasetCommandHandler.handle()` to use per-dataset config.

### Step 6: Add Configuration Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/DatasetConfigController.java`

```java
@RestController
@RequestMapping("/version/datasets/{dataset}/config")
public class DatasetConfigController {

  @GetMapping
  public ResponseEntity<DatasetConfigResponse> getConfig(@PathVariable String dataset) {
    // Return dataset configuration
  }

  @PatchMapping
  public ResponseEntity<Void> updateConfig(
      @PathVariable String dataset,
      @RequestBody UpdateConfigRequest request) {
    // Update topic configuration
  }
}
```

---

## Use Case Examples

### Example 1: High-Throughput Dataset

```bash
curl -X POST http://localhost:8080/version/datasets/products \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Admin <admin@example.org>" \
  -d '{
    "description": "Product catalog - high write volume",
    "kafka": {
      "partitions": 16,
      "replicationFactor": 3,
      "retentionMs": -1,
      "compressionType": "lz4"
    }
  }'
```

**Result:** Topic `vc.products.events` created with 16 partitions, LZ4 compression.

### Example 2: Archive Dataset

```bash
curl -X POST http://localhost:8080/version/datasets/archive-2024 \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Admin <admin@example.org>" \
  -d '{
    "description": "Archive for 2024 data",
    "kafka": {
      "partitions": 3,
      "retentionMs": 2592000000,
      "compressionType": "zstd"
    }
  }'
```

**Result:** Topic with 30-day retention, ZSTD compression (highest compression ratio).

### Example 3: Compliance Dataset

```bash
curl -X POST http://localhost:8080/version/datasets/compliance-audit \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Admin <admin@example.org>" \
  -d '{
    "description": "Compliance audit trail",
    "kafka": {
      "partitions": 6,
      "replicationFactor": 5,
      "retentionMs": -1,
      "minInSyncReplicas": 3
    }
  }'
```

**Result:** Topic with RF=5, min.insync.replicas=3 (maximum durability).

---

## Testing

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/command/CreateDatasetCommandHandlerConfigTest.java`

```java
@Test
void createDataset_withCustomPartitions_shouldCreateTopicWithCustomConfig() {
  KafkaTopicConfig config = new KafkaTopicConfig(12, ...);
  CreateDatasetCommand command = new CreateDatasetCommand("test", ..., config);

  handler.handle(command);

  // Verify topic created with 12 partitions
}

@Test
void createDataset_partitionCountTooHigh_shouldThrowException() {
  KafkaTopicConfig config = new KafkaTopicConfig(200, ...);  // Over limit
  CreateDatasetCommand command = new CreateDatasetCommand("test", ..., config);

  assertThatThrownBy(() -> handler.handle(command))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Partition count must be between");
}

@Test
void createDataset_replicationFactorExceedsBrokers_shouldThrowException() {
  KafkaTopicConfig config = new KafkaTopicConfig(..., (short) 10, ...);
  // Cluster has only 3 brokers
  assertThatThrownBy(() -> handler.handle(command))
      .isInstanceOf(IllegalArgumentException.class);
}
```

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/DatasetConfigIT.java`

```java
@Test
void createDataset_withCustomConfig_shouldCreateTopicWithConfig() throws Exception {
  CreateDatasetRequest request = new CreateDatasetRequest(
      "High-throughput dataset",
      Optional.empty(),
      Optional.of(new KafkaTopicConfig(
          Optional.of(12),
          Optional.of((short) 1),
          Optional.of(-1L),
          Optional.of(false),
          Optional.empty(),
          Optional.empty(),
          Optional.of("lz4")
      ))
  );

  restTemplate.postForEntity("/version/datasets/test", request, ...);

  // Verify topic exists with 12 partitions
  try (AdminClient adminClient = AdminClient.create(...)) {
    DescribeTopicsResult result = adminClient.describeTopics(
        Collections.singleton("vc.test.events")
    );
    TopicDescription desc = result.all().get().get("vc.test.events");

    assertThat(desc.partitions()).hasSize(12);

    // Verify config
    ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, "vc.test.events");
    Config config = adminClient.describeConfigs(Collections.singleton(resource))
        .all().get().get(resource);

    assertThat(config.get("compression.type").value()).isEqualTo("lz4");
  }
}
```

---

## Documentation

### API Documentation

**File:** `src/main/resources/openapi/openapi.yaml`

```yaml
components:
  schemas:
    CreateDatasetRequest:
      type: object
      properties:
        description:
          type: string
        initialGraph:
          type: string
          format: uri
        kafka:
          $ref: '#/components/schemas/KafkaTopicConfig'

    KafkaTopicConfig:
      type: object
      description: Custom Kafka topic configuration (optional)
      properties:
        partitions:
          type: integer
          minimum: 1
          maximum: 100
          default: 6
        replicationFactor:
          type: integer
          minimum: 1
          maximum: 5
          default: 3
        retentionMs:
          type: integer
          format: int64
          default: -1
          description: Retention in milliseconds (-1 = infinite)
        compaction:
          type: boolean
          default: false
        compressionType:
          type: string
          enum: [none, gzip, snappy, lz4, zstd]
          default: snappy
```

### User Guide

**File:** `docs/user-guide/dataset-configuration.md`

Document:
- When to use custom configuration
- Performance implications
- Cost implications (more partitions = more resources)
- Best practices

---

## Acceptance Criteria

- [ ] `POST /datasets/{name}` accepts optional `kafka` configuration object
- [ ] Default config used if `kafka` object omitted
- [ ] Partition count validated (1-100)
- [ ] Replication factor validated (1 ≤ RF ≤ available brokers)
- [ ] Retention validated (≥1 hour or -1)
- [ ] Topics created with per-dataset configuration
- [ ] `GET /datasets/{name}` returns Kafka configuration
- [ ] `PATCH /datasets/{name}/config` allows increasing partitions
- [ ] `PATCH /datasets/{name}/config` allows changing retention
- [ ] Cannot decrease partitions (returns 400)
- [ ] Unit and integration tests pass
- [ ] OpenAPI documentation updated
- [ ] User guide created

---

## Related Tasks

- **Previous:** [04-monitoring-metrics.md](./04-monitoring-metrics.md)
- **Next:** [06-topic-health-checks.md](./06-topic-health-checks.md)
- **Depends on:** Task 01 (dataset creation must exist)

---

## References

- [Kafka Topic Configuration](https://kafka.apache.org/documentation/#topicconfigs)
- [AdminClient API](https://kafka.apache.org/34/javadoc/org/apache/kafka/clients/admin/AdminClient.html)
- [Partition Management](https://kafka.apache.org/documentation/#basic_ops_increase_replication_factor)
