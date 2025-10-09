# CHUCC Server Architecture Guide for AI Agents

**Essential reading for AI agents working on this codebase**

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture at a Glance](#architecture-at-a-glance)
3. [Core Concepts](#core-concepts)
4. [Component Map](#component-map)
5. [Data Flow](#data-flow)
6. [Testing Architecture](#testing-architecture)
7. [Key Patterns](#key-patterns)
8. [Directory Structure](#directory-structure)
9. [Important Files](#important-files)
10. [Common Tasks](#common-tasks)

---

## System Overview

**CHUCC Server** implements SPARQL 1.2 Protocol with Version Control, enabling Git-like operations on RDF graphs.

### What It Does
- **Graph Store Protocol (GSP)**: CRUD operations on RDF graphs via HTTP (PUT, GET, POST, DELETE, PATCH)
- **Version Control**: Branches, tags, commits, merge, revert, cherry-pick, squash, rebase
- **SPARQL Protocol**: Query and update RDF data with version control selectors
- **Event Sourcing**: All changes captured as events in Kafka for audit and replay

### Core Architecture Pattern
**CQRS + Event Sourcing**

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Client    │         │   Command   │         │   Kafka     │
│  (HTTP)     │────────>│   Handler   │────────>│  (Events)   │
└─────────────┘         └─────────────┘         └─────────────┘
                              │                         │
                              │ validates               │ publishes
                              │ creates event           │ CommitCreatedEvent
                              ▼                         │
                        Returns 200/201               ▼
                        with ETag                ┌─────────────┐
                                                │  Projector  │
                                                │ (Consumer)  │
                                                └─────────────┘
                                                      │ updates
                                                      ▼
                                                ┌─────────────┐
                                                │ Repositories│
                                                │ (Read Model)│
                                                └─────────────┘
```

**Key Insight**: HTTP responses return IMMEDIATELY after creating events. Repository updates happen ASYNCHRONOUSLY via event projectors.

---

## Architecture at a Glance

### Technology Stack
- **Java 21** + **Spring Boot 3.5**: Modern Java with dependency injection
- **Apache Jena 5.5**: RDF graph processing and SPARQL execution
- **Apache Kafka**: Event store and message bus
- **RDFPatch**: Event format (describes graph changes as patches)
- **In-Memory Storage**: DatasetGraphInMemory (like Apache Jena Fuseki)

### Design Principles
1. **Event Sourcing**: Events are source of truth, never delete events
2. **CQRS**: Separate write model (commands) from read model (queries)
3. **Async Processing**: Commands return immediately, projectors update read models
4. **Test Isolation**: Projector disabled by default in tests
5. **Git-Like Semantics**: UUIDv7 commit IDs, branch pointers, immutable commits

---

## Core Concepts

### 1. Events (Write Model)

All state changes produce events published to Kafka:

```java
// Example: Creating a commit
CommitCreatedEvent event = new CommitCreatedEvent(
    dataset: "default",
    commitId: "01JCDN3KXQ7GMVR2T8YFWP9H4M", // UUIDv7
    parents: ["01JCDN2XYZ..."],
    message: "Update graph",
    author: "Alice",
    timestamp: Instant.now(),
    patch: "TX .\nA <s> <p> \"o\" .\nTC ."  // RDFPatch
);
```

**Event Types** (10 total):
- `CommitCreatedEvent` - New commit with RDF changes
- `BranchCreatedEvent`, `BranchResetEvent`, `BranchRebasedEvent`
- `TagCreatedEvent`
- `CherryPickedEvent`, `CommitsSquashedEvent`, `RevertCreatedEvent`
- `MergedEvent`, `SnapshotCreatedEvent`

### 2. Commits (Immutable)

Like Git commits:
- **UUIDv7 ID**: Sortable, contains timestamp
- **Parents**: One or more parent commits (merge = 2 parents)
- **RDFPatch**: Describes what changed
- **Metadata**: Author, message, timestamp
- **Immutable**: Never modified after creation

### 3. Branches (Mutable Pointers)

Branch points to latest commit on that branch:

```java
Branch main = new Branch("main", CommitId.of("01JCDN3KXQ..."));
```

Operations move branch pointers:
- **Commit**: Branch advances to new commit
- **Reset**: Branch moves to different commit
- **Merge**: Branch advances to merge commit

### 4. Selectors (Version Control)

How clients specify which version to read/write:

| Selector | Type | Example | Use Case |
|----------|------|---------|----------|
| `branch` | Mutable | `?branch=main` | Work on latest |
| `commit` | Immutable | `?commit=01JCDN...` | Read-only, specific version |
| `asOf` | Time-travel | `?asOf=2025-01-15T10:00:00Z` | Historical query |

**Rule**: Selectors are mutually exclusive (can't combine branch + commit).

### 5. RDFPatch Format

Events store changes as RDF Patch (W3C format):

```
TX .                                    # Begin transaction
A <s> <p> "value" .                     # Add triple
D <s> <p> "oldValue" .                  # Delete triple
TC .                                    # Commit transaction
```

Patches compose: applying patches in order rebuilds dataset state.

---

## Component Map

### Package Structure

```
org.chucc.vcserver/
├── command/              # Write side (CQRS commands)
│   ├── *Command.java     # Command DTOs
│   └── *CommandHandler.java  # Command handlers
├── config/               # Spring configuration
├── controller/           # HTTP endpoints
│   ├── GraphStoreController.java
│   ├── BranchController.java
│   ├── SparqlController.java
│   └── ... (11 controllers)
├── domain/               # Domain model
│   ├── Commit.java
│   ├── Branch.java
│   ├── Tag.java
│   └── RdfPatch.java
├── event/                # Event definitions
│   ├── *Event.java       # Event DTOs
│   └── EventPublisher.java
├── projection/           # Read side (CQRS projectors)
│   └── ReadModelProjector.java  # Kafka consumer, updates repositories
├── repository/           # Read model storage
│   ├── CommitRepository.java
│   ├── BranchRepository.java
│   └── DatasetGraphRepository.java
├── service/              # Business logic
│   ├── SelectorResolutionService.java
│   ├── RdfDiffService.java
│   ├── DatasetService.java
│   └── ... (many services)
└── spring/               # Spring utilities
    └── common/           # Error handling, problem+json
```

### Key Components

#### Controllers (HTTP Layer)
- **GraphStoreController**: GSP endpoints (`/data`)
- **SparqlController**: SPARQL endpoints (`/sparql`)
- **BranchController**: Branch operations (`/version/branches`)
- **TagController**: Tag operations (`/version/tags`)
- **CommitController**: Commit info (`/version/commits/{id}`)
- **MergeController**: Merge operations (`/version/merge`)
- **AdvancedOpsController**: Revert, cherry-pick, squash, rebase

#### Command Handlers (Write Logic)
- **PutGraphCommandHandler**: Handle PUT /data
- **PostGraphCommandHandler**: Handle POST /data
- **CreateBranchCommandHandler**: Create new branch
- **MergeCommandHandler**: Three-way merge with conflict detection
- And 15+ more handlers

#### Services (Business Logic)
- **SelectorResolutionService**: Resolve branch/commit/asOf to CommitId
- **RdfDiffService**: Compute diff between two datasets
- **DatasetService**: Materialize dataset at specific commit
- **ConflictDetectionService**: Three-way merge conflict detection
- **RdfPatchService**: Apply RDF Patch to dataset

#### Repositories (Read Model)
- **CommitRepository**: Store/retrieve commits (in-memory)
- **BranchRepository**: Store/retrieve branches (in-memory)
- **DatasetGraphRepository**: Store materialized graphs (in-memory)

#### Event Processing
- **EventPublisher**: Publish events to Kafka (async)
- **ReadModelProjector**: Consume events from Kafka, update repositories

---

## Data Flow

### Write Operation Flow (e.g., PUT /data)

```
1. HTTP Request
   ↓
2. GraphStoreController.putGraph()
   - Validate selectors
   - Parse RDF content
   ↓
3. CommandBus.execute(PutGraphCommand)
   ↓
4. PutGraphCommandHandler.handle()
   - Resolve selector to branch
   - Check If-Match precondition
   - Compute RDF diff
   - Create CommitCreatedEvent
   ↓
5. EventPublisher.publish(event)
   - Publish to Kafka topic
   - Returns CompletableFuture
   ↓
6. HTTP Response
   - 200 OK / 201 Created
   - ETag: "commitId"
   - Location: /version/commits/{commitId}
   ↓
   [Async - happens later]
   ↓
7. ReadModelProjector (Kafka consumer)
   - Consume CommitCreatedEvent
   - Save commit to CommitRepository
   - Build dataset graph
   - Update branch HEAD
   ↓
8. Repositories Updated
   - CommitRepository has new commit
   - BranchRepository has updated branch
   - DatasetGraphRepository has new graph state
```

**CRITICAL**: Step 6 (HTTP response) happens BEFORE step 8 (repositories updated).

### Read Operation Flow (e.g., GET /data)

```
1. HTTP Request with selector
   ↓
2. GraphStoreController.getGraph()
   ↓
3. SelectorResolutionService.resolve()
   - branch → latest commit on branch
   - commit → that specific commit
   - asOf → commit at or before timestamp
   ↓
4. DatasetGraphRepository.getGraph()
   - Retrieve materialized graph at commit
   ↓
5. Serialize to requested format
   - Turtle, N-Triples, JSON-LD, RDF/XML
   ↓
6. HTTP Response
   - 200 OK
   - ETag: "commitId"
   - Content-Type: <format>
```

**Key**: Reads are synchronous, query materialized read model.

---

## Testing Architecture

### Test Isolation Pattern

**CRITICAL for AI agents**: Projector is DISABLED by default in integration tests!

```java
// application-it.yml
projector:
  kafka-listener:
    enabled: false  # Disabled by default
```

**Why**: Prevents cross-test contamination (test A's events processed by test B).

### Test Categories

**1. API Layer Tests (90% of tests)**
Test HTTP contract WITHOUT async processing:

```java
@SpringBootTest
@ActiveProfiles("it")
class MyApiTest extends IntegrationTestFixture {
  // Projector DISABLED by default

  @Test
  void putGraph_shouldReturn200() {
    // Act: HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: HTTP response ONLY
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();

    // ❌ DO NOT query repositories - not updated yet!
    // Note: Repository updates handled by ReadModelProjector (disabled)
  }
}
```

**2. Projector Tests (10% of tests)**
Test event handlers WITH projector enabled:

```java
@SpringBootTest
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphEventProjectorIT extends IntegrationTestFixture {
  // Projector ENABLED for this test

  @Test
  void commitCreatedEvent_shouldBeProjected() throws Exception {
    // Arrange: Create event
    CommitCreatedEvent event = ...;

    // Act: Publish to Kafka
    eventPublisher.publish(event).get();

    // Assert: Wait for async projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findById(commitId);
          assertThat(commit).isPresent();
        });
  }
}
```

**Reference**: See `.claude/CLAUDE.md` for complete testing guidelines.

### Testing Decision Table

| I want to test... | Enable Projector? | Use await()? | Test Class Type |
|-------------------|-------------------|--------------|-----------------|
| HTTP status codes | ❌ No | ❌ No | API Layer |
| HTTP headers | ❌ No | ❌ No | API Layer |
| Validation errors | ❌ No | ❌ No | API Layer |
| Event projection | ✅ Yes | ✅ Yes | Projector Test |

---

## Key Patterns

### 1. Command Pattern

All writes go through commands:

```java
// 1. Define command
public record PutGraphCommand(
    String dataset,
    String graphUri,
    String branch,
    Model model,
    String author,
    String message,
    Optional<CommitId> expectedHead
) implements Command {}

// 2. Implement handler
@Component
public class PutGraphCommandHandler implements CommandHandler<PutGraphCommand, CommitId> {
  @Override
  public CommitId handle(PutGraphCommand command) {
    // Validate, compute diff, create event, publish
    return commitId;
  }
}

// 3. Execute via CommandBus
CommitId result = commandBus.execute(command);
```

### 2. Event Sourcing Pattern

Events are append-only, immutable log:

```java
// Create event
CommitCreatedEvent event = new CommitCreatedEvent(...);

// Publish to Kafka (async)
CompletableFuture<RecordMetadata> future = eventPublisher.publish(event);

// Event stored forever in Kafka topic
// Can replay all events to rebuild state
```

### 3. Repository Pattern

Read models accessed via repositories:

```java
// Find branch
Optional<Branch> branch = branchRepository.findByDatasetAndName("default", "main");

// Save commit
commitRepository.save("default", commit, patch);

// Get graph
Optional<DatasetGraph> graph = datasetGraphRepository.getGraph("default", commitId, graphUri);
```

### 4. Selector Resolution Pattern

Convert user selector to concrete commit ID:

```java
// User provides: ?branch=main
CommitId commitId = selectorResolutionService.resolve("default", "main", null, null);

// User provides: ?asOf=2025-01-15T10:00:00Z
CommitId commitId = selectorResolutionService.resolve("default", null, null, "2025-01-15T10:00:00Z");
```

### 5. Problem Details Pattern

All errors use RFC 7807:

```java
return ProblemDetailFactory.createBadRequest(
    "selector_conflict",
    "Cannot specify both 'branch' and 'commit' selectors"
);

// Returns:
{
  "type": "urn:problem-type:selector_conflict",
  "title": "Selector Conflict",
  "status": 400,
  "detail": "Cannot specify both 'branch' and 'commit' selectors"
}
```

---

## Directory Structure

```
CHUCC-server/
├── .claude/                    # AI agent configuration
│   ├── CLAUDE.md               # Testing guidelines (READ THIS!)
│   ├── agents/                 # Agent profiles
│   └── protocol/               # Version Control spec
├── .mvn/                       # Maven configuration
├── .tasks/                     # Implementation task breakdown
│   ├── PROJECT_STATUS_AND_ROADMAP.md  # Current status
│   ├── finishing/              # Remaining tasks (SPARQL endpoints)
│   ├── gsp/                    # GSP implementation tasks (complete)
│   └── test-isolation/         # Test isolation tasks (complete)
├── docs/                       # Documentation (YOU ARE HERE)
│   ├── README.md               # Navigation guide
│   ├── architecture/           # Architecture docs
│   ├── api/                    # API specifications
│   ├── development/            # Development guides
│   ├── operations/             # Ops guides
│   └── conformance/            # Protocol conformance
├── src/
│   ├── main/
│   │   ├── java/org/chucc/vcserver/  # Source code
│   │   └── resources/          # Config (application.yml, etc.)
│   └── test/
│       ├── java/org/chucc/vcserver/  # Tests
│       └── resources/          # Test config (application-it.yml)
├── pom.xml                     # Maven dependencies
└── README.md                   # Project README
```

---

## Important Files

### Must-Read Files for AI Agents

1. **`.claude/CLAUDE.md`** (Most important!)
   - Testing strategy (API Layer vs Projector tests)
   - Code quality requirements
   - Build process and Maven commands
   - Common patterns and anti-patterns

2. **`.tasks/PROJECT_STATUS_AND_ROADMAP.md`**
   - What's complete (GSP, Version Control API)
   - What's missing (SPARQL Query/Update)
   - Implementation roadmap
   - Task breakdown with time estimates

3. **`docs/architecture/README.md`** (This file)
   - Architecture overview
   - Component map
   - Data flow diagrams
   - Key patterns

4. **`docs/architecture/c4-level2-container.md`**
   - Technology choices
   - Container boundaries
   - Data storage

5. **`docs/architecture/cqrs-event-sourcing.md`**
   - CQRS pattern explained
   - Event sourcing benefits
   - Why async processing

### Key Source Files

**Controllers (Entry Points)**:
- `GraphStoreController.java` - GSP endpoints
- `SparqlController.java` - SPARQL endpoints (incomplete)
- `BranchController.java` - Branch management
- `MergeController.java` - Merge operations

**Command Handlers (Business Logic)**:
- `PutGraphCommandHandler.java` - Example write handler
- `MergeCommandHandler.java` - Complex merge logic

**Event Processing**:
- `ReadModelProjector.java` - Event consumer (10 handlers)
- `EventPublisher.java` - Event producer

**Domain Model**:
- `Commit.java` - Immutable commit
- `Branch.java` - Mutable branch pointer
- `RdfPatch.java` - Patch representation

**Services (Reusable Logic)**:
- `SelectorResolutionService.java` - Selector resolution
- `RdfDiffService.java` - Diff computation
- `ConflictDetectionService.java` - Merge conflicts

### Configuration Files

**Application Configuration**:
- `src/main/resources/application.yml` - Main config
- `src/main/resources/application-it.yml` - Integration test config (projector disabled!)
- `src/main/resources/application-dev.yml` - Development config

**Build Configuration**:
- `pom.xml` - Dependencies, plugins, build process
- `.mvn/maven.config` - Maven defaults
- `checkstyle.xml` - Code style rules
- `spotbugs-exclude.xml` - SpotBugs suppression

**Test Configuration**:
- `src/test/resources/test.properties` - Test properties
- `src/test/resources/logback-test.xml` - Test logging

---

## Common Tasks

### Task 1: Add New Command

1. Create command record:
```java
public record MyCommand(String field1, ...) implements Command {}
```

2. Create handler:
```java
@Component
public class MyCommandHandler implements CommandHandler<MyCommand, Result> {
  public Result handle(MyCommand command) {
    // 1. Validate
    // 2. Create event
    // 3. Publish event
    // 4. Return result
  }
}
```

3. Use in controller:
```java
MyCommand command = new MyCommand(...);
Result result = commandBus.execute(command);
```

### Task 2: Add New Event Handler

1. Define event:
```java
public record MyEvent(String field1, ...) {}
```

2. Add handler to ReadModelProjector:
```java
@KafkaListener(...)
public void handleMyEvent(MyEvent event) {
  // Update repositories
}
```

3. Create projector test:
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyEventProjectorIT {
  @Test
  void myEvent_shouldBeProjected() {
    eventPublisher.publish(myEvent).get();
    await().untilAsserted(() -> {
      // Verify repository updated
    });
  }
}
```

### Task 3: Add New Controller Endpoint

1. Add method to controller:
```java
@GetMapping("/my-endpoint")
public ResponseEntity<String> myEndpoint(...) {
  // 1. Validate input
  // 2. Execute command or query
  // 3. Return response with headers
}
```

2. Add OpenAPI annotations:
```java
@Operation(summary = "My operation")
@ApiResponse(responseCode = "200", description = "Success")
```

3. Add integration test:
```java
@Test
void myEndpoint_shouldReturn200() {
  ResponseEntity<String> response = restTemplate.exchange(...);
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

### Task 4: Fix Failed Test

1. **Read test output carefully**
2. **Check if projector should be enabled**:
   - Test uses `await()`? → Need projector
   - Test queries repositories after HTTP request? → Need projector OR remove assertions
3. **Run single test**:
```bash
mvn test -Dtest=TestClassName
```
4. **Check logs**:
```bash
tail -f target/surefire-reports/TestClassName-output.txt
```

### Task 5: Add Static Analysis

1. **Before writing code**:
```bash
mvn -q clean compile checkstyle:check spotbugs:check
```

2. **Fix violations immediately**:
   - Checkstyle: Indentation, line length, Javadoc
   - SpotBugs: Defensive copying, null checks
   - PMD: Code duplication, complexity

3. **Run tests**:
```bash
mvn -q test -Dtest=MyNewTest
```

4. **Full build**:
```bash
mvn -q clean install
```

---

## Key Architectural Decisions

### Why CQRS + Event Sourcing?

1. **Audit Trail**: Every change is an event (compliance, debugging)
2. **Time Travel**: Rebuild state at any point in time
3. **Scalability**: Separate read/write models, scale independently
4. **Flexibility**: Add new projectors without changing write side

### Why In-Memory Storage?

- **Simplicity**: No external database required
- **Performance**: Fast reads/writes
- **Stateless**: Rebuild from events on restart
- **Like Fuseki**: Familiar to Apache Jena users

**Trade-off**: Data lost on restart, but can replay from Kafka.

### Why Kafka for Events?

- **Durability**: Events persisted to disk
- **Ordering**: Per-partition order guarantees
- **Replay**: Rebuild state from event log
- **Scalability**: Distributed, high throughput

### Why Test Isolation (Projector Disabled)?

**Before (Projector Always On)**:
- Test A publishes events
- Test B's projector consumes Test A's events
- Test B sees unexpected data
- Tests flaky, timing-dependent

**After (Projector Disabled by Default)**:
- Each test publishes events
- No projector consuming events
- Tests isolated, predictable
- 26% faster execution!

**Result**: 819 tests passing, zero cross-contamination.

---

## Next Steps

1. **Read C4 Models**: Understand system at different abstraction levels
   - [Level 1: Context](c4-level1-context.md) - External dependencies
   - [Level 2: Container](c4-level2-container.md) - Technology choices
   - [Level 3: Component](c4-level3-component.md) - Internal structure

2. **Read CQRS Guide**: Deep dive into event sourcing pattern
   - [CQRS & Event Sourcing](cqrs-event-sourcing.md)

3. **Read Testing Guide**: Master test isolation pattern
   - [Testing Strategy](../../.claude/CLAUDE.md)

4. **Check Current Status**: See what's done and what remains
   - [Project Status](../../.tasks/PROJECT_STATUS_AND_ROADMAP.md)

5. **Start Coding**: Pick a task from `.tasks/finishing/`

---

## Questions? Issues?

- **Architecture questions**: Re-read this guide
- **Testing questions**: Read `.claude/CLAUDE.md`
- **API questions**: Read `docs/api/`
- **Build issues**: Read `docs/development/quality-tools.md`
- **Stuck on a task**: Read `.tasks/PROJECT_STATUS_AND_ROADMAP.md`

**Remember**: This is a CQRS + Event Sourcing system. Writes are async, reads are sync. Test accordingly!
