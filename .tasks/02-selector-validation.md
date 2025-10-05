# Task 02: Implement Selector Validation

## Priority
🔴 **HIGH** - Foundation for query and update operations

## Dependencies
- Task 01 (error handling) - needs SelectorConflictException

## Protocol Reference
- Section 4: Selectors (URL parameters)
- Section 6: Concurrency, ETag, and Conditional Requests

## Context
The protocol requires that selectors (`branch`, `commit`, `asOf`) are mutually exclusive unless explicitly documented. Providing multiple selectors should result in a 400 Bad Request with error code `selector_conflict`.

Exception: `asOf` is allowed with `branch` to select the base state (§3.2).

## Current State
No validation exists for selector conflicts.

## Target State
Centralized selector validation that:
- Ensures only one primary selector is provided
- Allows `asOf` + `branch` combination
- Throws `SelectorConflictException` on violation
- Returns 400 with problem+json

## Implementation Tasks

### 1. Create Selector Value Object
**File**: `src/main/java/org/chucc/vcserver/domain/Selector.java`
```java
public class Selector {
    private final String branch;
    private final String commit;
    private final String asOf;

    private Selector(String branch, String commit, String asOf) {
        validate(branch, commit, asOf);
        this.branch = branch;
        this.commit = commit;
        this.asOf = asOf;
    }

    public static Selector of(String branch, String commit, String asOf) {
        return new Selector(branch, commit, asOf);
    }

    private void validate(String branch, String commit, String asOf) {
        // Validation logic per §4
    }

    public boolean hasBranch() { return branch != null; }
    public boolean hasCommit() { return commit != null; }
    public boolean hasAsOf() { return asOf != null; }
    // getters
}
```

### 2. Add Validation Logic
Rules from §4:
1. Exactly one of: `branch`, `commit`, `asOf`
2. EXCEPT: `asOf` + `branch` is allowed
3. Parameter names are case-sensitive and normative

**Validation matrix:**
| branch | commit | asOf | Valid? |
|--------|--------|------|--------|
| ✓      | -      | -    | ✓      |
| -      | ✓      | -    | ✓      |
| -      | -      | ✓    | ✓      |
| ✓      | -      | ✓    | ✓ (special case) |
| ✓      | ✓      | -    | ✗ selector_conflict |
| -      | ✓      | ✓    | ✗ selector_conflict |
| ✓      | ✓      | ✓    | ✗ selector_conflict |

### 3. Update Controllers
Add selector validation to:
- `SparqlController.querySparqlGet()` - line 86
- `SparqlController.executeSparqlPost()` - line 181
- Future POST /version/commits endpoint

### 4. Create Utility for Common Validation
**File**: `src/main/java/org/chucc/vcserver/util/SelectorValidator.java`
```java
public class SelectorValidator {
    public static void validateMutualExclusion(String branch, String commit, String asOf) {
        int count = count(branch) + count(commit) + count(asOf);

        if (count == 0) {
            return; // All null is valid (use default)
        }

        if (count == 1) {
            return; // Exactly one is valid
        }

        if (count == 2 && branch != null && asOf != null && commit == null) {
            return; // asOf + branch is allowed per §3.2
        }

        throw new SelectorConflictException(
            "Selectors branch, commit, and asOf are mutually exclusive (except asOf+branch)"
        );
    }

    private static int count(String s) {
        return s != null && !s.isBlank() ? 1 : 0;
    }
}
```

## Acceptance Criteria
- [ ] Selector value object created
- [ ] Validation enforces mutual exclusivity per §4
- [ ] Special case: asOf + branch is allowed
- [ ] SelectorConflictException thrown on violation
- [ ] Exception handler converts to 400 with problem+json
- [ ] Error code is `selector_conflict`

## Test Requirements

### Unit Tests
1. `SelectorTest.java`
   - Valid: only branch
   - Valid: only commit
   - Valid: only asOf
   - Valid: branch + asOf
   - Invalid: branch + commit → SelectorConflictException
   - Invalid: commit + asOf → SelectorConflictException
   - Invalid: all three → SelectorConflictException
   - Valid: all null (default case)

2. `SelectorValidatorTest.java`
   - Same test cases as above

### Integration Tests
1. `SelectorValidationIntegrationTest.java`
   - GET /sparql?query=...&branch=main&commit=xyz → 400
   - Verify response body has code: "selector_conflict"
   - Verify Content-Type: application/problem+json

## Files to Create
- `src/main/java/org/chucc/vcserver/domain/Selector.java`
- `src/main/java/org/chucc/vcserver/util/SelectorValidator.java`
- `src/test/java/org/chucc/vcserver/domain/SelectorTest.java`
- `src/test/java/org/chucc/vcserver/util/SelectorValidatorTest.java`
- `src/test/java/org/chucc/vcserver/integration/SelectorValidationIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
  - Add validation to querySparqlGet()
  - Add validation to executeSparqlPost()

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low-Medium** - Straightforward validation logic, well-defined rules

## Notes
- This validation will be reused in many endpoints
- Consider creating a Spring @Component for dependency injection if needed
- The special case (asOf + branch) is important for time-travel queries
