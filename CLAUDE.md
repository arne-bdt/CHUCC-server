This project shall implement [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/) and the [Version Control Extension](./protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md).

The basic technology stack is:
- Java 21 + Spring Boot 3.5 
- using Apache Jena 5.5 - supporting only in-memory graphs based on org.apache.jena.sparql.core.mem.DatasetGraphInMemory (like Apache Jena Fuseki) 
- implementing a CQRS-pattern with Event-Sourcing 
- RDFPatch from "jena-rdfpatch" for the events 
- store the events in Apache Kafka with an appropriate topic structure and setup

Prefer JUnit and Mockito for testing.
Use a test-driven development (TDD) approach. Write unit tests and integration tests for each feature before implementing it.
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

To minimize token usage and catch issues early, follow this two-phase approach:

**Phase 1: Fast Static Analysis (before running tests)**
```bash
mvn clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check
```
This catches code quality issues (formatting, bugs, duplications) in ~30 seconds without running the full test suite.

**Phase 2a: Incremental Test Run (for new/modified code)**
```bash
mvn clean install -Dtest=NewTestClass,ModifiedTestClass
```
Run only the added and modified tests first to verify new functionality.

**Phase 2b: Full Build (final verification)**
```bash
mvn clean install
```
Run all tests including integration tests. Fix any warnings or errors.

**Important Notes:**
- `-DskipTests` is NEVER allowed
- Fix issues in Phase 1 before proceeding to Phase 2
- Fix issues in Phase 2a before proceeding to Phase 2b
- A build is not complete until Phase 2b succeeds with zero violations

Build configuration:
- Use "mvn clean install" for normal builds (batch mode enabled by default via .mvn/maven.config)
- Use "mvn clean install -X" only when you need verbose/debug output to diagnose build issues
- The logback-test.xml configuration reduces Spring Boot, Kafka, and Testcontainers noise during tests

In the prompts I intentionally use words like "maybe" because I want to give you the opportunity to make alternative suggestions or propose variations if you think they are better.

Whenever you think a task is too big for one step:
- do not start right away to implement it, instead:
- create a folder with an ordered list of broken down ai-agent-friendly tasks in separate markdown files, which are small enough to be executed in one session
- making oversight easier and reducing token usage

After an implementation task, please provide me with a proper git commit message including a short description of what you have done.