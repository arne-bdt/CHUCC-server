# CHUCC Server - Task Roadmap

This directory contains task breakdowns for major features and enhancements planned for CHUCC Server. Each task is designed to be completable in one development session (3-6 hours).

---

## Overview

The tasks are organized into four main areas:

1. **Snapshot Optimization** - Use snapshots to speed up operations
2. **Deletion Features** - Implement branch and dataset deletion
3. **Cache Optimization** - Implement LRU cache eviction strategy
4. **Java APIs** - Create plain Java APIs matching SPARQL and Graph Store protocols

---

## Task Categories

### 1. Snapshot Optimization

**Goal:** Use snapshots to dramatically improve performance for startup, query materialization, and recovery.

**Current State:** Snapshots are created every N commits but **not used** for recovery or queries.

**Target State:** Snapshots used everywhere to reduce event replay time.

| Task | File | Priority | Est. Time |
|------|------|----------|-----------|
| 01. Snapshot Recovery on Startup | `snapshots/01-snapshot-recovery-on-startup.md` | High | 3-4 hours |
| 02. Snapshot-Based Materialization | `snapshots/02-snapshot-based-materialization.md` | High | 3-4 hours |

**Dependencies:** Task 01 must be completed before Task 02.

**Performance Impact:**
- **Startup:** 10k events: 30s → 3s (with snapshots every 1000 commits)
- **Queries:** Deep history (5000 commits): 2.5s → 50ms (50x faster)

---

### 2. Deletion Features

**Goal:** Implement missing deletion features (branch deletion, dataset deletion) with proper CQRS + Event Sourcing patterns.

**Current State:** Both features return `501 Not Implemented`.

**Target State:** Full deletion support with optional Kafka topic cleanup.

| Task | File | Priority | Est. Time |
|------|------|----------|-----------|
| 01. Implement Branch Deletion | `deletion/01-implement-branch-deletion.md` | Medium | 2-3 hours |
| 02. Implement Dataset Deletion | `deletion/02-implement-dataset-deletion.md` | Medium | 4-6 hours |

**Key Features:**
- Branch deletion: Deletes branch pointer, preserves commits
- Dataset deletion: Optionally deletes Kafka topic (irreversible)
- Protection: Cannot delete `main` branch or `default` dataset
- Confirmation required for dataset deletion

**Design Decisions:**
- Commits never deleted (audit trail)
- Kafka topic deletion optional (configurable)
- Events always published (even for deletion)

---

### 3. Cache Optimization

**Goal:** Replace unbounded caching with LRU (Least Recently Used) cache to prevent OutOfMemoryError.

**Current State:** Unbounded `ConcurrentHashMap` - memory grows indefinitely.

**Target State:** Caffeine-based LRU cache with configurable size limits.

| Task | File | Priority | Est. Time |
|------|------|----------|-----------|
| 01. Implement LRU Cache Eviction | `cache-optimization/01-implement-lru-cache-eviction.md` | High | 3-4 hours |

**Dependencies:** Snapshot tasks (01, 02) should be completed first for efficient graph rebuilding.

**Key Features:**
- LRU eviction with configurable max size
- Always keep latest commit per branch (never evict hot data)
- Optional TTL (time-to-live)
- Metrics: cache size, hit rate, eviction count

**Configuration:**
```yaml
vc:
  cache:
    max-size: 100                    # Max cached graphs
    keep-latest-per-branch: true     # Pin latest commits
    ttl-minutes: 0                   # Optional TTL
```

**Memory Impact:**
- **Before:** Unbounded (risk of OOM)
- **After:** Bounded to ~100 graphs × 5 MB = 500 MB

---

### 4. Java APIs

**Goal:** Create plain Java APIs for SPARQL Protocol and Graph Store Protocol that can be used without HTTP overhead.

**Current State:** Only HTTP endpoints available.

**Target State:** Clean Java APIs for embedded use, testing, and library integration.

| Task | File | Priority | Est. Time |
|------|------|----------|-----------|
| 01. Create Java SPARQL API | `java-api/01-create-java-sparql-api.md` | Medium | 3-4 hours |
| 02. Create Java Graph Store API | `java-api/02-create-java-graph-store-api.md` | Medium | 3-4 hours |

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
- Unit testing without HTTP
- Library integration
- Microservices communication

---

## Recommended Implementation Order

### Phase 1: Performance & Memory (Highest Priority)

**Goal:** Prevent OutOfMemoryError and improve performance

1. ✅ `snapshots/01-snapshot-recovery-on-startup.md`
2. ✅ `snapshots/02-snapshot-based-materialization.md`
3. ✅ `cache-optimization/01-implement-lru-cache-eviction.md`

**Rationale:** These tasks address critical production concerns (memory leaks, slow queries).

---

### Phase 2: Missing Features (Medium Priority)

**Goal:** Complete the API surface

4. ✅ `deletion/01-implement-branch-deletion.md`
5. ✅ `deletion/02-implement-dataset-deletion.md`

**Rationale:** Branch/dataset deletion are expected features. Defer if not critical.

---

### Phase 3: Developer Experience (Lower Priority)

**Goal:** Improve usability for Java developers

6. ✅ `java-api/01-create-java-sparql-api.md`
7. ✅ `java-api/02-create-java-graph-store-api.md`

**Rationale:** Nice-to-have for embedded use. Can be deferred if time-constrained.

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

### For Project Managers

- **Phase 1 tasks are critical** - schedule these first
- **Phase 2 tasks are features** - prioritize based on user needs
- **Phase 3 tasks are enhancements** - nice-to-have

### For Architects

- All tasks follow **CQRS + Event Sourcing** patterns
- All tasks maintain **backward compatibility**
- All tasks include **rollback plans**
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
- Performance tests

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

After implementing these tasks, monitor:

**Performance Metrics:**
- `dataset.cache.size` - Cache size
- `dataset.cache.hit.rate` - Cache efficiency
- `dataset.snapshot.hits` - Snapshot usage
- `dataset.materialize` - Materialization time

**Memory Metrics:**
- `jvm.memory.used` - Heap usage
- `jvm.gc.pause` - Garbage collection

**Event Metrics:**
- `event.published` - Events published
- `event.projector.processed` - Events processed
- `event.projector.processing` - Processing time

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

---

## References

- [Architecture Overview](../docs/architecture/README.md)
- [CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../.claude/CLAUDE.md)
- [Kafka Storage Guide](../docs/operations/kafka-storage-guide.md)
- [Performance Optimization](../docs/operations/performance.md)

---

## Status Legend

- ✅ **Completed** - Task is done and merged
- 🚧 **In Progress** - Task is being worked on
- 📋 **Planned** - Task is ready to start
- ⏸️ **Deferred** - Task is lower priority
- ❌ **Cancelled** - Task no longer needed

Current status: All tasks are **📋 Planned**
