# Session 1: URL Utilities and OpenAPI Updates

**Status:** ⏳ Not Started
**Priority:** High (Foundation for all other sessions)
**Estimated Time:** 3-4 hours
**Complexity:** Medium

---

## Overview

Create utility classes for URL construction and update OpenAPI specifications to reflect the new semantic routing patterns. This provides the foundation for all subsequent routing refactoring sessions.

**Goal:** Establish URL building infrastructure and API documentation framework before implementing actual endpoint changes.

---

## Deliverables

1. ✅ `VersionControlUrls` utility class
2. ✅ Response header helper methods
3. ✅ OpenAPI spec updates with new path structures
4. ✅ Unit tests for URL utilities
5. ✅ OpenAPI validation passes

---

## Task 1: Create VersionControlUrls Utility Class

### Location

**New File:** `src/main/java/org/chucc/vcserver/controller/util/VersionControlUrls.java`

### Implementation

```java
package org.chucc.vcserver.controller.util;

import org.chucc.vcserver.domain.CommitId;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Utility class for constructing version control URLs following semantic routing patterns.
 * <p>
 * This class provides helper methods to build RESTful URLs that make datasets, branches,
 * commits, and tags directly shareable and bookmarkable.
 * </p>
 * <p>
 * URL Pattern: {@code /{dataset}/version/{ref-type}/{ref-name}/{service}}
 * </p>
 *
 * @see <a href="docs/architecture/semantic-routing.md">Semantic Routing Specification</a>
 */
public final class VersionControlUrls {

  private VersionControlUrls() {
    // Utility class - prevent instantiation
  }

  // ==================== Dataset URLs ====================

  /**
   * Constructs URL for dataset root.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata")
   */
  public static String dataset(String dataset) {
    return "/" + dataset;
  }

  /**
   * Constructs URL for dataset metadata endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/datasets/mydata")
   */
  public static String datasetMetadata(String dataset) {
    return "/datasets/" + dataset;
  }

  // ==================== Branch URLs ====================

  /**
   * Constructs URL for branch resource.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main")
   */
  public static String branch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s", dataset, branch);
  }

  /**
   * Constructs URL for branch list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/branches")
   */
  public static String branches(String dataset) {
    return String.format("/%s/version/branches", dataset);
  }

  /**
   * Constructs URL for branch history.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/history")
   */
  public static String branchHistory(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/history", dataset, branch);
  }

  // ==================== Commit URLs ====================

  /**
   * Constructs URL for commit resource.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890")
   */
  public static String commit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s", dataset, commitId);
  }

  /**
   * Constructs URL for commit resource.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID object
   * @return URL path
   */
  public static String commit(String dataset, CommitId commitId) {
    return commit(dataset, commitId.value());
  }

  /**
   * Constructs URL for commit list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/commits")
   */
  public static String commits(String dataset) {
    return String.format("/%s/version/commits", dataset);
  }

  /**
   * Constructs URL for commit history (from specific commit).
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../history")
   */
  public static String commitHistory(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/history", dataset, commitId);
  }

  /**
   * Constructs URL for diff between two commits.
   *
   * @param dataset Dataset name
   * @param fromCommit Source commit ID
   * @param toCommit Target commit ID
   * @return URL path (e.g., "/mydata/version/commits/abc/diff/def")
   */
  public static String commitDiff(String dataset, String fromCommit, String toCommit) {
    return String.format("/%s/version/commits/%s/diff/%s", dataset, fromCommit, toCommit);
  }

  // ==================== Tag URLs ====================

  /**
   * Constructs URL for tag resource.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0")
   */
  public static String tag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s", dataset, tag);
  }

  /**
   * Constructs URL for tag list endpoint.
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/tags")
   */
  public static String tags(String dataset) {
    return String.format("/%s/version/tags", dataset);
  }

  // ==================== SPARQL Endpoints ====================

  /**
   * Constructs URL for SPARQL query endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/sparql")
   */
  public static String sparqlAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/sparql", dataset, branch);
  }

  /**
   * Constructs URL for SPARQL query endpoint at commit.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../sparql")
   */
  public static String sparqlAtCommit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/sparql", dataset, commitId);
  }

  /**
   * Constructs URL for SPARQL query endpoint at tag.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0/sparql")
   */
  public static String sparqlAtTag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s/sparql", dataset, tag);
  }

  /**
   * Constructs URL for SPARQL update endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/update")
   */
  public static String updateAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/update", dataset, branch);
  }

  /**
   * Constructs URL for current-state SPARQL query (shortcut to main HEAD).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/sparql")
   */
  public static String sparql(String dataset) {
    return String.format("/%s/sparql", dataset);
  }

  /**
   * Constructs URL for current-state SPARQL update (shortcut to main branch).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/update")
   */
  public static String update(String dataset) {
    return String.format("/%s/update", dataset);
  }

  // ==================== Graph Store Protocol Endpoints ====================

  /**
   * Constructs URL for GSP endpoint at branch.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/data")
   */
  public static String dataAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/data", dataset, branch);
  }

  /**
   * Constructs URL for GSP endpoint at commit.
   *
   * @param dataset Dataset name
   * @param commitId Commit ID
   * @return URL path (e.g., "/mydata/version/commits/01936d8f.../data")
   */
  public static String dataAtCommit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s/data", dataset, commitId);
  }

  /**
   * Constructs URL for GSP endpoint at tag.
   *
   * @param dataset Dataset name
   * @param tag Tag name
   * @return URL path (e.g., "/mydata/version/tags/v1.0/data")
   */
  public static String dataAtTag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s/data", dataset, tag);
  }

  /**
   * Constructs URL for current-state GSP (shortcut to main HEAD).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/data")
   */
  public static String data(String dataset) {
    return String.format("/%s/data", dataset);
  }

  // ==================== Advanced Operations ====================

  /**
   * Constructs URL for merge endpoint.
   *
   * @param dataset Dataset name
   * @param targetBranch Target branch name
   * @return URL path (e.g., "/mydata/version/branches/main/merge")
   */
  public static String merge(String dataset, String targetBranch) {
    return String.format("/%s/version/branches/%s/merge", dataset, targetBranch);
  }

  /**
   * Constructs URL for reset endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/reset")
   */
  public static String reset(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/reset", dataset, branch);
  }

  /**
   * Constructs URL for revert endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/revert")
   */
  public static String revert(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/revert", dataset, branch);
  }

  /**
   * Constructs URL for cherry-pick endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/cherry-pick")
   */
  public static String cherryPick(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/cherry-pick", dataset, branch);
  }

  /**
   * Constructs URL for rebase endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/rebase")
   */
  public static String rebase(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/rebase", dataset, branch);
  }

  /**
   * Constructs URL for squash endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/squash")
   */
  public static String squash(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/squash", dataset, branch);
  }

  // ==================== Batch Operations ====================

  /**
   * Constructs URL for batch graphs endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/batch-graphs")
   */
  public static String batchGraphs(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/batch-graphs", dataset, branch);
  }

  /**
   * Constructs URL for batch SPARQL endpoint.
   *
   * @param dataset Dataset name
   * @param branch Branch name
   * @return URL path (e.g., "/mydata/version/branches/main/batch")
   */
  public static String batch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/batch", dataset, branch);
  }

  // ==================== Refs ====================

  /**
   * Constructs URL for refs list (all branches and tags).
   *
   * @param dataset Dataset name
   * @return URL path (e.g., "/mydata/version/refs")
   */
  public static String refs(String dataset) {
    return String.format("/%s/version/refs", dataset);
  }
}
```

---

## Task 2: Create Response Header Helpers

### Location

**New File:** `src/main/java/org/chucc/vcserver/controller/util/ResponseHeaderBuilder.java`

### Implementation

```java
package org.chucc.vcserver.controller.util;

import org.springframework.http.HttpHeaders;

/**
 * Utility class for building response headers for semantic routing.
 * <p>
 * Provides helper methods to add Content-Location and Link headers that help clients
 * discover canonical URLs and related resources (HATEOAS pattern).
 * </p>
 */
public final class ResponseHeaderBuilder {

  private ResponseHeaderBuilder() {
    // Utility class - prevent instantiation
  }

  /**
   * Adds Content-Location header with canonical URL.
   *
   * @param headers HTTP headers
   * @param canonicalUrl Canonical URL path
   */
  public static void addContentLocation(HttpHeaders headers, String canonicalUrl) {
    headers.set("Content-Location", canonicalUrl);
  }

  /**
   * Adds Link header for related resource.
   *
   * @param headers HTTP headers
   * @param url Related resource URL
   * @param rel Relationship type (e.g., "version", "branch", "commit")
   */
  public static void addLink(HttpHeaders headers, String url, String rel) {
    String linkValue = String.format("<%s>; rel=\"%s\"", url, rel);
    headers.add("Link", linkValue);
  }

  /**
   * Adds Link header for commit version.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param commitId Commit ID
   */
  public static void addCommitLink(HttpHeaders headers, String dataset, String commitId) {
    addLink(headers, VersionControlUrls.commit(dataset, commitId), "version");
  }

  /**
   * Adds Link header for branch.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param branch Branch name
   */
  public static void addBranchLink(HttpHeaders headers, String dataset, String branch) {
    addLink(headers, VersionControlUrls.branch(dataset, branch), "branch");
  }

  /**
   * Adds Link header for tag.
   *
   * @param headers HTTP headers
   * @param dataset Dataset name
   * @param tag Tag name
   */
  public static void addTagLink(HttpHeaders headers, String dataset, String tag) {
    addLink(headers, VersionControlUrls.tag(dataset, tag), "tag");
  }
}
```

---

## Task 3: Create Unit Tests

### Location

**New File:** `src/test/java/org/chucc/vcserver/controller/util/VersionControlUrlsTest.java`

### Implementation

```java
package org.chucc.vcserver.controller.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.domain.CommitId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VersionControlUrls}.
 */
class VersionControlUrlsTest {

  @Test
  void dataset_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.dataset("mydata"))
        .isEqualTo("/mydata");
  }

  @Test
  void branch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.branch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main");
  }

  @Test
  void commit_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.commit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890");
  }

  @Test
  void commitWithObject_shouldBuildCorrectUrl() {
    CommitId commitId = CommitId.fromString("01936d8f-1234-7890-abcd-ef1234567890");
    assertThat(VersionControlUrls.commit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890");
  }

  @Test
  void tag_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.tag("mydata", "v1.0"))
        .isEqualTo("/mydata/version/tags/v1.0");
  }

  @Test
  void sparqlAtBranch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.sparqlAtBranch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/sparql");
  }

  @Test
  void sparqlAtCommit_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.sparqlAtCommit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/sparql");
  }

  @Test
  void dataAtBranch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.dataAtBranch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/data");
  }

  @Test
  void commitDiff_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.commitDiff("mydata", "abc", "def"))
        .isEqualTo("/mydata/version/commits/abc/diff/def");
  }

  @Test
  void merge_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.merge("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/merge");
  }

  @Test
  void batchGraphs_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.batchGraphs("mydata", "develop"))
        .isEqualTo("/mydata/version/branches/develop/batch-graphs");
  }
}
```

---

## Task 4: Update OpenAPI Annotations (Example)

Update controller OpenAPI annotations to reflect new URL patterns:

### Example: BranchController

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

**Before:**
```java
@Tag(name = "Version Control - Branches", description = "Branch management operations")
@RestController
@RequestMapping("/version/branches")
public class BranchController {

  @Operation(summary = "Get branch details")
  @GetMapping("/{name}")
  public ResponseEntity<BranchInfo> getBranch(
      @RequestParam(defaultValue = "default") String dataset,
      @PathVariable String name) {
    // ...
  }
}
```

**After:**
```java
@Tag(name = "Version Control - Branches", description = "Branch management operations")
@RestController
@RequestMapping("/{dataset}/version/branches")
public class BranchController {

  @Operation(
      summary = "Get branch details",
      description = "Returns metadata for a specific branch including HEAD commit ID, protection status, and timestamps."
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Branch details returned successfully"),
      @ApiResponse(responseCode = "404", description = "Branch or dataset not found")
  })
  @GetMapping("/{name}")
  public ResponseEntity<BranchInfo> getBranch(
      @Parameter(description = "Dataset name", example = "default")
      @PathVariable String dataset,

      @Parameter(description = "Branch name", example = "main")
      @PathVariable String name) {
    // ...
  }
}
```

**Note:** This task only updates OpenAPI annotations. Actual routing changes happen in subsequent sessions.

---

## Task 5: Validate OpenAPI Spec

After updates, validate that OpenAPI spec is correct:

```bash
# Run application
mvn -q spring-boot:run

# Access Swagger UI (in separate terminal)
curl http://localhost:8080/v3/api-docs

# Should return valid OpenAPI 3.0 JSON without errors
```

**Validation points:**
- ✅ All path parameters documented
- ✅ Example values provided
- ✅ Response codes documented
- ✅ No duplicate operation IDs

---

## Testing Strategy

### Unit Tests

```bash
mvn -q test -Dtest=VersionControlUrlsTest
```

**Expected:** All tests pass

### Integration Tests

No integration tests needed for this session (utility classes only).

---

## Quality Gates

```bash
# Full build must pass
mvn -q clean install

# Specific checks:
mvn -q compile checkstyle:check    # Zero violations
mvn -q spotbugs:check               # Zero warnings
mvn -q pmd:check                    # Zero violations
```

---

## Completion Checklist

- [ ] `VersionControlUrls` class created with all URL builder methods
- [ ] `ResponseHeaderBuilder` class created with header helpers
- [ ] Unit tests for `VersionControlUrls` pass
- [ ] OpenAPI annotations updated with path parameters
- [ ] OpenAPI spec validates (no errors)
- [ ] Javadoc complete for all public methods
- [ ] All quality gates pass (Checkstyle, SpotBugs, PMD)
- [ ] Build succeeds (`mvn -q clean install`)

---

## Next Session

**Session 2:** [Dataset Parameter Standardization](./session-2-dataset-parameter-standardization.md)
- Use `VersionControlUrls` to build response URLs
- Add `Content-Location` headers using `ResponseHeaderBuilder`

---

**Estimated Time:** 3-4 hours
**Priority:** High (foundation for all routing changes)
