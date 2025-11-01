# Task: Implement Diff Endpoint

**Status:** ✅ Completed
**Priority:** Medium
**Category:** Version Control Protocol (EXTENSION)
**Estimated Time:** 1 hour

---

## Overview

Implement the diff endpoint that currently returns 501:
- `GET /version/diff` - Get RDF Patch representing changes between two commits

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md) (EXTENSION - not in official spec)

**Note:** This is an **EXTENSION** endpoint. The official SPARQL 1.2 Protocol does not define diff operations.

---

## Current State

**Controller:** [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)

**Not Implemented (returns 501):**
- ❌ `GET /version/diff` (line 138)

**Existing Infrastructure:**
- ✅ `DatasetService.materializeCommit(dataset, commitId)` - Loads commit snapshots (line 532)
- ✅ `RdfPatchUtil.diff(DatasetGraph, DatasetGraph)` - Computes diff as RDF Patch (line 134)
- ✅ `RDFPatchOps.write(OutputStream, RDFPatch)` - Serializes RDF Patch to text format

**Feature Flag:**
- `vcProperties.isDiffEnabled()` - Must be true for endpoint to work (line 128)

---

## Requirements

### GET /version/diff - Diff Two Commits

**Request:**
```http
GET /version/diff?dataset=mydata&from=01933e4a-7b2c-7000-8000-000000000001&to=01933e4a-9d4e-7000-8000-000000000003 HTTP/1.1
Accept: text/rdf-patch
```

**Query Parameters:**
- `dataset` (required) - Dataset name
- `from` (required) - From commit ID (UUIDv7)
- `to` (required) - To commit ID (UUIDv7)

**Response:** 200 OK
```
H id <urn:uuid:01933e4a-9d4e-7000-8000-000000000003> .
H prev <urn:uuid:01933e4a-7b2c-7000-8000-000000000001> .
TC .
PA <http://example.org/g> .
A <http://example.org/subject1> <http://example.org/pred1> "value1" .
D <http://example.org/subject2> <http://example.org/pred2> "value2" .
TC .
```

**Semantics:**
- Returns RDF Patch representing: `state[to] - state[from]`
- Additions: Quads in `to` but not in `from`
- Deletions: Quads in `from` but not in `to`
- Works for any two commits (not just parent-child)

**Error Responses:**
- `400 Bad Request` - Missing dataset, from, or to parameter
- `404 Not Found` - Diff endpoint disabled (feature flag)
- `404 Not Found` - Either commit not found

**Headers:**
- `Content-Type: text/rdf-patch`

---

## Design Decisions

**Snapshot Strategy:**
- Use `DatasetService.materializeCommit()` to load full dataset state at each commit
- Leverages existing snapshot optimization (lines 351-375 in DatasetService.java)
- Uses Caffeine cache for historical commits (lines 304-321)
- No need for additional caching

**Diff Algorithm:**
- Use existing `RdfPatchUtil.diff(DatasetGraph, DatasetGraph)` (lines 134-178)
- Compares default graph + all named graphs
- Handles blank nodes correctly (triple-by-triple comparison)

**Commit Relationship:**
- Diff works for ANY two commits (not required to be related)
- No validation of parent-child relationship
- No validation of branch membership

---

## Implementation Steps

### Step 1: Implement Service

**New File:** `src/main/java/org/chucc/vcserver/service/DiffService.java`

```java
@Service
public class DiffService {
  private final DatasetService datasetService;
  private final CommitRepository commitRepository;

  public DiffService(DatasetService datasetService, CommitRepository commitRepository) {
    this.datasetService = datasetService;
    this.commitRepository = commitRepository;
  }

  /**
   * Computes the RDF Patch representing changes from 'from' commit to 'to' commit.
   *
   * @param dataset the dataset name
   * @param fromCommitId the source commit ID
   * @param toCommitId the target commit ID
   * @return RDF Patch as string (text/rdf-patch format)
   * @throws CommitNotFoundException if either commit is not found
   */
  public String diffCommits(String dataset, CommitId fromCommitId, CommitId toCommitId) {
    // Validate commits exist
    if (!commitRepository.exists(dataset, fromCommitId)) {
      throw new CommitNotFoundException(
          "Commit not found: " + fromCommitId.value(), true);
    }
    if (!commitRepository.exists(dataset, toCommitId)) {
      throw new CommitNotFoundException(
          "Commit not found: " + toCommitId.value(), true);
    }

    // Load dataset states at both commits
    DatasetGraph fromDataset = datasetService.materializeCommit(dataset, fromCommitId);
    DatasetGraph toDataset = datasetService.materializeCommit(dataset, toCommitId);

    // Compute diff using existing utility
    RDFPatch patch = RdfPatchUtil.diff(fromDataset, toDataset);

    // Serialize to RDF Patch text format
    return serializePatch(patch);
  }

  /**
   * Serializes an RDF Patch to text format.
   */
  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFPatchOps.write(out, patch);
    return out.toString(StandardCharsets.UTF_8);
  }
}
```

### Step 2: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

Replace the 501 stub method (lines 138-140) with:

```java
public ResponseEntity<String> diffCommits(
    @RequestParam String dataset,  // Add dataset parameter
    @RequestParam String from,
    @RequestParam String to
) {
  // Check feature flag
  if (!vcProperties.isDiffEnabled()) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(
            "Diff endpoint is disabled",
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND"
        ).toString());
  }

  // Validate parameters
  if (dataset == null || dataset.isBlank()) {
    throw new IllegalArgumentException("Dataset parameter is required");
  }

  try {
    // Parse commit IDs
    CommitId fromCommitId = CommitId.of(from);
    CommitId toCommitId = CommitId.of(to);

    // Call service
    String patchText = diffService.diffCommits(dataset, fromCommitId, toCommitId);

    // Return RDF Patch
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/rdf-patch"))
        .body(patchText);

  } catch (IllegalArgumentException e) {
    throw new IllegalArgumentException("Invalid commit ID format", e);
  } catch (CommitNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(
            e.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND"
        ).toString());
  }
}
```

### Step 3: Update OpenAPI Annotations

Update the `@GetMapping` annotation to include dataset parameter:

```java
@GetMapping(value = "/diff", produces = "text/rdf-patch")
@Operation(
    summary = "Diff two commits",
    description = "Get RDF Patch representing changes from 'from' commit to 'to' commit. "
        + "⚠️ EXTENSION: This endpoint is not part of the official "
        + "SPARQL 1.2 Protocol specification."
)
@Parameter(name = "dataset", description = "Dataset name", required = true)
@Parameter(name = "from", description = "From commit ID (UUIDv7)", required = true)
@Parameter(name = "to", description = "To commit ID (UUIDv7)", required = true)
```

### Step 4: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/DiffEndpointIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class DiffEndpointIT extends IntegrationTestFixture {

  @Test
  void diffCommits_shouldReturnRdfPatch() {
    // Arrange: Create dataset with 2 commits
    String dataset = "test-diff";
    String commit1 = createCommitWithTriples(dataset, List.of(
        new Triple("http://ex.org/s1", "http://ex.org/p1", "v1")
    ));
    String commit2 = createCommitWithTriples(dataset, List.of(
        new Triple("http://ex.org/s1", "http://ex.org/p1", "v1"),
        new Triple("http://ex.org/s2", "http://ex.org/p2", "v2")  // Added
    ));

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        String.format("/version/diff?dataset=%s&from=%s&to=%s", dataset, commit1, commit2),
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .isEqualTo("text/rdf-patch");

    String patchText = response.getBody();
    assertThat(patchText).contains("A <http://ex.org/s2> <http://ex.org/p2> \"v2\"");
    assertThat(patchText).doesNotContain("D "); // No deletions
  }

  @Test
  void diffCommits_withDeletions_shouldIncludeDeleteOperations() {
    // Test that deletions appear in patch
  }

  @Test
  void diffCommits_sameCommit_shouldReturnEmptyPatch() {
    // Diff commit with itself should have no operations
  }

  @Test
  void diffCommits_nonExistentCommit_shouldReturn404() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/diff?dataset=test&from=invalid&to=invalid",
        HttpMethod.GET,
        null,
        String.class
    );
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void diffCommits_missingDataset_shouldReturn400() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/diff?from=abc&to=def",
        HttpMethod.GET,
        null,
        String.class
    );
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void diffCommits_featureDisabled_shouldReturn404() {
    // Mock vcProperties.isDiffEnabled() = false
    // Verify 404 response
  }
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/service/DiffServiceTest.java`

Test the service logic independently (commit validation, patch generation).

---

## Success Criteria

- ✅ Endpoint implemented (no 501 responses)
- ✅ Service layer implemented (read-only)
- ✅ Integration tests pass (6+ test cases)
- ✅ RDF Patch serialization works correctly
- ✅ Feature flag check works (returns 404 when disabled)
- ✅ Proper error handling (404 for missing commits, 400 for invalid params)
- ✅ Content-Type header set to `text/rdf-patch`
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

**Pattern:** API Layer Test (projector DISABLED)
- Test HTTP status codes and headers
- Verify RDF Patch format
- Do NOT query repositories (projector disabled)

**Example Test Structure:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class DiffEndpointIT extends IntegrationTestFixture {
  // Projector DISABLED by default
  // Only test API responses
}
```

---

## Notes

- This is a **read-only** operation (no commands/events needed)
- Leverages existing DatasetService snapshot infrastructure
- Uses Caffeine cache for performance (historical commits)
- Diff works for any two commits (no relationship required)
- This is an **EXTENSION** - not part of official SPARQL 1.2 spec
- Feature flag: `chucc.version-control.diff-enabled=true` (default: true)

---

## Performance Characteristics

**Time Complexity:**
- O(1) if both commits cached
- O(n) if commits need materialization (n = patches to replay)
- O(m) for diff computation (m = total quads in both commits)

**Space Complexity:**
- O(m) for two dataset states in memory

**Optimization:**
- Snapshots reduce replay cost (start from nearest snapshot)
- Cache reduces repeated diff operations

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)
- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) (line 532)
- [RdfPatchUtil.java](../../src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java) (line 134)
- [Apache Jena RDFPatch](https://jena.apache.org/documentation/rdfpatch/)
