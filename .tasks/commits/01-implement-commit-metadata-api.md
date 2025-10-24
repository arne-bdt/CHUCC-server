# Task: Implement Commit Metadata Endpoint

**Status:** Not Started
**Priority:** Medium
**Category:** Version Control Protocol
**Estimated Time:** 2-3 hours

---

## Overview

Implement the missing Commit Metadata endpoint that currently returns 501:
- `GET /version/commits/{id}` - Get commit metadata

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [CommitController.java](../../src/main/java/org/chucc/vcserver/controller/CommitController.java)

**Implemented:**
- ✅ `POST /version/commits` - Create commit (line 64)

**Not Implemented (returns 501):**
- ❌ `GET /version/commits/{id}` (line 257)

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
  "branches": ["main", "feature-x"],
  "tags": ["v1.0.0"],
  "patchSize": 42
}
```

**Headers:**
- `ETag: "01933e4a-9d4e-7000-8000-000000000003"` (strong, commit ID is immutable)

**Error Responses:**
- `404 Not Found` - Commit not found

**Implementation:**
- Service: `CommitService.getCommitMetadata(String dataset, String commitId)`
- Returns `Optional<CommitMetadata>`
- Query `CommitRepository` for commit details
- Optionally include branches/tags that point to this commit

---

## Implementation Steps

### Step 1: Create DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/CommitMetadata.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Commit metadata DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitMetadata(
    String id,
    String message,
    String author,
    Instant timestamp,
    List<String> parents,
    List<String> branches,
    List<String> tags,
    Integer patchSize
) {}
```

### Step 2: Implement Service

**File:** `src/main/java/org/chucc/vcserver/service/CommitService.java`

Add method:

```java
public Optional<CommitMetadata> getCommitMetadata(String dataset, String commitId) {
  return commitRepository.findById(dataset, commitId)
      .map(commit -> {
        // Find branches pointing to this commit
        List<String> branches = branchRepository.findByHeadCommit(dataset, commitId)
            .stream()
            .map(Branch::getName)
            .toList();

        // Find tags pointing to this commit
        List<String> tags = tagRepository.findByTargetCommit(dataset, commitId)
            .stream()
            .map(Tag::getName)
            .toList();

        return new CommitMetadata(
            commit.getId(),
            commit.getMessage(),
            commit.getAuthor(),
            commit.getTimestamp(),
            commit.getParents(),
            branches.isEmpty() ? null : branches,
            tags.isEmpty() ? null : tags,
            commit.getPatchSize()
        );
      });
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/CommitController.java`

Replace the 501 stub method:

```java
@GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> getCommit(
    @Parameter(description = "Commit id (UUIDv7)", required = true)
    @PathVariable String id,
    @Parameter(description = "Dataset name")
    @RequestParam(defaultValue = "default") String dataset
) {
  return commitService.getCommitMetadata(dataset, id)
      .<ResponseEntity<?>>map(metadata -> ResponseEntity.ok()
          .eTag("\"" + id + "\"")
          .body(metadata))
      .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Commit not found: " + id,
              404,
              "COMMIT_NOT_FOUND"
          )));
}
```

### Step 4: Update Repository (if needed)

Add methods to repositories if they don't exist:
- `BranchRepository.findByHeadCommit(dataset, commitId)`
- `TagRepository.findByTargetCommit(dataset, commitId)`

### Step 5: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/CommitControllerIT.java`

```java
@Test
void getCommit_shouldReturnCommitMetadata() {
  // Arrange: Create commit via POST
  String commitId = createTestCommit();

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
  assertThat(response.getBody()).contains(commitId);
}

@Test
void getCommit_withInvalidId_shouldReturn404() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/commits/00000000-0000-0000-0000-000000000000?dataset=default",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

---

## Success Criteria

- ✅ Endpoint implemented (no 501 response)
- ✅ DTO created
- ✅ Service layer implemented (read-only)
- ✅ Integration tests pass
- ✅ ETag header included (strong, immutable)
- ✅ Branches/tags list included if available
- ✅ Zero quality violations
- ✅ Full build passes

---

## Notes

- This is a **read-only** operation (no commands/events needed)
- Commit IDs are **immutable** (perfect for strong ETags)
- Including branches/tags is optional but useful

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [CommitController.java](../../src/main/java/org/chucc/vcserver/controller/CommitController.java)
- [CommitService.java](../../src/main/java/org/chucc/vcserver/service/CommitService.java)
