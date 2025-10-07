# Task 08: POST Graph Operation (Merge/Add)

## Objective
Implement POST operation for additive merge of RDF content into a graph.

## Background
`POST /data?graph={iri}&branch={name}` merges RDF content into existing graph (additive only). Creates commit with ADD operations only (no deletes).

## Tasks

### 1. Define PostGraphCommand
Create `src/main/java/org/chucc/vcserver/command/PostGraphCommand.java`:
- Similar to PutGraphCommand but for additive merge
- Fields: dataset, graphIri, isDefaultGraph, branch, baseCommit, rdfContent, contentType, author, message, ifMatchETag

### 2. Create PostGraphCommandHandler
Create `src/main/java/org/chucc/vcserver/command/PostGraphCommandHandler.java`:
- Validate precondition (If-Match)
- Get current graph state
- Parse new RDF content
- Compute additive diff (only ADD operations for quads not already present)
- Check for no-op (all triples already present) → return NoOpResult
- Generate commit ID
- Create RDF Patch with ADD operations
- Publish CommitCreatedEvent
- Return event

### 3. Extend GraphDiffService
Update `GraphDiffService`:
- Add method: `RdfPatch computePostDiff(Model currentGraph, Model newContent, String graphIri)`
  - Computes diff: newContent MINUS currentGraph (only adds)
  - Returns RdfPatch with ADD operations only

### 4. Update GraphStoreController.postGraph()
- Similar to PUT but invoke PostGraphCommandHandler
- Handle multipart/form-data for multiple document uploads (optional for v1, can defer)

### 5. Write Unit Tests
- `PostGraphCommandHandlerTest`
- `GraphDiffServiceTest` (test POST diff computation)

### 6. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStorePostIntegrationTest.java`:

**API Layer Tests**:
- Test POST adds triples to existing graph (verify response)
- Test POST creates new graph if not exists (verify 201 vs 200)
- Test POST with all triples already present returns 204 (no-op)
- Test POST with various RDF formats
- Test POST with If-Match precondition

**Full System Tests**:
- Test POST eventually updates repository (use await())
- Test GET after POST shows merged content

## Acceptance Criteria
- [ ] POST endpoint merges RDF additively
- [ ] No-op detection works (204 when all triples already present)
- [ ] POST respects If-Match precondition
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 07 (PUT controller integration)

## Estimated Complexity
Medium (4-5 hours)
