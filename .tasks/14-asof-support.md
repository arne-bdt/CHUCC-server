# Task 14: Implement asOf Selector Support

## Priority
🟢 **LOW** - Advanced feature

## Dependencies
- Task 02 (selector validation)
- Task 05 (POST /version/commits)

## Protocol Reference
- Section 2: Core Concepts
- Section 3.2: Refs and Commits
- Section 4: Selectors

## Context
The `asOf` selector enables time-travel queries and operations. When used with `branch`, it selects the branch state at or before the specified timestamp (inclusive).

## Current State
- `asOf` parameter accepted but not implemented
- Selector validation allows `asOf` + `branch` combination (Task 02)

## Target State
- `asOf` with `branch` resolves to historical commit
- `asOf` standalone selects any commit at/before timestamp
- Timestamp format: RFC 3339 (ISO 8601)
- Semantics: inclusive (exactly at or most recent before)

## asOf Semantics

Given: `?branch=main&asOf=2025-01-15T10:30:00Z`

Algorithm:
1. Get branch's commit history
2. Filter commits with timestamp ≤ asOf timestamp
3. Select most recent commit (latest before or at asOf)
4. Use that commit for query/update base

## Implementation Tasks

### 1. Create Timestamp Resolution Service
**File**: `src/main/java/org/chucc/vcserver/service/TimestampResolutionService.java`

```java
@Service
public class TimestampResolutionService {

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private BranchRepository branchRepository;

    /**
     * Resolve asOf timestamp to a commit ID.
     *
     * @param branchName optional branch name (null for any commit)
     * @param asOf RFC 3339 timestamp string
     * @return commit ID at or before timestamp
     * @throws CommitNotFoundException if no commit exists at/before timestamp
     */
    public CommitId resolveAsOf(String branchName, String asOf) {
        Instant timestamp = parseRfc3339(asOf);

        if (branchName != null) {
            // asOf + branch: walk branch history
            return resolveAsOfOnBranch(branchName, timestamp);
        } else {
            // asOf only: find any commit at/before timestamp
            return resolveAsOfGlobal(timestamp);
        }
    }

    private CommitId resolveAsOfOnBranch(String branchName, Instant timestamp) {
        Branch branch = branchRepository.findByName(branchName)
            .orElseThrow(() -> new BranchNotFoundException(branchName));

        // Walk commit history from branch HEAD backwards
        CommitId currentId = branch.getHead();
        Commit bestMatch = null;

        while (currentId != null) {
            Commit commit = commitRepository.findById(currentId)
                .orElseThrow(() -> new CommitNotFoundException(currentId.toString()));

            // Check if commit is at or before timestamp
            if (!commit.timestamp().isAfter(timestamp)) {
                // Found a match - this is the most recent one
                bestMatch = commit;
                break;
            }

            // Move to parent
            if (commit.parents().length > 0) {
                currentId = commit.parents()[0];
            } else {
                currentId = null;
            }
        }

        if (bestMatch == null) {
            throw new CommitNotFoundException(
                "No commit found on branch " + branchName + " at or before " + timestamp
            );
        }

        return bestMatch.id();
    }

    private CommitId resolveAsOfGlobal(Instant timestamp) {
        // Find most recent commit across all commits
        Optional<Commit> commit = commitRepository.findLatestBeforeTimestamp(timestamp);
        return commit
            .map(Commit::id)
            .orElseThrow(() -> new CommitNotFoundException(
                "No commit found at or before " + timestamp
            ));
    }

    private Instant parseRfc3339(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid RFC 3339 timestamp: " + timestamp, e);
        }
    }
}
```

### 2. Add Repository Method
**File**: `src/main/java/org/chucc/vcserver/repository/CommitRepository.java` (update)

```java
public interface CommitRepository {
    // ... existing methods

    /**
     * Find the most recent commit at or before the given timestamp.
     */
    Optional<Commit> findLatestBeforeTimestamp(Instant timestamp);
}
```

Implementation will depend on storage mechanism (in-memory, database, etc.).

### 3. Update Selector Resolution in Controllers
**File**: `src/main/java/org/chucc/vcserver/service/SelectorResolutionService.java` (new)

```java
@Service
public class SelectorResolutionService {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private TimestampResolutionService timestampResolutionService;

    /**
     * Resolve selector parameters to a commit ID.
     */
    public CommitId resolve(String branch, String commit, String asOf) {
        // Validation already done by Task 02

        if (commit != null) {
            // Direct commit reference
            return new CommitId(commit);
        }

        if (asOf != null) {
            // asOf with or without branch
            return timestampResolutionService.resolveAsOf(branch, asOf);
        }

        if (branch != null) {
            // Branch HEAD
            Branch b = branchRepository.findByName(branch)
                .orElseThrow(() -> new BranchNotFoundException(branch));
            return b.getHead();
        }

        // No selector: use default branch HEAD
        Branch defaultBranch = branchRepository.findByName("main")
            .orElseThrow(() -> new IllegalStateException("No default branch"));
        return defaultBranch.getHead();
    }
}
```

### 4. Update SparqlController
**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java` (update)

```java
@Autowired
private SelectorResolutionService selectorResolutionService;

public ResponseEntity<String> querySparqlGet(
    @RequestParam String query,
    @RequestParam(required = false) String branch,
    @RequestParam(required = false) String commit,
    @RequestParam(required = false) String asOf,
    ...
) {
    // Validate selectors (Task 02)
    SelectorValidator.validateMutualExclusion(branch, commit, asOf);

    // Resolve to commit ID
    CommitId targetCommit = selectorResolutionService.resolve(branch, commit, asOf);

    // Materialize dataset at that commit
    Dataset dataset = datasetService.materializeCommit(targetCommit);

    // Execute query against that dataset
    String results = executeSparqlQuery(query, dataset);

    return ResponseEntity.ok()
        .eTag(ETagUtil.createStrongETag(targetCommit.toString()))
        .body(results);
}
```

### 5. Update CommitController for asOf
**File**: `src/main/java/org/chucc/vcserver/controller/CommitController.java` (update)

```java
public ResponseEntity<?> createCommit(
    @RequestBody String patchBody,
    @RequestParam(required = false) String branch,
    @RequestParam(required = false) String commit,
    @RequestParam(required = false) String asOf,
    ...
) {
    // Validate: asOf only allowed with branch per §3.2
    if (asOf != null && branch == null) {
        throw new IllegalArgumentException("asOf selector requires branch parameter");
    }

    // Resolve base commit
    CommitId baseCommit = selectorResolutionService.resolve(branch, commit, asOf);

    // ... apply patch to base state
}
```

## Request Examples

### Query at specific time
```http
GET /sparql?query=SELECT * WHERE { ?s ?p ?o }&branch=main&asOf=2025-01-01T00:00:00Z HTTP/1.1

HTTP/1.1 200 OK
ETag: "01936c7f-8a2e-7890-abcd-ef1234567890"
Content-Type: application/sparql-results+json

{
  "results": {
    "bindings": [...]
  }
}
```

### Create commit from historical state
```http
POST /version/commits?branch=main&asOf=2024-12-01T00:00:00Z HTTP/1.1
Content-Type: text/rdf-patch
SPARQL-VC-Author: Alice
SPARQL-VC-Message: Apply patch to historical state

TX .
A <http://example.org/s> <http://example.org/p> "value" .
TC .

HTTP/1.1 201 Created
...
```

This creates a new commit based on the December 1st state of main, then applies the patch.

## Acceptance Criteria
- [ ] asOf parameter parsed as RFC 3339 timestamp
- [ ] asOf + branch resolves to historical commit
- [ ] asOf standalone resolves to global historical commit
- [ ] Invalid timestamp format returns 400 Bad Request
- [ ] No commit at/before timestamp returns 404 Not Found
- [ ] SPARQL queries work with asOf selector
- [ ] Commit creation works with asOf + branch
- [ ] asOf without branch on POST commits returns 400 (per spec)

## Test Requirements

### Unit Tests
1. `TimestampResolutionServiceTest.java`
   - Test parseRfc3339 with valid/invalid timestamps
   - Test resolveAsOfOnBranch with commit before timestamp
   - Test resolveAsOfOnBranch with no commits before timestamp
   - Test resolveAsOfGlobal

2. `SelectorResolutionServiceTest.java`
   - Test all selector combinations
   - Test asOf resolution

### Integration Tests
1. `AsOfSelectorIntegrationTest.java`
   - Create commits at different times on branch
   - Query with asOf at various points
   - Verify correct historical state returned
   - Create commit with asOf + branch
   - Verify patch applied to historical base

2. `TimeTravel QueryIntegrationTest.java`
   - Insert data at time T1
   - Update data at time T2
   - Delete data at time T3
   - Query with asOf=T1 → see original
   - Query with asOf=T2 → see updated
   - Query with asOf=T3 → see deleted

## Files to Create
- `src/main/java/org/chucc/vcserver/service/TimestampResolutionService.java`
- `src/main/java/org/chucc/vcserver/service/SelectorResolutionService.java`
- `src/test/java/org/chucc/vcserver/service/TimestampResolutionServiceTest.java`
- `src/test/java/org/chucc/vcserver/service/SelectorResolutionServiceTest.java`
- `src/test/java/org/chucc/vcserver/integration/AsOfSelectorIntegrationTest.java`
- `src/test/java/org/chucc/vcserver/integration/TimeTravelQueryIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/repository/CommitRepository.java`
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Medium-High** - Requires commit history traversal and timestamp comparison

## Notes
- Inclusive semantics: asOf=T returns commit AT time T if exists, otherwise most recent before T
- Performance: consider caching timestamp→commit mapping for frequently accessed timepoints
- Timezone handling: RFC 3339 includes timezone, Instant handles UTC properly
- Use case: "Show me the data as it was at end of Q4 2024"
- Combine with SPARQL queries for powerful temporal analytics
