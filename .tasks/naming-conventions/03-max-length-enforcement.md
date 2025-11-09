# Task: Enforce Maximum Length for Branches and Tags

**Status:** ✅ Completed
**Priority:** Medium
**Actual Time:** 1 hour

---

## Objective

Enforce maximum length limits for branch and tag names (255 characters, as specified in protocol) to prevent potential issues with storage, display, and URL handling.

---

## Background

Current implementation:
- **Dataset names:** Max 249 characters ✅ (enforced, Kafka limit)
- **Branch names:** No limit ❌ (should be 255 per protocol)
- **Tag names:** No limit ❌ (should be 255 per protocol)

Protocol specification (§14):
```abnf
branch-name = 1*255identifier-char   ; Maximum 255 characters (recommended)
tag-name    = 1*255identifier-char   ; Maximum 255 characters (recommended)
```

---

## Why 255 Characters?

1. **URL handling:** Most web servers have URL length limits (2048-8192 characters)
   - Example URL: `/dataset-name/version/branches/very-long-branch-name/sparql?query=...`
   - Leaving room for dataset name, path segments, query parameters

2. **Database storage:** VARCHAR(255) is common field size in databases

3. **User experience:** Names longer than 255 characters are impractical to read/type

4. **Git compatibility:** Git has practical limits around 255 characters for ref names

---

## Current Implementation Status

### Branch.java

```java
// Currently: No max length check
private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

public Branch(String name, CommitId commitId, boolean isProtected,
              Instant createdAt, Instant lastUpdated, int commitCount) {
  // Validation: pattern only, no length check
  if (!VALID_NAME_PATTERN.matcher(name).matches()) {
    throw new IllegalArgumentException(...);
  }
  // ...
}
```

### Tag.java

```java
// Currently: No max length check
private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

public Tag {
  // Validation: pattern only, no length check
  if (!VALID_NAME_PATTERN.matcher(name).matches()) {
    throw new IllegalArgumentException(...);
  }
  // ...
}
```

---

## Implementation Plan

### Option 1: Add Length Check to Domain Entities (Simple)

**Update:** `Branch.java`

```java
public class Branch {
  private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");
  private static final int MAX_NAME_LENGTH = 255;

  public Branch(String name, CommitId commitId, boolean isProtected,
                Instant createdAt, Instant lastUpdated, int commitCount) {
    Objects.requireNonNull(name, "Branch name cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }

    // NEW: Check max length
    if (name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
        "Branch name too long (max " + MAX_NAME_LENGTH + " characters): " + name.length());
    }

    // Normalize to NFC
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    if (!normalized.equals(name)) {
      throw new IllegalArgumentException(
        "Branch name must be in Unicode NFC normalization form: " + name);
    }

    // Validate pattern
    if (!VALID_NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
        "Branch name must match pattern ^[A-Za-z0-9._\\-]+$: " + name);
    }

    // Rest of constructor...
  }
}
```

**Update:** `Tag.java`

```java
public record Tag(...) {
  private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");
  private static final int MAX_NAME_LENGTH = 255;

  public Tag {
    Objects.requireNonNull(name, "Tag name cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Tag name cannot be blank");
    }

    // NEW: Check max length
    if (name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
        "Tag name too long (max " + MAX_NAME_LENGTH + " characters): " + name.length());
    }

    // Rest of validation...
  }
}
```

---

### Option 2: Use IdentifierValidator (If Task 02 Completed)

If `IdentifierValidator` utility was created in Task 02:

```java
// Branch.java
public Branch(String name, CommitId commitId, boolean isProtected,
              Instant createdAt, Instant lastUpdated, int commitCount) {
  Objects.requireNonNull(commitId, "Branch commitId cannot be null");
  // ...

  // Validate name (includes max length check)
  IdentifierValidator.validate(name, 255, "Branch");

  // ...
}

// Tag.java
public Tag {
  Objects.requireNonNull(commitId, "Tag commitId cannot be null");
  Objects.requireNonNull(createdAt, "Tag createdAt cannot be null");

  // Validate name (includes max length check)
  IdentifierValidator.validate(name, 255, "Tag");
}
```

---

## Tests to Add

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/domain/BranchTest.java`

```java
@Test
void constructor_nameTooLong_shouldThrow() {
  CommitId commitId = CommitId.generate();
  String longName = "a".repeat(256);  // 256 characters

  assertThatThrownBy(() -> new Branch(longName, commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("too long")
    .hasMessageContaining("255");
}

@Test
void constructor_nameExactlyMaxLength_shouldSucceed() {
  CommitId commitId = CommitId.generate();
  String maxName = "a".repeat(255);  // Exactly 255 characters

  Branch branch = new Branch(maxName, commitId);

  assertThat(branch.getName()).hasSize(255);
}

@Test
void constructor_nameJustBelowMaxLength_shouldSucceed() {
  CommitId commitId = CommitId.generate();
  String name = "a".repeat(254);

  Branch branch = new Branch(name, commitId);

  assertThat(branch.getName()).hasSize(254);
}
```

**File:** `src/test/java/org/chucc/vcserver/domain/TagTest.java`

```java
@Test
void constructor_nameTooLong_shouldThrow() {
  CommitId commitId = CommitId.generate();
  String longName = "a".repeat(256);

  assertThatThrownBy(() -> new Tag(longName, commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("too long")
    .hasMessageContaining("255");
}

@Test
void constructor_nameExactlyMaxLength_shouldSucceed() {
  CommitId commitId = CommitId.generate();
  String maxName = "a".repeat(255);

  Tag tag = new Tag(maxName, commitId);

  assertThat(tag.name()).hasSize(255);
}
```

---

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/BranchCreationIT.java`

```java
@Test
void createBranch_nameTooLong_shouldReturn400() {
  String longName = "feature-" + "a".repeat(250);  // > 255 characters
  CreateBranchRequest request = new CreateBranchRequest(longName, "main", false);

  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.APPLICATION_JSON);
  headers.set("SPARQL-VC-Author", "TestUser");

  HttpEntity<CreateBranchRequest> httpEntity = new HttpEntity<>(request, headers);

  ResponseEntity<String> response = restTemplate.postForEntity(
    "/default/version/branches", httpEntity, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(response.getBody())
    .contains("too long")
    .contains("255");
}
```

---

## Implementation Steps

1. **Choose implementation option:**
   - Option 1: Direct implementation (if Task 02 not done)
   - Option 2: Use `IdentifierValidator` (if Task 02 completed)

2. **Update domain entities:**
   - Add max length constant: `private static final int MAX_NAME_LENGTH = 255;`
   - Add length check in constructor
   - Update error message to include max length

3. **Add unit tests:**
   - Test name too long (256+ chars)
   - Test name exactly at max (255 chars)
   - Test name just below max (254 chars)
   - Test with Unicode characters (count characters, not bytes)

4. **Add integration tests:**
   - Test branch creation with long name
   - Test tag creation with long name

5. **Run tests:**
   ```bash
   mvn -q test -Dtest=BranchTest,TagTest
   mvn -q test -Dtest=BranchCreationIT,TagCreationIT
   ```

6. **Update documentation:**
   - Javadoc in `Branch.java` and `Tag.java`
   - Update protocol docs if needed

---

## Acceptance Criteria

- ✅ Branch names longer than 255 characters are rejected
- ✅ Tag names longer than 255 characters are rejected
- ✅ Clear error message indicating max length
- ✅ Names at exactly 255 characters are accepted
- ✅ Unicode character counting is correct (not byte counting)
- ✅ All tests pass
- ✅ No breaking changes (existing short names still work)

---

## Edge Cases to Consider

### Unicode Character Counting

**Important:** Count **characters**, not **bytes**!

```java
// ❌ WRONG: Counts bytes (UTF-8 encoding)
if (name.getBytes(StandardCharsets.UTF_8).length > 255) { ... }

// ✅ CORRECT: Counts characters (code points)
if (name.length() > 255) { ... }
```

**Example:**
- String: `"café"` (4 characters, 5 bytes in UTF-8)
- Should count as: **4 characters** ✅
- Should NOT count as: 5 bytes ❌

### NFC Normalization and Length

After NFC normalization, length might change:

```java
String original = "café";                     // Using decomposed form: café
String normalized = Normalizer.normalize(original, Normalizer.Form.NFC);

// Length might differ after normalization
// Check length AFTER normalization
```

**Implementation:**
```java
// Step 1: Normalize
String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);

// Step 2: Check length on normalized string
if (normalized.length() > MAX_NAME_LENGTH) {
  throw new IllegalArgumentException("Name too long");
}
```

---

## References

- Protocol spec: `protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md` §14
- Git ref limits: https://git-scm.com/docs/git-check-ref-format
- Unicode normalization: `docs/architecture/naming-conventions-analysis.md` §3.5

---

## Risks

**Very low risk:**
- Unlikely anyone has created extremely long branch/tag names
- If they have, they'll get clear error messages on next update
- No data loss (existing refs are immutable)

---

## Follow-up Tasks

- Consider adding API field validation (`@Size(max=255)` in DTOs)
- Add monitoring/metrics for name length distribution
- Document max length in OpenAPI specs
