# Task 02: Update ReadModelProjector for Eager Materialization

**Status:** Not Started
**Priority:** High (Core Functionality)
**Category:** Architecture Enhancement
**Estimated Time:** 4-5 hours
**Complexity:** Medium-High
**Prerequisites:** Task 01 completed (MaterializedBranchRepository created)

---

## Overview

Modify `ReadModelProjector` to eagerly materialize branch HEADs by applying patches to `MaterializedBranchRepository` when processing commit events. This shifts graph materialization from query-time (DatasetService) to event-time (Projector).

**Current Behavior:**
- Projector stores commits and patches in `CommitRepository`
- Projector updates branch HEAD pointer
- DatasetService builds graphs on-demand when queried

**New Behavior:**
- Projector stores commits and patches (unchanged)
- Projector updates branch HEAD pointer (unchanged)
- **NEW:** Projector applies patches to materialized branch graphs
- DatasetService returns pre-materialized graphs (instant query response)

---

## Current State

### ReadModelProjector - handleCommitCreated

**File:** [ReadModelProjector.java:243-298](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L243-L298)

```java
void handleCommitCreated(CommitCreatedEvent event) {
    logger.debug("Processing CommitCreatedEvent: {}", event.eventId());

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create Commit domain object
    Commit commit = new Commit(
        CommitId.of(event.commitId()),
        event.parents().stream().map(CommitId::of).toList(),
        event.author(),
        event.message(),
        event.timestamp(),
        event.patchSize()
    );

    // Save commit and patch
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch HEAD if branch is specified
    if (event.branch() != null) {
        Optional<Branch> branchOpt = branchRepository.findByDatasetAndName(
            event.dataset(), event.branch());

        if (branchOpt.isPresent()) {
            branchRepository.updateBranchHead(
                event.dataset(),
                event.branch(),
                CommitId.of(event.commitId())
            );

            // Notify DatasetService of latest commit update for cache management
            datasetService.updateLatestCommit(
                event.dataset(),
                event.branch(),
                CommitId.of(event.commitId())
            );

            logger.debug("Updated branch {} to commit {}", event.branch(), event.commitId());
        } else {
            logger.debug("Skipping branch update for non-existent branch: {}", event.branch());
        }
    }
}
```

**What's Missing:** No graph materialization - patch is stored but not applied to any DatasetGraph.

### Other Event Handlers

Similar patterns in:
- `handleBranchCreated()` - Creates branch metadata, no graph materialization
- `handleBranchDeleted()` - Deletes branch metadata, no graph cleanup
- Other commit events (revert, cherry-pick, squash, rebase) - Same pattern

---

## Requirements

### Functional Requirements

1. **Eager Patch Application**
   - After storing commit in `CommitRepository`, apply patch to materialized graph
   - Only if event specifies a branch (commits without branches are dangling)
   - Use `MaterializedBranchRepository.applyPatchToBranch()`

2. **Branch Lifecycle Integration**
   - `handleBranchCreated()`: Initialize materialized graph (empty or clone parent)
   - `handleBranchDeleted()`: Remove materialized graph
   - `handleCommitCreated()`: Apply patch to branch graph

3. **Error Handling**
   - If patch application fails, log error but don't fail projection
   - Eventual consistency: graph can be rebuilt from commits later
   - Decision: Should projection retry or skip?

4. **CQRS Compliance**
   - Projector is still read-side-only (no commands)
   - Only updates read models (repositories + materialized graphs)
   - Events remain source of truth

### Non-Functional Requirements

1. **Performance**
   - Patch application should not significantly slow down projection
   - Expected: ~10-50ms per patch (Jena in-memory operations)

2. **Reliability**
   - Graceful degradation on patch application errors
   - Logging for debugging
   - Metrics for monitoring (next task)

3. **Maintainability**
   - Clear separation: CommitRepository = event log, MaterializedBranchRepository = materialized views
   - DRY: Extract common patch application logic

---

## Implementation Steps

### Step 1: Add MaterializedBranchRepository Dependency

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Add field:**
```java
private final MaterializedBranchRepository materializedBranchRepo;
```

**Update constructor:**
```java
public ReadModelProjector(
    KafkaEventPublisher eventPublisher,
    CommitRepository commitRepository,
    BranchRepository branchRepository,
    TagRepository tagRepository,
    SnapshotRepository snapshotRepository,
    DatasetService datasetService,
    MaterializedBranchRepository materializedBranchRepo  // ← NEW
) {
    this.eventPublisher = eventPublisher;
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
    this.tagRepository = tagRepository;
    this.snapshotRepository = snapshotRepository;
    this.datasetService = datasetService;
    this.materializedBranchRepo = materializedBranchRepo;  // ← NEW
}
```

### Step 2: Update handleCommitCreated

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:243-298`

**Add patch application after branch update:**

```java
void handleCommitCreated(CommitCreatedEvent event) {
    logger.debug("Processing CommitCreatedEvent: {}", event.eventId());

    // Parse RDF Patch from string
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create Commit domain object
    Commit commit = new Commit(
        CommitId.of(event.commitId()),
        event.parents().stream().map(CommitId::of).toList(),
        event.author(),
        event.message(),
        event.timestamp(),
        event.patchSize()
    );

    // Save commit and patch to event log
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch HEAD if branch is specified
    if (event.branch() != null) {
        Optional<Branch> branchOpt = branchRepository.findByDatasetAndName(
            event.dataset(), event.branch());

        if (branchOpt.isPresent()) {
            // Update branch pointer
            branchRepository.updateBranchHead(
                event.dataset(),
                event.branch(),
                CommitId.of(event.commitId())
            );

            // ✅ NEW: Apply patch to materialized graph
            try {
                materializedBranchRepo.applyPatchToBranch(
                    event.dataset(),
                    event.branch(),
                    patch
                );
                logger.debug("Applied patch to materialized graph {}/{}",
                    event.dataset(), event.branch());
            } catch (Exception e) {
                // Log error but don't fail projection
                // Graph can be rebuilt later from commit history
                logger.error("Failed to apply patch to materialized graph {}/{}: {}",
                    event.dataset(), event.branch(), e.getMessage(), e);
            }

            // Notify DatasetService of latest commit update
            datasetService.updateLatestCommit(
                event.dataset(),
                event.branch(),
                CommitId.of(event.commitId())
            );

            logger.debug("Updated branch {} to commit {}", event.branch(), event.commitId());
        } else {
            logger.debug("Skipping branch update for non-existent branch: {}", event.branch());
        }
    }
}
```

### Step 3: Update handleBranchCreated

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:300-350`

**Add materialized graph initialization:**

```java
void handleBranchCreated(BranchCreatedEvent event) {
    logger.debug("Processing BranchCreatedEvent: {}", event.eventId());

    // Create Branch domain object
    Optional<CommitId> fromCommitOpt = Optional.ofNullable(event.fromCommit())
        .map(CommitId::of);

    Branch branch = new Branch(
        event.branchName(),
        fromCommitOpt.orElse(null),
        event.timestamp(),
        event.timestamp()
    );

    // Save branch metadata
    branchRepository.save(event.dataset(), branch);

    // ✅ NEW: Initialize materialized graph
    try {
        Optional<String> parentBranch = Optional.ofNullable(event.fromBranch());
        materializedBranchRepo.createBranch(
            event.dataset(),
            event.branchName(),
            parentBranch
        );
        logger.info("Initialized materialized graph for branch {}/{}",
            event.dataset(), event.branchName());
    } catch (Exception e) {
        // Log error but don't fail projection
        logger.error("Failed to initialize materialized graph for branch {}/{}: {}",
            event.dataset(), event.branchName(), e.getMessage(), e);
    }

    logger.info("Branch created: {} in dataset {}", event.branchName(), event.dataset());
}
```

### Step 4: Update handleBranchDeleted

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:352-380`

**Add materialized graph cleanup:**

```java
void handleBranchDeleted(BranchDeletedEvent event) {
    logger.debug("Processing BranchDeletedEvent: {}", event.eventId());

    // Delete branch metadata
    branchRepository.delete(event.dataset(), event.branchName());

    // ✅ NEW: Delete materialized graph
    try {
        materializedBranchRepo.deleteBranch(event.dataset(), event.branchName());
        logger.info("Deleted materialized graph for branch {}/{}",
            event.dataset(), event.branchName());
    } catch (Exception e) {
        // Log error but don't fail projection
        logger.error("Failed to delete materialized graph for branch {}/{}: {}",
            event.dataset(), event.branchName(), e.getMessage(), e);
    }

    // Notify DatasetService to evict cached graphs for this branch
    datasetService.evictBranchCache(event.dataset(), event.branchName());

    logger.info("Branch deleted: {} from dataset {}", event.branchName(), event.dataset());
}
```

### Step 5: Update Other Commit Event Handlers (Optional Optimization)

For events that also create commits:
- `handleRevertCreated()` (line 470)
- `handleCherryPicked()` (line 518)
- `handleCommitsSquashed()` (line 650) - if refactored per task `.tasks/architecture/02-refactor-squash-rebase-to-pure-cqrs.md`
- `handleBranchRebased()` (line 407) - if refactored

**Pattern for each:**
```java
// After saving commit and updating branch...

// Apply patch to materialized graph
if (event.branch() != null) {
    try {
        materializedBranchRepo.applyPatchToBranch(
            event.dataset(),
            event.branch(),
            patch
        );
        logger.debug("Applied {} patch to materialized graph {}/{}",
            eventType, event.dataset(), event.branch());
    } catch (Exception e) {
        logger.error("Failed to apply {} patch to materialized graph {}/{}: {}",
            eventType, event.dataset(), event.branch(), e.getMessage(), e);
    }
}
```

**For this task:** Update at minimum:
- `handleRevertCreated()`
- `handleCherryPicked()`

**Defer to later:** Squash/Rebase (pending CQRS refactoring task)

### Step 6: Update Constructor Tests

**File:** `src/test/java/org/chucc/vcserver/projection/ReadModelProjectorTest.java`

Add `MaterializedBranchRepository` mock to constructor:

```java
@Mock
private MaterializedBranchRepository materializedBranchRepo;

@BeforeEach
void setUp() {
    projector = new ReadModelProjector(
        eventPublisher,
        commitRepository,
        branchRepository,
        tagRepository,
        snapshotRepository,
        datasetService,
        materializedBranchRepo  // ← NEW
    );
}
```

### Step 7: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/MaterializedGraphProjectionIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for materialized graph projection.
 *
 * <p>These tests verify that CommitCreatedEvents are correctly projected
 * to materialized branch graphs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable projector!
class MaterializedGraphProjectionIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private MaterializedBranchRepository materializedBranchRepo;

  @Test
  void commitCreated_shouldProjectToMaterializedGraph() throws Exception {
    // Arrange
    String dataset = "test-dataset";
    String branch = "main";
    String graphUri = "http://example.org/graph";
    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    createDataset(dataset);
    createBranch(dataset, branch);

    // Act: Create commit via GSP PUT
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/turtle"));
    headers.set("X-Author", "test-author");
    headers.set("X-Commit-Message", "Add test data");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    ResponseEntity<Void> response = restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request,
        Void.class,
        dataset, graphUri, branch
    );

    // Assert: HTTP response
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    // Assert: Wait for projection to materialized graph
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph graph = materializedBranchRepo.getBranchGraph(dataset, branch);

          graph.begin(ReadWrite.READ);
          try {
            Node g = NodeFactory.createURI(graphUri);
            Node s = NodeFactory.createURI("http://example.org/subject");
            Node p = NodeFactory.createURI("http://example.org/predicate");
            Node o = NodeFactory.createLiteral("value");

            Quad expectedQuad = Quad.create(g, s, p, o);
            assertThat(graph.contains(expectedQuad))
                .as("Materialized graph should contain projected quad")
                .isTrue();
          } finally {
            graph.end();
          }
        });
  }

  @Test
  void branchCreated_shouldInitializeMaterializedGraph() throws Exception {
    // Arrange
    String dataset = "test-dataset";
    String branch = "feature";
    createDataset(dataset);

    // Act: Create branch
    createBranch(dataset, branch);

    // Assert: Wait for materialized graph initialization
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(materializedBranchRepo.exists(dataset, branch))
              .as("Materialized graph should exist for new branch")
              .isTrue();
        });
  }

  @Test
  void branchCreatedFromParent_shouldCloneParentGraph() throws Exception {
    // Arrange: Create main branch with data
    String dataset = "test-dataset";
    String mainBranch = "main";
    String featureBranch = "feature";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, mainBranch);

    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;
    putGraph(dataset, graphUri, mainBranch, turtle, "test-author", "Add data");

    // Wait for main branch materialization
    await().atMost(Duration.ofSeconds(10))
        .until(() -> {
          DatasetGraph graph = materializedBranchRepo.getBranchGraph(dataset, mainBranch);
          graph.begin(ReadWrite.READ);
          try {
            return !graph.isEmpty();
          } finally {
            graph.end();
          }
        });

    // Act: Create feature branch from main
    createBranchFrom(dataset, featureBranch, mainBranch);

    // Assert: Feature branch has cloned data
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph featureGraph = materializedBranchRepo.getBranchGraph(dataset, featureBranch);

          featureGraph.begin(ReadWrite.READ);
          try {
            Node g = NodeFactory.createURI(graphUri);
            Node s = NodeFactory.createURI("http://example.org/subject");
            Node p = NodeFactory.createURI("http://example.org/predicate");
            Node o = NodeFactory.createLiteral("value");

            Quad expectedQuad = Quad.create(g, s, p, o);
            assertThat(featureGraph.contains(expectedQuad))
                .as("Feature branch should contain cloned data from main")
                .isTrue();
          } finally {
            featureGraph.end();
          }
        });
  }

  @Test
  void branchDeleted_shouldRemoveMaterializedGraph() throws Exception {
    // Arrange
    String dataset = "test-dataset";
    String branch = "feature";
    createDataset(dataset);
    createBranch(dataset, branch);

    await().atMost(Duration.ofSeconds(10))
        .until(() -> materializedBranchRepo.exists(dataset, branch));

    // Act: Delete branch
    deleteBranch(dataset, branch);

    // Assert: Materialized graph removed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(materializedBranchRepo.exists(dataset, branch))
              .as("Materialized graph should be deleted with branch")
              .isFalse();
        });
  }

  // Helper methods
  private void createDataset(String dataset) {
    restTemplate.postForEntity(
        "/version/datasets/{dataset}",
        null,
        Void.class,
        dataset
    );
  }

  private void createBranch(String dataset, String branch) {
    restTemplate.postForEntity(
        "/version/branches?dataset={dataset}&name={branch}",
        null,
        Void.class,
        dataset, branch
    );
  }

  private void createBranchFrom(String dataset, String branch, String fromBranch) {
    restTemplate.postForEntity(
        "/version/branches?dataset={dataset}&name={branch}&from={from}",
        null,
        Void.class,
        dataset, branch, fromBranch
    );
  }

  private void deleteBranch(String dataset, String branch) {
    restTemplate.delete(
        "/version/branches/{branch}?dataset={dataset}",
        branch, dataset
    );
  }

  private void putGraph(String dataset, String graphUri, String branch,
                        String turtle, String author, String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/turtle"));
    headers.set("X-Author", author);
    headers.set("X-Commit-Message", message);

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request,
        Void.class,
        dataset, graphUri, branch
    );
  }
}
```

### Step 8: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=MaterializedGraphProjectionIT 2>&1 | tail -20

# Phase 3: Run all projector tests
mvn -q test -Dtest=*ProjectorIT 2>&1 | tail -20

# Phase 4: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ `MaterializedBranchRepository` dependency added to `ReadModelProjector`
- ✅ `handleCommitCreated()` applies patches to materialized graphs
- ✅ `handleBranchCreated()` initializes materialized graphs
- ✅ `handleBranchDeleted()` removes materialized graphs
- ✅ `handleRevertCreated()` and `handleCherryPicked()` apply patches
- ✅ Error handling: graceful degradation on patch failures
- ✅ Integration tests pass with projector enabled
- ✅ All existing tests still pass (no regressions)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Integration Tests (Projector Enabled)

**CRITICAL:** Must enable projector in tests:
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

**Pattern:**
```java
@Test
void event_shouldUpdateMaterializedGraph() throws Exception {
    // Arrange
    createDataset(...);
    createBranch(...);

    // Act
    performOperation(...);  // Triggers event

    // Assert: Wait for async projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph graph = materializedBranchRepo.getBranchGraph(...);
          // Verify graph state
        });
}
```

### Unit Tests (Mocked)

Update existing `ReadModelProjectorTest`:
- Mock `MaterializedBranchRepository`
- Verify `applyPatchToBranch()` called with correct arguments
- Verify error handling doesn't fail projection

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

### Test Code
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjectorTest.java` (add mock)
- `src/test/java/org/chucc/vcserver/integration/MaterializedGraphProjectionIT.java` (NEW)

**Total:** 1 modified, 1 new file

---

## Design Decisions

### Decision 1: Error Handling Strategy

**Question:** If patch application to materialized graph fails, should we:
1. Fail the entire projection (strict consistency)?
2. Log error and continue (graceful degradation)?

**Chosen:** Option 2 (graceful degradation)

**Rationale:**
- CommitRepository is source of truth (event log preserved)
- Materialized graph can be rebuilt from commits later
- Failing projection could create inconsistencies in other repositories
- Better to have stale materialized view than broken projection

**Recovery:** Add manual rebuild endpoint in Task 04.

### Decision 2: Patch Re-parsing

**Question:** Patch is already parsed in `handleCommitCreated()`. Should we:
1. Parse once, pass to both repositories?
2. Parse twice (once for CommitRepository, once for MaterializedBranchRepository)?

**Chosen:** Option 2 (parse twice)

**Rationale:**
- Simpler code (each repository gets what it needs)
- Parsing is cheap (~1-2ms)
- Avoids coupling between repositories

### Decision 3: Branch Cloning Source

**Question:** When creating branch from parent, should we:
1. Clone from parent's materialized graph (fast)?
2. Replay commits from parent's history (consistent with commit log)?

**Chosen:** Option 1 (clone from materialized graph)

**Rationale:**
- Much faster (O(1) vs O(n) where n = number of commits)
- Materialized graph is always up-to-date (maintained by projector)
- Consistent with eager materialization philosophy
- If materialized graph is corrupted, rebuild mechanism in Task 04 handles it

---

## CQRS Compliance Check

| Aspect | Compliance |
|--------|------------|
| **Projector is read-side-only** | ✅ Yes - only updates read models |
| **No command execution** | ✅ Yes - only processes events |
| **Idempotent event handling** | ✅ Yes - applying same patch twice is idempotent |
| **Eventual consistency** | ✅ Yes - graphs updated asynchronously |
| **Events are source of truth** | ✅ Yes - CommitRepository preserves event log |

---

## Performance Considerations

### Expected Performance Impact

**Per Commit Event:**
- Parse patch: ~1-2ms
- Save to CommitRepository: ~5-10ms
- Apply to materialized graph: ~10-50ms (depends on patch size)
- **Total overhead:** ~15-60ms per commit

**Acceptable:** Most commits are small (1-100 operations).

### Large Commits

For commits with >10,000 operations:
- Consider async patch application (background thread)
- Add timeout/abort mechanism
- Log slow patch applications

**For this task:** Accept synchronous application, optimize in Task 04 if needed.

---

## References

- [ReadModelProjector.java:243-298](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L243-L298) - handleCommitCreated
- [ReadModelProjector.java:300-350](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L300-L350) - handleBranchCreated
- [ReadModelProjector.java:352-380](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L352-L380) - handleBranchDeleted
- [MaterializedBranchRepository.java](../../src/main/java/org/chucc/vcserver/repository/MaterializedBranchRepository.java) - Task 01
- [CQRS Guide - Projectors](../../docs/architecture/cqrs-event-sourcing.md#read-model-query-side)

---

## Next Steps

After completing this task:
1. ✅ Mark this task file as complete
2. ✅ Move to Task 03: Update DatasetService to use materialized views
3. ✅ Update `.tasks/README.md` to track progress

---

## Notes

### Why Projector and Not DatasetService?

**Question:** Why update projector (read side) instead of DatasetService (query layer)?

**Answer:** Projector is the correct layer for materialized view maintenance:
- Projector = "Write to read models when events arrive"
- DatasetService = "Read from read models when queries arrive"

Updating DatasetService to maintain graphs would violate separation of concerns.

### Impact on Existing Tests

Most existing integration tests should **not be affected** because:
- They don't enable the projector (`@TestPropertySource` not set)
- They test HTTP API only (command side)
- MaterializedBranchRepository operations only happen when projector is enabled

**Exception:** Projector-specific tests must be updated to mock new dependency.
