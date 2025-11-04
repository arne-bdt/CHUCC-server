# Kafka Listener Investigation - Event-Driven Test Setup Issue

**Date:** 2025-11-04
**Status:** ✅ RESOLVED
**Priority:** HIGH - Blocking event-driven test migration

**Resolution:** handleDatasetCreated() was only updating cache, not creating materialized graph. Fixed by adding materializedBranchRepository.createBranch() call in ReadModelProjector:548-569.

## Problem Statement

ReadModelProjector's @KafkaListener is **not consuming events** in integration tests, even when explicitly enabled with `projector.kafka-listener.enabled=true`. This prevents event-driven test setup from working.

## Context

We attempted to implement event-driven test setup (Session 5 of implementation plan) but discovered that the Kafka consumer never receives events published during test setup.

### What Works

✅ CreateDatasetCommandHandler executes successfully
✅ Kafka topics are created (`vc.default.events`)
✅ Events are published to Kafka (`.get()` completes without error)
✅ Test properly sets `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`

### What Fails

❌ ReadModelProjector never consumes events from Kafka
❌ `await()` times out after 10 seconds
❌ Commit/Branch/Materialized graph never appear in repositories

## Files Modified

### 1. ITFixture.java
**Changes:**
- Added `@Autowired EventPublisher kafkaEventPublisher` (line 60-61)
- Added `@Autowired CreateDatasetCommandHandler createDatasetCommandHandler` (line 63-64)
- Rewrote `createInitialCommitAndBranchViaEvents()` to use CreateDatasetCommandHandler (lines 222-263)

**Key Implementation:**
```java
protected void createInitialCommitAndBranchViaEvents(String dataset) {
  // Create dataset using command handler, which automatically:
  // 1. Creates Kafka topics (vc.{dataset}.events and vc.{dataset}.events.dlq)
  // 2. Creates initial commit with empty patch
  // 3. Creates main branch pointing to initial commit
  // 4. Publishes DatasetCreatedEvent to Kafka
  if (createDatasetCommandHandler != null) {
    CreateDatasetCommand command = new CreateDatasetCommand(...);
    VersionControlEvent event = createDatasetCommandHandler.handle(command);
    if (event instanceof DatasetCreatedEvent datasetEvent) {
      initialCommitId = CommitId.of(datasetEvent.initialCommitId());
    }
  }

  // Mode 2: Projector enabled - await() for async projection
  if (projectorEnabled) {
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // TIMES OUT HERE - projector never consumes events
          assertThat(commitRepository.findByDatasetAndId(dataset, initialCommitId)).isPresent();
          assertThat(branchRepository.findByDatasetAndName(dataset, "main")).isPresent();
          assertThat(materializedBranchRepo.exists(dataset, "main")).isTrue();
        });
  }
}
```

### 2. ProjectorTimingObservationIT.java (Created)
**Purpose:** Observational test to reproduce and diagnose the materialization timing issue

**Test 1:** `observeProjectorMaterializationTiming()`
- Logs timestamps when commit/branch/graph become available
- Attempts batch operation after setup
- **Result:** Timeout after 15 seconds waiting for graph

**Test 2:** `verifyMaterializedGraphTimingOnly()`
- Simple test just checking graph availability
- **Result:** Timeout after 15 seconds waiting for graph

## Investigation Plan

### Possible Causes (Ordered by Likelihood)

1. **@KafkaListener not auto-starting in test context**
   - Spring Boot test configuration issue
   - Container factory not initialized
   - Listener disabled despite property setting

2. **Topic pattern not matching created topics**
   - Pattern: `vc\..*\.events`
   - Created topic: `vc.default.events`
   - Potential regex mismatch

3. **Consumer group configuration issue**
   - Unique consumer groups per test class
   - Group ID conflicts
   - Offset management problems

4. **Kafka container not ready**
   - Listener starts before Kafka fully initialized
   - Topic creation timing issue
   - TestContainers startup race

5. **Event deserialization failure**
   - Silent deserialization errors
   - No error logs visible in test output

---

## Investigation Steps

###Step 1: Verify @KafkaListener Auto-Startup
**Goal:** Confirm if the listener container actually starts

**Status:** ✅ IN PROGRESS

**Actions Taken:**
1. ✅ Added constructor logging to ReadModelProjector (line 152)
2. ✅ Added @PostConstruct method with logging (lines 159-163)
3. ✅ Added handleEvent() entry logging (lines 193-198)
4. ⏳ Running test to check for log output

**Expected Duration:** 15-20 minutes

**Success Criteria:**
- Logs show listener container starting
- OR logs show why it's not starting

**Findings:**
- Test compiled and ran successfully with new logging
- No "ReadModelProjector" log messages visible in initial grep check
- Need to check test logs directly (logback-test.xml may suppress INFO logs)

---

### Step 2: Verify Topic Pattern Matching
**Goal:** Ensure pattern `vc\..*\.events` matches `vc.default.events`

**Actions:**
1. Add logging to show which topics listener is subscribed to
2. Verify topic exists in Kafka (using AdminClient or kafka-topics.sh)
3. Test regex pattern in isolation
4. Check if consumer actually subscribes to the topic

**Expected Duration:** 10-15 minutes

**Success Criteria:**
- Logs show successful topic subscription
- OR identify pattern mismatch

---

### Step 3: Check Consumer Group Configuration
**Goal:** Verify consumer group setup is correct

**Actions:**
1. Check consumer group ID in test logs
2. Verify unique group ID per test class
3. Check offset reset strategy (`earliest` vs `latest`)
4. Verify no existing offsets blocking consumption

**Expected Duration:** 15-20 minutes

**Success Criteria:**
- Consumer group properly configured
- Consuming from beginning of topic

---

### Step 4: Verify Kafka Container Readiness
**Goal:** Ensure Kafka is fully ready before listener starts

**Actions:**
1. Add explicit wait for Kafka readiness in test setup
2. Check if topics exist before publishing events
3. Verify broker is reachable
4. Add health check before test proceeds

**Expected Duration:** 20-25 minutes

**Success Criteria:**
- Kafka proven ready before events published
- OR identify startup race condition

---

### Step 5: Check Event Deserialization
**Goal:** Verify events can be deserialized correctly

**Actions:**
1. Enable DEBUG logging for Kafka consumer
2. Check for deserialization errors in logs
3. Verify event schema compatibility
4. Test manual deserialization

**Expected Duration:** 15-20 minutes

**Success Criteria:**
- Events deserialize successfully
- OR identify deserialization errors

---

## Current Session Progress

### Completed
- ✅ Diagnosed that events ARE being consumed by ReadModelProjector (projector working correctly!)
- ✅ Verified event publishing works (Kafka acknowledges)
- ✅ Verified Kafka topics are created
- ✅ Created observational test to reproduce issue
- ✅ Ruled out "missing Kafka topics" as the cause
- ✅ Modified ITFixture to use CreateDatasetCommandHandler (creates topics properly)
- ✅ Added comprehensive logging to ReadModelProjector:
  - Constructor logging (line 152)
  - @PostConstruct initialization logging (lines 159-163)
  - handleEvent() entry logging (lines 193-198)
- ✅ Enabled INFO logging for ReadModelProjector in logback-test.xml
- ✅ Confirmed @KafkaListener IS consuming events (logs show handleEvent() called)
- ✅ Discovered root cause: handleDatasetCreated() only updated cache, didn't create materialized graph
- ✅ Implemented fix: Added materializedBranchRepository.createBranch() to handleDatasetCreated()

### In Progress
- ⏳ Fixing handleDatasetCreated() to create materialized graph
  - Discovered projector only updates cache, doesn't save to repositories
  - Added materialized graph creation to handleDatasetCreated()
  - Still investigating why test times out

### Blocked
- Sessions 5-8 of event-driven migration plan (blocked by this issue)

### Key Architectural Findings

**Discovery 1: CQRS Write Pattern**
CreateDatasetCommandHandler (command side) ALWAYS saves to repositories, regardless of projector mode. This is correct CQRS architecture - commands handle writes synchronously.

**Discovery 2: Projector Role Incomplete**
handleDatasetCreated() was only updating cache, not creating materialized graph. The projector is responsible for maintaining read models (materialized graphs), not just caches.

**Discovery 3: Event-Driven Test Pattern Requirement**
For projector-enabled tests to work, the projector must fully reconstruct all necessary state (commit, branch, AND materialized graph) from events.

**Discovery 4: Original Assumption Was Wrong**
Initial assumption: "Events are not reaching projector" → **FALSE**
Actual problem: "Projector receives events but doesn't create materialized graph" → **TRUE**

### Next Actions
1. ✅ DONE: Enable INFO logging for ReadModelProjector
2. ✅ DONE: Confirm events reaching projector
3. ✅ DONE: Identify missing materialized graph creation
4. ✅ DONE: Implement fix in handleDatasetCreated()
5. ⏳ PENDING: Verify fix resolves test timeouts
6. ⏳ PENDING: Clean up temporary logging and test code
7. ⏳ PENDING: Document the pattern for future event handlers

---

## References

- **Implementation Plan:** `.tasks/batch/05-event-driven-setup-implementation-plan.md`
- **ReadModelProjector:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:170-175`
- **Test Configuration:** `src/test/resources/application-it.properties`
- **Kafka Properties:** `src/main/resources/application.yml` (kafka.bootstrap-servers, spring.kafka.consumer)

---

## Notes

- BatchOperationsProjectorIT (the "flaky test") suffers from the SAME issue
- This is NOT a race condition - it's a complete failure to consume
- The projector works fine in production and in other projector-enabled tests that were already working
- Need to understand what's different about CreateDatasetCommandHandler approach vs existing working tests

---

## Resolution (2025-11-04)

### Root Cause Identified

The issue was **NOT** that events weren't reaching the projector (as initially suspected). The real problem was that `handleDatasetCreated()` was incomplete - it only updated the dataset cache but **did not create the materialized graph**.

### The Fix

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java:548-569`

Added materialized graph creation to `handleDatasetCreated()`:

```java
void handleDatasetCreated(DatasetCreatedEvent event) {
  logger.info("Processing DatasetCreatedEvent: dataset={}, mainBranch={}, initialCommit={}",
      event.dataset(), event.mainBranch(), event.initialCommitId());

  // Update latest commit tracking for cache optimization
  datasetService.updateLatestCommit(
      event.dataset(),
      event.mainBranch(),
      CommitId.of(event.initialCommitId())
  );

  // Create empty materialized graph for main branch
  // This ensures the graph exists for subsequent SPARQL operations
  materializedBranchRepo.createBranch(    // ← THIS WAS MISSING!
      event.dataset(),
      event.mainBranch(),
      java.util.Optional.empty()  // Empty graph for initial commit
  );

  logger.info("Dataset {} created and tracked in cache, materialized graph initialized",
      event.dataset());
}
```

### Why This Matters

In CQRS + Event Sourcing architecture, the **projector** is responsible for reconstructing ALL read-model state from events. When a test uses event-driven setup (publishing DatasetCreatedEvent), the projector must:

1. ✅ Update the dataset cache (was already doing this)
2. ✅ Create the initial commit (handled by separate CommitCreatedEvent)
3. ✅ Create the branch record (handled by separate BranchCreatedEvent)
4. ✅ **Create the materialized graph** (was MISSING!)

Without step 4, tests that await materialized graph availability would timeout.

### Verification

Test: `ProjectorTimingObservationIT`
- **Before fix:** Timeout after 10 seconds waiting for materialized graph
- **After fix:** Both tests pass (Tests run: 2, Failures: 0, Errors: 0)

Log evidence:
```
Dataset default created and tracked in cache, materialized graph initialized
```

### Cleanup Performed

1. **Removed verbose logging** from `handleEvent()` (ReadModelProjector:183-188)
2. **Deleted observational test** (`ProjectorTimingObservationIT.java`)
3. **Removed temporary logger config** from `logback-test.xml`
4. **Kept constructor logging** (ReadModelProjector:152) - useful operational visibility

### Impact

This fix unblocks:
- ✅ Event-driven test setup pattern (Sessions 5-8 of implementation plan)
- ✅ BatchOperationsProjectorIT (the "flaky test" that suffered from the same issue)
- ✅ All future projector-enabled integration tests using CreateDatasetCommandHandler

### Lessons Learned

1. **Always check projector completeness:** When adding new event handlers, ensure they fully reconstruct ALL necessary read-model state
2. **Event-driven testing reveals gaps:** Using events for test setup exposed that handleDatasetCreated() was incomplete
3. **Materialized graphs are part of read model:** Projectors must create them, not just rely on command-side creation
