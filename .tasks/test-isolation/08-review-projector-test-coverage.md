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

- [ ] All 10 event handlers documented in coverage matrix
- [ ] All current GraphEventProjectorIT tests listed
- [ ] All other integration tests checked for projector enablement
- [ ] Coverage matrix completed with actual findings
- [ ] Category A (high priority) tests identified
- [ ] Category B (medium priority) tests identified
- [ ] Clear recommendation for Tasks 09-10

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

*To be filled in during task execution with actual findings:*

```
Handler Method | Current Test Coverage | Line # in ReadModelProjector | Needs Test?
---------------|----------------------|----------------------------|-------------
handleCommitCreated          | [Details] | Line XXX | Yes/No
handleBranchCreated          | [Details] | Line XXX | Yes/No
handleBranchReset            | [Details] | Line XXX | Yes/No
handleBranchRebased          | [Details] | Line XXX | Yes/No
handleTagCreated             | [Details] | Line XXX | Yes/No
handleRevertCreated          | [Details] | Line XXX | Yes/No
handleSnapshotCreated        | [Details] | Line XXX | Yes/No
handleCherryPicked           | [Details] | Line XXX | Yes/No
handleCommitsSquashed        | [Details] | Line XXX | Yes/No
handleBatchGraphsCompleted   | [Details] | Line XXX | Yes/No
```

## Recommendation for Tasks 09-10

*To be filled in after analysis:*

```
Based on coverage analysis:

Task 09 should add tests for:
- [Handler 1] - No coverage
- [Handler 2] - No coverage
- [Handler 3] - Unverified coverage

Task 10 should add tests for:
- [Handler 4] - No coverage
- [Handler 5] - Unverified coverage

Estimated total new tests needed: X tests
Estimated effort: Y hours
```
