# SHACL Validation Protocol - Complete Task List

## Phase 1: Core Validation (MVP) - 8-10 hours

### ✅ Task 1: Core Infrastructure (3-4 hours)
**File:** `01-core-infrastructure.md`
**Status:** Ready to start

**Deliverables:**
- DTOs (ValidationRequest, ValidationResponse, GraphReference, DataReference, ValidationOptions, ResultsConfig)
- Exceptions (ShaclValidationException, InvalidShapesException, InvalidGraphReferenceException)
- Configuration (ShaclValidationProperties)
- Controller skeleton (ShaclValidationController)

### ✅ Task 2: Basic Inline Validation (2-3 hours)
**File:** `02-basic-inline-validation.md`
**Status:** Blocked by Task 1

**Deliverables:**
- ShaclValidationEngine (Apache Jena SHACL integration)
- ShaclValidationService (orchestration)
- Content negotiation (Turtle, JSON-LD, RDF/XML, N-Triples)
- Integration tests (5 tests)

**Fuseki Compatibility:** ✅ Yes

### ✅ Task 3: Local Graph Reference Resolution (3-4 hours)
**File:** `03-local-graph-references.md`
**Status:** Blocked by Task 2

**Deliverables:**
- GraphReferenceResolver service
- Local shapes graph resolution
- Cross-dataset validation
- GraphNotFoundException
- Integration tests (5 tests) + Unit tests (7 tests)

**Your Primary Use Case:** ✅ Implemented here

---

## Phase 2: Cross-Dataset Support - 4-6 hours

### ⏳ Task 4: Cross-Dataset Validation (2-3 hours)
**File:** `04-cross-dataset-validation.md` (Not yet created)
**Status:** Not started

**Note:** Already working from Task 3! This task would add polish and additional tests.

**Deliverables:**
- Additional cross-dataset test scenarios
- Performance optimization for cross-dataset access
- Documentation and examples

### ⏳ Task 5: Validation Modes (2-3 hours)
**File:** `05-validation-modes.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- Separate graph validation (default - already working)
- Merged graph validation (combine multiple graphs)
- Dataset-level validation (quad structure preserved)
- Integration tests for each mode

---

## Phase 3: Result Persistence - 6-8 hours

### ✅ Task 6: Result Storage with CQRS (4-5 hours)
**File:** `06-result-storage-cqrs.md`
**Status:** Blocked by Tasks 1-3

**Deliverables:**
- StoreValidationResultCommand
- ValidationResultStoredEvent
- StoreValidationResultCommandHandler
- ValidationResultProjector
- GraphConflictException
- Integration tests with projector enabled (3 tests)

**Your Critical Requirement:** ✅ Store validation results for historical analysis

### ⏳ Task 7: Version Control Integration for Results (2-3 hours)
**File:** `07-vc-integration-results.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- SPARQL-VC-Author header support
- Custom commit messages
- Branch integration (store on specific branch)
- ETag and Location headers

---

## Phase 4: Version Control Integration - 3-4 hours

### ⏳ Task 8: Selectors for Shapes and Data (3-4 hours)
**File:** `08-vc-selectors.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- Branch selector (shapes.branch, data.branch)
- Commit selector (shapes.commit, data.commit)
- AsOf selector (shapes.asOf, data.asOf)
- Integration with DatasetService materialization
- Historical validation tests

**Your Historical Validation Use Case:** ✅ Implemented here

---

## Phase 5: Remote Endpoint Support - 5-7 hours

### ⏳ Task 9: Remote Endpoint Client (3-4 hours)
**File:** `09-remote-endpoint-client.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- RemoteEndpointClient service
- SPARQL CONSTRUCT queries for graph fetching
- Timeout and retry logic
- Caching (Caffeine)
- Integration tests for remote shapes/data

### ⏳ Task 10: Security Controls (2-3 hours)
**File:** `10-security-controls.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- SSRF prevention (block private IPs)
- URL validation (scheme whitelist)
- Response size limits
- TLS certificate verification
- Security integration tests

---

## Phase 6: Advanced Features (Optional) - 6-8 hours

### ⏳ Task 11: Performance Optimization (3-4 hours)
**File:** `11-performance-optimization.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- Shapes graph caching
- Parallel validation (multiple graphs)
- Incremental validation (RDFPatch-based)
- Performance benchmarks

### ⏳ Task 12: Batch Validation (3-4 hours)
**File:** `12-batch-validation.md` (Not yet created)
**Status:** Not started

**Deliverables:**
- Batch validation request/response DTOs
- Multiple dataset validation in one request
- Batch validation endpoint
- Integration tests

---

## Summary

**Total Tasks:** 12
**Completed:** 0
**Ready to Start:** 4 (Tasks 1, 2, 3, 6 have detailed specs)
**Not Yet Detailed:** 8 (Tasks 4, 5, 7-12)

**Total Estimated Time:** 32-43 hours
**Required for MVP (Phase 1-3):** 18-24 hours
**Optional (Phase 4-6):** 14-19 hours

---

## Recommended Start Order

1. **Task 1** → Foundation (3-4 hours)
2. **Task 2** → First working endpoint (2-3 hours)
3. **Task 3** → Your primary use case (3-4 hours)
4. **Task 6** → Your critical use case (4-5 hours)

**After 4 tasks (12-16 hours):** You'll have a fully functional SHACL validation system with:
- ✅ Inline shapes (Fuseki-compatible)
- ✅ Local shapes from any dataset
- ✅ Cross-dataset validation
- ✅ Result storage for historical analysis

The remaining tasks (4, 5, 7-12) add:
- Additional validation modes
- Version control selectors
- Remote endpoint support
- Performance optimization
- Advanced features

---

## Creating Remaining Task Files

If you want to implement phases 4-6, I can create detailed task files for Tasks 4, 5, 7-12 similar to the existing ones.

Just ask: "Create remaining SHACL task files" and I'll generate them with the same level of detail.

---

## Progress Tracking

Update this file as tasks are completed:

- ✅ Task 1: Core Infrastructure - COMPLETED (YYYY-MM-DD)
- ✅ Task 2: Basic Inline Validation - COMPLETED (YYYY-MM-DD)
- ✅ Task 3: Local Graph References - COMPLETED (YYYY-MM-DD)
- ... and so on

---

**Last Updated:** 2025-11-14
**Created By:** Claude (AI Assistant)
**Status:** Ready for implementation
