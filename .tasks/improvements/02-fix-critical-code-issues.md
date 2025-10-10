# Fix Critical Code Issues from Code Review

## Background

The Code Reviewer identified 3 critical issues in the SPARQL Query implementation that need immediate attention.

## Critical Issues

### 1. Resource Leak Risk in SparqlController

**Location**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java:142-177`

**Issue**: Dataset returned by `materializeAtCommit()` is not closed, potentially leaking memory.

```java
// ❌ Current code - no try-with-resources
Dataset dataset = datasetService.materializeAtCommit(datasetName, targetCommit);
String results = sparqlQueryService.executeQuery(dataset, query, format);
// Dataset never closed!
```

**Fix**: Use try-with-resources pattern

```java
// ✅ Fixed code
try (Dataset dataset = datasetService.materializeAtCommit(datasetName, targetCommit)) {
  String results = sparqlQueryService.executeQuery(dataset, query, format);
  return ResponseEntity.ok()
      .eTag("\"" + targetCommit.value() + "\"")
      .contentType(getMediaType(format))
      .body(results);
}
```

**Tasks**:
- [ ] Wrap Dataset usage in try-with-resources in `querySparqlGet()` method
- [ ] Verify Dataset implements AutoCloseable (it does via Jena API)
- [ ] Test with multiple concurrent queries to verify no leaks

---

### 2. Missing CommitNotFoundException Handling

**Location**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java:142-177`

**Issue**: `selectorResolutionService.resolve()` can throw `CommitNotFoundException`, but it's not caught.

**Current Exceptions Caught**:
- `BranchNotFoundException` ✅
- `IllegalArgumentException` ✅
- `CommitNotFoundException` ❌ Missing!

**Fix**: Add catch block for CommitNotFoundException

```java
try {
  // ... query execution
} catch (BranchNotFoundException | CommitNotFoundException e) {
  return problemDetailService.createNotFoundResponse(e.getMessage());
} catch (IllegalArgumentException e) {
  return problemDetailService.createBadRequestResponse(
      "MALFORMED_QUERY", e.getMessage());
}
```

**Tasks**:
- [ ] Add `CommitNotFoundException` to catch block
- [ ] Update exception handling to use multi-catch pattern
- [ ] Add test case for missing commit scenario
- [ ] Verify 404 response with proper Problem Details format

---

### 3. Incomplete Javadoc

**Location**: Multiple methods missing `@throws` tags

**Missing Documentation**:

1. `SparqlController.querySparqlGet()` (lines 117-181)
   - Missing: `@throws BranchNotFoundException`
   - Missing: `@throws CommitNotFoundException`
   - Missing: `@throws IllegalArgumentException`

2. `DatasetService.materializeAtCommit()` (lines 243-246)
   - Missing: `@throws CommitNotFoundException`

**Fix**: Add complete Javadoc

```java
/**
 * Handles GET requests to the SPARQL query endpoint.
 *
 * @param query the SPARQL query string
 * @param branch optional branch selector
 * @param commit optional commit selector
 * @param asOf optional timestamp selector
 * @param request the HTTP request for Accept header processing
 * @return query results with ETag header
 * @throws BranchNotFoundException if specified branch does not exist
 * @throws CommitNotFoundException if specified commit does not exist
 * @throws IllegalArgumentException if query is malformed or selectors conflict
 */
public ResponseEntity<?> querySparqlGet(...) {
```

**Tasks**:
- [ ] Add missing `@throws` tags to `SparqlController.querySparqlGet()`
- [ ] Add missing `@throws` tag to `DatasetService.materializeAtCommit()`
- [ ] Run Checkstyle to verify all Javadoc complete
- [ ] Consider adding `@throws` to `SparqlQueryService.executeQuery()` for QueryParseException

---

## Acceptance Criteria

- [ ] All 3 critical issues resolved
- [ ] No resource leaks (verified with try-with-resources)
- [ ] All exceptions properly handled and documented
- [ ] Checkstyle passes with zero violations
- [ ] New test cases added for exception scenarios
- [ ] Build succeeds: `mvn clean install`

## Testing Checklist

After fixes:
- [ ] Test concurrent queries (10+ simultaneous) - no memory leaks
- [ ] Test with non-existent commit ID - returns 404
- [ ] Test with non-existent branch - returns 404
- [ ] Test with malformed SPARQL - returns 400
- [ ] Verify all responses use Problem Details format

## References

- Code Reviewer report
- `SparqlController.java:142-177`
- `DatasetService.java:243-246`
- Jena Dataset API documentation
