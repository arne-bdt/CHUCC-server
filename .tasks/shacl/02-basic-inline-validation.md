# Task 2: Basic Inline Validation (Fuseki Compatibility)

**Phase:** 1 (Core Validation - MVP)
**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Dependencies:** Task 1 (Core Infrastructure)

---

## Overview

This task implements basic SHACL validation with inline shapes, providing compatibility with Apache Jena Fuseki's SHACL endpoint.

**Features:**
- Accept shapes graph as inline RDF content in multiple formats
- Format detection from `shapes.format` field (turtle, jsonld, rdfxml, ntriples, n3)
- Validate local data graphs (default, named, or union)
- Return validation report in requested RDF format
- Content negotiation for response format
- Integration with Apache Jena SHACL library

**Goal:** Quickest path to a working SHACL validation endpoint.

---

## Current State

**Existing (from Task 1):**
- ✅ DTOs (ValidationRequest, ValidationResponse, GraphReference with format field, DataReference)
- ✅ Exceptions (ShaclValidationException, InvalidShapesException)
- ✅ Configuration (ShaclValidationProperties)
- ✅ Controller skeleton (ShaclValidationController)

**Missing:**
- ❌ ShaclValidationEngine (Apache Jena SHACL integration)
- ❌ ShaclValidationService (orchestration with format detection)
- ❌ Content negotiation for RDF formats
- ❌ Integration tests for inline validation

---

## Requirements

### API Specification

**Endpoint:** `POST /{dataset}/shacl`

**Request Example (Inline Shapes - Turtle):**
```bash
curl -X POST http://localhost:8080/mydata/shacl \
  -H "Content-Type: application/json" \
  -H "Accept: text/turtle" \
  -d '{
    "shapes": {
      "source": "inline",
      "format": "turtle",
      "data": "@prefix sh: <http://www.w3.org/ns/shacl#> ..."
    },
    "data": {
      "source": "local",
      "dataset": "mydata",
      "graphs": ["default"]
    }
  }'
```

**Request Example (Inline Shapes - JSON-LD):**
```bash
curl -X POST http://localhost:8080/mydata/shacl \
  -H "Content-Type: application/json" \
  -d '{
    "shapes": {
      "source": "inline",
      "format": "jsonld",
      "data": "{\"@context\": ..., \"@type\": \"sh:NodeShape\", ...}"
    },
    "data": {
      "source": "local",
      "dataset": "mydata",
      "graphs": ["default"]
    }
  }'
```

**Response (200 OK):**
```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .

[] a sh:ValidationReport ;
   sh:conforms true .
```

### Supported RDF Formats

**Input (shapes.format):**
- `turtle` (default) - Turtle syntax
- `jsonld` - JSON-LD
- `rdfxml` - RDF/XML
- `ntriples` - N-Triples
- `n3` - Notation3

**Output (Accept header):**
- `text/turtle` (default)
- `application/ld+json`
- `application/rdf+xml`
- `application/n-triples`

---

## Implementation Steps

### Step 1: Create ShaclValidationEngine

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationEngine.java`

```java
package org.chucc.vcserver.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.chucc.vcserver.exception.InvalidShapesException;
import org.chucc.vcserver.exception.ShaclValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Low-level engine for SHACL validation using Apache Jena.
 *
 * <p>This service wraps the Apache Jena SHACL library and provides
 * error handling for validation operations.</p>
 */
@Service
public class ShaclValidationEngine {

  private static final Logger logger = LoggerFactory.getLogger(ShaclValidationEngine.class);

  /**
   * Validate a data graph against a shapes graph.
   *
   * @param shapesGraph SHACL shapes graph
   * @param dataGraph data graph to validate
   * @return validation report
   * @throws InvalidShapesException if shapes graph is invalid
   * @throws ShaclValidationException if validation fails
   */
  public ValidationReport validate(Graph shapesGraph, Graph dataGraph) {
    logger.debug("Starting SHACL validation");

    try {
      // Parse shapes graph
      Shapes shapes = Shapes.parse(shapesGraph);

      // Validate data graph
      ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);

      logger.debug("Validation completed: conforms={}", report.conforms());
      return report;

    } catch (org.apache.jena.shacl.parser.ShaclParseException e) {
      logger.error("Invalid SHACL shapes graph: {}", e.getMessage());
      throw new InvalidShapesException(
          "Invalid SHACL shapes graph: " + e.getMessage(), e
      );

    } catch (Exception e) {
      logger.error("SHACL validation failed: {}", e.getMessage(), e);
      throw new ShaclValidationException(
          "SHACL validation failed: " + e.getMessage(), e
      );
    }
  }
}
```

### Step 2: Create ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

```java
package org.chucc.vcserver.service;

import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.config.ShaclValidationProperties;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.chucc.vcserver.exception.DatasetNotFoundException;
import org.chucc.vcserver.exception.InvalidGraphReferenceException;
import org.chucc.vcserver.repository.DatasetGraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;

/**
 * High-level service for SHACL validation operations.
 *
 * <p>Orchestrates validation workflow:</p>
 * <ol>
 *   <li>Resolve shapes graph (inline, local, or remote)</li>
 *   <li>Resolve data graph(s)</li>
 *   <li>Perform validation via ShaclValidationEngine</li>
 *   <li>Return validation report</li>
 * </ol>
 */
@Service
public class ShaclValidationService {

  private static final Logger logger = LoggerFactory.getLogger(ShaclValidationService.class);

  private final ShaclValidationEngine validationEngine;
  private final DatasetGraphRepository datasetGraphRepository;
  private final ShaclValidationProperties properties;

  /**
   * Construct ShaclValidationService.
   *
   * @param validationEngine SHACL validation engine
   * @param datasetGraphRepository dataset graph repository
   * @param properties validation configuration properties
   */
  public ShaclValidationService(
      ShaclValidationEngine validationEngine,
      DatasetGraphRepository datasetGraphRepository,
      ShaclValidationProperties properties
  ) {
    this.validationEngine = validationEngine;
    this.datasetGraphRepository = datasetGraphRepository;
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
    Graph shapesGraph = resolveShapesGraph(request);

    // Step 2: Resolve data graph
    Graph dataGraph = resolveDataGraph(dataset, request);

    // Step 3: Validate
    ValidationReport report = validationEngine.validate(shapesGraph, dataGraph);

    logger.info("SHACL validation completed: conforms={}", report.conforms());
    return report;
  }

  /**
   * Resolve shapes graph from request.
   *
   * @param request validation request
   * @return shapes graph
   */
  private Graph resolveShapesGraph(ValidationRequest request) {
    var shapes = request.shapes();

    if (!"inline".equals(shapes.source())) {
      throw new InvalidGraphReferenceException(
          "Only inline shapes supported in this task (Task 2). " +
          "Local and remote shapes will be implemented in Task 3."
      );
    }

    if (shapes.data() == null || shapes.data().isBlank()) {
      throw new InvalidGraphReferenceException("shapes.data is required for inline source");
    }

    // Check size limit
    if (shapes.data().length() > properties.maxShapesSize()) {
      throw new InvalidGraphReferenceException(
          "Shapes data exceeds maximum size: " + properties.maxShapesSize() + " bytes"
      );
    }

    // Parse inline RDF with format detection
    Lang lang = parseLang(shapes.format());

    try {
      Graph graph = org.apache.jena.graph.Factory.createDefaultGraph();
      RDFDataMgr.read(graph, new StringReader(shapes.data()), null, lang);

      logger.debug("Parsed inline shapes graph: {} triples (format: {})",
          graph.size(), shapes.format());
      return graph;

    } catch (Exception e) {
      logger.error("Failed to parse inline shapes: {}", e.getMessage());
      throw new InvalidGraphReferenceException(
          "Failed to parse inline shapes (" + shapes.format() + "): " + e.getMessage()
      );
    }
  }

  /**
   * Resolve data graph from request.
   *
   * @param dataset dataset name
   * @param request validation request
   * @return data graph
   */
  private Graph resolveDataGraph(String dataset, ValidationRequest request) {
    var data = request.data();

    if (!"local".equals(data.source())) {
      throw new InvalidGraphReferenceException(
          "Only local data source supported in this task (Task 2). " +
          "Remote data will be implemented in Task 7."
      );
    }

    // Get dataset graph
    DatasetGraph dsg = datasetGraphRepository.getDatasetGraph(dataset)
        .orElseThrow(() -> new DatasetNotFoundException(dataset));

    // Handle single graph case (Task 2 limitation)
    if (data.graphs().size() != 1) {
      throw new InvalidGraphReferenceException(
          "Only single graph validation supported in this task (Task 2). " +
          "Multiple graphs (union mode) will be implemented in Task 4."
      );
    }

    String graphName = data.graphs().get(0);

    Graph graph;
    if ("default".equals(graphName)) {
      graph = dsg.getDefaultGraph();
    } else if ("union".equals(graphName)) {
      graph = dsg.getUnionGraph();
    } else {
      graph = dsg.getGraph(org.apache.jena.graph.NodeFactory.createURI(graphName));
    }

    if (graph == null || graph.isEmpty()) {
      logger.warn("Data graph '{}' is empty or does not exist", graphName);
    }

    logger.debug("Resolved data graph '{}': {} triples", graphName,
        graph != null ? graph.size() : 0);
    return graph;
  }

  /**
   * Parse Lang from format string.
   *
   * @param format RDF format string (turtle, jsonld, rdfxml, ntriples, n3)
   * @return Jena Lang constant
   */
  private Lang parseLang(String format) {
    return switch (format) {
      case "jsonld" -> RDFLanguages.JSONLD;
      case "rdfxml" -> RDFLanguages.RDFXML;
      case "ntriples" -> RDFLanguages.NTRIPLES;
      case "n3" -> RDFLanguages.N3;
      default -> RDFLanguages.TURTLE;  // Default and "turtle" both use Turtle
    };
  }
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java`

Replace the `validateShacl` method:

```java
package org.chucc.vcserver.controller;

import jakarta.validation.Valid;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shacl.ValidationReport;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.chucc.vcserver.service.ShaclValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.StringWriter;

/**
 * Controller for SHACL validation operations.
 *
 * <p>Implements the SHACL Validation Protocol, enabling validation of RDF data
 * against SHACL shapes with flexible source options (inline, local datasets,
 * remote endpoints) and result persistence.</p>
 */
@RestController
@RequestMapping("/{dataset}/shacl")
public class ShaclValidationController {

  private static final Logger logger = LoggerFactory.getLogger(ShaclValidationController.class);

  private final ShaclValidationService validationService;

  /**
   * Construct ShaclValidationController.
   *
   * @param validationService SHACL validation service
   */
  public ShaclValidationController(ShaclValidationService validationService) {
    this.validationService = validationService;
  }

  /**
   * Validate data graphs against a shapes graph.
   *
   * <p>This implementation (Task 2) supports:</p>
   * <ul>
   *   <li>Inline shapes (all RDF formats: turtle, jsonld, rdfxml, ntriples, n3)</li>
   *   <li>Local data graphs (single graph only)</li>
   *   <li>Content negotiation for response format</li>
   * </ul>
   *
   * <p>Limitations (to be addressed in later tasks):</p>
   * <ul>
   *   <li>No local/remote shapes graph references (Task 3)</li>
   *   <li>No union graph validation (Task 4)</li>
   *   <li>No result storage (Task 5)</li>
   *   <li>No version control selectors (Task 6)</li>
   *   <li>No remote endpoints (Task 7-8)</li>
   * </ul>
   *
   * @param dataset dataset name (path variable)
   * @param request validation request with shapes, data, options, and results config
   * @param accept Accept header for response format
   * @return validation report in requested RDF format (200 OK)
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> validateShacl(
      @PathVariable String dataset,
      @Valid @RequestBody ValidationRequest request,
      @RequestHeader(value = "Accept", defaultValue = "text/turtle") String accept
  ) {
    logger.info("SHACL validation request for dataset: {}", dataset);

    // Perform validation
    ValidationReport report = validationService.validate(dataset, request);

    // Serialize report in requested format
    String reportContent = serializeReport(report, accept);

    // Determine Content-Type from Accept header
    String contentType = accept.contains("json") ? "application/ld+json" :
                         accept.contains("xml") ? "application/rdf+xml" :
                         accept.contains("n-triples") ? "application/n-triples" :
                         "text/turtle";

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(reportContent);
  }

  /**
   * Serialize validation report to RDF format.
   *
   * @param report validation report
   * @param accept Accept header value
   * @return serialized report
   */
  private String serializeReport(ValidationReport report, String accept) {
    Lang lang;

    if (accept.contains("json")) {
      lang = RDFLanguages.JSONLD;
    } else if (accept.contains("xml")) {
      lang = RDFLanguages.RDFXML;
    } else if (accept.contains("n-triples")) {
      lang = RDFLanguages.NTRIPLES;
    } else {
      lang = RDFLanguages.TURTLE;  // Default
    }

    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, report.getModel(), lang);
    return writer.toString();
  }
}
```

### Step 4: Add Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ShaclValidationIT.java`

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
 * Integration tests for SHACL Validation Protocol - Basic Inline Validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ShaclValidationIT extends ITFixture {

  private static final String PERSON_SHAPES_TURTLE = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .
      @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
              sh:path ex:name ;
              sh:datatype xsd:string ;
              sh:minCount 1 ;
          ] ;
          sh:property [
              sh:path ex:age ;
              sh:datatype xsd:integer ;
              sh:minInclusive 0 ;
          ] .
      """;

  private static final String PERSON_SHAPES_JSONLD = """
      {
        "@context": {
          "sh": "http://www.w3.org/ns/shacl#",
          "ex": "http://example.org/",
          "xsd": "http://www.w3.org/2001/XMLSchema#"
        },
        "@id": "ex:PersonShape",
        "@type": "sh:NodeShape",
        "sh:targetClass": {"@id": "ex:Person"},
        "sh:property": [
          {
            "sh:path": {"@id": "ex:name"},
            "sh:datatype": {"@id": "xsd:string"},
            "sh:minCount": 1
          },
          {
            "sh:path": {"@id": "ex:age"},
            "sh:datatype": {"@id": "xsd:integer"},
            "sh:minInclusive": 0
          }
        ]
      }
      """;

  @BeforeEach
  void setUp() {
    // Add valid data to default graph
    String patch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Alice> <http://example.org/name> "Alice" .
        A <http://example.org/Alice> <http://example.org/age> "30"^^<http://www.w3.org/2001/XMLSchema#integer> .
        TC .
        """;

    applyPatchToDataset(getDatasetName(), patch);
  }

  @Test
  void validate_withInlineShapesTurtle_conformingData_shouldReturnConformsTrue() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, PERSON_SHAPES_TURTLE, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "text/turtle");

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms true");
  }

  @Test
  void validate_withInlineShapesJsonLd_conformingData_shouldReturnConformsTrue() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "jsonld", null, null, null, PERSON_SHAPES_JSONLD, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms true");
  }

  @Test
  void validate_withInlineShapes_nonConformingData_shouldReturnConformsFalse() {
    // Arrange
    // Add invalid data (missing name, invalid age type)
    String invalidPatch = """
        TX .
        A <http://example.org/Bob> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Bob> <http://example.org/age> "thirty" .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), invalidPatch);

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, PERSON_SHAPES_TURTLE, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "text/turtle");

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms false")
        .contains("sh:result");
  }

  @Test
  void validate_withInvalidShapes_shouldReturn422() {
    // Arrange
    String invalidShapes = "@prefix invalid syntax";

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, invalidShapes, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody())
        .contains("invalid_graph_reference");  // Parse error caught early
  }

  @Test
  void validate_withJsonLdResponse_shouldReturnJsonLd() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, PERSON_SHAPES_TURTLE, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "application/ld+json");

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/ld+json");
    assertThat(response.getBody())
        .contains("@context")
        .contains("ValidationReport");
  }

  @Test
  void validate_withDefaultFormat_shouldUseTurtle() {
    // Arrange (format field omitted, should default to turtle)
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", null, null, null, null, PERSON_SHAPES_TURTLE, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms");
  }
}
```

---

## Testing Strategy

### Integration Tests
1. **Conforming Data (Turtle)** - Shapes + valid data → `sh:conforms true`
2. **Conforming Data (JSON-LD)** - Shapes in JSON-LD format → `sh:conforms true`
3. **Non-Conforming Data** - Shapes + invalid data → `sh:conforms false` with violations
4. **Invalid Shapes** - Malformed shapes → 422 with `invalid_graph_reference`
5. **Content Negotiation** - Accept: application/ld+json → JSON-LD response
6. **Default Format** - Omitted format field → Defaults to turtle

**Test Pattern:** Projector DISABLED (read-only operation, no CQRS)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationEngine.java`
2. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

**Total Files:** 2 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java` - Implement validation logic
2. `src/test/java/org/chucc/vcserver/integration/ShaclValidationIT.java` - Add comprehensive tests

**Total Files:** 2 modified files

---

## Success Criteria

- ✅ Inline shapes validation working (all formats: turtle, jsonld, rdfxml, ntriples, n3)
- ✅ Format detection from `shapes.format` field
- ✅ Local data graph resolution working
- ✅ Apache Jena SHACL integration correct
- ✅ Content negotiation (Turtle, JSON-LD, RDF/XML, N-Triples)
- ✅ Error handling (invalid shapes, missing data)
- ✅ 6 integration tests passing
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

# Run integration tests
mvn -q test -Dtest=ShaclValidationIT

# Full build
mvn -q clean install
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Commit** changes with conventional commit message
3. **Delete** this task file
4. **Update** `.tasks/shacl/README.md` (mark Task 2 as completed)
5. **Proceed** to Task 3: Local Graph Reference Resolution

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §3.2, §4, §5.1, §8
- **[Apache Jena SHACL](https://jena.apache.org/documentation/shacl/)** - Validation API
- **[Apache Jena RDF I/O](https://jena.apache.org/documentation/io/)** - RDF format support
- **[SHACL Specification](https://www.w3.org/TR/shacl/)** - Validation report format
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing patterns

---

**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Status:** Blocked by Task 1
