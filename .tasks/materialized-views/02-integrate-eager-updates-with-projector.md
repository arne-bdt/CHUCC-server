# Task 02: Integrate Eager Cache Updates with ReadModelProjector

**Status:** Not Started
**Priority:** High (Core Functionality)
**Category:** Architecture Enhancement
**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Prerequisites:** Task 01 completed (`applyPatchToBranchCache()` method exists)

---

## Overview

Update `ReadModelProjector` to eagerly update the DatasetService cache when processing commit events. This shifts graph materialization from query-time to event-time, providing instant query responses.

**Current Behavior:**
- Projector stores commits in `CommitRepository`
- Projector updates branch HEAD pointer
- DatasetService builds graphs on-demand when queried

**New Behavior:**
- Projector stores commits (unchanged)
- Projector updates branch HEAD pointer (unchanged)
- **NEW:** Projector applies patches to DatasetService cache
- Queries return instantly (graph already up-to-date)

---

## Current State

### ReadModelProjector - handleCommitCreated

**File:** [ReadModelProjector.java:243-298](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L243-L298)

```java
void handleCommitCreated(CommitCreatedEvent event) {
    logger.debug("Processing CommitCreatedEvent: {}", event.eventId());

    // Parse RDF Patch
    RDFPatch patch = RDFPatchOps.read(
        new ByteArrayInputStream(event.rdfPatch().getBytes(StandardCharsets.UTF_8)));

    // Create and save commit
    Commit commit = new Commit(...);
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch HEAD if specified
    if (event.branch() != null) {
        branchRepository.updateBranchHead(...);
        datasetService.updateLatestCommit(...);  // ← Only updates tracking
        logger.debug("Updated branch {} to commit {}", event.branch(), event.commitId());
    }
}
```

**What's Missing:** No graph update - cache not updated until first query.

---

## Requirements

### Functional Requirements

1. **Eager Cache Updates**
   - After storing commit, call `datasetService.applyPatchToBranchCache()`
   - Only if event specifies a branch (dangling commits have no branch)
   - Apply patch to cached graph for instant queries

2. **Branch Lifecycle**
   - `handleBranchDeleted()`: Evict cached graphs (already exists)
   - No changes needed for `handleBranchCreated()` (lazy init works fine)

3. **Other Commit Events**
   - `handleRevertCreated()`: Apply patch eagerly
   - `handleCherryPicked()`: Apply patch eagerly
   - Squash/Rebase: Defer to future CQRS refactoring task

4. **Error Handling**
   - If eager update fails, log error but don't fail projection
   - Graceful degradation: lazy rebuild on next query
   - Events remain source of truth

### Non-Functional Requirements

1. **Performance**
   - Eager update adds ~10-50ms to projection time (acceptable)
   - Queries become 10-20x faster (100ms → <10ms)
   - Net win for read-heavy workloads

2. **Reliability**
   - Transactional patch application (already handled by Task 01)
   - No impact on event log (CommitRepository unchanged)

---

## Implementation Steps

### Step 1: Update handleCommitCreated

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:243-298`

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

            // ✅ NEW: Eagerly update cached graph
            try {
                datasetService.applyPatchToBranchCache(
                    event.dataset(),
                    event.branch(),
                    CommitId.of(event.commitId()),
                    patch
                );
                logger.debug("Eagerly updated cached graph for {}/{}",
                    event.dataset(), event.branch());
            } catch (Exception e) {
                // Log error but don't fail projection
                // Graph will be rebuilt on next query (graceful degradation)
                logger.error("Failed to eagerly update cache for {}/{}: {}. " +
                    "Graph will be rebuilt on next query.",
                    event.dataset(), event.branch(), e.getMessage(), e);
            }

            logger.debug("Updated branch {} to commit {}", event.branch(), event.commitId());
        } else {
            logger.debug("Skipping branch update for non-existent branch: {}", event.branch());
        }
    }
}
```

**Key Changes:**
- Added try-catch around `applyPatchToBranchCache()`
- Graceful degradation on failure
- Removed now-redundant `updateLatestCommit()` call (handled inside `applyPatchToBranchCache()`)

### Step 2: Update handleRevertCreated

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:470+`

```java
void handleRevertCreated(RevertCreatedEvent event) {
    logger.debug("Processing RevertCreatedEvent: {}", event.eventId());

    // Parse RDF Patch
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create and save commit
    Commit commit = new Commit(...);
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch HEAD
    if (event.branch() != null) {
        branchRepository.updateBranchHead(...);

        // ✅ NEW: Eagerly update cached graph
        try {
            datasetService.applyPatchToBranchCache(
                event.dataset(),
                event.branch(),
                CommitId.of(event.commitId()),
                patch
            );
            logger.debug("Eagerly updated cached graph for revert {}/{}",
                event.dataset(), event.branch());
        } catch (Exception e) {
            logger.error("Failed to eagerly update cache for revert {}/{}: {}",
                event.dataset(), event.branch(), e.getMessage(), e);
        }
    }
}
```

### Step 3: Update handleCherryPicked

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:518+`

```java
void handleCherryPicked(CherryPickedEvent event) {
    logger.debug("Processing CherryPickedEvent: {}", event.eventId());

    // Parse RDF Patch
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        event.rdfPatch().getBytes(StandardCharsets.UTF_8));
    RDFPatch patch = RDFPatchOps.read(inputStream);

    // Create and save commit
    Commit commit = new Commit(...);
    commitRepository.save(event.dataset(), commit, patch);

    // Update branch HEAD
    if (event.targetBranch() != null) {
        branchRepository.updateBranchHead(...);

        // ✅ NEW: Eagerly update cached graph
        try {
            datasetService.applyPatchToBranchCache(
                event.dataset(),
                event.targetBranch(),
                CommitId.of(event.newCommitId()),
                patch
            );
            logger.debug("Eagerly updated cached graph for cherry-pick {}/{}",
                event.dataset(), event.targetBranch());
        } catch (Exception e) {
            logger.error("Failed to eagerly update cache for cherry-pick {}/{}: {}",
                event.dataset(), event.targetBranch(), e.getMessage(), e);
        }
    }
}
```

### Step 4: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/EagerCacheUpdateIT.java` (NEW)

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for eager cache updates.
 *
 * <p>Verifies that CommitCreatedEvents trigger eager cache updates,
 * providing instant query performance for branch HEAD queries.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable projector!
class EagerCacheUpdateIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void commitCreated_shouldEagerlyUpdateCache() throws Exception {
    // Arrange
    String dataset = "test-dataset";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "eager value" .
        """;

    // Act: Create commit via GSP PUT
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/turtle"));
    headers.set("X-Author", "test-author");
    headers.set("X-Commit-Message", "Test eager update");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    ResponseEntity<Void> putResponse = restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request,
        Void.class,
        dataset, graphUri, branch
    );

    assertThat(putResponse.getStatusCode().is2xxSuccessful()).isTrue();

    // Wait for projection to complete
    await().atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> true);

    // Assert: Query should be fast (using eagerly updated cache)
    String sparql = """
        SELECT ?o
        WHERE {
          GRAPH <http://example.org/graph> {
            <http://example.org/subject> <http://example.org/predicate> ?o .
          }
        }
        """;

    HttpHeaders queryHeaders = new HttpHeaders();
    queryHeaders.setContentType(MediaType.valueOf("application/sparql-query"));
    HttpEntity<String> queryRequest = new HttpEntity<>(sparql, queryHeaders);

    long startTime = System.currentTimeMillis();

    ResponseEntity<String> queryResponse = restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        queryRequest,
        String.class,
        dataset, branch
    );

    long queryTime = System.currentTimeMillis() - startTime;

    // Assert: Response correct
    assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(queryResponse.getBody()).contains("eager value");

    // Assert: Query fast (<100ms means cache was used)
    assertThat(queryTime)
        .as("Query should be fast because cache was eagerly updated")
        .isLessThan(100);
  }

  @Test
  void multipleCommits_shouldKeepCacheUpToDate() throws Exception {
    // Arrange
    String dataset = "test-dataset";
    String branch = "main";
    String graphUri = "http://example.org/graph";

    createDataset(dataset);
    createBranch(dataset, branch);

    // Act: Create 5 commits
    for (int i = 0; i < 5; i++) {
      String turtle = String.format(
          "@prefix ex: <http://example.org/> . ex:subject ex:value \"%d\" .", i);

      putGraph(dataset, graphUri, branch, turtle);

      // Wait for projection
      await().atMost(Duration.ofSeconds(5))
          .pollDelay(Duration.ofMillis(200))
          .until(() -> true);
    }

    // Assert: Final query returns latest value
    String sparql = """
        SELECT ?o
        WHERE {
          GRAPH <http://example.org/graph> {
            <http://example.org/subject> <http://example.org/value> ?o .
          }
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(sparql, headers);

    ResponseEntity<String> response = restTemplate.postForEntity(
        "/datasets/{dataset}?branch={branch}",
        request,
        String.class,
        dataset, branch
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"4\"");  // Latest value
  }

  // Helper methods
  private void createDataset(String dataset) {
    restTemplate.postForEntity("/version/datasets/{dataset}", null, Void.class, dataset);
  }

  private void createBranch(String dataset, String branch) {
    restTemplate.postForEntity(
        "/version/branches?dataset={dataset}&name={branch}",
        null, Void.class, dataset, branch
    );
  }

  private void putGraph(String dataset, String graphUri, String branch, String turtle) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.valueOf("text/turtle"));
    headers.set("X-Author", "test-author");
    headers.set("X-Commit-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    restTemplate.put(
        "/datasets/{dataset}?graph={graph}&branch={branch}",
        request, Void.class, dataset, graphUri, branch
    );
  }
}
```

### Step 5: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run specific tests
mvn -q test -Dtest=EagerCacheUpdateIT 2>&1 | tail -20

# Phase 3: Run all projector tests (ensure no regressions)
mvn -q test -Dtest=*ProjectorIT 2>&1 | tail -20

# Phase 4: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ `handleCommitCreated()` calls `applyPatchToBranchCache()`
- ✅ `handleRevertCreated()` applies patches eagerly
- ✅ `handleCherryPicked()` applies patches eagerly
- ✅ Error handling: graceful degradation on eager update failures
- ✅ Integration tests verify instant query performance
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
void event_shouldEagerlyUpdateCache() throws Exception {
    // Arrange: Create dataset and branch
    createDataset(...);
    createBranch(...);

    // Act: Trigger commit (creates event)
    performOperation(...);

    // Wait for async projection
    await().atMost(Duration.ofSeconds(10)).until(() -> true);

    // Assert: Query is fast (cache was updated)
    long startTime = System.currentTimeMillis();
    ResponseEntity<String> response = executeQuery(...);
    long queryTime = System.currentTimeMillis() - startTime;

    assertThat(queryTime).isLessThan(100);  // Fast!
}
```

### Unit Tests (Optional)

Update existing `ReadModelProjectorTest` if it exists:
- Verify `applyPatchToBranchCache()` called with correct arguments
- Verify error handling doesn't fail projection

---

## Files to Modify

### Production Code
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

### Test Code
- `src/test/java/org/chucc/vcserver/integration/EagerCacheUpdateIT.java` (NEW)
- `src/test/java/org/chucc/vcserver/projection/ReadModelProjectorTest.java` (optional update)

**Total:** 1 production file modified, 1 new test file

---

## Performance Impact

### Before (Lazy Cache)

**First query to branch HEAD:**
1. Resolve branch → commit (5ms)
2. Cache miss
3. Find nearest snapshot (10ms)
4. Load commits (20ms)
5. Apply patches (50-200ms)
6. Cache result (5ms)

**Total:** 90-240ms

### After (Eager Cache)

**Commit event processing:**
1. Save to CommitRepository (10ms)
2. Update branch pointer (5ms)
3. Apply patch to cache (10-50ms)

**Total:** 25-65ms overhead per commit

**Query to branch HEAD:**
1. Resolve branch → commit (5ms)
2. Cache hit (<1ms)

**Total:** <10ms (10-20x faster!)

**Net Win:** Read-heavy workloads benefit significantly!

---

## CQRS Compliance Check

| Aspect | Compliance |
|--------|------------|
| **Projector is read-side-only** | ✅ Yes - only updates read models |
| **No command execution** | ✅ Yes - only processes events |
| **Idempotent event handling** | ✅ Yes - applying same patch twice is safe (Jena RDFPatch) |
| **Eventual consistency** | ✅ Yes - cache updated asynchronously |
| **Events are source of truth** | ✅ Yes - CommitRepository preserves event log |

**Pattern:** Projector maintains multiple read models:
- CommitRepository (event log)
- BranchRepository (metadata)
- DatasetService cache (materialized graphs)

---

## Design Decisions

### Decision 1: Error Handling Strategy

**Question:** If eager cache update fails, should we:
1. Fail the entire projection?
2. Log error and continue (graceful degradation)?

**Chosen:** Option 2 (graceful degradation)

**Rationale:**
- CommitRepository is source of truth (event log preserved)
- Cache can be rebuilt on next query (lazy fallback)
- Failing projection could break other read models
- Better to have stale cache than broken projection

### Decision 2: Remove updateLatestCommit Call?

**Question:** `applyPatchToBranchCache()` calls `updateLatestCommit()` internally. Should we remove the explicit call in projector?

**Chosen:** Yes, remove it (avoid duplication)

**Rationale:**
- Simpler code
- Single responsibility: `applyPatchToBranchCache()` handles both patch and tracking
- No functional difference

---

## Next Steps

After completing this task:
1. ✅ Verify performance improvement (queries should be <10ms)
2. ✅ Move to Task 03: Add performance benchmarks (optional)
3. ✅ Move to Task 04: Add monitoring and recovery mechanisms
4. ✅ Update `.tasks/README.md` to track progress

---

## References

- [ReadModelProjector.java:243-298](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java#L243-L298) - handleCommitCreated
- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - applyPatchToBranchCache (Task 01)
- [CQRS Guide - Projectors](../../docs/architecture/cqrs-event-sourcing.md#read-model-query-side)

---

## Notes

### Why Not Update on BranchCreated?

**Question:** Should we initialize cache when branch is created?

**Answer:** No need! Lazy initialization works fine:
- First commit on new branch triggers eager update
- Empty branches have no queries (nothing to cache)
- Keeps code simpler

### Impact on Existing Tests

Most existing tests **not affected** because:
- They don't enable projector (`@TestPropertySource` not set)
- They test HTTP API only (command side)
- Eager updates only happen when projector is enabled

**Only projector-specific tests need updates** (add new test file).
