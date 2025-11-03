# Task: Implement Batch Operations Endpoint (Simplified)

**Status:** Not Started
**Priority:** Medium
**Category:** Version Control Protocol
**Estimated Time:** 2-3 hours

---

## Overview

Implement the missing Batch Operations endpoint that currently returns 501:
- `POST /version/batch` - Combine multiple write operations into a single commit

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.6](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

**Simplified Scope:**
- ✅ Write operations only (`update`, `applyPatch`)
- ✅ Single commit mode (combine all writes into one commit)
- ✅ Fail-fast validation (no atomic rollback - not possible in event sourcing)
- ✅ Reuse existing `CommitCreatedEvent` (no new events needed)

**Rationale for Simplification:**
- Queries (reads) don't fit CQRS event model
- Atomic rollback impossible with Kafka-based eventual consistency
- Single commit mode is the only genuinely useful feature (cleaner history for migrations)
- Separate commits for each operation can be achieved by calling endpoints multiple times

---

## Current State

**Controller:** [BatchController.java](../../src/main/java/org/chucc/vcserver/controller/BatchController.java)

**Not Implemented (returns 501):**
- ❌ `POST /version/batch` (line 53)

**Note:** This endpoint is **distinct** from `/version/batch-graphs` (Graph Store Protocol batch endpoint).

---

## Requirements

### POST /version/batch - Execute Batch of Write Operations

**Request:**
```http
POST /version/batch?dataset=myDataset HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>
SPARQL-VC-Message: Batch update for migration

{
  "operations": [
    {
      "type": "update",
      "sparql": "INSERT DATA { <http://example.org/s1> <http://example.org/p1> \"value1\" }",
      "branch": "main"
    },
    {
      "type": "update",
      "sparql": "DELETE DATA { <http://example.org/s2> <http://example.org/p2> \"old\" }",
      "branch": "main"
    },
    {
      "type": "applyPatch",
      "patch": "A <http://example.org/s3> <http://example.org/p3> \"value3\" .",
      "branch": "main"
    }
  ],
  "branch": "main"
}
```

**Parameters:**
- `dataset` (query param, required) - Dataset name
- `operations` (required) - Array of write operations
  - `type` ∈ {`update`, `applyPatch`}
  - `sparql` (for type=update) - SPARQL Update query
  - `patch` (for type=applyPatch) - RDF Patch text
  - `branch` (optional) - Override branch for this operation
- `branch` (optional) - Default branch for all operations (default: "main")
- `SPARQL-VC-Author` header - Author info (required)
- `SPARQL-VC-Message` header - Commit message (required)

**Validation Rules:**
- All operations must target the same branch (fail if mixed)
- All operations must be valid (syntax check) before executing any
- Fail-fast: First validation error stops the entire batch

**Response:** 202 Accepted (CQRS async pattern)
```json
{
  "status": "accepted",
  "commitId": "01933e4a-9d4e-7000-8000-000000000005",
  "branch": "main",
  "operationCount": 3,
  "message": "Batch update for migration"
}
```

**Error Response:** 400 Bad Request
```json
{
  "type": "about:blank",
  "title": "Batch Operation Failed",
  "status": 400,
  "code": "BATCH_VALIDATION_FAILED",
  "failedAt": 1,
  "detail": "SPARQL syntax error in operation 1: Expected INSERT or DELETE"
}
```

**Headers:**
- `Location: /version/commits/{commitId}` - Created commit
- `SPARQL-VC-Status: pending` - Eventual consistency

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `dto/BatchWriteRequest.java`
- `dto/WriteOperation.java`
- `dto/BatchWriteResponse.java`

**File:** `src/main/java/org/chucc/vcserver/dto/BatchWriteRequest.java`
```java
package org.chucc.vcserver.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Request DTO for batch write operations endpoint.
 *
 * <p>Combines multiple write operations (SPARQL updates or RDF patches) into a single commit.
 * All operations must target the same branch.
 *
 * @param operations List of write operations to execute
 * @param branch Default branch for all operations (can be overridden per operation)
 */
public record BatchWriteRequest(
    @NotNull @NotEmpty @Valid List<WriteOperation> operations,
    String branch  // Nullable, defaults to "main" if not specified
) {
  /**
   * Validates the batch request.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("Operations list cannot be empty");
    }

    // Validate individual operations
    for (int i = 0; i < operations.size(); i++) {
      try {
        operations.get(i).validate();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid operation at index " + i + ": " + e.getMessage(), e);
      }
    }

    // Ensure all operations target same branch
    String targetBranch = determineTargetBranch();
    for (int i = 0; i < operations.size(); i++) {
      WriteOperation op = operations.get(i);
      String opBranch = op.branch() != null ? op.branch() : targetBranch;
      if (!opBranch.equals(targetBranch)) {
        throw new IllegalArgumentException(
            "All operations must target the same branch. Operation " + i
            + " targets '" + opBranch + "' but expected '" + targetBranch + "'");
      }
    }
  }

  /**
   * Determines the target branch (from request or first operation).
   *
   * @return target branch name
   */
  public String determineTargetBranch() {
    if (branch != null) {
      return branch;
    }
    if (!operations.isEmpty() && operations.get(0).branch() != null) {
      return operations.get(0).branch();
    }
    return "main";  // Default
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/WriteOperation.java`
```java
package org.chucc.vcserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Single write operation in a batch request.
 *
 * @param type Operation type: "update" or "applyPatch"
 * @param sparql SPARQL Update query (required if type=update)
 * @param patch RDF Patch text (required if type=applyPatch)
 * @param branch Target branch (overrides request-level branch)
 */
public record WriteOperation(
    @NotNull @NotBlank String type,
    String sparql,
    String patch,
    String branch
) {
  /**
   * Validates the write operation.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Type is required");
    }

    if (!type.equals("update") && !type.equals("applyPatch")) {
      throw new IllegalArgumentException(
          "Invalid type: '" + type + "'. Must be 'update' or 'applyPatch'");
    }

    if ("update".equals(type)) {
      if (sparql == null || sparql.isBlank()) {
        throw new IllegalArgumentException("SPARQL query required for type 'update'");
      }
    }

    if ("applyPatch".equals(type)) {
      if (patch == null || patch.isBlank()) {
        throw new IllegalArgumentException("Patch content required for type 'applyPatch'");
      }
    }
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/BatchWriteResponse.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for batch write operations endpoint.
 *
 * @param status Status of the batch operation ("accepted")
 * @param commitId ID of the created commit
 * @param branch Target branch name
 * @param operationCount Number of operations combined into the commit
 * @param message Commit message
 */
public record BatchWriteResponse(
    String status,
    String commitId,
    String branch,
    @JsonProperty("operationCount") int operationCount,
    String message
) {
  /**
   * Creates a successful batch write response.
   *
   * @param commitId Created commit ID
   * @param branch Target branch
   * @param operationCount Number of operations
   * @param message Commit message
   * @return response DTO
   */
  public static BatchWriteResponse accepted(
      String commitId,
      String branch,
      int operationCount,
      String message) {
    return new BatchWriteResponse("accepted", commitId, branch, operationCount, message);
  }
}
```

### Step 2: Create Batch Service

**File:** `src/main/java/org/chucc/vcserver/service/BatchOperationService.java`

This service orchestrates the batch operation:
1. Validates all operations upfront
2. Converts SPARQL updates to RDF patches
3. Combines all patches into one
4. Delegates to existing `CreateCommitCommandHandler`

```java
package org.chucc.vcserver.service;

import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.dto.WriteOperation;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service for batch write operations.
 *
 * <p>Combines multiple write operations (SPARQL updates or RDF patches) into a single commit.
 */
@Service
public class BatchOperationService {

  private final DatasetService datasetService;
  private final SparqlUpdateService updateService;

  public BatchOperationService(DatasetService datasetService,
                                SparqlUpdateService updateService) {
    this.datasetService = datasetService;
    this.updateService = updateService;
  }

  /**
   * Converts all operations to RDF patches and combines them.
   *
   * @param dataset Dataset name
   * @param operations List of write operations
   * @param branch Target branch
   * @return Combined RDF patch
   * @throws IllegalArgumentException if any operation is invalid
   */
  public RDFPatch combineOperations(
      String dataset,
      List<WriteOperation> operations,
      String branch) {

    List<RDFPatch> patches = new ArrayList<>();

    for (int i = 0; i < operations.size(); i++) {
      WriteOperation op = operations.get(i);
      try {
        RDFPatch patch = convertToPatch(dataset, op, branch);
        patches.add(patch);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Failed to process operation " + i + ": " + e.getMessage(), e);
      }
    }

    return RdfPatchUtil.combine(patches);
  }

  private RDFPatch convertToPatch(String dataset, WriteOperation op, String branch) {
    return switch (op.type()) {
      case "update" -> {
        // Execute SPARQL update against branch HEAD and extract patch
        DatasetGraph before = datasetService.materializeBranch(dataset, branch);
        DatasetGraph after = before.copy();
        updateService.executeUpdate(after, op.sparql());
        yield RdfPatchUtil.diff(before, after);
      }
      case "applyPatch" -> {
        // Parse RDF patch directly
        yield RdfPatchUtil.parse(op.patch());
      }
      default -> throw new IllegalArgumentException("Unknown operation type: " + op.type());
    };
  }
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/BatchController.java`

Replace 501 stub with full implementation:

```java
@PostMapping("/batch")
public ResponseEntity<BatchWriteResponse> executeBatch(
    @RequestParam String dataset,
    @RequestHeader(value = "SPARQL-VC-Author", required = true) String author,
    @RequestHeader(value = "SPARQL-VC-Message", required = true) String message,
    @RequestBody @Valid BatchWriteRequest request) {

  try {
    // Validate request
    request.validate();

    String targetBranch = request.determineTargetBranch();

    // Combine operations into single patch
    RDFPatch combinedPatch = batchOperationService.combineOperations(
        dataset, request.operations(), targetBranch);

    // Create single commit using existing command handler
    CreateCommitCommand command = new CreateCommitCommand(
        UUID.randomUUID().toString(),
        dataset,
        targetBranch,
        combinedPatch.toString(),
        message,
        author,
        Instant.now()
    );

    CommitCreatedEvent event = (CommitCreatedEvent) createCommitCommandHandler.handle(command);

    // Build response
    BatchWriteResponse response = BatchWriteResponse.accepted(
        event.commitId(),
        targetBranch,
        request.operations().size(),
        message
    );

    return ResponseEntity
        .status(HttpStatus.ACCEPTED)
        .header("Location", "/version/commits/" + event.commitId())
        .header("SPARQL-VC-Status", "pending")
        .body(response);

  } catch (IllegalArgumentException e) {
    // Validation error
    throw new BadRequestException("Batch validation failed: " + e.getMessage());
  }
}
```

### Step 4: Write Tests

**Integration Tests:** `src/test/java/org/chucc/vcserver/integration/BatchOperationsIT.java`

Test cases:
1. ✅ Batch with single update operation
2. ✅ Batch with multiple updates
3. ✅ Batch with applyPatch operations
4. ✅ Batch with mixed update + applyPatch
5. ✅ Empty operations list (400 error)
6. ✅ Invalid operation type (400 error)
7. ✅ Missing SPARQL for update (400 error)
8. ✅ Missing patch for applyPatch (400 error)
9. ✅ Mixed branches (400 error)
10. ✅ Missing author header (400 error)

**Unit Tests:** `src/test/java/org/chucc/vcserver/service/BatchOperationServiceTest.java`

Test cases:
1. ✅ Combine multiple updates
2. ✅ Combine multiple patches
3. ✅ Combine mixed operations
4. ✅ Invalid SPARQL syntax error
5. ✅ Invalid patch syntax error

---

## Success Criteria

- ✅ Endpoint implemented (no 501 response)
- ✅ DTOs created with validation
- ✅ BatchOperationService implemented
- ✅ Single commit mode works (all writes combined)
- ✅ SPARQL updates converted to patches correctly
- ✅ Direct patch application works
- ✅ Mixed operations work
- ✅ Fail-fast validation prevents partial execution
- ✅ Integration tests pass (10 tests)
- ✅ Unit tests pass (5 tests)
- ✅ Zero quality violations
- ✅ Full build passes

---

## Design Decisions

### Why Write-Only?

**Queries (reads) don't fit the batch model:**
- Reads don't produce events (no state change)
- Can't combine query results with commit response
- Client can easily make multiple query requests

### Why Single Commit Only?

**Multiple commits can be achieved by calling endpoints separately:**
- Batch endpoint's value: cleaner history (1 commit instead of N)
- Multiple commits = N API calls (no performance benefit from batching)

### Why No Atomic Rollback?

**Event sourcing makes true rollback impossible:**
- Events published to Kafka immediately
- Can't unpublish events
- Eventual consistency model
- Fail-fast validation is best we can do

### CQRS Compliance

**Reuses existing infrastructure:**
- No new events (`CommitCreatedEvent` is sufficient)
- No new command handlers (uses `CreateCommitCommandHandler`)
- Controller orchestrates, existing services do work
- Clean separation of concerns

---

## Files to Create/Modify

### Create (5 files)
- `src/main/java/org/chucc/vcserver/dto/BatchWriteRequest.java`
- `src/main/java/org/chucc/vcserver/dto/WriteOperation.java`
- `src/main/java/org/chucc/vcserver/dto/BatchWriteResponse.java`
- `src/main/java/org/chucc/vcserver/service/BatchOperationService.java`
- `src/test/java/org/chucc/vcserver/integration/BatchOperationsIT.java`
- `src/test/java/org/chucc/vcserver/service/BatchOperationServiceTest.java`

### Modify (1 file)
- `src/main/java/org/chucc/vcserver/controller/BatchController.java` (replace 501 stub)

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.6](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [BatchController.java](../../src/main/java/org/chucc/vcserver/controller/BatchController.java)
- [CreateCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java)
- [RdfPatchUtil.java](../../src/main/java/org/chucc/vcserver/util/RdfPatchUtil.java)
