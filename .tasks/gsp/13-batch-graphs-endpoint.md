# Task 13: Batch Graph Operations Endpoint

## Objective
Implement `/version/batch-graphs` endpoint for atomic batch operations on multiple graphs.

## Background
Clients can submit multiple graph operations (PUT, POST, PATCH, DELETE) in a single request. All operations execute atomically, producing a single commit (or no commit for no-op).

## Tasks

### 1. Define BatchGraphsRequest DTO
Create `src/main/java/org/chucc/vcserver/dto/BatchGraphsRequest.java`:
```java
{
  "mode": "single|multiple",  // single commit or multiple commits
  "branch": "branch-name",
  "author": "author-id",
  "message": "commit message",
  "operations": [
    {
      "method": "PUT",
      "graph": "http://ex.org/g1",
      "data": "...",            // RDF content (for PUT/POST)
      "contentType": "text/turtle"
    },
    {
      "method": "PATCH",
      "graph": "http://ex.org/g2",
      "patch": "...",           // RDF Patch (for PATCH)
      "contentType": "text/rdf-patch"
    },
    {
      "method": "DELETE",
      "graph": "http://ex.org/g3"
    }
  ]
}
```

### 2. Define BatchGraphsCommand
Create `src/main/java/org/chucc/vcserver/command/BatchGraphsCommand.java`:
- Fields: dataset, branch, baseCommit, operations (list), author, message, mode

### 3. Create BatchGraphsCommandHandler
Create `src/main/java/org/chucc/vcserver/command/BatchGraphsCommandHandler.java`:
- Validate all operations upfront
- For mode="single":
  - Execute all operations
  - Combine all patches into single RDF Patch
  - Check for no-op (combined patch is empty) → return NoOpResult
  - Create single commit
- For mode="multiple":
  - Execute operations sequentially
  - Create separate commit for each non-empty operation
- Return list of created commits

### 4. Create BatchGraphsController
Create `src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java`:
- `@PostMapping("/version/batch-graphs")`
- Accept BatchGraphsRequest JSON
- Resolve branch selector
- Invoke BatchGraphsCommandHandler
- Return 200 with commit IDs (or 204 for no-op)
- Return 400 for validation errors
- Return 409 for conflicts

Response format:
```json
{
  "commits": [
    {
      "id": "01936b2e...",
      "operations": ["PUT http://ex.org/g1", "PATCH http://ex.org/g2"]
    }
  ]
}
```

### 5. Write Unit Tests
- Test batch validation
- Test mode="single" creates one commit
- Test mode="multiple" creates multiple commits
- Test no-op detection (entire batch is no-op)

### 6. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/BatchGraphsIntegrationTest.java`:
- Test batch with multiple PUTs (single commit)
- Test batch with mixed operations (PUT, POST, DELETE)
- Test batch no-op returns 204
- Test batch conflict detection
- Test mode="single" vs mode="multiple"

## Acceptance Criteria
- [ ] /version/batch-graphs endpoint accepts batch requests
- [ ] mode="single" creates one commit for all operations
- [ ] mode="multiple" creates separate commits
- [ ] No-op detection works for entire batch
- [ ] Conflict detection works
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 12 (ETag preconditions)

## Estimated Complexity
Medium-High (6-8 hours)
