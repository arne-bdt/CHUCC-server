# Task: Create Plain Java API for Graph Store Protocol

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** Task 01 (SPARQL Java API - for consistent API style)

---

## Context

Currently, Graph Store Protocol operations are only accessible via HTTP endpoints:
- GET `/data` - Retrieve graph
- PUT `/data` - Replace graph
- POST `/data` - Merge graph
- DELETE `/data` - Delete graph
- PATCH `/data` - Apply RDF Patch

**Goal:** Create a programmatic Java API for direct graph manipulation without HTTP overhead.

---

## Implementation Plan

### Step 1: Create GraphStoreProtocolApi Interface

**File:** `src/main/java/org/chucc/vcserver/api/GraphStoreProtocolApi.java`

```java
package org.chucc.vcserver.api;

import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdfpatch.RDFPatch;

/**
 * Plain Java API for Graph Store Protocol operations.
 * Provides programmatic access to graph CRUD operations.
 *
 * <p>Example usage:
 * <pre>{@code
 * GraphStoreProtocolApi api = applicationContext.getBean(GraphStoreProtocolApi.class);
 *
 * // Read graph
 * Optional<Model> model = api.getGraph(
 *     "http://example.org/graph1",
 *     GraphSelector.branch("main")
 * );
 *
 * // Replace graph
 * Model newModel = ModelFactory.createDefaultModel();
 * newModel.add(/* triples */);
 *
 * GraphOperationResult result = api.putGraph(
 *     "http://example.org/graph1",
 *     newModel,
 *     GraphSelector.branch("main"),
 *     "alice@example.com",
 *     "Replace graph"
 * );
 *
 * String commitId = result.getCommitId();
 * }</pre>
 */
public interface GraphStoreProtocolApi {

  /**
   * Retrieves a named graph.
   *
   * @param graphUri the graph URI (null for default graph)
   * @param selector the version selector
   * @return the graph model, or empty if graph doesn't exist
   */
  Optional<Model> getGraph(String graphUri, GraphSelector selector);

  /**
   * Retrieves the default graph.
   *
   * @param selector the version selector
   * @return the default graph model
   */
  default Model getDefaultGraph(GraphSelector selector) {
    return getGraph(null, selector).orElseGet(
        () -> org.apache.jena.rdf.model.ModelFactory.createDefaultModel());
  }

  /**
   * Replaces a graph (PUT operation).
   *
   * @param graphUri the graph URI (null for default graph)
   * @param model the new graph content
   * @param selector the version selector (branch name)
   * @param author the author
   * @param message the commit message
   * @return the operation result with commit ID
   */
  GraphOperationResult putGraph(
      String graphUri,
      Model model,
      GraphSelector selector,
      String author,
      String message);

  /**
   * Merges triples into a graph (POST operation).
   *
   * @param graphUri the graph URI
   * @param model the triples to add
   * @param selector the version selector
   * @param author the author
   * @param message the commit message
   * @return the operation result
   */
  GraphOperationResult postGraph(
      String graphUri,
      Model model,
      GraphSelector selector,
      String author,
      String message);

  /**
   * Deletes a graph (DELETE operation).
   *
   * @param graphUri the graph URI
   * @param selector the version selector
   * @param author the author
   * @param message the commit message
   * @return the operation result
   */
  GraphOperationResult deleteGraph(
      String graphUri,
      GraphSelector selector,
      String author,
      String message);

  /**
   * Applies an RDF Patch to a graph (PATCH operation).
   *
   * @param graphUri the graph URI
   * @param patch the RDF patch
   * @param selector the version selector
   * @param author the author
   * @param message the commit message
   * @return the operation result
   */
  GraphOperationResult patchGraph(
      String graphUri,
      RDFPatch patch,
      GraphSelector selector,
      String author,
      String message);

  // Convenience methods with defaults

  default GraphOperationResult putGraph(String graphUri, Model model, GraphSelector selector) {
    return putGraph(graphUri, model, selector, "anonymous", "PUT graph via Java API");
  }

  default GraphOperationResult postGraph(String graphUri, Model model, GraphSelector selector) {
    return postGraph(graphUri, model, selector, "anonymous", "POST graph via Java API");
  }

  default GraphOperationResult deleteGraph(String graphUri, GraphSelector selector) {
    return deleteGraph(graphUri, selector, "anonymous", "DELETE graph via Java API");
  }

  default GraphOperationResult patchGraph(String graphUri, RDFPatch patch, GraphSelector selector) {
    return patchGraph(graphUri, patch, selector, "anonymous", "PATCH graph via Java API");
  }
}
```

---

### Step 2: Create Supporting Value Objects

**File:** `src/main/java/org/chucc/vcserver/api/GraphSelector.java`

```java
package org.chucc.vcserver.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Version selector for graph operations.
 * Similar to SparqlSelector but for Graph Store Protocol.
 */
public sealed interface GraphSelector {

  record Branch(String name) implements GraphSelector {
    public Branch {
      Objects.requireNonNull(name, "Branch name cannot be null");
    }
  }

  record Commit(String commitId) implements GraphSelector {
    public Commit {
      Objects.requireNonNull(commitId, "Commit ID cannot be null");
    }
  }

  record Timestamp(Instant instant) implements GraphSelector {
    public Timestamp {
      Objects.requireNonNull(instant, "Timestamp cannot be null");
    }
  }

  static Branch branch(String name) {
    return new Branch(name);
  }

  static Commit commit(String commitId) {
    return new Commit(commitId);
  }

  static Timestamp asOf(Instant instant) {
    return new Timestamp(instant);
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/api/GraphOperationResult.java`

```java
package org.chucc.vcserver.api;

import java.time.Instant;

/**
 * Result of a graph operation (PUT, POST, DELETE, PATCH).
 */
public interface GraphOperationResult {

  /**
   * Returns the commit ID created by the operation.
   */
  String getCommitId();

  /**
   * Returns the timestamp of the operation.
   */
  Instant getTimestamp();

  /**
   * Returns the author.
   */
  String getAuthor();

  /**
   * Returns the commit message.
   */
  String getMessage();

  /**
   * Returns whether the operation was a no-op (no changes).
   */
  boolean isNoOp();
}
```

---

### Step 3: Implement GraphStoreProtocolApi

**File:** `src/main/java/org/chucc/vcserver/api/GraphStoreProtocolApiImpl.java`

```java
package org.chucc.vcserver.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Optional;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.command.*;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SelectorResolutionService;
import org.chucc.vcserver.service.VersionSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementation of Graph Store Protocol Java API.
 */
@Service
public class GraphStoreProtocolApiImpl implements GraphStoreProtocolApi {
  private static final Logger logger = LoggerFactory.getLogger(GraphStoreProtocolApiImpl.class);

  private final DatasetService datasetService;
  private final SelectorResolutionService selectorResolutionService;
  private final PutGraphCommandHandler putGraphCommandHandler;
  private final PostGraphCommandHandler postGraphCommandHandler;
  private final DeleteGraphCommandHandler deleteGraphCommandHandler;
  private final PatchGraphCommandHandler patchGraphCommandHandler;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public GraphStoreProtocolApiImpl(
      DatasetService datasetService,
      SelectorResolutionService selectorResolutionService,
      PutGraphCommandHandler putGraphCommandHandler,
      PostGraphCommandHandler postGraphCommandHandler,
      DeleteGraphCommandHandler deleteGraphCommandHandler,
      PatchGraphCommandHandler patchGraphCommandHandler) {
    this.datasetService = datasetService;
    this.selectorResolutionService = selectorResolutionService;
    this.putGraphCommandHandler = putGraphCommandHandler;
    this.postGraphCommandHandler = postGraphCommandHandler;
    this.deleteGraphCommandHandler = deleteGraphCommandHandler;
    this.patchGraphCommandHandler = patchGraphCommandHandler;
  }

  @Override
  public Optional<Model> getGraph(String graphUri, GraphSelector selector) {
    logger.debug("Getting graph via Java API: {}", graphUri);

    // Resolve selector
    VersionSelector versionSelector = convertSelector(selector);
    CommitId commitId = selectorResolutionService.resolve(versionSelector, "default");

    // Get graph
    if (graphUri == null) {
      // Default graph
      Model model = datasetService.getDefaultGraph("default", commitId);
      return Optional.of(model);
    } else {
      // Named graph
      Model model = datasetService.getGraph("default", commitId, graphUri);
      return Optional.ofNullable(model);
    }
  }

  @Override
  public GraphOperationResult putGraph(
      String graphUri,
      Model model,
      GraphSelector selector,
      String author,
      String message) {

    logger.debug("Putting graph via Java API: {}", graphUri);

    // Convert selector (must be branch)
    if (!(selector instanceof GraphSelector.Branch branch)) {
      throw new IllegalArgumentException(
          "Graph writes require a branch selector, got: " + selector.getClass().getSimpleName());
    }

    // Create command
    PutGraphCommand command = new PutGraphCommand(
        "default",
        branch.name(),
        graphUri,
        model,
        author,
        message,
        null  // No If-Match
    );

    // Execute
    CommitCreatedEvent event = putGraphCommandHandler.handle(command);

    return new OperationResult(event);
  }

  @Override
  public GraphOperationResult postGraph(
      String graphUri,
      Model model,
      GraphSelector selector,
      String author,
      String message) {

    logger.debug("Posting graph via Java API: {}", graphUri);

    if (!(selector instanceof GraphSelector.Branch branch)) {
      throw new IllegalArgumentException("Graph writes require a branch selector");
    }

    PostGraphCommand command = new PostGraphCommand(
        "default",
        branch.name(),
        graphUri,
        model,
        author,
        message
    );

    CommitCreatedEvent event = postGraphCommandHandler.handle(command);

    return new OperationResult(event);
  }

  @Override
  public GraphOperationResult deleteGraph(
      String graphUri,
      GraphSelector selector,
      String author,
      String message) {

    logger.debug("Deleting graph via Java API: {}", graphUri);

    if (!(selector instanceof GraphSelector.Branch branch)) {
      throw new IllegalArgumentException("Graph writes require a branch selector");
    }

    DeleteGraphCommand command = new DeleteGraphCommand(
        "default",
        branch.name(),
        graphUri,
        author,
        message
    );

    // DeleteGraph may return null if graph was already empty (no-op)
    CommitCreatedEvent event = deleteGraphCommandHandler.handle(command);

    return event != null ? new OperationResult(event) : new NoOpResult();
  }

  @Override
  public GraphOperationResult patchGraph(
      String graphUri,
      RDFPatch patch,
      GraphSelector selector,
      String author,
      String message) {

    logger.debug("Patching graph via Java API: {}", graphUri);

    if (!(selector instanceof GraphSelector.Branch branch)) {
      throw new IllegalArgumentException("Graph writes require a branch selector");
    }

    PatchGraphCommand command = new PatchGraphCommand(
        "default",
        branch.name(),
        graphUri,
        patch,
        author,
        message
    );

    CommitCreatedEvent event = patchGraphCommandHandler.handle(command);

    return new OperationResult(event);
  }

  private VersionSelector convertSelector(GraphSelector selector) {
    return switch (selector) {
      case GraphSelector.Branch b -> new VersionSelector.Branch(b.name());
      case GraphSelector.Commit c -> new VersionSelector.Commit(c.commitId());
      case GraphSelector.Timestamp t -> new VersionSelector.AsOf(t.instant());
    };
  }

  // Result implementations

  private record OperationResult(CommitCreatedEvent event) implements GraphOperationResult {

    @Override
    public String getCommitId() {
      return event.commitId();
    }

    @Override
    public Instant getTimestamp() {
      return event.timestamp();
    }

    @Override
    public String getAuthor() {
      return event.author();
    }

    @Override
    public String getMessage() {
      return event.message();
    }

    @Override
    public boolean isNoOp() {
      return false;
    }
  }

  private record NoOpResult() implements GraphOperationResult {

    @Override
    public String getCommitId() {
      return null;
    }

    @Override
    public Instant getTimestamp() {
      return Instant.now();
    }

    @Override
    public String getAuthor() {
      return "system";
    }

    @Override
    public String getMessage() {
      return "No operation (graph already empty)";
    }

    @Override
    public boolean isNoOp() {
      return true;
    }
  }
}
```

---

### Step 4: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/api/GraphStoreProtocolApiTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class GraphStoreProtocolApiTest {

  @Autowired
  private GraphStoreProtocolApi api;

  @Test
  void putGraph_shouldReplaceGraph() {
    // Arrange
    setupTestDataset();

    Model newModel = ModelFactory.createDefaultModel();
    newModel.add(
        newModel.createResource("http://example.org/s1"),
        newModel.createProperty("http://example.org/p1"),
        newModel.createResource("http://example.org/o1")
    );

    // Act
    GraphOperationResult result = api.putGraph(
        "http://example.org/graph1",
        newModel,
        GraphSelector.branch("main"),
        "alice",
        "Replace graph"
    );

    // Assert
    assertThat(result.getCommitId()).isNotNull();
    assertThat(result.getAuthor()).isEqualTo("alice");
    assertThat(result.isNoOp()).isFalse();

    // Verify graph was replaced
    Optional<Model> retrieved = api.getGraph(
        "http://example.org/graph1",
        GraphSelector.branch("main")
    );

    assertThat(retrieved).isPresent();
    assertThat(retrieved.get().size()).isEqualTo(1);
  }

  @Test
  void getGraph_nonExistent_shouldReturnEmpty() {
    setupTestDataset();

    Optional<Model> result = api.getGraph(
        "http://example.org/non-existent",
        GraphSelector.branch("main")
    );

    assertThat(result).isEmpty();
  }

  @Test
  void deleteGraph_emptyGraph_shouldReturnNoOp() {
    setupTestDataset();

    // Delete non-existent graph
    GraphOperationResult result = api.deleteGraph(
        "http://example.org/empty",
        GraphSelector.branch("main")
    );

    assertThat(result.isNoOp()).isTrue();
  }
}
```

---

## Usage Examples

**Example 1: Read and write graphs**
```java
@Autowired
private GraphStoreProtocolApi api;

public void replaceGraph() {
  // Create new model
  Model model = ModelFactory.createDefaultModel();
  Resource person = model.createResource("http://example.org/person1");
  person.addProperty(FOAF.name, "Alice");
  person.addProperty(FOAF.mbox, "alice@example.com");

  // Replace graph
  GraphOperationResult result = api.putGraph(
      "http://example.org/people",
      model,
      GraphSelector.branch("main"),
      "admin",
      "Update people graph"
  );

  System.out.println("Created commit: " + result.getCommitId());
}

public void readGraph() {
  Optional<Model> model = api.getGraph(
      "http://example.org/people",
      GraphSelector.branch("main")
  );

  if (model.isPresent()) {
    model.get().listStatements().forEachRemaining(statement -> {
      System.out.println(statement);
    });
  }
}
```

**Example 2: Apply RDF Patch**
```java
public void applyChanges() {
  // Create patch (add + delete operations)
  String patchText = """
      A <http://example.org/s1> <http://example.org/p1> <http://example.org/o1> <http://example.org/g1> .
      D <http://example.org/s2> <http://example.org/p2> <http://example.org/o2> <http://example.org/g1> .
      """;

  RDFPatch patch = RDFPatchOps.read(new ByteArrayInputStream(patchText.getBytes()));

  GraphOperationResult result = api.patchGraph(
      "http://example.org/g1",
      patch,
      GraphSelector.branch("main"),
      "alice",
      "Apply incremental changes"
  );

  System.out.println("Patched, commit: " + result.getCommitId());
}
```

---

## Success Criteria

- [ ] `GraphStoreProtocolApi` interface defined
- [ ] Implementation delegates to existing command handlers
- [ ] GET, PUT, POST, DELETE, PATCH operations supported
- [ ] Branch, commit, and timestamp selectors work
- [ ] Named graphs and default graph supported
- [ ] No-op detection (DELETE on empty graph)
- [ ] All tests pass
- [ ] Javadoc complete
- [ ] Usage examples provided

---

## Future Enhancements

- **Batch operations:** Put/post multiple graphs in one transaction
- **Streaming:** Support large graphs without loading fully in memory
- **Async API:** Return `CompletableFuture<GraphOperationResult>`
- **Conditional writes:** Support If-Match for optimistic locking
- **Bulk delete:** Delete multiple graphs at once
