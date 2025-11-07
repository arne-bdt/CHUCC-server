# Session 3: Versioned SPARQL Endpoints

**Status:** ✅ Complete (2025-11-07)
**Priority:** High (Core functionality)
**Estimated Time:** 5-6 hours
**Actual Time:** ~5 hours
**Complexity:** Medium-High
**Dependencies:** Session 1 (URL utilities), Session 2 (dataset parameter)

---

## Overview

Implement versioned SPARQL query and update endpoints that allow querying at specific branches, commits, or tags. This is the core feature that makes dataset states shareable and citable via immutable URLs.

**Goal:** Users can query/update at any version reference using RESTful URLs.

---

## Deliverables

1. ✅ `GET/POST /{dataset}/version/branches/{name}/sparql` - Query at branch
2. ✅ `GET/POST /{dataset}/version/commits/{id}/sparql` - Query at commit (immutable)
3. ✅ `GET/POST /{dataset}/version/tags/{name}/sparql` - Query at tag
4. ✅ `POST /{dataset}/version/branches/{name}/update` - Update branch
5. ✅ Integration tests for all endpoints
6. ✅ Immutability enforcement (no updates to commits/tags)

---

## Background: Current SPARQL Routing

### Current Implementation

**File:** `src/main/java/org/chucc/vcserver/controller/SparqlController.java`

**Current pattern:**
```
GET/POST /sparql?query=...&dataset=default&branch=main
GET/POST /sparql?query=...&dataset=default&commit=01936d8f...
POST /update?update=...&dataset=default&branch=main
```

**Selectors:** Query parameters `branch`, `commit`, `asOf`

---

## Task 1: Create Versioned SPARQL Controller

### New File: `VersionedSparqlController.java`

**Location:** `src/main/java/org/chucc/vcserver/controller/VersionedSparqlController.java`

```java
package org.chucc.vcserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.service.SparqlQueryService;
import org.chucc.vcserver.service.SparqlUpdateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Versioned SPARQL query and update endpoints.
 * <p>
 * Supports querying and updating at specific version references:
 * <ul>
 *   <li><b>Branch:</b> {@code /{dataset}/version/branches/{name}/sparql}</li>
 *   <li><b>Commit:</b> {@code /{dataset}/version/commits/{id}/sparql} (read-only)</li>
 *   <li><b>Tag:</b> {@code /{dataset}/version/tags/{name}/sparql} (read-only)</li>
 * </ul>
 * </p>
 *
 * @see <a href="docs/architecture/semantic-routing.md">Semantic Routing</a>
 */
@Tag(name = "SPARQL Protocol - Versioned", description = "SPARQL operations at specific versions")
@RestController
public class VersionedSparqlController {

  private final SparqlQueryService queryService;
  private final SparqlUpdateService updateService;

  public VersionedSparqlController(
      SparqlQueryService queryService,
      SparqlUpdateService updateService) {
    this.queryService = queryService;
    this.updateService = updateService;
  }

  // ==================== Query at Branch ====================

  /**
   * Execute SPARQL query at branch HEAD.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param query SPARQL query string
   * @param asOf Time-travel timestamp (optional)
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at branch",
      description = "Execute SPARQL query against branch HEAD. Supports time-travel with asOf parameter."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Query executed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid SPARQL query"),
      @ApiResponse(responseCode = "404", description = "Branch or dataset not found")
  })
  @GetMapping("/{dataset}/version/branches/{branch}/sparql")
  public ResponseEntity<String> queryAtBranch(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(description = "Branch name", example = "main", required = true)
      @PathVariable String branch,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query,

      @Parameter(description = "Time-travel timestamp (ISO 8601)", example = "2025-11-03T10:00:00Z")
      @RequestParam(required = false) OffsetDateTime asOf) {

    // Execute query
    String results = queryService.executeQuery(dataset, branch, Optional.ofNullable(asOf), query);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.sparqlAtBranch(dataset, branch)
    );
    ResponseHeaderBuilder.addBranchLink(headers, dataset, branch);

    // Get current commit ID and add version link
    String commitId = queryService.getCurrentCommit(dataset, branch);
    ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType("application/sparql-results+json"))
        .body(results);
  }

  /**
   * Execute SPARQL query at branch HEAD (POST form).
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param query SPARQL query string (from POST body)
   * @param asOf Time-travel timestamp (optional)
   * @return Query results
   */
  @PostMapping(
      value = "/{dataset}/version/branches/{branch}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtBranchPost(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestParam String query,
      @RequestParam(required = false) OffsetDateTime asOf) {

    // Delegate to GET handler
    return queryAtBranch(dataset, branch, query, asOf);
  }

  // ==================== Query at Commit ====================

  /**
   * Execute SPARQL query at specific commit (immutable).
   *
   * @param dataset Dataset name
   * @param commitId Commit ID (UUIDv7)
   * @param query SPARQL query string
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at commit",
      description = "Execute SPARQL query against specific commit. **Immutable** - perfect for citations!"
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Query executed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid SPARQL query or commit ID"),
      @ApiResponse(responseCode = "404", description = "Commit or dataset not found")
  })
  @GetMapping("/{dataset}/version/commits/{commitId}/sparql")
  public ResponseEntity<String> queryAtCommit(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(
          description = "Commit ID (UUIDv7)",
          example = "01936d8f-1234-7890-abcd-ef1234567890",
          required = true
      )
      @PathVariable String commitId,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query) {

    // Execute query at commit
    String results = queryService.executeQueryAtCommit(dataset, commitId, query);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.sparqlAtCommit(dataset, commitId)
    );
    ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);

    // Add Cache-Control header (commits are immutable!)
    headers.setCacheControl("public, max-age=31536000, immutable");

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType("application/sparql-results+json"))
        .body(results);
  }

  /**
   * Execute SPARQL query at commit (POST form).
   */
  @PostMapping(
      value = "/{dataset}/version/commits/{commitId}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtCommitPost(
      @PathVariable String dataset,
      @PathVariable String commitId,
      @RequestParam String query) {

    return queryAtCommit(dataset, commitId, query);
  }

  // ==================== Query at Tag ====================

  /**
   * Execute SPARQL query at tagged version.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @param query SPARQL query string
   * @return Query results
   */
  @Operation(
      summary = "Query dataset at tag",
      description = "Execute SPARQL query against tagged version."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Query executed successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid SPARQL query"),
      @ApiResponse(responseCode = "404", description = "Tag or dataset not found")
  })
  @GetMapping("/{dataset}/version/tags/{tag}/sparql")
  public ResponseEntity<String> queryAtTag(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,

      @Parameter(description = "Tag name", example = "v1.0", required = true)
      @PathVariable String tag,

      @Parameter(description = "SPARQL query", required = true)
      @RequestParam String query) {

    // Execute query at tag
    String results = queryService.executeQueryAtTag(dataset, tag, query);

    // Get commit ID for this tag
    String commitId = queryService.getTagCommit(dataset, tag);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.sparqlAtTag(dataset, tag)
    );
    ResponseHeaderBuilder.addTagLink(headers, dataset, tag);
    ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);

    return ResponseEntity.ok()
        .headers(headers)
        .contentType(MediaType.parseMediaType("application/sparql-results+json"))
        .body(results);
  }

  /**
   * Execute SPARQL query at tag (POST form).
   */
  @PostMapping(
      value = "/{dataset}/version/tags/{tag}/sparql",
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
  )
  public ResponseEntity<String> queryAtTagPost(
      @PathVariable String dataset,
      @PathVariable String tag,
      @RequestParam String query) {

    return queryAtTag(dataset, tag, query);
  }

  // ==================== Update at Branch ====================

  /**
   * Execute SPARQL update at branch (creates commit).
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @param update SPARQL update string
   * @param author Commit author (header)
   * @param message Commit message (header, optional)
   * @return Update response with commit ID
   */
  @Operation(
      summary = "Update dataset at branch",
      description = "Execute SPARQL update against branch HEAD. Creates new commit automatically."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Update executed, commit created"),
      @ApiResponse(responseCode = "204", description = "No-op (no changes detected)"),
      @ApiResponse(responseCode = "400", description = "Invalid SPARQL update"),
      @ApiResponse(responseCode = "404", description = "Branch or dataset not found"),
      @ApiResponse(responseCode = "409", description = "Conflict (use If-Match header)")
  })
  @PostMapping(
      value = "/{dataset}/version/branches/{branch}/update",
      consumes = "application/sparql-update"
  )
  public ResponseEntity<String> updateAtBranch(
      @PathVariable String dataset,
      @PathVariable String branch,

      @RequestBody String update,

      @RequestHeader(value = "SPARQL-VC-Author", required = false) String author,
      @RequestHeader(value = "SPARQL-VC-Message", required = false) String message,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {

    // Execute update (creates commit)
    String commitId = updateService.executeUpdate(
        dataset,
        branch,
        update,
        Optional.ofNullable(author).orElse("anonymous"),
        Optional.ofNullable(message).orElse("SPARQL update"),
        Optional.ofNullable(ifMatch)
    );

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.updateAtBranch(dataset, branch)
    );
    ResponseHeaderBuilder.addBranchLink(headers, dataset, branch);
    ResponseHeaderBuilder.addCommitLink(headers, dataset, commitId);

    // Add Location header pointing to new commit
    headers.setLocation(
        java.net.URI.create(VersionControlUrls.commit(dataset, commitId))
    );

    return ResponseEntity.ok()
        .headers(headers)
        .body(String.format("{\"commitId\": \"%s\"}", commitId));
  }
}
```

---

## Task 2: Update SparqlQueryService

Add methods to support version-aware queries:

**File:** `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java`

```java
/**
 * Execute query at specific commit (immutable).
 *
 * @param dataset Dataset name
 * @param commitId Commit ID
 * @param query SPARQL query
 * @return Query results
 */
public String executeQueryAtCommit(String dataset, String commitId, String query) {
  // Get materialized view at commit
  DatasetGraph datasetGraph = materializedViewService.getViewAtCommit(dataset, commitId);

  // Execute query
  return executeQueryOnDataset(datasetGraph, query);
}

/**
 * Execute query at tag.
 *
 * @param dataset Dataset name
 * @param tag Tag name
 * @param query SPARQL query
 * @return Query results
 */
public String executeQueryAtTag(String dataset, String tag, String query) {
  // Resolve tag to commit
  String commitId = tagRepository.findByDatasetAndName(dataset, tag)
      .orElseThrow(() -> new TagNotFoundException(dataset, tag))
      .commitId();

  // Delegate to commit query
  return executeQueryAtCommit(dataset, commitId, query);
}

/**
 * Get commit ID for tag.
 */
public String getTagCommit(String dataset, String tag) {
  return tagRepository.findByDatasetAndName(dataset, tag)
      .orElseThrow(() -> new TagNotFoundException(dataset, tag))
      .commitId();
}
```

---

## Task 3: Create Integration Tests

### New File: `VersionedSparqlControllerIT.java`

**Location:** `src/test/java/org/chucc/vcserver/controller/VersionedSparqlControllerIT.java`

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("it")
class VersionedSparqlControllerIT extends IntegrationTestFixture {

  @Test
  void queryAtBranch_shouldReturnResults() {
    // Arrange: Insert test data
    String insertQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"test\" }";
    sparqlUpdateService.executeUpdate(DATASET, "main", insertQuery, "test", "Add test data", Optional.empty());

    // Act: Query at branch
    String url = "/default/version/branches/main/sparql?query=" +
        URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }", StandardCharsets.UTF_8);

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Location"))
        .containsExactly("/default/version/branches/main/sparql");
    assertThat(response.getBody()).contains("http://example.org/s");
  }

  @Test
  void queryAtCommit_shouldReturnImmutableResults() {
    // Arrange: Create commit
    String insertQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"v1\" }";
    String commitId = sparqlUpdateService.executeUpdate(
        DATASET, "main", insertQuery, "test", "Version 1", Optional.empty());

    // Act: Query at commit
    String url = String.format("/default/version/commits/%s/sparql?query=%s",
        commitId,
        URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }", StandardCharsets.UTF_8));

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Location"))
        .contains("/default/version/commits/" + commitId + "/sparql");
    assertThat(response.getHeaders().getCacheControl())
        .contains("immutable"); // Immutable!
    assertThat(response.getBody()).contains("http://example.org/s");
  }

  @Test
  void queryAtCommit_afterSubsequentChanges_shouldReturnOriginalState() {
    // Arrange: Create commit v1
    String insertV1 = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"v1\" }";
    String commitIdV1 = sparqlUpdateService.executeUpdate(
        DATASET, "main", insertV1, "test", "Version 1", Optional.empty());

    // Arrange: Create commit v2 (different data)
    String deleteV1 = "DELETE DATA { <http://example.org/s> <http://example.org/p> \"v1\" }";
    String insertV2 = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"v2\" }";
    sparqlUpdateService.executeUpdate(
        DATASET, "main", deleteV1 + "; " + insertV2, "test", "Version 2", Optional.empty());

    // Act: Query at commit v1 (should still show v1, not v2)
    String url = String.format("/default/version/commits/%s/sparql?query=%s",
        commitIdV1,
        URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }", StandardCharsets.UTF_8));

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert: Should return v1 data, not v2!
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"v1\"");
    assertThat(response.getBody()).doesNotContain("\"v2\"");
  }

  @Test
  void queryAtTag_shouldReturnResults() {
    // Arrange: Create commit and tag
    String insertQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"v1.0\" }";
    String commitId = sparqlUpdateService.executeUpdate(
        DATASET, "main", insertQuery, "test", "Release 1.0", Optional.empty());

    tagService.createTag(DATASET, "v1.0", commitId, "test", "First release", true);

    // Act: Query at tag
    String url = "/default/version/tags/v1.0/sparql?query=" +
        URLEncoder.encode("SELECT * WHERE { ?s ?p ?o }", StandardCharsets.UTF_8);

    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Location"))
        .containsExactly("/default/version/tags/v1.0/sparql");
    assertThat(response.getBody()).contains("\"v1.0\"");
  }

  @Test
  void updateAtBranch_shouldCreateCommit() {
    // Act: Execute update
    String url = "/default/version/branches/main/update";
    String updateQuery = "INSERT DATA { <http://example.org/s> <http://example.org/p> \"new\" }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    headers.set("SPARQL-VC-Message", "Add new data");

    HttpEntity<String> request = new HttpEntity<>(updateQuery, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Location"))
        .contains("/default/version/branches/main/update");
    assertThat(response.getHeaders().getLocation()).isNotNull(); // Points to new commit
    assertThat(response.getBody()).contains("commitId");
  }

  @Test
  void updateAtCommit_shouldReturn405MethodNotAllowed() {
    // Act: Try to update at commit (should fail - immutable!)
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    String url = "/default/version/commits/" + commitId + "/update";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));

    HttpEntity<String> request = new HttpEntity<>("INSERT DATA { <s> <p> <o> }", headers);

    // Assert: Should return 405 Method Not Allowed
    assertThatThrownBy(() -> restTemplate.postForEntity(url, request, String.class))
        .hasMessageContaining("405");
  }
}
```

---

## Task 4: Add Immutability Enforcement

Commits and tags are immutable - no update endpoints should exist for them:

**Implementation:** Simply don't create update endpoints for commits/tags. Spring will return `405 Method Not Allowed` automatically.

**Test:**
```java
@Test
void updateAtCommit_shouldReturn405() {
  // Commits are immutable - no update endpoint exists
  String url = "/default/version/commits/01936d8f.../update";
  assertThatThrownBy(() -> restTemplate.postForEntity(url, "...", String.class))
      .hasMessageContaining("405 Method Not Allowed");
}
```

---

## Quality Gates

```bash
# Unit tests
mvn -q test -Dtest=VersionedSparqlControllerIT

# Integration tests
mvn -q test -Dtest=*SparqlControllerIT

# Full build
mvn -q clean install
```

---

## Completion Checklist

- [ ] `VersionedSparqlController` created
- [ ] Query at branch endpoint implemented
- [ ] Query at commit endpoint implemented (immutable)
- [ ] Query at tag endpoint implemented
- [ ] Update at branch endpoint implemented
- [ ] No update endpoints for commits/tags (405 enforcement)
- [ ] `SparqlQueryService` methods added
- [ ] Integration tests pass
- [ ] Response headers correct (`Content-Location`, `Link`, `Cache-Control`)
- [ ] OpenAPI documentation updated
- [ ] All quality gates pass

---

## Next Session

**Session 4:** [Versioned Graph Store Protocol Endpoints](./session-4-versioned-gsp-endpoints.md)

---

**Estimated Time:** 5-6 hours
**Priority:** High (core feature)
