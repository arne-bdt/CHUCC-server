# Task 08: Implement Cherry-Pick Operation

## Priority
🟢 **LOW** - Advanced operation

## Dependencies
- Task 01 (error handling)
- Task 02 (selector validation)

## Protocol Reference
- Section 3.4: Advanced Operations

## Context
Cherry-pick applies a specific commit to a target branch, creating a new commit with the same changes but different parent and commit ID.

## Current State
No cherry-pick implementation exists.

## Target State
POST /version/cherry-pick endpoint that applies a commit's changes to a branch.

## Request Format (§3.4)
```json
{
  "commit": "commitId",
  "onto": "branch-name"
}
```

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/CherryPickRequest.java`
```java
public class CherryPickRequest {
    private String commit;    // Source commit to cherry-pick
    private String onto;      // Target branch

    // validation, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/CherryPickResponse.java`
```java
public class CherryPickResponse {
    private String newCommit;     // Newly created commit ID
    private String branch;        // Target branch
    private String sourceCommit;  // Original cherry-picked commit

    // constructors, getters, setters
}
```

### 2. Create Command and Event
**File**: `src/main/java/org/chucc/vcserver/command/CherryPickCommand.java`
```java
public record CherryPickCommand(
    String commitId,
    String targetBranch,
    String author,
    String message
) implements Command {}
```

**File**: `src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java`
```java
public record CherryPickedEvent(
    String newCommitId,
    String sourceCommitId,
    String branch,
    String author,
    String message,
    String timestamp
) implements VersionControlEvent {}
```

### 3. Create CommandHandler
**File**: `src/main/java/org/chucc/vcserver/command/CherryPickCommandHandler.java`

Algorithm:
1. Fetch source commit's patch
2. Fetch target branch's HEAD state
3. Apply patch to target state
4. Detect conflicts (use PatchIntersection)
5. If conflicts: throw CherryPickConflictException
6. If no conflicts: create new commit, publish event

```java
@Component
public class CherryPickCommandHandler implements CommandHandler<CherryPickCommand> {

    public String handle(CherryPickCommand command) {
        // Get source commit
        Commit sourceCommit = commitRepository.findById(command.commitId())
            .orElseThrow(() -> new CommitNotFoundException(command.commitId()));

        // Get target branch
        Branch targetBranch = branchRepository.findByName(command.targetBranch())
            .orElseThrow(() -> new BranchNotFoundException(command.targetBranch()));

        // Get source commit's patch
        RDFPatch sourcePatch = sourceCommit.getPatch();

        // Get target HEAD state
        Dataset targetState = datasetService.materializeCommit(targetBranch.getHead());

        // Check for conflicts
        PatchIntersection intersection = new PatchIntersection(sourcePatch, targetState);
        if (intersection.hasConflicts()) {
            throw new CherryPickConflictException(intersection.getConflicts());
        }

        // Apply patch
        Dataset newState = datasetService.applyPatch(targetState, sourcePatch);

        // Create new commit
        CommitId newCommitId = CommitId.generate();
        Commit newCommit = new Commit(
            newCommitId,
            new CommitId[]{targetBranch.getHead()},
            command.author(),
            command.message() != null ? command.message() :
                "Cherry-pick " + command.commitId(),
            Instant.now(),
            sourcePatch
        );

        // Publish event
        eventPublisher.publish(new CherryPickedEvent(...));

        return newCommitId.toString();
    }
}
```

### 4. Add Controller Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java` (update)

```java
@PostMapping(
    value = "/cherry-pick",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Operation(
    summary = "Cherry-pick commit",
    description = "Apply a specific commit to a target branch"
)
@ApiResponse(responseCode = "201", description = "Commit cherry-picked successfully")
@ApiResponse(
    responseCode = "409",
    description = "Cherry-pick conflict",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<CherryPickResponse> cherryPick(
    @RequestBody CherryPickRequest request,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message
) {
    // Handle command
    // Return 201 with new commit details
}
```

### 5. Create Custom Exception
**File**: `src/main/java/org/chucc/vcserver/exception/CherryPickConflictException.java`
```java
public class CherryPickConflictException extends VcException {
    private final List<ConflictItem> conflicts;

    public CherryPickConflictException(List<ConflictItem> conflicts) {
        super("cherry_pick_conflict", 409, "Cherry-pick operation encountered conflicts");
        this.conflicts = conflicts;
    }

    public List<ConflictItem> getConflicts() {
        return conflicts;
    }
}
```

### 6. Update Exception Handler
**File**: `src/main/java/org/chucc/vcserver/exception/VcExceptionHandler.java` (update)

Add handler for CherryPickConflictException that returns ConflictProblemDetail.

## Request Example
```http
POST /version/cherry-pick HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice
SPARQL-VC-Message: Cherry-pick feature from develop

{
  "commit": "01936c7f-8a2e-7890-abcd-ef1234567890",
  "onto": "main"
}
```

## Response Examples

### Success (201)
```http
HTTP/1.1 201 Created
Location: /version/commits/01936c81-9999-7890-abcd-ef1234567890
ETag: "01936c81-9999-7890-abcd-ef1234567890"
Content-Type: application/json

{
  "newCommit": "01936c81-9999-7890-abcd-ef1234567890",
  "branch": "main",
  "sourceCommit": "01936c7f-8a2e-7890-abcd-ef1234567890"
}
```

### Conflict (409)
```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "code": "cherry_pick_conflict",
  "conflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/s",
      "predicate": "http://example.org/p",
      "object": "\"conflicting value\"",
      "details": "Source adds triple that conflicts with target state"
    }
  ]
}
```

## Acceptance Criteria
- [ ] POST /version/cherry-pick endpoint created
- [ ] Applies source commit changes to target branch
- [ ] Creates new commit with different ID
- [ ] Detects and reports conflicts
- [ ] Returns 201 on success with new commit details
- [ ] Returns 409 on conflict with conflict details
- [ ] Returns 404 if source commit or target branch not found
- [ ] New commit has one parent (target branch HEAD)

## Test Requirements

### Unit Tests
1. `CherryPickCommandHandlerTest.java`
   - Test successful cherry-pick
   - Test conflict detection
   - Test with missing commit → exception
   - Test with missing branch → exception

2. `CherryPickedEventTest.java`
   - Test event structure

### Integration Tests
1. `CherryPickIntegrationTest.java`
   - Create branch A with commit X
   - Create branch B with different commits
   - Cherry-pick commit X onto branch B
   - Verify new commit created on B
   - Verify B contains changes from X
   - Test conflicting cherry-pick → 409

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/CherryPickRequest.java`
- `src/main/java/org/chucc/vcserver/dto/CherryPickResponse.java`
- `src/main/java/org/chucc/vcserver/command/CherryPickCommand.java`
- `src/main/java/org/chucc/vcserver/command/CherryPickCommandHandler.java`
- `src/main/java/org/chucc/vcserver/event/CherryPickedEvent.java`
- `src/main/java/org/chucc/vcserver/exception/CherryPickConflictException.java`
- `src/test/java/org/chucc/vcserver/command/CherryPickCommandHandlerTest.java`
- `src/test/java/org/chucc/vcserver/event/CherryPickedEventTest.java`
- `src/test/java/org/chucc/vcserver/integration/CherryPickIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java`
- `src/main/java/org/chucc/vcserver/exception/VcExceptionHandler.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium-High** - Requires conflict detection logic

## Notes
- Cherry-pick creates new commit even if patch is identical
- The new commit has different parent and timestamp
- Conflict detection reuses PatchIntersection utility
- Message defaults to "Cherry-pick {commitId}" if not provided
