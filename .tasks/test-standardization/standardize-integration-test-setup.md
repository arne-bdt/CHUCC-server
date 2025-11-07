# Task: Standardize Integration Test Setup and Variable Naming

**Status:** ✅ COMPLETED (2025-11-07)
**Priority:** Medium
**Actual Effort:** ~2 hours

## Problem

Integration tests across the codebase use inconsistent patterns for dataset naming:

1. **Constant-based naming**: `DATASET_NAME = "test-dataset"` (used in most tests)
2. **Field variable naming**: `String dataset` initialized in `@BeforeEach` (used in RebaseIT, SquashIT)
3. **Inline literals**: `"default"` or `"test"` hardcoded in tests
4. **Dynamic naming**: `"rebase-test-" + System.nanoTime()` for unique names per run

This inconsistency caused issues during URL migration when automated sed commands only matched specific patterns.

## Current Patterns Found

### Pattern 1: Constant-based (Most Common)
```java
private static final String DATASET_NAME = "test-dataset";

@Test
void myTest() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/endpoint",
        // ...
    );
}
```
**Used in:** ResetOperationIT, RevertIT, CherryPickIT, CommitCreationIT, etc.

### Pattern 2: Field Variable (Less Common)
```java
private String dataset;

@BeforeEach
void setUp() {
    dataset = getDatasetName(); // or "rebase-test-" + System.nanoTime()
    // ...
}

@Test
void myTest() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/version/endpoint",
        // ...
    );
}
```
**Used in:** RebaseIT, SquashIT

### Pattern 3: Inline Literals
```java
@Test
void myTest() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/default/version/endpoint",
        // ...
    );
}
```
**Used in:** Various tests using "default" dataset

## Proposed Solution

### Option A: Standardize on Constant Pattern (Recommended)

**Pros:**
- Most common pattern already
- Easy to find with grep/sed
- Clear intent (final constant)
- Works well with static test data

**Cons:**
- Doesn't work for tests needing unique dataset names per run
- May cause cross-test contamination if cleanup fails

**Implementation:**
```java
public class MyIntegrationTest extends IntegrationTestFixture {
    private static final String DATASET_NAME = "my-test-dataset";

    @BeforeEach
    void setUp() {
        // Use DATASET_NAME throughout
        createDatasetViaCommand(DATASET_NAME, ...);
    }

    @Test
    void myTest() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/" + DATASET_NAME + "/version/endpoint",
            HttpMethod.GET, null, String.class
        );
    }
}
```

### Option B: Standardize on Field Variable with Helper

**Pros:**
- Supports unique names per test run
- Better test isolation
- Prevents cross-test contamination

**Cons:**
- Requires refactoring many existing tests
- More complex cleanup logic
- Harder to search/replace in bulk

**Implementation:**
```java
public class MyIntegrationTest extends IntegrationTestFixture {
    private String dataset;

    @BeforeEach
    void setUp() {
        dataset = getUniqueDatasetName("my-test");
        createDatasetViaCommand(dataset, ...);
    }

    @AfterEach
    void cleanup() {
        deleteDataset(dataset);
    }

    @Test
    void myTest() {
        ResponseEntity<String> response = restTemplate.exchange(
            "/" + dataset + "/version/endpoint",
            HttpMethod.GET, null, String.class
        );
    }
}
```

## Recommendation

**Adopt Option A** for most tests, with Option B reserved for tests that genuinely need per-run uniqueness:

1. **Default: Use constant pattern** for 90% of tests
2. **Special cases: Use field variable** only when needed (e.g., RebaseIT with complex setup)
3. **Add helper to IntegrationTestFixture:**
   ```java
   protected String getUniqueDatasetName(String prefix) {
       return prefix + "-" + System.nanoTime();
   }
   ```

## Implementation Steps

1. **Audit all integration tests** to identify current patterns
   ```bash
   grep -r "String.*dataset\|DATASET_NAME" src/test/java/org/chucc/vcserver/integration/
   ```

2. **Create standardization decision matrix:**
   - Which tests can use constants?
   - Which tests need unique names?
   - Document the decision

3. **Refactor in phases:**
   - Phase 1: Simple tests (no complex setup) → Constant pattern
   - Phase 2: Complex tests (multi-dataset, concurrent) → Field variable pattern
   - Phase 3: Add helper methods to IntegrationTestFixture

4. **Update documentation:**
   - Add section to CLAUDE.md about test naming conventions
   - Document when to use each pattern

5. **Add checkstyle/PMD rule** (optional):
   - Warn on inline dataset literals in integration tests
   - Encourage use of constants or fields

## Files to Review

Integration test files with dataset usage:
- `src/test/java/org/chucc/vcserver/integration/*IT.java` (all ~40 files)
- `src/test/java/org/chucc/vcserver/testutil/ITFixture.java` (base class)

## Success Criteria

- [ ] All integration tests use either constant or field variable pattern (no inline literals)
- [ ] Consistent naming convention documented
- [ ] Helper methods added to IntegrationTestFixture
- [ ] Future automated refactoring (sed/awk) works reliably
- [ ] No cross-test contamination issues

## Notes

- This task was created after discovering inconsistency during dataset-in-path URL migration
- The inconsistency caused sed commands to miss some tests (RebaseIT, SquashIT)
- Standardization will make future bulk refactoring more reliable
