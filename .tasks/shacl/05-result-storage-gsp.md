# Task 5: Result Storage via Graph Store Protocol

**Phase:** 2 (Cross-Graph Validation & Storage)
**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Dependencies:** Tasks 1-4 (Core Infrastructure, Inline Validation, Local Graph References, Union Validation)

---

## Overview

This task implements your critical requirement: storing validation results for historical analysis.

**Key Architectural Decision:** SHACL validation results are just RDF graphs → Use existing Graph Store Protocol!

**Features:**
- Store validation reports using existing `PUT /data?graph=X` endpoint
- Reuse existing commit creation infrastructure (no new CQRS components)
- Support overwrite control via HTTP method (PUT vs POST)
- Extract commit ID from ETag header
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

**Goal:** Enable continuous validation workflows with historical trend analysis, WITHOUT creating new CQRS components.

---

## Current State

**Existing:**
- ✅ Validation working (Tasks 1-4)
- ✅ Graph Store Protocol (`PUT /data?graph=X`)
- ✅ CreateCommitCommandHandler (creates commits for graph updates)
- ✅ ReadModelProjector (projects events to repositories)

**Missing:**
- ❌ Integration between ShaclValidationService and Graph Store Protocol
- ❌ Internal HTTP client for calling GSP
- ❌ Result storage logic in controller

**NOT NEEDED (unlike original Task 6):**
- ✅ No StoreValidationResultCommand (reuse CreateCommitCommand)
- ✅ No ValidationResultStoredEvent (reuse CommitCreatedEvent)
- ✅ No ValidationResultProjector (reuse ReadModelProjector)

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

- `409 Conflict` - Graph exists and overwrite=false (from GSP)
- `404 Not Found` - Target dataset does not exist (from GSP)

---

## Implementation Steps

### Step 1: Add Internal HTTP Client Configuration

**File:** `src/main/java/org/chucc/vcserver/config/InternalHttpClientConfig.java`

```java
package org.chucc.vcserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for internal HTTP client.
 *
 * <p>Used for internal service-to-service calls (e.g., SHACL validation
 * storing results via Graph Store Protocol).</p>
 */
@Configuration
public class InternalHttpClientConfig {

  @Value("${server.port:8080}")
  private int serverPort;

  /**
   * Create RestTemplate for internal HTTP calls.
   *
   * @param builder rest template builder
   * @return configured rest template
   */
  @Bean(name = "internalRestTemplate")
  public RestTemplate internalRestTemplate(RestTemplateBuilder builder) {
    return builder
        .rootUri("http://localhost:" + serverPort)
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(30))
        .build();
  }
}
```

### Step 2: Update ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Add new method and helper methods:

```java
// Add to class fields:
@Autowired
@Qualifier("internalRestTemplate")
private RestTemplate internalRestTemplate;

@Value("${server.port:8080}")
private int serverPort;

/**
 * Validate and optionally store results via Graph Store Protocol.
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
    commitId = storeResultsViaGSP(request, report);
  }

  return new ValidationResult(report, commitId);
}

/**
 * Store validation results using Graph Store Protocol.
 *
 * <p>This method internally calls PUT/POST /data?graph=X to store the
 * validation report, reusing the existing commit creation infrastructure.</p>
 *
 * @param request validation request
 * @param report validation report
 * @return commit ID extracted from ETag header
 */
private String storeResultsViaGSP(
    ValidationRequest request,
    ValidationReport report
) {
  var storeConfig = request.results().store();

  // Serialize report to Turtle
  String reportTurtle = serializeReport(report.getModel(), "turtle");

  // Build GSP URL
  String graphParam = urlEncode(storeConfig.graph());
  String url = String.format("/%s/data?graph=%s",
      storeConfig.dataset(),
      graphParam
  );

  // Prepare headers
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.parseMediaType("text/turtle"));
  headers.set("SPARQL-VC-Author", "SHACL Validator");  // Default author

  HttpEntity<String> entity = new HttpEntity<>(reportTurtle, headers);

  // Call Graph Store Protocol endpoint
  // Use PUT if overwrite=true, POST if overwrite=false
  HttpMethod method = storeConfig.overwrite() ? HttpMethod.PUT : HttpMethod.POST;

  logger.debug("Storing validation result via GSP: {} {} (overwrite={})",
      method, url, storeConfig.overwrite());

  ResponseEntity<String> response = internalRestTemplate.exchange(
      url,
      method,
      entity,
      String.class
  );

  // Extract commit ID from ETag
  String etag = response.getHeaders().getETag();
  if (etag == null) {
    throw new ShaclValidationException("No ETag returned from graph storage");
  }

  String commitId = etag.replaceAll("\"", "");
  logger.info("Validation result stored: commitId={}, graph={}", commitId, storeConfig.graph());

  return commitId;
}

/**
 * Serialize RDF model to string in specified format.
 *
 * @param model RDF model
 * @param format format name (turtle, jsonld, rdfxml, ntriples)
 * @return serialized RDF string
 */
private String serializeReport(org.apache.jena.rdf.model.Model model, String format) {
  Lang lang = parseLang(format);
  StringWriter writer = new StringWriter();
  RDFDataMgr.write(writer, model, lang);
  return writer.toString();
}

/**
 * URL-encode a string for use in query parameters.
 *
 * @param value value to encode
 * @return URL-encoded value
 */
private String urlEncode(String value) {
  return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
}

/**
 * Validation result with optional commit ID.
 *
 * @param report validation report
 * @param commitId commit ID (if results were stored)
 */
public record ValidationResult(ValidationReport report, String commitId) {
}
```

Add imports:
```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java`

Replace `validateShacl` method:

```java
/**
 * Validate data graphs against a shapes graph.
 *
 * <p>Supports two response modes:</p>
 * <ul>
 *   <li><b>Validation only</b> - Returns 200 OK with sh:ValidationReport</li>
 *   <li><b>Validation + storage</b> - Returns 202 Accepted with commit ID</li>
 * </ul>
 *
 * @param dataset dataset name (path variable)
 * @param request validation request with shapes, data, options, and results config
 * @param accept Accept header for response format (validation-only mode)
 * @return validation report (200 OK) or storage confirmation (202 Accepted)
 */
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
    // Validate and store via Graph Store Protocol
    var result = validationService.validateAndStore(dataset, request);

    // Check if report should also be returned
    if (!request.results().returnReport()) {
      // Storage only: Return minimal response
      ValidationResponse response = new ValidationResponse(
          "Validation completed and result stored",
          result.commitId(),
          result.report().conforms(),
          request.results().store().graph()
      );

      String location = "/" + request.results().store().dataset() +
                        "/data?graph=" + urlEncode(request.results().store().graph());

      return ResponseEntity.accepted()
          .header("Location", location)
          .header("ETag", "\"" + result.commitId() + "\"")
          .contentType(MediaType.APPLICATION_JSON)
          .body(response);
    }

    // Both return and store: Return report with storage metadata
    String reportContent = serializeReport(result.report(), accept);
    String contentType = determineContentType(accept);

    String location = "/" + request.results().store().dataset() +
                      "/data?graph=" + urlEncode(request.results().store().graph());

    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .header("Location", location)
        .header("ETag", "\"" + result.commitId() + "\"")
        .header("X-Commit-Id", result.commitId())
        .header("X-Conforms", String.valueOf(result.report().conforms()))
        .contentType(MediaType.parseMediaType(contentType))
        .body(reportContent);

  } else {
    // Validation only (no storage)
    ValidationReport report = validationService.validate(dataset, request);
    String reportContent = serializeReport(report, accept);
    String contentType = determineContentType(accept);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(reportContent);
  }
}

/**
 * Determine Content-Type from Accept header.
 *
 * @param accept Accept header value
 * @return Content-Type string
 */
private String determineContentType(String accept) {
  if (accept.contains("json")) {
    return "application/ld+json";
  } else if (accept.contains("xml")) {
    return "application/rdf+xml";
  } else if (accept.contains("n-triples")) {
    return "application/n-triples";
  } else {
    return "text/turtle";
  }
}

/**
 * URL-encode a string for use in headers.
 *
 * @param value value to encode
 * @return URL-encoded value
 */
private String urlEncode(String value) {
  return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
}
```

Add imports:
```java
import org.chucc.vcserver.dto.shacl.ValidationResponse;
import org.springframework.http.HttpStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
```

---

## Testing Strategy

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ValidationResultStorageIT.java`

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
 * Integration tests for SHACL validation result storage via Graph Store Protocol.
 *
 * <p>These tests enable the projector to verify async result storage.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // Enable projector!
class ValidationResultStorageIT extends ITFixture {

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
        new GraphReference("inline", "turtle", null, null, null, SHAPES, null, null, null),
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
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getFirst("X-Commit-Id")).isNotNull();
    assertThat(response.getHeaders().getFirst("X-Conforms")).isEqualTo("true");

    // Assert body contains validation report
    assertThat(response.getBody())
        .contains("sh:ValidationReport")
        .contains("sh:conforms");

    // Assert projection (wait for async processing via GSP)
    String commitId = response.getHeaders().getETag().replaceAll("\"", "");

    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify graph stored via GSP
          ResponseEntity<String> graphResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-1",
              String.class
          );
          assertThat(graphResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(graphResponse.getBody())
              .contains("sh:ValidationReport")
              .contains("sh:conforms");

          // Verify commit created via CreateCommitCommand
          var commitResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/version/commits/" + commitId,
              String.class
          );
          assertThat(commitResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        });
  }

  @Test
  void validate_withStorage_overwriteFalse_graphExists_shouldReturn409() {
    // Arrange: Create existing graph via GSP
    String existingReport = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        [] a sh:ValidationReport ; sh:conforms true .
        """;

    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.setContentType(MediaType.parseMediaType("text/turtle"));

    HttpEntity<String> putEntity = new HttpEntity<>(existingReport, putHeaders);

    restTemplate.exchange(
        "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-2",
        HttpMethod.PUT,
        putEntity,
        String.class
    );

    // Wait for graph to be stored
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          ResponseEntity<String> check = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-2",
              String.class
          );
          assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

    // Attempt to store without overwrite
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, SHAPES, null, null, null),
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

    // Assert: GSP returns 409 Conflict (POST to existing graph)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void validate_withStorage_overwriteTrue_graphExists_shouldReplace() throws Exception {
    // Arrange: Create existing graph
    String existingReport = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        [] a sh:ValidationReport ; sh:conforms false .
        """;

    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.setContentType(MediaType.parseMediaType("text/turtle"));

    HttpEntity<String> putEntity = new HttpEntity<>(existingReport, putHeaders);

    restTemplate.exchange(
        "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-3",
        HttpMethod.PUT,
        putEntity,
        String.class
    );

    // Wait for graph to be stored
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          ResponseEntity<String> check = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-3",
              String.class
          );
          assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        });

    // Overwrite with new validation result
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, SHAPES, null, null, null),
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
    assertThat(response.getBody().conforms()).isTrue();  // New report conforms

    // Verify graph replaced
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          ResponseEntity<String> graphResponse = restTemplate.getForEntity(
              "/" + REPORTS_DATASET + "/data?graph=http://example.org/reports/test-3",
              String.class
          );
          assertThat(graphResponse.getBody())
              .contains("sh:ValidationReport")
              .contains("sh:conforms true");  // Updated to true
        });
  }
}
```

### Test Scenarios

1. **Store and Create Commit** - Verify async projection via GSP with `await()`
2. **Conflict Detection (overwrite=false)** - Graph exists → 409 from GSP
3. **Overwrite (overwrite=true)** - Graph exists → Replace via PUT

**Test Pattern:** Projector **ENABLED** via `@TestPropertySource` (to verify async commit creation)

---

## Architecture Diagram

**Before (Task 6 - Over-engineered):**
```
POST /shacl with results.store
  ↓
StoreValidationResultCommand (NEW)
  ↓
ValidationResultStoredEvent (NEW)
  ↓
ValidationResultProjector (NEW)
```

**After (Task 5 - Reuse existing):**
```
POST /shacl with results.store
  ↓
ShaclValidationService.validateAndStore()
  ↓
Internal: PUT /data?graph=X (EXISTING GSP)
  ↓
GraphStoreController (EXISTING)
  ↓
CreateCommitCommandHandler (EXISTING)
  ↓
CommitCreatedEvent (EXISTING)
  ↓
ReadModelProjector (EXISTING)
```

**Components eliminated:** 3 (command, event, projector)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/config/InternalHttpClientConfig.java`
2. `src/test/java/org/chucc/vcserver/integration/ValidationResultStorageIT.java`

**Total Files:** 2 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Add validateAndStore()
2. `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java` - Handle storage

**Total Files:** 2 modified files

---

## Success Criteria

- ✅ Validation report stored via Graph Store Protocol
- ✅ Commit created by existing CreateCommitCommandHandler
- ✅ Overwrite control working (PUT vs POST)
- ✅ Conflict detection (409 when graph exists with overwrite=false)
- ✅ ETag header contains commit ID
- ✅ Location header points to stored graph
- ✅ 3 integration tests passing (with projector enabled)
- ✅ Zero new CQRS components created
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
mvn -q test -Dtest=ValidationResultStorageIT

# Full build
mvn -q clean install
```

---

## Why This Approach is Better

| Aspect | Old (Task 6) | New (Task 5) |
|--------|--------------|--------------|
| **Components** | 3 new (command, event, projector) | 0 new (reuse existing) |
| **Complexity** | High (new CQRS workflow) | Low (internal HTTP call) |
| **Consistency** | Separate storage logic | Same as manual graph PUT |
| **Event Replay** | New event type to handle | Same events as graph updates |
| **Commit Graph** | Detached commits (no parents) | Proper commit graph |
| **Code Lines** | ~400 lines | ~100 lines |

**Key Insight:** Validation results ARE graphs → Treat them like graphs!

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Invoke specialized agents:**
   - `@test-isolation-validator` (projector enablement)
   - `@code-reviewer` (final review)
3. **Commit** changes with conventional commit message
4. **Delete** this task file (and delete old `06-result-storage-cqrs.md`)
5. **Update** `.tasks/shacl/README.md` (mark Task 5 as completed)
6. **Proceed** to Task 6: Version Control Selectors

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §5.2, §6, §10
- **[Graph Store Protocol](https://www.w3.org/TR/sparql11-http-rdf-update/)** - PUT/POST semantics
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing with projector enabled
- **[Awaitility](https://www.awaitility.org/)** - Async testing library

---

**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Status:** Blocked by Tasks 1-4
