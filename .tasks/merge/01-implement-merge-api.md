# Task: Implement Merge Endpoint

**Status:** Not Started
**Priority:** High
**Category:** Version Control Protocol
**Estimated Time:** 5-6 hours

---

## Overview

Implement the missing Merge endpoint that currently returns 501:
- `POST /version/merge` - Merge two refs/commits into a target branch

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.3](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [MergeController.java](../../src/main/java/org/chucc/vcserver/controller/MergeController.java)

**Not Implemented (returns 501):**
- ❌ `POST /version/merge` (line 53)

---

## Requirements

### POST /version/merge - Merge Branches/Commits

**Request:**
```http
POST /version/merge HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "into": "main",
  "from": "feature-x",
  "strategy": "three-way",
  "fastForward": "allow",
  "resolutions": []
}
```

**Parameters:**
- `into` (required) - Target branch name
- `from` (required) - Source ref (branch name or commit ID)
- `strategy` (optional) - Merge strategy: `three-way`, `ours`, `theirs`, `manual` (default: `three-way`)
- `fastForward` (optional) - Fast-forward mode: `allow`, `only`, `never` (default: `allow`)
- `resolutions` (optional) - Manual conflict resolutions (for `manual` strategy)

**Response:** 200 OK (successful merge)
```json
{
  "result": "merged",
  "mergeCommit": "01933e4a-9d4e-7000-8000-000000000005",
  "into": "main",
  "from": "feature-x",
  "strategy": "three-way",
  "fastForward": false,
  "conflictsResolved": 0
}
```

**Response:** 200 OK (fast-forward)
```json
{
  "result": "fast-forward",
  "mergeCommit": null,
  "into": "main",
  "from": "feature-x",
  "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
  "fastForward": true
}
```

**Response:** 409 Conflict
```json
{
  "type": "about:blank",
  "title": "Merge Conflict",
  "status": 409,
  "code": "merge_conflict",
  "conflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Alice",
      "predicate": "http://xmlns.com/foaf/0.1/name",
      "object": "\"Alice Smith\"",
      "details": "Conflicting values from both branches"
    }
  ]
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request or missing fields
- `404 Not Found` - Branch or commit not found
- `409 Conflict` - Merge conflicts detected
- `412 Precondition Failed` - If-Match mismatch

**CQRS Pattern:**
- Command: `MergeCommand(dataset, into, from, strategy, fastForward, author, message, resolutions)`
- Handler: `MergeCommandHandler`
- Event: `BranchMergedEvent(dataset, into, from, mergeCommitId, strategy, conflicts)`

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `dto/MergeRequest.java`
- `dto/MergeResponse.java`
- `dto/MergeConflict.java`
- `dto/MergeResolution.java`

**File:** `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MergeRequest(
    String into,
    String from,
    @JsonProperty(defaultValue = "three-way") String strategy,
    @JsonProperty(defaultValue = "allow") String fastForward,
    List<MergeResolution> resolutions
) {
  public void validate() {
    if (into == null || into.isBlank()) {
      throw new IllegalArgumentException("Target branch (into) is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }
    if (strategy != null && !List.of("three-way", "ours", "theirs", "manual").contains(strategy)) {
      throw new IllegalArgumentException("Invalid strategy: " + strategy);
    }
    if (fastForward != null && !List.of("allow", "only", "never").contains(fastForward)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + fastForward);
    }
  }
}
```

### Step 2: Create Command & Event

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommand.java`
**File:** `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java`

### Step 3: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`

```java
@Component
public class MergeCommandHandler implements CommandHandler {

  @Override
  public Event handle(Command command) {
    MergeCommand cmd = (MergeCommand) command;

    // 1. Resolve refs to commit IDs
    String intoCommit = resolveRef(cmd.dataset(), cmd.into());
    String fromCommit = resolveRef(cmd.dataset(), cmd.from());

    // 2. Check for fast-forward possibility
    if (canFastForward(intoCommit, fromCommit)) {
      if ("never".equals(cmd.fastForward())) {
        // Create merge commit even if fast-forward possible
        return createMergeCommit(cmd, intoCommit, fromCommit);
      } else {
        // Fast-forward: just move branch pointer
        return createFastForwardEvent(cmd, fromCommit);
      }
    }

    // 3. Check fast-forward-only mode
    if ("only".equals(cmd.fastForward())) {
      throw new IllegalStateException("Fast-forward not possible, but fastForward=only");
    }

    // 4. Perform three-way merge
    return performMerge(cmd, intoCommit, fromCommit);
  }

  private Event performMerge(MergeCommand cmd, String base, String head) {
    // Find common ancestor
    String ancestor = findCommonAncestor(base, head);

    // Load three versions
    Dataset baseData = loadCommitData(ancestor);
    Dataset intoData = loadCommitData(base);
    Dataset fromData = loadCommitData(head);

    // Compute diffs
    RdfPatch baseToInto = computeDiff(baseData, intoData);
    RdfPatch baseToFrom = computeDiff(baseData, fromData);

    // Detect conflicts
    List<MergeConflict> conflicts = detectConflicts(baseToInto, baseToFrom);

    if (!conflicts.isEmpty()) {
      if ("manual".equals(cmd.strategy()) && cmd.resolutions() != null) {
        // Apply manual resolutions
        applyResolutions(conflicts, cmd.resolutions());
      } else if ("ours".equals(cmd.strategy())) {
        // Keep "into" branch changes
        conflicts.clear();
      } else if ("theirs".equals(cmd.strategy())) {
        // Keep "from" branch changes
        conflicts.clear();
      } else {
        // three-way strategy with unresolved conflicts
        throw new MergeConflictException(conflicts);
      }
    }

    // Create merge commit
    String mergeCommitId = uuidGenerator.generateCommitId();
    RdfPatch mergedPatch = combineDiffs(baseToInto, baseToFrom);

    BranchMergedEvent event = new BranchMergedEvent(
        uuidGenerator.generateEventId(),
        cmd.dataset(),
        cmd.into(),
        cmd.from(),
        mergeCommitId,
        List.of(base, head),
        mergedPatch,
        cmd.author(),
        cmd.message(),
        Instant.now()
    );

    eventPublisher.publish(event);
    return event;
  }
}
```

### Step 4: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/MergeController.java`

Replace 501 stub with full implementation.

### Step 5: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add handler for `BranchMergedEvent`.

### Step 6: Write Tests

**Integration Tests:**
- Fast-forward merge
- Three-way merge (no conflicts)
- Merge conflicts (409 response)
- Merge with "ours" strategy
- Merge with "theirs" strategy
- Fast-forward-only mode failures

**Unit Tests:**
- `MergeCommandHandlerTest.java`
- Test conflict detection
- Test merge strategies

---

## Merge Algorithm

**Three-Way Merge:**
1. Find common ancestor commit
2. Load three versions: ancestor, base (into), head (from)
3. Compute diffs: ancestor→base, ancestor→head
4. Detect conflicts (overlapping changes to same quads)
5. Apply strategy (three-way, ours, theirs, manual)
6. Create merge commit with two parents

**Fast-Forward:**
- Occurs when `into` is ancestor of `from`
- No merge commit created, just move branch pointer
- Can be disabled with `fastForward: "never"`

**Conflict Detection:**
- Two diffs conflict if they modify the same quad
- Example: Both branches change `<s> <p> ?o`
- Conflict item must include `graph`, `subject`, `predicate`, `object`

---

## Success Criteria

- ✅ Endpoint implemented (no 501 response)
- ✅ DTOs created with validation
- ✅ Command/Event/Handler implemented
- ✅ All merge strategies work (three-way, ours, theirs)
- ✅ Fast-forward detection works
- ✅ Conflict detection works (409 response)
- ✅ Integration tests pass
- ✅ Unit tests pass
- ✅ Zero quality violations
- ✅ Full build passes

---

## Complexity Notes

This is the most complex task:
- Requires three-way merge algorithm
- Conflict detection logic
- Common ancestor finding
- Multiple merge strategies
- Consider breaking into sub-tasks if needed

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.3](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [MergeController.java](../../src/main/java/org/chucc/vcserver/controller/MergeController.java)
- [Git Three-Way Merge Algorithm](https://en.wikipedia.org/wiki/Merge_(version_control)#Three-way_merge)
