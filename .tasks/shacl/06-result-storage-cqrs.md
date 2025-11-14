# Task 6: Result Storage with CQRS

**Phase:** 3 (Result Persistence)
**Estimated Time:** 4-5 hours
**Complexity:** High
**Dependencies:** Tasks 1-3 (Core Infrastructure, Inline Validation, Local Graph References)

---

## Overview

This task implements your critical requirement: storing validation results for historical analysis.

**Features:**
- Store validation reports in any dataset/graph
- Create version control commits for stored results
- CQRS pattern (command → event → projector)
- Overwrite control (overwrite=true/false)
- Both return AND store results simultaneously

**Example:**
```json
{
  "shapes": {...},
  "data": {...},
  "results": {
    "return": true,
    "store": {
      "dataset": "validation-reports",
      "graph": "http://example.org/reports/2025-01-15",
      "overwrite": true
    }
  }
}
```

**Response:** `202 Accepted` with commit ID

**Goal:** Enable continuous validation workflows with historical trend analysis.

---

## Current State

**Existing:**
- ✅ Validation working (Tasks 1-3)
- ✅ CQRS infrastructure (CreateCommitCommandHandler, ReadModelProjector)
- ✅ Graph Store Protocol (PUT graph operations)

**Missing:**
- ❌ StoreValidationResultCommand
- ❌ ValidationResultStoredEvent
- ❌ StoreValidationResultCommandHandler
- ❌ ValidationResultProjector
- ❌ Integration with ShaclValidationService

---

## Requirements

### API Specification

**Request (With Storage):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/person"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"]
  },
  "results": {
    "return": true,
    "store": {
      "dataset": "qa-reports",
      "graph": "http://example.org/reports/2025-01-15",
      "overwrite": true
    }
  }
}
```

**Response (202 Accepted):**
```json
{
  "message": "Validation completed and result stored",
  "commitId": "01936d8f-1234-7890-abcd-ef1234567890",
  "conforms": false,
  "resultsGraph": "http://example.org/reports/2025-01-15"
}
```

**Headers:**
```
Location: /qa-reports/data?graph=http://example.org/reports/2025-01-15
ETag: "01936d8f-1234-7890-abcd-ef1234567890"
```

### Error Scenarios

- `409 Conflict` - Graph exists and overwrite=false
- `404 Not Found` - Target dataset does not exist
- `403 Forbidden` - No write permission on target dataset

---

## Implementation Steps

### Step 1: Create Command

**File:** `src/main/java/org/chucc/vcserver/command/StoreValidationResultCommand.java`

```java
package org.chucc.vcserver.command;

import org.apache.jena.rdf.model.Model;

/**
 * Command to store a SHACL validation result graph.
 *
 * @param dataset target dataset name
 * @param graph target graph URI
 * @param validationReport validation report model
 * @param overwrite whether to overwrite existing graph
 * @param author author of the commit
 * @param shapesSource description of shapes source (for commit message)
 * @param dataSource description of data source (for commit message)
 * @param conforms whether validation passed
 */
public record StoreValidationResultCommand(
    String dataset,
    String graph,
    Model validationReport,
    boolean overwrite,
    String author,
    String shapesSource,
    String dataSource,
    boolean conforms
) {
}
```

### Step 2: Create Event

**File:** `src/main/java/org/chucc/vcserver/event/ValidationResultStoredEvent.java`

```java
package org.chucc.vcserver.event;

import java.time.Instant;

/**
 * Event published when a SHACL validation result is stored.
 *
 * <p>This event triggers the projector to:</p>
 * <ul>
 *   <li>Store the validation report in the target graph</li>
 *   <li>Create a version control commit</li>
 * </ul>
 *
 * @param eventId unique event ID (UUIDv7)
 * @param timestamp when the event occurred
 * @param dataset target dataset name
 * @param graph target graph URI
 * @param commitId version control commit ID
 * @param reportTurtle validation report serialized as Turtle
 * @param author author of the commit
 * @param conforms whether validation passed
 */
public record ValidationResultStoredEvent(
    String eventId,
    Instant timestamp,
    String dataset,
    String graph,
    String commitId,
    String reportTurtle,
    String author,
    boolean conforms
) implements BaseEvent {

  @Override
  public String getDataset() {
    return dataset;
  }

  @Override
  public Instant getTimestamp() {
    return timestamp;
  }
}
```

### Step 3: Create Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/StoreValidationResultCommandHandler.java`

```java
package org.chucc.vcserver.command;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.event.ValidationResultStoredEvent;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.GraphConflictException;
import org.chucc.vcserver.publisher.EventPublisher;
import org.chucc.vcserver.repository.DatasetGraphRepository;
import org.chucc.vcserver.util.UuidV7Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Handles StoreValidationResultCommand by publishing ValidationResultStoredEvent.
 *
 * <p>CQRS Pattern:</p>
 * <ul>
 *   <li>Command side: Validate inputs, serialize report, publish event</li>
 *   <li>Event side: Projector stores graph and creates commit</li>
 * </ul>
 */
@Component
public class StoreValidationResultCommandHandler {

  private static final Logger logger = LoggerFactory.getLogger(
      StoreValidationResultCommandHandler.class
  );

  private final DatasetGraphRepository datasetGraphRepository;
  private final EventPublisher eventPublisher;

  public StoreValidationResultCommandHandler(
      DatasetGraphRepository datasetGraphRepository,
      EventPublisher eventPublisher
  ) {
    this.datasetGraphRepository = datasetGraphRepository;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Handle command to store validation result.
   *
   * @param command store validation result command
   * @return commit ID
   * @throws DatasetNotFoundException if target dataset does not exist
   * @throws GraphConflictException if graph exists and overwrite=false
   */
  public CompletableFuture<String> handle(StoreValidationResultCommand command) {
    logger.info("Storing validation result: dataset={}, graph={}",
        command.dataset(), command.graph());

    // Validate target dataset exists
    DatasetGraph dsg = datasetGraphRepository.getDatasetGraph(command.dataset())
        .orElseThrow(() -> new DatasetNotFoundException(command.dataset()));

    // Check for conflict (if overwrite=false)
    if (!command.overwrite()) {
      Graph existingGraph = dsg.getGraph(NodeFactory.createURI(command.graph()));
      if (existingGraph != null && !existingGraph.isEmpty()) {
        throw new GraphConflictException(command.dataset(), command.graph());
      }
    }

    // Serialize validation report to Turtle
    String reportTurtle = serializeReport(command.validationReport());

    // Generate commit ID
    String commitId = UuidV7Generator.generate();

    // Create event
    ValidationResultStoredEvent event = new ValidationResultStoredEvent(
        UuidV7Generator.generate(),
        Instant.now(),
        command.dataset(),
        command.graph(),
        commitId,
        reportTurtle,
        command.author(),
        command.conforms()
    );

    // Publish event
    logger.debug("Publishing ValidationResultStoredEvent: commitId={}", commitId);
    return eventPublisher.publish(event)
        .thenApply(v -> commitId);
  }

  /**
   * Serialize validation report to Turtle.
   *
   * @param report validation report model
   * @return Turtle string
   */
  private String serializeReport(Model report) {
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, report, Lang.TURTLE);
    return writer.toString();
  }
}
```

### Step 4: Create Projector

**File:** `src/main/java/org/chucc/vcserver/projection/ValidationResultProjector.java`

```java
package org.chucc.vcserver.projection;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.event.ValidationResultStoredEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.DatasetGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.Collections;

/**
 * Projector for SHACL validation result storage events.
 *
 * <p>Handles:</p>
 * <ul>
 *   <li>Storing validation report in target graph</li>
 *   <li>Creating version control commit</li>
 * </ul>
 */
@Component
public class ValidationResultProjector {

  private static final Logger logger = LoggerFactory.getLogger(ValidationResultProjector.class);

  private final DatasetGraphRepository datasetGraphRepository;
  private final CommitRepository commitRepository;

  public ValidationResultProjector(
      DatasetGraphRepository datasetGraphRepository,
      CommitRepository commitRepository
  ) {
    this.datasetGraphRepository = datasetGraphRepository;
    this.commitRepository = commitRepository;
  }

  /**
   * Handle ValidationResultStoredEvent.
   *
   * @param event validation result stored event
   */
  @KafkaListener(
      topics = "#{kafkaTopicConfig.getTopicForDataset('#{environment.getProperty(\"dataset.name\", \"default\")}')}",
      groupId = "validation-result-projector",
      containerFactory = "kafkaListenerContainerFactory"
  )
  public void handleValidationResultStored(ValidationResultStoredEvent event) {
    logger.info("Projecting ValidationResultStoredEvent: dataset={}, graph={}",
        event.dataset(), event.graph());

    try {
      // Get dataset graph
      DatasetGraph dsg = datasetGraphRepository.getDatasetGraph(event.dataset())
          .orElseThrow(() -> new IllegalStateException(
              "Dataset not found: " + event.dataset()
          ));

      // Parse validation report
      Graph reportGraph = org.apache.jena.graph.Factory.createDefaultGraph();
      RDFDataMgr.read(reportGraph, new StringReader(event.reportTurtle()), null, Lang.TURTLE);

      // Store in target graph (overwrite mode)
      Graph targetGraph = dsg.getGraph(NodeFactory.createURI(event.graph()));
      targetGraph.clear();
      reportGraph.find().forEachRemaining(targetGraph::add);

      logger.debug("Stored validation report: {} triples in graph '{}'",
          targetGraph.size(), event.graph());

      // Create commit
      String commitMessage = createCommitMessage(event);

      Commit commit = new Commit(
          event.commitId(),
          commitMessage,
          event.author(),
          event.timestamp(),
          Collections.emptyList(),  // No parents (detached commit)
          null,  // No patch (direct graph write)
          (long) reportGraph.size()  // Patch size = triple count
      );

      commitRepository.save(event.dataset(), commit, null);

      logger.info("Created commit for validation result: commitId={}", event.commitId());

    } catch (Exception e) {
      logger.error("Failed to project ValidationResultStoredEvent: {}", e.getMessage(), e);
      throw new RuntimeException("Projection failed", e);
    }
  }

  /**
   * Create commit message for validation result storage.
   *
   * @param event validation result stored event
   * @return commit message
   */
  private String createCommitMessage(ValidationResultStoredEvent event) {
    String status = event.conforms() ? "PASSED" : "FAILED";
    return String.format("SHACL validation %s: stored in graph '%s'", status, event.graph());
  }
}
```

### Step 5: Update ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Add method:

```java
/**
 * Validate and optionally store results.
 *
 * @param dataset dataset name (from path variable)
 * @param request validation request
 * @return validation report and optional commit ID
 */
public ValidationResult validateAndStore(String dataset, ValidationRequest request) {
  logger.info("SHACL validation with storage: dataset={}", dataset);

  // Step 1: Perform validation
  ValidationReport report = validate(dataset, request);

  // Step 2: Store results (if requested)
  String commitId = null;
  if (request.results() != null && request.results().store() != null) {
    commitId = storeResults(request, report).join();
  }

  return new ValidationResult(report, commitId);
}

/**
 * Store validation results.
 *
 * @param request validation request
 * @param report validation report
 * @return completable future with commit ID
 */
private CompletableFuture<String> storeResults(
    ValidationRequest request,
    ValidationReport report
) {
  var storeConfig = request.results().store();

  // Create command
  String author = "SHACL Validator";  // TODO: Get from request header in Task 7

  String shapesSource = describeShapesSource(request.shapes());
  String dataSource = describeDataSource(request.data());

  StoreValidationResultCommand command = new StoreValidationResultCommand(
      storeConfig.dataset(),
      storeConfig.graph(),
      report.getModel(),
      storeConfig.overwrite(),
      author,
      shapesSource,
      dataSource,
      report.conforms()
  );

  return storeValidationResultCommandHandler.handle(command);
}

/**
 * Describe shapes source for commit message.
 */
private String describeShapesSource(GraphReference shapes) {
  return switch (shapes.source()) {
    case "inline" -> "inline shapes";
    case "local" -> shapes.dataset() + ":" + shapes.graph();
    case "remote" -> shapes.endpoint() + ":" + shapes.graph();
    default -> "unknown";
  };
}

/**
 * Describe data source for commit message.
 */
private String describeDataSource(DataReference data) {
  return switch (data.source()) {
    case "local" -> data.dataset() + ":" + String.join(",", data.graphs());
    case "remote" -> data.endpoint() + ":" + String.join(",", data.graphs());
    default -> "unknown";
  };
}

/**
 * Validation result with optional commit ID.
 */
public record ValidationResult(ValidationReport report, String commitId) {
}
```

### Step 6: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java`

Replace `validateShacl` method:

```java
@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> validateShacl(
    @PathVariable String dataset,
    @Valid @RequestBody ValidationRequest request,
    @RequestHeader(value = "Accept", defaultValue = "text/turtle") String accept
) {
  logger.info("SHACL validation request for dataset: {}", dataset);

  // Check if storage is requested
  boolean shouldStore = request.results() != null &&
                        request.results().store() != null;

  if (shouldStore) {
    // Validate and store
    var result = validationService.validateAndStore(dataset, request);

    // Prepare response
    ValidationResponse response = new ValidationResponse(
        "Validation completed and result stored",
        result.commitId(),
        result.report().conforms(),
        request.results().store().graph()
    );

    // Return 202 Accepted (CQRS async pattern)
    String location = "/" + request.results().store().dataset() +
                      "/data?graph=" + request.results().store().graph();

    return ResponseEntity.accepted()
        .header("Location", location)
        .header("ETag", result.commitId())
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);

  } else {
    // Validation only (no storage)
    ValidationReport report = validationService.validate(dataset, request);
    String reportContent = serializeReport(report, accept);

    String contentType = accept.contains("json") ? "application/ld+json" :
                         accept.contains("xml") ? "application/rdf+xml" :
                         accept.contains("n-triples") ? "application/n-triples" :
                         "text/turtle";

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(reportContent);
  }
}
```

### Step 7: Add GraphConflictException

**File:** `src/main/java/org/chucc/vcserver/exception/GraphConflictException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to overwrite a graph without overwrite=true.
 */
public class GraphConflictException extends VcServerException {

  public GraphConflictException(String dataset, String graph) {
    super(
        "Graph '" + graph + "' already exists in dataset '" + dataset + "' (set overwrite=true to replace)",
        HttpStatus.CONFLICT,
        "graph_exists"
    );
  }
}
```

### Step 8: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ValidationResultPersistenceIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.dto.shacl.*;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for SHACL validation result persistence.
 *
 * <p>These tests enable the projector to verify async result storage.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // Enable projector!
class ValidationResultPersistenceIT extends ITFixture {

  private static final String SHAPES = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .

      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
              sh:path ex:name ;
              sh:datatype <http://www.w3.org/2001/XMLSchema#string> ;
              sh:minCount 1 ;
          ] .
      """;

  private static final String REPORTS_DATASET = "test-reports";

  @BeforeEach
  void setUp() {
    // Create reports dataset
    createDatasetViaApi(REPORTS_DATASET);

    // Add test data
    String dataPatch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Alice> <http://example.org/name> "Alice" .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), dataPatch);
  }

  @Test
  void validate_withStorage_shouldStoreReportAndCreateCommit() throws Exception {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", null, null, null, SHAPES, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        new ResultsConfig(
            true,
            new ResultsConfig.StoreConfig(
                REPORTS_DATASET,
                "http://example.org/reports/test-1",
                false
            )
        )
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<ValidationResponse> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        ValidationResponse.class
    );

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().commitId()).isNotNull();
    assertThat(response.getBody().conforms()).isTrue();

    // Assert projection (wait for async processing)
    String commitId = response.getBody().commitId();

    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify graph stored
          ResponseEntity<String> graphResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-1",
              String.class
          );
          assertThat(graphResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(graphResponse.getBody())
              .contains("sh:ValidationReport")
              .contains("sh:conforms");

          // Verify commit created
          var commitResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/version/commits/" + commitId,
              String.class
          );
          assertThat(commitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        });
  }

  @Test
  void validate_withStorage_overwriteFalse_graphExists_shouldReturn409() {
    // Arrange: Create existing graph
    String existingPatch = """
        TX .
        A <http://example.org/existing> <http://example.org/predicate> <http://example.org/object> <http://example.org/reports/test-2> .
        TC .
        """;
    applyPatchToDataset(REPORTS_DATASET, existingPatch);

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", null, null, null, SHAPES, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        new ResultsConfig(
            false,  // Don't return report
            new ResultsConfig.StoreConfig(
                REPORTS_DATASET,
                "http://example.org/reports/test-2",
                false  // overwrite=false
            )
        )
        );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody())
        .contains("graph_exists");
  }

  @Test
  void validate_withStorage_overwriteTrue_graphExists_shouldReplace() throws Exception {
    // Arrange: Create existing graph
    String existingPatch = """
        TX .
        A <http://example.org/existing> <http://example.org/predicate> <http://example.org/object> <http://example.org/reports/test-3> .
        TC .
        """;
    applyPatchToDataset(REPORTS_DATASET, existingPatch);

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", null, null, null, SHAPES, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        new ResultsConfig(
            false,
            new ResultsConfig.StoreConfig(
                REPORTS_DATASET,
                "http://example.org/reports/test-3",
                true  // overwrite=true
            )
        )
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<ValidationResponse> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        ValidationResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Verify graph replaced
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          ResponseEntity<String> graphResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-3",
              String.class
          );
          assertThat(graphResponse.getBody())
              .contains("sh:ValidationReport")
              .doesNotContain("http://example.org/existing");
        });
  }
}
```

---

## Testing Strategy

### Integration Tests (Projector ENABLED!)
1. **Store and Create Commit** - Verify async projection with `await()`
2. **Conflict Detection** - Graph exists, overwrite=false → 409
3. **Overwrite** - Graph exists, overwrite=true → Replace graph
4. **Missing Dataset** - Target dataset not found → 404

**Test Pattern:** Projector **ENABLED** via `@TestPropertySource`

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/command/StoreValidationResultCommand.java`
2. `src/main/java/org/chucc/vcserver/event/ValidationResultStoredEvent.java`
3. `src/main/java/org/chucc/vcserver/command/StoreValidationResultCommandHandler.java`
4. `src/main/java/org/chucc/vcserver/projection/ValidationResultProjector.java`
5. `src/main/java/org/chucc/vcserver/exception/GraphConflictException.java`
6. `src/test/java/org/chucc/vcserver/integration/ValidationResultPersistenceIT.java`

**Total Files:** 6 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Add validateAndStore()
2. `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java` - Handle storage

**Total Files:** 2 modified files

---

## Success Criteria

- ✅ CQRS pattern implemented correctly
- ✅ Validation report stored in target graph
- ✅ Version control commit created
- ✅ Overwrite control working
- ✅ Conflict detection (409 when graph exists)
- ✅ 3 integration tests passing (with projector enabled)
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Zero compiler warnings
- ✅ Full build passes: `mvn -q clean install`

---

## Verification Commands

```bash
# Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Run integration tests (projector enabled!)
mvn -q test -Dtest=ValidationResultPersistenceIT

# Full build
mvn -q clean install
```

---

## After Completion - Invoke Specialized Agents

```bash
@cqrs-compliance-checker  # Verify CQRS pattern
@test-isolation-validator # Verify projector enablement
@event-schema-evolution-checker # Verify event schema
@code-reviewer # Final review
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Invoke specialized agents** (see above)
3. **Commit** changes with conventional commit message
4. **Delete** this task file
5. **Update** `.tasks/shacl/README.md` (mark Task 6 as completed)
6. **Proceed** to Task 7: Version Control Integration for Results (author header, commit message customization)

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §3.2, §5.2, §6, §10
- **[CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)** - Command/Event pattern
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing with projector enabled
- **[Awaitility](https://www.awaitility.org/)** - Async testing library

---

**Estimated Time:** 4-5 hours
**Complexity:** High
**Status:** Blocked by Tasks 1-3
