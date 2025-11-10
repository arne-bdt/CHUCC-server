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
    description = "Returns a paginated list of all branches and tags with their target commits"
)
@ApiResponse(
    responseCode = "200",
    description = "Refs list returned successfully",
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
    RefsListResponse response = refService.getAllRefs(dataset, limit, offset);

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.refs(dataset));

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
        branch.getCommitId().value()  // CORRECTED: Use getCommitId(), not getHeadCommitId()
    )));

    // Add tags
    List<Tag> tags = tagRepository.findAllByDataset(dataset);
    tags.forEach(tag -> allRefs.add(new RefInfo(
        "tag",
        tag.getName(),
        tag.getTargetCommitId().value()
    )));

    // Calculate hasMore BEFORE applying pagination (matches HistoryService pattern)
    boolean hasMore = allRefs.size() > offset + limit;

    // Apply offset and limit
    List<RefInfo> refs = allRefs.stream()
        .skip(offset)
        .limit(limit)
        .toList();

    // Build pagination metadata (uses existing PaginationInfo)
    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);

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
 * Response DTO for listing refs with pagination.
 * Contains a list of refs (branches and tags) and pagination metadata.
 */
@Schema(description = "Paginated list of refs (branches and tags)")
public record RefsListResponse(
    @Schema(description = "List of refs for current page")
    List<RefInfo> refs,

    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
) {
    /**
     * Compact constructor with defensive copying.
     *
     * @param refs list of ref information
     * @param pagination pagination metadata
     */
    public RefsListResponse {
        refs = refs != null ? List.copyOf(refs) : List.of();
    }
}
```

---

### 4. Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/RefsPaginationIT.java` (create new file)

**Add pagination tests:**
```java
package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.chucc.vcserver.dto.CreateBranchRequest;
import org.chucc.vcserver.dto.CreateBranchResponse;
import org.chucc.vcserver.dto.CreateTagRequest;
import org.chucc.vcserver.dto.RefsListResponse;
import org.chucc.vcserver.dto.TagInfo;
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
 * Integration tests for refs pagination.
 * Tests with projector enabled to verify read-side behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class RefsPaginationIT extends ITFixture {

  private static final String DATASET_NAME = "test-refs-pagination";

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected String getDatasetName() {
    return DATASET_NAME;
  }

  @Test
  void listRefs_withDefaultPagination_shouldReturnPaginatedResults() {
    // Arrange: Create 6 branches + 6 tags = 12 refs (+ 1 main branch = 13 total)
    for (int i = 0; i < 6; i++) {
      createBranch("branch-" + i);
    }
    for (int i = 0; i < 6; i++) {
      createTag("v" + i);
    }

    // Act: Request with limit=10
    ResponseEntity<RefsListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/refs?limit=10",
        HttpMethod.GET,
        null,
        RefsListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    RefsListResponse body = response.getBody();
    assertThat(body.refs()).hasSize(10);
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
  void listRefs_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 5 branches + 5 tags = 10 refs (+ 1 main = 11 total)
    for (int i = 0; i < 5; i++) {
      createBranch("branch-" + i);
    }
    for (int i = 0; i < 5; i++) {
      createTag("v" + i);
    }

    // Act: Get page 2 (offset=5, limit=5)
    ResponseEntity<RefsListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/refs?offset=5&limit=5",
        HttpMethod.GET,
        null,
        RefsListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    RefsListResponse body = response.getBody();
    assertThat(body.refs()).hasSize(5);
    assertThat(body.pagination().offset()).isEqualTo(5);
    assertThat(body.pagination().limit()).isEqualTo(5);
    assertThat(body.pagination().hasMore()).isTrue();
  }

  @Test
  void listRefs_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total (only main branch exists)
    ResponseEntity<RefsListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/refs?offset=1000&limit=100",
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
        "/" + DATASET_NAME + "/version/refs?limit=2000",
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
        "/" + DATASET_NAME + "/version/refs?offset=-1",
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
        "/" + DATASET_NAME + "/version/refs?limit=20",
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
    CreateBranchRequest request = new CreateBranchRequest(name, "main", false);
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    HttpEntity<CreateBranchRequest> httpEntity = new HttpEntity<>(request, headers);
    restTemplate.postForEntity(
        "/" + DATASET_NAME + "/version/branches",
        httpEntity,
        CreateBranchResponse.class
    );

    // Wait for async projection to complete
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var branch = branchRepository.findByDatasetAndName(DATASET_NAME, name);
          assertThat(branch).isPresent();
        });
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

**File:** `src/test/java/org/chucc/vcserver/service/RefServiceTest.java`

**Add pagination tests:**
```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.RefsListResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefServiceTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private TagRepository tagRepository;

  private RefService refService;

  @BeforeEach
  void setUp() {
    refService = new RefService(branchRepository, tagRepository);
  }

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
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().offset()).isEqualTo(10);
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

  @Test
  void getAllRefs_emptyRepository_shouldReturnEmptyList() {
    // Arrange
    when(branchRepository.findAllByDataset("test")).thenReturn(List.of());
    when(tagRepository.findAllByDataset("test")).thenReturn(List.of());

    // Act
    RefsListResponse response = refService.getAllRefs("test", 100, 0);

    // Assert
    assertThat(response.refs()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  private Branch createBranch(String name) {
    return new Branch(name, CommitId.generate(), false, Instant.now(), Instant.now(), 1);
  }

  private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author", Instant.now());
  }
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
- [ ] Reuse PaginationInfo DTO (created in Task 1)
- [ ] Add OpenAPI annotations
- [ ] Create RefsPaginationIT with 6 integration test scenarios
- [ ] Create RefServiceTest with 3 unit tests
- [ ] Run static analysis: `mvn -q clean compile checkstyle:check spotbugs:check`
- [ ] Run tests: `mvn -q test -Dtest=RefsPaginationIT,RefServiceTest`
- [ ] Full build: `mvn -q clean install`

---

## Verification

1. Start application: `mvn spring-boot:run`
2. Open Swagger UI: http://localhost:8080/swagger-ui.html
3. Test GET /{dataset}/version/refs with:
   - No parameters (default: limit=100, offset=0)
   - Custom pagination: offset=10, limit=20
   - Edge cases: offset=9999, limit=1001
4. Verify response includes both branches and tags

---

## References

- Pattern: Task 1 (BranchController) and Task 2 (TagController)
- Protocol: SPARQL 1.2 Protocol §3.2
- DTO: PaginationInfo (limit, offset, hasMore)
