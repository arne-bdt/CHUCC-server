# Task: Fix CQRS Violations in RebaseCommandHandler and SquashCommandHandler

**Status:** Not Started
**Priority:** High
**Estimated Time:** 2-3 sessions (8-12 hours)
**Dependencies:** None

---

## Context

**Problem:** `RebaseCommandHandler` and `SquashCommandHandler` violate the CQRS + Event Sourcing architecture by directly updating repositories instead of letting the `ReadModelProjector` handle it.

**Current (WRONG) pattern:**
```
Command Handler → Update repositories → Create event → Publish event
                    ↑ VIOLATION
```

**Correct CQRS pattern:**
```
Command Handler → Create event → Publish event (async)
                                       ↓
                                 Kafka Topic
                                       ↓
                  Projector → Update repositories (async)
```

**Why this matters:**
1. **Breaks Event Sourcing**: Repositories updated BEFORE event published means events aren't source of truth
2. **Breaks eventual consistency**: HTTP response returns synchronously, defeating async architecture benefits
3. **Breaks event replay**: Rebuilding from events would update repositories twice (handler + projector)
4. **Breaks CQRS separation**: Command side shouldn't update read model (repositories)

**Discovered by:** CQRS compliance checker on 2025-10-11
**Related to:** DeleteDatasetCommandHandler fix (which correctly records outcomes in events)

---

## Affected Files

### 1. RebaseCommandHandler
**File:** `src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java`
**Lines:** 132, 141
**Issue:** Saves commits and updates branch directly

### 2. SquashCommandHandler
**File:** `src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java`
**Lines:** 109, 114
**Issue:** Saves commit and updates branch directly

---

## Implementation Plan

### Step 1: Analyze Current Event Schemas

Review what information events currently contain:

**BranchRebasedEvent:**
```java
public record BranchRebasedEvent(
    String dataset,
    String branch,
    String newHead,           // Final rebased commit
    String previousHead,      // Old head
    List<String> newCommits,  // List of new commit IDs
    String author,
    Instant timestamp
) {}
```

**Missing:** RDF patches for each rebased commit!

**CommitsSquashedEvent:**
```java
public record CommitsSquashedEvent(
    String dataset,
    String branch,
    String newCommitId,           // Squashed commit ID
    List<String> squashedCommitIds, // Original commit IDs
    String author,
    String message,
    Instant timestamp,
    String previousHead
) {}
```

**Missing:** Combined RDF patch and parent commit ID!

---

### Step 2: Update Event Schemas

#### 2a. Update BranchRebasedEvent

**File:** `src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java`

**Add field:**
```java
public record BranchRebasedEvent(
    String dataset,
    String branch,
    String newHead,
    String previousHead,
    List<String> newCommits,
    List<String> rdfPatches,  // ← ADD THIS: One patch per commit
    String author,
    Instant timestamp
) implements VersionControlEvent {

  public BranchRebasedEvent {
    // Validation
    if (newCommits.size() != rdfPatches.size()) {
      throw new IllegalArgumentException(
        "Number of commits must match number of patches");
    }
  }
}
```

#### 2b. Update CommitsSquashedEvent

**File:** `src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java`

**Add fields:**
```java
public record CommitsSquashedEvent(
    String dataset,
    String branch,
    String newCommitId,
    List<String> squashedCommitIds,
    String author,
    String message,
    Instant timestamp,
    String previousHead,
    String rdfPatch,        // ← ADD THIS: Combined patch
    String parentCommitId   // ← ADD THIS: Parent of squashed commit
) implements VersionControlEvent {}
```

---

### Step 3: Update RebaseCommandHandler

**File:** `src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java`

**Current (WRONG):**
```java
@Override
public VersionControlEvent handle(RebaseCommand command) {
  // ... validation and commit range calculation ...

  // ❌ VIOLATION: Creating commits in command handler
  CommitId currentCommitId = newBaseCommit;
  for (Commit originalCommit : commitsToRebase) {
    CommitId newCommitId = CommitId.generate();

    RDFPatch patch = commitRepository
        .findPatchByDatasetAndId(command.dataset(), originalCommit.id())
        .orElseThrow(...);

    Commit newCommit = new Commit(
        newCommitId,
        List.of(currentCommitId),  // Parent is previous rebased commit
        originalCommit.author(),
        originalCommit.message(),
        Instant.now()
    );

    // ❌ VIOLATION: Saving to repository
    commitRepository.save(command.dataset(), newCommit, patch);

    currentCommitId = newCommitId;
  }

  // ❌ VIOLATION: Updating branch
  branch.updateCommit(currentCommitId);
  branchRepository.save(command.dataset(), branch);

  // Event created AFTER repository updates
  VersionControlEvent event = new BranchRebasedEvent(...);
  eventPublisher.publish(event);

  return event;
}
```

**Correct (SHOULD BE):**
```java
@Override
public VersionControlEvent handle(RebaseCommand command) {
  // ... validation and commit range calculation ...

  // ✅ GOOD: Collect information WITHOUT updating repositories
  List<String> newCommitIds = new ArrayList<>();
  List<String> rdfPatches = new ArrayList<>();

  for (Commit originalCommit : commitsToRebase) {
    CommitId newCommitId = CommitId.generate();
    newCommitIds.add(newCommitId.value());

    // Get the patch (read-only)
    RDFPatch patch = commitRepository
        .findPatchByDatasetAndId(command.dataset(), originalCommit.id())
        .orElseThrow(...);

    // Serialize patch to string
    String patchString = serializePatch(patch);
    rdfPatches.add(patchString);
  }

  // ✅ GOOD: Create event with ALL information for projector
  VersionControlEvent event = new BranchRebasedEvent(
      command.dataset(),
      command.branch(),
      newCommitIds.get(newCommitIds.size() - 1), // Final commit
      branch.getCommitId().value(),               // Previous head
      newCommitIds,
      rdfPatches,  // ← NEW: Patches for projector to use
      command.author(),
      Instant.now()
  );

  // ✅ GOOD: Publish event (async, projector will update repositories)
  eventPublisher.publish(event)
      .exceptionally(ex -> {
        logger.error("Failed to publish event {}: {}",
            event.getClass().getSimpleName(), ex.getMessage(), ex);
        return null;
      });

  // ✅ GOOD: Return event immediately (no repository updates)
  return event;
}

private String serializePatch(RDFPatch patch) {
  ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
  RDFPatchOps.write(outputStream, patch);
  return outputStream.toString(java.nio.charset.StandardCharsets.UTF_8);
}
```

---

### Step 4: Update SquashCommandHandler

**File:** `src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java`

**Current (WRONG):**
```java
@Override
public VersionControlEvent handle(SquashCommand command) {
  // ... validation and diff calculation ...

  // ❌ VIOLATION: Saving commit
  Commit newCommit = new Commit(
      newCommitId,
      List.of(baseCommitId),
      command.author(),
      command.message(),
      Instant.now()
  );
  commitRepository.save(command.dataset(), newCommit, combinedPatch);

  // ❌ VIOLATION: Updating branch
  String previousHead = branch.getCommitId().value();
  branch.updateCommit(newCommitId);
  branchRepository.save(command.dataset(), branch);

  VersionControlEvent event = new CommitsSquashedEvent(...);
  eventPublisher.publish(event);
  return event;
}
```

**Correct (SHOULD BE):**
```java
@Override
public VersionControlEvent handle(SquashCommand command) {
  // ... validation and diff calculation ...

  // ✅ GOOD: Generate commit ID without saving
  CommitId newCommitId = CommitId.generate();

  // ✅ GOOD: Serialize patch to string
  String patchString = serializePatch(combinedPatch);

  // ✅ GOOD: Create event with ALL information
  VersionControlEvent event = new CommitsSquashedEvent(
      command.dataset(),
      command.branch(),
      newCommitId.value(),
      command.commitIds(),
      command.author(),
      command.message(),
      Instant.now(),
      branch.getCommitId().value(),  // Previous head
      patchString,                    // ← NEW: Patch for projector
      baseCommitId.value()            // ← NEW: Parent commit
  );

  // ✅ GOOD: Publish event (async)
  eventPublisher.publish(event)
      .exceptionally(ex -> {
        logger.error("Failed to publish event {}: {}",
            event.getClass().getSimpleName(), ex.getMessage(), ex);
        return null;
      });

  // ✅ GOOD: Return event immediately
  return event;
}
```

---

### Step 5: Update ReadModelProjector

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

#### 5a. Update handleBranchRebased

**Current:**
```java
void handleBranchRebased(BranchRebasedEvent event) {
  // Only updates branch head
  branchRepository.updateBranchHead(
      event.dataset(),
      event.branch(),
      CommitId.of(event.newHead())
  );
}
```

**Correct:**
```java
void handleBranchRebased(BranchRebasedEvent event) {
  logger.debug("Processing BranchRebasedEvent: branch={}, commits={}, dataset={}",
      event.branch(), event.newCommits().size(), event.dataset());

  // Check if branch exists
  if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
    logger.debug("Skipping rebase for non-existent branch: {} in dataset: {}",
        event.branch(), event.dataset());
    return;
  }

  // Process each rebased commit (create commit chain)
  CommitId currentParent = CommitId.of(event.previousHead());

  for (int i = 0; i < event.newCommits().size(); i++) {
    String commitIdStr = event.newCommits().get(i);
    String patchStr = event.rdfPatches().get(i);

    // Parse patch
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        patchStr.getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Get original commit info (for author/message)
    // Note: This info should ideally be in the event!
    // For now, use the rebase author and indicate it's a rebase
    Commit commit = new Commit(
        CommitId.of(commitIdStr),
        List.of(currentParent),
        event.author(),
        "Rebased commit", // TODO: Include original message in event
        Instant.now()
    );

    // Save commit
    commitRepository.save(event.dataset(), commit, patch);

    currentParent = CommitId.of(commitIdStr);
  }

  // Update branch to point to final rebased commit
  branchRepository.updateBranchHead(
      event.dataset(),
      event.branch(),
      CommitId.of(event.newHead())
  );

  logger.debug("Rebased {} commits on branch: {} in dataset: {}",
      event.newCommits().size(), event.branch(), event.dataset());
}
```

#### 5b. Update handleCommitsSquashed

**Current:**
```java
void handleCommitsSquashed(CommitsSquashedEvent event) {
  // Only updates branch head
  branchRepository.updateBranchHead(
      event.dataset(),
      event.branch(),
      CommitId.of(event.newCommitId())
  );
}
```

**Correct:**
```java
void handleCommitsSquashed(CommitsSquashedEvent event) {
  logger.debug("Processing CommitsSquashedEvent: newCommit={}, squashedCount={}, dataset={}",
      event.newCommitId(), event.squashedCommitIds().size(), event.dataset());

  // Check if branch exists
  if (branchRepository.findByDatasetAndName(event.dataset(), event.branch()).isEmpty()) {
    logger.debug("Skipping squash for non-existent branch: {} in dataset: {}",
        event.branch(), event.dataset());
    return;
  }

  // Parse RDF patch
  ByteArrayInputStream inputStream = new ByteArrayInputStream(
      event.rdfPatch().getBytes(StandardCharsets.UTF_8));
  RDFPatch patch = RDFPatchOps.read(inputStream);

  // Create squashed commit
  Commit commit = new Commit(
      CommitId.of(event.newCommitId()),
      List.of(CommitId.of(event.parentCommitId())),
      event.author(),
      event.message(),
      event.timestamp()
  );

  // Save commit
  commitRepository.save(event.dataset(), commit, patch);

  // Update branch to point to squashed commit
  branchRepository.updateBranchHead(
      event.dataset(),
      event.branch(),
      CommitId.of(event.newCommitId())
  );

  logger.debug("Squashed {} commits into {} on branch: {} in dataset: {}",
      event.squashedCommitIds().size(), event.newCommitId(),
      event.branch(), event.dataset());
}
```

---

### Step 6: Update Tests

#### 6a. Update RebaseCommandHandler tests

**File:** `src/test/java/org/chucc/vcserver/command/RebaseCommandHandlerTest.java`

- Remove expectations for `commitRepository.save()`
- Remove expectations for `branchRepository.save()`
- Verify event contains patches

#### 6b. Update SquashCommandHandler tests

**File:** `src/test/java/org/chucc/vcserver/command/SquashCommandHandlerTest.java`

- Remove expectations for `commitRepository.save()`
- Remove expectations for `branchRepository.save()`
- Verify event contains patch and parent

#### 6c. Update projector integration tests

**File:** `src/test/java/org/chucc/vcserver/integration/AdvancedOperationsProjectorIT.java`

These tests should already enable the projector and use `await()`. Verify they still pass.

---

### Step 7: Update Event Schema Evolution Check

**File:** `src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java`

Add documentation about backward compatibility:

```java
/**
 * Event indicating that a branch was rebased.
 *
 * <p><strong>Schema Evolution:</strong></p>
 * <ul>
 *   <li><strong>Before:</strong> Event did not contain RDF patches</li>
 *   <li><strong>After:</strong> Event contains patches for all rebased commits</li>
 *   <li><strong>Migration:</strong> Old events without patches should be skipped during replay
 *     (rebase operations before this change cannot be replayed)</li>
 * </ul>
 */
```

---

## Testing Strategy

### Unit Tests
1. Test command handlers create events (don't update repositories)
2. Test projector handlers save commits and update branches
3. Mock all repository operations

### Integration Tests
1. Test rebase end-to-end with projector enabled
2. Test squash end-to-end with projector enabled
3. Verify commits are created correctly
4. Verify branches point to correct commits

### Manual Testing
1. Rebase a branch with 5 commits
2. Check Kafka for BranchRebasedEvent
3. Verify event contains all patches
4. Verify projector processes event correctly
5. Repeat for squash operation

---

## Success Criteria

- [ ] `RebaseCommandHandler` does NOT call `commitRepository.save()`
- [ ] `RebaseCommandHandler` does NOT call `branchRepository.save()`
- [ ] `SquashCommandHandler` does NOT call `commitRepository.save()`
- [ ] `SquashCommandHandler` does NOT call `branchRepository.save()`
- [ ] `BranchRebasedEvent` contains RDF patches
- [ ] `CommitsSquashedEvent` contains RDF patch and parent commit
- [ ] `ReadModelProjector.handleBranchRebased()` saves commits
- [ ] `ReadModelProjector.handleCommitsSquashed()` saves commit
- [ ] All unit tests pass
- [ ] All integration tests pass (with projector enabled)
- [ ] CQRS compliance checker reports no violations

---

## Rollback Plan

If issues arise:
1. Revert command handler changes (restore repository updates)
2. Revert event schema changes
3. Revert projector changes
4. Run full test suite
5. No data loss (old events still in Kafka)

**Note:** Once deployed, old events (without patches) cannot be replayed. Consider this before deploying to production.

---

## Migration Considerations

### Event Schema Changes

**Breaking change:** Old `BranchRebasedEvent` and `CommitsSquashedEvent` don't have patches.

**Options:**
1. **Accept limitation**: Old events cannot be replayed (document this)
2. **Version events**: Add `schemaVersion` field to events
3. **Dual write**: Support both old and new event formats during transition

**Recommended:** Option 1 (accept limitation) - rebase/squash operations are rare and typically not replayed.

---

## Additional Improvements (Optional)

### Enhancement 1: Include Original Commit Metadata in Rebase
Currently, rebased commits lose original author/message. Consider adding:
```java
public record RebasedCommitInfo(
    String commitId,
    String author,
    String message,
    String rdfPatch
) {}
```

Then update `BranchRebasedEvent` to use `List<RebasedCommitInfo>`.

### Enhancement 2: Event Versioning
Add schema versioning to all events:
```java
public interface VersionControlEvent {
  String dataset();
  default int schemaVersion() { return 1; }
}
```

---

## References

- **CQRS Guide:** `docs/architecture/cqrs-event-sourcing.md`
- **C4 Component Diagram:** `docs/architecture/c4-level3-component.md` (line 296)
- **Related Fix:** DeleteDatasetCommandHandler (correctly records outcomes)
- **Event Sourcing Principle:** Events must record what actually happened

---

## Notes

- This is the **last major CQRS violation** in the codebase
- After this fix, all command handlers follow the same pattern
- Consider running CQRS compliance checker regularly (CI/CD)
- This fix makes the codebase fully event-sourced and replay-safe
