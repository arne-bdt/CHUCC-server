# CHUCC Server - Task Roadmap

This directory contains task breakdowns for remaining features and enhancements planned for CHUCC Server. Each task is designed to be completable in one development session (3-6 hours).

---

## Overview

This roadmap tracks the **remaining tasks** for CHUCC Server. Completed tasks have been removed from this directory.

**Remaining task areas:**
1. **SPARQL Features** - Add time-travel query tests
2. **Infrastructure** - Request context for dataset name
3. **Java APIs** - Create plain Java APIs matching SPARQL and Graph Store protocols
4. **Refactoring** - Migrate from Model API to Graph API for performance improvements

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

For details on completed work, see git history:
```bash
git log --oneline --grep="cache\|snapshot\|deletion\|named graph\|CQRS\|event flow" --since="2025-10-01"
```

---

## Task Categories

### 1. SPARQL Features

**Goal:** Add comprehensive tests for time-travel SPARQL queries.

**Current State:** Time-travel parameter validation exists, but query correctness not verified.

**Target State:** Integration tests verify historical data retrieval works correctly.

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 01. Implement Time-Travel Query Tests | `sparql-features/01-implement-time-travel-sparql-queries.md` | Medium | 3-4 hours | 📋 Not Started |

**Dependencies:** Requires event publishing to be implemented (testing/01).

**Test Scenarios:**
1. Query at T1 returns initial data
2. Query at T2 returns updated data
3. Query after deletion returns empty results
4. Query between T1 and T2 returns T1 state
5. Query without asOf returns current state

---

### 2. Infrastructure

**Goal:** Remove hardcoded dataset names and support multi-dataset operations.

**Current State:** Dataset name hardcoded as "default" in multiple controllers.

**Target State:** Dataset name from request parameter or context.

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 01. Implement Request Context for Dataset | `infrastructure/01-implement-request-context-for-dataset.md` | Low | 2-3 hours | 📋 Not Started |

**Affected Controllers:**
- GraphStoreController
- BatchGraphsController
- SparqlController
- BranchController

**Benefits:**
- Removes hardcoded values
- Enables future multi-dataset support
- Foundation for multi-tenancy
- API consistency improvement

---

### 3. Java APIs

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

### 4. Refactoring - Model API to Graph API Migration

**Goal:** Improve performance and efficiency by migrating from Apache Jena's Model API to the lower-level Graph API.

**Current State:** Code uses Model API extensively.

**Target State:** Internal code uses Graph API; Model API only at boundaries.

See detailed breakdown: [`refactoring/README.md`](./refactoring/README.md)

| Task | File | Priority | Est. Time | Status |
|------|------|----------|-----------|--------|
| 01. Migrate RdfParsingService | `refactoring/01-migrate-rdf-parsing-service.md` | High | 1-2 hours | 📋 Not Started |
| 02. Migrate GraphSerializationService | `refactoring/02-migrate-graph-serialization-service.md` | High | 1-2 hours | 📋 Not Started |
| 03. Migrate GraphDiffService | `refactoring/03-migrate-graph-diff-service.md` | High | 2-3 hours | 📋 Not Started |

**Note:** Additional tasks (04-10) will be created after completing the foundational tasks 01-03.

**Expected Benefits:**
- **Performance:** 20-30% faster graph operations
- **Memory:** 15-25% reduction
- **Efficiency:** More efficient triple iteration
- **Direct access:** No wrapper object overhead

**Dependencies:** Tasks must be completed sequentially (01 → 02 → 03).

---

## Recommended Implementation Order

### Phase 1: Infrastructure (High Priority)

**Goal:** Improve infrastructure and remove hardcoded values

1. 📋 `infrastructure/01-implement-request-context-for-dataset.md` (2-3 hours)

**Rationale:**
- Request context removes hardcoded values and enables multi-dataset support
- Foundation for multi-tenancy
- Relatively independent task

**Estimated Time:** 2-3 hours total

---

### Phase 2: Testing & Validation (Medium Priority)

**Goal:** Verify time-travel queries work correctly

2. 📋 `sparql-features/01-implement-time-travel-sparql-queries.md` (3-4 hours)

**Rationale:**
- Validates time-travel query correctness
- Ensures version control features work as designed
- Provides living documentation via tests

**Estimated Time:** 3-4 hours

---

### Phase 3: Refactoring Foundation (Medium Priority)

**Goal:** Establish Graph API usage in foundational services

3. 📋 `refactoring/01-migrate-rdf-parsing-service.md` (1-2 hours)
4. 📋 `refactoring/02-migrate-graph-serialization-service.md` (1-2 hours)
5. 📋 `refactoring/03-migrate-graph-diff-service.md` (2-3 hours)

**Rationale:** These changes unlock performance improvements across the entire codebase.

**Estimated Time:** 5-7 hours total

---

### Phase 4: Java APIs (Lower Priority)

**Goal:** Provide programmatic access for embedded use cases

6. 📋 `java-api/01-create-java-sparql-api.md` (3-4 hours)
7. 📋 `java-api/02-create-java-graph-store-api.md` (3-4 hours)

**Rationale:** Nice-to-have for embedded use. Can be deferred if time-constrained.

**Estimated Time:** 6-8 hours total

---

### Phase 5: Refactoring Completion (Lower Priority)

**Goal:** Complete Model-to-Graph migration

8. Tasks 04-10 (to be created after Phase 3)

**Rationale:** Finish what was started in Phase 3.

**Estimated Time:** 8-10 hours total

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

- **Phase 1 tasks are critical** - unlock performance improvements
- **Phase 2 tasks are features** - nice-to-have
- **Phase 3 tasks are cleanup** - complete the refactoring

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

**Current overall status:** 4 task areas remaining

---

## Quick Stats

**Completed:**
- ✅ Snapshot optimization (2 tasks)
- ✅ Cache optimization (2 tasks)
- ✅ Deletion features (2 tasks)
- ✅ Named graph support (1 task)
- ✅ CQRS event flow (1 task)
- **Total completed:** 8 tasks

**Remaining:**
- 📋 SPARQL Features (1 task)
- 📋 Infrastructure (1 task)
- 📋 Java APIs (2 tasks)
- 📋 Refactoring (3+ tasks)
- **Total remaining:** 7+ tasks

**Progress:** ~53% complete (8 of 15+ tasks)

**Next Steps:**
1. Add request context for dataset (infrastructure/01)
2. Add time-travel query tests (sparql-features/01)
3. Start refactoring foundation (refactoring/01-03)
