# CHUCC Server - Development Guidelines

## 🚀 Start Here

**First time working on this project?** Read these docs first:
1. **[Architecture Overview](../docs/architecture/README.md)** - Complete system understanding
2. **[CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)** - Core pattern explanation
3. **[C4 Component Diagram](../docs/architecture/c4-level3-component.md)** - Component structure

These documents explain the "why" and "what" of the architecture. This file focuses on the "how" - practical guidelines for implementation.

---

## Project Overview

This project implements [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/) with a [Version Control Extension](./protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md).

**Technology Stack:**
- Java 21 + Spring Boot 3.5
- Apache Jena 5.5 (in-memory RDF: `DatasetGraphInMemory`)
- CQRS + Event Sourcing architecture
- RDFPatch events stored in Apache Kafka

**Key Architectural Principle:**
- Commands create events → Events published to Kafka → Projectors update repositories (async)
- HTTP responses return **before** repositories updated (eventual consistency)

📖 **For deep dive**: See [CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)

---

## Testing Guidelines

### Test-Driven Development (TDD)

- Write tests **before** implementation
- Think from user perspective first
- Add additional tests after implementation for coverage

### CRITICAL: Test Isolation Pattern

**ReadModelProjector is DISABLED by default** in integration tests (since 2025-10-09).

**Why:** Prevents cross-test contamination when all tests share same Kafka topics.

**Solution:** Projector enabled only in dedicated projector tests via:
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
```

### Integration Test Patterns

#### Pattern 1: API Layer Test (90% of tests)

**Goal:** Test HTTP contract (command side) only

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class MyApiTest extends IntegrationTestFixture {
  // Projector DISABLED by default

  @Test
  void operation_shouldReturnCorrectResponse() {
    // Act: HTTP request
    ResponseEntity<String> response = restTemplate.exchange(...);

    // Assert: API response only
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();

    // ❌ DO NOT query repositories - projector disabled!
    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }
}
```

#### Pattern 2: Projector Test (10% of tests)

**Goal:** Test event projection (read side)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable!
class GraphEventProjectorIT extends IntegrationTestFixture {

  @Test
  void commitCreatedEvent_shouldBeProjected() throws Exception {
    // Arrange
    CommitCreatedEvent event = new CommitCreatedEvent(...);

    // Act
    eventPublisher.publish(event).get();

    // Assert: Wait for async projection
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        var commit = commitRepository.findById(commitId);
        assertThat(commit).isPresent();
      });
  }
}
```

### Testing Decision Table

| I want to test... | Enable Projector? | Use await()? | Example |
|-------------------|-------------------|--------------|---------|
| HTTP status/headers | ❌ No | ❌ No | GraphStorePutIntegrationTest |
| Validation errors | ❌ No | ❌ No | ErrorResponseIntegrationTest |
| Event projection | ✅ Yes | ✅ Yes | GraphEventProjectorIT |
| ReadModelProjector handlers | ✅ Yes | ✅ Yes | VersionControlProjectorIT |

### Common Testing Mistakes

❌ **Mistake 1:** Querying repository without enabling projector
```java
restTemplate.exchange(...);
var commit = commitRepository.findById(id);  // ❌ Not there!
```
**Fix:** Enable projector + use `await()`

❌ **Mistake 2:** Using `await()` without enabling projector
```java
await().until(() -> repository.findById(id).isPresent());  // ❌ Timeout!
```
**Fix:** Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`

❌ **Mistake 3:** Not using `await()` when projector enabled
```java
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MyTest {
  @Test void test() {
    eventPublisher.publish(event).get();
    var commit = repository.findById(id);  // ❌ Race condition!
  }
}
```
**Fix:** Use `await()` to wait for projection

### Test Organization

- **GraphEventProjectorIT**: GSP event handlers
- **VersionControlProjectorIT**: Version control event handlers
- **AdvancedOperationsProjectorIT**: Advanced operation handlers
- **ReadModelProjectorIT**: Basic projector functionality

📖 **For conceptual understanding**: See [CQRS + Event Sourcing Guide - Testing Implications](../docs/architecture/cqrs-event-sourcing.md#testing-implications)

---

## Code Quality Standards

### Checkstyle Rules

- **Indentation**: 2 spaces (code blocks), 4 spaces (continuation lines)
- **Line length**: Max 100 characters
- **Javadoc**: Required for all public classes/methods/constructors
  - Must include `@param` and `@return` tags
- **Import order**: No blank lines; order: external libs (edu.*, com.*), java.*, org.*

### SpotBugs Common Patterns

- **EI_EXPOSE_REP**: Use defensive copying for mutable collections
  - Getters: `return new ArrayList<>(internalList);`
  - Setters: `this.list = new ArrayList<>(list);`
- **SE_BAD_FIELD**: Make non-serializable fields `transient`
- Use `@SuppressFBWarnings` only for false positives

### PMD Rules

- **CPD (Copy/Paste Detector)**: No code duplication
- Extract common code into helper methods/utility classes

### Compiler Warnings

- **All warnings enabled**: `-Xlint:all,-options` (deprecation, unchecked, rawtypes, etc.)
- **Warnings fail build**: `-Werror` enforces zero-tolerance policy
- **Rationale**: Prevent technical debt from deprecated APIs and unsafe operations
- **What's checked**:
  - Deprecation warnings (using deprecated APIs)
  - Unchecked operations (raw types, unchecked casts)
  - Unused imports/variables (also caught by Checkstyle/PMD)
  - Serial version UID warnings
  - Varargs heap pollution

---

## Build Process

### Quality Requirements

**CRITICAL:** Only completely successful builds are acceptable.
- ✅ All tests pass (currently ~1153 tests)
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Zero compiler warnings (enforced by `-Werror`)
- ✅ `BUILD SUCCESS` message

### Token-Efficient Build Strategy

**Phase 1: Static Analysis (~30 seconds)**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check
```
- Catches quality issues before running tests
- Only proceed if zero violations

**Phase 2a: Incremental Tests (~10-30 seconds)**
```bash
mvn -q test -Dtest=NewTestClass,ModifiedTestClass
```
- Test only new/modified classes
- Skip if only docs/config changed

**Phase 2b: Full Build (~2-3 minutes)**
```bash
mvn -q clean install
```
- Runs all tests (~1142 unit + integration tests)
- All quality gates enforced
- Required before completion

### Verifying Build Success

⚠️ **ALWAYS check output** - don't assume success!

```bash
# ✅ GOOD: Direct Maven command
mvn -q clean install

# ❌ BAD: cmd.exe wrapper may suppress output
cmd.exe /c "mvn -q clean install"
```

**After build, verify:**
- "Tests run: X, Failures: 0" appears
- "BUILD SUCCESS" appears at end
- If uncertain: `mvn test 2>&1 | tail -50`

### When Failures Occur

Re-run without `-q` for full details:
```bash
mvn clean compile checkstyle:check  # Full Phase 1 output
mvn test -Dtest=FailingTestClass    # Full test output
mvn clean install                    # Full build output
```

### Token Usage Optimization

- **Without `-q`**: ~50,000 tokens
- **With `-q` (unit tests)**: ~3,000 tokens
- **With `-q` (integration tests)**: ~8,000 tokens (Spring Boot autoconfiguration output)
- **Savings**: 84-94% reduction

**Optimization Checklist:**
- ✅ Use `mvn -q` for all commands
- ✅ For integration tests, check only final results: `mvn -q test -Dtest=TestClass 2>&1 | tail -20`
- ✅ Use Glob/Grep tools (not `ls`, `find`, `cat`)
- ✅ Read specific file paths when known
- ✅ Use `Read` with offset/limit for large files
- ✅ Only re-run without `-q` when investigating failures

**Integration Test Best Practice:**
```bash
# ✅ GOOD: Run test and check only results
mvn -q test -Dtest=MyIntegrationTest 2>&1 | tail -20

# ❌ AVOID: Full output wastes tokens
mvn -q test -Dtest=MyIntegrationTest
```

---

## Development Workflow

### Before Starting

1. Read relevant code files for context
2. Check existing tests for patterns
3. Plan approach (mention if breakdown needed)

### During Implementation

1. Use `-q` for all Maven commands
2. Run Phase 1 (static analysis) before tests
3. Write tests first (TDD)
4. Run Phase 2a (incremental) after implementation
5. **Invoke specialized agents for verification** (see below)
6. Run Phase 2b (full build) when ready to complete

### Specialized Agent Verification

After implementing features, invoke specialized agents:

- **After command/projector changes:** `@cqrs-compliance-checker`
- **After writing tests:** `@test-isolation-validator`
- **After modifying events:** `@event-schema-evolution-checker`
- **Before completing large tasks:** `@documentation-sync-agent`

See [Agent Guide](./.claude/agents/README.md) for details

### For Large Tasks

- Create breakdown in `.tasks/` folder
- Each file should be completable in one session
- Reference: See `.tasks/gsp/` for examples

### After Implementation

Provide git commit message:
```
<type>: <short description>

<detailed description>
- Key changes
- Performance improvements
- Breaking changes (if any)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`

---

## Project Conventions

### User Prompts

User intentionally uses "maybe" to invite alternative suggestions. Feel free to propose better approaches.

### Build Assumptions

- Assume project had no build errors before your changes
- Fix any errors/warnings as part of your task
- If you find a bug, check for pattern across codebase

### Build and Test Configuration

- **Batch mode**: Enabled via `.mvn/maven.config` (cleaner output)
- **English locale**: Enforced via `.mvn/jvm.config` (consistent messages across environments)
- **Test logging**: `logback-test.xml` reduces Spring/Kafka/Testcontainers noise
- **Test config**: `src/test/resources/test.properties`
- **Test utilities**: `IntegrationTestFixture`, `TestConstants`, `KafkaTestContainers`

---

## Additional Resources

### Architecture Documentation

- **[Documentation Index](../docs/README.md)** - Navigation hub
- **[Architecture Overview](../docs/architecture/README.md)** - System guide for agents
- **[C4 Level 1: Context](../docs/architecture/c4-level1-context.md)** - External dependencies
- **[C4 Level 2: Container](../docs/architecture/c4-level2-container.md)** - Technology choices
- **[C4 Level 3: Component](../docs/architecture/c4-level3-component.md)** - Internal structure
- **[CQRS + Event Sourcing](../docs/architecture/cqrs-event-sourcing.md)** - Core pattern

### Development Guides

- **[Contributing](../docs/development/contributing.md)** - Contribution guidelines
- **[Quality Tools](../docs/development/quality-tools.md)** - Checkstyle, SpotBugs, PMD
- **[Token Optimization](./TOKEN_OPTIMIZATION.md)** - Advanced token-saving strategies

### API Documentation

- **[OpenAPI Guide](../docs/api/openapi-guide.md)** - API documentation
- **[Error Codes](../docs/api/error-codes.md)** - RFC 7807 error reference
- **[API Extensions](../docs/api/api-extensions.md)** - Version control extensions

---

## Quick Reference

### Fast Feedback Commands

```bash
# Fastest: Compile + checkstyle (~10 sec)
mvn -q compile checkstyle:check

# Static analysis (~30 sec)
mvn -q compile checkstyle:check spotbugs:check

# Unit tests only (~1 min)
mvn -q clean test

# Full build (~2-3 min)
mvn -q clean install
```

### Debug Commands

```bash
# Verbose output
mvn clean install -X

# Test output only
mvn test -Dsurefire.printSummary=true
```

### Test Selection

```bash
# Specific unit test class
mvn -q test -Dtest=SquashCommandHandlerTest

# Specific integration test (use tail to reduce output)
mvn -q test -Dtest=TimeTravelQueryIT 2>&1 | tail -20

# Multiple classes
mvn -q test -Dtest=GraphStoreControllerIT,BranchControllerIT

# Pattern matching
mvn -q test -Dtest=*ControllerIT
```

---

## Summary

**This file provides practical "how-to" guidelines for development.**

**For conceptual understanding**, read the architecture documentation:
- Why CQRS + Event Sourcing? → [CQRS Guide](../docs/architecture/cqrs-event-sourcing.md)
- How are components organized? → [C4 Component Diagram](../docs/architecture/c4-level3-component.md)
- What's the system context? → [C4 Context Diagram](../docs/architecture/c4-level1-context.md)

**Key principles:**
1. Test-driven development (TDD)
2. Test isolation (projector disabled by default)
3. Zero quality violations
4. Token-efficient builds (`-q` flag)
5. Clear, conventional commit messages

**When in doubt**: Read the architecture docs first, then ask questions.
