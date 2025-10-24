# Task: Implement Branch List, Create, and Get Info Endpoints

**Status:** Completed
**Priority:** High
**Category:** Version Control Protocol
**Estimated Time:** 6-8 hours
**Completed:** 2025-10-24

---

## Overview

Implement the three missing Branch API endpoints that currently return 501:
- `GET /version/branches` - List all branches
- `POST /version/branches` - Create a new branch
- `GET /version/branches/{name}` - Get branch information

This is a **full Git-like implementation** with branch metadata (creation time, update time, commit count) and protected branch management.

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

**Existing Infrastructure:**
- ✅ `BranchCreatedEvent` exists but missing `author`, `sourceRef`, `protected` fields
- ✅ `BranchDeletedEvent` exists with `author` field
- ✅ `Branch` domain entity exists but missing metadata (timestamps, commitCount, protected flag)
- ✅ `BranchRepository` ready for extensions
- ✅ `ReadModelProjector.handleBranchCreated()` exists (line 296)

---

## Architecture Decision: Why Not Reuse RefService?

**Question:** Why create separate branch endpoints when `RefService` already lists branches?

**Answer:**
- `/version/refs` provides **unified view** of branches + tags (minimal info)
- `/version/branches` provides **branch-specific operations** with full metadata
- Git has both: `git show-ref` (unified) and `git branch --list` (detailed)
- Branches need additional operations: create, protect, track commit counts

**Analogy:** RefService is like `ls`, BranchService is like `ls -lah`

---

## Requirements

### 1. GET /version/branches - List All Branches

**Request:**
```http
GET /version/branches?dataset=default HTTP/1.1
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
      "createdAt": "2025-10-24T10:00:00Z",
      "lastUpdated": "2025-10-24T12:34:56Z",
      "commitCount": 42
    },
    {
      "name": "feature-x",
      "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
      "protected": false,
      "createdAt": "2025-10-24T11:00:00Z",
      "lastUpdated": "2025-10-24T13:45:12Z",
      "commitCount": 5
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
POST /version/branches?dataset=default HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "name": "feature-new",
  "from": "main",
  "protected": false
}
```

**Parameters:**
- `name` (required) - Branch name (must match `^[A-Za-z0-9._\-]+$`, Unicode NFC normalized)
- `from` (required) - Source ref (branch name or commit ID)
- `protected` (optional) - Whether branch is protected (default: false)

**Response:** 202 Accepted
```json
{
  "name": "feature-new",
  "headCommit": "01933e4a-9d4e-7000-8000-000000000003",
  "createdFrom": "main",
  "protected": false
}
```

**Headers:**
- `Location: /version/branches/feature-new?dataset=default`
- `ETag: "01933e4a-9d4e-7000-8000-000000000003"`
- `SPARQL-VC-Status: pending`

**Error Responses:**
- `400 Bad Request` - Invalid branch name or missing fields
- `409 Conflict` - Branch already exists
- `404 Not Found` - Source ref not found

**CQRS Pattern:**
- Command: `CreateBranchCommand(dataset, name, sourceRef, protected, author)`
- Handler: `CreateBranchCommandHandler`
- Event: `BranchCreatedEvent(dataset, branchName, headCommit, sourceRef, protected, author, timestamp)`

**Note on Status Code:** Uses `202 Accepted` (not `201 Created`) to match CQRS eventual consistency pattern. Branch created in read model asynchronously after event projection.

---

### 3. GET /version/branches/{name} - Get Branch Info

**Request:**
```http
GET /version/branches/feature-x?dataset=default HTTP/1.1
Accept: application/json
```

**Response:** 200 OK
```json
{
  "name": "feature-x",
  "headCommit": "01933e4a-8c3d-7000-8000-000000000002",
  "protected": false,
  "createdAt": "2025-10-24T11:00:00Z",
  "lastUpdated": "2025-10-24T13:45:12Z",
  "commitCount": 5
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

### Step 0: Extend Branch Domain Model (PREREQUISITE)

**CRITICAL:** This step MUST be completed first as all other steps depend on it.

**File:** `src/main/java/org/chucc/vcserver/domain/Branch.java`

**Current state:**
```java
public final class Branch {
  private final String name;
  private CommitId commitId;
}
```

**New state:**
```java
public final class Branch {
  private final String name;
  private CommitId commitId;
  private final boolean isProtected;        // NEW: Protected flag
  private final Instant createdAt;          // NEW: Creation timestamp
  private Instant lastUpdated;              // NEW: Last update timestamp
  private int commitCount;                  // NEW: Total commits on this branch
}
```

**Changes:**

1. **Add new fields:**
   ```java
   private final boolean isProtected;
   private final Instant createdAt;
   private Instant lastUpdated;
   private int commitCount;
   ```

2. **Update constructor:**
   ```java
   /**
    * Creates a new Branch with full metadata.
    *
    * @param name the branch name (must be non-null, non-blank, in NFC form, and match pattern)
    * @param commitId the commit this branch points to (must be non-null)
    * @param isProtected whether this branch is protected from deletion/force-push
    * @param createdAt the creation timestamp (must be non-null)
    * @param lastUpdated the last update timestamp (must be non-null)
    * @param commitCount the number of commits on this branch (must be >= 1)
    * @throws IllegalArgumentException if validation fails
    */
   public Branch(String name, CommitId commitId, boolean isProtected,
                 Instant createdAt, Instant lastUpdated, int commitCount) {
     // ... existing validation for name and commitId ...

     Objects.requireNonNull(createdAt, "createdAt cannot be null");
     Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");

     if (commitCount < 1) {
       throw new IllegalArgumentException("commitCount must be at least 1");
     }

     this.name = name;
     this.commitId = commitId;
     this.isProtected = isProtected;
     this.createdAt = createdAt;
     this.lastUpdated = lastUpdated;
     this.commitCount = commitCount;
   }

   /**
    * Creates a new Branch with default timestamps (for tests/convenience).
    *
    * @param name the branch name
    * @param commitId the commit this branch points to
    */
   public Branch(String name, CommitId commitId) {
     this(name, commitId, false, Instant.now(), Instant.now(), 1);
   }
   ```

3. **Add getters:**
   ```java
   public boolean isProtected() { return isProtected; }
   public Instant getCreatedAt() { return createdAt; }
   public Instant getLastUpdated() { return lastUpdated; }
   public int getCommitCount() { return commitCount; }
   ```

4. **Update `updateCommit()` method:**
   ```java
   /**
    * Updates the branch to point to a new commit (fast-forward or force update).
    * Also updates lastUpdated timestamp and increments commit count.
    *
    * @param newCommitId the new commit ID
    * @throws IllegalArgumentException if newCommitId is null
    */
   public void updateCommit(CommitId newCommitId) {
     Objects.requireNonNull(newCommitId, "New commitId cannot be null");
     this.commitId = newCommitId;
     this.lastUpdated = Instant.now();
     this.commitCount++;
   }
   ```

5. **Update `equals()` and `hashCode()`:**
   ```java
   @Override
   public boolean equals(Object obj) {
     if (this == obj) return true;
     if (obj == null || getClass() != obj.getClass()) return false;
     Branch branch = (Branch) obj;
     return Objects.equals(name, branch.name)
         && Objects.equals(commitId, branch.commitId)
         && isProtected == branch.isProtected
         && Objects.equals(createdAt, branch.createdAt)
         && Objects.equals(lastUpdated, branch.lastUpdated)
         && commitCount == branch.commitCount;
   }

   @Override
   public int hashCode() {
     return Objects.hash(name, commitId, isProtected, createdAt, lastUpdated, commitCount);
   }
   ```

**Files that need updates after this change:**
1. `ReadModelProjector.handleBranchCreated()` - use new constructor
2. `DatasetService.initializeEmptyDataset()` - mark "main" as protected
3. All test files that create branches (use 2-arg convenience constructor)

---

### Step 1: Update BranchCreatedEvent

**File:** `src/main/java/org/chucc/vcserver/event/BranchCreatedEvent.java`

**Current state:** Has `eventId`, `dataset`, `branchName`, `commitId`, `timestamp`

**Add fields:**
```java
@JsonProperty("sourceRef") String sourceRef,
@JsonProperty("protected") boolean isProtected,
@JsonProperty("author") String author
```

**Full updated record:**
```java
/**
 * Event representing the creation of a new branch.
 */
public record BranchCreatedEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("dataset") String dataset,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("commitId") String commitId,
    @JsonProperty("sourceRef") String sourceRef,      // NEW
    @JsonProperty("protected") boolean isProtected,   // NEW
    @JsonProperty("author") String author,            // NEW
    @JsonProperty("timestamp") Instant timestamp)
    implements VersionControlEvent {

  // Update compact constructor validation
  public BranchCreatedEvent {
    eventId = (eventId == null) ? UuidCreator.getTimeOrderedEpoch().toString() : eventId;

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(sourceRef, "Source ref cannot be null");      // NEW
    Objects.requireNonNull(author, "Author cannot be null");             // NEW
    Objects.requireNonNull(timestamp, "Timestamp cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (sourceRef.isBlank()) {                                           // NEW
      throw new IllegalArgumentException("Source ref cannot be blank");
    }
    if (author.isBlank()) {                                              // NEW
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }

  /**
   * Convenience constructor that auto-generates eventId.
   */
  public BranchCreatedEvent(
      String dataset,
      String branchName,
      String commitId,
      String sourceRef,
      boolean isProtected,
      String author,
      Instant timestamp) {
    this(null, dataset, branchName, commitId, sourceRef, isProtected, author, timestamp);
  }

  @Override
  public AggregateIdentity getAggregateIdentity() {
    return AggregateIdentity.branch(dataset, branchName);
  }
}
```

**Note:** This is a **breaking change** to the event schema. Existing events in Kafka won't have these fields. For production, you'd need event schema versioning or migration. For this project (development phase), we accept the breaking change.

---

### Step 2: Create DTOs

**File:** `src/main/java/org/chucc/vcserver/dto/BranchInfo.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Branch information DTO with full metadata.
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
    @JsonProperty(defaultValue = "false") Boolean isProtected
) {
  /**
   * Validates the request fields.
   * Branch name validation delegates to Branch domain entity (NFC normalization + regex).
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Branch name is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }
    // Note: Branch name pattern validation happens in Branch constructor
    // Pattern: ^[A-Za-z0-9._\-]+$ + Unicode NFC normalization
  }

  /**
   * Gets the protected flag, defaulting to false if null.
   *
   * @return the protected flag
   */
  public boolean isProtected() {
    return isProtected != null && isProtected;
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/CreateBranchResponse.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for branch creation.
 */
public record CreateBranchResponse(
    String name,
    @JsonProperty("headCommit") String headCommit,
    @JsonProperty("createdFrom") String createdFrom,
    @JsonProperty("protected") boolean isProtected
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

### Step 3: Create Command & Update CommandHandler

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
    boolean isProtected,
    String author
) {}
```

**File:** `src/main/java/org/chucc/vcserver/command/CreateBranchCommandHandler.java`
```java
package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchAlreadyExistsException;
import org.chucc.vcserver.exception.RefNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles CreateBranchCommand by validating and producing a BranchCreatedEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class CreateBranchCommandHandler implements CommandHandler<CreateBranchCommand> {

  private static final Logger logger = LoggerFactory.getLogger(CreateBranchCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;

  /**
   * Constructs a CreateBranchCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param eventPublisher the event publisher
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public CreateBranchCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public VersionControlEvent handle(CreateBranchCommand command) {
    logger.info("Creating branch: {} from {} in dataset: {}",
        command.branchName(), command.sourceRef(), command.dataset());

    // Validate branch doesn't already exist
    if (branchRepository.exists(command.dataset(), command.branchName())) {
      throw new BranchAlreadyExistsException(command.branchName());
    }

    // Resolve source ref to commit ID
    String headCommitId = resolveSourceRef(command.dataset(), command.sourceRef());

    // Validate branch name (will be done by Branch constructor in projector,
    // but we can do early validation here)
    try {
      new Branch(command.branchName(),
          org.chucc.vcserver.domain.CommitId.of(headCommitId));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid branch name: " + e.getMessage(), e);
    }

    // Produce event
    VersionControlEvent event = new BranchCreatedEvent(
        command.dataset(),
        command.branchName(),
        headCommitId,
        command.sourceRef(),
        command.isProtected(),
        command.author(),
        Instant.now()
    );

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

    logger.info("Branch {} created from {} (commit: {}) in dataset {}",
        command.branchName(), command.sourceRef(), headCommitId, command.dataset());

    return event;
  }

  /**
   * Resolves a source ref (branch name or commit ID) to a commit ID.
   *
   * @param dataset the dataset name
   * @param sourceRef the source ref
   * @return the resolved commit ID
   * @throws RefNotFoundException if the ref cannot be resolved
   */
  private String resolveSourceRef(String dataset, String sourceRef) {
    // Try as branch first
    var branch = branchRepository.findByDatasetAndName(dataset, sourceRef);
    if (branch.isPresent()) {
      return branch.get().getCommitId().value();
    }

    // Try as commit ID
    if (commitRepository.exists(dataset, sourceRef)) {
      return sourceRef;
    }

    throw new RefNotFoundException("Source ref not found: " + sourceRef);
  }
}
```

---

### Step 4: Create Exception Classes (if not exist)

**File:** `src/main/java/org/chucc/vcserver/exception/BranchAlreadyExistsException.java`
```java
package org.chucc.vcserver.exception;

/**
 * Exception thrown when attempting to create a branch that already exists.
 */
public class BranchAlreadyExistsException extends RuntimeException {
  public BranchAlreadyExistsException(String branchName) {
    super("Branch already exists: " + branchName);
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/exception/RefNotFoundException.java`
```java
package org.chucc.vcserver.exception;

/**
 * Exception thrown when a ref (branch or commit) cannot be found.
 */
public class RefNotFoundException extends RuntimeException {
  public RefNotFoundException(String message) {
    super(message);
  }
}
```

---

### Step 5: Implement Service

**File:** `src/main/java/org/chucc/vcserver/service/BranchService.java`
```java
package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.dto.BranchInfo;
import org.chucc.vcserver.repository.BranchRepository;
import org.springframework.stereotype.Service;

/**
 * Service for branch operations.
 * Provides detailed branch information (unlike RefService which provides unified view).
 */
@Service
public class BranchService {

  private final BranchRepository branchRepository;

  /**
   * Constructs a BranchService.
   *
   * @param branchRepository the branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repository is Spring-managed bean and is intentionally shared")
  public BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  /**
   * Lists all branches in a dataset with full metadata.
   *
   * @param dataset the dataset name
   * @return list of branch information
   */
  public List<BranchInfo> listBranches(String dataset) {
    return branchRepository.findAllByDataset(dataset)
        .stream()
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getCommitId().value(),
            branch.isProtected(),
            branch.getCreatedAt(),
            branch.getLastUpdated(),
            branch.getCommitCount()
        ))
        .toList();
  }

  /**
   * Gets detailed information about a specific branch.
   *
   * @param dataset the dataset name
   * @param name the branch name
   * @return branch information if found
   */
  public Optional<BranchInfo> getBranchInfo(String dataset, String name) {
    return branchRepository.findByDatasetAndName(dataset, name)
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getCommitId().value(),
            branch.isProtected(),
            branch.getCreatedAt(),
            branch.getLastUpdated(),
            branch.getCommitCount()
        ));
  }
}
```

---

### Step 6: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

**Add dependencies:**
```java
private final CreateBranchCommandHandler createBranchCommandHandler;
private final BranchService branchService;

public BranchController(
    DeleteBranchCommandHandler deleteBranchCommandHandler,
    CreateBranchCommandHandler createBranchCommandHandler,  // NEW
    BranchService branchService) {                          // NEW
  this.deleteBranchCommandHandler = deleteBranchCommandHandler;
  this.createBranchCommandHandler = createBranchCommandHandler;
  this.branchService = branchService;
}
```

**Replace the three 501 stub methods:**

```java
/**
 * List all branches with full metadata.
 *
 * @param dataset the dataset name
 * @return list of branches
 */
@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = "List branches", description = "List all branches in the repository")
@ApiResponse(
    responseCode = "200",
    description = "Branch list",
    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
)
public ResponseEntity<BranchListResponse> listBranches(
    @Parameter(description = "Dataset name")
    @RequestParam(defaultValue = "default") String dataset
) {
  List<BranchInfo> branches = branchService.listBranches(dataset);
  return ResponseEntity.ok(new BranchListResponse(branches));
}

/**
 * Create a new branch.
 *
 * @param request the branch creation request
 * @param dataset the dataset name
 * @param author the author of the branch creation
 * @return the created branch information
 */
@PostMapping(
    consumes = MediaType.APPLICATION_JSON_VALUE,
    produces = MediaType.APPLICATION_JSON_VALUE
)
@Operation(
    summary = "Create branch",
    description = "Create a new branch from a commit or branch"
)
@ApiResponse(
    responseCode = "202",
    description = "Branch creation accepted (eventual consistency)",
    headers = {
        @Header(
            name = "Location",
            description = "URL of the created branch",
            schema = @Schema(type = "string")
        ),
        @Header(
            name = "ETag",
            description = "Head commit ID",
            schema = @Schema(type = "string")
        ),
        @Header(
            name = "SPARQL-VC-Status",
            description = "Status of the operation (pending)",
            schema = @Schema(type = "string")
        )
    },
    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
)
@ApiResponse(
    responseCode = "400",
    description = "Invalid request",
    content = @Content(mediaType = "application/problem+json")
)
@ApiResponse(
    responseCode = "404",
    description = "Source ref not found",
    content = @Content(mediaType = "application/problem+json")
)
@ApiResponse(
    responseCode = "409",
    description = "Branch already exists",
    content = @Content(mediaType = "application/problem+json")
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
        .body(new org.chucc.vcserver.dto.ProblemDetail(
            e.getMessage(), 400, "INVALID_REQUEST"));
  }

  // Create command
  CreateBranchCommand command = new CreateBranchCommand(
      dataset,
      request.name(),
      request.from(),
      request.isProtected(),
      author != null ? author : "anonymous"
  );

  // Handle command
  try {
    BranchCreatedEvent event = (BranchCreatedEvent) createBranchCommandHandler.handle(command);

    // Build response
    CreateBranchResponse response = new CreateBranchResponse(
        event.branchName(),
        event.commitId(),
        event.sourceRef(),
        event.isProtected()
    );

    // Build Location URI
    String location = org.springframework.web.servlet.support.ServletUriComponentsBuilder
        .fromCurrentRequest()
        .path("/{name}")
        .buildAndExpand(event.branchName())
        .toUriString();

    return ResponseEntity
        .status(HttpStatus.ACCEPTED)  // 202, not 201 (eventual consistency)
        .header(HttpHeaders.LOCATION, location)
        .eTag("\"" + event.commitId() + "\"")
        .header("SPARQL-VC-Status", "pending")
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
  } catch (org.chucc.vcserver.exception.BranchAlreadyExistsException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new org.chucc.vcserver.dto.ProblemDetail(
            e.getMessage(), 409, "BRANCH_ALREADY_EXISTS"));
  } catch (org.chucc.vcserver.exception.RefNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new org.chucc.vcserver.dto.ProblemDetail(
            e.getMessage(), 404, "REF_NOT_FOUND"));
  } catch (IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(new org.chucc.vcserver.dto.ProblemDetail(
            e.getMessage(), 400, "INVALID_BRANCH_NAME"));
  }
}

/**
 * Get branch information.
 *
 * @param name the branch name
 * @param dataset the dataset name
 * @return branch information
 */
@GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = "Get branch", description = "Get branch information")
@ApiResponse(
    responseCode = "200",
    description = "Branch info",
    headers = @Header(
        name = "ETag",
        description = "Head commit id (strong ETag)",
        schema = @Schema(type = "string")
    ),
    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
)
@ApiResponse(
    responseCode = "404",
    description = "Branch not found",
    content = @Content(mediaType = "application/problem+json")
)
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
          .body(new org.chucc.vcserver.dto.ProblemDetail(
              "Branch not found: " + name,
              404,
              "BRANCH_NOT_FOUND"
          )));
}
```

---

### Step 7: Update Projector Handler

**File:** `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

**Update `handleBranchCreated()` method (around line 296):**

```java
/**
 * Handles BranchCreatedEvent by creating a new branch with full metadata.
 *
 * @param event the branch created event
 */
void handleBranchCreated(BranchCreatedEvent event) {
  logger.debug("Processing BranchCreatedEvent: branchName={}, commitId={}, protected={}, dataset={}",
      event.branchName(), event.commitId(), event.isProtected(), event.dataset());

  Branch branch = new Branch(
      event.branchName(),
      CommitId.of(event.commitId()),
      event.isProtected(),
      event.timestamp(),       // createdAt
      event.timestamp(),       // lastUpdated (same as creation initially)
      1                        // Initial commit count
  );

  branchRepository.save(event.dataset(), branch);

  logger.debug("Created branch: {} pointing to {} (protected: {}) in dataset: {}",
      event.branchName(), event.commitId(), event.isProtected(), event.dataset());
}
```

---

### Step 8: Update DatasetService

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**Update `initializeEmptyDataset()` method (around line 197):**

```java
// Create main branch pointing to initial commit (PROTECTED by default)
Branch mainBranch = new Branch(
    "main",
    initialCommit.id(),
    true,                    // NEW: main is protected
    Instant.now(),          // NEW: createdAt
    Instant.now(),          // NEW: lastUpdated
    1                       // NEW: initial commit count
);
branchRepository.save(datasetName, mainBranch);
```

---

### Step 9: Update DeleteBranchCommandHandler

**File:** `src/main/java/org/chucc/vcserver/command/DeleteBranchCommandHandler.java`

**Update protection check (line 50-53):**

```java
// Check if branch is protected
if (branch.isProtected()) {
  throw new ProtectedBranchException("Cannot delete protected branch: " + command.branchName());
}
```

**Remove hardcoded "main" check:**
```java
// DELETE THIS:
// if (MAIN_BRANCH.equals(command.branchName())) {
//   throw new ProtectedBranchException("Cannot delete main branch");
// }
```

---

### Step 10: Write Tests

See full test implementations in the original detailed task file sections. Tests include:
- **BranchApiIT** - API layer tests (projector disabled)
- **CreateBranchCommandHandlerTest** - Unit tests for command handler
- **BranchProjectorIT** - Projector tests (projector enabled)

---

### Step 11: Update All Test Files (Batch Update)

**Impact:** ~50 test files that use `new Branch(...)`

**Strategy:** Use the 2-argument convenience constructor `new Branch(name, commitId)` for tests.

**Find and replace pattern:**
```java
// Old (if any tests manually specify metadata):
new Branch("name", commitId, false, Instant.now(), Instant.now(), 1)

// New (convenience constructor):
new Branch("name", commitId)
```

---

## Success Criteria

- ✅ Branch domain model extended with metadata (protected, timestamps, commitCount)
- ✅ BranchCreatedEvent updated with author, sourceRef, protected fields
- ✅ All three endpoints implemented (no 501 responses)
- ✅ DTOs created with proper validation
- ✅ Command/Handler/Event/Service implemented following CQRS pattern
- ✅ Projector handler updated to use new metadata
- ✅ DatasetService creates "main" as protected branch
- ✅ DeleteBranchCommandHandler uses `branch.isProtected()` check
- ✅ Integration tests pass (API layer + projector tests)
- ✅ Unit tests pass (command handler)
- ✅ All existing tests updated to use new Branch constructor
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD)
- ✅ Zero compiler warnings
- ✅ Full build passes (`mvn -q clean install`)

---

## Testing Strategy

**API Layer Tests (projector DISABLED):**
- Test HTTP status codes (200, 202, 400, 404, 409)
- Test response headers (Location, ETag, SPARQL-VC-Status)
- Test error responses (validation, conflicts, not found)
- Do NOT query repositories (projector disabled)

**Command Handler Tests (unit):**
- Test command validation
- Test event production
- Test error cases (branch exists, ref not found, invalid name)
- Test protected flag handling

**Projector Tests (projector ENABLED):**
- Test `BranchCreatedEvent` → branch entity created with metadata
- Test protected flag projection
- Use `await()` pattern for async verification

---

## Implementation Order

1. **Step 0** (CRITICAL FIRST): Extend Branch domain model
2. **Step 8**: Update DatasetService to create protected main branch
3. **Step 9**: Update DeleteBranchCommandHandler to use isProtected()
4. **Step 1**: Update BranchCreatedEvent with new fields
5. **Step 7**: Update ReadModelProjector handler
6. **Step 11**: Fix all test files (use convenience constructor)
7. **Run Phase 1**: `mvn -q clean compile checkstyle:check spotbugs:check`
8. **Step 4**: Create exception classes
9. **Step 2**: Create DTOs
10. **Step 3**: Create command and handler
11. **Step 5**: Implement service
12. **Step 6**: Update controller
13. **Step 10**: Write tests
14. **Run Phase 2a**: `mvn -q test -Dtest=*CommandHandlerTest,BranchApiIT`
15. **Run Phase 2b**: `mvn -q clean install`

---

## Breaking Changes

**Event Schema Change:**
- `BranchCreatedEvent` adds 3 new required fields
- Existing events in Kafka won't have these fields → deserialization may fail
- **Mitigation:** For production, use event versioning or schema evolution
- **Current approach:** Development phase, acceptable breaking change

**Domain Model Change:**
- `Branch` constructor signature changed
- All code creating branches must be updated
- **Mitigation:** Convenience constructor `Branch(name, commitId)` for backward compatibility

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../../.claude/CLAUDE.md)
- [BranchController.java](../../src/main/java/org/chucc/vcserver/controller/BranchController.java)
- [DeleteBranchCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/DeleteBranchCommandHandler.java)
- [Branch.java](../../src/main/java/org/chucc/vcserver/domain/Branch.java)
- [BranchCreatedEvent.java](../../src/main/java/org/chucc/vcserver/event/BranchCreatedEvent.java)
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java)

---

## Notes

**Why full Git-like metadata?**
- Supports future features: branch age analysis, stale branch detection, commit count tracking
- Enables Git-like workflows: `git branch -v` (verbose), `git branch --sort=committerdate`
- Protected branches critical for production: prevent accidental main deletion/force-push

**Performance considerations:**
- `commitCount` updated on every branch commit → minimal overhead (in-memory)
- Alternative: Compute on-demand from CommitRepository → expensive for large histories
- Current approach: Incremental tracking via `Branch.updateCommit()`

**Future enhancements:**
- Support `?protected=true` flag in `POST /version/branches` to override default
- Add `PATCH /version/branches/{name}` to toggle protection after creation
- Add branch rename operation (Git: `git branch -m old new`)
- Add `last_commit_author`, `last_commit_message` for richer listings
