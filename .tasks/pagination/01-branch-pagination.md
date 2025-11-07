# Task 1: Add Pagination to BranchController

**Status:** Not Started
**Priority:** High
**Estimated Time:** 2-3 hours
**Endpoint:** `GET /{dataset}/version/branches`

---

## Objective

Add `offset` and `limit` pagination parameters to the branch listing endpoint, following the pattern used in `HistoryController`.

---

## Current Implementation

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java:73`

```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<BranchListResponse> listBranches(
    @PathVariable String dataset
) {
    List<BranchInfo> branches = branchService.listBranches(dataset);
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.branches(dataset));
    return ResponseEntity.ok().headers(headers).body(new BranchListResponse(branches));
}
```

**Problem:** Returns ALL branches with no pagination.

---

## Changes Required

### 1. Controller Layer

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

**Add constant:**
```java
private static final int MAX_LIMIT = 1000;
```

**Update method signature:**
```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(
    summary = "List branches",
    description = "Returns a paginated list of all branches in the dataset with metadata"
)
@ApiResponse(
    responseCode = "200",
    description = "Branch list returned successfully",
    headers = {
        @Header(
            name = "Content-Location",
            description = "Canonical URL for this resource",
            schema = @Schema(type = "string")
        ),
        @Header(
            name = "Link",
            description = "RFC 5988 pagination links (next page when available)",
            schema = @Schema(type = "string")
        )
    },
    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
)
@ApiResponse(
    responseCode = "400",
    description = "Bad Request - Invalid pagination parameters",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<BranchListResponse> listBranches(
    @Parameter(description = "Dataset name", example = "default", required = true)
    @PathVariable String dataset,
    @Parameter(description = "Limit number of results (max 1000)")
    @RequestParam(required = false, defaultValue = "100") Integer limit,
    @Parameter(description = "Offset for pagination")
    @RequestParam(required = false, defaultValue = "0") Integer offset
) {
    // Validate pagination parameters
    if (limit < 1 || limit > MAX_LIMIT) {
        throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
    }
    if (offset < 0) {
        throw new IllegalArgumentException("Offset cannot be negative");
    }

    // Call service with pagination
    BranchListResponse response = branchService.listBranches(dataset, limit, offset);

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.branches(dataset));

    // Add Link header for next page (RFC 5988)
    if (response.pagination().hasMore()) {
        String nextUrl = buildNextPageUrl(dataset, limit, offset);
        headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
}
```

**Add helper method:**
```java
/**
 * Builds the URL for the next page in pagination.
 *
 * @param dataset dataset name
 * @param limit page limit
 * @param offset current offset
 * @return URL for next page
 */
private String buildNextPageUrl(String dataset, int limit, int offset) {
    return String.format("/%s/version/branches?offset=%d&limit=%d",
        dataset, offset + limit, limit);
}
```

---

### 2. Service Layer

**File:** `src/main/java/org/chucc/vcserver/service/BranchService.java`

**Update method signature:**
```java
/**
 * Lists all branches in a dataset with pagination.
 *
 * @param dataset the dataset name
 * @param limit maximum number of results to return
 * @param offset number of results to skip
 * @return branch list response with pagination metadata
 */
public BranchListResponse listBranches(String dataset, int limit, int offset) {
    List<Branch> allBranches = branchRepository.findAllByDataset(dataset);

    // Calculate pagination
    int totalCount = allBranches.size();
    boolean hasMore = (offset + limit) < totalCount;

    // Apply offset and limit
    List<BranchInfo> branches = allBranches.stream()
        .skip(offset)
        .limit(limit)
        .map(this::toBranchInfo)
        .toList();

    // Build pagination metadata
    PaginationMetadata pagination = new PaginationMetadata(
        totalCount,
        offset,
        limit,
        hasMore
    );

    return new BranchListResponse(branches, pagination);
}

private BranchInfo toBranchInfo(Branch branch) {
    return new BranchInfo(
        branch.getName(),
        branch.getHeadCommitId(),
        branch.isProtected(),
        branch.getCreatedAt(),
        branch.getUpdatedAt()
    );
}
```

---

### 3. DTO Layer

**File:** `src/main/java/org/chucc/vcserver/dto/BranchListResponse.java`

**Update to include pagination:**
```java
package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response for branch list endpoint with pagination.
 */
@Schema(description = "Paginated list of branches")
public record BranchListResponse(
    @Schema(description = "List of branches for current page")
    List<BranchInfo> branches,

    @Schema(description = "Pagination metadata")
    PaginationMetadata pagination
) {
}
```

**Create PaginationMetadata if it doesn't exist:**

**File:** `src/main/java/org/chucc/vcserver/dto/PaginationMetadata.java`

```java
package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Pagination metadata for collection responses.
 */
@Schema(description = "Pagination metadata")
public record PaginationMetadata(
    @Schema(description = "Total number of items available", example = "250")
    int totalCount,

    @Schema(description = "Number of items skipped", example = "0")
    int offset,

    @Schema(description = "Maximum number of items in this response", example = "100")
    int limit,

    @Schema(description = "Whether more items are available", example = "true")
    boolean hasMore
) {
}
```

---

### 4. Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/BranchControllerIT.java`

**Add pagination tests:**
```java
@Test
void listBranches_withDefaultPagination_shouldReturnFirst100() {
    // Arrange: Create 150 branches
    for (int i = 0; i < 150; i++) {
        createBranch("branch-" + String.format("%03d", i));
    }

    // Act
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/branches",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body.branches()).hasSize(100);
    assertThat(body.pagination().totalCount()).isEqualTo(151); // +1 for main branch
    assertThat(body.pagination().offset()).isEqualTo(0);
    assertThat(body.pagination().limit()).isEqualTo(100);
    assertThat(body.pagination().hasMore()).isTrue();

    // Assert Link header
    List<String> linkHeaders = response.getHeaders().get("Link");
    assertThat(linkHeaders).hasSize(1);
    assertThat(linkHeaders.get(0))
        .contains("offset=100")
        .contains("limit=100")
        .contains("rel=\"next\"");
}

@Test
void listBranches_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 50 branches
    for (int i = 0; i < 50; i++) {
        createBranch("branch-" + i);
    }

    // Act: Get page 2 (offset=10, limit=20)
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/branches?offset=10&limit=20",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body.branches()).hasSize(20);
    assertThat(body.pagination().offset()).isEqualTo(10);
    assertThat(body.pagination().limit()).isEqualTo(20);
    assertThat(body.pagination().hasMore()).isTrue();
}

@Test
void listBranches_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total (only main branch exists)
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/branches?offset=1000&limit=100",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body.branches()).isEmpty();
    assertThat(body.pagination().totalCount()).isEqualTo(1);
    assertThat(body.pagination().hasMore()).isFalse();
}

@Test
void listBranches_limitExceedsMax_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/version/branches?limit=2000",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
}

@Test
void listBranches_negativeOffset_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/version/branches?offset=-1",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}

private void createBranch(String name) {
    CreateBranchRequest request = new CreateBranchRequest(name, "main", false);
    HttpEntity<CreateBranchRequest> httpEntity =
        new HttpEntity<>(request, createAuthorHeader());
    restTemplate.postForEntity(
        "/" + dataset + "/version/branches",
        httpEntity,
        CreateBranchResponse.class
    );
}
```

---

### 5. Service Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/BranchServiceTest.java`

**Add pagination tests:**
```java
@Test
void listBranches_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 50 branches
    List<Branch> allBranches = IntStream.range(0, 50)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    when(branchRepository.findAllByDataset("test")).thenReturn(allBranches);

    // Act: Get second page (offset=10, limit=20)
    BranchListResponse response = branchService.listBranches("test", 20, 10);

    // Assert
    assertThat(response.branches()).hasSize(20);
    assertThat(response.pagination().totalCount()).isEqualTo(50);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().hasMore()).isTrue();

    // Verify first branch in page is at correct offset
    assertThat(response.branches().get(0).name()).isEqualTo("branch-10");
}

@Test
void listBranches_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 25 branches
    List<Branch> allBranches = IntStream.range(0, 25)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    when(branchRepository.findAllByDataset("test")).thenReturn(allBranches);

    // Act: Last page (offset=20, limit=10)
    BranchListResponse response = branchService.listBranches("test", 10, 20);

    // Assert
    assertThat(response.branches()).hasSize(5); // Only 5 remaining
    assertThat(response.pagination().hasMore()).isFalse();
}

private Branch createBranch(String name) {
    return new Branch(name, CommitId.generate(), false);
}
```

---

## Checklist

- [ ] Update BranchController with pagination parameters
- [ ] Add MAX_LIMIT constant
- [ ] Add buildNextPageUrl() helper method
- [ ] Update BranchService.listBranches() signature
- [ ] Implement pagination logic in service
- [ ] Update BranchListResponse DTO
- [ ] Create PaginationMetadata DTO (if doesn't exist)
- [ ] Add OpenAPI annotations
- [ ] Write integration tests (6 scenarios)
- [ ] Write service unit tests (2 scenarios)
- [ ] Run static analysis: `mvn -q clean compile checkstyle:check spotbugs:check`
- [ ] Run tests: `mvn -q test -Dtest=BranchControllerIT,BranchServiceTest`
- [ ] Test in Swagger UI
- [ ] Full build: `mvn -q clean install`

---

## Verification

1. Start application: `mvn spring-boot:run`
2. Open Swagger UI: http://localhost:8080/swagger-ui.html
3. Test GET /version/branches with:
   - No parameters (default: limit=100, offset=0)
   - Custom pagination: offset=10, limit=20
   - Edge cases: offset=9999, limit=1001

---

## References

- Pattern: `HistoryController.listHistory()` (line 109)
- DTO pattern: `HistoryResponse` with `PaginationInfo`
- Link header: RFC 5988
- Protocol: SPARQL 1.2 Protocol §3.2
