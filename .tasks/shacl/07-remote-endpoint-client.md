# Task 7: Remote Endpoint Client

**Phase:** 4 (Remote Endpoint Support)
**Estimated Time:** 3-4 hours
**Complexity:** Medium-High
**Dependencies:** Tasks 1-6 (Core Infrastructure through Version Control Selectors)

---

## Overview

This task implements remote SPARQL endpoint support for fetching shapes and data graphs.

**Features:**
- Fetch graphs from remote SPARQL endpoints via SPARQL CONSTRUCT
- HTTP timeout and retry logic
- Response caching (Caffeine)
- Content negotiation for RDF formats
- Integration with existing validation workflow

**Use Cases:**
- Validate local data against shapes from external schema registry
- Validate data from external source against local shapes
- Cross-organization data validation

**Goal:** Enable federated SHACL validation across distributed RDF datasources (essential for linked data).

---

## Current State

**Existing (from Tasks 1-6):**
- ✅ GraphReference and DataReference DTOs with source="remote"
- ✅ Cross-field validation for remote source
- ✅ ShaclValidationService orchestration

**Missing:**
- ❌ RemoteEndpointClient service
- ❌ SPARQL CONSTRUCT query generation
- ❌ Caching infrastructure (Caffeine)
- ❌ Remote endpoint configuration properties
- ❌ Integration tests for remote fetching

---

## Requirements

### API Specification

**Endpoint:** `POST /{dataset}/shacl`

**Request Example (Remote Shapes):**
```json
{
  "shapes": {
    "source": "remote",
    "endpoint": "https://schemas.example.org/sparql",
    "graph": "http://example.org/shapes/person"
  },
  "data": {
    "source": "local",
    "dataset": "users",
    "graphs": ["default"]
  }
}
```

**Request Example (Remote Data):**
```json
{
  "shapes": {
    "source": "inline",
    "data": "@prefix sh: <http://www.w3.org/ns/shacl#> ..."
  },
  "data": {
    "source": "remote",
    "endpoint": "https://external-data.example.org/sparql",
    "graphs": ["http://example.org/users"]
  }
}
```

**Response:** Same as normal validation (200 OK with ValidationReport)

### SPARQL CONSTRUCT Query

```sparql
CONSTRUCT { ?s ?p ?o }
WHERE {
  GRAPH <http://example.org/shapes/person> {
    ?s ?p ?o
  }
}
```

---

## Implementation Steps

### Step 1: Add Remote Endpoint Configuration

**File:** `src/main/java/org/chucc/vcserver/config/ShaclValidationProperties.java`

Update the record to add remote endpoint configuration:

```java
@ConfigurationProperties(prefix = "chucc.shacl.validation")
@Validated
public record ShaclValidationProperties(
    Boolean enabled,
    Integer maxShapesSize,
    Integer maxDataSize,
    Integer timeout,
    Integer maxConcurrent,

    RemoteEndpointConfig remote  // NEW
) {

  public ShaclValidationProperties {
    if (enabled == null) {
      enabled = true;
    }
    if (maxShapesSize == null) {
      maxShapesSize = 10485760;  // 10MB
    }
    if (maxDataSize == null) {
      maxDataSize = 104857600;  // 100MB
    }
    if (timeout == null) {
      timeout = 60000;  // 60 seconds
    }
    if (maxConcurrent == null) {
      maxConcurrent = 10;
    }
    if (remote == null) {
      remote = new RemoteEndpointConfig(null, null, null, null);
    }
  }

  /**
   * Remote endpoint configuration.
   *
   * @param enabled whether remote endpoints are enabled
   * @param timeout timeout for remote requests (milliseconds)
   * @param maxResponseSize maximum response size (bytes)
   * @param cacheEnabled whether to cache remote responses
   */
  public record RemoteEndpointConfig(
      Boolean enabled,

      @Min(1000)
      @Max(300000)  // 5 minutes
      Integer timeout,

      @Min(1024)
      @Max(104857600)  // 100MB
      Integer maxResponseSize,

      Boolean cacheEnabled
  ) {
    public RemoteEndpointConfig {
      if (enabled == null) {
        enabled = true;
      }
      if (timeout == null) {
        timeout = 30000;  // 30 seconds
      }
      if (maxResponseSize == null) {
        maxResponseSize = 104857600;  // 100MB
      }
      if (cacheEnabled == null) {
        cacheEnabled = true;
      }
    }
  }
}
```

### Step 2: Create RemoteEndpointClient

**File:** `src/main/java/org/chucc/vcserver/service/RemoteEndpointClient.java`

```java
package org.chucc.vcserver.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.chucc.vcserver.config.ShaclValidationProperties;
import org.chucc.vcserver.exception.RemoteEndpointException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Client for fetching RDF graphs from remote SPARQL endpoints.
 *
 * <p>Uses SPARQL CONSTRUCT queries to retrieve graphs with optional caching.</p>
 */
@Service
public class RemoteEndpointClient {

  private static final Logger logger = LoggerFactory.getLogger(RemoteEndpointClient.class);

  private final RestTemplate restTemplate;
  private final ShaclValidationProperties properties;
  private final Cache<String, Graph> cache;

  /**
   * Construct RemoteEndpointClient.
   *
   * @param builder rest template builder
   * @param properties SHACL validation properties
   */
  public RemoteEndpointClient(
      RestTemplateBuilder builder,
      ShaclValidationProperties properties
  ) {
    this.properties = properties;

    // Configure RestTemplate with timeout
    this.restTemplate = builder
        .setConnectTimeout(Duration.ofMillis(properties.remote().timeout()))
        .setReadTimeout(Duration.ofMillis(properties.remote().timeout()))
        .build();

    // Configure cache
    if (properties.remote().cacheEnabled()) {
      this.cache = Caffeine.newBuilder()
          .expireAfterWrite(1, TimeUnit.HOURS)
          .maximumSize(100)
          .recordStats()
          .build();
      logger.info("Remote endpoint cache enabled (TTL: 1 hour, max size: 100)");
    } else {
      this.cache = null;
      logger.info("Remote endpoint cache disabled");
    }
  }

  /**
   * Fetch graph from remote SPARQL endpoint.
   *
   * @param endpoint SPARQL endpoint URL
   * @param graphUri graph URI to fetch
   * @return graph fetched from endpoint
   * @throws RemoteEndpointException if fetch fails
   */
  public Graph fetchGraph(String endpoint, String graphUri) {
    String cacheKey = endpoint + ":" + graphUri;

    // Check cache first
    if (cache != null) {
      Graph cached = cache.getIfPresent(cacheKey);
      if (cached != null) {
        logger.debug("Cache HIT: {} (size: {} triples)", cacheKey, cached.size());
        return cached;
      }
      logger.debug("Cache MISS: {}", cacheKey);
    }

    // Fetch from remote endpoint
    Graph graph = fetchGraphFromEndpoint(endpoint, graphUri);

    // Store in cache
    if (cache != null) {
      cache.put(cacheKey, graph);
      logger.debug("Cached graph: {} ({} triples)", cacheKey, graph.size());
    }

    return graph;
  }

  /**
   * Fetch graph from remote SPARQL endpoint via CONSTRUCT query.
   *
   * @param endpoint SPARQL endpoint URL
   * @param graphUri graph URI to fetch
   * @return fetched graph
   * @throws RemoteEndpointException if fetch fails
   */
  private Graph fetchGraphFromEndpoint(String endpoint, String graphUri) {
    logger.info("Fetching graph from remote endpoint: {} (graph: {})", endpoint, graphUri);

    // Build SPARQL CONSTRUCT query
    String sparqlQuery = String.format(
        "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <%s> { ?s ?p ?o } }",
        graphUri
    );

    // Prepare request
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");  // Prefer Turtle format

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("query", sparqlQuery);

    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

    try {
      // Execute SPARQL query
      ResponseEntity<String> response = restTemplate.postForEntity(
          endpoint,
          entity,
          String.class
      );

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new RemoteEndpointException(
            "Remote endpoint returned non-2xx status: " + response.getStatusCode()
        );
      }

      String responseBody = response.getBody();
      if (responseBody == null || responseBody.isBlank()) {
        logger.warn("Remote endpoint returned empty response (graph may not exist)");
        return org.apache.jena.graph.Factory.createDefaultGraph();  // Empty graph
      }

      // Check response size
      if (responseBody.length() > properties.remote().maxResponseSize()) {
        throw new RemoteEndpointException(
            "Remote endpoint response exceeds maximum size: " +
            properties.remote().maxResponseSize() + " bytes"
        );
      }

      // Parse RDF response
      Graph graph = org.apache.jena.graph.Factory.createDefaultGraph();
      Lang lang = RDFLanguages.TURTLE;  // We requested Turtle in Accept header

      RDFDataMgr.read(graph, new StringReader(responseBody), null, lang);

      logger.info("Fetched graph from remote endpoint: {} triples", graph.size());
      return graph;

    } catch (org.springframework.web.client.ResourceAccessException e) {
      logger.error("Remote endpoint timeout: {}", endpoint, e);
      throw new RemoteEndpointException(
          "Remote endpoint timeout: " + endpoint, e
      );

    } catch (Exception e) {
      logger.error("Failed to fetch graph from remote endpoint: {}", endpoint, e);
      throw new RemoteEndpointException(
          "Failed to fetch graph from remote endpoint: " + e.getMessage(), e
      );
    }
  }

  /**
   * Get cache statistics (for monitoring).
   *
   * @return cache statistics as string
   */
  public String getCacheStats() {
    if (cache == null) {
      return "Cache disabled";
    }
    return cache.stats().toString();
  }
}
```

### Step 3: Add RemoteEndpointException

**File:** `src/main/java/org/chucc/vcserver/exception/RemoteEndpointException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when remote SPARQL endpoint is unreachable or returns an error.
 */
public class RemoteEndpointException extends VcServerException {

  /**
   * Construct exception with message.
   *
   * @param message error message
   */
  public RemoteEndpointException(String message) {
    super(message, HttpStatus.BAD_GATEWAY, "remote_endpoint_error");
  }

  /**
   * Construct exception with message and cause.
   *
   * @param message error message
   * @param cause underlying exception
   */
  public RemoteEndpointException(String message, Throwable cause) {
    super(message, HttpStatus.BAD_GATEWAY, "remote_endpoint_error", cause);
  }
}
```

### Step 4: Update ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java`

Add remote endpoint support:

```java
// Add to class fields:
@Autowired
private RemoteEndpointClient remoteEndpointClient;

/**
 * Resolve shapes graph from request (with remote support).
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
    return resolveRemoteShapesGraph(shapes);
  }

  throw new InvalidGraphReferenceException(
      "Invalid shapes source: " + shapes.source()
  );
}

/**
 * Resolve remote shapes graph.
 *
 * @param shapes shapes reference
 * @return shapes graph from remote endpoint
 */
private Graph resolveRemoteShapesGraph(GraphReference shapes) {
  if (!properties.remote().enabled()) {
    throw new InvalidGraphReferenceException(
        "Remote endpoints are disabled in configuration"
    );
  }

  logger.info("Fetching remote shapes: endpoint={}, graph={}",
      shapes.endpoint(), shapes.graph());

  Graph graph = remoteEndpointClient.fetchGraph(shapes.endpoint(), shapes.graph());

  if (graph == null || graph.isEmpty()) {
    throw new InvalidGraphReferenceException(
        "Remote shapes graph '" + shapes.graph() + "' is empty or does not exist at endpoint '" +
        shapes.endpoint() + "'"
    );
  }

  return graph;
}

/**
 * Resolve data graph from request (with remote support).
 */
private Graph resolveDataGraph(String dataset, ValidationRequest request) {
  var data = request.data();

  if ("local".equals(data.source())) {
    // ... existing local logic
  }

  if ("remote".equals(data.source())) {
    return resolveRemoteDataGraph(data, request.options());
  }

  throw new InvalidGraphReferenceException(
      "Invalid data source: " + data.source()
  );
}

/**
 * Resolve remote data graph.
 *
 * @param data data reference
 * @param options validation options
 * @return data graph from remote endpoint
 */
private Graph resolveRemoteDataGraph(DataReference data, ValidationOptions options) {
  if (!properties.remote().enabled()) {
    throw new InvalidGraphReferenceException(
        "Remote endpoints are disabled in configuration"
    );
  }

  logger.info("Fetching remote data: endpoint={}, graphs={}",
      data.endpoint(), data.graphs());

  // Union mode: Fetch and combine multiple graphs
  if ("union".equals(options.validateGraphs())) {
    List<Graph> graphs = new ArrayList<>();
    for (String graphUri : data.graphs()) {
      Graph graph = remoteEndpointClient.fetchGraph(data.endpoint(), graphUri);
      if (graph != null && !graph.isEmpty()) {
        graphs.add(graph);
      }
    }

    if (graphs.isEmpty()) {
      throw new InvalidGraphReferenceException(
          "All remote graphs are empty or do not exist"
      );
    }

    // Create union
    Graph unionGraph = graphs.get(0);
    for (int i = 1; i < graphs.size(); i++) {
      unionGraph = new org.apache.jena.graph.compose.Union(unionGraph, graphs.get(i));
    }

    return unionGraph;
  }

  // Separately mode: Single graph only
  if (data.graphs().size() != 1) {
    throw new InvalidGraphReferenceException(
        "Multiple graphs require validateGraphs='union'"
    );
  }

  Graph graph = remoteEndpointClient.fetchGraph(data.endpoint(), data.graphs().get(0));

  if (graph == null || graph.isEmpty()) {
    throw new InvalidGraphReferenceException(
        "Remote data graph '" + data.graphs().get(0) + "' is empty or does not exist"
    );
  }

  return graph;
}
```

### Step 5: Update Application Configuration

**File:** `src/main/resources/application.yml`

```yaml
chucc:
  shacl:
    validation:
      enabled: true
      max-shapes-size: 10485760
      max-data-size: 104857600
      timeout: 60000
      max-concurrent: 10

      # Remote endpoint configuration (Task 7)
      remote:
        enabled: true
        timeout: 30000              # 30 seconds
        max-response-size: 104857600  # 100MB
        cache-enabled: true
```

### Step 6: Add Caffeine Dependency

**File:** `pom.xml`

Add Caffeine cache dependency:

```xml
<!-- Caffeine cache for remote endpoint caching -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## Testing Strategy

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/RemoteEndpointValidationIT.java`

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
 * Integration tests for SHACL validation with remote SPARQL endpoints.
 *
 * <p>Note: These tests use local SPARQL endpoint as mock remote endpoint.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class RemoteEndpointValidationIT extends ITFixture {

  private static final String SCHEMAS_DATASET = "test-remote-schemas";

  private static final String PERSON_SHAPES = """
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

  private String remoteEndpointUrl;

  @BeforeEach
  void setUp() {
    // Create "remote" schema dataset
    createDatasetViaApi(SCHEMAS_DATASET);

    // Store shapes in remote dataset
    storeGraphViaGSP(SCHEMAS_DATASET, "http://example.org/shapes", PERSON_SHAPES);

    // Build "remote" endpoint URL (actually our local instance)
    remoteEndpointUrl = "http://localhost:" + port + "/" + SCHEMAS_DATASET + "/sparql";

    // Add test data locally
    String dataPatch = """
        TX .
        A <http://example.org/Alice> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Alice> <http://example.org/name> "Alice" .
        TC .
        """;
    applyPatchToDataset(getDatasetName(), dataPatch);
  }

  @Test
  void validate_withRemoteShapes_shouldFetchAndValidate() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            remoteEndpointUrl, null, null, null, null),
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
  void validate_withRemoteShapes_caching_shouldUseCachedGraph() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            remoteEndpointUrl, null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act: First request (cache miss)
    ResponseEntity<String> response1 = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Act: Second request (cache hit)
    ResponseEntity<String> response2 = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert: Both should succeed
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Note: Cache hit verification would require metrics/logging inspection
  }

  @Test
  void validate_withRemoteData_shouldFetchAndValidate() {
    // Arrange: Store data in "remote" dataset
    String remoteDataPatch = """
        TX .
        A <http://example.org/Bob> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Person> .
        A <http://example.org/Bob> <http://example.org/name> "Bob" .
        TC .
        """;
    applyPatchToDataset(SCHEMAS_DATASET, remoteDataPatch);

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, PERSON_SHAPES, null, null, null),
        new DataReference("remote", null, List.of("default"),
            remoteEndpointUrl, null, null, null),
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
  void validate_withInvalidRemoteEndpoint_shouldReturn502() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://nonexistent.example.org/sparql", null, null, null, null),
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(response.getBody())
        .contains("remote_endpoint_error");
  }

  /**
   * Helper method to store graph via Graph Store Protocol.
   */
  private void storeGraphViaGSP(String dataset, String graphUri, String content) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/turtle"));

    HttpEntity<String> entity = new HttpEntity<>(content, headers);

    restTemplate.exchange(
        "/" + dataset + "/data?graph=" + graphUri,
        HttpMethod.PUT,
        entity,
        String.class
    );
  }
}
```

### Test Scenarios

1. **Remote Shapes** - Fetch shapes from remote endpoint → validate successfully
2. **Caching** - Second request uses cached graph (cache hit)
3. **Remote Data** - Fetch data from remote endpoint → validate successfully
4. **Invalid Endpoint** - Unreachable endpoint → 502 remote_endpoint_error

**Test Pattern:** Projector DISABLED (read-only operation)

**Note:** Tests use local SPARQL endpoint as mock remote endpoint (same instance, different dataset)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/service/RemoteEndpointClient.java`
2. `src/main/java/org/chucc/vcserver/exception/RemoteEndpointException.java`
3. `src/test/java/org/chucc/vcserver/integration/RemoteEndpointValidationIT.java`

**Total Files:** 3 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/config/ShaclValidationProperties.java` - Add remote endpoint config
2. `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` - Add remote graph resolution
3. `src/main/resources/application.yml` - Add remote endpoint configuration
4. `pom.xml` - Add Caffeine dependency

**Total Files:** 4 modified files

---

## Success Criteria

- ✅ Remote shapes fetching via SPARQL CONSTRUCT
- ✅ Remote data fetching via SPARQL CONSTRUCT
- ✅ Caching enabled with Caffeine (1 hour TTL, 100 entries)
- ✅ Timeout handling (30 seconds default)
- ✅ Response size limits enforced
- ✅ Configuration properties working
- ✅ 4 integration tests passing
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
mvn -q test -Dtest=RemoteEndpointValidationIT

# Full build
mvn -q clean install
```

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Commit** changes with conventional commit message
3. **Delete** this task file
4. **Update** `.tasks/shacl/README.md` (mark Task 7 as completed)
5. **Proceed** to Task 8: Security Controls (SSRF prevention)

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - §5.2 (remote endpoints)
- **[SPARQL Protocol](https://www.w3.org/TR/sparql11-protocol/)** - CONSTRUCT query
- **[Caffeine Cache](https://github.com/ben-manes/caffeine)** - High-performance caching library
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Testing patterns

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium-High
**Status:** Blocked by Tasks 1-6
