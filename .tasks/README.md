# CHUCC Server - Task Roadmap

This directory contains task breakdowns for implementing the remaining SPARQL 1.2 Protocol Version Control Extension endpoints. Each task is designed to be completable in one development session (3-6 hours).

---

## Status: NEW TASKS ADDED ⚠️

**Previously:** All tasks were completed and removed (October 24, 2025)

**Now:** 6 new tasks added to implement protocol endpoints that currently return 501 "Not Implemented"

---

## New Tasks (Added 2025-10-24)

### 🔴 High Priority

#### 1. Branch Management API
**File:** [`.tasks/branches/01-implement-branch-list-and-create-api.md`](./branches/01-implement-branch-list-and-create-api.md)

**Endpoints:**
- `GET /version/branches` - List all branches
- `POST /version/branches` - Create new branch
- `GET /version/branches/{name}` - Get branch info

**Status:** Not Started
**Estimated Time:** 3-4 hours
**Protocol Spec:** §3.2 (implied)

---

#### 2. Tag Management API
**File:** [`.tasks/tags/01-implement-tag-list-and-create-api.md`](./tags/01-implement-tag-list-and-create-api.md)

**Endpoints:**
- `GET /version/tags` - List all tags
- `POST /version/tags` - Create new immutable tag

**Status:** Not Started
**Estimated Time:** 3-4 hours
**Protocol Spec:** §3.5

**Already Implemented:**
- ✅ `GET /version/tags/{name}` - Get tag details
- ✅ `DELETE /version/tags/{name}` - Delete tag

---

#### 3. Merge Operations API
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

#### 4. History & Diff API
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

#### 5. Commit Metadata API
**File:** [`.tasks/commits/01-implement-commit-metadata-api.md`](./commits/01-implement-commit-metadata-api.md)

**Endpoints:**
- `GET /version/commits/{id}` - Get commit metadata

**Status:** Not Started
**Estimated Time:** 2-3 hours
**Protocol Spec:** §3.2

**Already Implemented:**
- ✅ `POST /version/commits` - Create commit (apply RDF Patch)

---

#### 6. Batch Operations API
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

## Progress Summary

**Total New Tasks:** 6 tasks
**Total Endpoints:** 11 endpoints
**Estimated Total Time:** 21-27 hours

**Priority Breakdown:**
- 🔴 High Priority: 3 tasks (Branches, Tags, Merge)
- 🟡 Medium Priority: 3 tasks (History, Commits, Batch)

**Current Status:** 0 of 6 tasks completed (0%)

---

## Recommended Implementation Order

1. **Commit Metadata API** (2-3 hours)
   - Simplest task
   - Read-only, no CQRS complexity
   - Good warm-up

2. **Branch Management API** (3-4 hours)
   - Essential functionality
   - Builds on existing delete branch handler

3. **Tag Management API** (3-4 hours)
   - Similar to branches
   - Immutability adds slight complexity

4. **History & Diff API** (4-5 hours)
   - Read-only operations
   - Diff requires RDF Patch serialization

5. **Batch Operations API** (4-5 hours)
   - Complex but modular
   - Reuses existing query/update logic

6. **Merge Operations API** (5-6 hours)
   - Most complex task
   - Requires merge algorithm
   - Should be implemented last

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

### ✅ Branch Operations
- `DELETE /version/branches/{name}` - Delete branch

### ✅ Tag Operations
- `GET /version/tags/{name}` - Get tag details
- `DELETE /version/tags/{name}` - Delete tag

### ✅ Commit Operations
- `POST /version/commits` - Create commit (apply RDF Patch)

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
5. Update this README (move task to "Completed" section)

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
- ✅ Refs listing (`GET /version/refs`) - *Already implemented*
- ❌ Commit metadata (`GET /version/commits/{id}`)
- ❌ History listing (`GET /version/history`)
- ❌ Branch management (`GET/POST /version/branches`)
- ❌ Tag management (`GET/POST /version/tags`)
- ❌ Merge operations (`POST /version/merge`)
- ❌ Batch operations (`POST /version/batch`)

### Optional Endpoints (Extensions)
- ❌ Diff (`GET /version/diff`)
- ❌ Blame (`GET /version/blame`)
- ✅ Advanced operations (cherry-pick, revert, reset, rebase, squash)

---

## Completed Work (October 2025)

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
- 711 tests (all passing)
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
   - Move task from "New Tasks" to "Completed Tasks"
   - Update progress percentage
   - Update endpoint status (❌ → ✅)

4. **Delete Task File**
   - Remove `.tasks/<category>/<task-file>.md`
   - Keep directory if other tasks remain

5. **Celebrate!** 🎉

---

## Questions?

- Read the task file for detailed implementation steps
- Check protocol specifications for requirements
- Review existing similar implementations for patterns
- Consult [Development Guidelines](../.claude/CLAUDE.md) for best practices

---

**Last Updated:** 2025-10-24
**Next Review:** After first task completion
