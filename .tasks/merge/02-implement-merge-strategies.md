# Task: Implement Merge API - Phase 2 (Conflict Resolution Strategies)

**Status:** Not Started (Requires Phase 1 Completion)
**Priority:** High
**Category:** Version Control Protocol (Phase 2 of 3)
**Estimated Time:** 3-4 hours (includes configurable conflict scope)

---

## Overview

Add automatic conflict resolution strategies ("ours" and "theirs") with **configurable conflict scope** to the merge endpoint. This phase builds on Phase 1's conflict detection to auto-resolve conflicts based on strategy and scope.

**Prerequisites:** Phase 1 must be completed (conflict detection working, returns 409).

**Key Design Decisions:**
1. Conflicts can be resolved at **graph-level** (default) or **dataset-level**
2. Graphs and datasets are the only valid RDF semantic boundaries
3. Configurable via `conflictScope` parameter

---

## RDF Terminology Reminder

**Valid RDF structures:**
- **Triple:** `(Subject, Predicate, Object)` - Statement within a graph
- **Quad:** `(Graph, Subject, Predicate, Object)` - Statement within a dataset
- **Graph:** Collection of triples
- **Dataset:** Collection of graphs (one default graph + zero or more named graphs)

**Valid conflict scopes:**
- ✅ **Graph-level:** Treat each graph as a semantic boundary
- ✅ **Dataset-level:** Treat entire dataset as a semantic boundary
- ❌ **NOT valid:** "Triple-level", "Predicate-level", "Quad-level" (leads to semantic errors)

---

## Conflict Resolution Granularity

### Graph-Level (Default)

When Phase 1 detects a conflicting quad, extract its graph name. **ALL operations in that graph** are treated as conflicting.

**Example:**
```
Dataset contains:
  - Graph-A (metadata): (Alice, age, 30), (Alice, name, "Alice")
  - Graph-B (emails): (Bob, email, "bob@old.com")

Into branch:
  - Graph-A: DELETE (Alice, age, 30), ADD (Alice, age, 31)
  - Graph-B: no changes

From branch:
  - Graph-A: DELETE (Alice, age, 30), ADD (Alice, age, 32)
  - Graph-B: ADD (Bob, email, "bob@new.com")

Conflict detected: Quad (Graph-A, Alice, age, 30) touched by both branches
Conflict scope: Graph-A only

"ours" strategy with conflictScope="graph":
  - Keep: ALL Graph-A operations from "into" → age=31
  - Discard: ALL Graph-A operations from "from"
  - Keep: ALL Graph-B operations from "from" → bob@new.com

Result:
  - Graph-A: (Alice, age, 31), (Alice, name, "Alice")  [from "into"]
  - Graph-B: (Bob, email, "bob@old.com"), (Bob, email, "bob@new.com")  [combined]
```

**Rationale:**
- Graphs are natural semantic boundaries in RDF
- Prevents partial merges within a graph
- Allows merging non-conflicting graphs
- Most flexible for multi-graph datasets

### Dataset-Level (Conservative)

When ANY quad conflicts, **ALL operations in the entire dataset** are treated as conflicting.

**Example (same scenario as above):**
```
Conflict detected: Quad (Graph-A, Alice, age, 30) touched by both branches
Conflict scope: Entire dataset

"ours" strategy with conflictScope="dataset":
  - Keep: ALL operations from "into" (Graph-A and Graph-B)
  - Discard: ALL operations from "from"

Result:
  - Graph-A: (Alice, age, 31), (Alice, name, "Alice")  [from "into"]
  - Graph-B: (Bob, email, "bob@old.com")  [from "into", no changes]
```

**Rationale:**
- Conservative approach
- Safest when graphs have dependencies
- Simpler for single-graph datasets
- Git-like behavior (repository as single unit)

---

## Scope of Phase 2

### ✅ In Scope
- Add `strategy` parameter to `MergeRequest` ("three-way", "ours", "theirs")
- Add `conflictScope` parameter to `MergeRequest` ("graph", "dataset")
- Extract conflicting graphs from quad conflicts
- Implement graph-level conflict resolution
- Implement dataset-level conflict resolution
- Update response to include `conflictsResolved` count

### ❌ Out of Scope (Phase 3)
- "manual" strategy with resolution array
- User-provided conflict resolutions

---

## Requirements

### API Request

```http
POST /version/merge HTTP/1.1
Content-Type: application/json

{
  "into": "main",
  "from": "feature-x",
  "strategy": "ours",
  "conflictScope": "graph",
  "fastForward": "allow"
}
```

**New Parameters:**
- `strategy` (optional) - Conflict resolution strategy:
  - `"three-way"` (default) - Return 409 if conflicts detected
  - `"ours"` - Keep "into" branch changes
  - `"theirs"` - Keep "from" branch changes
- `conflictScope` (optional) - Conflict boundary:
  - `"graph"` (default) - Resolve per conflicting graph
  - `"dataset"` - Resolve entire dataset at once

### Merge Strategies

**three-way (default):**
- Phase 1 behavior: return 409 if conflicts detected
- `conflictScope` parameter ignored

**ours:**
- Auto-resolve conflicts by keeping "into" branch changes
- **conflictScope="graph":** Keep all "into" operations in conflicting graphs only
- **conflictScope="dataset":** Keep all "into" operations in entire dataset
- Never returns 409 Conflict

**theirs:**
- Auto-resolve conflicts by keeping "from" branch changes
- **conflictScope="graph":** Keep all "from" operations in conflicting graphs only
- **conflictScope="dataset":** Keep all "from" operations in entire dataset
- Never returns 409 Conflict

### API Response

```json
{
  "result": "merged",
  "mergeCommit": "01933e4a-9d4e-7000-8000-000000000005",
  "into": "main",
  "from": "feature-x",
  "strategy": "ours",
  "conflictScope": "graph",
  "fastForward": false,
  "conflictsResolved": 3
}
```

**New Fields:**
- `conflictScope` (string) - Scope used for resolution
- `conflictsResolved` (integer) - Number of conflicts auto-resolved

---

## Implementation Steps

### Step 1: Update DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`

```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for merge operation.
 */
public record MergeRequest(
    @NotBlank(message = "Target branch (into) is required")
    String into,

    @NotBlank(message = "Source ref (from) is required")
    String from,

    @JsonProperty(defaultValue = "allow")
    String fastForward,

    @JsonProperty(defaultValue = "three-way")
    String strategy,          // NEW: "three-way", "ours", "theirs"

    @JsonProperty(defaultValue = "graph")
    String conflictScope,     // NEW: "graph", "dataset"

    Object resolutions        // Phase 3: Not used in Phase 2
) {

  public void validate() {
    if (into == null || into.isBlank()) {
      throw new IllegalArgumentException("Target branch (into) is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }

    // Validate fastForward
    String ff = fastForward != null ? fastForward : "allow";
    if (!List.of("allow", "only", "never").contains(ff)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + ff);
    }

    // Validate strategy
    if (strategy != null) {
      String strat = strategy.toLowerCase();
      if (!List.of("three-way", "ours", "theirs").contains(strat)) {
        throw new IllegalArgumentException(
            "Invalid strategy: " + strategy + ". Valid values: three-way, ours, theirs");
      }
    }

    // Validate conflictScope
    if (conflictScope != null) {
      String scope = conflictScope.toLowerCase();
      if (!List.of("graph", "dataset").contains(scope)) {
        throw new IllegalArgumentException(
            "Invalid conflictScope: " + conflictScope + ". Valid values: graph, dataset");
      }
    }
  }

  public String normalizedFastForward() {
    return fastForward != null ? fastForward : "allow";
  }

  public String normalizedStrategy() {
    return strategy != null ? strategy.toLowerCase() : "three-way";
  }

  public String normalizedConflictScope() {
    return conflictScope != null ? conflictScope.toLowerCase() : "graph";
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`

Update factory method:

```java
public static MergeResponse merged(String into, String from, String mergeCommit,
                                   String strategy, String conflictScope, int conflictsResolved) {
  return new MergeResponse(
      "merged",
      mergeCommit,
      into,
      from,
      null,
      false,
      strategy != null ? strategy : "three-way",
      conflictScope != null ? conflictScope : "graph",
      conflictsResolved
  );
}
```

Update record:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeResponse(
    String result,
    String mergeCommit,
    String into,
    String from,
    String headCommit,
    Boolean fastForward,
    String strategy,
    String conflictScope,     // NEW
    Integer conflictsResolved
) {
  // ... factory methods
}
```

---

### Step 2: Update Merge Utilities

**File:** `src/main/java/org/chucc/vcserver/util/MergeUtil.java`

Add conflict scope methods:

```java
/**
 * Extracts conflicting graph names from quad-level conflicts.
 *
 * @param conflicts quad-level conflicts from detectConflicts()
 * @return set of conflicting graph nodes
 */
public static Set<Node> extractConflictingGraphs(List<MergeConflict> conflicts) {
  Set<Node> graphs = new HashSet<>();

  for (MergeConflict conflict : conflicts) {
    Node graphNode = parseGraphNode(conflict.graph());
    graphs.add(graphNode);
  }

  return graphs;
}

/**
 * Parses a graph node string.
 */
private static Node parseGraphNode(String graphStr) {
  if ("urn:x-arq:DefaultGraph".equals(graphStr)) {
    return Quad.defaultGraphIRI;
  }
  return NodeFactory.createURI(graphStr);
}

/**
 * Resolves conflicts using "ours" strategy.
 *
 * @param baseToInto changes from base to "into"
 * @param baseToFrom changes from base to "from"
 * @param conflicts detected conflicts
 * @param conflictScope "graph" or "dataset"
 * @return merged patch with "into" winning
 */
public static RDFPatch resolveWithOurs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                       List<MergeConflict> conflicts, String conflictScope) {
  RDFPatchBuilder builder = RDFPatchOps.builder();

  if ("dataset".equals(conflictScope)) {
    // Dataset-level: keep ALL "into", discard ALL "from"
    applyAllOperations(builder, baseToInto);
  } else {
    // Graph-level: keep ALL "into", keep non-conflicting "from" graphs
    Set<Node> conflictingGraphs = extractConflictingGraphs(conflicts);
    applyAllOperations(builder, baseToInto);
    applyGraphFilteredOperations(builder, baseToFrom, conflictingGraphs, false);
  }

  return builder.build();
}

/**
 * Resolves conflicts using "theirs" strategy.
 *
 * @param baseToInto changes from base to "into"
 * @param baseToFrom changes from base to "from"
 * @param conflicts detected conflicts
 * @param conflictScope "graph" or "dataset"
 * @return merged patch with "from" winning
 */
public static RDFPatch resolveWithTheirs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                         List<MergeConflict> conflicts, String conflictScope) {
  RDFPatchBuilder builder = RDFPatchOps.builder();

  if ("dataset".equals(conflictScope)) {
    // Dataset-level: keep ALL "from", discard ALL "into"
    applyAllOperations(builder, baseToFrom);
  } else {
    // Graph-level: keep non-conflicting "into" graphs, keep ALL "from"
    Set<Node> conflictingGraphs = extractConflictingGraphs(conflicts);
    applyGraphFilteredOperations(builder, baseToInto, conflictingGraphs, false);
    applyAllOperations(builder, baseToFrom);
  }

  return builder.build();
}

/**
 * Applies all operations from a patch.
 */
private static void applyAllOperations(RDFPatchBuilder builder, RDFPatch patch) {
  patch.apply(new RDFChanges() {
    @Override
    public void add(Node g, Node s, Node p, Node o) {
      builder.add(g, s, p, o);
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
      builder.delete(g, s, p, o);
    }

    // Other methods: no-op
    @Override public void start() {}
    @Override public void finish() {}
    @Override public void header(String field, Node value) {}
    @Override public void addPrefix(Node gn, String prefix, String uriStr) {}
    @Override public void deletePrefix(Node gn, String prefix) {}
    @Override public void txnBegin() {}
    @Override public void txnCommit() {}
    @Override public void txnAbort() {}
    @Override public void segment() {}
  });
}

/**
 * Applies operations from a patch, filtering by graph.
 *
 * @param graphFilter set of graph nodes to filter
 * @param include if true, include only operations in graphFilter; if false, exclude them
 */
private static void applyGraphFilteredOperations(RDFPatchBuilder builder, RDFPatch patch,
                                                 Set<Node> graphFilter, boolean include) {
  patch.apply(new RDFChanges() {
    @Override
    public void add(Node g, Node s, Node p, Node o) {
      boolean inFilter = graphFilter.contains(g);
      if (include ? inFilter : !inFilter) {
        builder.add(g, s, p, o);
      }
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
      boolean inFilter = graphFilter.contains(g);
      if (include ? inFilter : !inFilter) {
        builder.delete(g, s, p, o);
      }
    }

    // Other methods: no-op
    @Override public void start() {}
    @Override public void finish() {}
    @Override public void header(String field, Node value) {}
    @Override public void addPrefix(Node gn, String prefix, String uriStr) {}
    @Override public void deletePrefix(Node gn, String prefix) {}
    @Override public void txnBegin() {}
    @Override public void txnCommit() {}
    @Override public void txnAbort() {}
    @Override public void segment() {}
  });
}
```

---

### Step 3: Update Command and Event

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommand.java`

```java
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String strategy,          // NEW: "three-way", "ours", "theirs"
    String conflictScope,     // NEW: "graph", "dataset"
    String author,
    String message
) implements Command {
}
```

**File:** `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java`

```java
public record BranchMergedEvent(
    String eventId,
    String dataset,
    String branchName,
    String sourceRef,
    String commitId,
    List<String> parentIds,
    RDFPatch patch,
    int patchSize,
    String author,
    String message,
    Instant timestamp,
    Integer conflictsResolved  // NEW: nullable for backward compatibility
) implements Event {

  // Constructor without conflictsResolved (backward compatibility)
  public BranchMergedEvent(String eventId, String dataset, String branchName,
                           String sourceRef, String commitId, List<String> parentIds,
                           RDFPatch patch, int patchSize, String author,
                           String message, Instant timestamp) {
    this(eventId, dataset, branchName, sourceRef, commitId, parentIds,
         patch, patchSize, author, message, timestamp, null);
  }
}
```

---

### Step 4: Update Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`

Update `performThreeWayMerge`:

```java
private Event performThreeWayMerge(MergeCommand cmd, String intoCommitId, String fromCommitId) {
  // Find common ancestor
  String ancestorId = ancestorFinder.findCommonAncestor(cmd.dataset(), intoCommitId, fromCommitId);
  if (ancestorId == null) {
    throw new IllegalStateException("No common ancestor found");
  }

  // Load datasets
  Dataset ancestorData = datasetService.loadCommitData(cmd.dataset(), ancestorId);
  Dataset intoData = datasetService.loadCommitData(cmd.dataset(), intoCommitId);
  Dataset fromData = datasetService.loadCommitData(cmd.dataset(), fromCommitId);

  // Compute diffs
  RDFPatch baseToInto = GraphDiffUtil.computeDiff(ancestorData, intoData);
  RDFPatch baseToFrom = GraphDiffUtil.computeDiff(ancestorData, fromData);

  // Detect conflicts
  List<MergeConflict> conflicts = MergeUtil.detectConflicts(baseToInto, baseToFrom);

  RDFPatch mergedPatch;
  int conflictsResolved = 0;

  if (!conflicts.isEmpty()) {
    String strategy = cmd.strategy() != null ? cmd.strategy() : "three-way";
    String conflictScope = cmd.conflictScope() != null ? cmd.conflictScope() : "graph";

    switch (strategy) {
      case "ours":
        mergedPatch = MergeUtil.resolveWithOurs(baseToInto, baseToFrom, conflicts, conflictScope);
        conflictsResolved = conflicts.size();
        break;

      case "theirs":
        mergedPatch = MergeUtil.resolveWithTheirs(baseToInto, baseToFrom, conflicts, conflictScope);
        conflictsResolved = conflicts.size();
        break;

      case "three-way":
      default:
        // Phase 1 behavior: throw exception
        throw new MergeConflictException(conflicts);
    }
  } else {
    // No conflicts: combine both patches
    mergedPatch = combineDiffs(baseToInto, baseToFrom);
  }

  return createMergeCommit(cmd, intoCommitId, fromCommitId, mergedPatch, conflictsResolved);
}
```

---

### Step 5: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/MergeController.java`

```java
MergeCommand command = new MergeCommand(
    dataset,
    request.into(),
    request.from(),
    request.normalizedFastForward(),
    request.normalizedStrategy(),
    request.normalizedConflictScope(),  // NEW
    author,
    null
);

// ... later in response building:

} else if (event instanceof BranchMergedEvent bme) {
  return ResponseEntity.ok(MergeResponse.merged(
      request.into(),
      request.from(),
      bme.commitId(),
      request.normalizedStrategy(),
      request.normalizedConflictScope(),
      bme.conflictsResolved() != null ? bme.conflictsResolved() : 0
  ));
}
```

---

### Step 6: Write Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`

```java
@Test
void merge_oursStrategy_graphScope_shouldResolveConflictingGraphsOnly() {
  // Arrange: Conflict in Graph-A, changes in Graph-B
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  // Graph-A: conflicting age updates
  applyPatch("branch-a", """
    D <http://example.org/graphA> <urn:Alice> <urn:age> "30" .
    A <http://example.org/graphA> <urn:Alice> <urn:age> "31" .
    """);

  applyPatch("branch-b", """
    D <http://example.org/graphA> <urn:Alice> <urn:age> "30" .
    A <http://example.org/graphA> <urn:Alice> <urn:age> "32" .
    A <http://example.org/graphB> <urn:Bob> <urn:name> "Bob" .
    """);

  // Act: Merge with graph-level "ours"
  MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", "ours", "graph", null);
  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().strategy()).isEqualTo("ours");
  assertThat(response.getBody().conflictScope()).isEqualTo("graph");
  assertThat(response.getBody().conflictsResolved()).isGreaterThan(0);
}

@Test
void merge_oursStrategy_datasetScope_shouldDiscardAllFromChanges() {
  // Same setup as above
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  applyPatch("branch-a", """
    D <http://example.org/graphA> <urn:Alice> <urn:age> "30" .
    A <http://example.org/graphA> <urn:Alice> <urn:age> "31" .
    """);

  applyPatch("branch-b", """
    D <http://example.org/graphA> <urn:Alice> <urn:age> "30" .
    A <http://example.org/graphA> <urn:Alice> <urn:age> "32" .
    A <http://example.org/graphB> <urn:Bob> <urn:name> "Bob" .
    """);

  // Act: Merge with dataset-level "ours"
  MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", "ours", "dataset", null);
  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().conflictScope()).isEqualTo("dataset");
  // With dataset scope, Graph-B changes from branch-b should also be discarded
}

@Test
void merge_theirsStrategy_graphScope_shouldKeepFromChangesInConflictingGraphs() {
  // Similar test for "theirs" strategy
}

@Test
void merge_invalidConflictScope_shouldReturn400() {
  MergeRequest request = new MergeRequest("main", "feature", "allow", "ours", "invalid", null);

  ResponseEntity<String> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      String.class
  );

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(response.getBody()).contains("Invalid conflictScope");
}
```

**File:** `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java`

```java
@Test
void resolveWithOurs_graphScope_shouldKeepIntoInConflictingGraphOnly() {
  // Test graph-level resolution
}

@Test
void resolveWithOurs_datasetScope_shouldKeepIntoEntireDataset() {
  // Test dataset-level resolution
}

@Test
void extractConflictingGraphs_shouldReturnUniqueGraphs() {
  List<MergeConflict> conflicts = List.of(
      new MergeConflict("http://example.org/graphA", "urn:s1", "urn:p1", "\"v1\"", null),
      new MergeConflict("http://example.org/graphA", "urn:s2", "urn:p2", "\"v2\"", null),
      new MergeConflict("http://example.org/graphB", "urn:s3", "urn:p3", "\"v3\"", null)
  );

  Set<Node> graphs = MergeUtil.extractConflictingGraphs(conflicts);

  assertThat(graphs).hasSize(2);
}
```

---

## Success Criteria

- ✅ `strategy` parameter accepted and validated
- ✅ `conflictScope` parameter accepted and validated (graph, dataset)
- ✅ "ours" strategy with graph-level scope works correctly
- ✅ "ours" strategy with dataset-level scope works correctly
- ✅ "theirs" strategy with both scopes works correctly
- ✅ Conflicting graphs extracted correctly from quad conflicts
- ✅ Non-conflicting graphs merged when using graph-level scope
- ✅ `conflictsResolved` count returned in response
- ✅ "three-way" strategy ignores conflictScope (returns 409)
- ✅ Integration tests pass (5+ test cases)
- ✅ Unit tests pass (scope extraction, both strategies, both scopes)
- ✅ Zero quality violations
- ✅ Full build passes

---

## Files to Modify

- `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`
- `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommand.java`
- `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`
- `src/main/java/org/chucc/vcserver/controller/MergeController.java`
- `src/main/java/org/chucc/vcserver/util/MergeUtil.java`
- `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`
- `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java`

---

## Edge Cases

1. **Single-graph dataset:** Both scopes behave identically
2. **Default graph only:** Handled via `Quad.defaultGraphIRI`
3. **Multiple conflicting graphs:** Each resolved independently (graph scope) or all together (dataset scope)
4. **Both branches add same quad:** Conservatively flagged as conflict, strategy handles gracefully
5. **Empty patches:** No conflicts, no operations

---

## Decision Matrix

| Scenario | Graph Scope | Dataset Scope |
|----------|-------------|---------------|
| Conflict in Graph-A only | Resolve Graph-A, merge Graph-B | Resolve entire dataset |
| Conflict in multiple graphs | Resolve each graph independently | Resolve entire dataset |
| Single graph dataset | Same as dataset scope | Conservative resolution |
| Inter-graph dependencies | May cause inconsistency ⚠️ | Safe ✅ |

**Recommendation:**
- Use `conflictScope="graph"` for multi-graph datasets with independent graphs
- Use `conflictScope="dataset"` for datasets with inter-graph dependencies or single-graph datasets

---

## Next Phase

- **Phase 3:** [Implement manual resolution](./03-implement-manual-resolution.md) ("manual" strategy) - **Optional**

**Note:** Phase 3 adds significant complexity. Evaluate Phase 2 in production before deciding on Phase 3.

---

## References

- [Phase 1 Task](./01-implement-merge-core.md)
- [Git Merge Strategies](https://git-scm.com/docs/merge-strategies)
- [Apache Jena RDFPatch](https://jena.apache.org/documentation/rdfpatch/)
- [RDF Named Graphs](https://www.w3.org/TR/rdf11-concepts/#section-dataset)
- [RDF 1.1 Concepts: Triples and Quads](https://www.w3.org/TR/rdf11-concepts/)
