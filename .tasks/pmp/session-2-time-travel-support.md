# Session 2: Time-Travel Prefix Queries

**Status:** Not Started
**Estimated Time:** 1.5-2 hours
**Priority:** Medium
**Dependencies:** Session 1 (Core Implementation)

---

## Overview

Add ability to query prefixes at specific commits (historical queries). Enables users to see what prefixes existed at any point in history.

**Goal:** Support `GET /version/datasets/{name}/commits/{id}/prefixes` for time-travel queries.

**Key Insight:** Reuses existing `MaterializedViewRebuildService` - minimal new code needed!

---

## Requirements

### Endpoint to Implement

```http
GET /version/datasets/{dataset}/commits/{commitId}/prefixes
Accept: application/json

→ 200 OK
{
  "dataset": "mydata",
  "commitId": "01JCDN2XYZ...",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

**Note:** No `branch` field (this is a commit query, not branch query).

---

## Implementation Steps

### Step 0: Write Tests First (TDD) (45 minutes)

**Add to `PrefixManagementIT.java`:**

#### Test 1: Happy Path - Historical Prefixes
```java
@Test
void getPrefixesAtCommit_shouldReturnHistoricalPrefixes() {
  // Arrange: Create commit with specific prefixes
  String commitId = createPrefixCommit(new UpdatePrefixesRequest(
      "Add historical prefixes",
      Map.of("old", "http://example.org/old/")
  ));

  // Act: Query prefixes at that commit
  ResponseEntity<PrefixResponse> response = restTemplate.exchange(
      "/version/datasets/" + DATASET_NAME + "/commits/" + commitId + "/prefixes",
      HttpMethod.GET,
      null,
      PrefixResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commitId + "\"");
  assertThat(response.getBody()).isNotNull();
  assertThat(response.getBody().dataset()).isEqualTo(DATASET_NAME);
  assertThat(response.getBody().commitId()).isEqualTo(commitId);
  assertThat(response.getBody().branch()).isNull();  // No branch (commit query)
  assertThat(response.getBody().prefixes()).containsEntry("old", "http://example.org/old/");
}
```

#### Test 2: Error - Commit Not Found
```java
@Test
void getPrefixesAtCommit_shouldReturn404_whenCommitNotFound() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/datasets/" + DATASET_NAME + "/commits/nonexistent-commit/prefixes",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

#### Test 3: Error - Dataset Not Found
```java
@Test
void getPrefixesAtCommit_shouldReturn404_whenDatasetNotFound() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/datasets/nonexistent-dataset/commits/any-commit/prefixes",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

#### Test 4: Multi-Commit History
```java
@Test
void getPrefixesAtCommit_shouldReturnDifferentPrefixes_forDifferentCommits() {
  // Arrange: Create two commits with different prefixes
  String commitId1 = createPrefixCommit(new UpdatePrefixesRequest(
      "Version 1 prefixes",
      Map.of("v1", "http://example.org/v1/")
  ));

  String commitId2 = createPrefixCommit(new UpdatePrefixesRequest(
      "Version 2 prefixes",
      Map.of("v2", "http://example.org/v2/")
  ));

  // Act: Query both commits
  PrefixResponse response1 = getPrefixesAtCommit(commitId1);
  PrefixResponse response2 = getPrefixesAtCommit(commitId2);

  // Assert: Each commit has correct prefixes
  assertThat(response1.prefixes()).containsKey("v1");
  assertThat(response1.prefixes()).doesNotContainKey("v2");

  assertThat(response2.prefixes()).containsKey("v1");  // Inherited
  assertThat(response2.prefixes()).containsKey("v2");  // Added
}
```

#### Test 5: Initial Commit (Empty Prefixes)
```java
@Test
void getPrefixesAtCommit_shouldReturnEmptyMap_forInitialCommit() {
  // Arrange: Get initial commit ID
  ResponseEntity<BranchResponse> branchResponse = restTemplate.getForEntity(
      "/version/datasets/" + DATASET_NAME + "/branches/main",
      BranchResponse.class
  );
  String initialCommitId = branchResponse.getBody().commitId();

  // Act
  PrefixResponse response = getPrefixesAtCommit(initialCommitId);

  // Assert: Initial commit has no prefixes
  assertThat(response.prefixes()).isEmpty();
}
```

#### Test 6: Prefix Deletions
```java
@Test
void getPrefixesAtCommit_shouldHandlePrefixDeletions() {
  // Arrange: Add prefix, then delete it
  String addCommitId = createPrefixCommit(new UpdatePrefixesRequest(
      "Add temp prefix",
      Map.of("temp", "http://example.org/temp/")
  ));

  String deleteCommitId = deletePrefixCommit("temp");

  // Act
  PrefixResponse afterAdd = getPrefixesAtCommit(addCommitId);
  PrefixResponse afterDelete = getPrefixesAtCommit(deleteCommitId);

  // Assert
  assertThat(afterAdd.prefixes()).containsKey("temp");
  assertThat(afterDelete.prefixes()).doesNotContainKey("temp");
}
```

#### Test 7: Caching Behavior (Functional Test)
```java
@Test
void getPrefixesAtCommit_shouldWorkConsistently_regardlessOfCaching() {
  // Arrange: Create commit
  String commitId = createPrefixCommit(new UpdatePrefixesRequest(
      "Cache test",
      Map.of("cache", "http://example.org/cache/")
  ));

  // Act: Query multiple times
  PrefixResponse response1 = getPrefixesAtCommit(commitId);
  PrefixResponse response2 = getPrefixesAtCommit(commitId);

  // Assert: Results should be identical (regardless of cache)
  assertThat(response1).isEqualTo(response2);
  assertThat(response1.prefixes()).containsEntry("cache", "http://example.org/cache/");
}
```

#### Helper Methods
```java
/**
 * Creates a commit with given prefixes and returns commit ID.
 */
private String createPrefixCommit(UpdatePrefixesRequest request) {
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.APPLICATION_JSON);
  headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
  HttpEntity<UpdatePrefixesRequest> httpEntity = new HttpEntity<>(request, headers);

  ResponseEntity<CommitResponse> response = restTemplate.exchange(
      "/version/datasets/" + DATASET_NAME + "/branches/main/prefixes",
      HttpMethod.PUT,
      httpEntity,
      CommitResponse.class
  );

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  return response.getBody().commitId();
}

/**
 * Queries prefixes at specific commit.
 */
private PrefixResponse getPrefixesAtCommit(String commitId) {
  ResponseEntity<PrefixResponse> response = restTemplate.exchange(
      "/version/datasets/" + DATASET_NAME + "/commits/" + commitId + "/prefixes",
      HttpMethod.GET,
      null,
      PrefixResponse.class
  );

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  return response.getBody();
}

/**
 * Deletes a prefix and returns commit ID.
 */
private String deletePrefixCommit(String prefix) {
  HttpHeaders headers = new HttpHeaders();
  headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
  HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

  ResponseEntity<CommitResponse> response = restTemplate.exchange(
      "/version/datasets/" + DATASET_NAME + "/branches/main/prefixes?prefix=" + prefix,
      HttpMethod.DELETE,
      httpEntity,
      CommitResponse.class
  );

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  return response.getBody().commitId();
}
```

**Run tests - they should FAIL (endpoint doesn't exist yet)**

---

### Step 1: Add Endpoint to Controller (20 minutes)

```java
/**
 * Retrieves prefix mappings at a specific commit (time-travel query).
 *
 * <p>This endpoint allows querying historical prefix states by rebuilding
 * the dataset at the specified commit. The operation leverages the materialized
 * view cache for performance.</p>
 *
 * <p><strong>Caching Behavior:</strong></p>
 * <ul>
 *   <li>If commit is in cache (LRU, max 100 entries): ~10ms response time</li>
 *   <li>If commit evicted: Rebuilt on-demand (~1s typical)</li>
 *   <li>Cache is shared with other time-travel operations</li>
 * </ul>
 *
 * @param dataset the dataset name
 * @param commitId the commit ID (UUIDv7 format)
 * @return prefix response with no branch field (commit query)
 * @throws DatasetNotFoundException if dataset doesn't exist
 * @throws CommitNotFoundException if commit doesn't exist
 */
@Operation(
    summary = "Get prefixes at specific commit",
    description = "Retrieves historical prefix mappings by time-traveling to a specific commit. "
        + "Response does not include branch field since this is a commit-based query."
)
@ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Prefixes retrieved successfully",
        headers = @Header(name = "ETag", description = "Commit ID as entity tag")
    ),
    @ApiResponse(
        responseCode = "404",
        description = "Dataset or commit not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
})
@GetMapping("/commits/{commitId}/prefixes")
public ResponseEntity<PrefixResponse> getPrefixesAtCommit(
    @Parameter(description = "Dataset name", example = "mydata")
    @PathVariable String dataset,
    @Parameter(description = "Commit ID (UUIDv7)", example = "01JCDN2XYZ...")
    @PathVariable String commitId) {

  try {
    // Rebuild dataset at specific commit
    DatasetGraph dsg = materializedViewRebuildService
        .rebuildAtCommit(dataset, commitId);

    // Extract prefixes from default graph
    Map<String, String> prefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    PrefixResponse response = new PrefixResponse(
        dataset,
        null,  // No branch (commit query)
        commitId,
        prefixes
    );

    return ResponseEntity
        .ok()
        .eTag(commitId)
        .body(response);

  } catch (DatasetNotFoundException e) {
    throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Dataset not found: " + dataset,
        e
    );
  } catch (CommitNotFoundException e) {
    throw new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Commit not found: " + commitId,
        e
    );
  }
}
```

---

### Step 2: Add Dependency Injection (5 minutes)

Update constructor in `PrefixManagementController`:

```java
private final MaterializedBranchRepository materializedBranchRepository;
private final BranchRepository branchRepository;
private final UpdatePrefixesCommandHandler updatePrefixesCommandHandler;
private final MaterializedViewRebuildService materializedViewRebuildService;  // NEW

/**
 * Constructs a new PrefixManagementController.
 *
 * @param materializedBranchRepository repository for materialized branches
 * @param branchRepository repository for branch metadata
 * @param updatePrefixesCommandHandler handler for prefix updates
 * @param materializedViewRebuildService service for time-travel queries
 */
public PrefixManagementController(
    MaterializedBranchRepository materializedBranchRepository,
    BranchRepository branchRepository,
    UpdatePrefixesCommandHandler updatePrefixesCommandHandler,
    MaterializedViewRebuildService materializedViewRebuildService) {  // NEW
  this.materializedBranchRepository = materializedBranchRepository;
  this.branchRepository = branchRepository;
  this.updatePrefixesCommandHandler = updatePrefixesCommandHandler;
  this.materializedViewRebuildService = materializedViewRebuildService;  // NEW
}
```

Add import:
```java
import org.chucc.vcserver.service.MaterializedViewRebuildService;
```

---

### Step 3: Run Tests - They Should Pass! (10 minutes)

```bash
mvn -q test -Dtest=PrefixManagementIT#getPrefixesAtCommit* 2>&1 | tail -20
```

**Expected:** All 7 tests pass

---

### Step 4: Static Analysis (5 minutes)

```bash
mvn -q compile checkstyle:check spotbugs:check pmd:check
```

**Expected:** Zero violations

---

## Success Criteria

### Functional Requirements
- ✅ GET `/commits/{id}/prefixes` returns historical prefixes
- ✅ Response includes `commitId`, no `branch` field
- ✅ Works for any commit (cached or not)
- ✅ Returns 404 if commit doesn't exist
- ✅ Returns 404 if dataset doesn't exist
- ✅ Handles initial commit (empty prefixes)
- ✅ Handles prefix deletions correctly
- ✅ ETag header set to commit ID

### Quality Requirements
- ✅ All 7 tests pass
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ OpenAPI documentation included
- ✅ Helper methods for test readability

### Performance Characteristics
- ✅ Cached commits: ~10ms (in-memory lookup)
- ✅ Uncached commits: ~1s (rebuild on-demand)
- ✅ Cache eviction: LRU with max 100 entries
- ✅ No memory leaks (validated by existing cache tests)

**Note:** Performance tests are deliberately omitted to avoid flakiness. Cache behavior is validated functionally instead.

---

## Files to Modify

```
src/main/java/org/chucc/vcserver/controller/
  └── PrefixManagementController.java  # Add getPrefixesAtCommit() + DI

src/test/java/org/chucc/vcserver/integration/
  └── PrefixManagementIT.java          # Add 7 tests + 3 helper methods
```

---

## Error Handling

### Exception Mapping

| Exception | HTTP Status | Scenario |
|-----------|-------------|----------|
| `DatasetNotFoundException` | 404 | Dataset doesn't exist |
| `CommitNotFoundException` | 404 | Commit doesn't exist |
| Runtime exceptions | 500 | Unexpected errors (logged) |

### Example Error Response

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Commit not found: 01JCDN2XYZ...",
  "instance": "/version/datasets/mydata/commits/01JCDN2XYZ.../prefixes"
}
```

---

## Caching Implementation Details

### How Caching Works

The `MaterializedViewRebuildService` maintains an LRU cache:

```java
// Configuration (application.yml)
chucc:
  materialized-views:
    cache:
      max-size: 100
      eviction-policy: LRU
```

### Cache Key Structure
```
dataset:commitId → DatasetGraph
```

### Cache Hit Scenarios
- Recently accessed commits (within last 100 accessed)
- Commits on active branches (frequently queried)
- Commits involved in recent merges/rebases

### Cache Miss Scenarios
- Old commits (evicted due to LRU)
- First access to any commit
- After cache cleared (restart)

### Rebuild Process (on cache miss)
1. Load commit from `CommitRepository`
2. Load all ancestor commits (walk commit graph)
3. Apply patches sequentially (includes PA/PD directives)
4. Store result in cache
5. Return `DatasetGraph`

**Typical Performance:**
- 10 commits: ~100ms
- 100 commits: ~1s
- 1000 commits: ~10s

---

## Testing Notes

### Why No Performance Tests?

Performance tests using `System.currentTimeMillis()` are **intentionally omitted** because:

1. **Flakiness:** CI environments have variable load
2. **Non-determinism:** Cache state depends on test execution order
3. **False negatives:** Slow machines != bugs

Instead, we validate:
- **Correctness:** Results are accurate (cached or not)
- **Consistency:** Multiple queries return identical results
- **Robustness:** Works regardless of cache state

### Performance Monitoring

If performance concerns arise:
- Enable JMX metrics (cache hit/miss ratio)
- Use profiler (JFR) for production analysis
- Check logs for slow queries (>2s logged as WARNING)

---

## Next Steps

After completing:
1. ✅ Run full build: `mvn -q clean install`
2. ✅ Verify all tests pass (~1385 tests total)
3. ✅ Create commit with conventional message
4. ✅ Proceed to [Session 3: Suggested Prefixes](./session-3-suggested-prefixes.md)

---

## References

- [MaterializedViewRebuildService.java](../../src/main/java/org/chucc/vcserver/service/MaterializedViewRebuildService.java)
- [Time-Travel Query Examples](../../docs/api/time-travel-queries.md)
- [Caching Strategy](../../docs/architecture/caching.md)
- [OpenAPI Annotations](https://springdoc.org/)
