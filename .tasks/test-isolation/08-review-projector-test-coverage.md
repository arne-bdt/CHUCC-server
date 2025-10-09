# Task 08: Review GraphEventProjectorIT Coverage

## Objective
Analyze GraphEventProjectorIT to identify which event handlers in ReadModelProjector have test coverage and which are missing, guiding Task 09 and 10 implementation.

## Background
ReadModelProjector has 10 event handler methods:
1. handleCommitCreated
2. handleBranchCreated
3. handleBranchReset
4. handleBranchRebased
5. handleTagCreated
6. handleRevertCreated
7. handleSnapshotCreated
8. handleCherryPicked
9. handleCommitsSquashed
10. handleBatchGraphsCompleted

With projector disabled by default in most tests, we must ensure ALL handlers have dedicated projector tests.

## Tasks

### 1. Review ReadModelProjector Event Handlers

Check `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`:

```bash
grep -n "void handle" src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java
```

List all handler methods with line numbers for reference.

### 2. Review Current GraphEventProjectorIT Tests

Check `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`:

```bash
grep -n "@Test" src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java
```

List all test methods to see what's currently covered.

### 3. Create Coverage Matrix

Document which handlers have tests:

| Event Handler | GraphEventProjectorIT Test | Other Integration Test | Dedicated Test Needed? |
|---------------|---------------------------|------------------------|------------------------|
| handleCommitCreated | ✅ putGraphEvent_shouldBeProjected | ✅ Multiple GSP tests | ✅ Already covered |
| handleBranchCreated | ❌ Missing | ✅ BranchOperationsIT? | ⚠️ Verify coverage |
| handleBranchReset | ❌ Missing | ✅ ResetOperationIT? | ⚠️ Verify coverage |
| handleBranchRebased | ❌ Missing | ✅ RebaseIntegrationTest? | ⚠️ Verify coverage |
| handleTagCreated | ❌ Missing | ✅ TagOperationsIT? | ⚠️ Verify coverage |
| handleRevertCreated | ❌ Missing | ✅ RevertIntegrationTest? | ⚠️ Verify coverage |
| handleSnapshotCreated | ❌ Missing | ❌ No test | ❌ Test needed |
| handleCherryPicked | ❌ Missing | ✅ CherryPickIT? | ⚠️ Verify coverage |
| handleCommitsSquashed | ❌ Missing | ✅ SquashIntegrationTest? | ⚠️ Verify coverage |
| handleBatchGraphsCompleted | ✅ batchGraphsEvent_shouldBeProjected | N/A | ✅ Already covered |

*To be updated during task execution*

### 4. Check Other Integration Tests

For each "⚠️ Verify coverage" entry, check if the test has projector enabled:

```bash
# Check if test uses @TestPropertySource to enable projector
grep -l "@TestPropertySource.*kafka.listener.enabled" src/test/java/org/chucc/vcserver/integration/*IntegrationTest.java

# Check if test uses await() (might depend on projection)
grep -l "await()" src/test/java/org/chucc/vcserver/integration/*IntegrationTest.java
```

**Key questions for each test:**
1. Does it have `@TestPropertySource(properties = "kafka.listener.enabled=true")`?
2. Does it use `await()` to verify repository state after event processing?
3. Does it assert on repository state that would only be updated by projector?

**If NO to all three:** The test only verifies API layer, handler needs dedicated projector test.
**If YES to any:** The test already verifies projection, handler might be covered.

### 5. Categorize Missing Tests

Based on analysis, create two categories:

**Category A: High Priority** (handlers with zero test coverage)
- handleSnapshotCreated - No existing test verifies this
- [Add others found with no coverage]

**Category B: Medium Priority** (handlers with potential coverage in other tests)
- Tests exist but don't explicitly verify projection
- Tests exist but projector might be disabled
- [Add handlers that need verification]

## Expected Findings

### Currently Tested in GraphEventProjectorIT

From visual inspection of existing test:
- ✅ handleCommitCreated - Multiple tests (PUT, POST, DELETE, PATCH)
- ✅ handleBatchGraphsCompleted - batchGraphsEvent_shouldBeProjected

### Likely Tested Elsewhere (Needs Verification)

These operations have integration tests, but we need to verify projector is enabled:
- handleBranchCreated - BranchCreated events from /version/branches
- handleBranchReset - ResetOperationIntegrationTest
- handleBranchRebased - RebaseIntegrationTest
- handleTagCreated - TagOperationsIntegrationTest
- handleRevertCreated - RevertIntegrationTest
- handleCherryPicked - CherryPickIntegrationTest
- handleCommitsSquashed - SquashIntegrationTest

### Likely Untested

- handleSnapshotCreated - Snapshot feature might not have integration tests yet

## Acceptance Criteria

- [x] All 10 event handlers documented in coverage matrix
- [x] All current GraphEventProjectorIT tests listed
- [x] All other integration tests checked for projector enablement
- [x] Coverage matrix completed with actual findings
- [x] Category A (version control operations) tests identified (3 handlers)
- [x] Category B (advanced operations) tests identified (3 handlers)
- [x] Clear recommendation for Tasks 09-10 (6 tests, 3-4 hours estimated)

## Dependencies

- Task 01 must be completed (projector disabled by default)
- Task 04 must be completed (GraphEventProjectorIT enables projector)

## Next Task

Task 09: Add Projector Tests for Version Control Operations (implement missing tests)

## Estimated Complexity

Medium (30-45 minutes)
- Review ReadModelProjector: 10 minutes
- Review GraphEventProjectorIT: 10 minutes
- Check other integration tests: 15-20 minutes
- Documentation: 5-10 minutes

## Coverage Matrix

**Completed: 2025-10-09 19:00**

| Handler Method | Line # | Projector Test Coverage | Needs New Test? |
|----------------|--------|------------------------|-----------------|
| handleCommitCreated | 112 | ✅ GraphEventProjectorIT (6 tests)<br>✅ ReadModelProjectorIT<br>✅ ReadModelProjectorKillRestartIT<br>✅ SnapshotServiceIT | ✅ Well covered |
| handleBranchCreated | 144 | ✅ ReadModelProjectorIT<br>✅ ReadModelProjectorKillRestartIT | ✅ Well covered |
| handleBranchReset | 164 | ✅ SnapshotServiceIT | ✅ Well covered |
| handleBranchRebased | 188 | ❌ No projector test | ❌ **Test needed** |
| handleTagCreated | 215 | ❌ No projector test | ❌ **Test needed** |
| handleRevertCreated | 229 | ❌ No projector test | ❌ **Test needed** |
| handleSnapshotCreated | 277 | ⚠️ SnapshotServiceIT creates events, but doesn't verify projection | ⚠️ **Verify needed** |
| handleCherryPicked | 292 | ❌ No projector test | ❌ **Test needed** |
| handleCommitsSquashed | 343 | ❌ No projector test | ❌ **Test needed** |
| handleBatchGraphsCompleted | 370 | ✅ GraphEventProjectorIT | ✅ Well covered |

**Summary:**
- ✅ **4/10 handlers** well tested with projector enabled
- ❌ **5/10 handlers** need new projector tests
- ⚠️ **1/10 handlers** need verification test

## Detailed Findings

**Tests with Projector Enabled:**
1. **GraphEventProjectorIT** (integration/)
   - 6 tests, all testing handleCommitCreated and handleBatchGraphsCompleted
   - Verifies async projection from GSP operations

2. **ReadModelProjectorIT** (projection/)
   - 4 tests testing handleCommitCreated and handleBranchCreated
   - Verifies basic projection and event ordering

3. **ReadModelProjectorKillRestartIT** (projection/)
   - 2 tests testing handleCommitCreated and handleBranchCreated
   - Verifies projector recovery after restart

4. **SnapshotServiceIT** (service/)
   - 3 tests using handleCommitCreated and handleBranchReset
   - Verifies snapshot creation at intervals

**Handlers Needing Tests (6 total):**

**Category A: Version Control Operations (Task 09)**
1. **handleBranchRebased** (line 188)
   - Operation: Rebase updates branch head to new commit chain
   - What to test: BranchRebasedEvent → branch HEAD updated in repository
   - Test exists: RebaseIntegrationTest (but projector disabled)

2. **handleRevertCreated** (line 229)
   - Operation: Revert creates new commit that undoes changes
   - What to test: RevertCreatedEvent → commit saved to repository
   - Test exists: RevertIntegrationTest (but projector disabled)

3. **handleSnapshotCreated** (line 277)
   - Operation: Snapshot event for branch state
   - What to test: SnapshotCreatedEvent → verify projector handles it (currently untested)
   - Note: SnapshotServiceIT creates snapshots but doesn't verify projection

**Category B: Advanced Operations (Task 10)**
4. **handleTagCreated** (line 215)
   - Operation: Tag created pointing to commit
   - What to test: TagCreatedEvent → tag saved to repository
   - Test exists: TagOperationsIntegrationTest (but projector disabled)

5. **handleCherryPicked** (line 292)
   - Operation: Cherry-pick applies commit to different branch
   - What to test: CherryPickedEvent → new commit created on target branch
   - Test exists: CherryPickIntegrationTest (but projector disabled)

6. **handleCommitsSquashed** (line 343)
   - Operation: Squash combines multiple commits into one
   - What to test: CommitsSquashedEvent → new squashed commit created
   - Test exists: SquashIntegrationTest (but projector disabled)

## Recommendation for Tasks 09-10

**Task 09: Add Version Control Operation Projector Tests** (~2 hours)
Add to ReadModelProjectorIT or create new VersionControlProjectorIT:
1. handleBranchRebased test
   - Publish BranchRebasedEvent
   - Verify branch HEAD updated
   - Verify old/new commit IDs tracked

2. handleRevertCreated test
   - Publish RevertCreatedEvent with revert commit
   - Verify revert commit saved to repository
   - Verify commit chain preserved

3. handleSnapshotCreated test
   - Publish SnapshotCreatedEvent
   - Verify projector processes it without errors
   - Verify any snapshot metadata is stored (if applicable)

**Task 10: Add Advanced Operation Projector Tests** (~2 hours)
Add to ReadModelProjectorIT or create new AdvancedOperationsProjectorIT:
1. handleTagCreated test
   - Publish TagCreatedEvent
   - Verify tag saved to repository
   - Verify tag points to correct commit

2. handleCherryPicked test
   - Publish CherryPickedEvent with new commit on target branch
   - Verify new commit saved
   - Verify commit parent chain correct

3. handleCommitsSquashed test
   - Publish CommitsSquashedEvent with squashed commit
   - Verify squashed commit saved
   - Verify commit replaces multiple commits

**Estimated Total:**
- New tests needed: **6 tests**
- Estimated effort: **3-4 hours** (1.5-2 hours per task)
- Files to modify: ReadModelProjectorIT or create 1-2 new test classes
- Pattern: Follow existing GraphEventProjectorIT structure

**Alternative Approach:**
Instead of adding to ReadModelProjectorIT, create dedicated test classes:
- **VersionControlProjectorIT** (Task 09): Rebase, Revert, Snapshot
- **AdvancedOperationsProjectorIT** (Task 10): Tag, CherryPick, Squash

This keeps tests organized by feature area and makes them easier to maintain.
