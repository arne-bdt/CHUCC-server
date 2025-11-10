# Add Pagination to Collection Endpoints

**Status:** In Progress (2/3 completed)
**Priority:** High (API Completeness)
**Estimated Time:** 6-8 hours (4.5 hours completed)
**Session ID:** `claude/audit-offset-limit-params-011CUtpRzJtQsm1pZ6Cda2j6`

---

## Overview

Add `offset` and `limit` pagination parameters to collection endpoints that currently return all results. This aligns with REST API best practices and ensures consistent behavior across all list operations.

**Audit Date:** 2025-11-07
**Audit Session:** claude/audit-offset-limit-params-011CUtpRzJtQsm1pZ6Cda2j6

---

## Current Status

### ✅ Already Implemented (3/5 endpoints)

1. **`GET /{dataset}/version/history`** - HistoryController:85
   - Has offset/limit (defaults: limit=100, max=1000, offset=0)
   - Includes RFC 5988 Link headers
   - Pagination metadata in response

2. **`GET /{dataset}/version/blame`** - HistoryController:306
   - Has offset/limit (defaults: limit=100, max=1000, offset=0)
   - Includes RFC 5988 Link headers
   - Pagination metadata in response

3. **`GET /{dataset}/version/branches`** - BranchController:77 ✅ **COMPLETED**
   - Has offset/limit (defaults: limit=100, max=1000, offset=0)
   - Includes RFC 5988 Link headers
   - Pagination metadata in response (uses PaginationInfo)
   - Implementation date: 2025-11-10

### ✅ Recently Completed

4. **`GET /{dataset}/version/tags`** - TagController:74 ✅ **COMPLETED**
   - Has offset/limit (defaults: limit=100, max=1000, offset=0)
   - Includes RFC 5988 Link headers
   - Pagination metadata in response (uses PaginationInfo)
   - Implementation date: 2025-11-10

### ❌ Missing Implementation (1/5 endpoints)

5. **`GET /{dataset}/version/refs`** - RefsController:43
   - Returns ALL refs (no pagination)
   - Protocol: SPARQL 1.2 Protocol §3.2

---

## Protocol Analysis

The SPARQL 1.2 Protocol Version Control Extension defines these as "list" operations:
- §3.2: "GET /version/refs — list branches/tags, their target commits"
- §3.5: "GET /version/tags — list all tags"

While pagination is not **mandated** by the protocol, REST best practices recommend it for:
- Performance with large datasets
- Consistent API behavior
- Efficient client data retrieval
- HTTP caching support

---

## Implementation Pattern

Follow the pattern from `HistoryController`:

```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<BranchListResponse> listBranches(
    @PathVariable String dataset,
    @RequestParam(required = false, defaultValue = "100") Integer limit,
    @RequestParam(required = false, defaultValue = "0") Integer offset
) {
    // Validate pagination
    if (limit < 1 || limit > MAX_LIMIT) {
        throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
    }
    if (offset < 0) {
        throw new IllegalArgumentException("Offset cannot be negative");
    }

    // Call service with pagination
    BranchListResponse response = branchService.listBranches(dataset, limit, offset);

    // Build Link header for next page (RFC 5988)
    if (response.pagination().hasMore()) {
        String nextUrl = buildNextPageUrl(dataset, limit, offset);
        headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
}
```

**Key Elements:**
- Default: limit=100, offset=0
- Max limit: 1000 (constant MAX_LIMIT)
- Response includes pagination metadata (totalCount, hasMore, offset, limit)
- RFC 5988 Link header with rel="next" when hasMore=true
- OpenAPI annotations document parameters

---

## Tasks

### ✅ Task 1: Add Pagination to BranchController (COMPLETED)
**File:** `01-branch-pagination.md`
**Time:** 2-3 hours (actual: 2.5 hours)
**Completed:** 2025-11-10

Add offset/limit to `GET /{dataset}/version/branches` endpoint.

**Implementation Summary:**
- Added pagination to BranchService with limit/offset parameters
- Updated BranchListResponse to include PaginationInfo
- Modified BranchController with validation and Link headers
- Created comprehensive test suite (5 integration + 3 unit tests)
- All quality gates passed (Checkstyle, SpotBugs, PMD)
- Code review: Grade A, CQRS compliant

---

### ✅ Task 2: Add Pagination to TagController (COMPLETED)
**File:** `02-tag-pagination.md`
**Time:** 2-3 hours (actual: 2 hours)
**Completed:** 2025-11-10

Add offset/limit to `GET /{dataset}/version/tags` endpoint.

**Implementation Summary:**
- Added pagination to TagService with limit/offset parameters
- Updated TagListResponse to include PaginationInfo
- Modified TagController with validation and Link headers
- Created PaginationValidator utility (eliminates duplication with BranchController)
- Created comprehensive test suite (5 integration + 3 unit tests)
- All quality gates passed (Checkstyle, SpotBugs, PMD, CPD)

---

### Task 3: Add Pagination to RefsController
**File:** `03-refs-pagination.md`
**Time:** 2-3 hours

Add offset/limit to `GET /{dataset}/version/refs` endpoint.

---

## Benefits

- **Performance:** Prevent memory exhaustion with large result sets
- **Consistency:** All collection endpoints behave the same way
- **Scalability:** Support datasets with thousands of branches/tags
- **UX:** Clients can fetch data incrementally
- **Caching:** Better HTTP cache efficiency with smaller responses

---

## Dependencies

None. Each task is independent and can be completed in any order.

---

## Testing Strategy

For each endpoint:
1. Unit tests for pagination logic (service layer)
2. Integration tests for API contract
   - Test default behavior (limit=100, offset=0)
   - Test custom limit/offset
   - Test boundary conditions (offset beyond total, limit > max)
   - Test Link header generation
   - Test pagination metadata in response
3. Projector disabled (default test mode)

---

## Recommended Implementation Order

1. **Task 1** (Branches) - Most commonly used endpoint
2. **Task 2** (Tags) - Similar to branches
3. **Task 3** (Refs) - Combines branches + tags logic

---

## Files to Modify

### Controllers
- `src/main/java/org/chucc/vcserver/controller/BranchController.java`
- `src/main/java/org/chucc/vcserver/controller/TagController.java`
- `src/main/java/org/chucc/vcserver/controller/RefsController.java`

### Services
- `src/main/java/org/chucc/vcserver/service/BranchService.java`
- `src/main/java/org/chucc/vcserver/service/TagService.java`
- `src/main/java/org/chucc/vcserver/service/RefService.java`

### DTOs
- `src/main/java/org/chucc/vcserver/dto/BranchListResponse.java`
- `src/main/java/org/chucc/vcserver/dto/TagListResponse.java`
- `src/main/java/org/chucc/vcserver/dto/RefsListResponse.java`
- `src/main/java/org/chucc/vcserver/dto/PaginationMetadata.java` (shared)

### Tests
- `src/test/java/org/chucc/vcserver/controller/BranchControllerIT.java`
- `src/test/java/org/chucc/vcserver/controller/TagControllerIT.java`
- `src/test/java/org/chucc/vcserver/controller/RefsControllerIT.java`
- `src/test/java/org/chucc/vcserver/service/BranchServiceTest.java`
- `src/test/java/org/chucc/vcserver/service/TagServiceTest.java`
- `src/test/java/org/chucc/vcserver/service/RefServiceTest.java`

---

## Quality Gates

All changes must pass:
- ✅ Checkstyle (zero violations)
- ✅ SpotBugs (zero warnings)
- ✅ PMD (zero violations)
- ✅ All tests pass (~1304 tests)
- ✅ Zero compiler warnings (`-Werror`)

---

## Documentation Updates

- [ ] Update OpenAPI annotations with pagination parameters
- [ ] Update `docs/api/openapi-guide.md` if needed
- [ ] Add pagination examples to Swagger UI
- [ ] Update this README when complete

---

## Notes

- The protocol doesn't mandate pagination, but it's a REST best practice
- Implementation mirrors existing `HistoryController` pattern
- All three endpoints should have identical pagination behavior
- Use `PaginationMetadata` record for consistency (shared DTO)
- Link headers follow RFC 5988 format: `</url>; rel="next"`
