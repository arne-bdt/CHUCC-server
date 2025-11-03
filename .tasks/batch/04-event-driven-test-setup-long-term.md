# Event-Driven Test Setup - Long-Term Solution

## Status: TODO
**Created:** 2025-11-03
**Priority:** Medium (Technical Debt)
**Complexity:** High
**Parent Task:** `.tasks/batch/03-fix-batch-test-isolation-race.md`
**Estimated Time:** 2-3 sessions

## TL;DR - Quick Start

**Problem:** Current test fixture bypasses event flow by directly saving to repositories, causing test isolation issues and not reflecting production behavior.

**Solution:** Refactor `ITFixture` to create test state via events (like production), ensuring tests exercise the full CQRS + Event Sourcing flow.

**Benefits:**
- ✅ Eliminates race conditions at the root cause
- ✅ Tests match production behavior (events → projectors → repositories)
- ✅ Improved test reliability and confidence
- ✅ Better alignment with CQRS architecture principles

**Current Workaround:** Synchronized cleanup (`.tasks/batch/03-fix-batch-test-isolation-race.md`) - works but doesn't address root cause

## Context

### Current Problem

The `ITFixture` class creates test state by directly saving to repositories:

```java
@BeforeEach
void setUpIntegrationTestFixture() {
  // Current approach: Direct repository saves (bypasses events)
  commitRepository.save(initialCommit);
  branchRepository.save(initialBranch);
  materializedBranchRepo.createBranch(dataset, branch, Optional.empty());
}
```

**Why This Is Problematic:**

1. **Bypasses Event Flow:** Tests don't exercise event publishing → Kafka → projector pipeline
2. **State Inconsistency:** Repository state != materialized view state (no events generated)
3. **Race Conditions:** Cache eviction during test cleanup causes rebuild failures
4. **Production Mismatch:** Tests behave differently than production code paths
5. **False Confidence:** Tests pass but production may fail due to event processing issues

### Desired State

Tests should create state via events (like production):

```java
@BeforeEach
void setUpIntegrationTestFixture() {
  // Desired approach: Event-driven setup
  CommitCreatedEvent event = new CommitCreatedEvent(...);
  eventPublisher.publishEvent(event);

  // Wait for async projection (if projector enabled)
  if (projectorEnabled) {
    await().untilAsserted(() -> {
      assertThat(commitRepository.findById(commitId)).isPresent();
      assertThat(materializedBranchRepo.exists(dataset, branch)).isTrue();
    });
  }
}
```

**Benefits:**

✅ **Full Event Flow:** Tests exercise complete CQRS pipeline
✅ **Production Parity:** Tests match real-world behavior
✅ **No Race Conditions:** Proper event ordering eliminates timing issues
✅ **Better Coverage:** Catches event processing bugs that direct saves miss
✅ **Architecture Compliance:** Tests enforce CQRS principles

## Architecture Background

### CQRS + Event Sourcing Flow

```
Command Handler → Event Created → Kafka → ReadModelProjector → Repositories
                                                  ↓
                                         MaterializedBranchRepo
```

**Current Test Setup (Broken):**
```
Test @BeforeEach → Direct Repository Save (❌ No events, no projector)
```

**Desired Test Setup (Correct):**
```
Test @BeforeEach → Event Published → Projector → Repositories (✅ Matches production)
```

### Two Test Modes

**Mode 1: Projector Disabled (90% of tests)**
- Test HTTP API only (command side)
- Events published but NOT consumed
- Repositories NOT updated by projector
- Tests assert HTTP response only

**Mode 2: Projector Enabled (10% of tests)**
- Test full event flow (command + query side)
- Events published AND consumed
- Repositories updated by projector
- Tests assert repository state after `await()`

### Key Insight

The fixture setup must work for BOTH modes:
- **Mode 1:** Publish event, then **directly save** to repositories (projector won't)
- **Mode 2:** Publish event, then **wait for projector** to update repositories

## Implementation Plan

### Phase 1: Add Event Publishing Support to ITFixture

**Goal:** Make ITFixture event-aware without breaking existing tests

**Files to Modify:**
- `src/test/java/org/chucc/vcserver/testutil/ITFixture.java`

**Changes:**

1. **Add EventPublisher dependency**
```java
@Autowired(required = false)
protected ApplicationEventPublisher eventPublisher;
```

2. **Detect if projector is enabled**
```java
@Value("${projector.kafka-listener.enabled:false}")
private boolean projectorEnabled;

protected boolean isProjectorEnabled() {
  return projectorEnabled;
}
```

3. **Create helper method for event-driven setup**
```java
/**
 * Creates initial commit and branch via events (like production).
 * Works in both projector-enabled and projector-disabled modes.
 *
 * @param dataset Dataset name
 */
protected void createInitialCommitAndBranchViaEvents(String dataset) {
  initialCommitId = CommitId.generate();

  // Create event (like command handlers do)
  CommitCreatedEvent event = new CommitCreatedEvent(
      dataset,
      initialCommitId,
      List.of(),  // no parents
      getInitialBranchName(),
      DEFAULT_AUTHOR,
      "Initial commit",
      Instant.now(),
      RDFPatchOps.emptyPatch()
  );

  // Publish event
  if (eventPublisher != null) {
    eventPublisher.publishEvent(event);
  }

  // Wait for projection OR save directly
  if (isProjectorEnabled()) {
    // Mode 2: Wait for projector to update repositories
    await().atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(dataset, initialCommitId))
              .isPresent();
          assertThat(branchRepository.exists(dataset, getInitialBranchName()))
              .isTrue();
          assertThat(materializedBranchRepo.exists(dataset, getInitialBranchName()))
              .isTrue();
        });
  } else {
    // Mode 1: Directly save to repositories (projector disabled)
    // This ensures tests without projector still have repository state
    Commit commit = new Commit(
        dataset,
        initialCommitId,
        List.of(),
        getInitialBranchName(),
        DEFAULT_AUTHOR,
        "Initial commit",
        Instant.now(),
        RDFPatchOps.emptyPatch()
    );
    commitRepository.save(commit);

    Branch branch = new Branch(dataset, getInitialBranchName(), initialCommitId);
    branchRepository.save(branch);

    materializedBranchRepo.createBranch(dataset, getInitialBranchName(),
        Optional.empty());
  }
}
```

4. **Add compatibility method**
```java
/**
 * Legacy method for backward compatibility.
 * Delegates to event-driven setup.
 *
 * @param dataset Dataset name
 * @deprecated Use createInitialCommitAndBranchViaEvents() directly
 */
@Deprecated(forRemoval = true)
protected void createInitialCommitAndBranch(String dataset) {
  createInitialCommitAndBranchViaEvents(dataset);
}
```

### Phase 2: Migrate Existing Tests (Gradual)

**Goal:** Incrementally migrate tests to use event-driven setup

**Strategy:** Tests continue working with compatibility layer, no immediate changes needed

**Optional Migration Steps:**
1. Replace `createInitialCommitAndBranch()` calls with `createInitialCommitAndBranchViaEvents()`
2. Remove `@Deprecated` annotation once all tests migrated
3. Delete old implementation

**Priority:** Low - compatibility layer means no urgent migration needed

### Phase 3: Add Admin API for Dataset Creation (If Needed)

**Context:** Some tests (like `BatchOperationsProjectorIT`) need datasets created before branches

**Current Limitation:** No REST API endpoint to create datasets

**Options:**

**Option A:** Use existing `CreateDatasetCommand` handler directly in tests
```java
@Autowired
private CreateDatasetCommandHandler createDatasetHandler;

@BeforeEach
void setup() {
  CreateDatasetCommand command = new CreateDatasetCommand("default");
  createDatasetHandler.handle(command);

  // Then create branches via events...
  createInitialCommitAndBranchViaEvents("default");
}
```

**Option B:** Add admin REST endpoint (if needed for API testing)
```java
// Controller
@PostMapping("/admin/datasets")
public ResponseEntity<Void> createDataset(@RequestBody CreateDatasetRequest req) {
  handler.handle(new CreateDatasetCommand(req.name()));
  return ResponseEntity.created(URI.create("/datasets/" + req.name())).build();
}
```

**Recommendation:** Option A (use command handler directly) - simpler, no new API needed

### Phase 4: Remove Synchronized Cleanup (Once Stable)

**Goal:** Remove temporary workaround once event-driven setup is proven

**File:** `src/test/java/org/chucc/vcserver/testutil/ITFixture.java`

**Change:** Remove `synchronized (CLEANUP_LOCK)` wrapper

**Validation:** Run full build 3+ times to confirm no race conditions

## Testing Strategy

### Validation Steps

1. **Unit Test the Fixture Changes**
   - Create `ITFixtureTest` to verify event publishing logic
   - Test both projector-enabled and projector-disabled modes
   - Verify timeout handling for await()

2. **Test with Existing Tests**
   - Run full test suite: `mvn clean test`
   - All existing tests should pass (backward compatible)
   - No behavior changes expected

3. **Test Projector-Enabled Mode**
   - Run `BatchOperationsProjectorIT` specifically
   - Verify events are published and projected correctly
   - Check for race conditions: run 5+ times

4. **Test Projector-Disabled Mode**
   - Run any `*ControllerIT` test
   - Verify direct repository saves still work
   - Ensure HTTP assertions pass

### Test Scenarios

**Scenario 1: Projector Disabled (Default)**
```java
@SpringBootTest
@ActiveProfiles("it")
class MyApiTest extends ITFixture {
  // projector.kafka-listener.enabled=false (default)

  @Test
  void test() {
    // Setup creates event + direct save
    // HTTP request works
    // No repository assertions (projector disabled)
  }
}
```

**Scenario 2: Projector Enabled**
```java
@SpringBootTest
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyProjectorTest extends ITFixture {

  @Test
  void test() {
    // Setup creates event → projector updates repositories
    // Can assert repository state after await()
  }
}
```

## Risk Assessment

### Risks & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Tests timeout waiting for projector | High | Medium | Add configurable timeout (default 10s), clear error messages |
| Backward compatibility breaks | High | Low | Keep old method as deprecated, gradual migration |
| Performance regression (slower tests) | Medium | High | Only projector-enabled tests affected, use `await().pollInterval()` to minimize |
| Kafka/Testcontainer flakiness | Medium | Low | Already handled by existing infrastructure |
| Event schema changes break setup | Low | Low | Tests catch this early (good thing!) |

### Performance Impact

**Current Setup Time:** ~50ms (direct repository saves)
**Event-Driven Setup Time (Mode 1):** ~100ms (publish + direct save)
**Event-Driven Setup Time (Mode 2):** ~500ms (publish + await projection)

**Mitigation:** Only ~40 tests use Mode 2, so overall suite impact is minimal

## Success Criteria

- [ ] ITFixture publishes events instead of direct saves
- [ ] Existing tests continue passing without modifications
- [ ] New event-driven setup works in both projector modes
- [ ] `BatchOperationsProjectorIT` passes consistently (5+ runs)
- [ ] No "Could not rebuild graph" warnings in logs
- [ ] Full test suite passes: `mvn clean install` (3+ runs)
- [ ] Zero test flakiness related to cache rebuilds
- [ ] Documentation updated (CLAUDE.md testing section)

## Files to Modify

### Primary Changes

1. **`src/test/java/org/chucc/vcserver/testutil/ITFixture.java`**
   - Add `ApplicationEventPublisher` dependency
   - Add `isProjectorEnabled()` helper
   - Add `createInitialCommitAndBranchViaEvents()` method
   - Mark old method as `@Deprecated`
   - Keep `synchronized(CLEANUP_LOCK)` for now (remove in Phase 4)

### Documentation Updates

2. **`.claude/CLAUDE.md`**
   - Update testing guidelines section
   - Document event-driven setup pattern
   - Add examples for both projector modes

3. **`docs/architecture/cqrs-event-sourcing.md`**
   - Add section on testing event-driven code
   - Explain fixture event publishing approach

### Optional (Phase 3)

4. **`src/main/java/org/chucc/vcserver/controller/AdminController.java`** (if needed)
   - Add POST /admin/datasets endpoint
   - Delegate to `CreateDatasetCommandHandler`

5. **`src/test/java/org/chucc/vcserver/testutil/ITFixtureTest.java`** (new)
   - Unit tests for fixture event publishing logic

## Implementation Checklist

### Phase 1: Core Implementation
- [ ] Add `ApplicationEventPublisher` to ITFixture
- [ ] Add `isProjectorEnabled()` helper method
- [ ] Implement `createInitialCommitAndBranchViaEvents()`
- [ ] Add Awaitility dependency if not present
- [ ] Mark old method as `@Deprecated`
- [ ] Run unit tests: `mvn clean test`
- [ ] Run integration tests: `mvn clean verify`

### Phase 2: Validation
- [ ] Run `BatchOperationsProjectorIT` 5 times: `mvn test -Dtest=BatchOperationsProjectorIT` (repeat 5x)
- [ ] Run full build 3 times: `mvn clean install` (repeat 3x)
- [ ] Check logs for "Could not rebuild graph" warnings
- [ ] Verify no test failures or timeouts

### Phase 3: Optional Enhancements
- [ ] Add admin API for dataset creation (if needed)
- [ ] Create ITFixtureTest unit tests
- [ ] Migrate high-priority tests to new setup (optional)

### Phase 4: Cleanup
- [ ] Remove `synchronized(CLEANUP_LOCK)` from cleanup code
- [ ] Run full build 3 times to confirm no race conditions
- [ ] Remove `@Deprecated` annotation after migration
- [ ] Delete old `createInitialCommitAndBranch()` implementation

### Phase 5: Documentation
- [ ] Update CLAUDE.md testing guidelines
- [ ] Update CQRS architecture docs
- [ ] Add code comments explaining event-driven setup
- [ ] Create examples for common test patterns

## Code Examples

### Example 1: Projector-Disabled Test (Current Pattern)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreControllerIT extends ITFixture {
  // projector disabled by default

  @Test
  void putGraph_shouldReturn201() {
    // Fixture publishes event + direct save
    // Test HTTP API only
    ResponseEntity<String> response = restTemplate.exchange(...);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // ❌ DO NOT assert repository state (projector disabled)
  }
}
```

### Example 2: Projector-Enabled Test (Event Verification)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class BatchOperationsProjectorIT extends ITFixture {

  @Test
  void batchUpdate_shouldProjectCorrectly() {
    // Fixture publishes event → projector updates repos

    // Execute batch operation
    ResponseEntity<String> response = restTemplate.exchange(...);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // ✅ CAN assert repository state (projector enabled + await)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findById(commitId);
          assertThat(commit).isPresent();
        });
  }
}
```

### Example 3: Custom Dataset Setup

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class CustomDatasetIT extends ITFixture {

  @Autowired
  private CreateDatasetCommandHandler createDatasetHandler;

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false;  // Don't use default setup
  }

  @BeforeEach
  void customSetup() {
    // Create custom dataset
    createDatasetHandler.handle(new CreateDatasetCommand("my-dataset"));

    // Create initial branch via events
    createInitialCommitAndBranchViaEvents("my-dataset");
  }

  @Test
  void test() {
    // Test with custom dataset...
  }
}
```

## Related Documentation

- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Testing Guidelines](../../.claude/CLAUDE.md#testing-guidelines)
- [Test Isolation Pattern](../../.claude/CLAUDE.md#critical-test-isolation-pattern)
- [Parent Task: Fix Batch Test Isolation](.tasks/batch/03-fix-batch-test-isolation-race.md)

## Notes

- This is a **long-term solution** that addresses the root cause
- The **synchronized cleanup** (current solution) is a valid short-term fix
- Event-driven setup provides **better test coverage** and **production parity**
- Migration is **gradual and backward compatible** - no rush needed
- Consider this when adding new `*ProjectorIT` tests in the future

## Session Log

**Session 2025-11-03:**
- Identified need for event-driven test setup as long-term solution
- Documented comprehensive implementation plan
- Created this task for future reference
- Current workaround (synchronized cleanup) is working and sufficient for now
