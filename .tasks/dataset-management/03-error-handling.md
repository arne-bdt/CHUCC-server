# Task: Robust Error Handling for Topic Creation

**Status:** Not Started
**Priority:** Important
**Category:** Error Handling / Resilience
**Estimated Time:** 3-4 hours

---

## Overview

Implement comprehensive error handling for Kafka topic creation and management:
- Handle topic-already-exists (idempotent operations)
- Handle Kafka broker unavailable
- Handle quota exceeded / authorization failures
- Rollback on partial failures
- Retry strategies
- Clear error messages to users

**Problem:** Topic creation can fail for many reasons. Without proper error handling, users get cryptic errors or partial dataset creation.

---

## Current State

**Basic Error Handling Exists:**
- ✅ Topic deletion catches exceptions ([DeleteDatasetCommandHandler.java:157-168](../../src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java#L157-L168))
- ❌ No retry logic for transient failures
- ❌ No rollback on partial failures
- ❌ Generic error messages (not user-friendly)
- ❌ No distinction between recoverable vs. fatal errors

---

## Error Scenarios

### 1. Topic Already Exists

**Scenario:** User tries to create dataset "mydata" when topic `vc.mydata.events` already exists.

**Current Behavior:** Exception thrown → 500 Internal Server Error

**Expected Behavior:**
- **If dataset exists in database:** Return `409 Conflict` (dataset already exists)
- **If topic exists but dataset doesn't:** Try to create dataset (topic creation is idempotent)
- **If both exist:** Return `409 Conflict`

**Implementation:**
```java
catch (ExecutionException e) {
  if (e.getCause() instanceof TopicExistsException) {
    logger.warn("Topic already exists: {} - checking dataset consistency", topicName);

    // Check if dataset exists in repository
    boolean datasetExists = datasetRepository.existsByName(dataset);

    if (datasetExists) {
      throw new DatasetAlreadyExistsException(dataset);  // 409
    } else {
      // Topic exists but dataset doesn't - inconsistent state!
      logger.warn("Orphaned topic found: {} - proceeding with dataset creation", topicName);
      // Continue with dataset creation (topic is already there)
    }
  }
}
```

### 2. Kafka Broker Unavailable

**Scenario:** Kafka cluster is down or unreachable.

**Error:** `org.apache.kafka.common.errors.TimeoutException`

**Expected Behavior:**
- Retry 3 times with exponential backoff (1s, 2s, 4s)
- If still fails, return `503 Service Unavailable`
- Log error with correlation ID for debugging

**Implementation:**
```java
@Retryable(
    value = {TimeoutException.class, LeaderNotAvailableException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
private void createKafkaTopicWithRetry(String dataset) throws KafkaException {
  // Topic creation logic
}

// Fallback handler
@Recover
private void recoverFromKafkaFailure(KafkaException e, String dataset) {
  logger.error("Failed to create Kafka topic after retries: {}", dataset, e);
  throw new KafkaUnavailableException(
      "Kafka cluster is unavailable. Please try again later.", e
  );
}
```

### 3. Authorization Failure

**Scenario:** Application doesn't have permission to create topics.

**Error:** `org.apache.kafka.common.errors.TopicAuthorizationException`

**Expected Behavior:**
- Do NOT retry (fatal error)
- Return `500 Internal Server Error` with clear message
- Alert operations team (log at ERROR level)

**Implementation:**
```java
catch (ExecutionException e) {
  if (e.getCause() instanceof TopicAuthorizationException) {
    logger.error("CRITICAL: No permission to create topic: {}", topicName);
    throw new KafkaAuthorizationException(
        "Server configuration error: insufficient permissions to create dataset", e
    );
  }
}
```

### 4. Quota Exceeded

**Scenario:** Too many partitions or topics created (Kafka quota limit).

**Error:** `org.apache.kafka.common.errors.PolicyViolationException`

**Expected Behavior:**
- Return `429 Too Many Requests` or `507 Insufficient Storage`
- Include Retry-After header
- Clear error message explaining quota

**Implementation:**
```java
catch (ExecutionException e) {
  if (e.getCause() instanceof PolicyViolationException) {
    logger.warn("Kafka quota exceeded when creating topic: {}", topicName);
    throw new KafkaQuotaExceededException(
        "Dataset creation failed: Kafka storage quota exceeded. Please contact administrator."
    );
  }
}
```

### 5. Invalid Topic Configuration

**Scenario:** Replication factor > available brokers (e.g., RF=3 but only 2 brokers).

**Error:** `org.apache.kafka.common.errors.InvalidReplicationFactorException`

**Expected Behavior:**
- Return `500 Internal Server Error`
- Clear message: "Insufficient Kafka brokers for replication"
- Log configuration mismatch

**Implementation:**
```java
catch (ExecutionException e) {
  if (e.getCause() instanceof InvalidReplicationFactorException) {
    logger.error("Invalid replication factor {} for topic: {} (insufficient brokers?)",
        kafkaProperties.getReplicationFactor(), topicName);
    throw new InvalidKafkaConfigurationException(
        "Server configuration error: replication factor exceeds available brokers"
    );
  }
}
```

### 6. Partial Failure (Topic Created, Dataset Save Failed)

**Scenario:**
1. Topic `vc.mydata.events` created successfully
2. Database save fails (constraint violation, DB unavailable, etc.)
3. Topic exists but dataset doesn't → inconsistent state

**Expected Behavior:**
- **Option A (Rollback):** Delete the topic if dataset creation fails
- **Option B (Idempotent):** Leave topic, next retry will detect and reuse it

**Implementation (Option A - Rollback):**
```java
public DatasetCreatedEvent handle(CreateDatasetCommand command) {
  String dataset = command.dataset();
  boolean topicCreated = false;

  try {
    // 1. Create Kafka topic
    createKafkaTopic(dataset);
    topicCreated = true;

    // 2. Create initial commit
    CommitId initialCommitId = createInitialCommit(dataset);

    // 3. Create main branch
    Branch mainBranch = createMainBranch(dataset, initialCommitId);

    // 4. Save to repositories
    commitRepository.save(dataset, initialCommit);
    branchRepository.save(dataset, mainBranch);

    // Success!
    return new DatasetCreatedEvent(...);

  } catch (Exception e) {
    // Rollback: Delete topic if it was created
    if (topicCreated) {
      logger.warn("Dataset creation failed - rolling back topic creation: {}", dataset);
      try {
        deleteKafkaTopic(dataset);
      } catch (Exception rollbackEx) {
        logger.error("Failed to rollback topic deletion: {}", dataset, rollbackEx);
        // Alert ops: manual cleanup needed
      }
    }
    throw new DatasetCreationException("Failed to create dataset: " + dataset, e);
  }
}
```

---

## Implementation Steps

### Step 1: Create Custom Exceptions

**File:** `src/main/java/org/chucc/vcserver/exception/kafka/`

Create exception hierarchy:
```java
// Base exception
public class KafkaException extends RuntimeException {
  public KafkaException(String message, Throwable cause) {
    super(message, cause);
  }
}

// Specific exceptions
public class KafkaUnavailableException extends KafkaException { }
public class KafkaAuthorizationException extends KafkaException { }
public class KafkaQuotaExceededException extends KafkaException { }
public class InvalidKafkaConfigurationException extends KafkaException { }
public class TopicCreationException extends KafkaException { }

// Dataset exceptions
public class DatasetAlreadyExistsException extends RuntimeException {
  public DatasetAlreadyExistsException(String dataset) {
    super("Dataset already exists: " + dataset);
  }
}
```

### Step 2: Update GlobalExceptionHandler

**File:** `src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java`

Map exceptions to HTTP status codes:
```java
@ExceptionHandler(DatasetAlreadyExistsException.class)
public ResponseEntity<ProblemDetail> handleDatasetAlreadyExists(
    DatasetAlreadyExistsException ex,
    WebRequest request) {

  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      ex.getMessage()
  );
  problem.setType(URI.create("/errors/dataset-already-exists"));
  problem.setTitle("Dataset Already Exists");
  problem.setProperty("dataset", extractDatasetName(ex.getMessage()));

  return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}

@ExceptionHandler(KafkaUnavailableException.class)
public ResponseEntity<ProblemDetail> handleKafkaUnavailable(
    KafkaUnavailableException ex,
    WebRequest request) {

  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.SERVICE_UNAVAILABLE,
      "Event store is temporarily unavailable"
  );
  problem.setType(URI.create("/errors/kafka-unavailable"));
  problem.setTitle("Event Store Unavailable");
  problem.setProperty("retryAfter", 30);  // seconds

  return ResponseEntity
      .status(HttpStatus.SERVICE_UNAVAILABLE)
      .header("Retry-After", "30")
      .body(problem);
}

@ExceptionHandler(KafkaAuthorizationException.class)
public ResponseEntity<ProblemDetail> handleKafkaAuthorization(
    KafkaAuthorizationException ex) {

  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Server configuration error"
  );
  problem.setType(URI.create("/errors/kafka-authorization"));
  problem.setTitle("Insufficient Permissions");

  // Alert ops team
  alertOpsTeam("Kafka authorization failure", ex);

  return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
}

@ExceptionHandler(KafkaQuotaExceededException.class)
public ResponseEntity<ProblemDetail> handleKafkaQuotaExceeded(
    KafkaQuotaExceededException ex) {

  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.INSUFFICIENT_STORAGE,  // 507
      ex.getMessage()
  );
  problem.setType(URI.create("/errors/kafka-quota-exceeded"));
  problem.setTitle("Storage Quota Exceeded");

  return ResponseEntity.status(507).body(problem);
}
```

### Step 3: Implement Retry Logic

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommandHandler.java`

Add Spring Retry support:
```java
@Service
@EnableRetry
public class CreateDatasetCommandHandler {

  @Retryable(
      value = {TimeoutException.class, LeaderNotAvailableException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 5000)
  )
  private void createKafkaTopicWithRetry(String dataset) {
    logger.info("Attempting to create Kafka topic for dataset: {}", dataset);
    createKafkaTopic(dataset);
  }

  @Recover
  private void recoverFromTopicCreationFailure(Exception e, String dataset) {
    logger.error("Failed to create topic after {} retries: {}", 3, dataset, e);
    throw new KafkaUnavailableException(
        "Event store is unavailable. Please try again later.", e
    );
  }
}
```

**Add dependency** to `pom.xml`:
```xml
<dependency>
  <groupId>org.springframework.retry</groupId>
  <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-aspects</artifactId>
</dependency>
```

### Step 4: Implement Rollback Logic

Add transaction-like behavior:
```java
private void rollbackTopicCreation(String dataset) {
  try {
    String topicName = kafkaProperties.getTopicName(dataset);
    logger.warn("Rolling back topic creation: {}", topicName);

    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
      logger.info("Successfully rolled back topic: {}", topicName);
    }
  } catch (Exception e) {
    logger.error("CRITICAL: Failed to rollback topic creation for dataset: {}",
        dataset, e);
    // Alert operations team for manual cleanup
    alertOpsTeam("Manual cleanup required for dataset: " + dataset, e);
  }
}
```

### Step 5: Add Validation

Validate dataset name and Kafka configuration before attempting topic creation:
```java
private void validateDatasetCreation(CreateDatasetCommand command) {
  String dataset = command.dataset();

  // 1. Validate dataset name
  validateDatasetName(dataset);

  // 2. Check if dataset already exists
  if (datasetRepository.existsByName(dataset)) {
    throw new DatasetAlreadyExistsException(dataset);
  }

  // 3. Validate Kafka configuration
  validateKafkaConfiguration();
}

private void validateKafkaConfiguration() {
  // Check broker connectivity
  try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    adminClient.describeCluster().nodes().get(5, TimeUnit.SECONDS);
  } catch (TimeoutException e) {
    throw new KafkaUnavailableException("Cannot connect to Kafka cluster", e);
  } catch (Exception e) {
    throw new InvalidKafkaConfigurationException("Invalid Kafka configuration", e);
  }
}
```

### Step 6: Add Health Check

**File:** `src/main/java/org/chucc/vcserver/health/KafkaHealthIndicator.java`

```java
@Component
public class KafkaHealthIndicator implements HealthIndicator {

  private final KafkaAdmin kafkaAdmin;

  @Override
  public Health health() {
    try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
      Collection<Node> nodes = adminClient.describeCluster()
          .nodes()
          .get(5, TimeUnit.SECONDS);

      return Health.up()
          .withDetail("brokers", nodes.size())
          .withDetail("status", "connected")
          .build();

    } catch (TimeoutException e) {
      return Health.down()
          .withDetail("status", "timeout")
          .withException(e)
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("status", "error")
          .withException(e)
          .build();
    }
  }
}
```

---

## Testing

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/command/CreateDatasetCommandHandlerErrorTest.java`

Test cases:
```java
@Test
void createDataset_topicAlreadyExists_shouldReturnConflict() {
  // Mock AdminClient to throw TopicExistsException
  // Mock repository to return dataset exists
  // Assert: DatasetAlreadyExistsException thrown
}

@Test
void createDataset_kafkaUnavailable_shouldRetry3Times() {
  // Mock AdminClient to throw TimeoutException
  // Verify: createKafkaTopic called 3 times
  // Assert: KafkaUnavailableException thrown after retries
}

@Test
void createDataset_authorizationFailure_shouldNotRetry() {
  // Mock AdminClient to throw TopicAuthorizationException
  // Verify: createKafkaTopic called only once (no retry)
  // Assert: KafkaAuthorizationException thrown
}

@Test
void createDataset_databaseSaveFails_shouldRollbackTopic() {
  // Topic creation succeeds
  // Repository.save() throws exception
  // Verify: deleteKafkaTopic called (rollback)
}
```

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/DatasetCreationErrorIT.java`

Test cases:
```java
@Test
void createDataset_duplicate_shouldReturn409() {
  // Create dataset "test"
  // Try to create again
  // Assert: 409 Conflict
}

@Test
void createDataset_kafkaDown_shouldReturn503() {
  // Stop Kafka container
  // Try to create dataset
  // Assert: 503 Service Unavailable
  // Verify: Retry-After header present
}
```

---

## Monitoring & Alerting

### Metrics

Add Micrometer counters:
```java
@Counted(value = "dataset.creation.failures", description = "Dataset creation failures")
@Timed(value = "dataset.creation.time", description = "Dataset creation time")
public DatasetCreatedEvent handle(CreateDatasetCommand command) {
  // Implementation
}

// In error handlers
meterRegistry.counter("kafka.topic.creation.failures",
    "reason", e.getClass().getSimpleName()
).increment();
```

### Logging

Structured logging with correlation IDs:
```java
logger.error("Dataset creation failed: dataset={}, reason={}, correlationId={}",
    dataset,
    e.getClass().getSimpleName(),
    MDC.get("correlationId"),
    e
);
```

### Alerts

Configure alerts for:
- `kafka.topic.creation.failures > 5 per minute` → Alert ops team
- `KafkaAuthorizationException` → Immediate page (critical config error)
- `KafkaQuotaExceededException` → Alert capacity planning team

---

## Acceptance Criteria

- [ ] All Kafka exceptions mapped to appropriate HTTP status codes
- [ ] Topic-already-exists is handled idempotently (no error if consistent)
- [ ] Transient failures (timeout, leader unavailable) retry 3 times with backoff
- [ ] Fatal failures (authorization) do not retry
- [ ] Partial failures (topic created, DB save failed) rollback topic creation
- [ ] Clear, user-friendly error messages in RFC 7807 format
- [ ] Health check endpoint reports Kafka connectivity status
- [ ] Metrics track failure reasons
- [ ] Unit tests cover all error scenarios
- [ ] Integration tests verify error responses
- [ ] Documentation updated with error codes and troubleshooting

---

## Related Tasks

- **Previous:** [02-production-kafka-config.md](./02-production-kafka-config.md)
- **Next:** [04-monitoring-metrics.md](./04-monitoring-metrics.md)
- **Depends on:** Task 01 (dataset creation endpoint)

---

## References

- [Spring Retry Documentation](https://docs.spring.io/spring-retry/docs/current/reference/html/)
- [Kafka AdminClient Exceptions](https://kafka.apache.org/34/javadoc/org/apache/kafka/common/errors/package-summary.html)
- [RFC 7807 Problem Details](https://datatracker.ietf.org/doc/html/rfc7807)
- [GlobalExceptionHandler.java](../../src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java)
