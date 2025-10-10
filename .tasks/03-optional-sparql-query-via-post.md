# Task 03: Optional - Implement SPARQL Query via POST

**Status**: Not started (OPTIONAL)
**Priority**: Low
**Estimated Effort**: 4-6 hours
**Dependencies**: None

## Overview

Implement SPARQL Query via HTTP POST with `Content-Type: application/sparql-query`. Currently returns 501 Not Implemented.

**Current State**: SparqlController.java lines 202-208 returns 501

**Why Optional/Low Priority:**
- SPARQL 1.1 Protocol §2.1 recommends GET for queries
- Most SPARQL clients use GET for queries
- POST for queries is mainly for very long query strings (>2048 chars)
- GET implementation is complete and production-ready
- This feature can be added later without breaking existing functionality

## Use Cases

### When Query via POST is Useful
1. **Very long queries** that exceed URL length limits (~2048 chars)
2. **Queries with sensitive data** (though not recommended - use authentication instead)
3. **Client compatibility** if some clients require POST (rare)

### When Query via GET is Sufficient (99% of cases)
- Normal SELECT queries (<1000 chars)
- ASK, CONSTRUCT, DESCRIBE queries
- Any query that fits in URL
- All time-travel and versioned queries

## Implementation Plan

### Prerequisites
All dependencies already exist and work for GET:
- ✅ SelectorResolutionService (resolves selectors to commit)
- ✅ DatasetService (materializes dataset)
- ✅ SparqlQueryService (executes queries)
- ✅ Content negotiation logic (determineResultFormat)

### Step 1: Parse POST Body and Content-Type (1 hour)

**Location**: SparqlController.java `executeSparqlPost()` lines 196-208

**Current Code**:
```java
boolean isUpdate = contentType != null
    && contentType.toLowerCase(java.util.Locale.ROOT)
        .contains("application/sparql-update");

if (!isUpdate) {
  // Query operations via POST not yet implemented
  return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
      .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body("{\"title\":\"SPARQL Query via POST not yet implemented\","
          + "\"status\":501}");
}
```

**New Code**:
```java
boolean isUpdate = contentType != null
    && contentType.toLowerCase(java.util.Locale.ROOT)
        .contains("application/sparql-update");

boolean isQuery = contentType != null
    && contentType.toLowerCase(java.util.Locale.ROOT)
        .contains("application/sparql-query");

boolean isFormEncoded = contentType != null
    && contentType.toLowerCase(java.util.Locale.ROOT)
        .contains("application/x-www-form-urlencoded");

if (isQuery) {
  return handleQueryViaPost(body, branch, commit, asOf, vcCommit, request);
}

if (isFormEncoded) {
  // Parse form data to determine query vs update
  // Look for "query" or "update" parameter
  return handleFormEncodedRequest(body, branch, commit, asOf, vcCommit,
                                   ifMatch, message, author, request);
}

if (isUpdate) {
  // Existing UPDATE handling (lines 210-317)
  ...
}
```

### Step 2: Implement handleQueryViaPost() Method (2 hours)

```java
/**
 * Handle SPARQL Query via POST with application/sparql-query.
 *
 * @param queryString SPARQL query from POST body
 * @param branch target branch (optional)
 * @param commit target commit (optional)
 * @param asOf timestamp for time-travel (optional)
 * @param vcCommit commit ID from SPARQL-VC-Commit header (optional)
 * @param request HTTP request for Accept header
 * @return query results with ETag
 */
private ResponseEntity<?> handleQueryViaPost(
    String queryString,
    String branch,
    String commit,
    String asOf,
    String vcCommit,
    HttpServletRequest request) {

  // Validate selector mutual exclusion
  try {
    SelectorValidator.validateMutualExclusion(branch, commit, asOf);
  } catch (IllegalArgumentException e) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 400, "SELECTOR_CONFLICT"));
  }

  // Default dataset name
  String datasetName = "default";

  try {
    // 1. Resolve selectors to target commit
    CommitId targetCommit = selectorResolutionService.resolve(
        datasetName, branch, commit, asOf);

    // 2. Materialize dataset at that commit
    Dataset dataset = datasetService.materializeAtCommit(datasetName, targetCommit);

    // 3. Determine result format from Accept header
    ResultFormat format = determineResultFormat(request.getHeader("Accept"));

    // 4. Execute query
    String results = sparqlQueryService.executeQuery(dataset, queryString, format);

    // 5. Return results with ETag header containing commit ID
    return ResponseEntity.ok()
        .eTag("\"" + targetCommit.value() + "\"")
        .contentType(getMediaType(format))
        .body(results);

  } catch (QueryParseException e) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(
            "SPARQL query is malformed: " + e.getMessage(),
            400,
            "MALFORMED_QUERY"));
  } catch (BranchNotFoundException | CommitNotFoundException e) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 404, "NOT_FOUND"));
  } catch (IllegalArgumentException e) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 400, "INVALID_REQUEST"));
  }
}
```

**Note**: This is almost identical to `querySparqlGet()` - consider refactoring to extract common logic.

### Step 3: Add Header Parameters to executeSparqlPost() Signature (30 minutes)

**Current Signature**:
```java
public ResponseEntity<String> executeSparqlPost(
    @RequestBody String body,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Branch", required = false) String branch,
    @RequestHeader(name = "If-Match", required = false) String ifMatch,
    @RequestHeader(name = "Content-Type", required = false) String contentType
)
```

**New Parameters Needed**:
```java
public ResponseEntity<String> executeSparqlPost(
    @RequestBody String body,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Branch", required = false) String branch,
    @RequestHeader(name = "If-Match", required = false) String ifMatch,
    @RequestHeader(name = "Content-Type", required = false) String contentType,
    // NEW: Query selector parameters
    @Parameter(description = "Target commit for query (read-only)")
    @RequestParam(required = false) String commit,
    @Parameter(description = "Query branch state at or before this timestamp (ISO8601)")
    @RequestParam(required = false) String asOf,
    @Parameter(description = "Commit ID for read consistency")
    @RequestHeader(name = "SPARQL-VC-Commit", required = false) String vcCommit,
    HttpServletRequest request
)
```

### Step 4: Refactor Common Logic (Optional but Recommended) (1 hour)

Extract common query execution logic to avoid duplication:

```java
/**
 * Execute SPARQL query against versioned dataset.
 * Shared by querySparqlGet() and handleQueryViaPost().
 */
private ResponseEntity<?> executeQuery(
    String datasetName,
    String queryString,
    String branch,
    String commit,
    String asOf,
    HttpServletRequest request) {
  // Common logic from both methods
  // ... (see Step 2 for implementation)
}
```

Then both methods become:
```java
public ResponseEntity<?> querySparqlGet(...) {
  return executeQuery("default", query, branch, commit, asOf, request);
}

private ResponseEntity<?> handleQueryViaPost(...) {
  return executeQuery("default", queryString, branch, commit, asOf, request);
}
```

### Step 5: Add Integration Tests (1-2 hours)

**New Test Class**: `SparqlQueryPostIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlQueryPostIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlQueryPost_shouldExecuteWithQueryContentType() {
    // Given: Query in POST body with application/sparql-query
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    headers.set("Accept", "application/sparql-results+json");
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Results returned
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"head\"", "\"results\"");
    assertThat(response.getHeaders().getETag()).isNotNull();
  }

  @Test
  void sparqlQueryPost_shouldSupportAllSelectors() {
    // Test with branch, commit, asOf selectors
    // Similar to SparqlQueryIntegrationTest patterns
  }

  @Test
  void sparqlQueryPost_shouldHandleVeryLongQuery() {
    // Given: Very long query (>2048 chars) that would exceed GET URL limit
    StringBuilder longQuery = new StringBuilder("SELECT * WHERE { ");
    for (int i = 0; i < 100; i++) {
      longQuery.append("?s").append(i).append(" ?p").append(i).append(" ?o").append(i)
               .append(" . ");
    }
    longQuery.append("}");

    // When/Then: Query succeeds via POST
    // ...
  }

  @Test
  void sparqlQueryPost_shouldReturn400ForMalformedQuery() {
    // Test error handling
  }
}
```

**Tests Needed** (10 tests total):
1. Basic query with application/sparql-query
2. Query with branch selector
3. Query with commit selector
4. Query with asOf selector
5. Very long query (>2048 chars)
6. Content negotiation (JSON, XML)
7. Error: Malformed query
8. Error: Selector conflict
9. Error: Non-existent branch
10. ASK and CONSTRUCT queries

### Step 6: Update OpenAPI Documentation (30 minutes)

Update `@Operation` and `@ApiResponse` annotations in `executeSparqlPost()`:

```java
@Operation(
    summary = "SPARQL Query or Update (POST)",
    description = "Execute a SPARQL query or update using HTTP POST. "
        + "Content-Type application/sparql-query → Query operation (NEW). "
        + "Content-Type application/sparql-update → Update operation (creates commit). "
        + "Content-Type application/x-www-form-urlencoded → Query or update based on form fields."
)
```

Add response examples for query results.

## Acceptance Criteria

### Required
- [ ] POST with `Content-Type: application/sparql-query` executes queries
- [ ] All selectors work (branch, commit, asOf)
- [ ] Content negotiation works (JSON, XML, CSV, TSV, Turtle, RDF/XML)
- [ ] ETag header contains commit ID
- [ ] Error handling matches GET implementation
- [ ] 10 integration tests passing
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings
- [ ] OpenAPI documentation updated

### Optional
- [ ] Code refactored to share logic between GET and POST
- [ ] Form-encoded requests handled (query parameter in POST body)

## Testing Strategy

### Manual Testing
```bash
# Test 1: Simple query via POST
curl -X POST http://localhost:3030/sparql?branch=main \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data "SELECT * WHERE { ?s ?p ?o } LIMIT 10"

# Expected: 200 OK with JSON results and ETag header

# Test 2: Very long query (exceeds GET URL limit)
curl -X POST http://localhost:3030/sparql?branch=main \
  -H "Content-Type: application/sparql-query" \
  --data @very-long-query.sparql

# Expected: 200 OK (query too long for GET)
```

### Automated Testing
```bash
mvn test -Dtest=SparqlQueryPostIntegrationTest
mvn clean install
```

## Estimated Effort Breakdown

| Task | Time | Complexity |
|------|------|------------|
| Parse Content-Type and route | 30 min | Low |
| Implement handleQueryViaPost() | 2 hours | Medium |
| Add parameters to signature | 30 min | Low |
| Refactor common logic (optional) | 1 hour | Medium |
| Write integration tests | 1-2 hours | Medium |
| Update documentation | 30 min | Low |
| **Total** | **4-6 hours** | **Medium** |

## Why This is Optional

1. **Low Usage**: <1% of SPARQL clients use POST for queries
2. **GET Works**: Current GET implementation handles 99% of use cases
3. **No Blocker**: System is production-ready without this
4. **Easy to Add Later**: Can be implemented without breaking changes
5. **Cost vs Benefit**: 4-6 hours for rarely-used feature

## When to Implement

Consider implementing when:
- Users specifically request it
- You encounter very long queries (>2048 chars) that fail via GET
- Client compatibility requires it
- You have spare time and want completeness

## References

**Specifications**:
- SPARQL 1.1 Protocol §2.1.2 - query via POST using application/sparql-query
- SPARQL 1.1 Protocol §2.1.3 - query via POST using form-encoded

**Implementation Files**:
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java`
  - Lines 202-208: Current 501 stub
  - Lines 86-107: GET implementation to mirror

**Reference Tests**:
- `src/test/java/org/chucc/vcserver/integration/SparqlQueryIntegrationTest.java`
  - Copy patterns for POST tests
