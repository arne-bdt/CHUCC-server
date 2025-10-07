# Task 06: PUT Graph Operation - Command and Event

## Objective
Implement command/event infrastructure for PUT operation (replace entire graph).

## Background
`PUT /data?graph={iri}&branch={name}` replaces entire graph content. Server computes RDF Patch (delete all quads from graph, then add new quads). Creates commit if patch is non-empty.

## Tasks

### 1. Define PutGraphCommand
Create `src/main/java/org/chucc/vcserver/command/PutGraphCommand.java`:
```java
public record PutGraphCommand(
  String dataset,
  String graphIri,        // or null for default graph
  boolean isDefaultGraph,
  String branch,          // target branch
  CommitId baseCommit,    // resolved from selectors
  String rdfContent,      // serialized RDF (Turtle, etc.)
  String contentType,     // for parsing
  String author,
  String message,
  String ifMatchETag      // optional precondition
) implements Command {}
```

### 2. Define GraphUpdatedEvent
Create `src/main/java/org/chucc/vcserver/event/GraphUpdatedEvent.java`:
```java
public record GraphUpdatedEvent(
  CommitId commitId,
  String dataset,
  String graphIri,
  boolean isDefaultGraph,
  String branch,
  CommitId parentCommitId,
  String patch,           // RDF Patch text
  String author,
  String message,
  Instant timestamp
) {}
```

### 3. Create PutGraphCommandHandler
Create `src/main/java/org/chucc/vcserver/command/PutGraphCommandHandler.java`:
- Implement `CommandHandler<PutGraphCommand, CommitCreatedEvent>` (reuse existing event type)
- Validate precondition (If-Match) via PreconditionService
- Get current graph state from DatasetService
- Parse new RDF content
- Compute diff (delete all old quads, add all new quads)
- Check if diff is empty (no-op detection) → return NoOpResult
- Generate UUIDv7 commit ID
- Create RDF Patch from diff
- Create CommitCreatedEvent with patch
- Publish event to Kafka
- Return event

### 4. Create GraphDiffService
Create `src/main/java/org/chucc/vcserver/service/GraphDiffService.java`:
- Method: `RdfPatch computePutDiff(Model oldGraph, Model newGraph, String graphIri)`
  - Generates DELETE operations for all quads in oldGraph
  - Generates ADD operations for all quads in newGraph
  - Returns RdfPatch
- Method: `boolean isPatchEmpty(RdfPatch patch)`
  - Checks if patch has no operations

### 5. Create RDF Parsing Service
Create `src/main/java/org/chucc/vcserver/service/RdfParsingService.java`:
- Method: `Model parseRdf(String content, String contentType)`
  - Parses Turtle, N-Triples, JSON-LD, RDF/XML, etc.
  - Throws 415 Unsupported Media Type if format not supported
  - Throws 400 Bad Request if parsing fails

### 6. Write Unit Tests
Create tests for:
- `PutGraphCommandHandlerTest` (mock dependencies)
- `GraphDiffServiceTest` (test diff computation)
- `RdfParsingServiceTest` (test parsing various formats)

## Acceptance Criteria
- [ ] PutGraphCommand and GraphUpdatedEvent defined
- [ ] PutGraphCommandHandler implements command logic
- [ ] GraphDiffService computes correct PUT diff
- [ ] RdfParsingService parses standard RDF formats
- [ ] No-op detection works (empty diff → no event)
- [ ] All unit tests pass with >80% coverage
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 05 (HEAD operation - ensures GET/HEAD are working)

## Estimated Complexity
Medium-High (6-8 hours)
