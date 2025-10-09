# Task 11: Update CLAUDE.md with Testing Strategy Documentation

## Objective
Document the projector isolation testing strategy in CLAUDE.md to guide future development and ensure consistent testing patterns.

## Background
With projector disabled by default in integration tests, developers need clear guidance on:
- When to enable/disable projector
- How to write API layer tests vs projector tests
- Testing patterns for CQRS architecture

## Tasks

### 1. Add Testing Strategy Section to CLAUDE.md

Add a new section after the existing "Test Quality Guidelines - Avoiding Superficial Tests" section:

```markdown
**Integration Testing with Kafka Event Projection:**

**IMPORTANT: ReadModelProjector (KafkaListener) is DISABLED by default in integration tests.**

This ensures proper test isolation in our CQRS + Event Sourcing architecture:
- Commands create events and return immediately (fire-and-forget)
- Events are published to Kafka (all tests share same Kafka topics)
- ReadModelProjector updates repositories asynchronously
- With projector enabled everywhere, tests consume each other's events (cross-contamination)

**Solution:** Disable projector by default, enable only in dedicated projector tests.

**Test Categories:**

**1. API Layer Integration Tests** (90% of integration tests)
Tests that verify HTTP API behavior WITHOUT event projection:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class MyApiIntegrationTest extends IntegrationTestFixture {
  // Projector is DISABLED by default - no @TestPropertySource needed

  @Test
  void operation_shouldReturnCorrectResponse() {
    // Arrange: Setup test data via IntegrationTestFixture

    // Act: Make HTTP request (creates event, returns immediately)
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: Verify synchronous API response ONLY
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("id").asText()).isNotNull();

    // ❌ DO NOT query repositories here - projector is disabled!
    // ❌ DO NOT use await() here - no async processing happening!

    // ✅ Add comment explaining why no repository assertions:
    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
```

**When to use:**
- Testing command handlers (PUT, POST, DELETE, PATCH operations)
- Testing validation (400 Bad Request responses)
- Testing error handling (404, 409, 412 responses)
- Testing HTTP headers (Location, ETag, SPARQL-Version-Control)

**DO NOT:**
- Query repositories after HTTP request (will see stale state)
- Use await() to wait for async projection (projector is disabled)
- Expect repository state to change during test

**2. Event Projector Integration Tests** (dedicated test class)
Tests that verify ReadModelProjector correctly processes events:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "kafka.listener.enabled=true")  // ← Enable projector!
class GraphEventProjectorIT extends IntegrationTestFixture {

  @Autowired
  private EventPublisher eventPublisher;

  @Test
  void commitCreatedEvent_shouldBeProjected() throws Exception {
    // Arrange: Create event
    CommitId commitId = CommitId.generate();
    CommitCreatedEvent event = new CommitCreatedEvent(...);

    // Act: Publish event to Kafka
    eventPublisher.publish(event).get();

    // Assert: Wait for async projection, then verify repository state
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // ✅ NOW safe to query repository (projector is enabled)
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().author()).isEqualTo("Alice");
        });
  }
}
```

**When to use:**
- Testing ReadModelProjector event handlers
- Verifying event serialization/deserialization
- Testing event-driven repository updates

**Requirements:**
- Must add `@TestPropertySource(properties = "kafka.listener.enabled=true")`
- Must use `await()` to wait for async projection
- Must use Awaitility library for proper async testing

**Reference:** See `GraphEventProjectorIT` for complete examples.

**3. Full System Integration Tests** (rare, use sparingly)
Tests that verify complete CQRS flow (command → event → projection):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "kafka.listener.enabled=true")  // ← Enable projector
@DirtiesContext  // ← Restart Spring context after test (cleanup)
class FullSystemIntegrationTest extends IntegrationTestFixture {

  @Test
  void putOperation_shouldEventuallyUpdateRepository() {
    // Arrange: Setup

    // Act: Make HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);
    JsonNode json = objectMapper.readTree(response.getBody());
    String commitId = json.get("id").asText();

    // Assert: Verify API response first
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Then: Wait for async event projection
    await().atMost(Duration.ofSeconds(10))
        .until(() -> commitRepository.findById(commitId).isPresent());

    // ✅ NOW verify repository state (after async processing)
    Commit commit = commitRepository.findById(commitId).orElseThrow();
    assertThat(commit.getAuthor()).isEqualTo("Alice");
  }
}
```

**When to use:**
- End-to-end testing of critical workflows
- Verifying complete CQRS flow works correctly
- Acceptance testing for major features

**Trade-offs:**
- ✅ Tests complete system integration
- ❌ Slower (async processing overhead)
- ❌ Requires @DirtiesContext (restarts Spring context)
- ❌ More complex (timing issues, flakiness)

**Use sparingly** - most tests should be API layer or projector tests.

**Configuration:**

In `application-it.yml`:
```yaml
kafka:
  listener:
    enabled: false  # Disabled by default
```

In `IntegrationTestFixture`:
```java
/**
 * Base class for integration tests.
 *
 * <p><strong>Event Projection:</strong> By default, the ReadModelProjector
 * is DISABLED to ensure test isolation. Tests that need projection must
 * add: {@code @TestPropertySource(properties = "kafka.listener.enabled=true")}
 */
public abstract class IntegrationTestFixture { ... }
```

**Troubleshooting:**

**Q: My test expects repository to be updated but it's not?**
A: Projector is disabled by default. Add `@TestPropertySource(properties = "kafka.listener.enabled=true")` and use `await()`.

**Q: I see "Branch not found" errors in logs?**
A: Cross-test contamination. Verify projector is disabled in `application-it.yml`.

**Q: My projector test times out?**
A: Check Kafka topic exists, check event is actually published, increase await timeout.

**Q: Should I test both API and projection in same test?**
A: No - separate concerns. API tests verify commands, projector tests verify queries.
```

### 2. Update Existing Test Guidelines

Locate and update the existing section in CLAUDE.md:

**Current section:**
```markdown
**Integration Test Types:**

**1. API Layer Integration Tests** (most common)
Tests that verify HTTP API behavior with commands/events:
```

**Add clarification:**
```markdown
**1. API Layer Integration Tests** (most common)
Tests that verify HTTP API behavior with commands/events.
**Note:** ReadModelProjector is DISABLED by default in these tests for isolation.
```

### 3. Add Quick Reference Table

Add a decision table for developers:

```markdown
**Testing Decision Table:**

| I want to test... | Test Type | Enable Projector? | Use await()? | Example |
|-------------------|-----------|-------------------|--------------|---------|
| HTTP status codes | API Layer | No | No | GraphStorePutIntegrationTest |
| HTTP headers | API Layer | No | No | ETagIntegrationTest |
| Validation errors | API Layer | No | No | ErrorResponseIntegrationTest |
| Command handler logic | API Layer | No | No | BatchGraphsIntegrationTest |
| Event projection | Projector | Yes (@TestPropertySource) | Yes | GraphEventProjectorIT |
| Full CQRS flow | Full System | Yes (@TestPropertySource) | Yes | (rare, avoid) |
| ReadModelProjector handlers | Projector | Yes (@TestPropertySource) | Yes | GraphEventProjectorIT |
```

### 4. Add Common Mistakes Section

```markdown
**Common Mistakes:**

❌ **Mistake 1:** Querying repository immediately after HTTP request in API test
```java
ResponseEntity<String> response = restTemplate.exchange(...);
var commit = commitRepository.findById(id);  // ❌ Will not find it!
assertThat(commit).isPresent();  // ❌ Will fail!
```
**Why it fails:** Projector is disabled, repository not updated.
**Fix:** Either enable projector + await(), or don't query repository.

❌ **Mistake 2:** Using await() in API test without enabling projector
```java
ResponseEntity<String> response = restTemplate.exchange(...);
await().until(() -> commitRepository.findById(id).isPresent());  // ❌ Will timeout!
```
**Why it fails:** Projector is disabled, repository never updated.
**Fix:** Add `@TestPropertySource(properties = "kafka.listener.enabled=true")`.

❌ **Mistake 3:** Not using await() when projector is enabled
```java
@TestPropertySource(properties = "kafka.listener.enabled=true")
class MyTest {
  @Test
  void test() {
    eventPublisher.publish(event).get();
    var commit = commitRepository.findById(id);  // ❌ Race condition!
    assertThat(commit).isPresent();  // ❌ May fail intermittently!
  }
}
```
**Why it fails:** Async projection not complete yet.
**Fix:** Use `await()` to wait for projection.

✅ **Correct Pattern for API Tests:**
```java
@Test
void putGraph_shouldReturn200() {
  // Act
  ResponseEntity<String> response = restTemplate.exchange(...);

  // Assert API response only
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().getFirst("ETag")).isNotNull();

  // ✅ No repository queries - projector disabled
  // Note: Repository updates handled by ReadModelProjector (async)
}
```

✅ **Correct Pattern for Projector Tests:**
```java
@TestPropertySource(properties = "kafka.listener.enabled=true")
class GraphEventProjectorIT extends IntegrationTestFixture {
  @Test
  void commitCreatedEvent_shouldBeProjected() {
    // Arrange
    CommitCreatedEvent event = ...;

    // Act
    eventPublisher.publish(event).get();

    // Assert with async wait
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findById(id);
          assertThat(commit).isPresent();
        });
  }
}
```
```

## Verification Steps

1. **Update CLAUDE.md** with all sections above

2. **Verify markdown syntax**:
   ```bash
   # Check for markdown formatting issues
   # (Use your preferred markdown linter or preview tool)
   ```

3. **Review for clarity**:
   - Can a new developer understand when to enable/disable projector?
   - Are examples clear and correct?
   - Are common mistakes well-explained?

4. **Get feedback** (optional):
   - Review with team member if available
   - Test examples compile and work

## Acceptance Criteria

- [x] "Integration Testing with Kafka Event Projection" section added to CLAUDE.md
- [x] All three test categories documented with examples
- [x] Quick reference decision table added
- [x] Common mistakes section added with fixes
- [x] Existing test guidelines updated with projector clarification
- [x] Documentation is clear and actionable
- [x] Code examples are syntactically correct

## Dependencies

- Tasks 01-10 must be completed (implementation done)

## Next Task

Task 12: Run Final Full Test Suite (verify complete implementation)

## Estimated Complexity

Medium (1 hour)
- Write documentation: 40 minutes
- Review and polish: 15 minutes
- Verification: 5 minutes

## Documentation Checklist

- [ ] Explains WHY projector is disabled (test isolation)
- [ ] Shows HOW to enable projector when needed
- [ ] Provides clear examples for each test type
- [ ] Includes decision table for quick reference
- [ ] Documents common mistakes and fixes
- [ ] Links to reference implementation (GraphEventProjectorIT)
- [ ] Explains configuration (application-it.yml)
- [ ] Provides troubleshooting guidance

## Notes

**Documentation Goals:**
1. **Clarity**: New developers should understand the pattern immediately
2. **Actionability**: Examples should be copy-paste-able
3. **Completeness**: Cover all common scenarios
4. **Maintainability**: Easy to update as patterns evolve

**Reference Material:**
- IntegrationTestFixture javadoc
- GraphEventProjectorIT implementation
- application-it.yml configuration
- ReadModelProjector @KafkaListener annotation

**After This Task:**
Developers should be able to:
- Write API layer tests without projector
- Write projector tests with projector enabled
- Understand when to use each pattern
- Troubleshoot common testing issues
- Maintain consistent testing practices

## Completion Summary

**Completed: 2025-10-09 20:25**

Enhanced CLAUDE.md testing documentation with comprehensive guidance on the projector
isolation pattern implemented in Tasks 01-10.

**Sections Added:**

1. **Testing Decision Table** (CLAUDE.md line 218)
   - Quick reference guide for when to enable/disable projector
   - 7 common test scenarios with clear guidance
   - Examples: GraphStorePutIntegrationTest, GraphEventProjectorIT, etc.

2. **Test Class Organization** (CLAUDE.md line 230)
   - Documents the 4 projector test classes created in Tasks 08-10
   - GraphEventProjectorIT: GSP event handlers
   - VersionControlProjectorIT: Version control operations (Task 09)
   - AdvancedOperationsProjectorIT: Advanced operations (Task 10)
   - ReadModelProjectorIT: Basic projector functionality
   - Maps each test class to specific event handlers tested

3. **Troubleshooting Section** (CLAUDE.md line 247)
   - 5 common questions with detailed answers
   - Q: Repository not updated? → Enable projector + await()
   - Q: Cross-test contamination? → Verify projector disabled in application-it.yml
   - Q: Projector test timeout? → 4-step debugging checklist
   - Q: Test both API and projection? → No, separate concerns
   - Q: When use @DirtiesContext? → Rarely, performance impact

4. **Updated References** (CLAUDE.md line 215)
   - Added VersionControlProjectorIT and AdvancedOperationsProjectorIT
   - References all 3 projector test classes for complete examples

**Documentation Impact:**

- **Clarity**: New developers can quickly understand the pattern via decision table
- **Actionability**: Examples are copy-paste-able and syntactically correct
- **Completeness**: Covers all test scenarios (API, Projector, Full System)
- **Maintainability**: Test class organization shows where to add new tests
- **Troubleshooting**: Common issues documented with clear solutions

**Existing Documentation Enhanced:**

Lines 107-216 already contained:
- ✅ Explanation of why projector is disabled (test isolation)
- ✅ Pattern 1: API Layer Tests (90% of tests)
- ✅ Pattern 2: Projector Tests (dedicated test class)
- ✅ Common Mistakes (3 mistakes with fixes)

**New Content Added:**

- ✅ Testing Decision Table (7 scenarios)
- ✅ Test Class Organization (4 test classes mapped to handlers)
- ✅ Troubleshooting (5 Q&A pairs)
- ✅ Updated references (3 test classes)

**Final Result:**

CLAUDE.md now provides complete guidance for the projector isolation testing strategy:
- 100% of ReadModelProjector event handlers have test coverage
- Clear patterns for when to enable/disable projector
- Decision table for quick reference
- Troubleshooting guide for common issues
- Test class organization for maintainability

**Test Isolation Implementation Complete:**

Tasks 01-11 finished successfully:
- Task 01: ✅ Disable projector by default
- Task 04: ✅ Enable projector in existing tests
- Task 05: ✅ Identify projector-dependent tests
- Task 06: ✅ Verify test suite (819 tests passing, 26% faster)
- Task 07: ✅ Verify zero cross-test errors
- Task 08: ✅ Review projector test coverage (10/10 handlers)
- Task 09: ✅ Add version control operation tests (3 tests)
- Task 10: ✅ Add advanced operation tests (3 tests)
- Task 11: ✅ Update testing documentation (complete)

**Benefits Achieved:**

- ✅ Complete test isolation (zero cross-contamination errors)
- ✅ 100% ReadModelProjector event handler coverage
- ✅ 26% faster test execution (50s vs 68s baseline)
- ✅ Clear testing patterns documented in CLAUDE.md
- ✅ Maintainable test organization by feature area
- ✅ Industry best practice for CQRS testing
