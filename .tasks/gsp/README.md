# Graph Store Protocol Implementation Tasks

## Overview
This directory contains a breakdown of tasks for implementing the SPARQL 1.2 Graph Store Protocol with Version Control Extension.

## Task Execution Strategy

### Phase 1: Foundation (Tasks 01-03)
- Domain model and validation utilities
- Controller skeleton and discovery endpoint
- **Checkpoint**: Run `mvn clean compile checkstyle:check spotbugs:check`

### Phase 2: Read Operations (Tasks 04-05)
- GET and HEAD operations
- Graph serialization and content negotiation
- **Checkpoint**: Run integration tests for GET/HEAD

### Phase 3: Write Operations (Tasks 06-10)
- PUT, POST, DELETE, PATCH commands and handlers
- RDF diff computation and patch application
- **Checkpoint**: Run integration tests for all write operations

### Phase 4: Version Control Integration (Tasks 11-14)
- Conflict detection
- ETag and If-Match support
- Batch operations
- Time-travel queries
- **Checkpoint**: Run full integration test suite

### Phase 5: Interoperability and Events (Tasks 15-16)
- Cross-protocol testing
- Event projector for read models
- **Checkpoint**: Run cross-protocol integration tests

### Phase 6: Polish and Optimization (Tasks 17-21)
- Performance optimization
- Security validation
- Error handling polish
- Documentation completion
- Final QA and compliance verification
- **Checkpoint**: Full build with zero violations

## Task Dependencies

```
01 (Domain Model)
  └─> 02 (Validation)
       └─> 03 (Controller Skeleton)
            ├─> 04 (GET)
            │    └─> 05 (HEAD)
            │         └─> 06 (PUT Command)
            │              └─> 07 (PUT Controller)
            │                   └─> 08 (POST)
            │                        └─> 09 (DELETE)
            │                             └─> 10 (PATCH)
            │                                  └─> 11 (Conflicts)
            │                                       └─> 12 (ETag)
            │                                            └─> 13 (Batch)
            │                                                 └─> 14 (Time Travel)
            │                                                      └─> 15 (Interop)
            │                                                           └─> 16 (Projector)
            │                                                                └─> 17 (Performance)
            │                                                                     └─> 18 (Security)
            │                                                                          └─> 19 (Errors)
            │                                                                               └─> 20 (Docs)
            │                                                                                    └─> 21 (QA)
```

## Estimation Summary

| Phase | Tasks | Estimated Hours | Complexity |
|-------|-------|-----------------|------------|
| 1. Foundation | 01-03 | 6-9 | Low-Medium |
| 2. Read Ops | 04-05 | 6-9 | Low-Medium |
| 3. Write Ops | 06-10 | 22-29 | Medium-High |
| 4. Version Control | 11-14 | 19-24 | Medium |
| 5. Interoperability | 15-16 | 8-10 | Medium |
| 6. Polish | 17-21 | 19-24 | Low-Medium |
| **Total** | **21 tasks** | **80-105 hours** | |

## Notes for AI Agents

### Testing Strategy
- Write unit tests before implementing features (TDD)
- Use API Layer tests (synchronous response validation)
- Use Full System tests with `await()` only when testing async projections
- Follow patterns from existing tests (see CLAUDE.md)

### Code Quality
- Run Phase 1 static analysis: `mvn clean compile checkstyle:check spotbugs:check pmd:check`
- Fix all violations before proceeding
- Run specific test classes: `mvn clean install -Dtest=ClassName`
- Run full build at checkpoints: `mvn clean install`

### Architecture Consistency
- Follow CQRS pattern: Commands → Events → Projectors
- Reuse existing services (SelectorResolutionService, DatasetService, etc.)
- Keep controllers thin (delegate to command handlers)
- Use problem+json for all errors

### Interoperability
- Remember: GSP and Protocol share the same commit DAG
- Commits from GSP must appear in `/version/history`
- Selectors must work identically across both protocols
- Batch endpoints are distinct: `/version/batch` (Protocol) vs `/version/batch-graphs` (GSP)

## Getting Started

To begin implementation:
1. Read `00-overview.md` for context
2. Start with Task 01 (domain model)
3. Follow task order (respects dependencies)
4. Run checkpoints after each phase
5. Update this README with progress

## Progress Tracking

- [ ] Phase 1: Foundation (Tasks 01-03)
- [ ] Phase 2: Read Operations (Tasks 04-05)
- [ ] Phase 3: Write Operations (Tasks 06-10)
- [ ] Phase 4: Version Control Integration (Tasks 11-14)
- [ ] Phase 5: Interoperability (Tasks 15-16)
- [ ] Phase 6: Polish and Optimization (Tasks 17-21)
