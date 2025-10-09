# Integration Test Isolation Implementation

## Overview
This directory contains tasks for implementing proper test isolation in Kafka-based integration tests for our CQRS + Event Sourcing architecture.

## Problem
Before this implementation:
- All integration tests shared Kafka topics (vc.default.events, vc.test-dataset.events)
- ReadModelProjector ran in every test class, consuming ALL events from shared topics
- Test class A processed events created by test class B (cross-contamination)
- Error logs: "Branch not found", "Cannot cherry-pick to non-existent branch"
- Difficult debugging due to async event processing side effects

## Solution
Disable ReadModelProjector by default in integration tests, enable only in dedicated projector tests.

**Benefits:**
- ✅ True test isolation - each test verifies only its own behavior
- ✅ No cross-contamination errors
- ✅ Faster API tests (no async projection overhead)
- ✅ Clearer test intent (API vs projection testing)
- ✅ Better debugging (errors from current test only)
- ✅ Industry best practice for CQRS testing

## Task Structure

### Phase 1: Core Implementation (Tasks 01-04)
Modify the system to support conditional projector startup:

- **Task 01**: ✅ COMPLETE - Disable Projector by Default
  - ✅ Add `autoStartup` parameter to ReadModelProjector
  - ✅ Configure application-it.yml
  - ✅ Update IntegrationTestFixture documentation

- **Task 04**: ✅ COMPLETE - Enable Projector in Existing Test
  - ✅ Update GraphEventProjectorIT with @TestPropertySource
  - ✅ Ensure existing projector test still works

### Phase 2: Verification (Tasks 05-07)
Verify the implementation works correctly:

- **Task 05**: Identify Projector-Dependent Tests
  - Search for tests using await()
  - Categorize tests by dependencies
  - Identify tests needing updates

- **Task 06**: Verify Test Suite Without Projector
  - Run all 792 tests
  - Confirm zero failures
  - Check execution time

- **Task 07**: Verify No Cross-Test Errors
  - Detailed log analysis
  - Confirm zero contamination errors
  - Document error elimination

### Phase 3: Comprehensive Testing (Tasks 08-10)
Ensure all event handlers have test coverage:

- **Task 08**: Review Projector Test Coverage
  - Analyze ReadModelProjector event handlers
  - Identify missing tests
  - Plan Tasks 09-10

- **Task 09**: Add Branch Lifecycle Tests
  - BranchCreatedEvent
  - BranchResetEvent
  - BranchRebasedEvent

- **Task 10**: Add VC Operation Tests
  - RevertCreatedEvent
  - CherryPickedEvent
  - CommitsSquashedEvent

### Phase 4: Documentation (Task 11)
Document the testing strategy:

- **Task 11**: Update CLAUDE.md
  - Explain testing patterns
  - Provide examples
  - Document common mistakes
  - Add decision table

## Execution Order

**Critical path:**
1. Task 01 (core implementation)
2. Task 04 (fix existing test)
3. Task 06 (verify all tests pass)
4. Task 07 (verify isolation)
5. Task 11 (documentation)

**Parallel opportunities:**
- Task 05 can run while implementing Task 04
- Task 08 can run while Task 06-07 are executing
- Tasks 09-10 can be implemented in parallel by different developers

**Dependencies:**
- Task 04 depends on Task 01
- Tasks 06-07 depend on Task 04
- Tasks 09-10 depend on Task 08
- Task 11 depends on Tasks 01-10 completion

## Success Criteria

- [x] All tests pass without projector enabled (VERIFIED 2025-10-09)
- [x] GraphEventProjectorIT passes with projector enabled (VERIFIED 2025-10-09)
- [ ] Zero error logs about cross-test contamination (Need Task 07 documentation)
- [ ] All 10 event handler methods have dedicated tests (Need Tasks 08-10)
- [x] CLAUDE.md documents testing strategy (Mostly complete, needs Task 11 polish)
- [x] Zero Checkstyle/SpotBugs violations (VERIFIED 2025-10-09)

**Status as of 2025-10-09**: Core implementation COMPLETE ✅. Tasks 01 & 04 done. Tasks 05-11 need analysis/documentation/additional tests. See STATUS.md for details.

## Estimated Timeline

- **Phase 1**: 1 hour (Tasks 01, 04)
- **Phase 2**: 1 hour (Tasks 05-07)
- **Phase 3**: 3-4 hours (Tasks 08-10)
- **Phase 4**: 1 hour (Task 11)
- **Total**: 6-7 hours

## Testing Strategy After Implementation

### API Layer Tests (90%)
```java
@SpringBootTest
@ActiveProfiles("it")
class MyApiTest extends IntegrationTestFixture {
  // Projector DISABLED by default

  @Test
  void shouldReturn200() {
    // Verify HTTP response only
    // No repository assertions
  }
}
```

### Projector Tests (10%)
```java
@SpringBootTest
@ActiveProfiles("it")
@TestPropertySource(properties = "kafka.listener.enabled=true")
class GraphEventProjectorIT extends IntegrationTestFixture {
  // Projector ENABLED explicitly

  @Test
  void shouldProjectEvent() {
    // Publish event
    // Use await() for async projection
    // Assert repository state
  }
}
```

## Architecture

### Before
```
Test A                Test B                Test C
   ↓                     ↓                     ↓
Commands            Commands            Commands
   ↓                     ↓                     ↓
Events to Kafka (vc.default.events)
   ↓                     ↓                     ↓
Projector A ←───────────┼─────────────────────┘
   ↓                    │
Branch "main"          │
                      │
                 Events from Test B/C!
                 → "Branch not found" errors
```

### After
```
Test A (API)        Test B (API)        Test C (Projector)
   ↓                     ↓                     ↓
Commands            Commands            Commands
   ↓                     ↓                     ↓
Events to Kafka (vc.default.events)
   ↓                     ↓                     ↓
No Projector        No Projector        Projector C ←────┐
   ↓                     ↓                     ↓           │
Tests HTTP          Tests HTTP          Branch "main"    │
Response            Response                              │
                                       Only processes    │
                                       its own events ───┘
```

## Configuration Files

### application-it.yml
```yaml
kafka:
  listener:
    enabled: false  # Disabled by default
```

### IntegrationTestFixture.java
```java
/**
 * Base class for integration tests.
 * ReadModelProjector is DISABLED by default.
 * To enable: @TestPropertySource(properties = "kafka.listener.enabled=true")
 */
public abstract class IntegrationTestFixture { ... }
```

### ReadModelProjector.java
```java
@KafkaListener(
    topicPattern = "vc\\..*\\.events",
    groupId = "read-model-projector",
    containerFactory = "kafkaListenerContainerFactory",
    autoStartup = "${kafka.listener.enabled:true}"  // Conditional startup
)
public void handleEvent(VersionControlEvent event) { ... }
```

## References

- **Industry Best Practices**: Stack Overflow, Spring Kafka documentation
- **Pattern**: Conditional KafkaListener with @TestPropertySource
- **Inspiration**: CQRS testing guides from Spring Boot community
- **Similar Projects**: Event Sourcing projects using Kafka + Testcontainers

## Troubleshooting

**If tests fail after implementation:**
1. Check Task 04 completed (GraphEventProjectorIT has @TestPropertySource)
2. Verify application-it.yml has `kafka.listener.enabled: false`
3. Run single test to isolate issue: `mvn test -Dtest=FailingTest`
4. Check logs for clues about projector state

**If error logs persist:**
1. Verify ReadModelProjector has `autoStartup` parameter
2. Check no tests override properties to enable projector unintentionally
3. Confirm Kafka consumer groups are unique per test class

## Next Steps

After completing all tasks:
1. Run full test suite: `mvn clean test`
2. Verify build success and zero errors
3. Review logs to confirm no contamination
4. Update team on new testing patterns
5. Monitor for any issues in CI/CD

## Notes

- This implementation follows Spring Kafka recommendations
- Aligns with CQRS testing best practices
- Minimal code changes (3 files modified)
- Maximum impact (complete test isolation)
- Scalable pattern for future growth
