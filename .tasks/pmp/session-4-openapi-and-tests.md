# Session 4: OpenAPI Documentation and Comprehensive Testing

**Status:** Not Started
**Estimated Time:** 2-3 hours
**Priority:** Medium
**Dependencies:** Sessions 1-3

---

## Overview

Complete OpenAPI documentation, add comprehensive error handling tests, and validate cross-protocol integration.

**Goal:** Production-ready documentation and test coverage.

---

## Tasks

### 1. OpenAPI Documentation (60 minutes)

Add to existing OpenAPI spec:

```yaml
/version/datasets/{dataset}/branches/{branch}/prefixes:
  get:
    summary: Retrieve prefix mappings for a branch
    operationId: getPrefixes
    tags: [Prefix Management]
    parameters:
      - $ref: '#/components/parameters/DatasetParam'
      - $ref: '#/components/parameters/BranchParam'
    responses:
      '200':
        description: Prefix map retrieved
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PrefixResponse'
      '404':
        $ref: '#/components/responses/NotFound'

  put:
    summary: Replace entire prefix map (creates commit)
    operationId: replacePrefixes
    tags: [Prefix Management]
    parameters:
      - $ref: '#/components/parameters/DatasetParam'
      - $ref: '#/components/parameters/BranchParam'
      - $ref: '#/components/parameters/AuthorHeader'
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/UpdatePrefixesRequest'
    responses:
      '201':
        description: Commit created
        headers:
          Location:
            schema:
              type: string
          ETag:
            schema:
              type: string
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CommitResponse'
      '400':
        $ref: '#/components/responses/BadRequest'
      '403':
        $ref: '#/components/responses/ProtectedBranch'
      '404':
        $ref: '#/components/responses/NotFound'

  # Add PATCH, DELETE similarly...

components:
  schemas:
    PrefixResponse:
      type: object
      required: [dataset, commitId, prefixes]
      properties:
        dataset:
          type: string
        branch:
          type: string
          nullable: true
        commitId:
          type: string
        prefixes:
          type: object
          additionalProperties:
            type: string

    UpdatePrefixesRequest:
      type: object
      required: [prefixes]
      properties:
        message:
          type: string
        prefixes:
          type: object
          additionalProperties:
            type: string
```

---

### 2. Error Handling Tests (60 minutes)

```java
@Test
void putPrefixes_shouldReturn403_whenBranchProtected() {
  // Arrange: Protect main branch
  protectBranch("default", "main");

  // Act
  ResponseEntity<String> response = putPrefixes(...);

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}

@Test
void putPrefixes_shouldReturn400_whenInvalidPrefixName() {
  UpdatePrefixesRequest request = new UpdatePrefixesRequest(
      null,
      Map.of("1invalid", "http://example.org/")  // Starts with number
  );

  // Act & Assert
  assertThat(putPrefixes(request).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
}

@Test
void putPrefixes_shouldReturn400_whenRelativeIRI() {
  UpdatePrefixesRequest request = new UpdatePrefixesRequest(
      null,
      Map.of("ex", "../relative")  // Not absolute
  );

  // Act & Assert
  assertThat(putPrefixes(request).getStatusCode())
      .isEqualTo(HttpStatus.BAD_REQUEST);
}
```

---

### 3. Cross-Protocol Integration Tests (30 minutes)

```java
@Test
void prefixesShouldPersistAfterGraphOperations() {
  // Arrange: Define prefixes
  definePrefixes(Map.of("foaf", "http://xmlns.com/foaf/0.1/"));

  // Act: Perform GSP operation
  putGraph("http://example.org/g1", "<data>");

  // Assert: Prefixes still present
  assertThat(getPrefixes().prefixes()).containsKey("foaf");
}

@Test
void prefixesShouldMergeAutomatically() {
  // Arrange: Create branch with different prefixes
  createBranch("dev");
  definePrefixesOnBranch("dev", Map.of("geo", "http://..."));

  // Act: Merge
  mergeBranch("dev", "main");

  // Assert: Both prefix sets present
  PrefixResponse mainPrefixes = getPrefixesOnBranch("main");
  assertThat(mainPrefixes.prefixes()).containsKey("geo");
}
```

---

## Files to Modify

```
docs/openapi/
  └── prefix-management.yaml  # NEW or add to existing

src/test/java/org/chucc/vcserver/integration/
  └── PrefixManagementIT.java  # Add 10+ validation tests
```

---

## Success Criteria

- ✅ Complete OpenAPI documentation
- ✅ All error cases covered (400, 403, 404)
- ✅ Cross-protocol tests pass
- ✅ Validation tests pass

---

**Next:** [Session 5: Merge Conflict Handling](./session-5-merge-conflict-handling.md) (Optional)
