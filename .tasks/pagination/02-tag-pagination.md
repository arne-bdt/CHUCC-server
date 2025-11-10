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
    description = "Returns a paginated list of all tags in the dataset"
)
@ApiResponse(
    responseCode = "200",
    description = "Tag list returned successfully",
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
public ResponseEntity<TagListResponse> listTags(
    @Parameter(description = "Dataset name", example = "default", required = true)
    @PathVariable String dataset,
    @Parameter(description = "Maximum number of results (max 1000)", example = "100")
    @RequestParam(required = false, defaultValue = "100") Integer limit,
    @Parameter(description = "Offset for pagination", example = "0")
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

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.tags(dataset));

    // Add Link header for next page (RFC 5988)
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

    // Calculate hasMore BEFORE applying pagination (matches HistoryService pattern)
    boolean hasMore = allTags.size() > offset + limit;

    // Apply offset and limit
    List<TagInfo> tags = allTags.stream()
        .skip(offset)
        .limit(limit)
        .map(this::toTagInfo)
        .toList();

    // Build pagination metadata (uses existing PaginationInfo)
    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);

    return new TagListResponse(tags, pagination);
}

private TagInfo toTagInfo(Tag tag) {
    return new TagInfo(
        tag.getName(),
        tag.getTargetCommitId().value(),
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
 * Response DTO for listing tags with pagination.
 * Contains a list of tag information and pagination metadata.
 */
@Schema(description = "Paginated list of tags")
public record TagListResponse(
    @Schema(description = "List of tags for current page")
    List<TagInfo> tags,

    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
) {
    /**
     * Compact constructor with defensive copying.
     *
     * @param tags list of tag information
     * @param pagination pagination metadata
     */
    public TagListResponse {
        tags = tags != null ? List.copyOf(tags) : List.of();
    }
}
```

---

### 4. Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/TagPaginationIT.java` (create new file)

**Add pagination tests:**
```java
package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.chucc.vcserver.dto.CreateTagRequest;
import org.chucc.vcserver.dto.TagInfo;
import org.chucc.vcserver.dto.TagListResponse;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for tag pagination.
 * Tests with projector enabled to verify read-side behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class TagPaginationIT extends ITFixture {

  private static final String DATASET_NAME = "test-tag-pagination";

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected String getDatasetName() {
    return DATASET_NAME;
  }

  @Test
  void listTags_withDefaultPagination_shouldReturnPaginatedResults() {
    // Arrange: Create 12 tags
    for (int i = 0; i < 12; i++) {
      createTag("v" + String.format("%03d", i));
    }

    // Act: Request with limit=10
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?limit=10",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(10);
    assertThat(body.pagination().offset()).isEqualTo(0);
    assertThat(body.pagination().limit()).isEqualTo(10);
    assertThat(body.pagination().hasMore()).isTrue();

    // Assert Link header
    List<String> linkHeaders = response.getHeaders().get("Link");
    assertThat(linkHeaders).hasSize(1);
    assertThat(linkHeaders.get(0))
        .contains("offset=10")
        .contains("limit=10")
        .contains("rel=\"next\"");
  }

  @Test
  void listTags_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 15 tags
    for (int i = 0; i < 15; i++) {
      createTag("v1.0." + i);
    }

    // Act: Get page 2 (offset=5, limit=5)
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?offset=5&limit=5",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(5);
    assertThat(body.pagination().offset()).isEqualTo(5);
    assertThat(body.pagination().limit()).isEqualTo(5);
    assertThat(body.pagination().hasMore()).isTrue();
  }

  @Test
  void listTags_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?offset=1000&limit=100",
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
        "/" + DATASET_NAME + "/version/tags?limit=2000",
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
        "/" + DATASET_NAME + "/version/tags?offset=-1",
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
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<CreateTagRequest> httpEntity = new HttpEntity<>(request, headers);
    restTemplate.postForEntity(
        "/" + DATASET_NAME + "/version/tags",
        httpEntity,
        TagInfo.class
    );

    // Wait for async projection to complete
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var tag = tagRepository.findByDatasetAndName(DATASET_NAME, name);
          assertThat(tag).isPresent();
        });
  }
}
```

---

### 5. Service Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/TagServiceTest.java`

**Add pagination tests:**
```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.TagListResponse;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  @Mock
  private TagRepository tagRepository;

  private TagService tagService;

  @BeforeEach
  void setUp() {
    tagService = new TagService(tagRepository);
  }

  @Test
  void listTags_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 50 tags
    List<Tag> allTags = IntStream.range(0, 50)
        .mapToObj(i -> createTag("v1.0." + i))
        .toList();
    when(tagRepository.findAllByDataset("test")).thenReturn(allTags);

    // Act: Get second page (limit=20, offset=10)
    TagListResponse response = tagService.listTags("test", 20, 10);

    // Assert
    assertThat(response.tags()).hasSize(20);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().hasMore()).isTrue();
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

  @Test
  void listTags_emptyRepository_shouldReturnEmptyList() {
    // Arrange
    when(tagRepository.findAllByDataset("test")).thenReturn(List.of());

    // Act
    TagListResponse response = tagService.listTags("test", 100, 0);

    // Assert
    assertThat(response.tags()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author", Instant.now());
  }
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
- [ ] Reuse PaginationInfo DTO (created in Task 1)
- [ ] Add OpenAPI annotations
- [ ] Create TagPaginationIT with 5 integration test scenarios
- [ ] Create TagServiceTest with 3 unit tests
- [ ] Run static analysis: `mvn -q clean compile checkstyle:check spotbugs:check`
- [ ] Run tests: `mvn -q test -Dtest=TagPaginationIT,TagServiceTest`
- [ ] Full build: `mvn -q clean install`

---

## Verification

1. Start application: `mvn spring-boot:run`
2. Open Swagger UI: http://localhost:8080/swagger-ui.html
3. Test GET /{dataset}/version/tags with:
   - No parameters (default: limit=100, offset=0)
   - Custom pagination: offset=10, limit=20
   - Edge cases: offset=9999, limit=1001

---

## References

- Pattern: Task 1 (BranchController)
- Protocol: SPARQL 1.2 Protocol §3.5
- DTO: PaginationInfo (limit, offset, hasMore)
