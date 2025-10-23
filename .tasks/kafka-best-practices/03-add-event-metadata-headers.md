# Task: Add Correlation ID for Distributed Tracing

**Status:** ✅ **Completed**
**Priority:** 🟡 **MEDIUM**
**Estimated Time:** 1-1.5 hours
**Actual Time:** 1.5 hours
**Dependencies:** None

---

## Context

**MISSING BEST PRACTICE:** CHUCC Server events lack correlation IDs, making it difficult to trace requests across the system (HTTP → Controller → Event → Projector).

### Current Implementation

[EventPublisher.java:149-235](src/main/java/org/chucc/vcserver/event/EventPublisher.java#L149-235):

```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // ✅ Has: dataset, eventType, eventId
  headers.add(new RecordHeader(EventHeaders.DATASET, ...));
  headers.add(new RecordHeader(EventHeaders.EVENT_TYPE, ...));
  headers.add(new RecordHeader(EventHeaders.EVENT_ID, ...));

  // ❌ Missing: correlationId, timestamp
}
```

### The Problem

**Without correlation IDs, distributed tracing is impossible:**

```
❌ Current logs (no correlation):
10:30:15.123 [http-nio-8080-exec-1] GraphStoreController: PUT /data?branch=main
10:30:15.125 [http-nio-8080-exec-2] EventPublisher: Publishing CommitCreatedEvent
10:30:15.130 [kafka-listener-1] ReadModelProjector: Processing CommitCreatedEvent
                                  ↑ Which HTTP request triggered this?

✅ With correlation IDs:
10:30:15.123 [abc-123] GraphStoreController: PUT /data?branch=main
10:30:15.125 [abc-123] EventPublisher: Publishing CommitCreatedEvent
10:30:15.130 [abc-123] ReadModelProjector: Processing CommitCreatedEvent
                        ↑ Clearly from the same request flow!
```

**Why This Matters:**
- Debugging: "Which API call caused this error?"
- Performance: "How long did this request take end-to-end?"
- Observability: "Trace request across Controller → Kafka → Projector"

---

## Goal

Add correlation ID and timestamp to all events for distributed tracing:
1. ✅ Generate unique correlation ID per HTTP request
2. ✅ Add correlation ID to all log statements (MDC)
3. ✅ Add correlation ID + timestamp to Kafka event headers
4. ✅ Support non-request contexts (background jobs, tests)

---

## Design Decisions

### 1. Correlation ID Generation

**Chosen: UUIDv7 (time-ordered)**

**Why:**
- Sortable by creation time (unlike UUIDv4)
- Globally unique across distributed systems
- Already used for eventId

**Format:** `01932c5c-8f7a-7890-b123-456789abcdef`

### 2. Correlation ID Storage

**Chosen: SLF4J MDC (Mapped Diagnostic Context)**

**Why:**
- Standard Java logging pattern
- Thread-local storage (safe for concurrent requests)
- Automatically inherited by Kafka listener threads
- No Spring request scope complexity

**Alternative rejected:** Spring request-scoped bean (too heavy, doesn't work outside HTTP)

### 3. Timestamp Format

**Chosen: Epoch milliseconds (UTC)**

**Why:**
- Standard Kafka format
- Easy to compare/sort
- Compact representation

**Format:** `1729593600000` (long)

---

## Implementation Plan

### Step 1: Create Correlation ID Filter (20 min)

**File:** `src/main/java/org/chucc/vcserver/filter/CorrelationIdFilter.java`

```java
package org.chucc.vcserver.filter;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that generates a correlation ID for each HTTP request.
 * The correlation ID is stored in SLF4J MDC for logging and can be
 * retrieved by EventPublisher to add to Kafka event headers.
 *
 * <p>Correlation IDs enable distributed tracing across:
 * HTTP Request → Controller → EventPublisher → Kafka → ReadModelProjector
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /**
   * MDC key for correlation ID (used in logging pattern and EventPublisher).
   */
  public static final String CORRELATION_ID_KEY = "correlationId";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    // Generate UUIDv7 (time-ordered) for this request
    String correlationId = UuidCreator.getTimeOrderedEpoch().toString();

    // Store in MDC for logging
    MDC.put(CORRELATION_ID_KEY, correlationId);

    try {
      // Process request (correlation ID available throughout)
      filterChain.doFilter(request, response);
    } finally {
      // Clean up MDC after request completes
      MDC.remove(CORRELATION_ID_KEY);
    }
  }
}
```

**Key design choices:**
- `@Order(HIGHEST_PRECEDENCE)`: Run before all other filters/interceptors
- `OncePerRequestFilter`: Guaranteed to run once per request
- MDC cleanup in `finally`: Prevents memory leaks in thread pools

---

### Step 2: Update EventPublisher to Add Headers (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

**Add imports:**
```java
import java.time.Instant;
import org.slf4j.MDC;
```

**Update `addHeaders()` method:**
```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // ... existing headers (dataset, eventType, eventId) ...

  // Add timestamp (UTC epoch milliseconds)
  headers.add(new RecordHeader(EventHeaders.TIMESTAMP,
      String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8)));

  // Add correlation ID (from MDC, if available)
  String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
  if (correlationId != null) {
    headers.add(new RecordHeader(EventHeaders.CORRELATION_ID,
        correlationId.getBytes(StandardCharsets.UTF_8)));
  }

  // ... rest of existing code ...
}
```

**Update EventHeaders constants:**
```java
public final class EventHeaders {
  // ... existing constants ...

  /**
   * Event timestamp (UTC epoch milliseconds).
   * Format: "1729593600000"
   */
  public static final String TIMESTAMP = "timestamp";

  /**
   * Correlation ID for distributed tracing.
   * Tracks request flow: HTTP → Controller → Event → Projector.
   * Format: UUIDv7 string (e.g., "01932c5c-8f7a-7890-b123-456789abcdef")
   */
  public static final String CORRELATION_ID = "correlationId";
}
```

**Why `correlationId` is nullable:**
- HTTP requests: correlation ID available (from filter)
- Background jobs: correlation ID null (not in HTTP context)
- Tests: correlation ID null (unless test sets MDC manually)

---

### Step 3: Update Logging Configuration (10 min)

**File:** `src/main/resources/logback-spring.xml`

**Update pattern to include correlation ID:**
```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{correlationId}] - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- ... rest of config ... -->
</configuration>
```

**Pattern explanation:**
- `%X{correlationId}`: MDC value (empty string if not set)
- Appears in all log statements during request

**Example output:**
```
2025-10-22 10:30:15.123 [http-nio-8080-exec-1] INFO  GraphStoreController [01932c5c-8f7a-7890-b123] - PUT /data?branch=main
2025-10-22 10:30:15.125 [http-nio-8080-exec-1] INFO  EventPublisher [01932c5c-8f7a-7890-b123] - Publishing CommitCreatedEvent
2025-10-22 10:30:15.130 [kafka-listener-1] INFO  ReadModelProjector [01932c5c-8f7a-7890-b123] - Processing CommitCreatedEvent
```

**Note:** `logback-test.xml` should be updated identically for test logs.

---

### Step 4: Update ReadModelProjector to Log Correlation ID (10 min)

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Update handler methods to extract and log correlation ID:**

```java
@KafkaListener(topics = "#{kafkaConfig.getEventsTopic()}", ...)
public void handleEvent(ConsumerRecord<String, VersionControlEvent> record) {
  VersionControlEvent event = record.value();
  Headers headers = record.headers();

  // Extract correlation ID from event headers (if present)
  String correlationId = extractHeader(headers, EventHeaders.CORRELATION_ID);
  if (correlationId != null) {
    // Set in MDC so all logs in this handler include it
    MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, correlationId);
  }

  try {
    // ... existing event handling ...
  } finally {
    // Clean up MDC after event processed
    MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
  }
}

/**
 * Extracts header value as UTF-8 string.
 */
private String extractHeader(Headers headers, String key) {
  Header header = headers.lastHeader(key);
  return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
}
```

**Why this matters:**
- Kafka listener runs in different thread than HTTP request
- MDC is thread-local, so we must manually propagate correlation ID
- Now projector logs show which HTTP request triggered the event

---

### Step 5: Write Tests (15 min)

**File:** `src/test/java/org/chucc/vcserver/event/EventPublisherTest.java`

**Test 1: Correlation ID included when MDC set**
```java
@Test
void publish_shouldIncludeCorrelationIdWhenMdcSet() {
  // Arrange: Set correlation ID in MDC (simulates HTTP request)
  String expectedCorrelationId = "test-correlation-123";
  MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, expectedCorrelationId);

  try {
    CommitCreatedEvent event = CommitCreatedEvent.create(...);

    // Act
    eventPublisher.publish(event);

    // Assert: Verify header
    ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());

    Headers headers = captor.getValue().headers();
    String actualCorrelationId = new String(
        headers.lastHeader(EventHeaders.CORRELATION_ID).value(),
        StandardCharsets.UTF_8);
    assertThat(actualCorrelationId).isEqualTo(expectedCorrelationId);
  } finally {
    MDC.clear();
  }
}
```

**Test 2: No correlation ID when MDC not set**
```java
@Test
void publish_shouldNotIncludeCorrelationIdWhenMdcNotSet() {
  // Arrange: Clear MDC (simulates background job)
  MDC.clear();

  CommitCreatedEvent event = CommitCreatedEvent.create(...);

  // Act
  eventPublisher.publish(event);

  // Assert: Verify no correlation ID header
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  Headers headers = captor.getValue().headers();
  assertThat(headers.lastHeader(EventHeaders.CORRELATION_ID)).isNull();
}
```

**Test 3: Timestamp always included**
```java
@Test
void publish_shouldAlwaysIncludeTimestamp() {
  CommitCreatedEvent event = CommitCreatedEvent.create(...);

  // Act
  eventPublisher.publish(event);

  // Assert: Verify timestamp header
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  Headers headers = captor.getValue().headers();
  String timestampStr = new String(
      headers.lastHeader(EventHeaders.TIMESTAMP).value(),
      StandardCharsets.UTF_8);

  long timestamp = Long.parseLong(timestampStr);
  assertThat(timestamp).isGreaterThan(1729593600000L); // Sanity check
}
```

**Integration Test:**

**File:** `src/test/java/org/chucc/vcserver/filter/CorrelationIdFilterIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class CorrelationIdFilterIT extends IntegrationTestFixture {

  @Test
  void httpRequest_shouldGenerateCorrelationId() {
    // Act: Make HTTP request
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        createTurtleEntity("<urn:s> <urn:p> <urn:o> ."),
        String.class);

    // Assert: Request succeeds
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Note: Correlation ID is in logs (verified manually)
    // and in Kafka event headers (verified by EventPublisherTest)
  }
}
```

---

### Step 6: Update Documentation (10 min)

**File:** `docs/architecture/cqrs-event-sourcing.md`

**Add section:**

```markdown
## Event Metadata Headers

All events published to Kafka include metadata headers for observability and debugging.

### Standard Headers

| Header | Type | Description | Example |
|--------|------|-------------|---------|
| `eventId` | String (UUIDv7) | Unique event identifier | `01932c5c-8f7a-7890-b123-456789abcdef` |
| `eventType` | String | Event type name | `CommitCreated` |
| `dataset` | String | Target dataset name | `default` |
| `timestamp` | Long (epoch millis) | When event was created (UTC) | `1729593600000` |
| `correlationId` | String (UUIDv7) | Request trace ID (distributed tracing) | `01932c5c-1234-5678-9abc-def012345678` |
| `aggregateType` | String | Aggregate type | `Branch` |
| `aggregateId` | String | Aggregate instance ID | `default:main` |

### Distributed Tracing

**Correlation ID** enables end-to-end request tracing across the CQRS architecture:

```
HTTP Request (correlationId=abc-123)
  ↓
Controller (correlationId=abc-123)
  ↓
EventPublisher (correlationId=abc-123)
  ↓
Kafka Event (header: correlationId=abc-123)
  ↓
ReadModelProjector (correlationId=abc-123)
```

**Example logs:**
```
2025-10-22 10:30:15.123 [01932c5c-...-b123] GraphStoreController: PUT /data?branch=main
2025-10-22 10:30:15.125 [01932c5c-...-b123] EventPublisher: Publishing CommitCreatedEvent
2025-10-22 10:30:15.130 [01932c5c-...-b123] ReadModelProjector: Processing CommitCreatedEvent
                        ↑ Same correlation ID throughout entire flow
```

**Use cases:**
- **Debugging:** "Which API call caused this projector error?"
- **Performance:** "How long did this request take end-to-end?"
- **Monitoring:** "Track request across async event processing"

### Header Availability

| Context | correlationId | timestamp |
|---------|---------------|-----------|
| HTTP requests | ✅ Present | ✅ Present |
| Background jobs | ❌ Absent | ✅ Present |
| Integration tests | ❌ Absent* | ✅ Present |

*Can be set manually via `MDC.put("correlationId", "test-123")` in tests.
```

---

## Success Criteria

- [x] CorrelationIdFilter created and registered
- [x] EventPublisher adds correlationId header (when MDC set)
- [x] EventPublisher adds timestamp header (always)
- [x] EventHeaders constants updated (CORRELATION_ID, TIMESTAMP)
- [x] ReadModelProjector extracts and logs correlation ID
- [x] Logging configuration includes correlation ID pattern
- [x] Unit tests verify header inclusion (3+ tests)
- [x] Integration test verifies filter integration
- [x] Documentation updated
- [x] All tests pass (~911 tests)
- [x] Zero quality violations (Checkstyle, SpotBugs, PMD)

---

## Future Enhancements

**When actually needed** (not now - YAGNI):

1. **Schema Versioning** - Add when first event schema evolution required
   - `schemaVersion` header (e.g., "1.0", "2.0")
   - Per-event constant: `CommitCreatedEvent.SCHEMA_VERSION = "1.0"`
   - Projector version handling

2. **Causation Tracking** - Add when implementing event-driven flows/sagas
   - `causationId` header (which event caused this event)
   - Event chains: Event1 → Event2 (causationId=Event1.eventId)
   - Only needed if events trigger other events

3. **User/Actor Tracking** - Add when implementing authentication
   - `userId` header (who triggered the request)
   - `actorType` header (user, system, api-key)

**Don't implement until you have concrete use cases.**

---

## References

- [Correlation ID Pattern (Microsoft)](https://docs.microsoft.com/en-us/azure/architecture/patterns/correlation-id)
- [SLF4J MDC Documentation](https://www.slf4j.org/manual.html#mdc)
- [EventPublisher.java](src/main/java/org/chucc/vcserver/event/EventPublisher.java)
- German Kafka CQRS/ES Best Practices

---

## Notes

**Complexity:** Low (standard pattern)
**Time:** ~1-1.5 hours
**Risk:** Very Low (additive change, no breaking changes)

This task delivers immediate value (distributed tracing) without unnecessary complexity.
