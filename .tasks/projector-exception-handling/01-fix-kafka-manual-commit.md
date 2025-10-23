# Task 01: Fix Kafka Manual Commit Configuration

## Objective

Fix Kafka consumer configuration to use manual commit (AckMode.RECORD) instead of auto-commit to ensure failed event processing doesn't lose messages.

## Current Problem

From [KafkaConfig.java:183-199](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L183-L199):

```java
// Current behavior:
// - If event processing fails BEFORE auto-commit interval → offset NOT committed (good)
// - If event processing fails AFTER auto-commit interval → offset MAY be committed (bad)
```

**Risk**: Auto-commit can commit offsets BEFORE exception handling runs, causing message loss on failure.

**Solution**: Use manual commit with AckMode.RECORD to ensure offsets only commit after successful processing.

## Implementation Steps

### 1. Update KafkaConfig.java

Disable auto-commit in consumer configuration:

```java
// Change from true to false
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
// Remove AUTO_COMMIT_INTERVAL_MS_CONFIG (no longer needed)
```

Configure manual commit in listener factory:

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent>
    kafkaListenerContainerFactory() {
  ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
  factory.setConsumerFactory(consumerFactory());
  factory.setConcurrency(1); // Single consumer for ordered processing

  // Manual commit: only commit offset after successful processing
  factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

  return factory;
}
```

Remove or update the TODO comment explaining the old problem.

### 2. Write Test: Exception Propagation

Create `ReadModelProjectorExceptionHandlingTest.java` (unit test):

```java
@ExtendWith(MockitoExtension.class)
class ReadModelProjectorExceptionHandlingTest {

  @Mock private CommitRepository commitRepository;
  @Mock private BranchRepository branchRepository;
  // ... other mocks

  @InjectMocks
  private ReadModelProjector projector;

  @Test
  void handleEvent_whenProcessingFails_shouldRethrowAsProjectionException() {
    // Arrange: Create valid event
    CommitCreatedEvent event = new CommitCreatedEvent(
        UUID.randomUUID(),
        "dataset1",
        CommitId.of("commit123"),
        null, // no parent
        "Test commit",
        "author@example.com",
        Instant.now(),
        "A R <s> <p> <o> .", // valid RDF patch
        null // no branch
    );

    // Mock repository to throw exception
    doThrow(new RuntimeException("Database connection failed"))
        .when(commitRepository).save(any());

    // Create mock ConsumerRecord
    ConsumerRecord<String, VersionControlEvent> record =
        new ConsumerRecord<>("topic", 0, 0, "key", event);

    // Act & Assert: Verify exception propagates
    assertThatThrownBy(() -> projector.handleEvent(record))
        .isInstanceOf(ProjectionException.class)
        .hasMessageContaining("Failed to project event")
        .hasCauseInstanceOf(RuntimeException.class)
        .cause()
        .hasMessageContaining("Database connection failed");
  }

  @Test
  void handleEvent_whenMalformedRdfPatch_shouldRethrowAsProjectionException() {
    // Arrange: Event with invalid RDF patch
    CommitCreatedEvent event = new CommitCreatedEvent(
        UUID.randomUUID(),
        "dataset1",
        CommitId.of("commit123"),
        null,
        "Test commit",
        "author@example.com",
        Instant.now(),
        "INVALID RDF SYNTAX <<<", // malformed
        null
    );

    ConsumerRecord<String, VersionControlEvent> record =
        new ConsumerRecord<>("topic", 0, 0, "key", event);

    // Act & Assert
    assertThatThrownBy(() -> projector.handleEvent(record))
        .isInstanceOf(ProjectionException.class)
        .hasMessageContaining("Failed to project event");
  }
}
```

### 3. Verify with Integration Test (Optional)

Update one existing integration test to verify manual commit behavior:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class ReadModelProjectorIT extends IntegrationTestFixture {

  @Test
  void projectionFailure_shouldAllowRetry() throws Exception {
    // This test already exists - just verify it still passes
    // Manual commit means failed events will be retried
  }
}
```

## What This Fix Achieves

### Before (Auto-Commit)
```
Event received → Processing starts → [Auto-commit happens] → Processing fails
                                      ↑
                                   Offset committed!
                                   Message lost forever
```

### After (Manual Commit)
```
Event received → Processing starts → Processing fails → Exception thrown
                                                       ↓
                                              Offset NOT committed
                                              Kafka retries delivery
```

## Configuration Changes Summary

| Setting | Before | After | Reason |
|---------|--------|-------|--------|
| `ENABLE_AUTO_COMMIT_CONFIG` | `true` | `false` | Manual control over commits |
| `AUTO_COMMIT_INTERVAL_MS_CONFIG` | `5000` | Removed | Not needed with manual commit |
| `AckMode` | Default (BATCH) | `RECORD` | Commit after each successful event |

## Testing Strategy

1. **Unit test**: Verify exception propagation (mock-based, fast)
2. **Integration tests**: Existing tests verify end-to-end behavior
3. **No Kafka infrastructure testing**: Trust Spring Kafka's retry mechanisms

## Acceptance Criteria

- [x] Auto-commit disabled in KafkaConfig
- [x] AckMode.RECORD configured in listener factory
- [x] TODO comment removed/updated
- [x] Unit test verifies exception propagation
- [x] All existing integration tests pass (911 tests)
- [x] Zero Checkstyle, SpotBugs, PMD violations

## Files to Modify

- `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`
- `src/test/java/org/chucc/vcserver/projection/ReadModelProjectorExceptionHandlingTest.java` (new)

## Rollback Plan

If issues arise:
```bash
git revert <commit-hash>
```

Manual commit is safer than auto-commit, so rollback unlikely needed.

## Estimated Time

**30-60 minutes** (not 2-3 hours)

- Configuration changes: 10 minutes
- Unit test: 20 minutes
- Run full build: 10-20 minutes
- Commit: 5 minutes

## References

- Spring Kafka Docs: [Container Properties](https://docs.spring.io/spring-kafka/docs/current/reference/html/#container-props)
- Current TODO: KafkaConfig.java lines 191-194
