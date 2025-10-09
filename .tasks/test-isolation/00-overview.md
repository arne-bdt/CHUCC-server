# Integration Test Isolation - Overview

## Purpose
Implement proper test isolation for Kafka-based integration tests in a CQRS + Event Sourcing architecture to prevent cross-test event contamination and eliminate false positive error logs.

## Problem Statement
Currently, all integration test classes share:
- Same Kafka container (singleton pattern - correct)
- Same Kafka topics (vc.default.events, vc.test-dataset.events)
- ReadModelProjector runs in every test class, consuming ALL events from shared topics

This causes:
- Cross-test event contamination (test A processes events from test B)
- Error logs: "Branch not found: feature in dataset: test-dataset"
- ReadModelProjector processing events from other test classes
- Difficult debugging due to async event processing side effects

## Root Cause
With shared Kafka topics, events from all test classes accumulate in the same topic. Each test class starts its own ReadModelProjector, which consumes events from the beginning (auto-offset-reset: earliest) or from where its unique consumer group left off (auto-offset-reset: latest).

Even with unique consumer groups, the projector in test class A will process events created by test class B if they share topics.

## Solution Approach: Conditional KafkaListener

Following Spring Boot + Kafka best practices for CQRS testing:

1. **Disable projector by default** in integration tests
   - Add `autoStartup = "${kafka.listener.enabled:true}"` to @KafkaListener
   - Set `kafka.listener.enabled: false` in application-it.yml

2. **Separate testing concerns**:
   - **API Layer Tests** (90% of tests): Test synchronous HTTP responses, projector disabled
   - **Projector Tests** (dedicated tests): Test async event projection, projector enabled
   - **E2E Tests** (optional): Full CQRS flow with @DirtiesContext

3. **Comprehensive projector testing**:
   - Ensure ALL event handlers in ReadModelProjector have dedicated tests
   - Use @TestPropertySource to enable projector only in projector-specific tests

## Architecture Alignment

This approach follows industry best practices:
- **Gary Russell** (Spring Kafka lead): "Use unique topic names in each test" (not feasible for us due to hardcoded dataset)
- **Testcontainers guide**: Use shared container, unique topics per test
- **CQRS testing pattern**: Test command side and query side separately

## Benefits

1. **True Test Isolation**: Each test verifies only its own behavior
2. **No False Positive Errors**: No more "Branch not found" logs from other tests
3. **Faster Tests**: API tests don't wait for async projection
4. **Clearer Intent**: Test names clearly indicate what's being tested
5. **Better Debugging**: Errors are always from the current test, not side effects
6. **Industry Standard**: Follows recommended CQRS testing patterns

## Task Execution Order

Tasks are organized into 5 phases:

1. **Phase 1: Core Implementation** (Tasks 01-03)
   - Modify ReadModelProjector to support conditional startup
   - Configure application-it.yml to disable by default
   - Update documentation

2. **Phase 2: Fix Existing Projector Test** (Task 04)
   - Update GraphEventProjectorIT to explicitly enable projector

3. **Phase 3: Verification** (Tasks 05-07)
   - Search for tests that might depend on async projection
   - Run full test suite to verify isolation works
   - Confirm error logs are eliminated

4. **Phase 4: Comprehensive Projector Testing** (Tasks 08-10)
   - Identify missing event handler tests
   - Add projector tests for all version control operations
   - Ensure 100% coverage of ReadModelProjector event handlers

5. **Phase 5: Documentation** (Task 11)
   - Update CLAUDE.md with testing strategy
   - Document when to enable/disable projector

## Success Criteria

- [ ] All 792 tests pass without projector enabled
- [ ] GraphEventProjectorIT passes with projector enabled
- [ ] Zero error logs about "Branch not found" or "Cannot cherry-pick to non-existent branch"
- [ ] All 10 event handler methods in ReadModelProjector have dedicated tests
- [ ] CLAUDE.md documents the testing strategy
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies

- Existing KafkaTestContainers singleton pattern (already implemented)
- Existing IntegrationTestFixture base class (already implemented)
- Existing GraphEventProjectorIT (already implemented)

## Estimated Complexity

Medium (4-6 hours total)
- Phase 1-3: 2 hours (implementation + verification)
- Phase 4: 2-3 hours (comprehensive projector tests)
- Phase 5: 1 hour (documentation)
