# CHUCC Server - Task Roadmap

This directory contains task breakdowns for implementing the remaining SPARQL 1.2 Protocol Version Control Extension endpoints. Each task is designed to be completable in one development session (3-6 hours).

---

## Status: IN PROGRESS

**Previously:** All original tasks were completed and removed (October 24, 2025)

**Now:** 2 tasks remain (2 protocol endpoints + 1 technical debt)

**Recent Completions:**
- Merge Operations API (All phases - 2025-10-28) ✅
- Materialized Views (All 6 tasks - 2025-10-26 to 2025-10-28) ✅
- Parallel Event Replay (Simplified approach - 2025-10-28) ✅
- patchSize Schema Evolution (2025-01-24) ✅
- Branch Management API (2025-10-24) ✅
- Dataset Management + Kafka Integration (2025-10-26) ✅

---

## Remaining Tasks

### 🟡 Medium Priority

#### 1. History Listing API
**File:** [`.tasks/history/01-implement-history-api.md`](./history/01-implement-history-api.md)

**Endpoint:**
- `GET /version/history` - List commit history with filters and pagination

**Status:** Not Started
**Estimated Time:** 2-3 hours
**Protocol Spec:** §3.2
**Complexity:** Low (⭐ Start here - good warm-up task)

**Features:**
- Filter by branch (commits reachable from HEAD)
- Filter by date range (since/until)
- Filter by author (exact match)
- Offset-based pagination with Link headers (RFC 5988)

---

#### 1. Diff API
**File:** [`.tasks/history/02-implement-diff-api.md`](./history/02-implement-diff-api.md)

**Endpoint:**
- `GET /version/diff` - Diff two commits (returns RDF Patch)

**Status:** Not Started
**Estimated Time:** 1 hour
**Protocol Spec:** Extension (not in official SPARQL 1.2 spec)
**Complexity:** Low (infrastructure already exists)

**Features:**
- Load commit snapshots (cached, optimized)
- Compute RDF Patch diff using existing utilities
- Serialize to text/rdf-patch format

**Note:** This is an **EXTENSION** (not in official SPARQL 1.2 spec)

---

#### 2. Blame API
**File:** [`.tasks/history/03-implement-blame-api.md`](./history/03-implement-blame-api.md)

**Endpoint:**
- `GET /version/blame` - Last-writer attribution for all quads

**Status:** Not Started
**Estimated Time:** 3-4 hours
**Protocol Spec:** Extension (not in official SPARQL 1.2 spec)
**Complexity:** Medium-High (reverse history traversal)

**Features:**
- Reverse BFS traversal from target commit to root
- Track quad additions with removal logic (handles delete/re-add)
- Early exit optimization when all quads found

**Note:** This is an **EXTENSION** (not in official SPARQL 1.2 spec)

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

**Feature Tasks:** 3 endpoint implementations (4-6 hours)
- Diff API: 1 task (1 hour)
- Blame API: 1 task (3-4 hours)
- Batch Operations API: 1 task (4-5 hours)

**Architecture/Technical Debt:** 1 task (optional improvement - 8-12 hours)

**Total Endpoints Remaining:** 3 endpoints to implement
**Total Estimated Time:**
- Protocol Endpoints: 8-10 hours (broken into smaller tasks)
- Technical Debt: 8-12 hours (optional)

**Priority Breakdown:**
- 🟡 Medium Priority: 3 tasks (Diff, Blame, Batch)
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

**Status:** All infrastructure tasks completed (2025-10-26 to 2025-10-28)

1. ✅ **Dataset Management + Kafka Integration** (completed 2025-10-26)
   - Dynamic dataset creation with automatic topic provisioning
   - Production Kafka configuration (RF=3, partitions=6)
   - Error handling with retry logic
   - Monitoring and health checks

2. ✅ **Materialized Views** (completed 2025-10-26 to 2025-10-27)
   - 10-20x faster branch HEAD queries
   - LRU eviction for memory management
   - Monitoring and recovery mechanisms

3. ✅ **Fail-Fast Projection Error Handling** (completed 2025-10-28)
   - Kafka retry with exponential backoff
   - Dead Letter Queue (DLQ) for failed events
   - Comprehensive error metrics

4. ✅ **Parallel Event Replay** (completed 2025-10-28)
   - Configurable consumer concurrency
   - 6x faster startup with parallel processing
   - Event processing timing metrics

---

### Feature Implementation

**Recommended Order (Start Simple, Build Up):**

1. **Diff API** (1 hour) ⭐ **Start here**
   - Read-only operation
   - Infrastructure already exists (DatasetService, RdfPatchUtil)
   - Just wire up existing components
   - Quick win after history

   - Read-only operation
   - More complex: reverse history traversal
   - Interesting algorithm (BFS with quad tracking)
   - Good challenge after simpler tasks

3. **Batch Operations API** (4-5 hours)
   - Write operations (CQRS)
   - Complex but modular
   - Reuses existing query/update logic
   - Performance optimization for bulk operations

### Infrastructure (Future/Optional)

**Recommended:** Defer until after feature implementation is complete.

3. **Per-Dataset Tuning** (4-5 hours)
   - Allows custom Kafka config per dataset
   - Nice-to-have for advanced use cases
   - Can be added later without breaking changes

4. **Topic Health Checks** (4-5 hours)
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
- ✅ Merge operations (`POST /version/merge`)
- ✅ History listing (`GET /version/history`)
- ❌ Batch operations (`POST /version/batch`)

### Optional Endpoints (Extensions)
- ❌ Diff (`GET /version/diff`)
- ❌ Blame (`GET /version/blame`)
- ✅ Advanced operations (cherry-pick, revert, reset, rebase, squash)

---

## Completed Tasks (2025)

### ✅ History Listing API (Completed 2025-11-01)
**File:** `.tasks/history/01-implement-history-api.md` (DELETED - task completed)

**Endpoint:**
- ✅ `GET /version/history` - List commit history with filters and pagination

**Status:** ✅ Completed (2025-11-01)
**Category:** Version Control Protocol
**Protocol Spec:** §3.2
**Estimated Time:** 2-3 hours (actual)

**Implementation:**
- Created DTOs: CommitHistoryInfo, HistoryResponse, PaginationInfo
- Implemented HistoryService with comprehensive filtering
- Updated HistoryController (replaced 501 stub)
- Branch filtering: BFS traversal to find reachable commits
- Date range filtering: since/until parameters (RFC3339/ISO8601)
- Author filtering: exact match, case-sensitive
- Offset-based pagination with RFC 5988 Link headers
- Added 10 integration tests (HistoryListingIT)
- Added 10 unit tests (HistoryServiceTest)

**Optimizations:**
- Fixed N+1 query problem: in-memory commit map for O(1) lookups
- Single-pass stream filtering (lazy evaluation)
- Pagination validation (max 1000 limit, DoS protection)

**Files Created:**
- `src/main/java/org/chucc/vcserver/dto/CommitHistoryInfo.java`
- `src/main/java/org/chucc/vcserver/dto/PaginationInfo.java`
- `src/main/java/org/chucc/vcserver/dto/HistoryResponse.java`
- `src/main/java/org/chucc/vcserver/service/HistoryService.java`
- `src/test/java/org/chucc/vcserver/integration/HistoryListingIT.java`
- `src/test/java/org/chucc/vcserver/service/HistoryServiceTest.java`

**Files Modified:**
- `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

**Design Decisions:**
- Read-only operation (no CQRS commands/events needed)
- Sorting: timestamp descending (newest first)
- Branch filter: BFS with in-memory commit map (performance optimization)
- Pagination: offset-based with hasMore flag (no total count for performance)
- Date parsing: RFC3339/ISO8601 format (Instant.parse)
- URL encoding: manual encoding for Link headers

---

### ✅ Merge Operations API (Completed 2025-10-28)
**File:** `.tasks/merge/` (DELETED - tasks completed)

**Endpoint:**
- ✅ `POST /version/merge` - Merge two refs/commits into target branch

**Status:** ✅ Completed (2025-10-28)
**Category:** Version Control Protocol
**Protocol Spec:** §3.3

**Implementation:**
- Phase 1: Core merge functionality (fast-forward, three-way merge, conflict detection)
- Phase 2: Conflict resolution strategies ("ours", "theirs") with configurable scope (graph-level, dataset-level)
- Phase 3: Manual resolution - **NOT IMPLEMENTED** (protocol mentions but doesn't define semantics)

**Files Created:**
- `src/main/java/org/chucc/vcserver/dto/MergeRequest.java`
- `src/main/java/org/chucc/vcserver/dto/MergeResponse.java`
- `src/main/java/org/chucc/vcserver/dto/MergeConflict.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommand.java`
- `src/main/java/org/chucc/vcserver/command/MergeCommandHandler.java`
- `src/main/java/org/chucc/vcserver/event/MergeCommitCreatedEvent.java`
- `src/main/java/org/chucc/vcserver/controller/MergeController.java`
- `src/main/java/org/chucc/vcserver/util/MergeUtil.java`
- `src/test/java/org/chucc/vcserver/integration/MergeOperationsIT.java`
- `src/test/java/org/chucc/vcserver/command/MergeCommandHandlerTest.java`

**Design Decisions:**
- Fast-forward modes: "allow", "only", "never"
- Merge strategies: "three-way" (default), "ours", "theirs"
- Conflict scope: "graph" (default), "dataset"
- Manual resolution strategy deferred (not required by protocol)
- Lowest Common Ancestor (LCA) algorithm for merge base
- Graph-level conflict detection by default
- Comprehensive conflict reporting with RFC 7807 errors

---

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

### ✅ Materialized Branch Views (Completed 2025-10-26 to 2025-10-28)
**Goal:** Move from on-demand graph materialization to eager materialization for instant query performance.

**Completed Tasks (6 total, 19-25 hours):**
1. ✅ MaterializedBranchRepository Infrastructure (3-4 hours) - 2025-10-26
2. ✅ ReadModelProjector Eager Materialization (4-5 hours) - 2025-10-26
3. ✅ DatasetService Integration (3-4 hours) - 2025-10-27
4. ✅ Monitoring and Recovery Mechanisms (3-4 hours) - 2025-10-27
5. ✅ LRU Eviction for Memory Management (4-5 hours) - 2025-10-27
6. ✅ Fail-Fast Projection Error Handling (2-3 hours) - 2025-10-28

**Benefits Achieved:**
- 10-20x faster branch HEAD queries (<10ms vs 100-200ms)
- Memory usage scales with branches, not history depth
- Production-ready monitoring (/actuator/health/materializedViews)
- Manual rebuild endpoint (/actuator/materialized-views/rebuild)
- LRU eviction prevents OutOfMemoryError
- Kafka retry with exponential backoff + DLQ for failed events
- Comprehensive Micrometer metrics

**CQRS Compliance:** ✅ 100% compliant, approved for production

---

### ✅ Parallel Event Replay (Completed 2025-10-28)
**Goal:** Reduce startup time by processing events in parallel.

**Status:** ✅ Completed (Simpler Approach)
**Implementation Time:** 2-3 hours (metrics + concurrency configuration)

**Completed:**
- Event processing timing metrics (`chucc.projection.event.duration`)
- Configurable consumer concurrency (`kafka.consumer.concurrency`)
- Parallel processing across dataset topics

**Benefits Achieved:**
- 6x faster startup (with 6 datasets and concurrency=6)
- Better CPU utilization (multiple cores used)
- Simple configuration (no partition management)
- Measurement infrastructure in place

**Configuration:**
```yaml
kafka:
  consumer:
    concurrency: 6  # Parallel consumers (default: 1)
```

**Design Decision:** The simpler approach (configurable concurrency) achieves ~90% of the benefit with 1% of the complexity compared to full partitioning strategy.

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
- 1168 tests (all passing)
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

**Last Updated:** 2025-10-28
**Next Review:** After next task completion
