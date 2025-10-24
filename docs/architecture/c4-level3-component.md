# C4 Model - Level 3: Component

**Component diagram showing internal structure of CHUCC Server**

## Overview

This document describes the **Component** (C4 Level 3) - a detailed view of the internal components within the CHUCC Server container and how they interact to implement CQRS + Event Sourcing architecture.

---

## Component Diagram (Textual)

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CHUCC Server Container                       │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │                      Web Layer (Controllers)                     ││
│  │                                                                  ││
│  │  GraphStoreController  BranchController  TagController           ││
│  │  SparqlController      CommitController  AdvancedOpsController   ││
│  │  BatchController       MergeController   HistoryController       ││
│  │  RefsController        BatchGraphsController                     ││
│  │                                                                  ││
│  └────────────────────────┬─────────────────────────────────────────┘│
│                           │ HTTP Requests                            │
│                           ▼                                          │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │                    Command Side (Write Model)                    ││
│  │                                                                  ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Command Handlers                              │    ││
│  │  │  PutGraphCommandHandler      CreateCommitCommandHandler  │    ││
│  │  │  PostGraphCommandHandler     CreateBranchCommandHandler  │    ││
│  │  │  PatchGraphCommandHandler    CreateTagCommandHandler     │    ││
│  │  │  DeleteGraphCommandHandler   ResetBranchCommandHandler   │    ││
│  │  │  BatchGraphsCommandHandler   RevertCommitCommandHandler  │    ││
│  │  │  RebaseCommandHandler        CherryPickCommandHandler    │    ││
│  │  │  SquashCommandHandler                                    │    ││
│  │  └────────────────────┬─────────────────────────────────────┘    ││
│  │                       │ Uses                                     ││
│  │                       ▼                                          ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Business Services                             │    ││
│  │  │  RdfParsingService         ConflictDetectionService      │    ││
│  │  │  RdfPatchService           SelectorResolutionService     │    ││
│  │  │  GraphDiffService          TimestampResolutionService    │    ││
│  │  │  GraphSerializationService PreconditionService           │    ││
│  │  │  RefService                SnapshotService               │    ││
│  │  │  BranchService             TagService                    │    ││
│  │  │  DatasetService                                          │    ││
│  │  └────────────────────┬─────────────────────────────────────┘    ││
│  │                       │ Creates                                  ││
│  │                       ▼                                          ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Domain Events                                 │    ││
│  │  │  CommitCreatedEvent        BranchRebasedEvent            │    ││
│  │  │  BranchCreatedEvent        CherryPickedEvent             │    ││
│  │  │  BranchResetEvent          CommitsSquashedEvent          │    ││
│  │  │  TagCreatedEvent           RevertCreatedEvent            │    ││
│  │  │  SnapshotCreatedEvent      BatchGraphsCompletedEvent     │    ││
│  │  └────────────────────┬─────────────────────────────────────┘    ││
│  │                       │ Publishes                                ││
│  │                       ▼                                          ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Event Publisher                               │    ││
│  │  │  EventPublisher (Kafka Producer)                         │    ││
│  │  └────────────────────┬─────────────────────────────────────┘    ││
│  └────────────────────────┼─────────────────────────────────────────┘│
│                           │                                          │
│                           ▼                                          │
│                    Apache Kafka                                      │
│                    (External System)                                 │
│                           │                                          │
│                           │ Consumes                                 │
│                           ▼                                          │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │                     Event Processing (Read Side)                 ││
│  │                                                                  ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Event Projector                               │    ││
│  │  │  ReadModelProjector (@KafkaListener)                     │    ││
│  │  │  - handleCommitCreated()                                 │    ││
│  │  │  - handleBranchCreated()                                 │    ││
│  │  │  - handleBranchReset()                                   │    ││
│  │  │  - handleTagCreated()                                    │    ││
│  │  │  - handleBranchRebased()                                 │    ││
│  │  │  - handleCherryPicked()                                  │    ││
│  │  │  - handleCommitsSquashed()                               │    ││
│  │  │  - handleRevertCreated()                                 │    ││
│  │  │  - handleSnapshotCreated()                               │    ││
│  │  │  - handleBatchGraphsCompleted()                          │    ││
│  │  └────────────────────┬─────────────────────────────────────┘    ││
│  │                       │ Updates                                  ││
│  │                       ▼                                          ││
│  │  ┌──────────────────────────────────────────────────────────┐    ││
│  │  │            Read Model Repositories                       │    ││
│  │  │  CommitRepository                                        │    ││
│  │  │  BranchRepository                                        │    ││
│  │  │  TagRepository                                           │    ││
│  │  │  DatasetGraphRepository (In-Memory Jena Graphs)          │    ││
│  │  └────────────────────▲─────────────────────────────────────┘    ││
│  └────────────────────────┼─────────────────────────────────────────┘│
│                           │ Queries                                  │
│  ┌────────────────────────┴─────────────────────────────────────────┐│
│  │                    Query Side (Read Model)                       ││
│  │                                                                  ││
│  │  Controllers query repositories directly for read operations     ││
│  │  - GET /data (GraphStoreController → DatasetGraphRepository)     ││
│  │  - GET /sparql (SparqlController → DatasetGraphRepository)       ││
│  │  - GET /version/branches (BranchController → BranchRepository)   ││
│  │  - GET /version/tags (TagController → TagRepository)             ││
│  │  - GET /version/history (HistoryController → CommitRepository)   ││
│  │                                                                  ││
│  └──────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────────┐│
│  │                    Cross-Cutting Components                      ││
│  │                                                                  ││
│  │  Domain Models: Commit, Branch, Tag, CommitId, Selector          ││
│  │  DTOs: CommitDTO, BranchDTO, TagDTO, ConflictDTO                 ││
│  │  Exception Handling: ProblemDetails (RFC 7807)                   ││
│  │  Configuration: Spring Boot Config, Kafka Config                 ││
│  │  Utilities: EventValidation, EventHeaders                        ││
│  │                                                                  ││
│  └──────────────────────────────────────────────────────────────────┘│
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Component Descriptions

### 1. Web Layer (Controllers)

**Package**: `org.chucc.vcserver.controller`

**Purpose**: HTTP endpoint handlers implementing REST API for SPARQL 1.2 Protocol + Version Control Extension

**Components**:

1. **GraphStoreController**
   - Implements Graph Store Protocol (GSP)
   - Endpoints: GET/PUT/POST/DELETE/PATCH /data
   - Handles RDF graph CRUD operations
   - Supports content negotiation (Turtle, N-Triples, JSON-LD, RDF/XML)
   - Returns ETags for optimistic concurrency control

2. **SparqlController**
   - Implements SPARQL Protocol
   - Endpoints: GET/POST /sparql
   - Executes SPARQL queries (SELECT, CONSTRUCT, ASK, DESCRIBE)
   - Supports selectors (branch, commit, asOf)
   - Returns results in JSON, XML, CSV, TSV

3. **BranchController**
   - Endpoints: GET/POST/PUT /version/branches
   - Branch management (create, list, get, reset)
   - Branch metadata retrieval

4. **TagController**
   - Endpoints: GET/POST /version/tags
   - Tag management (create, list, get)
   - Immutable tags for releases

5. **CommitController**
   - Endpoints: GET /version/commits/{id}
   - Retrieve commit details and patches

6. **HistoryController**
   - Endpoints: GET /version/history
   - Query commit history with filters
   - Pagination support

7. **MergeController**
   - Endpoints: POST /version/merge
   - Three-way merge with conflict detection
   - Returns merge result or conflicts

8. **AdvancedOpsController**
   - Endpoints: POST /version/rebase, /version/cherry-pick, /version/squash, /version/revert
   - Advanced version control operations

9. **BatchController**
   - Endpoints: POST /version/batch-graphs
   - Atomic batch graph operations

10. **BatchGraphsController**
    - Additional batch operations endpoint

11. **RefsController**
    - Endpoints: GET /version/refs
    - List all references (branches + tags)

**Responsibilities**:
- Validate HTTP requests
- Parse request bodies (RDF, JSON)
- Delegate to command handlers
- Return HTTP responses with proper status codes and headers
- Handle errors and return RFC 7807 Problem Details

**Dependencies**:
- Command Handlers (for writes)
- Repositories (for reads)
- Services (RdfParsingService, GraphSerializationService, SelectorResolutionService)

---

### 2. Command Side (Write Model)

**Package**: `org.chucc.vcserver.command`

**Purpose**: Handle write operations by creating events (CQRS pattern)

#### 2.1 Command Handlers

**Components**:

**Graph Store Protocol Handlers**:
1. **PutGraphCommandHandler**
   - Creates or replaces a graph
   - Computes RDF diff (add/delete triples)
   - Creates `CommitCreatedEvent`

2. **PostGraphCommandHandler**
   - Merges triples into existing graph
   - Computes additive diff
   - Creates `CommitCreatedEvent`

3. **PatchGraphCommandHandler**
   - Applies RDF Patch to graph
   - Validates patch operations
   - Creates `CommitCreatedEvent`

4. **DeleteGraphCommandHandler**
   - Deletes entire graph
   - Creates delete-all patch
   - Creates `CommitCreatedEvent`

5. **BatchGraphsCommandHandler**
   - Processes multiple graph operations atomically
   - Creates `BatchGraphsCompletedEvent` with combined patch

**Version Control Handlers**:
6. **CreateCommitCommandHandler**
   - Creates explicit commit
   - Validates author, message
   - Creates `CommitCreatedEvent`

7. **CreateBranchCommandHandler**
   - Creates new branch from commit
   - Validates branch name uniqueness
   - Creates `BranchCreatedEvent`

8. **CreateTagCommandHandler**
   - Creates immutable tag
   - Validates tag name uniqueness
   - Creates `TagCreatedEvent`

9. **ResetBranchCommandHandler**
   - Moves branch pointer to different commit
   - Creates `BranchResetEvent`

**Advanced Operation Handlers**:
10. **RevertCommitCommandHandler**
    - Creates inverse commit
    - Creates `RevertCreatedEvent`

11. **RebaseCommandHandler**
    - Replays commits on new base
    - Creates `BranchRebasedEvent`

12. **CherryPickCommandHandler**
    - Applies commit to different branch
    - Creates `CherryPickedEvent`

13. **SquashCommandHandler**
    - Combines multiple commits
    - Creates `CommitsSquashedEvent`

**Key Pattern**:
```java
public interface CommandHandler<C extends Command> {
  VersionControlEvent handle(C command);
}
```

All handlers:
1. Validate command
2. Query current state from repositories
3. Apply business logic
4. Create event
5. Publish event to Kafka (via EventPublisher)
6. Return event (for HTTP response)

**Important**: Handlers do NOT update repositories - that's done by projectors.

---

#### 2.2 Business Services

**Components**:

1. **RdfParsingService**
   - Parse RDF from HTTP requests
   - Supports: Turtle, N-Triples, JSON-LD, RDF/XML, TriG, N-Quads
   - Validates RDF syntax
   - Returns Jena Model or DatasetGraph

2. **RdfPatchService**
   - Parse RDFPatch text format
   - Convert between RDFPatch and internal format
   - Apply patches to graphs
   - Generate patch text

3. **GraphDiffService**
   - Compute difference between two graphs
   - Returns added and deleted triples
   - Used for PUT/POST operations

4. **GraphSerializationService**
   - Serialize Jena graphs to RDF formats
   - Content negotiation support
   - Handles large graphs efficiently

5. **ConflictDetectionService**
   - Three-way merge algorithm
   - Detects conflicting changes
   - Returns structured conflict representation
   - Used by MergeController

6. **SelectorResolutionService**
   - Resolves selectors (branch, commit, asOf) to CommitId
   - Handles default branch resolution
   - Validates selector existence

7. **TimestampResolutionService**
   - Resolves asOf timestamp to CommitId
   - Finds latest commit ≤ timestamp
   - Uses UUIDv7 ordering for efficiency

8. **PreconditionService**
   - Validates If-Match, If-None-Match headers
   - ETag comparison for optimistic locking
   - Returns 412 Precondition Failed on mismatch

9. **RefService**
   - Lists all references (branches + tags)
   - Combines branch and tag repositories

10. **BranchService**
    - Branch-specific query operations
    - Lists branches with full metadata (protected status, timestamps, commit count)
    - Provides detailed branch information for API endpoints
    - Read-side service (queries only, no mutations)

11. **TagService**
    - Tag-specific business logic
    - Validates tag immutability

12. **SnapshotService**
    - Creates dataset snapshots
    - Combines all graphs into single commit

13. **DatasetService**
    - Dataset-level operations
    - Currently hardcoded to "default" dataset

**Responsibilities**:
- Pure business logic (no side effects)
- Stateless (can be safely parallelized)
- Used by command handlers and controllers
- No direct database/Kafka access

---

### 3. Event Publishing

**Package**: `org.chucc.vcserver.event`

**Purpose**: Publish events to Kafka (Event Sourcing)

**Components**:

1. **EventPublisher**
   - Wraps Spring Kafka Producer
   - Publishes events to `<dataset>-events` topic
   - Returns `CompletableFuture<RecordMetadata>` for async tracking
   - Handles serialization (JSON)
   - Adds event headers (eventId, eventType, timestamp)

2. **Domain Events** (10 event types):
   - `CommitCreatedEvent` - New commit with RDF patch
   - `BranchCreatedEvent` - New branch created
   - `BranchResetEvent` - Branch pointer moved
   - `BranchRebasedEvent` - Branch rebased onto new base
   - `TagCreatedEvent` - Immutable tag created
   - `CherryPickedEvent` - Commit cherry-picked to branch
   - `CommitsSquashedEvent` - Multiple commits combined
   - `RevertCreatedEvent` - Commit reverted
   - `SnapshotCreatedEvent` - Dataset snapshot created
   - `BatchGraphsCompletedEvent` - Batch operation completed

All events extend `VersionControlEvent` interface.

**Event Structure**:
```java
public record CommitCreatedEvent(
  String dataset,
  CommitId commitId,
  CommitId parentId,  // null for initial commit
  String author,
  String message,
  Instant timestamp,
  String patch  // RDFPatch text format
) implements VersionControlEvent { }
```

**Event Publishing Flow**:
```java
// Command handler creates event
CommitCreatedEvent event = new CommitCreatedEvent(...);

// Publish to Kafka (async)
CompletableFuture<RecordMetadata> future = eventPublisher.publish(event);

// HTTP response returned IMMEDIATELY (don't wait for projection)
return ResponseEntity.ok().eTag(commitId.value()).build();
```

**Key Point**: HTTP responses return **before** repositories are updated. Updates happen asynchronously via projectors.

---

### 4. Event Processing (Read Side)

**Package**: `org.chucc.vcserver.projection`

**Purpose**: Consume events from Kafka and update read model repositories

**Components**:

1. **ReadModelProjector**
   - Annotated with `@KafkaListener`
   - Consumes from `<dataset>-events` topic
   - Consumer group: `chucc-server-projector`
   - Processes events in order (per partition)

**Event Handlers**:
```java
@Component
public class ReadModelProjector {

  @KafkaListener(topics = "${kafka.topic-prefix}-events")
  public void handleCommitCreated(CommitCreatedEvent event) {
    // 1. Save commit to CommitRepository
    commitRepository.save(event.dataset(), commit, patch);

    // 2. Update branch head pointer
    branchRepository.updateBranchHead(event.dataset(), branch, commitId);

    // 3. Rebuild graph by applying patch
    datasetGraphRepository.applyPatch(event.dataset(), commitId, patch);
  }

  @KafkaListener(topics = "${kafka.topic-prefix}-events")
  public void handleBranchCreated(BranchCreatedEvent event) {
    branchRepository.save(event.dataset(), branch);
  }

  @KafkaListener(topics = "${kafka.topic-prefix}-events")
  public void handleBranchReset(BranchResetEvent event) {
    branchRepository.updateBranchHead(event.dataset(), branch, newCommitId);
  }

  @KafkaListener(topics = "${kafka.topic-prefix}-events")
  public void handleTagCreated(TagCreatedEvent event) {
    tagRepository.save(event.dataset(), tag);
  }

  // ... handlers for other 6 event types
}
```

**Key Characteristics**:
- **Idempotent**: Same event can be processed multiple times safely
- **Ordered**: Events processed in commit order (per partition)
- **Async**: Updates repositories asynchronously (eventual consistency)
- **Rebuilds State**: On startup, replays all events to rebuild state

**Test Isolation**:
- **Disabled by default** in integration tests (`projector.kafka-listener.enabled: false`)
- Prevents cross-test contamination (tests share same Kafka topics)
- Enabled only in dedicated projector tests (`GraphEventProjectorIT`, `VersionControlProjectorIT`)

---

### 5. Read Model Repositories

**Package**: `org.chucc.vcserver.repository`

**Purpose**: Store materialized read models for fast queries

**Components**:

1. **CommitRepository**
   - Stores commits and their patches
   - Structure: `ConcurrentHashMap<(Dataset, CommitId), Commit>`
   - Operations: save, findById, findAll, findByParent, delete
   - Used by: HistoryController, Command Handlers (for lineage validation)

2. **BranchRepository**
   - Stores branch metadata and head pointers
   - Structure: `ConcurrentHashMap<(Dataset, BranchName), Branch>`
   - Operations: save, findById, findAll, updateHead, delete
   - Used by: BranchController, SelectorResolutionService

3. **TagRepository**
   - Stores immutable tags
   - Structure: `ConcurrentHashMap<(Dataset, TagName), Tag>`
   - Operations: save, findById, findAll, delete
   - Used by: TagController, SelectorResolutionService

4. **DatasetGraphRepository** (Implicit)
   - Stores materialized RDF graphs (Apache Jena `DatasetGraph`)
   - Structure: `ConcurrentHashMap<(Dataset, CommitId), DatasetGraph>`
   - Operations: applyPatch, getGraph, buildGraph
   - Used by: GraphStoreController, SparqlController
   - Implementation: In-memory Jena `DatasetGraphInMemory`

**Key Characteristics**:
- **In-Memory**: All data stored in JVM heap (fast but not durable)
- **Thread-Safe**: Use `ConcurrentHashMap` for concurrent access
- **Eventually Consistent**: Updated asynchronously by projectors
- **Rebuildable**: Can rebuild from event log (Kafka)

**Storage Model**:
```java
// Commit storage
class CommitRepository {
  private final ConcurrentHashMap<
    CompositeKey<String, CommitId>,  // (dataset, commitId)
    Commit                           // commit metadata
  > commits = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<
    CompositeKey<String, CommitId>,  // (dataset, commitId)
    RDFPatch                         // patch for this commit
  > patches = new ConcurrentHashMap<>();
}

// Graph storage (conceptual)
class DatasetGraphRepository {
  private final ConcurrentHashMap<
    CompositeKey<String, CommitId>,   // (dataset, commitId)
    DatasetGraph                      // Jena in-memory graph
  > graphs = new ConcurrentHashMap<>();
}
```

---

### 6. Query Side (Read Operations)

**Purpose**: Controllers query repositories directly for read operations (no commands/events)

**Read Flows**:

1. **Get Graph** (GET /data):
   ```
   GraphStoreController
     → SelectorResolutionService.resolve(selector) → CommitId
     → DatasetGraphRepository.getGraph(commitId) → DatasetGraph
     → GraphSerializationService.serialize(graph) → RDF
     → HTTP Response (200 OK + ETag)
   ```

2. **Execute SPARQL Query** (GET /sparql):
   ```
   SparqlController
     → SelectorResolutionService.resolve(selector) → CommitId
     → DatasetGraphRepository.getGraph(commitId) → DatasetGraph
     → Jena QueryExecution.execute(query, graph) → ResultSet
     → Serialize results (JSON/XML/CSV/TSV)
     → HTTP Response (200 OK)
   ```

3. **List Branches** (GET /version/branches):
   ```
   BranchController
     → BranchRepository.findAll(dataset) → List<Branch>
     → Convert to DTOs
     → HTTP Response (200 OK + JSON)
   ```

4. **Get Commit History** (GET /version/history):
   ```
   HistoryController
     → CommitRepository.findAll(dataset) → List<Commit>
     → Filter by branch/author/since/until
     → Paginate
     → Convert to DTOs
     → HTTP Response (200 OK + JSON)
   ```

**Key Point**: Read operations are **fast** (in-memory) and **eventually consistent** (may lag writes by milliseconds).

---

### 7. Cross-Cutting Components

#### 7.1 Domain Models

**Package**: `org.chucc.vcserver.domain`

**Purpose**: Core domain entities (immutable value objects)

**Components**:
- `Commit` - Commit metadata (id, parent, author, message, timestamp)
- `Branch` - Branch metadata (name, head commit)
- `Tag` - Tag metadata (name, target commit)
- `CommitId` - UUIDv7 identifier for commits
- `Selector` - Union type (branch | commit | asOf)
- `DatasetRef` - Dataset identifier
- `GraphIdentifier` - Named graph URI

**Characteristics**:
- Immutable (Java records)
- No business logic (pure data)
- Used across all layers

#### 7.2 DTOs (Data Transfer Objects)

**Package**: `org.chucc.vcserver.dto`

**Purpose**: JSON serialization for HTTP responses

**Components**:
- `CommitDTO` - Commit JSON representation
- `BranchDTO` - Branch JSON representation
- `TagDTO` - Tag JSON representation
- `ConflictDTO` - Merge conflict representation
- `ErrorDTO` - RFC 7807 Problem Details

**Characteristics**:
- Annotated with Jackson annotations
- Include HATEOAS links
- Separate from domain models (avoid coupling HTTP to domain)

#### 7.3 Exception Handling

**Package**: `org.chucc.vcserver.exception`

**Purpose**: Structured error handling with RFC 7807 Problem Details

**Components**:
- `NotFoundException` - 404 Not Found
- `ConflictException` - 409 Conflict
- `BadRequestException` - 400 Bad Request
- `PreconditionFailedException` - 412 Precondition Failed
- `GlobalExceptionHandler` - @ControllerAdvice for error translation

**Error Response Format**:
```json
{
  "type": "https://chucc.org/errors/not-found",
  "title": "Branch Not Found",
  "status": 404,
  "detail": "Branch 'feature-x' not found in dataset 'default'",
  "instance": "/version/branches/feature-x"
}
```

#### 7.4 Configuration

**Package**: `org.chucc.vcserver.config`

**Purpose**: Spring Boot and Kafka configuration

**Components**:
- `KafkaConfig` - Kafka producer/consumer configuration
- `WebConfig` - HTTP content negotiation configuration
- `SwaggerConfig` - OpenAPI documentation configuration

#### 7.5 Utilities

**Package**: `org.chucc.vcserver.util`

**Purpose**: Helper classes

**Components**:
- `EventValidation` - Event validation utilities
- `EventHeaders` - Event header constants

---

## Component Interactions

### Write Operation Flow (PUT /data)

```
1. HTTP Request
   ↓
2. GraphStoreController.put()
   ↓
3. RdfParsingService.parse(request)
   ↓
4. SelectorResolutionService.resolve(selector) → CommitId
   ↓
5. DatasetGraphRepository.getGraph(commitId) → Current graph
   ↓
6. GraphDiffService.diff(current, new) → Added/Deleted triples
   ↓
7. PutGraphCommandHandler.handle(command)
   ↓
8. Create CommitCreatedEvent
   ↓
9. EventPublisher.publish(event) → Kafka (async)
   ↓
10. HTTP Response (200 OK + ETag) ← IMMEDIATE RETURN
   ↓
[Later, asynchronously...]
   ↓
11. ReadModelProjector.handleCommitCreated(event)
    ↓
12. CommitRepository.save(commit)
    ↓
13. BranchRepository.updateHead(branch, commitId)
    ↓
14. DatasetGraphRepository.applyPatch(commitId, patch)
```

**Key Points**:
- Steps 1-10 happen **synchronously** (~10-50ms)
- Steps 11-14 happen **asynchronously** (milliseconds later)
- HTTP client receives response **before** repositories updated

### Read Operation Flow (GET /data)

```
1. HTTP Request
   ↓
2. GraphStoreController.get()
   ↓
3. SelectorResolutionService.resolve(selector) → CommitId
   ↓
4. DatasetGraphRepository.getGraph(commitId) → DatasetGraph
   ↓
5. GraphSerializationService.serialize(graph) → RDF
   ↓
6. HTTP Response (200 OK + ETag + RDF)
```

**Key Points**:
- All steps synchronous (~1-10ms)
- Fast (in-memory reads)
- Eventually consistent (may see slightly stale data after writes)

### Merge Operation Flow (POST /version/merge)

```
1. HTTP Request
   ↓
2. MergeController.merge()
   ↓
3. SelectorResolutionService.resolve(sourceBranch) → sourceCommitId
   ↓
4. SelectorResolutionService.resolve(targetBranch) → targetCommitId
   ↓
5. CommitRepository.findCommonAncestor(source, target) → baseCommitId
   ↓
6. DatasetGraphRepository.getGraph(baseCommitId) → baseGraph
   DatasetGraphRepository.getGraph(sourceCommitId) → sourceGraph
   DatasetGraphRepository.getGraph(targetCommitId) → targetGraph
   ↓
7. ConflictDetectionService.merge(base, source, target)
   ↓
8a. If conflicts → HTTP Response (409 Conflict + ConflictDTO)
   ↓
8b. If no conflicts → Create CommitCreatedEvent
   ↓
9. EventPublisher.publish(event)
   ↓
10. HTTP Response (200 OK + CommitDTO)
```

---

## Component Dependencies

### Dependency Graph

```
Controllers
  ↓ depends on
Command Handlers + Services + Repositories
  ↓ depends on
Domain Models + DTOs
  ↓ depends on
(nothing - pure data)

Command Handlers
  ↓ depends on
Services + Repositories + Events + EventPublisher
  ↓ depends on
Domain Models

EventPublisher
  ↓ depends on
Kafka (external)

ReadModelProjector
  ↓ depends on
Repositories + Domain Models
  ↑ consumes
Kafka (external)
```

### Dependency Rules

**Layer Architecture** (enforced by package structure):
1. **Controllers** depend on: Handlers, Services, Repositories
2. **Handlers** depend on: Services, Repositories, Events, EventPublisher
3. **Services** depend on: Domain Models (pure logic, no side effects)
4. **Repositories** depend on: Domain Models (storage only)
5. **Events** depend on: Domain Models (pure data)
6. **Domain Models** depend on: Nothing (pure data)

**Circular Dependency Prevention**:
- Controllers do NOT call each other
- Handlers do NOT call other handlers
- Services are stateless and can call other services
- Repositories do NOT call services or handlers

**Dependency Injection**:
- All components managed by Spring
- Constructor injection (required dependencies)
- No field injection (testability)

---

## Testing Strategy per Component

### 1. Controllers

**Test Type**: Integration Tests (with mocked Kafka)

**Pattern**:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreControllerIT {
  @Test
  void putGraph_shouldReturn200() {
    // Arrange
    String rdf = "...";

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
      "/data?branch=main", PUT, new HttpEntity<>(rdf), String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
```

**Key Point**: Projector **disabled** by default (test API contract only)

### 2. Command Handlers

**Test Type**: Unit Tests (mock dependencies)

**Pattern**:
```java
class PutGraphCommandHandlerTest {
  @Test
  void handle_shouldCreateCommitCreatedEvent() {
    // Arrange
    var handler = new PutGraphCommandHandler(
      mockGraphDiffService,
      mockEventPublisher,
      mockDatasetGraphRepository);
    var command = new PutGraphCommand(...);

    // Act
    var event = handler.handle(command);

    // Assert
    assertThat(event).isInstanceOf(CommitCreatedEvent.class);
    assertThat(event.patch()).contains("A <s> <p> <o>");
    verify(mockEventPublisher).publish(event);
  }
}
```

### 3. Services

**Test Type**: Unit Tests (pure logic)

**Pattern**:
```java
class ConflictDetectionServiceTest {
  @Test
  void merge_shouldDetectConflicts() {
    // Arrange
    var service = new ConflictDetectionService();
    var base = createGraph("...");
    var source = createGraph("...");
    var target = createGraph("...");

    // Act
    var result = service.merge(base, source, target);

    // Assert
    assertThat(result.hasConflicts()).isTrue();
    assertThat(result.conflicts()).hasSize(2);
  }
}
```

### 4. Projectors

**Test Type**: Integration Tests (with **enabled** projector)

**Pattern**:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable!
class GraphEventProjectorIT {
  @Test
  void handleCommitCreated_shouldUpdateRepositories() throws Exception {
    // Arrange
    var event = new CommitCreatedEvent(...);

    // Act
    eventPublisher.publish(event).get();

    // Assert: Wait for async projection
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        var commit = commitRepository.findById(commitId);
        assertThat(commit).isPresent();
        assertThat(commit.get().author()).isEqualTo("Alice");
      });
  }
}
```

**Key Point**: Projector **enabled** explicitly + use `await()` for async

### 5. Repositories

**Test Type**: Unit Tests (simple storage tests)

**Pattern**:
```java
class CommitRepositoryTest {
  @Test
  void save_shouldStoreCommit() {
    // Arrange
    var repository = new CommitRepository();
    var commit = new Commit(...);

    // Act
    repository.save("default", commit);

    // Assert
    var found = repository.findById("default", commit.id());
    assertThat(found).isPresent();
    assertThat(found.get()).isEqualTo(commit);
  }
}
```

### Test Isolation Rule

**CRITICAL**: Projector disabled by default in tests to prevent cross-test contamination:
- 90% of tests: API layer (projector **disabled**)
- 10% of tests: Projector tests (projector **enabled** + `await()`)

---

## Key Interfaces

### 1. Command Handler Interface

```java
/**
 * Handles a command by creating an event.
 *
 * @param <C> command type
 */
public interface CommandHandler<C extends Command> {
  /**
   * Handle the command and return the created event.
   *
   * @param command the command to handle
   * @return the created event
   */
  VersionControlEvent handle(C command);
}
```

### 2. Event Interface

```java
/**
 * Base interface for all version control events.
 */
public interface VersionControlEvent {
  String dataset();
  CommitId commitId();
  Instant timestamp();
  String eventType();
}
```

### 3. Repository Interface (Example)

```java
/**
 * Repository for commits.
 */
public interface CommitRepository {
  void save(String dataset, Commit commit, RDFPatch patch);
  Optional<Commit> findById(String dataset, CommitId id);
  List<Commit> findAll(String dataset);
  List<Commit> findByParent(String dataset, CommitId parentId);
  void delete(String dataset, CommitId id);
}
```

---

## Component Communication Patterns

### 1. Synchronous (Controllers → Services)

- **Pattern**: Direct method calls
- **Return**: Immediate results
- **Used for**: Read operations, validation, business logic

### 2. Asynchronous (Command → Event → Projection)

- **Pattern**: Event-driven via Kafka
- **Return**: HTTP response returns before projection complete
- **Used for**: Write operations (eventual consistency)

### 3. Event Sourcing (Kafka → Projector → Repositories)

- **Pattern**: Event replay to rebuild state
- **Return**: No return value (side effects only)
- **Used for**: Read model updates, system recovery

---

## Component Scalability

### Horizontal Scaling

**Controllers + Command Handlers**:
- Stateless (can run multiple instances)
- Load balanced via HTTP reverse proxy
- No shared state (except Kafka and repositories)

**Projectors**:
- Consumer group allows multiple instances
- Kafka partitions enable parallelism
- Each partition processed by one consumer

**Repositories**:
- In-memory (limited to single JVM)
- Future: External cache (Redis) for multi-instance coordination

### Vertical Scaling

**Memory**:
- Increase JVM heap for larger datasets
- Repositories scale with commit count and graph size

**CPU**:
- Parallel query execution (Jena supports parallelism)
- Parallel event processing (multiple Kafka partitions)

---

## Component Design Principles

### 1. Single Responsibility Principle (SRP)

Each component has ONE reason to change:
- Controllers: HTTP concerns
- Handlers: Command processing
- Services: Business logic
- Repositories: Storage
- Projectors: Event processing

### 2. Open/Closed Principle (OCP)

- New event types: Add handler to projector (no changes to other components)
- New endpoints: Add controller (no changes to existing controllers)
- New operations: Add command + handler (no changes to event flow)

### 3. Dependency Inversion Principle (DIP)

- Components depend on abstractions (interfaces), not implementations
- Example: CommandHandler interface, Repository interfaces

### 4. Interface Segregation Principle (ISP)

- Small, focused interfaces
- Example: Separate repository interfaces per entity

### 5. Command Query Responsibility Segregation (CQRS)

- **Write Model**: Commands → Events → Kafka
- **Read Model**: Queries → Repositories
- Separate concerns enable independent scaling and optimization

---

## References

- [C4 Model Website](https://c4model.com/)
- [Level 1: Context Diagram](c4-level1-context.md)
- [Level 2: Container Diagram](c4-level2-container.md)
- [CQRS & Event Sourcing Guide](cqrs-event-sourcing.md)
- [Architecture Overview](README.md)

---

## Summary

**CHUCC Server** internal component structure implements CQRS + Event Sourcing with:

**Write Path**:
- Controllers → Command Handlers → Events → Kafka → Projectors → Repositories

**Read Path**:
- Controllers → Repositories → HTTP Response (fast, in-memory)

**Key Components**:
- 11 Controllers (HTTP endpoints)
- 13 Command Handlers (write operations)
- 12 Services (business logic)
- 3 Repositories + 1 Graph Repository (read model)
- 1 Projector with 10 event handlers (async updates)
- 10 Event types (domain events)

**Key Patterns**:
- Command Handler pattern (write model)
- Repository pattern (read model)
- Event Sourcing (Kafka as source of truth)
- Eventual Consistency (async projection)
- Test Isolation (projector disabled by default)

**Component Boundaries**:
- Clear separation between write and read concerns
- Domain models independent of infrastructure
- Services stateless and reusable
- Controllers handle HTTP concerns only
