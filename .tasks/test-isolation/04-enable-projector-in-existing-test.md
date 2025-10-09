# Task 04: Enable Projector in GraphEventProjectorIT

## Objective
Update the existing GraphEventProjectorIT test to explicitly enable the ReadModelProjector, ensuring it continues to work after projector is disabled by default in application-it.yml.

## Background
GraphEventProjectorIT is the only existing integration test that specifically tests event projection. It publishes events to Kafka and uses `await()` to verify that ReadModelProjector correctly processes them.

After disabling projector by default in Task 01, this test must explicitly enable it via `@TestPropertySource`.

## Tasks

### 1. Add @TestPropertySource to GraphEventProjectorIT

Modify `src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java`:

Add the annotation to the class:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "kafka.listener.enabled=true")  // ← Add this line
class GraphEventProjectorIT extends IntegrationTestFixture {
  // ... existing implementation
}
```

**Key points:**
- `@TestPropertySource` overrides `kafka.listener.enabled` for this test only
- Must be placed before class declaration, after other class-level annotations
- This test class is the reference example for testing event projection

### 2. Update Class Javadoc

Add/update the class-level javadoc to document the projector enablement:

```java
/**
 * Integration test for Graph Store Protocol event projection.
 *
 * <p>Verifies that the ReadModelProjector can handle events from GSP operations:
 * <ul>
 *   <li>CommitCreatedEvent (from PUT, POST, DELETE, PATCH operations)
 *   <li>BatchGraphsCompletedEvent (from batch graph operations)
 * </ul>
 *
 * <p><strong>Note:</strong> This test explicitly enables the ReadModelProjector
 * via {@code @TestPropertySource(properties = "kafka.listener.enabled=true")}
 * because projector is disabled by default in integration tests for test isolation.
 *
 * <p>This test manually publishes events to verify projector readiness.
 * When controllers are updated to publish events to Kafka, the complete
 * CQRS flow will work end-to-end.
 *
 * <p>Current state: Command handlers create events, but controllers don't
 * publish them to Kafka yet. This test verifies the projection logic works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "kafka.listener.enabled=true")
class GraphEventProjectorIT extends IntegrationTestFixture {
  // ... existing implementation
}
```

### 3. No Other Changes Needed

**Do NOT change:**
- Test methods - all existing tests should work as-is
- await() usage - still needed for async projection
- Event publishing logic - already correct
- Kafka topic setup - already correct

**Why:**
The only change needed is enabling the projector for this test class. The test logic is already correct.

## Verification Steps

1. **Compile the code**:
   ```bash
   mvn -q clean compile test-compile
   ```

2. **Run ONLY GraphEventProjectorIT** (with projector enabled):
   ```bash
   mvn -q test -Dtest=GraphEventProjectorIT
   ```

   Expected output:
   ```
   Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
   BUILD SUCCESS
   ```

3. **Verify no error logs**:
   - Should NOT see "Branch not found" errors
   - Should NOT see "Cannot cherry-pick to non-existent branch" errors
   - Should see normal "Successfully projected event" INFO logs

4. **Run Checkstyle**:
   ```bash
   mvn -q checkstyle:check
   ```

## Acceptance Criteria

- [ ] GraphEventProjectorIT has `@TestPropertySource(properties = "kafka.listener.enabled=true")`
- [ ] Class javadoc documents why projector is explicitly enabled
- [ ] GraphEventProjectorIT passes all 8 tests
- [ ] No error logs during GraphEventProjectorIT execution
- [ ] Zero Checkstyle violations
- [ ] Test execution time similar to before (~15-20 seconds)

## Dependencies

- Task 01 must be completed first (projector disabled by default)

## Next Task

Task 05: Search for Tests Using await() (identify other projector-dependent tests)

## Estimated Complexity

Low (15 minutes)
