# Task 01: Disable ReadModelProjector by Default in Integration Tests

## Objective
Modify ReadModelProjector to support conditional startup, allowing it to be disabled in API-layer integration tests while still being available for dedicated projector tests.

## Background
Currently, ReadModelProjector's @KafkaListener starts automatically in every test class, consuming events from shared Kafka topics. This causes cross-test contamination where test A processes events created by test B.

The Spring Kafka `autoStartup` parameter allows conditional listener startup based on a property.

## Tasks

### 1. Add autoStartup Parameter to @KafkaListener

Modify `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`:

```java
@KafkaListener(
    topicPattern = "vc\\..*\\.events",
    groupId = "read-model-projector",
    containerFactory = "kafkaListenerContainerFactory",
    autoStartup = "${kafka.listener.enabled:true}"  // ← Add this line
)
public void handleEvent(VersionControlEvent event) {
  // ... existing implementation
}
```

**Key points:**
- `autoStartup` controls when the listener starts
- `${kafka.listener.enabled:true}` reads from property, defaults to `true` (enabled)
- When property is `false`, listener container is created but NOT started
- No other code changes needed in ReadModelProjector

### 2. Update application-it.yml

Modify `src/main/resources/application-it.yml`:

```yaml
spring:
  main:
    banner-mode: off
  kafka:
    consumer:
      group-id: vc-server-it
      auto-offset-reset: latest  # Keep as 'latest'
    producer:
      acks: all

# Disable transactional Kafka for integration tests with Testcontainers
# Testcontainers Kafka doesn't require transactions and simplifies testing
kafka:
  transactional-id-prefix: ""
  listener:
    enabled: false  # ← Add this line

management:
  endpoint:
    health:
      show-details: always

logging:
  # ... existing logging config
```

**Key points:**
- `kafka.listener.enabled: false` disables projector in ALL integration tests by default
- Tests that need projector must explicitly override with `@TestPropertySource`
- Keep `auto-offset-reset: latest` - still useful for reducing startup events

### 3. Update IntegrationTestFixture Javadoc

Modify `src/test/java/org/chucc/vcserver/testutil/IntegrationTestFixture.java`:

Update the class-level javadoc:

```java
/**
 * Base class for integration tests providing common setup and cleanup.
 * Handles repository cleanup, initial commit/branch creation, and Kafka setup.
 *
 * <p>Tests can extend this class to get automatic repository cleanup,
 * initial dataset setup, and Kafka Testcontainer configuration before each test.
 *
 * <p><strong>Event Projection:</strong> By default, the ReadModelProjector
 * (KafkaListener) is DISABLED in integration tests to ensure test isolation.
 * Most integration tests verify the HTTP API layer (command side) without
 * async event projection (query side).
 *
 * <p>Tests that specifically need to verify event projection should:
 * <ul>
 *   <li>Add {@code @TestPropertySource(properties = "kafka.listener.enabled=true")}
 *   <li>Use {@code await().atMost(...)} to wait for async projection
 *   <li>See {@link GraphEventProjectorIT} for examples
 * </ul>
 */
public abstract class IntegrationTestFixture {
  // ... existing implementation
}
```

**Key points:**
- Document that projector is disabled by default
- Explain the rationale (test isolation, command/query separation)
- Provide guidance on how to enable projector when needed
- Reference GraphEventProjectorIT as an example

## Verification Steps

1. **Compile the code**:
   ```bash
   mvn -q clean compile
   ```

2. **Verify no syntax errors**:
   - Check that @KafkaListener annotation syntax is correct
   - Check that application-it.yml is valid YAML

3. **Run Checkstyle and SpotBugs**:
   ```bash
   mvn -q checkstyle:check spotbugs:check
   ```

4. **Don't run tests yet** - Task 04 must be completed first (GraphEventProjectorIT needs projector enabled)

## Acceptance Criteria

- [ ] ReadModelProjector @KafkaListener has `autoStartup = "${kafka.listener.enabled:true}"`
- [ ] application-it.yml has `kafka.listener.enabled: false`
- [ ] IntegrationTestFixture javadoc documents projector disabled by default
- [ ] Code compiles successfully
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings

## Dependencies

None - this is the first task

## Next Task

Task 04: Update GraphEventProjectorIT to Enable Projector (must be done before running tests)

## Estimated Complexity

Low (30 minutes)
