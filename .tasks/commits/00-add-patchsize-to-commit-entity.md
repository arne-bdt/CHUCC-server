# Task: Add patchSize to Commit Entity and Events

**Status:** Not Started
**Priority:** High (blocking task for 01-implement-commit-metadata-api)
**Category:** Schema Evolution
**Estimated Time:** 3-4 hours

---

## Overview

Add `patchSize` field to the `Commit` entity and `CommitCreatedEvent` to enable:
- Commit metadata API responses
- Monitoring and observability (commit size tracking)
- Optimized snapshot strategies (trigger snapshots after N operations)

**Related Task:** This is a prerequisite for [01-implement-commit-metadata-api.md](./01-implement-commit-metadata-api.md)

---

## Current State

**Commit Entity:** [Commit.java](../../src/main/java/org/chucc/vcserver/domain/Commit.java)
```java
public record Commit(
    CommitId id,
    List<CommitId> parents,
    String author,
    String message,
    Instant timestamp
    // Missing: patchSize
) {}
```

**CommitCreatedEvent:** [CommitCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitCreatedEvent.java)
```java
public record CommitCreatedEvent(
    String eventId,
    String dataset,
    String commitId,
    List<String> parents,
    String branch,
    String message,
    String author,
    Instant timestamp,
    String rdfPatch
    // Missing: patchSize
) {}
```

---

## Requirements

### 1. Add `patchSize` to Commit Entity

**Definition:**
- **patchSize** = Total number of RDF Patch operations (add + delete statements)
- Example: Patch with 5 adds + 3 deletes = patchSize of 8

**Use Cases:**
- Display in commit metadata API
- Trigger snapshots when cumulative size exceeds threshold
- Monitor write volume per commit

### 2. Event Schema Evolution

**Critical:** This is a **breaking change** to the event schema.

**Compatibility Strategy:**
- New field is **required** for new events
- Old events without `patchSize` can use default value (0) during replay
- All new command handlers must compute and include `patchSize`

---

## Implementation Steps

### Step 1: Add Helper Method to Count Patch Operations

**File:** `src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java` (create new)

```java
package org.chucc.vcserver.util;

import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;

/**
 * Utility class for RDF Patch operations.
 */
public final class RdfPatchUtil {

  private RdfPatchUtil() {
    // Utility class
  }

  /**
   * Counts the total number of operations in an RDF Patch.
   * This includes all add and delete operations.
   *
   * @param patch the RDF Patch
   * @return the total number of operations (adds + deletes)
   */
  public static int countOperations(RDFPatch patch) {
    RDFChangesCollector collector = new RDFChangesCollector();
    patch.apply(collector);

    // Count quad operations (adds + deletes)
    int addCount = collector.added().size();
    int deleteCount = collector.deleted().size();

    return addCount + deleteCount;
  }
}
```

### Step 2: Update Commit Entity

**File:** [Commit.java](../../src/main/java/org/chucc/vcserver/domain/Commit.java)

Add `patchSize` field:

```java
public record Commit(
    CommitId id,
    List<CommitId> parents,
    String author,
    String message,
    Instant timestamp,
    int patchSize  // ← NEW FIELD
) {

  public Commit {
    // ... existing validation ...

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }

    // ... rest of validation ...
  }

  // Update static factory method
  public static Commit create(List<CommitId> parents, String author, String message, int patchSize) {
    return new Commit(
        CommitId.generate(),
        parents,
        author,
        message,
        Instant.now(),
        patchSize  // ← Add parameter
    );
  }
}
```

### Step 3: Update CommitCreatedEvent

**File:** [CommitCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitCreatedEvent.java)

Add `patchSize` field:

```java
public record CommitCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("parents") List<String> parents,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize  // ← NEW FIELD
) implements VersionControlEvent {

  public CommitCreatedEvent {
    // ... existing validation ...

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }
  }

  // Update convenience constructor
  public CommitCreatedEvent(
      String dataset,
      String commitId,
      List<String> parents,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {  // ← Add parameter
    this(null, dataset, commitId, parents, branch, message, author, timestamp, rdfPatch, patchSize);
  }
}
```

### Step 4: Update Command Handlers

**All command handlers that create CommitCreatedEvent must compute patchSize.**

#### 4a. CreateCommitCommandHandler

**File:** [CreateCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java)

```java
// After parsing patch
RDFPatch patch = RDFPatchOps.read(patchInputStream);
int patchSize = RdfPatchUtil.countOperations(patch);

// Create event
CommitCreatedEvent event = new CommitCreatedEvent(
    command.dataset(),
    commitId.toString(),
    parentCommitIds,
    command.branch(),
    command.message(),
    command.author(),
    timestamp,
    command.rdfPatch(),
    patchSize  // ← Add this
);
```

#### 4b. GraphCommandUtil (GSP operations)

**File:** [GraphCommandUtil.java](../../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java)

```java
// After building patch
String patchString = patchWriter.toString();
int patchSize = RdfPatchUtil.countOperations(patch);

// Create event
return new CommitCreatedEvent(
    dataset,
    commitId.toString(),
    parentIds,
    branch,
    message,
    author,
    timestamp,
    patchString,
    patchSize  // ← Add this
);
```

#### 4c. Other Handlers

Update all handlers that create CommitCreatedEvent:
- `SquashCommandHandler` (rebase/squash operations)
- `RebaseCommandHandler` (rebase operations)
- `CherryPickCommandHandler` (cherry-pick operations)
- `RevertCommandHandler` (revert operations)

**Pattern:** Always compute `patchSize` after creating the RDFPatch.

### Step 5: Update ReadModelProjector

**File:** [ReadModelProjector.java:245-253](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L245-L253)

Update to use `patchSize` from event:

```java
// Create Commit domain object
Commit commit = new Commit(
    CommitId.of(event.commitId()),
    event.parents().stream()
        .map(CommitId::of)
        .toList(),
    event.author(),
    event.message(),
    event.timestamp(),
    event.patchSize()  // ← Use from event
);
```

### Step 6: Update All Tests

**Unit Tests:** Update all tests that create `Commit` or `CommitCreatedEvent`:
- `CommitTest.java`
- `CommitCreatedEventTest.java`
- `CreateCommitCommandHandlerTest.java`
- All command handler tests
- All integration tests

**Pattern:**
```java
// Old
Commit commit = new Commit(id, parents, author, message, timestamp);

// New
Commit commit = new Commit(id, parents, author, message, timestamp, patchSize);
```

### Step 7: Update Test Fixtures

**File:** [ITFixture.java](../../src/test/java/org/chucc/vcserver/testutil/ITFixture.java)

Update helper methods that create commits:

```java
protected Commit createTestCommit() {
  return new Commit(
      CommitId.generate(),
      List.of(),
      "Test Author",
      "Test commit",
      Instant.now(),
      0  // ← Add patchSize parameter (0 for test commits)
  );
}
```

---

## Testing Strategy

### Unit Tests

**Test RdfPatchUtil:**
```java
@Test
void countOperations_shouldCountAddsAndDeletes() {
  String patchStr = """
      A <http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .
      A <http://ex.org/s2> <http://ex.org/p2> <http://ex.org/o2> .
      D <http://ex.org/s3> <http://ex.org/p3> <http://ex.org/o3> .
      """;
  RDFPatch patch = RDFPatchOps.fromString(patchStr);

  int count = RdfPatchUtil.countOperations(patch);

  assertThat(count).isEqualTo(3);  // 2 adds + 1 delete
}
```

**Test Commit validation:**
```java
@Test
void commit_withNegativePatchSize_shouldThrowException() {
  assertThatThrownBy(() -> new Commit(
      CommitId.generate(),
      List.of(),
      "Author",
      "Message",
      Instant.now(),
      -1  // ← Invalid
  )).isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Patch size cannot be negative");
}
```

### Integration Tests

**Verify patchSize in created commits:**
```java
@Test
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
void commitCreated_shouldStorePatchSize() throws Exception {
  // Arrange
  String patchStr = """
      A <http://ex.org/s> <http://ex.org/p> "value" .
      """;

  // Act
  String commitId = createCommitViaPOST(patchStr);

  // Assert: Wait for projection
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        Commit commit = commitRepository.findByDatasetAndId("default", CommitId.of(commitId)).get();
        assertThat(commit.patchSize()).isEqualTo(1);
      });
}
```

---

## Event Schema Migration Strategy

### Backward Compatibility

**Old events** (without `patchSize`) will fail deserialization after this change.

**Options:**

#### Option A: Make patchSize Optional During Transition (Recommended)
```java
@JsonProperty("patchSize") Integer patchSize  // nullable
```

In ReadModelProjector:
```java
int size = event.patchSize() != null ? event.patchSize() : 0;
Commit commit = new Commit(..., size);
```

**Pros:** Graceful degradation
**Cons:** Temporary nullable field

#### Option B: Accept Loss of Old Events
- Clear Kafka topics before deployment
- Acceptable for development/testing

**Recommendation:** Use Option A for production, Option B for development.

---

## Success Criteria

- ✅ `RdfPatchUtil.countOperations()` implemented with unit tests
- ✅ `Commit` entity includes `patchSize` field
- ✅ `CommitCreatedEvent` includes `patchSize` field
- ✅ All command handlers compute and set `patchSize`
- ✅ `ReadModelProjector` uses `patchSize` from events
- ✅ All existing tests updated (compile without errors)
- ✅ New tests verify `patchSize` computation and storage
- ✅ Zero quality violations
- ✅ Full build passes: `mvn -q clean install`
- ✅ Event schema evolution checker passes

---

## Files to Create/Modify

### Create
- `src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java`
- `src/test/java/org/chucc/vcserver/util/RdfPatchUtilTest.java`

### Modify (Production)
- `src/main/java/org/chucc/vcserver/domain/Commit.java`
- `src/main/java/org/chucc/vcserver/event/CommitCreatedEvent.java`
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`
- `src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java`
- `src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java`
- `src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java`
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

### Modify (Tests - ~30 files)
- All tests that construct `Commit` or `CommitCreatedEvent`
- See `git grep "new Commit("` for full list

---

## Post-Implementation

**CRITICAL:** Invoke specialized agent after completion:
```bash
@event-schema-evolution-checker
```

This will verify:
- Event deserialization still works
- No breaking changes to event replay
- Kafka serialization/deserialization tests pass

---

## References

- [Commit.java](../../src/main/java/org/chucc/vcserver/domain/Commit.java)
- [CommitCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitCreatedEvent.java)
- [ReadModelProjector.java:245](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L245)
- [CQRS Guide - Event Schema Evolution](../../docs/architecture/cqrs-event-sourcing.md)

---

**Next Task:** After completing this task, proceed to [01-implement-commit-metadata-api.md](./01-implement-commit-metadata-api.md)
