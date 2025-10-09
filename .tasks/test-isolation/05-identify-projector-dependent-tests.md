# Task 05: Identify Tests Depending on Async Projection

## Objective
Search for integration tests that might depend on ReadModelProjector's async event processing, and verify they work correctly with projector disabled.

## Background
After disabling projector by default, we need to ensure no existing tests break. Tests that use `await()` to wait for async projection might need updates.

However, based on the project's CQRS architecture:
- **Command handlers** create events and return immediately (fire-and-forget)
- **Controllers** return HTTP responses synchronously
- **ReadModelProjector** updates repositories asynchronously

Most integration tests verify HTTP API behavior (command side) and should NOT depend on projection.

## Tasks

### 1. Search for Tests Using await()

Search for integration tests using Awaitility's `await()`:

```bash
grep -r "await()" src/test/java/org/chucc/vcserver/integration/
```

**Expected findings:**
- GraphEventProjectorIT - already handled in Task 04 ✅
- Possibly other tests that wait for async projection

### 2. Analyze Each Test Using await()

For each test found, determine:

**Question 1: What is the test waiting for?**
- Repository updates (projection) → Needs projector enabled
- HTTP response only → Should NOT use await(), fix the test
- External resource (not projection) → No change needed

**Question 2: What does the test actually verify?**
- API layer (HTTP status, headers, response body) → Projector NOT needed
- Event projection (repository state after events) → Projector needed
- Full CQRS flow (command → event → projection) → Projector needed

### 3. Categorize Findings

Create three categories:

**Category A: API Layer Tests (No Projector Needed)**
- Tests that verify synchronous HTTP responses
- Should NOT use await() - remove it if present
- Examples: GraphStorePutIntegrationTest, GraphStorePostIntegrationTest

**Category B: Projector Tests (Projector Required)**
- Tests that verify async event projection
- Must add `@TestPropertySource(properties = "kafka.listener.enabled=true")`
- Examples: GraphEventProjectorIT (already done)

**Category C: No Changes Needed**
- Tests that don't use await()
- Tests using await() for non-projection reasons
- Examples: Most validation tests, error response tests

## Verification Steps

1. **Run the search**:
   ```bash
   grep -r "await()" src/test/java/org/chucc/vcserver/integration/ | grep -v ".class"
   ```

2. **Document findings** in this file (edit the "Findings" section below)

3. **For each Category A test** (if any found):
   - Read the test to understand what it verifies
   - Confirm it tests HTTP API only
   - Document that await() should be removed

4. **For each Category B test** (if any found):
   - Read the test to understand what it verifies
   - Confirm it tests event projection
   - Document that it needs `@TestPropertySource` added

## Findings

### Tests Using await()

*To be filled in during task execution:*

```
# Example format:
# File: GraphEventProjectorIT.java
# Category: B (Projector Required)
# Status: Already handled in Task 04 ✅
# Lines: Multiple test methods use await()
# Purpose: Verify ReadModelProjector processes events correctly

# Add findings here...
```

### Analysis Summary

*To be filled in during task execution:*

```
# Example format:
# Total tests using await(): X
# Category A (Remove await): Y tests
# Category B (Enable projector): Z tests
# Category C (No change): W tests

# Add summary here...
```

### Required Actions

*To be filled in during task execution:*

```
# Example format:
# 1. Test: SomeIntegrationTest
#    Action: Remove await(), test only verifies HTTP response
#    Estimated effort: 5 minutes
#
# 2. Test: AnotherIntegrationTest
#    Action: Add @TestPropertySource, test verifies projection
#    Estimated effort: 10 minutes

# Add action items here...
```

## Acceptance Criteria

- [ ] Search completed for all integration tests
- [ ] Each test using await() is analyzed and categorized
- [ ] Findings section filled with detailed analysis
- [ ] Action items identified for any tests needing changes
- [ ] Analysis summary provides clear overview

## Dependencies

- Task 04 must be completed (GraphEventProjectorIT already handled)

## Next Task

Task 06: Run Full Test Suite (verify all tests work with projector disabled)

## Estimated Complexity

Low-Medium (30-45 minutes)
- Search: 5 minutes
- Analysis per test: 5-10 minutes each
- Documentation: 10-15 minutes

## Notes

**Based on CLAUDE.md guidance:**

The project follows these testing patterns:

1. **API Layer Integration Tests** (most common):
   - Test HTTP API behavior synchronously
   - Should NOT query repositories after HTTP request
   - Repository updates handled by event projectors (async)
   - Comment: "Repository updates handled by event projectors"

2. **Full System Integration Tests** (less common):
   - Test complete CQRS flow including projection
   - Must use `await()` for async event processing
   - Requires awaitility library

3. **Exception: Synchronous Operations**
   - Some operations update repositories synchronously (not via events)
   - Example: Tag operations in TagOperationsIntegrationTest

This analysis should identify which pattern each test follows.
