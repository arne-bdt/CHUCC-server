# CHUCC Server - Task Roadmap

This directory contains task breakdowns for implementing the remaining SPARQL 1.2 Protocol Version Control Extension endpoints. Each task is designed to be completable in one development session (3-6 hours).

---

## Status: IN PROGRESS

**Previously:** All original tasks were completed and removed (October 24, 2025)

**Now:** 5 tasks remain to implement protocol endpoints that currently return 501 "Not Implemented"

**Recent Completions:**
- patchSize Schema Evolution (2025-01-24) - CQRS Compliant ✅
- Branch Management API (2025-10-24)

---

## Remaining Tasks

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

## Progress Summary

**Feature Tasks:** 3 tasks (3 endpoint implementations remaining)
**Schema Evolution:** 0 tasks (all completed)
**Architecture/Technical Debt:** 1 task (optional improvement)

**Total Endpoints Remaining:** 6 endpoints to implement
**Total Estimated Time:** 13-16 hours (features) + 8-12 hours (architecture)

**Priority Breakdown:**
- 🔴 High Priority: 1 task (Merge)
- 🟡 Medium Priority: 2 tasks (History/Diff, Batch)
- 🔵 Low Priority: 1 task (Squash/Rebase refactoring - optional)

**Current Status:** All schema evolution tasks and Tag Management completed (100%)
- ✅ CommitCreatedEvent patchSize (completed 2025-01-24)
- ✅ Revert/CherryPick patchSize (completed 2025-01-25)
- ✅ Commit Metadata API (completed 2025-01-25)
- ✅ Branch Management API (completed 2025-10-24)
- ✅ Tag Management API (completed 2025-10-25)

---

## Recommended Implementation Order

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

**Last Updated:** 2025-10-25
**Next Review:** After next task completion
