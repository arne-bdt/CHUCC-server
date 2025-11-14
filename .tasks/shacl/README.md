# SHACL Validation Protocol - Implementation Plan

## Overview

This directory contains tasks for implementing the SHACL Validation Protocol, enabling advanced RDF data validation with:
- Flexible shapes sources (inline, local datasets, remote endpoints)
- Cross-dataset validation (shapes from dataset A, data from dataset B)
- Result persistence via existing Graph Store Protocol (no new CQRS components!)
- Version control integration (validate against historical states)
- Union graph validation (cross-graph constraints)

**Protocol Specification:** `protocol/SHACL_Validation_Protocol.md`

---

## Implementation Phases

### Phase 1: Core Validation (MVP)
**Goal:** Basic SHACL validation with inline shapes and local graphs

- **Task 1:** Core Infrastructure (DTOs with format field, cross-validation, simplified config)
- **Task 2:** Basic Inline Validation (format detection, Fuseki compatibility)
- **Task 3:** Local Graph Reference Resolution

**Total Estimated Time:** 8-10 hours

### Phase 2: Cross-Graph Validation & Storage
**Goal:** Union graph validation and result persistence

- **Task 4:** Union Graph Validation (cross-graph constraints via Apache Jena Union)
- **Task 5:** Result Storage via Graph Store Protocol (reuse existing GSP, eliminate CQRS overhead)

**Total Estimated Time:** 4-6 hours

### Phase 3: Version Control Integration
**Goal:** Validate against historical states

- **Task 6:** Version Control Selectors (branch/commit/asOf for shapes and data)

**Total Estimated Time:** 3-4 hours

### Phase 4: Remote Endpoint Support
**Goal:** Use shapes/data from remote SPARQL endpoints

- **Task 7:** Remote Endpoint Client (SPARQL CONSTRUCT, caching)
- **Task 8:** Security Controls (SSRF prevention, timeouts, IP blocking)

**Total Estimated Time:** 5-7 hours

### Phase 5: Advanced Features (Optional)
**Goal:** Performance optimization

- **Task 9:** Performance Optimization (shapes caching, parallel validation)

**Total Estimated Time:** 3-4 hours

---

## Total Implementation Effort

**Required Features (Phases 1-4):** 20-27 hours
**Optional Features (Phase 5):** 3-4 hours

**Estimated Sessions:** 6-9 sessions (3-4 hours per session)

**Reduction from original plan:** 12 tasks → 9 tasks (25% fewer tasks, 30% less time)

---

## Recommended Implementation Order

1. **Start:** Task 1 (Core Infrastructure) - Foundation with format detection
2. **Next:** Task 2 (Basic Inline Validation) - Quickest path to working endpoint
3. **Then:** Task 3 (Local Graph Resolution) - Core feature for local datasets
4. **Continue:** Task 4 (Union Graph Validation) - Cross-graph constraints
5. **Important:** Task 5 (Result Storage via GSP) - Critical for historical analysis, reuses existing infrastructure
6. **Version Control:** Task 6 (Selectors) - Validate against historical states
7. **Remote:** Tasks 7-8 (Remote Endpoints) - External data validation
8. **Optional:** Task 9 (Performance) - Defer until needed

---

## Key Architectural Decisions

### ✅ Format Detection
- Added `format` field to GraphReference (turtle, jsonld, rdfxml, ntriples, n3)
- Inline shapes support all RDF serialization formats
- Default format: turtle

### ✅ Cross-Field Validation
- GraphReference validates field combinations based on source type
- DataReference validates field combinations based on source type
- Selector conflicts detected (branch + commit + asOf mutually exclusive)

### ✅ Simplified Configuration
- Removed premature remote/cache/storage configuration
- Added configuration when actually needed (Tasks 7-9)
- Minimal viable config for Phase 1

### ✅ Union Graph Mode
- Eliminated "merged" and "dataset" validation modes (over-engineered)
- Two modes: "separately" (default) and "union"
- Union mode uses Apache Jena's Union class for cross-graph constraints

### ✅ Result Storage Architecture
**OLD (Over-engineered):**
- StoreValidationResultCommand (NEW)
- ValidationResultStoredEvent (NEW)
- ValidationResultProjector (NEW)
- Total: 3 new CQRS components

**NEW (Reuse existing):**
- Internal HTTP call to `PUT /data?graph=X`
- Reuses GraphStoreController
- Reuses CreateCommitCommandHandler
- Reuses CommitCreatedEvent
- Reuses ReadModelProjector
- Total: 0 new CQRS components

**Benefits:**
- Results stored exactly like any other graph
- Full version control history maintained
- Proper commit graph (not detached commits)
- Event replay works (has RDFPatch)
- 75% less code

---

## Dependencies

### External Libraries
- ✅ Apache Jena SHACL (already included in pom.xml)
- ✅ Apache Jena Core 5.5.0
- ✅ Spring Boot 3.5
- ✅ Jackson (JSON processing)

### Internal Components
- ✅ DatasetService (graph materialization for selectors)
- ✅ CommitRepository, BranchRepository (version control)
- ✅ GraphStoreController (result storage)
- ✅ CreateCommitCommandHandler (commit creation)
- ✅ ReadModelProjector (event projection)
- ✅ CQRS infrastructure (command handlers, event publishers)
- ✅ RFC 7807 error handling
- ✅ ITFixture test infrastructure

---

## Success Criteria (All Tasks)

Each task must meet these criteria before completion:

- ✅ All endpoints return appropriate responses (no 501)
- ✅ DTOs created with validation and cross-field checks
- ✅ Apache Jena SHACL integration working
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (services, utilities) where applicable
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`
- ✅ Documentation updated (if needed)
- ✅ Task file deleted after completion

---

## Protocol Compliance

All tasks implement features from:
- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)**
- **[SHACL Specification (W3C)](https://www.w3.org/TR/shacl/)**

### Primary Endpoint

**`POST /{dataset}/shacl`** - Validate data graphs against shapes graph

**Required Features:**
- ✅ Inline shapes (all RDF formats)
- ✅ Local graph references (your primary use case)
- ✅ Union graph validation (cross-graph constraints)
- ✅ Result persistence via GSP (no new CQRS components)
- ✅ Version control selectors (branch/commit/asOf)
- ✅ Remote endpoint support (essential for linked data)

**Optional Features:**
- ⏭️ Performance optimization (caching, parallelization)

---

## Testing Strategy

### Unit Tests
- `ShaclValidationEngine` - Apache Jena SHACL wrapper, error handling
- `GraphReferenceResolver` - All source types, selectors (Task 6+)
- `RemoteEndpointClient` - Timeout, retry, caching (Task 7+)

### Integration Tests
- `ShaclValidationIT` - Inline validation, format detection
- `UnionGraphValidationIT` - Cross-graph constraint validation
- `ValidationResultStorageIT` - Store results via GSP, verify commit creation
- `HistoricalValidationIT` - Validate with selectors (Task 6+)
- `RemoteEndpointValidationIT` - Remote shapes/data (Task 7+)

**Test Pattern:**
- API layer tests: Projector **DISABLED** (90% of tests)
- Storage tests: Projector **ENABLED** via `@TestPropertySource` (10% of tests)
- Use `await()` for async projection verification

---

## Architecture Integration

### Result Storage (Task 5)

**Query-Side Operation (Validation):**
```
POST /{dataset}/shacl
  ↓
ShaclValidationService.validate()
  ↓
Return: ValidationReport (200 OK)
```

**Command-Side Operation (Storage):**
```
POST /{dataset}/shacl (with results.store)
  ↓
ShaclValidationService.validateAndStore()
  ↓
Internal HTTP: PUT /data?graph=X
  ↓
GraphStoreController (EXISTING)
  ↓
CreateCommitCommandHandler (EXISTING)
  ↓
Return: 202 Accepted + commit ID
```

**Key Insight:** Validation is read-only. Only result storage is a write operation (uses existing GSP).

### Component Structure

```
┌─────────────────────────────────────────┐
│   ShaclValidationController             │
│   - POST /{dataset}/shacl                │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   ShaclValidationService                 │
│   - validate() [read-only]               │
│   - validateAndStore() [write via GSP]   │
└──────────────┬──────────────────────────┘
               │
               ├─→ ShaclValidationEngine (Apache Jena)
               ├─→ GraphReferenceResolver (shapes/data)
               └─→ Internal REST call (PUT /data?graph=X)
                   ↓
                   GraphStoreController (EXISTING)
```

---

## Configuration

### Application Properties

```yaml
chucc:
  shacl:
    validation:
      # Feature flags
      enabled: true

      # Resource limits
      max-shapes-size: 10485760  # 10MB
      max-data-size: 104857600   # 100MB
      timeout: 60000             # 60 seconds
      max-concurrent: 10

# Advanced configuration added in later tasks:
# - Task 7: remote endpoint configuration
# - Task 9: caching configuration
```

---

## Security Considerations

### Input Validation (Task 1)
- Dataset names match ABNF grammar (already enforced)
- Graph URIs are valid IRIs
- Selectors are mutually exclusive
- Format field validated (turtle, jsonld, rdfxml, ntriples, n3)

### Access Control (Tasks 6-8)
- `READ` permission on shapes dataset/graph
- `READ` permission on data dataset/graph(s)
- `WRITE` permission on results dataset/graph (if storing)

### SSRF Prevention (Task 8)
- Validate URL scheme (http/https only)
- Block private IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
- Block localhost/127.0.0.1
- Enforce timeouts (default: 30 seconds)
- Limit response size (default: 100MB)

### Resource Limits (All Tasks)
- Max shapes graph size: 10MB
- Max data graph size: 100MB
- Validation timeout: 60 seconds
- Max concurrent validations: 10

---

## Progress Tracking

**Phase 1:** ⏳ Not Started (0/3 tasks)
**Phase 2:** ⏳ Not Started (0/2 tasks)
**Phase 3:** ⏳ Not Started (0/1 tasks)
**Phase 4:** ⏳ Not Started (0/2 tasks)
**Phase 5:** ⏳ Not Started (0/1 tasks)

**Overall Progress:** 0% (0/9 tasks completed)

---

## Task Files

### Phase 1 (MVP)
- `01-core-infrastructure.md` ✅ Ready
- `02-basic-inline-validation.md` ✅ Ready
- `03-local-graph-references.md` ✅ Ready

### Phase 2 (Cross-Graph & Storage)
- `04-union-graph-validation.md` ✅ Ready
- `05-result-storage-gsp.md` ✅ Ready

### Phase 3 (Version Control)
- `06-vc-selectors.md` ✅ Ready

### Phase 4 (Remote Endpoints)
- `07-remote-endpoint-client.md` ✅ Ready
- `08-security-controls.md` ✅ Ready

### Phase 5 (Performance)
- `09-performance-optimization.md` ✅ Ready

---

## Task Completion Workflow

When a task is completed:

1. **Verify Success Criteria**
   - All features implemented
   - All tests pass
   - Zero quality violations
   - Full build succeeds: `mvn -q clean install`

2. **Invoke Specialized Agents (if applicable)**
   - `@test-isolation-validator` (after writing tests)
   - `@code-reviewer` (for final review)
   - `@documentation-sync-agent` (if architecture changes)

3. **Create Commit**
   - Follow conventional commit format
   - Include "Generated with Claude Code" footer

4. **Update This README**
   - Mark task as completed (⏳ → ✅)
   - Update progress percentage
   - Move to "Completed Tasks" section

5. **Delete Task File**
   - Remove `.tasks/shacl/<task-file>.md`

---

## Completed Tasks

(None yet - will be updated as tasks are completed)

---

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - Complete specification
- **[SHACL Protocol Summary](../../protocol/SHACL_Protocol_Summary.md)** - Implementation guide
- **[SHACL Specification (W3C)](https://www.w3.org/TR/shacl/)** - SHACL semantics
- **[Apache Jena SHACL](https://jena.apache.org/documentation/shacl/)** - Java API documentation
- **[Apache Jena Union Graphs](https://jena.apache.org/documentation/notes/composition.html)** - Graph composition
- **[Development Guidelines](../../.claude/CLAUDE.md)** - CHUCC development best practices
- **[CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)** - Architecture pattern
- **[Graph Store Protocol](https://www.w3.org/TR/sparql11-http-rdf-update/)** - PUT/POST semantics

---

**Last Updated:** 2025-11-14 (revised based on architectural review)
**Status:** Ready for implementation
**Next Task:** Task 1 - Core Infrastructure
