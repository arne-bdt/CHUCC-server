# Task 11: Implement Squash Operation

## Priority
🟢 **LOW** - Advanced operation

## Dependencies
- Task 01 (error handling)

## Protocol Reference
- Section 3.4: Advanced Operations

## Context
Squash combines multiple commits into a single commit, useful for cleaning up history before merging.

## Current State
No squash implementation exists.

## Target State
POST /version/squash endpoint that combines commits.

## Request Format (§3.4)
```json
{
  "branch": "branch-name",
  "commits": ["id1", "id2", "id3"],
  "message": "new message",
  "author": "optional author"
}
```

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/SquashRequest.java`
```java
public class SquashRequest {
    private String branch;           // Target branch
    private List<String> commits;    // Commits to squash (in order)
    private String message;          // Combined message
    private String author;           // Optional author override

    // validation, getters, setters

    public void validate() {
        if (commits == null || commits.size() < 2) {
            throw new IllegalArgumentException("Must specify at least 2 commits to squash");
        }
    }
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/SquashResponse.java`
```java
public class SquashResponse {
    private String branch;
    private String newCommit;              // Squashed commit ID
    private List<String> squashedCommits;  // Original commit IDs
    private String previousHead;           // Branch HEAD before squash

    // constructors, getters, setters
}
```

### 2. Create Command and Event
**File**: `src/main/java/org/chucc/vcserver/command/SquashCommand.java`
```java
public record SquashCommand(
    String branch,
    List<String> commitIds,
    String message,
    String author
) implements Command {}
```

**File**: `src/main/java/org/chucc/vcserver/event/CommitsSquashedEvent.java`
```java
public record CommitsSquashedEvent(
    String branch,
    String newCommitId,
    List<String> squashedCommitIds,
    String author,
    String message,
    String timestamp
) implements VersionControlEvent {}
```

### 3. Create CommandHandler
**File**: `src/main/java/org/chucc/vcserver/command/SquashCommandHandler.java`

Algorithm:
1. Validate: commits must be contiguous in branch history
2. Get state before first commit
3. Get state after last commit
4. Compute combined patch (diff between before and after)
5. Create single new commit with combined patch
6. Update branch to point to new commit

```java
@Component
public class SquashCommandHandler implements CommandHandler<SquashCommand> {

    public SquashResponse handle(SquashCommand command) {
        // 1. Validate branch exists
        Branch branch = branchRepository.findByName(command.branch())
            .orElseThrow(() -> new BranchNotFoundException(command.branch()));

        // 2. Validate commits exist and are contiguous
        List<Commit> commits = validateAndFetchCommits(command.commitIds());

        // 3. Find parent of first commit (base state)
        Commit firstCommit = commits.get(0);
        if (firstCommit.parents().length == 0) {
            throw new IllegalArgumentException("Cannot squash initial commit");
        }
        CommitId baseCommitId = firstCommit.parents()[0];

        // 4. Get state before and after
        Dataset beforeState = datasetService.materializeCommit(baseCommitId);
        Commit lastCommit = commits.get(commits.size() - 1);
        Dataset afterState = datasetService.materializeCommit(lastCommit.id());

        // 5. Compute combined patch (diff)
        RDFPatch combinedPatch = RdfPatchUtil.diff(beforeState, afterState);

        // 6. Create new squashed commit
        CommitId newCommitId = CommitId.generate();
        Commit newCommit = new Commit(
            newCommitId,
            new CommitId[]{baseCommitId},
            command.author() != null ? command.author() : firstCommit.author(),
            command.message(),
            Instant.now(),
            combinedPatch
        );

        commitRepository.save(newCommit);

        // 7. Find commits after the squashed range
        List<Commit> commitsAfter = findCommitsAfter(lastCommit.id(), branch.getHead());

        // 8. If there are commits after, need to reapply them
        if (!commitsAfter.isEmpty()) {
            // Reapply commits (similar to rebase)
            // This is complex - may want to restrict squash to HEAD range only
            throw new UnsupportedOperationException(
                "Squashing non-HEAD commits not yet supported"
            );
        }

        // 9. Update branch
        String previousHead = branch.getHead().toString();
        branch.setHead(newCommitId);
        branchRepository.save(branch);

        // 10. Publish event
        eventPublisher.publish(new CommitsSquashedEvent(...));

        return new SquashResponse(
            command.branch(),
            newCommitId.toString(),
            command.commitIds(),
            previousHead
        );
    }

    private List<Commit> validateAndFetchCommits(List<String> commitIds) {
        List<Commit> commits = new ArrayList<>();
        for (String id : commitIds) {
            Commit commit = commitRepository.findById(new CommitId(id))
                .orElseThrow(() -> new CommitNotFoundException(id));
            commits.add(commit);
        }

        // Verify commits are contiguous in history
        for (int i = 1; i < commits.size(); i++) {
            Commit current = commits.get(i);
            Commit previous = commits.get(i - 1);

            if (current.parents().length != 1 ||
                !current.parents()[0].equals(previous.id())) {
                throw new IllegalArgumentException(
                    "Commits must be contiguous in branch history"
                );
            }
        }

        return commits;
    }
}
```

### 4. Add Controller Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java` (update)

```java
@PostMapping(
    value = "/squash",
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Operation(
    summary = "Squash commits",
    description = "Combine multiple commits into a single commit"
)
@ApiResponse(
    responseCode = "200",
    description = "Commits squashed successfully",
    headers = @Header(
        name = "ETag",
        description = "New commit ID",
        schema = @Schema(type = "string")
    )
)
@ApiResponse(
    responseCode = "400",
    description = "Invalid request - commits not contiguous",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<SquashResponse> squashCommits(
    @RequestBody SquashRequest request
) {
    request.validate();
    // Handle command
    // Return response with ETag
}
```

## Request Example
```http
POST /version/squash HTTP/1.1
Content-Type: application/json

{
  "branch": "feature",
  "commits": [
    "01936c7f-1111-7890-abcd-ef1234567890",
    "01936c7f-2222-7890-abcd-ef1234567890",
    "01936c7f-3333-7890-abcd-ef1234567890"
  ],
  "message": "Implement feature X",
  "author": "Alice"
}
```

## Response Example
```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "01936c81-aaaa-7890-abcd-ef1234567890"

{
  "branch": "feature",
  "newCommit": "01936c81-aaaa-7890-abcd-ef1234567890",
  "squashedCommits": [
    "01936c7f-1111-7890-abcd-ef1234567890",
    "01936c7f-2222-7890-abcd-ef1234567890",
    "01936c7f-3333-7890-abcd-ef1234567890"
  ],
  "previousHead": "01936c7f-3333-7890-abcd-ef1234567890"
}
```

## Acceptance Criteria
- [ ] POST /version/squash endpoint created
- [ ] Combines multiple commits into one
- [ ] Validates commits are contiguous
- [ ] Computes combined patch correctly
- [ ] Updates branch pointer
- [ ] Returns 200 with new commit details
- [ ] Returns 400 if commits not contiguous
- [ ] Returns 404 if any commit not found
- [ ] Combined message used for new commit

## Test Requirements

### Unit Tests
1. `SquashCommandHandlerTest.java`
   - Test squashing 2 commits
   - Test squashing 3+ commits
   - Test non-contiguous commits → error
   - Test missing commit → error

2. `CommitsSquashedEventTest.java`
   - Test event structure

### Integration Tests
1. `SquashIntegrationTest.java`
   - Create branch with commits A, B, C
   - Squash B and C
   - Verify new commit has combined changes
   - Verify branch HEAD is new commit
   - Verify original commits still exist (orphaned)

## Files to Create
- All files listed in Implementation Tasks section

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium-High** - Complex validation and patch combination

## Notes
- Initial version: only squash at branch HEAD (simplifies logic)
- Future enhancement: support squashing commits in middle of history (requires rebase)
- Combined patch should be semantically equivalent to applying all patches in sequence
- Consider limiting number of commits to squash (e.g., max 100) for performance
- Squashing is a form of history rewriting - use with caution
