# Task 16b: Wire Event Publishing in Command Handlers

## Objective
Update all command handlers to publish events to Kafka, completing the CQRS event-driven architecture.

## Background
**Current State:**
- Command handlers create `VersionControlEvent` objects
- Events are returned to controllers for HTTP responses
- Events are **NOT published to Kafka**
- `ReadModelProjector` is ready to consume events but receives none in production
- Repositories are never updated asynchronously

**Root Cause:**
The original task specifications (Tasks 06-10) mentioned "publish event to Kafka" but this was never implemented. Events are created but not published.

**Architecture Decision:**
Events should be published by **command handlers** (not controllers) because:
1. Command handlers are the "command side" of CQRS - they own event creation
2. Publishing is a domain concern (what happened), not infrastructure (HTTP response)
3. Handlers can publish async (fire-and-forget) while still returning event for HTTP response
4. Keeps controllers thin and focused on HTTP concerns

## Affected Command Handlers

### Phase 1: GSP Handlers (High Priority - 5 handlers)
These are critical for Graph Store Protocol to work end-to-end:
1. **PutGraphCommandHandler** → publishes `CommitCreatedEvent`
2. **PostGraphCommandHandler** → publishes `CommitCreatedEvent`
3. **DeleteGraphCommandHandler** → publishes `CommitCreatedEvent`
4. **PatchGraphCommandHandler** → publishes `CommitCreatedEvent`
5. **BatchGraphsCommandHandler** → publishes `BatchGraphsCompletedEvent`

### Phase 2: Protocol Handlers (Medium Priority - 8 handlers)
These support the Version Control Protocol:
6. **CreateCommitCommandHandler** → publishes `CommitCreatedEvent`
7. **CreateBranchCommandHandler** → publishes `BranchCreatedEvent`
8. **CreateTagCommandHandler** → publishes `TagCreatedEvent`
9. **ResetBranchCommandHandler** → publishes `BranchResetEvent`
10. **RevertCommitCommandHandler** → publishes `RevertCreatedEvent`
11. **RebaseCommandHandler** → publishes `BranchRebasedEvent`
12. **CherryPickCommandHandler** → publishes `CherryPickedEvent`
13. **SquashCommandHandler** → publishes `CommitsSquashedEvent`

## Implementation Pattern

For each command handler, follow this pattern:

### 1. Inject EventPublisher
```java
@Component
public class XxxCommandHandler implements CommandHandler<XxxCommand> {

  private final EventPublisher eventPublisher;
  // ... other dependencies

  public XxxCommandHandler(
      EventPublisher eventPublisher,
      // ... other dependencies
  ) {
    this.eventPublisher = eventPublisher;
    // ... other assignments
  }
}
```

### 2. Publish Event (Fire-and-Forget)
```java
@Override
public VersionControlEvent handle(XxxCommand command) {
  // ... existing logic to create event
  VersionControlEvent event = new XxxEvent(...);

  // Publish to Kafka (async, fire-and-forget)
  eventPublisher.publish(event)
      .exceptionally(ex -> {
        logger.error("Failed to publish event {}: {}",
            event.getClass().getSimpleName(), ex.getMessage(), ex);
        // Don't fail the command - event created successfully
        // Projector can recover from event log if needed
        return null;
      });

  // Return event for HTTP response (synchronous)
  return event;
}
```

### 3. Add Logger (if not present)
```java
private static final Logger logger = LoggerFactory.getLogger(XxxCommandHandler.class);
```

### 4. Add PMD Suppression (if needed)
```java
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
```

## Special Cases

### GraphCommandUtil.finalizeGraphCommand()
This utility is used by multiple GSP handlers. Two options:
- **Option A**: Pass EventPublisher as parameter, publish in utility
- **Option B**: Publish in each handler after calling utility

**Recommendation: Option B** - Keep utility pure (no side effects), publish in handlers.

### BatchGraphsCommandHandler
Special case - publishes `BatchGraphsCompletedEvent` which contains a list of `CommitCreatedEvent`:
- Publish the `BatchGraphsCompletedEvent` (contains all commits)
- Do **NOT** publish individual `CommitCreatedEvent` objects
- `ReadModelProjector.handleBatchGraphsCompleted()` will process each commit

### Null Event Returns (No-Op)
Some handlers return `null` for no-op cases:
```java
if (event == null) {
  // No-op, nothing to publish
  return null;
}
eventPublisher.publish(event).exceptionally(...);
return event;
```

## Testing Strategy

### Unit Tests
**No changes needed** - Unit tests mock dependencies, event publishing is a side effect.

### Integration Tests
**Two approaches:**

**Approach 1: Keep current tests (manual publish)**
- Existing tests manually publish events via EventPublisher
- These tests verify projector works correctly
- Add new tests for end-to-end flow (HTTP → Handler → Kafka → Projector)

**Approach 2: Update existing tests**
- Remove manual event publishing from tests
- Tests now verify complete flow automatically
- Use `await()` to verify repositories updated via projection

**Recommendation: Approach 1** - Less invasive, clearer test intent.

### New Integration Tests
Add to `GraphEventProjectorIT` or create `EndToEndFlowIT`:
```java
@Test
void gspPut_shouldPublishEventAndUpdateRepository() throws Exception {
  // When - Execute GSP PUT
  ResponseEntity<?> response = restTemplate.exchange("/data?default=true&branch=main",
      HttpMethod.PUT, request, String.class);

  CommitId commitId = extractCommitId(response);

  // Then - Event published to Kafka and repository updated (via projector)
  await().atMost(Duration.ofSeconds(10))
      .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId).isPresent());
}
```

## Error Handling Philosophy

**Fire-and-Forget with Logging:**
- Event publishing failures are **logged** but **don't fail the command**
- Rationale: The command succeeded (event created), Kafka is async infrastructure
- Event is still returned to controller for HTTP response
- Projector can be rebuilt from full event log if needed

**Alternative (NOT recommended):**
- Block until publish succeeds
- Fail command if publish fails
- Problem: Introduces latency, creates coupling to Kafka availability

## Implementation Steps

### Step 1: Phase 1 - GSP Handlers (Priority 1)
1. Update `PutGraphCommandHandler`
2. Update `PostGraphCommandHandler`
3. Update `DeleteGraphCommandHandler`
4. Update `PatchGraphCommandHandler`
5. Update `BatchGraphsCommandHandler`
6. Run GSP integration tests to verify
7. Run `GraphEventProjectorIT` to verify projection works

### Step 2: Phase 2 - Protocol Handlers (Priority 2)
8. Update `CreateCommitCommandHandler`
9. Update `CreateBranchCommandHandler`
10. Update `CreateTagCommandHandler`
11. Update `ResetBranchCommandHandler`
12. Update `RevertCommitCommandHandler`
13. Update `RebaseCommandHandler`
14. Update `CherryPickCommandHandler`
15. Update `SquashCommandHandler`
16. Run Protocol integration tests to verify

### Step 3: End-to-End Verification
17. Add end-to-end integration tests (HTTP → Kafka → Projector)
18. Verify all events flow correctly
19. Check logs for any publishing errors
20. Run full build: `mvn clean install`

## Code Quality Checklist

- [ ] EventPublisher injected via constructor (DI)
- [ ] SpotBugs: Add `@SuppressFBWarnings` for EventPublisher field if needed
- [ ] Checkstyle: Logger declaration follows conventions
- [ ] PMD: Add `@SuppressWarnings("PMD.GuardLogStatement")` if using logger
- [ ] Error handling: `.exceptionally()` logs errors appropriately
- [ ] Tests: Verify behavior (unit tests unchanged, integration tests pass)
- [ ] No duplicate event publishing (check for double-publish)

## Acceptance Criteria

- [ ] All 13 command handlers publish events to Kafka
- [ ] Events published async (fire-and-forget with error logging)
- [ ] HTTP responses still include event data (synchronous return)
- [ ] `GraphEventProjectorIT` tests pass (projection works)
- [ ] New end-to-end tests pass (HTTP → Kafka → Projector)
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings
- [ ] Zero PMD violations
- [ ] Full build passes: `mvn clean install`

## Dependencies
- Task 16 (Event Projector) - **COMPLETED**
- EventPublisher service - **EXISTS**
- ReadModelProjector - **EXISTS**

## Estimated Complexity

**Phase 1 (GSP Handlers):**
- Simple pattern: ~15 min per handler
- 5 handlers × 15 min = 75 min
- Testing & verification: 30 min
- **Total: ~2 hours**

**Phase 2 (Protocol Handlers):**
- Same pattern: ~15 min per handler
- 8 handlers × 15 min = 120 min
- Testing & verification: 30 min
- **Total: ~2.5 hours**

**End-to-End Testing:**
- Write new integration tests: 1 hour
- Debugging & verification: 0.5 hours
- **Total: ~1.5 hours**

**Grand Total: 6 hours (Medium complexity)**

## Breaking into Sub-Tasks?

**Option 1: Single Task (Recommended)**
- All handlers use identical pattern
- Can be done in one session
- Clear atomic unit of work
- Easier to track progress

**Option 2: Two Sub-Tasks**
- 16b-1: GSP Handlers (high priority, 2 hours)
- 16b-2: Protocol Handlers + E2E tests (medium priority, 4 hours)
- Advantage: Can ship GSP functionality sooner
- Disadvantage: Incomplete CQRS architecture until both done

**Recommendation: Option 1 (Single Task)** - Complete the architecture in one go.

## Notes

- This task completes the CQRS event-driven architecture
- After this task, all write operations will update read models asynchronously
- `GraphEventProjectorIT` was written in anticipation of this task
- The existing `EventPublisher` service is production-ready (transactional support, headers, partitioning)
- Event publishing is fire-and-forget for performance (eventual consistency model)

## Follow-Up Tasks

After this task, consider:
- **Task 17**: Performance optimization (caching, graph materialization)
- **Monitoring**: Add metrics for event publishing success/failure rates
- **Observability**: Distributed tracing for command → event → projection flow
