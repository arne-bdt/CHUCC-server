# Task 20: OpenAPI Documentation Completion

## Objective
Complete and polish OpenAPI/Swagger documentation for all Graph Store Protocol endpoints.

## Background
Well-documented APIs are essential for client developers. OpenAPI provides interactive documentation and client generation.

## Tasks

### 1. Review Current Documentation
- Examine existing OpenAPI annotations in GraphStoreController
- Check Swagger UI (typically at /swagger-ui.html)
- Identify missing or incomplete documentation

### 2. Enhance Endpoint Documentation
For each endpoint (GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS):
- Add detailed @Operation description
- Document all parameters with @Parameter
- Document all response codes with @ApiResponse
- Add example request/response bodies using @Content and @Schema

### 3. Add Schema Definitions
Create @Schema annotations for:
- BatchGraphsRequest
- BatchGraphsResponse
- ProblemDetail (error responses)
- CommitResponse

### 4. Add Examples
Add @ExampleObject annotations with realistic examples:
- PUT request with Turtle content
- PATCH request with RDF Patch
- Batch request with multiple operations
- Error responses for common cases

### 5. Document Version Control Features
Ensure documentation explains:
- How selectors work (branch, commit, asOf)
- How ETag and If-Match work
- How conflict detection works
- Relationship to SPARQL Protocol

### 6. Test Documentation
- Start application and access Swagger UI
- Verify all endpoints are documented
- Test "Try it out" functionality
- Verify examples are valid
- Check for broken links or missing references

### 7. Generate Client Code (Optional)
Test OpenAPI spec quality by generating client code:
- Use OpenAPI Generator to create Java/TypeScript client
- Verify generated code is usable
- Fix any schema issues

## Acceptance Criteria
- [ ] All endpoints fully documented in OpenAPI
- [ ] Examples provided for all operations
- [ ] Swagger UI is complete and functional
- [ ] Documentation explains version control features
- [ ] Client generation works (if optional task done)
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 19 (error handling polish)

## Estimated Complexity
Low-Medium (3-4 hours)
