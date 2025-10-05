# Task 01: Implement Problem+JSON Error Handling

## Priority
🔴 **HIGH** - Foundation for all other tasks

## Dependencies
None - this is a foundational task

## Protocol Reference
- Section 9: Error Format (problem+json)
- Section 8: Status Codes

## Context
The protocol requires all error responses to use RFC 7807 problem+json format with specific canonical error codes. This provides consistent, machine-readable error responses.

## Current State
Controllers return simple JSON error objects with only `title` and `status` fields.

## Target State
All error responses use problem+json format with:
- `type` (URI reference)
- `title` (human-readable summary)
- `status` (HTTP status code)
- `code` (canonical error code from spec)
- Additional fields as needed (e.g., `conflicts` array)

## Canonical Error Codes (from spec §9)
- `selector_conflict` - Multiple mutually exclusive selectors provided
- `merge_conflict` - Merge cannot proceed due to conflicts
- `concurrent_write_conflict` - Concurrent write detected, operation rejected
- `graph_not_found` - Specified graph does not exist
- `tag_retarget_forbidden` - Attempt to change immutable tag target
- `rebase_conflict` - Rebase operation encountered conflicts
- `cherry_pick_conflict` - Cherry-pick operation encountered conflicts

## Implementation Tasks

### 1. Create Problem+JSON DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/ProblemDetail.java`
```java
public class ProblemDetail {
    private String type = "about:blank";
    private String title;
    private int status;
    private String code;
    // + constructors, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/ConflictProblemDetail.java`
```java
public class ConflictProblemDetail extends ProblemDetail {
    private List<ConflictItem> conflicts;
    // + constructors, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/ConflictItem.java`
```java
public class ConflictItem {
    private String graph;    // REQUIRED per §11
    private String subject;  // REQUIRED per §11
    private String predicate;// REQUIRED per §11
    private String object;   // REQUIRED per §11
    private String details;  // Optional explanation
    // + constructors, getters, setters
}
```

### 2. Create Custom Exceptions
**File**: `src/main/java/org/chucc/vcserver/exception/VcException.java`
- Base exception with `code` and `status` fields

**File**: `src/main/java/org/chucc/vcserver/exception/SelectorConflictException.java`
**File**: `src/main/java/org/chucc/vcserver/exception/MergeConflictException.java`
**File**: `src/main/java/org/chucc/vcserver/exception/ConcurrentWriteConflictException.java`
- Each extends VcException with appropriate defaults

### 3. Create Global Exception Handler
**File**: `src/main/java/org/chucc/vcserver/exception/VcExceptionHandler.java`
```java
@ControllerAdvice
public class VcExceptionHandler {
    @ExceptionHandler(VcException.class)
    public ResponseEntity<ProblemDetail> handleVcException(VcException ex) {
        // Convert to problem+json with correct media type
    }
}
```

## Acceptance Criteria
- [ ] All DTOs created with required fields per §11
- [ ] Custom exception hierarchy created
- [ ] Global exception handler converts exceptions to problem+json
- [ ] Content-Type header is `application/problem+json`
- [ ] All canonical error codes from §9 are represented

## Test Requirements

### Unit Tests
1. `ProblemDetailTest.java` - DTO serialization/deserialization
2. `ConflictItemTest.java` - All required fields present
3. `VcExceptionHandlerTest.java` - Exception to problem+json conversion

### Integration Tests
1. `ErrorResponseIntegrationTest.java`
   - Trigger selector_conflict error
   - Verify response status 400
   - Verify Content-Type is application/problem+json
   - Verify response structure matches RFC 7807

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/ProblemDetail.java`
- `src/main/java/org/chucc/vcserver/dto/ConflictProblemDetail.java`
- `src/main/java/org/chucc/vcserver/dto/ConflictItem.java`
- `src/main/java/org/chucc/vcserver/exception/VcException.java`
- `src/main/java/org/chucc/vcserver/exception/SelectorConflictException.java`
- `src/main/java/org/chucc/vcserver/exception/MergeConflictException.java`
- `src/main/java/org/chucc/vcserver/exception/ConcurrentWriteConflictException.java`
- `src/main/java/org/chucc/vcserver/exception/VcExceptionHandler.java`
- `src/test/java/org/chucc/vcserver/dto/ProblemDetailTest.java`
- `src/test/java/org/chucc/vcserver/dto/ConflictItemTest.java`
- `src/test/java/org/chucc/vcserver/exception/VcExceptionHandlerTest.java`
- `src/test/java/org/chucc/vcserver/integration/ErrorResponseIntegrationTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium** - Standard exception handling pattern, but needs careful attention to spec requirements

## Notes
- This is used by all subsequent tasks
- Conflict items MUST include graph field (alignment with GSP extension per §10)
- Some existing code may throw generic exceptions - those will be migrated in later tasks
