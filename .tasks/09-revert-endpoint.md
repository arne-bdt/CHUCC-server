# Task 09: Wire Up Revert Endpoint

## Priority
🟡 **MEDIUM** - Infrastructure exists, needs HTTP surface

## Dependencies
- Task 01 (error handling)
- Task 02 (selector validation)

## Protocol Reference
- Section 3.4: Advanced Operations

## Context
The revert command, event, and handler already exist in the codebase. This task only needs to create the HTTP controller endpoint to expose the functionality.

## Current State
- ✅ RevertCommitCommand exists
- ✅ RevertCommitCommandHandler exists
- ✅ RevertCreatedEvent exists
- ❌ No controller endpoint

## Target State
POST /version/revert endpoint that creates an inverse commit.

## Request Format (§3.4)
```json
{
  "commit": "commitId",
  "branch": "branch-name"
}
```

This creates a new commit on the specified branch that undoes the changes from the specified commit.

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/RevertRequest.java`
```java
public class RevertRequest {
    private String commit;    // Commit to revert
    private String branch;    // Target branch for revert commit

    // validation, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/RevertResponse.java`
```java
public class RevertResponse {
    private String newCommit;        // Newly created revert commit ID
    private String branch;           // Target branch
    private String revertedCommit;   // Original commit that was reverted

    // constructors, getters, setters
}
```

### 2. Add Controller Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java` (update)

```java
@PostMapping(
    value = "/revert",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Operation(
    summary = "Revert commit",
    description = "Create inverse commit that undoes changes from a specified commit"
)
@ApiResponse(
    responseCode = "201",
    description = "Revert commit created",
    headers = @Header(
        name = "Location",
        description = "URI of the revert commit",
        schema = @Schema(type = "string")
    )
)
@ApiResponse(
    responseCode = "404",
    description = "Commit or branch not found",
    content = @Content(mediaType = "application/problem+json")
)
@ApiResponse(
    responseCode = "409",
    description = "Revert would cause conflicts",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<RevertResponse> revertCommit(
    @RequestBody RevertRequest request,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message
) {
    // Delegate to existing RevertCommitCommandHandler
    // Build response from result
    // Return 201 with Location and ETag headers
}
```

### 3. Review Existing Command/Handler
**File**: `src/main/java/org/chucc/vcserver/command/RevertCommitCommand.java` (review)
**File**: `src/main/java/org/chucc/vcserver/command/RevertCommitCommandHandler.java` (review)

Ensure they support:
- Inverting the patch from the specified commit
- Applying inverse patch to branch HEAD
- Conflict detection (if inverse patch conflicts with current state)
- Creating revert commit with appropriate message

### 4. Update Exception Handling
Ensure RevertCommitCommandHandler throws appropriate exceptions:
- CommitNotFoundException
- BranchNotFoundException
- RevertConflictException (if needed - may reuse ConcurrentWriteConflictException)

## Request Example
```http
POST /version/revert HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice
SPARQL-VC-Message: Revert problematic change

{
  "commit": "01936c7f-8a2e-7890-abcd-ef1234567890",
  "branch": "main"
}
```

## Response Example
```http
HTTP/1.1 201 Created
Location: /version/commits/01936c81-aaaa-7890-abcd-ef1234567890
ETag: "01936c81-aaaa-7890-abcd-ef1234567890"
Content-Type: application/json

{
  "newCommit": "01936c81-aaaa-7890-abcd-ef1234567890",
  "branch": "main",
  "revertedCommit": "01936c7f-8a2e-7890-abcd-ef1234567890"
}
```

## Acceptance Criteria
- [ ] POST /version/revert endpoint created
- [ ] Delegates to existing RevertCommitCommandHandler
- [ ] Returns 201 with new commit details
- [ ] Returns 404 if commit or branch not found
- [ ] Returns 409 if revert would cause conflicts
- [ ] Location header points to new commit
- [ ] ETag header contains new commit ID
- [ ] Default message is "Revert {commitId}" if not provided

## Test Requirements

### Unit Tests
1. `AdvancedOpsControllerTest.java` (update)
   - Test revert endpoint
   - Mock command handler
   - Verify response structure

2. `RevertCommitCommandHandlerTest.java` (review existing)
   - Ensure comprehensive coverage exists

### Integration Tests
1. `RevertIntegrationTest.java`
   - Create commit with changes
   - Revert the commit
   - Verify original changes are undone
   - Verify revert commit exists
   - Verify branch HEAD points to revert commit
   - Test reverting non-existent commit → 404
   - Test reverting to non-existent branch → 404

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/RevertRequest.java`
- `src/main/java/org/chucc/vcserver/dto/RevertResponse.java`
- `src/test/java/org/chucc/vcserver/integration/RevertIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java`
- `src/test/java/org/chucc/vcserver/controller/AdvancedOpsControllerTest.java`

## Files to Review
- `src/main/java/org/chucc/vcserver/command/RevertCommitCommand.java`
- `src/main/java/org/chucc/vcserver/command/RevertCommitCommandHandler.java`
- `src/main/java/org/chucc/vcserver/event/RevertCreatedEvent.java`
- `src/test/java/org/chucc/vcserver/command/RevertCommitCommandHandlerTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low** - Infrastructure exists, just need HTTP wiring

## Notes
- This is the easiest task since the business logic already exists
- Focus on proper HTTP mapping and error handling
- Revert is different from reset: it creates a new commit, doesn't move pointers
- Common use case: undo a bad commit while preserving history
