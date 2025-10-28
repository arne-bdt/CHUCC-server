# Task: Implement Merge API - Phase 3 (Manual Conflict Resolution)

**Status:** Not Started (Requires Phase 2 Completion)
**Priority:** Medium (Optional Feature)
**Category:** Version Control Protocol (Phase 3 of 3)
**Estimated Time:** 3-4 hours

---

## Overview

Add manual conflict resolution support, allowing users to specify exact resolutions for detected conflicts via the `resolutions` array.

**Prerequisites:** Phase 2 must be completed (strategies working).

**Note:** This is an advanced feature. Consider deferring if protocol compliance doesn't require it.

---

## Scope of Phase 3

### ✅ In Scope
- Add `MergeResolution` DTO
- Implement "manual" strategy
- Validate that resolutions match detected conflicts
- Apply user-provided resolutions
- Return detailed error if resolutions incomplete

### ❌ Out of Scope
- Conflict resolution UI/wizard
- Partial resolution (all-or-nothing)
- Conflict merge markers (like Git's `<<<<<<<`)

---

## Requirements

### Manual Resolution Request

```http
POST /version/merge HTTP/1.1
Content-Type: application/json

{
  "into": "main",
  "from": "feature-x",
  "strategy": "manual",
  "resolutions": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Alice",
      "predicate": "http://xmlns.com/foaf/0.1/age",
      "action": "keep-into",
      "customValue": null
    },
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Bob",
      "predicate": "http://xmlns.com/foaf/0.1/name",
      "action": "keep-from",
      "customValue": null
    },
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Charlie",
      "predicate": "http://xmlns.com/foaf/0.1/age",
      "action": "custom",
      "customValue": "\"35\""
    }
  ]
}
```

**Resolution Actions:**
- `"keep-into"` - Keep target branch ("into") value
- `"keep-from"` - Keep source branch ("from") value
- `"keep-both"` - Keep both values (for multi-valued properties)
- `"delete-both"` - Delete both values
- `"custom"` - Use `customValue` (must be valid RDF term)

### Validation Rules

1. **Strategy Check:** `resolutions` only used when `strategy = "manual"`
2. **Completeness:** Must provide resolution for EVERY detected conflict
3. **Matching:** Each resolution must match a detected conflict (g, s, p)
4. **Custom Value:** Required when `action = "custom"`

### Error Response (Incomplete Resolutions)

```json
{
  "type": "about:blank",
  "title": "Incomplete Conflict Resolutions",
  "status": 400,
  "code": "incomplete_resolutions",
  "detail": "Manual strategy requires resolutions for all conflicts",
  "unresolvedConflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Dave",
      "predicate": "http://xmlns.com/foaf/0.1/age",
      "object": "\"40\""
    }
  ]
}
```

---

## Implementation Steps

### Step 1: Create MergeResolution DTO

**File:** `src/main/java/org/chucc/vcserver/dto/MergeResolution.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents a user-provided conflict resolution.
 *
 * <p>Used with "manual" merge strategy to specify how to resolve specific conflicts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeResolution(
    @NotBlank(message = "Graph URI is required")
    String graph,

    @NotBlank(message = "Subject URI is required")
    String subject,

    @NotBlank(message = "Predicate URI is required")
    String predicate,

    @NotBlank(message = "Action is required")
    String action,  // "keep-into", "keep-from", "keep-both", "delete-both", "custom"

    String customValue  // Required if action = "custom"
) {
  /**
   * Validates the resolution.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (graph == null || graph.isBlank()) {
      throw new IllegalArgumentException("Graph URI is required");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("Subject URI is required");
    }
    if (predicate == null || predicate.isBlank()) {
      throw new IllegalArgumentException("Predicate URI is required");
    }
    if (action == null || action.isBlank()) {
      throw new IllegalArgumentException("Action is required");
    }

    if (!java.util.List.of("keep-into", "keep-from", "keep-both", "delete-both", "custom")
        .contains(action)) {
      throw new IllegalArgumentException("Invalid action: " + action);
    }

    if ("custom".equals(action) && (customValue == null || customValue.isBlank())) {
      throw new IllegalArgumentException("customValue is required when action='custom'");
    }
  }

  /**
   * Checks if this resolution matches a conflict.
   *
   * @param conflict the conflict to match
   * @return true if (g, s, p) match
   */
  public boolean matches(MergeConflict conflict) {
    return graph.equals(conflict.graph())
        && subject.equals(conflict.subject())
        && predicate.equals(conflict.predicate());
  }
}
```

---

### Step 2: Update MergeRequest DTO

**File:** `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`

Change `resolutions` type from `Object` to `List<MergeResolution>`:
```java
public record MergeRequest(
    @NotBlank(message = "Target branch (into) is required")
    String into,

    @NotBlank(message = "Source ref (from) is required")
    String from,

    @JsonProperty(defaultValue = "allow")
    String fastForward,

    @JsonProperty(defaultValue = "three-way")
    String strategy,

    List<MergeResolution> resolutions  // Changed from Object
) {
  public void validate() {
    // ... existing validation ...

    // Validate resolutions if provided
    if (resolutions != null) {
      for (MergeResolution resolution : resolutions) {
        resolution.validate();
      }

      // Manual strategy requires resolutions
      if ("manual".equals(normalizedStrategy()) && resolutions.isEmpty()) {
        throw new IllegalArgumentException("Manual strategy requires at least one resolution");
      }
    }

    // If strategy is NOT manual, resolutions should be empty
    if (!"manual".equals(normalizedStrategy()) && resolutions != null && !resolutions.isEmpty()) {
      throw new IllegalArgumentException("Resolutions are only used with 'manual' strategy");
    }
  }
}
```

---

### Step 3: Implement Manual Resolution Logic

**File:** `src/main/java/org/chucc/vcserver/util/MergeUtil.java`

Add manual resolution method:
```java
/**
 * Resolves conflicts using manual resolutions.
 *
 * @param baseToInto changes from base to "into"
 * @param baseToFrom changes from base to "from"
 * @param conflicts detected conflicts
 * @param resolutions user-provided resolutions
 * @return merged patch with manual resolutions applied
 * @throws IllegalArgumentException if resolutions incomplete
 */
public static RDFPatch resolveManually(RDFPatch baseToInto, RDFPatch baseToFrom,
                                       List<MergeConflict> conflicts,
                                       List<MergeResolution> resolutions) {
  // 1. Validate completeness
  List<MergeConflict> unresolved = new ArrayList<>();
  for (MergeConflict conflict : conflicts) {
    boolean resolved = resolutions.stream()
        .anyMatch(res -> res.matches(conflict));
    if (!resolved) {
      unresolved.add(conflict);
    }
  }

  if (!unresolved.isEmpty()) {
    throw new IncompleteResolutionsException(unresolved);
  }

  // 2. Build merged patch
  RDFPatchBuilder builder = new RDFPatchBuilder();

  // Extract conflict quads
  Set<Quad> conflictQuads = extractQuadsFromConflicts(conflicts);

  // Apply non-conflicting changes from both branches
  addNonConflictingChanges(builder, baseToInto, conflictQuads);
  addNonConflictingChanges(builder, baseToFrom, conflictQuads);

  // Apply manual resolutions
  for (MergeResolution resolution : resolutions) {
    applyResolution(builder, resolution, baseToInto, baseToFrom);
  }

  return builder.build();
}

private static void applyResolution(RDFPatchBuilder builder, MergeResolution resolution,
                                    RDFPatch baseToInto, RDFPatch baseToFrom) {
  Node g = NodeFactory.createURI(resolution.graph());
  Node s = NodeFactory.createURI(resolution.subject());
  Node p = NodeFactory.createURI(resolution.predicate());

  switch (resolution.action()) {
    case "keep-into":
      // Find matching quad in baseToInto and apply it
      findAndApply(builder, baseToInto, g, s, p);
      break;

    case "keep-from":
      // Find matching quad in baseToFrom and apply it
      findAndApply(builder, baseToFrom, g, s, p);
      break;

    case "keep-both":
      // Apply both quads
      findAndApply(builder, baseToInto, g, s, p);
      findAndApply(builder, baseToFrom, g, s, p);
      break;

    case "delete-both":
      // Don't apply either (both deleted)
      break;

    case "custom":
      // Parse custom value and add
      Node o = parseRDFTerm(resolution.customValue());
      builder.add(g, s, p, o);
      break;

    default:
      throw new IllegalArgumentException("Unknown action: " + resolution.action());
  }
}

private static void findAndApply(RDFPatchBuilder builder, RDFPatch patch,
                                Node g, Node s, Node p) {
  patch.apply(new RDFChanges() {
    @Override
    public void add(Node graph, Node subj, Node pred, Node obj) {
      if (graph.equals(g) && subj.equals(s) && pred.equals(p)) {
        builder.add(graph, subj, pred, obj);
      }
    }

    @Override
    public void delete(Node graph, Node subj, Node pred, Node obj) {
      if (graph.equals(g) && subj.equals(s) && pred.equals(p)) {
        builder.delete(graph, subj, pred, obj);
      }
    }

    // Other methods: no-op
  });
}

private static Node parseRDFTerm(String value) {
  // Parse RDF term string (URI, literal, blank node)
  if (value.startsWith("<") && value.endsWith(">")) {
    return NodeFactory.createURI(value.substring(1, value.length() - 1));
  } else if (value.startsWith("\"")) {
    // Parse literal (may have language tag or datatype)
    return NodeFactory.createLiteral(value);
  } else if (value.startsWith("_:")) {
    return NodeFactory.createBlankNode(value.substring(2));
  } else {
    throw new IllegalArgumentException("Invalid RDF term: " + value);
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/exception/IncompleteResolutionsException.java`
```java
package org.chucc.vcserver.exception;

import org.chucc.vcserver.dto.MergeConflict;
import java.util.List;

/**
 * Exception thrown when manual resolutions are incomplete.
 */
public class IncompleteResolutionsException extends RuntimeException {

  private final List<MergeConflict> unresolvedConflicts;

  public IncompleteResolutionsException(List<MergeConflict> unresolvedConflicts) {
    super("Manual strategy requires resolutions for all conflicts. Missing: "
          + unresolvedConflicts.size());
    this.unresolvedConflicts = List.copyOf(unresolvedConflicts);
  }

  public List<MergeConflict> getUnresolvedConflicts() {
    return unresolvedConflicts;
  }
}
```

---

### Step 4: Update Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`

Update to handle manual strategy:
```java
private Event performThreeWayMerge(MergeCommand cmd, String intoCommitId, String fromCommitId) {
  // ... (existing code to load datasets and compute diffs) ...

  // Detect conflicts
  List<MergeConflict> conflicts = MergeUtil.detectConflicts(baseToInto, baseToFrom);

  RDFPatch mergedPatch;
  int conflictsResolved = 0;

  if (!conflicts.isEmpty()) {
    String strategy = cmd.strategy() != null ? cmd.strategy() : "three-way";

    switch (strategy) {
      case "ours":
        mergedPatch = MergeUtil.resolveWithOurs(baseToInto, baseToFrom, conflicts);
        conflictsResolved = conflicts.size();
        break;

      case "theirs":
        mergedPatch = MergeUtil.resolveWithTheirs(baseToInto, baseToFrom, conflicts);
        conflictsResolved = conflicts.size();
        break;

      case "manual":
        // NEW: Apply manual resolutions
        if (cmd.resolutions() == null || cmd.resolutions().isEmpty()) {
          throw new IllegalArgumentException("Manual strategy requires resolutions");
        }
        mergedPatch = MergeUtil.resolveManually(baseToInto, baseToFrom, conflicts,
                                                cmd.resolutions());
        conflictsResolved = conflicts.size();
        break;

      case "three-way":
      default:
        throw new MergeConflictException(conflicts);
    }
  } else {
    mergedPatch = combineDiffs(baseToInto, baseToFrom);
  }

  return createMergeCommit(cmd, intoCommitId, fromCommitId, mergedPatch, conflictsResolved);
}
```

---

### Step 5: Update Command

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommand.java`

Add `resolutions` field:
```java
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String strategy,
    List<MergeResolution> resolutions,  // NEW
    String author,
    String message
) implements Command {
}
```

---

### Step 6: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/MergeController.java`

Pass resolutions to command:
```java
MergeCommand command = new MergeCommand(
    dataset,
    request.into(),
    request.from(),
    request.normalizedFastForward(),
    request.normalizedStrategy(),
    request.resolutions(),  // NEW
    author,
    null
);
```

**File:** `src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java`

Add handler for `IncompleteResolutionsException`:
```java
@ExceptionHandler(IncompleteResolutionsException.class)
public ResponseEntity<ProblemDetail> handleIncompleteResolutions(
    IncompleteResolutionsException ex) {
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.BAD_REQUEST,
      "Manual strategy requires resolutions for all conflicts"
  );
  problem.setTitle("Incomplete Conflict Resolutions");
  problem.setProperty("code", "incomplete_resolutions");
  problem.setProperty("unresolvedConflicts", ex.getUnresolvedConflicts());

  return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
}
```

---

### Step 7: Write Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`

Add tests for manual resolution:
```java
@Test
void merge_manualStrategy_withCompleteResolutions_shouldApplyThem() {
  // Arrange: Create diverged branches
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"31\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"32\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with manual resolution (keep 31)
  MergeResolution resolution = new MergeResolution(
      "http://example.org/g",
      "http://example.org/Alice",
      "http://example.org/age",
      "keep-into",
      null
  );
  MergeRequest request = new MergeRequest(
      "branch-a",
      "branch-b",
      "allow",
      "manual",
      List.of(resolution)
  );

  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().conflictsResolved()).isEqualTo(1);

  // Verify result
  String result = queryGraph("branch-a", "SELECT ?age WHERE { <http://example.org/Alice> <http://example.org/age> ?age }");
  assertThat(result).contains("31");
}

@Test
void merge_manualStrategy_withIncompleteResolutions_shouldReturn400() {
  // Arrange: Create diverged branches with 2 conflicts
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"31\" .\n"
                + "A <http://example.org/g> <http://example.org/Bob> <http://example.org/age> \"41\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"32\" .\n"
                + "A <http://example.org/g> <http://example.org/Bob> <http://example.org/age> \"42\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with only 1 resolution (missing Bob)
  MergeResolution resolution = new MergeResolution(
      "http://example.org/g",
      "http://example.org/Alice",
      "http://example.org/age",
      "keep-into",
      null
  );
  MergeRequest request = new MergeRequest(
      "branch-a",
      "branch-b",
      "allow",
      "manual",
      List.of(resolution)
  );

  ResponseEntity<String> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  assertThat(response.getBody()).contains("incomplete_resolutions");
  assertThat(response.getBody()).contains("Bob");
}

@Test
void merge_manualStrategy_withCustomValue_shouldApplyCustomValue() {
  // Arrange: Create diverged branches
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"31\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"32\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with custom value (35)
  MergeResolution resolution = new MergeResolution(
      "http://example.org/g",
      "http://example.org/Alice",
      "http://example.org/age",
      "custom",
      "\"35\""
  );
  MergeRequest request = new MergeRequest(
      "branch-a",
      "branch-b",
      "allow",
      "manual",
      List.of(resolution)
  );

  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // Verify custom value
  String result = queryGraph("branch-a", "SELECT ?age WHERE { <http://example.org/Alice> <http://example.org/age> ?age }");
  assertThat(result).contains("35");
  assertThat(result).doesNotContain("31");
  assertThat(result).doesNotContain("32");
}
```

---

## Success Criteria

- ✅ `MergeResolution` DTO created with validation
- ✅ "manual" strategy accepts resolutions array
- ✅ Validation ensures resolutions are complete
- ✅ All resolution actions work (keep-into, keep-from, keep-both, delete-both, custom)
- ✅ Incomplete resolutions return 400 with unresolved conflicts
- ✅ Custom values parsed correctly (URIs, literals, blank nodes)
- ✅ Integration tests pass (4+ test cases)
- ✅ Unit tests pass
- ✅ Zero quality violations
- ✅ Full build passes

---

## Files to Create

**New Files:**
- `src/main/java/org/chucc/vcserver/dto/MergeResolution.java`
- `src/main/java/org/chucc/vcserver/exception/IncompleteResolutionsException.java`

**Files to Modify:**
- `src/main/java/org/chucc/vcserver/dto/MergeRequest.java` (change resolutions type)
- `src/main/java/org/chucc/vcserver/command/MergeCommand.java` (add resolutions field)
- `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java` (add manual case)
- `src/main/java/org/chucc/vcserver/controller/MergeController.java` (pass resolutions)
- `src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java` (add handler)
- `src/main/java/org/chucc/vcserver/util/MergeUtil.java` (add resolveManually method)
- `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java` (add tests)

---

## Design Considerations

### Why All-or-Nothing?

Manual resolution requires ALL conflicts to be resolved. Partial resolution is not supported because:
1. **Simplicity:** Easier to implement and test
2. **Safety:** No silent conflicts
3. **User workflow:** Users should resolve all conflicts before merging (Git model)

If partial resolution is needed later, add a `allowPartial` flag.

### Why No Conflict Markers?

Git-style conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) are:
- Text-file specific (don't work for RDF triples)
- Require client-side parsing
- Complex to implement correctly

Instead, we use structured JSON responses with explicit conflict objects.

### Future Enhancements

If needed later:
- **Conflict preview:** `POST /version/merge/preview` (dry-run, returns conflicts without merging)
- **Resolution templates:** Pre-defined resolution strategies per predicate
- **Conflict blame:** Show which commits introduced conflicting changes

---

## Previous Phases

- **Phase 1:** [Core merge functionality](./01-implement-merge-core.md)
- **Phase 2:** [Merge strategies](./02-implement-merge-strategies.md)

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.3](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [Git Merge Conflict Resolution](https://git-scm.com/book/en/v2/Git-Tools-Advanced-Merging)
