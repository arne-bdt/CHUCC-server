# Task 01: Create MaterializedBranchRepository Infrastructure

**Status:** Not Started
**Priority:** High (Foundation for materialized views)
**Category:** Architecture Enhancement
**Estimated Time:** 3-4 hours
**Complexity:** Medium

---

## Overview

Create the `MaterializedBranchRepository` component that manages continuously updated, transactional DatasetGraph instances for each branch HEAD. This is the foundation for moving from on-demand graph materialization to eager materialization.

**Current Problem:**
- `DatasetService` builds graphs on-demand for every query (expensive)
- Graph state is cached (LRU) but may be evicted
- No guarantee that branch HEADs are always materialized

**Solution:**
- New repository component maintains one DatasetGraph per branch HEAD
- Graphs updated immediately when events are processed
- Always available, never evicted (unless branch deleted)

---

## Current State

### Existing DatasetService Pattern (On-Demand)

**File:** [DatasetService.java:252-271](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L252-L271)

```java
private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    CacheKey key = new CacheKey(datasetName, commitId);

    // For latest commits, use get() which ensures they stay in cache
    if (cacheProperties.isKeepLatestPerBranch() && isLatestCommit(datasetName, commitId)) {
        return datasetCache.get(key, k -> buildDatasetGraph(datasetName, commitId));
    }

    // For non-latest commits, use regular get (subject to LRU eviction)
    DatasetGraphInMemory graph = datasetCache.getIfPresent(key);

    if (graph == null) {
        // Cache miss - build graph
        graph = buildDatasetGraph(datasetName, commitId);
        datasetCache.put(key, graph);
    }

    return graph;
}
```

**Graph Building (Expensive):**

**File:** [DatasetService.java:320-354](../../src/main/java/org/chucc/vcserver/service/DatasetService.java#L320-L354)

```java
private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
    // Try to find nearest snapshot
    Optional<Snapshot> snapshotOpt = findNearestSnapshot(datasetName, commitId);

    DatasetGraphInMemory datasetGraph;
    CommitId startCommit;

    if (snapshotOpt.isPresent()) {
        // Clone snapshot as starting point
        datasetGraph = cloneDatasetGraph(snapshot.graph());
        startCommit = snapshot.commitId();
    } else {
        // Build from scratch
        datasetGraph = new DatasetGraphInMemory();
        startCommit = null;
    }

    // Apply patches from snapshot to target commit
    Commit targetCommit = commitRepository.findByDatasetAndId(...)
        .orElseThrow(...);
    applyPatchHistorySince(datasetName, targetCommit, datasetGraph, startCommit);

    return datasetGraph;
}
```

---

## Requirements

### Functional Requirements

1. **Store one DatasetGraph per branch HEAD**
   - Key: `dataset:branch` → Value: `DatasetGraph`
   - Thread-safe concurrent access
   - Transactional updates using Jena's `ReadWrite` interface

2. **Graph Lifecycle Operations**
   - `getBranchGraph(dataset, branch)` - Get existing or create empty
   - `applyPatchToBranch(dataset, branch, patch)` - Apply patch within transaction
   - `createBranch(dataset, branch, parentBranch)` - Clone parent or create empty
   - `deleteBranch(dataset, branch)` - Remove graph and release memory
   - `exists(dataset, branch)` - Check if materialized graph exists

3. **Transactional Safety**
   - All write operations wrapped in Jena transactions
   - Atomic patch application (commit or rollback)
   - Exception safety (abort on error)

4. **Memory Management**
   - Monitor total memory usage
   - Log graph sizes periodically
   - Provide metrics for monitoring

### Non-Functional Requirements

1. **Performance**
   - O(1) lookup for branch graphs
   - Concurrent read access (multiple queries)
   - Serialized write access per branch

2. **Reliability**
   - Graceful handling of patch application failures
   - Automatic cleanup on branch deletion
   - Safe concurrent access

3. **Observability**
   - Metrics: number of materialized graphs, total memory usage
   - Logging: graph creation, deletion, patch application
   - Health checks

---

## Implementation Steps

### Step 1: Create Repository Interface

**File:** `src/main/java/org/chucc/vcserver/repository/MaterializedBranchRepository.java`

```java
package org.chucc.vcserver.repository;

import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * Repository for managing materialized DatasetGraph views per branch HEAD.
 *
 * <p>This repository maintains continuously updated, transactional RDF graphs
 * for each branch. Unlike on-demand materialization, these graphs are always
 * kept in sync with the latest commit on each branch.
 *
 * <p>Memory usage scales with the number of branches and triples per branch,
 * not with the depth of commit history (which is stored separately in
 * CommitRepository).
 *
 * @see org.chucc.vcserver.repository.CommitRepository for event-sourced commit history
 * @see org.apache.jena.sparql.core.DatasetGraph for Jena's transactional graph API
 */
public interface MaterializedBranchRepository {

  /**
   * Get the materialized graph for a branch HEAD, creating it if necessary.
   *
   * <p>If the graph doesn't exist, an empty DatasetGraph is created and stored.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return the transactional DatasetGraph for this branch (never null)
   */
  DatasetGraph getBranchGraph(String dataset, String branch);

  /**
   * Apply an RDF patch to a branch's materialized graph within a transaction.
   *
   * <p>The operation is atomic: either the patch is fully applied and committed,
   * or it is rolled back on error. Uses Jena's READ_WRITE transaction mode.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param patch the RDF patch to apply
   * @throws PatchApplicationException if patch application fails
   */
  void applyPatchToBranch(String dataset, String branch, RDFPatch patch);

  /**
   * Create a new branch by cloning the parent branch's graph or starting empty.
   *
   * <p>If parentBranch is provided, the parent's current graph state is cloned.
   * Otherwise, an empty DatasetGraph is created.
   *
   * @param dataset the dataset name
   * @param branch the new branch name
   * @param parentBranch optional parent branch to clone from
   * @throws BranchNotFoundException if parent branch doesn't exist
   */
  void createBranch(String dataset, String branch, Optional<String> parentBranch);

  /**
   * Delete a branch's materialized graph and release memory.
   *
   * <p>This operation is idempotent - calling it on a non-existent branch
   * has no effect.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   */
  void deleteBranch(String dataset, String branch);

  /**
   * Check if a materialized graph exists for a branch.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return true if the branch has a materialized graph
   */
  boolean exists(String dataset, String branch);

  /**
   * Get the current number of materialized graphs.
   *
   * @return count of materialized graphs across all datasets
   */
  int getGraphCount();
}
```

### Step 2: Create Custom Exception

**File:** `src/main/java/org/chucc/vcserver/exception/PatchApplicationException.java`

```java
package org.chucc.vcserver.exception;

/**
 * Exception thrown when RDF patch application fails.
 *
 * <p>This typically indicates:
 * - Invalid patch syntax
 * - Patch operations that violate RDF constraints
 * - I/O errors during graph updates
 */
public class PatchApplicationException extends RuntimeException {

  /**
   * Constructs a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public PatchApplicationException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public PatchApplicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

### Step 3: Create In-Memory Implementation

**File:** `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java`

```java
package org.chucc.vcserver.repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.PatchApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * In-memory implementation of MaterializedBranchRepository.
 *
 * <p>Stores all materialized graphs in a ConcurrentHashMap for thread-safe access.
 * Memory usage scales with the number of branches and triples per branch.
 *
 * <p>Thread Safety:
 * - ConcurrentHashMap provides thread-safe map operations
 * - Jena transactions provide thread-safe graph operations
 * - computeIfAbsent ensures atomic graph creation
 */
@Repository
public class InMemoryMaterializedBranchRepository implements MaterializedBranchRepository {

  private static final Logger logger =
      LoggerFactory.getLogger(InMemoryMaterializedBranchRepository.class);

  // Key: "dataset:branch" → Value: DatasetGraph
  private final ConcurrentHashMap<String, DatasetGraph> branchGraphs = new ConcurrentHashMap<>();

  @Override
  public DatasetGraph getBranchGraph(String dataset, String branch) {
    String key = toKey(dataset, branch);
    return branchGraphs.computeIfAbsent(key, k -> {
      logger.info("Creating new materialized graph for {}/{}", dataset, branch);
      return createEmptyDatasetGraph();
    });
  }

  @Override
  public void applyPatchToBranch(String dataset, String branch, RDFPatch patch) {
    DatasetGraph graph = getBranchGraph(dataset, branch);

    graph.begin(ReadWrite.WRITE);
    try {
      RDFPatchOps.applyChange(graph, patch);
      graph.commit();
      logger.debug("Applied patch to materialized graph {}/{}", dataset, branch);
    } catch (Exception e) {
      graph.abort();
      String errorMsg = String.format(
          "Failed to apply patch to materialized graph %s/%s", dataset, branch);
      logger.error(errorMsg, e);
      throw new PatchApplicationException(errorMsg, e);
    } finally {
      graph.end();
    }
  }

  @Override
  public void createBranch(String dataset, String branch, Optional<String> parentBranch) {
    String key = toKey(dataset, branch);

    if (parentBranch.isPresent()) {
      String parentKey = toKey(dataset, parentBranch.get());
      DatasetGraph parentGraph = branchGraphs.get(parentKey);

      if (parentGraph == null) {
        throw new BranchNotFoundException(dataset, parentBranch.get());
      }

      logger.info("Creating branch {}/{} by cloning {}", dataset, branch, parentBranch.get());
      DatasetGraph clonedGraph = cloneDatasetGraph(parentGraph);
      branchGraphs.put(key, clonedGraph);
    } else {
      logger.info("Creating empty branch {}/{}", dataset, branch);
      branchGraphs.put(key, createEmptyDatasetGraph());
    }
  }

  @Override
  public void deleteBranch(String dataset, String branch) {
    String key = toKey(dataset, branch);
    DatasetGraph graph = branchGraphs.remove(key);

    if (graph != null) {
      logger.info("Deleted materialized graph for {}/{}", dataset, branch);
      graph.close();
    }
  }

  @Override
  public boolean exists(String dataset, String branch) {
    String key = toKey(dataset, branch);
    return branchGraphs.containsKey(key);
  }

  @Override
  public int getGraphCount() {
    return branchGraphs.size();
  }

  /**
   * Create an empty transactional DatasetGraph.
   *
   * @return new empty DatasetGraph
   */
  private DatasetGraph createEmptyDatasetGraph() {
    // Use in-memory transactional dataset
    return DatasetGraphFactory.createTxnMem();
  }

  /**
   * Clone a DatasetGraph by copying all graphs and triples.
   *
   * @param source the source graph to clone
   * @return a new DatasetGraph with copied data
   */
  private DatasetGraph cloneDatasetGraph(DatasetGraph source) {
    DatasetGraph target = createEmptyDatasetGraph();

    source.begin(ReadWrite.READ);
    try {
      // Copy all named graphs
      source.listGraphNodes().forEachRemaining(graphName -> {
        Graph sourceGraph = source.getGraph(graphName);
        Graph targetGraph = target.getGraph(graphName);

        // Copy prefix mappings
        targetGraph.getPrefixMapping().setNsPrefixes(sourceGraph.getPrefixMapping());

        // Copy all triples
        sourceGraph.find().forEachRemaining(targetGraph::add);
      });

      // Copy default graph
      Graph defaultSource = source.getDefaultGraph();
      Graph defaultTarget = target.getDefaultGraph();
      defaultTarget.getPrefixMapping().setNsPrefixes(defaultSource.getPrefixMapping());
      defaultSource.find().forEachRemaining(defaultTarget::add);

      source.commit();
    } finally {
      source.end();
    }

    return target;
  }

  /**
   * Create cache key from dataset and branch names.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return cache key in format "dataset:branch"
   */
  private String toKey(String dataset, String branch) {
    return dataset + ":" + branch;
  }
}
```

### Step 4: Add Unit Tests

**File:** `src/test/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepositoryTest.java`

```java
package org.chucc.vcserver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.PatchApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryMaterializedBranchRepositoryTest {

  private InMemoryMaterializedBranchRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryMaterializedBranchRepository();
  }

  @Test
  void getBranchGraph_shouldCreateNewGraphIfNotExists() {
    // Act
    DatasetGraph graph = repository.getBranchGraph("dataset1", "main");

    // Assert
    assertThat(graph).isNotNull();
    assertThat(repository.exists("dataset1", "main")).isTrue();
    assertThat(repository.getGraphCount()).isEqualTo(1);
  }

  @Test
  void getBranchGraph_shouldReturnSameGraphOnMultipleCalls() {
    // Act
    DatasetGraph graph1 = repository.getBranchGraph("dataset1", "main");
    DatasetGraph graph2 = repository.getBranchGraph("dataset1", "main");

    // Assert
    assertThat(graph1).isSameAs(graph2);
    assertThat(repository.getGraphCount()).isEqualTo(1);
  }

  @Test
  void applyPatchToBranch_shouldApplyPatchSuccessfully() {
    // Arrange
    String patchStr = """
        TX .
        A <http://example.org/graph> <http://example.org/s> <http://example.org/p> <http://example.org/o> .
        TC .
        """;
    RDFPatch patch = parsePatch(patchStr);

    // Act
    repository.applyPatchToBranch("dataset1", "main", patch);

    // Assert
    DatasetGraph graph = repository.getBranchGraph("dataset1", "main");
    graph.begin(ReadWrite.READ);
    try {
      Node graphNode = NodeFactory.createURI("http://example.org/graph");
      Node subject = NodeFactory.createURI("http://example.org/s");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Node object = NodeFactory.createURI("http://example.org/o");

      Quad expectedQuad = Quad.create(graphNode, subject, predicate, object);
      assertThat(graph.contains(expectedQuad)).isTrue();
    } finally {
      graph.end();
    }
  }

  @Test
  void applyPatchToBranch_shouldRollbackOnError() {
    // Arrange: Invalid patch (missing TC)
    String invalidPatchStr = "TX .\nA <s> <p> <o> .\n";  // Missing TC

    // Act & Assert
    assertThatThrownBy(() -> {
      RDFPatch patch = parsePatch(invalidPatchStr);
      repository.applyPatchToBranch("dataset1", "main", patch);
    }).isInstanceOf(PatchApplicationException.class);
  }

  @Test
  void createBranch_shouldCreateEmptyBranchWhenNoParent() {
    // Act
    repository.createBranch("dataset1", "feature", Optional.empty());

    // Assert
    assertThat(repository.exists("dataset1", "feature")).isTrue();
    DatasetGraph graph = repository.getBranchGraph("dataset1", "feature");

    graph.begin(ReadWrite.READ);
    try {
      assertThat(graph.isEmpty()).isTrue();
    } finally {
      graph.end();
    }
  }

  @Test
  void createBranch_shouldCloneParentBranch() {
    // Arrange: Create main branch with data
    String patchStr = """
        TX .
        A <http://example.org/g> <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    repository.applyPatchToBranch("dataset1", "main", parsePatch(patchStr));

    // Act: Create feature branch from main
    repository.createBranch("dataset1", "feature", Optional.of("main"));

    // Assert: Feature branch has same data
    DatasetGraph featureGraph = repository.getBranchGraph("dataset1", "feature");
    featureGraph.begin(ReadWrite.READ);
    try {
      assertThat(featureGraph.isEmpty()).isFalse();
      Node graphNode = NodeFactory.createURI("http://example.org/g");
      Node subject = NodeFactory.createURI("http://example.org/s");
      Node predicate = NodeFactory.createURI("http://example.org/p");
      Quad quad = Quad.create(graphNode, subject, predicate, NodeFactory.createLiteral("value"));
      assertThat(featureGraph.contains(quad)).isTrue();
    } finally {
      featureGraph.end();
    }

    // Assert: Branches are independent (different instances)
    DatasetGraph mainGraph = repository.getBranchGraph("dataset1", "main");
    assertThat(mainGraph).isNotSameAs(featureGraph);
  }

  @Test
  void createBranch_shouldThrowExceptionIfParentDoesNotExist() {
    // Act & Assert
    assertThatThrownBy(() ->
        repository.createBranch("dataset1", "feature", Optional.of("nonexistent"))
    ).isInstanceOf(BranchNotFoundException.class);
  }

  @Test
  void deleteBranch_shouldRemoveGraph() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");
    assertThat(repository.exists("dataset1", "main")).isTrue();

    // Act
    repository.deleteBranch("dataset1", "main");

    // Assert
    assertThat(repository.exists("dataset1", "main")).isFalse();
    assertThat(repository.getGraphCount()).isZero();
  }

  @Test
  void deleteBranch_shouldBeIdempotent() {
    // Act: Delete non-existent branch
    repository.deleteBranch("dataset1", "nonexistent");

    // Assert: No exception thrown
    assertThat(repository.getGraphCount()).isZero();
  }

  @Test
  void exists_shouldReturnFalseForNonExistentBranch() {
    // Act & Assert
    assertThat(repository.exists("dataset1", "main")).isFalse();
  }

  @Test
  void getGraphCount_shouldReturnCorrectCount() {
    // Arrange
    repository.getBranchGraph("dataset1", "main");
    repository.getBranchGraph("dataset1", "feature");
    repository.getBranchGraph("dataset2", "main");

    // Assert
    assertThat(repository.getGraphCount()).isEqualTo(3);
  }

  private RDFPatch parsePatch(String patchStr) {
    ByteArrayInputStream inputStream = new ByteArrayInputStream(
        patchStr.getBytes(StandardCharsets.UTF_8));
    return RDFPatchOps.read(inputStream);
  }
}
```

### Step 5: Run Build and Verify

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run new tests
mvn -q test -Dtest=InMemoryMaterializedBranchRepositoryTest

# Phase 3: Full build
mvn -q clean install
```

---

## Success Criteria

- ✅ `MaterializedBranchRepository` interface created with complete Javadoc
- ✅ `InMemoryMaterializedBranchRepository` implementation created
- ✅ `PatchApplicationException` created
- ✅ All unit tests pass (12+ test cases)
- ✅ Thread-safe concurrent access verified
- ✅ Jena transactions used correctly (begin/commit/abort/end)
- ✅ Graph cloning works correctly
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Testing Strategy

### Unit Tests (No Projector Needed)

All tests in this phase are pure unit tests:
- Test repository operations in isolation
- No Spring Boot context needed
- No Kafka/projector involved
- Fast execution (<1 second)

### Test Categories

1. **Graph Lifecycle Tests**
   - Creating graphs
   - Retrieving existing graphs
   - Deleting graphs

2. **Patch Application Tests**
   - Successful patch application
   - Transaction rollback on error
   - Multiple patches applied in sequence

3. **Branch Cloning Tests**
   - Clone from parent branch
   - Create empty branch
   - Verify independence of cloned graphs

4. **Edge Cases**
   - Non-existent parent branch
   - Idempotent deletion
   - Concurrent access (optional, can defer to integration tests)

---

## Files to Create

### Production Code
- `src/main/java/org/chucc/vcserver/repository/MaterializedBranchRepository.java` (interface)
- `src/main/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepository.java` (impl)
- `src/main/java/org/chucc/vcserver/exception/PatchApplicationException.java`

### Test Code
- `src/test/java/org/chucc/vcserver/repository/InMemoryMaterializedBranchRepositoryTest.java`

**Total:** 4 new files

---

## Dependencies

### No New Maven Dependencies

All required libraries already in project:
- Apache Jena (RDFPatch, DatasetGraph, transactions)
- Spring Framework (@Repository annotation)
- SLF4J (logging)
- JUnit 5 + AssertJ (testing)

---

## Next Steps

After completing this task:
1. ✅ Mark this task file as complete
2. ✅ Move to Task 02: Update ReadModelProjector to use MaterializedBranchRepository
3. ✅ Update `.tasks/README.md` to track progress

---

## References

- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Current on-demand materialization
- [CommitRepository.java](../../src/main/java/org/chucc/vcserver/repository/CommitRepository.java) - Similar repository pattern
- [Apache Jena Transactions](https://jena.apache.org/documentation/txn/) - Transaction API documentation
- [DatasetGraph API](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/sparql/core/DatasetGraph.html)

---

## Notes

### Memory Considerations

With 10 branches × 100K triples per branch:
- Estimated memory: ~200 MB (Jena in-memory)
- Acceptable for most deployments
- Monitor via metrics in later tasks

### Alternative Implementations (Future)

For production with many branches or large graphs:
- TDB2-backed persistent graphs (on-disk)
- External graph database (GraphDB, Neptune)
- Hybrid approach (hot branches in-memory, cold on-disk)

This task uses in-memory for simplicity and performance.
