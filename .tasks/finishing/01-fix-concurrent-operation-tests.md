# Task 01: Fix Concurrent Operation Tests

## Objective

Re-enable 4 disabled tests in ConcurrentGraphOperationsIntegrationTest by adding proper projector configuration.

## Background

The tests in `ConcurrentGraphOperationsIntegrationTest` are disabled with this comment:
> "DISABLED: These tests require full CQRS async processing to update branch HEAD between operations. The current approach times out waiting for event projectors."

The tests are actually correct, but they need the projector enabled to work. With the test isolation infrastructure completed (Tasks 01-11), we now know the fix is simple: add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`.

## Current Status

**File**: `src/test/java/org/chucc/vcserver/integration/ConcurrentGraphOperationsIntegrationTest.java`

**Tests Disabled** (4 tests):
1. `putGraph_shouldDetectConcurrentWrites()`
2. `postGraph_shouldDetectConcurrentWrites()`
3. `patchGraph_shouldDetectConcurrentWrites()`
4. `deleteGraph_shouldDetectConcurrentWrites()`

**Reason for Failure**:
- Tests use `await()` to wait for branch HEAD updates
- Branch HEAD updates happen via ReadModelProjector (async event processing)
- Projector is disabled by default in integration tests
- Tests timeout waiting for events that never get processed

**Why Tests Are Valuable**:
- Verify optimistic concurrency control (If-Match headers)
- Test conflict detection across multiple operations
- Ensure ETag-based preconditions work correctly
- Validate RFC 7807 problem+json responses for conflicts

## Implementation

### Step 1: Update Test Class Annotation

**File**: `src/test/java/org/chucc/vcserver/integration/ConcurrentGraphOperationsIntegrationTest.java`

**Change**:
```java
// REMOVE this annotation:
@Disabled("Requires full async CQRS setup - conflict detection tested elsewhere")

// ADD this annotation:
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

**Full class annotation should be**:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
@Testcontainers
class ConcurrentGraphOperationsIntegrationTest extends IntegrationTestFixture {
```

### Step 2: Update Class Javadoc

Replace the current Javadoc with:
```java
/**
 * Integration tests for concurrent graph operations.
 * Verifies conflict detection when multiple clients attempt to modify the same graph
 * using If-Match headers for optimistic concurrency control.
 *
 * <p><strong>Note:</strong> This test explicitly enables the ReadModelProjector
 * via {@code @TestPropertySource(properties = "projector.kafka-listener.enabled=true")}
 * because tests use {@code await()} to wait for branch HEAD updates from async event
 * processing. This is required to test full concurrent operation scenarios.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>PUT operations detect concurrent writes (412 Precondition Failed)
 *   <li>POST operations detect concurrent writes (409 Conflict)
 *   <li>PATCH operations detect concurrent writes (409 Conflict)
 *   <li>DELETE operations detect concurrent writes (409 Conflict)
 *   <li>RFC 7807 problem+json responses for conflicts
 * </ul>
 */
```

### Step 3: Verify Tests Pass

Run the tests:
```bash
mvn test -Dtest=ConcurrentGraphOperationsIntegrationTest
```

**Expected Output**:
```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
```

### Step 4: Run Static Analysis

Verify zero violations:
```bash
mvn checkstyle:check spotbugs:check
```

### Step 5: Update Test Count Documentation

The total test count will increase from 819 to 823.

Update any documentation that references test counts (if needed).

## Verification Steps

1. **Compile and test**:
   ```bash
   mvn clean test -Dtest=ConcurrentGraphOperationsIntegrationTest
   ```
   - All 4 tests should pass
   - No timeouts
   - Execution time ~80 seconds (4 tests × ~20s each)

2. **Verify conflict detection**:
   - Check logs show "Processing CommitCreatedEvent" messages
   - Verify 409 Conflict and 412 Precondition Failed responses
   - Verify application/problem+json content types

3. **Run full test suite**:
   ```bash
   mvn -q clean install
   ```
   - Test count should be 823 (819 + 4)
   - Zero failures
   - Zero violations

## Acceptance Criteria

- [x] `@Disabled` annotation removed
- [x] `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")` added
- [x] Class Javadoc updated with explanation
- [x] All 4 tests pass
- [x] No test timeouts
- [x] Zero Checkstyle violations
- [x] Zero SpotBugs warnings
- [x] Test execution completes in < 2 minutes
- [x] Full test suite passes (823 tests)

## Dependencies

- Test isolation infrastructure (Tasks 01-11) - Complete ✅

## Estimated Complexity

**Low** (30-60 minutes)
- Update annotations: 5 minutes
- Update Javadoc: 5 minutes
- Run tests: 2 minutes
- Fix any issues: 10-20 minutes (if needed)
- Run full suite: 5 minutes
- Documentation: 10 minutes

## Notes

**Why This Is a Quick Win**:
- Tests are already well-written
- No logic changes needed
- Just needs proper configuration
- Validates test isolation pattern

**Test Pattern**:
These tests follow the **Full System Integration Test** pattern:
1. Make HTTP request (creates event)
2. Wait for async event projection with `await()`
3. Make second HTTP request with old ETag
4. Verify conflict detected (409/412)

This is different from most tests (API Layer) that don't enable projector.

**Reference**:
- See CLAUDE.md "Integration Testing with Kafka Event Projection" section
- See "Testing Decision Table" for when to enable projector
- See GraphEventProjectorIT for similar projector-enabled test pattern

## Success Criteria

When this task is complete:
- 4 previously disabled tests now pass
- Test suite count increases to 823
- Concurrent operation testing coverage complete
- Test isolation pattern validated
