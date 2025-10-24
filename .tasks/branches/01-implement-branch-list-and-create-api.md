# Task: Implement Branch List, Create, and Get Info Endpoints

**Status:** Not Started
**Priority:** High
**Category:** Version Control Protocol
**Estimated Time:** 3-4 hours

---

## Overview

Implement the three missing Branch API endpoints that currently return 501:
- `GET /version/branches` - List all branches
- `POST /version/branches` - Create a new branch
- `GET /version/branches/{name}` - Get branch information

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md) (implied in §3.2)

---

## Current State

**Controller:** [BranchController.java](../../src/main/java/org/chucc/vcserver/controller/BranchController.java)

**Implemented:**
- ✅ `DELETE /version/branches/{name}` - Delete branch (with protection for main)

**Not Implemented (returns 501):**
- ❌ `GET /version/branches` (line 62)
- ❌ `POST /version/branches` (line 94)
- ❌ `GET /version/branches/{name}` (line 128)

---

## Requirements

### 1. GET /version/branches - List All Branches

**Request:**
```http
GET /version/branches HTTP/1.1
Accept: application/json
```

**Response:** 200 OK
```json
{
  "branches": [
    {
      "name": "main",
      "headCommit": "01933e4a-7b2c-7000-8000-000000000001",
      "protected": true,
      "lastUpdated": "2025-10-24T12:34:56Z"
    },
    {
      "name": "feature-x",
      "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
      "protected": false,
      "lastUpdated": "2025-10-24T13:45:12Z"
    }
  ]
}
```

**Service Method:**
- `BranchService.listBranches(String dataset)`
- Returns `List<BranchInfo>`

---

### 2. POST /version/branches - Create Branch

**Request:**
```http
POST /version/branches HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "name": "feature-new",
  "from": "main",
  "type": "branch"
}
```

**Parameters:**
- `name` (required) - Branch name (alphanumeric, hyphens, underscores, slashes)
- `from` (required) - Source ref (branch name or commit ID)
- `type` (optional) - "branch" or "commit" (default: "branch")

**Response:** 201 Created
```json
{
  "name": "feature-new",
  "headCommit": "01933e4a-9d4e-7000-8000-000000000003",
  "createdFrom": "main"
}
```

**Headers:**
- `Location: /version/branches/feature-new`
- `ETag: "01933e4a-9d4e-7000-8000-000000000003"`
- `SPARQL-VC-Status: pending`

**Error Responses:**
- `400 Bad Request` - Invalid branch name or missing fields
- `409 Conflict` - Branch already exists
- `404 Not Found` - Source ref not found

**CQRS Pattern:**
- Command: `CreateBranchCommand(dataset, name, from, author)`
- Handler: `CreateBranchCommandHandler`
- Event: `BranchCreatedEvent(dataset, branchName, headCommit, sourceRef, author, timestamp)`

---

### 3. GET /version/branches/{name} - Get Branch Info

**Request:**
```http
GET /version/branches/feature-x HTTP/1.1
Accept: application/json
```

**Response:** 200 OK
```json
{
  "name": "feature-x",
  "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
  "protected": false,
  "createdAt": "2025-10-24T10:00:00Z",
  "lastUpdated": "2025-10-24T13:45:12Z",
  "commitCount": 42
}
```

**Headers:**
- `ETag: "01933e4a-8c3d-7000-8000-000000000002"` (strong, current head commit)

**Error Responses:**
- `404 Not Found` - Branch not found

**Service Method:**
- `BranchService.getBranchInfo(String dataset, String name)`
- Returns `Optional<BranchInfo>`

---

## Implementation Steps

### Step 1: Create DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/BranchInfo.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Branch information DTO.
 */
public record BranchInfo(
    String name,
    @JsonProperty("headCommit") String headCommitId,
    boolean isProtected,
    Instant createdAt,
    Instant lastUpdated,
    int commitCount
) {}
```

**File:** `src/main/java/org/chucc/vcserver/dto/CreateBranchRequest.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a branch.
 */
public record CreateBranchRequest(
    String name,
    String from,
    @JsonProperty(defaultValue = "branch") String type
) {
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Branch name is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }
    if (!name.matches("[a-zA-Z0-9/_-]+")) {
      throw new IllegalArgumentException("Invalid branch name format");
    }
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/CreateBranchResponse.java`
```java
package org.chucc.vcserver.dto;

/**
 * Response DTO for branch creation.
 */
public record CreateBranchResponse(
    String name,
    String headCommit,
    String createdFrom
) {}
```

**File:** `src/main/java/org/chucc/vcserver/dto/BranchListResponse.java`
```java
package org.chucc.vcserver.dto;

import java.util.List;

/**
 * Response DTO for listing branches.
 */
public record BranchListResponse(
    List<BranchInfo> branches
) {}
```

---

### Step 2: Create Command & Event

**File:** `src/main/java/org/chucc/vcserver/command/CreateBranchCommand.java`
```java
package org.chucc.vcserver.command;

/**
 * Command to create a new branch.
 */
public record CreateBranchCommand(
    String dataset,
    String branchName,
    String sourceRef,
    String author
) implements Command {}
```

**File:** `src/main/java/org/chucc/vcserver/event/BranchCreatedEvent.java`
```java
package org.chucc.vcserver.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;

/**
 * Event indicating a branch was created.
 */
@JsonTypeName("branch-created")
public record BranchCreatedEvent(
    String eventId,
    String dataset,
    String branchName,
    String headCommitId,
    String sourceRef,
    String author,
    Instant timestamp
) implements Event {}
```

---

### Step 3: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/CreateBranchCommandHandler.java`
```java
package org.chucc.vcserver.command;

import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.Event;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.util.UuidGenerator;
import org.springframework.stereotype.Component;
import java.time.Instant;

/**
 * Handles CreateBranchCommand.
 */
@Component
public class CreateBranchCommandHandler implements CommandHandler {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;
  private final UuidGenerator uuidGenerator;

  public CreateBranchCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher,
      UuidGenerator uuidGenerator) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
    this.uuidGenerator = uuidGenerator;
  }

  @Override
  public Event handle(Command command) {
    CreateBranchCommand cmd = (CreateBranchCommand) command;

    // Validate branch doesn't already exist
    if (branchRepository.exists(cmd.dataset(), cmd.branchName())) {
      throw new IllegalStateException("Branch already exists: " + cmd.branchName());
    }

    // Resolve source ref to commit ID
    String headCommitId = resolveSourceRef(cmd.dataset(), cmd.sourceRef());

    // Create event
    BranchCreatedEvent event = new BranchCreatedEvent(
        uuidGenerator.generateEventId(),
        cmd.dataset(),
        cmd.branchName(),
        headCommitId,
        cmd.sourceRef(),
        cmd.author(),
        Instant.now()
    );

    // Publish event asynchronously
    eventPublisher.publish(event);

    return event;
  }

  private String resolveSourceRef(String dataset, String sourceRef) {
    // Try as branch first
    var branch = branchRepository.findByName(dataset, sourceRef);
    if (branch.isPresent()) {
      return branch.get().getHeadCommitId();
    }

    // Try as commit ID
    if (commitRepository.exists(dataset, sourceRef)) {
      return sourceRef;
    }

    throw new IllegalArgumentException("Source ref not found: " + sourceRef);
  }
}
```

---

### Step 4: Implement Service

**File:** `src/main/java/org/chucc/vcserver/service/BranchService.java`
```java
package org.chucc.vcserver.service;

import org.chucc.vcserver.dto.BranchInfo;
import org.chucc.vcserver.repository.BranchRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service for branch operations.
 */
@Service
public class BranchService {

  private final BranchRepository branchRepository;

  public BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  public List<BranchInfo> listBranches(String dataset) {
    return branchRepository.findAll(dataset)
        .stream()
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getHeadCommitId(),
            "main".equals(branch.getName()),
            branch.getCreatedAt(),
            branch.getUpdatedAt(),
            branch.getCommitCount()
        ))
        .toList();
  }

  public Optional<BranchInfo> getBranchInfo(String dataset, String name) {
    return branchRepository.findByName(dataset, name)
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getHeadCommitId(),
            "main".equals(branch.getName()),
            branch.getCreatedAt(),
            branch.getUpdatedAt(),
            branch.getCommitCount()
        ));
  }
}
```

---

### Step 5: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

Replace the three 501 stub methods with full implementations:

```java
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<BranchListResponse> listBranches(
    @Parameter(description = "Dataset name")
    @RequestParam(defaultValue = "default") String dataset
) {
  List<BranchInfo> branches = branchService.listBranches(dataset);
  return ResponseEntity.ok(new BranchListResponse(branches));
}

@PostMapping(
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
public ResponseEntity<?> createBranch(
    @RequestBody CreateBranchRequest request,
    @Parameter(description = "Dataset name")
    @RequestParam(defaultValue = "default") String dataset,
    @Parameter(description = "Author of the branch creation")
    @RequestHeader(name = "SPARQL-VC-Author", required = false) String author
) {
  // Validate request
  try {
    request.validate();
  } catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 400, "INVALID_REQUEST"));
  }

  // Create command
  CreateBranchCommand command = new CreateBranchCommand(
      dataset,
      request.name(),
      request.from(),
      author != null ? author : "anonymous"
  );

  // Handle command
  try {
    BranchCreatedEvent event = (BranchCreatedEvent) createBranchCommandHandler.handle(command);

    // Build response
    CreateBranchResponse response = new CreateBranchResponse(
        event.branchName(),
        event.headCommitId(),
        event.sourceRef()
    );

    // Build Location URI
    String location = ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{name}")
        .buildAndExpand(event.branchName())
        .toUriString();

    return ResponseEntity
        .accepted()
        .header("Location", location)
        .eTag("\"" + event.headCommitId() + "\"")
        .header("SPARQL-VC-Status", "pending")
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  } catch (IllegalStateException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 409, "BRANCH_EXISTS"));
  } catch (IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new ProblemDetail(e.getMessage(), 404, "NOT_FOUND"));
  }
}

@GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<?> getBranch(
    @Parameter(description = "Branch name", required = true)
    @PathVariable String name,
    @Parameter(description = "Dataset name")
    @RequestParam(defaultValue = "default") String dataset
) {
  return branchService.getBranchInfo(dataset, name)
      .<ResponseEntity<?>>map(info -> ResponseEntity.ok()
          .eTag("\"" + info.headCommitId() + "\"")
          .body(info))
      .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(
              "Branch not found: " + name,
              404,
              "BRANCH_NOT_FOUND"
          )));
}
```

---

### Step 6: Add Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add handler for `BranchCreatedEvent`:

```java
@EventHandler
public void on(BranchCreatedEvent event) {
  Branch branch = new Branch(
      event.dataset(),
      event.branchName(),
      event.headCommitId(),
      event.timestamp(),
      event.timestamp(),
      1  // Initial commit count
  );
  branchRepository.save(branch);
}
```

---

### Step 7: Write Tests

**Integration Test:** `src/test/java/org/chucc/vcserver/integration/BranchControllerIT.java`

```java
@Test
void listBranches_shouldReturnAllBranches() {
  // Arrange: Create test branches via API
  // ...

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/branches?dataset=default",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody()).contains("main");
}

@Test
void createBranch_shouldCreateNewBranch() {
  // Arrange
  String requestBody = """
      {
        "name": "feature-test",
        "from": "main"
      }
      """;

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/branches?dataset=default",
      HttpMethod.POST,
      new HttpEntity<>(requestBody, headers),
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  assertThat(response.getHeaders().getLocation()).isNotNull();
  assertThat(response.getHeaders().getETag()).isNotNull();
  // Note: Repository check requires projector enabled + await()
}

@Test
void getBranchInfo_shouldReturnBranchDetails() {
  // Arrange: Create branch first
  // ...

  // Act
  ResponseEntity<String> response = restTemplate.exchange(
      "/version/branches/main?dataset=default",
      HttpMethod.GET,
      null,
      String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().getETag()).isNotNull();
  assertThat(response.getBody()).contains("main");
}
```

**Unit Test:** `src/test/java/org/chucc/vcserver/command/CreateBranchCommandHandlerTest.java`

Test command handler logic, event creation, validation errors.

---

## Success Criteria

- ✅ All three endpoints implemented (no 501 responses)
- ✅ DTOs created with validation
- ✅ Command/Event/Handler implemented following CQRS pattern
- ✅ Service layer implemented
- ✅ Projector handler added
- ✅ Integration tests pass (API layer)
- ✅ Unit tests pass (command handler)
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ Zero compiler warnings
- ✅ Full build passes (`mvn -q clean install`)

---

## Testing Strategy

**API Layer Tests (projector DISABLED):**
- Test HTTP status codes
- Test response headers (Location, ETag, SPARQL-VC-Status)
- Test error responses (400, 404, 409)
- Do NOT query repositories

**Projector Tests (projector ENABLED):**
- Test `BranchCreatedEvent` → branch entity created
- Use `await()` pattern for async verification

---

## Files to Create/Modify

**New Files:**
- `dto/BranchInfo.java`
- `dto/CreateBranchRequest.java`
- `dto/CreateBranchResponse.java`
- `dto/BranchListResponse.java`
- `command/CreateBranchCommand.java`
- `event/BranchCreatedEvent.java`
- `command/CreateBranchCommandHandler.java`
- `service/BranchService.java`
- `test/.../integration/BranchControllerIT.java`
- `test/.../command/CreateBranchCommandHandlerTest.java`

**Modified Files:**
- `controller/BranchController.java` (replace 501 stubs)
- `projection/ReadModelProjector.java` (add BranchCreatedEvent handler)

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../../.claude/CLAUDE.md)
- [BranchController.java](../../src/main/java/org/chucc/vcserver/controller/BranchController.java)
- [DeleteBranchCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/DeleteBranchCommandHandler.java) (reference implementation)
