# Task: Fix Integration Test Failures

**Status:** Not Started
**Priority:** Critical
**Category:** Bug Fix / Test Repair
**Estimated Time:** 30-60 minutes
**Created:** 2025-11-01
**Source:** Build failure in `mvn clean install`

---

## Overview

Integration tests are failing in two test classes:
1. `HistoryListingIT` - 6 errors
2. `CrossProtocolInteroperabilityIT` - 1 failure

These failures may be pre-existing (not caused by diff endpoint implementation), but they must be fixed to restore build success.

**Build Status:** ❌ FAILING
```
Tests run: 380, Failures: 1, Errors: 6, Skipped: 0
BUILD FAILURE
```

---

## Failed Tests

### 1. HistoryListingIT - 6 Errors

**File:** `src/test/java/org/chucc/vcserver/integration/HistoryListingIT.java`

**Test Class:** History listing endpoint tests

**Errors:** 6 test methods failing with errors (not assertion failures)

**Suspected Issues:**
- Test container issues (Kafka not starting properly)
- Test isolation problems (cross-test contamination)
- Race conditions in async projection
- Missing test data setup

**Error Pattern (from build log):**
```
[ERROR] org.chucc.vcserver.integration.HistoryListingIT.listHistory_withPagination_shouldReturnLinkHeader
```

**Investigation Steps:**

1. **Run test in isolation:**
   ```bash
   mvn test -Dtest=HistoryListingIT
   ```

2. **Check for Kafka/Testcontainers issues:**
   ```bash
   # Look for Docker environment problems
   docker ps
   docker info
   ```

3. **Review test output:**
   ```bash
   # Run without -q to see full error details
   mvn test -Dtest=HistoryListingIT 2>&1 | grep -A 20 "ERROR"
   ```

4. **Check test isolation:**
   - Does test extend `ITFixture`?
   - Is projector properly disabled?
   - Are unique dataset names used?

**Possible Fixes:**

**A) Missing ITFixture base class:**
```java
// If test doesn't extend ITFixture
class HistoryListingIT {  // ❌ No cleanup between tests

// Should be:
class HistoryListingIT extends ITFixture {  // ✅ Automatic cleanup
```

**B) Projector enabled inappropriately:**
```java
// If test enables projector but doesn't use await()
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ❌

// Should either remove property or add await()
```

**C) Dataset name collision:**
```java
// If multiple tests use same dataset name
private static final String DATASET = "test-dataset";  // ❌ Collision

// Should use unique names per test or rely on ITFixture cleanup
```

---

### 2. CrossProtocolInteroperabilityIT - 1 Failure

**File:** `src/test/java/org/chucc/vcserver/integration/CrossProtocolInteroperabilityIT.java`

**Test Class:** Cross-protocol integration tests

**Failures:** 1 test method failing with assertion failure

**Suspected Issues:**
- API contract changed (diff endpoint added)
- Expected response format mismatch
- Endpoint availability check failing

**Error Pattern (from build log):**
```
[ERROR] Failures:
[ERROR]   CrossProtocolInteroperabilityIT.versionHistoryEndpoint_shouldExist:78
```

**Investigation Steps:**

1. **Run test in isolation:**
   ```bash
   mvn test -Dtest=CrossProtocolInteroperabilityIT
   ```

2. **Review failing assertion (line 78):**
   ```bash
   # Read the test file
   sed -n '70,85p' src/test/java/org/chucc/vcserver/integration/CrossProtocolInteroperabilityIT.java
   ```

3. **Check what the test expects:**
   - Endpoint list?
   - Specific response format?
   - Presence/absence of diff endpoint?

**Possible Fixes:**

**A) Endpoint list includes diff now:**
```java
// If test checks for specific endpoint list
List<String> expectedEndpoints = List.of(
    "/version/history",
    // Missing: "/version/diff"
);

// Should include newly implemented endpoint:
List<String> expectedEndpoints = List.of(
    "/version/history",
    "/version/diff",  // ✅ Add this
    "/version/blame"
);
```

**B) Status code changed:**
```java
// If test expects 501 for diff
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);  // ❌

// Should expect 200 or appropriate status
assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);  // ✅
```

---

## Implementation Steps

### Step 1: Diagnose HistoryListingIT Errors (15 minutes)

1. Run test without `-q` flag:
   ```bash
   mvn test -Dtest=HistoryListingIT 2>&1 | tee history-test-output.txt
   ```

2. Analyze error messages:
   - Docker/Testcontainers errors?
   - NullPointerException?
   - Timeout errors?
   - Projection errors?

3. Check test structure:
   ```bash
   grep -n "class HistoryListingIT\|extends\|@BeforeEach\|@BeforeAll" \
     src/test/java/org/chucc/vcserver/integration/HistoryListingIT.java
   ```

4. Identify root cause and document findings

### Step 2: Fix HistoryListingIT Issues (15-30 minutes)

Based on diagnosis, apply appropriate fix:

**Common fixes:**
- Add `extends ITFixture`
- Remove inappropriate projector enablement
- Fix dataset name collisions
- Add proper cleanup in `@BeforeEach`
- Fix async timing issues with `await()`

### Step 3: Diagnose CrossProtocolInteroperabilityIT Failure (5 minutes)

1. Run test:
   ```bash
   mvn test -Dtest=CrossProtocolInteroperabilityIT::versionHistoryEndpoint_shouldExist
   ```

2. Read the failing test (around line 78):
   ```bash
   sed -n '60,90p' src/test/java/org/chucc/vcserver/integration/CrossProtocolInteroperabilityIT.java
   ```

3. Identify what changed (likely diff endpoint now returns 200 instead of 501)

### Step 4: Fix CrossProtocolInteroperabilityIT (5-10 minutes)

Update test to reflect current API state:
- If checking endpoint list, add `/version/diff`
- If checking status code, expect 200 instead of 501
- If checking response content, update expectations

### Step 5: Verify All Tests Pass (5 minutes)

```bash
# Run both fixed test classes
mvn test -Dtest=HistoryListingIT,CrossProtocolInteroperabilityIT

# If passing, run full integration test suite
mvn verify -DskipUTs=true

# Should see:
# Tests run: 380, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

## Success Criteria

- ✅ All 6 HistoryListingIT errors resolved
- ✅ CrossProtocolInteroperabilityIT failure resolved
- ✅ Full integration test suite passes (380 tests)
- ✅ `mvn clean install` succeeds
- ✅ No new test failures introduced

---

## Testing Strategy

**Approach:**
1. Fix tests one at a time
2. Run individually after each fix
3. Run both together when individually passing
4. Run full suite when both passing

**Verification:**
```bash
# Quick verification
mvn verify -DskipUTs=true 2>&1 | tail -30

# Should show:
# Tests run: 380, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

## Notes

### HistoryListingIT Considerations

- **Test Isolation:** May need to use `ITFixture` base class for automatic cleanup
- **Async Timing:** If projector enabled, must use `await()` pattern
- **Kafka:** Testcontainers must start successfully (check Docker)
- **Dataset Cleanup:** `@BeforeEach` should clean repositories

### CrossProtocolInteroperabilityIT Considerations

- **API Changes:** Diff endpoint changed from 501 → 200
- **Endpoint Discovery:** May need to update expected endpoint list
- **Feature Flags:** Test may need to account for configurable endpoints

---

## Common Patterns from Similar Fixes

**Pattern 1: Missing ITFixture**
```java
// Before
class MyIT {
  @BeforeEach
  void setUp() {
    // Manual cleanup
    commitRepository.deleteAll();
  }
}

// After
class MyIT extends ITFixture {
  // Automatic cleanup from base class
}
```

**Pattern 2: Projector Misuse**
```java
// Before
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyIT extends ITFixture {
  @Test
  void test() {
    // Queries repository without await()  ❌
    var result = repository.findById(id);
  }
}

// After (Option A: Remove projector)
class MyIT extends ITFixture {
  @Test
  void test() {
    // Test API only, don't query repository
    ResponseEntity<String> response = restTemplate.get(...);
    assertThat(response.getStatusCode()).isEqualTo(200);
  }
}

// After (Option B: Use await())
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyIT extends ITFixture {
  @Test
  void test() {
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        var result = repository.findById(id);
        assertThat(result).isPresent();
      });
  }
}
```

---

## References

- Test Patterns: `.claude/CLAUDE.md#testing-guidelines`
- CQRS Testing: `docs/architecture/cqrs-event-sourcing.md#testing-implications`
- ITFixture Base Class: `src/test/java/org/chucc/vcserver/testutil/ITFixture.java`
- Build Output: `mvn clean install` (2025-11-01)

---

## Commit Message Template

```
fix: resolve integration test failures in HistoryListingIT and CrossProtocolInteroperabilityIT

Fixes 7 failing integration tests identified in build:
- HistoryListingIT: 6 errors (root cause: [INSERT CAUSE])
- CrossProtocolInteroperabilityIT: 1 failure (diff endpoint now returns 200)

Changes:
- [Describe specific fixes applied]
- [Test pattern improvements]
- [Any cleanup or refactoring]

All 380 integration tests now passing.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```
