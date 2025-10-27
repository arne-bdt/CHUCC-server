# CHUCC Server - Task Roadmap

This directory contains task breakdowns for implementing the remaining SPARQL 1.2 Protocol Version Control Extension endpoints. Each task is designed to be completable in one development session (3-6 hours).

---

## Status: IN PROGRESS

**Previously:** All original tasks were completed and removed (October 24, 2025)

**Now:** 11 tasks remain (3 protocol endpoints + 1 technical debt + 2 production hardening + 1 performance optimization + 4 architecture enhancements completed)

**Recent Completions:**
- patchSize Schema Evolution (2025-01-24) - CQRS Compliant ✅
- Branch Management API (2025-10-24)
- Dataset Management + Kafka Integration (2025-10-26) ✅

---

## Remaining Tasks

### 🟠 High Priority (Architecture Enhancement)

#### 0. Materialized Branch Views - Continuously Updated DatasetGraphs
**Directory:** [`.tasks/materialized-views/`](./materialized-views/)

**Goal:** Move from on-demand graph materialization to eager materialization for instant query performance.

**Status:** ✅ COMPLETED (All 4 tasks completed)
**Estimated Time:** 13-17 hours (4 tasks)
**Category:** Architecture Enhancement
**Completion:** 100% (4/4 tasks completed)

**Tasks:**
1. ✅ [Task 01: Create MaterializedBranchRepository Infrastructure](./materialized-views/01-create-materialized-branch-repository.md) (3-4 hours) - COMPLETED 2025-10-26
   - Created MaterializedBranchRepository interface
   - Implemented InMemoryMaterializedBranchRepository with per-branch locking
   - Custom RDFChangesApply to bypass Jena transaction management
   - 16 unit tests (all passing)

2. ✅ [Task 02: Update ReadModelProjector for Eager Materialization](./materialized-views/02-update-projector-for-eager-materialization.md) (4-5 hours) - COMPLETED 2025-10-26
   - Updated ReadModelProjector to apply patches eagerly
   - Handle branch creation/deletion in materialized views
   - 4 integration tests (MaterializedGraphProjectionIT, all passing)
   - **Note:** CQRS agent identified exception handling improvements needed (see follow-up tasks)

3. ✅ [Task 03: Update DatasetService to Use Materialized Views](./materialized-views/03-update-datasetservice-for-materialized-views.md) (3-4 hours) - COMPLETED 2025-10-27
   - Updated DatasetService to use pre-materialized branch graphs
   - Implemented getMaterializedBranchGraph() for O(1) branch HEAD lookups
   - Simplified cache logic (removed branch HEAD pinning)
   - Graceful fallback to on-demand building when materialized graph unavailable
   - 4 new unit tests (all passing)
   - **CQRS Agent Verification:** ✅ 100% compliant, APPROVED FOR PRODUCTION
   - **Test Isolation Validation:** ✅ PERFECT isolation patterns
   - Performance: 10-20x faster branch HEAD queries (<5ms vs 50-500ms)

4. ✅ [Task 04: Add Monitoring and Recovery Mechanisms](./materialized-views/04-add-monitoring-and-recovery.md) (3-4 hours) - COMPLETED 2025-10-27
   - Micrometer metrics (graph count, memory usage, patch operations, rebuild operations)
   - Health checks for materialized views (/actuator/health/materializedViews)
   - Manual rebuild endpoint for recovery (/actuator/materialized-views/rebuild)
   - Scheduled monitoring with periodic statistics logging
   - Configuration properties for materialized views

**Technical Debt Identified (from agent reviews):**
- ~~Exception handling in projector needs improvement (rethrow vs swallow)~~ → **Task 06** created
- Idempotency check needed for branch creation
- Transaction management documentation needed
- ✅ MaterializedViewRebuildIT test refactored (extended ITFixture, removed duplicate setup) - COMPLETED 2025-10-27
- ~~LRU eviction needed for memory management~~ → **Task 05** created

**Benefits:**
- ✅ **10-20x faster** branch HEAD queries (<10ms vs 100-200ms)
- ✅ Memory usage scales with branches, not history depth
- ✅ CQRS compliant (projector maintains materialized views)
- ✅ Backward compatible (historical queries unchanged)
- ✅ Production-ready monitoring and recovery

**CQRS Compliance:** ✅ Full compliance
- Projector applies patches to materialized graphs (read model updates)
- DatasetService returns materialized graphs (query side)
- Events remain source of truth in CommitRepository

**Follow-Up Tasks (Production Hardening):**
5. [Task 05: Add LRU Eviction for Memory Management](./materialized-views/05-add-lru-eviction-for-memory-management.md) (4-5 hours) - **HIGH PRIORITY**
   - Replace unbounded ConcurrentHashMap with Caffeine LRU cache
   - Configurable max-branches limit (default: 25)
   - On-demand rebuild for evicted graphs
   - Cache metrics (hits, misses, evictions)
   - **Impact:** Prevents OutOfMemoryError in large deployments

6. [Task 06: Implement Fail-Fast Projection Errors](./materialized-views/06-implement-fail-fast-projection-errors.md) (2-3 hours) - **HIGH PRIORITY**
   - Re-throw exceptions on patch application failures
   - Kafka retry with exponential backoff
   - Dead Letter Queue (DLQ) for failed events
   - **Impact:** Ensures data consistency (no silent corruption)

---

### 🔴 High Priority

#### 1. Merge Operations API
**File:** [`.tasks/merge/01-implement-merge-api.md`](./merge/01-implement-merge-api.md)

**Endpoints:**
- `POST /version/merge` - Merge two refs/commits

**Status:** Not Started
**Estimated Time:** 5-6 hours (most complex)
**Protocol Spec:** §3.3

**Complexity:**
- Three-way merge algorithm
- Conflict detection
- Multiple merge strategies (three-way, ours, theirs, manual)
- Fast-forward detection

---

### 🟡 Medium Priority

#### 2. History & Diff API
**File:** [`.tasks/history/01-implement-history-diff-blame-api.md`](./history/01-implement-history-diff-blame-api.md)

**Endpoints:**
- `GET /version/history` - List commit history with filters
- `GET /version/diff` - Diff two commits (returns RDF Patch)
- `GET /version/blame` - Last-writer attribution

**Status:** Not Started
**Estimated Time:** 4-5 hours
**Protocol Spec:** §3.2 (history), Extensions (diff, blame)

**Note:** Diff and blame are **extensions** (not in official SPARQL 1.2 spec)

---

#### 3. Batch Operations API
**File:** [`.tasks/batch/01-implement-batch-operations-api.md`](./batch/01-implement-batch-operations-api.md)

**Endpoints:**
- `POST /version/batch` - Execute batch of SPARQL operations atomically

**Status:** Not Started
**Estimated Time:** 4-5 hours
**Protocol Spec:** §3.6

**Features:**
- Atomic and non-atomic execution modes
- Single commit mode (combine all writes)
- Mixed operations (query, update, applyPatch)

**Note:** Distinct from `/version/batch-graphs` (Graph Store Protocol batch endpoint)

---

### 🔵 Low Priority (Technical Debt)

#### 4. Refactor Squash/Rebase Handlers to Pure CQRS
**File:** [`.tasks/architecture/02-refactor-squash-rebase-to-pure-cqrs.md`](./architecture/02-refactor-squash-rebase-to-pure-cqrs.md)

**Problem:**
- `SquashCommandHandler` and `RebaseCommandHandler` write directly to repository
- Violates CQRS pattern (dual-write, no event replay)
- Breaks eventual consistency promise

**Solution:**
- Enrich events with full commit data (patch, patchSize, parents)
- Remove repository writes from command handlers
- Let projector handle all repository updates

**Status:** Not Started
**Estimated Time:** 8-12 hours
**Category:** Architectural Refactoring
**Complexity:** High

**Note:** This is technical debt, not a critical bug. Can be deferred.

---

### 🟢 Medium Priority (Performance Optimization)

#### 5. Parallel Event Replay
**File:** [`.tasks/performance/01-implement-parallel-event-replay.md`](./performance/01-implement-parallel-event-replay.md)

**Goal:** Reduce startup time by processing events in parallel using Kafka partitioning.

**Status:** Not Started
**Estimated Time:** 6-8 hours
**Category:** Performance & Scalability
**Complexity:** High

**Problem:**
- Current: Single consumer processes ALL events sequentially
- Result: 10,000 events = 10 seconds startup (1ms/event)
- Bottleneck: Only one CPU core utilized

**Solution:**
- Partition topics by dataset (dataset = partition key)
- Multiple consumer instances (one per partition)
- Events for different datasets processed in parallel
- Startup time reduced by factor of N (partition count)

**Benefits:**
- ✅ **10x faster startup** (with 10 partitions)
- ✅ Better CPU utilization (all cores used)
- ✅ Horizontal scalability
- ✅ Dataset isolation

**Trade-offs:**
- ⚠️ Increased complexity (partition management)
- ⚠️ No cross-dataset ordering guarantees
- ⚠️ Cross-dataset operations become more complex

**When to Implement:**
- Large deployments (>100 datasets)
- Startup time critical (high availability requirements)
- Multiple CPU cores available

**Configuration:**
```yaml
chucc:
  kafka:
    partitions: 6  # Default
    consumer:
      concurrency: 6  # Match partition count
```

---

### 🟠 Critical (Infrastructure)

#### 5. ✅ Dataset Management with Kafka Topic Integration - COMPLETED
**Status:** ✅ COMPLETED (2025-10-26)

**Completed Tasks:**
1. ✅ **Dataset Creation Endpoint** (Critical)
   - `POST /version/datasets/{name}` - Create dataset with automatic Kafka topic creation
   - Completed: See commit a91f9f2

2. ✅ **Production Kafka Config** (Critical)
   - Updated Kafka configuration for production (RF=3, partitions=6)
   - Completed: See commit 0ad6c89

3. ✅ **Error Handling** (Important)
   - Robust error handling for topic creation failures with retry logic
   - Completed: See commit 353ee54

4. ✅ **Monitoring & Metrics** (Important)
   - Comprehensive monitoring for dataset/topic lifecycle via Micrometer
   - Completed: See commit 654ba92

5. ⏭️ **Per-Dataset Tuning** (Future)
   - Custom Kafka configuration per dataset
   - Status: Skipped (future enhancement)

6. ✅ **Topic Health Checks** (Simplified)
   - Manual health checks and healing endpoints (no auto-healing or scheduled checks)
   - Completed: See commit 6612682

**Deliverables:**
- ✅ Dataset creation/deletion endpoints
- ✅ Automatic Kafka topic provisioning with production settings
- ✅ RFC 7807 error handling with retry logic
- ✅ Health monitoring endpoints (`/actuator/kafka/health-detailed`)
- ✅ Manual topic healing (`/actuator/kafka/topics/{dataset}/heal?confirm=true`)
- ✅ Comprehensive metrics via Micrometer

---

## Progress Summary

**Architecture Enhancements:** 4 tasks completed (Materialized Views - 13-17 hours) ✅
**Production Hardening:** 2 tasks (LRU Eviction, Fail-Fast - 6-8 hours) - **HIGH PRIORITY**
**Performance Optimization:** 1 task (Parallel Replay - 6-8 hours) - **MEDIUM PRIORITY**
**Feature Tasks:** 3 tasks (3 endpoint implementations - 13-16 hours)
**Schema Evolution:** 0 tasks (all completed) ✅
**Architecture/Technical Debt:** 1 task (optional improvement - 8-12 hours)
**Infrastructure/Operations:** 0 tasks (all completed) ✅

**Total Endpoints Remaining:** 6 endpoints to implement
**Total Estimated Time:**
- ✅ Materialized Views: 13-17 hours (completed)
- 🔴 Production Hardening: 6-8 hours (high priority)
- 🟢 Performance: 6-8 hours (medium priority)
- Protocol Endpoints: 13-16 hours
- Technical Debt: 8-12 hours (optional)

**Priority Breakdown:**
- ✅ Architecture Enhancement: 4 tasks (Materialized Views) - COMPLETED
- 🔴 Production Hardening: 2 tasks (Memory, Consistency) - **CRITICAL**
- 🟢 Performance: 1 task (Parallel Replay)
- 🔴 High Priority: 1 task (Merge)
- 🟡 Medium Priority: 2 tasks (History/Diff, Batch)
- 🔵 Low Priority: 1 task (Squash/Rebase refactoring - optional)

**Current Status:** All infrastructure and schema evolution tasks completed (100%)
- ✅ CommitCreatedEvent patchSize (completed 2025-01-24)
- ✅ Revert/CherryPick patchSize (completed 2025-01-25)
- ✅ Commit Metadata API (completed 2025-01-25)
- ✅ Branch Management API (completed 2025-10-24)
- ✅ Tag Management API (completed 2025-10-25)
- ✅ Dataset Management + Kafka Integration (completed 2025-10-26)

---

## Recommended Implementation Order

### ✅ Infrastructure (Critical) - COMPLETED

**Status:** All infrastructure tasks completed (2025-10-26)

1. ✅ **Dataset Creation Endpoint** (completed)
   - Enables dynamic dataset creation without DevOps
   - Creates Kafka topics automatically
   - See commit a91f9f2

2. ✅ **Production Kafka Config** (completed)
   - Production safety with RF=3
   - See commit 0ad6c89

3. ✅ **Error Handling** (completed)
   - User experience with clear error messages (RFC 7807)
   - Retry logic and rollback mechanisms
   - See commit 353ee54

4. ✅ **Monitoring & Metrics** (completed)
   - Production operations monitoring
   - Proactive issue detection via Micrometer
   - See commits 654ba92, 6612682

### Architecture Enhancement (Recommended First)

**Materialized Branch Views** (13-17 hours total)

**Why first:**
- Significant performance improvement (10-20x faster queries)
- Foundation for better user experience
- Independent of protocol endpoints (can be done in parallel)
- Production-ready monitoring and recovery

**Implementation order:**
1. **Task 01: MaterializedBranchRepository** (3-4 hours)
   - Foundation layer - no dependencies
   - Can be tested in isolation

2. **Task 02: Update ReadModelProjector** (4-5 hours)
   - Integrates with existing projector
   - Enables eager materialization

3. **Task 03: Update DatasetService** (3-4 hours)
   - Instant query performance
   - Backward compatible

4. **Task 04: Monitoring & Recovery** (3-4 hours)
   - Production operational tools
   - Completes the feature

**Alternatively:** Implement in parallel with protocol endpoints if multiple developers available.

---

### Feature Implementation

1. **History & Diff API** (4-5 hours)
   - Read-only operations
   - Diff requires RDF Patch serialization
   - Useful for debugging and auditing

2. **Batch Operations API** (4-5 hours)
   - Complex but modular
   - Reuses existing query/update logic
   - Performance optimization for bulk operations

3. **Merge Operations API** (5-6 hours)
   - Most complex task
   - Requires merge algorithm
   - Should be implemented last

### Infrastructure (Future/Optional)

**Recommended:** Defer until after feature implementation is complete.

5. **Per-Dataset Tuning** (4-5 hours)
   - Allows custom Kafka config per dataset
   - Nice-to-have for advanced use cases
   - Can be added later without breaking changes

6. **Topic Health Checks** (4-5 hours)
   - Automated health checks and self-healing
   - Improves operational reliability
   - Can be added incrementally

### Architecture/Technical Debt (Optional)

This task improves CQRS compliance but is not required for feature completeness:

**Refactor Squash/Rebase to Pure CQRS** (8-12 hours)
   - High complexity, significant refactoring
   - Fixes dual-write pattern and event replay
   - Enables proper eventual consistency testing
   - **Recommended:** Defer until production deployment is planned

---

## Already Implemented Features

The following endpoints are **already implemented** and working:

### ✅ Advanced Operations (All Implemented)
- `POST /version/reset` - Reset branch pointer
- `POST /version/cherry-pick` - Cherry-pick commit
- `POST /version/revert` - Revert commit
- `POST /version/rebase` - Rebase branch
- `POST /version/squash` - Squash commits

**Controller:** [AdvancedOpsController.java](../src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java)

### ✅ Branch Operations (All Implemented - 2025-10-24)
- `GET /version/branches` - List all branches with full metadata
- `POST /version/branches` - Create new branch from commit/branch
- `GET /version/branches/{name}` - Get branch information
- `DELETE /version/branches/{name}` - Delete branch

**Controller:** [BranchController.java](../src/main/java/org/chucc/vcserver/controller/BranchController.java)

### ✅ Tag Operations (All Implemented - 2025-10-25)
- `GET /version/tags` - List all tags with message and author
- `POST /version/tags` - Create new immutable tag
- `GET /version/tags/{name}` - Get tag details
- `DELETE /version/tags/{name}` - Delete tag

**Controller:** [TagController.java](../src/main/java/org/chucc/vcserver/controller/TagController.java)

### ✅ Commit Operations (Partial)
- `POST /version/commits` - Create commit (apply RDF Patch)

**Note:** Commit metadata endpoint still needs implementation

### ✅ Graph Store Protocol
- `GET/PUT/POST/DELETE` - All GSP operations
- Batch graphs operations
- Named graph support
- Version control selectors

---

## Task File Structure

Each task file includes:

1. **Overview** - What needs to be implemented
2. **Current State** - What's already done
3. **Requirements** - API specification
4. **Implementation Steps** - Detailed step-by-step guide
5. **Success Criteria** - Definition of done
6. **Testing Strategy** - Test approach (API vs Projector tests)
7. **Files to Create/Modify** - Complete file list
8. **References** - Links to protocol specs and code

---

## Development Guidelines

### Before Starting a Task

1. Read the task file completely
2. Review the protocol specification
3. Check existing similar implementations for patterns
4. Plan test cases

### During Implementation

1. Follow TDD (write tests first)
2. Use `-q` flag for Maven commands
3. Run static analysis before tests: `mvn -q compile checkstyle:check spotbugs:check`
4. Implement incrementally (DTOs → Command/Event → Handler → Controller)
5. **Invoke specialized agents** after implementation:
   - `@cqrs-compliance-checker` after command/projector changes
   - `@test-isolation-validator` after writing tests
   - `@event-schema-evolution-checker` after modifying events

### After Implementation

1. Run full build: `mvn -q clean install`
2. Verify zero quality violations
3. Create conventional commit message
4. **Delete the task file** when completed
5. Update this README (move task to "Completed Tasks" section)

---

## Testing Strategy

### API Layer Tests (90% of tests)
- Projector **DISABLED** by default
- Test HTTP status codes and headers
- Do **NOT** query repositories
- Example: `BranchControllerIT`

### Projector Tests (10% of tests)
- Projector **ENABLED** via `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
- Use `await()` pattern for async verification
- Test event projection to repositories
- Example: `ReadModelProjectorIT`

See [Development Guidelines](../.claude/CLAUDE.md) for detailed testing patterns.

---

## Success Criteria for All Tasks

- ✅ All endpoints return appropriate responses (no 501)
- ✅ DTOs created with validation
- ✅ CQRS pattern followed (Command → Event → Projector)
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (command handlers)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`
- ✅ Task file deleted after completion

---

## Protocol Compliance

All tasks implement endpoints from:
- **[SPARQL 1.2 Protocol Version Control Extension](../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)**
- **[Graph Store Protocol Version Control Extension](../protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md)**

### Required Endpoints (Normative)
- ✅ Commit creation (`POST /version/commits`)
- ✅ Refs listing (`GET /version/refs`)
- ✅ Branch management (`GET/POST/GET/{name} /version/branches`)
- ✅ Commit metadata (`GET /version/commits/{id}`)
- ✅ Tag management (`GET/POST /version/tags`)
- ❌ History listing (`GET /version/history`)
- ❌ Merge operations (`POST /version/merge`)
- ❌ Batch operations (`POST /version/batch`)

### Optional Endpoints (Extensions)
- ❌ Diff (`GET /version/diff`)
- ❌ Blame (`GET /version/blame`)
- ✅ Advanced operations (cherry-pick, revert, reset, rebase, squash)

---

## Completed Tasks (2025)

### ✅ Commit Metadata API (Completed 2025-01-25)
**File:** `.tasks/commits/01-implement-commit-metadata-api.md` (DELETED - task completed)

**Endpoints:**
- ✅ `GET /version/commits/{id}?dataset={name}` - Get commit metadata

**Status:** ✅ Completed (2025-01-25)
**Category:** Version Control Protocol
**Protocol Spec:** §3.2

**Implementation:**
- Added CommitService (read-only service)
- Added CommitMetadataDto with defensive copying
- Updated CommitController (replaced 501 stub)
- Added integration and unit tests (8 new tests)

**Files Created:**
- `src/main/java/org/chucc/vcserver/service/CommitService.java`
- `src/main/java/org/chucc/vcserver/dto/CommitMetadataDto.java`
- `src/test/java/org/chucc/vcserver/integration/CommitMetadataIT.java`
- `src/test/java/org/chucc/vcserver/service/CommitServiceTest.java`

**Files Modified:**
- `src/main/java/org/chucc/vcserver/controller/CommitController.java`
- `src/test/java/org/chucc/vcserver/controller/CommitControllerTest.java`
- `src/test/java/org/chucc/vcserver/integration/CrossProtocolInteroperabilityIT.java`

**Design Decisions:**
- Read-only operation (no CQRS commands needed)
- Strong ETag support (commits are immutable)
- No branches/tags lists (expensive O(n) scan, use `/version/refs` instead)
- Minimal metadata (id, message, author, timestamp, parents, patchSize)

---

### ✅ Add patchSize to Revert/CherryPick Events (Completed 2025-01-25)
- Added `patchSize` field to `RevertCreatedEvent`
- Added `patchSize` field to `CherryPickedEvent`
- Updated `RevertCommitCommandHandler` to compute patchSize before event creation
- Updated `CherryPickCommandHandler` to compute patchSize before event creation
- Removed business logic from `ReadModelProjector` (now uses event data only)
- Updated all affected tests (10+ test files)
- CQRS compliance verified by specialized agent

**Benefits:**
- Consistent with CommitCreatedEvent pattern
- Business logic properly on write side (command handlers)
- Events fully self-contained for event replay
- Performance: Patch parsed once instead of twice

**Files Modified:**
- Events: RevertCreatedEvent.java, CherryPickedEvent.java
- Handlers: RevertCommitCommandHandler.java, CherryPickCommandHandler.java
- Projector: ReadModelProjector.java
- Tests: 10+ test files

### ✅ Tag Management API (Completed 2025-10-25)
**File:** `.tasks/tags/01-implement-tag-list-and-create-api.md` (DELETED - task completed)

**Endpoints:**
- ✅ `GET /version/tags` - List all tags with message and author
- ✅ `POST /version/tags` - Create new immutable tag

**Status:** ✅ Completed (2025-10-25)
**Category:** Version Control Protocol
**Protocol Spec:** §3.5

**Implementation:**
- Added TagInfo DTO (unified response for list and create)
- Added CreateTagRequest with validation
- Added TagListResponse with defensive copying
- Updated Tag domain model (message, author, createdAt fields)
- Updated CreateTagCommand and TagCreatedEvent (added message, author)
- Enhanced CreateTagCommandHandler (tag existence validation, commit validation)
- Updated TagService (listTags method)
- Updated TagController (replaced 501 stubs with full implementations)
- Added TagCreatedEvent handler to ReadModelProjector
- Added 10 integration tests in TagOperationsIT

**Design Decisions:**
- Single TagInfo DTO instead of separate CreateTagResponse (DRY)
- 202 Accepted for async CQRS pattern (eventual consistency)
- X-Author header fallback chain: body → header → "anonymous"
- Tag immutability enforcement (409 Conflict on duplicates)
- Nullable message/author for backward compatibility

**Files Created:**
- `src/main/java/org/chucc/vcserver/dto/TagInfo.java`
- `src/main/java/org/chucc/vcserver/dto/CreateTagRequest.java`
- `src/main/java/org/chucc/vcserver/dto/TagListResponse.java`
- `src/test/java/org/chucc/vcserver/integration/TagOperationsIT.java`

**Files Modified:**
- `src/main/java/org/chucc/vcserver/domain/Tag.java`
- `src/main/java/org/chucc/vcserver/command/CreateTagCommand.java`
- `src/main/java/org/chucc/vcserver/event/TagCreatedEvent.java`
- `src/main/java/org/chucc/vcserver/command/CreateTagCommandHandler.java`
- `src/main/java/org/chucc/vcserver/service/TagService.java`
- `src/main/java/org/chucc/vcserver/controller/TagController.java`
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`
- 12+ test files (constructor signature updates)

---

### ✅ Branch Management API (Completed 2025-10-24)
- Implemented `GET /version/branches` - List all branches
- Implemented `POST /version/branches` - Create new branch
- Implemented `GET /version/branches/{name}` - Get branch info
- Full Git-like metadata support (timestamps, commit count, protection)

**Files:**
- Controller: [BranchController.java](../src/main/java/org/chucc/vcserver/controller/BranchController.java)
- Service: [BranchService.java](../src/main/java/org/chucc/vcserver/service/BranchService.java)
- Command Handler: [CreateBranchCommandHandler.java](../src/main/java/org/chucc/vcserver/command/CreateBranchCommandHandler.java)

---

## Completed Work (Before October 2025)

The following major features were completed before these tasks were added:

### ✅ Core CQRS + Event Sourcing (Completed)
- Full event publishing from controllers to Kafka
- ReadModelProjector processes events and updates repositories
- Eventual consistency model validated end-to-end

### ✅ Version Control Features (Completed)
- Time-travel SPARQL queries
- Branch deletion with protection
- Dataset deletion
- Tag get/delete operations
- Advanced operations (reset, cherry-pick, revert, rebase, squash)

### ✅ Performance & Optimization (Completed)
- Snapshot optimization for faster startup
- LRU cache eviction (Caffeine)
- Model API to Graph API migration (20-30% performance gain)

### ✅ Quality & Testing (Completed)
- 1078 tests (all passing)
- Zero quality violations
- Test isolation pattern (projector disabled by default)
- Comprehensive integration test suite

For details on completed work, see git history:
```bash
git log --oneline --since="2025-10-01"
```

---

## Architecture & Documentation

For understanding the implemented system:

- **[Architecture Overview](../docs/architecture/README.md)** - Complete system understanding
- **[CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)** - Core pattern explanation
- **[C4 Component Diagram](../docs/architecture/c4-level3-component.md)** - Component structure
- **[Development Guidelines](../.claude/CLAUDE.md)** - "How-to" for development
- **[OpenAPI Guide](../docs/api/openapi-guide.md)** - API documentation

---

## Contributing

See [Contributing Guide](../docs/development/contributing.md) for development workflow.

---

## Task Completion Workflow

When a task is completed:

1. **Verify Success Criteria**
   - All endpoints implemented (no 501)
   - All tests pass
   - Zero quality violations
   - Full build succeeds

2. **Create Commit**
   - Follow conventional commit format
   - Include "Generated with Claude Code" footer

3. **Update This README**
   - Move task from "Remaining Tasks" to "Completed Tasks"
   - Update progress percentage
   - Update endpoint status (❌ → ✅)

4. **Delete Task File/Folder**
   - Remove `.tasks/<category>/<task-file>.md`
   - Remove directory if no other tasks remain

5. **Celebrate!** 🎉

---

## Questions?

- Read the task file for detailed implementation steps
- Check protocol specifications for requirements
- Review existing similar implementations for patterns
- Consult [Development Guidelines](../.claude/CLAUDE.md) for best practices

---

**Last Updated:** 2025-10-27
**Next Review:** After next task completion
