# Task 04: Fix Command Handler Silent Failures (CRITICAL)

## Objective

**CRITICAL FIX:** Prevent silent data loss by ensuring command handlers wait for Kafka confirmation before returning success to clients.

## Issue Summary

**Current Behavior (BROKEN):**
1. Client sends HTTP request (e.g., `PUT /data?graph=...`)
2. Command handler creates event
3. Command handler publishes event to Kafka **asynchronously** (fire-and-forget)
4. Command handler returns event immediately (doesn't wait for Kafka)
5. Controller returns **HTTP 200 OK** to client
6. **IF** Kafka publishing fails (network, Kafka down, etc.):
   - Exception is logged but swallowed (`.exceptionally()` returns null)
   - Client thinks operation succeeded
   - Event never reaches Kafka
   - Read model never updated
   - **SILENT DATA LOSS**

**Expected Behavior (CORRECT):**
1. Client sends HTTP request
2. Command handler creates event
3. Command handler publishes event to Kafka and **WAITS** for confirmation
4. **IF** Kafka publishing fails:
   - Exception propagates to controller
   - Controller returns **HTTP 500 Internal Server Error**
   - Client knows operation failed and can retry
5. **ONLY IF** Kafka confirms:
   - Command handler returns event
   - Controller returns HTTP 200 OK
   - Read model will be updated (eventually)

---

## Root Cause

### Problematic Pattern (Fire-and-Forget)

**Location:** Multiple files use this pattern

```java
// ❌ BROKEN: Fire-and-forget with swallowed exception
eventPublisher.publish(event)
    .exceptionally(ex -> {
      logger.error("Failed to publish event {}: {}",
          event.getClass().getSimpleName(), ex.getMessage(), ex);
      return null;  // ❌ Swallows exception
    });

return event;  // ❌ Returns immediately (before Kafka confirms!)
```

**Why this is dangerous:**
- `.exceptionally()` catches the exception from the `CompletableFuture`
- Returning `null` converts failed future → successful future (with null value)
- Caller never knows about the failure
- HTTP 200 OK sent to client even though Kafka is down

---

## Implementation Plan

### Step 1: Fix GraphCommandUtil.finalizeAndPublishGraphCommand()

**File:** `src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java`
**Lines:** 165-176

**Before:**
```java
// Publish event to Kafka (fire-and-forget, async)
if (event != null) {
  eventPublisher.publish(event)
      .exceptionally(ex -> {
        logger.error("Failed to publish event {}: {}",
            event.getClass().getSimpleName(), ex.getMessage(), ex);
        return null;  // ❌ Swallows exception
      });
}

return event;  // ❌ Returns before Kafka confirms
```

**After:**
```java
// Publish event to Kafka and wait for confirmation
if (event != null) {
  try {
    // Block until Kafka confirms (or fails)
    eventPublisher.publish(event).get();
    logger.debug("Successfully published event {} to Kafka",
        event.getClass().getSimpleName());
  } catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    logger.error("Interrupted while publishing event {}: {}",
        event.getClass().getSimpleName(), ex.getMessage(), ex);
    throw new IllegalStateException("Failed to publish event to Kafka", ex);
  } catch (ExecutionException ex) {
    logger.error("Failed to publish event {} to Kafka: {}",
        event.getClass().getSimpleName(), ex.getMessage(), ex);
    throw new IllegalStateException("Failed to publish event to Kafka", ex.getCause());
  }
}

return event;
```

**Why `.get()` is correct:**
- Blocks current thread until Kafka confirms (sync)
- Throws exception if Kafka fails
- Exception propagates to controller → HTTP 500
- Client knows operation failed and can retry

**Performance consideration:**
- Yes, this adds latency (~10-50ms for Kafka round-trip)
- BUT: This is CORRECT behavior per SPARQL Protocol
- HTTP response MUST reflect whether operation succeeded
- Trading slight latency for **data integrity** is the right choice

---

### Step 2: Fix CreateCommitCommandHandler.handle()

**File:** `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`
**Lines:** 138-146

**Before:**
```java
// Publish event to Kafka (fire-and-forget, async)
eventPublisher.publish(event)
    .exceptionally(ex -> {
      logger.error("Failed to publish event {}: {}",
          event.getClass().getSimpleName(), ex.getMessage(), ex);
      return null;
    });

return event;
```

**After:**
```java
// Publish event to Kafka and wait for confirmation
try {
  eventPublisher.publish(event).get();
  logger.debug("Successfully published CommitCreatedEvent to Kafka");
} catch (InterruptedException ex) {
  Thread.currentThread().interrupt();
  logger.error("Interrupted while publishing event {}: {}",
      event.getClass().getSimpleName(), ex.getMessage(), ex);
  throw new IllegalStateException("Failed to publish event to Kafka", ex);
} catch (ExecutionException ex) {
  logger.error("Failed to publish event {} to Kafka: {}",
      event.getClass().getSimpleName(), ex.getMessage(), ex);
  throw new IllegalStateException("Failed to publish event to Kafka", ex.getCause());
}

return event;
```

---

### Step 3: Find and Fix All Other Command Handlers

**Search pattern:**
```bash
grep -rn "eventPublisher.publish" src/main/java/org/chucc/vcserver/command/
```

**Files to review:**
- [x] `CreateCommitCommandHandler.java`
- [x] `GraphCommandUtil.java` (affects PUT, POST, DELETE, PATCH)
- [ ] Check all other `*CommandHandler.java` files

**For each file:**
1. Search for `eventPublisher.publish(event)`
2. If followed by `.exceptionally()` → **FIX REQUIRED**
3. Replace with `.get()` pattern (see Step 1)

---

### Step 4: Add Helper Method (Optional, for DRY)

Create utility method in `GraphCommandUtil` or `EventPublisher`:

```java
/**
 * Publishes event to Kafka and blocks until confirmation.
 *
 * @param event the event to publish
 * @throws IllegalStateException if publishing fails
 */
public static void publishAndWait(EventPublisher publisher, VersionControlEvent event) {
  try {
    publisher.publish(event).get();
  } catch (InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new IllegalStateException("Failed to publish event to Kafka", ex);
  } catch (ExecutionException ex) {
    throw new IllegalStateException("Failed to publish event to Kafka", ex.getCause());
  }
}
```

Then use in handlers:
```java
GraphCommandUtil.publishAndWait(eventPublisher, event);
return event;
```

---

### Step 5: Add Integration Test

**File:** Create `CommandFailureHandlingIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class CommandFailureHandlingIT extends IntegrationTestFixture {

  @Test
  void putGraph_whenKafkaPublishFails_shouldReturn500() {
    // This test requires mocking EventPublisher to simulate Kafka failure
    // OR: Stop Kafka container mid-test (more realistic but complex)

    // Arrange: Prepare valid RDF content
    String turtle = "@prefix ex: <http://example.org/> . ex:subject ex:predicate ex:object .";

    // TODO: Mock EventPublisher to throw exception
    // (or stop Kafka container)

    // Act: Send PUT request
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        String.class
    );

    // Assert: Should return 500, not 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

    // Assert: Error response should mention Kafka
    String body = response.getBody();
    assertThat(body).contains("Failed to publish event to Kafka");
  }

  @Test
  void putGraph_whenKafkaAvailable_shouldReturn200() {
    // This test verifies the happy path still works

    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:subject ex:predicate ex:object .";

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        String.class
    );

    // Assert: Should return 200 OK
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();
  }
}
```

**Note:** Testing Kafka failures is tricky. Options:
1. Mock `EventPublisher` (unit test level)
2. Stop Kafka container mid-test (integration test level, more realistic)
3. Use Testcontainers pause/unpause (cleanest approach)

---

### Step 6: Update Error Handling Documentation

Add to `docs/architecture/cqrs-event-sourcing.md`:

```markdown
## Command-Side Exception Handling

### Synchronous Event Publishing

**Important:** Command handlers MUST wait for Kafka confirmation before returning.

```java
// ❌ WRONG: Fire-and-forget (silent failures)
eventPublisher.publish(event).exceptionally(ex -> { log; return null; });
return event;

// ✅ CORRECT: Wait for Kafka confirmation
eventPublisher.publish(event).get();  // Throws on failure
return event;
```

**Why this matters:**
- HTTP response reflects actual operation status
- If Kafka is down, client gets HTTP 500 (can retry)
- If Kafka succeeds, client gets HTTP 200 (operation succeeded)
- No silent data loss

**Performance:**
- Adds ~10-50ms latency (Kafka round-trip time)
- Acceptable trade-off for data integrity
- Still achieves "eventual consistency" (read model updates async)
```

---

## Acceptance Criteria

- [ ] `GraphCommandUtil.finalizeAndPublishGraphCommand()` uses `.get()` to wait for Kafka
- [ ] `CreateCommitCommandHandler.handle()` uses `.get()` to wait for Kafka
- [ ] All other command handlers reviewed and fixed (if needed)
- [ ] Integration test added to verify Kafka failures return HTTP 500
- [ ] Integration test verifies happy path still returns HTTP 200
- [ ] Documentation updated in CQRS guide
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] All tests pass

---

## Files to Modify

- **CRITICAL:**
  - `src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java` (lines 165-176)
  - `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java` (lines 138-146)

- **Review (may need fixes):**
  - All files matching `src/main/java/org/chucc/vcserver/command/*CommandHandler.java`

- **New Files:**
  - `src/test/java/org/chucc/vcserver/integration/CommandFailureHandlingIT.java`

- **Documentation:**
  - `docs/architecture/cqrs-event-sourcing.md` (add command-side exception handling section)

---

## Estimated Time

3-4 hours
- 1 hour: Fix command handlers
- 1 hour: Add integration tests
- 1 hour: Run full test suite and fix any issues
- 1 hour: Update documentation

---

## Priority

**CRITICAL** - This is a data loss bug that affects all write operations.

---

## Notes

### Why .get() Instead of Async?

**Question:** "Why not keep async and just propagate exceptions properly?"

**Answer:** Controllers are synchronous HTTP endpoints. Options:

1. **Synchronous (.get())** - RECOMMENDED
   ```java
   VersionControlEvent event = handler.handle(command);  // Blocks until Kafka confirms
   return ResponseEntity.ok().build();  // 200 OK only if Kafka succeeded
   ```
   - Simple, correct, clear semantics
   - Adds ~10-50ms latency
   - HTTP response accurately reflects operation status

2. **Async (CompletableFuture)** - COMPLEX
   ```java
   CompletableFuture<VersionControlEvent> eventFuture = handler.handleAsync(command);
   return eventFuture.thenApply(event -> ResponseEntity.ok().build());
   ```
   - Requires Spring WebFlux or Servlet 3.1 async support
   - More complex error handling
   - Marginal performance benefit in this use case
   - HTTP layer becomes async (DeferredResult, etc.)

**Recommendation:** Use `.get()` for simplicity and correctness. The latency is acceptable and the semantics are clearer.

---

## References

- [Audit Findings Report](./audit-findings.md#3-command-handlers---critical-issue)
- [CompletableFuture.get() Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html#get())
- [Spring Kafka Producer Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html#kafka-template-send-results)
