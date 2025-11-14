# Task 3: Local Graph Reference Resolution

**Phase:** 1 (Core Validation - MVP)
**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Dependencies:** Task 2 (Basic Inline Validation)

---

## Overview

This task implements your primary use case: using shapes from any graph in any local dataset.

**Features:**
- Resolve shapes from local datasets (same or different dataset)
- Support graph URIs, "default", and "union"
- Resolve data from local datasets (already working from Task 2)
- Cross-dataset validation (shapes from dataset A, data from dataset B)

**Example:**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schema-registry",
    "graph": "http://example.org/shapes/my-shape"
  },
  "data": {
    "source": "local",
    "dataset": "production-data",
    "graphs": ["union"]
  }
}
```

**Goal:** Enable cross-dataset validation workflows you described.

---

## Current State

**Existing (from Task 2):**
- ✅ Inline shapes resolution
- ✅ Local data graph resolution (single graph)
- ✅ ShaclValidationEngine
- ✅ ShaclValidationService (orchestration)

**Missing:**
- ❌ Local shapes graph resolution
- ❌ GraphReferenceResolver service (reusable component)
- ❌ Cross-dataset tests

---

## Requirements

### API Specification

**Request Example (Cross-Dataset):**
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
  }
}
```

**Request Example (Same Dataset):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "mydata",
    "graph": "http://example.org/shapes"
  },
  "data": {
    "source": "local",
    "dataset": "mydata",
    "graphs": ["http://example.org/data"]
  }
}
```

**Request Example (Union Graph):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "union"
  },
  "data": {
    "source": "local",
    "dataset": "data",
    "graphs": ["union"]
  }
}
```

---

## Implementation Steps

### Step 1: Create GraphReferenceResolver

**File:** `src/main/java/org/chucc/vcserver/service/GraphReferenceResolver.java`

```java
package org.chucc.vcserver.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.config.ShaclValidationProperties;
import org.chucc.vcserver.dto.shacl.GraphReference;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.GraphNotFoundException;
import org.chucc.vcserver.exception.InvalidGraphReferenceException;
import org.chucc.vcserver.repository.DatasetGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;

/**
 * Service for resolving graph references (inline, local, or remote).
 *
 * <p>Handles:</p>
 * <ul>
 *   <li>Inline RDF content parsing</li>
 *   <li>Local dataset graph resolution</li>
 *   <li>Remote SPARQL endpoint fetching (Task 9)</li>
 *   <li>Version control selectors (Task 8)</li>
 * </ul>
 */
@Service
public class GraphReferenceResolver {

  private static final Logger logger = LoggerFactory.getLogger(GraphReferenceResolver.class);

  private final DatasetGraphRepository datasetGraphRepository;
  private final ShaclValidationProperties properties;

  public GraphReferenceResolver(
      DatasetGraphRepository datasetGraphRepository,
      ShaclValidationProperties properties
  ) {
    this.datasetGraphRepository = datasetGraphRepository;
    this.properties = properties;
  }

  /**
   * Resolve a graph reference to an Apache Jena Graph.
   *
   * @param reference graph reference
   * @param context context for error messages ("shapes" or "data")
   * @param maxSize maximum allowed graph size in bytes
   * @return resolved graph
   * @throws InvalidGraphReferenceException if reference is invalid
   * @throws DatasetNotFoundException if dataset does not exist
   * @throws GraphNotFoundException if graph does not exist
   */
  public Graph resolve(GraphReference reference, String context, int maxSize) {
    String source = reference.source();

    logger.debug("Resolving {} graph reference: source={}", context, source);

    return switch (source) {
      case "inline" -> resolveInline(reference, maxSize);
      case "local" -> resolveLocal(reference);
      case "remote" -> throw new InvalidGraphReferenceException(
          "Remote graph references not yet implemented (Task 9)"
      );
      default -> throw new InvalidGraphReferenceException(
          "Invalid source: " + source + " (must be 'inline', 'local', or 'remote')"
      );
    };
  }

  /**
   * Resolve inline RDF content.
   *
   * @param reference graph reference
   * @param maxSize maximum size in bytes
   * @return parsed graph
   */
  private Graph resolveInline(GraphReference reference, int maxSize) {
    String data = reference.data();

    if (data == null || data.isBlank()) {
      throw new InvalidGraphReferenceException("data is required for inline source");
    }

    if (data.length() > maxSize) {
      throw new InvalidGraphReferenceException(
          "Inline data exceeds maximum size: " + maxSize + " bytes"
      );
    }

    try {
      Graph graph = org.apache.jena.graph.Factory.createDefaultGraph();
      Lang lang = RDFLanguages.TURTLE;  // Default to Turtle

      RDFDataMgr.read(graph, new StringReader(data), null, lang);

      logger.debug("Parsed inline graph: {} triples", graph.size());
      return graph;

    } catch (Exception e) {
      logger.error("Failed to parse inline RDF: {}", e.getMessage());
      throw new InvalidGraphReferenceException(
          "Failed to parse inline RDF: " + e.getMessage()
      );
    }
  }

  /**
   * Resolve local dataset graph.
   *
   * @param reference graph reference
   * @return graph from local dataset
   */
  private Graph resolveLocal(GraphReference reference) {
    String dataset = reference.dataset();
    String graphName = reference.graph();

    if (dataset == null || dataset.isBlank()) {
      throw new InvalidGraphReferenceException("dataset is required for local source");
    }

    if (graphName == null || graphName.isBlank()) {
      throw new InvalidGraphReferenceException("graph is required for local source");
    }

    // Version control selectors
    if (reference.branch() != null || reference.commit() != null || reference.asOf() != null) {
      throw new InvalidGraphReferenceException(
          "Version control selectors not yet implemented (Task 8)"
      );
    }

    // Get dataset graph
    DatasetGraph dsg = datasetGraphRepository.getDatasetGraph(dataset)
        .orElseThrow(() -> new DatasetNotFoundException(dataset));

    // Resolve graph
    Graph graph;
    if ("default".equals(graphName)) {
      graph = dsg.getDefaultGraph();
      logger.debug("Resolved default graph from dataset '{}': {} triples", dataset, graph.size());

    } else if ("union".equals(graphName)) {
      graph = dsg.getUnionGraph();
      logger.debug("Resolved union graph from dataset '{}': {} triples", dataset, graph.size());

    } else {
      // Named graph
      graph = dsg.getGraph(NodeFactory.createURI(graphName));

      if (graph == null || graph.isEmpty()) {
        throw new GraphNotFoundException(dataset, graphName);
      }

      logger.debug("Resolved named graph '{}' from dataset '{}': {} triples",
          graphName, dataset, graph.size());
    }

    return graph;
  }
}
```

### Step 2: Refactor ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Replace existing implementation:

```java
package org.chucc.vcserver.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.shacl.ValidationReport;
import org.chucc.vcserver.config.ShaclValidationProperties;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * High-level service for SHACL validation operations.
 */
@Service
public class ShaclValidationService {

  private static final Logger logger = LoggerFactory.getLogger(ShaclValidationService.class);

  private final ShaclValidationEngine validationEngine;
  private final GraphReferenceResolver graphResolver;
  private final ShaclValidationProperties properties;

  public ShaclValidationService(
      ShaclValidationEngine validationEngine,
      GraphReferenceResolver graphResolver,
      ShaclValidationProperties properties
  ) {
    this.validationEngine = validationEngine;
    this.graphResolver = graphResolver;
    this.properties = properties;
  }

  /**
   * Validate data graphs against shapes graph.
   *
   * @param dataset dataset name (for local data graphs)
   * @param request validation request
   * @return validation report
   */
  public ValidationReport validate(String dataset, ValidationRequest request) {
    logger.info("Starting SHACL validation for dataset: {}", dataset);

    // Step 1: Resolve shapes graph
    Graph shapesGraph = graphResolver.resolve(
        request.shapes(),
        "shapes",
        properties.maxShapesSize()
    );

    // Step 2: Resolve data graph (single graph only for now)
    Graph dataGraph = resolveDataGraph(dataset, request);

    // Step 3: Validate
    ValidationReport report = validationEngine.validate(shapesGraph, dataGraph);

    logger.info("SHACL validation completed: conforms={}", report.conforms());
    return report;
  }

  /**
   * Resolve data graph from request.
   *
   * <p>This method handles single graph resolution. Multiple graph modes
   * (separate, merged, dataset) will be implemented in Task 5.</p>
   *
   * @param dataset dataset name (from path variable)
   * @param request validation request
   * @return data graph
   */
  private Graph resolveDataGraph(String dataset, ValidationRequest request) {
    var data = request.data();

    // Ensure single graph
    if (data.graphs().size() != 1) {
      throw new InvalidGraphReferenceException(
          "Only single graph validation supported in this task (Task 3). " +
          "Multiple graphs will be implemented in Task 5."
      );
    }

    // Override dataset from path if data.dataset is provided
    String datasetToUse = (data.dataset() != null && !data.dataset().isBlank())
        ? data.dataset()
        : dataset;

    // Create a GraphReference for the single data graph
    GraphReference dataRef = new GraphReference(
        "local",
        datasetToUse,
        data.graphs().get(0),
        null,  // endpoint
        null,  // data
        data.branch(),
        data.commit(),
        data.asOf()
    );

    return graphResolver.resolve(dataRef, "data", properties.maxDataSize());
  }
}
```

### Step 3: Add GraphNotFoundException

**File:** `src/main/java/org/chucc/vcserver/exception/GraphNotFoundException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a named graph does not exist in a dataset.
 */
public class GraphNotFoundException extends VcServerException {

  public GraphNotFoundException(String dataset, String graph) {
    super(
        "Graph '" + graph + "' not found in dataset '" + dataset + "'",
        HttpStatus.NOT_FOUND,
        "data_graph_not_found"
    );
  }
}
```

### Step 4: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/CrossDatasetValidationIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.dto.shacl.DataReference;
import org.chucc.vcserver.dto.shacl.GraphReference;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for cross-dataset SHACL validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CrossDatasetValidationIT extends ITFixture {

  private static final String SHAPES_DATASET = "test-schemas";
  private static final String DATA_DATASET = "test-data";

  @BeforeEach
  void setUp() {
    // Create schemas dataset with shapes
    createDatasetViaApi(SHAPES_DATASET);

    String shapesPatch = """
        TX .
        A <http://example.org/PersonShape> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.w3.org/ns/shacl#NodeShape> <http://example.org/shapes> .
        A <http://example.org/PersonShape> <http://www.w3.org/ns/shacl#targetClass> <http://example.org/Person> <http://example.org/shapes> .
        TC .
        """;
    applyPatchToDataset(SHAPES_DATASET, shapesPatch);

    // Create data dataset with test data
    createDatasetViaApi(DATA_DATASET);

    String dataPatch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Alice> <http://example.org/name> "Alice" .
        TC .
        """;
    applyPatchToDataset(DATA_DATASET, dataPatch);
  }

  @Test
  void validate_crossDataset_shapesFromDatasetA_dataFromDatasetB() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", SHAPES_DATASET, "http://example.org/shapes", null, null, null, null, null),
        new DataReference("local", DATA_DATASET, List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "text/turtle");

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATA_DATASET + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms");
  }

  @Test
  void validate_localShapes_unionGraph() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", SHAPES_DATASET, "union", null, null, null, null, null),
        new DataReference("local", DATA_DATASET, List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATA_DATASET + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void validate_localShapes_missingDataset_shouldReturn404() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", "nonexistent", "default", null, null, null, null, null),
        new DataReference("local", DATA_DATASET, List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATA_DATASET + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("dataset_not_found");
  }

  @Test
  void validate_localShapes_missingGraph_shouldReturn404() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", SHAPES_DATASET, "http://nonexistent.org/graph", null, null, null, null, null),
        new DataReference("local", DATA_DATASET, List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATA_DATASET + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("data_graph_not_found");
  }
}
```

### Step 5: Add Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/GraphReferenceResolverTest.java`

```java
package org.chucc.vcserver.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.config.ShaclValidationProperties;
import org.chucc.vcserver.dto.shacl.GraphReference;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.GraphNotFoundException;
import org.chucc.vcserver.exception.InvalidGraphReferenceException;
import org.chucc.vcserver.repository.DatasetGraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GraphReferenceResolver.
 */
class GraphReferenceResolverTest {

  private GraphReferenceResolver resolver;
  private DatasetGraphRepository datasetGraphRepository;
  private ShaclValidationProperties properties;

  @BeforeEach
  void setUp() {
    datasetGraphRepository = mock(DatasetGraphRepository.class);
    properties = new ShaclValidationProperties(
        true, 10485760, 104857600, 60000, 10, null, null, null
    );

    resolver = new GraphReferenceResolver(datasetGraphRepository, properties);
  }

  @Test
  void resolve_inline_validTurtle_shouldParseSuccessfully() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:subject ex:predicate ex:object .";
    GraphReference ref = new GraphReference("inline", null, null, null, turtle, null, null, null);

    // Act
    Graph graph = resolver.resolve(ref, "shapes", 10000);

    // Assert
    assertThat(graph.size()).isEqualTo(1);
  }

  @Test
  void resolve_inline_exceedsMaxSize_shouldThrowException() {
    // Arrange
    String largeTurtle = "x".repeat(10001);
    GraphReference ref = new GraphReference("inline", null, null, null, largeTurtle, null, null, null);

    // Act & Assert
    assertThatThrownBy(() -> resolver.resolve(ref, "shapes", 10000))
        .isInstanceOf(InvalidGraphReferenceException.class)
        .hasMessageContaining("exceeds maximum size");
  }

  @Test
  void resolve_local_defaultGraph_shouldReturnDefaultGraph() {
    // Arrange
    DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
    dsg.getDefaultGraph().add(
        org.apache.jena.graph.NodeFactory.createURI("http://s"),
        org.apache.jena.graph.NodeFactory.createURI("http://p"),
        org.apache.jena.graph.NodeFactory.createURI("http://o")
    );

    when(datasetGraphRepository.getDatasetGraph("mydata"))
        .thenReturn(Optional.of(dsg));

    GraphReference ref = new GraphReference("local", "mydata", "default", null, null, null, null, null);

    // Act
    Graph graph = resolver.resolve(ref, "data", 100000);

    // Assert
    assertThat(graph.size()).isEqualTo(1);
  }

  @Test
  void resolve_local_namedGraph_shouldReturnNamedGraph() {
    // Arrange
    DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
    Graph namedGraph = dsg.getGraph(org.apache.jena.graph.NodeFactory.createURI("http://example.org/g"));
    namedGraph.add(
        org.apache.jena.graph.NodeFactory.createURI("http://s"),
        org.apache.jena.graph.NodeFactory.createURI("http://p"),
        org.apache.jena.graph.NodeFactory.createURI("http://o")
    );

    when(datasetGraphRepository.getDatasetGraph("mydata"))
        .thenReturn(Optional.of(dsg));

    GraphReference ref = new GraphReference("local", "mydata", "http://example.org/g", null, null, null, null, null);

    // Act
    Graph graph = resolver.resolve(ref, "data", 100000);

    // Assert
    assertThat(graph.size()).isEqualTo(1);
  }

  @Test
  void resolve_local_unionGraph_shouldReturnUnionGraph() {
    // Arrange
    DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
    dsg.getDefaultGraph().add(
        org.apache.jena.graph.NodeFactory.createURI("http://s1"),
        org.apache.jena.graph.NodeFactory.createURI("http://p1"),
        org.apache.jena.graph.NodeFactory.createURI("http://o1")
    );

    Graph namedGraph = dsg.getGraph(org.apache.jena.graph.NodeFactory.createURI("http://example.org/g"));
    namedGraph.add(
        org.apache.jena.graph.NodeFactory.createURI("http://s2"),
        org.apache.jena.graph.NodeFactory.createURI("http://p2"),
        org.apache.jena.graph.NodeFactory.createURI("http://o2")
    );

    when(datasetGraphRepository.getDatasetGraph("mydata"))
        .thenReturn(Optional.of(dsg));

    GraphReference ref = new GraphReference("local", "mydata", "union", null, null, null, null, null);

    // Act
    Graph graph = resolver.resolve(ref, "data", 100000);

    // Assert
    assertThat(graph.size()).isEqualTo(2);  // Both triples in union
  }

  @Test
  void resolve_local_missingDataset_shouldThrowException() {
    // Arrange
    when(datasetGraphRepository.getDatasetGraph("nonexistent"))
        .thenReturn(Optional.empty());

    GraphReference ref = new GraphReference("local", "nonexistent", "default", null, null, null, null, null);

    // Act & Assert
    assertThatThrownBy(() -> resolver.resolve(ref, "shapes", 100000))
        .isInstanceOf(DatasetNotFoundException.class)
        .hasMessageContaining("nonexistent");
  }

  @Test
  void resolve_local_emptyNamedGraph_shouldThrowException() {
    // Arrange
    DatasetGraph dsg = DatasetGraphFactory.createTxnMem();

    when(datasetGraphRepository.getDatasetGraph("mydata"))
        .thenReturn(Optional.of(dsg));

    GraphReference ref = new GraphReference("local", "mydata", "http://example.org/empty", null, null, null, null, null);

    // Act & Assert
    assertThatThrownBy(() -> resolver.resolve(ref, "shapes", 100000))
        .isInstanceOf(GraphNotFoundException.class)
        .hasMessageContaining("http://example.org/empty");
  }
}
```

---

## Testing Strategy

### Unit Tests
- GraphReferenceResolver: All source types, error cases, graph types

### Integration Tests
- Cross-dataset validation (shapes from dataset A, data from dataset B)
- Union graph resolution
- Missing dataset/graph error handling
- Default graph resolution

**Test Pattern:** Projector DISABLED (read-only operation)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/service/GraphReferenceResolver.java`
2. `src/main/java/org/chucc/vcserver/exception/GraphNotFoundException.java`
3. `src/test/java/org/chucc/vcserver/integration/CrossDatasetValidationIT.java`
4. `src/test/java/org/chucc/vcserver/service/GraphReferenceResolverTest.java`

**Total Files:** 4 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Use GraphReferenceResolver

**Total Files:** 1 modified file

---

## Success Criteria

- ✅ Local shapes graph resolution working
- ✅ Cross-dataset validation working
- ✅ Union graph support
- ✅ Default graph support
- ✅ Named graph support
- ✅ Error handling (missing dataset, missing graph)
- ✅ 5 integration tests passing
- ✅ 7 unit tests passing
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

# Run tests
mvn -q test -Dtest=CrossDatasetValidationIT
mvn -q test -Dtest=GraphReferenceResolverTest

# Full build
mvn -q clean install
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Invoke specialized agents:**
   - `@code-reviewer` for final review
   - `@test-isolation-validator` for test verification
3. **Commit** changes with conventional commit message
4. **Delete** this task file
5. **Update** `.tasks/shacl/README.md` (mark Task 3 as completed)
6. **Proceed** to Task 4: Cross-Dataset Validation (already working!) or Task 5: Validation Modes

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §2.2, §5.2
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing patterns
- **[Apache Jena Dataset](https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/sparql/core/DatasetGraph.html)** - DatasetGraph API

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Status:** Blocked by Task 2
