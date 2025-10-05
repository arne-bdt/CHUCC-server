# Task 03: Fix Request Header Names

## Priority
🟡 **MEDIUM** - Compatibility with spec

## Dependencies
None - simple rename

## Protocol Reference
- Section 5: Headers

## Context
Current implementation uses non-standard header names. The protocol specifies exact header names that are normative and case-sensitive.

## Current State
```java
// SparqlController.java line 184, 186, 188
@RequestHeader(name = "SPARQL-VC-Branch", required = false) String vcBranch
@RequestHeader(name = "SPARQL-VC-Commit-Message", required = false) String commitMessage
@RequestHeader(name = "SPARQL-VC-Commit-Author", required = false) String commitAuthor
```

## Target State (per §5)

### Request Headers (writes)
- ✅ `SPARQL-VC-Author` (SHOULD) - display name or URI
- ✅ `SPARQL-VC-Message` (SHOULD) - commit message

### Request Headers (reads)
- ✅ `SPARQL-VC-Commit` (MUST be supported) - commit ID for read operations

### Response Headers (discovery/status)
- ✅ `SPARQL-Version-Control: true`
- ✅ `ETag: "<id>"`

## Changes Required

### Remove Header
- ❌ `SPARQL-VC-Branch` - selectors use URL parameters per §4, not headers

### Rename Headers
- `SPARQL-VC-Commit-Message` → `SPARQL-VC-Message`
- `SPARQL-VC-Commit-Author` → `SPARQL-VC-Author`

### Add New Header Support
- `SPARQL-VC-Commit` for read requests (GET operations)

## Implementation Tasks

### 1. Update SparqlController
**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java`

**Line 184-189** - Update executeSparqlPost parameters:
```java
// BEFORE
@RequestHeader(name = "SPARQL-VC-Branch", required = false) String vcBranch,
@RequestHeader(name = "SPARQL-VC-Commit-Message", required = false) String commitMessage,
@RequestHeader(name = "SPARQL-VC-Commit-Author", required = false) String commitAuthor,

// AFTER
@RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
@RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
```

**Line 86-98** - Add SPARQL-VC-Commit header support to querySparqlGet:
```java
// ADD new parameter
@Parameter(description = "Commit ID for read consistency")
@RequestHeader(name = "SPARQL-VC-Commit", required = false) String vcCommit,
```

Note: `branch` selector should already be a query parameter (line 94).

### 2. Update CommandHandler/Command Classes
If any command classes reference the old header names, update them:
- Check `CreateCommitCommand` for author/message fields
- Update any DTOs that map these headers

### 3. Update Documentation
**File**: Update API documentation/Swagger annotations to reflect:
- Write operations: SPARQL-VC-Author, SPARQL-VC-Message (SHOULD)
- Read operations: SPARQL-VC-Commit (servers MUST support, clients MAY ignore)

## Acceptance Criteria
- [ ] `SPARQL-VC-Message` header accepted for commit message
- [ ] `SPARQL-VC-Author` header accepted for commit author
- [ ] `SPARQL-VC-Commit` header accepted for read operations
- [ ] `SPARQL-VC-Branch` header removed (use query param instead)
- [ ] Swagger docs updated with correct header names
- [ ] All references to old names removed

## Test Requirements

### Unit Tests
1. `SparqlControllerTest.java` - Update existing tests
   - Test POST with SPARQL-VC-Message header
   - Test POST with SPARQL-VC-Author header
   - Test GET with SPARQL-VC-Commit header
   - Verify old header names are NOT accepted

### Integration Tests
1. `SparqlHeaderIntegrationTest.java`
   - POST update with headers
   - Verify commit created with correct author/message
   - GET query with SPARQL-VC-Commit header
   - Verify query executed against specified commit

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
- `src/test/java/org/chucc/vcserver/controller/SparqlControllerTest.java`
- Any command/DTO classes that reference these headers

## Files to Check
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommand.java`
- Any other command handlers that process author/message

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low** - Simple rename, no logic changes

## Notes
- This is a breaking change for any existing clients
- Consider supporting both old and new names temporarily with deprecation warning
- The removal of SPARQL-VC-Branch is correct: selectors belong in URL params per §4
- SPARQL-VC-Commit header for reads allows clients to verify which commit was actually queried (important for distributed systems)
