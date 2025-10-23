# CQRS + Event Sourcing Architecture

**Deep dive into CHUCC Server's core architectural pattern**

## Table of Contents

1. [Overview](#overview)
2. [What is CQRS?](#what-is-cqrs)
3. [What is Event Sourcing?](#what-is-event-sourcing)
4. [Why CQRS + Event Sourcing?](#why-cqrs--event-sourcing)
5. [How It Works in CHUCC](#how-it-works-in-chucc)
6. [Write Model (Command Side)](#write-model-command-side)
7. [Read Model (Query Side)](#read-model-query-side)
8. [Event Flow](#event-flow)
9. [Benefits](#benefits)
10. [Trade-offs](#trade-offs)
11. [Testing Implications](#testing-implications)
12. [Common Patterns](#common-patterns)
13. [Exception Handling](#exception-handling)
14. [Anti-Patterns](#anti-patterns)
15. [Troubleshooting](#troubleshooting)
16. [References](#references)

---

## Overview

CHUCC Server implements **CQRS (Command Query Responsibility Segregation)** with **Event Sourcing** to provide:
- Git-like version control for RDF data
- Complete audit trail of all changes
- Time-travel queries (asOf selector)
- Eventual consistency with high write throughput
- Ability to rebuild state from events

This document explains the pattern, its implementation, and how to work with it effectively.

---

## What is CQRS?

**CQRS (Command Query Responsibility Segregation)** separates read and write operations into different models.

### Traditional Architecture (Without CQRS)

```
┌─────────────┐
│  Controller │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Service   │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  Database   │  ← Single model for reads AND writes
└─────────────┘
```

**Problem**: Same model optimized for both reads and writes leads to compromises:
- Complex queries slow down writes
- Write validations slow down reads
- Scaling reads and writes independently is difficult

### CQRS Architecture

```
Write Side (Commands)              Read Side (Queries)
┌─────────────┐                    ┌─────────────┐
│  Controller │                    │  Controller │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       ▼                                  ▼
┌─────────────┐                    ┌─────────────┐
│   Command   │                    │ Query       │
│   Handler   │                    │ Service     │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       ▼                                  ▼
┌─────────────┐                    ┌─────────────┐
│ Write Model │                    │ Read Model  │
│  (Events)   │ ──── Sync ───────> │ (Repository)│
└─────────────┘                    └─────────────┘
```

**Key Idea**:
- **Commands** change state (PUT, POST, DELETE)
- **Queries** read state (GET)
- Different models optimized for each concern

---

## What is Event Sourcing?

**Event Sourcing** stores state as a sequence of events rather than current state.

### Traditional Persistence (State-Based)

```
Database stores CURRENT state:

Branches Table:
┌──────────┬──────────┬─────────────┐
│ Name     │ HeadID   │ Updated     │
├──────────┼──────────┼─────────────┤
│ main     │ commit-3 │ 2025-10-09  │  ← Only current state!
└──────────┴──────────┴─────────────┘

Lost Information:
- What were previous values?
- When did changes occur?
- Who made changes?
- Why were changes made?
```

### Event Sourcing (Event-Based)

```
Event Store contains ALL events:

Event Log:
┌────┬─────────────────────┬────────────┬─────────────┐
│ #  │ Event Type          │ Data       │ Timestamp   │
├────┼─────────────────────┼────────────┼─────────────┤
│ 1  │ BranchCreated       │ name=main  │ 10:00:00    │
│ 2  │ CommitCreated       │ id=commit-1│ 10:05:00    │
│ 3  │ BranchReset         │ head=c1    │ 10:06:00    │
│ 4  │ CommitCreated       │ id=commit-2│ 10:10:00    │
│ 5  │ BranchReset         │ head=c2    │ 10:11:00    │
│ 6  │ CommitCreated       │ id=commit-3│ 10:15:00    │
│ 7  │ BranchReset         │ head=c3    │ 10:16:00    │
└────┴─────────────────────┴────────────┴─────────────┘

Current State = Replay events 1-7:
  main.head = commit-3

Historical State (10:12:00) = Replay events 1-5:
  main.head = commit-2  ← Time-travel!
```

**Key Idea**:
- Events are **immutable facts** about what happened
- Current state is computed by **replaying events**
- History is **never lost** (complete audit trail)

---

## Why CQRS + Event Sourcing?

CHUCC Server needs Git-like version control for RDF data. This requires:

### Requirements

1. **Version Control**: Track all changes to RDF graphs
2. **Branching**: Multiple parallel versions
3. **Merging**: Combine changes from different branches
4. **Time-Travel**: Query historical states (`asOf` selector)
5. **Audit Trail**: Who changed what, when, and why
6. **Conflict Detection**: Identify conflicting edits

### Why Traditional Architecture Fails

**Problem 1: No History**
```java
// Traditional: Update overwrites history
branchRepository.updateHead(branchName, newCommitId);
// Lost: What was the previous head?
```

**Problem 2: Complex Merge Logic**
```java
// Traditional: Merge logic mixed with database transactions
@Transactional
void merge(source, target) {
  // Complex merge logic + conflict detection
  // + database updates ALL in one transaction
  // → Slow, hard to test, hard to scale
}
```

**Problem 3: No Time-Travel**
```java
// Traditional: Only current state available
Graph graph = repository.getGraph(branchName);
// Cannot query graph as it was yesterday!
```

### Why CQRS + Event Sourcing Solves This

**Solution 1: Events Preserve History**
```java
// Event Sourcing: Every change recorded as event
eventPublisher.publish(new BranchResetEvent(branchName, newCommitId));
// History preserved: All BranchResetEvents stored forever
```

**Solution 2: Separate Write and Read Concerns**
```java
// CQRS: Write side (command handler)
CommitCreatedEvent event = mergeCommandHandler.handle(mergeCommand);
eventPublisher.publish(event);  // Fast, no database locks

// CQRS: Read side (projector - async)
@KafkaListener
void handleCommitCreated(CommitCreatedEvent event) {
  commitRepository.save(event.commit());  // Update read model
}
```

**Solution 3: Time-Travel via Event Replay**
```java
// Event Sourcing: Replay events up to timestamp
List<Event> events = eventStore.getEventsUntil(timestamp);
Graph historicalGraph = replayEvents(events);
// Can query any historical state!
```

---

## How It Works in CHUCC

### Architecture Overview

```
┌───────────────────────────────────────────────────────────────┐
│                         CHUCC Server                          │
│                                                               │
│  Write Side (Commands)          Event Store (Kafka)          │
│  ┌────────────────┐             ┌────────────────┐          │
│  │ Controller     │             │ Kafka Topics   │          │
│  │   ↓            │             │ default-events │          │
│  │ CommandHandler │ ──Publish─> │ • Event 1      │          │
│  │   ↓            │             │ • Event 2      │          │
│  │ EventPublisher │             │ • Event 3      │          │
│  └────────────────┘             │ • ...          │          │
│                                  └────────┬───────┘          │
│                                           │                  │
│                                           │ Consume          │
│                                           ▼                  │
│  Read Side (Queries)            ┌────────────────┐          │
│  ┌────────────────┐             │ Projector      │          │
│  │ Controller     │             │ (@KafkaListen) │          │
│  │   ↓            │             └────────┬───────┘          │
│  │ Repository     │ <────Updates────────┘                   │
│  │   ↓            │                                          │
│  │ HTTP Response  │                                          │
│  └────────────────┘                                          │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### Key Components

1. **Command Handlers** (Write Side)
   - Validate commands
   - Create events
   - Publish to Kafka
   - Return immediately (don't wait for projection)

2. **Event Publisher**
   - Wraps Kafka Producer
   - Publishes events to `<dataset>-events` topic
   - Async (non-blocking)

3. **Kafka** (Event Store)
   - Stores events with infinite retention
   - Provides ordering guarantee (per partition)
   - Source of truth

4. **Read Model Projector** (Read Side)
   - Consumes events from Kafka
   - Updates repositories (async)
   - Rebuilds state from events on startup

5. **Repositories** (Read Model)
   - In-memory storage for fast queries
   - Updated asynchronously by projectors
   - Eventually consistent with event log

---

## Write Model (Command Side)

### Command Flow

```
1. HTTP Request (PUT /data?branch=main)
   ↓
2. GraphStoreController.put()
   ↓
3. Validate request (RDF syntax, branch exists)
   ↓
4. Resolve selector (branch=main → commitId=commit-123)
   ↓
5. Load current graph (from read model)
   ↓
6. Compute diff (added/deleted triples)
   ↓
7. Create command (PutGraphCommand)
   ↓
8. PutGraphCommandHandler.handle(command)
   ↓
9. Create event (CommitCreatedEvent with RDFPatch)
   ↓
10. Publish event to Kafka (async)
    ↓
11. Return HTTP Response (200 OK + ETag)
    ← IMMEDIATE RETURN (before repositories updated!)
```

### Example: PUT Graph

```java
@RestController
public class GraphStoreController {

  @PutMapping("/data")
  public ResponseEntity<Void> put(
      @RequestParam(defaultValue = "main") String branch,
      @RequestBody String rdf) {

    // 1. Parse RDF
    Model newGraph = rdfParsingService.parse(rdf, contentType);

    // 2. Resolve selector to commitId
    CommitId currentCommitId = selectorResolutionService.resolve(
      new Selector.Branch(branch));

    // 3. Load current graph (from read model - fast!)
    DatasetGraph currentGraph = datasetGraphRepository.getGraph(currentCommitId);

    // 4. Compute diff
    RDFPatch patch = graphDiffService.diff(currentGraph, newGraph);

    // 5. Create command
    PutGraphCommand command = new PutGraphCommand(
      branch, graphName, patch, author, message);

    // 6. Handle command (creates event)
    CommitCreatedEvent event = putGraphCommandHandler.handle(command);

    // 7. Publish event to Kafka (async!)
    eventPublisher.publish(event);  // Returns CompletableFuture

    // 8. Return HTTP Response IMMEDIATELY
    // ⚠️ Repositories NOT YET UPDATED at this point!
    return ResponseEntity.ok()
      .eTag("\"" + event.commitId().value() + "\"")
      .build();
  }
}
```

**CRITICAL**: HTTP response returns **before** repositories are updated!

### Command Handler Example

```java
@Component
public class PutGraphCommandHandler implements CommandHandler<PutGraphCommand> {

  private final EventPublisher eventPublisher;
  private final DatasetGraphRepository datasetGraphRepository;  // Read model

  @Override
  public CommitCreatedEvent handle(PutGraphCommand command) {
    // 1. Load current graph (from read model)
    DatasetGraph currentGraph = datasetGraphRepository.getGraph(
      command.dataset(), command.branch());

    // 2. Apply business logic (compute diff)
    RDFPatch patch = graphDiffService.diff(currentGraph, command.newGraph());

    // 3. Create event (immutable fact)
    CommitId newCommitId = CommitId.generate();  // UUIDv7
    CommitCreatedEvent event = new CommitCreatedEvent(
      command.dataset(),
      newCommitId,
      command.parentCommitId(),
      command.author(),
      command.message(),
      Instant.now(),
      patch.toString()  // RDFPatch text format
    );

    // 4. Publish event (async)
    // ⚠️ Do NOT wait for publication (non-blocking)
    eventPublisher.publish(event);

    // 5. Return event (for HTTP response)
    return event;
  }
}
```

**Key Points**:
- Handler does NOT update repositories
- Handler creates event (immutable fact)
- Handler publishes event (async)
- Repositories updated later by projector

---

## Read Model (Query Side)

### Query Flow

```
1. HTTP Request (GET /data?branch=main)
   ↓
2. GraphStoreController.get()
   ↓
3. Resolve selector (branch=main → commitId=commit-123)
   ↓
4. Query repository (read model)
   ↓
5. Serialize graph (Turtle, JSON-LD, etc.)
   ↓
6. Return HTTP Response (200 OK + ETag + RDF)
```

### Example: GET Graph

```java
@RestController
public class GraphStoreController {

  @GetMapping("/data")
  public ResponseEntity<String> get(
      @RequestParam(defaultValue = "main") String branch,
      @RequestParam(required = false) String graph) {

    // 1. Resolve selector to commitId
    CommitId commitId = selectorResolutionService.resolve(
      new Selector.Branch(branch));

    // 2. Query read model (fast - in-memory!)
    DatasetGraph datasetGraph = datasetGraphRepository.getGraph(commitId);

    // 3. Extract named graph (or default graph)
    Model model = datasetGraph.getGraph(graphName);

    // 4. Serialize to RDF
    String rdf = graphSerializationService.serialize(model, acceptHeader);

    // 5. Return response
    return ResponseEntity.ok()
      .eTag("\"" + commitId.value() + "\"")
      .contentType(contentType)
      .body(rdf);
  }
}
```

**Key Points**:
- Queries are fast (in-memory reads)
- No business logic (just data retrieval)
- Eventually consistent (may lag writes by milliseconds)

### Projector Example

```java
@Component
public class ReadModelProjector {

  @KafkaListener(
    topics = "${kafka.topic-prefix}-events",
    groupId = "chucc-server-projector"
  )
  public void handleCommitCreated(CommitCreatedEvent event) {
    // 1. Save commit metadata
    Commit commit = new Commit(
      event.commitId(),
      event.parentId(),
      event.author(),
      event.message(),
      event.timestamp()
    );
    commitRepository.save(event.dataset(), commit);

    // 2. Update branch head pointer
    branchRepository.updateBranchHead(
      event.dataset(),
      event.branch(),
      event.commitId()
    );

    // 3. Apply patch to graph (rebuild state)
    RDFPatch patch = RDFPatchOps.read(event.patch());
    datasetGraphRepository.applyPatch(
      event.dataset(),
      event.commitId(),
      patch
    );

    // ✅ Read model NOW up-to-date with this event
  }

  @KafkaListener(
    topics = "${kafka.topic-prefix}-events",
    groupId = "chucc-server-projector"
  )
  public void handleBranchCreated(BranchCreatedEvent event) {
    Branch branch = new Branch(event.name(), event.headCommitId());
    branchRepository.save(event.dataset(), branch);
  }

  // ... handlers for other 8 event types
}
```

**Key Points**:
- Projector consumes events from Kafka
- Updates repositories (side effects)
- Idempotent (can replay same event safely)
- Ordered (events processed in commit order per partition)

---

## Event Flow

### Complete Write-Read Flow

```
Time  │ What Happens
──────┼─────────────────────────────────────────────────────────────
T0    │ Client: PUT /data?branch=main
      │   (Upload new RDF graph)
      │
T1    │ Controller: Parse RDF, validate
      │
T2    │ CommandHandler: Compute diff, create event
      │
T3    │ EventPublisher: Publish event to Kafka
      │   ↓ (async)
      │   Kafka: Event stored in topic
      │
T4    │ Controller: Return 200 OK + ETag
      │   ← HTTP RESPONSE SENT (repositories NOT yet updated!)
      │
      │ ... milliseconds pass ...
      │
T5    │ Kafka: Event delivered to consumer
      │
T6    │ Projector: Consume event
      │   - Update CommitRepository
      │   - Update BranchRepository
      │   - Apply patch to DatasetGraphRepository
      │
T7    │ ✅ Repositories NOW updated (eventual consistency)
      │
T8    │ Client: GET /data?branch=main
      │
T9    │ Controller: Query repository
      │   ✅ Sees updated graph (from T6)
      │
T10   │ Controller: Return 200 OK + RDF
```

**Timing**:
- T0 → T4: ~10-50ms (write latency)
- T4 → T7: ~1-10ms (projection latency)
- T8 → T10: ~1-10ms (read latency)

**Key Insight**: Total consistency window is ~2-60ms (acceptable for most use cases).

---

## Benefits

### 1. Complete Audit Trail

**Problem (Traditional)**:
```sql
UPDATE branches SET head = 'commit-3' WHERE name = 'main';
-- Lost: When? Who? Why? What was previous value?
```

**Solution (Event Sourcing)**:
```json
{
  "eventType": "BranchResetEvent",
  "branch": "main",
  "oldHead": "commit-2",
  "newHead": "commit-3",
  "author": "alice@example.com",
  "timestamp": "2025-10-09T10:16:00Z",
  "reason": "Merge feature-x into main"
}
```

Every change has:
- Who made the change (author)
- When it happened (timestamp)
- What changed (oldHead → newHead)
- Why it happened (reason/message)

### 2. Time-Travel Queries

**Capability**: Query any historical state

**Example**:
```http
GET /data?asOf=2025-10-08T10:00:00Z
```

**Implementation**:
```java
// Find all events before timestamp
List<Event> events = eventStore.getEventsUntil(timestamp);

// Replay events to rebuild historical state
DatasetGraph historicalGraph = replayEvents(events);

// Return historical graph
return historicalGraph;
```

**Use Cases**:
- Debug: "What did the graph look like when bug occurred?"
- Compliance: "Show me the data as it was on Dec 31, 2024"
- Comparison: "How has this ontology evolved over time?"

### 3. Event Replay (System Recovery)

**Capability**: Rebuild entire system state from events

**Scenario**: Server crashes, all in-memory data lost

**Recovery**:
```java
// On startup
@PostConstruct
void rebuildState() {
  // Consume ALL events from Kafka (from beginning)
  List<Event> allEvents = kafkaConsumer.consumeFromBeginning();

  // Replay events to rebuild repositories
  for (Event event : allEvents) {
    projector.handle(event);  // Updates repositories
  }

  // ✅ State fully restored!
}
```

**Why it works**: Kafka retains events forever (infinite retention).

### 4. Independent Scaling

**Write Side**:
- Scale by adding more command handler instances
- Load balanced via HTTP
- No coordination needed (events published to Kafka)

**Read Side**:
- Scale by adding more query instances
- Kafka consumer group distributes events across instances
- Each partition processed by one consumer

**Kafka**:
- Scale by adding more partitions
- More partitions = more parallelism

### 5. Separation of Concerns

**Write Side**: Optimized for correctness and validation
- Business logic
- Conflict detection
- Patch computation

**Read Side**: Optimized for performance
- Fast in-memory reads
- No validation (already validated on write side)
- Simple data access

### 6. Testability

**Unit Test Command Handler** (no Kafka needed):
```java
@Test
void handle_shouldCreateCommitCreatedEvent() {
  var handler = new PutGraphCommandHandler(mockDeps);
  var command = new PutGraphCommand(...);

  var event = handler.handle(command);

  assertThat(event).isInstanceOf(CommitCreatedEvent.class);
  // No database, no Kafka needed!
}
```

**Integration Test Projector** (with real Kafka):
```java
@Test
void handleCommitCreated_shouldUpdateRepositories() {
  eventPublisher.publish(new CommitCreatedEvent(...));

  await().until(() -> commitRepository.findById(id).isPresent());

  // Verify projection worked
}
```

---

## Trade-offs

### 1. Eventual Consistency

**Trade-off**: Reads may lag writes by milliseconds

**Example**:
```http
PUT /data?branch=main
  → 200 OK (returned immediately)

GET /data?branch=main  (1ms later)
  → May still see OLD graph! (projection not complete yet)
```

**Mitigation**: Use ETag from PUT response in subsequent GET:
```http
PUT /data?branch=main
  → 200 OK, ETag: "commit-123"

GET /data?branch=main&commit=commit-123
  → Always sees correct graph (query specific commit)
```

**Acceptable Because**:
- Consistency window is small (~2-60ms)
- Git has same behavior (commit → push takes time)
- Most clients don't need immediate consistency

### 2. Increased Complexity

**Trade-off**: More components = more complexity

**Traditional**:
- Controller → Service → Database (3 components)

**CQRS + Event Sourcing**:
- Controller → CommandHandler → EventPublisher → Kafka → Projector → Repository (6 components)

**Mitigation**:
- Clear documentation (this guide!)
- Consistent patterns across all handlers
- Comprehensive tests

### 3. Storage Overhead

**Trade-off**: Events stored forever = more storage

**Example**:
- 1 million commits
- Each commit ~5 KB (RDFPatch)
- Total: 5 GB of events

**Mitigation**:
- Kafka compression (GZIP): ~10x reduction → 500 MB
- Snapshots (future): Periodic full-state snapshots reduce replay time
- Acceptable: Storage is cheap, history is valuable

### 4. Debugging Async Issues

**Trade-off**: Harder to trace async flow

**Traditional** (synchronous):
```
PUT /data → Error → Stack trace points to exact line
```

**CQRS + Event Sourcing** (async):
```
PUT /data → 200 OK (command side succeeded)
... later ...
Projector → Error (read side failed)
  → Stack trace shows projector error, not original request!
```

**Mitigation**:
- Event IDs for correlation: Trace request → event → projection
- Structured logging: Log event ID at each step
- Monitoring: Track projection lag (Kafka consumer lag metrics)

### 5. Learning Curve

**Trade-off**: Developers must understand CQRS + Event Sourcing

**Challenges**:
- New pattern (unfamiliar to many)
- Async thinking (not sequential)
- Test isolation (projector on/off)

**Mitigation**:
- This documentation!
- Code examples and tests
- Clear naming conventions

---

## Testing Implications

### Test Isolation Challenge

**Problem**: All tests share same Kafka topics

**Scenario**:
```
Test 1: PUT /data?branch=main (creates event)
  → Kafka: Event published to "default-events"

Test 2: PUT /data?branch=feature (creates event)
  → Kafka: Event published to "default-events"

Projector (enabled globally):
  → Consumes Test 1 event → Updates repositories
  → Consumes Test 2 event → Updates repositories

Test 1 assertions:
  assertThat(branchRepository.findById("main")).isPresent();
  assertThat(branchRepository.findById("feature")).isPresent();  // ❌ LEAK!
```

**Solution**: Disable projector by default

**Configuration** (`application-it.yml`):
```yaml
projector:
  kafka-listener:
    enabled: false  # Disabled by default in tests
```

### Test Patterns

#### Pattern 1: API Layer Test (90% of tests)

**Goal**: Test HTTP contract (commands only)

**Configuration**: Projector **disabled** (default)

**Example**:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreControllerIT {

  @Test
  void putGraph_shouldReturn200() {
    // Arrange
    String rdf = "<s> <p> <o> .";

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
      "/data?branch=main", PUT, new HttpEntity<>(rdf), String.class);

    // Assert: Verify synchronous API response ONLY
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();

    // ❌ DO NOT query repositories - projector disabled!
    // ❌ DO NOT use await() - no async processing!

    // ✅ Add comment explaining:
    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
```

#### Pattern 2: Projector Test (10% of tests)

**Goal**: Test event projection (read side)

**Configuration**: Projector **enabled** explicitly

**Example**:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable!
class GraphEventProjectorIT {

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private CommitRepository commitRepository;

  @Test
  void handleCommitCreated_shouldUpdateRepositories() throws Exception {
    // Arrange
    CommitCreatedEvent event = new CommitCreatedEvent(
      "default",
      CommitId.generate(),
      null,  // no parent (initial commit)
      "Alice",
      "Test commit",
      Instant.now(),
      "A <s> <p> <o> ."
    );

    // Act: Publish event to Kafka
    eventPublisher.publish(event).get();  // Wait for Kafka ack

    // Assert: Wait for async projection, then verify repository
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        var commit = commitRepository.findById("default", event.commitId());
        assertThat(commit).isPresent();
        assertThat(commit.get().author()).isEqualTo("Alice");
      });
  }
}
```

**Key Differences**:
- API Layer: Tests commands (HTTP responses)
- Projector: Tests queries (repository updates)
- API Layer: Projector disabled (no await)
- Projector: Projector enabled (must await)

### Decision Table

| I want to test... | Test Type | Enable Projector? | Use await()? |
|-------------------|-----------|-------------------|--------------|
| HTTP status codes | API Layer | ❌ No | ❌ No |
| HTTP headers (ETag) | API Layer | ❌ No | ❌ No |
| Validation errors (400/404) | API Layer | ❌ No | ❌ No |
| Command handler logic | API Layer | ❌ No | ❌ No |
| Event projection | Projector | ✅ Yes | ✅ Yes |
| Repository updates | Projector | ✅ Yes | ✅ Yes |
| Full CQRS flow | Projector | ✅ Yes | ✅ Yes |

---

## Common Patterns

### Pattern 1: Command Handler

**Structure**:
```java
@Component
public class XxxCommandHandler implements CommandHandler<XxxCommand> {

  @Override
  public VersionControlEvent handle(XxxCommand command) {
    // 1. Query current state (from read model)
    var currentState = repository.findById(command.id());

    // 2. Validate command
    if (currentState.isEmpty()) {
      throw new NotFoundException("...");
    }

    // 3. Apply business logic
    var result = businessService.doSomething(currentState, command);

    // 4. Create event
    var event = new XxxEvent(result);

    // 5. Publish event (async)
    eventPublisher.publish(event);

    // 6. Return event (for HTTP response)
    return event;
  }
}
```

**Key Points**:
- Query read model (not event store)
- No side effects on repositories (projector does that)
- Return event for HTTP response

### Pattern 2: Event Handler (Projector)

**Structure**:
```java
@Component
public class ReadModelProjector {

  @KafkaListener(topics = "...", groupId = "...")
  public void handleXxxEvent(XxxEvent event) {
    // 1. Extract data from event
    var data = event.data();

    // 2. Update repository (side effect)
    repository.save(data);

    // 3. No return value (side effect only)
  }
}
```

**Key Points**:
- Idempotent (can replay safely)
- Side effects only (no return value)
- No business logic (already done in command handler)

### Pattern 3: Selector Resolution

**Purpose**: Resolve selector (branch, commit, asOf) to CommitId

**Usage**:
```java
// In controller or command handler
Selector selector = parseSelector(request);  // branch=main
CommitId commitId = selectorResolutionService.resolve(selector);

// Now use commitId to query read model
DatasetGraph graph = datasetGraphRepository.getGraph(commitId);
```

**Selector Types**:
- `Selector.Branch(name)` → Resolve to branch head commit
- `Selector.Commit(id)` → Use commit ID directly
- `Selector.AsOf(timestamp)` → Find latest commit ≤ timestamp

### Pattern 4: ETag-Based Optimistic Locking

**Purpose**: Prevent lost updates (concurrent writes)

**Flow**:
```http
GET /data?branch=main
  ← 200 OK, ETag: "commit-123"

PUT /data?branch=main
  If-Match: "commit-123"
  ← 200 OK (if branch head still commit-123)
  ← 412 Precondition Failed (if branch head changed)
```

**Implementation**:
```java
@PutMapping("/data")
public ResponseEntity<Void> put(
    @RequestHeader(name = "If-Match", required = false) String ifMatch,
    @RequestBody String rdf) {

  // 1. Resolve current head
  CommitId currentHead = branchRepository.getHead(branch);

  // 2. Check precondition
  if (ifMatch != null && !ifMatch.equals(currentHead.value())) {
    throw new PreconditionFailedException("Branch has been modified");
  }

  // 3. Proceed with PUT
  // ...
}
```

---

## Exception Handling

### Read Side (Projector): Fail-Fast Strategy

The `ReadModelProjector` uses a **fail-fast** approach to maintain read model consistency:

1. **Exceptions are rethrown** (not swallowed)
2. **Kafka offset is NOT committed** on failure
3. **No silent failures** (all errors visible)

#### Why This Matters

**Silent failures create permanent inconsistencies:**
```java
// ❌ DANGEROUS: Silent failure
@KafkaListener(...)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  try {
    processEvent(record.value());
  } catch (Exception ex) {
    logger.error("Failed", ex);
    // ❌ Offset commits → event lost forever!
  }
}
```

**Rethrowing enables recovery:**
```java
// ✅ CORRECT: Rethrow for retry
@KafkaListener(...)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  try {
    processEvent(record.value());
  } catch (Exception ex) {
    logger.error("Failed", ex);
    throw new ProjectionException("...", ex);
    // ✅ Offset NOT committed → Kafka retries
  }
}
```

#### Failure Scenarios

| Failure Type | Kafka Behavior | Read Model Impact |
|--------------|---------------|-------------------|
| Transient (network) | Automatic retry | Eventually consistent |
| Poison event (bug) | Dead Letter Queue | Operations alerted |
| Duplicate event | Deduplication skips | No impact (idempotent) |

#### Configuration

Retry/DLQ behavior configured in `application.yml`:

```yaml
spring:
  kafka:
    consumer:
      # CRITICAL: Disable auto-commit to prevent silent failures
      # Offset only commits when event processing succeeds
      enable-auto-commit: false

    listener:
      # CRITICAL: Per-record ACK for fail-fast behavior
      # If event processing throws exception:
      #   1. Exception logged
      #   2. Offset NOT committed
      #   3. Kafka retries event (or sends to DLQ)
      ack-mode: record

projector:
  deduplication:
    enabled: true
    cache-size: 10000  # LRU cache for retried events
```

#### Deduplication

Retried events are deduplicated by `eventId` to ensure idempotent projection:

```java
// Check if event already processed
if (processedEventIds.getIfPresent(event.eventId()) != null) {
  logger.warn("Skipping duplicate event: {}", event.eventId());
  return;  // ✅ Idempotent - safe to skip retry
}

// Process event
processEvent(event);

// Mark as processed
processedEventIds.put(event.eventId(), Boolean.TRUE);
```

---

### Command Side: Wait for Kafka Confirmation

**CRITICAL:** Command handlers MUST wait for Kafka confirmation before returning to avoid silent data loss.

#### The Problem (Fire-and-Forget)

```java
// ❌ DANGEROUS: Fire-and-forget pattern
public VersionControlEvent handle(PutGraphCommand command) {
  var event = createEvent(command);

  // ❌ BAD: Returns before Kafka confirms!
  eventPublisher.publish(event)
      .exceptionally(ex -> {
        logger.error("Failed", ex);
        return null;  // ❌ Swallows exception!
      });

  return event;  // ❌ Controller returns 200 OK even if Kafka is down!
}
```

**Impact:**
- Client receives HTTP 200 OK
- But event never reaches Kafka (network failure, Kafka down, etc.)
- Read model never updated
- **Silent data loss**

#### The Solution (Wait for Kafka)

```java
// ✅ CORRECT: Wait for Kafka confirmation
public VersionControlEvent handle(PutGraphCommand command) {
  var event = createEvent(command);

  try {
    // ✅ GOOD: Block until Kafka confirms
    eventPublisher.publish(event).get();
  } catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Failed to publish event", ex);
  } catch (ExecutionException ex) {
    throw new IllegalStateException("Failed to publish event", ex.getCause());
  }

  return event;  // ✅ Only returns if Kafka succeeded
}
```

**Benefits:**
- If Kafka fails → HTTP 500 (client knows to retry)
- If Kafka succeeds → HTTP 200 (client knows operation succeeded)
- No silent data loss

**Performance:**
- Adds ~10-50ms latency (Kafka round-trip time)
- Acceptable trade-off for data integrity
- Still achieves "eventual consistency" (read model updates async)

---

### Summary Table

| Component | Exception Strategy | Offset Behavior | Result |
|-----------|-------------------|-----------------|--------|
| **ReadModelProjector** | Rethrow (fail-fast) | NOT committed on error | Kafka retries or DLQ |
| **Command Handlers** | Wait for Kafka (`.get()`) | N/A (publisher side) | HTTP 500 on failure |
| **EventPublisher** | Return `CompletableFuture` | N/A | Caller handles future |

---

### Related Documentation

- [ADR-0003: Projector Fail-Fast Exception Handling](./decisions/0003-projector-fail-fast-exception-handling.md)
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) (implementation)
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)

---

## Anti-Patterns

### ❌ Anti-Pattern 1: Waiting for Projection in Command Handler

**Bad**:
```java
public CommitCreatedEvent handle(PutGraphCommand command) {
  var event = new CommitCreatedEvent(...);

  // ❌ BAD: Waiting for projection defeats async architecture!
  CompletableFuture<RecordMetadata> future = eventPublisher.publish(event);
  future.get();  // ❌ BLOCKING!

  // ❌ BAD: Querying repository immediately after publish
  var commit = commitRepository.findById(event.commitId());  // ❌ NOT YET THERE!

  return event;
}
```

**Why Bad**:
- Defeats async architecture (becomes synchronous)
- Slower (wait for Kafka + projection)
- HTTP response delayed

**Good**:
```java
public CommitCreatedEvent handle(PutGraphCommand command) {
  var event = new CommitCreatedEvent(...);

  // ✅ GOOD: Publish without waiting (fire-and-forget)
  eventPublisher.publish(event);  // Async!

  // ✅ GOOD: Return immediately (don't query repository)
  return event;
}
```

### ❌ Anti-Pattern 2: Querying Repository in API Test

**Bad**:
```java
@Test
void putGraph_shouldUpdateRepository() {
  // Act
  restTemplate.exchange("/data?branch=main", PUT, ...);

  // ❌ BAD: Projector disabled, repository not updated!
  var commit = commitRepository.findById(commitId);
  assertThat(commit).isPresent();  // ❌ FAILS!
}
```

**Why Bad**:
- Projector disabled by default in tests
- Repository not updated immediately
- Test will fail or be flaky

**Good** (API Layer Test):
```java
@Test
void putGraph_shouldReturn200() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange("/data?branch=main", PUT, ...);

  // ✅ GOOD: Test API contract only
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().getETag()).isNotNull();

  // Note: Repository updates handled by ReadModelProjector (disabled in this test)
}
```

**Good** (Projector Test):
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class ProjectorTest {
  @Test
  void handleCommitCreated_shouldUpdateRepository() {
    // Act
    eventPublisher.publish(new CommitCreatedEvent(...)).get();

    // ✅ GOOD: Wait for async projection
    await().until(() -> commitRepository.findById(commitId).isPresent());

    // ✅ GOOD: Verify repository after projection
    assertThat(commitRepository.findById(commitId)).isPresent();
  }
}
```

### ❌ Anti-Pattern 3: Business Logic in Projector

**Bad**:
```java
@KafkaListener(topics = "...")
public void handleCommitCreated(CommitCreatedEvent event) {
  // ❌ BAD: Validation in projector!
  if (event.author() == null) {
    throw new IllegalArgumentException("Author required");
  }

  // ❌ BAD: Business logic in projector!
  if (event.patch().contains("sensitive-data")) {
    event = redactEvent(event);
  }

  commitRepository.save(event.dataset(), commit);
}
```

**Why Bad**:
- Events already validated in command handler
- Business logic should be in command handler (write side)
- Projector should be "dumb" (just update repositories)
- Validation in projector prevents event replay

**Good**:
```java
// Command handler (write side): Validate and apply business logic
public CommitCreatedEvent handle(PutGraphCommand command) {
  // ✅ GOOD: Validate in command handler
  if (command.author() == null) {
    throw new IllegalArgumentException("Author required");
  }

  // ✅ GOOD: Business logic in command handler
  if (command.patch().contains("sensitive-data")) {
    patch = redactPatch(command.patch());
  }

  var event = new CommitCreatedEvent(..., patch);
  eventPublisher.publish(event);
  return event;
}

// Projector (read side): Just update repositories
@KafkaListener(topics = "...")
public void handleCommitCreated(CommitCreatedEvent event) {
  // ✅ GOOD: No validation, no business logic (just update)
  commitRepository.save(event.dataset(), commit);
}
```

### ❌ Anti-Pattern 4: Modifying Events

**Bad**:
```java
@KafkaListener(topics = "...")
public void handleCommitCreated(CommitCreatedEvent event) {
  // ❌ BAD: Modifying event!
  event.setProcessed(true);  // ❌ Events are immutable!
  event.patch = sanitize(event.patch);  // ❌ Don't change event data!

  commitRepository.save(event.dataset(), commit);
}
```

**Why Bad**:
- Events are immutable facts (cannot change history)
- Event replay would produce different results
- Breaks event sourcing model

**Good**:
```java
// ✅ GOOD: Events are immutable (Java records)
public record CommitCreatedEvent(
  String dataset,
  CommitId commitId,
  String author,
  String patch
  // No setters - immutable!
) implements VersionControlEvent { }

@KafkaListener(topics = "...")
public void handleCommitCreated(CommitCreatedEvent event) {
  // ✅ GOOD: Use event as-is (don't modify)
  commitRepository.save(event.dataset(), commit);
}
```

---

## Troubleshooting

### Problem: "Repository not updated after write"

**Symptoms**:
```java
restTemplate.exchange("/data?branch=main", PUT, ...);
var graph = datasetGraphRepository.getGraph("main");
// graph is still old!
```

**Diagnosis**:
1. Check if projector is enabled: `projector.kafka-listener.enabled`
2. Check Kafka consumer lag: Is projector consuming events?
3. Check for projector exceptions in logs

**Solution**:
- In tests: Enable projector + use `await()`
- In production: Check Kafka connectivity

### Problem: "Projector test timing out"

**Symptoms**:
```java
@Test
void handleCommitCreated_shouldUpdateRepository() {
  eventPublisher.publish(event).get();
  await().until(...);  // ❌ Timeout!
}
```

**Diagnosis**:
1. Check if projector is enabled: `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
2. Check if Kafka topic exists: Topic auto-creation may fail
3. Check for exceptions in projector handler
4. Check Kafka logs for delivery issues

**Solution**:
```java
// Ensure topic exists before publishing
@BeforeEach
void setup() {
  kafkaAdmin.createTopics(new NewTopic("default-events", 1, (short) 1));
}

// Increase await timeout
await().atMost(Duration.ofSeconds(30))  // Longer timeout for debugging
  .pollInterval(Duration.ofMillis(100))  // Check more frequently
  .untilAsserted(() -> {
    var commit = commitRepository.findById(id);
    assertThat(commit).describedAs("Commit not found - projector not running?")
      .isPresent();
  });
```

### Problem: "Cross-test contamination"

**Symptoms**:
```java
@Test
void test1() {
  // Creates branch "main"
}

@Test
void test2() {
  // Expects no branches, but "main" exists!
  assertThat(branchRepository.findAll()).isEmpty();  // ❌ Fails!
}
```

**Diagnosis**:
- Projector enabled globally (all tests share same repositories)
- Events from test1 consumed by projector in test2

**Solution**:
- Disable projector by default in tests
- Enable projector only in dedicated projector tests
- Use `@DirtiesContext` (last resort - slow!)

### Problem: "Events published but not consumed"

**Symptoms**:
- HTTP writes succeed (200 OK)
- Reads return stale data
- Kafka consumer lag increasing

**Diagnosis**:
1. Check projector is running: `projector.kafka-listener.enabled=true`
2. Check Kafka connectivity: Can consumer connect to Kafka?
3. Check consumer group: Is group active?
4. Check for exceptions in projector

**Solution**:
```bash
# Check consumer group status
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group chucc-server-projector --describe

# Expected output:
# GROUP                    TOPIC           PARTITION  LAG
# chucc-server-projector   default-events  0          0  ← Lag should be 0 or small
```

---

## References

### External Resources

- [Martin Fowler - CQRS](https://martinfowler.com/bliki/CQRS.html)
- [Martin Fowler - Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [Microsoft - CQRS Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/cqrs)
- [Event Sourcing - Greg Young](https://www.eventstore.com/blog/what-is-event-sourcing)

### Internal Documentation

- [Architecture Overview](README.md)
- [C4 Level 1: Context](c4-level1-context.md) - System boundary and external dependencies
- [C4 Level 2: Container](c4-level2-container.md) - Technology choices (Spring Boot, Kafka)
- [C4 Level 3: Component](c4-level3-component.md) - Internal component structure
- [Testing Strategy](../../.claude/CLAUDE.md) - Test patterns and isolation

### Code Examples

- `PutGraphCommandHandler` - Example command handler
- `ReadModelProjector` - Example event projector
- `GraphEventProjectorIT` - Example projector test
- `GraphStoreControllerIT` - Example API layer test

---

## Summary

**CQRS + Event Sourcing** in CHUCC Server provides:

✅ **Benefits**:
- Complete audit trail (who, what, when, why)
- Time-travel queries (asOf selector)
- Event replay (system recovery)
- Independent scaling (write vs. read)
- Separation of concerns (commands vs. queries)

⚠️ **Trade-offs**:
- Eventual consistency (~2-60ms lag)
- Increased complexity (more components)
- Storage overhead (events stored forever)
- Async debugging challenges
- Learning curve

🔑 **Key Concepts**:
- **Commands** create **events** (write side)
- **Events** stored in **Kafka** (source of truth)
- **Projectors** consume events, update **repositories** (read side)
- **HTTP responses** return **immediately** (before projection)
- **Repositories** are **eventually consistent** (async updates)

🧪 **Testing**:
- API Layer: Projector **disabled** (test commands)
- Projector: Projector **enabled** + `await()` (test queries)
- Separation prevents cross-test contamination

📚 **Next Steps**:
- Read [C4 Level 3: Component](c4-level3-component.md) for detailed component structure
- Study code examples: `PutGraphCommandHandler`, `ReadModelProjector`
- Run projector tests: `GraphEventProjectorIT`, `VersionControlProjectorIT`
