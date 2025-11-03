# OpenAPI Documentation Guide

## Overview

The SPARQL 1.2 Protocol Version Control Extension provides comprehensive OpenAPI 3.0 documentation
for all API endpoints. The documentation is interactive and can be accessed through Swagger UI.

## Accessing Swagger UI

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Access Swagger UI in your browser:**
   ```
   http://localhost:8080/swagger-ui.html
   ```

   Alternative URL:
   ```
   http://localhost:8080/swagger-ui/index.html
   ```

3. **Access OpenAPI JSON specification:**
   ```
   http://localhost:8080/v3/api-docs
   ```

4. **Access OpenAPI YAML specification:**
   ```
   http://localhost:8080/v3/api-docs.yaml
   ```

## What's Documented

### API Information

- **Title**: SPARQL 1.2 Protocol Version Control Extension
- **Version**: 1.0.0
- **Description**: Comprehensive overview of version control features
- **Contact**: CHUCC Project
- **License**: Apache 2.0

### Graph Store Protocol Endpoints

All GSP endpoints are fully documented with:

- **OPTIONS /data**: Capability discovery
- **GET /data**: Retrieve graph content
- **HEAD /data**: Check graph existence
- **PUT /data**: Create or replace graph
- **POST /data**: Merge data into graph
- **DELETE /data**: Remove graph
- **PATCH /data**: Apply RDF Patch to graph

Each endpoint includes:

- Summary and detailed description
- Parameter documentation (query params, headers)
- Request body schemas with examples
- Response codes with schemas
- Example responses for success and error cases

### Request/Response Schemas

All DTOs have comprehensive @Schema annotations:

1. **ProblemDetail**: RFC 7807 error responses
   - Machine-readable error codes
   - Human-readable messages
   - Hints for resolution
   - Example: `selector_conflict` error

2. **CommitResponse**: Commit metadata
   - Commit ID (UUIDv7)
   - Parent commits
   - Author and message
   - Timestamp (RFC 3339)

3. **BatchGraphsRequest**: Batch operation requests
   - Mode: single or multiple commits
   - Branch, author, message
   - List of graph operations (PUT, POST, PATCH, DELETE)
   - Example request with multiple operations

4. **BatchGraphsResponse**: Batch operation results
   - List of commits created
   - Operation descriptions per commit

### Version Control Features

The documentation explains:

- **Selectors**: How to use `branch`, `commit`, `asOf` parameters
- **Time-Travel Queries**: RFC 3339 timestamp format and behavior
- **ETags**: Commit IDs for optimistic locking
- **If-Match**: Precondition checking for concurrent writes
- **Conflict Detection**: How concurrent modifications are handled

### Error Handling

All error responses documented with:

- HTTP status codes (400, 404, 409, 412, 415, 422, 501)
- RFC 7807 problem+json format
- Error codes: `selector_conflict`, `graph_not_found`, `precondition_failed`, etc.
- Example error responses with hints

## Testing with Swagger UI

### Try It Out Feature

Swagger UI provides an interactive "Try it out" button for each endpoint:

1. **GET /data** - Retrieve a graph:
   ```
   Parameters:
   - graph: http://example.org/graph1
   - branch: main
   ```

2. **PUT /data** - Create a graph:
   ```
   Parameters:
   - graph: http://example.org/graph1
   - branch: main

   Headers:
   - SPARQL-VC-Author: Alice
   - SPARQL-VC-Message: Create graph
   - Content-Type: text/turtle

   Body:
   @prefix ex: <http://example.org/> .
   ex:subject ex:predicate "value" .
   ```

3. **GET /data with Time-Travel**:
   ```
   Parameters:
   - graph: http://example.org/graph1
   - asOf: 2025-10-01T12:00:00Z
   ```

### Response Examples

Each endpoint shows:

- **Success responses**: Status code, headers (ETag, Location), body
- **Error responses**: Problem+json with error code and hint

### Schemas Section

The "Schemas" section at the bottom shows:

- All DTO schemas with field descriptions
- Example values for each field
- Required vs optional fields
- Enum values for constrained fields

## Client Code Generation

The OpenAPI specification can be used to generate client code:

### Java Client

```bash
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./generated/java-client
```

### TypeScript Client

```bash
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g typescript-axios \
  -o ./generated/typescript-client
```

### Python Client

```bash
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g python \
  -o ./generated/python-client
```

## Customizing Documentation

### Adding Examples

To add more examples to endpoints, use `@ExampleObject`:

```java
@ApiResponse(
    responseCode = "200",
    content = @Content(
        mediaType = "text/turtle",
        examples = @ExampleObject(
            name = "Simple triple",
            value = "<http://ex.org/s> <http://ex.org/p> \"value\" ."
        )
    )
)
```

### Grouping Endpoints

Endpoints are grouped by `@Tag`:

```java
@Tag(name = "Graph Store Protocol",
     description = "SPARQL 1.2 GSP operations with version control")
```

### Hiding Endpoints

To hide an endpoint from documentation:

```java
@Operation(hidden = true)
```

## Maintaining OpenAPI Documentation

### When to Update

The OpenAPI specification should be updated whenever endpoints are added, modified, or removed.

#### Automatic Documentation (No Action Required)

**Protocol-standard endpoints** are automatically documented via SpringDoc annotations:

- SPARQL endpoints (`/sparql`)
- Version control endpoints (`/version/branches`, `/version/commits`, `/version/tags`, `/version/merge`, etc.)
- Graph Store Protocol endpoints (`/data`)

These endpoints are fully documented through Spring annotations (`@Operation`, `@ApiResponse`, `@Schema`). Changes to controller methods automatically update the OpenAPI spec.

#### Manual Documentation Required

**CHUCC-specific extensions** require explicit OpenAPI specification updates when added or modified:

- **Dataset management**: `POST /version/datasets/{name}`, `DELETE /version/datasets/{name}`
- **Batch graphs**: `POST /version/batch-graphs`
- **Operational endpoints**: `/actuator/*` endpoints

**Why manual?** These endpoints may use custom serialization, non-standard patterns, or be outside the standard Spring MVC flow.

### Update Checklist

When adding a new CHUCC-specific extension endpoint:

- [ ] **Controller annotations complete:**
  - `@Operation` with summary and description
  - `@ApiResponse` for all status codes (2xx, 4xx, 5xx)
  - `@Parameter` for all path/query parameters
  - `@RequestBody` with schema reference

- [ ] **DTO schemas documented:**
  - `@Schema` annotation at class level with description
  - `@Schema` annotation on all fields with descriptions
  - Example values provided via `example` attribute
  - Required fields marked with `@NotNull` or `required = true`

- [ ] **Error responses documented:**
  - All possible error codes listed (400, 404, 409, 412, 422, etc.)
  - Problem+json format with error code examples
  - Hints for resolution included in examples

- [ ] **Documentation files updated:**
  - [ ] `docs/api/api-extensions.md` - Add endpoint with full details
  - [ ] `docs/api/spec-compliance.md` - Add to "CHUCC-Specific Extensions" section
  - [ ] `.claude/CLAUDE.md` - Add integration test examples if relevant

- [ ] **Validation:**
  - [ ] Start application and access Swagger UI: `http://localhost:8080/swagger-ui.html`
  - [ ] Verify endpoint appears with correct details
  - [ ] Test "Try it out" functionality
  - [ ] Generate client code to verify schema correctness
  - [ ] Check OpenAPI JSON is valid: `http://localhost:8080/v3/api-docs`

### Example: Adding a New Endpoint

**Step 1: Controller annotations**

```java
@PostMapping("/version/datasets/{name}")
@Operation(
    summary = "Create dataset",
    description = "Create a new dataset with automatic Kafka topic creation"
)
@ApiResponse(
    responseCode = "202",
    description = "Dataset creation accepted (eventual consistency)",
    headers = {
        @Header(name = "Location", description = "URL of the created dataset"),
        @Header(name = "SPARQL-VC-Status", description = "Status: pending")
    },
    content = @Content(
        mediaType = MediaType.APPLICATION_JSON_VALUE,
        schema = @Schema(implementation = DatasetCreationResponse.class)
    )
)
@ApiResponse(
    responseCode = "400",
    description = "Invalid dataset name or request",
    content = @Content(
        mediaType = "application/problem+json",
        schema = @Schema(implementation = ProblemDetail.class)
    )
)
@ApiResponse(
    responseCode = "409",
    description = "Dataset already exists",
    content = @Content(
        mediaType = "application/problem+json",
        schema = @Schema(implementation = ProblemDetail.class)
    )
)
public ResponseEntity<DatasetCreationResponse> createDataset(
    @Parameter(description = "Dataset name (Kafka-compatible)")
    @PathVariable String name,
    @RequestBody(required = false) CreateDatasetRequest request,
    @Parameter(description = "Author of the dataset creation")
    @RequestHeader(value = "SPARQL-VC-Author", required = false) String author
) {
    // Implementation
}
```

**Step 2: DTO schemas**

```java
@Schema(description = "Request for creating a new dataset")
public record CreateDatasetRequest(
    @Schema(description = "Optional dataset description", example = "Production RDF dataset")
    String description,

    @Schema(description = "Optional initial graph URI", example = "http://example.org/initial")
    String initialGraph,

    @Schema(description = "Custom Kafka topic configuration (null uses defaults)")
    KafkaTopicConfig kafka
) {
    // ...
}
```

**Step 3: Update api-extensions.md**

Add full documentation with:
- Endpoint path and method
- Purpose and rationale
- Request/response formats with examples
- Status codes
- Configuration notes

**Step 4: Update spec-compliance.md**

Add row to "CHUCC-Specific Extensions" table:
```markdown
| POST /version/datasets/{name} | ✅ Implemented | Create dataset | Multi-tenant deployments |
```

**Step 5: Validate**

```bash
# Start application
mvn spring-boot:run

# Open Swagger UI
open http://localhost:8080/swagger-ui.html

# Verify endpoint appears and "Try it out" works

# Generate test client
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./generated/test-client

# Verify no generation errors
```

### Common Pitfalls

**Missing required fields:**
- Forgetting `@NotNull` or `required = true` on mandatory fields
- Results in generated clients not enforcing validation

**Inconsistent error codes:**
- Documenting 400 but actually returning 422
- Always verify status codes match implementation

**Outdated examples:**
- Examples using deprecated fields or incorrect formats
- Keep examples synchronized with schema changes

**Missing DTOs:**
- Referenced schema not having `@Schema` annotations
- Results in empty schema in OpenAPI spec

### Maintenance Schedule

**On every PR:**
- Verify Swagger UI shows correct documentation
- Check that new endpoints have complete annotations
- Run static analysis: `mvn clean compile`

**Quarterly:**
- Review all endpoints for documentation accuracy
- Update examples with realistic data
- Validate generated client code against latest spec
- Check for deprecated endpoints that should be removed

## Configuration

OpenAPI configuration is in `src/main/java/org/chucc/vcserver/config/OpenApiConfig.java`:

- API title, description, version
- Contact information
- License information
- Server URLs

## Troubleshooting

### Swagger UI not accessible

- Verify application is running: `curl http://localhost:8080/actuator/health`
- Check SpringDoc is in dependencies: `springdoc-openapi-starter-webmvc-ui`

### Missing endpoints

- Ensure `@RestController` and `@RequestMapping` annotations are present
- Check that methods are public
- Verify `@Operation` annotation is present

### Schema not showing

- Ensure DTO has `@Schema` annotation at class level
- Verify fields have `@Schema` annotations
- Check that getters/setters are present (required for Jackson)

## References

- [SpringDoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI Specification 3.0](https://swagger.io/specification/)
- [OpenAPI Generator](https://openapi-generator.tech/)
- [RFC 7807 Problem Details](https://tools.ietf.org/html/rfc7807)
- [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/)
