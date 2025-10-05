# Task 12: Implement ETag and Conditional Request Support

## Priority
🟡 **MEDIUM** - Important for concurrency control

## Dependencies
- Task 01 (error handling)
- Task 05 (POST /version/commits)

## Protocol Reference
- Section 6: Concurrency, ETag, and Conditional Requests (Option A)

## Context
The protocol uses ETags and If-Match headers for optimistic concurrency control. This provides early failure detection before semantic conflict analysis.

## Current State
- Some responses include ETag headers
- No If-Match precondition checking

## Target State
- All read responses include strong ETags
- Write operations support If-Match preconditions
- 412 Precondition Failed on ETag mismatch
- Semantic conflict detection still required (ETags are advisory)

## ETag Semantics (§6)

### For Branch Resources
- **Strong ETag**: current HEAD commit ID
- Format: `ETag: "01936c7f-8a2e-7890-abcd-ef1234567890"`
- Changes whenever branch HEAD changes

### For Commit Resources
- **Strong ETag**: commit ID (immutable)
- Format: `ETag: "01936c7f-8a2e-7890-abcd-ef1234567890"`
- Never changes (commits are immutable)

### Conditional Requests
- Client sends: `If-Match: "expectedCommitId"`
- Server checks: does branch HEAD still equal expectedCommitId?
- If no: return 412 Precondition Failed
- If yes: continue with operation (still may get 409 later)

## Implementation Tasks

### 1. Create ETag Utility
**File**: `src/main/java/org/chucc/vcserver/util/ETagUtil.java`
```java
public class ETagUtil {

    /**
     * Create strong ETag from commit ID.
     */
    public static String createStrongETag(String commitId) {
        return "\"" + commitId + "\"";
    }

    /**
     * Parse commit ID from ETag value.
     */
    public static String parseETag(String etag) {
        if (etag == null) {
            return null;
        }
        // Remove quotes if present
        return etag.replaceAll("^\"|\"$", "");
    }

    /**
     * Check if ETag matches expected value.
     */
    public static boolean matches(String etag, String expectedCommitId) {
        String parsed = parseETag(etag);
        return parsed != null && parsed.equals(expectedCommitId);
    }
}
```

### 2. Create Precondition Failed Exception
**File**: `src/main/java/org/chucc/vcserver/exception/PreconditionFailedException.java`
```java
public class PreconditionFailedException extends VcException {
    private final String expected;
    private final String actual;

    public PreconditionFailedException(String expected, String actual) {
        super("precondition_failed", 412,
              "If-Match precondition failed: expected " + expected + ", actual " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    // getters
}
```

### 3. Create Precondition Checker Service
**File**: `src/main/java/org/chucc/vcserver/service/PreconditionService.java`
```java
@Service
public class PreconditionService {

    @Autowired
    private BranchRepository branchRepository;

    /**
     * Check If-Match precondition for branch write operations.
     * Returns silently if check passes.
     * Throws PreconditionFailedException if check fails.
     */
    public void checkIfMatch(String branchName, String ifMatchHeader) {
        if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
            // No precondition specified - allow operation
            return;
        }

        Branch branch = branchRepository.findByName(branchName)
            .orElseThrow(() -> new BranchNotFoundException(branchName));

        String currentHead = branch.getHead().toString();
        String expectedHead = ETagUtil.parseETag(ifMatchHeader);

        if (!currentHead.equals(expectedHead)) {
            throw new PreconditionFailedException(expectedHead, currentHead);
        }
    }
}
```

### 4. Update Controllers to Add ETag Headers

**File**: `src/main/java/org/chucc/vcserver/controller/CommitController.java` (update)
```java
// GET /version/commits/{id}
public ResponseEntity<CommitResponse> getCommit(@PathVariable String id) {
    Commit commit = commitRepository.findById(new CommitId(id))
        .orElseThrow(() -> new CommitNotFoundException(id));

    CommitResponse response = mapToResponse(commit);

    return ResponseEntity.ok()
        .eTag(ETagUtil.createStrongETag(id))
        .body(response);
}

// POST /version/commits (update from Task 05)
public ResponseEntity<CommitResponse> createCommit(
    // ... parameters including:
    @RequestHeader(name = "If-Match", required = false) String ifMatch
) {
    // Check precondition if branch selector used
    if (branch != null && ifMatch != null) {
        preconditionService.checkIfMatch(branch, ifMatch);
    }

    // ... rest of implementation
    // Return with ETag header
    return ResponseEntity.created(location)
        .eTag(ETagUtil.createStrongETag(newCommitId))
        .body(response);
}
```

**File**: `src/main/java/org/chucc/vcserver/controller/BranchController.java` (update)
```java
// GET /version/branches/{name}
public ResponseEntity<BranchResponse> getBranch(@PathVariable String name) {
    Branch branch = branchRepository.findByName(name)
        .orElseThrow(() -> new BranchNotFoundException(name));

    BranchResponse response = mapToResponse(branch);

    return ResponseEntity.ok()
        .eTag(ETagUtil.createStrongETag(branch.getHead().toString()))
        .body(response);
}
```

**File**: `src/main/java/org/chucc/vcserver/controller/MergeController.java` (update)
```java
// POST /version/merge
public ResponseEntity<MergeResponse> mergeBranches(
    @RequestBody MergeRequest request,
    @RequestHeader(name = "If-Match", required = false) String ifMatch
) {
    // Check precondition on target branch
    if (ifMatch != null) {
        preconditionService.checkIfMatch(request.getInto(), ifMatch);
    }

    // ... rest of implementation
}
```

### 5. Update SparqlController
**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java` (update)

```java
// POST (updates)
public ResponseEntity<String> executeSparqlPost(
    // ... existing parameters, add:
    @RequestHeader(name = "If-Match", required = false) String ifMatch
) {
    // If this is an update operation and branch selector is used
    if (isUpdate && branchSelector != null && ifMatch != null) {
        preconditionService.checkIfMatch(branchSelector, ifMatch);
    }

    // ... rest of implementation
}

// GET (queries) - add ETag to response
public ResponseEntity<String> querySparqlGet(...) {
    // Execute query
    // Determine which commit was queried
    String queriedCommitId = resolveCommitId(branch, commit, asOf);

    // Return with ETag
    return ResponseEntity.ok()
        .eTag(ETagUtil.createStrongETag(queriedCommitId))
        .body(results);
}
```

### 6. Document Advisory Nature
Add to API documentation: ETags provide early failure detection but servers MUST still perform semantic overlap detection per §6.

## Request/Response Examples

### Read with ETag
```http
GET /version/branches/main HTTP/1.1

HTTP/1.1 200 OK
ETag: "01936c7f-8a2e-7890-abcd-ef1234567890"
Content-Type: application/json

{
  "name": "main",
  "head": "01936c7f-8a2e-7890-abcd-ef1234567890"
}
```

### Conditional Write (Success)
```http
POST /version/commits?branch=main HTTP/1.1
Content-Type: text/rdf-patch
If-Match: "01936c7f-8a2e-7890-abcd-ef1234567890"

TX .
A <http://example.org/s> <http://example.org/p> "value" .
TC .

HTTP/1.1 201 Created
Location: /version/commits/01936c81-1111-7890-abcd-ef1234567890
ETag: "01936c81-1111-7890-abcd-ef1234567890"
```

### Precondition Failed
```http
POST /version/commits?branch=main HTTP/1.1
Content-Type: text/rdf-patch
If-Match: "01936c7f-8a2e-7890-abcd-ef1234567890"

...

HTTP/1.1 412 Precondition Failed
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Precondition Failed",
  "status": 412,
  "code": "precondition_failed",
  "detail": "If-Match precondition failed: expected 01936c7f-8a2e-7890-abcd-ef1234567890, actual 01936c81-2222-7890-abcd-ef1234567890"
}
```

## Acceptance Criteria
- [ ] GET /version/branches/{name} returns ETag with HEAD commit
- [ ] GET /version/commits/{id} returns ETag with commit ID
- [ ] POST /version/commits supports If-Match precondition
- [ ] POST /version/merge supports If-Match precondition
- [ ] SPARQL UPDATE supports If-Match precondition
- [ ] SPARQL QUERY returns ETag with queried commit
- [ ] 412 Precondition Failed on mismatch
- [ ] Error includes expected and actual values
- [ ] Semantic conflict detection still performed after precondition check

## Test Requirements

### Unit Tests
1. `ETagUtilTest.java`
   - Test createStrongETag
   - Test parseETag with/without quotes
   - Test matches()

2. `PreconditionServiceTest.java`
   - Test successful precondition check
   - Test failed precondition → exception
   - Test null If-Match → no check

### Integration Tests
1. `ETagIntegrationTest.java`
   - GET branch, save ETag
   - POST commit with If-Match using saved ETag
   - Verify success
   - Concurrent update from another client
   - POST commit with stale If-Match
   - Verify 412 Precondition Failed

2. `ConcurrencyControlIntegrationTest.java`
   - Test race condition between two clients
   - One uses If-Match, gets 412
   - One doesn't use If-Match, gets 409 (semantic conflict)

## Files to Create
- `src/main/java/org/chucc/vcserver/util/ETagUtil.java`
- `src/main/java/org/chucc/vcserver/service/PreconditionService.java`
- `src/main/java/org/chucc/vcserver/exception/PreconditionFailedException.java`
- `src/test/java/org/chucc/vcserver/util/ETagUtilTest.java`
- `src/test/java/org/chucc/vcserver/service/PreconditionServiceTest.java`
- `src/test/java/org/chucc/vcserver/integration/ETagIntegrationTest.java`
- `src/test/java/org/chucc/vcserver/integration/ConcurrencyControlIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`
- `src/main/java/org/chucc/vcserver/controller/BranchController.java`
- `src/main/java/org/chucc/vcserver/controller/MergeController.java`
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
- `src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium** - Straightforward HTTP headers, but touches many controllers

## Notes
- ETags are **advisory** - semantic conflict detection is still required
- Strong ETags only (no weak ETags for version control)
- 412 fails fast, before applying patches (efficiency)
- 409 still possible after 412 check passes (race conditions)
- Consider adding If-None-Match support for GET operations (future enhancement)
