# Task: Enable Full CQRS Event Flow (Controller → Kafka → Projector)

**Status:** Not Started
**Priority:** High
**Estimated Time:** 1-2 sessions (4-8 hours)
**Dependencies:** ReadModelProjector already implemented

---

## Context

Currently, CHUCC Server has a **partial CQRS implementation**:
- ✅ Command handlers create events
- ✅ ReadModelProjector can consume events and update repositories
- ❌ **Controllers do NOT publish events to Kafka**

**Current Flow (Incomplete):**
```
HTTP Request → Controller → Command Handler → Event created
                                                    ↓
                                              (Event discarded)

                                              Repositories NOT updated!
```

**Target Flow (Complete CQRS):**
```
HTTP Request → Controller → Command Handler → Event created
                                                    ↓
                                              Publish to Kafka
                                                    ↓
                                        ReadModelProjector consumes
                                                    ↓
                                          Repositories updated!
```

**Problem:**
- Multiple integration tests are commented out (10+ tests across 5 files)
- Tests that verify repository updates after API calls cannot run
- No-op detection tests cannot verify behavior
- Full system validation is impossible

**Evidence:**
- `GraphEventProjectorIT.java:43-47` states: "Command handlers create events, but controllers don't publish them to Kafka yet"
- Tests with `// TODO: Re-enable when event projectors are implemented` (but projectors ARE implemented!)
- Tests with `// TODO: No-op detection test requires event processing implementation`

---

## Affected Test Files

### Tests to Re-enable

1. **GraphStoreDeleteIT.java**
   - Line 31: `deleteGraph_shouldReturn204WithHeaders_whenDeletingExistingGraph`
   - Line 99: `deleteGraph_shouldReturn204_whenGraphIsAlreadyEmpty` (no-op detection)
   - Line 249: `deleteGraph_shouldEventuallyUpdateRepository_whenDeletingGraph`
   - Line 298: (repository update verification)

2. **GraphStorePutIT.java**
   - Line 98: `putGraph_shouldReturn204_whenReplacingWithIdenticalContent` (no-op detection)
   - Line 353: `putGraph_shouldEventuallyUpdateRepository_whenCreatingNewGraph`
   - Line 386: (repository update verification)

3. **GraphStorePostIT.java**
   - Line 99: `postGraph_shouldReturn204_whenMergingIdenticalContent` (no-op detection)
   - Line 360: `postGraph_shouldEventuallyUpdateRepository_whenMergingGraph`
   - Line 404: (repository update verification)

4. **GraphStorePatchIT.java**
   - Line 346: Full system tests requiring event processing

**Total:** 10+ commented-out tests waiting for this feature.

---

## Design Decisions

### 1. Where to Publish Events

**Option 1: In Controllers** (Not recommended)
```java
@PostMapping
public ResponseEntity<?> operation(...) {
  var event = commandHandler.handle(command);
  eventPublisher.publish(event);  // ❌ Controller responsibility grows
  return ResponseEntity.ok().build();
}
```
**Cons:** Controllers become aware of event publishing infrastructure

**Option 2: In Command Handlers** (Recommended)
```java
public class MyCommandHandler {
  private final EventPublisher eventPublisher;

  public Event handle(Command command) {
    var event = createEvent(command);
    eventPublisher.publish(event);  // ✅ Handler's responsibility
    return event;
  }
}
```
**Pros:**
- Controllers remain thin
- Command handlers own event creation AND publishing
- Single Responsibility Principle

**Decision:** Implement in command handlers.

### 2. Event Publishing Interface

Create a generic `EventPublisher` interface:

```java
package org.chucc.vcserver.event;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes domain events to Kafka for async processing.
 */
public interface EventPublisher {
  /**
   * Publish event to Kafka asynchronously.
   *
   * @param event the domain event to publish
   * @return CompletableFuture that completes when event is sent
   */
  CompletableFuture<Void> publish(DomainEvent event);
}
```

Implementation uses Spring Kafka:

```java
@Service
public class KafkaEventPublisher implements EventPublisher {
  private final KafkaTemplate<String, DomainEvent> kafkaTemplate;

  @Override
  public CompletableFuture<Void> publish(DomainEvent event) {
    String topic = determineTopicForEvent(event);
    String key = event.getDatasetName();

    return kafkaTemplate.send(topic, key, event)
        .thenApply(result -> null);
  }

  private String determineTopicForEvent(DomainEvent event) {
    // Map event types to topics
    if (event instanceof GraphUpdatedEvent) return "graph-events";
    if (event instanceof CommitCreatedEvent) return "commit-events";
    // etc.
    throw new IllegalArgumentException("Unknown event type");
  }
}
```

### 3. Synchronous vs Asynchronous Publishing

**Decision:** Asynchronous (non-blocking)

- HTTP response returns **before** Kafka acknowledgment
- Consistent with CQRS eventual consistency model
- Better throughput
- Must handle Kafka failures gracefully (log + retry)

**Error Handling:**
```java
eventPublisher.publish(event)
    .exceptionally(ex -> {
      log.error("Failed to publish event {}", event, ex);
      // TODO: Consider dead letter queue or retry logic
      return null;
    });
```

---

## Implementation Plan

### Step 1: Create EventPublisher Interface and Implementation

**Files to create:**
- `src/main/java/org/chucc/vcserver/event/EventPublisher.java` (interface)
- `src/main/java/org/chucc/vcserver/event/KafkaEventPublisher.java` (implementation)

**Files to modify:**
- `src/main/resources/application.yml` - Add Kafka producer configuration
- `src/main/java/org/chucc/vcserver/config/KafkaConfig.java` - Bean configuration

**Changes:**
1. Define EventPublisher interface
2. Implement KafkaEventPublisher using Spring Kafka
3. Configure topic routing (event type → topic name)
4. Add error handling and logging
5. Write unit tests for KafkaEventPublisher

### Step 2: Inject EventPublisher into Command Handlers

**Files to modify:**
- All command handler classes (e.g., `PutGraphCommandHandler`, `PostGraphCommandHandler`, etc.)

**Changes:**
1. Add `EventPublisher` field to each command handler
2. Inject via constructor
3. Call `eventPublisher.publish(event)` after creating event
4. Handle CompletableFuture appropriately (async, with error logging)

**Example:**
```java
@Service
public class PutGraphCommandHandler implements CommandHandler<PutGraphCommand, GraphUpdatedEvent> {

  private final EventPublisher eventPublisher;

  @Autowired
  public PutGraphCommandHandler(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Override
  public GraphUpdatedEvent handle(PutGraphCommand command) {
    // Create event
    GraphUpdatedEvent event = new GraphUpdatedEvent(...);

    // Publish to Kafka asynchronously
    eventPublisher.publish(event)
        .exceptionally(ex -> {
          log.error("Failed to publish GraphUpdatedEvent for commit {}",
                    event.getCommitId(), ex);
          return null;
        });

    return event;
  }
}
```

### Step 3: Update Integration Tests

**Files to modify:**
- `GraphStoreDeleteIT.java` - Uncomment tests, add `@TestPropertySource` for projector
- `GraphStorePutIT.java` - Uncomment tests, add `@TestPropertySource` for projector
- `GraphStorePostIT.java` - Uncomment tests, add `@TestPropertySource` for projector
- `GraphStorePatchIT.java` - Uncomment tests, add `@TestPropertySource` for projector

**Required changes per test file:**
1. Add `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")` to test classes
2. Uncomment all event-based tests
3. Verify tests use `await()` for eventual consistency
4. Ensure proper test isolation (each test cleans up after itself)

**Example test structure:**
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")  // ← Enable projector
class GraphStorePutIT extends IntegrationTestFixture {

  @Test
  void putGraph_shouldEventuallyUpdateRepository_whenCreatingNewGraph() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");

    // When - HTTP request
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TURTLE_SIMPLE, headers),
        String.class
    );

    // Then - API response immediate
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String commitId = response.getHeaders().getETag().replace("\"", "");

    // And - Repository eventually updated (async)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> commit = commitRepository.findById(commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().getMessage()).isEqualTo("Initial commit");
        });
  }
}
```

### Step 4: Verify End-to-End Flow

**Manual verification steps:**
1. Start application with Kafka
2. Perform PUT operation via HTTP
3. Check Kafka topic for published event
4. Check repository for updated data
5. Verify logs show event publishing and consumption

**Tools:**
- Kafka console consumer: `kafka-console-consumer.sh --topic graph-events`
- Spring Boot Actuator: Check metrics for event publishing
- Application logs: Verify no errors in event publishing

### Step 5: Add Monitoring and Metrics

**Files to modify:**
- `KafkaEventPublisher.java` - Add metrics

**Metrics to add:**
```java
@Service
public class KafkaEventPublisher implements EventPublisher {

  private final MeterRegistry meterRegistry;

  @Override
  public CompletableFuture<Void> publish(DomainEvent event) {
    Timer.Sample sample = Timer.start(meterRegistry);

    return kafkaTemplate.send(topic, key, event)
        .thenApply(result -> {
          sample.stop(Timer.builder("event.publish.time")
              .tag("event.type", event.getClass().getSimpleName())
              .register(meterRegistry));

          meterRegistry.counter("event.publish.success",
              "event.type", event.getClass().getSimpleName()).increment();

          return null;
        })
        .exceptionally(ex -> {
          sample.stop(Timer.builder("event.publish.time")
              .tag("event.type", event.getClass().getSimpleName())
              .tag("status", "failed")
              .register(meterRegistry));

          meterRegistry.counter("event.publish.failed",
              "event.type", event.getClass().getSimpleName()).increment();

          log.error("Failed to publish event", ex);
          return null;
        });
  }
}
```

---

## Tests

### Unit Tests

**KafkaEventPublisherTest:**
```java
@ExtendWith(MockitoExtension.class)
class KafkaEventPublisherTest {

  @Mock
  private KafkaTemplate<String, DomainEvent> kafkaTemplate;

  @Mock
  private MeterRegistry meterRegistry;

  @InjectMocks
  private KafkaEventPublisher publisher;

  @Test
  void publish_shouldSendEventToKafka() {
    // Given
    GraphUpdatedEvent event = new GraphUpdatedEvent(...);
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    // When
    CompletableFuture<Void> result = publisher.publish(event);

    // Then
    assertThat(result).isCompleted();
    verify(kafkaTemplate).send(eq("graph-events"), eq("dataset1"), eq(event));
  }

  @Test
  void publish_shouldHandleKafkaFailure() {
    // Given
    GraphUpdatedEvent event = new GraphUpdatedEvent(...);
    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka down")));

    // When
    CompletableFuture<Void> result = publisher.publish(event);

    // Then
    assertThat(result).isCompletedExceptionally();
    // Verify error logged (use LogCaptor or similar)
  }
}
```

### Integration Tests

**Re-enable all commented-out tests in:**
- GraphStoreDeleteIT
- GraphStorePutIT
- GraphStorePostIT
- GraphStorePatchIT

All tests should:
1. Perform HTTP operation
2. Verify immediate API response (201, 204, etc.)
3. Use `await()` to verify eventual consistency
4. Check repository state after projection

---

## Success Criteria

- [ ] EventPublisher interface created
- [ ] KafkaEventPublisher implementation complete with error handling
- [ ] All command handlers publish events to Kafka
- [ ] Kafka topics configured correctly
- [ ] All 10+ commented-out tests uncommented and passing
- [ ] No-op detection tests work (verify no event published for identical content)
- [ ] Integration tests use `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
- [ ] End-to-end flow verified: HTTP → Event → Kafka → Projector → Repository
- [ ] Metrics added for event publishing (success/failure/latency)
- [ ] Error handling tested (Kafka unavailable, topic doesn't exist, etc.)
- [ ] Zero Checkstyle, SpotBugs, PMD violations
- [ ] All existing tests still pass (~911 tests)
- [ ] Documentation updated

---

## Rollback Plan

If issues arise:

1. **Disable event publishing** - Add feature flag:
   ```yaml
   vc:
     event-publishing:
       enabled: false  # Disable if problems occur
   ```

2. **Revert commits** - Each step should be a separate commit:
   - Step 1: EventPublisher interface
   - Step 2: Command handler integration
   - Step 3: Test updates

3. **Fallback behavior** - System continues to work without event publishing:
   - Commands still execute
   - HTTP responses still return
   - Only repository updates are missing (acceptable for rollback)

---

## Performance Considerations

**Event Publishing Overhead:**
- Kafka send: ~1-5ms (async, doesn't block HTTP response)
- HTTP response time unchanged
- Total throughput may increase (no blocking on repository updates)

**Kafka Load:**
- One event per HTTP operation
- Average event size: ~500 bytes - 5KB
- Expected load: Minimal (same as current operation count)

**Memory Impact:**
- Kafka producer buffers: ~32MB default
- Minimal additional heap usage

---

## Configuration

**application.yml changes:**

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
      retries: 3
      acks: 1  # Leader acknowledgment (balance between durability and performance)

vc:
  event-publishing:
    enabled: true  # Feature flag
    topics:
      graph-events: graph-events
      commit-events: commit-events
      branch-events: branch-events
```

---

## Documentation Updates

After implementation:

1. **Architecture Documentation** (`docs/architecture/cqrs-event-sourcing.md`)
   - Update flow diagrams to show complete event flow
   - Document event publishing behavior
   - Explain eventual consistency guarantees

2. **Development Guidelines** (`.claude/CLAUDE.md`)
   - Update testing patterns to reflect that projector tests now work end-to-end
   - Remove "projector not yet implemented" caveats
   - Document when to enable projector in tests

3. **Operations Guide** (`docs/operations/`)
   - Add Kafka monitoring recommendations
   - Document event publishing metrics
   - Troubleshooting guide for event publishing failures

---

## Future Enhancements

After completing basic event publishing:

1. **Transactional Outbox Pattern**
   - Store events in database first
   - Publish to Kafka separately
   - Guarantees no event loss even if Kafka is down

2. **Event Replay**
   - Admin endpoint to replay events from Kafka
   - Rebuild read models from event log

3. **Event Versioning**
   - Handle schema evolution
   - Support multiple event versions

4. **Dead Letter Queue**
   - Failed events go to DLQ
   - Admin UI to retry failed events

---

## References

- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/reference/html/)
- Affected tests: GraphStoreDeleteIT, GraphStorePutIT, GraphStorePostIT, GraphStorePatchIT
- Related comment: GraphEventProjectorIT.java:43-47

---

## Notes

**Why This Is Critical:**
- Without event publishing, the CQRS architecture is incomplete
- Repository queries will return stale data
- Integration tests cannot verify full system behavior
- Production system would not be eventually consistent

**Complexity Level:** Medium-High
- Requires understanding of Spring Kafka
- Must maintain async semantics
- Test isolation becomes more important
- Error handling is critical for production readiness

**Expected Impact:**
- 10+ tests will be enabled
- Full CQRS flow will work
- System will be eventually consistent
- Better observability via metrics
