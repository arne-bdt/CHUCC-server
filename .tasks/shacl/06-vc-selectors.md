# Task 6: Version Control Selectors

**Phase:** 3 (Version Control Integration)
**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Dependencies:** Tasks 1-5 (Core Infrastructure, Inline Validation, Local Graphs, Union Validation, Result Storage)

---

## Overview

This task implements version control selectors for historical validation.

**Features:**
- Validate shapes against historical states (branch/commit/asOf selectors)
- Validate data against historical states
- Integration with existing DatasetService.materializeCommit()
- Support for all existing selectors (already defined in GraphReference and DataReference DTOs)

**Use Cases:**
- "Is this data valid according to the schema that existed in March 2024?"
- "Does the current data conform to the shapes on the 'dev' branch?"
- "Validate data at commit X against shapes at commit Y"

**Goal:** Enable time-travel validation for compliance auditing and schema evolution testing.

---

## Current State

**Existing (from Tasks 1-5):**
- ✅ DTOs already have selector fields (branch, commit, asOf)
- ✅ Cross-field validation prevents selector conflicts
- ✅ DatasetService with materializeCommit() method
- ✅ BranchRepository for branch→commit resolution
- ✅ CommitRepository for commit metadata

**Missing:**
- ❌ Selector resolution logic in ShaclValidationService
- ❌ Integration with DatasetService for historical materialization
- ❌ Integration tests for historical validation

---

## Requirements

### API Specification

**Endpoint:** `POST /{dataset}/shacl`

**Request Example (Branch Selector):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/person",
    "branch": "production"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"],
    "branch": "staging"
  }
}
```

**Request Example (Commit Selector):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/person",
    "commit": "01936d8f-1234-7890-abcd-ef1234567890"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"]
  }
}
```

**Request Example (AsOf Selector):**
```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/person",
    "asOf": "2024-03-15T10:30:00Z"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"]
  }
}
```

**Response:** Same as normal validation (200 OK with ValidationReport)

---

## Implementation Steps

### Step 1: Update ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Add selector resolution methods:

```java
// Add to class fields:
@Autowired
private DatasetService datasetService;

@Autowired
private BranchRepository branchRepository;

/**
 * Resolve shapes graph from request (with selector support).
 *
 * @param request validation request
 * @return shapes graph
 */
private Graph resolveShapesGraph(ValidationRequest request) {
  var shapes = request.shapes();

  if ("inline".equals(shapes.source())) {
    return resolveInlineShapes(shapes);
  }

  if ("local".equals(shapes.source())) {
    return resolveLocalShapesGraph(shapes);
  }

  if ("remote".equals(shapes.source())) {
    throw new InvalidGraphReferenceException(
        "Remote shapes not yet implemented (Task 7)"
    );
  }

  throw new InvalidGraphReferenceException(
      "Invalid shapes source: " + shapes.source()
  );
}

/**
 * Resolve local shapes graph (with selector support).
 *
 * @param shapes shapes reference
 * @return shapes graph
 */
private Graph resolveLocalShapesGraph(GraphReference shapes) {
  // Resolve dataset at specific state (if selector provided)
  DatasetGraph dsg = resolveDatasetAtSelector(
      shapes.dataset(),
      shapes.branch(),
      shapes.commit(),
      shapes.asOf()
  );

  // Get specific graph from dataset
  Graph graph = resolveSingleGraph(dsg, shapes.graph());

  if (graph == null || graph.isEmpty()) {
    throw new InvalidGraphReferenceException(
        "Shapes graph '" + shapes.graph() + "' is empty or does not exist in dataset '" +
        shapes.dataset() + "'"
    );
  }

  return graph;
}

/**
 * Resolve data graph from request (with selector support).
 *
 * @param dataset default dataset name (from path variable)
 * @param request validation request
 * @return data graph (single or union)
 */
private Graph resolveDataGraph(String dataset, ValidationRequest request) {
  var data = request.data();

  if (!"local".equals(data.source())) {
    throw new InvalidGraphReferenceException(
        "Only local data source supported in this task. " +
        "Remote data will be implemented in Task 7."
    );
  }

  // Use explicit dataset or fall back to path variable
  String targetDataset = data.dataset() != null ? data.dataset() : dataset;

  // Resolve dataset at specific state (if selector provided)
  DatasetGraph dsg = resolveDatasetAtSelector(
      targetDataset,
      data.branch(),
      data.commit(),
      data.asOf()
  );

  // Handle union vs. separately mode
  String validateMode = request.options().validateGraphs();

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
 * Resolve dataset at specific version using selectors.
 *
 * <p>Selectors are mutually exclusive (enforced by DTO validation):</p>
 * <ul>
 *   <li><b>branch</b> - Resolve to branch HEAD commit</li>
 *   <li><b>commit</b> - Resolve to specific commit</li>
 *   <li><b>asOf</b> - Resolve to state at timestamp</li>
 *   <li><b>none</b> - Use current state</li>
 * </ul>
 *
 * @param dataset dataset name
 * @param branch branch name (optional)
 * @param commit commit ID (optional)
 * @param asOf RFC3339 timestamp (optional)
 * @return dataset graph at specified state
 */
private DatasetGraph resolveDatasetAtSelector(
    String dataset,
    String branch,
    String commit,
    String asOf
) {
  // Commit selector: Direct materialization
  if (commit != null && !commit.isBlank()) {
    logger.debug("Materializing dataset '{}' at commit: {}", dataset, commit);
    return datasetService.materializeCommit(dataset, commit);
  }

  // Branch selector: Resolve branch HEAD, then materialize
  if (branch != null && !branch.isBlank()) {
    String commitId = branchRepository
        .findByName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(dataset, branch))
        .commitId();

    logger.debug("Resolved branch '{}' to commit: {} (materializing)", branch, commitId);
    return datasetService.materializeCommit(dataset, commitId);
  }

  // AsOf selector: Materialize at timestamp
  if (asOf != null && !asOf.isBlank()) {
    Instant timestamp;
    try {
      timestamp = Instant.parse(asOf);
    } catch (Exception e) {
      throw new InvalidGraphReferenceException(
          "Invalid asOf timestamp format (expected RFC3339): " + asOf
      );
    }

    logger.debug("Materializing dataset '{}' at timestamp: {}", dataset, timestamp);
    return datasetService.materializeAtTimestamp(dataset, timestamp);
  }

  // No selector: Use current state (main branch HEAD)
  logger.debug("Using current state of dataset: {}", dataset);
  return datasetGraphRepository.getDatasetGraph(dataset)
      .orElseThrow(() -> new DatasetNotFoundException(dataset));
}
```

Add new exception if not already exists:

**File:** `src/main/java/org/chucc/vcserver/exception/BranchNotFoundException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a branch does not exist.
 */
public class BranchNotFoundException extends VcServerException {

  /**
   * Construct exception with dataset and branch name.
   *
   * @param dataset dataset name
   * @param branch branch name
   */
  public BranchNotFoundException(String dataset, String branch) {
    super(
        "Branch '" + branch + "' not found in dataset '" + dataset + "'",
        HttpStatus.NOT_FOUND,
        "branch_not_found"
    );
  }
}
```

Add imports to ShaclValidationService:
```java
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.repository.BranchRepository;
import java.time.Instant;
```

---

## Testing Strategy

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/HistoricalValidationIT.java`

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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SHACL validation with version control selectors.
 *
 * <p>Tests historical validation using branch, commit, and asOf selectors.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class HistoricalValidationIT extends ITFixture {

  private static final String SCHEMAS_DATASET = "test-schemas";

  private static final String V1_SHAPES = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .
      @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
              sh:path ex:name ;
              sh:datatype xsd:string ;
              sh:minCount 1 ;
          ] .
      """;

  private static final String V2_SHAPES = """
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
              sh:path ex:email ;
              sh:datatype xsd:string ;
              sh:minCount 1 ;
          ] .
      """;

  private String v1CommitId;
  private String v2CommitId;
  private Instant v1Timestamp;

  @BeforeEach
  void setUp() {
    // Create schemas dataset
    createDatasetViaApi(SCHEMAS_DATASET);

    // Store V1 shapes
    v1Timestamp = Instant.now();
    v1CommitId = storeGraphViaGSP(SCHEMAS_DATASET, "http://example.org/shapes", V1_SHAPES);

    // Wait a bit to ensure different timestamps
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    // Store V2 shapes (overwrites)
    v2CommitId = storeGraphViaGSP(SCHEMAS_DATASET, "http://example.org/shapes", V2_SHAPES);

    // Add test data (missing email field)
    String dataPatch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Alice> <http://example.org/name> "Alice" .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), dataPatch);
  }

  @Test
  void validate_withCommitSelector_shouldValidateAgainstHistoricalShapes() {
    // Arrange: Validate against V1 shapes (no email required)
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, null, v1CommitId, null),
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

    // Assert: Should conform (V1 doesn't require email)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms true");
  }

  @Test
  void validate_withoutSelector_shouldValidateAgainstCurrentShapes() {
    // Arrange: Validate against current shapes (V2, requires email)
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, null, null, null),
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

    // Assert: Should NOT conform (V2 requires email)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms false");
  }

  @Test
  void validate_withBranchSelector_shouldResolveToCommit() {
    // Arrange: main branch should point to V2
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, "main", null, null),
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

    // Assert: Should NOT conform (main branch has V2)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms false");
  }

  @Test
  void validate_withAsOfSelector_shouldMaterializeAtTimestamp() {
    // Arrange: asOf timestamp between V1 and V2
    String asOfTimestamp = v1Timestamp.plusSeconds(1).toString();

    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, null, null, asOfTimestamp),
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

    // Assert: Should conform (V1 was active at that timestamp)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms true");
  }

  @Test
  void validate_withInvalidBranch_shouldReturn404() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, "nonexistent", null, null),
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("branch_not_found");
  }

  @Test
  void validate_withInvalidAsOfFormat_shouldReturn400() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("local", null, SCHEMAS_DATASET, "http://example.org/shapes",
            null, null, null, null, "invalid-timestamp"),
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("invalid_graph_reference")
        .contains("Invalid asOf timestamp format");
  }

  /**
   * Helper method to store graph via Graph Store Protocol.
   *
   * @param dataset dataset name
   * @param graphUri graph URI
   * @param content RDF content (Turtle)
   * @return commit ID (from ETag)
   */
  private String storeGraphViaGSP(String dataset, String graphUri, String content) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/turtle"));

    HttpEntity<String> entity = new HttpEntity<>(content, headers);

    ResponseEntity<String> response = restTemplate.exchange(
        "/" + dataset + "/data?graph=" + graphUri,
        HttpMethod.PUT,
        entity,
        String.class
    );

    return response.getHeaders().getETag().replaceAll("\"", "");
  }
}
```

### Test Scenarios

1. **Commit Selector** - Validate against V1 shapes (no email required) → conforms
2. **Current State** - Validate against V2 shapes (email required) → does not conform
3. **Branch Selector** - main branch resolves to V2 → does not conform
4. **AsOf Selector** - Timestamp between V1 and V2 → V1 active → conforms
5. **Invalid Branch** - Nonexistent branch → 404 branch_not_found
6. **Invalid AsOf Format** - Malformed timestamp → 400 invalid_graph_reference

**Test Pattern:** Projector DISABLED (read-only operation)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/exception/BranchNotFoundException.java`
2. `src/test/java/org/chucc/vcserver/integration/HistoricalValidationIT.java`

**Total Files:** 2 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Add selector resolution logic

**Total Files:** 1 modified file

---

## Success Criteria

- ✅ Branch selector resolves to branch HEAD commit
- ✅ Commit selector materializes specific commit
- ✅ AsOf selector materializes dataset at timestamp
- ✅ No selector uses current state (main branch HEAD)
- ✅ Invalid branch returns 404
- ✅ Invalid asOf format returns 400
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
mvn -q test -Dtest=HistoricalValidationIT

# Full build
mvn -q clean install
```

---

## Use Cases Enabled

### Use Case 1: Schema Evolution Testing
**Scenario:** Test if current data conforms to old schema version

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/person",
    "commit": "01936d8f-old-schema"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"]
  }
}
```

### Use Case 2: Compliance Auditing
**Scenario:** "Was this data valid according to the regulations that existed on 2024-03-15?"

```json
{
  "shapes": {
    "source": "local",
    "dataset": "regulations",
    "graph": "http://example.org/gdpr-rules",
    "asOf": "2024-03-15T00:00:00Z"
  },
  "data": {
    "source": "local",
    "dataset": "customer-data",
    "graphs": ["default"],
    "asOf": "2024-03-15T23:59:59Z"
  }
}
```

### Use Case 3: Multi-Environment Validation
**Scenario:** Validate staging data against production schemas

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/shapes/product",
    "branch": "production"
  },
  "data": {
    "source": "local",
    "dataset": "products",
    "graphs": ["default"],
    "branch": "staging"
  }
}
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Commit** changes with conventional commit message
3. **Delete** this task file
4. **Update** `.tasks/shacl/README.md` (mark Task 6 as completed)
5. **Proceed** to Task 7: Remote Endpoint Client

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §3.2, §5.2 (selectors)
- **[DatasetService](../../src/main/java/org/chucc/vcserver/service/DatasetService.java)** - Materialization methods
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Version control integration

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Status:** Blocked by Tasks 1-5
