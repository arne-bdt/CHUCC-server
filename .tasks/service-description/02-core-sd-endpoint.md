# Phase 2: Core SD Endpoint Implementation

**Status:** ⏳ Not Started (depends on Phase 1)
**Priority:** High
**Estimated Time:** 3-4 hours
**Complexity:** Medium

---

## Overview

Implement the basic service description endpoint with static SPARQL capabilities (QUERY, UPDATE, GSP support). This phase establishes the infrastructure for serving RDF-based service descriptions with content negotiation.

**Goal:** Create a working service description endpoint that describes CHUCC's core SPARQL capabilities.

---

## Prerequisites

- ✅ Phase 1 completed (design and vocabulary defined)
- ✅ Vocabulary design document available
- ✅ Endpoint structure decided

---

## Implementation Steps

### Step 1: Create ServiceDescriptionService

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

**Purpose:** Generate RDF model representing service description

```java
package org.chucc.vcserver.service;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Service;

/**
 * Service for generating SPARQL 1.1 Service Description.
 * <p>
 * Implements W3C SPARQL 1.1 Service Description specification:
 * https://www.w3.org/TR/sparql11-service-description/
 * </p>
 */
@Service
public class ServiceDescriptionService {

  private static final String SD_NS = "http://www.w3.org/ns/sparql-service-description#";
  private static final String VC_NS = "http://chucc.org/ns/version-control#";

  private final String baseUrl;

  /**
   * Constructs service description service.
   *
   * @param baseUrl Base URL of the service
   */
  public ServiceDescriptionService(
      @Value("${server.base-url:http://localhost:8080}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * Generates service description model.
   *
   * @return RDF model describing the service
   */
  public Model generateServiceDescription() {
    Model model = ModelFactory.createDefaultModel();

    // Set namespace prefixes
    model.setNsPrefix("sd", SD_NS);
    model.setNsPrefix("vc", VC_NS);

    // Create service resource
    Resource service = model.createResource(baseUrl + "/sparql");
    service.addProperty(RDF.type, model.createResource(SD_NS + "Service"));

    // Endpoint
    service.addProperty(
        model.createProperty(SD_NS + "endpoint"),
        model.createResource(baseUrl + "/sparql"));

    // Supported languages
    service.addProperty(
        model.createProperty(SD_NS + "supportedLanguage"),
        model.createResource(SD_NS + "SPARQL11Query"));
    service.addProperty(
        model.createProperty(SD_NS + "supportedLanguage"),
        model.createResource(SD_NS + "SPARQL11Update"));

    // Features
    addFeatures(model, service);

    // Result formats
    addResultFormats(model, service);

    // Extension features
    addExtensionFeatures(model, service);

    return model;
  }

  private void addFeatures(Model model, Resource service) {
    // Standard SPARQL 1.1 features
    service.addProperty(
        model.createProperty(SD_NS + "feature"),
        model.createResource(SD_NS + "UnionDefaultGraph"));
    service.addProperty(
        model.createProperty(SD_NS + "feature"),
        model.createResource(SD_NS + "BasicFederatedQuery"));
  }

  private void addResultFormats(Model model, Resource service) {
    // SPARQL Results formats
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_JSON"));
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_XML"));
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/SPARQL_Results_CSV"));

    // RDF serialization formats
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/Turtle"));
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/RDF_XML"));
    service.addProperty(
        model.createProperty(SD_NS + "resultFormat"),
        model.createResource("http://www.w3.org/ns/formats/JSON-LD"));
  }

  private void addExtensionFeatures(Model model, Resource service) {
    // Version control extension features
    service.addProperty(
        model.createProperty(SD_NS + "extensionFunction"),
        model.createResource(VC_NS + "timeTravel"));
    service.addProperty(
        model.createProperty(SD_NS + "feature"),
        model.createResource(VC_NS + "VersionControl"));
  }
}
```

---

### Step 2: Create ServiceDescriptionController

**Location:** `src/main/java/org/chucc/vcserver/controller/ServiceDescriptionController.java`

**Purpose:** REST endpoint for serving service description

```java
package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.chucc.vcserver.service.ServiceDescriptionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for SPARQL 1.1 Service Description endpoint.
 * <p>
 * Provides machine-readable metadata about the service's capabilities,
 * datasets, and version control features.
 * </p>
 *
 * @see <a href="https://www.w3.org/TR/sparql11-service-description/">
 *   SPARQL 1.1 Service Description</a>
 */
@Tag(name = "Service Description", description = "SPARQL service metadata and capabilities")
@RestController
public class ServiceDescriptionController {

  private final ServiceDescriptionService serviceDescriptionService;

  /**
   * Constructs service description controller.
   *
   * @param serviceDescriptionService Service description service
   */
  public ServiceDescriptionController(ServiceDescriptionService serviceDescriptionService) {
    this.serviceDescriptionService = serviceDescriptionService;
  }

  /**
   * Returns service description (well-known URI).
   *
   * @param request HTTP request
   * @param response HTTP response
   * @throws IOException if writing response fails
   */
  @Operation(
      summary = "Get service description (well-known URI)",
      description = "Returns SPARQL 1.1 Service Description in RDF format with content negotiation. "
          + "Describes service capabilities, supported features, and available datasets.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service description returned"),
      @ApiResponse(responseCode = "406", description = "Unsupported media type requested")
  })
  @GetMapping(
      value = "/.well-known/void",
      produces = {
          "text/turtle",
          "application/ld+json",
          "application/rdf+xml",
          "application/n-triples"
      })
  public void getServiceDescriptionWellKnown(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    serveServiceDescription(request, response);
  }

  /**
   * Returns service description (explicit endpoint).
   *
   * @param request HTTP request
   * @param response HTTP response
   * @throws IOException if writing response fails
   */
  @Operation(
      summary = "Get service description",
      description = "Returns SPARQL 1.1 Service Description in RDF format with content negotiation. "
          + "Alternative to /.well-known/void endpoint.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Service description returned"),
      @ApiResponse(responseCode = "406", description = "Unsupported media type requested")
  })
  @GetMapping(
      value = "/service-description",
      produces = {
          "text/turtle",
          "application/ld+json",
          "application/rdf+xml",
          "application/n-triples"
      })
  public void getServiceDescription(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    serveServiceDescription(request, response);
  }

  private void serveServiceDescription(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {

    // Generate service description model
    Model model = serviceDescriptionService.generateServiceDescription();

    // Content negotiation
    String acceptHeader = request.getHeader("Accept");
    Lang format = determineFormat(acceptHeader);

    // Set response headers
    response.setContentType(format.getHeaderString());
    response.setCharacterEncoding("UTF-8");

    // Write RDF
    RDFDataMgr.write(response.getOutputStream(), model, format);
  }

  private Lang determineFormat(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.contains("*/*")) {
      return Lang.TURTLE; // Default
    }

    Lang format = RDFLanguages.contentTypeToLang(acceptHeader);
    if (format != null) {
      return format;
    }

    // Try specific formats
    if (acceptHeader.contains("application/ld+json")) {
      return Lang.JSONLD;
    } else if (acceptHeader.contains("application/rdf+xml")) {
      return Lang.RDFXML;
    } else if (acceptHeader.contains("application/n-triples")) {
      return Lang.NTRIPLES;
    } else if (acceptHeader.contains("text/turtle")) {
      return Lang.TURTLE;
    }

    return Lang.TURTLE; // Fallback
  }
}
```

---

### Step 3: Configure Base URL

**Location:** `src/main/resources/application.yml`

Add configuration for base URL:

```yaml
server:
  # Base URL for service description (override in production)
  base-url: ${SERVER_BASE_URL:http://localhost:8080}
```

---

### Step 4: Create Integration Tests

**Location:** `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.chucc.vcserver.testutil.IntegrationTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.io.StringReader;

/**
 * Integration tests for Service Description endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ServiceDescriptionIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void wellKnownVoid_shouldReturnServiceDescription() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/.well-known/void",
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");
    assertThat(response.getBody()).contains("sd:Service");
  }

  @Test
  void serviceDescription_shouldReturnServiceDescription() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("sd:Service");
  }

  @Test
  void serviceDescription_shouldSupportTurtle() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/service-description",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");

    // Verify valid Turtle
    Model model = RDFDataMgr.loadModel(
        new StringReader(response.getBody()), null, Lang.TURTLE);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void serviceDescription_shouldSupportJsonLd() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/ld+json");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/service-description",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/ld+json");

    // Verify valid JSON-LD
    Model model = RDFDataMgr.loadModel(
        new StringReader(response.getBody()), null, Lang.JSONLD);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void serviceDescription_shouldDescribeSparql11Query() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:SPARQL11Query");
  }

  @Test
  void serviceDescription_shouldDescribeSparql11Update() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:SPARQL11Update");
  }

  @Test
  void serviceDescription_shouldDescribeResultFormats() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:resultFormat");
    assertThat(response.getBody()).contains("SPARQL_Results_JSON");
  }
}
```

---

## Files to Create

1. **Service**
   - `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

2. **Controller**
   - `src/main/java/org/chucc/vcserver/controller/ServiceDescriptionController.java`

3. **Tests**
   - `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

4. **Configuration** (modify existing)
   - `src/main/resources/application.yml` (add `server.base-url`)

---

## Testing Strategy

### Unit Tests
Not needed (service logic is straightforward model generation)

### Integration Tests
- ✅ Test `GET /.well-known/void` endpoint
- ✅ Test `GET /service-description` endpoint
- ✅ Test Turtle content negotiation
- ✅ Test JSON-LD content negotiation
- ✅ Test RDF/XML content negotiation
- ✅ Verify SPARQL 1.1 Query support described
- ✅ Verify SPARQL 1.1 Update support described
- ✅ Verify result formats described

### Manual Testing

```bash
# Test Turtle format (default)
curl -H "Accept: text/turtle" http://localhost:8080/.well-known/void

# Test JSON-LD format
curl -H "Accept: application/ld+json" http://localhost:8080/service-description

# Test RDF/XML format
curl -H "Accept: application/rdf+xml" http://localhost:8080/service-description
```

---

## Success Criteria

- ✅ `GET /.well-known/void` endpoint works
- ✅ `GET /service-description` endpoint works
- ✅ Content negotiation supports Turtle, JSON-LD, RDF/XML, N-Triples
- ✅ Service description includes:
  - Service endpoint URL
  - Supported languages (SPARQL 1.1 Query, Update)
  - Standard features (UnionDefaultGraph, etc.)
  - Result formats (JSON, XML, CSV, Turtle, etc.)
  - Extension features (version control)
- ✅ All integration tests pass
- ✅ Valid RDF output for all formats
- ✅ Zero quality violations

---

## Build Commands

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Incremental tests
mvn -q test -Dtest=ServiceDescriptionIT

# Phase 3: Full build
mvn -q clean install
```

---

## Next Phase

**Phase 3:** [Dataset Integration](./03-dataset-integration.md)
- Discover available datasets dynamically
- Describe named graphs per dataset
- Link to dataset-specific endpoints

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium
