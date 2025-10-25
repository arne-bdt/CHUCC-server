# Task: Topic Health Checks and Auto-Healing

**Status:** Not Started
**Priority:** Future / Nice-to-Have
**Category:** Reliability / Operations
**Estimated Time:** 4-5 hours

---

## Overview

Implement automated health checks and self-healing for Kafka topics:
- Detect missing topics (topic deleted but dataset exists)
- Detect under-replicated partitions
- Detect inconsistent configuration (actual vs. expected)
- Auto-recreate missing topics (with safeguards)
- Alert on unhealthy topics
- Health check endpoint for monitoring

**Use Cases:**
- **Topic accidentally deleted:** Auto-recreate with correct configuration
- **Partition under-replicated:** Alert operations team
- **Config drift:** Detect and optionally fix configuration mismatches
- **Monitoring:** Expose health status for external monitoring systems

**Problem:** If a Kafka topic is manually deleted or misconfigured, the system becomes inconsistent without manual intervention.

---

## Current State

**No Health Checks:**
- ❌ No detection of missing topics
- ❌ No detection of under-replicated partitions
- ❌ No detection of configuration drift
- ❌ No auto-healing
- ❌ Basic Kafka health check exists but doesn't validate topics

**Existing Health Indicator:**
- ✅ Basic connectivity check (see Task 03)
- ❌ Doesn't verify managed topics exist

---

## Requirements

### 1. Health Check Types

#### A. Topic Existence Check

**Check:** Verify all datasets have corresponding Kafka topics.

**Implementation:**
```java
@Scheduled(fixedRate = 300000)  // Every 5 minutes
public void checkTopicExistence() {
  List<String> datasets = datasetRepository.findAllNames();

  for (String dataset : datasets) {
    String expectedTopic = kafkaProperties.getTopicName(dataset);

    if (!topicExists(expectedTopic)) {
      logger.error("CRITICAL: Topic missing for dataset: {} (expected: {})",
          dataset, expectedTopic);

      meterRegistry.counter("kafka.topic.missing",
          "dataset", dataset,
          "topic", expectedTopic
      ).increment();

      // Optionally auto-heal
      if (autoHealingEnabled) {
        recreateMissingTopic(dataset);
      } else {
        alertOpsTeam("Topic missing for dataset: " + dataset);
      }
    }
  }
}

private boolean topicExists(String topicName) {
  try (AdminClient adminClient = AdminClient.create(...)) {
    Set<String> topics = adminClient.listTopics().names().get();
    return topics.contains(topicName);
  } catch (Exception e) {
    logger.error("Failed to check topic existence: {}", topicName, e);
    return false;  // Assume missing if check fails
  }
}
```

#### B. Replication Health Check

**Check:** Verify all partitions are fully replicated (ISR = expected replicas).

**Implementation:**
```java
@Scheduled(fixedRate = 60000)  // Every minute
public void checkReplicationHealth() {
  try (AdminClient adminClient = AdminClient.create(...)) {
    Set<String> managedTopics = getManagedTopicNames();

    DescribeTopicsResult result = adminClient.describeTopics(managedTopics);
    Map<String, TopicDescription> descriptions = result.all().get();

    for (Map.Entry<String, TopicDescription> entry : descriptions.entrySet()) {
      String topicName = entry.getKey();
      TopicDescription desc = entry.getValue();

      for (TopicPartitionInfo partition : desc.partitions()) {
        int replicaCount = partition.replicas().size();
        int isrCount = partition.isr().size();  // In-Sync Replicas

        if (isrCount < replicaCount) {
          logger.warn("Under-replicated partition: topic={}, partition={}, " +
              "replicas={}, isr={}",
              topicName, partition.partition(), replicaCount, isrCount);

          meterRegistry.gauge("kafka.partition.under_replicated",
              Tags.of("topic", topicName, "partition", String.valueOf(partition.partition())),
              1.0
          );

          // Alert if under-replicated for >10 minutes
          checkUnderReplicationDuration(topicName, partition.partition());
        } else {
          meterRegistry.gauge("kafka.partition.under_replicated",
              Tags.of("topic", topicName, "partition", String.valueOf(partition.partition())),
              0.0
          );
        }
      }
    }
  }
}
```

#### C. Configuration Drift Check

**Check:** Verify topic configuration matches expected configuration.

**Implementation:**
```java
@Scheduled(fixedRate = 3600000)  // Every hour
public void checkConfigurationDrift() {
  List<Dataset> datasets = datasetRepository.findAll();

  for (Dataset dataset : datasets) {
    String topicName = kafkaProperties.getTopicName(dataset.getName());
    KafkaTopicConfig expectedConfig = dataset.getKafkaConfig();

    KafkaTopicConfig actualConfig = getActualTopicConfig(topicName);

    if (!actualConfig.equals(expectedConfig)) {
      logger.warn("Configuration drift detected: dataset={}, topic={}",
          dataset.getName(), topicName);

      logConfigDifferences(expectedConfig, actualConfig);

      meterRegistry.counter("kafka.config.drift",
          "dataset", dataset.getName()
      ).increment();

      // Optionally auto-correct
      if (autoCorrectConfigEnabled) {
        correctConfigurationDrift(topicName, expectedConfig, actualConfig);
      }
    }
  }
}

private void correctConfigurationDrift(
    String topicName,
    KafkaTopicConfig expected,
    KafkaTopicConfig actual) {

  logger.info("Auto-correcting configuration drift for topic: {}", topicName);

  try (AdminClient adminClient = AdminClient.create(...)) {
    ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);

    List<AlterConfigOp> ops = new ArrayList<>();

    // Correct retention if different
    if (expected.retentionMs() != actual.retentionMs()) {
      ops.add(new AlterConfigOp(
          new ConfigEntry("retention.ms", String.valueOf(expected.retentionMs())),
          AlterConfigOp.OpType.SET
      ));
    }

    // Correct compression if different
    if (!expected.compressionType().equals(actual.compressionType())) {
      ops.add(new AlterConfigOp(
          new ConfigEntry("compression.type", expected.compressionType()),
          AlterConfigOp.OpType.SET
      ));
    }

    if (!ops.isEmpty()) {
      adminClient.incrementalAlterConfigs(
          Map.of(resource, ops)
      ).all().get();

      logger.info("Configuration drift corrected for topic: {}", topicName);
    }
  } catch (Exception e) {
    logger.error("Failed to correct configuration drift: {}", topicName, e);
  }
}
```

#### D. Orphan Topic Detection

**Check:** Find Kafka topics that don't correspond to any dataset.

**Implementation:**
```java
@Scheduled(fixedRate = 3600000)  // Every hour
public void checkOrphanTopics() {
  try (AdminClient adminClient = AdminClient.create(...)) {
    Set<String> allTopics = adminClient.listTopics().names().get();

    Set<String> managedTopics = allTopics.stream()
        .filter(name -> name.startsWith("vc.") && name.endsWith(".events"))
        .collect(Collectors.toSet());

    List<String> datasets = datasetRepository.findAllNames();
    Set<String> expectedTopics = datasets.stream()
        .map(kafkaProperties::getTopicName)
        .collect(Collectors.toSet());

    // Find orphans: managed topics without corresponding datasets
    Set<String> orphanTopics = new HashSet<>(managedTopics);
    orphanTopics.removeAll(expectedTopics);

    if (!orphanTopics.isEmpty()) {
      logger.warn("Orphan topics detected: {}", orphanTopics);

      for (String orphan : orphanTopics) {
        meterRegistry.counter("kafka.topic.orphan",
            "topic", orphan
        ).increment();
      }

      // Alert for manual cleanup
      alertOpsTeam("Orphan topics found: " + orphanTopics);
    }
  }
}
```

### 2. Auto-Healing Configuration

**File:** `src/main/resources/application.yml`

```yaml
kafka:
  health-check:
    enabled: true
    auto-healing:
      enabled: false                # Safe default: disabled
      recreate-missing-topics: false
      correct-config-drift: false
      require-confirmation: true    # Require ops approval before healing
    alerts:
      enabled: true
      missing-topic-alert: true
      under-replication-alert: true
      config-drift-alert: true
      orphan-topic-alert: true
```

**File:** `src/main/java/org/chucc/vcserver/config/KafkaHealthCheckProperties.java`

```java
@Component
@ConfigurationProperties(prefix = "kafka.health-check")
public class KafkaHealthCheckProperties {

  private boolean enabled = true;
  private AutoHealing autoHealing = new AutoHealing();
  private Alerts alerts = new Alerts();

  public static class AutoHealing {
    private boolean enabled = false;
    private boolean recreateMissingTopics = false;
    private boolean correctConfigDrift = false;
    private boolean requireConfirmation = true;

    // Getters and setters
  }

  public static class Alerts {
    private boolean enabled = true;
    private boolean missingTopicAlert = true;
    private boolean underReplicationAlert = true;
    private boolean configDriftAlert = true;
    private boolean orphanTopicAlert = true;

    // Getters and setters
  }
}
```

### 3. Health Check Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/KafkaHealthController.java`

```java
@RestController
@RequestMapping("/actuator/kafka")
public class KafkaHealthController {

  @GetMapping("/health-detailed")
  public ResponseEntity<KafkaHealthReport> getDetailedHealth() {
    KafkaHealthReport report = new KafkaHealthReport();

    // Check connectivity
    report.setConnectivity(checkConnectivity());

    // Check all managed topics
    report.setTopics(checkAllTopics());

    // Check replication health
    report.setReplication(checkReplicationHealth());

    // Check configuration drift
    report.setConfigDrift(checkConfigDrift());

    // Determine overall status
    if (report.hasErrors()) {
      return ResponseEntity.status(503).body(report);
    } else if (report.hasWarnings()) {
      return ResponseEntity.status(200).body(report);
    } else {
      return ResponseEntity.ok(report);
    }
  }

  @GetMapping("/topics/{dataset}/health")
  public ResponseEntity<TopicHealthReport> getTopicHealth(@PathVariable String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);

    TopicHealthReport report = new TopicHealthReport();
    report.setDataset(dataset);
    report.setTopicName(topicName);

    // Check existence
    report.setExists(topicExists(topicName));

    if (report.exists()) {
      // Check replication
      report.setReplicationHealth(checkTopicReplication(topicName));

      // Check configuration
      report.setConfigHealth(checkTopicConfig(topicName, dataset));
    }

    return ResponseEntity.ok(report);
  }

  @PostMapping("/topics/{dataset}/heal")
  public ResponseEntity<HealingResult> healTopic(
      @PathVariable String dataset,
      @RequestParam(defaultValue = "false") boolean confirm) {

    if (!confirm) {
      return ResponseEntity.badRequest()
          .body(new HealingResult("Confirmation required (confirm=true)"));
    }

    // Attempt to heal topic issues
    HealingResult result = topicHealer.heal(dataset);

    if (result.isSuccess()) {
      return ResponseEntity.ok(result);
    } else {
      return ResponseEntity.status(500).body(result);
    }
  }
}
```

### 4. Auto-Healing Logic

**File:** `src/main/java/org/chucc/vcserver/health/TopicHealer.java`

```java
@Service
public class TopicHealer {

  public HealingResult heal(String dataset) {
    HealingResult result = new HealingResult();

    try {
      String topicName = kafkaProperties.getTopicName(dataset);

      // 1. Check if topic exists
      if (!topicExists(topicName)) {
        logger.info("Healing: Recreating missing topic for dataset: {}", dataset);
        recreateTopic(dataset);
        result.addAction("Recreated missing topic: " + topicName);
      }

      // 2. Check configuration drift
      Dataset datasetEntity = datasetRepository.findByName(dataset)
          .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + dataset));

      KafkaTopicConfig expectedConfig = datasetEntity.getKafkaConfig();
      KafkaTopicConfig actualConfig = getActualTopicConfig(topicName);

      if (!expectedConfig.equals(actualConfig)) {
        logger.info("Healing: Correcting configuration drift for topic: {}", topicName);
        correctConfigurationDrift(topicName, expectedConfig, actualConfig);
        result.addAction("Corrected configuration drift");
      }

      // 3. Check partition count
      int expectedPartitions = expectedConfig.partitions();
      int actualPartitions = getPartitionCount(topicName);

      if (actualPartitions < expectedPartitions) {
        logger.info("Healing: Increasing partitions from {} to {} for topic: {}",
            actualPartitions, expectedPartitions, topicName);
        increasePartitions(topicName, expectedPartitions);
        result.addAction("Increased partitions to " + expectedPartitions);
      }

      result.setSuccess(true);
      return result;

    } catch (Exception e) {
      logger.error("Failed to heal topic for dataset: {}", dataset, e);
      result.setSuccess(false);
      result.setError(e.getMessage());
      return result;
    }
  }

  private void recreateTopic(String dataset) {
    Dataset datasetEntity = datasetRepository.findByName(dataset)
        .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + dataset));

    KafkaTopicConfig config = datasetEntity.getKafkaConfig();
    String topicName = kafkaProperties.getTopicName(dataset);

    try (AdminClient adminClient = AdminClient.create(...)) {
      NewTopic newTopic = new NewTopic(
          topicName,
          config.partitions(),
          config.replicationFactor()
      );

      // Set configuration
      Map<String, String> topicConfig = buildTopicConfig(config);
      newTopic.configs(topicConfig);

      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();

      logger.info("Successfully recreated topic: {}", topicName);

      meterRegistry.counter("kafka.topic.recreated",
          "dataset", dataset
      ).increment();

    } catch (Exception e) {
      logger.error("Failed to recreate topic: {}", topicName, e);
      throw new RuntimeException("Topic recreation failed", e);
    }
  }
}
```

---

## Implementation Steps

### Step 1: Create Health Check Service

**File:** `src/main/java/org/chucc/vcserver/health/KafkaTopicHealthChecker.java`

Implement all health check methods.

### Step 2: Create Scheduled Job

**File:** `src/main/java/org/chucc/vcserver/health/KafkaHealthCheckScheduler.java`

```java
@Component
@ConditionalOnProperty(name = "kafka.health-check.enabled", havingValue = "true")
public class KafkaHealthCheckScheduler {

  @Scheduled(fixedRateString = "${kafka.health-check.interval-ms:300000}")
  public void runHealthChecks() {
    checkTopicExistence();
    checkReplicationHealth();
    checkConfigurationDrift();
    checkOrphanTopics();
  }
}
```

### Step 3: Create Health Check Endpoint

Implement `KafkaHealthController` with detailed health status.

### Step 4: Create Auto-Healing Service

Implement `TopicHealer` with safeguards.

### Step 5: Add Configuration

Add health check properties to application.yml.

### Step 6: Add Metrics

Add health check metrics for monitoring.

---

## Testing

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/health/TopicHealerTest.java`

```java
@Test
void heal_missingTopic_shouldRecreateTopic() {
  // Mock: topic doesn't exist
  // Call: healer.heal("test")
  // Verify: topic created with correct config
}

@Test
void heal_configDrift_shouldCorrectConfig() {
  // Mock: topic exists but config is wrong
  // Call: healer.heal("test")
  // Verify: config updated
}

@Test
void heal_partitionCountLow_shouldIncreasePartitions() {
  // Mock: topic has 3 partitions, expected 6
  // Call: healer.heal("test")
  // Verify: partitions increased to 6
}
```

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/health/KafkaHealthCheckIT.java`

```java
@Test
void healthCheck_missingTopic_shouldDetect() throws Exception {
  // Create dataset
  datasetRepository.save(new Dataset("test", ...));

  // Don't create Kafka topic

  // Run health check
  healthChecker.checkTopicExistence();

  // Verify: metric incremented
  double missingCount = meterRegistry.get("kafka.topic.missing").counter().count();
  assertThat(missingCount).isGreaterThan(0);
}

@Test
void autoHeal_missingTopic_shouldRecreate() throws Exception {
  // Enable auto-healing
  healthCheckProperties.getAutoHealing().setEnabled(true);
  healthCheckProperties.getAutoHealing().setRecreateMissingTopics(true);

  // Create dataset without topic
  Dataset dataset = new Dataset("test", ...);
  datasetRepository.save(dataset);

  // Run health check with auto-heal
  healthChecker.checkTopicExistence();

  // Verify: topic now exists
  try (AdminClient adminClient = AdminClient.create(...)) {
    Set<String> topics = adminClient.listTopics().names().get();
    assertThat(topics).contains("vc.test.events");
  }
}
```

---

## Safety Considerations

### 1. Auto-Healing Safeguards

**Require explicit enable:**
```yaml
kafka:
  health-check:
    auto-healing:
      enabled: false  # Must be explicitly enabled
```

**Confirmation required:**
- Auto-healing via API requires `?confirm=true` query parameter
- Prevents accidental execution

**Dry-run mode:**
```java
@GetMapping("/topics/{dataset}/heal-preview")
public ResponseEntity<HealingPreview> previewHealing(@PathVariable String dataset) {
  // Show what would be done, but don't execute
}
```

**Audit log:**
```java
private void logHealingAction(String dataset, String action) {
  auditLog.info("HEALING: dataset={}, action={}, user={}, timestamp={}",
      dataset, action, getCurrentUser(), Instant.now()
  );
}
```

### 2. Rate Limiting

**Prevent healing loops:**
```java
private final Map<String, Instant> lastHealingTime = new ConcurrentHashMap<>();
private static final Duration MIN_HEALING_INTERVAL = Duration.ofMinutes(10);

private boolean canHeal(String dataset) {
  Instant lastHealing = lastHealingTime.get(dataset);
  if (lastHealing != null) {
    Duration timeSince = Duration.between(lastHealing, Instant.now());
    if (timeSince.compareTo(MIN_HEALING_INTERVAL) < 0) {
      logger.warn("Healing rate limit: cannot heal dataset {} again (last healing: {})",
          dataset, lastHealing);
      return false;
    }
  }
  return true;
}
```

### 3. Alerting Before Auto-Healing

**Alert before taking action:**
```java
if (autoHealingEnabled && !topicExists(topicName)) {
  // 1. Alert first
  alertOpsTeam("Topic missing for dataset: " + dataset +
      ". Auto-healing will trigger in 5 minutes.");

  // 2. Wait for confirmation or timeout
  scheduledExecutor.schedule(() -> {
    if (autoHealingConfirmed(dataset) || confirmationTimeout) {
      recreateMissingTopic(dataset);
    }
  }, 5, TimeUnit.MINUTES);
}
```

---

## Acceptance Criteria

- [ ] Health check detects missing topics
- [ ] Health check detects under-replicated partitions
- [ ] Health check detects configuration drift
- [ ] Health check detects orphan topics
- [ ] Auto-healing can recreate missing topics (when enabled)
- [ ] Auto-healing can correct configuration drift (when enabled)
- [ ] Auto-healing requires explicit enable + confirmation
- [ ] `/actuator/kafka/health-detailed` returns comprehensive health report
- [ ] `/actuator/kafka/topics/{dataset}/health` returns topic-specific health
- [ ] `/actuator/kafka/topics/{dataset}/heal` manually triggers healing
- [ ] Metrics track health check results
- [ ] Rate limiting prevents healing loops
- [ ] Audit log records all healing actions
- [ ] Unit and integration tests pass
- [ ] Documentation updated

---

## Related Tasks

- **Previous:** [05-per-dataset-tuning.md](./05-per-dataset-tuning.md)
- **Depends on:** Tasks 01-04 (dataset lifecycle, metrics, error handling)

---

## References

- [Kafka AdminClient](https://kafka.apache.org/34/javadoc/org/apache/kafka/clients/admin/AdminClient.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kafka Monitoring](https://kafka.apache.org/documentation/#monitoring)
- [Under-Replicated Partitions](https://kafka.apache.org/documentation/#design_replicatedlog)
