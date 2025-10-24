# Task: Implement Batch Operations Endpoint

**Status:** Not Started
**Priority:** Medium
**Category:** Version Control Protocol
**Estimated Time:** 4-5 hours

---

## Overview

Implement the missing Batch Operations endpoint that currently returns 501:
- `POST /version/batch` - Execute a batch of SPARQL operations atomically

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.6](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [BatchController.java](../../src/main/java/org/chucc/vcserver/controller/BatchController.java)

**Not Implemented (returns 501):**
- ❌ `POST /version/batch` (line 53)

**Note:** This endpoint is **distinct** from `/version/batch-graphs` (Graph Store Protocol batch endpoint).

---

## Requirements

### POST /version/batch - Execute Batch of Operations

**Request:**
```http
POST /version/batch HTTP/1.1
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>
SPARQL-VC-Message: Batch update for migration

{
  "operations": [
    {
      "type": "query",
      "sparql": "SELECT * WHERE { ?s ?p ?o } LIMIT 10",
      "branch": "main"
    },
    {
      "type": "update",
      "sparql": "INSERT DATA { <http://example.org/s> <http://example.org/p> \"value\" }",
      "branch": "main"
    },
    {
      "type": "applyPatch",
      "patch": "A <http://example.org/s2> <http://example.org/p2> \"value2\" .",
      "branch": "main"
    }
  ],
  "atomic": true,
  "singleCommit": true
}
```

**Parameters:**
- `operations` (required) - Array of operations to execute
  - Each operation has `type` ∈ {`query`, `update`, `applyPatch`}
  - Each can specify selector (`branch`, `commit`, `asOf`)
- `atomic` (optional, default `true`) - Execute all or nothing
- `singleCommit` (optional, default `false`) - Combine all writes into one commit

**Response:** 200 OK
```json
{
  "results": [
    {
      "index": 0,
      "type": "query",
      "status": "success",
      "result": { /* SPARQL query results */ }
    },
    {
      "index": 1,
      "type": "update",
      "status": "success",
      "commitId": "01933e4a-9d4e-7000-8000-000000000005"
    },
    {
      "index": 2,
      "type": "applyPatch",
      "status": "success",
      "commitId": "01933e4a-9d4e-7000-8000-000000000005"
    }
  ],
  "overallStatus": "success",
  "combinedCommitId": "01933e4a-9d4e-7000-8000-000000000005"
}
```

**Error Response:** 400 Bad Request (atomic failure)
```json
{
  "type": "about:blank",
  "title": "Batch Operation Failed",
  "status": 400,
  "code": "BATCH_FAILED",
  "failedAt": 1,
  "detail": "SPARQL syntax error in operation 1"
}
```

**Headers:**
- `SPARQL-VC-Status: pending` (if any writes occurred)
- `Location: /version/commits/{combinedCommitId}` (if single commit mode)

**CQRS Pattern:**
- Command: `BatchOperationCommand(dataset, operations, atomic, singleCommit, author, message)`
- Handler: `BatchOperationCommandHandler`
- Event: `BatchOperationExecutedEvent` (if single commit)
  - Or individual events for each operation

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `dto/BatchRequest.java`
- `dto/BatchOperation.java`
- `dto/BatchResponse.java`
- `dto/BatchOperationResult.java`

**File:** `src/main/java/org/chucc/vcserver/dto/BatchRequest.java`
```java
package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BatchRequest(
    List<BatchOperation> operations,
    @JsonProperty(defaultValue = "true") Boolean atomic,
    @JsonProperty(defaultValue = "false") Boolean singleCommit
) {
  public void validate() {
    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("Operations list cannot be empty");
    }
    for (int i = 0; i < operations.size(); i++) {
      try {
        operations.get(i).validate();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid operation at index " + i + ": " + e.getMessage());
      }
    }
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/BatchOperation.java`
```java
package org.chucc.vcserver.dto;

public record BatchOperation(
    String type,  // "query", "update", "applyPatch"
    String sparql,  // For query/update
    String patch,  // For applyPatch (RDF Patch)
    String branch,  // Selector
    String commit,  // Selector
    String asOf  // Selector
) {
  public void validate() {
    if (type == null || !List.of("query", "update", "applyPatch").contains(type)) {
      throw new IllegalArgumentException("Invalid type: " + type);
    }
    if ("query".equals(type) || "update".equals(type)) {
      if (sparql == null || sparql.isBlank()) {
        throw new IllegalArgumentException("SPARQL required for type: " + type);
      }
    }
    if ("applyPatch".equals(type)) {
      if (patch == null || patch.isBlank()) {
        throw new IllegalArgumentException("Patch required for type: applyPatch");
      }
    }
    // Validate selectors (mutually exclusive)
    int selectorCount = (branch != null ? 1 : 0) + (commit != null ? 1 : 0) + (asOf != null ? 1 : 0);
    if (selectorCount > 1) {
      throw new IllegalArgumentException("Only one selector allowed (branch, commit, or asOf)");
    }
  }
}
```

### Step 2: Create Command & Event

**File:** `src/main/java/org/chucc/vcserver/command/BatchOperationCommand.java`
**File:** `src/main/java/org/chucc/vcserver/event/BatchOperationExecutedEvent.java`

### Step 3: Implement Command Handler

**File:** `src/main/java/org/chucc/vcserver/command/BatchOperationCommandHandler.java`

```java
@Component
public class BatchOperationCommandHandler implements CommandHandler {

  private final SparqlQueryService queryService;
  private final SparqlUpdateService updateService;
  private final CommitService commitService;

  @Override
  public Event handle(Command command) {
    BatchOperationCommand cmd = (BatchOperationCommand) command;

    List<BatchOperationResult> results = new ArrayList<>();
    List<RdfPatch> patches = new ArrayList<>();

    for (int i = 0; i < cmd.operations().size(); i++) {
      BatchOperation op = cmd.operations().get(i);

      try {
        BatchOperationResult result = executeOperation(cmd.dataset(), op);
        results.add(result);

        // Collect patches for single commit mode
        if (cmd.singleCommit() && result.patch() != null) {
          patches.add(result.patch());
        }

      } catch (Exception e) {
        if (cmd.atomic()) {
          // Atomic mode: rollback and fail entire batch
          throw new BatchOperationException("Operation " + i + " failed", i, e);
        } else {
          // Non-atomic: record error and continue
          results.add(new BatchOperationResult(i, op.type(), "error", null, e.getMessage()));
        }
      }
    }

    // Single commit mode: combine all patches
    String combinedCommitId = null;
    if (cmd.singleCommit() && !patches.isEmpty()) {
      RdfPatch combinedPatch = combinePatchs(patches);
      combinedCommitId = createCommit(cmd.dataset(), combinedPatch, cmd.author(), cmd.message());
    }

    // Create event
    BatchOperationExecutedEvent event = new BatchOperationExecutedEvent(
        uuidGenerator.generateEventId(),
        cmd.dataset(),
        results,
        combinedCommitId,
        Instant.now()
    );

    eventPublisher.publish(event);
    return event;
  }

  private BatchOperationResult executeOperation(String dataset, BatchOperation op) {
    return switch (op.type()) {
      case "query" -> executeQuery(dataset, op);
      case "update" -> executeUpdate(dataset, op);
      case "applyPatch" -> executePatch(dataset, op);
      default -> throw new IllegalArgumentException("Unknown operation type: " + op.type());
    };
  }
}
```

### Step 4: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/BatchController.java`

Replace 501 stub with full implementation.

### Step 5: Write Tests

**Integration Tests:**
- Batch with multiple queries
- Batch with multiple updates (single commit)
- Batch with mixed operations
- Atomic mode failure (rollback)
- Non-atomic mode (partial success)
- Selector validation (branch, commit, asOf)

**Unit Tests:**
- `BatchOperationCommandHandlerTest.java`
- Test operation execution
- Test error handling

---

## Atomic Execution

**Atomic Mode (`atomic: true`):**
- Execute operations in order
- If any fails, rollback and return error
- No commits created if batch fails

**Non-Atomic Mode (`atomic: false`):**
- Execute all operations
- Record errors for failed operations
- Continue execution even if some fail
- Return mixed success/error results

**Single Commit Mode (`singleCommit: true`):**
- Collect all RDF Patch changes
- Combine into single patch
- Create one commit at the end
- Requires atomic mode (otherwise inconsistent)

---

## Success Criteria

- ✅ Endpoint implemented (no 501 response)
- ✅ DTOs created with validation
- ✅ Command/Event/Handler implemented
- ✅ Atomic and non-atomic modes work
- ✅ Single commit mode works
- ✅ Mixed operations work (query + update + patch)
- ✅ Selector validation works
- ✅ Integration tests pass
- ✅ Unit tests pass
- ✅ Zero quality violations
- ✅ Full build passes

---

## Notes

- This endpoint is **SPARQL Protocol specific**
- Distinct from `/version/batch-graphs` (Graph Store Protocol)
- Useful for migrations and bulk operations
- Atomic mode provides transaction-like semantics

---

## References

- [SPARQL 1.2 Protocol VC Extension §3.6](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [BatchController.java](../../src/main/java/org/chucc/vcserver/controller/BatchController.java)
- [BatchGraphsController.java](../../src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java) (GSP variant for reference)
