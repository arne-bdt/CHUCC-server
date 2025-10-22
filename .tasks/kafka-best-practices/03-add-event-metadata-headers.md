# Task: Add Event Metadata Headers (Correlation, Causation, Schema Version)

**Status:** Not Started
**Priority:** 🟡 **MEDIUM**
**Estimated Time:** 2-3 hours
**Dependencies:** 02-implement-event-deduplication.md (for eventId)

---

## Context

**MISSING BEST PRACTICE:** CHUCC Server events lack critical metadata headers required for distributed tracing, debugging, and schema evolution.

### Current Implementation

[EventPublisher.java:149-235](src/main/java/org/chucc/vcserver/event/EventPublisher.java#L149-235):

```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // ✅ Has: dataset, eventType
  headers.add(new RecordHeader(EventHeaders.DATASET, ...));
  headers.add(new RecordHeader(EventHeaders.EVENT_TYPE, ...));

  // ❌ Missing: eventId, correlationId, causationId, schemaVersion
}
```

### What's Missing (From German Checklist)

> **Im Event Header: `eventType`, `schemaVersion`, `aggregateId`, `eventId`, `causationId`, `correlationId`**

**Currently Missing:**
- ❌ `correlationId` - Trace request across entire system
- ❌ `causationId` - Which event caused this event
- ❌ `schemaVersion` - Event schema version for evolution
- ❌ `timestamp` - When event was created (UTC)

**Why This Matters:**

**1. Distributed Tracing**
```
HTTP Request (correlationId=abc-123)
  → Command: CreateCommit (correlationId=abc-123)
    → Event: CommitCreated (correlationId=abc-123)  ← Trace entire flow
      → Projector: Update Repository (correlationId=abc-123)
```

**2. Causality Tracking**
```
Event 1: BranchCreated (eventId=e1)
  → Event 2: CommitCreated (causationId=e1) ← "Caused by e1"
    → Event 3: BranchReset (causationId=e2) ← "Caused by e2"
```

**3. Schema Evolution**
```
Event: CommitCreated (schemaVersion=1.0)  → Old schema
Event: CommitCreated (schemaVersion=2.0)  → New schema with extra field

Projector can handle both schemas based on version
```

---

## Goal

Add comprehensive event metadata headers to support:
1. ✅ Distributed tracing (correlationId)
2. ✅ Causality tracking (causationId)
3. ✅ Schema evolution (schemaVersion)
4. ✅ Event timestamps (timestamp)

---

## Design Decisions

### 1. Correlation ID Source

**Chosen: Request Scope**

**Spring Request Scope Bean:**
```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
  private String correlationId;
  // Auto-generated on request start
}
```

**Alternative:** HTTP Header `X-Correlation-ID` (user-provided)

### 2. Causation ID Tracking

**Chosen: Event-to-Event Chain**

**Pattern:**
```java
// First event (no causation)
Event e1 = new BranchCreatedEvent(causationId=null);

// Second event (caused by e1)
Event e2 = new CommitCreatedEvent(causationId=e1.getEventId());
```

**Stored in event itself (not just header)**

### 3. Schema Version Format

**Chosen: Semantic Versioning**

**Format:** `"1.0"`, `"1.1"`, `"2.0"`

**Per Event Type:**
```java
CommitCreatedEvent.SCHEMA_VERSION = "1.0";
BranchCreatedEvent.SCHEMA_VERSION = "1.0";
```

**Incremented on breaking changes**

---

## Implementation Plan

### Step 1: Create RequestContext Bean (30 min)

**File:** `src/main/java/org/chucc/vcserver/context/RequestContext.java`

```java
package org.chucc.vcserver.context;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * Request-scoped context holding metadata for distributed tracing.
 * Automatically created per HTTP request.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {

  private final String correlationId;
  private final long requestStartTime;

  /**
   * Constructor: Auto-generates correlation ID.
   */
  public RequestContext() {
    this.correlationId = UuidCreator.getTimeOrderedEpoch().toString();
    this.requestStartTime = System.currentTimeMillis();
  }

  /**
   * Returns the correlation ID for this request.
   * All events created during this request will share this ID.
   */
  public String getCorrelationId() {
    return correlationId;
  }

  /**
   * Returns request start time (for duration tracking).
   */
  public long getRequestStartTime() {
    return requestStartTime;
  }

  /**
   * Calculates request duration in milliseconds.
   */
  public long getRequestDurationMs() {
    return System.currentTimeMillis() - requestStartTime;
  }
}
```

**Add interceptor to log correlation ID:**

**File:** `src/main/java/org/chucc/vcserver/config/RequestContextInterceptor.java`

```java
package org.chucc.vcserver.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chucc.vcserver.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to populate MDC with correlation ID for logging.
 */
@Component
public class RequestContextInterceptor implements HandlerInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(RequestContextInterceptor.class);
  private final RequestContext requestContext;

  public RequestContextInterceptor(RequestContext requestContext) {
    this.requestContext = requestContext;
  }

  @Override
  public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) {
    // Add correlation ID to MDC (for logging)
    MDC.put("correlationId", requestContext.getCorrelationId());

    logger.debug("Request started: method={}, uri={}, correlationId={}",
        request.getMethod(), request.getRequestURI(), requestContext.getCorrelationId());

    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler,
                               Exception ex) {
    logger.debug("Request completed: duration={}ms, correlationId={}",
        requestContext.getRequestDurationMs(), requestContext.getCorrelationId());

    // Clear MDC
    MDC.remove("correlationId");
  }
}
```

**Register interceptor:**

**File:** `src/main/java/org/chucc/vcserver/config/WebMvcConfig.java`

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Autowired
  private RequestContextInterceptor requestContextInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestContextInterceptor);
  }
}
```

---

### Step 2: Add Schema Version to Event Records (30 min)

**Pattern:**
```java
public record CommitCreatedEvent(
    String eventId,
    String dataset,
    // ... fields
) implements VersionControlEvent {

  public static final String SCHEMA_VERSION = "1.0";

  @Override
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }
}
```

**Add to VersionControlEvent interface:**
```java
public interface VersionControlEvent {
  String getEventId();
  String dataset();
  AggregateIdentity getAggregateIdentity();

  /**
   * Returns the schema version for this event type.
   * Format: Semantic versioning (e.g., "1.0", "2.0").
   */
  String getSchemaVersion();
}
```

**Apply to all 12 event types** with `SCHEMA_VERSION = "1.0"`.

---

### Step 3: Add Causation ID to Event Records (20 min)

**Update select events to support causation:**

**CommitCreatedEvent** (most important):
```java
public record CommitCreatedEvent(
    String eventId,
    String dataset,
    String commitId,
    List<String> parents,
    String branch,
    String author,
    String message,
    Instant timestamp,
    String rdfPatch,
    String causationId  // ✅ NEW: Optional causation ID
) implements VersionControlEvent {

  /**
   * Factory method with causation.
   */
  public static CommitCreatedEvent createWithCausation(
      String causationId,
      String dataset,
      // ... other params
  ) {
    return new CommitCreatedEvent(
        UuidCreator.getTimeOrderedEpoch().toString(),
        dataset,
        commitId,
        parents,
        branch,
        author,
        message,
        timestamp,
        rdfPatch,
        causationId  // Store causation
    );
  }

  /**
   * Factory method without causation (original).
   */
  public static CommitCreatedEvent create(
      String dataset,
      // ... params
  ) {
    return createWithCausation(null, dataset, ...);
  }
}
```

**Add causationId to select events:**
- CommitCreatedEvent ✅
- BranchResetEvent ✅
- RevertCreatedEvent ✅
- (Others: add if causation makes sense)

---

### Step 4: Update EventPublisher Headers (15 min)

**File:** `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

```java
private void addHeaders(Headers headers, VersionControlEvent event) {
  // Core event metadata
  headers.add(new RecordHeader(EventHeaders.EVENT_ID,
      event.getEventId().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.EVENT_TYPE,
      event.getClass().getSimpleName()
          .replace("Event", "")
          .getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.SCHEMA_VERSION,
      event.getSchemaVersion().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.DATASET,
      event.dataset().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.TIMESTAMP,
      String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8)));

  // Aggregate metadata
  AggregateIdentity aggregateId = event.getAggregateIdentity();
  headers.add(new RecordHeader(EventHeaders.AGGREGATE_TYPE,
      aggregateId.getAggregateType().getBytes(StandardCharsets.UTF_8)));
  headers.add(new RecordHeader(EventHeaders.AGGREGATE_ID,
      aggregateId.getPartitionKey().getBytes(StandardCharsets.UTF_8)));

  // Tracing metadata
  String correlationId = getCorrelationId();
  if (correlationId != null) {
    headers.add(new RecordHeader(EventHeaders.CORRELATION_ID,
        correlationId.getBytes(StandardCharsets.UTF_8)));
  }

  // Causation (if event supports it)
  if (event instanceof CausationAware causationAwareEvent) {
    String causationId = causationAwareEvent.getCausationId();
    if (causationId != null) {
      headers.add(new RecordHeader(EventHeaders.CAUSATION_ID,
          causationId.getBytes(StandardCharsets.UTF_8)));
    }
  }

  // ... event-specific headers (branch, commit, etc.)
}

/**
 * Get correlation ID from request context (Spring request scope).
 */
private String getCorrelationId() {
  try {
    return requestContext.getCorrelationId();
  } catch (Exception e) {
    // Not in request scope (e.g., background job) - no correlation ID
    return null;
  }
}
```

**Update EventHeaders constants:**
```java
public final class EventHeaders {
  public static final String EVENT_ID = "eventId";
  public static final String EVENT_TYPE = "eventType";
  public static final String SCHEMA_VERSION = "schemaVersion";
  public static final String DATASET = "dataset";
  public static final String TIMESTAMP = "timestamp";
  public static final String CORRELATION_ID = "correlationId";
  public static final String CAUSATION_ID = "causationId";
  public static final String AGGREGATE_TYPE = "aggregateType";
  public static final String AGGREGATE_ID = "aggregateId";
  // ... existing
}
```

---

### Step 5: Create CausationAware Interface (10 min)

**File:** `src/main/java/org/chucc/vcserver/event/CausationAware.java`

```java
package org.chucc.vcserver.event;

/**
 * Marker interface for events that support causation tracking.
 * Causation ID indicates which event caused this event to be created.
 */
public interface CausationAware {

  /**
   * Returns the event ID that caused this event.
   * Returns null if this event was not caused by another event (user action).
   */
  String getCausationId();
}
```

**Implement in select events:**
```java
public record CommitCreatedEvent(..., String causationId)
    implements VersionControlEvent, CausationAware {

  @Override
  public String getCausationId() {
    return causationId;
  }
}
```

---

### Step 6: Update Logging to Use Correlation ID (15 min)

**Update logback-spring.xml** to include correlation ID:

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [correlationId=%X{correlationId}] - %msg%n</pattern>
    </encoder>
  </appender>
</configuration>
```

**Example log output:**
```
2025-10-22 10:30:15.123 [http-nio-8080-exec-1] INFO  GraphStoreController [correlationId=01932c5c-8f7a-7890-b123] - PUT /data?branch=main
2025-10-22 10:30:15.125 [http-nio-8080-exec-1] INFO  EventPublisher [correlationId=01932c5c-8f7a-7890-b123] - Publishing CommitCreatedEvent
2025-10-22 10:30:15.130 [kafka-listener-1] INFO  ReadModelProjector [correlationId=01932c5c-8f7a-7890-b123] - Processing CommitCreatedEvent
```

---

### Step 7: Write Tests (30 min)

**Unit Test:**
```java
@Test
void publish_shouldIncludeCorrelationIdInHeaders() {
  // Arrange: Mock request context
  when(requestContext.getCorrelationId()).thenReturn("test-correlation-123");

  CommitCreatedEvent event = CommitCreatedEvent.create(...);

  // Act
  eventPublisher.publish(event);

  // Assert: Verify header
  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  Headers headers = captor.getValue().headers();
  String correlationId = new String(headers.lastHeader("correlationId").value());
  assertThat(correlationId).isEqualTo("test-correlation-123");
}

@Test
void publish_shouldIncludeSchemaVersionInHeaders() {
  CommitCreatedEvent event = CommitCreatedEvent.create(...);

  eventPublisher.publish(event);

  ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> captor =
      ArgumentCaptor.forClass(ProducerRecord.class);
  verify(kafkaTemplate).send(captor.capture());

  Headers headers = captor.getValue().headers();
  String schemaVersion = new String(headers.lastHeader("schemaVersion").value());
  assertThat(schemaVersion).isEqualTo("1.0");
}
```

---

### Step 8: Documentation (20 min)

**Update:** `docs/architecture/cqrs-event-sourcing.md`

```markdown
## Event Metadata

All events include comprehensive metadata headers for tracing and debugging:

### Header Fields

| Header | Type | Description | Example |
|--------|------|-------------|---------|
| `eventId` | String (UUIDv7) | Unique event identifier | `01932c5c-8f7a-7890-b123-456789abcdef` |
| `eventType` | String | Event type name | `CommitCreated` |
| `schemaVersion` | String | Event schema version (semantic versioning) | `1.0` |
| `dataset` | String | Target dataset name | `default` |
| `timestamp` | Long | Event creation timestamp (epoch millis) | `1729593600000` |
| `correlationId` | String (UUIDv7) | Request trace ID (entire flow) | `01932c5c-1234-...` |
| `causationId` | String (UUIDv7) | Which event caused this event | `01932c5c-5678-...` |
| `aggregateType` | String | Aggregate type | `Branch` |
| `aggregateId` | String | Aggregate instance ID | `default:main` |

### Distributed Tracing

**Correlation ID**: Tracks entire request flow across system

```
HTTP Request → Controller → CommandHandler → EventPublisher → Kafka → Projector
       ↓              ↓             ↓               ↓            ↓           ↓
correlationId=abc-123 (same ID throughout entire flow)
```

**Example:**
```
2025-10-22 10:30:15.123 [correlationId=abc-123] GraphStoreController: PUT /data
2025-10-22 10:30:15.125 [correlationId=abc-123] EventPublisher: Publishing CommitCreatedEvent
2025-10-22 10:30:15.130 [correlationId=abc-123] ReadModelProjector: Processing event
```

### Causation Tracking

**Causation ID**: Links events in event-driven flows

```
Event 1: BranchCreated (eventId=e1, causationId=null)
  ↓
Event 2: CommitCreated (eventId=e2, causationId=e1)  ← "Caused by e1"
  ↓
Event 3: BranchReset (eventId=e3, causationId=e2)    ← "Caused by e2"
```

### Schema Evolution

**Schema Version**: Enables backward-compatible changes

```java
// Version 1.0 (current)
public record CommitCreatedEvent(
    String eventId,
    String commitId,
    String author,
    String message
) {
  public static final String SCHEMA_VERSION = "1.0";
}

// Version 2.0 (future - adds new field)
public record CommitCreatedEvent(
    String eventId,
    String commitId,
    String author,
    String message,
    List<String> tags  // ✅ NEW field
) {
  public static final String SCHEMA_VERSION = "2.0";
}

// Projector handles both
public void handleCommitCreated(CommitCreatedEvent event) {
  if (event.getSchemaVersion().equals("1.0")) {
    // Handle v1.0
  } else if (event.getSchemaVersion().equals("2.0")) {
    // Handle v2.0 (with tags)
  }
}
```
```

---

## Success Criteria

- [ ] RequestContext bean created (request scope)
- [ ] RequestContextInterceptor logs correlation ID
- [ ] All events have SCHEMA_VERSION constant
- [ ] CausationAware interface created
- [ ] Select events support causation ID
- [ ] EventPublisher adds all metadata headers
- [ ] Logging includes correlation ID
- [ ] Unit tests verify header inclusion (5+ tests)
- [ ] Documentation updated
- [ ] All tests pass
- [ ] Zero quality violations

---

## References

- German Kafka CQRS/ES Best Practices
- [Correlation ID Pattern (Microsoft)](https://docs.microsoft.com/en-us/azure/architecture/patterns/correlation-id)
- [EventPublisher.java](src/main/java/org/chucc/vcserver/event/EventPublisher.java)

---

## Notes

**Complexity:** Medium
**Time:** ~2-3 hours
**Risk:** Low (additive change)

This task enables distributed tracing and schema evolution - critical for production systems.
