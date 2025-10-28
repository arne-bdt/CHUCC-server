# Task: Implement Merge API - Phase 1 (Core Functionality)

**Status:** Not Started
**Priority:** High
**Category:** Version Control Protocol (Phase 1 of 3)
**Estimated Time:** 4-5 hours

---

## Overview

Implement the core merge functionality with fast-forward detection and conflict detection. This phase does NOT include conflict resolution strategies - conflicts will return 409 responses.

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.3](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Scope of Phase 1

### ✅ In Scope
- Fast-forward detection and execution
- Three-way merge algorithm
- Common ancestor finding (LCA algorithm)
- Graph diffing utilities
- Conflict detection (return 409 Conflict)
- Basic DTOs and CQRS components

### ❌ Out of Scope (Later Phases)
- Conflict resolution strategies ("ours", "theirs", "manual")
- `MergeResolution` DTO
- Conflict auto-resolution

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
  "fastForward": "allow"
}
```

**Parameters (Phase 1):**
- `into` (required) - Target branch name
- `from` (required) - Source ref (branch name or commit ID)
- `fastForward` (optional) - Fast-forward mode: `allow`, `only`, `never` (default: `allow`)
- `strategy` (optional) - Ignored in Phase 1 (always "three-way")
- `resolutions` (optional) - Ignored in Phase 1

**Response: 200 OK (fast-forward)**
```json
{
  "result": "fast-forward",
  "mergeCommit": null,
  "into": "main",
  "from": "feature-x",
  "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
  "fastForward": true,
  "strategy": "fast-forward"
}
```

**Response: 409 Conflict (merge conflicts detected)**
```json
{
  "type": "about:blank",
  "title": "Merge Conflict",
  "status": 409,
  "code": "merge_conflict",
  "detail": "Merge conflicts detected between branches. Use 'ours' or 'theirs' strategy, or resolve manually.",
  "conflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/Alice",
      "predicate": "http://xmlns.com/foaf/0.1/name",
      "object": "\"Alice Smith\""
    }
  ]
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request or missing fields
- `404 Not Found` - Branch or commit not found
- `409 Conflict` - Merge conflicts detected
- `412 Precondition Failed` - If-Match mismatch
- `422 Unprocessable Entity` - Fast-forward not possible but `fastForward=only`

---

## Conflict Detection Rules

**Rule:** Any quad that appears in BOTH branches' changesets is flagged as a conflict.

**Conflict Detection Algorithm:**
```
1. Compute baseToInto diff (base → "into" branch)
2. Compute baseToFrom diff (base → "from" branch)
3. Union all quads touched by each branch:
   - intoTouched = intoChanges.additions ∪ intoChanges.deletions
   - fromTouched = fromChanges.additions ∪ fromChanges.deletions
4. Find overlapping quads:
   - conflicts = intoTouched ∩ fromTouched
5. If conflicts.isEmpty() → merge succeeds
   Else → return 409 Conflict
```

**Examples:**

| Base State | Branch A | Branch B | Conflict? |
|------------|----------|----------|-----------|
| Has (s,p,o) | Deletes (s,p,o) | Deletes (s,p,o) | ✅ Yes (conservative) |
| Has (s,p,o) | Deletes (s,p,o) | No change | ❌ No |
| Has (s,p,o) | Deletes (s,p,o), adds (s,p,o') | Deletes (s,p,o), adds (s,p,o'') | ✅ Yes (deletion overlap) |
| No (s,p,o) | Adds (s,p,o) | Adds (s,p,o) | ✅ Yes (conservative) |
| No (s,p,o) | Adds (s,p,o) | Adds (s,p,o') | ❌ No (different quads) |

**Note:** This is a conservative approach that flags false positives (e.g., both branches making identical changes). Phase 2 will add strategies to auto-resolve these.

---

## Implementation Steps

### Step 1: Create DTOs (No Resolution Support Yet)

**File:** `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for merge operation.
 *
 * <p>Phase 1: Only fastForward parameter is used. Strategy and resolutions are ignored.
 */
public record MergeRequest(
    @NotBlank(message = "Target branch (into) is required")
    String into,

    @NotBlank(message = "Source ref (from) is required")
    String from,

    @JsonProperty(defaultValue = "allow")
    String fastForward,

    // Phase 2+: Not used in Phase 1
    String strategy,

    // Phase 3: Not used in Phase 1
    Object resolutions
) {
  /**
   * Validates the merge request.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (into == null || into.isBlank()) {
      throw new IllegalArgumentException("Target branch (into) is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }

    String ff = fastForward != null ? fastForward : "allow";
    if (!java.util.List.of("allow", "only", "never").contains(ff)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + ff);
    }
  }

  /**
   * Returns the normalized fast-forward mode.
   *
   * @return fast-forward mode (defaults to "allow")
   */
  public String normalizedFastForward() {
    return fastForward != null ? fastForward : "allow";
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for merge operation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeResponse(
    String result,           // "merged", "fast-forward", "conflict"
    String mergeCommit,      // Null for fast-forward
    String into,
    String from,
    String headCommit,       // For fast-forward: the commit that HEAD now points to
    Boolean fastForward,
    String strategy,         // Phase 1: always "three-way" or "fast-forward"
    Integer conflictsResolved // Phase 2+: always 0 in Phase 1
) {
  /**
   * Creates a fast-forward merge response.
   *
   * @param into target branch
   * @param from source ref
   * @param headCommit the commit HEAD now points to
   * @return fast-forward response
   */
  public static MergeResponse fastForward(String into, String from, String headCommit) {
    return new MergeResponse(
        "fast-forward",
        null,
        into,
        from,
        headCommit,
        true,
        "fast-forward",
        0
    );
  }

  /**
   * Creates a merge commit response.
   *
   * @param into target branch
   * @param from source ref
   * @param mergeCommit ID of created merge commit
   * @return merge response
   */
  public static MergeResponse merged(String into, String from, String mergeCommit) {
    return new MergeResponse(
        "merged",
        mergeCommit,
        into,
        from,
        null,
        false,
        "three-way",
        0
    );
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/MergeConflict.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a single merge conflict.
 *
 * <p>In Phase 1, this represents a quad that was modified by both branches.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeConflict(
    String graph,
    String subject,
    String predicate,
    String object,
    String details
) {
  /**
   * Creates a conflict from a Jena Quad.
   *
   * @param quad the conflicting quad
   * @return conflict DTO
   */
  public static MergeConflict fromQuad(org.apache.jena.sparql.core.Quad quad) {
    return new MergeConflict(
        quad.getGraph().toString(),
        quad.getSubject().toString(),
        quad.getPredicate().toString(),
        quad.getObject().toString(),
        "Modified by both branches"
    );
  }
}
```

---

### Step 2: Create Merge Utilities

**File:** `src/main/java/org/chucc/vcserver/util/GraphDiffUtil.java`
```java
package org.chucc.vcserver.util;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.Quad;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for computing RDF graph diffs.
 */
public final class GraphDiffUtil {

  private GraphDiffUtil() {
    // Utility class
  }

  /**
   * Computes the diff from base dataset to target dataset.
   *
   * <p>Returns an RDFPatch containing:
   * - Deletions: quads in base but not in target
   * - Additions: quads in target but not in base
   *
   * @param base the base dataset
   * @param target the target dataset
   * @return RDFPatch representing the diff
   */
  public static RDFPatch computeDiff(Dataset base, Dataset target) {
    List<Quad> deletions = new ArrayList<>();
    List<Quad> additions = new ArrayList<>();

    // Find deletions (in base, not in target)
    base.asDatasetGraph().find().forEachRemaining(quad -> {
      if (!target.asDatasetGraph().contains(quad)) {
        deletions.add(quad);
      }
    });

    // Find additions (in target, not in base)
    target.asDatasetGraph().find().forEachRemaining(quad -> {
      if (!base.asDatasetGraph().contains(quad)) {
        additions.add(quad);
      }
    });

    // Build RDFPatch
    return RDFPatchOps.withWriter(writer -> {
      writer.header("diff", base.hashCode() + " -> " + target.hashCode());
      deletions.forEach(writer::delete);
      additions.forEach(writer::add);
    });
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/util/MergeUtil.java`
```java
package org.chucc.vcserver.util;

import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.dto.MergeConflict;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility for merge conflict detection.
 */
public final class MergeUtil {

  private MergeUtil() {
    // Utility class
  }

  /**
   * Detects conflicts between two RDFPatch changesets.
   *
   * <p>A conflict occurs when the same quad appears in both changesets
   * (either as addition or deletion).
   *
   * @param intoChanges changes from base to "into" branch
   * @param fromChanges changes from base to "from" branch
   * @return list of conflicting quads
   */
  public static List<MergeConflict> detectConflicts(RDFPatch intoChanges, RDFPatch fromChanges) {
    // Collect all quads touched by each branch
    Set<Quad> intoTouched = new HashSet<>();
    collectQuads(intoChanges, intoTouched);

    Set<Quad> fromTouched = new HashSet<>();
    collectQuads(fromChanges, fromTouched);

    // Find overlapping quads
    Set<Quad> overlapping = new HashSet<>(intoTouched);
    overlapping.retainAll(fromTouched);

    // Convert to conflict DTOs
    List<MergeConflict> conflicts = new ArrayList<>();
    for (Quad quad : overlapping) {
      conflicts.add(MergeConflict.fromQuad(quad));
    }

    return conflicts;
  }

  private static void collectQuads(RDFPatch patch, Set<Quad> quads) {
    // Use RDFPatch visitor to collect all quads
    patch.apply(new org.apache.jena.rdfpatch.RDFChanges() {
      @Override
      public void add(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                      org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        quads.add(new Quad(g, s, p, o));
      }

      @Override
      public void delete(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                         org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        quads.add(new Quad(g, s, p, o));
      }

      // Other methods: no-op
      @Override public void start() {}
      @Override public void finish() {}
      @Override public void header(String field, org.apache.jena.graph.Node value) {}
      @Override public void addPrefix(org.apache.jena.graph.Node gn, String prefix, String uriStr) {}
      @Override public void deletePrefix(org.apache.jena.graph.Node gn, String prefix) {}
      @Override public void txnBegin() {}
      @Override public void txnCommit() {}
      @Override public void txnAbort() {}
      @Override public void segment() {}
    });
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/util/CommonAncestorFinder.java`
```java
package org.chucc.vcserver.util;

import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.repository.CommitRepository;
import java.util.*;

/**
 * Finds the lowest common ancestor (LCA) of two commits.
 *
 * <p>Uses a breadth-first search algorithm to find the first common ancestor.
 */
public final class CommonAncestorFinder {

  private final CommitRepository commitRepository;

  public CommonAncestorFinder(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Finds the lowest common ancestor of two commits.
   *
   * @param datasetName dataset name
   * @param commit1Id first commit ID
   * @param commit2Id second commit ID
   * @return LCA commit ID, or null if no common ancestor
   */
  public String findCommonAncestor(String datasetName, String commit1Id, String commit2Id) {
    if (commit1Id.equals(commit2Id)) {
      return commit1Id;
    }

    // BFS from both commits simultaneously
    Set<String> visited1 = new HashSet<>();
    Set<String> visited2 = new HashSet<>();
    Queue<String> queue1 = new LinkedList<>();
    Queue<String> queue2 = new LinkedList<>();

    queue1.add(commit1Id);
    queue2.add(commit2Id);
    visited1.add(commit1Id);
    visited2.add(commit2Id);

    while (!queue1.isEmpty() || !queue2.isEmpty()) {
      // Expand from commit1
      if (!queue1.isEmpty()) {
        String current = queue1.poll();
        if (visited2.contains(current)) {
          return current; // Found common ancestor
        }

        Optional<Commit> commit = commitRepository.findById(datasetName, current);
        if (commit.isPresent()) {
          for (String parentId : commit.get().getParentIds()) {
            if (visited1.add(parentId)) {
              queue1.add(parentId);
            }
          }
        }
      }

      // Expand from commit2
      if (!queue2.isEmpty()) {
        String current = queue2.poll();
        if (visited1.contains(current)) {
          return current; // Found common ancestor
        }

        Optional<Commit> commit = commitRepository.findById(datasetName, current);
        if (commit.isPresent()) {
          for (String parentId : commit.get().getParentIds()) {
            if (visited2.add(parentId)) {
              queue2.add(parentId);
            }
          }
        }
      }
    }

    return null; // No common ancestor found
  }

  /**
   * Checks if commit1 is an ancestor of commit2 (commit2 is reachable from commit1).
   *
   * @param datasetName dataset name
   * @param ancestorId potential ancestor commit ID
   * @param descendantId potential descendant commit ID
   * @return true if ancestorId is ancestor of descendantId
   */
  public boolean isAncestor(String datasetName, String ancestorId, String descendantId) {
    if (ancestorId.equals(descendantId)) {
      return true;
    }

    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(descendantId);
    visited.add(descendantId);

    while (!queue.isEmpty()) {
      String current = queue.poll();

      Optional<Commit> commit = commitRepository.findById(datasetName, current);
      if (commit.isEmpty()) {
        continue;
      }

      for (String parentId : commit.get().getParentIds()) {
        if (parentId.equals(ancestorId)) {
          return true;
        }
        if (visited.add(parentId)) {
          queue.add(parentId);
        }
      }
    }

    return false;
  }
}
```

---

### Step 3: Create Command & Event

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommand.java`
```java
package org.chucc.vcserver.command;

/**
 * Command to merge two branches.
 *
 * @param dataset dataset name
 * @param into target branch name
 * @param from source ref (branch or commit)
 * @param fastForward fast-forward mode ("allow", "only", "never")
 * @param author author of merge commit
 * @param message merge commit message
 */
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String author,
    String message
) implements Command {
}
```

**File:** `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java`
```java
package org.chucc.vcserver.event;

import org.apache.jena.rdfpatch.RDFPatch;
import java.time.Instant;
import java.util.List;

/**
 * Event published when a merge operation creates a new commit.
 *
 * <p>Note: Fast-forward merges use BranchUpdatedEvent instead.
 *
 * @param eventId unique event ID
 * @param dataset dataset name
 * @param branchName target branch that was updated
 * @param sourceRef source ref that was merged
 * @param commitId ID of created merge commit
 * @param parentIds parent commit IDs (exactly 2 for merge commits)
 * @param patch RDFPatch applied in merge
 * @param patchSize number of quads changed
 * @param author author of merge
 * @param message merge commit message
 * @param timestamp event timestamp
 */
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
    Instant timestamp
) implements Event {
}
```

**Note:** Fast-forward merges will reuse `BranchUpdatedEvent` (already exists).

---

### Step 4: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`
```java
package org.chucc.vcserver.command;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.dto.MergeConflict;
import org.chucc.vcserver.event.BranchMergedEvent;
import org.chucc.vcserver.event.BranchUpdatedEvent;
import org.chucc.vcserver.event.Event;
import org.chucc.vcserver.exception.MergeConflictException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.util.CommonAncestorFinder;
import org.chucc.vcserver.util.GraphDiffUtil;
import org.chucc.vcserver.util.MergeUtil;
import org.chucc.vcserver.util.UuidGenerator;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

/**
 * Handles merge commands (Phase 1: conflict detection only).
 */
@Component
public class MergeCommandHandler implements CommandHandler {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;
  private final CommonAncestorFinder ancestorFinder;
  private final UuidGenerator uuidGenerator;

  public MergeCommandHandler(BranchRepository branchRepository,
                             CommitRepository commitRepository,
                             DatasetService datasetService,
                             UuidGenerator uuidGenerator) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
    this.ancestorFinder = new CommonAncestorFinder(commitRepository);
    this.uuidGenerator = uuidGenerator;
  }

  @Override
  public Event handle(Command command) {
    MergeCommand cmd = (MergeCommand) command;

    // 1. Resolve refs to commit IDs
    String intoCommitId = resolveRef(cmd.dataset(), cmd.into());
    String fromCommitId = resolveRef(cmd.dataset(), cmd.from());

    // 2. Check for fast-forward possibility
    boolean canFastForward = ancestorFinder.isAncestor(cmd.dataset(), intoCommitId, fromCommitId);

    if (canFastForward) {
      if ("never".equals(cmd.fastForward())) {
        // Create merge commit even though fast-forward is possible
        return createMergeCommit(cmd, intoCommitId, fromCommitId);
      } else {
        // Fast-forward: just move branch pointer
        return new BranchUpdatedEvent(
            uuidGenerator.generateEventId(),
            cmd.dataset(),
            cmd.into(),
            fromCommitId,
            Instant.now()
        );
      }
    }

    // 3. Check fast-forward-only mode
    if ("only".equals(cmd.fastForward())) {
      throw new IllegalStateException(
          "Fast-forward merge not possible (branches have diverged), but fastForward=only");
    }

    // 4. Perform three-way merge
    return performThreeWayMerge(cmd, intoCommitId, fromCommitId);
  }

  private Event performThreeWayMerge(MergeCommand cmd, String intoCommitId, String fromCommitId) {
    // Find common ancestor
    String ancestorId = ancestorFinder.findCommonAncestor(cmd.dataset(), intoCommitId, fromCommitId);
    if (ancestorId == null) {
      throw new IllegalStateException("No common ancestor found between " + intoCommitId +
                                      " and " + fromCommitId);
    }

    // Load three datasets
    Dataset ancestorData = datasetService.loadCommitData(cmd.dataset(), ancestorId);
    Dataset intoData = datasetService.loadCommitData(cmd.dataset(), intoCommitId);
    Dataset fromData = datasetService.loadCommitData(cmd.dataset(), fromCommitId);

    // Compute diffs
    RDFPatch baseToInto = GraphDiffUtil.computeDiff(ancestorData, intoData);
    RDFPatch baseToFrom = GraphDiffUtil.computeDiff(ancestorData, fromData);

    // Detect conflicts
    List<MergeConflict> conflicts = MergeUtil.detectConflicts(baseToInto, baseToFrom);

    if (!conflicts.isEmpty()) {
      // Phase 1: Always throw exception on conflicts
      throw new MergeConflictException(conflicts);
    }

    // No conflicts: create merge commit
    return createMergeCommit(cmd, intoCommitId, fromCommitId);
  }

  private Event createMergeCommit(MergeCommand cmd, String intoCommitId, String fromCommitId) {
    // Load both commits' data
    Dataset intoData = datasetService.loadCommitData(cmd.dataset(), intoCommitId);
    Dataset fromData = datasetService.loadCommitData(cmd.dataset(), fromCommitId);

    // For now, use simple "take all from source" strategy
    // (This will be improved in Phase 2)
    RDFPatch mergePatch = GraphDiffUtil.computeDiff(intoData, fromData);
    int patchSize = countPatchOperations(mergePatch);

    String mergeCommitId = uuidGenerator.generateCommitId();

    return new BranchMergedEvent(
        uuidGenerator.generateEventId(),
        cmd.dataset(),
        cmd.into(),
        cmd.from(),
        mergeCommitId,
        List.of(intoCommitId, fromCommitId),
        mergePatch,
        patchSize,
        cmd.author(),
        cmd.message() != null ? cmd.message() : "Merge " + cmd.from() + " into " + cmd.into(),
        Instant.now()
    );
  }

  private String resolveRef(String dataset, String ref) {
    // Try branch first
    return branchRepository.findByName(dataset, ref)
        .map(branch -> branch.getHeadCommitId())
        .orElseGet(() -> {
          // Try commit ID directly
          if (commitRepository.findById(dataset, ref).isPresent()) {
            return ref;
          }
          throw new IllegalArgumentException("Ref not found: " + ref);
        });
  }

  private int countPatchOperations(RDFPatch patch) {
    final int[] count = {0};
    patch.apply(new org.apache.jena.rdfpatch.RDFChanges() {
      @Override public void add(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                                org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        count[0]++;
      }
      @Override public void delete(org.apache.jena.graph.Node g, org.apache.jena.graph.Node s,
                                   org.apache.jena.graph.Node p, org.apache.jena.graph.Node o) {
        count[0]++;
      }
      @Override public void start() {}
      @Override public void finish() {}
      @Override public void header(String field, org.apache.jena.graph.Node value) {}
      @Override public void addPrefix(org.apache.jena.graph.Node gn, String prefix, String uriStr) {}
      @Override public void deletePrefix(org.apache.jena.graph.Node gn, String prefix) {}
      @Override public void txnBegin() {}
      @Override public void txnCommit() {}
      @Override public void txnAbort() {}
      @Override public void segment() {}
    });
    return count[0];
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/exception/MergeConflictException.java`
```java
package org.chucc.vcserver.exception;

import org.chucc.vcserver.dto.MergeConflict;
import java.util.List;

/**
 * Exception thrown when merge conflicts are detected.
 */
public class MergeConflictException extends RuntimeException {

  private final List<MergeConflict> conflicts;

  public MergeConflictException(List<MergeConflict> conflicts) {
    super("Merge conflicts detected: " + conflicts.size() + " conflicting quad(s)");
    this.conflicts = List.copyOf(conflicts);
  }

  public List<MergeConflict> getConflicts() {
    return conflicts;
  }
}
```

---

### Step 5: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/MergeController.java`

Replace the 501 stub with full implementation:
```java
@PostMapping
public ResponseEntity<MergeResponse> merge(
    @RequestParam(defaultValue = "default") String dataset,
    @RequestBody MergeRequest request,
    @RequestHeader(value = "SPARQL-VC-Author", required = false) String authorHeader) {

  // Validate request
  request.validate();

  // Extract author (from body or header)
  String author = authorHeader != null ? authorHeader : "anonymous";

  // Create command
  MergeCommand command = new MergeCommand(
      dataset,
      request.into(),
      request.from(),
      request.normalizedFastForward(),
      author,
      null  // Phase 1: no custom message support
  );

  // Execute command
  try {
    Event event = mergeCommandHandler.handle(command);
    eventPublisher.publish(event).get();

    // Build response based on event type
    if (event instanceof BranchUpdatedEvent bue) {
      return ResponseEntity.ok(MergeResponse.fastForward(
          request.into(),
          request.from(),
          bue.newHeadCommitId()
      ));
    } else if (event instanceof BranchMergedEvent bme) {
      return ResponseEntity.ok(MergeResponse.merged(
          request.into(),
          request.from(),
          bme.commitId()
      ));
    } else {
      throw new IllegalStateException("Unexpected event type: " + event.getClass());
    }

  } catch (MergeConflictException e) {
    // Return 409 Conflict with conflict details
    throw e;  // Let GlobalExceptionHandler format the response
  } catch (InterruptedException | ExecutionException e) {
    throw new RuntimeException("Merge operation failed", e);
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java`

Add handler for `MergeConflictException`:
```java
@ExceptionHandler(MergeConflictException.class)
public ResponseEntity<ProblemDetail> handleMergeConflict(MergeConflictException ex) {
  ProblemDetail problem = ProblemDetail.forStatusAndDetail(
      HttpStatus.CONFLICT,
      "Merge conflicts detected between branches. Use 'ours' or 'theirs' strategy, " +
      "or resolve manually."
  );
  problem.setTitle("Merge Conflict");
  problem.setProperty("code", "merge_conflict");
  problem.setProperty("conflicts", ex.getConflicts());

  return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}
```

---

### Step 6: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add handler for `BranchMergedEvent`:
```java
private void handleBranchMerged(BranchMergedEvent event) {
  String dataset = event.dataset();

  // 1. Create merge commit
  Commit mergeCommit = new Commit(
      event.commitId(),
      dataset,
      event.message(),
      event.author(),
      event.timestamp(),
      event.parentIds(),
      event.patch(),
      event.patchSize()
  );
  commitRepository.save(mergeCommit);

  // 2. Update branch HEAD
  branchRepository.findByName(dataset, event.branchName()).ifPresent(branch -> {
    branch.setHeadCommitId(event.commitId());
    branch.setLastModified(event.timestamp());
    branchRepository.save(branch);
  });

  // 3. Update materialized view (if enabled)
  if (materializedViewEnabled) {
    Dataset currentData = materializedViewRepository.get(dataset, event.branchName());
    if (currentData != null) {
      event.patch().apply(currentData);
      materializedViewRepository.put(dataset, event.branchName(), currentData);
    }
  }
}
```

---

### Step 7: Write Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.dto.MergeRequest;
import org.chucc.vcserver.dto.MergeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for merge operations (Phase 1: conflict detection only).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class MergeOperationsIT extends IntegrationTestFixture {

  @Test
  void merge_fastForward_shouldMoveBranchPointer() {
    // Arrange: Create feature branch from main
    createBranch("feature", "main");

    // Make commit on feature
    String patch = "A <http://example.org/g> <http://example.org/s> <http://example.org/p> <http://example.org/o> .";
    applyPatch("feature", patch);

    // Act: Merge feature into main (fast-forward)
    MergeRequest request = new MergeRequest("main", "feature", "allow", null, null);
    ResponseEntity<MergeResponse> response = restTemplate.postForEntity(
        "/version/merge?dataset=test",
        request,
        MergeResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().result()).isEqualTo("fast-forward");
    assertThat(response.getBody().fastForward()).isTrue();
    assertThat(response.getBody().mergeCommit()).isNull();
  }

  @Test
  void merge_withConflicts_shouldReturn409() {
    // Arrange: Create diverged branches
    String commitId = getCurrentCommit("main");
    createBranch("branch-a", commitId);
    createBranch("branch-b", commitId);

    // Both branches modify same quad
    String patchA = "D <http://example.org/g> <http://example.org/s> <http://example.org/p> <http://example.org/o> .";
    applyPatch("branch-a", patchA);

    String patchB = "D <http://example.org/g> <http://example.org/s> <http://example.org/p> <http://example.org/o> .";
    applyPatch("branch-b", patchB);

    // Act: Merge branch-b into branch-a
    MergeRequest request = new MergeRequest("branch-a", "branch-b", "allow", null, null);
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/version/merge?dataset=test",
        request,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("merge_conflict");
    assertThat(response.getBody()).contains("conflicts");
  }

  @Test
  void merge_fastForwardOnly_whenNotPossible_shouldReturn422() {
    // Arrange: Create diverged branches
    String commitId = getCurrentCommit("main");
    createBranch("branch-a", commitId);
    createBranch("branch-b", commitId);

    applyPatch("branch-a", "A <http://example.org/g> <http://example.org/s1> <http://example.org/p> <http://example.org/o> .");
    applyPatch("branch-b", "A <http://example.org/g> <http://example.org/s2> <http://example.org/p> <http://example.org/o> .");

    // Act: Merge with fastForward=only
    MergeRequest request = new MergeRequest("branch-a", "branch-b", "only", null, null);
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/version/merge?dataset=test",
        request,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }
}
```

**File:** `src/test/java/org/chucc/vcserver/util/CommonAncestorFinderTest.java`

Unit tests for common ancestor algorithm.

**File:** `src/test/java/org/chucc/vcserver/util/GraphDiffUtilTest.java`

Unit tests for graph diffing.

**File:** `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java`

Unit tests for conflict detection.

---

## Success Criteria

- ✅ Fast-forward detection works correctly
- ✅ Fast-forward merges update branch pointer (use BranchUpdatedEvent)
- ✅ Three-way merge creates merge commit (use BranchMergedEvent)
- ✅ Conflict detection works (409 response with conflict list)
- ✅ `fastForward=only` fails when branches diverged (422 response)
- ✅ `fastForward=never` creates merge commit even when fast-forward possible
- ✅ Common ancestor finding works
- ✅ Graph diffing utilities work
- ✅ DTOs created with validation
- ✅ Integration tests pass (6+ test cases)
- ✅ Unit tests pass (utilities)
- ✅ Zero quality violations
- ✅ Full build passes

---

## Files to Create

**New Files:**
- `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`
- `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`
- `src/main/java/org/chucc/vcserver/dto/MergeConflict.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommand.java`
- `src/main/java/org/chucc/vcserver/event/BranchMergedEvent.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`
- `src/main/java/org/chucc/vcserver/exception/MergeConflictException.java`
- `src/main/java/org/chucc/vcserver/util/GraphDiffUtil.java`
- `src/main/java/org/chucc/vcserver/util/MergeUtil.java`
- `src/main/java/org/chucc/vcserver/util/CommonAncestorFinder.java`
- `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`
- `src/test/java/org/chucc/vcserver/util/CommonAncestorFinderTest.java`
- `src/test/java/org/chucc/vcserver/util/GraphDiffUtilTest.java`
- `src/test/java/org/chucc/vcserver/util/MergeUtilTest.java`

**Files to Modify:**
- `src/main/java/org/chucc/vcserver/controller/MergeController.java` (replace 501 stub)
- `src/main/java/org/chucc/vcserver/controller/GlobalExceptionHandler.java` (add MergeConflictException handler)
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java` (add BranchMergedEvent handler)

---

## Next Phases

- **Phase 2:** [Implement merge strategies](./02-implement-merge-strategies.md) ("ours", "theirs")
- **Phase 3:** [Implement manual resolution](./03-implement-manual-resolution.md) ("manual" strategy)

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.3](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [MergeController.java](../../src/main/java/org/chucc/vcserver/controller/MergeController.java)
- [Lowest Common Ancestor Algorithm](https://en.wikipedia.org/wiki/Lowest_common_ancestor)
