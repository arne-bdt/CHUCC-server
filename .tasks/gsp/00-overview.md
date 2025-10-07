# Graph Store Protocol Implementation - Overview

## Purpose
Implement SPARQL 1.2 Graph Store Protocol with Version Control Extension to enable RESTful graph management alongside the existing SPARQL Protocol implementation.

## Scope
- Base SPARQL 1.2 Graph Store Protocol (GET, PUT, POST, DELETE, HEAD on graphs)
- PATCH operation for RDF Patch application
- Version control selectors (branch, commit, asOf)
- Shared version control operations (/version/*)
- Batch graph operations (/version/batch-graphs)
- ETag and If-Match precondition support
- Conflict detection and resolution
- Full interoperability with existing SPARQL Protocol extension

## Architecture Alignment
The implementation will follow the existing CQRS + Event Sourcing pattern:
- **Commands**: GraphUpdateCommand, GraphDeleteCommand, GraphPatchCommand, etc.
- **Events**: GraphUpdatedEvent, GraphDeletedEvent, etc. (published to Kafka)
- **Projectors**: Update read models asynchronously
- **Controllers**: Thin REST layer delegating to command handlers
- **Services**: Reuse existing SelectorResolutionService, PreconditionService, DatasetService

## Shared Infrastructure
Both SPARQL Protocol and Graph Store Protocol extensions share:
- Same commit DAG, branches, and tags
- Same /version/ namespace for metadata operations
- Same selector semantics (branch, commit, asOf)
- Same conflict detection and ETag semantics
- Same event store (Kafka topics)

## Task Execution Order
Tasks are numbered to indicate dependencies and recommended execution order:
1. Foundation (domain model, validation, infrastructure)
2. Read operations (GET, HEAD)
3. Write operations (PUT, POST, DELETE, PATCH)
4. Version control integration (selectors, ETag, conflict detection)
5. Batch operations
6. Advanced features (no-op detection, time-travel, etc.)
7. Interoperability testing

## Success Criteria
- All GSP operations work with version control selectors
- Commits created by GSP operations appear in /version/history
- Commits from SPARQL Protocol are queryable via GSP selectors
- Zero test failures, zero Checkstyle violations, zero SpotBugs warnings
- Full compliance with both specifications
