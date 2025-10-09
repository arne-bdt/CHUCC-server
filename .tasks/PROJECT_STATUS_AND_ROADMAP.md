# CHUCC Server - Project Status and Roadmap

**Date**: 2025-10-09
**Analysis by**: Claude Code

## Executive Summary

The CHUCC Server implements a SPARQL 1.2 Protocol with Version Control Extension using CQRS + Event Sourcing architecture. The project has strong foundations with comprehensive Graph Store Protocol (GSP) implementation and Version Control operations, but SPARQL Query/Update endpoints remain unimplemented.

**Current State**:
- ✅ **Complete**: Graph Store Protocol (GSP) - all CRUD operations
- ✅ **Complete**: Version Control API - branches, tags, commits, history
- ✅ **Complete**: Advanced operations - merge, revert, cherry-pick, squash
- ✅ **Complete**: Test isolation infrastructure with 100% projector coverage
- ✅ **Complete**: Event sourcing with Kafka + RDFPatch
- ❌ **Missing**: SPARQL Query execution (`/sparql` GET - returns 501)
- ❌ **Missing**: SPARQL Update execution (`/sparql` POST - returns 501)
- ⚠️ **Incomplete**: 4 concurrent operation tests disabled (need projector enablement)

**Next Steps**:
1. Re-enable and fix concurrent operation tests (simple projector enablement fix)
2. Implement SPARQL Query endpoint with selector support
3. Implement SPARQL Update endpoint with commit creation
4. Complete GSP Phase 6 polish tasks
5. Add conformance testing suite

## Project Architecture

### Technology Stack
- Java 21 + Spring Boot 3.5
- Apache Jena 5.5 (in-memory graphs with DatasetGraphInMemory)
- CQRS + Event Sourcing pattern
- Apache Kafka for event storage
- RDFPatch (jena-rdfpatch) for event representation
- JUnit 5 + AssertJ + Awaitility for testing
- Testcontainers for integration tests

### Key Components

**1. Command Side (Write Model)**
- Controllers: GraphStoreController, BranchController, TagController, etc.
- Command Handlers: Create events, validate invariants, publish to Kafka
- Domain Model: Commit, Branch, Tag, RdfPatch

**2. Event Store**
- Kafka topics per dataset: `<dataset>-events`
- Events: CommitCreatedEvent, BranchCreatedEvent, MergedEvent, etc.
- Persistent event log (source of truth)

**3. Query Side (Read Model)**
- ReadModelProjector: Consumes events, updates repositories
- Repositories: CommitRepository, BranchRepository, DatasetGraphRepository
- Materialized views for fast queries

**4. Testing Strategy** (✅ Complete as of 2025-10-09)
- Projector disabled by default in integration tests (test isolation)
- API Layer tests: Test HTTP contract without async processing
- Projector tests: Test event handlers with projector enabled
- 100% coverage of ReadModelProjector event handlers (10/10)

## Detailed Status by Component

### ✅ Graph Store Protocol (GSP) - COMPLETE

**Implementation Status**: ~95% complete

| Endpoint | Status | Tests | Notes |
|----------|--------|-------|-------|
| GET /data | ✅ Complete | ✅ GraphStoreGetIntegrationTest | Supports all selectors, content negotiation |
| HEAD /data | ✅ Complete | ✅ GraphStoreHeadIntegrationTest | Returns ETags, metadata |
| PUT /data | ✅ Complete | ✅ GraphStorePutIntegrationTest | Creates/replaces graphs |
| POST /data | ✅ Complete | ✅ GraphStorePostIntegrationTest | Merges triples into graphs |
| DELETE /data | ✅ Complete | ✅ GraphStoreDeleteIntegrationTest | Removes graphs |
| PATCH /data | ✅ Complete | ✅ GraphStorePatchIntegrationTest | Applies RDF Patch |
| OPTIONS /data | ✅ Complete | ✅ GraphStoreOptionsIntegrationTest | Discovery endpoint |
| POST /version/batch-graphs | ✅ Complete | ✅ BatchGraphsIntegrationTest | Batch operations |

**Features Implemented**:
- ✅ All HTTP methods (GET, HEAD, PUT, POST, DELETE, PATCH, OPTIONS)
- ✅ Selector support (branch, commit, asOf)
- ✅ Content negotiation (Turtle, N-Triples, JSON-LD, RDF/XML, RDF Patch)
- ✅ ETag generation and validation
- ✅ If-Match precondition handling
- ✅ Conflict detection (412 Precondition Failed, 409 Conflict)
- ✅ No-op detection (returns 204 without creating commit)
- ✅ Batch operations (atomic multi-graph updates)
- ✅ Time-travel queries (asOf with inclusive millisecond precision)
- ✅ Error handling (RFC 7807 Problem Details)
- ✅ OpenAPI documentation

**Remaining Work**:
- 🔧 Fix 4 disabled concurrent operation tests (ConcurrentGraphOperationsIntegrationTest)
  - Tests are correct but disabled due to projector timing
  - **Fix**: Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
  - Estimated: 30 minutes

### ✅ Version Control API - COMPLETE

**Implementation Status**: 100% complete

| Endpoint | Status | Tests | Notes |
|----------|--------|-------|-------|
| GET /version/branches | ✅ Complete | ✅ BranchOperationsIT | List branches |
| POST /version/branches | ✅ Complete | ✅ BranchOperationsIT | Create branch |
| GET /version/branches/{name} | ✅ Complete | ✅ BranchOperationsIT | Get branch |
| PUT /version/branches/{name} | ✅ Complete | ✅ BranchOperationsIT | Reset branch |
| GET /version/tags | ✅ Complete | ✅ TagOperationsIT | List tags |
| POST /version/tags | ✅ Complete | ✅ TagOperationsIT | Create tag |
| GET /version/tags/{name} | ✅ Complete | ✅ TagOperationsIT | Get tag |
| GET /version/commits/{id} | ✅ Complete | ✅ CommitOperationsIT | Get commit |
| GET /version/history | ✅ Complete | ✅ HistoryQueryIT | List commits with filters |
| POST /version/merge | ✅ Complete | ✅ MergeIntegrationTest | Three-way merge |
| POST /version/revert | ✅ Complete | ✅ RevertIntegrationTest | Revert commit |
| POST /version/cherry-pick | ✅ Complete | ✅ CherryPickIT | Cherry-pick commit |
| POST /version/squash | ✅ Complete | ✅ SquashIntegrationTest | Squash commits |
| POST /version/rebase | ✅ Complete | ✅ RebaseIntegrationTest | Rebase branch |

**Features Implemented**:
- ✅ Branch management (create, list, get, reset)
- ✅ Tag management (create, list, get) - immutable
- ✅ Commit retrieval with full metadata
- ✅ History queries with filters (branch, since, until, author, limit)
- ✅ RFC 5988 pagination (Link headers)
- ✅ Three-way merge with conflict detection
- ✅ Fast-forward merge policy (allow, only, never)
- ✅ Structured conflict representation (JSON schema)
- ✅ Revert (creates inverse patch commit)
- ✅ Cherry-pick (applies commit to different branch)
- ✅ Squash (combines multiple commits)
- ✅ Rebase (replay commits on new base)

### ❌ SPARQL Protocol - NOT IMPLEMENTED

**Implementation Status**: 5% complete (skeleton only)

| Endpoint | Status | Tests | Notes |
|----------|--------|-------|-------|
| GET /sparql | ❌ Stub (501) | ❌ No tests | Query endpoint not implemented |
| POST /sparql | ❌ Stub (501) | ❌ No tests | Update endpoint not implemented |
| OPTIONS /sparql | ✅ Complete | ✅ Works | Discovery endpoint implemented |

**Current State**:
- ✅ Controller skeleton exists (SparqlController.java)
- ✅ OPTIONS endpoint returns capability headers
- ✅ Selector validation in place (mutual exclusion checks)
- ❌ No query execution engine
- ❌ No update execution engine
- ❌ No dataset materialization at specific commits
- ❌ No integration tests

**Required Implementation**:
1. **SPARQL Query (GET /sparql)**
   - Inject SelectorResolutionService
   - Resolve selectors to target commit
   - Materialize dataset at that commit (DatasetService)
   - Execute query using Apache Jena ARQ
   - Return results (JSON, XML, CSV, TSV)
   - Add ETag header with commit ID

2. **SPARQL Update (POST /sparql)**
   - Parse SPARQL UPDATE
   - Apply update to current branch HEAD
   - Compute RDF diff (before/after comparison)
   - Create CommitCreatedEvent
   - Publish to Kafka
   - Return ETag (new commit ID) and Location header

**Dependencies**:
- DatasetService.materializeCommit(commitId) → Dataset
- SelectorResolutionService.resolve() (already exists)
- Apache Jena ARQ query/update engine
- RDF diff computation (similar to PUT/POST handlers)

**Estimated Effort**: 2-3 days
- Query implementation: 1 day
- Update implementation: 1-2 days
- Integration tests: 0.5 day

### ✅ Test Infrastructure - COMPLETE

**Test Isolation Implementation**: 100% complete (Tasks 01-11)

**Achievements**:
- ✅ Projector disabled by default in integration tests
- ✅ Zero cross-test contamination errors (was: many)
- ✅ 26% faster test execution (50s vs 68s)
- ✅ 100% ReadModelProjector event handler coverage (10/10)
- ✅ Comprehensive testing documentation in CLAUDE.md
- ✅ 819 tests passing, 0 failures

**Test Classes Created**:
- GraphEventProjectorIT: Tests GSP event handlers
- VersionControlProjectorIT: Tests VC operation handlers
- AdvancedOperationsProjectorIT: Tests advanced operation handlers
- ReadModelProjectorIT: Tests basic projector functionality

**Test Organization**:
- API Layer tests (90%): Test HTTP contract without projector
- Projector tests (10%): Test event handlers with projector enabled
- Clear decision table for when to enable projector

**Known Issues**:
- 🔧 ConcurrentGraphOperationsIntegrationTest: 4 tests disabled
  - Tests are correct but need projector enabled
  - Simple fix: Add @TestPropertySource annotation

### ✅ Event Sourcing & CQRS - COMPLETE

**Event Store**: Apache Kafka with per-dataset topics

**Events Implemented** (10 types):
- CommitCreatedEvent
- BranchCreatedEvent, BranchResetEvent, BranchRebasedEvent
- TagCreatedEvent
- CherryPickedEvent
- CommitsSquashedEvent
- RevertCreatedEvent
- MergedEvent
- SnapshotCreatedEvent

**Event Projector** (ReadModelProjector):
- ✅ All 10 handlers implemented and tested
- ✅ Kafka consumer with configurable auto-start
- ✅ Updates CommitRepository, BranchRepository
- ✅ Builds in-memory dataset graphs (DatasetGraphRepository)
- ✅ Idempotent event processing

**Event Publishing** (EventPublisher):
- ✅ Kafka producer with async publishing
- ✅ CompletableFuture-based API
- ✅ JSON serialization of events
- ✅ Partition key by dataset for ordering

## Test Suite Overview

**Total Tests**: 819 passing (0 failures, 9 skipped)

**Test Categories**:
- Unit tests: ~698 tests
- Integration tests: ~121 tests
- Projector tests: 9 tests (3 test classes)

**Skipped Tests**:
- 4 tests in ConcurrentGraphOperationsIntegrationTest (projector timing issue)
- 5 tests in other areas (unknown - need investigation)

**Test Execution Time**: 50.364 seconds

**Code Quality**:
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations

## Roadmap

### Phase 1: Fix Known Issues (Estimated: 1-2 hours)

**Task**: Re-enable Concurrent Operation Tests

**Objective**: Fix 4 disabled tests in ConcurrentGraphOperationsIntegrationTest

**Implementation**:
1. Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")` to class
2. Remove `@Disabled` annotation
3. Run tests and verify they pass
4. Update test documentation

**Files to modify**:
- `src/test/java/org/chucc/vcserver/integration/ConcurrentGraphOperationsIntegrationTest.java`

**Acceptance Criteria**:
- [ ] All 4 tests pass
- [ ] No test timeouts
- [ ] Zero Checkstyle violations
- [ ] Test count increases to 823 (819 + 4)

**Estimated Time**: 30-60 minutes

---

### Phase 2: SPARQL Query Implementation (Estimated: 1-2 days)

**Task**: Implement `/sparql` GET endpoint for query execution

**Objective**: Execute SPARQL queries with version control selector support

**Prerequisites**:
- SelectorResolutionService (already exists)
- DatasetService.materializeCommit() needs implementation
- Apache Jena ARQ integration

**Implementation Steps**:

**Step 1**: Implement DatasetService.materializeCommit() (4-6 hours)
- Create method to rebuild dataset at specific commit
- Traverse commit history from target back to initial commit
- Apply RDF patches in order
- Cache materialized datasets for performance
- Add unit tests

**Step 2**: Integrate Apache Jena ARQ (2-3 hours)
- Add query execution using Jena ARQ
- Parse SPARQL query string
- Execute against materialized dataset
- Format results (JSON, XML, CSV, TSV)
- Add error handling for malformed queries

**Step 3**: Complete SparqlController.querySparqlGet() (2-3 hours)
- Inject SelectorResolutionService and DatasetService
- Resolve selectors to target commit
- Materialize dataset at commit
- Execute query
- Return results with ETag header
- Add comprehensive error handling

**Step 4**: Add Integration Tests (2-3 hours)
- SparqlQueryIntegrationTest
- Test query with branch selector
- Test query with commit selector
- Test query with asOf selector
- Test selector conflict errors
- Test malformed query errors
- Test content negotiation (JSON, XML, CSV, TSV)

**Files to create/modify**:
- `src/main/java/org/chucc/vcserver/service/DatasetService.java` (enhance)
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java` (complete querySparqlGet)
- `src/test/java/org/chucc/vcserver/integration/SparqlQueryIntegrationTest.java` (new)

**Acceptance Criteria**:
- [ ] GET /sparql returns 200 with query results
- [ ] All selectors work (branch, commit, asOf)
- [ ] ETag header contains commit ID
- [ ] Selector conflicts return 400
- [ ] Malformed queries return 400
- [ ] All result formats work (JSON, XML, CSV, TSV)
- [ ] Integration tests pass
- [ ] Zero Checkstyle violations

**Estimated Time**: 1-2 days (8-16 hours)

---

### Phase 3: SPARQL Update Implementation (Estimated: 1-2 days)

**Task**: Implement `/sparql` POST endpoint for update execution

**Objective**: Execute SPARQL updates that create commits on target branches

**Implementation Steps**:

**Step 1**: Create UpdateCommand and Handler (3-4 hours)
- SparqlUpdateCommand (dataset, branch, update, author, message)
- SparqlUpdateCommandHandler
- Parse SPARQL UPDATE string
- Apply update to current branch HEAD dataset
- Compute RDF diff (before/after)
- Detect no-op updates
- Create CommitCreatedEvent
- Publish to Kafka

**Step 2**: Complete SparqlController.executeSparqlPost() (2-3 hours)
- Distinguish query vs update based on Content-Type
- Require SPARQL-VC-Branch, SPARQL-VC-Author, SPARQL-VC-Message headers
- Handle If-Match for optimistic concurrency
- Return ETag (new commit ID) and Location headers
- Return 204 for no-op updates (no commit created)
- Add error handling

**Step 3**: Add Integration Tests (2-3 hours)
- SparqlUpdateIntegrationTest
- Test INSERT DATA
- Test DELETE DATA
- Test DELETE/INSERT WHERE
- Test no-op update returns 204
- Test precondition failures (If-Match)
- Test missing required headers
- Test malformed updates

**Files to create/modify**:
- `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommand.java` (new)
- `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommandHandler.java` (new)
- `src/main/java/org/chucc/vcserver/controller/SparqlController.java` (complete executeSparqlPost)
- `src/test/java/org/chucc/vcserver/integration/SparqlUpdateIntegrationTest.java` (new)

**Acceptance Criteria**:
- [ ] POST /sparql creates commits on target branch
- [ ] ETag header contains new commit ID
- [ ] Location header points to /version/commits/{id}
- [ ] No-op updates return 204 without commit
- [ ] If-Match precondition works (412 on mismatch)
- [ ] Required headers enforced (400 if missing)
- [ ] Malformed updates return 400
- [ ] Integration tests pass
- [ ] Zero Checkstyle violations

**Estimated Time**: 1-2 days (8-16 hours)

---

### Phase 4: GSP Polish (Estimated: 2-3 days)

**Task**: Complete remaining GSP tasks from original plan

**Tasks**:
- Task 17: Performance optimization (caching, metrics)
- Task 18: Security validation review
- Task 19: Error handling polish (ensure all RFC 7807 codes)
- Task 20: OpenAPI documentation completion
- Task 21: Final integration testing

**See**: `.tasks/gsp/` for detailed task descriptions

**Estimated Time**: 2-3 days

---

### Phase 5: Conformance Testing (Estimated: 1-2 days)

**Task**: Create conformance test suite per original plan T19

**Objective**: Tiny CI suite that hits normative behaviors

**Implementation**:
1. Create `.tasks/conformance/` directory
2. Write Level 1 conformance tests:
   - UUIDv7 commit IDs
   - RDF Patch acceptance/production
   - Strong ETags
   - asOf inclusive behavior
   - Selector conflict errors
3. Write Level 2 conformance tests:
   - Merge algorithm
   - Conflict representation
   - Fast-forward policy
4. Add curl scripts in test README
5. Add to CI pipeline

**Acceptance Criteria**:
- [ ] Level 1 tests pass (basic version control)
- [ ] Level 2 tests pass (advanced operations)
- [ ] Test README contains curl examples
- [ ] CI runs conformance suite

**Estimated Time**: 1-2 days

---

### Phase 6: Observability (Optional - Estimated: 1-2 days)

**Task**: Add metrics and monitoring per original plan T20

**Implementation**:
1. Add Micrometer metrics:
   - RDF patch apply time
   - Event projector lag
   - Query execution time
   - Commit creation rate
2. Optional: Periodic snapshots to speed restart
3. Create Grafana dashboard JSON
4. Add snapshot recovery integration test

**Estimated Time**: 1-2 days (if needed)

## Summary of Work Remaining

| Phase | Description | Estimated Time | Priority |
|-------|-------------|----------------|----------|
| Phase 1 | Fix concurrent operation tests | 1-2 hours | High |
| Phase 2 | SPARQL Query implementation | 1-2 days | High |
| Phase 3 | SPARQL Update implementation | 1-2 days | High |
| Phase 4 | GSP polish (Tasks 17-21) | 2-3 days | Medium |
| Phase 5 | Conformance testing | 1-2 days | Medium |
| Phase 6 | Observability (optional) | 1-2 days | Low |
| **Total** | | **7-12 days** | |

**Critical Path**: Phases 1, 2, 3 (implement SPARQL endpoints)
**Nice-to-Have**: Phases 4, 5, 6 (polish and conformance)

## Recommendations

### Immediate Next Steps (Start Here)

1. **Fix Concurrent Tests** (Phase 1)
   - Quick win: 30-60 minutes
   - Increases test count to 823
   - Validates projector test isolation pattern

2. **Implement SPARQL Query** (Phase 2)
   - Most critical missing feature
   - Enables read operations via SPARQL
   - Foundation for SPARQL Update

3. **Implement SPARQL Update** (Phase 3)
   - Completes write operations
   - Makes protocol fully functional
   - Enables SPARQL-based workflows

### Alternative Approach: Complete GSP First

If SPARQL is not immediately critical:
1. Complete Phase 4 (GSP polish)
2. Complete Phase 5 (conformance testing)
3. Release GSP-only version (read/write via Graph Store Protocol)
4. Add SPARQL later as enhancement

This approach delivers a working system faster (3-4 days vs 7-12 days).

### Dependencies to Consider

- **Apache Jena ARQ**: Already in dependencies, ready to use
- **Dataset Materialization**: Core capability needed for SPARQL query
- **RDF Diff**: Already implemented for GSP, reuse for SPARQL Update
- **Event Publishing**: Already working, just needs new UpdateCommand

### Testing Strategy

- Continue TDD approach: Write tests before implementation
- Use API Layer tests for HTTP contract validation
- Use Projector tests only when testing event processing
- Follow CLAUDE.md testing guidelines
- Run `mvn -q checkstyle:check spotbugs:check` frequently

### Code Quality Maintenance

- Continue zero-violation policy (Checkstyle, SpotBugs, PMD)
- Use `-q` flag for token efficiency
- Run incremental tests during development
- Run full `mvn clean install` at phase completions

## Conclusion

The CHUCC Server has a solid foundation with complete Graph Store Protocol implementation, comprehensive version control operations, and robust test infrastructure. The main gap is SPARQL Query/Update endpoint implementation.

**Recommended Path Forward**:
1. Fix concurrent tests (quick win)
2. Implement SPARQL Query (1-2 days)
3. Implement SPARQL Update (1-2 days)
4. Polish and conformance testing (2-4 days)

**Total Effort**: 7-12 days to complete implementation

**Alternative**: Complete GSP polish first (3-4 days), release GSP-only version, add SPARQL later.

The project is well-positioned for completion with clear tasks and minimal remaining work.
