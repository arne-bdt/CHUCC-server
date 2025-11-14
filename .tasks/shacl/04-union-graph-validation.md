# Task 4: Union Graph Validation

**Phase:** 2 (Cross-Graph Validation)
**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Dependencies:** Tasks 1-3 (Core Infrastructure, Inline Validation, Local Graph References)

---

## Overview

This task implements union graph validation, enabling cross-graph constraint validation.

**Features:**
- Combine multiple graphs into Apache Jena union graph
- Validate cross-graph constraints (e.g., referential integrity across graphs)
- Support `validateGraphs: "union"` option
- Preserve individual graph validation with `validateGraphs: "separately"` (default)

**Use Case:** Validate referential integrity where Person entities in one graph reference Organization entities in another graph.

**Goal:** Enable cross-graph SHACL validation for linked data scenarios.

---

## Current State

**Existing (from Tasks 1-3):**
- ✅ Inline shapes validation working
- ✅ Local graph reference resolution working
- ✅ Single graph validation working
- ✅ ValidationOptions with `validateGraphs` field

**Missing:**
- ❌ Union graph creation from multiple data graphs
- ❌ Cross-graph validation logic
- ❌ Integration tests for union mode

---

## Requirements

### API Specification

**Endpoint:** `POST /{dataset}/shacl`

**Request Example (Union Mode):**
```json
{
  "shapes": {
    "source": "inline",
    "data": "@prefix sh: <http://www.w3.org/ns/shacl#> ..."
  },
  "data": {
    "source": "local",
    "dataset": "mydata",
    "graphs": [
      "http://example.org/persons",
      "http://example.org/organizations"
    ]
  },
  "options": {
    "validateGraphs": "union"
  }
}
```

**Response (200 OK):**
```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .

[] a sh:ValidationReport ;
   sh:conforms true .
```

### Validation Modes

**separately (default):**
- Validate each graph independently
- Most common use case
- Violations scoped to individual graphs

**union:**
- Combine all specified graphs into single union graph
- Validate cross-graph constraints
- Use case: Referential integrity across graphs

---

## Implementation Steps

### Step 1: Update ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Update the `resolveDataGraph` method to support union mode:

```java
/**
 * Resolve data graph from request.
 *
 * <p>Supports two validation modes:</p>
 * <ul>
 *   <li><b>separately</b> - Single graph (enforced in this method)</li>
 *   <li><b>union</b> - Multiple graphs combined into union</li>
 * </ul>
 *
 * @param dataset dataset name
 * @param request validation request
 * @return data graph (single or union)
 */
private Graph resolveDataGraph(String dataset, ValidationRequest request) {
  var data = request.data();

  if (!"local".equals(data.source())) {
    throw new InvalidGraphReferenceException(
        "Only local data source supported in this task (Task 4). " +
        "Remote data will be implemented in Task 7."
    );
  }

  // Get dataset graph
  DatasetGraph dsg = datasetGraphRepository.getDatasetGraph(dataset)
      .orElseThrow(() -> new DatasetNotFoundException(dataset));

  String validateMode = request.options().validateGraphs();

  // Union mode: Combine multiple graphs
  if ("union".equals(validateMode)) {
    return createUnionGraph(dsg, data.graphs());
  }

  // Separately mode: Single graph only
  if (data.graphs().size() != 1) {
    throw new InvalidGraphReferenceException(
        "Multiple graphs require validateGraphs='union'. " +
        "Use validateGraphs='separately' for single graph validation."
    );
  }

  return resolveSingleGraph(dsg, data.graphs().get(0));
}

/**
 * Create union graph from multiple graphs.
 *
 * @param dsg dataset graph
 * @param graphNames list of graph names to combine
 * @return union graph
 */
private Graph createUnionGraph(DatasetGraph dsg, List<String> graphNames) {
  if (graphNames.isEmpty()) {
    throw new InvalidGraphReferenceException("graphs list cannot be empty");
  }

  // Resolve all individual graphs
  List<Graph> graphs = new ArrayList<>();
  for (String graphName : graphNames) {
    Graph graph = resolveSingleGraph(dsg, graphName);
    if (graph != null && !graph.isEmpty()) {
      graphs.add(graph);
    } else {
      logger.warn("Skipping empty or non-existent graph: {}", graphName);
    }
  }

  if (graphs.isEmpty()) {
    throw new InvalidGraphReferenceException(
        "All specified graphs are empty or do not exist"
    );
  }

  // Create union using Apache Jena's Union class
  Graph unionGraph = graphs.get(0);
  for (int i = 1; i < graphs.size(); i++) {
    unionGraph = new org.apache.jena.graph.compose.Union(unionGraph, graphs.get(i));
  }

  logger.debug("Created union graph from {} graphs: {} total triples",
      graphs.size(), unionGraph.size());

  return unionGraph;
}

/**
 * Resolve single graph by name.
 *
 * @param dsg dataset graph
 * @param graphName graph name (URI, "default", or "union")
 * @return graph
 */
private Graph resolveSingleGraph(DatasetGraph dsg, String graphName) {
  Graph graph;

  if ("default".equals(graphName)) {
    graph = dsg.getDefaultGraph();
  } else if ("union".equals(graphName)) {
    // Use Apache Jena's built-in union graph (all named graphs)
    graph = dsg.getUnionGraph();
  } else {
    graph = dsg.getGraph(org.apache.jena.graph.NodeFactory.createURI(graphName));
  }

  if (graph == null) {
    logger.warn("Graph '{}' does not exist", graphName);
    return org.apache.jena.graph.Factory.createDefaultGraph();  // Empty graph
  }

  logger.debug("Resolved graph '{}': {} triples", graphName, graph.size());
  return graph;
}
```

Add imports at the top of the file:
```java
import org.apache.jena.graph.compose.Union;
import java.util.ArrayList;
import java.util.List;
```

---

## Testing Strategy

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/UnionGraphValidationIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.dto.shacl.DataReference;
import org.chucc.vcserver.dto.shacl.GraphReference;
import org.chucc.vcserver.dto.shacl.ValidationOptions;
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
 * Integration tests for SHACL union graph validation.
 *
 * <p>Tests cross-graph constraint validation by combining multiple graphs
 * into a union before validation.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class UnionGraphValidationIT extends ITFixture {

  // SHACL shape requiring cross-graph referential integrity
  private static final String CROSS_GRAPH_SHAPES = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .
      @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
              sh:path ex:worksFor ;
              sh:class ex:Organization ;
              sh:minCount 1 ;
          ] .
      """;

  @BeforeEach
  void setUp() {
    // Graph 1: Persons
    String personsGraph = "http://example.org/persons";
    String personsPatch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> <http://example.org/persons> .
        A <http://example.org/Alice> <http://example.org/worksFor> <http://example.org/ACME> <http://example.org/persons> .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), personsPatch);

    // Graph 2: Organizations
    String orgsGraph = "http://example.org/organizations";
    String orgsPatch = """
        TX .
        A <http://example.org/ACME> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Organization> <http://example.org/organizations> .
        A <http://example.org/ACME> <http://example.org/name> "ACME Corp" <http://example.org/organizations> .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), orgsPatch);
  }

  @Test
  void validate_unionMode_crossGraphReferentialIntegrity_shouldConform() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, CROSS_GRAPH_SHAPES, null, null, null),
        new DataReference("local", getDatasetName(),
            List.of("http://example.org/persons", "http://example.org/organizations"),
            null, null, null, null),
        new ValidationOptions("union", null, null),
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
  void validate_unionMode_missingReferencedEntity_shouldNotConform() {
    // Arrange
    // Add person referencing non-existent organization
    String invalidPatch = """
        TX .
        A <http://example.org/Bob> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> <http://example.org/persons> .
        A <http://example.org/Bob> <http://example.org/worksFor> <http://example.org/NonExistent> <http://example.org/persons> .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), invalidPatch);

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, CROSS_GRAPH_SHAPES, null, null, null),
        new DataReference("local", getDatasetName(),
            List.of("http://example.org/persons", "http://example.org/organizations"),
            null, null, null, null),
        new ValidationOptions("union", null, null),
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
        .contains("sh:conforms false")
        .contains("sh:result");
  }

  @Test
  void validate_separatelyMode_multipleGraphs_shouldReturnError() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, CROSS_GRAPH_SHAPES, null, null, null),
        new DataReference("local", getDatasetName(),
            List.of("http://example.org/persons", "http://example.org/organizations"),
            null, null, null, null),
        new ValidationOptions("separately", null, null),  // Wrong mode
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("invalid_graph_reference")
        .contains("Multiple graphs require validateGraphs='union'");
  }

  @Test
  void validate_unionMode_singleGraph_shouldWork() {
    // Arrange (union mode with single graph should work)
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, CROSS_GRAPH_SHAPES, null, null, null),
        new DataReference("local", getDatasetName(),
            List.of("http://example.org/persons"),
            null, null, null, null),
        new ValidationOptions("union", null, null),
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
        .contains("sh:ValidationReport");
  }

  @Test
  void validate_unionMode_emptyGraphs_shouldReturnError() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, CROSS_GRAPH_SHAPES, null, null, null),
        new DataReference("local", getDatasetName(),
            List.of("http://example.org/nonexistent1", "http://example.org/nonexistent2"),
            null, null, null, null),
        new ValidationOptions("union", null, null),
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("invalid_graph_reference")
        .contains("All specified graphs are empty or do not exist");
  }
}
```

### Test Scenarios

1. **Cross-Graph Referential Integrity (Valid)** - Person→Organization link exists → conforms
2. **Cross-Graph Referential Integrity (Invalid)** - Person→Organization link broken → violation
3. **Separately Mode with Multiple Graphs** - Error message guides user to union mode
4. **Union Mode with Single Graph** - Should work (union of 1 graph)
5. **Union Mode with Empty Graphs** - Error: all graphs empty

**Test Pattern:** Projector DISABLED (read-only operation)

---

## Files to Create

1. `src/test/java/org/chucc/vcserver/integration/UnionGraphValidationIT.java`

**Total Files:** 1 new file

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Add union graph logic

**Total Files:** 1 modified file

---

## Success Criteria

- ✅ Union graph creation from multiple named graphs
- ✅ Cross-graph constraint validation working
- ✅ Separately mode enforces single graph
- ✅ Union mode accepts single or multiple graphs
- ✅ Empty graph detection
- ✅ 5 integration tests passing
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
mvn -q test -Dtest=UnionGraphValidationIT

# Full build
mvn -q clean install
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Commit** changes with conventional commit message
3. **Delete** this task file
4. **Update** `.tasks/shacl/README.md` (mark Task 4 as completed)
5. **Proceed** to Task 5: Result Storage via Graph Store Protocol

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §5.2 (validation modes)
- **[Apache Jena Union Graphs](https://jena.apache.org/documentation/notes/composition.html)** - Graph composition
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing patterns

---

**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Status:** Blocked by Tasks 1-3
