# SHACL Validation Protocol - Complete Task List

## Phase 1: Core Validation (MVP) - 8-10 hours

### ✅ Task 1: Core Infrastructure (3-4 hours)
**File:** `01-core-infrastructure.md`
**Status:** Ready to start

**Deliverables:**
- DTOs with format field (GraphReference, DataReference, ValidationOptions, ResultsConfig, ValidationRequest, ValidationResponse)
- Cross-field validation methods in GraphReference and DataReference
- Exceptions (ShaclValidationException, InvalidShapesException, InvalidGraphReferenceException)
- Simplified configuration (ShaclValidationProperties - no premature optimization)
- Controller skeleton (ShaclValidationController)

**Key Changes from Original:**
- ✅ Added `format` field to GraphReference (turtle, jsonld, rdfxml, ntriples, n3)
- ✅ Added cross-field validation in compact constructors
- ✅ Removed premature remote/cache/storage config
- ✅ Simplified ValidationOptions to "separately" | "union" only

### ✅ Task 2: Basic Inline Validation (2-3 hours)
**File:** `02-basic-inline-validation.md`
**Status:** Blocked by Task 1

**Deliverables:**
- ShaclValidationEngine (Apache Jena SHACL integration)
- ShaclValidationService (orchestration with format detection)
- Content negotiation (Turtle, JSON-LD, RDF/XML, N-Triples)
- Integration tests (6 tests)

**Key Changes from Original:**
- ✅ Added format detection from `shapes.format` field
- ✅ Added `parseLang()` helper method

**Fuseki Compatibility:** ✅ Yes

### ✅ Task 3: Local Graph Reference Resolution (3-4 hours)
**File:** `03-local-graph-references.md`
**Status:** Blocked by Task 2

**Deliverables:**
- GraphReferenceResolver service (or integrated into ShaclValidationService)
- Local shapes graph resolution
- Cross-dataset validation
- GraphNotFoundException handling
- Integration tests (5 tests) + Unit tests (7 tests)

**Your Primary Use Case:** ✅ Implemented here

---

## Phase 2: Cross-Graph Validation & Storage - 4-6 hours

### ✅ Task 4: Union Graph Validation (2-3 hours)
**File:** `04-union-graph-validation.md`
**Status:** Blocked by Tasks 1-3

**Deliverables:**
- Union graph creation from multiple graphs via Apache Jena Union class
- Cross-graph constraint validation
- ValidationOptions with "union" mode
- Integration tests (5 tests)

**Key Changes from Original:**
- ✅ Eliminated "merged" and "dataset" validation modes (over-engineered)
- ✅ Two modes only: "separately" (default) and "union"
- ✅ Cross-graph referential integrity validation

### ✅ Task 5: Result Storage via Graph Store Protocol (2-3 hours)
**File:** `05-result-storage-gsp.md`
**Status:** Blocked by Tasks 1-4

**Deliverables:**
- InternalHttpClientConfig (RestTemplate for internal calls)
- ShaclValidationService.validateAndStore() method
- Internal HTTP call to `PUT /data?graph=X`
- ETag extraction for commit ID
- Integration tests with projector enabled (3 tests)

**ARCHITECTURAL BREAKTHROUGH:**
- ❌ No StoreValidationResultCommand (reuse CreateCommitCommand)
- ❌ No ValidationResultStoredEvent (reuse CommitCreatedEvent)
- ❌ No ValidationResultProjector (reuse ReadModelProjector)
- ✅ 0 new CQRS components (75% code reduction)

**Your Critical Requirement:** ✅ Store validation results for historical analysis

---

## Phase 3: Version Control Integration - 3-4 hours

### ✅ Task 6: Version Control Selectors (3-4 hours)
**File:** `06-vc-selectors.md`
**Status:** Ready to start

**Deliverables:**
- Branch selector (shapes.branch, data.branch)
- Commit selector (shapes.commit, data.commit)
- AsOf selector (shapes.asOf, data.asOf)
- Integration with DatasetService.materializeCommit()
- Historical validation tests

**Your Historical Validation Use Case:** ✅ Implemented here

---

## Phase 4: Remote Endpoint Support - 5-7 hours

### ✅ Task 7: Remote Endpoint Client (3-4 hours)
**File:** `07-remote-endpoint-client.md`
**Status:** Ready to start

**Deliverables:**
- RemoteEndpointClient service
- SPARQL CONSTRUCT queries for graph fetching
- Timeout and retry logic
- Caching (Caffeine)
- Integration tests for remote shapes/data

**Essential for Linked Data:** ✅ Yes

### ✅ Task 8: Security Controls (2-3 hours)
**File:** `08-security-controls.md`
**Status:** Ready to start

**Deliverables:**
- SSRF prevention (block private IPs: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
- URL validation (scheme whitelist: http, https)
- Response size limits
- TLS certificate verification
- Security integration tests

---

## Phase 5: Advanced Features (Optional) - 3-4 hours

### ✅ Task 9: Performance Optimization (3-4 hours)
**File:** `09-performance-optimization.md`
**Status:** Ready to start

**Deliverables:**
- Shapes graph caching (avoid re-parsing)
- Parallel validation (multiple datasets in batch)
- Metrics and monitoring

---

## Summary

**Total Tasks:** 9 (down from original 12)
**Completed:** 0
**Ready to Start:** 9 (All tasks have detailed specs)
**Not Yet Detailed:** 0

**Total Estimated Time:** 23-31 hours
**Required for MVP (Phases 1-4):** 20-27 hours
**Optional (Phase 5):** 3-4 hours

**Reduction from Original Plan:**
- Tasks: 12 → 9 (25% reduction)
- Time: 32-43 hours → 23-31 hours (30% reduction)
- New CQRS components: 3 → 0 (100% reduction!)

---

## Recommended Start Order

1. **Task 1** → Foundation with format field and cross-validation (3-4 hours)
2. **Task 2** → First working endpoint with format detection (2-3 hours)
3. **Task 3** → Local graph references (3-4 hours)
4. **Task 4** → Union graph validation (2-3 hours)
5. **Task 5** → Result storage via GSP (2-3 hours)

**After 5 tasks (12-17 hours):** You'll have a fully functional SHACL validation system with:
- ✅ Inline shapes (all RDF formats)
- ✅ Local shapes from any dataset
- ✅ Cross-dataset validation
- ✅ Union graph validation (cross-graph constraints)
- ✅ Result storage for historical analysis (no new CQRS components!)

The remaining tasks (6-9) add:
- Version control selectors (historical validation)
- Remote endpoint support (essential for linked data)
- Performance optimization

---

## Key Architectural Improvements

### 1. Format Detection
**Before:** No format field, assumed Turtle
**After:** `format` field supports turtle, jsonld, rdfxml, ntriples, n3

### 2. Cross-Field Validation
**Before:** Only annotation-based validation
**After:** Compact constructor validates field combinations based on source type

### 3. Configuration
**Before:** Premature optimization (remote, cache, storage config in Task 1)
**After:** Minimal config (Task 1), add advanced config when needed (Tasks 7-9)

### 4. Validation Modes
**Before:** Three modes (separately, merged, dataset)
**After:** Two modes (separately, union) - simpler and sufficient

### 5. Result Storage
**Before:** 3 new CQRS components (command, event, projector)
**After:** 0 new components (reuse existing Graph Store Protocol)

**Impact:**
- 75% less code
- Results stored exactly like any other graph
- Proper commit graph (not detached)
- Event replay works (has RDFPatch)

---

## Progress Tracking

Update this file as tasks are completed:

- ⏳ Task 1: Core Infrastructure - NOT STARTED
- ⏳ Task 2: Basic Inline Validation - NOT STARTED
- ⏳ Task 3: Local Graph References - NOT STARTED
- ⏳ Task 4: Union Graph Validation - NOT STARTED
- ⏳ Task 5: Result Storage via GSP - NOT STARTED
- ⏳ Task 6: Version Control Selectors - NOT STARTED
- ⏳ Task 7: Remote Endpoint Client - NOT STARTED
- ⏳ Task 8: Security Controls - NOT STARTED
- ⏳ Task 9: Performance Optimization - NOT STARTED

**Completion Format:**
- ✅ Task X: Description - COMPLETED (YYYY-MM-DD)

---

## Creating Remaining Task Files

If you want to implement phases 3-5, create detailed task files for Tasks 6-9 similar to the existing ones.

**Task 6 (Version Control Selectors):**
- Reuse existing DatasetService.materializeCommit() infrastructure
- Add selector resolution logic to ShaclValidationService
- Integration tests with historical validation

**Task 7 (Remote Endpoint Client):**
- RestTemplate configuration for remote calls
- SPARQL CONSTRUCT query building
- Caffeine cache integration
- Integration tests with mock remote endpoint

**Task 8 (Security Controls):**
- IP range blocking (private IPs, localhost)
- URL scheme validation (http/https only)
- Response size limits
- Security integration tests

**Task 9 (Performance Optimization):**
- Shapes graph caching (parse once, reuse)
- Parallel validation (CompletableFuture)
- Metrics (Micrometer)

---

**Last Updated:** 2025-11-14 (revised based on architectural review)
**Created By:** Claude (AI Assistant)
**Status:** Ready for implementation
