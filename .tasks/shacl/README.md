# SHACL Validation Protocol - Implementation Plan

## Overview

This directory contains tasks for implementing the SHACL Validation Protocol, enabling advanced RDF data validation with:
- Flexible shapes sources (local datasets, remote endpoints, inline)
- Cross-dataset validation (shapes from dataset A, data from dataset B)
- Result persistence (store validation reports for historical analysis)
- Version control integration (validate against historical states)
- Multiple validation modes (separate, merged, dataset-level)

**Protocol Specification:** `protocol/SHACL_Validation_Protocol.md`

---

## Implementation Phases

### Phase 1: Core Validation (MVP)
**Goal:** Basic SHACL validation with inline shapes (Fuseki-compatible)

- **Task 1:** Core Infrastructure (DTOs, exceptions, configuration)
- **Task 2:** Basic Inline Validation (Fuseki compatibility)
- **Task 3:** Local Graph Reference Resolution

**Total Estimated Time:** 8-10 hours

### Phase 2: Cross-Dataset Support
**Goal:** Validate data across multiple datasets

- **Task 4:** Cross-Dataset Validation
- **Task 5:** Validation Modes (separate, merged, dataset)

**Total Estimated Time:** 4-6 hours

### Phase 3: Result Persistence
**Goal:** Store validation reports for historical analysis

- **Task 6:** Result Storage (CQRS implementation)
- **Task 7:** Version Control Integration for Results

**Total Estimated Time:** 6-8 hours

### Phase 4: Version Control Integration
**Goal:** Validate against historical states

- **Task 8:** Selectors for Shapes and Data (branch/commit/asOf)

**Total Estimated Time:** 3-4 hours

### Phase 5: Remote Endpoint Support
**Goal:** Use shapes/data from remote SPARQL endpoints

- **Task 9:** Remote Endpoint Client
- **Task 10:** Security Controls (SSRF prevention, timeouts)

**Total Estimated Time:** 5-7 hours

### Phase 6: Advanced Features (Optional)
**Goal:** Performance optimization and advanced workflows

- **Task 11:** Performance Optimization (caching, parallelization)
- **Task 12:** Batch Validation (multiple datasets in one request)

**Total Estimated Time:** 6-8 hours

---

## Total Implementation Effort

**Required Features (Phases 1-5):** 26-35 hours
**Optional Features (Phase 6):** 6-8 hours

**Estimated Sessions:** 8-12 sessions (3-4 hours per session)

---

## Recommended Implementation Order

1. **Start:** Task 1 (Core Infrastructure) - Foundation for all other tasks
2. **Next:** Task 2 (Basic Inline Validation) - Quickest path to working endpoint
3. **Then:** Task 3 (Local Graph Resolution) - Core feature for your use case
4. **Continue:** Tasks 4-5 (Cross-Dataset) - Advanced feature you requested
5. **Important:** Tasks 6-7 (Result Persistence) - Critical for your historical analysis use case
6. **Version Control:** Task 8 (Selectors) - Integrate with existing VC infrastructure
7. **Remote:** Tasks 9-10 (Remote Endpoints) - External data validation
8. **Optional:** Tasks 11-12 (Performance) - Defer until needed

---

## Dependencies

### External Libraries
- ✅ Apache Jena SHACL (already included in pom.xml)
- ✅ Apache Jena Core 5.5.0
- ✅ Spring Boot 3.5
- ✅ Jackson (JSON processing)

### Internal Components
- ✅ DatasetService (graph materialization)
- ✅ CommitRepository, BranchRepository (version control)
- ✅ CQRS infrastructure (command handlers, event publishers)
- ✅ RFC 7807 error handling
- ✅ ITFixture test infrastructure

---

## Success Criteria (All Tasks)

Each task must meet these criteria before completion:

- ✅ All endpoints return appropriate responses (no 501)
- ✅ DTOs created with validation
- ✅ Apache Jena SHACL integration working
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (services, utilities)
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
- ✅ Inline shapes (Fuseki compatibility)
- ✅ Local graph references (your primary use case)
- ✅ Cross-dataset validation
- ✅ Result persistence
- ✅ Version control selectors
- ✅ Multiple validation modes

**Optional Features:**
- ⏭️ Remote endpoint support
- ⏭️ Batch validation
- ⏭️ Webhook notifications

---

## Testing Strategy

### Unit Tests
- `ShaclValidationService` - All validation modes, error cases
- `GraphReferenceResolver` - All source types, selectors
- `ValidationResultStorageService` - Commit creation, overwrite logic
- `RemoteEndpointClient` - Timeout, retry, caching

### Integration Tests
- `ShaclValidationIT` - End-to-end validation workflows
- `CrossDatasetValidationIT` - Shapes from dataset A, data from dataset B
- `ValidationResultPersistenceIT` - Store results, version control commits
- `HistoricalValidationIT` - Validate with selectors
- `RemoteEndpointValidationIT` - Remote shapes/data

**Test Pattern:**
- API layer tests: Projector **DISABLED** (90% of tests)
- Projector tests: Projector **ENABLED** via `@TestPropertySource` (10% of tests)
- Use `await()` for async projection verification

---

## Architecture Integration

### CQRS Pattern (Result Storage Only)

**Command Side (write operations):**
```
POST /{dataset}/shacl (with results.store)
    ↓
ShaclValidationCommandHandler
    ↓
Publish: ValidationCompletedEvent
    ↓
Return: 202 Accepted
```

**Query Side (read operations):**
```
ValidationCompletedEvent
    ↓
ValidationResultProjector
    ↓
Store report graph
    ↓
Create commit (if version-controlled)
```

**Note:** Validation itself is NOT a write operation - it's read-only. Only result storage uses CQRS.

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
│   - Orchestrate validation workflow      │
│   - Coordinate resolvers and engine      │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   GraphReferenceResolver                 │
│   - Resolve shapes graph reference       │
│   - Resolve data graph references        │
│   - Apply version selectors              │
│   - Handle remote endpoints              │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   ShaclValidationEngine                  │
│   - Apache Jena SHACL integration        │
│   - Validation modes (separate/merged)   │
│   - Report generation                    │
└──────────────┬──────────────────────────┘
               │
               ↓ (if storing)
┌─────────────────────────────────────────┐
│   StoreValidationResultCommandHandler    │
│   - Create commit with result graph      │
│   - Publish event                        │
│   - Return 202 Accepted                  │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   ValidationResultProjector              │
│   - Listen to ValidationResultStoredEvent│
│   - Update repositories                  │
└─────────────────────────────────────────┘
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

      # Remote endpoints
      remote:
        enabled: true
        timeout: 30000           # 30 seconds
        max-response-size: 104857600
        block-private-ips: true
        allowed-schemes:
          - http
          - https

      # Caching
      cache:
        enabled: true
        ttl: 3600                # 1 hour
        max-entries: 100

      # Result storage
      storage:
        default-overwrite: false
        enable-versioning: true
```

---

## Security Considerations

### Input Validation
- Dataset names match ABNF grammar (already enforced)
- Graph URIs are valid IRIs
- Selectors are mutually exclusive
- Remote endpoint URLs validated

### Access Control
- `READ` permission on shapes dataset/graph
- `READ` permission on data dataset/graph(s)
- `WRITE` permission on results dataset/graph (if storing)

### SSRF Prevention
- Validate URL scheme (http/https only)
- Block private IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
- Block localhost/127.0.0.1
- Enforce timeouts (default: 30 seconds)
- Limit response size (default: 100MB)

### Resource Limits
- Max shapes graph size: 10MB
- Max data graph size: 100MB
- Validation timeout: 60 seconds
- Max concurrent validations: 10

---

## Progress Tracking

**Phase 1:** ⏳ Not Started (0/3 tasks)
**Phase 2:** ⏳ Not Started (0/2 tasks)
**Phase 3:** ⏳ Not Started (0/2 tasks)
**Phase 4:** ⏳ Not Started (0/1 tasks)
**Phase 5:** ⏳ Not Started (0/2 tasks)
**Phase 6:** ⏳ Not Started (0/2 tasks)

**Overall Progress:** 0% (0/12 tasks completed)

---

## Task Completion Workflow

When a task is completed:

1. **Verify Success Criteria**
   - All features implemented
   - All tests pass
   - Zero quality violations
   - Full build succeeds: `mvn -q clean install`

2. **Invoke Specialized Agents**
   - `@cqrs-compliance-checker` (if CQRS components added)
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

## References

- **[SHACL Validation Protocol](../../protocol/SHACL_Validation_Protocol.md)** - Complete specification
- **[SHACL Protocol Summary](../../protocol/SHACL_Protocol_Summary.md)** - Implementation guide
- **[SHACL Specification (W3C)](https://www.w3.org/TR/shacl/)** - SHACL semantics
- **[Apache Jena SHACL](https://jena.apache.org/documentation/shacl/)** - Java API documentation
- **[Development Guidelines](../../.claude/CLAUDE.md)** - CHUCC development best practices
- **[CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)** - Architecture pattern

---

**Last Updated:** 2025-11-14
**Status:** Ready for implementation
**Next Task:** Task 1 - Core Infrastructure
