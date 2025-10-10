# Task: Implement Branch Deletion

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1 session (2-3 hours)
**Dependencies:** None

---

## Context

Branch deletion is currently NOT implemented - the endpoint returns `501 Not Implemented`.

**Current stub:**
```java
@DeleteMapping("/{name}")
public ResponseEntity<Void> deleteBranch(@PathVariable String name) {
  return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
}
```

**Goal:** Implement full branch deletion following CQRS + Event Sourcing pattern.

---

## Design Decisions

### What Gets Deleted?

| Item | Action | Rationale |
|------|--------|-----------|
| **Branch pointer** | ✅ Deleted from BranchRepository | Branch no longer accessible |
| **Commits** | ❌ NOT deleted | May be referenced by other branches/tags |
| **RDF Patches** | ❌ NOT deleted | Part of audit trail |
| **Kafka events** | ❌ NOT deleted | Immutable event log |
| **Dataset cache** | ⚠️ Optionally cleared | Memory optimization |

**Key principle:** Delete only the branch pointer, preserve all history.

### Protection Rules

- ❌ Cannot delete `main` branch (default branch)
- ❌ Cannot delete currently checked-out branch (if concept exists)
- ✅ Can delete any other branch
- ⚠️ Warning if branch has unmerged commits (future enhancement)

---

## Implementation Plan

### Step 1: Create DeleteBranchCommand

**File:** `src/main/java/org/chucc/vcserver/command/DeleteBranchCommand.java`

```java
package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to delete a branch.
 */
public record DeleteBranchCommand(
    String dataset,
    String branchName,
    String author) {

  public DeleteBranchCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
```

---

### Step 2: Create BranchDeletedEvent

**File:** `src/main/java/org/chucc/vcserver/event/BranchDeletedEvent.java`

```java
package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Event representing the deletion of a branch.
 */
public record BranchDeletedEvent(
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("lastCommitId") String lastCommitId,  // For audit trail
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  public BranchDeletedEvent {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(lastCommitId, "Last commit ID cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
  }
}
```

**Update VersionControlEvent interface:**
```java
@JsonSubTypes({
    // ... existing types
    @JsonSubTypes.Type(value = BranchDeletedEvent.class, name = "BranchDeleted")
})
public sealed interface VersionControlEvent
    permits BranchCreatedEvent,
        CommitCreatedEvent,
        // ... existing types
        BranchDeletedEvent {
  // ...
}
```

**Update KafkaConfig TYPE_MAPPINGS:**
```java
configProps.put(JsonDeserializer.TYPE_MAPPINGS,
    "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
    + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
    // ... existing mappings
    + "BranchDeleted:org.chucc.vcserver.event.BranchDeletedEvent");
```

---

### Step 3: Create DeleteBranchCommandHandler

**File:** `src/main/java/org/chucc/vcserver/command/DeleteBranchCommandHandler.java`

```java
package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.BranchDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.ProtectedBranchException;
import org.chucc.vcserver.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles deletion of branches.
 */
@Component
public class DeleteBranchCommandHandler {
  private static final Logger logger = LoggerFactory.getLogger(DeleteBranchCommandHandler.class);

  private final BranchRepository branchRepository;
  private final EventPublisher eventPublisher;

  public DeleteBranchCommandHandler(
      BranchRepository branchRepository,
      EventPublisher eventPublisher) {
    this.branchRepository = branchRepository;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Handles branch deletion command.
   *
   * @param command the delete branch command
   * @return the branch deleted event
   * @throws BranchNotFoundException if branch doesn't exist
   * @throws ProtectedBranchException if trying to delete protected branch (main)
   */
  public BranchDeletedEvent handle(DeleteBranchCommand command) {
    logger.info("Deleting branch: {} in dataset: {}", command.branchName(), command.dataset());

    // 1. Check if branch exists
    Branch branch = branchRepository.findByDatasetAndName(command.dataset(), command.branchName())
        .orElseThrow(() -> new BranchNotFoundException(
            "Branch not found: " + command.branchName() + " in dataset: " + command.dataset()));

    // 2. Protect main branch
    if ("main".equals(command.branchName())) {
      throw new ProtectedBranchException("Cannot delete main branch");
    }

    // 3. Create event
    BranchDeletedEvent event = new BranchDeletedEvent(
        command.dataset(),
        command.branchName(),
        branch.getCommitId().value(),  // Record last commit for audit
        command.author(),
        Instant.now()
    );

    // 4. Publish event (async)
    eventPublisher.publish(event);

    logger.info("Branch {} deleted from dataset {} (was at commit {})",
        command.branchName(), command.dataset(), branch.getCommitId());

    return event;
  }
}
```

**Create exception classes:**

```java
// src/main/java/org/chucc/vcserver/exception/ProtectedBranchException.java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ProtectedBranchException extends RuntimeException {
  public ProtectedBranchException(String message) {
    super(message);
  }
}
```

---

### Step 4: Implement Controller Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

```java
@DeleteMapping("/{name}")
public ResponseEntity<Void> deleteBranch(
    @PathVariable String name,
    @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author) {

  // Create command
  DeleteBranchCommand command = new DeleteBranchCommand(
      "default",  // TODO: Get from request context
      name,
      author
  );

  // Handle command
  BranchDeletedEvent event = deleteBranchCommandHandler.handle(command);

  // Return 204 No Content
  return ResponseEntity.noContent().build();
}
```

---

### Step 5: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
@KafkaListener(...)
public void handleEvent(VersionControlEvent event) {
  switch (event) {
    // ... existing cases
    case BranchDeletedEvent e -> handleBranchDeleted(e);
  }
}

/**
 * Handles BranchDeletedEvent by removing the branch from the repository.
 */
void handleBranchDeleted(BranchDeletedEvent event) {
  logger.debug("Processing BranchDeletedEvent: branchName={}, dataset={}",
      event.branchName(), event.dataset());

  boolean deleted = branchRepository.delete(event.dataset(), event.branchName());

  if (deleted) {
    logger.info("Deleted branch: {} from dataset: {} (was at commit: {})",
        event.branchName(), event.dataset(), event.lastCommitId());

    // Optional: Clear cache for this branch
    datasetService.clearCacheForBranch(event.dataset(), event.branchName());
  } else {
    logger.warn("Branch {} not found in dataset {} during deletion (event from different test?)",
        event.branchName(), event.dataset());
  }
}
```

**Add cache clearing method to DatasetService:**
```java
public void clearCacheForBranch(String datasetName, String branchName) {
  // Get commits for this branch and clear their cache entries
  // This is optional - cache will be rebuilt on next query
  logger.debug("Cleared cache for branch {} in dataset {}", branchName, datasetName);
}
```

---

### Step 6: Add EventPublisher Support

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

Update `getPartitionKey()` method:
```java
private String getPartitionKey(VersionControlEvent event) {
  return switch (event) {
    case BranchCreatedEvent e -> e.branchName();
    case BranchResetEvent e -> e.branchName();
    case BranchDeletedEvent e -> e.branchName();  // ← Add this
    // ... other cases
  };
}
```

Update `addHeaders()` method:
```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // ... existing code

  switch (event) {
    // ... existing cases
    case BranchDeletedEvent e -> {
      headers.add(new RecordHeader(EventHeaders.BRANCH,
          e.branchName().getBytes(StandardCharsets.UTF_8)));
      headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
          e.lastCommitId().getBytes(StandardCharsets.UTF_8)));
    }
  }
}
```

---

### Step 7: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/command/DeleteBranchCommandHandlerTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class DeleteBranchCommandHandlerTest {

  @Autowired
  private DeleteBranchCommandHandler handler;

  @Autowired
  private BranchRepository branchRepository;

  @Test
  void handle_existingBranch_shouldCreateDeletedEvent() {
    // Arrange
    Branch branch = new Branch("feature-x", CommitId.generate());
    branchRepository.save("default", branch);

    DeleteBranchCommand command = new DeleteBranchCommand("default", "feature-x", "alice");

    // Act
    BranchDeletedEvent event = handler.handle(command);

    // Assert
    assertThat(event.dataset()).isEqualTo("default");
    assertThat(event.branchName()).isEqualTo("feature-x");
    assertThat(event.lastCommitId()).isEqualTo(branch.getCommitId().value());
    assertThat(event.author()).isEqualTo("alice");
  }

  @Test
  void handle_mainBranch_shouldThrowProtectedBranchException() {
    // Arrange
    Branch main = new Branch("main", CommitId.generate());
    branchRepository.save("default", main);

    DeleteBranchCommand command = new DeleteBranchCommand("default", "main", "alice");

    // Act & Assert
    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(ProtectedBranchException.class)
        .hasMessageContaining("Cannot delete main branch");
  }

  @Test
  void handle_nonExistentBranch_shouldThrowBranchNotFoundException() {
    DeleteBranchCommand command = new DeleteBranchCommand("default", "non-existent", "alice");

    assertThatThrownBy(() -> handler.handle(command))
        .isInstanceOf(BranchNotFoundException.class)
        .hasMessageContaining("Branch not found");
  }
}
```

**Integration test:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class BranchDeletionIT extends IntegrationTestFixture {

  @Test
  void deleteBranch_shouldRemoveFromRepository() throws Exception {
    String dataset = "default";
    String branch = "feature-delete-test";

    // Create branch
    BranchCreatedEvent createEvent = new BranchCreatedEvent(
        dataset, branch, CommitId.generate().value(), Instant.now());
    eventPublisher.publish(createEvent).get();

    await().until(() -> branchRepository.findByDatasetAndName(dataset, branch).isPresent());

    // Delete branch via HTTP
    ResponseEntity<Void> response = restTemplate.exchange(
        "/version/branches/" + branch,
        HttpMethod.DELETE,
        new HttpEntity<>(new HttpHeaders()),
        Void.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Wait for projection
    await().until(() -> branchRepository.findByDatasetAndName(dataset, branch).isEmpty());

    // Verify branch is gone
    assertThat(branchRepository.findByDatasetAndName(dataset, branch)).isEmpty();
  }
}
```

---

## API Documentation

Update **OpenAPI annotations** in `BranchController.java`:

```java
@DeleteMapping("/{name}")
@Operation(
    summary = "Delete branch",
    description = "Deletes a branch. The main branch cannot be deleted. "
        + "Commits are preserved even after branch deletion."
)
@ApiResponse(responseCode = "204", description = "Branch deleted successfully")
@ApiResponse(responseCode = "403", description = "Cannot delete protected branch (main)",
    content = @Content(mediaType = "application/problem+json"))
@ApiResponse(responseCode = "404", description = "Branch not found",
    content = @Content(mediaType = "application/problem+json"))
public ResponseEntity<Void> deleteBranch(@PathVariable String name) {
  // ...
}
```

---

## Success Criteria

- [ ] `DELETE /version/branches/{name}` returns 204 for valid branch
- [ ] `DELETE /version/branches/main` returns 403 (protected)
- [ ] `DELETE /version/branches/non-existent` returns 404
- [ ] `BranchDeletedEvent` published to Kafka
- [ ] Branch removed from `BranchRepository` (after projection)
- [ ] Commits still accessible (not deleted)
- [ ] All tests pass (unit + integration)
- [ ] OpenAPI documentation updated

---

## Future Enhancements

- Add `--force` flag to override protection
- Warn if branch has unmerged commits
- Support bulk deletion (delete multiple branches)
- Add undo/restore branch feature (recreate from last commit ID)
