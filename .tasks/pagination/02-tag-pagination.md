# Task 2: Add Pagination to TagController

**Status:** Not Started
**Priority:** High
**Estimated Time:** 2-3 hours
**Endpoint:** `GET /{dataset}/version/tags`

---

## Objective

Add `offset` and `limit` pagination parameters to the tag listing endpoint, following the same pattern as BranchController.

---

## Current Implementation

**File:** `src/main/java/org/chucc/vcserver/controller/TagController.java:64`

```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<TagListResponse> listTags(@PathVariable String dataset) {
    return ResponseEntity.ok(new TagListResponse(tagService.listTags(dataset)));
}
```

**Problem:** Returns ALL tags with no pagination.

---

## Changes Required

### 1. Controller Layer

**File:** `src/main/java/org/chucc/vcserver/controller/TagController.java`

**Add constant:**
```java
private static final int MAX_LIMIT = 1000;
```

**Update method:**
```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(
    summary = "List tags",
    description = "List all tags in the repository with pagination"
)
@ApiResponse(
    responseCode = "200",
    description = "Tags",
    headers = {
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
public ResponseEntity<TagListResponse> listTags(
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
    TagListResponse response = tagService.listTags(dataset, limit, offset);

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
    return String.format("/%s/version/tags?offset=%d&limit=%d",
        dataset, offset + limit, limit);
}
```

---

### 2. Service Layer

**File:** `src/main/java/org/chucc/vcserver/service/TagService.java`

**Update method signature:**
```java
/**
 * Lists all tags in a dataset with pagination.
 *
 * @param dataset the dataset name
 * @param limit maximum number of results to return
 * @param offset number of results to skip
 * @return tag list response with pagination metadata
 */
public TagListResponse listTags(String dataset, int limit, int offset) {
    List<Tag> allTags = tagRepository.findAllByDataset(dataset);

    // Calculate pagination
    int totalCount = allTags.size();
    boolean hasMore = (offset + limit) < totalCount;

    // Apply offset and limit
    List<TagInfo> tags = allTags.stream()
        .skip(offset)
        .limit(limit)
        .map(this::toTagInfo)
        .toList();

    // Build pagination metadata
    PaginationMetadata pagination = new PaginationMetadata(
        totalCount,
        offset,
        limit,
        hasMore
    );

    return new TagListResponse(tags, pagination);
}

private TagInfo toTagInfo(Tag tag) {
    return new TagInfo(
        tag.getName(),
        tag.getTargetCommitId(),
        tag.getMessage(),
        tag.getAuthor(),
        tag.getCreatedAt()
    );
}
```

---

### 3. DTO Layer

**File:** `src/main/java/org/chucc/vcserver/dto/TagListResponse.java`

**Update to include pagination:**
```java
package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response for tag list endpoint with pagination.
 */
@Schema(description = "Paginated list of tags")
public record TagListResponse(
    @Schema(description = "List of tags for current page")
    List<TagInfo> tags,

    @Schema(description = "Pagination metadata")
    PaginationMetadata pagination
) {
}
```

---

### 4. Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/TagControllerIT.java`

**Add pagination tests:**
```java
@Test
void listTags_withDefaultPagination_shouldReturnFirst100() {
    // Arrange: Create 150 tags
    for (int i = 0; i < 150; i++) {
        createTag("v" + String.format("%03d", i));
    }

    // Act
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/tags",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(100);
    assertThat(body.pagination().totalCount()).isEqualTo(150);
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
void listTags_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 50 tags
    for (int i = 0; i < 50; i++) {
        createTag("v1.0." + i);
    }

    // Act: Get page 2 (offset=10, limit=20)
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/tags?offset=10&limit=20",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(20);
    assertThat(body.pagination().offset()).isEqualTo(10);
    assertThat(body.pagination().limit()).isEqualTo(20);
    assertThat(body.pagination().hasMore()).isTrue();
}

@Test
void listTags_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + dataset + "/version/tags?offset=1000&limit=100",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).isEmpty();
    assertThat(body.pagination().hasMore()).isFalse();
}

@Test
void listTags_limitExceedsMax_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/version/tags?limit=2000",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}

@Test
void listTags_negativeOffset_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/version/tags?offset=-1",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
}

private void createTag(String name) {
    CreateTagRequest request = new CreateTagRequest(
        name,
        mainCommitId.value(),
        "Tag " + name,
        "test-author"
    );
    HttpEntity<CreateTagRequest> httpEntity = new HttpEntity<>(request);
    restTemplate.postForEntity(
        "/" + dataset + "/version/tags",
        httpEntity,
        TagInfo.class
    );
}
```

---

### 5. Service Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/TagServiceTest.java`

**Add pagination tests:**
```java
@Test
void listTags_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 50 tags
    List<Tag> allTags = IntStream.range(0, 50)
        .mapToObj(i -> createTag("v1.0." + i))
        .toList();
    when(tagRepository.findAllByDataset("test")).thenReturn(allTags);

    // Act: Get second page (offset=10, limit=20)
    TagListResponse response = tagService.listTags("test", 20, 10);

    // Assert
    assertThat(response.tags()).hasSize(20);
    assertThat(response.pagination().totalCount()).isEqualTo(50);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().hasMore()).isTrue();

    // Verify first tag in page is at correct offset
    assertThat(response.tags().get(0).name()).isEqualTo("v1.0.10");
}

@Test
void listTags_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 25 tags
    List<Tag> allTags = IntStream.range(0, 25)
        .mapToObj(i -> createTag("v1.0." + i))
        .toList();
    when(tagRepository.findAllByDataset("test")).thenReturn(allTags);

    // Act: Last page (offset=20, limit=10)
    TagListResponse response = tagService.listTags("test", 10, 20);

    // Assert
    assertThat(response.tags()).hasSize(5); // Only 5 remaining
    assertThat(response.pagination().hasMore()).isFalse();
}

private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author");
}
```

---

## Checklist

- [ ] Update TagController with pagination parameters
- [ ] Add MAX_LIMIT constant
- [ ] Add buildNextPageUrl() helper method
- [ ] Update TagService.listTags() signature
- [ ] Implement pagination logic in service
- [ ] Update TagListResponse DTO
- [ ] Reuse PaginationMetadata DTO (created in Task 1)
- [ ] Add OpenAPI annotations
- [ ] Write integration tests (6 scenarios)
- [ ] Write service unit tests (2 scenarios)
- [ ] Run static analysis: `mvn -q clean compile checkstyle:check spotbugs:check`
- [ ] Run tests: `mvn -q test -Dtest=TagControllerIT,TagServiceTest`
- [ ] Test in Swagger UI
- [ ] Full build: `mvn -q clean install`

---

## Verification

1. Start application: `mvn spring-boot:run`
2. Open Swagger UI: http://localhost:8080/swagger-ui.html
3. Test GET /version/tags with:
   - No parameters (default: limit=100, offset=0)
   - Custom pagination: offset=10, limit=20
   - Edge cases: offset=9999, limit=1001

---

## References

- Pattern: Task 1 (BranchController)
- Protocol: SPARQL 1.2 Protocol §3.5
