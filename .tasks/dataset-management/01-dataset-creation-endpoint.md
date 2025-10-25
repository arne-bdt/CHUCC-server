# Task: Implement Dataset Creation Endpoint with Kafka Topic Creation

**Status:** Not Started
**Priority:** Critical
**Category:** Dataset Management / Kafka Integration
**Estimated Time:** 4-5 hours

---

## Overview

Implement explicit dataset creation endpoint that:
- Creates Kafka topic for the dataset
- Creates initial commit with empty dataset
- Creates default "main" branch
- Publishes `DatasetCreatedEvent`

**Problem:** Currently, topics are never created in production code. Without explicit dataset creation, users cannot dynamically create datasets without DevOps intervention.

---

## Current State

**Missing Components:**
- ❌ No `POST /version/datasets/{name}` endpoint
- ❌ No Kafka topic creation in production code
- ❌ `DatasetService.createDataset()` exists but is never called (lines 183-214)
- ✅ Topic deletion already implemented in `DeleteDatasetCommandHandler`

**Existing Infrastructure:**
- ✅ `KafkaAdmin` bean configured ([KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java))
- ✅ `KafkaProperties` has topic template and config ([KafkaProperties.java](../../src/main/java/org/chucc/vcserver/config/KafkaProperties.java))
- ✅ Topic deletion pattern exists ([DeleteDatasetCommandHandler.java:141-169](../../src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java#L141-L169))

---

## Requirements

### 1. API Endpoint

**POST /version/datasets/{name}**

**Request:**
```http
POST /version/datasets/mydata HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "description": "My new dataset",
  "initialGraph": "http://example.org/graph/default"
}
```

**Success Response (201 Created):**
```http
HTTP/1.1 201 Created
Location: /version/datasets/mydata
Content-Type: application/json

{
  "name": "mydata",
  "description": "My new dataset",
  "mainBranch": "main",
  "initialCommitId": "abc123...",
  "kafkaTopic": "vc.mydata.events",
  "createdAt": "2025-10-25T12:00:00Z"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid dataset name (contains invalid Kafka topic characters)
- `409 Conflict` - Dataset already exists
- `500 Internal Server Error` - Kafka topic creation failed

---

## Implementation Steps

### Step 1: Create Domain Model (if needed)

**File:** `src/main/java/org/chucc/vcserver/domain/Dataset.java` (may already exist in repositories)

Check if a `Dataset` entity/model exists. If not, create minimal model:
```java
public class Dataset {
  private String name;
  private String description;
  private String mainBranch;
  private CommitId initialCommitId;
  private Instant createdAt;
  // getters, setters, equals, hashCode
}
```

### Step 2: Create Command

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommand.java`

```java
public record CreateDatasetCommand(
    String dataset,
    String description,
    String author,
    Optional<String> initialGraph
) implements VersionControlCommand {
  // Validation in constructor
}
```

### Step 3: Create Event

**File:** `src/main/java/org/chucc/vcserver/event/DatasetCreatedEvent.java`

```java
public record DatasetCreatedEvent(
    String eventId,
    String dataset,
    String mainBranch,
    String initialCommitId,
    String description,
    String author,
    Instant timestamp
) implements VersionControlEvent {

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.dataset(dataset);
  }
}
```

### Step 4: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/CreateDatasetCommandHandler.java`

**Key responsibilities:**
1. Validate dataset name (no invalid Kafka characters: `/`, `\`, `,`, ` `, etc.)
2. Check if dataset already exists (query repository)
3. **Create Kafka topic** using `AdminClient`
4. Create initial empty commit
5. Create main branch pointing to initial commit
6. Save to repositories
7. Create and return `DatasetCreatedEvent`

**Kafka Topic Creation Logic:**
```java
private void createKafkaTopic(String dataset) {
  String topicName = kafkaProperties.getTopicName(dataset);

  try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
    NewTopic newTopic = new NewTopic(
        topicName,
        kafkaProperties.getPartitions(),
        kafkaProperties.getReplicationFactor()
    );

    // Set topic config
    Map<String, String> config = new HashMap<>();
    config.put("retention.ms", String.valueOf(kafkaProperties.getRetentionMs()));
    config.put("cleanup.policy", kafkaProperties.isCompaction() ? "compact" : "delete");
    // Add min.insync.replicas only if replication-factor > 1
    if (kafkaProperties.getReplicationFactor() > 1) {
      config.put("min.insync.replicas", "2");
    }
    newTopic.configs(config);

    adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
    logger.info("Created Kafka topic: {}", topicName);
  } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new DatasetCreationException("Failed to create topic: " + topicName, e);
  } catch (ExecutionException e) {
    if (e.getCause() instanceof TopicExistsException) {
      logger.warn("Topic already exists: {}", topicName);
      // This is okay - idempotent
    } else {
      throw new DatasetCreationException("Failed to create topic: " + topicName, e);
    }
  }
}
```

**Reference:** Use [DeleteDatasetCommandHandler.deleteKafkaTopic()](../../src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java#L141-L169) as pattern.

### Step 5: Create Controller

**File:** `src/main/java/org/chucc/vcserver/controller/DatasetController.java`

```java
@RestController
@RequestMapping("/version/datasets")
public class DatasetController {

  @PostMapping("/{dataset}")
  public ResponseEntity<DatasetCreationResponse> createDataset(
      @PathVariable String dataset,
      @RequestBody(required = false) CreateDatasetRequest request,
      @RequestHeader(value = "SPARQL-VC-Author", required = false) String author
  ) {
    // Validate author
    // Create command
    // Execute command handler
    // Publish event
    // Return 201 Created with Location header
  }

  @GetMapping("/{dataset}")
  public ResponseEntity<DatasetInfo> getDataset(@PathVariable String dataset) {
    // Get dataset metadata from repository
    // Return dataset info
  }

  @GetMapping
  public ResponseEntity<List<DatasetInfo>> listDatasets() {
    // List all datasets
  }
}
```

### Step 6: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add handler for `DatasetCreatedEvent`:
```java
private void handleDatasetCreated(DatasetCreatedEvent event) {
  // Update dataset repository/metadata
  logger.info("Projected DatasetCreatedEvent: {}", event.dataset());
}
```

### Step 7: Error Handling

Create custom exception:
```java
public class DatasetCreationException extends RuntimeException {
  public DatasetCreationException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

Map to RFC 7807 error in `GlobalExceptionHandler`.

### Step 8: Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/DatasetCreationIT.java`

Test cases:
- ✅ Create dataset - success (201)
- ✅ Create dataset with invalid name - failure (400)
- ✅ Create dataset that already exists - conflict (409)
- ✅ Verify Kafka topic was created
- ✅ Verify main branch was created
- ✅ Verify initial commit exists
- ✅ Create dataset, then delete it, then recreate - success (idempotent)

**File:** `src/test/java/org/chucc/vcserver/command/CreateDatasetCommandHandlerTest.java`

Unit tests for command handler logic.

---

## Validation Rules

### Dataset Name Validation

**Invalid characters for Kafka topics:**
- `/`, `\`, `,`, ` ` (space), `\0`, `\n`, `\r`, `\t`
- Must not be `.` or `..`
- Max length: 249 characters
- Pattern: `^[a-zA-Z0-9._-]+$`

**Implementation:**
```java
private static final Pattern VALID_DATASET_NAME =
    Pattern.compile("^[a-zA-Z0-9._-]+$");

private void validateDatasetName(String dataset) {
  if (dataset == null || dataset.isEmpty()) {
    throw new IllegalArgumentException("Dataset name cannot be empty");
  }
  if (dataset.equals(".") || dataset.equals("..")) {
    throw new IllegalArgumentException("Dataset name cannot be '.' or '..'");
  }
  if (dataset.length() > 249) {
    throw new IllegalArgumentException("Dataset name too long (max 249 chars)");
  }
  if (!VALID_DATASET_NAME.matcher(dataset).matches()) {
    throw new IllegalArgumentException(
        "Dataset name contains invalid characters. Allowed: a-z, A-Z, 0-9, ., _, -"
    );
  }
}
```

---

## OpenAPI Documentation

**File:** `src/main/resources/openapi/openapi.yaml`

Add endpoint definition:
```yaml
/version/datasets/{dataset}:
  post:
    summary: Create a new dataset
    operationId: createDataset
    tags:
      - Dataset Management
    parameters:
      - name: dataset
        in: path
        required: true
        schema:
          type: string
          pattern: '^[a-zA-Z0-9._-]+$'
          maxLength: 249
    requestBody:
      required: false
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/CreateDatasetRequest'
    responses:
      '201':
        description: Dataset created successfully
        headers:
          Location:
            schema:
              type: string
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/DatasetCreationResponse'
      '400':
        $ref: '#/components/responses/BadRequest'
      '409':
        $ref: '#/components/responses/Conflict'
```

---

## Testing Checklist

- [ ] Unit test: `CreateDatasetCommandHandlerTest`
- [ ] Integration test: `DatasetCreationIT` (API layer, projector disabled)
- [ ] Integration test: `DatasetProjectorIT` (projector enabled, verify event projection)
- [ ] Integration test: Verify Kafka topic exists after creation
- [ ] Integration test: Create → Delete → Recreate (idempotent)
- [ ] Test invalid dataset names (special characters)
- [ ] Test dataset name too long
- [ ] Test duplicate dataset creation
- [ ] Test topic creation failure handling
- [ ] Manual test: Verify topic in Kafka using `kafka-topics.sh --list`

---

## Acceptance Criteria

- [ ] `POST /version/datasets/{name}` returns 201 with dataset metadata
- [ ] Kafka topic `vc.{name}.events` is created automatically
- [ ] Initial commit is created with empty dataset
- [ ] Main branch is created pointing to initial commit
- [ ] `DatasetCreatedEvent` is published to Kafka
- [ ] Event is projected to read model repositories
- [ ] Invalid dataset names return 400 with clear error message
- [ ] Duplicate dataset creation returns 409
- [ ] Topic creation failures return 500 with error details
- [ ] All tests pass
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] OpenAPI documentation updated

---

## Related Tasks

- **Next:** [02-production-kafka-config.md](./02-production-kafka-config.md) - Update Kafka config for production
- **Depends on:** None (foundational task)
- **Blocked by:** None

---

## References

- [KafkaProperties.java](../../src/main/java/org/chucc/vcserver/config/KafkaProperties.java)
- [DeleteDatasetCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java) - Topic deletion pattern
- [EventPublisher.java](../../src/main/java/org/chucc/vcserver/event/EventPublisher.java) - Topic routing logic
- [AggregateIdentity.java](../../src/main/java/org/chucc/vcserver/event/AggregateIdentity.java) - Partition key logic
- [Kafka Topic Naming](https://kafka.apache.org/documentation/#topicconfigs) - Official docs
