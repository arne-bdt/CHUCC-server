# Task 07: PUT Graph Operation - Controller Integration

## Objective
Wire PUT command handler to GraphStoreController and implement HTTP endpoint.

## Background
This task connects the PUT command infrastructure (Task 06) to the REST API.

## Tasks

### 1. Update GraphStoreController.putGraph()
- Parse and validate parameters (graph/default, branch, commit, asOf)
- Resolve selector to base CommitId via SelectorResolutionService
- Read request body
- Extract headers (If-Match, SPARQL-VC-Author, SPARQL-VC-Message)
- Create PutGraphCommand
- Invoke PutGraphCommandHandler
- Handle NoOpResult → return 204 No Content
- Handle CommitCreatedEvent → return 200 OK with:
  - `ETag: "<commitId>"`
  - `Location: /version/commits/{commitId}`
  - `SPARQL-Version-Control: true`
- Handle precondition failure → return 412 Precondition Failed
- Handle conflict → return 409 Conflict with problem+json

### 2. Add Request Body Reading
- Support Content-Type: text/turtle, application/n-triples, application/ld+json, application/rdf+xml
- Return 415 if Content-Type unsupported
- Return 400 if request body is malformed

### 3. Write Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStorePutIntegrationTest.java`:

**API Layer Tests** (test synchronous response):
- Test PUT creates graph on branch (verify 200, Location, ETag headers)
- Test PUT replaces existing graph (verify response format)
- Test PUT with different RDF formats (Turtle, N-Triples, JSON-LD)
- Test PUT with If-Match precondition (verify 412 on mismatch)
- Test PUT with invalid RDF (verify 400)
- Test PUT with unsupported media type (verify 415)
- Test PUT with selector validation errors (verify 400)

**Full System Tests** (test async event processing):
- Test PUT eventually updates repository (use await())
- Test GET after PUT returns new content (use await())

### 4. Update OpenAPI Documentation
- Add examples for PUT request/response
- Document all Content-Type options
- Document ETag and Location response headers

## Acceptance Criteria
- [ ] PUT endpoint accepts RDF and creates commits
- [ ] Response includes ETag and Location headers
- [ ] No-op detection returns 204
- [ ] If-Match precondition works (412 on mismatch)
- [ ] Content negotiation works for input formats
- [ ] All integration tests pass (API layer + full system)
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 06 (PUT command and event)

## Estimated Complexity
Medium (5-6 hours)
