# CHUCC Server - Task Roadmap

This directory contains task breakdowns for remaining features and enhancements planned for CHUCC Server. Each task is designed to be completable in one development session (3-6 hours).

---

## Overview

This roadmap tracks the **remaining tasks** for CHUCC Server. Completed tasks have been removed from this directory.

**Remaining task areas:**
1. **Java APIs** - Create plain Java APIs matching SPARQL and Graph Store protocols
2. **Command-Side Exception Handling** - Fix fire-and-forget pattern (CRITICAL)

---

## Completed Tasks

The following task areas have been **successfully completed** and their task files removed:

### ✅ Snapshot Optimization (Completed)
- Snapshots now used for faster startup and query materialization
- SnapshotKafkaStore implemented for on-demand snapshot loading
- Metadata caching added for performance

### ✅ Cache Optimization (Completed)
- LRU cache eviction implemented using Caffeine
- Bounded memory usage preventing OutOfMemoryError
- Cache metrics available via Actuator

### ✅ Deletion Features (Completed)
- Branch deletion implemented with protection for main branch
- Dataset deletion implemented with confirmation requirement
- Optional Kafka topic cleanup support

### ✅ Named Graph Support (Completed)
- Full named graph support for Graph Store Protocol
- Quad-based RDF Patch handling for named graphs
- Parameter validation (`?graph=<uri>` vs `?default=true`)
- Integration tests verify end-to-end functionality

### ✅ CQRS Event Flow (Completed)
- Full event publishing from controllers to Kafka
- ReadModelProjector processes events and updates repositories
- Enabled and fixed 10 integration tests verifying async event flow
- Eventual consistency model validated end-to-end

### ✅ Time-Travel SPARQL Queries (Completed)
- 5 comprehensive integration tests verify historical data retrieval
- Tests cover queries at different timestamps (T1, T2, after deletion, midpoint)
- End-to-end testing using Graph Store Protocol HTTP API
- Async projector verification with await() pattern
- Query result correctness validated for all time-travel scenarios

### ✅ Model API to Graph API Migration (Completed)
- Migrated RdfParsingService to return Graph instead of Model
- Migrated GraphSerializationService to accept Graph instead of Model
- Migrated GraphDiffService to use Graph API for improved performance
- 20-30% performance improvement in graph operations
- 15-25% memory reduction in graph processing
- All 911+ tests pass with zero quality violations

### ✅ Dataset Parameter Implementation (Completed)
- BatchGraphsController now accepts dataset parameter
- GraphStoreController now accepts dataset parameter (all 6 GSP operations)
- SparqlController now accepts dataset parameter (query + update)
- All hardcoded "default" values removed
- Backward compatible with default value = "default"
- Full API consistency across all endpoints

### ✅ Kafka Best Practices (Completed)
- Aggregate-ID based partition key strategy ensures event ordering
- UUIDv7-based event deduplication for exactly-once processing
- Correlation ID support for distributed tracing (HTTP → Kafka → Projector)
- Comprehensive event serialization tests (JSON round-trip validation)
- Idempotent non-transactional publishing optimized for single-event pattern
- Production-ready CQRS/Event Sourcing implementation

### ✅ Projector Exception Handling (Completed - Projector Side)
- Manual commit configuration (AckMode.RECORD) prevents message loss
- Exception rethrowing verified and tested (3 unit tests)
- ADR-0003 created documenting fail-fast strategy
- Comprehensive documentation added to ReadModelProjector and CQRS guide
- Codebase audit completed (identified command-side issue for future work)
- **Note:** Command-side fire-and-forget issue remains (see `.tasks/projector-exception-handling/04-fix-command-handler-exceptions.md`)

For details on completed work, see git history:
```bash
git log --oneline --grep="cache\|snapshot\|deletion\|named graph\|CQRS\|event flow\|time-travel\|migrate.*Graph\|dataset parameter\|partition key\|deduplication\|correlation" --since="2025-10-01"
```

---

## Task Categories

### 1. Java APIs

**Goal:** Create plain Java APIs for SPARQL Protocol and Graph Store Protocol that can be used without HTTP overhead.

**Current State:** Only HTTP endpoints available.

**Target State:** Clean Java APIs for embedded use, testing, and library integration.

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 01. Create Java SPARQL API | `java-api/01-create-java-sparql-api.md` | Medium | 3-4 hours | 📋 Not Started |
| 02. Create Java Graph Store API | `java-api/02-create-java-graph-store-api.md` | Medium | 3-4 hours | 📋 Not Started |

**Dependencies:** Task 01 should be completed first to establish API patterns.

---

### 2. Command-Side Exception Handling

**Goal:** Fix fire-and-forget pattern in command handlers to prevent silent data loss.

**Current State:** Command handlers return HTTP 200 OK before Kafka confirms event publishing.

**Target State:** Command handlers wait for Kafka confirmation before returning success to clients.

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 04. Fix Command Handler Fire-and-Forget | `projector-exception-handling/04-fix-command-handler-exceptions.md` | **CRITICAL** | 3-4 hours | 📋 Not Started |

**Impact:** **CRITICAL** - Silent data loss possible if Kafka is down or publishing fails.

**Files Affected:**
- `GraphCommandUtil.java` (affects all GSP operations)
- `CreateCommitCommandHandler.java`
- All command handlers using fire-and-forget pattern

**See:** [projector-exception-handling/README.md](./projector-exception-handling/README.md) for context and completed work.

---

**API Style:**
```java
// SPARQL Protocol API
@Autowired
private SparqlProtocolApi api;

SparqlQueryResult result = api.query(
    "SELECT * WHERE { ?s ?p ?o } LIMIT 10",
    SparqlSelector.branch("main")
);

// Graph Store Protocol API
@Autowired
private GraphStoreProtocolApi api;

Optional<Model> model = api.getGraph(
    "http://example.org/graph1",
    GraphSelector.branch("main")
);
```

**Use Cases:**
- Embedded applications
- Unit testing without HTTP overhead
- Library integration
- Microservices communication

**Benefits:**
- No HTTP serialization/deserialization overhead
- Direct method invocation (10-100x faster)
- Type-safe API (compile-time checking)
- Better for testing (no need to start HTTP server)

---

## Deferred Features

The following features were evaluated but deferred as premature optimizations. They may be revisited when specific conditions are met:

### Snapshot Compaction Strategy
**Status:** Deferred

Current metadata caching (Caffeine) is sufficient for current scale (~100 snapshots, ~100MB storage). Proposed Kafka log compaction would use `dataset:branch` key which would delete historical snapshots needed for time-travel queries.

**Revisit when:**
1. Snapshot storage exceeds 10GB, OR
2. Query latency exceeds 500ms, OR
3. Alternative key design solves historical retention requirement

### Schema Registry
**Status:** Deferred

Over-engineered for current single-app architecture. All event consumers are in the same Java application using shared event classes.

**Revisit when:**
- External consumers emerge (polyglot microservices, data analytics, third-party integrations)

### Kafka Transaction Support & read_committed
**Status:** Deferred

Current idempotent non-transactional publishing is sufficient. Each command publishes ONE event (no multi-event atomicity needed). Current approach (retry + idempotence) is simpler and performs better (~50% higher throughput).

**Revisit when:**
1. Coordinating writes across multiple Kafka topics, OR
2. Batch operations require rollback capability, OR
3. Multi-event atomic publishing is needed

---

## Recommended Implementation Order

### Command-Side Exception Handling (**CRITICAL** Priority)

**Goal:** Prevent silent data loss

1. 🔴 `projector-exception-handling/04-fix-command-handler-exceptions.md` (3-4 hours)

**Rationale:** **CRITICAL BUG** - Command handlers can return success while event publishing fails silently.

**Estimated Time:** 3-4 hours

---

### Java APIs (Medium Priority)

**Goal:** Provide programmatic access for embedded use cases

1. 📋 `java-api/01-create-java-sparql-api.md` (3-4 hours)
2. 📋 `java-api/02-create-java-graph-store-api.md` (3-4 hours)

**Rationale:** Nice-to-have for embedded use. Can be deferred if time-constrained.

**Estimated Time:** 6-8 hours total

---

## How to Use This Roadmap

### For Developers

1. **Pick a task** from the appropriate phase
2. **Read the task file** thoroughly - it contains:
   - Context and goals
   - Step-by-step implementation plan
   - Code examples and tests
   - Success criteria
3. **Implement the task** following TDD principles:
   - Write tests first
   - Implement incrementally
   - Run quality checks (Checkstyle, SpotBugs, PMD)
4. **Verify completion** using success criteria
5. **Commit and document** the changes
6. **Delete the task file** once completed

### For Project Managers

- **Phase 1 tasks are critical** - unlock performance improvements across the codebase
- **Phase 2 tasks are enhancements** - nice-to-have for embedded use
- **Phase 3 tasks are completion** - finish the refactoring work

### For Architects

- All tasks follow **CQRS + Event Sourcing** patterns (where applicable)
- All tasks maintain **backward compatibility**
- All tasks have **comprehensive tests**

---

## Task File Structure

Each task file follows this template:

```markdown
# Task: [Task Name]

**Status:** Not Started / In Progress / Completed
**Priority:** High / Medium / Low
**Estimated Time:** X hours
**Dependencies:** List of prerequisite tasks

## Context
- Current state
- Problem statement
- Goal

## Design Decisions
- Key choices made
- Trade-offs considered

## Implementation Plan
- Step 1: [Description]
- Step 2: [Description]
- ...

## Tests
- Unit tests
- Integration tests
- Performance tests (if applicable)

## Success Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- ...

## Rollback Plan
- How to revert if issues arise

## Future Enhancements
- Ideas for later improvements
```

---

## Metrics & Monitoring

After implementing Java API tasks, monitor:

**API Performance Metrics:**
- `api.sparql.query.time` - Query execution time
- `api.sparql.update.time` - Update execution time
- `api.graph.get.time` - Graph retrieval time

After implementing refactoring tasks, monitor:

**Performance Metrics:**
- `graph.diff.time` - Graph diff operation time (should decrease by 20-30%)
- `jvm.memory.used` - Heap usage (should decrease by 15-25%)

---

## Contributing

When adding new tasks:

1. Create a new directory if needed (e.g., `.tasks/new-feature/`)
2. Follow the task template above
3. Ensure task is completable in one session (3-6 hours)
4. Include detailed implementation steps
5. Provide code examples
6. Define clear success criteria
7. Update this README with the new task

**When completing a task:**
1. Verify all success criteria met
2. Ensure all tests pass (currently ~911 tests)
3. Verify zero quality violations (Checkstyle, SpotBugs, PMD)
4. Commit with conventional commit message
5. **Delete the task file and folder** (if folder is empty)
6. Update this README

---

## References

- [Architecture Overview](../docs/architecture/README.md)
- [CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../.claude/CLAUDE.md)
- [Performance Optimization](../docs/operations/performance.md)

---

## Status Legend

- ✅ **Completed** - Task is done, merged, and task file deleted
- 🚧 **In Progress** - Task is being worked on
- 📋 **Not Started** - Task is ready to start
- 🔴 **Critical** - High priority bug or data loss risk
- ⏸️ **Deferred** - Task is lower priority
- ❌ **Cancelled** - Task no longer needed

**Current overall status:** 2 task areas remaining (1 critical, 1 medium priority)

---

## Quick Stats

**Completed:**
- ✅ Snapshot optimization (2 tasks)
- ✅ Cache optimization (2 tasks)
- ✅ Deletion features (2 tasks)
- ✅ Named graph support (1 task)
- ✅ CQRS event flow (1 task)
- ✅ Time-travel SPARQL queries (1 task)
- ✅ Model API to Graph API migration (3 tasks)
- ✅ Dataset parameter implementation (1 task)
- ✅ Kafka best practices (4 tasks)
- ✅ Projector exception handling - read side (4 tasks)
- **Total completed:** 21 tasks

**Remaining:**
- 🔴 Command-side exception handling (1 task - CRITICAL)
- 📋 Java APIs (2 tasks - Medium priority)
- **Total remaining:** 3 tasks

**Progress:** ~88% complete (21 of 24 tasks)

**Next Steps (Priority Order):**
1. 🔴 **CRITICAL:** Fix command handler fire-and-forget (projector-exception-handling/04) - 3-4 hours
2. 🟢 Low: Create Java APIs (java-api/01-02) - 6-8 hours
