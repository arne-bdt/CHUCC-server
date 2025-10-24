# Task 04: Implement HTTP 202 Accepted for Eventual Consistency

## Objective

**Fix the command-side fire-and-forget pattern** to properly reflect the eventual consistency model of CQRS + Event Sourcing.

## Issue Summary

**Current Behavior (BROKEN):**
1. Client sends HTTP request (e.g., `PUT /data?graph=...`)
2. Command handler creates event and publishes to Kafka **asynchronously**
3. Command handler uses `.exceptionally()` that **swallows exceptions** (returns null)
4. Controller returns **HTTP 200 OK** immediately
5. **Problems:**
   - ❌ If Kafka fails, client gets HTTP 200 but data is lost (silent failure)
   - ❌ HTTP 200 implies data is queryable, but read model hasn't updated yet
   - ❌ Lies about consistency model (pretends to be synchronous)

**Correct Behavior (This Task):**
1. Client sends HTTP request
2. Command handler creates event and publishes to Kafka **asynchronously**
3. Command handler logs failures but **doesn't swallow exceptions**
4. Controller returns **HTTP 202 Accepted** (not 200 OK)
5. **Benefits:**
   - ✅ HTTP 202 = "Accepted for processing, not yet complete"
   - ✅ Honest about eventual consistency
   - ✅ Clients know they should use ETag or poll for completion
   - ✅ If Kafka fails before HTTP response, return HTTP 503 Service Unavailable
   - ✅ Aligns with CQRS + Event Sourcing patterns

---

## Architecture Decision

### Why HTTP 202 Accepted? (Not 200 OK)

From [RFC 9110 §15.3.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3):

> The 202 (Accepted) status code indicates that the request has been accepted for processing, but the processing has not been completed.

**This is perfect for Event Sourcing:**
- Event stored ✅ (in Kafka)
- Processing started ✅ (projector will process)
- Processing NOT complete ❌ (read model updates asynchronously)

**Alternative considered and rejected:**
- ❌ HTTP 200 OK - Implies synchronous completion (not true)
- ❌ HTTP 201 Created - Implies resource exists and is queryable (not yet true)
- ❌ `.get()` to wait for Kafka - Adds latency, still doesn't wait for projection

---

## Root Cause

### Problematic Pattern (Fire-and-Forget with Swallowed Exceptions)

**Location:** Multiple files use this pattern

```java
// ❌ BROKEN: Exception swallowed, returns null on failure
eventPublisher.publish(event)
    .exceptionally(ex -> {
      logger.error("Failed to publish event {}: {}",
          event.getClass().getSimpleName(), ex.getMessage(), ex);
      return null;  // ❌ Converts failed future → successful future with null
    });

return event;  // ❌ Returns immediately, even if Kafka failed
```

**Why this is dangerous:**
- `.exceptionally()` catches exceptions from `CompletableFuture`
- Returning `null` makes the future "successful" (just with null value)
- Caller never knows about the failure
- HTTP 200 OK sent even if Kafka is down

---

## Implementation Plan

### Step 1: Fix Exception Handling in GraphCommandUtil

**File:** [src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java](../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java:165-176)

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

return event;
```

**After:**
```java
// Publish event to Kafka (async, with proper error logging)
if (event != null) {
  eventPublisher.publish(event)
      .whenComplete((result, ex) -> {
        if (ex != null) {
          logger.error("Failed to publish event {} to Kafka: {}",
              event.getClass().getSimpleName(), ex.getMessage(), ex);
          // Note: Exception logged but not swallowed
          // If this happens before HTTP response, controller will catch it
        } else {
          logger.debug("Successfully published event {} to Kafka",
              event.getClass().getSimpleName());
        }
      });
}

return event;
```

**Key difference:**
- `.whenComplete()` doesn't suppress the exception (unlike `.exceptionally()`)
- Exception will propagate if it occurs before HTTP response is sent
- If exception occurs after HTTP response sent, it's logged but doesn't affect client

---

### Step 2: Fix Exception Handling in CreateCommitCommandHandler

**File:** [src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java](../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java:138-146)

**Apply same fix as Step 1:**

```java
// Publish event to Kafka (async, with proper error logging)
eventPublisher.publish(event)
    .whenComplete((result, ex) -> {
      if (ex != null) {
        logger.error("Failed to publish event {} to Kafka: {}",
            event.getClass().getSimpleName(), ex.getMessage(), ex);
      } else {
        logger.debug("Successfully published event {} to Kafka",
            event.getClass().getSimpleName());
      }
    });

return event;
```

---

### Step 3: Find and Fix All Other Command Handlers

**Search for all instances:**
```bash
grep -rn "\.exceptionally" src/main/java/org/chucc/vcserver/command/
```

**For each occurrence of `.exceptionally()` that swallows exceptions:**
- Replace with `.whenComplete()` pattern
- Log errors but don't suppress them

---

### Step 4: Update Controllers to Return HTTP 202 Accepted

**Files to update:** All controllers that handle write operations

**Example: GraphStoreController.putGraph()**

**File:** [src/main/java/org/chucc/vcserver/controller/GraphStoreController.java](../src/main/java/org/chucc/vcserver/controller/GraphStoreController.java:463-479)

**Before:**
```java
// Handle successful commit creation
if (event instanceof org.chucc.vcserver.event.CommitCreatedEvent commitEvent) {
  HttpHeaders headers = new HttpHeaders();
  headers.set(HttpHeaders.LOCATION, "/version/commits/" + commitEvent.commitId());
  headers.setETag("\"" + commitEvent.commitId() + "\"");
  headers.set("SPARQL-Version-Control", "true");

  return ResponseEntity.ok().headers(headers).build();  // ❌ 200 OK
}
```

**After:**
```java
// Handle successful commit creation
if (event instanceof org.chucc.vcserver.event.CommitCreatedEvent commitEvent) {
  HttpHeaders headers = new HttpHeaders();
  headers.set(HttpHeaders.LOCATION, "/version/commits/" + commitEvent.commitId());
  headers.setETag("\"" + commitEvent.commitId() + "\"");
  headers.set("SPARQL-Version-Control", "true");
  headers.set("SPARQL-VC-Status", "pending");  // Indicates projection in progress

  return ResponseEntity
      .accepted()  // ✅ 202 Accepted
      .headers(headers)
      .build();
}
```

**Apply this pattern to:**
- [GraphStoreController.java](../src/main/java/org/chucc/vcserver/controller/GraphStoreController.java) - PUT, POST, DELETE, PATCH
- [SparqlController.java](../src/main/java/org/chucc/vcserver/controller/SparqlController.java) - SPARQL Update
- [BranchController.java](../src/main/java/org/chucc/vcserver/controller/BranchController.java) - Branch operations
- [MergeController.java](../src/main/java/org/chucc/vcserver/controller/MergeController.java) - Merge operations
- [TagController.java](../src/main/java/org/chucc/vcserver/controller/TagController.java) - Tag operations
- [BatchController.java](../src/main/java/org/chucc/vcserver/controller/BatchController.java) - Batch operations
- [AdvancedOpsController.java](../src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java) - Squash, cherry-pick

---

### Step 5: Update OpenAPI Annotations in Controllers

**Files to update:**
- Update `@ApiResponse` annotations to use `202` instead of `200`/`201`/`204`
- Add documentation explaining eventual consistency

**Example:**
```java
@ApiResponse(
    responseCode = "202",
    description = "Accepted - Event published to Kafka, read model will update asynchronously. "
        + "Use ETag header in subsequent queries to verify projection completion.",
    headers = {
        @Header(
            name = "Location",
            description = "URI of the created commit",
            schema = @Schema(type = "string")
        ),
        @Header(
            name = "ETag",
            description = "Commit ID of the new state (may not be queryable yet)",
            schema = @Schema(type = "string")
        ),
        @Header(
            name = "SPARQL-VC-Status",
            description = "Status of projection: 'pending' (not yet queryable)",
            schema = @Schema(type = "string", allowableValues = {"pending"})
        )
    }
)
```

---

### Step 6: Update Protocol Specifications

**File:** [protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md](../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

Update the "Response Codes" section for SPARQL Update operations:

```markdown
## Response Codes

### SPARQL Update Operations

- **202 Accepted** - Update accepted, event published to Kafka, read model will update asynchronously
  - Response includes:
    - `Location`: URI of created commit
    - `ETag`: Commit ID
    - `SPARQL-VC-Status: pending`
- **400 Bad Request** - Malformed SPARQL Update query
- **409 Conflict** - Concurrent modification detected
```

**File:** [protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md](../protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md)

Update response codes for PUT, POST, DELETE, PATCH operations:

```markdown
## Response Codes

### Write Operations (PUT, POST, DELETE, PATCH)

- **202 Accepted** - Operation accepted, event published to Kafka
  - Response headers:
    - `Location`: URI of created commit
    - `ETag`: Commit ID of new state
    - `SPARQL-VC-Status: pending` - Read model projection in progress
- **204 No Content** - No-op (empty patch, no changes)
- **400 Bad Request** - Invalid RDF content or parameters
- **409 Conflict** - Concurrent modification detected (If-Match mismatch)
- **412 Precondition Failed** - If-Match header doesn't match current state

### Eventual Consistency

Write operations use **eventual consistency**:
1. HTTP 202 returned when event published to Kafka
2. Read model updates asynchronously (typically 50-200ms)
3. Clients can query immediately using commit selector: `?commit={commitId}`
4. Queries via branch selector return updated data after projection completes
```

---

### Step 7: Update OpenAPI Specification

**File:** [api/openapi.yaml](../api/openapi.yaml)

Update all write operation responses (search for `responses:` in PUT/POST/DELETE/PATCH operations):

**Before:**
```yaml
responses:
  '200':
    description: Graph updated successfully
    headers:
      ETag:
        schema:
          type: string
        description: Commit ID of the new state
```

**After:**
```yaml
responses:
  '202':
    description: Accepted - Event published to Kafka, read model will update asynchronously
    headers:
      Location:
        schema:
          type: string
        description: URI of the created commit
        example: /version/commits/01JFQM4X5K...
      ETag:
        schema:
          type: string
        description: Commit ID of the new state (may not be queryable yet)
        example: "01JFQM4X5K..."
      SPARQL-VC-Status:
        schema:
          type: string
          enum: [pending]
        description: Projection status (pending = not yet queryable via branch selector)
```

Apply to all write operations:
- `/data` (PUT, POST, DELETE, PATCH)
- `/sparql` (POST for UPDATE)
- `/version/branches` (POST, DELETE)
- `/version/tags` (POST, DELETE)
- `/version/merge` (POST)
- `/version/squash` (POST)
- `/version/cherry-pick` (POST)

---

### Step 8: Add Integration Tests

**File:** Create [src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java](../src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class EventualConsistencyIT extends IntegrationTestFixture {

  @Test
  void putGraph_shouldReturn202Accepted() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    // Act
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        Void.class
    );

    // Assert: HTTP 202 Accepted (not 200 OK)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Assert: Headers present
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getFirst("SPARQL-VC-Status"))
        .isEqualTo("pending");
  }

  @Test
  void putGraph_eventuallyBecomesQueryable() {
    // This test verifies eventual consistency
    // Note: Projector is DISABLED by default, so this only tests the event flow

    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    // Act: PUT graph
    ResponseEntity<Void> putResponse = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        Void.class
    );

    // Assert: HTTP 202 Accepted
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String commitId = extractEtag(putResponse.getHeaders().getETag());

    // Note: We can query using commit selector (bypasses read model)
    ResponseEntity<String> getResponse = restTemplate.exchange(
        "/data?graph=http://example.org/graph1&commit=" + commitId,
        HttpMethod.GET,
        new HttpEntity<>(createHeaders("text/turtle")),
        String.class
    );

    // Assert: Can query via commit selector (uses event store directly)
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody()).contains("ex:s");
  }

  @Test
  @TestPropertySource(properties = "projector.kafka-listener.enabled=true")
  void putGraph_withProjectorEnabled_eventuallyUpdatesReadModel() throws Exception {
    // This test verifies async projection (requires projector enabled)

    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    // Act: PUT graph
    ResponseEntity<Void> putResponse = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        Void.class
    );

    // Assert: HTTP 202 Accepted
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Assert: Eventually queryable via branch selector (uses read model)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          ResponseEntity<String> getResponse = restTemplate.exchange(
              "/data?graph=http://example.org/graph1&branch=main",
              HttpMethod.GET,
              new HttpEntity<>(createHeaders("text/turtle")),
              String.class
          );
          assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(getResponse.getBody()).contains("ex:s");
        });
  }
}
```

---

### Step 9: Update Architecture Documentation

**File:** [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)

Add section:

```markdown
## HTTP Status Codes and Eventual Consistency

### Write Operations Return HTTP 202 Accepted

All write operations (PUT, POST, DELETE, PATCH, SPARQL Update) return **HTTP 202 Accepted**.

**Why 202, not 200?**
- Event is stored durably in Kafka ✅
- Read model updates asynchronously ⏳
- HTTP 202 = "Accepted for processing, not yet complete" (RFC 9110)

**Response headers:**
- `Location`: URI of created commit (`/version/commits/{commitId}`)
- `ETag`: Commit ID of new state
- `SPARQL-VC-Status`: `pending` (read model not yet updated)

**Client workflow:**

```http
# 1. Client sends write request
PUT /data?graph=http://example.org/graph1
Content-Type: text/turtle

@prefix ex: <http://example.org/> . ex:s ex:p ex:o .

# 2. Server responds with 202 Accepted
HTTP/1.1 202 Accepted
Location: /version/commits/01JFQM4X5K...
ETag: "01JFQM4X5K..."
SPARQL-VC-Status: pending

# 3a. Client queries using commit selector (immediate)
GET /data?graph=http://example.org/graph1&commit=01JFQM4X5K...

HTTP/1.1 200 OK  ← Works immediately (uses event store)

# 3b. Client queries using branch selector (eventual)
GET /data?graph=http://example.org/graph1&branch=main

HTTP/1.1 200 OK  ← Works after projection completes (~100ms)
```

**Projection latency:** Typically 50-200ms (p99 < 500ms)

### Read Model Lag Detection

Clients can detect if read model hasn't caught up yet:

```http
GET /data?graph=http://example.org/graph1&branch=main
If-Match: "01JFQM4X5K..."  ← Expected commit from write response

HTTP/1.1 412 Precondition Failed  ← Read model hasn't reached this commit yet
```

Client should retry with exponential backoff or use commit selector.

### Consistency Guarantees

| Guarantee | Status | Notes |
|-----------|--------|-------|
| **Event durability** | ✅ Strong | Events stored in Kafka before HTTP 202 |
| **Read-your-writes** | ⏳ Eventual | Use commit selector for immediate reads |
| **Monotonic reads** | ✅ Guaranteed | ETag-based versioning ensures no time travel |
| **Causal consistency** | ✅ Guaranteed | Event ordering preserved per dataset+branch |
```

---

## Acceptance Criteria

- [ ] `GraphCommandUtil.finalizeAndPublishGraphCommand()` uses `.whenComplete()` (not `.exceptionally()`)
- [ ] `CreateCommitCommandHandler.handle()` uses `.whenComplete()` (not `.exceptionally()`)
- [ ] All command handlers reviewed and fixed
- [ ] All write endpoints return **HTTP 202 Accepted** (not 200/201/204)
- [ ] Response includes `SPARQL-VC-Status: pending` header
- [ ] OpenAPI documentation updated to reflect HTTP 202
- [ ] Integration tests verify HTTP 202 response
- [ ] Integration tests verify eventual consistency (with projector enabled)
- [ ] Documentation updated in CQRS guide
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] All tests pass (~911+ tests)

---

## Files to Modify

**CRITICAL (Exception Handling):**
- [src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java](../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java:165-176)
- [src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java](../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java:138-146)

**Controllers (HTTP 202):**
- [src/main/java/org/chucc/vcserver/controller/GraphStoreController.java](../src/main/java/org/chucc/vcserver/controller/GraphStoreController.java)
- [src/main/java/org/chucc/vcserver/controller/SparqlController.java](../src/main/java/org/chucc/vcserver/controller/SparqlController.java)
- [src/main/java/org/chucc/vcserver/controller/BranchController.java](../src/main/java/org/chucc/vcserver/controller/BranchController.java)
- [src/main/java/org/chucc/vcserver/controller/MergeController.java](../src/main/java/org/chucc/vcserver/controller/MergeController.java)
- [src/main/java/org/chucc/vcserver/controller/TagController.java](../src/main/java/org/chucc/vcserver/controller/TagController.java)
- [src/main/java/org/chucc/vcserver/controller/BatchController.java](../src/main/java/org/chucc/vcserver/controller/BatchController.java)
- [src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java](../src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java)

**Tests (New):**
- [src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java](../src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java)

**Protocol Specifications:**
- [protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md](../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md) (update write operations to specify HTTP 202)
- [protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md](../protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md) (update GSP operations to specify HTTP 202)

**API Specification:**
- [api/openapi.yaml](../api/openapi.yaml) (update all write operation responses: 202 instead of 200/201/204)

**Documentation:**
- [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)
- [docs/api/api-extensions.md](../docs/api/api-extensions.md) (add `SPARQL-VC-Status` header docs)

---

## Estimated Time

5-6 hours
- 1 hour: Fix exception handling in command handlers
- 2 hours: Update all controllers to return HTTP 202
- 1 hour: Add integration tests
- 1-2 hours: Update protocol specifications, OpenAPI spec, and documentation

---

## Priority

**CRITICAL** - This fix addresses:
1. Silent data loss bug (swallowed exceptions)
2. Misleading HTTP semantics (200 OK implies synchronous completion)
3. Misalignment with CQRS + Event Sourcing patterns

---

## Trade-offs and Alternatives

### Alternative: Synchronous (.get() to wait for Kafka)

```java
// Wait for Kafka confirmation before returning
eventPublisher.publish(event).get();
return ResponseEntity.ok().build();  // 200 OK
```

**Rejected because:**
- Still lies about consistency (read model not updated yet)
- Adds latency (~10-50ms)
- Doesn't align with eventual consistency model

### Alternative: Keep HTTP 200 OK

**Rejected because:**
- HTTP 200 OK implies "request fully processed" (RFC 9110)
- Misleads clients into thinking data is queryable
- Violates principle of least surprise

### Chosen Approach: HTTP 202 Accepted

**Benefits:**
- ✅ Honest about eventual consistency
- ✅ Aligns with CQRS patterns
- ✅ Fixes silent failure bug
- ✅ Provides proper headers for client guidance
- ✅ No artificial latency

---

## Verification with Specialized Agents

After implementing this task, invoke these specialized agents for verification:

1. **`@test-isolation-validator`** - Verify integration tests follow proper patterns
   - Ensures EventualConsistencyIT uses correct projector on/off patterns
   - Validates await() usage when projector enabled

2. **`@cqrs-compliance-checker`** - Verify CQRS + Event Sourcing compliance
   - Ensures controllers don't query repositories (command/query separation)
   - Validates async event publishing pattern
   - Checks HTTP 202 semantics align with eventual consistency

3. **`@documentation-sync-agent`** - Ensure docs match implementation
   - Verifies protocol specs match controller behavior
   - Checks OpenAPI spec reflects actual responses
   - Validates CQRS guide examples are accurate

4. **`@code-reviewer`** - Final code quality review
   - Reviews exception handling patterns
   - Validates HTTP status code usage
   - Checks for code quality issues

**Usage:**
```bash
# After implementation, invoke agents in conversation:
@test-isolation-validator
@cqrs-compliance-checker
@documentation-sync-agent
@code-reviewer
```

---

## Follow-Up Tasks (Optional)

After completing this task, consider:

- **Task 04c:** [Idempotency Token Support](./04c-idempotency-tokens.md) - Allow safe retries with duplicate detection

---

## References

- [RFC 9110 §15.3.3 - 202 Accepted](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3)
- [Audit Findings Report](./audit-findings.md#3-command-handlers---critical-issue)
- [CQRS + Event Sourcing Guide](../docs/architecture/cqrs-event-sourcing.md)
- [Spring Kafka Producer Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html)
