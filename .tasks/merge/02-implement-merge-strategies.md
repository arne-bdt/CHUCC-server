# Task: Implement Merge API - Phase 2 (Conflict Resolution Strategies)

**Status:** Not Started (Requires Phase 1 Completion)
**Priority:** High
**Category:** Version Control Protocol (Phase 2 of 3)
**Estimated Time:** 2-3 hours

---

## Overview

Add automatic conflict resolution strategies ("ours" and "theirs") to the merge endpoint. This phase builds on Phase 1's conflict detection to auto-resolve conflicts based on strategy.

**Prerequisites:** Phase 1 must be completed (conflict detection working, returns 409).

---

## Scope of Phase 2

### ✅ In Scope
- Add `strategy` parameter to `MergeRequest`
- Implement "ours" strategy (keep target branch changes)
- Implement "theirs" strategy (keep source branch changes)
- Auto-resolve conflicts based on strategy
- Update response to include `conflictsResolved` count

### ❌ Out of Scope (Phase 3)
- "manual" strategy with resolution array
- `MergeResolution` DTO
- User-provided conflict resolutions

---

## Requirements

### Merge Strategies

**three-way (default):**
- Phase 1 behavior: return 409 if conflicts detected
- No changes needed

**ours:**
- Auto-resolve conflicts by keeping target branch ("into") changes
- If both branches modify same quad, keep "into" version
- Never returns 409 Conflict

**theirs:**
- Auto-resolve conflicts by keeping source branch ("from") changes
- If both branches modify same quad, keep "from" version
- Never returns 409 Conflict

### Updated Request

```http
POST /version/merge HTTP/1.1
Content-Type: application/json

{
  "into": "main",
  "from": "feature-x",
  "strategy": "ours",
  "fastForward": "allow"
}
```

**New Parameter:**
- `strategy` (optional) - Merge strategy: `"three-way"`, `"ours"`, `"theirs"` (default: `"three-way"`)

### Updated Response

```json
{
  "result": "merged",
  "mergeCommit": "01933e4a-9d4e-7000-8000-000000000005",
  "into": "main",
  "from": "feature-x",
  "strategy": "ours",
  "fastForward": false,
  "conflictsResolved": 3
}
```

**New Field:**
- `conflictsResolved` (integer) - Number of conflicts auto-resolved by strategy

---

## Strategy Resolution Logic

### "ours" Strategy

**Algorithm:**
1. Detect conflicts (same as Phase 1)
2. For each conflicting quad:
   - If quad in "into" changes: keep it
   - If quad in "from" changes only: apply it
3. Result: "into" branch wins all conflicts

**Example:**
```
Base:     (Alice, age, 30)
Into:     (Alice, age, 31)  [delete 30, add 31]
From:     (Alice, age, 32)  [delete 30, add 32]

Conflict: Both delete (Alice, age, 30)
Resolution (ours): Keep "into" changes → Result: (Alice, age, 31)
```

### "theirs" Strategy

**Algorithm:**
1. Detect conflicts (same as Phase 1)
2. For each conflicting quad:
   - If quad in "from" changes: keep it
   - If quad in "into" changes only: apply it
3. Result: "from" branch wins all conflicts

**Example:**
```
Base:     (Alice, age, 30)
Into:     (Alice, age, 31)  [delete 30, add 31]
From:     (Alice, age, 32)  [delete 30, add 32]

Conflict: Both delete (Alice, age, 30)
Resolution (theirs): Keep "from" changes → Result: (Alice, age, 32)
```

---

## Implementation Steps

### Step 1: Update DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`

Add `strategy` validation:
```java
public void validate() {
  // ... existing validation ...

  String strat = strategy != null ? strategy : "three-way";
  if (!List.of("three-way", "ours", "theirs", "manual").contains(strat)) {
    throw new IllegalArgumentException("Invalid strategy: " + strat);
  }
}

public String normalizedStrategy() {
  return strategy != null ? strategy : "three-way";
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`

Update static factory methods to accept `conflictsResolved`:
```java
public static MergeResponse merged(String into, String from, String mergeCommit,
                                   String strategy, int conflictsResolved) {
  return new MergeResponse(
      "merged",
      mergeCommit,
      into,
      from,
      null,
      false,
      strategy,
      conflictsResolved
  );
}
```

---

### Step 2: Update Command

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommand.java`

Add `strategy` field:
```java
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String strategy,      // NEW: "three-way", "ours", "theirs"
    String author,
    String message
) implements Command {
}
```

---

### Step 3: Implement Strategy Resolution

**File:** `src/main/java/org/chucc/vcserver/util/MergeUtil.java`

Add strategy resolution methods:
```java
/**
 * Resolves conflicts using "ours" strategy.
 *
 * <p>Keeps all changes from "into" branch, discards conflicting changes from "from" branch.
 *
 * @param baseToInto changes from base to "into"
 * @param baseToFrom changes from base to "from"
 * @param conflicts detected conflicts
 * @return merged patch with "into" changes winning
 */
public static RDFPatch resolveWithOurs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                       List<MergeConflict> conflicts) {
  Set<Quad> conflictQuads = extractQuadsFromConflicts(conflicts);

  // Start with "into" changes (they win)
  RDFPatchBuilder builder = new RDFPatchBuilder();
  applyPatch(builder, baseToInto);

  // Add non-conflicting changes from "from"
  addNonConflictingChanges(builder, baseToFrom, conflictQuads);

  return builder.build();
}

/**
 * Resolves conflicts using "theirs" strategy.
 *
 * <p>Keeps all changes from "from" branch, discards conflicting changes from "into" branch.
 *
 * @param baseToInto changes from base to "into"
 * @param baseToFrom changes from base to "from"
 * @param conflicts detected conflicts
 * @return merged patch with "from" changes winning
 */
public static RDFPatch resolveWithTheirs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                         List<MergeConflict> conflicts) {
  Set<Quad> conflictQuads = extractQuadsFromConflicts(conflicts);

  // Start with "from" changes (they win)
  RDFPatchBuilder builder = new RDFPatchBuilder();
  applyPatch(builder, baseToFrom);

  // Add non-conflicting changes from "into"
  addNonConflictingChanges(builder, baseToInto, conflictQuads);

  return builder.build();
}

private static Set<Quad> extractQuadsFromConflicts(List<MergeConflict> conflicts) {
  Set<Quad> quads = new HashSet<>();
  for (MergeConflict conflict : conflicts) {
    // Convert MergeConflict back to Quad
    // (You'll need to parse the strings back to Nodes)
    quads.add(conflictToQuad(conflict));
  }
  return quads;
}

private static void addNonConflictingChanges(RDFPatchBuilder builder, RDFPatch patch,
                                            Set<Quad> conflictQuads) {
  patch.apply(new RDFChanges() {
    @Override
    public void add(Node g, Node s, Node p, Node o) {
      Quad quad = new Quad(g, s, p, o);
      if (!conflictQuads.contains(quad)) {
        builder.add(g, s, p, o);
      }
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
      Quad quad = new Quad(g, s, p, o);
      if (!conflictQuads.contains(quad)) {
        builder.delete(g, s, p, o);
      }
    }

    // Other methods: no-op
  });
}
```

---

### Step 4: Update Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`

Update `performThreeWayMerge` to handle strategies:
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
        // Keep "into" branch changes
        mergedPatch = MergeUtil.resolveWithOurs(baseToInto, baseToFrom, conflicts);
        conflictsResolved = conflicts.size();
        break;

      case "theirs":
        // Keep "from" branch changes
        mergedPatch = MergeUtil.resolveWithTheirs(baseToInto, baseToFrom, conflicts);
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

  // Create merge commit
  return createMergeCommit(cmd, intoCommitId, fromCommitId, mergedPatch, conflictsResolved);
}

private Event createMergeCommit(MergeCommand cmd, String intoCommitId, String fromCommitId,
                                RDFPatch mergedPatch, int conflictsResolved) {
  int patchSize = countPatchOperations(mergedPatch);
  String mergeCommitId = uuidGenerator.generateCommitId();

  return new BranchMergedEvent(
      uuidGenerator.generateEventId(),
      cmd.dataset(),
      cmd.into(),
      cmd.from(),
      mergeCommitId,
      List.of(intoCommitId, fromCommitId),
      mergedPatch,
      patchSize,
      cmd.author(),
      cmd.message() != null ? cmd.message() : "Merge " + cmd.from() + " into " + cmd.into(),
      Instant.now()
  );
}

private RDFPatch combineDiffs(RDFPatch baseToInto, RDFPatch baseToFrom) {
  // Simple combination: apply both patches
  RDFPatchBuilder builder = new RDFPatchBuilder();
  applyPatch(builder, baseToInto);
  applyPatch(builder, baseToFrom);
  return builder.build();
}
```

---

### Step 5: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/MergeController.java`

Pass `strategy` to command:
```java
MergeCommand command = new MergeCommand(
    dataset,
    request.into(),
    request.from(),
    request.normalizedFastForward(),
    request.normalizedStrategy(),  // NEW
    author,
    null
);
```

Update response building:
```java
} else if (event instanceof BranchMergedEvent bme) {
  return ResponseEntity.ok(MergeResponse.merged(
      request.into(),
      request.from(),
      bme.commitId(),
      request.normalizedStrategy(),  // NEW
      conflictsResolvedCount           // NEW (extract from handler)
  ));
}
```

**Note:** You'll need to return `conflictsResolved` count from handler. Consider adding it to `BranchMergedEvent`:
```java
public record BranchMergedEvent(
    // ... existing fields ...
    int conflictsResolved  // NEW
) implements Event {
}
```

---

### Step 6: Write Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`

Add tests for strategies:
```java
@Test
void merge_oursStrategy_shouldResolveConflictsWithIntoChanges() {
  // Arrange: Create diverged branches
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  // Add conflicting data
  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"31\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"32\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with "ours" strategy
  MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", "ours", null);
  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().result()).isEqualTo("merged");
  assertThat(response.getBody().strategy()).isEqualTo("ours");
  assertThat(response.getBody().conflictsResolved()).isGreaterThan(0);

  // Verify: Result has "31" (from branch-a, not "32" from branch-b)
  String result = queryGraph("branch-a", "SELECT ?age WHERE { <http://example.org/Alice> <http://example.org/age> ?age }");
  assertThat(result).contains("31");
  assertThat(result).doesNotContain("32");
}

@Test
void merge_theirsStrategy_shouldResolveConflictsWithFromChanges() {
  // Arrange: Create diverged branches
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  // Add conflicting data
  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"31\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/age> \"32\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with "theirs" strategy
  MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", "theirs", null);
  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().result()).isEqualTo("merged");
  assertThat(response.getBody().strategy()).isEqualTo("theirs");
  assertThat(response.getBody().conflictsResolved()).isGreaterThan(0);

  // Verify: Result has "32" (from branch-b, not "31" from branch-a)
  String result = queryGraph("branch-a", "SELECT ?age WHERE { <http://example.org/Alice> <http://example.org/age> ?age }");
  assertThat(result).contains("32");
  assertThat(result).doesNotContain("31");
}

@Test
void merge_oursStrategy_withNoConflicts_shouldApplyBothChanges() {
  // Arrange: Create diverged branches with non-overlapping changes
  String baseCommit = getCurrentCommit("main");
  createBranch("branch-a", baseCommit);
  createBranch("branch-b", baseCommit);

  String patchA = "A <http://example.org/g> <http://example.org/Alice> <http://example.org/name> \"Alice\" .";
  applyPatch("branch-a", patchA);

  String patchB = "A <http://example.org/g> <http://example.org/Bob> <http://example.org/name> \"Bob\" .";
  applyPatch("branch-b", patchB);

  // Act: Merge with "ours" strategy
  MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", "ours", null);
  ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
      "/version/merge?dataset=test",
      request,
      MergeResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody().conflictsResolved()).isEqualTo(0);

  // Verify: Both changes applied
  String result = queryGraph("branch-a", "SELECT ?name WHERE { ?s <http://example.org/name> ?name }");
  assertThat(result).contains("Alice");
  assertThat(result).contains("Bob");
}
```

**File:** `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java`

Add unit tests for strategy resolution:
```java
@Test
void resolveWithOurs_shouldKeepIntoChanges() {
  // Arrange
  RDFPatch baseToInto = createPatch("A <g> <s> <p> \"31\" .");
  RDFPatch baseToFrom = createPatch("A <g> <s> <p> \"32\" .");
  List<MergeConflict> conflicts = List.of(new MergeConflict(...));

  // Act
  RDFPatch result = MergeUtil.resolveWithOurs(baseToInto, baseToFrom, conflicts);

  // Assert
  assertThat(result).contains("31");
  assertThat(result).doesNotContain("32");
}

@Test
void resolveWithTheirs_shouldKeepFromChanges() {
  // Similar test for "theirs"
}
```

---

## Success Criteria

- ✅ `strategy` parameter accepted and validated
- ✅ "ours" strategy resolves conflicts (keeps "into" changes)
- ✅ "theirs" strategy resolves conflicts (keeps "from" changes)
- ✅ `conflictsResolved` count returned in response
- ✅ Non-conflicting changes always applied
- ✅ "three-way" strategy still returns 409 on conflicts (Phase 1 behavior preserved)
- ✅ Integration tests pass (3+ new test cases)
- ✅ Unit tests pass
- ✅ Zero quality violations
- ✅ Full build passes

---

## Files to Modify

- `src/main/java/org/chucc/vcserver/dto/MergeRequest.java` (add strategy validation)
- `src/main/java/org/chucc/vcserver/dto/MergeResponse.java` (add conflictsResolved parameter)
- `src/main/java/org/chucc/vcserver/command/MergeCommand.java` (add strategy field)
- `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java` (add conflictsResolved field)
- `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java` (implement strategies)
- `src/main/java/org/chucc/vcserver/controller/MergeController.java` (pass strategy)
- `src/main/java/org/chucc/vcserver/util/MergeUtil.java` (add strategy methods)
- `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java` (add strategy tests)
- `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java` (add strategy unit tests)

---

## Next Phase

- **Phase 3:** [Implement manual resolution](./03-implement-manual-resolution.md) ("manual" strategy with resolution array)

---

## References

- [Phase 1 Task](./01-implement-merge-core.md)
- [Git Merge Strategies](https://git-scm.com/docs/merge-strategies)
