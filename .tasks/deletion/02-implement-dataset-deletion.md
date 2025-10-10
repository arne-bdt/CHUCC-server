# Task: Implement Dataset Deletion with Optional Kafka Topic Cleanup

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1-2 sessions (4-6 hours)
**Dependencies:** Task 01 (Branch deletion - for deleting all branches first)

---

## Context

Dataset deletion is currently NOT implemented. There's no endpoint for it yet.

**Goal:** Implement dataset deletion that:
1. Deletes all branches in the dataset
2. Clears all in-memory data (commits, caches)
3. **Optionally** deletes the Kafka topic
4. Follows CQRS + Event Sourcing pattern

---

## Design Decisions

### What Gets Deleted?

| Item | Action | Configurable? |
|------|--------|---------------|
| **All branches** | ✅ Deleted | No (always) |
| **BranchRepository entries** | ✅ Deleted | No (always) |
| **CommitRepository entries** | ✅ Deleted | No (always) |
| **DatasetService cache** | ✅ Cleared | No (always) |
| **Kafka topic** | ⚠️ Optional | ✅ Yes (config) |
| **Kafka events** | ⚠️ Optional | ✅ Yes (config) |

### Kafka Topic Deletion Options

**Option 1: Never delete topic (default - safest)**
- Topic and all events remain
- Can restore dataset by replaying events
- Good for audit/compliance

**Option 2: Delete topic (destructive)**
- Topic and all events permanently deleted
- Cannot restore dataset
- Frees Kafka storage

**Configuration:**
```yaml
vc:
  dataset-deletion:
    delete-kafka-topic: false  # Default: keep topic for audit trail
```

### Protection Rules

- ❌ Cannot delete dataset with name "default" (system dataset)
- ⚠️ Require confirmation parameter to prevent accidental deletion
- ⚠️ Check if snapshots exist and warn user

---

## Implementation Plan

### Step 1: Create DeleteDatasetCommand

**File:** `src/main/java/org/chucc/vcserver/command/DeleteDatasetCommand.java`

```java
package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to delete an entire dataset.
 */
public record DeleteDatasetCommand(
    String dataset,
    String author,
    boolean deleteKafkaTopic,
    boolean confirmed) {  // Require explicit confirmation

  public DeleteDatasetCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
```

---

### Step 2: Create DatasetDeletedEvent

**File:** `src/main/java/org/chucc/vcserver/event/DatasetDeletedEvent.java`

```java
package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Event representing the deletion of an entire dataset.
 */
public record DatasetDeletedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("deletedBranches") List<String> deletedBranches,
    @JsonProperty("deletedCommitCount") int deletedCommitCount,
    @JsonProperty("kafkaTopicDeleted") boolean kafkaTopicDeleted)
    implements VersionControlEvent {

  public DatasetDeletedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    Objects.requireNonNull(deletedBranches, "Deleted branches cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }

    // Defensive copy
    deletedBranches = List.copyOf(deletedBranches);
  }
}
```

**Update VersionControlEvent interface and KafkaConfig** (similar to BranchDeleted).

---

### Step 3: Create DeleteDatasetCommandHandler

**File:** `src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java`

```java
package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.DatasetDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.ProtectedDatasetException;
import org.chucc.vcserver.exception.UnconfirmedDeletionException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Handles deletion of entire datasets.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement")
public class DeleteDatasetCommandHandler {
  private static final Logger logger = LoggerFactory.getLogger(DeleteDatasetCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;
  private final EventPublisher eventPublisher;
  private final KafkaAdmin kafkaAdmin;
  private final KafkaProperties kafkaProperties;
  private final VersionControlProperties vcProperties;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public DeleteDatasetCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService,
      EventPublisher eventPublisher,
      KafkaAdmin kafkaAdmin,
      KafkaProperties kafkaProperties,
      VersionControlProperties vcProperties) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
    this.eventPublisher = eventPublisher;
    this.kafkaAdmin = kafkaAdmin;
    this.kafkaProperties = kafkaProperties;
    this.vcProperties = vcProperties;
  }

  /**
   * Handles dataset deletion command.
   *
   * @param command the delete dataset command
   * @return the dataset deleted event
   * @throws DatasetNotFoundException if dataset doesn't exist
   * @throws ProtectedDatasetException if trying to delete protected dataset
   * @throws UnconfirmedDeletionException if deletion not confirmed
   */
  public DatasetDeletedEvent handle(DeleteDatasetCommand command) {
    logger.warn("Deleting dataset: {} (deleteKafkaTopic={}, confirmed={})",
        command.dataset(), command.deleteKafkaTopic(), command.confirmed());

    // 1. Require confirmation
    if (!command.confirmed()) {
      throw new UnconfirmedDeletionException(
          "Dataset deletion requires explicit confirmation (confirmed=true)");
    }

    // 2. Protect system dataset
    if ("default".equals(command.dataset())) {
      throw new ProtectedDatasetException("Cannot delete default dataset");
    }

    // 3. Check if dataset exists (has branches)
    List<Branch> branches = branchRepository.findAllByDataset(command.dataset());
    if (branches.isEmpty()) {
      throw new DatasetNotFoundException("Dataset not found or already empty: " + command.dataset());
    }

    // 4. Count commits for audit
    int commitCount = commitRepository.findAllByDataset(command.dataset()).size();

    // 5. Create event BEFORE deletion (for audit trail)
    DatasetDeletedEvent event = new DatasetDeletedEvent(
        command.dataset(),
        command.author(),
        Instant.now(),
        branches.stream().map(Branch::getName).toList(),
        commitCount,
        command.deleteKafkaTopic()
    );

    // 6. Publish event (async)
    eventPublisher.publish(event);

    // 7. Optionally delete Kafka topic (destructive!)
    if (command.deleteKafkaTopic() && vcProperties.isAllowKafkaTopicDeletion()) {
      deleteKafkaTopic(command.dataset());
    } else if (command.deleteKafkaTopic()) {
      logger.warn("Kafka topic deletion requested but not allowed by configuration");
    }

    logger.warn("Dataset {} deleted: {} branches, {} commits, Kafka topic deleted: {}",
        command.dataset(), branches.size(), commitCount, command.deleteKafkaTopic());

    return event;
  }

  /**
   * Deletes the Kafka topic for the dataset.
   * This is destructive and irreversible!
   */
  private void deleteKafkaTopic(String dataset) {
    String topicName = kafkaProperties.getTopicName(dataset);

    try {
      logger.warn("Deleting Kafka topic: {}", topicName);

      DeleteTopicsResult result = kafkaAdmin.deleteTopics(List.of(topicName));
      result.all().get();  // Wait for deletion to complete

      logger.warn("Kafka topic deleted: {}", topicName);
    } catch (ExecutionException | InterruptedException e) {
      logger.error("Failed to delete Kafka topic: {}", topicName, e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      // Don't throw - deletion already recorded in event
    }
  }
}
```

**Create exception classes:**
```java
// src/main/java/org/chucc/vcserver/exception/ProtectedDatasetException.java
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProtectedDatasetException extends RuntimeException {
  public ProtectedDatasetException(String message) {
    super(message);
  }
}

// src/main/java/org/chucc/vcserver/exception/UnconfirmedDeletionException.java
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UnconfirmedDeletionException extends RuntimeException {
  public UnconfirmedDeletionException(String message) {
    super(message);
  }
}
```

---

### Step 4: Add Configuration

**File:** `src/main/java/org/chucc/vcserver/config/VersionControlProperties.java`

```java
/**
 * Whether to allow Kafka topic deletion when deleting datasets.
 * Default: false (keep topics for audit trail).
 */
private boolean allowKafkaTopicDeletion = false;

public boolean isAllowKafkaTopicDeletion() {
  return allowKafkaTopicDeletion;
}

public void setAllowKafkaTopicDeletion(boolean allowKafkaTopicDeletion) {
  this.allowKafkaTopicDeletion = allowKafkaTopicDeletion;
}
```

**application.yml:**
```yaml
vc:
  # ... existing config
  allow-kafka-topic-deletion: false  # Never delete topics by default
```

---

### Step 5: Create Controller Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/DatasetController.java`

```java
package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.DeleteDatasetCommand;
import org.chucc.vcserver.command.DeleteDatasetCommandHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dataset management endpoints.
 */
@RestController
@RequestMapping("/version/datasets")
@Tag(name = "Version Control", description = "Dataset management operations")
public class DatasetController {

  private final DeleteDatasetCommandHandler deleteDatasetCommandHandler;

  public DatasetController(DeleteDatasetCommandHandler deleteDatasetCommandHandler) {
    this.deleteDatasetCommandHandler = deleteDatasetCommandHandler;
  }

  /**
   * Delete a dataset.
   *
   * @param name dataset name
   * @param deleteKafkaTopic whether to delete Kafka topic (destructive!)
   * @param confirmed confirmation flag (required)
   * @param author author of the deletion
   * @return no content (204)
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete dataset",
      description = "Deletes an entire dataset including all branches, commits, and caches. "
          + "Optionally deletes the Kafka topic (irreversible!). "
          + "Requires explicit confirmation to prevent accidental deletion."
  )
  @ApiResponse(responseCode = "204", description = "Dataset deleted successfully")
  @ApiResponse(responseCode = "400", description = "Deletion not confirmed",
      content = @Content(mediaType = "application/problem+json"))
  @ApiResponse(responseCode = "403", description = "Cannot delete protected dataset (default)",
      content = @Content(mediaType = "application/problem+json"))
  @ApiResponse(responseCode = "404", description = "Dataset not found",
      content = @Content(mediaType = "application/problem+json"))
  public ResponseEntity<Void> deleteDataset(
      @Parameter(description = "Dataset name", required = true)
      @PathVariable String name,
      @Parameter(description = "Delete Kafka topic (destructive!)")
      @RequestParam(defaultValue = "false") boolean deleteKafkaTopic,
      @Parameter(description = "Confirmation flag (must be true)", required = true)
      @RequestParam(defaultValue = "false") boolean confirmed,
      @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author) {

    // Create command
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        name,
        author,
        deleteKafkaTopic,
        confirmed
    );

    // Handle command
    deleteDatasetCommandHandler.handle(command);

    // Return 204 No Content
    return ResponseEntity.noContent().build();
  }
}
```

---

### Step 6: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
@KafkaListener(...)
public void handleEvent(VersionControlEvent event) {
  switch (event) {
    // ... existing cases
    case DatasetDeletedEvent e -> handleDatasetDeleted(e);
  }
}

/**
 * Handles DatasetDeletedEvent by clearing all in-memory data for the dataset.
 */
void handleDatasetDeleted(DatasetDeletedEvent event) {
  logger.warn("Processing DatasetDeletedEvent: dataset={}, branches={}, commits={}",
      event.dataset(), event.deletedBranches().size(), event.deletedCommitCount());

  // 1. Delete all branches
  branchRepository.deleteAllByDataset(event.dataset());

  // 2. Delete all commits
  commitRepository.deleteAllByDataset(event.dataset());

  // 3. Clear dataset cache
  datasetService.clearCache(event.dataset());

  // 4. Clear snapshot cache (if SnapshotService has per-dataset storage)
  snapshotService.clearSnapshotsForDataset(event.dataset());

  logger.warn("Dataset {} fully deleted from memory (Kafka topic deleted: {})",
      event.dataset(), event.kafkaTopicDeleted());
}
```

**Add method to SnapshotService:**
```java
public void clearSnapshotsForDataset(String dataset) {
  latestSnapshots.remove(dataset);
  commitCounters.remove(dataset);
  logger.debug("Cleared snapshots for dataset: {}", dataset);
}
```

---

### Step 7: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/command/DeleteDatasetCommandHandlerTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class DeleteDatasetCommandHandlerTest {

  @Autowired
  private DeleteDatasetCommandHandler handler;

  @Autowired
  private BranchRepository branchRepository;

  @Test
  void handle_withConfirmation_shouldCreateDeletedEvent() {
    // Arrange
    String dataset = "test-dataset";
    branchRepository.save(dataset, new Branch("main", CommitId.generate()));
    branchRepository.save(dataset, new Branch("feature", CommitId.generate()));

    DeleteDatasetCommand command = new DeleteDatasetCommand(
        dataset, "alice", false, true);

    // Act
    DatasetDeletedEvent event = handler.handle(command);

    // Assert
    assertThat(event.dataset()).isEqualTo(dataset);
    assertThat(event.deletedBranches()).hasSize(2);
    assertThat(event.kafkaTopicDeleted()).isFalse();
  }

  @Test
  void handle_withoutConfirmation_shouldThrowUnconfirmedDeletionException() {
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        "test", "alice", false, false);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(UnconfirmedDeletionException.class);
  }

  @Test
  void handle_defaultDataset_shouldThrowProtectedDatasetException() {
    DeleteDatasetCommand command = new DeleteDatasetCommand(
        "default", "alice", false, true);

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ProtectedDatasetException.class);
  }
}
```

**Integration test:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = {
    "projector.kafka-listener.enabled=true",
    "vc.allow-kafka-topic-deletion=false"  // Don't delete topics in tests
})
class DatasetDeletionIT extends IntegrationTestFixture {

  @Test
  void deleteDataset_shouldClearAllData() throws Exception {
    String dataset = "test-deletion";

    // Create dataset with branches and commits
    eventPublisher.publish(new BranchCreatedEvent(
        dataset, "main", CommitId.generate().value(), Instant.now())).get();
    eventPublisher.publish(new BranchCreatedEvent(
        dataset, "feature", CommitId.generate().value(), Instant.now())).get();

    await().until(() -> branchRepository.findAllByDataset(dataset).size() == 2);

    // Delete dataset via HTTP
    ResponseEntity<Void> response = restTemplate.exchange(
        "/version/datasets/" + dataset + "?confirmed=true",
        HttpMethod.DELETE,
        new HttpEntity<>(new HttpHeaders()),
        Void.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Wait for projection
    await().until(() -> branchRepository.findAllByDataset(dataset).isEmpty());

    // Verify all data cleared
    assertThat(branchRepository.findAllByDataset(dataset)).isEmpty();
    assertThat(commitRepository.findAllByDataset(dataset)).isEmpty();
  }
}
```

---

## Success Criteria

- [ ] `DELETE /version/datasets/{name}?confirmed=true` returns 204
- [ ] `DELETE /version/datasets/{name}` (no confirmation) returns 400
- [ ] `DELETE /version/datasets/default` returns 403 (protected)
- [ ] `DatasetDeletedEvent` published to Kafka
- [ ] All branches deleted from `BranchRepository`
- [ ] All commits deleted from `CommitRepository`
- [ ] Cache cleared from `DatasetService`
- [ ] Kafka topic deleted (if requested and allowed)
- [ ] All tests pass
- [ ] OpenAPI documentation added

---

## Kafka Topic Deletion Considerations

### Verification Before Deletion

Check if topic can be safely deleted:
1. No other consumers active
2. No pending writes
3. Snapshots captured (if needed for compliance)

### Rollback if Topic Deleted by Mistake

**No rollback possible** - topic deletion is irreversible!

**Mitigation:**
- Require explicit confirmation
- Default to keeping topic
- Log deletion prominently (WARN level)
- Consider "soft delete" (mark as deleted but keep topic)

---

## Future Enhancements

- **Soft delete:** Mark dataset as deleted without removing data
- **Scheduled deletion:** Delete after X days grace period
- **Backup before delete:** Export to S3/disk before deletion
- **Restore from backup:** Recreate dataset from export
- **Bulk deletion:** Delete multiple datasets at once
