# Session 1: Core Prefix Management Implementation

**Status:** Not Started
**Estimated Time:** 4-5 hours
**Priority:** High (Start Here)
**Dependencies:** None (all infrastructure exists)

---

## Overview

Implement basic GET/PUT/PATCH/DELETE operations for prefix management. This session provides the foundation for all other prefix management features.

**Goal:** Users can retrieve, replace, add, and delete prefixes via REST API. All operations create commits (version controlled).

---

## Current State

**Existing Infrastructure:**
- ✅ `CreateCommitCommandHandler` - Creates commits with RDFPatch
- ✅ `MaterializedBranchRepository` - Caches branch HEAD (includes PrefixMapping)
- ✅ `ReadModelProjector` - Applies PA/PD directives from RDFPatch
- ✅ RDFPatch support - PA (Prefix Add) and PD (Prefix Delete) directives

**What exists (empty stub):**
```java
@RestController
@RequestMapping("/version/datasets/{dataset}")
public class PrefixManagementController {
  // Currently returns 501 Not Implemented
}
```

---

## Requirements

### Endpoints to Implement

#### 1. GET - Retrieve Prefixes
```http
GET /version/datasets/{dataset}/branches/{branch}/prefixes
Accept: application/json

→ 200 OK
{
  "dataset": "mydata",
  "branch": "main",
  "commitId": "01JCDN...",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

#### 2. PUT - Replace Entire Prefix Map
```http
PUT /version/datasets/{dataset}/branches/{branch}/prefixes
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "message": "Update prefixes",
  "prefixes": {
    "ex": "http://example.org/",
    "schema": "http://schema.org/"
  }
}

→ 201 Created
Location: /version/datasets/mydata/commits/01JCDN...
ETag: "01JCDN..."

{
  "dataset": "mydata",
  "branch": "main",
  "commitId": "01JCDN...",
  "message": "Update prefixes"
}
```

#### 3. PATCH - Add/Update Selected Prefixes
```http
PATCH /version/datasets/{dataset}/branches/{branch}/prefixes
Content-Type: application/json
SPARQL-VC-Author: Bob <bob@example.org>

{
  "message": "Add geospatial prefixes",
  "prefixes": {
    "geo": "http://www.opengis.net/ont/geosparql#",
    "sf": "http://www.opengis.net/ont/sf#"
  }
}

→ 201 Created
{
  "commitId": "01JCDN...",
  "message": "Add geospatial prefixes"
}
```

#### 4. DELETE - Remove Prefixes
```http
DELETE /version/datasets/{dataset}/branches/{branch}/prefixes?prefix=temp&prefix=test
SPARQL-VC-Author: Alice <alice@example.org>

→ 201 Created
{
  "commitId": "01JCDN...",
  "message": "Remove prefixes: temp, test"
}
```

---

## Implementation Steps

### Step 1: Create DTOs (30 minutes)

**1.1 Create `PrefixResponse.java`**

```java
package org.chucc.vcserver.dto;

import java.util.Map;

/**
 * Response containing prefix mappings for a branch or commit.
 */
public record PrefixResponse(
    String dataset,
    String branch,  // Nullable for commit-based queries
    String commitId,
    Map<String, String> prefixes
) {
  /**
   * Creates a prefix response.
   *
   * @param dataset the dataset name
   * @param branch the branch name (nullable for commit queries)
   * @param commitId the commit ID
   * @param prefixes the prefix mappings (prefix name → IRI)
   */
  public PrefixResponse {
    // Defensive copy
    prefixes = Map.copyOf(prefixes);
  }
}
```

**1.2 Create `UpdatePrefixesRequest.java`**

```java
package org.chucc.vcserver.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request to update prefix mappings.
 */
public record UpdatePrefixesRequest(
    String message,  // Optional commit message

    @NotNull(message = "Prefixes map is required")
    Map<String, String> prefixes
) {
  /**
   * Creates an update prefixes request.
   *
   * @param message optional commit message
   * @param prefixes prefix mappings to add/replace/delete
   */
  public UpdatePrefixesRequest {
    // Defensive copy
    prefixes = Map.copyOf(prefixes);
  }
}
```

**1.3 Create `CommitResponse.java`** (reusable DTO)

```java
package org.chucc.vcserver.dto;

import org.chucc.vcserver.domain.CommitMetadata;

/**
 * Response after creating a commit.
 */
public record CommitResponse(
    String dataset,
    String branch,
    String commitId,
    String message,
    String author,
    String timestamp
) {
  /**
   * Creates a commit response from commit metadata.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param commit the commit metadata
   * @return the commit response
   */
  public static CommitResponse from(String dataset, String branch, CommitMetadata commit) {
    return new CommitResponse(
        dataset,
        branch,
        commit.id(),
        commit.message(),
        commit.author(),
        commit.timestamp().toString()
    );
  }
}
```

---

### Step 2: Create Command and Handler (90 minutes)

**2.1 Create `UpdatePrefixesCommand.java`**

```java
package org.chucc.vcserver.command;

import java.util.Map;
import java.util.Optional;

/**
 * Command to update prefix mappings on a branch.
 */
public record UpdatePrefixesCommand(
    String dataset,
    String branch,
    String author,
    Map<String, String> newPrefixes,
    Operation operation,
    Optional<String> message
) {
  /**
   * Operation type for prefix updates.
   */
  public enum Operation {
    PUT,     // Replace all prefixes
    PATCH,   // Add/update selected prefixes
    DELETE   // Remove selected prefixes
  }

  /**
   * Creates an update prefixes command.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author
   * @param newPrefixes the prefix mappings
   * @param operation the operation type
   * @param message optional commit message
   */
  public UpdatePrefixesCommand {
    newPrefixes = Map.copyOf(newPrefixes);
  }
}
```

**2.2 Create `UpdatePrefixesCommandHandler.java`**

```java
package org.chucc.vcserver.command;

import java.util.Map;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.CommitMetadata;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.stereotype.Component;

/**
 * Handles prefix update commands by generating RDFPatch with PA/PD directives.
 *
 * <p>This handler delegates to CreateCommitCommandHandler - no new events needed.
 */
@Component
public class UpdatePrefixesCommandHandler {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final BranchRepository branchRepository;
  private final CreateCommitCommandHandler createCommitCommandHandler;

  /**
   * Creates an update prefixes command handler.
   *
   * @param materializedBranchRepository the materialized branch repository
   * @param branchRepository the branch repository
   * @param createCommitCommandHandler the commit creation handler
   */
  public UpdatePrefixesCommandHandler(
      MaterializedBranchRepository materializedBranchRepository,
      BranchRepository branchRepository,
      CreateCommitCommandHandler createCommitCommandHandler) {
    this.materializedBranchRepository = materializedBranchRepository;
    this.branchRepository = branchRepository;
    this.createCommitCommandHandler = createCommitCommandHandler;
  }

  /**
   * Handles prefix update command.
   *
   * @param cmd the update prefixes command
   * @return commit metadata
   * @throws BranchNotFoundException if branch doesn't exist
   */
  public CommitMetadata handle(UpdatePrefixesCommand cmd) {
    // 1. Validate branch exists
    branchRepository.findByDatasetAndName(cmd.dataset(), cmd.branch())
        .orElseThrow(() -> new BranchNotFoundException(cmd.dataset(), cmd.branch()));

    // 2. Get current prefixes from materialized branch
    DatasetGraph currentDsg = materializedBranchRepository
        .getMaterializedBranch(cmd.dataset(), cmd.branch());
    Map<String, String> oldPrefixes = currentDsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    // 3. Generate RDFPatch with PA/PD directives
    RDFPatch patch = buildPrefixPatch(oldPrefixes, cmd.newPrefixes(), cmd.operation());

    // 4. Create commit via existing handler
    String message = cmd.message().orElseGet(() -> generateDefaultMessage(cmd));
    CreateCommitCommand commitCmd = new CreateCommitCommand(
        cmd.dataset(),
        cmd.branch(),
        message,
        cmd.author(),
        RDFPatchOps.str(patch)
    );

    return createCommitCommandHandler.handle(commitCmd);
  }

  /**
   * Builds RDFPatch with PA/PD directives.
   *
   * @param oldPrefixes current prefix mappings
   * @param newPrefixes new prefix mappings
   * @param operation the operation type
   * @return RDFPatch with prefix directives
   */
  RDFPatch buildPrefixPatch(
      Map<String, String> oldPrefixes,
      Map<String, String> newPrefixes,
      UpdatePrefixesCommand.Operation operation) {

    StringBuilder patchStr = new StringBuilder("TX .\n");

    switch (operation) {
      case PUT -> {
        // Remove all old prefixes
        oldPrefixes.forEach((prefix, iri) ->
            patchStr.append("PD ").append(prefix).append(": .\n"));
        // Add all new prefixes
        newPrefixes.forEach((prefix, iri) ->
            patchStr.append("PA ").append(prefix).append(": <").append(iri).append("> .\n"));
      }
      case PATCH -> {
        // Add/update only specified prefixes
        newPrefixes.forEach((prefix, iri) ->
            patchStr.append("PA ").append(prefix).append(": <").append(iri).append("> .\n"));
      }
      case DELETE -> {
        // Remove specified prefixes
        newPrefixes.keySet().forEach(prefix ->
            patchStr.append("PD ").append(prefix).append(": .\n"));
      }
    }

    patchStr.append("TC .");
    return RDFPatchOps.read(patchStr.toString());
  }

  /**
   * Generates default commit message.
   *
   * @param cmd the command
   * @return default message
   */
  private String generateDefaultMessage(UpdatePrefixesCommand cmd) {
    return switch (cmd.operation()) {
      case PUT -> "Replace prefix map";
      case PATCH -> "Add prefixes: " + String.join(", ", cmd.newPrefixes().keySet());
      case DELETE -> "Remove prefixes: " + String.join(", ", cmd.newPrefixes().keySet());
    };
  }
}
```

---

### Step 3: Create REST Controller (60 minutes)

**3.1 Create `PrefixManagementController.java`**

```java
package org.chucc.vcserver.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.command.UpdatePrefixesCommand;
import org.chucc.vcserver.command.UpdatePrefixesCommand.Operation;
import org.chucc.vcserver.command.UpdatePrefixesCommandHandler;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitMetadata;
import org.chucc.vcserver.dto.CommitResponse;
import org.chucc.vcserver.dto.PrefixResponse;
import org.chucc.vcserver.dto.UpdatePrefixesRequest;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Prefix Management Protocol (PMP).
 *
 * <p>Implements version-aware prefix management with commit creation.
 *
 * @see <a href="../../protocol/Prefix_Management_Protocol.md">PMP Specification</a>
 */
@RestController
@RequestMapping("/version/datasets/{dataset}")
public class PrefixManagementController {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final BranchRepository branchRepository;
  private final UpdatePrefixesCommandHandler updatePrefixesCommandHandler;

  /**
   * Creates a prefix management controller.
   *
   * @param materializedBranchRepository the materialized branch repository
   * @param branchRepository the branch repository
   * @param updatePrefixesCommandHandler the prefix update handler
   */
  public PrefixManagementController(
      MaterializedBranchRepository materializedBranchRepository,
      BranchRepository branchRepository,
      UpdatePrefixesCommandHandler updatePrefixesCommandHandler) {
    this.materializedBranchRepository = materializedBranchRepository;
    this.branchRepository = branchRepository;
    this.updatePrefixesCommandHandler = updatePrefixesCommandHandler;
  }

  /**
   * Retrieves prefix mappings for a branch.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return prefix response
   * @throws BranchNotFoundException if branch doesn't exist
   */
  @GetMapping("/branches/{branch}/prefixes")
  public ResponseEntity<PrefixResponse> getCurrentPrefixes(
      @PathVariable String dataset,
      @PathVariable String branch) {

    // Get branch (validate exists)
    Branch branchObj = branchRepository
        .findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(dataset, branch));

    // Read prefixes from materialized branch
    DatasetGraph dsg = materializedBranchRepository
        .getMaterializedBranch(dataset, branch);
    Map<String, String> prefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    PrefixResponse response = new PrefixResponse(
        dataset,
        branch,
        branchObj.headCommitId(),
        prefixes
    );

    return ResponseEntity
        .ok()
        .eTag(branchObj.headCommitId())
        .body(response);
  }

  /**
   * Replaces entire prefix map (creates commit).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author
   * @param request the update request
   * @return commit response
   */
  @PutMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> replacePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestHeader("SPARQL-VC-Author") String author,
      @Valid @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        Operation.PUT,
        Optional.ofNullable(request.message())
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    URI location = URI.create(
        "/version/datasets/" + dataset + "/commits/" + commit.id()
    );

    CommitResponse response = CommitResponse.from(dataset, branch, commit);

    return ResponseEntity
        .created(location)
        .eTag(commit.id())
        .body(response);
  }

  /**
   * Adds or updates selected prefixes (creates commit).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author
   * @param request the update request
   * @return commit response
   */
  @PatchMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> updatePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestHeader("SPARQL-VC-Author") String author,
      @Valid @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        Operation.PATCH,
        Optional.ofNullable(request.message())
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    URI location = URI.create(
        "/version/datasets/" + dataset + "/commits/" + commit.id()
    );

    CommitResponse response = CommitResponse.from(dataset, branch, commit);

    return ResponseEntity
        .created(location)
        .eTag(commit.id())
        .body(response);
  }

  /**
   * Removes specified prefixes (creates commit).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param prefixNames the prefix names to remove
   * @param message optional commit message
   * @param author the commit author
   * @return commit response
   */
  @DeleteMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> deletePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestParam("prefix") List<String> prefixNames,
      @RequestParam(required = false) String message,
      @RequestHeader("SPARQL-VC-Author") String author) {

    // Convert prefix list to map (value doesn't matter for DELETE)
    Map<String, String> prefixesToDelete = prefixNames.stream()
        .collect(java.util.stream.Collectors.toMap(p -> p, p -> ""));

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        prefixesToDelete,
        Operation.DELETE,
        Optional.ofNullable(message)
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    URI location = URI.create(
        "/version/datasets/" + dataset + "/commits/" + commit.id()
    );

    CommitResponse response = CommitResponse.from(dataset, branch, commit);

    return ResponseEntity
        .created(location)
        .eTag(commit.id())
        .body(response);
  }
}
```

---

### Step 4: Write Integration Tests (90 minutes)

**4.1 Create `PrefixManagementIT.java`**

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.chucc.vcserver.dto.CommitResponse;
import org.chucc.vcserver.dto.PrefixResponse;
import org.chucc.vcserver.dto.UpdatePrefixesRequest;
import org.chucc.vcserver.testutil.IntegrationTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Prefix Management Protocol (PMP).
 *
 * <p>Tests API layer only (projector disabled by default).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class PrefixManagementIT extends IntegrationTestFixture {

  @Test
  void getPrefixes_shouldReturnEmptyMap_whenNoPrefix() {
    // Act
    ResponseEntity<PrefixResponse> response = restTemplate.exchange(
        "/version/datasets/default/branches/main/prefixes",
        HttpMethod.GET,
        null,
        PrefixResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().prefixes()).isEmpty();
    assertThat(response.getHeaders().getETag()).isNotNull();
  }

  @Test
  void putPrefixes_shouldReturn201Created() {
    // Arrange
    UpdatePrefixesRequest request = new UpdatePrefixesRequest(
        "Add RDF prefixes",
        Map.of(
            "rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "rdfs", "http://www.w3.org/2000/01/rdf-schema#"
        )
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<UpdatePrefixesRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<CommitResponse> response = restTemplate.exchange(
        "/version/datasets/default/branches/main/prefixes",
        HttpMethod.PUT,
        httpEntity,
        CommitResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().commitId()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Add RDF prefixes");
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getETag()).isNotNull();

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void patchPrefixes_shouldReturn201Created() {
    // Arrange
    UpdatePrefixesRequest request = new UpdatePrefixesRequest(
        "Add FOAF prefix",
        Map.of("foaf", "http://xmlns.com/foaf/0.1/")
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<UpdatePrefixesRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<CommitResponse> response = restTemplate.exchange(
        "/version/datasets/default/branches/main/prefixes",
        HttpMethod.PATCH,
        httpEntity,
        CommitResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).isEqualTo("Add FOAF prefix");
  }

  @Test
  void deletePrefixes_shouldReturn201Created() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

    // Act
    ResponseEntity<CommitResponse> response = restTemplate.exchange(
        "/version/datasets/default/branches/main/prefixes?prefix=temp&prefix=test",
        HttpMethod.DELETE,
        httpEntity,
        CommitResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().message()).contains("Remove prefixes");
  }

  @Test
  void putPrefixes_shouldReturn400_whenAuthorMissing() {
    // Arrange
    UpdatePrefixesRequest request = new UpdatePrefixesRequest(
        null,
        Map.of("ex", "http://example.org/")
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    // No SPARQL-VC-Author header

    HttpEntity<UpdatePrefixesRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/datasets/default/branches/main/prefixes",
        HttpMethod.PUT,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void getPrefixes_shouldReturn404_whenBranchNotFound() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/datasets/default/branches/nonexistent/prefixes",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
```

Add 4-5 more tests:
- `putPrefixes_shouldReplaceAllPrefixes()`
- `patchPrefixes_shouldPreserveExistingPrefixes()`
- `deletePrefixes_shouldBeIdempotent()`
- `putPrefixes_shouldValidatePrefixNames()`
- `putPrefixes_shouldValidateAbsoluteIris()`

---

### Step 5: Write Unit Tests (30 minutes)

**5.1 Create `UpdatePrefixesCommandHandlerTest.java`**

```java
package org.chucc.vcserver.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for UpdatePrefixesCommandHandler.
 */
class UpdatePrefixesCommandHandlerTest {

  @Test
  void buildPrefixPatch_shouldGeneratePaDirectives_forPatchOperation() {
    // Arrange
    UpdatePrefixesCommandHandler handler = new UpdatePrefixesCommandHandler(
        mock(MaterializedBranchRepository.class),
        mock(BranchRepository.class),
        mock(CreateCommitCommandHandler.class)
    );

    Map<String, String> oldPrefixes = Map.of();
    Map<String, String> newPrefixes = Map.of(
        "foaf", "http://xmlns.com/foaf/0.1/",
        "geo", "http://www.opengis.net/ont/geosparql#"
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        newPrefixes,
        UpdatePrefixesCommand.Operation.PATCH
    );

    // Assert
    String patchStr = patch.toString();
    assertThat(patchStr).contains("PA foaf: <http://xmlns.com/foaf/0.1/>");
    assertThat(patchStr).contains("PA geo: <http://www.opengis.net/ont/geosparql#>");
    assertThat(patchStr).doesNotContain("PD");  // No deletions
  }

  @Test
  void buildPrefixPatch_shouldGeneratePdThenPa_forPutOperation() {
    // Arrange
    UpdatePrefixesCommandHandler handler = new UpdatePrefixesCommandHandler(
        mock(MaterializedBranchRepository.class),
        mock(BranchRepository.class),
        mock(CreateCommitCommandHandler.class)
    );

    Map<String, String> oldPrefixes = Map.of(
        "old1", "http://example.org/old1/",
        "old2", "http://example.org/old2/"
    );
    Map<String, String> newPrefixes = Map.of(
        "new", "http://example.org/new/"
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        newPrefixes,
        UpdatePrefixesCommand.Operation.PUT
    );

    // Assert
    String patchStr = patch.toString();
    assertThat(patchStr).contains("PD old1:");
    assertThat(patchStr).contains("PD old2:");
    assertThat(patchStr).contains("PA new: <http://example.org/new/>");
  }

  @Test
  void buildPrefixPatch_shouldGeneratePdDirectives_forDeleteOperation() {
    // Arrange
    UpdatePrefixesCommandHandler handler = new UpdatePrefixesCommandHandler(
        mock(MaterializedBranchRepository.class),
        mock(BranchRepository.class),
        mock(CreateCommitCommandHandler.class)
    );

    Map<String, String> oldPrefixes = Map.of();
    Map<String, String> prefixesToDelete = Map.of(
        "temp", "",
        "test", ""
    );

    // Act
    RDFPatch patch = handler.buildPrefixPatch(
        oldPrefixes,
        prefixesToDelete,
        UpdatePrefixesCommand.Operation.DELETE
    );

    // Assert
    String patchStr = patch.toString();
    assertThat(patchStr).contains("PD temp:");
    assertThat(patchStr).contains("PD test:");
    assertThat(patchStr).doesNotContain("PA");  // No additions
  }
}
```

---

### Step 6: Run Tests and Fix Issues (30 minutes)

**6.1 Run static analysis:**
```bash
mvn -q compile checkstyle:check spotbugs:check pmd:check
```

**6.2 Run tests:**
```bash
mvn -q test -Dtest=PrefixManagementIT
mvn -q test -Dtest=UpdatePrefixesCommandHandlerTest
```

**6.3 Fix any issues:**
- Checkstyle violations
- SpotBugs warnings (defensive copying, etc.)
- Test failures

---

### Step 7: Verify CQRS Compliance (15 minutes)

After implementation, invoke the specialized agent:

```
@cqrs-compliance-checker

Please verify UpdatePrefixesCommandHandler follows CQRS patterns:
- Command handler should delegate to CreateCommitCommandHandler
- No new events should be created
- No direct repository writes
```

---

## Success Criteria

### Functional
- ✅ GET returns prefixes from materialized branch
- ✅ PUT creates commit with PD (old) + PA (new) directives
- ✅ PATCH creates commit with PA directives only
- ✅ DELETE creates commit with PD directives
- ✅ All operations return 201 Created with commit metadata
- ✅ Location header points to created commit
- ✅ ETag contains commit ID

### Quality
- ✅ All tests pass (10+ integration tests)
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Zero compiler warnings

### CQRS Compliance
- ✅ No new events created (reuses CommitCreatedEvent)
- ✅ No direct repository writes
- ✅ Command handler delegates to CreateCommitCommandHandler
- ✅ Projector applies PA/PD directives automatically

---

## Files to Create

```
src/main/java/org/chucc/vcserver/
  ├── dto/
  │   ├── PrefixResponse.java                    # NEW
  │   ├── UpdatePrefixesRequest.java             # NEW
  │   └── CommitResponse.java                    # NEW (reusable)
  ├── command/
  │   ├── UpdatePrefixesCommand.java             # NEW
  │   └── UpdatePrefixesCommandHandler.java      # NEW
  └── controller/
      └── PrefixManagementController.java        # NEW

src/test/java/org/chucc/vcserver/
  ├── integration/
  │   └── PrefixManagementIT.java                # NEW
  └── command/
      └── UpdatePrefixesCommandHandlerTest.java  # NEW
```

---

## Common Issues and Solutions

### Issue 1: RDFPatch Parsing Fails
**Symptom:** `RDFPatchOps.read()` throws exception

**Solution:** Ensure proper format:
```
TX .
PA foaf: <http://xmlns.com/foaf/0.1/> .
PD temp: .
TC .
```
(Note: space before period, no space after colon in PD)

### Issue 2: Prefixes Not Updated
**Symptom:** GET returns old prefixes after PUT

**Solution:** This is EXPECTED with projector disabled (test isolation pattern). The projector would apply PA/PD directives in production.

### Issue 3: Missing Author Header Returns 500
**Symptom:** Should return 400 Bad Request

**Solution:** Add validation in controller:
```java
@RequestHeader("SPARQL-VC-Author") String author
```
Spring will return 400 if header missing.

---

## Next Steps

After completing this session:

1. ✅ Mark session as completed in [README.md](./README.md)
2. ✅ Commit changes with message:
   ```
   feat(pmp): implement core prefix management operations

   - Add GET/PUT/PATCH/DELETE endpoints for prefix management
   - Create UpdatePrefixesCommandHandler (reuses CreateCommitCommandHandler)
   - Add PrefixManagementController
   - Add integration tests (10+ tests)
   - Add unit tests for command handler

   Prefix changes now create commits with RDFPatch PA/PD directives.
   All operations version-controlled via existing CQRS infrastructure.

   🤖 Generated with Claude Code

   Co-Authored-By: Claude <noreply@anthropic.com>
   ```
3. ✅ Proceed to [Session 2: Time-Travel Support](./session-2-time-travel-support.md)

---

## References

- [Prefix Management Protocol v1.0](../../protocol/Prefix_Management_Protocol.md)
- [CHUCC Implementation Guide](../../docs/api/prefix-management.md)
- [RDFPatch PA/PD Directives](https://afs.github.io/rdf-patch/#prefix-directives)
- [CreateCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java)

---

**Ready to start!** Begin with Step 1 (Create DTOs) and work through sequentially.
