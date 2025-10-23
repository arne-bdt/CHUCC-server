# CHUCC Server - Task Roadmap

This directory contains task breakdowns for remaining features and enhancements planned for CHUCC Server. Each task is designed to be completable in one development session (3-6 hours).

---

## Overview

This roadmap tracks the **remaining tasks** for CHUCC Server. Completed tasks have been removed from this directory.

**Remaining task areas:**
1. **Java APIs** - Create plain Java APIs matching SPARQL and Graph Store protocols
2. **Kafka Best Practices** - 🔴 **CRITICAL** - Implement Kafka CQRS/Event Sourcing best practices

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

For details on completed work, see git history:
```bash
git log --oneline --grep="cache\|snapshot\|deletion\|named graph\|CQRS\|event flow\|time-travel\|migrate.*Graph\|dataset parameter" --since="2025-10-01"
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

### 2. Kafka Best Practices

**Goal:** Implement Kafka CQRS/Event Sourcing best practices based on industry standards.

**Current State:** CHUCC Server uses Kafka for event sourcing with improved best practices:
- ✅ Partition key uses aggregate-ID pattern (ensures ordering guarantees)
- ✅ Event deduplication implemented (UUIDv7-based exactly-once processing)
- ✅ Correlation ID for distributed tracing (timestamp header included)
- ⚠️ JSON serialization with manual type mappings (no comprehensive tests)
- ⚠️ Snapshots not in separate compacted topic
- ⚠️ Consumer isolation level not set to read_committed

**Target State:** Production-ready Kafka CQRS/ES implementation with practical safeguards.

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 01. Fix Partition Key Strategy | `kafka-best-practices/01-fix-partition-key-strategy.md` | 🔴 **CRITICAL** | 3-4 hours | ✅ Completed |
| 02. Implement Event Deduplication | `kafka-best-practices/02-implement-event-deduplication.md` | 🔴 **CRITICAL** | 3-4 hours | ✅ Completed |
| 03. Add Correlation ID for Distributed Tracing | `kafka-best-practices/03-add-event-metadata-headers.md` | 🟡 Medium | 1-1.5 hours | ✅ Completed |
| 04. Add Event Serialization Tests | `kafka-best-practices/04-add-event-serialization-tests.md` | 🟡 Medium | 30-45 min | ✅ Completed |
| 05. Snapshot Compaction Strategy | `kafka-best-practices/05-snapshot-compaction-strategy.md` | 🟡 Low-Medium | 2-3 hours | 📋 Not Started |
| 06. Transaction Support for Consumers | `kafka-best-practices/06-add-transaction-support-for-consumers.md` | 🟡 Medium | 2 hours | 📋 Not Started |

**Dependencies:**
- ✅ Task 02 completed (Task 01 prerequisite)
- ✅ Task 03 completed (Task 02 prerequisite)
- None for remaining tasks (can be done in any order)

**Impact:**

**Completed:**
1. ✅ **Partition Key Strategy** - Aggregate-ID based partitioning ensures ordering
2. ✅ **Event Deduplication** - Exactly-once processing with UUIDv7 cache
3. ✅ **Correlation ID** - Full distributed tracing across HTTP → Kafka → Projector
4. ✅ **Serialization Tests** - Comprehensive JSON round-trip validation for all 12 event types

**Medium Priority (Production-Ready):**
5. **Transaction Support** - Data integrity for consumers (2 hours)
6. **Snapshot Compaction** - Storage efficiency (2-3 hours)

**Estimated Remaining Time:** 4-5 hours (spread across 2 tasks)

**Recommended Order:**
1. 🟡 Transaction Support (Task 06) - 2 hours ← **Start here**
2. 🟡 Snapshot Compaction (Task 05) - 2-3 hours

**Deferred:**
- **Schema Registry** - Over-engineered for current single-app architecture. Revisit when external consumers emerge (polyglot microservices, data analytics, third-party integrations)

---

## Recommended Implementation Order

### Phase 1: Java APIs (Medium Priority)

**Goal:** Provide programmatic access for embedded use cases

2. 📋 `java-api/01-create-java-sparql-api.md` (3-4 hours)
3. 📋 `java-api/02-create-java-graph-store-api.md` (3-4 hours)

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
- ⏸️ **Deferred** - Task is lower priority
- ❌ **Cancelled** - Task no longer needed

**Current overall status:** 1 task area remaining

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
- ✅ Kafka best practices: Partition key strategy (1 task)
- ✅ Kafka best practices: Event deduplication (1 task)
- ✅ Kafka best practices: Correlation ID for distributed tracing (1 task)
- ✅ Kafka best practices: Event serialization tests (1 task)
- **Total completed:** 17 tasks

**Remaining:**
- 📋 Java APIs (2 tasks)
- 🟡 Kafka Best Practices (2 tasks remaining)
- **Total remaining:** 4 tasks

**Progress:** ~81% complete (17 of 21 tasks)

**Next Steps (Priority Order):**
1. 🟡 Medium: Transaction support for consumers (kafka-best-practices/06) - 2 hours ← **START HERE**
2. 🟡 Medium: Snapshot compaction strategy (kafka-best-practices/05) - 2-3 hours
3. 🟢 Low: Create Java APIs (java-api/01-02) - 6-8 hours
