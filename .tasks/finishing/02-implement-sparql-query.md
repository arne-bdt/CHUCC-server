# Task 02: Implement SPARQL Query Endpoint

## Objective

Implement `/sparql` GET endpoint to execute SPARQL queries with version control selector support (branch, commit, asOf).

## Background

Currently, `SparqlController.querySparqlGet()` returns 501 Not Implemented. This task completes the SPARQL 1.2 Protocol query endpoint with full version control integration.

**Current State**:
- ✅ Controller skeleton exists
- ✅ Selector validation in place (mutual exclusion)
- ✅ OPTIONS endpoint works
- ❌ No query execution engine
- ❌ No dataset materialization
- ❌ No integration tests

## Implementation Plan

### Phase 1: Dataset Materialization Service (4-6 hours)

**Objective**: Create/enhance DatasetService to materialize dataset at specific commits

**File**: `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**New Method**:
```java
/**
 * Materializes dataset at specific commit by replaying history.
 *
 * @param dataset dataset name
 * @param commitId target commit
 * @return materialized Dataset
 */
public Dataset materializeAtCommit(String dataset, CommitId commitId) {
  // 1. Get commit chain from commitId back to initial commit
  List<Commit> commits = getCommitHistory(dataset, commitId);

  // 2. Create empty dataset
  Dataset ds = DatasetFactory.create();

  // 3. Apply patches in chronological order (oldest first)
  for (Commit commit : commits) {
    applyPatch(ds, commit.getPatch());
  }

  return ds;
}
```

**Implementation Details**:
- Use CommitRepository to traverse commit history
- Apply RDFPatch in order using existing RdfPatchService
- Consider caching materialized datasets (optional)
- Handle edge cases (empty history, missing commits)

**Unit Tests**:
- `DatasetServiceTest.materializeAtCommit_shouldApplyPatchesInOrder()`
- `DatasetServiceTest.materializeAtCommit_shouldReturnEmptyForInitialCommit()`
- `DatasetServiceTest.materializeAtCommit_shouldHandleMissingCommit()`

### Phase 2: Integrate Apache Jena ARQ (2-3 hours)

**Objective**: Add SPARQL query execution using Jena ARQ engine

**File**: `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java` (new)

**Service Interface**:
```java
@Service
public class SparqlQueryService {

  /**
   * Executes SPARQL query against dataset.
   *
   * @param dataset the dataset to query
   * @param queryString SPARQL query string
   * @param resultFormat desired result format (JSON, XML, CSV, TSV)
   * @return query results as String
   * @throws QueryParseException if query is malformed
   * @throws QueryExecException if execution fails
   */
  public String executeQuery(Dataset dataset, String queryString, ResultFormat resultFormat) {
    // 1. Parse query
    Query query = QueryFactory.create(queryString);

    // 2. Execute query
    try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
      // 3. Format results
      if (query.isSelectType()) {
        ResultSet results = qexec.execSelect();
        return formatSelectResults(results, resultFormat);
      } else if (query.isAskType()) {
        boolean result = qexec.execAsk();
        return formatAskResult(result, resultFormat);
      } else if (query.isConstructType()) {
        Model model = qexec.execConstruct();
        return formatGraphResults(model, resultFormat);
      } else if (query.isDescribeType()) {
        Model model = qexec.execDescribe();
        return formatGraphResults(model, resultFormat);
      }
    }
    throw new IllegalArgumentException("Unknown query type");
  }

  private String formatSelectResults(ResultSet results, ResultFormat format) {
    // Use ResultSetFormatter from Jena
    switch (format) {
      case JSON: return ResultSetFormatter.asJSON(results);
      case XML: return ResultSetFormatter.asXMLString(results);
      case CSV: return ResultSetFormatter.asText(results, ",");
      case TSV: return ResultSetFormatter.asText(results, "\\t");
    }
  }

  // Similar formatters for ASK, CONSTRUCT, DESCRIBE...
}
```

**Unit Tests**:
- `SparqlQueryServiceTest.executeQuery_shouldExecuteSelectQuery()`
- `SparqlQueryServiceTest.executeQuery_shouldExecuteAskQuery()`
- `SparqlQueryServiceTest.executeQuery_shouldFormatResultsAsJson()`
- `SparqlQueryServiceTest.executeQuery_shouldThrowOnMalformedQuery()`

### Phase 3: Complete Controller Implementation (2-3 hours)

**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java`

**Replace TODO in querySparqlGet()**:
```java
@GetMapping
public ResponseEntity<String> querySparqlGet(
    @RequestParam String query,
    @RequestParam(required = false) String branch,
    @RequestParam(required = false) String commit,
    @RequestParam(required = false) String asOf,
    @RequestHeader(name = "SPARQL-VC-Commit", required = false) String vcCommit
) {
  // Validate selector mutual exclusion
  SelectorValidator.validateMutualExclusion(branch, commit, asOf);

  // Default dataset
  String dataset = "default";

  try {
    // 1. Resolve selectors to target commit
    CommitId targetCommit = selectorResolutionService.resolve(dataset, branch, commit, asOf);

    // 2. Materialize dataset at that commit
    Dataset ds = datasetService.materializeAtCommit(dataset, targetCommit);

    // 3. Determine result format from Accept header
    ResultFormat format = determineResultFormat(request.getHeader("Accept"));

    // 4. Execute query
    String results = sparqlQueryService.executeQuery(ds, query, format);

    // 5. Return results with ETag
    HttpHeaders headers = new HttpHeaders();
    headers.setETag("\"" + targetCommit.value() + "\"");
    headers.setContentType(getMediaType(format));

    return ResponseEntity.ok()
        .headers(headers)
        .body(results);

  } catch (QueryParseException e) {
    return ProblemDetailFactory.createBadRequest(
        "malformed_query",
        "SPARQL query is malformed: " + e.getMessage()
    );
  } catch (SelectorConflictException e) {
    return ProblemDetailFactory.createBadRequest(
        "selector_conflict",
        e.getMessage()
    );
  } catch (BranchNotFoundException | CommitNotFoundException e) {
    return ProblemDetailFactory.createNotFound(e.getMessage());
  }
}
```

**Helper Methods**:
```java
private ResultFormat determineResultFormat(String acceptHeader) {
  if (acceptHeader == null) return ResultFormat.JSON;

  if (acceptHeader.contains("application/sparql-results+json")) {
    return ResultFormat.JSON;
  } else if (acceptHeader.contains("application/sparql-results+xml")) {
    return ResultFormat.XML;
  } else if (acceptHeader.contains("text/csv")) {
    return ResultFormat.CSV;
  } else if (acceptHeader.contains("text/tab-separated-values")) {
    return ResultFormat.TSV;
  }

  return ResultFormat.JSON; // default
}

private MediaType getMediaType(ResultFormat format) {
  switch (format) {
    case JSON: return MediaType.parseMediaType("application/sparql-results+json");
    case XML: return MediaType.parseMediaType("application/sparql-results+xml");
    case CSV: return MediaType.parseMediaType("text/csv");
    case TSV: return MediaType.parseMediaType("text/tab-separated-values");
  }
}
```

**Inject Dependencies**:
```java
@RestController
@RequestMapping("/sparql")
public class SparqlController {

  private final VersionControlProperties vcProperties;
  private final SelectorResolutionService selectorResolutionService;
  private final DatasetService datasetService;
  private final SparqlQueryService sparqlQueryService;

  public SparqlController(
      VersionControlProperties vcProperties,
      SelectorResolutionService selectorResolutionService,
      DatasetService datasetService,
      SparqlQueryService sparqlQueryService
  ) {
    this.vcProperties = vcProperties;
    this.selectorResolutionService = selectorResolutionService;
    this.datasetService = datasetService;
    this.sparqlQueryService = sparqlQueryService;
  }

  // ... methods
}
```

### Phase 4: Integration Tests (2-3 hours)

**File**: `src/test/java/org/chucc/vcserver/integration/SparqlQueryIntegrationTest.java`

**Test Structure**:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlQueryIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlQuery_shouldExecuteWithBranchSelector() {
    // Given: Graph data via GSP PUT
    putGraph("http://example.org/graph1", "<s> <p> \"value\" .", "main");

    // When: Query via SPARQL
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/sparql?query=" + urlEncode(query) + "&branch=main",
        String.class
    );

    // Then: Results returned
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).contains("\"s\"", "\"p\"", "\"o\"");
  }

  @Test
  void sparqlQuery_shouldExecuteWithCommitSelector() {
    // Given: Graph data with commit ID
    String commitId = putGraph("http://example.org/graph1", "<s> <p> \"v1\" .", "main");

    // When: Query specific commit
    String query = "SELECT * WHERE { ?s ?p ?o }";
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/sparql?query=" + urlEncode(query) + "&commit=" + commitId,
        String.class
    );

    // Then: Results from that commit
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + commitId + "\"");
  }

  @Test
  void sparqlQuery_shouldExecuteWithAsOfSelector() {
    // Given: Two commits at different times
    Instant time1 = Instant.now();
    putGraph("http://example.org/graph1", "<s> <p> \"v1\" .", "main");

    Thread.sleep(10); // Ensure different timestamps

    Instant time2 = Instant.now();
    putGraph("http://example.org/graph1", "<s> <p> \"v2\" .", "main");

    // When: Query with asOf between commits
    String query = "SELECT * WHERE { ?s ?p ?o }";
    String asOf = time1.plusMillis(5).toString();
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/sparql?query=" + urlEncode(query) + "&asOf=" + asOf,
        String.class
    );

    // Then: Results from first commit
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"v1\"");
  }

  @Test
  void sparqlQuery_shouldReturn400ForSelectorConflict() {
    // When: Query with both branch and commit
    String query = "SELECT * WHERE { ?s ?p ?o }";
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/sparql?query=" + urlEncode(query) + "&branch=main&commit=abc123",
        String.class
    );

    // Then: Selector conflict error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("selector_conflict");
  }

  @Test
  void sparqlQuery_shouldReturn400ForMalformedQuery() {
    // When: Query with syntax error
    String query = "SELECT * WHERE { ?s ?p }"; // Missing closing }
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/sparql?query=" + urlEncode(query) + "&branch=main",
        String.class
    );

    // Then: Malformed query error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("malformed_query");
  }

  @Test
  void sparqlQuery_shouldSupportJsonResultFormat() {
    // Given: Graph data
    putGraph("http://example.org/graph1", "<s> <p> \"value\" .", "main");

    // When: Query with Accept: application/sparql-results+json
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.parseMediaType("application/sparql-results+json")));

    HttpEntity<Void> request = new HttpEntity<>(headers);
    String query = "SELECT * WHERE { ?s ?p ?o }";
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?query=" + urlEncode(query) + "&branch=main",
        HttpMethod.GET,
        request,
        String.class
    );

    // Then: JSON results
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getBody()).contains("{", "\"head\"", "\"results\"");
  }

  // Helper methods
  private String putGraph(String graphUri, String turtle, String branch) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("text/turtle"));
    headers.set("SPARQL-VC-Author", "Test");
    headers.set("SPARQL-VC-Message", "Test graph");

    HttpEntity<String> request = new HttpEntity<>(turtle, headers);
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=" + graphUri + "&branch=" + branch,
        HttpMethod.PUT,
        request,
        String.class
    );

    return response.getHeaders().getETag().replace("\"", "");
  }
}
```

## Verification Steps

1. **Unit tests**:
   ```bash
   mvn test -Dtest=DatasetServiceTest,SparqlQueryServiceTest
   ```
   - All new tests pass

2. **Integration tests**:
   ```bash
   mvn test -Dtest=SparqlQueryIntegrationTest
   ```
   - All query tests pass (6+ tests)
   - Execution time < 1 minute

3. **Static analysis**:
   ```bash
   mvn checkstyle:check spotbugs:check
   ```
   - Zero violations

4. **Full build**:
   ```bash
   mvn -q clean install
   ```
   - All tests pass
   - BUILD SUCCESS

## Acceptance Criteria

- [x] DatasetService.materializeAtCommit() implemented and tested
- [x] SparqlQueryService created with query execution
- [x] SparqlController.querySparqlGet() completed (no 501)
- [x] All selectors work (branch, commit, asOf)
- [x] ETag header contains commit ID
- [x] Selector conflicts return 400 with problem+json
- [x] Malformed queries return 400 with problem+json
- [x] All result formats work (JSON, XML, CSV, TSV)
- [x] Content negotiation works (Accept header)
- [x] Integration tests pass (6+ tests)
- [x] Zero Checkstyle violations
- [x] Zero SpotBugs warnings
- [x] Full test suite passes

## Dependencies

- Task 01 (Fix concurrent tests) - Optional but recommended
- Apache Jena ARQ - Already in dependencies
- SelectorResolutionService - Already exists
- CommitRepository - Already exists

## Estimated Complexity

**Medium-High** (1-2 days / 8-16 hours)
- Phase 1: Dataset materialization (4-6 hours)
- Phase 2: Jena ARQ integration (2-3 hours)
- Phase 3: Controller completion (2-3 hours)
- Phase 4: Integration tests (2-3 hours)
- Testing and debugging (2-4 hours)

## Notes

**Key Design Decisions**:
- Dataset materialization replays full history (simple, correct)
- No caching initially (can add later if needed)
- Use Apache Jena ARQ directly (standard, well-tested)
- Follow existing error handling patterns (RFC 7807)

**Performance Considerations**:
- Materializing large histories may be slow
- Consider caching materialized datasets in future
- UUIDv7 commit IDs enable time-based optimizations

**Testing Strategy**:
- Use API Layer tests (no projector needed)
- Queries are read-only, no async processing required
- Test via GSP PUT + SPARQL GET workflow

## Success Criteria

When this task is complete:
- GET /sparql returns 200 with query results
- Version control selectors work correctly
- SPARQL 1.2 Query protocol functional
- Foundation ready for Task 03 (SPARQL Update)
