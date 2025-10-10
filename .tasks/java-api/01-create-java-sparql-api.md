# Task: Create Plain Java API for SPARQL Protocol

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** None (wraps existing HTTP controllers)

---

## Context

Currently, SPARQL operations are only accessible via HTTP endpoints:
- POST `/sparql` - SPARQL Query
- POST `/update` - SPARQL Update

**Goal:** Create a programmatic Java API that:
1. Provides the same functionality as HTTP endpoints
2. Can be used by Java applications without HTTP overhead
3. Useful for embedded use cases, testing, and library integration

---

## Design Decisions

### API Style

**Option 1: Mirror HTTP API** (Recommended)
- Java API matches HTTP semantics exactly
- Easy to understand for users familiar with SPARQL Protocol
- Returns same types as controllers

**Option 2: Fluent Builder API**
- More "Java-like" with method chaining
- More complex to implement
- Defer to future enhancement

**Choose:** Option 1 (simpler, matches spec)

### API Location

```
org.chucc.vcserver.api.SparqlProtocolApi
```

Separate from controllers (controllers delegate to API internally).

---

## Implementation Plan

### Step 1: Create SparqlProtocolApi Interface

**File:** `src/main/java/org/chucc/vcserver/api/SparqlProtocolApi.java`

```java
package org.chucc.vcserver.api;

import java.util.Optional;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

/**
 * Plain Java API for SPARQL Protocol operations.
 * Provides programmatic access to SPARQL queries and updates.
 *
 * <p>This API mirrors the HTTP SPARQL Protocol but can be used
 * directly from Java without HTTP overhead.
 *
 * <p>Example usage:
 * <pre>{@code
 * SparqlProtocolApi api = applicationContext.getBean(SparqlProtocolApi.class);
 *
 * // Execute SPARQL query
 * SparqlQueryResult result = api.query(
 *     "SELECT * WHERE { ?s ?p ?o } LIMIT 10",
 *     SparqlSelector.branch("main")
 * );
 *
 * if (result.isResultSet()) {
 *     ResultSet rs = result.getResultSet();
 *     while (rs.hasNext()) {
 *         QuerySolution qs = rs.next();
 *         // Process results
 *     }
 * }
 *
 * // Execute SPARQL update
 * SparqlUpdateResult updateResult = api.update(
 *     "INSERT DATA { <http://example.org/s> <http://example.org/p> <http://example.org/o> }",
 *     SparqlSelector.branch("main"),
 *     "alice@example.com",
 *     "Add triple"
 * );
 *
 * String commitId = updateResult.getCommitId();
 * }</pre>
 */
public interface SparqlProtocolApi {

  /**
   * Executes a SPARQL query.
   *
   * @param query the SPARQL query string
   * @param selector the version selector (branch, commit, timestamp)
   * @return the query result (SELECT, CONSTRUCT, ASK, or DESCRIBE)
   * @throws org.chucc.vcserver.exception.InvalidSparqlQueryException if query is invalid
   */
  SparqlQueryResult query(String query, SparqlSelector selector);

  /**
   * Executes a SPARQL update.
   *
   * @param update the SPARQL update string
   * @param selector the version selector (branch name)
   * @param author the author of the update
   * @param message the commit message
   * @return the update result containing commit ID and metadata
   * @throws org.chucc.vcserver.exception.InvalidSparqlUpdateException if update is invalid
   */
  SparqlUpdateResult update(
      String update,
      SparqlSelector selector,
      String author,
      String message);

  /**
   * Executes a SPARQL update with default author and message.
   *
   * @param update the SPARQL update string
   * @param selector the version selector
   * @return the update result
   */
  default SparqlUpdateResult update(String update, SparqlSelector selector) {
    return update(update, selector, "anonymous", "SPARQL UPDATE via Java API");
  }
}
```

---

### Step 2: Create Supporting Value Objects

**File:** `src/main/java/org/chucc/vcserver/api/SparqlSelector.java`

```java
package org.chucc.vcserver.api;

import java.time.Instant;
import java.util.Objects;

/**
 * Version selector for SPARQL queries.
 * Specifies which version of the dataset to query.
 */
public sealed interface SparqlSelector {

  /**
   * Select by branch name (latest commit).
   */
  record Branch(String name) implements SparqlSelector {
    public Branch {
      Objects.requireNonNull(name, "Branch name cannot be null");
    }
  }

  /**
   * Select by specific commit ID.
   */
  record Commit(String commitId) implements SparqlSelector {
    public Commit {
      Objects.requireNonNull(commitId, "Commit ID cannot be null");
    }
  }

  /**
   * Select by timestamp (asOf query).
   */
  record Timestamp(Instant instant) implements SparqlSelector {
    public Timestamp {
      Objects.requireNonNull(instant, "Timestamp cannot be null");
    }
  }

  /**
   * Creates a branch selector.
   */
  static Branch branch(String name) {
    return new Branch(name);
  }

  /**
   * Creates a commit selector.
   */
  static Commit commit(String commitId) {
    return new Commit(commitId);
  }

  /**
   * Creates a timestamp selector.
   */
  static Timestamp asOf(Instant instant) {
    return new Timestamp(instant);
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/api/SparqlQueryResult.java`

```java
package org.chucc.vcserver.api;

import java.util.Optional;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;

/**
 * Result of a SPARQL query.
 * Can contain either a ResultSet (SELECT), Model (CONSTRUCT/DESCRIBE), or boolean (ASK).
 */
public interface SparqlQueryResult {

  /**
   * Returns the result type.
   */
  ResultType getResultType();

  /**
   * Returns the result set for SELECT queries.
   */
  Optional<ResultSet> getResultSet();

  /**
   * Returns the model for CONSTRUCT/DESCRIBE queries.
   */
  Optional<Model> getModel();

  /**
   * Returns the boolean result for ASK queries.
   */
  Optional<Boolean> getBoolean();

  /**
   * Checks if this is a SELECT query result.
   */
  default boolean isResultSet() {
    return getResultType() == ResultType.SELECT;
  }

  /**
   * Checks if this is a CONSTRUCT/DESCRIBE query result.
   */
  default boolean isModel() {
    return getResultType() == ResultType.GRAPH;
  }

  /**
   * Checks if this is an ASK query result.
   */
  default boolean isBoolean() {
    return getResultType() == ResultType.BOOLEAN;
  }

  /**
   * Result type enum.
   */
  enum ResultType {
    SELECT,
    GRAPH,  // CONSTRUCT or DESCRIBE
    BOOLEAN  // ASK
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/api/SparqlUpdateResult.java`

```java
package org.chucc.vcserver.api;

import java.time.Instant;

/**
 * Result of a SPARQL update operation.
 */
public interface SparqlUpdateResult {

  /**
   * Returns the commit ID created by the update.
   */
  String getCommitId();

  /**
   * Returns the timestamp of the update.
   */
  Instant getTimestamp();

  /**
   * Returns the author of the update.
   */
  String getAuthor();

  /**
   * Returns the commit message.
   */
  String getMessage();
}
```

---

### Step 3: Implement SparqlProtocolApi

**File:** `src/main/java/org/chucc/vcserver/api/SparqlProtocolApiImpl.java`

```java
package org.chucc.vcserver.api;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Optional;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;
import org.chucc.vcserver.command.SparqlUpdateCommand;
import org.chucc.vcserver.command.SparqlUpdateCommandHandler;
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
 * Implementation of SPARQL Protocol Java API.
 */
@Service
public class SparqlProtocolApiImpl implements SparqlProtocolApi {
  private static final Logger logger = LoggerFactory.getLogger(SparqlProtocolApiImpl.class);

  private final DatasetService datasetService;
  private final SelectorResolutionService selectorResolutionService;
  private final SparqlUpdateCommandHandler sparqlUpdateCommandHandler;

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "All dependencies are Spring-managed beans")
  public SparqlProtocolApiImpl(
      DatasetService datasetService,
      SelectorResolutionService selectorResolutionService,
      SparqlUpdateCommandHandler sparqlUpdateCommandHandler) {
    this.datasetService = datasetService;
    this.selectorResolutionService = selectorResolutionService;
    this.sparqlUpdateCommandHandler = sparqlUpdateCommandHandler;
  }

  @Override
  public SparqlQueryResult query(String query, SparqlSelector selector) {
    logger.debug("Executing SPARQL query via Java API");

    // Parse query
    Query parsedQuery = QueryFactory.create(query);

    // Resolve selector to dataset reference
    VersionSelector versionSelector = convertSelector(selector);
    DatasetRef datasetRef = resolveSelector(versionSelector);

    // Get dataset
    org.apache.jena.query.Dataset dataset = datasetService.getDataset(datasetRef);

    // Execute query
    try (QueryExecution qexec = org.apache.jena.query.QueryExecutionFactory.create(
        parsedQuery, dataset)) {

      return switch (parsedQuery.getQueryType()) {
        case Query.QueryTypeSelect -> {
          ResultSet rs = qexec.execSelect();
          // Must materialize result set (it's tied to QueryExecution lifecycle)
          ResultSet materialized = ResultSetFactory.copyResults(rs);
          yield new SelectResult(materialized);
        }
        case Query.QueryTypeConstruct -> {
          Model model = qexec.execConstruct();
          yield new GraphResult(model);
        }
        case Query.QueryTypeDescribe -> {
          Model model = qexec.execDescribe();
          yield new GraphResult(model);
        }
        case Query.QueryTypeAsk -> {
          boolean result = qexec.execAsk();
          yield new BooleanResult(result);
        }
        default -> throw new IllegalArgumentException(
            "Unsupported query type: " + parsedQuery.getQueryType());
      };
    }
  }

  @Override
  public SparqlUpdateResult update(
      String update,
      SparqlSelector selector,
      String author,
      String message) {

    logger.debug("Executing SPARQL update via Java API");

    // Parse update
    UpdateRequest updateRequest = UpdateFactory.create(update);

    // Convert selector (must be branch for updates)
    if (!(selector instanceof SparqlSelector.Branch branch)) {
      throw new IllegalArgumentException(
          "SPARQL updates require a branch selector, got: " + selector.getClass().getSimpleName());
    }

    // Create command
    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default",  // TODO: support multiple datasets
        branch.name(),
        update,
        author,
        message
    );

    // Execute command
    CommitCreatedEvent event = sparqlUpdateCommandHandler.handle(command);

    // Return result
    return new UpdateResult(
        event.commitId(),
        event.timestamp(),
        event.author(),
        event.message()
    );
  }

  /**
   * Converts API selector to internal VersionSelector.
   */
  private VersionSelector convertSelector(SparqlSelector selector) {
    return switch (selector) {
      case SparqlSelector.Branch b -> new VersionSelector.Branch(b.name());
      case SparqlSelector.Commit c -> new VersionSelector.Commit(c.commitId());
      case SparqlSelector.Timestamp t -> new VersionSelector.AsOf(t.instant());
    };
  }

  /**
   * Resolves selector to DatasetRef.
   */
  private DatasetRef resolveSelector(VersionSelector selector) {
    CommitId commitId = selectorResolutionService.resolve(selector, "default");
    return new DatasetRef("default", commitId.value());
  }

  // Inner classes for results

  private record SelectResult(ResultSet resultSet) implements SparqlQueryResult {
    @Override
    public ResultType getResultType() {
      return ResultType.SELECT;
    }

    @Override
    public Optional<ResultSet> getResultSet() {
      return Optional.of(resultSet);
    }

    @Override
    public Optional<Model> getModel() {
      return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean() {
      return Optional.empty();
    }
  }

  private record GraphResult(Model model) implements SparqlQueryResult {
    @Override
    public ResultType getResultType() {
      return ResultType.GRAPH;
    }

    @Override
    public Optional<ResultSet> getResultSet() {
      return Optional.empty();
    }

    @Override
    public Optional<Model> getModel() {
      return Optional.of(model);
    }

    @Override
    public Optional<Boolean> getBoolean() {
      return Optional.empty();
    }
  }

  private record BooleanResult(boolean value) implements SparqlQueryResult {
    @Override
    public ResultType getResultType() {
      return ResultType.BOOLEAN;
    }

    @Override
    public Optional<ResultSet> getResultSet() {
      return Optional.empty();
    }

    @Override
    public Optional<Model> getModel() {
      return Optional.empty();
    }

    @Override
    public Optional<Boolean> getBoolean() {
      return Optional.of(value);
    }
  }

  private record UpdateResult(
      String commitId,
      Instant timestamp,
      String author,
      String message) implements SparqlUpdateResult {

    @Override
    public String getCommitId() {
      return commitId;
    }

    @Override
    public Instant getTimestamp() {
      return timestamp;
    }

    @Override
    public String getAuthor() {
      return author;
    }

    @Override
    public String getMessage() {
      return message;
    }
  }
}
```

---

### Step 4: Add Tests

**Test file:** `src/test/java/org/chucc/vcserver/api/SparqlProtocolApiTest.java`

```java
@SpringBootTest
@ActiveProfiles("test")
class SparqlProtocolApiTest {

  @Autowired
  private SparqlProtocolApi api;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Test
  void query_selectQuery_shouldReturnResultSet() {
    // Arrange
    setupTestData();

    String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";

    // Act
    SparqlQueryResult result = api.query(query, SparqlSelector.branch("main"));

    // Assert
    assertThat(result.isResultSet()).isTrue();
    assertThat(result.getResultSet()).isPresent();

    ResultSet rs = result.getResultSet().get();
    assertThat(rs.hasNext()).isTrue();
  }

  @Test
  void query_askQuery_shouldReturnBoolean() {
    setupTestData();

    String query = "ASK WHERE { ?s ?p ?o }";

    SparqlQueryResult result = api.query(query, SparqlSelector.branch("main"));

    assertThat(result.isBoolean()).isTrue();
    assertThat(result.getBoolean()).isPresent();
  }

  @Test
  void update_insertData_shouldCreateCommit() {
    setupTestData();

    String update = "INSERT DATA { <http://example.org/s> <http://example.org/p> <http://example.org/o> }";

    SparqlUpdateResult result = api.update(
        update,
        SparqlSelector.branch("main"),
        "alice",
        "Test insert"
    );

    assertThat(result.getCommitId()).isNotNull();
    assertThat(result.getAuthor()).isEqualTo("alice");
    assertThat(result.getMessage()).isEqualTo("Test insert");
  }

  private void setupTestData() {
    // Create initial dataset with commits
    // ...
  }
}
```

---

## Usage Examples

**Example 1: Execute SELECT query**
```java
@Autowired
private SparqlProtocolApi api;

public void queryData() {
  SparqlQueryResult result = api.query(
      "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object } LIMIT 100",
      SparqlSelector.branch("main")
  );

  if (result.isResultSet()) {
    ResultSet rs = result.getResultSet().get();
    while (rs.hasNext()) {
      QuerySolution solution = rs.next();
      RDFNode subject = solution.get("subject");
      RDFNode predicate = solution.get("predicate");
      RDFNode object = solution.get("object");
      System.out.println(subject + " " + predicate + " " + object);
    }
  }
}
```

**Example 2: Execute SPARQL update**
```java
public String addTriples() {
  SparqlUpdateResult result = api.update(
      "INSERT DATA { <http://example.org/person1> <http://xmlns.com/foaf/0.1/name> \"Alice\" }",
      SparqlSelector.branch("main"),
      "alice@example.com",
      "Add person data"
  );

  return result.getCommitId();  // For subsequent queries
}
```

**Example 3: Time-travel query**
```java
public void queryHistoricalData() {
  Instant yesterday = Instant.now().minus(Duration.ofDays(1));

  SparqlQueryResult result = api.query(
      "SELECT * WHERE { ?s ?p ?o }",
      SparqlSelector.asOf(yesterday)
  );

  // Process results as of yesterday
}
```

---

## Success Criteria

- [ ] `SparqlProtocolApi` interface defined
- [ ] Implementation delegates to existing command handlers/services
- [ ] SELECT, CONSTRUCT, DESCRIBE, ASK queries supported
- [ ] SPARQL updates supported
- [ ] Branch, commit, and timestamp selectors work
- [ ] Result types properly encapsulated
- [ ] All tests pass
- [ ] Javadoc documentation complete
- [ ] Usage examples in tests

---

## Future Enhancements

- **Streaming results:** Support large result sets without loading all in memory
- **Async API:** Return `CompletableFuture<SparqlQueryResult>`
- **Multiple datasets:** Support dataset parameter
- **Query timeout:** Add timeout parameter
- **Explain API:** Return query execution plan
