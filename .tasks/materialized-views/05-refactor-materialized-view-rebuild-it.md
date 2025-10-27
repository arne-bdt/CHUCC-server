# Task 05: Refactor MaterializedViewRebuildIT Test

## Status
**Priority:** Low (Technical Debt)
**Estimated Time:** 1-2 hours
**Category:** Test Quality Improvement

## Overview

The `MaterializedViewRebuildIT` integration test currently violates codebase test patterns by not extending the `ITFixture` base class. This leads to code duplication and inconsistency with other integration tests in the codebase.

## Problem

As identified by the test-isolation-validator agent:

1. **Missing Base Class Extension**: Test doesn't extend `ITFixture`
2. **Duplicate Kafka Setup**: Manually declares Kafka container instead of using inherited one
3. **Manual Repository Cleanup**: Duplicates ITFixture cleanup logic
4. **Manual Initial Setup**: Creates initial commit manually instead of using ITFixture helpers
5. **Weak Async Verification**: Uses trivial `await()` condition in health endpoint test

## Current State

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedViewRebuildIT {
  // Manual Kafka setup (lines 52-59)
  // Manual repository cleanup (lines 83-85)
  // Manual initial commit creation (lines 91-112)
  // ...
}
```

## Requirements

Refactor the test to follow codebase patterns:

1. **Extend ITFixture base class**
2. **Remove duplicate Kafka setup** (use inherited `kafkaContainer`)
3. **Remove manual repository cleanup** (use ITFixture's `setUpIntegrationTestFixture()`)
4. **Use ITFixture helper methods** (e.g., `createCommit()`, `createSimplePatch()`)
5. **Fix weak await() condition** in health endpoint test
6. **Maintain all existing test coverage** (6 tests must still pass)

## Implementation Steps

### Step 1: Extend ITFixture

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedViewRebuildIT extends ITFixture {  // ← Add extends

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private KafkaProperties kafkaProperties;

  // Remove: kafka container (inherited from ITFixture)
  // Remove: @DynamicPropertySource (inherited from ITFixture)
  // Remove: branchRepository/commitRepository autowiring (inherited from ITFixture)

  private static final String REBUILD_DATASET = "rebuild-test-dataset";

  @Override
  protected String getDatasetName() {
    return REBUILD_DATASET;
  }

  @BeforeEach
  void setUp() throws Exception {
    // ITFixture.setUpIntegrationTestFixture() already called automatically
    ensureTopicExists(REBUILD_DATASET);
  }

  // ... tests unchanged
}
```

### Step 2: Remove Duplicate Kafka Setup

Delete lines 52-59:
```java
// DELETE THIS:
@Container
private static KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

@DynamicPropertySource
static void kafkaProperties(DynamicPropertyRegistry registry) {
  registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
  registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
}
```

Use inherited `kafkaContainer` from ITFixture instead.

### Step 3: Remove Manual Repository Cleanup

Delete lines 83-85:
```java
// DELETE THIS:
branchRepository.deleteAllByDataset(DEFAULT_DATASET);
commitRepository.deleteAllByDataset(DEFAULT_DATASET);
```

ITFixture already does this in `setUpIntegrationTestFixture()`.

### Step 4: Simplify Initial Commit Setup

Replace lines 91-112 with:
```java
// Use ITFixture helper (already creates initial commit by default)
// Or override shouldCreateInitialSetup() to return true
```

### Step 5: Fix Weak Async Verification

Replace weak await() condition in `healthEndpoint_shouldReportGraphCountAfterCommits()`:

```java
// BEFORE (lines 290-293):
await().atMost(Duration.ofSeconds(10))
    .pollDelay(Duration.ofMillis(500))
    .until(() -> true);  // ⚠️ Always true

// AFTER:
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() -> {
      var commit = commitRepository.findByDatasetAndId(REBUILD_DATASET, commitId);
      assertThat(commit).isPresent();
    });
```

### Step 6: Verify Tests Still Pass

Run integration tests:
```bash
mvn -q test -Dtest=MaterializedViewRebuildIT
```

All 6 tests must pass:
- ✅ `healthEndpoint_shouldReportMaterializedViewsStatus()`
- ✅ `rebuildEndpoint_shouldRequireConfirmation()`
- ✅ `rebuildEndpoint_shouldRebuildBranchFromCommitHistory()`
- ✅ `rebuildEndpoint_shouldReturnErrorForNonexistentDataset()`
- ✅ `rebuildEndpoint_shouldReturnErrorForNonexistentBranch()`
- ✅ `healthEndpoint_shouldReportGraphCountAfterCommits()`

## Success Criteria

- [ ] Test extends `ITFixture` base class
- [ ] No duplicate Kafka setup code
- [ ] No manual repository cleanup code
- [ ] Uses ITFixture helper methods where applicable
- [ ] Strong async verification (no trivial `await()` conditions)
- [ ] All 6 tests pass
- [ ] Consistent with other 39 integration tests in codebase
- [ ] Zero quality violations
- [ ] Full build passes: `mvn -q clean install`

## Files to Modify

- `src/test/java/org/chucc/vcserver/integration/MaterializedViewRebuildIT.java`

## Reference

- **ITFixture Base Class:** `src/test/java/org/chucc/vcserver/testutil/ITFixture.java`
- **Reference Pattern:** `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`
- **Test-Isolation Validator Report:** See Task 04 completion review

## Benefits

- **Reduced Code Duplication:** ~40 lines removed
- **Consistency:** Matches patterns used by other 39 integration tests
- **Maintainability:** Changes to ITFixture will propagate automatically
- **Proper Consumer Group Isolation:** Uses ITFixture's per-test group-id setup
- **Stronger Async Verification:** Actual projection verification instead of delays

## Notes

- This is optional technical debt (test works but violates patterns)
- Medium severity (affects maintainability, not functionality)
- Can be deferred if higher-priority tasks are pending
- Estimated time: 1-2 hours (straightforward refactoring)
