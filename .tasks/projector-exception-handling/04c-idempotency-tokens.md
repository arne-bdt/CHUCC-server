# Task 04c: Idempotency Token Support (Optional Enhancement)

## Status
**OPTIONAL** - Enhances Task 04 but not required

## Dependencies
- Task 04 must be completed first (HTTP 202 Accepted implementation)

## Objective

Allow clients to safely retry write operations without creating duplicate commits by providing an idempotency key.

## Context

After Task 04, clients receive HTTP 202 Accepted but may not know if the operation succeeded:

**Problem scenario:**
1. Client sends `PUT /data?graph=...`
2. Server returns HTTP 202 Accepted
3. Network fails before client receives response
4. Client retries (because it didn't see the response)
5. **Result:** Two identical commits created 😞

**With idempotency tokens:**
1. Client sends `PUT /data?graph=...` with `Idempotency-Key: abc123`
2. Server returns HTTP 202 Accepted
3. Network fails
4. Client retries with **same** `Idempotency-Key: abc123`
5. **Result:** Server detects duplicate, returns original commit 😊

---

## Design

### Idempotency Key Header

Follow [RFC Draft: Idempotency-Key HTTP Header](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header):

```http
PUT /data?graph=http://example.org/graph1
Content-Type: text/turtle
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

@prefix ex: <http://example.org/> . ex:s ex:p ex:o .
```

**Key requirements:**
- Client-provided UUID or random string
- Unique per operation (not reused across different operations)
- Server stores mapping: `idempotency_key → commit_id`
- Duplicate requests return same commit (idempotent)

---

## Implementation Plan

### Step 1: Create Idempotency Table

**Schema:**
```sql
CREATE TABLE idempotency_keys (
  idempotency_key VARCHAR(255) PRIMARY KEY,
  dataset VARCHAR(255) NOT NULL,
  commit_id VARCHAR(36) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  expires_at TIMESTAMP NOT NULL,

  INDEX idx_expires_at (expires_at)  -- For cleanup
);
```

**Retention:** 24 hours (configurable)

### Step 2: Create IdempotencyKey Entity

**File:** Create [src/main/java/org/chucc/vcserver/domain/IdempotencyKey.java](../src/main/java/org/chucc/vcserver/domain/IdempotencyKey.java)

```java
package org.chucc.vcserver.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Idempotency key for safe operation retries.
 * Maps client-provided idempotency keys to commit IDs.
 */
@Table("idempotency_keys")
public class IdempotencyKey {

  @Id
  private String idempotencyKey;
  private String dataset;
  private String commitId;
  private Instant createdAt;
  private Instant expiresAt;

  // Constructors, getters, setters...

  /**
   * Creates a new idempotency key record.
   *
   * @param idempotencyKey the client-provided key
   * @param dataset the dataset name
   * @param commitId the commit ID
   * @param ttlHours time-to-live in hours (default: 24)
   * @return new IdempotencyKey instance
   */
  public static IdempotencyKey create(
      String idempotencyKey,
      String dataset,
      String commitId,
      int ttlHours) {
    Instant now = Instant.now();
    IdempotencyKey key = new IdempotencyKey();
    key.setIdempotencyKey(idempotencyKey);
    key.setDataset(dataset);
    key.setCommitId(commitId);
    key.setCreatedAt(now);
    key.setExpiresAt(now.plusSeconds(ttlHours * 3600));
    return key;
  }
}
```

### Step 3: Create IdempotencyKeyRepository

**File:** Create [src/main/java/org/chucc/vcserver/repository/IdempotencyKeyRepository.java](../src/main/java/org/chucc/vcserver/repository/IdempotencyKeyRepository.java)

```java
package org.chucc.vcserver.repository;

import java.time.Instant;
import java.util.Optional;
import org.chucc.vcserver.domain.IdempotencyKey;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for idempotency keys.
 */
@Repository
public interface IdempotencyKeyRepository extends CrudRepository<IdempotencyKey, String> {

  /**
   * Finds an idempotency key by key and dataset.
   *
   * @param idempotencyKey the idempotency key
   * @param dataset the dataset name
   * @return the idempotency key record if found
   */
  @Query("SELECT * FROM idempotency_keys WHERE idempotency_key = :idempotencyKey "
      + "AND dataset = :dataset AND expires_at > :now")
  Optional<IdempotencyKey> findByKeyAndDataset(
      String idempotencyKey,
      String dataset,
      Instant now
  );

  /**
   * Deletes expired idempotency keys.
   *
   * @param now current timestamp
   * @return number of deleted records
   */
  @Modifying
  @Query("DELETE FROM idempotency_keys WHERE expires_at < :now")
  int deleteExpired(Instant now);
}
```

### Step 4: Add Idempotency Check to Command Handlers

**File:** [src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java](../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java)

Add new method:

```java
/**
 * Checks for idempotency key and returns existing commit if found.
 * Returns empty if idempotency key is null or not found.
 *
 * @param idempotencyKey the client-provided idempotency key (nullable)
 * @param dataset the dataset name
 * @param idempotencyKeyRepository the repository
 * @return existing commit ID if duplicate request, empty otherwise
 */
public static Optional<String> checkIdempotencyKey(
    String idempotencyKey,
    String dataset,
    IdempotencyKeyRepository idempotencyKeyRepository) {

  if (idempotencyKey == null || idempotencyKey.isBlank()) {
    return Optional.empty();
  }

  return idempotencyKeyRepository
      .findByKeyAndDataset(idempotencyKey, dataset, Instant.now())
      .map(IdempotencyKey::getCommitId);
}

/**
 * Stores idempotency key mapping after successful commit.
 *
 * @param idempotencyKey the client-provided idempotency key (nullable)
 * @param dataset the dataset name
 * @param commitId the commit ID
 * @param idempotencyKeyRepository the repository
 * @param ttlHours time-to-live in hours
 */
public static void storeIdempotencyKey(
    String idempotencyKey,
    String dataset,
    String commitId,
    IdempotencyKeyRepository idempotencyKeyRepository,
    int ttlHours) {

  if (idempotencyKey == null || idempotencyKey.isBlank()) {
    return;  // No idempotency key provided
  }

  IdempotencyKey key = IdempotencyKey.create(
      idempotencyKey,
      dataset,
      commitId,
      ttlHours
  );

  idempotencyKeyRepository.save(key);
}
```

### Step 5: Update Controllers to Accept Idempotency-Key Header

**File:** [src/main/java/org/chucc/vcserver/controller/GraphStoreController.java](../src/main/java/org/chucc/vcserver/controller/GraphStoreController.java)

```java
@PutMapping(...)
public ResponseEntity<?> putGraph(
    // ... existing parameters ...
    @Parameter(description = "Idempotency key for safe retries")
    @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
    @RequestBody String body) {

  // ... existing validation ...

  // Check for duplicate request
  Optional<String> existingCommitId = GraphCommandUtil.checkIdempotencyKey(
      idempotencyKey,
      dataset,
      idempotencyKeyRepository
  );

  if (existingCommitId.isPresent()) {
    // Duplicate request - return original commit
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.LOCATION, "/version/commits/" + existingCommitId.get());
    headers.setETag("\"" + existingCommitId.get() + "\"");
    headers.set("SPARQL-Version-Control", "true");
    headers.set("SPARQL-VC-Status", "completed");  // Already processed
    headers.set("X-Idempotency-Replayed", "true");  // Indicates replay

    return ResponseEntity
        .accepted()  // Still 202 (was accepted before)
        .headers(headers)
        .build();
  }

  // ... existing command handling ...

  // Store idempotency key after successful commit
  if (event instanceof org.chucc.vcserver.event.CommitCreatedEvent commitEvent) {
    GraphCommandUtil.storeIdempotencyKey(
        idempotencyKey,
        dataset,
        commitEvent.commitId(),
        idempotencyKeyRepository,
        24  // 24-hour TTL
    );

    // ... return response ...
  }
}
```

### Step 6: Add Cleanup Job

**File:** Create [src/main/java/org/chucc/vcserver/service/IdempotencyKeyCleanupService.java](../src/main/java/org/chucc/vcserver/service/IdempotencyKeyCleanupService.java)

```java
package org.chucc.vcserver.service;

import java.time.Instant;
import org.chucc.vcserver.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cleans up expired idempotency keys.
 */
@Service
public class IdempotencyKeyCleanupService {

  private static final Logger logger = LoggerFactory.getLogger(
      IdempotencyKeyCleanupService.class
  );

  private final IdempotencyKeyRepository repository;

  public IdempotencyKeyCleanupService(IdempotencyKeyRepository repository) {
    this.repository = repository;
  }

  /**
   * Deletes expired idempotency keys.
   * Runs every hour.
   */
  @Scheduled(cron = "0 0 * * * *")  // Every hour
  public void cleanupExpiredKeys() {
    int deleted = repository.deleteExpired(Instant.now());
    if (deleted > 0) {
      logger.info("Cleaned up {} expired idempotency keys", deleted);
    }
  }
}
```

### Step 7: Add Configuration

**File:** [src/main/resources/application.yml](../src/main/resources/application.yml)

```yaml
vc:
  idempotency:
    enabled: true
    ttl-hours: 24  # Idempotency key retention (24 hours)
```

### Step 8: Add Integration Tests

**File:** Create [src/test/java/org/chucc/vcserver/integration/IdempotencyIT.java](../src/test/java/org/chucc/vcserver/integration/IdempotencyIT.java)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class IdempotencyIT extends IntegrationTestFixture {

  @Test
  void putGraph_withIdempotencyKey_preventsDuplicates() {
    // Arrange
    String idempotencyKey = UUID.randomUUID().toString();
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    HttpHeaders headers = createHeaders("text/turtle");
    headers.set("Idempotency-Key", idempotencyKey);

    // Act: First request
    ResponseEntity<Void> response1 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers),
        Void.class
    );

    // Act: Second request (duplicate)
    ResponseEntity<Void> response2 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers),
        Void.class
    );

    // Assert: Both return 202 Accepted
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Assert: Same commit ID returned
    String commitId1 = extractEtag(response1.getHeaders().getETag());
    String commitId2 = extractEtag(response2.getHeaders().getETag());
    assertThat(commitId1).isEqualTo(commitId2);

    // Assert: Second response has replay header
    assertThat(response2.getHeaders().getFirst("X-Idempotency-Replayed"))
        .isEqualTo("true");
  }

  @Test
  void putGraph_withDifferentIdempotencyKeys_createsMultipleCommits() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    HttpHeaders headers1 = createHeaders("text/turtle");
    headers1.set("Idempotency-Key", UUID.randomUUID().toString());

    HttpHeaders headers2 = createHeaders("text/turtle");
    headers2.set("Idempotency-Key", UUID.randomUUID().toString());

    // Act: Two requests with different keys
    ResponseEntity<Void> response1 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers1),
        Void.class
    );

    ResponseEntity<Void> response2 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers2),
        Void.class
    );

    // Assert: Different commit IDs
    String commitId1 = extractEtag(response1.getHeaders().getETag());
    String commitId2 = extractEtag(response2.getHeaders().getETag());
    assertThat(commitId1).isNotEqualTo(commitId2);

    // Assert: No replay header
    assertThat(response2.getHeaders().getFirst("X-Idempotency-Replayed"))
        .isNull();
  }

  @Test
  void putGraph_withoutIdempotencyKey_allowsDuplicates() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";

    // Act: Two requests without idempotency key
    ResponseEntity<Void> response1 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        Void.class
    );

    ResponseEntity<Void> response2 = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, createHeaders("text/turtle")),
        Void.class
    );

    // Assert: Different commit IDs (duplicates allowed)
    String commitId1 = extractEtag(response1.getHeaders().getETag());
    String commitId2 = extractEtag(response2.getHeaders().getETag());
    assertThat(commitId1).isNotEqualTo(commitId2);
  }
}
```

### Step 9: Update Documentation

**File:** [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)

Add section:

```markdown
## Idempotency Token Support

### Safe Retries with Idempotency-Key Header

Clients can provide an `Idempotency-Key` header to prevent duplicate commits:

```http
PUT /data?graph=http://example.org/graph1
Content-Type: text/turtle
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

@prefix ex: <http://example.org/> . ex:s ex:p ex:o .
```

**How it works:**
1. Client generates unique key (UUID recommended)
2. Server checks if key was used before
3. If duplicate: Returns original commit (no new commit created)
4. If new: Creates commit and stores key mapping

**Response headers:**
- `X-Idempotency-Replayed: true` - Indicates duplicate request
- `SPARQL-VC-Status: completed` - Original commit already projected

**Retention:** Idempotency keys expire after 24 hours (configurable)

**Example client implementation:**
```javascript
async function putGraphWithRetry(graphUri, turtle) {
  const idempotencyKey = crypto.randomUUID();
  const maxRetries = 3;

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const response = await fetch(`/data?graph=${graphUri}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'text/turtle',
          'Idempotency-Key': idempotencyKey  // Same key for all retries
        },
        body: turtle
      });

      if (response.status === 202) {
        return {
          commitId: response.headers.get('ETag'),
          wasReplayed: response.headers.get('X-Idempotency-Replayed') === 'true'
        };
      }
    } catch (error) {
      if (attempt === maxRetries - 1) throw error;
      await sleep(1000 * Math.pow(2, attempt));  // Exponential backoff
    }
  }
}
```
```

---

## Acceptance Criteria

- [ ] Idempotency keys table created (migration script)
- [ ] `IdempotencyKey` entity and repository implemented
- [ ] All write endpoints accept `Idempotency-Key` header
- [ ] Duplicate requests return original commit (no new commit created)
- [ ] Response includes `X-Idempotency-Replayed: true` header for duplicates
- [ ] Cleanup job deletes expired keys hourly
- [ ] Configuration option for TTL (`vc.idempotency.ttl-hours`)
- [ ] Integration tests verify idempotency behavior
- [ ] OpenAPI documentation updated
- [ ] CQRS guide updated with client examples
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] All tests pass

---

## Files to Modify

**New Files:**
- [src/main/java/org/chucc/vcserver/domain/IdempotencyKey.java](../src/main/java/org/chucc/vcserver/domain/IdempotencyKey.java)
- [src/main/java/org/chucc/vcserver/repository/IdempotencyKeyRepository.java](../src/main/java/org/chucc/vcserver/repository/IdempotencyKeyRepository.java)
- [src/main/java/org/chucc/vcserver/service/IdempotencyKeyCleanupService.java](../src/main/java/org/chucc/vcserver/service/IdempotencyKeyCleanupService.java)
- [src/test/java/org/chucc/vcserver/integration/IdempotencyIT.java](../src/test/java/org/chucc/vcserver/integration/IdempotencyIT.java)
- [src/main/resources/db/migration/V012__create_idempotency_keys_table.sql](../src/main/resources/db/migration/V012__create_idempotency_keys_table.sql)

**Modified Files:**
- [src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java](../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java)
- [src/main/java/org/chucc/vcserver/controller/GraphStoreController.java](../src/main/java/org/chucc/vcserver/controller/GraphStoreController.java)
- [src/main/java/org/chucc/vcserver/controller/SparqlController.java](../src/main/java/org/chucc/vcserver/controller/SparqlController.java)
- All other write controllers (Branch, Merge, Tag, Batch, AdvancedOps)
- [src/main/resources/application.yml](../src/main/resources/application.yml)
- [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md)

---

## Estimated Time

4-5 hours
- 1 hour: Create entity, repository, and database migration
- 2 hours: Update all controllers and command handlers
- 1 hour: Add tests
- 1 hour: Add cleanup job and documentation

---

## Priority

**OPTIONAL** (Low Priority)

**Benefits:**
- Clients can safely retry without creating duplicates
- Follows RFC draft standard (future-proof)
- Useful for unreliable networks

**Can be deferred if:**
- Network reliability is high
- Clients use optimistic locking (If-Match) instead
- Duplicate commits are acceptable (can be squashed later)

---

## Alternative: Client-Side Idempotency

Instead of server-side tracking, clients can use:

**Option 1: Content-based idempotency**
- Hash the RDF content
- Check if identical commit already exists
- Requires client to query history before writing

**Option 2: Optimistic locking (If-Match)**
- Client reads current ETag
- Sends write with `If-Match: "{etag}"`
- Server rejects if concurrent write occurred
- Client must handle 412 Precondition Failed

**Recommendation:** Use If-Match first, add idempotency tokens later if needed.

---

## Verification with Specialized Agents

After implementing this task, invoke these specialized agents:

1. **`@test-isolation-validator`** - Verify test patterns
   - Ensures IdempotencyIT follows proper patterns
   - Validates no cross-test contamination

2. **`@cqrs-compliance-checker`** - Verify command-side patterns
   - Ensures idempotency check happens before event creation
   - Validates no repository queries in command handlers
   - Checks transactional boundaries

3. **`@event-schema-evolution-checker`** - Verify no event changes
   - Ensures idempotency doesn't modify event schemas
   - Validates backward compatibility

4. **`@documentation-sync-agent`** - Ensure docs updated
   - Verifies protocol specs document Idempotency-Key header
   - Checks OpenAPI spec includes header
   - Validates CQRS guide has retry examples

5. **`@code-reviewer`** - Code quality review
   - Reviews repository implementation
   - Validates cleanup job
   - Checks configuration

---

## References

- [RFC Draft: Idempotency-Key HTTP Header](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header)
- [Stripe API: Idempotent Requests](https://stripe.com/docs/api/idempotent_requests)
- [Task 04: HTTP 202 Accepted](./04-fix-command-handler-exceptions.md)
