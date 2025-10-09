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
