# Task: Diff Endpoint Optional Enhancements

**Status:** ✅ Completed
**Priority:** Low (Optional)
**Category:** Enhancement / Code Quality
**Estimated Time:** 25-30 minutes
**Actual Time:** ~25 minutes
**Created:** 2025-11-01
**Completed:** 2025-11-01
**Source:** code-reviewer agent suggestions on commit 556141e
**Completion Commit:** 39bcbe3 (test: add optional enhancements for diff endpoint)

---

## Overview

Optional improvements to the diff endpoint implementation based on code-reviewer agent feedback. These enhancements improve code quality, testability, and maintainability but are not required for functionality.

**Related Commit:** 556141e (feat: implement diff endpoint)

---

## Enhancements

### 1. Add Unit Tests for DiffService

**File:** `src/test/java/org/chucc/vcserver/service/DiffServiceTest.java` (NEW)

**Estimated Time:** 15-20 minutes

**Current State:**
- Only integration tests exist (`DiffEndpointIT`)
- Service logic tested end-to-end with Spring Boot + Testcontainers
- No isolated unit tests for service methods

**Benefits of Unit Tests:**
- Faster feedback (no Spring context, Kafka, Docker)
- Easier debugging of service logic in isolation
- Better coverage of edge cases
- Clear documentation of service contract

**Implementation:**

```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DiffService.
 * Tests service logic in isolation using mocks.
 */
@ExtendWith(MockitoExtension.class)
class DiffServiceTest {

  @Mock
  private DatasetService datasetService;

  @Mock
  private CommitRepository commitRepository;

  @InjectMocks
  private DiffService diffService;

  @Test
  void diffCommits_whenFromCommitNotFound_shouldThrowCommitNotFoundException() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(false);

    // When/Then
    assertThatThrownBy(() -> diffService.diffCommits(dataset, fromCommit, toCommit))
        .isInstanceOf(CommitNotFoundException.class)
        .hasMessageContaining("Commit not found: " + fromCommit.value());

    // Verify early exit (didn't check toCommit or materialize)
    verify(commitRepository, never()).exists(dataset, toCommit);
    verify(datasetService, never()).materializeCommit(anyString(), any(CommitId.class));
  }

  @Test
  void diffCommits_whenToCommitNotFound_shouldThrowCommitNotFoundException() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(false);

    // When/Then
    assertThatThrownBy(() -> diffService.diffCommits(dataset, fromCommit, toCommit))
        .isInstanceOf(CommitNotFoundException.class)
        .hasMessageContaining("Commit not found: " + toCommit.value());

    // Verify didn't try to materialize
    verify(datasetService, never()).materializeCommit(anyString(), any(CommitId.class));
  }

  @Test
  void diffCommits_whenBothCommitsExist_shouldCallDatasetServiceTwice() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(true);

    DatasetGraph emptyDataset = DatasetGraphFactory.createTxnMem();
    when(datasetService.materializeCommit(dataset, fromCommit)).thenReturn(emptyDataset);
    when(datasetService.materializeCommit(dataset, toCommit)).thenReturn(emptyDataset);

    // When
    String result = diffService.diffCommits(dataset, fromCommit, toCommit);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).contains("TX"); // RDF Patch header

    verify(datasetService).materializeCommit(dataset, fromCommit);
    verify(datasetService).materializeCommit(dataset, toCommit);
  }

  @Test
  void diffCommits_whenSameCommit_shouldReturnEmptyPatch() {
    // Given
    String dataset = "test";
    CommitId commit = CommitId.generate();

    when(commitRepository.exists(dataset, commit)).thenReturn(true);

    DatasetGraph emptyDataset = DatasetGraphFactory.createTxnMem();
    when(datasetService.materializeCommit(dataset, commit)).thenReturn(emptyDataset);

    // When
    String result = diffService.diffCommits(dataset, commit, commit);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).contains("TX"); // Has transaction markers
    assertThat(result).doesNotContain("A "); // No additions
    assertThat(result).doesNotContain("D "); // No deletions
  }

  @Test
  void diffCommits_whenCommitsHaveDifferences_shouldIncludeAdditionsAndDeletions() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(true);

    // Create datasets with differences
    DatasetGraph fromDataset = DatasetGraphFactory.createTxnMem();
    DatasetGraph toDataset = DatasetGraphFactory.createTxnMem();

    // Add a triple to fromDataset (will be deleted in diff)
    fromDataset.add(
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/default"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/s1"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/p1"),
        org.apache.jena.graph.NodeFactory.createLiteral("old")
    );

    // Add a different triple to toDataset (will be addition in diff)
    toDataset.add(
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/default"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/s2"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/p2"),
        org.apache.jena.graph.NodeFactory.createLiteral("new")
    );

    when(datasetService.materializeCommit(dataset, fromCommit)).thenReturn(fromDataset);
    when(datasetService.materializeCommit(dataset, toCommit)).thenReturn(toDataset);

    // When
    String result = diffService.diffCommits(dataset, fromCommit, toCommit);

    // Then
    assertThat(result).contains("D "); // Has deletions
    assertThat(result).contains("A "); // Has additions
  }
}
```

**Test Coverage:**
- ✅ From commit not found
- ✅ To commit not found
- ✅ Both commits exist (materialization called)
- ✅ Same commit (empty patch)
- ✅ Different commits (additions and deletions)

---

### 2. Extract Media Type Constant

**Files:**
- `src/main/java/org/chucc/vcserver/controller/HistoryController.java` (MODIFY)

**Estimated Time:** 2 minutes

**Current State:**
```java
@GetMapping(value = "/diff", produces = "text/rdf-patch")  // Line 214
.contentType(MediaType.parseMediaType("text/rdf-patch"))  // Line 274
```

**Issue:** String literal duplicated; typo risk.

**Improvement:**
```java
// Option A: Controller-level constant
public class HistoryController {
  private static final String MEDIA_TYPE_RDF_PATCH = "text/rdf-patch";

  @GetMapping(value = "/diff", produces = MEDIA_TYPE_RDF_PATCH)
  // ...
  .contentType(MediaType.parseMediaType(MEDIA_TYPE_RDF_PATCH))
}

// Option B: Shared constants class (if multiple controllers use it)
public final class MediaTypes {
  public static final String RDF_PATCH = "text/rdf-patch";

  private MediaTypes() {} // Prevent instantiation
}

// Usage:
@GetMapping(value = "/diff", produces = MediaTypes.RDF_PATCH)
.contentType(MediaType.parseMediaType(MediaTypes.RDF_PATCH))
```

**Recommendation:** Option A (controller-level) unless other controllers also use RDF Patch media type.

**Benefits:**
- Single source of truth
- No typo risk
- Easier to change if media type evolves

---

### 3. Add Diff Symmetry Test

**File:** `src/test/java/org/chucc/vcserver/integration/DiffEndpointIT.java` (MODIFY)

**Estimated Time:** 5 minutes

**Current State:** No test verifies that `diff(A, B)` is the inverse of `diff(B, A)`.

**Test to Add:**
```java
@Test
void diffCommits_reverseDirection_shouldInvertPatch() {
  // Given: Two commits with different data
  String dataset = "test-diff-symmetry";

  // Commit 1: Contains triple s1/p1/v1
  CommitId commit1 = createCommit(
      dataset,
      List.of(),
      "Alice <alice@example.org>",
      "First commit",
      createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
  );

  // Commit 2: Contains triple s2/p2/v2 (different from commit1)
  CommitId commit2 = createCommit(
      dataset,
      List.of(commit1),
      "Alice <alice@example.org>",
      "Second commit",
      createSimplePatch("http://ex.org/s2", "http://ex.org/p2", "value2")
  );

  // When: Request diff in both directions
  String forwardUrl = String.format(
      "/version/diff?dataset=%s&from=%s&to=%s",
      dataset, commit1.value(), commit2.value()
  );
  String reverseUrl = String.format(
      "/version/diff?dataset=%s&from=%s&to=%s",
      dataset, commit2.value(), commit1.value()
  );

  ResponseEntity<String> forwardResponse = restTemplate.exchange(
      forwardUrl, HttpMethod.GET, null, String.class);
  ResponseEntity<String> reverseResponse = restTemplate.exchange(
      reverseUrl, HttpMethod.GET, null, String.class);

  String forwardPatch = forwardResponse.getBody();
  String reversePatch = reverseResponse.getBody();

  // Then: Forward additions should be reverse deletions
  assertThat(forwardPatch).contains("A <http://ex.org/s2>");  // s2 added in forward
  assertThat(reversePatch).contains("D <http://ex.org/s2>");  // s2 deleted in reverse

  // And: Forward deletions should be reverse additions
  assertThat(forwardPatch).contains("D <http://ex.org/s1>");  // s1 deleted in forward
  assertThat(reversePatch).contains("A <http://ex.org/s1>");  // s1 added in reverse

  // And: Both should be valid patches
  assertThat(forwardResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(reverseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

**Benefits:**
- Verifies diff semantics correctness
- Catches potential bugs in `RdfPatchUtil.diff()`
- Documents expected behavior
- Increases confidence in diff accuracy

---

### 4. Parameterize Missing Parameter Tests (Optional)

**File:** `src/test/java/org/chucc/vcserver/integration/DiffEndpointIT.java` (MODIFY)

**Estimated Time:** 5 minutes (OPTIONAL - current approach is fine)

**Current State:** Three separate tests for missing parameters.

**Alternative Approach (if desired):**
```java
@ParameterizedTest(name = "missing {0} parameter should return 400")
@MethodSource("missingParameterCases")
void diffCommits_missingRequiredParameter_shouldReturn400(
    String parameterName, String url) {
  // When
  ResponseEntity<String> response = restTemplate.exchange(
      url, HttpMethod.GET, null, String.class);

  // Then
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}

static Stream<Arguments> missingParameterCases() {
  CommitId dummyId = CommitId.generate();
  return Stream.of(
      Arguments.of("dataset",
          String.format("/version/diff?from=%s&to=%s", dummyId.value(), dummyId.value())),
      Arguments.of("from",
          String.format("/version/diff?dataset=test&to=%s", dummyId.value())),
      Arguments.of("to",
          String.format("/version/diff?dataset=test&from=%s", dummyId.value()))
  );
}
```

**Recommendation:** **Keep current approach** (separate tests). The minor duplication is acceptable given clarity of individual test methods.

---

## Implementation Steps

### Step 1: Add Unit Tests (15-20 minutes)

1. Create `src/test/java/org/chucc/vcserver/service/DiffServiceTest.java`
2. Copy test template from above
3. Add JUnit 5 and Mockito dependencies (should already exist)
4. Run tests: `mvn test -Dtest=DiffServiceTest`
5. Verify all pass

### Step 2: Extract Media Type Constant (2 minutes)

1. Open `src/main/java/org/chucc/vcserver/controller/HistoryController.java`
2. Add constant at class level:
   ```java
   private static final String MEDIA_TYPE_RDF_PATCH = "text/rdf-patch";
   ```
3. Replace string literals (lines 214, 274)
4. Run `mvn -q compile checkstyle:check`

### Step 3: Add Symmetry Test (5 minutes)

1. Open `src/test/java/org/chucc/vcserver/integration/DiffEndpointIT.java`
2. Add `diffCommits_reverseDirection_shouldInvertPatch()` test
3. Run test: `mvn test -Dtest=DiffEndpointIT::diffCommits_reverseDirection_shouldInvertPatch`
4. Verify passes

### Step 4: Run Full Test Suite (5 minutes)

```bash
# Run all diff-related tests
mvn test -Dtest=DiffServiceTest,DiffEndpointIT

# Run static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Should all pass
```

---

## Success Criteria

- ✅ DiffServiceTest created with 5+ unit tests (all passing)
- ✅ Media type constant extracted (no string duplication)
- ✅ Symmetry test added and passing
- ✅ Zero quality violations
- ✅ All existing tests still pass

---

## Notes

- **Priority:** These are nice-to-have improvements, not critical
- **Effort:** Low (25-30 minutes total)
- **Value:** Improved code quality and test coverage
- **Risk:** Minimal (only adding tests and extracting constants)

**Recommendation:** Implement when time permits or as part of broader code quality improvements.

---

## References

- Code Review: code-reviewer agent output (2025-11-01)
- Original Commit: 556141e (feat: implement diff endpoint)
- Testing Guidelines: `.claude/CLAUDE.md#testing-strategy`

---

## Commit Message Template

```
test: add unit tests and enhancements for diff endpoint

Adds optional improvements based on code-reviewer feedback:

1. Unit tests for DiffService (5 test cases)
   - Tests validation logic in isolation
   - Faster feedback without Spring/Kafka overhead

2. Extract media type constant
   - Eliminates "text/rdf-patch" string duplication
   - Reduces typo risk

3. Add diff symmetry test
   - Verifies diff(A,B) = inverse of diff(B,A)
   - Improves confidence in diff correctness

All tests passing. Zero quality violations.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```
