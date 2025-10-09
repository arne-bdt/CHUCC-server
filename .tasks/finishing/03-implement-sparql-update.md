# Task 03: Implement SPARQL Update Endpoint

## Objective

Implement `/sparql` POST endpoint to execute SPARQL updates that create commits on target branches.

## Background

Currently, `SparqlController.executeSparqlPost()` returns 501 Not Implemented. This task completes the SPARQL 1.2 Protocol update endpoint with version control commit creation.

**Current State**:
- ✅ Controller skeleton exists
- ✅ Query endpoint complete (Task 02)
- ❌ No update execution engine
- ❌ No command/handler for SPARQL updates
- ❌ No integration tests

## Implementation Plan

### Phase 1: Create Command and Handler (3-4 hours)

**File**: `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommand.java` (new)

```java
public record SparqlUpdateCommand(
    String dataset,
    String branch,
    String updateString,
    String author,
    String message,
    Optional<CommitId> expectedHead // For If-Match precondition
) implements Command {

  public SparqlUpdateCommand {
    Objects.requireNonNull(dataset, "dataset must not be null");
    Objects.requireNonNull(branch, "branch must not be null");
    Objects.requireNonNull(updateString, "updateString must not be null");
    Objects.requireNonNull(author, "author must not be null");
    Objects.requireNonNull(message, "message must not be null");
    Objects.requireNonNull(expectedHead, "expectedHead must not be null");
  }
}
```

**File**: `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommandHandler.java` (new)

```java
@Component
public class SparqlUpdateCommandHandler implements CommandHandler<SparqlUpdateCommand, CommitId> {

  private final BranchRepository branchRepository;
  private final DatasetService datasetService;
  private final EventPublisher eventPublisher;
  private final RdfDiffService rdfDiffService;

  @Override
  public CommitId handle(SparqlUpdateCommand command) {
    // 1. Get current branch HEAD
    Branch branch = branchRepository.findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new BranchNotFoundException(command.branch()));

    // 2. Check If-Match precondition
    if (command.expectedHead().isPresent()) {
      CommitId expected = command.expectedHead().get();
      if (!branch.getCommitId().equals(expected)) {
        throw new PreconditionFailedException(
            "Branch HEAD is " + branch.getCommitId() + " but expected " + expected
        );
      }
    }

    // 3. Materialize current dataset at branch HEAD
    Dataset currentDataset = datasetService.materializeAtCommit(
        command.dataset(),
        branch.getCommitId()
    );

    // 4. Apply SPARQL update to create new dataset
    Dataset updatedDataset = applyUpdate(currentDataset, command.updateString());

    // 5. Compute RDF diff
    RdfPatch patch = rdfDiffService.computeDiff(currentDataset, updatedDataset);

    // 6. Detect no-op (empty patch)
    if (patch.isEmpty()) {
      return branch.getCommitId(); // Return current HEAD, no commit created
    }

    // 7. Create commit event
    CommitId newCommitId = CommitId.generate();
    CommitCreatedEvent event = new CommitCreatedEvent(
        command.dataset(),
        newCommitId.value(),
        List.of(branch.getCommitId().value()),
        command.message(),
        command.author(),
        Instant.now(),
        patch.toString()
    );

    // 8. Publish event
    eventPublisher.publish(event);

    return newCommitId;
  }

  private Dataset applyUpdate(Dataset dataset, String updateString) {
    try {
      // Parse SPARQL UPDATE
      UpdateRequest update = UpdateFactory.create(updateString);

      // Create mutable copy of dataset
      Dataset mutableDataset = DatasetFactory.createTxnMem();
      DatasetUtils.addAll(mutableDataset, dataset);

      // Execute update
      UpdateAction.execute(update, mutableDataset);

      return mutableDataset;

    } catch (QueryParseException e) {
      throw new MalformedUpdateException("SPARQL update is malformed: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new UpdateExecutionException("Failed to execute SPARQL update", e);
    }
  }
}
```

**New Exceptions**:
```java
public class MalformedUpdateException extends RuntimeException {
  public MalformedUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}

public class UpdateExecutionException extends RuntimeException {
  public UpdateExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

**Unit Tests**:
- `SparqlUpdateCommandHandlerTest.handle_shouldCreateCommitForInsertUpdate()`
- `SparqlUpdateCommandHandlerTest.handle_shouldCreateCommitForDeleteUpdate()`
- `SparqlUpdateCommandHandlerTest.handle_shouldReturnCurrentHeadForNoOp()`
- `SparqlUpdateCommandHandlerTest.handle_shouldThrowForMalformedUpdate()`
- `SparqlUpdateCommandHandlerTest.handle_shouldThrowForPreconditionFailure()`

### Phase 2: Complete Controller Implementation (2-3 hours)

**File**: `src/main/java/org/chucc/vcserver/controller/SparqlController.java`

**Replace TODO in executeSparqlPost()**:
```java
@PostMapping
public ResponseEntity<String> executeSparqlPost(
    @RequestBody String body,
    @RequestParam(required = false) String branch,
    @RequestHeader(name = "SPARQL-VC-Message", required = false) String message,
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author,
    @RequestHeader(name = "SPARQL-VC-Branch", required = false) String vcBranch,
    @RequestHeader(name = "If-Match", required = false) String ifMatch,
    @RequestHeader(name = "Content-Type", required = false) String contentType
) {
  // Determine if query or update based on Content-Type
  boolean isUpdate = contentType != null &&
      (contentType.contains("application/sparql-update") ||
       contentType.contains("application/x-www-form-urlencoded"));

  if (!isUpdate) {
    // Execute query (delegate to query service)
    return executeQuery(body, branch);
  }

  // === SPARQL Update ===

  // Validate required headers for updates
  String targetBranch = vcBranch != null ? vcBranch : branch;
  if (targetBranch == null) {
    return ProblemDetailFactory.createBadRequest(
        "missing_branch",
        "SPARQL-VC-Branch header or branch parameter required for updates"
    );
  }
  if (author == null) {
    return ProblemDetailFactory.createBadRequest(
        "missing_author",
        "SPARQL-VC-Author header required for updates"
    );
  }
  if (message == null) {
    return ProblemDetailFactory.createBadRequest(
        "missing_message",
        "SPARQL-VC-Message header required for updates"
    );
  }

  // Parse If-Match header
  Optional<CommitId> expectedHead = Optional.empty();
  if (ifMatch != null) {
    String etag = ifMatch.replace("\"", "");
    expectedHead = Optional.of(CommitId.of(etag));
  }

  try {
    // Create command
    SparqlUpdateCommand command = new SparqlUpdateCommand(
        "default", // dataset
        targetBranch,
        body,
        author,
        message,
        expectedHead
    );

    // Execute command
    CommitId newCommitId = commandBus.execute(command);

    // Get branch to check if no-op
    Branch updatedBranch = branchRepository.findByDatasetAndName("default", targetBranch)
        .orElseThrow();

    boolean isNoOp = !updatedBranch.getCommitId().equals(newCommitId);

    if (isNoOp) {
      // No-op update: return 204 with current HEAD
      HttpHeaders headers = new HttpHeaders();
      headers.setETag("\"" + updatedBranch.getCommitId().value() + "\"");
      return ResponseEntity.noContent().headers(headers).build();
    } else {
      // Commit created: return 200/201 with ETag and Location
      HttpHeaders headers = new HttpHeaders();
      headers.setETag("\"" + newCommitId.value() + "\"");
      headers.setLocation(URI.create("/version/commits/" + newCommitId.value()));

      return ResponseEntity.ok()
          .headers(headers)
          .body("{\"commitId\":\"" + newCommitId.value() + "\"}");
    }

  } catch (MalformedUpdateException e) {
    return ProblemDetailFactory.createBadRequest(
        "malformed_update",
        "SPARQL update is malformed: " + e.getMessage()
    );
  } catch (PreconditionFailedException e) {
    return ProblemDetailFactory.createPreconditionFailed(
        "etag_mismatch",
        e.getMessage()
    );
  } catch (BranchNotFoundException e) {
    return ProblemDetailFactory.createNotFound(e.getMessage());
  } catch (UpdateExecutionException e) {
    return ProblemDetailFactory.createInternalServerError(
        "update_execution_failed",
        e.getMessage()
    );
  }
}
```

**Inject CommandBus**:
```java
@RestController
@RequestMapping("/sparql")
public class SparqlController {

  private final VersionControlProperties vcProperties;
  private final SelectorResolutionService selectorResolutionService;
  private final DatasetService datasetService;
  private final SparqlQueryService sparqlQueryService;
  private final CommandBus commandBus;
  private final BranchRepository branchRepository;

  // Constructor with all dependencies
}
```

### Phase 3: Integration Tests (2-3 hours)

**File**: `src/test/java/org/chucc/vcserver/integration/SparqlUpdateIntegrationTest.java`

**Test Structure**:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlUpdateIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlUpdate_shouldCreateCommitForInsertData() {
    // Given: Empty dataset
    createBranch("test-branch");

    // When: Execute INSERT DATA
    String update = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"value\" }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Branch", "test-branch");
    headers.set("SPARQL-VC-Author", "Test User");
    headers.set("SPARQL-VC-Message", "Insert test data");

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: Commit created
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getBody()).contains("\"commitId\"");
  }

  @Test
  void sparqlUpdate_shouldReturn204ForNoOp() {
    // Given: Dataset with triple
    createBranch("test-branch");
    insertData("test-branch", "<s> <p> \"value\" .");

    // When: Execute same INSERT DATA (no-op)
    String update = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"value\" }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Branch", "test-branch");
    headers.set("SPARQL-VC-Author", "Test User");
    headers.set("SPARQL-VC-Message", "No-op insert");

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: No commit created
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNull();
  }

  @Test
  void sparqlUpdate_shouldRespectIfMatchPrecondition() {
    // Given: Dataset with commit
    createBranch("test-branch");
    String commit1 = insertData("test-branch", "<s> <p> \"v1\" .");

    // When: Update with correct ETag
    String update = "DELETE DATA { <http://example.org/s> <http://example.org/p> \"v1\" }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Branch", "test-branch");
    headers.set("SPARQL-VC-Author", "Test User");
    headers.set("SPARQL-VC-Message", "Delete data");
    headers.set("If-Match", "\"" + commit1 + "\"");

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: Update succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void sparqlUpdate_shouldReturn412ForETagMismatch() {
    // Given: Dataset with two commits
    createBranch("test-branch");
    String commit1 = insertData("test-branch", "<s> <p> \"v1\" .");
    String commit2 = insertData("test-branch", "<s> <p> \"v2\" .");

    // When: Update with old ETag
    String update = "DELETE DATA { <http://example.org/s> <http://example.org/p> \"v2\" }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Branch", "test-branch");
    headers.set("SPARQL-VC-Author", "Test User");
    headers.set("SPARQL-VC-Message", "Delete data");
    headers.set("If-Match", "\"" + commit1 + "\""); // Old ETag

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: Precondition failed
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("etag_mismatch");
  }

  @Test
  void sparqlUpdate_shouldReturn400ForMalformedUpdate() {
    // Given: Branch exists
    createBranch("test-branch");

    // When: Execute malformed update
    String update = "INSERT DATA { invalid syntax }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Branch", "test-branch");
    headers.set("SPARQL-VC-Author", "Test User");
    headers.set("SPARQL-VC-Message", "Malformed update");

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: Bad request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("malformed_update");
  }

  @Test
  void sparqlUpdate_shouldReturn400ForMissingHeaders() {
    // When: Update without required headers
    String update = "INSERT DATA { <s> <p> \"value\" }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    // Missing SPARQL-VC-Branch, SPARQL-VC-Author, SPARQL-VC-Message

    HttpEntity<String> request = new HttpEntity<>(update, headers);
    ResponseEntity<String> response = restTemplate.postForEntity("/sparql", request, String.class);

    // Then: Bad request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsAnyOf("missing_branch", "missing_author", "missing_message");
  }

  // Helper methods
  private void createBranch(String name) {
    // Use POST /version/branches
  }

  private String insertData(String branch, String turtle) {
    // Use PUT /data with turtle
  }
}
```

## Verification Steps

1. **Unit tests**:
   ```bash
   mvn test -Dtest=SparqlUpdateCommandHandlerTest
   ```
   - All handler tests pass (5+ tests)

2. **Integration tests**:
   ```bash
   mvn test -Dtest=SparqlUpdateIntegrationTest
   ```
   - All update tests pass (6+ tests)
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

- [x] SparqlUpdateCommand created
- [x] SparqlUpdateCommandHandler implemented and tested
- [x] SparqlController.executeSparqlPost() completed (no 501)
- [x] UPDATE creates commits on target branch
- [x] ETag header contains new commit ID
- [x] Location header points to /version/commits/{id}
- [x] No-op updates return 204 without commit
- [x] If-Match precondition works (412 on mismatch)
- [x] Required headers enforced (400 if missing)
- [x] Malformed updates return 400 with problem+json
- [x] Integration tests pass (6+ tests)
- [x] Zero Checkstyle violations
- [x] Zero SpotBugs warnings
- [x] Full test suite passes

## Dependencies

- Task 02 (SPARQL Query) - Required
- DatasetService.materializeAtCommit() from Task 02 - Required
- RdfDiffService - Already exists
- CommandBus - Already exists

## Estimated Complexity

**Medium-High** (1-2 days / 8-16 hours)
- Phase 1: Command and handler (3-4 hours)
- Phase 2: Controller completion (2-3 hours)
- Phase 3: Integration tests (2-3 hours)
- Testing and debugging (2-4 hours)

## Notes

**Key Design Decisions**:
- Reuse RdfDiffService (same as GSP PUT/POST)
- Follow existing command/handler pattern
- No-op detection returns current HEAD (204)
- If-Match uses ETag for optimistic concurrency

**Similarities to GSP**:
- Both compute RDF diffs
- Both create CommitCreatedEvent
- Both support If-Match preconditions
- Both detect no-ops

**Differences from GSP**:
- SPARQL UPDATE uses Jena UpdateAction instead of RDF parsing
- Updates can modify multiple graphs
- Complex update patterns (DELETE/INSERT WHERE)

**Testing Strategy**:
- Use API Layer tests (no projector needed)
- Test INSERT DATA, DELETE DATA, DELETE/INSERT WHERE
- Verify no-op detection
- Verify precondition handling

## Success Criteria

When this task is complete:
- POST /sparql creates commits for updates
- SPARQL 1.2 Update protocol functional
- Version control integration complete
- SPARQL endpoints fully implemented
