# Task 13: Implement No-Op Patch Detection

## Priority
🟡 **MEDIUM** - Protocol requirement

## Dependencies
- Task 05 (POST /version/commits)

## Protocol Reference
- Section 7: Changesets

## Context
Per §7: "A no-op patch (applies cleanly but yields no dataset change) MUST NOT create a new commit → 204 No Content."

This prevents polluting commit history with empty commits.

## Current State
All patches create commits, even if they result in no changes.

## Target State
- Detect when patch application results in no dataset change
- Return 204 No Content without creating commit
- Do NOT increment commit count, do NOT move branch pointer

## What is a No-Op Patch?

A patch that:
1. Applies without syntax errors
2. Does not result in any semantic change to the dataset

Examples:
- Adding a triple that already exists
- Deleting a triple that doesn't exist
- Multiple operations that cancel out

```
TX .
A <http://example.org/s> <http://example.org/p> "value" .
D <http://example.org/s> <http://example.org/p> "value" .
TC .
```

## Implementation Tasks

### 1. Enhance RdfPatchUtil
**File**: `src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java` (update)

Add method to detect no-op patches:

```java
/**
 * Check if applying a patch to a dataset results in no change.
 *
 * @param patch the RDF Patch to apply
 * @param baseDataset the dataset to apply to
 * @return true if patch is a no-op (no semantic change)
 */
public static boolean isNoOp(RDFPatch patch, Dataset baseDataset) {
    // Create temporary copy of dataset
    Dataset tempDataset = DatasetFactory.create();
    DatasetGraph baseGraph = baseDataset.asDatasetGraph();
    DatasetGraph tempGraph = tempDataset.asDatasetGraph();

    // Copy all quads to temp dataset
    baseGraph.find().forEachRemaining(tempGraph::add);

    // Apply patch to temp dataset
    try {
        RDFPatchOps.applyChange(tempDataset, patch);
    } catch (Exception e) {
        // Patch doesn't apply cleanly - not a no-op
        return false;
    }

    // Compare before and after
    return isIsomorphic(baseGraph, tempGraph);
}

/**
 * Check if two dataset graphs are isomorphic.
 */
private static boolean isIsomorphic(DatasetGraph g1, DatasetGraph g2) {
    // Count quads
    long count1 = g1.stream().count();
    long count2 = g2.stream().count();

    if (count1 != count2) {
        return false;
    }

    // Check all quads in g1 exist in g2
    return g1.stream().allMatch(g2::contains) &&
           g2.stream().allMatch(g1::contains);
}
```

### 2. Update CreateCommitCommandHandler
**File**: `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java` (update)

Add no-op detection before creating commit:

```java
public CreateCommitResult handle(CreateCommitCommand command) {
    // ... existing code to parse patch and get base dataset

    // Check if patch is a no-op
    if (RdfPatchUtil.isNoOp(command.patch(), baseDataset)) {
        return CreateCommitResult.noOp();
    }

    // ... continue with normal commit creation
}

// Add result class to distinguish no-op from normal commit
public sealed interface CreateCommitResult {
    record Success(String commitId) implements CreateCommitResult {}
    record NoOp() implements CreateCommitResult {}

    static CreateCommitResult success(String commitId) {
        return new Success(commitId);
    }

    static CreateCommitResult noOp() {
        return new NoOp();
    }
}
```

### 3. Update CommitController
**File**: `src/main/java/org/chucc/vcserver/controller/CommitController.java` (update)

Handle no-op result:

```java
@PostMapping(
    consumes = "text/rdf-patch",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<?> createCommit(...) {
    // Build command
    CreateCommitCommand command = new CreateCommitCommand(...);

    // Execute
    CreateCommitResult result = commandHandler.handle(command);

    // Handle result
    return switch (result) {
        case CreateCommitResult.Success(String commitId) -> {
            CommitResponse response = new CommitResponse(...);
            yield ResponseEntity.created(...)
                .eTag(...)
                .body(response);
        }
        case CreateCommitResult.NoOp() -> {
            // No content response
            yield ResponseEntity.noContent().build();
        }
    };
}
```

### 4. Update SPARQL UPDATE Handler
**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java` (update)

If SPARQL UPDATE results in no-op patch:

```java
public ResponseEntity<String> executeSparqlPost(...) {
    // ... execute update, convert to RDF Patch

    // Check if no-op
    if (RdfPatchUtil.isNoOp(generatedPatch, currentDataset)) {
        return ResponseEntity.noContent().build();
    }

    // ... continue with commit creation
}
```

## Response Examples

### No-Op Patch
```http
POST /version/commits?branch=main HTTP/1.1
Content-Type: text/rdf-patch

TX .
A <http://example.org/s> <http://example.org/p> "existing value" .
TC .

HTTP/1.1 204 No Content
```

### Normal Commit
```http
POST /version/commits?branch=main HTTP/1.1
Content-Type: text/rdf-patch

TX .
A <http://example.org/s> <http://example.org/p> "new value" .
TC .

HTTP/1.1 201 Created
Location: /version/commits/01936c81-1111-7890-abcd-ef1234567890
ETag: "01936c81-1111-7890-abcd-ef1234567890"
Content-Type: application/json

{
  "id": "01936c81-1111-7890-abcd-ef1234567890",
  ...
}
```

## Acceptance Criteria
- [ ] No-op patch detection implemented
- [ ] Adding existing triple → 204 No Content
- [ ] Deleting non-existent triple → 204 No Content
- [ ] Self-canceling operations → 204 No Content
- [ ] No commit created for no-op patches
- [ ] Branch pointer unchanged
- [ ] Normal patches still create commits
- [ ] POST /version/commits supports no-op detection
- [ ] SPARQL UPDATE supports no-op detection

## Test Requirements

### Unit Tests
1. `RdfPatchUtilTest.java` (update)
   - Test isNoOp with empty patch
   - Test isNoOp with add existing triple
   - Test isNoOp with delete non-existent triple
   - Test isNoOp with self-canceling operations
   - Test non-no-op patch returns false

2. `CreateCommitCommandHandlerTest.java` (update)
   - Test no-op detection in handler
   - Verify NoOp result returned
   - Verify no commit created

### Integration Tests
1. `NoOpPatchIntegrationTest.java`
   - Create commit with data
   - POST same data again (add existing)
   - Verify 204 No Content
   - Verify no new commit created
   - Verify branch HEAD unchanged
   - POST delete non-existent triple
   - Verify 204 No Content
   - POST self-canceling patch
   - Verify 204 No Content

2. `SparqlUpdateNoOpIntegrationTest.java`
   - Execute SPARQL UPDATE that inserts existing data
   - Verify 204 No Content
   - Verify no commit created

## Files to Modify
- `src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java`
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
- `src/test/java/org/chucc/vcserver/util/RdfPatchUtilTest.java`
- `src/test/java/org/chucc/vcserver/command/CreateCommitCommandHandlerTest.java`

## Files to Create
- `src/test/java/org/chucc/vcserver/integration/NoOpPatchIntegrationTest.java`
- `src/test/java/org/chucc/vcserver/integration/SparqlUpdateNoOpIntegrationTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium** - Requires dataset comparison logic

## Notes
- Performance consideration: isomorphism check can be expensive for large datasets
- Consider caching dataset signatures/hashes for faster comparison (future optimization)
- Blank node handling: isomorphism must account for blank node equivalence
- No-op detection happens AFTER patch is validated and BEFORE commit creation
- This is a MUST requirement per §7, not optional
