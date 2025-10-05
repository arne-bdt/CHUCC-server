# Task 05: Implement POST /version/commits Endpoint

## Priority
🔴 **HIGH** - Core write operation

## Dependencies
- Task 01 (error handling)
- Task 02 (selector validation)

## Protocol Reference
- Section 3.2: Refs and Commits
- Section 7: Changesets

## Context
The primary way to create commits is by POSTing RDF Patch to /version/commits with a target ref selector. This is distinct from SPARQL UPDATE (which operates on the SPARQL protocol surface).

## Current State
No POST /version/commits endpoint exists. Commits can only be created via SPARQL UPDATE.

## Target State
- POST /version/commits with RDF Patch body
- Selector: `branch` (required) OR `commit` (detached commit)
- `asOf` allowed only with `branch` to select base state
- Returns 201 Created with commit metadata
- Returns 204 No Content for no-op patches (§7)

## Implementation Tasks

### 1. Create Request/Response DTOs
**File**: `src/main/java/org/chucc/vcserver/dto/CreateCommitRequest.java`
```java
public class CreateCommitRequest {
    private String patch;     // RDF Patch content
    private String branch;    // Target branch
    private String commit;    // Or detached commit base
    private String asOf;      // Optional: base state timestamp
    private String author;    // From header
    private String message;   // From header

    // validation, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/CommitResponse.java`
```java
public class CommitResponse {
    private String id;           // UUIDv7
    private String[] parents;    // Parent commit IDs
    private String author;
    private String message;
    private String timestamp;    // RFC 3339

    // constructors, getters, setters
}
```

### 2. Update CommitController
**File**: `src/main/java/org/chucc/vcserver/controller/CommitController.java`

Add POST endpoint:
```java
@PostMapping(
    consumes = "text/rdf-patch",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<CommitResponse> createCommit(
    @RequestBody String patchBody,
    @RequestParam(required = false) String branch,
    @RequestParam(required = false) String commit,
    @RequestParam(required = false) String asOf,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
    @RequestHeader(name = "If-Match", required = false) String ifMatch
) {
    // Validate selectors (Task 02)
    // Apply patch via CommandHandler
    // Return 201 with Location header
    // OR 204 if no-op patch
}
```

### 3. Enhance CreateCommitCommandHandler
**File**: `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`

Add logic to:
- Accept patch as string
- Validate patch syntax
- Detect no-op patches (applies cleanly but no dataset change)
- Handle asOf with branch selector
- Return commit metadata or null (no-op)

### 4. Add No-Op Detection
Per §7: A no-op patch applies cleanly but yields no dataset change → 204 No Content, MUST NOT create commit.

```java
public boolean isNoOpPatch(RDFPatch patch, Dataset baseDataset) {
    // Apply patch to temporary dataset
    // Compare before/after
    // Return true if identical
}
```

## Request Examples

### Create commit on branch
```http
POST /version/commits?branch=main HTTP/1.1
Content-Type: text/rdf-patch
SPARQL-VC-Author: Alice <mailto:alice@example.org>
SPARQL-VC-Message: Add new triple

TX .
A <http://example.org/s> <http://example.org/p> "value" .
TC .
```

### Create detached commit
```http
POST /version/commits?commit=01936c7f-8a2e-7890-abcd-ef1234567890 HTTP/1.1
Content-Type: text/rdf-patch
SPARQL-VC-Author: Bob
SPARQL-VC-Message: Experimental change

TX .
A <http://example.org/s2> <http://example.org/p2> "value2" .
TC .
```

### With asOf selector
```http
POST /version/commits?branch=main&asOf=2025-01-01T00:00:00Z HTTP/1.1
Content-Type: text/rdf-patch
...
```

## Response Examples

### Success (201 Created)
```http
HTTP/1.1 201 Created
Location: /version/commits/01936c81-4567-7890-abcd-ef1234567890
ETag: "01936c81-4567-7890-abcd-ef1234567890"
Content-Type: application/json

{
  "id": "01936c81-4567-7890-abcd-ef1234567890",
  "parents": ["01936c7f-8a2e-7890-abcd-ef1234567890"],
  "author": "Alice <mailto:alice@example.org>",
  "message": "Add new triple",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### No-op patch (204)
```http
HTTP/1.1 204 No Content
```

## Acceptance Criteria
- [ ] POST /version/commits with branch selector creates commit
- [ ] POST /version/commits with commit selector creates detached commit
- [ ] asOf + branch selector works correctly
- [ ] Selector validation enforced (Task 02)
- [ ] No-op patch returns 204 No Content without creating commit
- [ ] 201 response includes Location and ETag headers
- [ ] Response body contains commit metadata
- [ ] Invalid patch syntax returns 400 Bad Request
- [ ] Content-Type text/rdf-patch required (415 otherwise)

## Test Requirements

### Unit Tests
1. `CreateCommitCommandHandlerTest.java` (update existing)
   - Test no-op patch detection
   - Test asOf + branch selector
   - Test detached commit creation

2. `CommitControllerTest.java` (update existing)
   - Mock command handler
   - Test all response scenarios

### Integration Tests
1. `CommitCreationIntegrationTest.java`
   - Create initial commit on branch
   - Apply patch, verify commit created
   - Apply no-op patch, verify 204 response
   - Verify branch HEAD updated
   - Create detached commit, verify not on any branch
   - Test asOf selector with historical base state

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/CreateCommitRequest.java`
- `src/main/java/org/chucc/vcserver/dto/CommitResponse.java`
- `src/test/java/org/chucc/vcserver/integration/CommitCreationIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`
- `src/test/java/org/chucc/vcserver/controller/CommitControllerTest.java`
- `src/test/java/org/chucc/vcserver/command/CreateCommitCommandHandlerTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**High** - Core functionality with multiple edge cases

## Notes
- This is a critical endpoint for the protocol
- No-op detection is important to avoid polluting history
- asOf + branch enables "create commit based on historical state"
- Consider supporting binary RDF Patch format (application/vnd.apache.jena.rdfpatch+thrift) in future
- If-Match header support will be added in Task 12 (ETag concurrency)
