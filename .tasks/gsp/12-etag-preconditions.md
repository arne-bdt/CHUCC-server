# Task 12: ETag and If-Match Precondition Support

## Objective
Implement full ETag support and If-Match precondition checking for graph operations.

## Background
- GET/HEAD return `ETag` header with last-modifying commit ID for the graph
- PUT/POST/PATCH/DELETE accept `If-Match` header for optimistic concurrency
- 412 Precondition Failed returned on ETag mismatch (advisory, semantic conflict detection still runs)

## Tasks

### 1. Implement Graph-Level ETag Computation
Create or update service:
- Method: `String computeGraphETag(CommitId commit, String graphIri)`
  - Returns commit ID where this graph was last modified
  - Walks commit history backwards to find last modification
  - Caches result for performance

Update `DatasetService` or create `GraphHistoryService`:
- Add method to find last commit that modified a specific graph
- Use RDF Patch inspection to determine which graphs each commit modified

### 2. Update GET/HEAD to Return Graph-Level ETag
Update GraphStoreController.getGraph() and headGraph():
- Compute graph-level ETag (not just branch head)
- Return ETag header: `ETag: "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"`
- Use strong ETag (quoted)

### 3. Implement If-Match Precondition Checking
Update PreconditionService or create GraphPreconditionService:
- Method: `void checkIfMatch(String ifMatchHeader, String graphIri, String branch)`
  - Resolve current graph ETag
  - Compare with If-Match header
  - Throw PreconditionFailedException if mismatch
- Integrate into all write command handlers (PUT, POST, PATCH, DELETE)

### 4. Return 412 on Precondition Failure
Update exception handler:
- Map PreconditionFailedException → 412 response
- Return problem+json with code "precondition_failed"

### 5. Write Unit Tests
- Test ETag computation for graph
- Test If-Match validation (match vs mismatch)
- Test ETag reflects last modification, not just branch head

### 6. Write Integration Tests
Update integration tests:
- Test GET returns correct graph-level ETag
- Test PUT with matching If-Match succeeds
- Test PUT with mismatched If-Match returns 412
- Test 412 returned before 409 (precondition checked first)
- Test HEAD returns same ETag as GET

## Acceptance Criteria
- [ ] GET/HEAD return graph-level ETag (last modifying commit)
- [ ] If-Match precondition validated for writes
- [ ] 412 returned on ETag mismatch
- [ ] Semantic conflict detection still runs (412 is advisory)
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 11 (conflict detection)

## Estimated Complexity
Medium (5-6 hours)
