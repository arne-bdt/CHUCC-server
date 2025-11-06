# Task: Fix Integration Test Isolation Issues

## Status
- **Created**: 2025-11-06
- **Status**: TODO
- **Priority**: High
- **Blocked by**: None
- **Related to**: 02-refactor-squash-rebase-to-pure-cqrs.md (completed)

## Problem Summary

RebaseIT and SquashIT integration tests currently:
- ✅ Pass individually when run alone
- ❌ Fail when run as part of full test suite (398 tests)
- Use architectural anti-patterns identified by automated agents

### Observed Failures
- **SquashIT.squash_shouldReturn200_whenSquashingTwoCommits** fails with commit ID mismatch
- Kafka timeout errors: "Expiring 1 record(s) for vc.duplicate-test.events"
- Tests run: 398, Failures: 1 (down from 3-4 originally)

## Root Causes (Agent Analysis)

### 1. Direct Repository Writes with Projector Enabled
**Severity**: CRITICAL - Architectural Violation

Both RebaseIT and SquashIT use direct repository writes in `@BeforeEach setUp()` while having projector enabled at class level:

```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class RebaseIT {
  @BeforeEach
  void setUp() {
    // VIOLATION: Direct repository writes bypass CQRS/Event Sourcing
    commitRepository.save(DATASET_NAME, commitA, RDFPatchOps.emptyPatch());
    branchRepository.save(DATASET_NAME, mainBranch);
    // ...
  }
}
```

**Why this violates architecture:**
- Bypasses CQRS/Event Sourcing pattern
- Can cause nested transaction errors with projector enabled
- According to CLAUDE.md line 242: "Direct repository writes are anti-patterns"
- ITFixture provides `createInitialCommitAndBranchViaEvents()` for event-driven setup

### 2. Tests Don't Extend ITFixture
**Impact**: Missing test isolation benefits
- No automatic cleanup synchronization (CLEANUP_LOCK)
- No event-driven setup utilities
- Duplicate Kafka configuration code
- Missing recommended base class benefits

### 3. Static Dataset Name Causes Cross-Test Contamination
Both tests use `DATASET_NAME = "test-dataset"` (static constant):
- Multiple tests write to same dataset name
- Shared Kafka topics (vc.test-dataset.events)
- No cleanup synchronization between tests
- Race conditions when tests run in parallel

### 4. Mixing Test Patterns
Tests incorrectly mix:
- **Setup**: Direct repository writes (bypasses write-side)
- **Verification**: Projector-based async checks (tests read-side)

This architectural inconsistency creates race conditions and test flakiness.

## Solution Options

### Option 1: Convert to Pure API Layer Tests (RECOMMENDED)

**Approach**: Disable projector, test only HTTP contract

**Benefits:**
- Aligns with 90% of test patterns in codebase
- No test isolation issues
- Fast test execution (no async waiting)
- Clear separation of concerns

**Implementation:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class RebaseIT extends ITFixture {
  // No @TestPropertySource - projector DISABLED by default

  @Override
  protected String getDatasetName() {
    return "rebase-test-" + System.nanoTime(); // Unique per run
  }

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false; // Custom setup needed
  }

  @BeforeEach
  void setUp() {
    // Use event-driven setup via createInitialCommitAndBranchViaEvents()
    // Or use HTTP API to create test data
  }

  @Test
  void rebase_shouldReturn202Accepted() {
    // Act: HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: API response ONLY (no repository checks)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(json.get("newHead").asText()).isNotNull();
    // Note: Repository updates handled by ReadModelProjector (tested separately)
  }
}
```

### Option 2: Split into API + Projector Tests

**API Test** (projector disabled):
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class RebaseIT extends ITFixture {
  @Test
  void rebase_shouldReturn202Accepted() {
    // Test HTTP contract only
  }
}
```

**Projector Test** (projector enabled):
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class RebaseProjectorIT extends ITFixture {
  @Test
  void branchRebasedEvent_shouldUpdateRepositories() {
    // Publish event directly, verify projection with await()
  }
}
```

### Option 3: Provide Test Command Handlers

Create command handlers specifically for test data creation:

```java
/**
 * Test-only command handler for creating commits directly.
 * Should ONLY be used in integration tests for complex setup scenarios.
 */
@Component
@Profile("it")
public class CreateTestCommitCommandHandler {
  public CommitCreatedEvent handle(CreateTestCommitCommand command) {
    // Create event and publish to Kafka
    // Projector will update repositories async
  }
}
```

## Implementation Plan

### Phase 1: Quick Fix (Immediate)
- [ ] Disable projector in RebaseIT and SquashIT
- [ ] Remove all `await()` and repository assertions from tests
- [ ] Only verify HTTP API responses (status codes, JSON body)
- [ ] Document that repository verification happens in separate projector tests

**Files to modify:**
- `src/test/java/org/chucc/vcserver/integration/RebaseIT.java`
- `src/test/java/org/chucc/vcserver/integration/SquashIT.java`

**Changes:**
1. Remove `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
2. Remove `await()` blocks
3. Remove all repository verification code
4. Keep direct repository writes in setUp (acceptable when projector disabled)

### Phase 2: Proper Architecture (Follow-up)
- [ ] Make RebaseIT and SquashIT extend ITFixture
- [ ] Use unique dataset names per test run
- [ ] Convert setUp to event-driven approach (use command handlers or HTTP API)
- [ ] Create separate projector tests if repository verification needed
- [ ] Remove duplicate Kafka configuration code

### Phase 3: Infrastructure (Long-term)
- [ ] Provide TestDataBuilder utility for creating complex commit graphs via events
- [ ] Document test patterns in CLAUDE.md
- [ ] Create examples of correct event-driven test setup
- [ ] Add Checkstyle rule to detect direct repository writes in integration tests

## Acceptance Criteria

- [ ] All 398 tests pass consistently in full suite
- [ ] No Kafka timeout errors
- [ ] RebaseIT and SquashIT pass when run together
- [ ] Tests follow patterns documented in CLAUDE.md lines 156-227
- [ ] No direct repository writes when projector enabled
- [ ] Tests extend ITFixture
- [ ] Event-driven setup used for all test data creation

## References

- **CLAUDE.md**: Lines 156-227 (Testing Guidelines)
- **ITFixture.java**: Lines 162-243 (Event-driven setup pattern)
- **Agent Reports**:
  - test-isolation-validator: Identified direct repository writes + projector enabled as critical violation
  - cqrs-compliance-checker: Confirmed architectural inconsistency in mixing direct writes with projector verification

## Notes

- Tests currently pass individually but fail in full suite due to cross-test contamination
- The core CQRS fixes (await patterns) are correct; issue is test setup methodology
- Quick fix (Phase 1) can be done immediately; proper fix (Phase 2) requires more refactoring
