# Task: Add patchSize to RevertCreatedEvent and CherryPickedEvent

**Status:** Not Started
**Priority:** Medium
**Category:** Event Schema Evolution
**Estimated Time:** 2-3 hours
**Complexity:** Low (similar to Task 00-add-patchsize-to-commit-entity)

---

## Overview

Add `patchSize` field to `RevertCreatedEvent` and `CherryPickedEvent` to eliminate business logic from the projector and ensure consistency with `CommitCreatedEvent`.

**Current Problem:**
The ReadModelProjector computes `patchSize` from the RDF Patch when handling these events, which violates CQRS principles by placing business logic on the read side.

**Related Work:**
- ✅ Task 00: Added patchSize to CommitCreatedEvent (completed 2025-01-24)
- This task applies the same pattern to two other commit-creating events

---

## Current State

### RevertCreatedEvent

**File:** [RevertCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/RevertCreatedEvent.java)

```java
public record RevertCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("revertCommitId") String revertCommitId,
    @JsonProperty("revertedCommitId") String revertedCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch
    // Missing: patchSize
) implements VersionControlEvent { }
```

**Projector workaround** ([ReadModelProjector.java:488](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L488)):
```java
// ⚠️ Business logic in projector (should be in command handler)
RDFPatch patch = RDFPatchOps.read(inputStream);
int patchSize = RdfPatchUtil.countOperations(patch);  // Computed here
Commit commit = new Commit(..., patchSize);
```

### CherryPickedEvent

**File:** [CherryPickedEvent.java](../../src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java)

```java
public record CherryPickedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("newCommitId") String newCommitId,
    @JsonProperty("sourceCommitId") String sourceCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch
    // Missing: patchSize
) implements VersionControlEvent { }
```

**Projector workaround** ([ReadModelProjector.java:606](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L606)):
```java
// ⚠️ Business logic in projector (should be in command handler)
RDFPatch patch = RDFPatchOps.read(inputStream);
int patchSize = RdfPatchUtil.countOperations(patch);  // Computed here
Commit commit = new Commit(..., patchSize);
```

---

## Why This Is Important

### CQRS Violation
- **Business logic on read side**: Counting operations is domain logic that belongs in command handlers
- **Inconsistent pattern**: CommitCreatedEvent has patchSize, but these events don't
- **Inefficient**: Patch is parsed twice (once in handler for validation, once in projector for counting)

### Event Sourcing Concerns
- **Not truly immutable**: patchSize is computed at replay time, not stored in event
- **Non-deterministic risk**: If RdfPatchUtil.countOperations() logic changes, old events behave differently
- **Incomplete event data**: Events don't contain all information needed to create commits

---

## Implementation Steps

### Step 1: Update RevertCreatedEvent

**File:** [RevertCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/RevertCreatedEvent.java)

Add `patchSize` field:

```java
public record RevertCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("revertCommitId") String revertCommitId,
    @JsonProperty("revertedCommitId") String revertedCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize  // ← NEW FIELD
) implements VersionControlEvent {

  public RevertCreatedEvent {
    // ... existing validation ...

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }
  }

  // Update convenience constructor
  public RevertCreatedEvent(
      String dataset,
      String revertCommitId,
      String revertedCommitId,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {  // ← Add parameter
    this(null, dataset, revertCommitId, revertedCommitId, branch, message, author,
         timestamp, rdfPatch, patchSize);
  }
}
```

### Step 2: Update CherryPickedEvent

**File:** [CherryPickedEvent.java](../../src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java)

Add `patchSize` field:

```java
public record CherryPickedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("newCommitId") String newCommitId,
    @JsonProperty("sourceCommitId") String sourceCommitId,
    @JsonProperty("branch") String branch,
    @JsonProperty("message") String message,
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("rdfPatch") String rdfPatch,
    @JsonProperty("patchSize") int patchSize  // ← NEW FIELD
) implements VersionControlEvent {

  public CherryPickedEvent {
    // ... existing validation ...

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }
  }

  // Update convenience constructor
  public CherryPickedEvent(
      String dataset,
      String newCommitId,
      String sourceCommitId,
      String branch,
      String message,
      String author,
      Instant timestamp,
      String rdfPatch,
      int patchSize) {  // ← Add parameter
    this(null, dataset, newCommitId, sourceCommitId, branch, message, author,
         timestamp, rdfPatch, patchSize);
  }
}
```

### Step 3: Update RevertCommitCommandHandler

**File:** [RevertCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/RevertCommitCommandHandler.java)

Compute patchSize before creating event:

```java
// After building revert patch
RDFPatch revertPatch = builder.build();
String patchString = RdfPatchUtil.toString(revertPatch);
int patchSize = RdfPatchUtil.countOperations(revertPatch);  // ← Compute here

// Create event with patchSize
VersionControlEvent event = new RevertCreatedEvent(
    command.dataset(),
    newCommitId.value(),
    command.commitId(),
    command.branch(),
    revertMessage,
    command.author(),
    Instant.now(),
    patchString,
    patchSize  // ← Add this
);
```

### Step 4: Update CherryPickCommandHandler

**File:** [CherryPickCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CherryPickCommandHandler.java)

Compute patchSize before creating event:

```java
// After getting source patch
RDFPatch sourcePatch = commitRepository.findPatch(
    command.dataset(),
    CommitId.of(command.sourceCommitId())
);
String patchString = RdfPatchUtil.toString(sourcePatch);
int patchSize = RdfPatchUtil.countOperations(sourcePatch);  // ← Compute here

// Create event with patchSize
VersionControlEvent event = new CherryPickedEvent(
    command.dataset(),
    newCommitId.value(),
    command.sourceCommitId(),
    command.targetBranch(),
    cherryPickMessage,
    command.author(),
    Instant.now(),
    patchString,
    patchSize  // ← Add this
);
```

### Step 5: Update ReadModelProjector

**File:** [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

**Remove computation logic** and use patchSize from event:

#### For RevertCreatedEvent (line 488):
```java
// Parse RDF Patch from string
ByteArrayInputStream inputStream = new ByteArrayInputStream(
    event.rdfPatch().getBytes(StandardCharsets.UTF_8));
RDFPatch patch = RDFPatchOps.read(inputStream);

// ❌ REMOVE THIS:
// int patchSize = RdfPatchUtil.countOperations(patch);

// ✅ USE EVENT FIELD:
int patchSize = event.patchSize();

// Create Commit domain object for revert
Commit commit = new Commit(
    CommitId.of(event.revertCommitId()),
    List.of(targetBranch.getCommitId()),
    event.author(),
    event.message(),
    event.timestamp(),
    patchSize  // ← From event
);
```

#### For CherryPickedEvent (line 606):
```java
// Parse RDF Patch from string
ByteArrayInputStream inputStream = new ByteArrayInputStream(
    event.rdfPatch().getBytes(StandardCharsets.UTF_8));
RDFPatch patch = RDFPatchOps.read(inputStream);

// ❌ REMOVE THIS:
// int patchSize = RdfPatchUtil.countOperations(patch);

// ✅ USE EVENT FIELD:
int patchSize = event.patchSize();

// Create Commit domain object for cherry-picked commit
Commit commit = new Commit(
    CommitId.of(event.newCommitId()),
    List.of(targetBranch.getCommitId()),
    event.author(),
    event.message(),
    event.timestamp(),
    patchSize  // ← From event
);
```

### Step 6: Update All Tests

Update tests that create these events (similar to Task 00):

**Test files to update:**
- `RevertCreatedEventTest.java`
- `CherryPickedEventTest.java`
- `RevertCommitCommandHandlerTest.java`
- `CherryPickCommandHandlerTest.java`
- `ReadModelProjectorTest.java`
- Integration tests that use these events

**Pattern:**
```java
// Old
RevertCreatedEvent event = new RevertCreatedEvent(
    dataset, revertCommitId, revertedCommitId, branch,
    message, author, timestamp, patch);

// New
RevertCreatedEvent event = new RevertCreatedEvent(
    dataset, revertCommitId, revertedCommitId, branch,
    message, author, timestamp, patch, patchSize);  // ← Add patchSize
```

---

## Event Schema Migration Strategy

### Breaking Change

This is a **breaking change** - old events without `patchSize` will fail deserialization.

### Migration Options

#### Option A: Make patchSize Optional (Recommended for Production)
```java
@JsonProperty("patchSize") Integer patchSize  // nullable
```

In canonical constructor:
```java
public RevertCreatedEvent {
    // ... existing validation ...

    // Default to 0 if null (old events)
    if (patchSize == null) {
        patchSize = 0;  // or compute from rdfPatch
    }

    if (patchSize < 0) {
        throw new IllegalArgumentException("Patch size cannot be negative");
    }
}
```

#### Option B: Accept Loss of Old Events (OK for Development)
- Clear Kafka topics before deployment
- No backward compatibility needed
- Simpler implementation

**Recommendation:** Use Option B for now (not in production yet), plan Option A for production deployment.

---

## Testing Strategy

### Unit Tests

**Test event validation:**
```java
@Test
void revertCreatedEvent_withNegativePatchSize_shouldThrowException() {
  assertThatThrownBy(() -> new RevertCreatedEvent(
      "dataset", "revertId", "revertedId", "main",
      "Revert message", "author", Instant.now(), "patch", -1
  )).isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Patch size cannot be negative");
}
```

**Test projector uses event field:**
```java
@Test
void handleRevertCreated_shouldUsePatchSizeFromEvent() {
  RevertCreatedEvent event = new RevertCreatedEvent(
      "dataset", "revertId", "revertedId", "main",
      "Revert message", "author", Instant.now(), "TX .\nTC .", 5
  );

  projector.handleRevertCreated(event);

  verify(commitRepository).save(eq("dataset"), argThat(commit ->
      commit.patchSize() == 5  // ← Must use value from event
  ), any());
}
```

### Integration Tests

Verify end-to-end flow:

```java
@Test
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
void revert_shouldStorePatchSize() throws Exception {
  // Arrange: Create commit with 3 operations
  String commitId = createCommitWithOperations(3);

  // Act: Revert the commit
  String revertCommitId = revertCommit(commitId);

  // Assert: Wait for projection and verify patchSize
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        Commit revertCommit = commitRepository.findById(revertCommitId).get();
        assertThat(revertCommit.patchSize()).isEqualTo(3);  // Same as original
      });
}
```

---

## Success Criteria

- ✅ `RevertCreatedEvent` includes `patchSize` field with validation
- ✅ `CherryPickedEvent` includes `patchSize` field with validation
- ✅ `RevertCommitCommandHandler` computes patchSize before creating event
- ✅ `CherryPickCommandHandler` computes patchSize before creating event
- ✅ `ReadModelProjector` uses `patchSize` from events (no computation)
- ✅ All existing tests updated and passing
- ✅ New tests verify patchSize computation and usage
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ Full build passes: `mvn -q clean install`
- ✅ CQRS compliance verified (no business logic in projector)

---

## Benefits

### CQRS Compliance
- ✅ Business logic (counting) in command handlers (write side)
- ✅ Projector uses data from events (read side)
- ✅ Proper separation of concerns

### Event Sourcing
- ✅ Events are fully self-contained
- ✅ Deterministic replay (no runtime computation)
- ✅ Consistent with CommitCreatedEvent pattern

### Performance
- ✅ Patch parsed once in handler, not again in projector
- ✅ No redundant RdfPatchUtil.countOperations() calls

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/event/RevertCreatedEvent.java`
- `src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java`
- `src/main/java/org/chucc/vcserver/command/RevertCommitCommandHandler.java`
- `src/main/java/org/chucc/vcserver/command/CherryPickCommandHandler.java`
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

### Test Code (~10-15 files)
- `src/test/java/org/chucc/vcserver/event/RevertCreatedEventTest.java`
- `src/test/java/org/chucc/vcserver/event/CherryPickedEventTest.java`
- `src/test/java/org/chucc/vcserver/command/RevertCommitCommandHandlerTest.java`
- `src/test/java/org/chucc/vcserver/command/CherryPickCommandHandlerTest.java`
- `src/test/java/org/chucc/vcserver/projection/ReadModelProjectorTest.java`
- `src/test/java/org/chucc/vcserver/integration/RevertIT.java`
- `src/test/java/org/chucc/vcserver/integration/CherryPickIT.java`
- `src/test/java/org/chucc/vcserver/integration/AdvancedOperationsProjectorIT.java`

---

## References

- [Task 00: Add patchSize to CommitCreatedEvent](../commits/00-add-patchsize-to-commit-entity.md) (completed 2025-01-24)
- [CQRS Guide - Event Schema Evolution](../../docs/architecture/cqrs-event-sourcing.md)
- [RevertCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/RevertCreatedEvent.java)
- [CherryPickedEvent.java](../../src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java)
- [ReadModelProjector.java:488](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L488) (revert handler)
- [ReadModelProjector.java:606](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L606) (cherry-pick handler)

---

## Estimated Effort Breakdown

- Event updates: 30 minutes
- Command handler updates: 30 minutes
- Projector updates: 15 minutes
- Test updates: 45-60 minutes
- Testing and validation: 30 minutes

**Total:** 2-3 hours
