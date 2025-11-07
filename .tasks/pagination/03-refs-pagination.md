# Task 3: Add Pagination to RefsController

**Status:** Not Started
**Priority:** High
**Estimated Time:** 2-3 hours
**Endpoint:** `GET /{dataset}/version/refs`

---

## Objective

Add `offset` and `limit` pagination parameters to the refs listing endpoint. This endpoint combines both branches and tags, so pagination is especially important.

---

## Current Implementation

**File:** `src/main/java/org/chucc/vcserver/controller/RefsController.java:43`

```java
@GetMapping(value = "/refs", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<RefsListResponse> listRefs(
    @PathVariable String dataset
) {
    var refs = refService.getAllRefs(dataset);
    return ResponseEntity.ok(new RefsListResponse(refs));
}
```

**Problem:** Returns ALL refs (branches + tags) with no pagination.

---

## Changes Required

### 1. Controller Layer

**File:** `src/main/java/org/chucc/vcserver/controller/RefsController.java`

**Add constant:**
```java
private static final int MAX_LIMIT = 1000;
```

**Update method:**
```java
@GetMapping(value = "/refs", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(
    summary = "List all refs",
    description = "List all branches and tags with their target commits (paginated)"
)
@ApiResponse(
    responseCode = "200",
    description = "Refs list",
    headers = {
        @Header(
            name = "Link",
            description = "RFC 5988 pagination links (next page when available)",
            schema = @Schema(type = "string")
        )
    },
    content = @Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE,
        schema = @Schema(implementation = RefsListResponse.class)
    )
)
@ApiResponse(
    responseCode = "400",
    description = "Bad Request - Invalid pagination parameters",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<RefsListResponse> listRefs(
    @Parameter(description = "Dataset name", required = true)
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
    RefsListResponse response = refService.getAllRefs(dataset, limit, offset);

    // Build Link header for next page (RFC 5988)
    HttpHeaders headers = new HttpHeaders();
    if (response.pagination().hasMore()) {
        String nextUrl = buildNextPageUrl(dataset, limit, offset);
        headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
}

/**
 * Builds the URL for the next page in pagination.
 *
 * @param dataset dataset name
 * @param limit page limit
 * @param offset current offset
 * @return URL for next page
 */
private String buildNextPageUrl(String dataset, int limit, int offset) {
    return String.format("/%s/version/refs?offset=%d&limit=%d",
        dataset, offset + limit, limit);
}
```

---

### 2. Service Layer

**File:** `src/main/java/org/chucc/vcserver/service/RefService.java`

**Update method signature:**
```java
/**
 * Lists all refs (branches and tags) in a dataset with pagination.
 *
 * @param dataset the dataset name
 * @param limit maximum number of results to return
 * @param offset number of results to skip
 * @return refs list response with pagination metadata
 */
public RefsListResponse getAllRefs(String dataset, int limit, int offset) {
    // Get all branches and tags
    List<RefInfo> allRefs = new ArrayList<>();

    // Add branches
    List<Branch> branches = branchRepository.findAllByDataset(dataset);
    branches.forEach(branch -> allRefs.add(new RefInfo(
        "branch",
        branch.getName(),
        branch.getHeadCommitId().value()
    )));

    // Add tags
    List<Tag> tags = tagRepository.findAllByDataset(dataset);
    tags.forEach(tag -> allRefs.add(new RefInfo(
        "tag",
        tag.getName(),
        tag.getTargetCommitId().value()
    )));

    // Calculate pagination
    int totalCount = allRefs.size();
    boolean hasMore = (offset + limit) < totalCount;

    // Apply offset and limit
    List<RefInfo> refs = allRefs.stream()
        .skip(offset)
        .limit(limit)
        .toList();

    // Build pagination metadata
    PaginationMetadata pagination = new PaginationMetadata(
        totalCount,
        offset,
        limit,
        hasMore
    );

    return new RefsListResponse(refs, pagination);
}
```

---

### 3. DTO Layer

**File:** `src/main/java/org/chucc/vcserver/dto/RefsListResponse.java`

**Update to include pagination:**
```java
package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response for refs list endpoint with pagination.
 */
@Schema(description = "Paginated list of refs (branches and tags)")
public record RefsListResponse(
    @Schema(description = "List of refs for current page")
    List<RefInfo> refs,

    @Schema(description = "Pagination metadata")
    PaginationMetadata pagination
) {
}
```

---

### 4. Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/RefsControllerIT.java` (create if needed)

**Add pagination tests:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class RefsControllerIT extends IntegrationTestFixture {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void listRefs_withDefaultPagination_shouldReturnFirst100() {
        // Arrange: Create 60 branches + 90 tags = 150 refs
        for (int i = 0; i < 60; i++) {
            createBranch("branch-" + i);
        }
        for (int i = 0; i < 90; i++) {
            createTag("v" + i);
        }

        // Act
        ResponseEntity<RefsListResponse> response = restTemplate.exchange(
            "/" + dataset + "/version/refs",
            HttpMethod.GET,
            null,
            RefsListResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RefsListResponse body = response.getBody();
        assertThat(body.refs()).hasSize(100);
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
    void listRefs_withCustomPagination_shouldReturnCorrectPage() {
        // Arrange: Create 50 refs total
        for (int i = 0; i < 25; i++) {
            createBranch("branch-" + i);
        }
        for (int i = 0; i < 25; i++) {
            createTag("v" + i);
        }

        // Act: Get page 2 (offset=10, limit=20)
        ResponseEntity<RefsListResponse> response = restTemplate.exchange(
            "/" + dataset + "/version/refs?offset=10&limit=20",
            HttpMethod.GET,
            null,
            RefsListResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RefsListResponse body = response.getBody();
        assertThat(body.refs()).hasSize(20);
        assertThat(body.pagination().offset()).isEqualTo(10);
        assertThat(body.pagination().limit()).isEqualTo(20);
        assertThat(body.pagination().hasMore()).isTrue();
    }

    @Test
    void listRefs_offsetBeyondTotal_shouldReturnEmptyList() {
        // Act: Offset beyond total (only main branch exists)
        ResponseEntity<RefsListResponse> response = restTemplate.exchange(
            "/" + dataset + "/version/refs?offset=1000&limit=100",
            HttpMethod.GET,
            null,
            RefsListResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RefsListResponse body = response.getBody();
        assertThat(body.refs()).isEmpty();
        assertThat(body.pagination().hasMore()).isFalse();
    }

    @Test
    void listRefs_limitExceedsMax_shouldReturn400() {
        // Act
        ResponseEntity<String> response = restTemplate.exchange(
            "/" + dataset + "/version/refs?limit=2000",
            HttpMethod.GET,
            null,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listRefs_negativeOffset_shouldReturn400() {
        // Act
        ResponseEntity<String> response = restTemplate.exchange(
            "/" + dataset + "/version/refs?offset=-1",
            HttpMethod.GET,
            null,
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listRefs_shouldIncludeBothBranchesAndTags() {
        // Arrange: Create 5 branches + 5 tags
        for (int i = 0; i < 5; i++) {
            createBranch("feature-" + i);
            createTag("v1." + i);
        }

        // Act
        ResponseEntity<RefsListResponse> response = restTemplate.exchange(
            "/" + dataset + "/version/refs?limit=20",
            HttpMethod.GET,
            null,
            RefsListResponse.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RefsListResponse body = response.getBody();

        // Count branches and tags
        long branchCount = body.refs().stream()
            .filter(ref -> "branch".equals(ref.type()))
            .count();
        long tagCount = body.refs().stream()
            .filter(ref -> "tag".equals(ref.type()))
            .count();

        assertThat(branchCount).isEqualTo(6); // 5 created + 1 main
        assertThat(tagCount).isEqualTo(5);
    }

    private void createBranch(String name) {
        // Implementation similar to BranchControllerIT
    }

    private void createTag(String name) {
        // Implementation similar to TagControllerIT
    }
}
```

---

### 5. Service Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/RefServiceTest.java`

**Add pagination tests:**
```java
@Test
void getAllRefs_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 30 branches + 20 tags = 50 refs
    List<Branch> branches = IntStream.range(0, 30)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    List<Tag> tags = IntStream.range(0, 20)
        .mapToObj(i -> createTag("v" + i))
        .toList();

    when(branchRepository.findAllByDataset("test")).thenReturn(branches);
    when(tagRepository.findAllByDataset("test")).thenReturn(tags);

    // Act: Get second page (offset=10, limit=20)
    RefsListResponse response = refService.getAllRefs("test", 20, 10);

    // Assert
    assertThat(response.refs()).hasSize(20);
    assertThat(response.pagination().totalCount()).isEqualTo(50);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().hasMore()).isTrue();
}

@Test
void getAllRefs_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 15 branches + 10 tags = 25 refs
    List<Branch> branches = IntStream.range(0, 15)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    List<Tag> tags = IntStream.range(0, 10)
        .mapToObj(i -> createTag("v" + i))
        .toList();

    when(branchRepository.findAllByDataset("test")).thenReturn(branches);
    when(tagRepository.findAllByDataset("test")).thenReturn(tags);

    // Act: Last page (offset=20, limit=10)
    RefsListResponse response = refService.getAllRefs("test", 10, 20);

    // Assert
    assertThat(response.refs()).hasSize(5); // Only 5 remaining
    assertThat(response.pagination().hasMore()).isFalse();
}

private Branch createBranch(String name) {
    return new Branch(name, CommitId.generate(), false);
}

private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author");
}
```

---

## Checklist

- [ ] Update RefsController with pagination parameters
- [ ] Add MAX_LIMIT constant
- [ ] Add buildNextPageUrl() helper method
- [ ] Update RefService.getAllRefs() signature
- [ ] Implement pagination logic in service
- [ ] Update RefsListResponse DTO
- [ ] Reuse PaginationMetadata DTO (created in Task 1)
- [ ] Add OpenAPI annotations
- [ ] Write integration tests (7 scenarios)
- [ ] Write service unit tests (2 scenarios)
- [ ] Run static analysis: `mvn -q clean compile checkstyle:check spotbugs:check`
- [ ] Run tests: `mvn -q test -Dtest=RefsControllerIT,RefServiceTest`
- [ ] Test in Swagger UI
- [ ] Full build: `mvn -q clean install`

---

## Verification

1. Start application: `mvn spring-boot:run`
2. Open Swagger UI: http://localhost:8080/swagger-ui.html
3. Test GET /version/refs with:
   - No parameters (default: limit=100, offset=0)
   - Custom pagination: offset=10, limit=20
   - Edge cases: offset=9999, limit=1001
4. Verify response includes both branches and tags

---

## References

- Pattern: Task 1 (BranchController) and Task 2 (TagController)
- Protocol: SPARQL 1.2 Protocol §3.2
