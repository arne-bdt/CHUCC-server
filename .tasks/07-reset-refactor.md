# Task 07: Refactor Reset Endpoint to Correct Path

## Priority
🟡 **MEDIUM** - Spec compliance

## Dependencies
- Task 01 (error handling)
- Task 02 (selector validation)

## Protocol Reference
- Section 3.4: Advanced Operations

## Context
The current implementation has reset at POST /version/branches/{name} but the spec requires POST /version/reset as a standalone advanced operation (§3.4).

## Current State
```java
// BranchController.java line 154-191
@PostMapping(value = "/{name}", ...)
public ResponseEntity<String> resetBranch(
    @PathVariable String name,
    @RequestBody String resetRequest
)
```

## Target State (per §3.4)
```java
// New ResetController.java or AdvancedOpsController.java
@PostMapping("/version/reset")
public ResponseEntity<ResetResponse> resetBranch(
    @RequestBody ResetRequest request
)
```

## Request Format (§3.4)
```json
{
  "branch": "branch-name",
  "to": "commitId",
  "mode": "hard|soft|mixed"
}
```

Where:
- **hard**: Move branch pointer, discard working state
- **soft**: Move branch pointer, keep working state
- **mixed**: Move branch pointer, partially keep working state

Note: For RDF datasets, working state semantics differ from Git. Consider:
- **hard**: Reset to commit, materialized dataset becomes commit state
- **soft**: Reset pointer only, changes remain uncommitted
- **mixed**: May not apply to RDF context (document this)

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/ResetRequest.java`
```java
public class ResetRequest {
    private String branch;       // Required
    private String to;           // Target commit ID
    private ResetMode mode;      // hard, soft, mixed

    // validation, getters, setters
}

public enum ResetMode {
    HARD, SOFT, MIXED
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/ResetResponse.java`
```java
public class ResetResponse {
    private String branch;
    private String newHead;      // Commit ID after reset
    private String previousHead; // Commit ID before reset

    // constructors, getters, setters
}
```

### 2. Create New Controller
**File**: `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java`

```java
@RestController
@RequestMapping("/version")
@Tag(name = "Version Control", description = "Advanced operations")
public class AdvancedOpsController {

    @PostMapping(
        value = "/reset",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
        summary = "Reset branch pointer",
        description = "Move branch pointer to a different commit"
    )
    @ApiResponse(responseCode = "200", description = "Branch reset successfully")
    @ApiResponse(
        responseCode = "404",
        description = "Branch or commit not found",
        content = @Content(mediaType = "application/problem+json")
    )
    public ResponseEntity<ResetResponse> resetBranch(
        @RequestBody ResetRequest request
    ) {
        // Use existing ResetBranchCommandHandler
        // Return response with before/after commit IDs
    }
}
```

### 3. Update ResetBranchCommand
**File**: `src/main/java/org/chucc/vcserver/command/ResetBranchCommand.java`

Ensure it supports all three modes if not already.

### 4. Deprecate Old Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/BranchController.java`

Option A: Remove POST /version/branches/{name} entirely
Option B: Keep it but delegate to new endpoint with deprecation warning

Recommend: **Remove it** to avoid confusion and enforce spec compliance.

### 5. Update Integration Tests
Move/update tests from BranchController to new AdvancedOpsController.

## Request Example
```http
POST /version/reset HTTP/1.1
Content-Type: application/json

{
  "branch": "main",
  "to": "01936c7f-8a2e-7890-abcd-ef1234567890",
  "mode": "hard"
}
```

## Response Example
```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "01936c7f-8a2e-7890-abcd-ef1234567890"

{
  "branch": "main",
  "newHead": "01936c7f-8a2e-7890-abcd-ef1234567890",
  "previousHead": "01936c80-1234-7890-abcd-ef1234567890"
}
```

## Acceptance Criteria
- [ ] POST /version/reset endpoint created
- [ ] Accepts JSON with branch, to, mode fields
- [ ] Returns 200 with reset details
- [ ] Returns 404 if branch or commit not found
- [ ] ETag header contains new head commit ID
- [ ] Old POST /version/branches/{name} endpoint removed
- [ ] All three modes (hard, soft, mixed) work correctly
- [ ] Existing ResetBranchCommand/Handler reused

## Test Requirements

### Unit Tests
1. `AdvancedOpsControllerTest.java`
   - Test reset with each mode
   - Test with non-existent branch → 404
   - Test with non-existent commit → 404

2. `ResetBranchCommandHandlerTest.java` (update existing)
   - Verify all modes work correctly

### Integration Tests
1. `ResetOperationIntegrationTest.java`
   - Create branch with commits
   - Reset to earlier commit (hard mode)
   - Verify branch pointer moved
   - Verify materialized dataset matches target commit
   - Test soft mode
   - Test mixed mode (if implemented)

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/ResetRequest.java`
- `src/main/java/org/chucc/vcserver/dto/ResetResponse.java`
- `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java`
- `src/test/java/org/chucc/vcserver/controller/AdvancedOpsControllerTest.java`
- `src/test/java/org/chucc/vcserver/integration/ResetOperationIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/BranchController.java` (remove POST /{name})
- `src/main/java/org/chucc/vcserver/command/ResetBranchCommand.java` (if needed)

## Files to Delete
- Tests specific to old endpoint path

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low-Medium** - Mainly refactoring/moving code

## Notes
- This brings the API into spec compliance
- Consider documenting RDF-specific semantics of soft/mixed modes
- The command/event infrastructure already exists, just need to wire up new endpoint
- Keep existing ResetBranchCommand and Handler - just change the HTTP surface
