# Task: Implement History Listing Endpoint

**Status:** Not Started
**Priority:** Medium (⭐ Start here - simplest task)
**Category:** Version Control Protocol
**Estimated Time:** 2-3 hours

---

## Overview

Implement the history listing endpoint that currently returns 501:
- `GET /version/history` - List commit history with filters and pagination

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)

**Not Implemented (returns 501):**
- ❌ `GET /version/history` (line 88)

---

## Requirements

### GET /version/history - List Commit History

**Request:**
```http
GET /version/history?dataset=mydata&branch=main&limit=100&offset=0&since=2025-10-01T00:00:00Z HTTP/1.1
Accept: application/json
```

**Query Parameters:**
- `dataset` (required) - Dataset name
- `branch` (optional) - Filter by branch name (commits reachable from branch HEAD)
- `limit` (optional, default 100) - Max results per page
- `offset` (optional, default 0) - Pagination offset
- `since` (optional) - Filter commits after timestamp (RFC3339/ISO8601)
- `until` (optional) - Filter commits before timestamp (RFC3339/ISO8601)
- `author` (optional) - Filter by author (exact match, case-sensitive)

**Response:** 200 OK
```json
{
  "commits": [
    {
      "id": "01933e4a-9d4e-7000-8000-000000000003",
      "message": "Add new feature",
      "author": "Alice <alice@example.org>",
      "timestamp": "2025-10-24T15:30:00Z",
      "parents": ["01933e4a-8c3d-7000-8000-000000000002"],
      "patchSize": 42
    },
    {
      "id": "01933e4a-8c3d-7000-8000-000000000002",
      "message": "Fix bug",
      "author": "Bob <bob@example.org>",
      "timestamp": "2025-10-24T12:00:00Z",
      "parents": ["01933e4a-7b2c-7000-8000-000000000001"],
      "patchSize": 15
    }
  ],
  "pagination": {
    "limit": 100,
    "offset": 0,
    "hasMore": true
  }
}
```

**Headers:**
- `Link: </version/history?dataset=mydata&offset=100&limit=100>; rel="next"` (RFC 5988 pagination)

**Error Responses:**
- `400 Bad Request` - Missing dataset parameter or invalid date format
- `404 Not Found` - Dataset or branch not found

---

## Design Decisions

**Branch Filtering:**
- "Filter by branch" means "commits reachable from branch HEAD via parent links"
- Algorithm: Start at branch HEAD, traverse parent links backwards
- Merge commits have multiple parents - follow all paths

**Date Filtering:**
- Uses commit timestamp (not author timestamp)
- `since` is inclusive (>=)
- `until` is inclusive (<=)

**Author Filtering:**
- Exact match, case-sensitive
- Matches full author string (e.g., "Alice <alice@example.org>")

**Pagination:**
- Offset-based pagination (simple, stateless)
- `hasMore` flag indicates if more results exist
- No "total" count (expensive for filtered queries)
- Link header provides next page URL (RFC 5988)

**Sorting:**
- Always sorted by timestamp descending (newest first)
- Stable sort (deterministic ordering)

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `src/main/java/org/chucc/vcserver/dto/CommitHistoryInfo.java`
- `src/main/java/org/chucc/vcserver/dto/HistoryResponse.java`
- `src/main/java/org/chucc/vcserver/dto/PaginationInfo.java`

**CommitHistoryInfo.java:**
```java
/**
 * Minimal commit metadata for history listings.
 */
public record CommitHistoryInfo(
    String id,
    String message,
    String author,
    Instant timestamp,
    List<String> parents,
    int patchSize
) {
  // Add defensive copy for parents list
  // Add validation (non-null, non-blank)
}
```

**HistoryResponse.java:**
```java
/**
 * Response for history listing endpoint.
 */
public record HistoryResponse(
    List<CommitHistoryInfo> commits,
    PaginationInfo pagination
) {
  // Add defensive copy for commits list
}
```

**PaginationInfo.java:**
```java
/**
 * Pagination metadata.
 */
public record PaginationInfo(
    int limit,
    int offset,
    boolean hasMore
) {
  // Add validation (non-negative)
}
```

### Step 2: Implement Service

**New File:** `src/main/java/org/chucc/vcserver/service/HistoryService.java`

```java
@Service
public class HistoryService {
  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;

  public HistoryResponse listHistory(
      String dataset,
      String branch,           // optional
      Instant since,           // optional
      Instant until,           // optional
      String author,           // optional
      int limit,
      int offset) {

    // Step 1: Get all commits for dataset
    List<Commit> allCommits = commitRepository.findAllByDataset(dataset);

    // Step 2: Filter by branch (if specified)
    if (branch != null) {
      Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
          .orElseThrow(() -> new BranchNotFoundException(...));

      Set<CommitId> reachableCommits = getReachableCommits(dataset, branchObj.getCommitId());
      allCommits = allCommits.stream()
          .filter(c -> reachableCommits.contains(c.id()))
          .toList();
    }

    // Step 3: Filter by date range
    if (since != null) {
      allCommits = allCommits.stream()
          .filter(c -> !c.timestamp().isBefore(since))
          .toList();
    }
    if (until != null) {
      allCommits = allCommits.stream()
          .filter(c -> !c.timestamp().isAfter(until))
          .toList();
    }

    // Step 4: Filter by author
    if (author != null) {
      allCommits = allCommits.stream()
          .filter(c -> c.author().equals(author))
          .toList();
    }

    // Step 5: Sort by timestamp descending (newest first)
    allCommits = allCommits.stream()
        .sorted(Comparator.comparing(Commit::timestamp).reversed())
        .toList();

    // Step 6: Apply pagination
    boolean hasMore = allCommits.size() > offset + limit;
    List<Commit> pageCommits = allCommits.stream()
        .skip(offset)
        .limit(limit)
        .toList();

    // Step 7: Convert to DTOs
    List<CommitHistoryInfo> commitInfos = pageCommits.stream()
        .map(this::toCommitHistoryInfo)
        .toList();

    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);
    return new HistoryResponse(commitInfos, pagination);
  }

  /**
   * Get all commits reachable from a starting commit by following parent links.
   */
  private Set<CommitId> getReachableCommits(String dataset, CommitId startCommit) {
    Set<CommitId> reachable = new HashSet<>();
    Queue<CommitId> queue = new LinkedList<>();
    queue.add(startCommit);

    while (!queue.isEmpty()) {
      CommitId current = queue.poll();

      if (reachable.contains(current)) {
        continue; // Already visited
      }

      reachable.add(current);

      // Add all parents to queue
      commitRepository.findByDatasetAndId(dataset, current)
          .ifPresent(commit -> queue.addAll(commit.parents()));
    }

    return reachable;
  }

  private CommitHistoryInfo toCommitHistoryInfo(Commit commit) {
    return new CommitHistoryInfo(
        commit.id().value(),
        commit.message(),
        commit.author(),
        commit.timestamp(),
        commit.parents().stream().map(CommitId::value).toList(),
        commit.patchSize()
    );
  }
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

Replace the 501 stub method (lines 88-90) with:

```java
public ResponseEntity<HistoryResponse> listHistory(
    @RequestParam String dataset,  // Now required
    @RequestParam(required = false) String branch,
    @RequestParam(required = false, defaultValue = "100") Integer limit,
    @RequestParam(required = false, defaultValue = "0") Integer offset,
    @RequestParam(required = false) String since,
    @RequestParam(required = false) String until,
    @RequestParam(required = false) String author
) {
  // Validate dataset parameter
  if (dataset == null || dataset.isBlank()) {
    throw new IllegalArgumentException("Dataset parameter is required");
  }

  // Parse date parameters (RFC3339/ISO8601)
  Instant sinceInstant = since != null ? Instant.parse(since) : null;
  Instant untilInstant = until != null ? Instant.parse(until) : null;

  // Call service
  HistoryResponse response = historyService.listHistory(
      dataset, branch, sinceInstant, untilInstant, author, limit, offset
  );

  // Build Link header for next page (RFC 5988)
  ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
  if (response.pagination().hasMore()) {
    String nextUrl = String.format(
        "/version/history?dataset=%s&offset=%d&limit=%d",
        dataset, offset + limit, limit
    );
    if (branch != null) {
      nextUrl += "&branch=" + branch;
    }
    // Add other filters if present
    responseBuilder.header("Link", String.format("<%s>; rel=\"next\"", nextUrl));
  }

  return responseBuilder.body(response);
}
```

### Step 4: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/HistoryListingIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class HistoryListingIT extends IntegrationTestFixture {

  @Test
  void listHistory_shouldReturnAllCommits() {
    // Arrange: Create dataset with 3 commits
    createTestCommits();

    // Act
    ResponseEntity<HistoryResponse> response = restTemplate.exchange(
        "/version/history?dataset=test-dataset",
        HttpMethod.GET,
        null,
        HistoryResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().commits()).hasSize(3);
    // Verify sorted by timestamp descending
    assertThat(response.getBody().commits().get(0).timestamp())
        .isAfter(response.getBody().commits().get(1).timestamp());
  }

  @Test
  void listHistory_withBranchFilter_shouldReturnOnlyReachableCommits() {
    // Create commits on different branches
    // Verify branch filtering works
  }

  @Test
  void listHistory_withDateFilter_shouldFilterCorrectly() {
    // Test since and until parameters
  }

  @Test
  void listHistory_withAuthorFilter_shouldFilterCorrectly() {
    // Test author filtering
  }

  @Test
  void listHistory_withPagination_shouldReturnLinkHeader() {
    // Create 150 commits
    // Request first page (limit=100)
    // Verify Link header for next page
    // Verify hasMore=true
  }

  @Test
  void listHistory_missingDataset_shouldReturn400() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/history",
        HttpMethod.GET,
        null,
        String.class
    );
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/service/HistoryServiceTest.java`

Test the service logic independently (reachable commits, filtering, pagination).

---

## Success Criteria

- ✅ Endpoint implemented (no 501 responses)
- ✅ DTOs created with validation and defensive copying
- ✅ Service layer implemented (read-only, no CQRS needed)
- ✅ Integration tests pass (8+ test cases)
- ✅ Pagination works with Link headers (RFC 5988)
- ✅ All filters work correctly (branch, author, date range)
- ✅ Branch filtering correctly identifies reachable commits
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

**Pattern:** API Layer Test (projector DISABLED)
- Test HTTP status codes and headers
- Do NOT query repositories (projector disabled)
- Use test fixtures to set up commit history

**Example Test Structure:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class HistoryListingIT extends IntegrationTestFixture {
  // Projector DISABLED by default
  // Only test API responses
}
```

---

## Notes

- This is a **read-only** operation (no commands/events needed)
- Reachable commits algorithm uses BFS to follow parent links
- Pagination is stateless (no cursor needed)
- Link header format follows RFC 5988
- No "total" count to avoid expensive O(n) scans

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)
- [CommitRepository.java](../../src/main/java/org/chucc/vcserver/repository/CommitRepository.java)
- [RFC 5988 - Web Linking](https://www.rfc-editor.org/rfc/rfc5988)
