# Task: Implement Commit Metadata Endpoint

**Status:** Not Started (Blocked by Task 00)
**Priority:** Medium
**Category:** Version Control Protocol
**Estimated Time:** 1-2 hours

---

## Overview

Implement the missing Commit Metadata endpoint that currently returns 501:
- `GET /version/commits/{id}?dataset={name}` - Get commit metadata

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

**Prerequisites:**
- ✅ Task 00 must be completed first ([00-add-patchsize-to-commit-entity.md](./00-add-patchsize-to-commit-entity.md))
- Commit entity must have `patchSize` field

---

## Current State

**Controller:** [CommitController.java](../../src/main/java/org/chucc/vcserver/controller/CommitController.java)

**Implemented:**
- ✅ `POST /version/commits` - Create commit ([line 64](../../src/main/java/org/chucc/vcserver/controller/CommitController.java#L64))

**Not Implemented (returns 501):**
- ❌ `GET /version/commits/{id}` ([line 253-260](../../src/main/java/org/chucc/vcserver/controller/CommitController.java#L253-L260))

---

## Requirements

### GET /version/commits/{id} - Get Commit Metadata

**Request:**
```http
GET /version/commits/01933e4a-9d4e-7000-8000-000000000003?dataset=default HTTP/1.1
Accept: application/json
```

**Response:** 200 OK
```json
{
  "id": "01933e4a-9d4e-7000-8000-000000000003",
  "message": "Add new feature",
  "author": "Alice <alice@example.org>",
  "timestamp": "2025-10-24T15:30:00Z",
  "parents": ["01933e4a-8c3d-7000-8000-000000000002"],
  "patchSize": 42
}
```

**Headers:**
- `ETag: "01933e4a-9d4e-7000-8000-000000000003"` (strong, commit ID is immutable)

**Error Responses:**
- `400 Bad Request` - Missing required `dataset` parameter
- `404 Not Found` - Commit not found

**Parameters:**
- `dataset` (required) - Dataset name

---

## Design Decisions

### What's Included

✅ **Core commit metadata:**
- `id` - Commit ID (UUIDv7)
- `message` - Commit message
- `author` - Author string
- `timestamp` - Commit timestamp (ISO 8601)
- `parents` - List of parent commit IDs (for DAG traversal)
- `patchSize` - Number of RDF operations (monitoring/optimization)

### What's NOT Included

❌ **Branches/tags lists** - Excluded because:
- Requires O(n) scan through all branches/tags
- Not typical in Git-like systems (`git show` doesn't list branches)
- Data could be stale due to eventual consistency
- Clients can use `GET /version/refs` if needed

❌ **Detailed patch content** - Excluded because:
- Large payloads (patches can be huge)
- Separate endpoint would be better if needed
- Clients can reconstruct via diff/history APIs

---

## Implementation Steps

### Step 1: Create DTO

**File:** `src/main/java/org/chucc/vcserver/dto/CommitMetadataDto.java` (create new)

```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * DTO for commit metadata response.
 * Contains core commit information without branches/tags (use /version/refs for that).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitMetadataDto(
    String id,
    String message,
    String author,
    Instant timestamp,
    List<String> parents,
    Integer patchSize
) {}
```

### Step 2: Create Service

**File:** `src/main/java/org/chucc/vcserver/service/CommitService.java` (create new)

```java
package org.chucc.vcserver.service;

import java.util.Optional;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.CommitMetadataDto;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for commit-related operations.
 */
@Service
public class CommitService {

  private final CommitRepository commitRepository;

  /**
   * Constructs a CommitService.
   *
   * @param commitRepository the commit repository
   */
  public CommitService(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Retrieves commit metadata by dataset and commit ID.
   *
   * @param dataset the dataset name
   * @param commitId the commit ID string
   * @return an Optional containing the commit metadata if found
   */
  public Optional<CommitMetadataDto> getCommitMetadata(String dataset, String commitId) {
    return commitRepository.findByDatasetAndId(dataset, CommitId.of(commitId))
        .map(commit -> new CommitMetadataDto(
            commit.id().toString(),
            commit.message(),
            commit.author(),
            commit.timestamp(),
            commit.parents().stream()
                .map(CommitId::toString)
                .toList(),
            commit.patchSize()
        ));
  }
}
```

### Step 3: Update Controller

**File:** [CommitController.java](../../src/main/java/org/chucc/vcserver/controller/CommitController.java)

**Replace the stub method at line 253-260:**

```java
/**
 * Gets commit metadata.
 *
 * @param id the commit ID (UUIDv7)
 * @param dataset the dataset name (required)
 * @return the commit metadata or 404 if not found
 */
@Operation(
    summary = "Get commit metadata",
    description = "Retrieves metadata for a specific commit including message, author, "
        + "timestamp, parents, and patch size.",
    responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Commit metadata retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CommitMetadataDto.class)
            ),
            headers = @Header(
                name = "ETag",
                description = "Strong ETag with commit ID",
                schema = @Schema(type = "string")
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad Request - Missing dataset parameter",
            content = @Content(mediaType = "application/problem+json")
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Commit not found",
            content = @Content(mediaType = "application/problem+json")
        )
    }
)
@GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> getCommit(
    @Parameter(description = "Commit ID (UUIDv7)", required = true)
    @PathVariable String id,
    @Parameter(description = "Dataset name", required = true)
    @RequestParam String dataset
) {
  return commitService.getCommitMetadata(dataset, id)
      .<ResponseEntity<?>>map(metadata -> ResponseEntity.ok()
          .eTag("\"" + id + "\"")  // Strong ETag (commit is immutable)
          .body(metadata))
      .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Commit not found: " + id + " in dataset: " + dataset,
              404,
              "COMMIT_NOT_FOUND"
          )));
}
```

**Add field to controller:**

```java
@RestController
@RequestMapping("/version/commits")
public class CommitController {

  private final CommitService commitService;  // ← Add this

  public CommitController(CommitService commitService) {
    this.commitService = commitService;
  }

  // ... rest of controller
}
```

### Step 4: Write Tests

**File:** `src/test/java/org/chucc/vcserver/integration/CommitMetadataIT.java` (create new)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.testutil.IntegrationTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for GET /version/commits/{id} endpoint.
 * Tests API layer only (projector disabled).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CommitMetadataIT extends IntegrationTestFixture {

  @Test
  void getCommit_shouldReturn501_untilImplemented() {
    // This test will be replaced after implementation

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/01933e4a-9d4e-7000-8000-000000000003?dataset=default",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
  }

  @Test
  void getCommit_withoutDataset_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/01933e4a-9d4e-7000-8000-000000000003",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void getCommit_withNonExistentCommit_shouldReturn404() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/00000000-0000-0000-0000-000000000000?dataset=default",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("COMMIT_NOT_FOUND");
  }

  @Test
  void getCommit_shouldReturnMetadataWithETag() {
    // Arrange: Create commit via POST
    String commitId = createTestCommit("default", "main", "Test commit");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/" + commitId + "?dataset=default",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commitId + "\"");
    assertThat(response.getBody())
        .contains("\"id\":\"" + commitId + "\"")
        .contains("\"message\":\"Test commit\"")
        .contains("\"author\":")
        .contains("\"timestamp\":")
        .contains("\"parents\":")
        .contains("\"patchSize\":");
  }

  @Test
  void getCommit_shouldIncludeParents() {
    // Arrange: Create two commits (second has parent)
    String commit1 = createTestCommit("default", "main", "First commit");
    String commit2 = createTestCommit("default", "main", "Second commit");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/" + commit2 + "?dataset=default",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("\"parents\":[\"" + commit1 + "\"]");
  }
}
```

**Helper method in IntegrationTestFixture:**

```java
protected String createTestCommit(String dataset, String branch, String message) {
  String patch = "A <http://ex.org/s> <http://ex.org/p> \"value\" .";

  ResponseEntity<String> response = restTemplate.exchange(
      "/version/commits?dataset=" + dataset + "&branch=" + branch,
      HttpMethod.POST,
      new HttpEntity<>(patch, createHeaders("application/rdf-patch", null)),
      String.class
  );

  return extractCommitIdFromResponse(response);
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/service/CommitServiceTest.java`

```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.CommitMetadataDto;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommitServiceTest {

  @Mock
  private CommitRepository commitRepository;

  @InjectMocks
  private CommitService commitService;

  @Test
  void getCommitMetadata_shouldReturnDto() {
    // Arrange
    CommitId id = CommitId.generate();
    CommitId parent = CommitId.generate();
    Commit commit = new Commit(
        id,
        List.of(parent),
        "Author <author@example.org>",
        "Test message",
        Instant.parse("2025-10-24T12:00:00Z"),
        42
    );

    when(commitRepository.findByDatasetAndId("default", id))
        .thenReturn(Optional.of(commit));

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "default",
        id.toString()
    );

    // Assert
    assertThat(result).isPresent();
    CommitMetadataDto dto = result.get();
    assertThat(dto.id()).isEqualTo(id.toString());
    assertThat(dto.message()).isEqualTo("Test message");
    assertThat(dto.author()).isEqualTo("Author <author@example.org>");
    assertThat(dto.timestamp()).isEqualTo(Instant.parse("2025-10-24T12:00:00Z"));
    assertThat(dto.parents()).containsExactly(parent.toString());
    assertThat(dto.patchSize()).isEqualTo(42);
  }

  @Test
  void getCommitMetadata_withNonExistentCommit_shouldReturnEmpty() {
    // Arrange
    CommitId id = CommitId.generate();
    when(commitRepository.findByDatasetAndId("default", id))
        .thenReturn(Optional.empty());

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "default",
        id.toString()
    );

    // Assert
    assertThat(result).isEmpty();
  }
}
```

---

## Success Criteria

- ✅ Endpoint implemented (no 501 response)
- ✅ `CommitMetadataDto` created
- ✅ `CommitService` implemented (read-only, no CQRS needed)
- ✅ Controller updated with proper OpenAPI annotations
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (service layer)
- ✅ ETag header included (strong, immutable)
- ✅ `dataset` parameter is required (400 if missing)
- ✅ Zero quality violations
- ✅ Full build passes: `mvn -q clean install`

---

## Notes

### Why This is Simple

- **Read-only operation** - No commands/events needed
- **No CQRS complexity** - Just queries CommitRepository
- **Strong ETag** - Commits are immutable (perfect for caching)
- **No expensive lookups** - Direct repository query by ID

### Why branches/tags Are Excluded

Per design review, branches/tags lists were removed because:
1. **Performance**: Requires O(n) iteration through all refs
2. **Not standard**: Git doesn't include this in `git show`
3. **Alternative exists**: Clients can use `GET /version/refs`
4. **Eventual consistency**: Data could be stale

### Cache-Friendly Design

Commits are **immutable**, making this endpoint perfect for HTTP caching:
- Strong ETag enables conditional requests (`If-None-Match`)
- Clients can cache indefinitely (commit never changes)
- CDNs can cache responses

---

## Files to Create/Modify

### Create
- `src/main/java/org/chucc/vcserver/dto/CommitMetadataDto.java`
- `src/main/java/org/chucc/vcserver/service/CommitService.java`
- `src/test/java/org/chucc/vcserver/service/CommitServiceTest.java`
- `src/test/java/org/chucc/vcserver/integration/CommitMetadataIT.java`

### Modify
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`
- `src/test/java/org/chucc/vcserver/testutil/IntegrationTestFixture.java` (add helper)

---

## References

- [Commit.java](../../src/main/java/org/chucc/vcserver/domain/Commit.java)
- [CommitController.java](../../src/main/java/org/chucc/vcserver/controller/CommitController.java)
- [CommitRepository.java](../../src/main/java/org/chucc/vcserver/repository/CommitRepository.java)
- [SPARQL 1.2 Protocol VC Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

**Blocked By:** [00-add-patchsize-to-commit-entity.md](./00-add-patchsize-to-commit-entity.md)
