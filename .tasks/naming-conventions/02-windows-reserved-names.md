# Task: Reject Windows Reserved Device Names

**Status:** ✅ Completed
**Priority:** Low
**Actual Time:** 2 hours

---

## Objective

Reject Windows reserved device names (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`) for datasets, branches, and tags to prevent cross-platform compatibility issues.

---

## Background

Windows treats certain names as reserved device names:
- **Console devices:** `CON`, `PRN`, `AUX`, `NUL`
- **Serial ports:** `COM1`, `COM2`, ..., `COM9`
- **Parallel ports:** `LPT1`, `LPT2`, ..., `LPT9`

Creating files/directories with these names on Windows causes errors or undefined behavior. While CHUCC uses in-memory RDF storage, Kafka topics and potential future file-based features could be affected.

---

## Windows Reserved Names

```java
private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
  // Console and printer
  "CON", "PRN", "AUX", "NUL",

  // Serial ports (COM1-COM9)
  "COM1", "COM2", "COM3", "COM4", "COM5",
  "COM6", "COM7", "COM8", "COM9",

  // Parallel ports (LPT1-LPT9)
  "LPT1", "LPT2", "LPT3", "LPT4", "LPT5",
  "LPT6", "LPT7", "LPT8", "LPT9"
);
```

**Note:** Windows treats these names **case-insensitively** (`CON` = `con` = `Con`).

---

## Implementation Plan

### 1. Create Shared Validation Utility

**New file:** `src/main/java/org/chucc/vcserver/util/IdentifierValidator.java`

```java
package org.chucc.vcserver.util;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for validating identifier names (datasets, branches, tags).
 * Enforces naming conventions from protocol specification.
 */
public final class IdentifierValidator {

  private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

  private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
  );

  private IdentifierValidator() {
    // Utility class
  }

  /**
   * Validates an identifier name (dataset, branch, or tag).
   *
   * @param name the identifier name
   * @param maxLength maximum allowed length
   * @param type type name for error messages ("Dataset", "Branch", "Tag")
   * @throws IllegalArgumentException if validation fails
   */
  public static void validate(String name, int maxLength, String type) {
    Objects.requireNonNull(name, type + " name cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException(type + " name cannot be blank");
    }

    if (name.length() > maxLength) {
      throw new IllegalArgumentException(
        type + " name too long (max " + maxLength + " characters)");
    }

    // Normalize to NFC
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    if (!normalized.equals(name)) {
      throw new IllegalArgumentException(
        type + " name must be in Unicode NFC normalization form");
    }

    // Validate pattern
    if (!VALID_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
        type + " name contains invalid characters. " +
        "Allowed: A-Z, a-z, 0-9, . (period), _ (underscore), - (hyphen)");
    }

    // Reserved names
    if (name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException(
        type + " name cannot be '.' or '..'");
    }

    // Windows reserved names (case-insensitive)
    if (WINDOWS_RESERVED_NAMES.contains(name.toUpperCase())) {
      throw new IllegalArgumentException(
        type + " name cannot be a Windows reserved device name: " + name);
    }

    // Optional: Reject names starting with underscore
    if (name.startsWith("_")) {
      throw new IllegalArgumentException(
        type + " name cannot start with '_' (reserved for internal use)");
    }

    // Optional: Reject names starting/ending with dot (branches/tags only)
    if (!type.equals("Dataset")) {
      if (name.startsWith(".") || name.endsWith(".")) {
        throw new IllegalArgumentException(
          type + " name cannot start or end with '.'");
      }
    }
  }
}
```

---

### 2. Update Domain Entities

**Update:** `Branch.java`

```java
// Remove local validation logic, delegate to IdentifierValidator
public Branch(String name, CommitId commitId, boolean isProtected,
              Instant createdAt, Instant lastUpdated, int commitCount) {
  Objects.requireNonNull(commitId, "Branch commitId cannot be null");
  Objects.requireNonNull(createdAt, "createdAt cannot be null");
  Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");

  // Validate name
  IdentifierValidator.validate(name, 255, "Branch");

  if (commitCount < 1) {
    throw new IllegalArgumentException("commitCount must be at least 1");
  }

  this.name = name;
  this.commitId = commitId;
  this.isProtected = isProtected;
  this.createdAt = createdAt;
  this.lastUpdated = lastUpdated;
  this.commitCount = commitCount;
}
```

**Update:** `Tag.java`

```java
public Tag {
  Objects.requireNonNull(commitId, "Tag commitId cannot be null");
  Objects.requireNonNull(createdAt, "Tag createdAt cannot be null");

  // Validate name
  IdentifierValidator.validate(name, 255, "Tag");
}
```

**Update:** `CreateDatasetCommand.java`

```java
public CreateDatasetCommand {
  Objects.requireNonNull(author, "Author cannot be null");
  Objects.requireNonNull(description, "Description cannot be null (use Optional.empty())");
  Objects.requireNonNull(initialGraph, "Initial graph cannot be null (use Optional.empty())");

  if (author.isBlank()) {
    throw new IllegalArgumentException("Author cannot be blank");
  }

  // Validate dataset name
  IdentifierValidator.validate(dataset, 249, "Dataset");

  // Validate Kafka configuration if provided
  if (kafkaConfig != null) {
    validateKafkaConfig(kafkaConfig);
  }
}
```

---

### 3. Add Tests

**New file:** `src/test/java/org/chucc/vcserver/util/IdentifierValidatorTest.java`

```java
@Test
void validate_windowsReservedName_CON_shouldThrow() {
  assertThatThrownBy(() -> IdentifierValidator.validate("CON", 249, "Dataset"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

@Test
void validate_windowsReservedName_con_shouldThrow() {
  // Case-insensitive check
  assertThatThrownBy(() -> IdentifierValidator.validate("con", 249, "Dataset"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

@Test
void validate_windowsReservedName_COM1_shouldThrow() {
  assertThatThrownBy(() -> IdentifierValidator.validate("COM1", 255, "Branch"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

@Test
void validate_windowsReservedName_LPT5_shouldThrow() {
  assertThatThrownBy(() -> IdentifierValidator.validate("LPT5", 255, "Tag"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

@Test
void validate_validNameSimilarToReserved_shouldAllow() {
  // "CONN" is not reserved (only "CON")
  assertThatCode(() -> IdentifierValidator.validate("CONN", 249, "Dataset"))
    .doesNotThrowAnyException();

  // "COM10" is not reserved (only COM1-COM9)
  assertThatCode(() -> IdentifierValidator.validate("COM10", 249, "Dataset"))
    .doesNotThrowAnyException();
}
```

**Update existing tests:**

```java
// BranchTest.java
@Test
void constructor_windowsReservedName_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Branch("CON", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

// TagTest.java
@Test
void constructor_windowsReservedName_shouldThrow() {
  CommitId commitId = CommitId.generate();
  assertThatThrownBy(() -> new Tag("PRN", commitId))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}

// CreateDatasetCommandTest.java
@Test
void validateDatasetName_windowsReservedName_shouldThrow() {
  assertThatThrownBy(() -> new CreateDatasetCommand("AUX", "author"))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Windows reserved device name");
}
```

---

## Integration Test

**New file:** `src/test/java/org/chucc/vcserver/integration/WindowsReservedNamesIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class WindowsReservedNamesIT extends IntegrationTestFixture {

  @Test
  void createDataset_windowsReservedName_shouldReturn400() {
    CreateDatasetRequest request = new CreateDatasetRequest(
      "Test dataset", null, null);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser");

    HttpEntity<CreateDatasetRequest> httpEntity =
      new HttpEntity<>(request, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
      "/datasets/CON", httpEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
      .contains("Windows reserved device name");
  }

  @Test
  void createBranch_windowsReservedName_shouldReturn400() {
    CreateBranchRequest request = new CreateBranchRequest("COM1", "main", false);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<CreateBranchRequest> httpEntity =
      new HttpEntity<>(request, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
      "/default/version/branches", httpEntity, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
      .contains("Windows reserved device name");
  }

  @Test
  void createTag_windowsReservedName_shouldReturn400() {
    // Similar test for tags
  }
}
```

---

## Implementation Steps

1. ✅ Create `IdentifierValidator` utility class
2. ✅ Update `Branch.java` to use validator
3. ✅ Update `Tag.java` to use validator
4. ✅ Update `CreateDatasetCommand.java` to use validator
5. ✅ Add unit tests for `IdentifierValidator`
6. ✅ Update existing domain entity tests
7. ✅ Add integration tests
8. ✅ Run full test suite: `mvn -q clean test`
9. ✅ Update Javadoc with Windows reserved name policy
10. ✅ Update `protocol/NAMING_CONVENTIONS.md` with Windows restrictions

---

## Acceptance Criteria

- ✅ All Windows reserved names rejected (case-insensitive)
- ✅ Clear error messages for users
- ✅ Unit test coverage >= 95%
- ✅ Integration tests verify API behavior
- ✅ No breaking changes (existing valid names still work)
- ✅ Documentation updated

---

## Decision Points

1. **Should we reject Windows reserved names?**
   - ✅ **Yes** - Prevents future cross-platform issues
   - ❌ No - Allow but document limitation

2. **Should we also reject names starting with `_`?**
   - ✅ Yes - Kafka convention for internal topics
   - ❌ No - Allow for now

3. **Should we reject names starting/ending with `.`?**
   - ✅ Yes (for branches/tags) - Hidden file convention
   - ❌ No - Allow for now

---

## References

- Windows device names: https://docs.microsoft.com/en-us/windows/win32/fileio/naming-a-file
- Protocol spec: `protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md` §14
- Analysis: `docs/architecture/naming-conventions-analysis.md` - Appendix A

---

## Risks

**Low risk:**
- This is an **additive change** (rejects previously allowed names)
- Unlikely anyone has created datasets/branches named `CON`, `PRN`, etc.
- If they have, they'll get clear error messages

**Migration:**
- Check existing datasets/branches/tags for conflicts before deploying
- Provide migration script if needed (rename CON → CON_)

---

## Notes

- Consider adding this to API documentation as a "naming best practice"
- May want to add monitoring/alerting if users hit this validation
- Could be extended to reject other problematic names (SQL keywords, etc.)
