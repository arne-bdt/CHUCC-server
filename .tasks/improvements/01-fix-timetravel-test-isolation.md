# Fix TimeTravelQueryIntegrationTest - Test Isolation Issues

## Background

The Test Isolation Validator identified critical violations in `TimeTravelQueryIntegrationTest`:
- ❌ Does NOT extend `IntegrationTestFixture` (uses manual Kafka setup)
- ❌ Direct repository access without projector enabled
- ❌ No `await()` used for async verification

## Current Issues

From `src/test/java/org/chucc/vcserver/integration/TimeTravelQueryIntegrationTest.java`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class TimeTravelQueryIntegrationTest {  // ❌ Should extend IntegrationTestFixture

  private static KafkaContainer kafkaContainer;  // ❌ Manual Kafka setup

  @BeforeEach
  void setUp() {
    // ❌ Direct repository access
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Creating commits manually without projector
    commitRepository.save(DATASET_NAME, commit1, RDFPatchOps.emptyPatch());
    // ...
  }
}
```

## Tasks

### 1. Refactor Test Structure

- [ ] Extend `IntegrationTestFixture` instead of manual Kafka setup
- [ ] Remove manual `KafkaContainer` and `@DynamicPropertySource`
- [ ] Change `DATASET_NAME` from "default" if needed (or use fixture's default)

### 2. Fix Test Pattern

Choose the correct pattern for each test:

**For API-only tests** (most tests):
- [ ] Test HTTP responses only
- [ ] Do NOT access repositories
- [ ] Do NOT enable projector
- [ ] Do NOT use `await()`

**For projector tests** (if any):
- [ ] Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
- [ ] Use `await()` for async verification
- [ ] Access repositories only after awaiting

### 3. Update Test Setup

- [ ] Remove direct `branchRepository.deleteAllByDataset()` calls
- [ ] Remove direct `commitRepository.save()` calls
- [ ] Use fixture's `initialCommitId` if needed
- [ ] Consider creating commits via HTTP API if needed

### 4. Re-enable Disabled Tests

Currently 3 tests are `@Disabled`:
- [ ] `queryWithAsOf_shouldAcceptParameter()` - asOf + branch combination
- [ ] `queryWithAsOfOnly_shouldAcceptParameter()` - asOf-only parameter
- [ ] `queryWithAsOfAfterAllCommits_shouldAcceptParameter()` - future timestamp

Investigate and fix the asOf resolution issues, then re-enable.

## Decision: Pattern Choice

**Recommended**: Convert ALL tests to **API Layer Pattern** (projector disabled)
- Tests verify HTTP contract and parameter validation
- No repository access needed
- Cleaner and faster tests

**Alternative**: If testing projector behavior is needed, create separate `TimeTravelProjectorIT` test class.

## Acceptance Criteria

- [ ] Test extends `IntegrationTestFixture`
- [ ] No manual Kafka setup
- [ ] No direct repository access in API-only tests
- [ ] Consistent with other integration tests (e.g., `SelectorValidationIntegrationTest`)
- [ ] All tests passing
- [ ] 3 disabled tests investigated and either fixed or documented

## References

- Test Isolation Validator report
- `IntegrationTestFixture.java` - base class
- `SelectorValidationIntegrationTest.java` - correct API layer pattern
- `GraphEventProjectorIT.java` - correct projector test pattern
