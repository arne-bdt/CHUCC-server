# Task 04b: Operation Status Endpoint (Optional Enhancement)

## Status
**OPTIONAL** - Enhances Task 04 but not required

## Dependencies
- Task 04 must be completed first (HTTP 202 Accepted implementation)

## Objective

Provide an endpoint for clients to poll and check if a write operation has been projected to the read model.

## Context

After Task 04, clients receive HTTP 202 Accepted with headers:
```http
HTTP/1.1 202 Accepted
Location: /version/commits/01JFQM4X5K...
ETag: "01JFQM4X5K..."
SPARQL-VC-Status: pending
```

**Current limitation:**
- Clients don't know *when* the read model will be updated
- No way to poll for completion (besides repeatedly querying the data)

**This task provides:**
- Status endpoint: `GET /operations/{operationId}`
- Returns `{"status": "completed"}` when projection is done
- Allows clients to poll intelligently

---

## Design

### Option A: Commit-Based Status (Simpler, Recommended)

No new tables needed. Status determined by checking if commit exists in repository.

**Endpoint:**
```http
GET /version/commits/{commitId}/status

Response (projection pending):
{
  "commitId": "01JFQM4X5K...",
  "status": "pending",
  "message": "Commit event published, projection in progress"
}

Response (projection complete):
{
  "commitId": "01JFQM4X5K...",
  "status": "completed",
  "message": "Commit projected to read model"
}
```

**Implementation:**
```java
@GetMapping("/version/commits/{commitId}/status")
public ResponseEntity<OperationStatus> getCommitStatus(
    @PathVariable String commitId,
    @RequestParam(defaultValue = "default") String dataset) {

  // Check if commit exists in read model
  boolean isProjected = commitRepository.existsByDatasetAndId(
      dataset,
      new CommitId(commitId)
  );

  if (isProjected) {
    return ResponseEntity.ok(new OperationStatus(
        commitId,
        "completed",
        "Commit projected to read model"
    ));
  } else {
    return ResponseEntity.ok(new OperationStatus(
        commitId,
        "pending",
        "Commit event published, projection in progress"
    ));
  }
}
```

**Benefits:**
- ✅ No new tables or infrastructure
- ✅ Simple implementation
- ✅ Uses existing commit repository
- ⚠️ Can't distinguish "pending" from "failed" (both look like "not found")

---

### Option B: Operation Tracking Table (More Complex)

Track operations in a dedicated table.

**Schema:**
```sql
CREATE TABLE operation_status (
  operation_id VARCHAR(36) PRIMARY KEY,
  dataset VARCHAR(255) NOT NULL,
  commit_id VARCHAR(36),
  status VARCHAR(20) NOT NULL,  -- pending, completed, failed
  created_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP,
  error_message TEXT
);
```

**Implementation:**
```java
// 1. Command handler creates operation record
@Transactional
public VersionControlEvent handle(PutGraphCommand command) {
  // ... create event ...

  // Create operation record
  Operation operation = new Operation(
      event.commitId(),
      command.dataset(),
      OperationStatus.PENDING
  );
  operationRepository.save(operation);

  // Publish event
  eventPublisher.publish(event);

  return event;
}

// 2. Projector updates operation status
@KafkaListener
void onCommitCreated(CommitCreatedEvent event) {
  // Project to read model
  commitRepository.save(...);

  // Update operation status
  operationRepository.updateStatus(
      event.commitId(),
      OperationStatus.COMPLETED
  );
}

// 3. Endpoint returns operation status
@GetMapping("/operations/{operationId}")
public ResponseEntity<OperationStatus> getOperationStatus(
    @PathVariable String operationId) {

  Operation operation = operationRepository.findById(operationId)
      .orElseThrow(() -> new OperationNotFoundException(operationId));

  return ResponseEntity.ok(new OperationStatus(
      operation.getId(),
      operation.getStatus(),
      operation.getErrorMessage()
  ));
}
```

**Benefits:**
- ✅ Can distinguish "pending" from "failed"
- ✅ Can store error messages
- ✅ Can track operation lifecycle
- ⚠️ Requires new table and additional writes
- ⚠️ More complexity

---

## Recommendation

**Start with Option A (Commit-Based Status):**
- Simpler implementation
- No new infrastructure
- Meets 90% of use cases

**Upgrade to Option B later if needed:**
- Only if clients need to distinguish "pending" from "failed"
- Only if detailed error tracking is required

---

## Implementation Plan (Option A)

### Step 1: Add Status Endpoint to CommitController

**File:** [src/main/java/org/chucc/vcserver/controller/CommitController.java](../src/main/java/org/chucc/vcserver/controller/CommitController.java)

```java
@GetMapping("/version/commits/{commitId}/status")
@Operation(
    summary = "Get commit projection status",
    description = "Check if commit has been projected to read model. "
        + "Returns 'pending' if event published but not yet projected, "
        + "'completed' if projection finished."
)
@ApiResponse(
    responseCode = "200",
    description = "Status returned",
    content = @Content(
        mediaType = "application/json",
        schema = @Schema(implementation = CommitStatus.class)
    )
)
public ResponseEntity<CommitStatus> getCommitStatus(
    @PathVariable String commitId,
    @RequestParam(defaultValue = "default") String dataset) {

  // Check if commit exists in read model
  boolean isProjected = commitRepository.existsByDatasetAndId(
      dataset,
      new CommitId(commitId)
  );

  CommitStatus status;
  if (isProjected) {
    status = new CommitStatus(
        commitId,
        "completed",
        "Commit projected to read model"
    );
  } else {
    status = new CommitStatus(
        commitId,
        "pending",
        "Commit event published, projection in progress"
    );
  }

  return ResponseEntity.ok(status);
}
```

### Step 2: Create CommitStatus DTO

**File:** Create [src/main/java/org/chucc/vcserver/dto/CommitStatus.java](../src/main/java/org/chucc/vcserver/dto/CommitStatus.java)

```java
package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Commit projection status response.
 */
@Schema(description = "Commit projection status")
public record CommitStatus(
    @Schema(description = "Commit ID", example = "01JFQM4X5K...")
    String commitId,

    @Schema(description = "Projection status", allowableValues = {"pending", "completed"})
    String status,

    @Schema(description = "Human-readable message")
    String message
) {
}
```

### Step 3: Add Integration Test

**File:** Update [src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java](../src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java)

```java
@Test
void commitStatus_beforeProjection_shouldReturnPending() {
  // Arrange
  String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

  // Act: Create commit
  ResponseEntity<Void> putResponse = restTemplate.exchange(
      "/data?graph=http://example.org/graph1",
      HttpMethod.PUT,
      new HttpEntity<>(turtle, createHeaders("text/turtle")),
      Void.class
  );

  String commitId = extractEtag(putResponse.getHeaders().getETag());

  // Act: Check status (projector disabled, so should be pending)
  ResponseEntity<CommitStatus> statusResponse = restTemplate.exchange(
      "/version/commits/" + commitId + "/status?dataset=default",
      HttpMethod.GET,
      HttpEntity.EMPTY,
      CommitStatus.class
  );

  // Assert: Status is pending
  assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(statusResponse.getBody().status()).isEqualTo("pending");
}

@Test
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
void commitStatus_afterProjection_shouldReturnCompleted() throws Exception {
  // Arrange
  String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

  // Act: Create commit
  ResponseEntity<Void> putResponse = restTemplate.exchange(
      "/data?graph=http://example.org/graph1",
      HttpMethod.PUT,
      new HttpEntity<>(turtle, createHeaders("text/turtle")),
      Void.class
  );

  String commitId = extractEtag(putResponse.getHeaders().getETag());

  // Assert: Eventually status becomes completed
  await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> {
        ResponseEntity<CommitStatus> statusResponse = restTemplate.exchange(
            "/version/commits/" + commitId + "/status?dataset=default",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            CommitStatus.class
        );

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody().status()).isEqualTo("completed");
      });
}
```

### Step 4: Update Documentation

**File:** [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)

Add to the "HTTP Status Codes and Eventual Consistency" section:

```markdown
### Polling for Projection Completion

Clients can poll the commit status endpoint to check when projection completes:

```http
GET /version/commits/01JFQM4X5K.../status?dataset=default

Response (pending):
{
  "commitId": "01JFQM4X5K...",
  "status": "pending",
  "message": "Commit event published, projection in progress"
}

Response (completed):
{
  "commitId": "01JFQM4X5K...",
  "status": "completed",
  "message": "Commit projected to read model"
}
```

**Recommended polling strategy:**
```javascript
async function waitForProjection(commitId, dataset = 'default') {
  const maxAttempts = 10;
  const initialDelay = 50;  // 50ms

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const response = await fetch(
      `/version/commits/${commitId}/status?dataset=${dataset}`
    );
    const status = await response.json();

    if (status.status === 'completed') {
      return true;
    }

    // Exponential backoff: 50ms, 100ms, 200ms, 400ms, ...
    await sleep(initialDelay * Math.pow(2, attempt));
  }

  throw new Error('Projection timeout');
}
```

**Performance:**
- P50 projection latency: ~50ms
- P99 projection latency: ~200ms
- P99.9 projection latency: ~500ms
```

---

## Acceptance Criteria

- [ ] `GET /version/commits/{commitId}/status` endpoint implemented
- [ ] Returns `{"status": "pending"}` when commit not in read model
- [ ] Returns `{"status": "completed"}` when commit exists in read model
- [ ] Integration test verifies pending status (projector disabled)
- [ ] Integration test verifies completed status (projector enabled)
- [ ] OpenAPI documentation added
- [ ] CQRS guide updated with polling strategy
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] All tests pass

---

## Files to Modify

**New Files:**
- [src/main/java/org/chucc/vcserver/dto/CommitStatus.java](../src/main/java/org/chucc/vcserver/dto/CommitStatus.java)

**Modified Files:**
- [src/main/java/org/chucc/vcserver/controller/CommitController.java](../src/main/java/org/chucc/vcserver/controller/CommitController.java)
- [src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java](../src/test/java/org/chucc/vcserver/integration/EventualConsistencyIT.java)
- [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)

---

## Estimated Time

2-3 hours
- 1 hour: Implement endpoint and DTO
- 1 hour: Add tests
- 30 min: Update documentation

---

## Priority

**OPTIONAL** (Medium Priority)

**Benefits:**
- Clients can poll intelligently (better than blind retries)
- Useful for debugging projection lag
- Simple implementation (no new infrastructure)

**Can be deferred if:**
- Clients are okay using commit selector for immediate reads
- Projection latency is consistently low (< 100ms)
- Client libraries handle eventual consistency well

---

## Verification with Specialized Agents

After implementing this task, invoke these specialized agents:

1. **`@test-isolation-validator`** - Verify test patterns
   - Ensures tests properly enable/disable projector
   - Validates await() usage in async tests

2. **`@cqrs-compliance-checker`** - Verify read-side patterns
   - Ensures status endpoint only queries repositories (read-side)
   - Validates no command/query mixing

3. **`@documentation-sync-agent`** - Ensure docs updated
   - Verifies CQRS guide includes polling examples
   - Checks OpenAPI spec documents status endpoint

4. **`@code-reviewer`** - Code quality review
   - Reviews endpoint implementation
   - Validates error handling

---

## References

- [Task 04: HTTP 202 Accepted](./04-fix-command-handler-exceptions.md)
- [RFC 9110 §15.3.3 - 202 Accepted](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.3.3)
