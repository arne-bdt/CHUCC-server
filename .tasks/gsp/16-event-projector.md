# Task 16: Graph Event Projector for Read Model

## Objective
Create event projector to update read model repositories when GraphUpdatedEvent is published.

## Background
The CQRS architecture requires projectors to consume events from Kafka and update read models. GSP operations create events that need to be projected to repositories.

## Tasks

### 1. Review Existing Projector
- Examine existing projectors (e.g., ReadModelProjector)
- Understand Kafka consumer setup
- Identify repositories that need updating

### 2. Determine Read Model Strategy
Options:
- **Option A**: No separate graph repository (compute graphs on-demand from commits)
- **Option B**: Graph snapshot repository for performance

**Recommendation**: Option A for initial implementation (simpler, aligns with event sourcing)

If Option B is needed later:
- Create GraphSnapshotRepository
- Store latest graph content per branch
- Update on GraphUpdatedEvent

### 3. Ensure Commit Projector Handles GSP Events
Verify that existing commit projector:
- Consumes GraphUpdatedEvent (or CommitCreatedEvent from GSP operations)
- Updates commit repository
- Updates branch head pointers
- Handles graph-specific commit metadata

### 4. Write Projector Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`:
- Test GraphUpdatedEvent consumed from Kafka
- Test commit repository updated
- Test branch head updated
- Test eventual consistency (use awaitility)

## Acceptance Criteria
- [ ] Events from GSP operations are consumed
- [ ] Commit repository updated with graph commits
- [ ] Branch heads updated correctly
- [ ] Projector integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 15 (interoperability testing)

## Estimated Complexity
Low-Medium (3-4 hours)
