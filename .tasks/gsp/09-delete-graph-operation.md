# Task 09: DELETE Graph Operation

## Objective
Implement DELETE operation to remove entire graph.

## Background
`DELETE /data?graph={iri}&branch={name}` removes all triples from the named graph. Creates commit with DELETE operations for all quads in graph.

## Tasks

### 1. Define DeleteGraphCommand
Create `src/main/java/org/chucc/vcserver/command/DeleteGraphCommand.java`:
- Fields: dataset, graphIri, isDefaultGraph, branch, baseCommit, author, message, ifMatchETag

### 2. Create DeleteGraphCommandHandler
Create `src/main/java/org/chucc/vcserver/command/DeleteGraphCommandHandler.java`:
- Validate precondition (If-Match)
- Get current graph state
- Check if graph is empty or non-existent → return NoOpResult or 404
- Generate DELETE operations for all quads in graph
- Create RDF Patch
- Publish CommitCreatedEvent
- Return event

### 3. Extend GraphDiffService
Update `GraphDiffService`:
- Add method: `RdfPatch computeDeleteDiff(Model currentGraph, String graphIri)`
  - Generates DELETE operations for all quads
  - Returns RdfPatch

### 4. Update GraphStoreController.deleteGraph()
- Validate parameters
- Resolve selector
- Invoke DeleteGraphCommandHandler
- Return 204 No Content (per HTTP spec for DELETE)
- Add ETag and Location headers
- Handle 404 if graph doesn't exist
- Handle 412 if precondition fails

### 5. Write Unit Tests
- `DeleteGraphCommandHandlerTest`
- Test DELETE on empty graph (no-op)
- Test DELETE on non-existent graph (404)

### 6. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStoreDeleteIntegrationTest.java`:

**API Layer Tests**:
- Test DELETE removes graph (verify 204 response)
- Test DELETE non-existent graph returns 404
- Test DELETE empty graph returns 204 (no-op)
- Test DELETE with If-Match precondition

**Full System Tests**:
- Test DELETE eventually removes from repository (use await())
- Test GET after DELETE returns 404

## Acceptance Criteria
- [ ] DELETE removes all graph triples
- [ ] 404 returned when graph doesn't exist
- [ ] No-op detection works for empty graphs
- [ ] If-Match precondition supported
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 08 (POST operation)

## Estimated Complexity
Low-Medium (3-4 hours)
