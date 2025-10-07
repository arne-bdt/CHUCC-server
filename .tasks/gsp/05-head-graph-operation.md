# Task 05: HEAD Graph Operation

## Objective
Implement HEAD operation to retrieve graph metadata without body.

## Background
`HEAD /data?graph={iri}` returns same headers as GET but no body. Used for checking graph existence and retrieving ETag.

## Tasks

### 1. Implement HEAD in GraphStoreController
Update `headGraph()` method:
- Reuse GET logic for parameter validation and selector resolution
- Check graph existence via DatasetService
- Return 404 if graph doesn't exist
- Return 200 with same headers as GET (ETag, Content-Type, SPARQL-Version-Control)
- **No body** in response

### 2. Refactor Common Logic
Extract common logic from GET and HEAD:
- Create private method `resolveGraphSelector(...)` for parameter validation + selector resolution
- Create private method `buildResponseHeaders(CommitId commit, String contentType)` for header construction

### 3. Write Integration Tests
Update `GraphStoreGetIntegrationTest.java` or create `GraphStoreHeadIntegrationTest.java`:
- Test HEAD returns same headers as GET
- Test HEAD returns no body
- Test HEAD with branch selector
- Test HEAD with commit selector
- Test HEAD returns 404 for non-existent graph
- Test ETag header matches commit ID

## Acceptance Criteria
- [ ] HEAD returns correct headers (ETag, Content-Type, etc.)
- [ ] HEAD returns no body
- [ ] HEAD reuses GET logic (DRY principle)
- [ ] All integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 04 (GET operation)

## Estimated Complexity
Low (2-3 hours)
