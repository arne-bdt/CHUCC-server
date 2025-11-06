# Task: Add Tests for Reserved Names

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1-2 hours

---

## Objective

Add comprehensive test coverage for reserved identifier names (`.`, `..`, names starting with `_`) across datasets, branches, and tags.

---

## Background

Current implementation validates against pattern `^[A-Za-z0-9._\-]+$` but doesn't explicitly test all reserved name cases. The protocol now specifies:

- Cannot be `.` or `..`
- SHOULD NOT start with `_` (reserved for internal use)
- SHOULD NOT start or end with `.` (for branches/tags)

---

## Test Cases to Add

### Dataset Name Tests

**File:** `src/test/java/org/chucc/vcserver/command/CreateDatasetCommandTest.java`

```java
@Test
void validateDatasetName_singleDot_shouldThrow() {
  assertThatThrownBy(() -> new CreateDatasetCommand(".", "author"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void validateDatasetName_doubleDot_shouldThrow() {
  assertThatThrownBy(() -> new CreateDatasetCommand("..", "author"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void validateDatasetName_startsWithUnderscore_shouldWarn() {
  // Option 1: Throw exception
  assertThatThrownBy(() -> new CreateDatasetCommand("_internal", "author"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot start with '_'");

  // Option 2: Log warning and allow (implementation decision)
  // CreateDatasetCommand command = new CreateDatasetCommand("_internal", "author");
  // assertThat(command.dataset()).isEqualTo("_internal");
  // Verify warning logged
}

@Test
void validateDatasetName_startsWithDoubleUnderscore_shouldThrow() {
  // Kafka internal topics start with __
  assertThatThrownBy(() -> new CreateDatasetCommand("__consumer_offsets", "author"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot start with '__'");
}
```

---

### Branch Name Tests

**File:** `src/test/java/org/chucc/vcserver/domain/BranchTest.java` (create if doesn't exist)

```java
@Test
void constructor_singleDot_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Branch(".", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void constructor_doubleDot_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Branch("..", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void constructor_startsWithDot_shouldWarn() {
  CommitId commitId = CommitId.generate();
  // Option 1: Throw exception
  assertThatThrownBy(() -> new Branch(".hidden", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot start with '.'");

  // Option 2: Log warning and allow (implementation decision)
}

@Test
void constructor_endsWithDot_shouldWarn() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Branch("branch.", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot end with '.'");
}

@Test
void constructor_startsWithUnderscore_shouldWarn() {
  CommitId commitId = CommitId.generate();
  // Document decision: allow with warning or reject
  // Option 1: Allow (current behavior)
  Branch branch = new Branch("_internal", commitId);
  assertThat(branch.getName()).isEqualTo("_internal");

  // Option 2: Reject
  // assertThatThrownBy(() -> new Branch("_internal", commitId))
  //   .isInstanceOf(IllegalArgumentException.class);
}
```

---

### Tag Name Tests

**File:** `src/test/java/org/chucc/vcserver/domain/TagTest.java` (create if doesn't exist)

```java
@Test
void constructor_singleDot_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Tag(".", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void constructor_doubleDot_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Tag("..", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot be '.' or '..'");
}

@Test
void constructor_startsWithDot_shouldWarn() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Tag(".temp", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot start with '.'");
}

@Test
void constructor_endsWithDot_shouldWarn() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Tag("tag.", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("cannot end with '.'");
}
```

---

## Implementation Steps

1. Create test files if they don't exist:
   - `BranchTest.java`
   - `TagTest.java`

2. Add reserved name tests to existing files:
   - `CreateDatasetCommandTest.java`

3. **Decision Point:** Should names starting with `_` or `.` be:
   - **Rejected** (throw exception)?
   - **Allowed with warning** (log warning)?
   - **Allowed silently** (current behavior)?

4. Update implementation if decision is to reject:
   - `Branch.java` - Add validation
   - `Tag.java` - Add validation
   - `CreateDatasetCommand.java` - Add validation

5. Run tests:
   ```bash
   mvn -q test -Dtest=BranchTest,TagTest,CreateDatasetCommandTest
   ```

---

## Acceptance Criteria

- ✅ All reserved name patterns tested
- ✅ Tests pass with clear error messages
- ✅ Decision documented on underscore/dot prefix handling
- ✅ Implementation matches protocol specification
- ✅ Test coverage >= 90% for validation logic

---

## References

- Protocol specification: `protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md` §14
- Naming conventions: `protocol/NAMING_CONVENTIONS.md`
- Analysis: `docs/architecture/naming-conventions-analysis.md`
- Current validation:
  - `Branch.java:14`
  - `Tag.java:20`
  - `CreateDatasetCommand.java:88-104`

---

## Notes

- This is a **low-risk** enhancement (adds tests only, no breaking changes)
- Can be completed independently of other tasks
- Consider adding property-based tests for fuzzing reserved names
