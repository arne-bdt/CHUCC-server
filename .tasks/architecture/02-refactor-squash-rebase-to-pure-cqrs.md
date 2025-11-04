# Task: Refactor Squash/Rebase Handlers to Pure CQRS Pattern

**Status:** Not Started
**Priority:** Low (Technical Debt)
**Category:** Architectural Refactoring
**Estimated Time:** 8-12 hours
**Complexity:** High (significant refactoring, event schema changes)

---

## Overview

Refactor `SquashCommandHandler` and `RebaseCommandHandler` to follow pure CQRS + Event Sourcing pattern by removing direct repository writes and letting the projector handle all data persistence.

**Current Problem:**
These handlers violate CQRS by writing commits directly to the repository before publishing events. This creates dual-write patterns, prevents proper event replay, and breaks the "HTTP response before repository update" principle.

---

## Current State (Architectural Violation)

### SquashCommandHandler

**File:** [SquashCommandHandler.java:111](../../src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java#L111)

**Current Flow:**
```
1. Compute combined patch
2. Create new Commit object
3. commitRepository.save(newCommit, patch)  ← ⚠️ Direct write
4. branchRepository.save(branch)            ← ⚠️ Direct write
5. Create CommitsSquashedEvent
6. Publish event
7. Return
```

**Code:**
```java
// 8. Create new squashed commit
CommitId newCommitId = CommitId.generate();
Commit newCommit = new Commit(...);

// 9. Save new commit with combined patch
commitRepository.save(command.dataset(), newCommit, combinedPatch);  // ⚠️

// 10. Update branch to point to new commit
branch.updateCommit(newCommitId);
branchRepository.save(command.dataset(), branch);  // ⚠️

// 11. Produce event
VersionControlEvent event = new CommitsSquashedEvent(...);
```

**Event:** [CommitsSquashedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java)
- Contains: newCommitId, squashedCommitIds, author, message, timestamp, previousHead
- **Missing**: rdfPatch, patchSize, parent commits

**Projector:** [ReadModelProjector.java:650](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L650)
```java
void handleCommitsSquashed(CommitsSquashedEvent event) {
    // Only updates branch pointer - doesn't create commit!
    branchRepository.updateBranchHead(...);
    datasetService.updateLatestCommit(...);
}
```

### RebaseCommandHandler

**File:** [RebaseCommandHandler.java:133](../../src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java#L133)

**Current Flow:**
```
1. Loop through commits to rebase
2. For each commit:
   a. Create new Commit object
   b. commitRepository.save(newCommit, patch)  ← ⚠️ Direct write
3. branchRepository.save(branch)                ← ⚠️ Direct write
4. Create BranchRebasedEvent
5. Publish event
6. Return
```

**Code:**
```java
for (String commitId : commitsToRebase) {
    // Create new commit
    CommitId newCommitId = CommitId.generate();
    Commit newCommit = new Commit(...);

    // Save new commit
    commitRepository.save(command.dataset(), newCommit, patch);  // ⚠️
    newCommitIds.add(newCommitId.value());
}

// 8. Update branch
branch.updateCommit(currentCommitId);
branchRepository.save(command.dataset(), branch);  // ⚠️

// 9. Produce event
VersionControlEvent event = new BranchRebasedEvent(...);
```

**Event:** [BranchRebasedEvent.java](../../src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java)
- Contains: branch, newHead, previousHead, newCommits (list of IDs), author, timestamp
- **Missing**: Patch data for each commit, patchSize, messages

**Projector:** [ReadModelProjector.java:407](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L407)
```java
void handleBranchRebased(BranchRebasedEvent event) {
    // Only updates branch pointer - doesn't create commits!
    branchRepository.updateBranchHead(...);
    datasetService.updateLatestCommit(...);
}
```

---

## Why This Is a Problem

### 1. Dual-Write Pattern

**Risk:**
```
Command Handler:
  1. commitRepository.save()  ← Write to PostgreSQL
  2. eventPublisher.publish() ← Write to Kafka
     ↓ If Kafka publish fails
  Result: Commits exist in DB, but no event emitted = INCONSISTENT STATE
```

### 2. Event Replay Failure

**Problem:**
```
Initial deployment:
  - SquashCommandHandler creates commit with ID "abc123"
  - Saves to repository
  - Publishes CommitsSquashedEvent

Event replay (e.g., new projector):
  - CommitsSquashedEvent replayed
  - Projector only updates branch pointer
  - Commit "abc123" doesn't exist!  ← MISSING DATA
```

### 3. Breaks Eventual Consistency Contract

**CHUCC Server promises:**
> "HTTP responses return before repository updates (eventual consistency)"

**Reality for Squash/Rebase:**
```
HTTP 202 Accepted returned
  ↑ AFTER
commitRepository.save()  ← Repository already updated!
```

### 4. Prevents Proper Testing

Integration tests can't verify eventual consistency:
```java
@Test
void squash_shouldReturnBeforeProjection() {
    // Act: Squash commits
    ResponseEntity<String> response = squashCommits(...);

    // Assert: Response immediate
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Problem: Commit already in repository!
    // Can't test eventual consistency pattern
}
```

---

## Solution: Pure CQRS Pattern

### Desired Flow

```
Command Handler:
  1. Validate business rules
  2. Create event(s) with ALL data needed
  3. Publish event(s) to Kafka
  4. Return 202 Accepted immediately

  ↓ (async, eventual)

Projector:
  1. Receive event from Kafka
  2. Create/update repositories
  3. Complete projection
```

---

## Implementation Options

### Option A: Enrich Existing Events (Simpler)

Add patch data to existing events:

**CommitsSquashedEvent:**
```java
public record CommitsSquashedEvent(
    String eventId,
    String dataset,
    String branch,
    String newCommitId,
    List<String> squashedCommitIds,
    List<String> parents,        // ← NEW
    String author,
    String message,
    Instant timestamp,
    String previousHead,
    String rdfPatch,             // ← NEW
    int patchSize                // ← NEW
) implements VersionControlEvent { }
```

**BranchRebasedEvent:**
```java
// Option A1: Single event with all commits
public record BranchRebasedEvent(
    String eventId,
    String dataset,
    String branch,
    String newHead,
    String previousHead,
    List<RebasedCommit> newCommits,  // ← CHANGED: Full commit data
    String author,
    Instant timestamp
) implements VersionControlEvent {

    public record RebasedCommit(
        String commitId,
        List<String> parents,
        String author,
        String message,
        String rdfPatch,
        int patchSize
    ) { }
}
```

**Pros:**
- ✅ Minimal event schema changes
- ✅ Atomic operation (one event)
- ✅ Easier to implement

**Cons:**
- ❌ Large events (multiple commits in one event for rebase)
- ❌ Still violates "one event per aggregate change" principle
- ❌ Harder to query individual commits in event store

### Option B: Create New Event Types (Proper Event Sourcing)

Create granular events for each state change:

**New Events:**
```java
// Replace CommitsSquashedEvent with:
public record SquashStartedEvent(
    String dataset,
    String branch,
    List<String> commitsToSquash,
    Instant timestamp
) implements VersionControlEvent { }

public record SquashedCommitCreatedEvent(
    String dataset,
    String commitId,
    List<String> parents,
    String branch,
    String message,
    String author,
    Instant timestamp,
    String rdfPatch,
    int patchSize,
    List<String> squashedCommitIds  // Reference to originals
) implements VersionControlEvent { }

public record SquashCompletedEvent(
    String dataset,
    String branch,
    String newCommitId,
    Instant timestamp
) implements VersionControlEvent { }
```

**Pros:**
- ✅ Proper event granularity
- ✅ Better event store queryability
- ✅ Follows Event Sourcing best practices
- ✅ Easier to add new event types later

**Cons:**
- ❌ More events to handle
- ❌ More complex implementation
- ❌ Requires careful ordering

### Option C: Use CommitCreatedEvent (Simplest)

Reuse existing CommitCreatedEvent:

**SquashCommandHandler:**
```java
// 1. Compute combined patch
RDFPatch combinedPatch = ...;
int patchSize = RdfPatchUtil.countOperations(combinedPatch);

// 2. Create CommitCreatedEvent (reuse existing event!)
CommitCreatedEvent event = new CommitCreatedEvent(
    command.dataset(),
    newCommitId.value(),
    List.of(baseCommitId.value()),
    command.branch(),
    command.message(),
    author,
    Instant.now(),
    patchString,
    patchSize
);

// 3. Publish event (projector handles repository save)
eventPublisher.publish(event);

// 4. Optionally: Publish SquashMetadataEvent for tracking
```

**Pros:**
- ✅ Reuses existing infrastructure
- ✅ Projector already handles CommitCreatedEvent
- ✅ Minimal code changes
- ✅ No new event types needed

**Cons:**
- ❌ Loses semantic meaning (was this a squash or regular commit?)
- ❌ Need separate event for squash metadata (which commits were squashed)

---

## Recommended Approach: Option A (Enrich Events)

**Rationale:**
1. **Atomicity**: Rebase is a single business transaction (like Git rebase - all or nothing)
2. **Event size**: Kafka configured for hundreds of MBs (graphs), so 1-5MB events are trivial
3. **Semantic clarity**: Maintains meaning of squash/rebase operations
4. **Single transaction boundary**: Projector creates all commits atomically
5. **Simpler failure handling**: Single event = single retry boundary

**Why not multiple events?**
- Partial rebase on failure = broken state (50 of 100 commits created)
- Complex compensating logic needed for rollback
- Violates atomicity requirement of rebase operation

---

## Implementation Steps (Option A)

### Step 1: Update CommitsSquashedEvent

**File:** [CommitsSquashedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java)

```java
public record CommitsSquashedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branch") String branch,
    @JsonProperty("newCommitId") String newCommitId,
    @JsonProperty("squashedCommitIds") List<String> squashedCommitIds,
    @JsonProperty("parents") List<String> parents,       // ← NEW
    @JsonProperty("author") String author,
    @JsonProperty("message") String message,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("previousHead") String previousHead,
    @JsonProperty("rdfPatch") String rdfPatch,           // ← NEW
    @JsonProperty("patchSize") int patchSize             // ← NEW
) implements VersionControlEvent {

  public CommitsSquashedEvent {
    // ... existing validation ...

    Objects.requireNonNull(parents, "Parents cannot be null");
    Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

    if (patchSize < 0) {
      throw new IllegalArgumentException("Patch size cannot be negative");
    }

    // Defensive copy
    parents = List.copyOf(parents);
  }
}
```

### Step 2: Update BranchRebasedEvent

**File:** [BranchRebasedEvent.java](../../src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java)

```java
public record BranchRebasedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branch") String branch,
    @JsonProperty("newHead") String newHead,
    @JsonProperty("previousHead") String previousHead,
    @JsonProperty("rebasedCommits") List<RebasedCommitData> rebasedCommits,  // ← CHANGED
    @JsonProperty("author") String author,
    @JsonProperty("timestamp") Instant timestamp
) implements VersionControlEvent {

  // Nested record with full commit data
  public record RebasedCommitData(
      @JsonProperty("commitId") String commitId,
      @JsonProperty("parents") List<String> parents,
      @JsonProperty("author") String author,
      @JsonProperty("message") String message,
      @JsonProperty("rdfPatch") String rdfPatch,
      @JsonProperty("patchSize") int patchSize
  ) {
    public RebasedCommitData {
      Objects.requireNonNull(commitId, "Commit ID cannot be null");
      Objects.requireNonNull(parents, "Parents cannot be null");
      Objects.requireNonNull(author, "Author cannot be null");
      Objects.requireNonNull(message, "Message cannot be null");
      Objects.requireNonNull(rdfPatch, "RDF Patch cannot be null");

      if (patchSize < 0) {
        throw new IllegalArgumentException("Patch size cannot be negative");
      }

      parents = List.copyOf(parents);
    }
  }
}
```

### Step 3: Update SquashCommandHandler

**File:** [SquashCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java)

**Remove direct repository writes:**

```java
// 8. Create new squashed commit metadata
CommitId newCommitId = CommitId.generate();
String author = command.author() != null ? command.author() : firstCommit.author();
int patchSize = RdfPatchUtil.countOperations(combinedPatch);
String patchString = RdfPatchUtil.toString(combinedPatch);

// ❌ REMOVE THIS:
// Commit newCommit = new Commit(...);
// commitRepository.save(command.dataset(), newCommit, combinedPatch);
// branch.updateCommit(newCommitId);
// branchRepository.save(command.dataset(), branch);

// ✅ CREATE EVENT WITH FULL DATA:
String previousHead = branch.getCommitId().value();
VersionControlEvent event = new CommitsSquashedEvent(
    command.dataset(),
    command.branch(),
    newCommitId.value(),
    command.commitIds(),
    List.of(baseCommitId.value()),  // ← NEW: parents
    author,
    command.message(),
    Instant.now(),
    previousHead,
    patchString,                    // ← NEW: patch
    patchSize                       // ← NEW: patchSize
);

// Publish event (projector will handle repository updates)
eventPublisher.publish(event);

return event;
```

### Step 4: Update RebaseCommandHandler

**File:** [RebaseCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java)

**Remove direct repository writes, collect commit data for event:**

```java
// Build list of rebased commits with full data
List<BranchRebasedEvent.RebasedCommitData> rebasedCommits = new ArrayList<>();
CommitId currentCommitId = newBaseCommit;

for (String commitId : commitsToRebase) {
    // Get original commit and patch
    Commit originalCommit = commitRepository.findByDatasetAndId(...).orElseThrow();
    RDFPatch patch = commitRepository.findPatch(...);

    // Create new commit metadata
    CommitId newCommitId = CommitId.generate();
    String patchString = RdfPatchUtil.toString(patch);
    int patchSize = originalCommit.patchSize();

    // ❌ REMOVE THIS:
    // Commit newCommit = new Commit(...);
    // commitRepository.save(command.dataset(), newCommit, patch);

    // ✅ COLLECT DATA FOR EVENT:
    rebasedCommits.add(new BranchRebasedEvent.RebasedCommitData(
        newCommitId.value(),
        List.of(currentCommitId.value()),
        originalCommit.author(),
        originalCommit.message(),
        patchString,
        patchSize
    ));

    currentCommitId = newCommitId;
}

// ❌ REMOVE THIS:
// branch.updateCommit(currentCommitId);
// branchRepository.save(command.dataset(), branch);

// ✅ CREATE EVENT WITH FULL DATA:
String previousHead = branch.getCommitId().value();
VersionControlEvent event = new BranchRebasedEvent(
    command.dataset(),
    command.branch(),
    currentCommitId.value(),
    previousHead,
    rebasedCommits,  // ← Full commit data
    command.author(),
    Instant.now()
);

// Publish event (projector will handle repository updates)
eventPublisher.publish(event);

return event;
```

### Step 5: Update ReadModelProjector

**File:** [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

**Add commit creation logic:**

#### For CommitsSquashedEvent (line 650):
```java
void handleCommitsSquashed(CommitsSquashedEvent event) {
    logger.debug("Processing CommitsSquashedEvent...");

    // Check if branch exists
    if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
      logger.debug("Skipping squash for non-existent branch...");
      return;
    }

    // ✅ CREATE COMMIT FROM EVENT DATA:
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    Commit commit = new Commit(
        CommitId.of(event.newCommitId()),
        event.parents().stream().map(CommitId::of).toList(),
        event.author(),
        event.message(),
        event.timestamp(),
        event.patchSize()
    );

    // Save commit with patch
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch to point to squashed commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.newCommitId())
    );

    // Notify DatasetService
    datasetService.updateLatestCommit(...);
}
```

#### For BranchRebasedEvent (line 407):
```java
void handleBranchRebased(BranchRebasedEvent event) {
    logger.debug("Processing BranchRebasedEvent...");

    // Check if branch exists
    if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
      logger.debug("Skipping rebase for non-existent branch...");
      return;
    }

    // ✅ CREATE ALL REBASED COMMITS:
    for (BranchRebasedEvent.RebasedCommitData commitData : event.rebasedCommits()) {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
            commitData.rdfPatch().getBytes(StandardCharsets.UTF_8));
        RDFPatch patch = RDFPatchOps.read(inputStream);

        Commit commit = new Commit(
            CommitId.of(commitData.commitId()),
            commitData.parents().stream().map(CommitId::of).toList(),
            commitData.author(),
            commitData.message(),
            event.timestamp(),
            commitData.patchSize()
        );

        // Save commit with patch
        commitRepository.save(event.dataset(), commit, patch);
    }

    // Update branch to point to final rebased commit
    branchRepository.updateBranchHead(
        event.dataset(),
        event.branch(),
        CommitId.of(event.newHead())
    );

    // Notify DatasetService
    datasetService.updateLatestCommit(...);
}
```

### Step 6: Update All Tests

**Major test updates needed:**

1. **Event tests:**
   - Update CommitsSquashedEvent tests with new fields
   - Update BranchRebasedEvent tests with new structure
   - Add validation tests for new fields

2. **Command handler tests:**
   - Update mocks (no repository writes)
   - Verify events contain full data
   - Assert repository NOT called

3. **Projector tests:**
   - Enable projector: `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
   - Use `await()` for async verification
   - Verify commits created from events

4. **Integration tests:**
   - Test eventual consistency (response before projection)
   - Verify event replay creates commits correctly

---

## Testing Strategy

### Unit Tests

**Test command handlers don't write to repository:**
```java
@Test
void squash_shouldNotWriteToRepository() {
  // Act
  squashCommandHandler.handle(command);

  // Assert: No repository writes
  verify(commitRepository, never()).save(any(), any(), any());
  verify(branchRepository, never()).save(any(), any());
}
```

**Test events contain full data:**
```java
@Test
void squash_shouldCreateEventWithFullData() {
  // Act
  VersionControlEvent event = squashCommandHandler.handle(command);

  // Assert
  assertThat(event).isInstanceOf(CommitsSquashedEvent.class);
  CommitsSquashedEvent squashEvent = (CommitsSquashedEvent) event;
  assertThat(squashEvent.rdfPatch()).isNotNull();
  assertThat(squashEvent.patchSize()).isGreaterThan(0);
  assertThat(squashEvent.parents()).isNotEmpty();
}
```

### Integration Tests

**Test eventual consistency:**
```java
@Test
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
void squash_shouldHaveEventualConsistency() throws Exception {
  // Arrange
  String commitId1 = createCommit();
  String commitId2 = createCommit();

  // Act: Squash commits
  ResponseEntity<String> response = squashCommits(List.of(commitId1, commitId2));

  // Assert: Response immediate (202 Accepted)
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

  // Extract new commit ID from response
  String newCommitId = extractCommitId(response);

  // Assert: Commit doesn't exist yet (eventual consistency)
  assertThat(commitRepository.findById(newCommitId)).isEmpty();

  // Assert: Wait for projection
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        Commit squashedCommit = commitRepository.findById(newCommitId).get();
        assertThat(squashedCommit).isNotNull();
        assertThat(squashedCommit.patchSize()).isGreaterThan(0);
      });
}
```

---

## Event Schema Migration

### Breaking Change

This is a **major breaking change** to event schema.

### Migration Strategy

#### Option 1: Versioned Events (Recommended)
```java
// Keep old event for backward compatibility
@Deprecated
public record CommitsSquashedEventV1(...) implements VersionControlEvent { }

// New event with full data
public record CommitsSquashedEventV2(...) implements VersionControlEvent { }

// Projector handles both
@KafkaListener
void handleSquash(VersionControlEvent event) {
    switch (event) {
        case CommitsSquashedEventV1 old -> handleSquashV1(old);  // Legacy
        case CommitsSquashedEventV2 newer -> handleSquashV2(newer);  // New
    }
}
```

#### Option 2: Clear Event Store (Development Only)
- Delete all Kafka topics
- Restart with new event schema
- Acceptable for development/testing

**Recommendation:** Use Option 2 for now (not in production), plan Option 1 for production.

---

## Success Criteria

- ✅ `SquashCommandHandler` does NOT write to repository
- ✅ `RebaseCommandHandler` does NOT write to repository
- ✅ `CommitsSquashedEvent` contains full commit data (patch, patchSize, parents)
- ✅ `BranchRebasedEvent` contains full commit data for all rebased commits
- ✅ `ReadModelProjector` creates commits from events
- ✅ All tests updated and passing (especially projector tests with `await()`)
- ✅ Integration tests verify eventual consistency
- ✅ Event replay correctly recreates commits
- ✅ Zero quality violations
- ✅ Full build passes: `mvn -q clean install`

---

## Benefits

### CQRS Compliance
- ✅ Pure command/query separation
- ✅ No dual-write pattern
- ✅ HTTP 202 returns before repository update

### Event Sourcing
- ✅ Events contain complete data
- ✅ Event replay works correctly
- ✅ Audit trail is complete

### Testing
- ✅ Proper eventual consistency testing
- ✅ Projector tests verify async behavior
- ✅ Clear separation of concerns

---

## Risks

### High Complexity
- Multiple commits in one event (rebase)
- More complex projector logic (creates multiple commits atomically)

### Event Size - NOT A CONCERN ✅
- **Context**: Kafka configured for several hundred MBs (needed for storing graphs)
- Rebase of 100 commits = ~1-5MB (well within limits)
- Even 1000 commits would be manageable
- **No pagination needed**

### Atomicity - REQUIRED FOR CORRECTNESS ✅
- **Rebase is conceptually atomic** (like Git rebase)
- Single event ensures all commits created together or none
- Multiple events would risk partial rebase on failure
- **Option A correctly models business semantics**

### Migration Challenges
- Breaking change to event schema
- Need backward compatibility strategy (or clear event store)
- Careful testing required

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java`
- `src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java`
- `src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java`
- `src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java`
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

### Test Code (~20+ files)
- All squash/rebase related tests
- Event serialization tests
- Projector tests (must enable projector!)
- Integration tests for eventual consistency

---

## References

- [SquashCommandHandler.java:111](../../src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java#L111)
- [RebaseCommandHandler.java:133](../../src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java#L133)
- [CommitsSquashedEvent.java](../../src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java)
- [BranchRebasedEvent.java](../../src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java)
- [ReadModelProjector.java:407](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L407) (rebase handler)
- [ReadModelProjector.java:650](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L650) (squash handler)
- [CQRS Guide - Dual Writes](../../docs/architecture/cqrs-event-sourcing.md)

---

## Estimated Effort Breakdown

- Event schema design: 1 hour
- Update CommitsSquashedEvent: 1 hour
- Update BranchRebasedEvent: 1-2 hours (more complex)
- Update SquashCommandHandler: 1 hour
- Update RebaseCommandHandler: 2 hours
- Update projector handlers: 2 hours
- Update tests: 3-4 hours
- Integration testing: 1-2 hours

**Total:** 8-12 hours

---

## Alternative: Accept Current Design

**If this refactoring is too complex**, consider:

1. **Document the exception** in architecture docs
2. **Add compensating transaction** if Kafka publish fails
3. **Monitor for inconsistencies** (commit exists but no event)
4. **Defer until production deployment** is planned

This is technical debt, not a critical bug. The system works correctly in normal operation.
