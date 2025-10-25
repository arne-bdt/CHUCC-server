# Task: Update Kafka Configuration for Production

**Status:** Not Started
**Priority:** Critical
**Category:** Kafka / Production Readiness
**Estimated Time:** 2-3 hours

---

## Overview

Update Kafka configuration from development defaults to production-ready settings:
- Increase replication factor from 1 to 3 (fault tolerance)
- Increase partition count from 3 to 6 (scalability)
- Add `min.insync.replicas` setting
- Configure producer for durability
- Configure consumer for reliability
- Add proper topic-level settings

**Problem:** Current configuration is development-only (RF=1). Data loss will occur on broker failure.

---

## Current State

**Development Configuration** ([application.yml](../../src/main/resources/application.yml)):
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  topic-template: vc.{dataset}.events
  partitions: 3                    # ❌ Too few for production
  replication-factor: 1            # ❌ CRITICAL: No fault tolerance!
  retention-ms: -1                 # ✅ Correct (infinite)
  compaction: false                # ✅ Correct (append-only)
```

**What's wrong:**
- ❌ RF=1 means **no data redundancy** (single point of failure)
- ❌ 3 partitions limits parallelism
- ❌ No `min.insync.replicas` setting
- ❌ Producer doesn't wait for all replicas
- ❌ No idempotence or transactions configured

---

## Requirements

### 1. Application Configuration

**File:** `src/main/resources/application.yml`

Update Kafka section:
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  topic-template: vc.{dataset}.events

  # Topic configuration (increased for production)
  partitions: ${KAFKA_PARTITIONS:6}              # 6 partitions (was 3)
  replication-factor: ${KAFKA_REPLICATION_FACTOR:1}  # 3 in production, 1 in dev
  retention-ms: ${KAFKA_RETENTION_MS:-1}         # -1 = infinite retention
  compaction: ${KAFKA_COMPACTION:false}          # false = append-only

  # Transactional ID prefix
  transactional-id-prefix: vc-tx-

  # Producer configuration
  producer:
    retries: ${KAFKA_PRODUCER_RETRIES:3}
    acks: ${KAFKA_PRODUCER_ACKS:all}             # Wait for all in-sync replicas
    enable-idempotence: ${KAFKA_PRODUCER_IDEMPOTENCE:true}  # Exactly-once
    max-in-flight-requests: ${KAFKA_PRODUCER_MAX_IN_FLIGHT:5}
    compression-type: ${KAFKA_PRODUCER_COMPRESSION:snappy}
    linger-ms: ${KAFKA_PRODUCER_LINGER_MS:10}    # Batch for 10ms
    batch-size: ${KAFKA_PRODUCER_BATCH_SIZE:32768}  # 32KB batches

  # Consumer configuration
  consumer:
    auto-commit-interval-ms: ${KAFKA_CONSUMER_AUTO_COMMIT_INTERVAL:1000}
    enable-auto-commit: ${KAFKA_CONSUMER_ENABLE_AUTO_COMMIT:true}
    isolation-level: ${KAFKA_CONSUMER_ISOLATION:read_committed}
    max-poll-records: ${KAFKA_CONSUMER_MAX_POLL_RECORDS:500}
```

### 2. Environment-Specific Profiles

**File:** `src/main/resources/application-prod.yml` (create if missing)

```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka-1:9092,kafka-2:9092,kafka-3:9092}
  partitions: 6
  replication-factor: 3              # CRITICAL: Must be 3

  producer:
    acks: all                        # Wait for all replicas
    enable-idempotence: true
    compression-type: snappy

  consumer:
    isolation-level: read_committed  # Only read committed transactions
```

**File:** `src/main/resources/application-dev.yml`

```yaml
kafka:
  bootstrap-servers: localhost:9092
  partitions: 3
  replication-factor: 1              # OK for development

  producer:
    acks: 1                          # Leader only (faster)
    enable-idempotence: false
```

**File:** `src/test/resources/application-it.yml`

```yaml
# Integration tests use Testcontainers
kafka:
  partitions: 3
  replication-factor: 1              # Testcontainers uses single broker
```

### 3. Update KafkaProperties

**File:** `src/main/java/org/chucc/vcserver/config/KafkaProperties.java`

Add new fields to `Producer` class:
```java
public static class Producer {
  private int retries = 3;
  private String acks = "all";
  private boolean enableIdempotence = true;
  private int maxInFlightRequests = 5;
  private String compressionType = "snappy";
  private int lingerMs = 10;
  private int batchSize = 32768;

  // Getters and setters
}
```

Add new fields to `Consumer` class:
```java
public static class Consumer {
  private int autoCommitIntervalMs = 1000;
  private boolean enableAutoCommit = true;
  private String isolationLevel = "read_committed";
  private int maxPollRecords = 500;

  // Getters and setters
}
```

### 4. Update KafkaConfig

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`

Update producer config factory:
```java
@Bean
public ProducerFactory<String, VersionControlEvent> producerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.getBootstrapServers());
  configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

  // Production settings
  configProps.put(ProducerConfig.ACKS_CONFIG,
      kafkaProperties.getProducer().getAcks());
  configProps.put(ProducerConfig.RETRIES_CONFIG,
      kafkaProperties.getProducer().getRetries());
  configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
      kafkaProperties.getProducer().isEnableIdempotence());
  configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
      kafkaProperties.getProducer().getMaxInFlightRequests());
  configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,
      kafkaProperties.getProducer().getCompressionType());
  configProps.put(ProducerConfig.LINGER_MS_CONFIG,
      kafkaProperties.getProducer().getLingerMs());
  configProps.put(ProducerConfig.BATCH_SIZE_CONFIG,
      kafkaProperties.getProducer().getBatchSize());

  // Transactions (if needed)
  configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,
      kafkaProperties.getTransactionalIdPrefix() + UUID.randomUUID());

  return new DefaultKafkaProducerFactory<>(configProps);
}
```

Update consumer config factory:
```java
@Bean
public ConsumerFactory<String, VersionControlEvent> consumerFactory() {
  Map<String, Object> configProps = new HashMap<>();
  configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
      kafkaProperties.getBootstrapServers());
  configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
  configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

  // Production settings
  configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
      kafkaProperties.getConsumer().isEnableAutoCommit());
  configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG,
      kafkaProperties.getConsumer().getAutoCommitIntervalMs());
  configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,
      kafkaProperties.getConsumer().getIsolationLevel());
  configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
      kafkaProperties.getConsumer().getMaxPollRecords());

  return new DefaultKafkaConsumerFactory<>(configProps);
}
```

### 5. Update Topic Creation Logic

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommandHandler.java`

When creating topics, add production settings:
```java
private void createKafkaTopic(String dataset) {
  String topicName = kafkaProperties.getTopicName(dataset);

  try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    NewTopic newTopic = new NewTopic(
        topicName,
        kafkaProperties.getPartitions(),
        kafkaProperties.getReplicationFactor()
    );

    // Topic-level configuration
    Map<String, String> config = new HashMap<>();
    config.put("retention.ms", String.valueOf(kafkaProperties.getRetentionMs()));
    config.put("cleanup.policy", kafkaProperties.isCompaction() ? "compact" : "delete");

    // Production settings (only if RF > 1)
    if (kafkaProperties.getReplicationFactor() > 1) {
      config.put("min.insync.replicas", "2");  // At least 2 replicas must ack
      config.put("unclean.leader.election.enable", "false");  // Prevent data loss
    }

    // Performance settings
    config.put("compression.type", "snappy");
    config.put("segment.ms", "604800000");  // 7 days per segment
    config.put("max.message.bytes", "1048576");  // 1MB max message

    newTopic.configs(config);
    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
  }
}
```

---

## Production Environment Variables

**Docker Compose / Kubernetes ConfigMap:**

```bash
# Kafka cluster (3 brokers)
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092

# Topic configuration
KAFKA_PARTITIONS=6
KAFKA_REPLICATION_FACTOR=3
KAFKA_RETENTION_MS=-1

# Producer configuration
KAFKA_PRODUCER_ACKS=all
KAFKA_PRODUCER_IDEMPOTENCE=true
KAFKA_PRODUCER_COMPRESSION=snappy

# Consumer configuration
KAFKA_CONSUMER_ISOLATION=read_committed
```

**Development (local):**
```bash
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_REPLICATION_FACTOR=1
KAFKA_PARTITIONS=3
```

---

## Kafka Broker Configuration

**For production Kafka cluster** (DevOps reference):

```properties
# broker.properties (each broker)
broker.id=1  # 2, 3 for other brokers
num.network.threads=8
num.io.threads=16

# Replication
default.replication.factor=3
min.insync.replicas=2
unclean.leader.election.enable=false

# Topic creation (disable auto-creation, use app logic)
auto.create.topics.enable=false

# Log retention
log.retention.hours=-1  # Infinite (matches app config)
log.segment.bytes=1073741824  # 1GB segments
log.retention.check.interval.ms=300000  # Check every 5 min

# Performance
num.replica.fetchers=4
replica.lag.time.max.ms=30000
```

---

## Validation & Testing

### Configuration Validation Test

**File:** `src/test/java/org/chucc/vcserver/config/KafkaConfigTest.java`

```java
@Test
void kafkaProperties_shouldLoadProductionConfig() {
  // Test with profile=prod
  // Verify replication-factor=3
  // Verify partitions=6
  // Verify producer.acks=all
}

@Test
void kafkaProperties_shouldLoadDevelopmentConfig() {
  // Test with profile=dev
  // Verify replication-factor=1
  // Verify partitions=3
}
```

### Integration Test Updates

**Update:** All integration tests that create topics

Ensure tests respect the configured replication factor:
```java
@BeforeEach
void setUp() throws Exception {
  // Use kafkaProperties.getReplicationFactor()
  // NOT hardcoded replication-factor=1
  NewTopic newTopic = new NewTopic(
      topicName,
      kafkaProperties.getPartitions(),
      kafkaProperties.getReplicationFactor()  // ← Use config, not hardcoded
  );
}
```

**Affected files:**
- `GraphEventProjectorIT.java`
- `VersionControlProjectorIT.java`
- `AdvancedOperationsProjectorIT.java`
- `DatasetDeletionIT.java`
- `BranchDeletionIT.java`

### Manual Production Verification

After deployment to production:

```bash
# 1. Verify broker configuration
kafka-configs.sh --bootstrap-server kafka-1:9092 \
  --describe --entity-type brokers --all

# 2. Create test dataset via API
curl -X POST http://localhost:8080/version/datasets/test-prod \
  -H "SPARQL-VC-Author: Admin <admin@example.org>"

# 3. Verify topic was created with correct settings
kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --describe --topic vc.test-prod.events

# Expected output:
# Topic: vc.test-prod.events
# PartitionCount: 6
# ReplicationFactor: 3
# Configs: retention.ms=-1, min.insync.replicas=2, ...

# 4. Verify topic configuration
kafka-configs.sh --bootstrap-server kafka-1:9092 \
  --describe --entity-type topics --entity-name vc.test-prod.events

# 5. Check consumer group
kafka-consumer-groups.sh --bootstrap-server kafka-1:9092 \
  --describe --group read-model-projector
```

---

## Migration Strategy

### For Existing Development Environments

**If topics already exist with RF=1:**

```bash
# Option 1: Delete and recreate (data loss!)
kafka-topics.sh --bootstrap-server localhost:9092 \
  --delete --topic vc.default.events

# Option 2: Increase replication factor (no data loss)
kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
  --reassignment-json-file increase-replication.json \
  --execute

# increase-replication.json:
# {"version":1,"partitions":[
#   {"topic":"vc.default.events","partition":0,"replicas":[1,2,3]},
#   {"topic":"vc.default.events","partition":1,"replicas":[1,2,3]},
#   {"topic":"vc.default.events","partition":2,"replicas":[1,2,3]}
# ]}
```

**Recommended:** Use Option 1 for dev environments (data is not critical).

### For Production Deployment

**New clusters:**
- Start with RF=3 from day 1
- No migration needed

**Existing clusters:**
- Use partition reassignment tool
- Monitor replication lag
- Verify all partitions are in-sync

---

## Documentation Updates

### 1. README.md

Add Kafka configuration section:
```markdown
## Kafka Configuration

### Development
- Replication Factor: 1 (single broker)
- Partitions: 3 per dataset

### Production
- Replication Factor: 3 (fault tolerance)
- Partitions: 6 per dataset
- Min In-Sync Replicas: 2
```

### 2. Deployment Guide

Create `docs/deployment/kafka-setup.md`:
- Broker requirements (3+ brokers)
- Configuration checklist
- Topic verification steps
- Troubleshooting guide

### 3. Architecture Documentation

Update [docs/architecture/cqrs-event-sourcing.md](../../docs/architecture/cqrs-event-sourcing.md):
- Document production Kafka settings
- Explain fault tolerance strategy
- Document partition count rationale

---

## Acceptance Criteria

- [ ] `application-prod.yml` sets `replication-factor: 3`
- [ ] `application-prod.yml` sets `partitions: 6`
- [ ] `application-dev.yml` keeps `replication-factor: 1` (dev/test)
- [ ] Producer configured with `acks=all` and `enable.idempotence=true`
- [ ] Consumer configured with `isolation.level=read_committed`
- [ ] Topic creation adds `min.insync.replicas=2` when RF > 1
- [ ] Integration tests respect configured replication factor
- [ ] Configuration validation tests pass
- [ ] Manual production verification checklist documented
- [ ] Documentation updated (README, deployment guide, architecture docs)
- [ ] All tests pass with new configuration
- [ ] Zero Checkstyle/SpotBugs/PMD violations

---

## Related Tasks

- **Previous:** [01-dataset-creation-endpoint.md](./01-dataset-creation-endpoint.md) - Dataset creation with topic creation
- **Next:** [03-error-handling.md](./03-error-handling.md) - Handle topic creation failures
- **Depends on:** Task 01 (topic creation logic must exist)

---

## References

- [Kafka Producer Configs](https://kafka.apache.org/documentation/#producerconfigs)
- [Kafka Consumer Configs](https://kafka.apache.org/documentation/#consumerconfigs)
- [Kafka Topic Configs](https://kafka.apache.org/documentation/#topicconfigs)
- [Kafka Replication](https://kafka.apache.org/documentation/#replication)
- [KafkaProperties.java](../../src/main/java/org/chucc/vcserver/config/KafkaProperties.java)
- [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java)
