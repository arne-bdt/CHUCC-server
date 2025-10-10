# ~~Investigate asOf Parameter Bug~~ - RESOLVED

## Resolution Summary

**Root Cause:** URL encoding bug in test code, NOT in asOf parameter handling.

**The Issue:**
- Tests used `.toUriString()` → `getForEntity(String, ...)` causing **double URL encoding**
- Double encoding corrupted SPARQL queries: `"SELECT * WHERE..."` → malformed query
- Error: `"MALFORMED_QUERY"` not invalid asOf parameter

**The Fix:**
- Changed all 3 tests to use `.build().toUri()` → `getForEntity(URI, ...)`
- Prevents double encoding by passing URI object instead of String
- Pattern matches working tests in `SelectorValidationIntegrationTest`

**Result:** All 6 tests in `TimeTravelQueryIntegrationTest` now pass (0 skipped)

---

## Original Issue Summary (INCORRECT DIAGNOSIS)

The SPARQL query endpoint returns **400 BAD_REQUEST** instead of **200 OK** when using valid `asOf` timestamps, even though:
- The selector combination is explicitly allowed per SPARQL 1.2 Protocol
- Timestamp format is valid RFC 3339
- The `SelectorValidationIntegrationTest.testBranchAndAsOfCombination_isAllowed()` test explicitly verifies this combination should not return 400

## Affected Tests

Three tests in `TimeTravelQueryIntegrationTest` are currently disabled:

1. **`queryWithAsOf_shouldAcceptParameter()`** - asOf + branch combination
2. **`queryWithAsOfOnly_shouldAcceptParameter()`** - asOf without branch (uses default)
3. **`queryWithAsOfAfterAllCommits_shouldAcceptParameter()`** - asOf with future timestamp

## Tested Scenarios

All scenarios consistently return **400 BAD_REQUEST**:

| Scenario | URL | Expected | Actual |
|----------|-----|----------|--------|
| asOf + branch | `/sparql?query=...&branch=main&asOf=2026-01-02T00:00:00Z` | 200 OK | 400 BAD_REQUEST |
| asOf only | `/sparql?query=...&asOf=2026-01-02T00:00:00Z` | 200 OK | 400 BAD_REQUEST |
| asOf (far future) | `/sparql?query=...&branch=main&asOf=2099-12-31T23:59:59Z` | 200 OK | 400 BAD_REQUEST |

**Note:** Tested with multiple timestamp values (2026, 2030, 2099) - all fail the same way.

## Expected Behavior

Per SPARQL 1.2 Protocol and existing validation tests:
- `asOf` + `branch` combination is **explicitly allowed** (not mutually exclusive)
- `asOf` without `branch` should use **default branch "main"** for time-travel
- Valid RFC 3339 timestamps should be **accepted**
- Future timestamps (after all commits) should **return latest commit state**

## Investigation Tasks

### 1. Trace Request Flow

- [ ] Add logging to `SparqlController.executeSparqlGet()` to see exact parameters received
- [ ] Check if `asOf` parameter is being parsed correctly
- [ ] Verify `SelectorResolutionService.resolve()` is called with correct arguments

### 2. Check Validation Logic

- [ ] Review `SelectorValidator` (if exists) for asOf handling
- [ ] Check for conflicts between validation rules
- [ ] Verify `SelectorValidationIntegrationTest.testBranchAndAsOfCombination_isAllowed()` actually passes

### 3. Investigate TimestampResolutionService

- [ ] Check `TimestampResolutionService.resolveAsOf()` error handling
- [ ] Verify it handles future timestamps correctly (should return latest commit)
- [ ] Test with actual commit timestamps vs. arbitrary future dates

### 4. Compare Working vs. Broken Tests

**Working:** `SelectorValidationIntegrationTest.testBranchAndAsOfCombination_isAllowed()`
- Uses timestamp `2024-01-01T00:00:00Z`
- Expects `!= 400`
- Does this test actually pass with 200, or does it pass with 404?

**Broken:** `TimeTravelQueryIntegrationTest` tests
- Use timestamps `2026-*` (future relative to test execution)
- Expect `200`
- Get `400`

**Hypothesis:** The difference might be in:
- Timestamp value (past vs. future)
- Whether commits exist at those timestamps
- Error handling in timestamp resolution

### 5. Root Cause Analysis

Possible causes:
1. **Validation bug:** Controller incorrectly rejects valid asOf parameters
2. **Timestamp parsing bug:** RFC 3339 parsing fails for certain dates
3. **Resolution bug:** `TimestampResolutionService` throws exception that becomes 400
4. **Missing implementation:** asOf handling not fully implemented in controller

### 6. Fix and Re-enable Tests

Once root cause identified:
- [ ] Fix the bug in appropriate location (likely `SparqlController` or validation layer)
- [ ] Add unit tests for the fix
- [ ] Re-enable the 3 disabled tests in `TimeTravelQueryIntegrationTest`
- [ ] Verify all tests pass

## Current Workaround

Tests are disabled with `@Disabled` annotations and clear documentation:
```java
@Test
@org.junit.jupiter.api.Disabled("asOf + branch returns 400, needs investigation")
void queryWithAsOf_shouldAcceptParameter() { ... }
```

## Files to Review

- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
- `src/main/java/org/chucc/vcserver/service/SelectorResolutionService.java`
- `src/main/java/org/chucc/vcserver/service/TimestampResolutionService.java`
- `src/main/java/org/chucc/vcserver/util/SelectorValidator.java` (if exists)
- `src/test/java/org/chucc/vcserver/integration/SelectorValidationIntegrationTest.java`

## References

- SPARQL 1.2 Protocol §4 - Version Control Selectors
- `IntegrationTestFixture.java` - Creates initial commit with `Instant.now()`
- `SelectorValidationIntegrationTest.testBranchAndAsOfCombination_isAllowed()` - Passing test with asOf

## Success Criteria

- [ ] Identify root cause of 400 responses
- [ ] Implement fix with unit tests
- [ ] Re-enable 3 disabled tests
- [ ] All 859+ tests passing
- [ ] Document any spec deviations or limitations
