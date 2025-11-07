# Integration Test Dataset Naming - Decision Matrix

**Date:** 2025-11-07
**Task:** Standardize integration test setup and dataset naming patterns

## Audit Results

### Summary Statistics
- **Total Integration Tests:** 48
- **Already Using Constant Pattern:** 16 tests (33%)
- **Already Using Field Variable Pattern:** 2 tests (4%)
- **Need Refactoring:** 3 tests (6%)
- **Special Cases:** 27 tests (57% - use ITFixture default or have no dataset variable)

---

## Pattern Classification

### ✅ Pattern 1: Constant-Based (Recommended Default) - 16 Tests

**Tests already compliant:**
1. AsOfSelectorIT - `DATASET_NAME = "test-dataset"`
2. CherryPickIT - `DATASET_NAME = "test-dataset"`
3. CommitCreationIT - `DATASET_NAME = "test-dataset"`
4. CommitMetadataIT - `DATASET_NAME = "test-dataset"`
5. ETagIT - `DATASET_NAME = "test-dataset"`
6. GraphStoreGetIT - `DATASET_NAME = "default"`
7. GraphStoreHeadIT - `DATASET_NAME = "default"`
8. GraphStoreTimeTravelIT - `DATASET_NAME = "default"`
9. HistoryListingIT - `DATASET_NAME = "test-dataset"`
10. MergeOperationsIT - `DATASET_NAME = "test-dataset"`
11. NoOpPatchIT - `DATASET_NAME = "test-dataset"`
12. RefsEndpointIT - `DATASET_NAME = "test-dataset"`
13. ResetOperationIT - `DATASET_NAME = "test-dataset"`
14. RevertIT - `DATASET_NAME = "test-dataset"`
15. SparqlUpdateNoOpIT - `DATASET_NAME = "default"`
16. TagOperationsIT - `DATASET_NAME = "test-dataset"`

**Pattern:**
```java
public class MyIT extends ITFixture {
  private static final String DATASET_NAME = "test-dataset";

  @Test
  void myTest() {
    // Use DATASET_NAME throughout
    restTemplate.exchange("/" + DATASET_NAME + "/version/...", ...);
  }
}
```

---

### ✅ Pattern 2: Field Variable (For Unique Names) - 2 Tests

**Tests already compliant:**
1. RebaseIT - Overrides `getDatasetName()` with `"rebase-test-" + System.nanoTime()`
2. SquashIT - Overrides `getDatasetName()` with `"squash-test-" + System.nanoTime()`

**Rationale:** These tests need unique dataset names per run because:
- Complex commit graph setup
- Override `shouldCreateInitialSetup()` to return false
- Custom multi-commit setup that cannot be created via standard command handlers

**Pattern:**
```java
public class RebaseIT extends ITFixture {
  private String dataset;

  @Override
  protected String getDatasetName() {
    return "rebase-test-" + System.nanoTime();
  }

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false; // Custom setup needed
  }

  @BeforeEach
  void setUp() {
    dataset = getDatasetName();
    // Custom commit graph setup...
  }
}
```

---

## Tests Needing Refactoring

### 🔧 Refactor to Constant Pattern

#### 1. BlameEndpointIT
**Current:** Each test method declares `String dataset = "test-blame";` (and variations)
**Issue:** Inline literals in each test method (11 occurrences)
**Action:** Add `private static final String DATASET_NAME` and replace all inline literals
**Complexity:** Medium (11 test methods to update)

#### 2. DiffEndpointIT
**Current:** Each test method declares `String dataset = "test-diff";` (and variations)
**Issue:** Inline literals in each test method (7 occurrences)
**Action:** Add `private static final String DATASET_NAME` and replace all inline literals
**Complexity:** Medium (7 test methods to update)

### 🔧 Standardize Dynamic Naming

#### 3. DatasetDeletionIT
**Current:** `String dataset = "test-dataset-" + System.currentTimeMillis();` in test method
**Issue:** Not using field variable pattern
**Action:** Move to field variable with `getDatasetName()` override (same pattern as RebaseIT)
**Rationale:** Test specifically tests dataset deletion - unique name prevents contamination
**Complexity:** Low (1 test method)

---

## Special Cases (No Action Needed)

The following tests either:
- Use ITFixture's `getDatasetName()` (returns "default")
- Don't have explicit dataset variables (rely on ITFixture setup)
- Are already using recommended patterns

**List of special case tests:**
- BatchGraphsIT
- BatchOperationsIT
- BatchOperationsProjectorIT
- ConcurrentGraphOperationsIT
- CrossProtocolInteroperabilityIT
- DatasetCreationIT
- ErrorResponseIT
- EventualConsistencyIT
- EventualConsistencyProjectorIT
- GraphEventProjectorIT
- GraphStoreDeleteIT
- GraphStoreErrorHandlingIT
- GraphStorePatchIT
- GraphStorePostIT
- GraphStoreProtocolDiscoveryIT
- GraphStorePutIT
- MaterializedGraphProjectionIT
- MaterializedViewEvictionIT (uses `getDatasetName()`)
- MaterializedViewRebuildIT
- SelectorValidationIT
- SparqlQueryIT
- SparqlQueryPostIT
- SparqlUpdateIT
- SparqlUpdateNoOpIT
- TimeTravelQueryIT
- VersionControlProjectorIT
- AdvancedOperationsProjectorIT

---

## Decision Rules

### When to use Constant Pattern (90% of tests):
- ✅ Test doesn't need unique dataset name per run
- ✅ Uses standard ITFixture setup
- ✅ Simple commit graph (1-2 commits)
- ✅ No cross-test contamination risk

### When to use Field Variable Pattern (10% of tests):
- ✅ Needs unique dataset name per run
- ✅ Complex custom setup (overrides `shouldCreateInitialSetup()`)
- ✅ Multi-commit graph that cannot be created via standard API
- ✅ Tests dataset deletion or other destructive operations

---

## Implementation Priority

1. **High Priority:** BlameEndpointIT, DiffEndpointIT (most impact for bulk refactoring)
2. **Medium Priority:** DatasetDeletionIT (standardize field variable pattern)
3. **Low Priority:** Documentation updates

---

## Success Metrics

- ✅ Zero inline dataset literals in integration tests
- ✅ All tests use either constant or field variable pattern
- ✅ Future sed/awk commands work reliably
- ✅ Clear documentation in CLAUDE.md
- ✅ All tests pass after refactoring
