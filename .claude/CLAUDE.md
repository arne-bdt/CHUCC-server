This project shall implement [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/) and the [Version Control Extension](./protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md).

The basic technology stack is:
- Java 21 + Spring Boot 3.5 
- using Apache Jena 5.5 - supporting only in-memory graphs based on org.apache.jena.sparql.core.mem.DatasetGraphInMemory (like Apache Jena Fuseki) 
- implementing a CQRS-pattern with Event-Sourcing 
- RDFPatch from "jena-rdfpatch" for the events 
- store the events in Apache Kafka with an appropriate topic structure and setup

Prefer JUnit and Mockito for testing.
Use a test-driven development (TDD) approach. Write unit tests and integration tests for each feature before implementing it.
The idea ist that you first switch into the user perspective how the feature should work and then you implement it.
You may add additional tests after implementing a feature to increase coverage.

**Test Quality Guidelines - Avoiding Superficial Tests:**

**IMPORTANT: This project uses CQRS with Event Sourcing and asynchronous event projectors.**
- Commands create events immediately
- Events are published to Kafka
- Event projectors update read models **asynchronously**
- HTTP responses return **before** repositories are updated

**Integration Test Types:**

**1. API Layer Integration Tests** (most common)
Tests that verify HTTP API behavior with commands/events:
```java
@Test
void operation_shouldReturnCorrectResponse() {
    // Arrange: Setup test data

    // Act: Make HTTP request (creates event, returns immediately)
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: Verify synchronous API response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("id").asText()).isNotNull();

    // ⚠️ DO NOT query repositories here - they're updated asynchronously!
    // Note: Repository updates handled by event projectors (see ReadModelProjectorIT)
}
```

**2. Full System Integration Tests** (for async verification)
Tests that verify the complete CQRS flow including event projection:
```java
@Test
void operation_shouldEventuallyUpdateRepository() {
    // Arrange: Setup test data

    // Act: Make HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);
    JsonNode json = objectMapper.readTree(response.getBody());
    String id = json.get("id").asText();

    // Assert: Wait for async event processing
    await().atMost(Duration.ofSeconds(5))
        .until(() -> repository.findById(id).isPresent());

    // ✅ NOW verify repository state (after async processing)
    Entity entity = repository.findById(id).orElseThrow();
    assertThat(entity.getProperty()).isEqualTo(expectedValue);
    assertThat(entity.getParent().getId()).isEqualTo(expectedParentId);
}
```
*Requires awaitility library for `await()` functionality*

**3. Event Projector Integration Tests** (verify async processing)
See existing examples: `ReadModelProjectorIT`, `EventPublisherKafkaIT`

**Red Flags for API Layer Tests:**
- ❌ Querying repositories immediately after HTTP request (will see stale state!)
- ❌ Not acknowledging async architecture in comments
- ✅ GOOD: Testing API contract (status, headers, response format)
- ✅ GOOD: Adding comment: "Repository updates handled by event projectors"

**Red Flags for Full System Tests:**
- Only checks HTTP status codes without waiting for async processing
- Verifies counts but not correctness after async updates
- Majority of tests are validation tests (400/404), with only 1-2 happy path tests

**Self-Test Questions:**
1. "Am I testing the API layer or the full system?"
   - API layer → Test synchronous response only
   - Full system → Use `await()` for async verification

2. "Would this test be flaky due to async timing?"
   - If YES → Add proper async waiting or move assertions to projector tests
   - If NO → Good, correctly scoped

**Exception: Synchronous Operations**
Some operations may update repositories synchronously (not via events):
```java
@Test
void synchronousOperation_shouldUpdateImmediately() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: Can verify repository immediately for synchronous operations
    assertThat(repository.findById(id)).isPresent();
}
```
Example: Tag operations in `TagOperationsIntegrationTest`

**Integration Testing with Kafka Event Projection:**

**CRITICAL: ReadModelProjector (KafkaListener) is DISABLED by default in integration tests** (as of 2025-10-09).

This ensures proper test isolation in our CQRS + Event Sourcing architecture:
- Commands create events and return immediately (fire-and-forget)
- Events are published to Kafka (all tests share same Kafka topics)
- ReadModelProjector updates repositories asynchronously
- With projector enabled everywhere, tests consume each other's events (cross-contamination)

**Solution:** Projector disabled by default, enabled only in dedicated projector tests via:
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

**When to Enable Projector:**
- ✅ **Enable**: When testing ReadModelProjector event handlers (GraphEventProjectorIT)
- ✅ **Enable**: When testing complete CQRS flow (command → event → projection)
- ❌ **Disable** (default): When testing HTTP API layer (command side only)
- ❌ **Disable** (default): When testing validation, error handling, headers

**Testing Patterns:**

**Pattern 1: API Layer Test** (90% of integration tests)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class MyApiTest extends IntegrationTestFixture {
  // Projector is DISABLED by default - no @TestPropertySource needed

  @Test
  void operation_shouldReturnCorrectResponse() {
    // Act: Make HTTP request (creates event, returns immediately)
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: Verify synchronous API response ONLY
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();

    // ❌ DO NOT query repositories - projector is disabled!
    // ❌ DO NOT use await() - no async processing!

    // ✅ Add comment explaining:
    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
```

**Pattern 2: Projector Test** (dedicated test class)
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable projector!
class GraphEventProjectorIT extends IntegrationTestFixture {

  @Autowired
  private EventPublisher eventPublisher;

  @Test
  void commitCreatedEvent_shouldBeProjected() throws Exception {
    // Arrange: Create event
    CommitCreatedEvent event = new CommitCreatedEvent(...);

    // Act: Publish event to Kafka
    eventPublisher.publish(event).get();

    // Assert: Wait for async projection, then verify repository
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findById(commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().author()).isEqualTo("Alice");
        });
  }
}
```

**Common Mistakes:**

❌ **Mistake 1:** Querying repository immediately after HTTP request
```java
ResponseEntity<String> response = restTemplate.exchange(...);
var commit = commitRepository.findById(id);  // ❌ Will not find it!
```
**Why:** Projector disabled, repository not updated.
**Fix:** Either enable projector + await(), or don't query repository.

❌ **Mistake 2:** Using await() without enabling projector
```java
await().until(() -> commitRepository.findById(id).isPresent());  // ❌ Timeout!
```
**Why:** Projector disabled, repository never updated.
**Fix:** Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`.

❌ **Mistake 3:** Not using await() when projector is enabled
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyTest {
  @Test
  void test() {
    eventPublisher.publish(event).get();
    var commit = commitRepository.findById(id);  // ❌ Race condition!
  }
}
```
**Why:** Async projection not complete yet.
**Fix:** Use `await()` to wait for projection.

**Reference:** See `GraphEventProjectorIT`, `VersionControlProjectorIT`, and
`AdvancedOperationsProjectorIT` for complete projector test examples.

**Testing Decision Table:**

| I want to test... | Test Type | Enable Projector? | Use await()? | Example |
|-------------------|-----------|-------------------|--------------|---------|
| HTTP status codes | API Layer | ❌ No | ❌ No | GraphStorePutIntegrationTest |
| HTTP headers | API Layer | ❌ No | ❌ No | ETagIntegrationTest |
| Validation errors | API Layer | ❌ No | ❌ No | ErrorResponseIntegrationTest |
| Command handler logic | API Layer | ❌ No | ❌ No | BatchGraphsIntegrationTest |
| Event projection | Projector | ✅ Yes (@TestPropertySource) | ✅ Yes | GraphEventProjectorIT |
| ReadModelProjector handlers | Projector | ✅ Yes (@TestPropertySource) | ✅ Yes | VersionControlProjectorIT |
| Full CQRS flow | Full System | ✅ Yes (@TestPropertySource) | ✅ Yes | (rare, avoid if possible) |

**Test Class Organization:**

The project organizes projector tests by feature area for better maintainability:
- **GraphEventProjectorIT**: Tests GSP (Graph Store Protocol) event handlers
  - `handleCommitCreated` (from PUT/POST/DELETE/PATCH graph operations)
  - `handleBatchGraphsCompleted` (from batch operations)
- **VersionControlProjectorIT**: Tests version control operation event handlers
  - `handleBranchRebased` (branch rebase operations)
  - `handleRevertCreated` (commit revert operations)
  - `handleSnapshotCreated` (dataset snapshot events)
- **AdvancedOperationsProjectorIT**: Tests advanced operation event handlers
  - `handleTagCreated` (tag creation operations)
  - `handleCherryPicked` (cherry-pick operations)
  - `handleCommitsSquashed` (squash operations)
- **ReadModelProjectorIT**: Tests basic projector functionality and event ordering
  - `handleCommitCreated`, `handleBranchCreated` (core events)

**Troubleshooting:**

**Q: My test expects repository to be updated but it's not?**
A: Projector is disabled by default. Add
`@TestPropertySource(properties = "projector.kafka-listener.enabled=true")` and use `await()`.

**Q: I see "Branch not found" errors in logs from other tests?**
A: Cross-test contamination detected. Verify projector is disabled by default in
`application-it.yml` (should have `projector.kafka-listener.enabled: false`).

**Q: My projector test times out waiting for projection?**
A: Check:
1. Kafka topic exists (ensureTopicExists() in test setup)
2. Event is actually published (check eventPublisher.publish().get() succeeds)
3. Event handler has no exceptions (check logs for projection errors)
4. Increase await timeout if processing is genuinely slow

**Q: Should I test both API and projection in same test?**
A: No - separate concerns. API layer tests verify HTTP contract (commands),
projector tests verify async event processing (queries). Use different test classes.

**Q: When should I use @DirtiesContext?**
A: Rarely. Only use when you need to restart the Spring context (e.g., testing
projector recovery after restart). Most tests should avoid it for performance.

This project uses Checkstyle and SpotBugs for static code analysis.

**Checkstyle Rules:**
- **Indentation**: 2 spaces for code blocks, 4 spaces for continuation (wrapped parameters/arguments)
- **Line length**: Maximum 100 characters
- **Javadoc**: Required for all public classes, methods, and constructors (including parameterized constructors)
  - Must include @param tags for all parameters
  - Must include @return tag for non-void methods
- **Import order**: No blank lines between imports; order: external libs (edu.*, com.*), then java.*, then org.* (project packages)
  ```java
  import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
  import java.util.ArrayList;
  import java.util.List;
  import org.chucc.vcserver.dto.ConflictItem;
  ```

**SpotBugs Common Patterns:**
- **EI_EXPOSE_REP/EI_EXPOSE_REP2**: Use defensive copying for mutable collections/arrays
  - Getters: `return new ArrayList<>(internalList);`
  - Setters/constructors: `this.list = new ArrayList<>(list);`
- **SE_BAD_FIELD**: Make non-serializable exception fields `transient`
- **SE_TRANSIENT_FIELD_NOT_RESTORED**: Suppress with @SuppressFBWarnings if field is not actually serialized
- Use `@SuppressFBWarnings(value = "CODE", justification = "reason")` only when warnings are false positives

**PMD Code Quality Rules:**
- **CPD (Copy/Paste Detector)**: No code duplication - extract common code into helper methods or utility classes
- When refactoring duplicated code, maintain readability and follow Single Responsibility Principle

**Build Process and Quality Requirements:**

**CRITICAL: Only completely successful builds are acceptable.**
- All tests must pass (no failures, no errors)
- Zero Checkstyle violations
- Zero SpotBugs warnings
- Zero PMD violations (including CPD duplications)
- Build must complete with `BUILD SUCCESS`

**Token-Efficient Build Strategy:**

To minimize token usage and catch issues early, follow this optimized three-phase approach:

**Phase 1: Fast Static Analysis (before running tests) - ~30 seconds**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check
```
- Uses `-q` (quiet mode) to show only warnings/errors
- Catches code quality issues without running tests
- Stops immediately on first violation
- **Only proceed if this succeeds with zero violations**

**Phase 2a: Incremental Test Run (for new/modified code) - ~10-30 seconds**
```bash
mvn -q test -Dtest=NewTestClass,ModifiedTestClass
```
- Run only unit tests for new/modified classes
- Uses `-q` to minimize output (only shows test failures)
- Verifies new functionality in isolation
- Skip this phase if only documentation or config changed

**Phase 2b: Full Build (final verification) - ~2-3 minutes**
```bash
mvn -q clean install
```
- Runs all unit tests (698 tests) and integration tests (15 tests)
- Uses `-q` to show only summary and failures
- All quality gates enforced (checkstyle, spotbugs, pmd, jacoco)
- **Success output is minimal (just "BUILD SUCCESS")**

**CRITICAL: Verifying Build Success**

⚠️ **ALWAYS verify build success by checking the output!** Do NOT assume a build succeeded just because the command completed.

When using Bash tool with `cmd.exe /c "mvn..."`, output may be suppressed. Instead:
1. Use `mvn` commands directly WITHOUT `cmd.exe /c` wrapper
2. Always check the output for "BUILD SUCCESS" or "BUILD FAILURE"
3. Look for test failure summaries in the output
4. If output is empty or unclear, re-run without `-q` to see full details

**Example of proper verification:**
```bash
# GOOD: Direct mvn command shows full output
mvn -q clean install

# BAD: cmd.exe /c may suppress output
cmd.exe /c "mvn -q clean install"
```

After running a build command, always:
- Check for "Tests run: X, Failures: Y" in output
- Verify Y (failures) is 0
- Confirm "BUILD SUCCESS" appears at the end
- If uncertain, run `mvn test 2>&1 | tail -50` to see test summary

**When Failures Occur:**
If a build fails with `-q`, re-run the same command WITHOUT `-q` to see full details:
```bash
# Re-run Phase 1 with full output
mvn clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check

# Re-run failed test with full output
mvn test -Dtest=FailingTestClass

# Re-run full build with full output
mvn clean install
```

**Token Usage Comparison:**
- **Without `-q`**: ~50,000 tokens (full test output + build logs)
- **With `-q`**: ~3,000 tokens (errors/warnings only)
- **Savings**: ~94% token reduction for successful builds

**Additional Optimization Strategies:**

**Skip Unchanged Test Categories:**
```bash
# Skip integration tests if only unit code changed
mvn -q clean test

# Run only integration tests if needed
mvn -q test-compile failsafe:integration-test failsafe:verify
```

**Parallel Builds (use with caution):**
```bash
# Use multiple cores for compilation (not tests - may cause flakiness)
mvn -q -T 1C clean compile checkstyle:check
```

**Fast Feedback Loop:**
```bash
# Compile + checkstyle only (fastest feedback - ~10 seconds)
mvn -q compile checkstyle:check

# Add spotbugs for deeper analysis (~20 seconds)
mvn -q compile checkstyle:check spotbugs:check
```

**Important Notes:**
- `-DskipTests` is NEVER allowed
- Always use `-q` for token efficiency
- Fix issues in Phase 1 before proceeding to Phase 2
- Fix issues in Phase 2a before proceeding to Phase 2b
- A build is not complete until Phase 2b succeeds with zero violations
- Re-run without `-q` only when investigating failures

**Build Configuration:**
- Batch mode is enabled by default via `.mvn/maven.config`
- The `logback-test.xml` configuration reduces Spring Boot, Kafka, and Testcontainers noise during tests
- Test configuration externalized in `src/test/resources/test.properties` for easy updates
- Use test utilities (`IntegrationTestFixture`, `TestConstants`, `KafkaTestContainers`) to reduce boilerplate

**Debug Mode (only when needed):**
```bash
# Full verbose output for troubleshooting
mvn clean install -X

# Verbose test output only
mvn test -Dsurefire.printSummary=true
```

**Workflow Best Practices:**

**Before Starting Implementation:**
1. Read relevant code files to understand context
2. Check existing tests for patterns
3. Plan the approach (mention if task needs breakdown)

**During Implementation:**
1. Use `-q` for all Maven commands to save tokens
2. Run Phase 1 (static analysis) before writing tests
3. Write tests first (TDD approach)
4. Run Phase 2a (incremental tests) after implementation
5. Only run Phase 2b (full build) when ready to complete

**For Large Tasks:**
- Create a task breakdown in `.tasks/` folder with numbered markdown files
- Each task file should be completable in one session
- Reference: See `.tasks/gsp/` for example structure

**Token Optimization Checklist:**
- ✅ Use `mvn -q` for all commands
- ✅ Use `Glob` and `Grep` tools instead of reading entire directories
- ✅ Read specific file paths when you know them
- ✅ Use `Read` with offset/limit for large files
- ✅ Avoid `ls`, `find`, `cat` bash commands - use dedicated tools
- ✅ Only re-run without `-q` when investigating failures
- ✅ Reference test utilities instead of duplicating setup code

**After Implementation:**
Provide a git commit message following this format:
```
<type>: <short description>

<detailed description>
- Bullet points for key changes
- Performance improvements
- Breaking changes (if any)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`

In the prompts I intentionally use words like "maybe" because I want to give you the opportunity to make alternative suggestions or propose variations if you think they are better.

Assume that when you get a new task the project had no build errors before. So if you find any build errors or warnings, you should fix them as part of the task and they most likely are cause by the not yet committed changes you made.
Also: If you find a bug and fix it, you should reflect that it might have a pattern and the same bug as well as the fix is to be applied to similar places in the code base.