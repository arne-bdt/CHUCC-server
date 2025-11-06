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

## Understanding Test Intent

**Critical Insight**: RebaseIT and SquashIT are **not** simple CRUD operations. They test **complex graph transformations** that warrant end-to-end verification:

- ✅ Verify HTTP request → Command → Event → Projector → Repository updates
- ✅ Verify commit graph structure correctness after complex operations
- ✅ Verify parent relationships are preserved correctly
- ✅ Verify patches are correctly applied/preserved

**Simplifying these to API-only tests would lose valuable test coverage.**

## Rock-Solid Solution (RECOMMENDED)

**Approach**: Keep end-to-end testing, but fix the setup methodology

**Key Principles:**
- ✅ Extend ITFixture for cleanup synchronization
- ✅ Use unique dataset names per test run (avoid cross-test contamination)
- ✅ **Event-driven setup** - use command handlers (never direct repository writes)
- ✅ Keep projector enabled + await() for verification
- ✅ **Zero architectural violations**

**Implementation Pattern:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Keep enabled!
class RebaseIT extends ITFixture {  // ← Extend ITFixture

  @Override
  protected String getDatasetName() {
    return "rebase-test-" + System.nanoTime();  // ← Unique per run
  }

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false;  // ← Custom commit graph needed
  }

  @BeforeEach
  void setUp() {
    String dataset = getDatasetName();

    // ✅ Event-driven setup using ITFixture helpers
    CommitId commitA = createCommitViaCommand(dataset, List.of(), "Alice", "Initial", "");
    CommitId commitB = createCommitViaCommand(dataset, List.of(commitA), "Alice", "Main B", patchB);
    CommitId commitE = createCommitViaCommand(dataset, List.of(commitB), "Alice", "Main E", patchE);

    createBranchViaCommand(dataset, "main", commitE);

    CommitId commitC = createCommitViaCommand(dataset, List.of(commitA), "Alice", "Feature C", patchC);
    CommitId commitD = createCommitViaCommand(dataset, List.of(commitC), "Alice", "Feature D", patchD);

    createBranchViaCommand(dataset, "feature", commitD);

    // ✅ Wait for projector to finish setup
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(dataset, commitE)).isPresent();
          assertThat(branchRepository.findByDatasetAndName(dataset, "main")).isPresent();
        });
  }

  @Test
  void rebase_shouldRebaseCommitsOntoNewBase() {
    // When: HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Then: Verify HTTP response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // ✅ AND verify end-to-end correctness (commit graph structure)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Branch feature = branchRepository.findByDatasetAndName(dataset, "feature").orElseThrow();
          Commit newHead = commitRepository.findByDatasetAndId(dataset, feature.getCommitId()).orElseThrow();
          assertThat(newHead.message()).isEqualTo("Feature commit D");
          assertThat(newHead.parents().get(0)).isEqualTo(expectedParent);
          // ... more graph structure verification
        });
  }
}
```

**Why This is Rock-Solid:**
- ✅ Preserves test value (end-to-end verification of complex operations)
- ✅ No architectural violations (event-driven setup throughout)
- ✅ Proper isolation (unique dataset names, extends ITFixture)
- ✅ Follows CQRS strictly (no direct repository writes, ever)
- ✅ Clear separation (setup via commands, verification via projector)

## Alternative Patterns (Reference)

### When to Use API-Only Tests
For **simple CRUD operations** that don't require graph structure verification:
- Create graph, update graph, delete graph
- Create branch, update branch reference
- Simple query operations

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class SimpleOperationIT extends ITFixture {
  // No @TestPropertySource - projector DISABLED
  // Only verify HTTP response
}
```

### When to Use End-to-End Tests
For **complex domain operations** that modify graph structure:
- Rebase (changes commit graph topology)
- Squash (merges commits, changes history)
- Merge (creates merge commits with multiple parents)
- Three-way merge with conflict resolution

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class ComplexOperationIT extends ITFixture {
  // Projector enabled - verify commit graph correctness
}
```

## Implementation Plan

### Phase 1: Fix Architecture, Preserve Coverage (Immediate)

**Goal**: Make tests architecturally correct while keeping end-to-end verification

- [ ] Check if ITFixture already has helper methods for event-driven commit/branch creation
- [ ] Add missing helper methods to ITFixture if needed:
  - `createCommitViaCommand(dataset, parents, author, message, patchContent)`
  - `createBranchViaCommand(dataset, branchName, commitId)`
- [ ] Update RebaseIT to extend ITFixture and use event-driven setup
- [ ] Update SquashIT to extend ITFixture and use event-driven setup
- [ ] Use unique dataset names: `"rebase-test-" + System.nanoTime()`
- [ ] **Keep projector enabled** + keep `await()` verification
- [ ] **Zero direct repository writes**

**Files to modify:**
- `src/test/java/org/chucc/vcserver/testutil/ITFixture.java` (add helpers if missing)
- `src/test/java/org/chucc/vcserver/integration/RebaseIT.java`
- `src/test/java/org/chucc/vcserver/integration/SquashIT.java`

**Key Changes:**
1. Extend ITFixture
2. Override `getDatasetName()` to return unique names
3. Override `shouldCreateInitialSetup()` to return false
4. Replace direct repository writes in `setUp()` with command-based helpers
5. Keep all existing `await()` verification (tests end-to-end correctness)

### Phase 2: Documentation & Patterns (Follow-up)

- [ ] Document "when to use end-to-end tests vs API-only tests" in CLAUDE.md
- [ ] Add testing decision table entry for "complex graph operations"
- [ ] Create example of complex commit graph setup in documentation
- [ ] Update CLAUDE.md testing section with pattern guidance

**Documentation Updates:**
- Add section distinguishing simple CRUD from complex graph operations
- Document that rebase/squash/merge warrant end-to-end testing
- Provide concrete examples of both patterns

### Phase 3: Infrastructure (Long-term)

- [ ] Consider `CommitGraphBuilder` utility for fluent commit graph creation:
  ```java
  new CommitGraphBuilder(dataset)
      .commit("A", List.of(), "Alice", "Initial", "")
      .commit("B", List.of("A"), "Alice", "Main B", patchB)
      .branch("main", "B")
      .build();
  ```
- [ ] Extract common commit graph patterns into reusable test utilities
- [ ] Consider graph visualization helpers for debugging complex test scenarios

## Acceptance Criteria

- [ ] All ~398 tests pass consistently in full suite
- [ ] No Kafka timeout errors
- [ ] RebaseIT and SquashIT pass when run together and in full suite
- [ ] Tests follow CQRS/Event Sourcing architecture strictly
- [ ] **Zero direct repository writes** (event-driven setup only)
- [ ] Tests extend ITFixture (cleanup synchronization, unique datasets)
- [ ] Projector kept enabled with await() verification
- [ ] End-to-end commit graph correctness verified

## References

- **CLAUDE.md**: Lines 156-227 (Testing Guidelines)
- **ITFixture.java**: Lines 162-243 (Event-driven setup pattern)
- **Agent Reports**:
  - test-isolation-validator: Identified direct repository writes + projector enabled as critical violation
  - cqrs-compliance-checker: Confirmed architectural inconsistency in mixing direct writes with projector verification

## Test Pattern Decision Table

| Operation Type | Test Pattern | Projector | Verification | Example |
|----------------|--------------|-----------|--------------|---------|
| Simple CRUD | API-only | ❌ Disabled | HTTP response only | GraphStorePutIT |
| Complex graph operations | End-to-end | ✅ Enabled | HTTP + await() + graph structure | RebaseIT, SquashIT |
| Projector behavior | Projector-focused | ✅ Enabled | Event → await() → repository | GraphEventProjectorIT |

## Key Insights

**Why This Plan is Different:**
- **Original plan**: Mistakenly treated rebase/squash as simple CRUD operations
- **This plan**: Recognizes them as complex graph transformations requiring end-to-end verification
- **Key realization**: The problem isn't the verification strategy, it's the setup methodology

**What Was Wrong vs What's Right:**
- ❌ **Wrong**: "These tests should be API-only" → loses valuable graph structure verification
- ✅ **Right**: "These tests should keep end-to-end verification but fix setup" → preserves test value

**Architecture Principle:**
- Direct repository writes violate CQRS/Event Sourcing **always**, not just "when projector enabled"
- Event-driven setup is not optional - it's an architectural requirement
- Test isolation comes from unique datasets + ITFixture, not from disabling projector

## Notes

- Tests currently pass individually but fail in full suite due to cross-test contamination
- Root cause: Direct repository writes + shared static dataset name + missing ITFixture benefits
- The await() patterns for verification are **correct** - they verify end-to-end correctness
- The issue is purely in setup methodology (direct writes instead of event-driven)
- Fix preserves test coverage while achieving architectural correctness
