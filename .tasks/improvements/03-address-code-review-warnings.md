# Address Code Review Warnings and Suggestions

## Background

The Code Reviewer identified 4 warnings and 5 suggestions for improvement in the SPARQL Query implementation. While not critical, these should be addressed for production readiness.

---

## Warnings (Medium Priority)

### Warning 1: Content Negotiation Defaults

**Location**: `SparqlController.determineResultFormat()` (line 226)

**Issue**: When no Accept header provided, defaults to JSON without explanation.

**Current Code**:
```java
private ResultFormat determineResultFormat(String acceptHeader) {
  if (acceptHeader == null || acceptHeader.isEmpty()) {
    return ResultFormat.JSON;  // ⚠️ Silent default
  }
  // ...
}
```

**Improvement**: Add comment explaining default
```java
// Default to JSON per SPARQL 1.1 Protocol recommendation (section 2.1)
return ResultFormat.JSON;
```

**Tasks**:
- [ ] Add comment explaining default format choice
- [ ] Consider logging at DEBUG level when defaulting
- [ ] Document default in API documentation
- [ ] Add test for missing Accept header

---

### Warning 2: Re-enable Disabled Tests

**Location**: `SparqlQueryIntegrationTest.java` - 10 tests `@Disabled`

**Issue**: All integration tests disabled with "HTTP request configuration issue"

**Tasks**:
- [ ] Investigate root cause of HTTP configuration issue
- [ ] Fix the same way `SelectorValidationIntegrationTest` was fixed (use URI objects)
- [ ] Update tests to use `UriComponentsBuilder.build().toUri()`
- [ ] Remove `@Disabled` annotations
- [ ] Verify all 10 tests pass

---

### Warning 3: Format Validation

**Location**: `SparqlController.determineResultFormat()` and `SparqlQueryService`

**Issue**: No validation that result format matches query type
- SELECT/ASK should return JSON/XML/CSV/TSV
- CONSTRUCT/DESCRIBE should return Turtle/RDF+XML

**Current**: All formats accepted for all query types (may cause runtime errors)

**Tasks**:
- [ ] Add query type detection in SparqlQueryService
- [ ] Validate format compatibility before execution
- [ ] Return 406 Not Acceptable for invalid combinations
- [ ] Add test cases for invalid format + query type combinations

**Example**:
```java
if (query.isSelectType() && (format == ResultFormat.TURTLE || format == ResultFormat.RDF_XML)) {
  throw new NotAcceptableException("SELECT queries cannot return RDF formats");
}
```

---

### Warning 4: Domain-Specific Exceptions

**Location**: `DatasetService.materializeAtCommit()` (line 246)

**Issue**: Throws generic `IllegalArgumentException` instead of domain exception

**Current**:
```java
throw new IllegalArgumentException("Commit not found: " + commitId);
```

**Improvement**:
```java
throw new CommitNotFoundException("Commit not found: " + commitId);
```

**Tasks**:
- [ ] Replace `IllegalArgumentException` with `CommitNotFoundException`
- [ ] Update exception handling in controllers
- [ ] Verify consistent exception types across codebase
- [ ] Update Javadoc

---

## Suggestions (Low Priority / Future Improvements)

### Suggestion 1: Extract Content Negotiation Service

**Rationale**: `determineResultFormat()` and `getMediaType()` could be reused

**Proposed**:
- [ ] Create `ContentNegotiationService` class
- [ ] Move format detection logic
- [ ] Add support for quality values (q parameter)
- [ ] Support multiple Accept values with preference ordering

---

### Suggestion 2: Add Query Result Limits

**Rationale**: Prevent memory exhaustion from large result sets

**Proposed**:
- [ ] Add configuration property `sparql.query.max-results`
- [ ] Apply LIMIT clause if not present
- [ ] Return 413 Payload Too Large if result exceeds limit
- [ ] Document limit in API documentation

**Example Config**:
```properties
sparql.query.max-results=10000
```

---

### Suggestion 3: Add Query Execution Metrics

**Rationale**: Monitor performance and identify slow queries

**Proposed**:
- [ ] Add Micrometer metrics for query execution time
- [ ] Add counter for query types (SELECT, ASK, etc.)
- [ ] Add counter for result formats
- [ ] Log slow queries (> 1 second)

**Metrics**:
- `sparql.query.execution.time` (Timer)
- `sparql.query.type` (Counter with tags)
- `sparql.query.format` (Counter with tags)

---

### Suggestion 4: Enhanced Error Messages

**Rationale**: Help users debug malformed queries

**Current**:
```
"SPARQL query is malformed: Lexical error at line 1, column 7..."
```

**Improved**:
```
"SPARQL query is malformed: Lexical error at line 1, column 7.
Unexpected token '37' - check query syntax near 'SELECT%20*'.
See SPARQL 1.1 specification: https://www.w3.org/TR/sparql11-query/"
```

**Tasks**:
- [ ] Enhance error messages with context
- [ ] Add links to SPARQL specification
- [ ] Consider suggesting common fixes (e.g., URL encoding issues)

---

### Suggestion 5: Query Validation Service

**Rationale**: Separate validation from execution

**Proposed**:
- [ ] Create `SparqlQueryValidator` class
- [ ] Pre-validate queries before materialization
- [ ] Return detailed validation errors
- [ ] Consider caching validated queries

---

## Priority Order

1. **High**: Warnings 2, 3, 4 (fix disabled tests, validation, domain exceptions)
2. **Medium**: Warning 1 (content negotiation comments)
3. **Low**: Suggestions 1-5 (future enhancements)

## Acceptance Criteria

### Must Have (Warnings)
- [ ] All warnings addressed
- [ ] 10 disabled tests re-enabled and passing
- [ ] Format validation implemented
- [ ] Consistent domain exceptions

### Nice to Have (Suggestions)
- [ ] At least 2 suggestions implemented
- [ ] Metrics added for monitoring
- [ ] Query limits configured

## References

- Code Reviewer report
- `SparqlController.java`
- `SparqlQueryService.java`
- `SparqlQueryIntegrationTest.java`
- SPARQL 1.1 Protocol: https://www.w3.org/TR/sparql11-protocol/
