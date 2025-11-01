# Task: Fix Diff Endpoint Code Quality Issues

**Status:** ✅ Completed
**Priority:** High
**Category:** Code Quality / Bug Fix
**Estimated Time:** 20-30 minutes
**Actual Time:** ~15 minutes
**Created:** 2025-11-01
**Completed:** 2025-11-01
**Source:** code-reviewer agent feedback on commit 556141e
**Completion Commits:** 689379d (FQCN fixes), 39bcbe3 (feature flag test)

---

## Overview

After implementing the diff endpoint (commit 556141e), the code-reviewer agent identified several code quality issues that should be addressed. These are minor issues that don't affect functionality but impact code consistency and maintainability.

**Related Commit:** 556141e (feat: implement diff endpoint)

---

## Issues Identified

### 1. FQCN Usage Instead of Imports (High Priority)

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

**Lines:** 40, 56, 264-267

**Issue:** Using fully-qualified class names instead of imports reduces readability and violates project conventions.

**Current Code:**
```java
private final org.chucc.vcserver.service.DiffService diffService;  // Line 40

public HistoryController(VersionControlProperties vcProperties,
    HistoryService historyService,
    org.chucc.vcserver.service.DiffService diffService) {          // Line 56

org.chucc.vcserver.domain.CommitId fromCommitId =                  // Line 264
    org.chucc.vcserver.domain.CommitId.of(from);
org.chucc.vcserver.domain.CommitId toCommitId =                    // Line 266
    org.chucc.vcserver.domain.CommitId.of(to);
```

**Fix Required:**
```java
// Add to imports section (after line 18)
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.service.DiffService;

// Update usage:
private final DiffService diffService;                              // Line 40

public HistoryController(VersionControlProperties vcProperties,
    HistoryService historyService,
    DiffService diffService) {                                     // Line 56

CommitId fromCommitId = CommitId.of(from);                         // Line 264
CommitId toCommitId = CommitId.of(to);                             // Line 266
```

---

### 2. Missing Feature Flag Test (Medium Priority)

**File:** `src/test/java/org/chucc/vcserver/integration/DiffEndpointIT.java`

**Issue:** No test verifies behavior when `vc.diff-enabled=false`. The controller has logic to return 404 when the feature is disabled (lines 247-255 in HistoryController), but this code path is untested.

**Test to Add:**
```java
@Test
@TestPropertySource(properties = "vc.diff-enabled=false")
void diffCommits_whenFeatureDisabled_shouldReturn404() {
  // Given: Valid dataset and commits
  String dataset = "test-diff-disabled";
  CommitId commit1 = createCommit(
      dataset,
      List.of(),
      "Alice <alice@example.org>",
      "Initial commit",
      createSimplePatch("http://ex.org/s1", "http://ex.org/p1", "value1")
  );

  // When: Request diff with feature disabled
  String url = String.format(
      "/version/diff?dataset=%s&from=%s&to=%s",
      dataset, commit1.value(), commit1.value()
  );
  ResponseEntity<String> response = restTemplate.exchange(
      url,
      HttpMethod.GET,
      null,
      String.class
  );

  // Then: Should return 404 Not Found
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  assertThat(response.getBody()).contains("Diff endpoint is disabled");
}
```

**Note:** May require separate test class since `@TestPropertySource` is class-level.

---

### 3. Inconsistent Error Handling (Medium Priority)

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

**Lines:** 277-287

**Issue:** Manual exception handling creates inconsistency with project's centralized exception handling.

**Current Code:**
```java
} catch (IllegalArgumentException e) {
  throw new IllegalArgumentException("Invalid commit ID format: " + e.getMessage(), e);
} catch (org.chucc.vcserver.exception.CommitNotFoundException e) {
  return ResponseEntity.status(HttpStatus.NOT_FOUND)
      .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body(new ProblemDetail(
          e.getMessage(),
          HttpStatus.NOT_FOUND.value(),
          "NOT_FOUND"
      ).toString());
}
```

**Investigation Required:**
1. Check if `@ControllerAdvice` exists that handles `CommitNotFoundException`
2. If yes, remove manual catch block and let global handler handle it
3. If no, make `IllegalArgumentException` handling explicit too

**Possible Fix (if @ControllerAdvice exists):**
```java
// Remove catch block for CommitNotFoundException
// Let global exception handler deal with it
public ResponseEntity<String> diffCommits(/*params*/) {
  // ... validation ...

  CommitId fromCommitId = CommitId.of(from);
  CommitId toCommitId = CommitId.of(to);

  // Throws CommitNotFoundException - handled by @ControllerAdvice
  String patchText = diffService.diffCommits(dataset, fromCommitId, toCommitId);

  return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("text/rdf-patch"))
      .body(patchText);
}
```

---

## Implementation Steps

### Step 1: Fix FQCN Usage (5 minutes)

1. Open `src/main/java/org/chucc/vcserver/controller/HistoryController.java`
2. Add imports after line 18:
   ```java
   import org.chucc.vcserver.domain.CommitId;
   import org.chucc.vcserver.service.DiffService;
   ```
3. Replace all FQCN usages with short names (lines 40, 56, 264, 266)
4. Run `mvn -q compile checkstyle:check` to verify

### Step 2: Add Feature Flag Test (10 minutes)

**Option A: Add to existing DiffEndpointIT**
- Add `@TestPropertySource` at class level
- May affect other tests (all would run with flag disabled)

**Option B: Create new test class (RECOMMENDED)**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "vc.diff-enabled=false")
class DiffEndpointFeatureFlagIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false;
  }

  @Test
  void diffCommits_whenFeatureDisabled_shouldReturn404() {
    // Test implementation from above
  }
}
```

### Step 3: Investigate Exception Handling (10 minutes)

1. Search for `@ControllerAdvice` or `@RestControllerAdvice`:
   ```bash
   grep -r "@ControllerAdvice\|@RestControllerAdvice" src/main/java/
   ```

2. If found:
   - Check if it handles `CommitNotFoundException`
   - If yes, remove manual catch block in `diffCommits` method
   - If no, add handler to `@ControllerAdvice`

3. If not found:
   - Consider creating `GlobalExceptionHandler` with `@ControllerAdvice`
   - Add handlers for common exceptions (`CommitNotFoundException`, `IllegalArgumentException`, etc.)

### Step 4: Run Tests (5 minutes)

```bash
# Run diff endpoint tests
mvn -q test -Dtest=DiffEndpointIT

# If new test class created
mvn -q test -Dtest=DiffEndpointFeatureFlagIT

# Run static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check
```

---

## Success Criteria

- ✅ No FQCN usage in HistoryController (proper imports added)
- ✅ Feature flag disabled scenario tested (404 response verified)
- ✅ Exception handling consistent with project patterns
- ✅ All tests pass (including new feature flag test)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ Code review findings addressed

---

## Testing Strategy

**Pattern:** API Layer Test (projector DISABLED)
- Test HTTP status codes and headers
- Verify feature flag behavior
- Do NOT query repositories

**New Test File (if created):**
```
src/test/java/org/chucc/vcserver/integration/DiffEndpointFeatureFlagIT.java
```

---

## Notes

- These are code quality improvements, not bug fixes
- Functionality is correct; this improves maintainability
- FQCN fix is cosmetic but important for consistency
- Feature flag test closes a coverage gap
- Exception handling investigation may reveal broader patterns

---

## References

- Code Review: code-reviewer agent output (2025-11-01)
- Original Commit: 556141e (feat: implement diff endpoint)
- Project Guidelines: .claude/CLAUDE.md
- Testing Patterns: docs/architecture/cqrs-event-sourcing.md#testing-implications

---

## Follow-up Tasks

After completing this task, consider:
- [ ] Add unit tests for DiffService (see `.tasks/enhancements/01-diff-endpoint-enhancements.md`)
- [ ] Extract media type constant
- [ ] Review other controllers for similar FQCN usage
- [ ] Document exception handling patterns in architecture docs
