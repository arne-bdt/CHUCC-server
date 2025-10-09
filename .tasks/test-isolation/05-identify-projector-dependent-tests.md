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

**Search completed: 2025-10-09**

Command used:
```bash
grep -r "await()" src/test/java/org/chucc/vcserver/integration/
```

**Results: 6 files found**

#### 1. GraphEventProjectorIT.java
- **Category**: B (Projector Required)
- **Status**: ✅ Already correctly configured in Task 04
- **Lines**: 6 uses of await() across multiple test methods
- **Purpose**: Verify ReadModelProjector processes events correctly
- **Configuration**: Has `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")` on line 52
- **Action**: None needed - already correct

#### 2. GraphStorePutIntegrationTest.java
- **Category**: C (No Change Needed)
- **Status**: ✅ No action required
- **Lines**: await() usage on lines 376, 402 (both in commented-out code)
- **Purpose**: Tests HTTP API responses for PUT operations
- **Configuration**: No @TestPropertySource (projector disabled)
- **Action**: None needed - await() is not active code

#### 3. GraphStorePostIntegrationTest.java
- **Category**: C (No Change Needed)
- **Status**: ✅ No action required
- **Lines**: await() usage on lines 115, 394, 432 (all in commented-out code)
- **Purpose**: Tests HTTP API responses for POST operations
- **Configuration**: No @TestPropertySource (projector disabled)
- **Action**: None needed - await() is not active code

#### 4. GraphStorePatchIntegrationTest.java
- **Category**: C (No Change Needed)
- **Status**: ✅ No action required
- **Lines**: await() usage on lines 347, 354 (in commented-out TODO examples)
- **Purpose**: Tests HTTP API responses for PATCH operations
- **Configuration**: No @TestPropertySource (projector disabled)
- **Action**: None needed - await() is not active code

#### 5. GraphStoreDeleteIntegrationTest.java
- **Category**: C (No Change Needed)
- **Status**: ✅ No action required
- **Lines**: await() usage on lines 47, 115, 265, 288, 314, 331 (all in commented-out code)
- **Purpose**: Tests HTTP API responses for DELETE operations
- **Configuration**: No @TestPropertySource (projector disabled)
- **Action**: None needed - await() is not active code

#### 6. ConcurrentGraphOperationsIntegrationTest.java
- **Category**: B (Projector Required - Currently Disabled)
- **Status**: ⚠️ Test is @Disabled (line 34)
- **Lines**: 8 uses of await() for verifying async projection in conflict detection
- **Purpose**: Tests concurrent write conflict detection via async projection
- **Configuration**: No @TestPropertySource (would need it if re-enabled)
- **Action**: If re-enabled, add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`

### Analysis Summary

**Completed: 2025-10-09**

- **Total files using await()**: 6 files
- **Category A (Remove await)**: 0 tests - None found
- **Category B (Enable projector)**: 2 tests
  - GraphEventProjectorIT.java ✅ (already correctly configured)
  - ConcurrentGraphOperationsIntegrationTest.java ⚠️ (disabled, would need @TestPropertySource if re-enabled)
- **Category C (No change)**: 4 tests
  - All GraphStore*IntegrationTest files have await() only in commented-out code

**Key Finding**: Only 1 active test uses await() (GraphEventProjectorIT), and it's already correctly configured with `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`.

**Impact Assessment**:
- ✅ Zero API layer tests incorrectly depend on async projection
- ✅ All active tests are correctly configured
- ✅ Commented-out code serves as documentation/examples (no action needed)
- ✅ Test isolation implementation is working as designed

### Required Actions

**None required for currently active tests.**

All integration tests are correctly configured:
- API layer tests (GraphStorePutIntegrationTest, GraphStorePostIntegrationTest, GraphStorePatchIntegrationTest, GraphStoreDeleteIntegrationTest) do not actively use await() and have projector disabled
- Projector test (GraphEventProjectorIT) correctly enables projector with @TestPropertySource
- Commented-out code does not affect test execution

**Future action (if ConcurrentGraphOperationsIntegrationTest is re-enabled)**:
1. Test: ConcurrentGraphOperationsIntegrationTest
   Action: Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
   Estimated effort: 2 minutes (add annotation, verify test passes)
   Justification: Test verifies async conflict detection via ReadModelProjector

## Acceptance Criteria

- [x] Search completed for all integration tests
- [x] Each test using await() is analyzed and categorized
- [x] Findings section filled with detailed analysis
- [x] Action items identified for any tests needing changes
- [x] Analysis summary provides clear overview

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
