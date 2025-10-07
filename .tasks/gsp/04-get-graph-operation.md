# Task 04: GET Graph Operation (Read-Only)

## Objective
Implement GET operation to retrieve graph content with version control selector support.

## Background
`GET /data?graph={iri}` or `GET /data?default=true` retrieves the graph at the selected version (branch/commit/asOf). Returns RDF in requested format (Turtle, N-Triples, JSON-LD, etc.).

## Tasks

### 1. Extend DatasetService for Graph Extraction
Update `src/main/java/org/chucc/vcserver/service/DatasetService.java`:
- Add method: `Model getGraph(CommitId commit, String graphIri)`
  - Materializes dataset at commit
  - Extracts named graph or default graph
  - Returns Apache Jena Model
- Add method: `Model getDefaultGraph(CommitId commit)`
  - Extracts default graph

### 2. Create GraphSerializationService
Create `src/main/java/org/chucc/vcserver/service/GraphSerializationService.java`:
- Method: `String serializeGraph(Model graph, String acceptHeader)`
  - Supports Turtle, N-Triples, JSON-LD, RDF/XML, N-Quads
  - Content negotiation based on Accept header
  - Throws 406 Not Acceptable if format unsupported

### 3. Implement GET in GraphStoreController
Update `getGraph()` method:
- Validate graph/default parameters
- Validate selector parameters (branch/commit/asOf)
- Resolve selector to CommitId via SelectorResolutionService
- Extract graph via DatasetService
- Return 404 if graph doesn't exist at that commit
- Serialize graph via GraphSerializationService
- Add `ETag` header with commit ID
- Add `SPARQL-Version-Control: true` header

### 4. Write Unit Tests
Create `src/test/java/org/chucc/vcserver/service/GraphSerializationServiceTest.java`:
- Test serialization to Turtle
- Test serialization to N-Triples
- Test serialization to JSON-LD
- Test 406 for unsupported format

### 5. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStoreGetIntegrationTest.java`:
- Test GET named graph at branch head
- Test GET default graph at branch head
- Test GET graph at specific commit
- Test GET graph with asOf selector
- Test 404 when graph doesn't exist
- Test 406 for unsupported Accept header
- Test ETag header matches commit ID

## Acceptance Criteria
- [ ] GET returns graph content in requested format
- [ ] Selector resolution works (branch/commit/asOf)
- [ ] ETag header contains commit ID
- [ ] 404 returned when graph not found
- [ ] Content negotiation works for standard RDF formats
- [ ] All unit and integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 03 (controller skeleton)

## Estimated Complexity
Medium (4-6 hours)
