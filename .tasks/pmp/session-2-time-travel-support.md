# Session 2: Time-Travel Prefix Queries

**Status:** Not Started
**Estimated Time:** 2-3 hours
**Priority:** Medium
**Dependencies:** Session 1 (Core Implementation)

---

## Overview

Add ability to query prefixes at specific commits (historical queries). Enables users to see what prefixes existed at any point in history.

**Goal:** Support `GET /version/datasets/{name}/commits/{id}/prefixes` for time-travel queries.

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

### Step 1: Add Endpoint to Controller (30 minutes)

```java
/**
 * Retrieves prefix mappings at a specific commit (time-travel).
 *
 * @param dataset the dataset name
 * @param commitId the commit ID
 * @return prefix response
 * @throws CommitNotFoundException if commit doesn't exist
 */
@GetMapping("/commits/{commitId}/prefixes")
public ResponseEntity<PrefixResponse> getPrefixesAtCommit(
    @PathVariable String dataset,
    @PathVariable String commitId) {

  // Rebuild dataset at specific commit
  DatasetGraph dsg = materializedViewRebuildService
      .rebuildAtCommit(dataset, commitId);

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
}
```

**Key insight:** Reuses existing `MaterializedViewRebuildService` - no new code needed!

---

### Step 2: Add Dependency Injection (5 minutes)

```java
private final MaterializedViewRebuildService materializedViewRebuildService;

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

---

### Step 3: Write Integration Tests (60 minutes)

**Add to `PrefixManagementIT.java`:**

```java
@Test
void getPrefixesAtCommit_shouldReturnHistoricalPrefixes() {
  // Arrange: Create commit with specific prefixes
  UpdatePrefixesRequest request = new UpdatePrefixesRequest(
      "Add historical prefixes",
      Map.of("old", "http://example.org/old/")
  );

  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.APPLICATION_JSON);
  headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
  HttpEntity<UpdatePrefixesRequest> httpEntity = new HttpEntity<>(request, headers);

  ResponseEntity<CommitResponse> createResponse = restTemplate.exchange(
      "/version/datasets/default/branches/main/prefixes",
      HttpMethod.PUT,
      httpEntity,
      CommitResponse.class
  );

  String commitId = createResponse.getBody().commitId();

  // Act: Query prefixes at that commit
  ResponseEntity<PrefixResponse> response = restTemplate.exchange(
      "/version/datasets/default/commits/" + commitId + "/prefixes",
      HttpMethod.GET,
      null,
      PrefixResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody()).isNotNull();
  assertThat(response.getBody().commitId()).isEqualTo(commitId);
  assertThat(response.getBody().branch()).isNull();  // No branch (commit query)
  assertThat(response.getBody().prefixes()).containsKey("old");
}

@Test
void getPrefixesAtCommit_shouldReturn404_whenCommitNotFound() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/datasets/default/commits/nonexistent-commit-id/prefixes",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}

@Test
void getPrefixesAtCommit_shouldReturnDifferentPrefixes_forDifferentCommits() {
  // Arrange: Create two commits with different prefixes
  // Commit 1: Add "v1" prefix
  UpdatePrefixesRequest request1 = new UpdatePrefixesRequest(
      "Version 1 prefixes",
      Map.of("v1", "http://example.org/v1/")
  );
  String commitId1 = createPrefixCommit(request1);

  // Commit 2: Add "v2" prefix
  UpdatePrefixesRequest request2 = new UpdatePrefixesRequest(
      "Version 2 prefixes",
      Map.of("v2", "http://example.org/v2/")
  );
  String commitId2 = createPrefixCommit(request2);

  // Act: Query both commits
  PrefixResponse response1 = getPrefixesAtCommit(commitId1);
  PrefixResponse response2 = getPrefixesAtCommit(commitId2);

  // Assert
  assertThat(response1.prefixes()).containsKey("v1");
  assertThat(response1.prefixes()).doesNotContainKey("v2");

  assertThat(response2.prefixes()).containsKey("v1");  // Inherited
  assertThat(response2.prefixes()).containsKey("v2");  // Added
}
```

Add 2-3 more tests:
- `getPrefixesAtCommit_shouldUseCacheForRecentCommits()`
- `getPrefixesAtCommit_shouldRebuildForOldCommits()`

---

### Step 4: Performance Testing (30 minutes)

**Verify caching behavior:**

```java
@Test
void getPrefixesAtCommit_shouldBeFast_whenCommitCached() {
  // Arrange: Create commit
  String commitId = createPrefixCommit(...);

  // Prime cache by accessing commit
  materializedViewRebuildService.rebuildAtCommit("default", commitId);

  // Act: Time subsequent access
  long start = System.currentTimeMillis();
  getPrefixesAtCommit(commitId);
  long duration = System.currentTimeMillis() - start;

  // Assert: Should be fast (<50ms for cached)
  assertThat(duration).isLessThan(50);
}

@Test
void getPrefixesAtCommit_shouldRebuildOnDemand_whenNotCached() {
  // Arrange: Create old commit that might be evicted
  String oldCommitId = createPrefixCommit(...);

  // Act: Query (may require rebuild)
  ResponseEntity<PrefixResponse> response = getPrefixesAtCommit(oldCommitId);

  // Assert: Should work (even if slower)
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  // Typically ~1s for rebuild with 100 commits
}
```

---

## Success Criteria

### Functional
- ✅ GET `/commits/{id}/prefixes` returns historical prefixes
- ✅ Response includes `commitId`, no `branch`
- ✅ Works for any commit (cached or not)
- ✅ Returns 404 if commit doesn't exist

### Performance
- ✅ Cached commits: <50ms
- ✅ Uncached commits: <2s typical (rebuild)
- ✅ No memory leaks (cache eviction works)

### Quality
- ✅ All tests pass (5+ new tests)
- ✅ Zero quality violations

---

## Files to Modify

```
src/main/java/org/chucc/vcserver/controller/
  └── PrefixManagementController.java  # Add getPrefixesAtCommit() method

src/test/java/org/chucc/vcserver/integration/
  └── PrefixManagementIT.java          # Add 5+ tests
```

---

## Next Steps

After completing:
1. ✅ Commit changes
2. ✅ Proceed to [Session 3: Suggested Prefixes](./session-3-suggested-prefixes.md)
