# Task 15: Clean Up Non-Spec Endpoints

## Priority
🟢 **LOW** - Cleanup after all features implemented

## Dependencies
- All previous tasks (01-14)

## Protocol Reference
- All sections - this task removes endpoints NOT in the spec

## Context
Some endpoints exist in the current implementation that are not part of the official protocol specification. These should be removed or documented as extensions.

## Endpoints to Review

### 1. GET /version/commits/{id}/changes
**Current**: CommitController.java line 69
**Spec**: Not in specification
**Recommendation**: **REMOVE** or mark as extension
- Spec has GET /version/commits/{id} for metadata
- Spec has GET /version/diff for comparing commits
- This endpoint duplicates functionality

### 2. GET /version/diff
**Current**: HistoryController.java line 82
**Spec**: Not in specification
**Recommendation**: **KEEP as extension** but document
- Useful utility for comparing any two commits
- Doesn't conflict with spec
- Add clear documentation that this is an extension

### 3. GET /version/blame
**Current**: HistoryController.java line 116
**Spec**: Not in specification
**Recommendation**: **KEEP as extension** but document
- Useful for attribution
- Enabled/disabled via VersionControlProperties
- Add clear documentation that this is an extension

### 4. GET /version/branches (separate from /version/refs)
**Current**: BranchController.java line 34
**Spec**: Spec has /version/refs (Task 04)
**Recommendation**: **KEEP for convenience**
- Delegates to same underlying service as /version/refs
- Filters to just branches
- Doesn't conflict with spec

### 5. GET /version/tags (separate from /version/refs)
**Current**: TagController.java line 29
**Spec**: Spec has /version/refs (Task 04) and /version/tags for operations
**Recommendation**: **KEEP**
- Spec explicitly mentions /version/tags in §3.5
- This is spec-compliant

## Implementation Tasks

### 1. Remove /version/commits/{id}/changes
**File**: `src/main/java/org/chucc/vcserver/controller/CommitController.java`

**Action**: Remove method getCommitChanges() (lines 69-96)

**Rationale**:
- Not in spec
- Functionality covered by GET /version/diff?from=parent&to={id}
- Reduces API surface

### 2. Document Extensions
**File**: Create `docs/api-extensions.md`

```markdown
# API Extensions

This implementation includes the following extensions beyond the SPARQL 1.2 Protocol Version Control Extension specification:

## GET /version/diff

**Purpose**: Compare two commits and return the changeset between them.

**Request**: `GET /version/diff?from={commitId}&to={commitId}`

**Response**: RDF Patch (text/rdf-patch)

**Status**: Extension (not in official spec)

**Compatibility**: Does not conflict with spec. Optional feature that can be disabled.

---

## GET /version/blame

**Purpose**: Get last-writer attribution for a resource.

**Request**: `GET /version/blame?subject={iri}`

**Response**: JSON with attribution information

**Status**: Extension (not in official spec)

**Compatibility**: Can be enabled/disabled via `vc.blame-enabled` property.

---

## Notes

All extensions:
- Are optional and can be disabled via configuration
- Do not interfere with spec-compliant operations
- Follow the same error format (problem+json) as spec
- Are clearly marked in API documentation
```

### 3. Update API Documentation
**File**: Update OpenAPI/Swagger annotations

For extension endpoints, add note in description:
```java
@Operation(
    summary = "Diff two commits",
    description = "Get changeset between two commits. " +
                  "⚠️ EXTENSION: This endpoint is not part of the official " +
                  "SPARQL 1.2 Protocol specification."
)
```

### 4. Add Configuration to Toggle Extensions
**File**: `src/main/java/org/chucc/vcserver/config/VersionControlProperties.java` (update)

```java
/**
 * Whether to enable diff endpoint (extension).
 */
private boolean diffEnabled = true;

public boolean isDiffEnabled() {
    return diffEnabled && level >= 1;
}

public void setDiffEnabled(boolean diffEnabled) {
    this.diffEnabled = diffEnabled;
}
```

### 5. Conditionally Enable Extension Endpoints
**File**: `src/main/java/org/chucc/vcserver/controller/HistoryController.java` (update)

```java
@GetMapping(value = "/diff", produces = "text/rdf-patch")
public ResponseEntity<String> diffCommits(...) {
    if (!vcProperties.isDiffEnabled()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body("{\"title\":\"Not Found\",\"status\":404," +
                  "\"detail\":\"Diff endpoint is disabled\"}");
    }

    // ... implementation
}
```

### 6. Create Compatibility Matrix Document
**File**: Create `docs/spec-compliance.md`

```markdown
# SPARQL 1.2 Protocol Version Control Extension - Compliance Matrix

## Conformance Level
This implementation supports **Level 2 (Advanced)** conformance.

## Core Endpoints (Level 1)

| Endpoint | Status | Notes |
|----------|--------|-------|
| GET /version/refs | ✅ Implemented | Task 04 |
| GET /version/commits/{id} | ✅ Implemented | Returns metadata |
| POST /version/commits | ✅ Implemented | Task 05 |
| GET /version/history | ✅ Implemented | With filters |
| SPARQL Query with selectors | ✅ Implemented | branch, commit, asOf |
| SPARQL Update with selectors | ✅ Implemented | Creates commits |

## Advanced Operations (Level 2)

| Endpoint | Status | Notes |
|----------|--------|-------|
| POST /version/merge | ✅ Implemented | Three-way merge |
| POST /version/cherry-pick | ✅ Implemented | Task 08 |
| POST /version/revert | ✅ Implemented | Task 09 |
| POST /version/reset | ✅ Implemented | Task 07 |
| POST /version/rebase | ✅ Implemented | Task 10 |
| POST /version/squash | ✅ Implemented | Task 11 |

## Tags

| Endpoint | Status | Notes |
|----------|--------|-------|
| GET /version/tags | ✅ Implemented | List all |
| POST /version/tags | ✅ Implemented | Create |
| GET /version/tags/{name} | ✅ Implemented | Task 06 |
| DELETE /version/tags/{name} | ✅ Implemented | Task 06 |

## Error Handling

| Feature | Status | Notes |
|---------|--------|-------|
| problem+json format | ✅ Implemented | Task 01 |
| Canonical error codes | ✅ Implemented | All codes from §9 |
| Conflict items schema | ✅ Implemented | Required fields |

## Selectors

| Feature | Status | Notes |
|---------|--------|-------|
| branch selector | ✅ Implemented | |
| commit selector | ✅ Implemented | |
| asOf selector | ✅ Implemented | Task 14 |
| Mutual exclusivity | ✅ Implemented | Task 02 |
| asOf + branch allowed | ✅ Implemented | Task 14 |

## Concurrency Control

| Feature | Status | Notes |
|---------|--------|-------|
| ETag headers | ✅ Implemented | Task 12 |
| If-Match preconditions | ✅ Implemented | Task 12 |
| Semantic conflict detection | ✅ Implemented | Via PatchIntersection |

## Extensions (Not in Spec)

| Endpoint | Status | Configurable |
|----------|--------|--------------|
| GET /version/diff | ⚠️ Extension | Yes |
| GET /version/blame | ⚠️ Extension | Yes |

## Notes

- All spec-required features are implemented
- Extensions can be disabled via configuration
- Full compatibility with Graph Store Protocol Version Control Extension
```

## Acceptance Criteria
- [ ] GET /version/commits/{id}/changes removed
- [ ] Extension endpoints clearly documented
- [ ] Extensions can be disabled via configuration
- [ ] Spec compliance matrix created
- [ ] API documentation updated with extension warnings
- [ ] No breaking changes to spec-compliant endpoints

## Test Requirements

### Update Tests
1. Remove tests for deleted endpoints
2. Update integration tests to verify extensions can be disabled
3. Add test for configuration-based endpoint toggling

## Files to Create
- `docs/api-extensions.md`
- `docs/spec-compliance.md`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/CommitController.java` (remove method)
- `src/main/java/org/chucc/vcserver/controller/HistoryController.java` (add config check)
- `src/main/java/org/chucc/vcserver/config/VersionControlProperties.java` (add properties)

## Files to Delete
- Tests specific to removed endpoints

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low** - Mostly documentation and removal

## Notes
- This task ensures clean separation between spec-compliant and extension features
- Extensions should be clearly marked to avoid confusion
- Configuration makes it easy to run in "spec-only" mode if desired
- Document any semantic differences from spec
- Final task - review everything works together
