# Task: Implement Time-Travel SPARQL Query Tests

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 1 session (3-4 hours)
**Dependencies:** Requires event publishing to be implemented (see testing/01-enable-full-cqrs-event-flow.md)

---

## Context

Currently, CHUCC Server supports **time-travel query parameters** (`asOf`), but **query functionality is not fully implemented**:
- ✅ HTTP parameter validation works (`asOf` parameter accepted)
- ✅ Invalid timestamps return appropriate errors
- ✅ Mutually exclusive selectors validated (asOf + commit returns 400)
- ❌ **Actual time-travel queries don't return historical data correctly**

**Current State:**
- `TimeTravelQueryIT.java` tests only HTTP contract (parameter validation)
- No tests verify that queries return correct historical data
- TODO comment at line 169 lists tests to add once SPARQL query functionality is complete

**Problem:**
- Cannot verify that time-travel queries actually work end-to-end
- No validation that historical state is reconstructed correctly
- Query results may not reflect the dataset state at the requested timestamp

**Goal:**
Add comprehensive integration tests that verify time-travel SPARQL queries return correct historical data.

---

## Test Scenarios

The TODO comment at `TimeTravelQueryIT.java:169-197` specifies 5 test scenarios:

### 1. Query with asOf at T1 (Initial Data)
**Test:** `queryWithAsOfAtT1_shouldReturnInitialData()`

**Scenario:**
```
T1: Create dataset with initial data via PUT
T2: Update data via PUT
Query: asOf=T1
Expected: Results contain only T1 data (NOT T2 data)
```

### 2. Query with asOf at T2 (Updated Data)
**Test:** `queryWithAsOfAtT2_shouldReturnUpdatedData()`

**Scenario:**
```
T1: Create dataset with data A via PUT
T2: Update dataset with data B via PUT
Query: asOf=T2
Expected: Results contain data B (NOT data A)
```

### 3. Query with asOf at T3 (After Deletion)
**Test:** `queryWithAsOfAtT3_shouldReturnLatestData()`

**Scenario:**
```
T1: Create dataset via PUT
T2: Update dataset via PUT
T3: Delete graph via DELETE
Query: asOf=T3
Expected: Results reflect deletion (empty graph or no results)
```

### 4. Query with asOf Between T1 and T2
**Test:** `queryWithAsOfBetweenT1AndT2_shouldReturnT1State()`

**Scenario:**
```
T1: Create at 10:00 AM
T2: Update at 11:00 AM
Query: asOf=10:30 AM (between T1 and T2)
Expected: Results contain T1 state (most recent commit before asOf)
```

### 5. Query without asOf (Current State)
**Test:** `queryWithoutAsOf_shouldReturnCurrentState()`

**Scenario:**
```
T1: Create data
T2: Update data
Query: No asOf parameter
Expected: Results contain T2 data (latest commit on branch)
```

---

## Design Decisions

### 1. Test Data Strategy

**Use Graph Store Protocol API:**
```java
// Create commits via HTTP PUT (not direct repository access)
restTemplate.exchange(
    "/data?default=true&branch=main",
    HttpMethod.PUT,
    new HttpEntity<>(TURTLE_DATA, headers),
    String.class
);
```

**Why:**
- Tests end-to-end flow (HTTP → Command → Event → Projector)
- Matches real-world usage
- Validates complete CQRS cycle

### 2. Projector Enablement

**Required:**
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

**Why:**
- Time-travel queries need commits in repository
- Commits are created by projector consuming events
- Without projector, repository remains empty

### 3. Timing Control

**Use explicit timestamps:**
```java
Instant t1 = Instant.parse("2025-10-21T10:00:00Z");
Instant t2 = Instant.parse("2025-10-21T11:00:00Z");
Instant t3 = Instant.parse("2025-10-21T12:00:00Z");
```

**Challenges:**
- Commit timestamps are generated server-side
- Cannot force specific commit timestamp via HTTP API

**Solution Options:**

**Option 1: Query between timestamps** (Recommended)
```java
// Create commit
ResponseEntity<String> response = restTemplate.exchange(...);
String etag = response.getHeaders().getETag();

// Extract commit timestamp from repository
Instant commitTime = getCommitTimestamp(etag);

// Query with asOf after this commit
query(asOf = commitTime.plusSeconds(1));
```

**Option 2: Wait and verify ordering**
```java
// Create at T1
Instant beforeFirstCommit = Instant.now();
Thread.sleep(1000);
ResponseEntity<String> response1 = restTemplate.exchange(...);
Instant afterFirstCommit = Instant.now();

// T1 commit timestamp is between beforeFirstCommit and afterFirstCommit
// Query with asOf = afterFirstCommit to ensure it's after T1
```

**Decision:** Use Option 1 (query repository for actual commit timestamps).

### 4. SPARQL Query Format

**Use simple queries for testing:**
```sparql
SELECT ?s ?p ?o WHERE {
  ?s ?p ?o .
}
```

**Why:**
- Easy to verify results
- Avoids query complexity issues
- Focuses on time-travel functionality

---

## Implementation Plan

### Step 1: Update TimeTravelQueryIT Class

**File:** `src/test/java/org/chucc/vcserver/integration/TimeTravelQueryIT.java`

**Changes:**
1. Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
2. Inject `CommitRepository` to query commit timestamps
3. Add helper method to create commits via HTTP
4. Add helper method to execute SPARQL queries with asOf parameter

**Example helper methods:**
```java
@Autowired
private CommitRepository commitRepository;

/**
 * Create a commit via HTTP PUT and return its timestamp.
 */
private Instant createCommitAndGetTimestamp(String turtle, String message) {
  HttpHeaders headers = new HttpHeaders();
  headers.set("Content-Type", "text/turtle");
  headers.set("SPARQL-VC-Author", "TestUser");
  headers.set("SPARQL-VC-Message", message);

  ResponseEntity<String> response = restTemplate.exchange(
      "/data?default=true&branch=main",
      HttpMethod.PUT,
      new HttpEntity<>(turtle, headers),
      String.class
  );

  String commitId = response.getHeaders().getETag().replace("\"", "");

  // Wait for projector
  await().atMost(Duration.ofSeconds(10))
      .until(() -> commitRepository.findById(commitId).isPresent());

  Commit commit = commitRepository.findById(commitId).orElseThrow();
  return commit.getTimestamp();
}

/**
 * Execute SPARQL query with asOf parameter.
 */
private ResponseEntity<String> queryWithAsOf(String query, Instant asOf) {
  URI uri = UriComponentsBuilder.fromPath("/sparql")
      .queryParam("query", query)
      .queryParam("branch", "main")
      .queryParam("asOf", asOf.toString())
      .build()
      .toUri();

  return restTemplate.getForEntity(uri, String.class);
}
```

### Step 2: Implement Test 1 - Query at T1

```java
@Test
void queryWithAsOfAtT1_shouldReturnInitialData() {
  // Given - Create initial commit at T1
  String initialData = """
      @prefix : <http://example.org/> .
      :alice :name "Alice" .
      """;
  Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

  // And - Update data at T2
  String updatedData = """
      @prefix : <http://example.org/> .
      :bob :name "Bob" .
      """;
  Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

  // When - Query with asOf=T1 (should see initial data only)
  String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
  ResponseEntity<String> response = queryWithAsOf(query, t1);

  // Then - Should return HTTP 200
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // And - Results should contain "Alice" (T1 data)
  assertThat(response.getBody()).contains("Alice");

  // And - Results should NOT contain "Bob" (T2 data)
  assertThat(response.getBody()).doesNotContain("Bob");
}
```

### Step 3: Implement Test 2 - Query at T2

```java
@Test
void queryWithAsOfAtT2_shouldReturnUpdatedData() {
  // Given - Create initial commit
  String initialData = """
      @prefix : <http://example.org/> .
      :alice :name "Alice" .
      """;
  Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

  // And - Update data (replaces, not merges)
  String updatedData = """
      @prefix : <http://example.org/> .
      :bob :name "Bob" .
      """;
  Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

  // When - Query with asOf=T2 (should see updated data only)
  String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
  ResponseEntity<String> response = queryWithAsOf(query, t2);

  // Then - Should return HTTP 200
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // And - Results should contain "Bob" (T2 data)
  assertThat(response.getBody()).contains("Bob");

  // And - Results should NOT contain "Alice" (T1 data, replaced by PUT)
  assertThat(response.getBody()).doesNotContain("Alice");
}
```

### Step 4: Implement Test 3 - Query After Deletion

```java
@Test
void queryWithAsOfAtT3_shouldReturnEmptyAfterDeletion() {
  // Given - Create initial data
  String initialData = """
      @prefix : <http://example.org/> .
      :alice :name "Alice" .
      """;
  Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

  // And - Update data
  String updatedData = """
      @prefix : <http://example.org/> .
      :bob :name "Bob" .
      """;
  Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

  // And - Delete graph at T3
  HttpHeaders deleteHeaders = new HttpHeaders();
  deleteHeaders.set("SPARQL-VC-Author", "TestUser");
  deleteHeaders.set("SPARQL-VC-Message", "Delete graph");

  ResponseEntity<Void> deleteResponse = restTemplate.exchange(
      "/data?default=true&branch=main",
      HttpMethod.DELETE,
      new HttpEntity<>(deleteHeaders),
      Void.class
  );

  String commitId = deleteResponse.getHeaders().getETag().replace("\"", "");

  // Wait for projector
  await().atMost(Duration.ofSeconds(10))
      .until(() -> commitRepository.findById(commitId).isPresent());

  Instant t3 = commitRepository.findById(commitId).orElseThrow().getTimestamp();

  // When - Query with asOf=T3 (after deletion)
  String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
  ResponseEntity<String> response = queryWithAsOf(query, t3);

  // Then - Should return HTTP 200
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // And - Results should be empty (graph was deleted)
  // Note: Exact format depends on SPARQL result serialization
  assertThat(response.getBody())
      .satisfiesAnyOf(
          body -> assertThat(body).contains("\"results\":{\"bindings\":[]}"),  // JSON
          body -> assertThat(body).contains("0 results")  // Other format
      );
}
```

### Step 5: Implement Test 4 - Query Between T1 and T2

```java
@Test
void queryWithAsOfBetweenT1AndT2_shouldReturnT1State() {
  // Given - Create at T1
  String initialData = """
      @prefix : <http://example.org/> .
      :alice :name "Alice" .
      """;
  Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

  // And - Wait 2 seconds to ensure timestamp difference
  Thread.sleep(2000);

  // And - Update at T2
  String updatedData = """
      @prefix : <http://example.org/> .
      :bob :name "Bob" .
      """;
  Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

  // When - Query with asOf between T1 and T2
  Instant asOf = t1.plusMillis((t2.toEpochMilli() - t1.toEpochMilli()) / 2);  // Midpoint
  String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
  ResponseEntity<String> response = queryWithAsOf(query, asOf);

  // Then - Should return HTTP 200
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // And - Results should contain T1 state (most recent before asOf)
  assertThat(response.getBody()).contains("Alice");
  assertThat(response.getBody()).doesNotContain("Bob");
}
```

### Step 6: Implement Test 5 - Query Without asOf

```java
@Test
void queryWithoutAsOf_shouldReturnCurrentState() {
  // Given - Create initial data
  String initialData = """
      @prefix : <http://example.org/> .
      :alice :name "Alice" .
      """;
  Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

  // And - Update data (current state)
  String updatedData = """
      @prefix : <http://example.org/> .
      :bob :name "Bob" .
      """;
  Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

  // When - Query without asOf parameter (should return current state)
  URI uri = UriComponentsBuilder.fromPath("/sparql")
      .queryParam("query", "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }")
      .queryParam("branch", "main")
      .build()
      .toUri();

  ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

  // Then - Should return HTTP 200
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

  // And - Results should contain current state (T2 data)
  assertThat(response.getBody()).contains("Bob");
  assertThat(response.getBody()).doesNotContain("Alice");
}
```

---

## Success Criteria

- [ ] All 5 tests implemented and passing
- [ ] Tests use Graph Store Protocol API to create commits (not direct repository access)
- [ ] Tests enable projector via `@TestPropertySource`
- [ ] Tests use `await()` for eventual consistency
- [ ] Query results verified to contain correct historical data
- [ ] Zero Checkstyle, SpotBugs, PMD violations
- [ ] All existing tests still pass (~911+ tests)
- [ ] TODO comment in TimeTravelQueryIT.java removed

---

## Rollback Plan

If tests reveal bugs in time-travel query implementation:

1. **Mark tests as @Disabled** with reason:
   ```java
   @Disabled("Time-travel query implementation needs fix - see issue #123")
   @Test
   void queryWithAsOfAtT1_shouldReturnInitialData() { ... }
   ```

2. **Document issues** in GitHub issues or task files

3. **Keep tests in codebase** - They serve as specification for correct behavior

---

## Dependencies

**Blocked by:**
- Event publishing must be implemented (see `testing/01-enable-full-cqrs-event-flow.md`)
- Without event publishing, commits won't be created in repository
- Projector must be working (already implemented)

**After event publishing is complete:**
- These tests can be implemented immediately
- No additional infrastructure changes needed

---

## Performance Considerations

**Test Execution Time:**
- Each test creates 2-3 commits via HTTP
- Each commit requires event publishing + projection (~100-500ms)
- Total per test: ~2-5 seconds
- 5 tests: ~10-25 seconds total

**Optimization:**
- Share dataset setup across tests (trade-off: less isolation)
- Use parallel test execution (Spring Boot test isolation)

---

## Documentation Updates

After implementation:

1. **TimeTravelQueryIT.java**
   - Remove TODO comment
   - Update class-level Javadoc to reflect new tests

2. **API Documentation** (`docs/api/openapi-guide.md`)
   - Add examples of time-travel queries
   - Document expected behavior for asOf parameter

3. **Version Control Extension Spec** (`.claude/protocol/`)
   - Ensure time-travel query examples are accurate

---

## Future Enhancements

After basic time-travel query tests:

1. **Complex Queries**
   - Test UNION, OPTIONAL, FILTER with time-travel
   - Verify graph patterns work correctly

2. **Performance Testing**
   - Benchmark time-travel query performance
   - Test with many commits (100+, 1000+)

3. **Edge Cases**
   - asOf before any commits (should return 404 or empty)
   - asOf far in future (should return latest)
   - asOf with microsecond precision

---

## References

- TODO comment: `TimeTravelQueryIT.java:169-197`
- Dependency task: `.tasks/testing/01-enable-full-cqrs-event-flow.md`
- [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/)
- [Version Control Extension](../../.claude/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Notes

**Why This Is Important:**
- Time-travel queries are a core feature of version-controlled SPARQL
- Without these tests, we cannot verify correctness
- Tests serve as living documentation of expected behavior

**Complexity Level:** Medium
- Requires understanding SPARQL result formats
- Needs careful timing control
- Depends on event publishing (external dependency)
