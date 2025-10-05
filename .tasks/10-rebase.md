# Task 10: Implement Rebase Operation

## Priority
🟢 **LOW** - Advanced operation, complex

## Dependencies
- Task 01 (error handling)
- Task 02 (selector validation)

## Protocol Reference
- Section 3.4: Advanced Operations

## Context
Rebase reapplies commits from one branch onto another, creating new commits with new IDs but same changes.

## Current State
No rebase implementation exists.

## Target State
POST /version/rebase endpoint that reapplies commits.

## Request Format (§3.4)
```json
{
  "branch": "branch-name",
  "onto": "target-ref",
  "from": "base-commit"
}
```

Where:
- **branch**: Branch to rebase
- **onto**: Target ref (branch or commit) to rebase onto
- **from**: Base commit (exclusive) - commits after this will be rebased

Example: Rebase feature branch onto updated main
- Branch: "feature"
- Onto: "main"
- From: original divergence point

## Algorithm
1. Find commits in range (from..branch]
2. For each commit in order:
   - Extract patch
   - Apply to current state
   - Detect conflicts
   - If conflict: abort and report
   - If success: create new commit
3. Move branch pointer to final commit
4. Return list of new commit IDs

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/RebaseRequest.java`
```java
public class RebaseRequest {
    private String branch;    // Branch to rebase
    private String onto;      // Target to rebase onto
    private String from;      // Base commit (exclusive)

    // validation, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/RebaseResponse.java`
```java
public class RebaseResponse {
    private String branch;
    private String newHead;           // Final commit after rebase
    private List<String> newCommits;  // All new commit IDs created
    private int rebasedCount;         // Number of commits rebased

    // constructors, getters, setters
}
```

### 2. Create Command and Event
**File**: `src/main/java/org/chucc/vcserver/command/RebaseCommand.java`
```java
public record RebaseCommand(
    String branch,
    String onto,
    String from,
    String author
) implements Command {}
```

**File**: `src/main/java/org/chucc/vcserver/event/BranchRebasedEvent.java`
```java
public record BranchRebasedEvent(
    String branch,
    String newHead,
    String previousHead,
    List<String> newCommits,
    String timestamp
) implements VersionControlEvent {}
```

### 3. Create CommandHandler
**File**: `src/main/java/org/chucc/vcserver/command/RebaseCommandHandler.java`

```java
@Component
public class RebaseCommandHandler implements CommandHandler<RebaseCommand> {

    public RebaseResponse handle(RebaseCommand command) {
        // 1. Validate inputs
        Branch branch = branchRepository.findByName(command.branch())
            .orElseThrow(() -> new BranchNotFoundException(command.branch()));

        CommitId ontoCommitId = resolveRef(command.onto());
        CommitId fromCommitId = new CommitId(command.from());

        // 2. Find commits to rebase (from..branch]
        List<Commit> commitsToRebase = findCommitRange(fromCommitId, branch.getHead());

        if (commitsToRebase.isEmpty()) {
            // No commits to rebase - no-op
            throw new IllegalArgumentException("No commits to rebase");
        }

        // 3. Get base state (onto ref)
        Dataset currentState = datasetService.materializeCommit(ontoCommitId);
        CommitId currentCommitId = ontoCommitId;

        List<String> newCommitIds = new ArrayList<>();

        // 4. Replay each commit
        for (Commit originalCommit : commitsToRebase) {
            RDFPatch patch = originalCommit.getPatch();

            // Check for conflicts
            PatchIntersection intersection = new PatchIntersection(patch, currentState);
            if (intersection.hasConflicts()) {
                throw new RebaseConflictException(
                    originalCommit.id(),
                    intersection.getConflicts()
                );
            }

            // Apply patch
            currentState = datasetService.applyPatch(currentState, patch);

            // Create new commit
            CommitId newCommitId = CommitId.generate();
            Commit newCommit = new Commit(
                newCommitId,
                new CommitId[]{currentCommitId},
                originalCommit.author(),
                originalCommit.message(),
                Instant.now(),
                patch
            );

            commitRepository.save(newCommit);
            newCommitIds.add(newCommitId.toString());
            currentCommitId = newCommitId;
        }

        // 5. Update branch pointer
        branch.setHead(currentCommitId);
        branchRepository.save(branch);

        // 6. Publish event
        eventPublisher.publish(new BranchRebasedEvent(...));

        return new RebaseResponse(
            command.branch(),
            currentCommitId.toString(),
            newCommitIds,
            newCommitIds.size()
        );
    }

    private List<Commit> findCommitRange(CommitId from, CommitId to) {
        // Walk commit graph from 'to' back to 'from' (exclusive)
        // Return commits in chronological order (oldest first)
    }

    private CommitId resolveRef(String ref) {
        // Check if it's a branch name
        Optional<Branch> branch = branchRepository.findByName(ref);
        if (branch.isPresent()) {
            return branch.get().getHead();
        }

        // Check if it's a tag name
        Optional<Tag> tag = tagRepository.findByName(ref);
        if (tag.isPresent()) {
            return tag.get().getTarget();
        }

        // Assume it's a commit ID
        return new CommitId(ref);
    }
}
```

### 4. Create Custom Exception
**File**: `src/main/java/org/chucc/vcserver/exception/RebaseConflictException.java`
```java
public class RebaseConflictException extends VcException {
    private final CommitId conflictingCommit;
    private final List<ConflictItem> conflicts;

    public RebaseConflictException(CommitId conflictingCommit, List<ConflictItem> conflicts) {
        super("rebase_conflict", 409,
              "Rebase encountered conflicts at commit " + conflictingCommit);
        this.conflictingCommit = conflictingCommit;
        this.conflicts = conflicts;
    }

    // getters
}
```

### 5. Add Controller Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java` (update)

```java
@PostMapping(
    value = "/rebase",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Operation(
    summary = "Rebase branch",
    description = "Reapply commits from one branch onto another"
)
@ApiResponse(responseCode = "200", description = "Branch rebased successfully")
@ApiResponse(
    responseCode = "409",
    description = "Rebase conflict",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<RebaseResponse> rebaseBranch(
    @RequestBody RebaseRequest request,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author
) {
    // Handle command
    // Return response
}
```

## Request Example
```http
POST /version/rebase HTTP/1.1
Content-Type: application/json

{
  "branch": "feature",
  "onto": "main",
  "from": "01936c7f-0000-7890-abcd-ef1234567890"
}
```

## Response Examples

### Success (200)
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "branch": "feature",
  "newHead": "01936c81-9999-7890-abcd-ef1234567890",
  "newCommits": [
    "01936c81-8888-7890-abcd-ef1234567890",
    "01936c81-9999-7890-abcd-ef1234567890"
  ],
  "rebasedCount": 2
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
  "code": "rebase_conflict",
  "detail": "Rebase encountered conflicts at commit 01936c81-8888-7890-abcd-ef1234567890",
  "conflicts": [...]
}
```

## Acceptance Criteria
- [ ] POST /version/rebase endpoint created
- [ ] Replays commits in correct order
- [ ] Creates new commits with new IDs
- [ ] Preserves original commit messages and authors
- [ ] Detects and reports conflicts
- [ ] Returns 200 with list of new commits
- [ ] Returns 409 on conflict
- [ ] Branch pointer updated to final commit

## Test Requirements

### Unit Tests
1. `RebaseCommandHandlerTest.java`
   - Test successful rebase
   - Test conflict during rebase
   - Test empty range (no commits to rebase)

### Integration Tests
1. `RebaseIntegrationTest.java`
   - Create main branch with commits A, B
   - Create feature branch from A with commits C, D
   - Add commit E to main
   - Rebase feature onto main
   - Verify feature has commits E, C', D' (new IDs)
   - Test conflicting rebase

## Files to Create
- All files listed in Implementation Tasks section

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**High** - Complex algorithm with conflict handling

## Notes
- Rebase rewrites history - use with caution
- All rebased commits get new UUIDs and timestamps
- Original commits remain in the DAG (orphaned)
- Consider implementing `--abort` flag for conflict recovery (future enhancement)
- Interactive rebase (editing/squashing during rebase) is out of scope for now
