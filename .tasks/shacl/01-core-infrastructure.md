# Task 1: Core Infrastructure for SHACL Validation

**Phase:** 1 (Core Validation - MVP)
**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Dependencies:** None

---

## Overview

This task creates the foundational infrastructure for SHACL validation:
- Request/response DTOs with JSON schema validation
- Configuration properties for resource limits and feature flags
- Custom exceptions for validation errors
- Base controller with endpoint skeleton
- Integration with Apache Jena SHACL library

**Goal:** Establish the foundation that all subsequent SHACL tasks will build upon.

---

## Current State

**Existing:**
- ✅ Apache Jena 5.5.0 (includes SHACL support)
- ✅ RFC 7807 error handling infrastructure
- ✅ Spring Boot configuration system
- ✅ ITFixture test infrastructure

**Missing:**
- ❌ SHACL-specific DTOs (request, response, options)
- ❌ SHACL configuration properties
- ❌ SHACL validation controller
- ❌ SHACL-specific exceptions
- ❌ Integration tests for SHACL endpoint

---

## Requirements

### API Specification

**Endpoint:** `POST /{dataset}/shacl`

**Request Body:**
```json
{
  "shapes": {
    "source": "inline" | "local" | "remote",
    "dataset": "string",
    "graph": "string",
    "endpoint": "string",
    "data": "string",
    "branch": "string",
    "commit": "string",
    "asOf": "RFC3339"
  },
  "data": {
    "source": "local" | "remote",
    "dataset": "string",
    "graphs": ["string"],
    "endpoint": "string",
    "branch": "string",
    "commit": "string",
    "asOf": "RFC3339"
  },
  "options": {
    "validateGraphs": "separately" | "merged" | "dataset",
    "targetNode": "string",
    "severity": "Violation" | "Warning" | "Info"
  },
  "results": {
    "return": boolean,
    "store": {
      "dataset": "string",
      "graph": "string",
      "overwrite": boolean
    }
  }
}
```

**Response (200 OK - Validation Only):**
```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .

[] a sh:ValidationReport ;
   sh:conforms true .
```

**Response (202 Accepted - With Storage):**
```json
{
  "message": "Validation completed and result stored",
  "commitId": "01936d8f-1234-7890-abcd-ef1234567890",
  "conforms": false,
  "resultsGraph": "http://example.org/reports/2025-01-15"
}
```

### Error Codes (RFC 7807)

- `invalid_request` - Malformed request body
- `invalid_graph_reference` - Invalid graph reference
- `selector_conflict` - Conflicting selectors (branch + commit)
- `dataset_not_found` - Dataset does not exist
- `shapes_graph_not_found` - Shapes graph does not exist
- `data_graph_not_found` - Data graph does not exist
- `format_not_available` - Requested RDF format not available
- `graph_exists` - Result graph exists (overwrite=false)
- `invalid_shapes` - Invalid SHACL shapes graph
- `remote_endpoint_error` - Remote endpoint unreachable
- `validation_error` - Validation engine failure
- `remote_timeout` - Remote endpoint timeout

---

## Implementation Steps

### Step 1: Create DTOs

#### 1.1 GraphReference DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/GraphReference.java`

```java
package org.chucc.vcserver.dto.shacl;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Reference to a graph (local, remote, or inline).
 */
public record GraphReference(
    @NotBlank
    @Pattern(regexp = "inline|local|remote", message = "source must be 'inline', 'local', or 'remote'")
    String source,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
    String dataset,

    String graph,  // URI, "default", or "union"

    String endpoint,  // Remote SPARQL endpoint URL

    String data,  // Inline RDF content

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,255}$", message = "Invalid branch name")
    String branch,

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$",
             message = "Invalid commit ID (must be UUIDv7)")
    String commit,

    String asOf  // RFC3339 timestamp
) {
  // Validation methods to be added
}
```

#### 1.2 DataReference DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/DataReference.java`

```java
package org.chucc.vcserver.dto.shacl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * Reference to data graphs to be validated.
 */
public record DataReference(
    @NotBlank
    @Pattern(regexp = "local|remote", message = "source must be 'local' or 'remote'")
    String source,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
    String dataset,

    @NotEmpty(message = "graphs list cannot be empty")
    List<String> graphs,

    String endpoint,

    @Pattern(regexp = "^[A-Za-z0-9._-]{1,255}$", message = "Invalid branch name")
    String branch,

    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$",
             message = "Invalid commit ID (must be UUIDv7)")
    String commit,

    String asOf
) {
}
```

#### 1.3 ValidationOptions DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/ValidationOptions.java`

```java
package org.chucc.vcserver.dto.shacl;

import jakarta.validation.constraints.Pattern;

/**
 * Options for SHACL validation.
 */
public record ValidationOptions(
    @Pattern(regexp = "separately|merged|dataset", message = "validateGraphs must be 'separately', 'merged', or 'dataset'")
    String validateGraphs,  // Default: "separately"

    String targetNode,  // URI of specific resource to validate

    @Pattern(regexp = "Violation|Warning|Info", message = "severity must be 'Violation', 'Warning', or 'Info'")
    String severity  // Filter results by severity
) {
  public ValidationOptions {
    if (validateGraphs == null) {
      validateGraphs = "separately";
    }
  }
}
```

#### 1.4 ResultsConfig DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/ResultsConfig.java`

```java
package org.chucc.vcserver.dto.shacl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration for validation results.
 */
public record ResultsConfig(
    Boolean returnReport,  // Default: true

    @Valid
    StoreConfig store
) {
  public ResultsConfig {
    if (returnReport == null) {
      returnReport = true;
    }
  }

  public record StoreConfig(
      @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
      String dataset,

      String graph,

      Boolean overwrite  // Default: false
  ) {
    public StoreConfig {
      if (overwrite == null) {
        overwrite = false;
      }
    }
  }
}
```

#### 1.5 ValidationRequest DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/ValidationRequest.java`

```java
package org.chucc.vcserver.dto.shacl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * SHACL validation request.
 */
public record ValidationRequest(
    @NotNull(message = "shapes is required")
    @Valid
    GraphReference shapes,

    @NotNull(message = "data is required")
    @Valid
    DataReference data,

    @Valid
    ValidationOptions options,

    @Valid
    ResultsConfig results
) {
  public ValidationRequest {
    if (options == null) {
      options = new ValidationOptions(null, null, null);
    }
    if (results == null) {
      results = new ResultsConfig(true, null);
    }
  }
}
```

#### 1.6 ValidationResponse DTO
**File:** `src/main/java/org/chucc/vcserver/dto/shacl/ValidationResponse.java`

```java
package org.chucc.vcserver.dto.shacl;

/**
 * SHACL validation response (when storing results).
 */
public record ValidationResponse(
    String message,
    String commitId,
    Boolean conforms,
    String resultsGraph
) {
}
```

### Step 2: Create Custom Exceptions

#### 2.1 ShaclValidationException
**File:** `src/main/java/org/chucc/vcserver/exception/ShaclValidationException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when SHACL validation fails (engine error, not validation report).
 */
public class ShaclValidationException extends VcServerException {

  public ShaclValidationException(String message) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR, "validation_error");
  }

  public ShaclValidationException(String message, Throwable cause) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR, "validation_error", cause);
  }
}
```

#### 2.2 InvalidShapesException
**File:** `src/main/java/org/chucc/vcserver/exception/InvalidShapesException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when shapes graph is invalid.
 */
public class InvalidShapesException extends VcServerException {

  public InvalidShapesException(String message) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_shapes");
  }

  public InvalidShapesException(String message, Throwable cause) {
    super(message, HttpStatus.UNPROCESSABLE_ENTITY, "invalid_shapes", cause);
  }
}
```

#### 2.3 InvalidGraphReferenceException
**File:** `src/main/java/org/chucc/vcserver/exception/InvalidGraphReferenceException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when graph reference is invalid.
 */
public class InvalidGraphReferenceException extends VcServerException {

  public InvalidGraphReferenceException(String message) {
    super(message, HttpStatus.BAD_REQUEST, "invalid_graph_reference");
  }
}
```

### Step 3: Create Configuration Properties

**File:** `src/main/java/org/chucc/vcserver/config/ShaclValidationProperties.java`

```java
package org.chucc.vcserver.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for SHACL validation.
 */
@ConfigurationProperties(prefix = "chucc.shacl.validation")
@Validated
public record ShaclValidationProperties(
    Boolean enabled,  // Default: true

    @Min(1024)
    @Max(104857600)
    Integer maxShapesSize,  // Default: 10MB

    @Min(1024)
    @Max(1073741824)
    Integer maxDataSize,  // Default: 100MB

    @Min(1000)
    @Max(600000)
    Integer timeout,  // Default: 60 seconds

    @Min(1)
    @Max(100)
    Integer maxConcurrent,  // Default: 10

    RemoteConfig remote,

    CacheConfig cache,

    StorageConfig storage
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
  }

  public record RemoteConfig(
      Boolean enabled,
      Integer timeout,
      Integer maxResponseSize,
      Boolean blockPrivateIps,
      java.util.List<String> allowedSchemes
  ) {
  }

  public record CacheConfig(
      Boolean enabled,
      Integer ttl,
      Integer maxEntries
  ) {
  }

  public record StorageConfig(
      Boolean defaultOverwrite,
      Boolean enableVersioning
  ) {
  }
}
```

### Step 4: Create Controller Skeleton

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java`

```java
package org.chucc.vcserver.controller;

import jakarta.validation.Valid;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.chucc.vcserver.dto.shacl.ValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for SHACL validation operations.
 *
 * <p>Implements the SHACL Validation Protocol, enabling validation of RDF data
 * against SHACL shapes with flexible source options (inline, local datasets,
 * remote endpoints) and result persistence.</p>
 *
 * @see <a href="../../protocol/SHACL_Validation_Protocol.md">SHACL Validation Protocol</a>
 */
@RestController
@RequestMapping("/{dataset}/shacl")
public class ShaclValidationController {

  private static final Logger logger = LoggerFactory.getLogger(ShaclValidationController.class);

  /**
   * Validate data graphs against a shapes graph.
   *
   * <p>Supports multiple validation modes:</p>
   * <ul>
   *   <li>Inline shapes (Fuseki-compatible)</li>
   *   <li>Local graph references (same or different dataset)</li>
   *   <li>Remote SPARQL endpoints</li>
   *   <li>Historical validation (branch/commit/asOf selectors)</li>
   * </ul>
   *
   * <p>Results can be returned immediately and/or stored in a specified graph
   * for historical analysis.</p>
   *
   * @param dataset dataset name (path variable)
   * @param request validation request with shapes, data, options, and results config
   * @return validation report (200 OK) or storage confirmation (202 Accepted)
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> validateShacl(
      @PathVariable String dataset,
      @Valid @RequestBody ValidationRequest request
  ) {
    logger.info("SHACL validation request for dataset: {}", dataset);

    // TODO: Implementation in subsequent tasks
    // - Task 2: Basic inline validation
    // - Task 3: Local graph reference resolution
    // - Task 4-5: Cross-dataset and validation modes
    // - Task 6-7: Result storage with CQRS
    // - Task 8: Version control selectors
    // - Task 9-10: Remote endpoint support

    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body("SHACL validation not yet implemented");
  }
}
```

### Step 5: Add Configuration to Application

**File:** `src/main/java/org/chucc/vcserver/VcServerApplication.java`

Add to `@EnableConfigurationProperties`:
```java
@EnableConfigurationProperties({
    // ... existing properties
    ShaclValidationProperties.class
})
```

### Step 6: Add Default Configuration

**File:** `src/main/resources/application.yml`

```yaml
chucc:
  shacl:
    validation:
      enabled: true
      max-shapes-size: 10485760    # 10MB
      max-data-size: 104857600     # 100MB
      timeout: 60000               # 60 seconds
      max-concurrent: 10

      remote:
        enabled: true
        timeout: 30000             # 30 seconds
        max-response-size: 104857600
        block-private-ips: true
        allowed-schemes:
          - http
          - https

      cache:
        enabled: true
        ttl: 3600                  # 1 hour
        max-entries: 100

      storage:
        default-overwrite: false
        enable-versioning: true
```

### Step 7: Create Integration Test Skeleton

**File:** `src/test/java/org/chucc/vcserver/integration/ShaclValidationIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SHACL Validation Protocol.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ShaclValidationIT extends ITFixture {

  @Test
  void endpoint_shouldExist() {
    // Arrange
    String url = "/" + getDatasetName() + "/shacl";

    // Act
    ResponseEntity<String> response = restTemplate.postForEntity(
        url,
        "{}",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode())
        .as("Endpoint should exist (not 404)")
        .isNotEqualTo(HttpStatus.NOT_FOUND);
  }

  // Additional tests will be added in Task 2 (Basic Inline Validation)
}
```

---

## Testing Strategy

### Unit Tests (Not Required for This Task)
- DTOs are simple records with validation annotations
- Configuration properties are tested via integration tests

### Integration Tests
1. **Endpoint Exists** - Verify `/shacl` endpoint is accessible (not 404)
2. **Invalid Request** - Verify validation errors for malformed requests
3. **Configuration Loading** - Verify properties loaded correctly

**Test Pattern:** Projector DISABLED (API layer test only)

---

## Files to Create

**DTOs:**
1. `src/main/java/org/chucc/vcserver/dto/shacl/GraphReference.java`
2. `src/main/java/org/chucc/vcserver/dto/shacl/DataReference.java`
3. `src/main/java/org/chucc/vcserver/dto/shacl/ValidationOptions.java`
4. `src/main/java/org/chucc/vcserver/dto/shacl/ResultsConfig.java`
5. `src/main/java/org/chucc/vcserver/dto/shacl/ValidationRequest.java`
6. `src/main/java/org/chucc/vcserver/dto/shacl/ValidationResponse.java`

**Exceptions:**
7. `src/main/java/org/chucc/vcserver/exception/ShaclValidationException.java`
8. `src/main/java/org/chucc/vcserver/exception/InvalidShapesException.java`
9. `src/main/java/org/chucc/vcserver/exception/InvalidGraphReferenceException.java`

**Configuration:**
10. `src/main/java/org/chucc/vcserver/config/ShaclValidationProperties.java`

**Controller:**
11. `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java`

**Tests:**
12. `src/test/java/org/chucc/vcserver/integration/ShaclValidationIT.java`

**Total Files:** 12 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/VcServerApplication.java` - Add `@EnableConfigurationProperties`
2. `src/main/resources/application.yml` - Add SHACL configuration section

**Total Files:** 2 modified files

---

## Success Criteria

- ✅ All 12 new files created with proper Javadoc
- ✅ DTOs have validation annotations
- ✅ Controller endpoint responds (not 404)
- ✅ Configuration properties loaded correctly
- ✅ Integration test passes (endpoint exists)
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

# Run integration test
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
4. **Update** `.tasks/shacl/README.md` (mark Task 1 as completed)
5. **Proceed** to Task 2: Basic Inline Validation

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - Complete specification (§3.2, §6, §7, §13)
- **[Development Guidelines](../../.claude/CLAUDE.md)** - CHUCC development best practices
- **[Jackson Validation](https://www.baeldung.com/spring-boot-bean-validation)** - Bean validation guide
- **[RFC 7807](https://www.rfc-editor.org/rfc/rfc7807.html)** - Problem Details for HTTP APIs

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium
**Status:** Ready to start
