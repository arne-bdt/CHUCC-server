# Task: Implement Blame Endpoint

**Status:** Not Started
**Priority:** Medium
**Category:** Version Control Protocol (EXTENSION)
**Estimated Time:** 3-4 hours

---

## Overview

Implement the blame endpoint that currently returns 501:
- `GET /version/blame` - Get last-writer attribution for all quads in a commit

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md) (EXTENSION - not in official spec)

**Note:** This is an **EXTENSION** endpoint. The official SPARQL 1.2 Protocol does not define blame operations.

---

## Current State

**Controller:** [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)

**Not Implemented (returns 501):**
- ❌ `GET /version/blame` (line 185)

**Existing Infrastructure:**
- ✅ `DatasetService.materializeCommit(dataset, commitId)` - Loads commit snapshot
- ✅ `CommitRepository.findPatchByDatasetAndId()` - Loads RDF Patch for commit
- ✅ `Commit.parents()` - Parent commit IDs for traversal

**Feature Flag:**
- `vcProperties.isBlameEnabled()` - Must be true for endpoint to work (line 175)

---

## Requirements

### GET /version/blame - Last-Writer Attribution

**Request:**
```http
GET /version/blame?dataset=mydata&commit=01933e4a-9d4e-7000-8000-000000000003 HTTP/1.1
Accept: application/json
```

**Query Parameters:**
- `dataset` (required) - Dataset name
- `commit` (required) - Commit ID (UUIDv7) to get blame for

**Response:** 200 OK
```json
{
  "commit": "01933e4a-9d4e-7000-8000-000000000003",
  "quads": [
    {
      "graph": "http://example.org/metadata",
      "subject": "http://example.org/Alice",
      "predicate": "http://xmlns.com/foaf/0.1/name",
      "object": "\"Alice\"",
      "lastModifiedBy": "Bob <bob@example.org>",
      "lastModifiedAt": "2025-10-24T12:00:00Z",
      "commitId": "01933e4a-8c3d-7000-8000-000000000002"
    },
    {
      "graph": "http://example.org/metadata",
      "subject": "http://example.org/Alice",
      "predicate": "http://xmlns.com/foaf/0.1/email",
      "object": "\"alice@example.org\"",
      "lastModifiedBy": "Alice <alice@example.org>",
      "lastModifiedAt": "2025-10-23T10:00:00Z",
      "commitId": "01933e4a-7b2c-7000-8000-000000000001"
    }
  ]
}
```

**Semantics:**
- Returns attribution info for ALL quads present in the target commit
- For each quad, identifies the most recent commit that added it
- If quad was deleted and re-added, shows the most recent addition

**Error Responses:**
- `400 Bad Request` - Missing dataset or commit parameter
- `404 Not Found` - Blame endpoint disabled (feature flag)
- `404 Not Found` - Commit not found

**Headers:**
- `Content-Type: application/json`

---

## Design Decisions

**Algorithm: Reverse History Traversal with Quad Tracking**

**Core Idea:**
- Walk commit history BACKWARDS from target commit to root
- Track which quads we need to find authors for
- When we see an ADD operation for a tracked quad, that's the author
- **CRITICAL:** Remove quad from tracking set after finding it (handles delete/re-add)

**Why removal is critical:**

Example scenario:
- Commit A (t=1): `ADD (g1, Alice, name, "Alice")`
- Commit B (t=2): `DELETE (g1, Alice, name, "Alice")`
- Commit C (t=3): `ADD (g1, Alice, name, "Alice")` ← same quad again!

Current state at C: Quad exists

Backwards traversal:
1. C: Find `ADD (g1, Alice, name, "Alice")` → blame = C → **REMOVE from targetQuads** ✅
2. B: Find `DELETE` → ignore (only look at ADDs)
3. A: Find `ADD (g1, Alice, name, "Alice")` → **quad not tracked anymore, SKIP** ✅

Result: Blame shows commit C (correct - the current incarnation started at C)

**Without removal:** Would incorrectly show commit A (the first addition, not the current one)

**Scope:**
- Blame ALL quads in target commit (not filtered by subject/predicate)
- Graph context always matters (quad-level blame, not triple-level)

**Optimization:**
- Early exit when all quads found
- BFS for merge commits (follow all parent paths)

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `src/main/java/org/chucc/vcserver/dto/BlameResponse.java`
- `src/main/java/org/chucc/vcserver/dto/QuadBlameInfo.java`

**QuadBlameInfo.java:**
```java
/**
 * Blame information for a single quad.
 */
public record QuadBlameInfo(
    String graph,                // Graph IRI or "default" for default graph
    String subject,
    String predicate,
    String object,
    String lastModifiedBy,       // Author who last added this quad
    Instant lastModifiedAt,      // Timestamp of last addition
    String commitId              // Commit ID that last added this quad
) {
  // Add validation (non-null, non-blank)
}
```

**BlameResponse.java:**
```java
/**
 * Response for blame endpoint.
 */
public record BlameResponse(
    String commit,               // Target commit ID
    List<QuadBlameInfo> quads
) {
  // Add defensive copy for quads list
}
```

### Step 2: Implement Service

**New File:** `src/main/java/org/chucc/vcserver/service/BlameService.java`

```java
@Service
public class BlameService {
  private final DatasetService datasetService;
  private final CommitRepository commitRepository;

  public BlameService(DatasetService datasetService, CommitRepository commitRepository) {
    this.datasetService = datasetService;
    this.commitRepository = commitRepository;
  }

  /**
   * Computes blame for all quads in a target commit.
   *
   * @param dataset the dataset name
   * @param targetCommitId the commit ID to get blame for
   * @return blame information for all quads
   * @throws CommitNotFoundException if commit is not found
   */
  public BlameResponse blameCommit(String dataset, CommitId targetCommitId) {
    // Step 1: Load target commit
    Commit targetCommit = commitRepository.findByDatasetAndId(dataset, targetCommitId)
        .orElseThrow(() -> new CommitNotFoundException(
            "Commit not found: " + targetCommitId.value(), true));

    // Step 2: Get all quads in target commit
    DatasetGraph targetDataset = datasetService.materializeCommit(dataset, targetCommitId);
    Set<Quad> targetQuads = new HashSet<>();
    targetDataset.find().forEachRemaining(targetQuads::add);

    // Step 3: Walk history backwards to find authors
    Map<Quad, QuadBlameInfo> blameMap = new HashMap<>();
    findAuthors(dataset, targetCommit, targetQuads, blameMap);

    // Step 4: Convert to response
    List<QuadBlameInfo> quads = new ArrayList<>(blameMap.values());
    return new BlameResponse(targetCommitId.value(), quads);
  }

  /**
   * Walks commit history backwards to find the author of each quad.
   *
   * @param dataset the dataset name
   * @param startCommit the commit to start from
   * @param targetQuads the quads to find authors for (modified in-place)
   * @param blameMap the map to populate with blame info
   */
  private void findAuthors(String dataset, Commit startCommit,
                            Set<Quad> targetQuads, Map<Quad, QuadBlameInfo> blameMap) {
    // Use BFS to traverse history (handles merge commits correctly)
    Queue<Commit> queue = new LinkedList<>();
    Set<CommitId> visited = new HashSet<>();

    queue.add(startCommit);
    visited.add(startCommit.id());

    while (!queue.isEmpty() && !targetQuads.isEmpty()) {
      Commit currentCommit = queue.poll();

      // Load patch for this commit
      Optional<RDFPatch> patchOpt = commitRepository.findPatchByDatasetAndId(
          dataset, currentCommit.id());

      if (patchOpt.isPresent()) {
        // Check if this commit added any of our target quads
        processCommitForBlame(currentCommit, patchOpt.get(), targetQuads, blameMap);
      }

      // Early exit if all quads found
      if (targetQuads.isEmpty()) {
        break;
      }

      // Add parents to queue
      for (CommitId parentId : currentCommit.parents()) {
        if (!visited.contains(parentId)) {
          visited.add(parentId);
          commitRepository.findByDatasetAndId(dataset, parentId)
              .ifPresent(queue::add);
        }
      }
    }

    // If any quads remain, they must have been added in initial commit
    // This shouldn't happen in practice, but handle defensively
    if (!targetQuads.isEmpty()) {
      logger.warn("Blame: {} quads not found in history for dataset {}",
          targetQuads.size(), dataset);
    }
  }

  /**
   * Checks if a commit added any target quads (via ADD operations).
   * If found, records blame info and removes quad from target set.
   *
   * @param commit the commit to check
   * @param patch the RDF patch for this commit
   * @param targetQuads the quads we're looking for (modified in-place)
   * @param blameMap the map to populate with blame info
   */
  private void processCommitForBlame(Commit commit, RDFPatch patch,
                                      Set<Quad> targetQuads, Map<Quad, QuadBlameInfo> blameMap) {
    // Use RDFChanges to inspect ADD operations only
    patch.apply(new RDFChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        Quad quad = Quad.create(g, s, p, o);

        if (targetQuads.contains(quad)) {
          // Found the birth of this quad!
          QuadBlameInfo blameInfo = new QuadBlameInfo(
              formatGraphNode(g),
              formatNode(s),
              formatNode(p),
              formatNode(o),
              commit.author(),
              commit.timestamp(),
              commit.id().value()
          );

          blameMap.put(quad, blameInfo);

          // CRITICAL: Remove from target set
          // This ensures we don't blame an older ADD if quad was deleted and re-added
          targetQuads.remove(quad);
        }
      }
    });
  }

  /**
   * Formats a graph node for display.
   */
  private String formatGraphNode(Node g) {
    if (g == null || g.equals(Quad.defaultGraphNodeGenerated)
        || g.equals(Quad.defaultGraphIRI)) {
      return "default";
    }
    return g.getURI();
  }

  /**
   * Formats a node for display.
   */
  private String formatNode(Node node) {
    if (node.isURI()) {
      return node.getURI();
    } else if (node.isLiteral()) {
      return node.toString();  // Includes quotes and datatype
    } else if (node.isBlank()) {
      return "_:" + node.getBlankNodeLabel();
    } else {
      return node.toString();
    }
  }
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

Replace the 501 stub method (lines 185-187) with:

```java
public ResponseEntity<BlameResponse> blameCommit(
    @RequestParam String dataset,  // Add dataset parameter
    @RequestParam String commit
) {
  // Check feature flag
  if (!vcProperties.isBlameEnabled()) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(
            "Blame endpoint is disabled",
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND"
        ).toString());
  }

  // Validate parameters
  if (dataset == null || dataset.isBlank()) {
    throw new IllegalArgumentException("Dataset parameter is required");
  }

  try {
    // Parse commit ID
    CommitId commitId = CommitId.of(commit);

    // Call service
    BlameResponse response = blameService.blameCommit(dataset, commitId);

    return ResponseEntity.ok(response);

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

### Step 4: Update OpenAPI Annotations

Update the `@GetMapping` annotation to include dataset parameter:

```java
@GetMapping(value = "/blame", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(
    summary = "Last-writer attribution",
    description = "Get last-writer attribution for all quads in a commit. "
        + "⚠️ EXTENSION: This endpoint is not part of the official "
        + "SPARQL 1.2 Protocol specification."
)
@Parameter(name = "dataset", description = "Dataset name", required = true)
@Parameter(name = "commit", description = "Commit ID (UUIDv7)", required = true)
```

### Step 5: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/BlameEndpointIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class BlameEndpointIT extends IntegrationTestFixture {

  @Test
  void blameCommit_shouldReturnAllQuads() {
    // Arrange: Create dataset with 2 commits
    String dataset = "test-blame";

    // Commit 1: Add 2 quads
    String commit1 = createCommitWithQuads(dataset, "Alice", List.of(
        quad("g1", "Alice", "name", "\"Alice\""),
        quad("g1", "Alice", "age", "30")
    ));

    // Commit 2: Add 1 more quad
    String commit2 = createCommitWithQuads(dataset, "Bob", List.of(
        quad("g1", "Alice", "email", "\"alice@example.org\"")
    ));

    // Act
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        String.format("/version/blame?dataset=%s&commit=%s", dataset, commit2),
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().commit()).isEqualTo(commit2);
    assertThat(response.getBody().quads()).hasSize(3);

    // First 2 quads blamed to Alice (commit1)
    assertThat(response.getBody().quads())
        .filteredOn(q -> q.commitId().equals(commit1))
        .hasSize(2);

    // Last quad blamed to Bob (commit2)
    assertThat(response.getBody().quads())
        .filteredOn(q -> q.commitId().equals(commit2))
        .hasSize(1);
  }

  @Test
  void blameCommit_withDeleteAndReAdd_shouldBlameReAdd() {
    // Arrange: Test delete/re-add scenario
    String dataset = "test-reblame";

    // Commit 1: Add quad
    String commit1 = createCommitWithQuads(dataset, "Alice", List.of(
        quad("g1", "Alice", "name", "\"Alice\"")
    ));

    // Commit 2: Delete quad
    String commit2 = createCommitWithDelete(dataset, "Bob",
        quad("g1", "Alice", "name", "\"Alice\"")
    );

    // Commit 3: Re-add same quad
    String commit3 = createCommitWithQuads(dataset, "Charlie", List.of(
        quad("g1", "Alice", "name", "\"Alice\"")
    ));

    // Act
    ResponseEntity<BlameResponse> response = restTemplate.exchange(
        String.format("/version/blame?dataset=%s&commit=%s", dataset, commit3),
        HttpMethod.GET,
        null,
        BlameResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().quads()).hasSize(1);

    // Quad should be blamed to Charlie (commit3), not Alice (commit1)
    QuadBlameInfo blameInfo = response.getBody().quads().get(0);
    assertThat(blameInfo.commitId()).isEqualTo(commit3);
    assertThat(blameInfo.lastModifiedBy()).isEqualTo("Charlie");
  }

  @Test
  void blameCommit_withMultipleGraphs_shouldTrackGraphContext() {
    // Test that graph context matters (same triple in different graphs)
  }

  @Test
  void blameCommit_nonExistentCommit_shouldReturn404() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/blame?dataset=test&commit=invalid",
        HttpMethod.GET,
        null,
        String.class
    );
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void blameCommit_missingDataset_shouldReturn400() {
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/blame?commit=abc",
        HttpMethod.GET,
        null,
        String.class
    );
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void blameCommit_featureDisabled_shouldReturn404() {
    // Mock vcProperties.isBlameEnabled() = false
    // Verify 404 response
  }
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/service/BlameServiceTest.java`

Test the service logic independently (history traversal, quad tracking, removal logic).

---

## Success Criteria

- ✅ Endpoint implemented (no 501 responses)
- ✅ DTOs created with validation and defensive copying
- ✅ Service layer implemented (read-only)
- ✅ Integration tests pass (6+ test cases)
- ✅ Reverse traversal algorithm works correctly
- ✅ Quad removal logic prevents incorrect blame for delete/re-add
- ✅ Early exit optimization works
- ✅ BFS handles merge commits correctly
- ✅ Feature flag check works (returns 404 when disabled)
- ✅ Proper error handling (404 for missing commits, 400 for invalid params)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

**Pattern:** API Layer Test (projector DISABLED)
- Test HTTP status codes and headers
- Verify blame attribution correctness
- Test delete/re-add scenario (critical test case)
- Do NOT query repositories (projector disabled)

**Critical Test Cases:**
1. **Basic blame**: Multiple commits, different authors
2. **Delete/re-add**: Verify quad removal logic works
3. **Multiple graphs**: Verify graph context matters
4. **Merge commits**: Verify BFS traversal
5. **Empty commit**: No quads to blame

---

## Notes

- This is a **read-only** operation (no commands/events needed)
- Algorithm uses BFS for correct merge commit handling
- **Quad removal is critical** for correct delete/re-add behavior
- This is an **EXTENSION** - not part of official SPARQL 1.2 spec
- Feature flag: `chucc.version-control.blame-enabled=true` (default: true)

---

## Performance Characteristics

**Time Complexity:**
- Best case: O(q) if all quads added in target commit (q = quad count)
- Average case: O(q × log h) (h = history depth)
- Worst case: O(q × h) if quads added in very old commits
- Early exit reduces actual cost significantly

**Space Complexity:**
- O(q) for target quads set
- O(h) for BFS queue and visited set
- O(q) for blame map

**Optimization:**
- Early exit when all quads found
- BFS avoids redundant traversal
- No caching needed (blame is infrequent operation)

---

## Algorithm Verification

**Correctness Property:**
For each quad Q in target commit T:
- Blame identifies commit C where Q was last added
- C is reachable from T via parent links
- No commit between C and T deleted and re-added Q

**Proof:**
- BFS guarantees we visit commits in reverse chronological order
- Removal ensures we only blame the *first* ADD we encounter (newest)
- If quad deleted and re-added, we blame the re-add (correct behavior)

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)
- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java)
- [CommitRepository.java](../../src/main/java/org/chucc/vcserver/repository/CommitRepository.java)
- Git blame implementation (conceptual reference)
