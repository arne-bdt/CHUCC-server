# Model API to Graph API Migration Tasks

This directory contains the task breakdown for migrating from Apache Jena's Model API to the Graph API.

## Overview

**Goal**: Improve performance and efficiency by using the lower-level Graph API instead of Model API.

**Benefits**:
- 20-30% performance improvement in graph operations
- 15-25% memory reduction
- More efficient triple iteration
- Direct node access without wrapper objects

## Task Files

### Planning Document
- **[model-to-graph-api-migration.md](./model-to-graph-api-migration.md)** - Complete migration strategy and plan

### Implementation Tasks

Execute tasks in this order:

1. **[01-migrate-rdf-parsing-service.md](./01-migrate-rdf-parsing-service.md)**
   - Change `RdfParsingService.parseRdf()` to return `Graph`
   - Foundational change
   - Estimated: 1-2 hours

2. **[02-migrate-graph-serialization-service.md](./02-migrate-graph-serialization-service.md)**
   - Change `GraphSerializationService.serializeGraph()` to accept `Graph`
   - Foundational change
   - Estimated: 1-2 hours
   - Depends on: Task 1

3. **[03-migrate-graph-diff-service.md](./03-migrate-graph-diff-service.md)**
   - Change `GraphDiffService` to use `Graph` instead of `Model`
   - Highest performance impact
   - Estimated: 2-3 hours
   - Depends on: Tasks 1, 2

4. **Task 4: Migrate DatasetService** (to be created)
   - Change `getGraph()` and `getDefaultGraph()` return types
   - Core infrastructure change
   - Estimated: 2-3 hours
   - Depends on: Tasks 1, 2, 3

5. **Task 5: Migrate GraphCommandUtil** (to be created)
   - Update utility methods to work with Graph
   - Estimated: 1 hour
   - Depends on: Task 4

6. **Task 6: Migrate All Command Handlers** (to be created)
   - Update 5 command handlers
   - Estimated: 2 hours
   - Depends on: Tasks 1-5

7. **Task 7: Handle SPARQL Special Cases** (to be created)
   - Evaluate Model usage in SparqlQueryService
   - Add conversion layer if needed
   - Estimated: 1 hour
   - Depends on: Tasks 1, 2

8. **Task 8-10: Update Tests** (to be created)
   - Service tests
   - Command handler tests
   - Integration tests
   - Estimated: 3-4 hours

## Progress Tracking

| Task | Status | Files Changed | Tests Updated | Build Status |
|------|--------|---------------|---------------|--------------|
| 01 - RdfParsingService | ⏳ Not Started | - | - | - |
| 02 - GraphSerializationService | ⏳ Not Started | - | - | - |
| 03 - GraphDiffService | ⏳ Not Started | - | - | - |
| 04 - DatasetService | ⏳ Not Started | - | - | - |
| 05 - GraphCommandUtil | ⏳ Not Started | - | - | - |
| 06 - Command Handlers | ⏳ Not Started | - | - | - |
| 07 - SPARQL Service | ⏳ Not Started | - | - | - |
| 08-10 - Tests | ⏳ Not Started | - | - | - |

**Legend**:
- ⏳ Not Started
- 🔄 In Progress
- ✅ Completed
- ❌ Blocked

## Quick Start

To begin the migration:

1. Read the main planning document: `model-to-graph-api-migration.md`
2. Start with Task 1: `01-migrate-rdf-parsing-service.md`
3. Follow the implementation steps in each task file
4. Update this README with progress as you complete tasks

## Build Verification

After each task:
```bash
# Phase 1: Static analysis
mvn -q clean compile checkstyle:check spotbugs:check pmd:check

# Phase 2: Run affected tests
mvn -q test -Dtest=<TestClassName>

# Phase 3: Full build (before marking task complete)
mvn -q clean install
```

## Success Criteria

For the entire migration:
- ✅ All 911+ tests pass
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Performance improvement measured
- ✅ Memory usage improvement measured
- ✅ No Model usage in internal code (only at boundaries)

## Performance Targets

Expected improvements:
- **GraphDiffService**: 20-30% faster
- **Overall memory**: 15-25% reduction
- **Large graphs (10K+ triples)**: More significant gains

## Notes

- Each task is designed to be completed independently
- Git commits should be atomic per task
- Tag before starting: `git tag before-model-to-graph-migration`
- Tasks 1-3 are foundational and must be completed first
- Tasks 4-10 can be parallelized once 1-3 are complete

## Questions or Issues?

Consult the main planning document or ask for clarification before starting a task.
