# Protocol Update Tasks - Overview

## Purpose
Update implementation to match the latest SPARQL 1.2 Protocol Version Control Extension specification.

## Task Organization
Each task is in a separate numbered markdown file. Tasks are designed to be:
- **Incremental**: Can be completed in a single focused session
- **Self-contained**: Includes all context, acceptance criteria, and test requirements
- **TDD-friendly**: Tests first, then implementation, then verify
- **Dependency-aware**: Clear prerequisites listed

## Recommended Order (with dependencies)

### Phase 1: Foundation (Required for everything else)
- [x] `01-error-handling.md` - Implement problem+json error responses
- [x] `02-selector-validation.md` - Implement selector mutual exclusivity validation
- [x] `03-header-updates.md` - Fix request header names to match spec

### Phase 2: Core Missing Endpoints
- [ ] `04-refs-endpoint.md` - GET /version/refs (depends on: 01)
- [ ] `05-commits-endpoint.md` - POST /version/commits (depends on: 01, 02)
- [ ] `06-tags-endpoints.md` - GET/DELETE /version/tags/{name} (depends on: 01)

### Phase 3: Advanced Operations Refactoring
- [ ] `07-reset-refactor.md` - Move reset endpoint to correct path (depends on: 01, 02)
- [ ] `09-revert-endpoint.md` - Add POST /version/revert controller (depends on: 01, 02)

### Phase 4: New Advanced Operations
- [ ] `08-cherry-pick.md` - POST /version/cherry-pick (depends on: 01, 02)
- [ ] `10-rebase.md` - POST /version/rebase (depends on: 01, 02)
- [ ] `11-squash.md` - POST /version/squash (depends on: 01, 02)

### Phase 5: Enhanced Features
- [ ] `12-etag-concurrency.md` - ETag and If-Match preconditions (depends on: 05)
- [ ] `13-no-op-patch.md` - No-op patch detection returning 204 (depends on: 05)
- [ ] `14-asof-support.md` - asOf selector with branch (depends on: 02)

### Phase 6: Cleanup
- [ ] `15-cleanup-deprecated.md` - Remove/document non-spec endpoints (depends on: all others)

## Progress Tracking
- **Total Tasks**: 15
- **Completed**: 0
- **In Progress**: None
- **Blocked**: None

## Quick Start
1. Start with Phase 1 tasks (foundation)
2. Each task file contains complete implementation instructions
3. Follow TDD approach: write tests first
4. Run `mvn clean install` after each task
5. Mark completed tasks with [x] in this file

## Session Plan Suggestion
- **Session 1**: Tasks 01-03 (Foundation)
- **Session 2**: Tasks 04-06 (Core endpoints)
- **Session 3**: Tasks 07-09 (Refactoring)
- **Session 4**: Tasks 08-11 (New operations)
- **Session 5**: Tasks 12-14 (Enhanced features)
- **Session 6**: Task 15 (Cleanup)

## Notes
- Some tasks can be done in parallel if dependencies are met
- Integration tests should verify cross-protocol consistency (SPARQL + GSP)
- All error responses must use problem+json format
- All conflict items must include subject, predicate, object, graph fields
