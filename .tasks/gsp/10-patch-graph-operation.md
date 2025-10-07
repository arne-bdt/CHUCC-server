# Task 10: PATCH Graph Operation (RDF Patch)

## Objective
Implement PATCH operation for applying RDF Patch directly to a graph.

## Background
`PATCH /data?graph={iri}&branch={name}` with `Content-Type: text/rdf-patch` applies an RDF Patch to the graph. This is more efficient than PUT/POST for incremental updates.

## Tasks

### 1. Define PatchGraphCommand
Create `src/main/java/org/chucc/vcserver/command/PatchGraphCommand.java`:
- Fields: dataset, graphIri, isDefaultGraph, branch, baseCommit, patchContent (String), author, message, ifMatchETag

### 2. Create PatchGraphCommandHandler
Create `src/main/java/org/chucc/vcserver/command/PatchGraphCommandHandler.java`:
- Validate precondition (If-Match)
- Parse RDF Patch from request body
- Validate patch syntax (return 400 if malformed)
- Apply patch to current graph state to verify it's applicable
- Check for no-op (patch applies but produces no change) → return NoOpResult
- Filter patch operations to target graph only (important!)
- Generate commit ID
- Publish CommitCreatedEvent with filtered patch
- Return event

### 3. Create RdfPatchService
Create `src/main/java/org/chucc/vcserver/service/RdfPatchService.java`:
- Method: `RdfPatch parsePatch(String patchText)`
  - Parses text/rdf-patch format
  - Throws 400 on syntax errors
- Method: `RdfPatch filterByGraph(RdfPatch patch, String graphIri)`
  - Filters patch to include only operations on target graph
  - Returns filtered patch
- Method: `boolean canApply(Model graph, RdfPatch patch)`
  - Validates patch can be applied (e.g., DELETE of non-existent quad fails)
  - Returns true if applicable
- Method: `Model applyPatch(Model graph, RdfPatch patch)`
  - Applies patch to model
  - Returns new model

### 4. Update GraphStoreController.patchGraph()
- Accept Content-Type: text/rdf-patch (or optional binary variant)
- Return 415 if Content-Type not supported
- Invoke PatchGraphCommandHandler
- Return 200 with ETag and Location on success
- Return 204 for no-op
- Return 422 if patch cannot be applied (semantic error)

### 5. Write Unit Tests
- `PatchGraphCommandHandlerTest`
- `RdfPatchServiceTest` (test parsing, filtering, applying)
- Test patch syntax errors (400)
- Test patch semantic errors (422)

### 6. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStorePatchIntegrationTest.java`:

**API Layer Tests**:
- Test PATCH applies RDF Patch (verify response)
- Test PATCH with malformed syntax returns 400
- Test PATCH that can't be applied returns 422
- Test PATCH with no-op returns 204
- Test PATCH with unsupported Content-Type returns 415

**Full System Tests**:
- Test PATCH eventually updates repository
- Test GET after PATCH shows updated content

## Acceptance Criteria
- [ ] PATCH applies RDF Patch to graph
- [ ] Patch filtering by graph works correctly
- [ ] Syntax errors return 400
- [ ] Semantic errors (unapplicable patch) return 422
- [ ] No-op detection works
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 09 (DELETE operation)

## Estimated Complexity
Medium-High (6-7 hours)
