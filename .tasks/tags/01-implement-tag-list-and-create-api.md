# Task: Implement Tag List and Create Endpoints

**Status:** Not Started
**Priority:** High
**Category:** Version Control Protocol
**Estimated Time:** 3-4 hours

---

## Overview

Implement the two missing Tag API endpoints that currently return 501:
- `GET /version/tags` - List all tags
- `POST /version/tags` - Create a new immutable tag

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.5](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [TagController.java](../../src/main/java/org/chucc/vcserver/controller/TagController.java)

**Implemented:**
- ✅ `GET /version/tags/{name}` - Get tag details (line 110)
- ✅ `DELETE /version/tags/{name}` - Delete tag (line 147)

**Not Implemented (returns 501):**
- ❌ `GET /version/tags` (line 62)
- ❌ `POST /version/tags` (line 97)

---

## Requirements

### 1. GET /version/tags - List All Tags

**Request:**
```http
GET /{dataset}/version/tags HTTP/1.1
Accept: application/json
```

**Response:** 200 OK
```json
{
  "tags": [
    {
      "name": "v1.0.0",
      "target": "01933e4a-7b2c-7000-8000-000000000001",
      "message": "Release version 1.0.0",
      "author": "Alice <alice@example.org>",
      "createdAt": "2025-10-24T12:34:56Z"
    },
    {
      "name": "v1.1.0",
      "target": "01933e4a-8c3d-7000-8000-000000000002",
      "message": null,
      "author": "Bob <bob@example.org>",
      "createdAt": "2025-10-24T14:22:10Z"
    }
  ]
}
```

**Service Method:**
- `TagService.listTags(String dataset)`
- Returns `List<TagInfo>`

---

### 2. POST /version/tags - Create Immutable Tag

**Request:**
```http
POST /{dataset}/version/tags HTTP/1.1
Content-Type: application/json

{
  "name": "v2.0.0",
  "target": "01933e4a-9d4e-7000-8000-000000000003",
  "message": "Major release with breaking changes",
  "author": "Charlie <charlie@example.org>"
}
```

**Parameters:**
- `name` (required) - Tag name (alphanumeric, dots, hyphens allowed)
- `target` (required) - Target commit ID (UUIDv7)
- `message` (optional) - Annotation message
- `author` (optional) - Author (falls back to header or "anonymous")

**Response:** 201 Created
```json
{
  "name": "v2.0.0",
  "target": "01933e4a-9d4e-7000-8000-000000000003",
  "message": "Major release with breaking changes",
  "author": "Charlie <charlie@example.org>",
  "createdAt": "2025-10-24T15:00:00Z"
}
```

**Headers:**
- `Location: /{dataset}/version/tags/v2.0.0`
- `SPARQL-VC-Status: pending`

**Error Responses:**
- `400 Bad Request` - Invalid tag name or missing fields
- `404 Not Found` - Target commit not found
- `409 Conflict` - Tag already exists (tags are immutable)

**CQRS Pattern:**
- Command: `CreateTagCommand(dataset, name, target, message, author)`
- Handler: `CreateTagCommandHandler`
- Event: `TagCreatedEvent(dataset, tagName, targetCommit, message, author, timestamp)`

**Tag Naming Rules:**
- Pattern: `^[a-zA-Z0-9._-]+$`
- Examples: `v1.0.0`, `release-2024`, `stable`, `beta.1`
- Invalid: spaces, special chars (`@`, `#`, `/`, etc.)

---

## Implementation Steps

### Step 1: Create DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/TagInfo.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Tag information DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TagInfo(
    String name,
    String target,
    String message,
    String author,
    Instant createdAt
) {}
```

**File:** `src/main/java/org/chucc/vcserver/dto/CreateTagRequest.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a tag.
 */
public record CreateTagRequest(
    String name,
    String target,
    @JsonProperty(required = false) String message,
    @JsonProperty(required = false) String author
) {
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Tag name is required");
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("Target commit ID is required");
    }
    if (!name.matches("[a-zA-Z0-9._-]+")) {
      throw new IllegalArgumentException(
          "Invalid tag name format (allowed: alphanumeric, dots, hyphens, underscores)");
    }
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/CreateTagResponse.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Response DTO for tag creation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateTagResponse(
    String name,
    String target,
    String message,
    String author,
    Instant createdAt
) {}
```

**File:** `src/main/java/org/chucc/vcserver/dto/TagListResponse.java`
```java
package org.chucc.vcserver.dto;

import java.util.List;

/**
 * Response DTO for listing tags.
 */
public record TagListResponse(
    List<TagInfo> tags
) {}
```

---

### Step 2: Create Command & Event

**File:** `src/main/java/org/chucc/vcserver/command/CreateTagCommand.java`
```java
package org.chucc.vcserver.command;

/**
 * Command to create a new immutable tag.
 */
public record CreateTagCommand(
    String dataset,
    String tagName,
    String targetCommit,
    String message,
    String author
) implements Command {}
```

**File:** `src/main/java/org/chucc/vcserver/event/TagCreatedEvent.java`
```java
package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;

/**
 * Event indicating a tag was created.
 */
@JsonTypeName("tag-created")
public record TagCreatedEvent(
    String eventId,
    String dataset,
    String tagName,
    String targetCommit,
    String message,
    String author,
    Instant timestamp
) implements Event {}
```

---

### Step 3: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/CreateTagCommandHandler.java`
```java
package org.chucc.vcserver.command;

import org.chucc.vcserver.event.Event;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.util.UuidGenerator;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * Handles CreateTagCommand.
 */
@Component
public class CreateTagCommandHandler implements CommandHandler {

  private final TagRepository tagRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;
  private final UuidGenerator uuidGenerator;

  public CreateTagCommandHandler(
      TagRepository tagRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher,
      UuidGenerator uuidGenerator) {
    this.tagRepository = tagRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
    this.uuidGenerator = uuidGenerator;
  }

  @Override
  public Event handle(Command command) {
    CreateTagCommand cmd = (CreateTagCommand) command;

    // Validate tag doesn't already exist (tags are immutable)
    if (tagRepository.exists(cmd.dataset(), cmd.tagName())) {
      throw new IllegalStateException("Tag already exists: " + cmd.tagName());
    }

    // Validate target commit exists
    if (!commitRepository.exists(cmd.dataset(), cmd.targetCommit())) {
      throw new IllegalArgumentException("Target commit not found: " + cmd.targetCommit());
    }

    // Create event
    TagCreatedEvent event = new TagCreatedEvent(
        uuidGenerator.generateEventId(),
        cmd.dataset(),
        cmd.tagName(),
        cmd.targetCommit(),
        cmd.message(),
        cmd.author(),
        Instant.now()
    );

    // Publish event asynchronously
    eventPublisher.publish(event);

    return event;
  }
}
```

---

### Step 4: Update Service

**File:** `src/main/java/org/chucc/vcserver/service/TagService.java`

Add `listTags` method:

```java
public List<TagInfo> listTags(String dataset) {
  return tagRepository.findAll(dataset)
      .stream()
      .map(tag -> new TagInfo(
          tag.getName(),
          tag.getTargetCommit(),
          tag.getMessage(),
          tag.getAuthor(),
          tag.getCreatedAt()
      ))
      .toList();
}
```

---

### Step 5: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/TagController.java`

Replace the two 501 stub methods:

```java
private final TagService tagService;
private final CreateTagCommandHandler createTagCommandHandler;

// Inject handlers in constructor

@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<TagListResponse> listTags(
    @PathVariable String dataset
) {
  List<TagInfo> tags = tagService.listTags(dataset);
  return ResponseEntity.ok(new TagListResponse(tags));
}

@PostMapping(
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<?> createTag(
    @PathVariable String dataset,
    @RequestBody CreateTagRequest request
) {
  // Validate request
  try {
    request.validate();
  } catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 400, "INVALID_REQUEST"));
  }

  // Determine author (request body > anonymous)
  String author = request.author() != null ? request.author() : "anonymous";

  // Create command
  CreateTagCommand command = new CreateTagCommand(
      dataset,
      request.name(),
      request.target(),
      request.message(),
      author
  );

  // Handle command
  try {
    TagCreatedEvent event = (TagCreatedEvent) createTagCommandHandler.handle(command);

    // Build response
    CreateTagResponse response = new CreateTagResponse(
        event.tagName(),
        event.targetCommit(),
        event.message(),
        event.author(),
        event.timestamp()
    );

    // Build Location URI
    String location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{name}")
        .buildAndExpand(event.tagName())
        .toUriString();

    return ResponseEntity
        .accepted()
        .header("Location", location)
        .header("SPARQL-VC-Status", "pending")
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  } catch (IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 409, "TAG_EXISTS"));
  } catch (IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 404, "COMMIT_NOT_FOUND"));
  }
}
```

---

### Step 6: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add handler for `TagCreatedEvent`:

```java
@EventHandler
public void on(TagCreatedEvent event) {
  Tag tag = new Tag(
      event.dataset(),
      event.tagName(),
      event.targetCommit(),
      event.message(),
      event.author(),
      event.timestamp()
  );
  tagRepository.save(tag);
}
```

---

### Step 7: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/TagControllerIT.java`

```java
@Test
void listTags_shouldReturnAllTags() {
  // Arrange: Create test tags via API
  // ...

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/default/version/tags",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody()).contains("v1.0.0");
}

@Test
void createTag_shouldCreateImmutableTag() {
  // Arrange: First create a commit
  String commitId = createTestCommit();

  String requestBody = """
      {
        "name": "v1.0.0",
        "target": "%s",
        "message": "Release 1.0",
        "author": "Alice"
      }
      """.formatted(commitId);

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/default/version/tags",
      HttpMethod.POST,
      new HttpEntity<>(requestBody, headers),
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  assertThat(response.getHeaders().getLocation()).isNotNull();
  assertThat(response.getHeaders().get("SPARQL-VC-Status")).contains("pending");
}

@Test
void createTag_withDuplicateName_shouldReturn409() {
  // Arrange: Create tag first
  // ...

  // Act: Try to create same tag again
  // ...

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
}

@Test
void createTag_withInvalidTarget_shouldReturn404() {
  String requestBody = """
      {
        "name": "invalid-tag",
        "target": "00000000-0000-0000-0000-000000000000"
      }
      """;

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/default/version/tags",
      HttpMethod.POST,
      new HttpEntity<>(requestBody, headers),
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/command/CreateTagCommandHandlerTest.java`

Test command handler logic, event creation, validation errors.

---

## Success Criteria

- ✅ Both endpoints implemented (no 501 responses)
- ✅ DTOs created with validation (tag name pattern)
- ✅ Command/Event/Handler implemented following CQRS pattern
- ✅ Service layer updated
- ✅ Projector handler added
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (command handler)
- ✅ Tag immutability enforced (409 on duplicate)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ Zero compiler warnings
- ✅ Full build passes (`mvn -q clean install`)

---

## Testing Strategy

**API Layer Tests (projector DISABLED):**
- Test HTTP status codes (200, 201, 400, 404, 409)
- Test response headers (Location, SPARQL-VC-Status)
- Test tag name validation
- Do NOT query repositories

**Projector Tests (projector ENABLED):**
- Test `TagCreatedEvent` → tag entity created
- Use `await()` pattern for async verification

---

## Files to Create/Modify

**New Files:**
- `dto/TagInfo.java`
- `dto/CreateTagRequest.java`
- `dto/CreateTagResponse.java`
- `dto/TagListResponse.java`
- `command/CreateTagCommand.java`
- `event/TagCreatedEvent.java`
- `command/CreateTagCommandHandler.java`
- `test/.../integration/TagControllerIT.java`
- `test/.../command/CreateTagCommandHandlerTest.java`

**Modified Files:**
- `controller/TagController.java` (replace 501 stubs)
- `service/TagService.java` (add listTags method)
- `projection/ReadModelProjector.java` (add TagCreatedEvent handler)

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.5](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../../.claude/CLAUDE.md)
- [TagController.java](../../src/main/java/org/chucc/vcserver/controller/TagController.java)
- [TagService.java](../../src/main/java/org/chucc/vcserver/service/TagService.java)
