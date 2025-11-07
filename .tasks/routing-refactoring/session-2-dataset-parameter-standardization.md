# Session 2: Dataset Parameter Standardization

**Status:** ✅ Complete (2025-11-07)
**Priority:** High (Prerequisite for versioned routing)
**Estimated Time:** 4-5 hours
**Actual Time:** ~4 hours
**Complexity:** Medium-High

---

## Overview

Standardize dataset addressing across all controllers by moving dataset from query parameter to path variable. This aligns the codebase with Fuseki patterns (`/{dataset}/{service}`) and establishes consistent dataset routing before adding version-aware endpoints.

**Current Problem:**
- Most controllers: `@RequestParam String dataset` (default: "default")
- TagController: `@PathVariable String dataset`
- **Inconsistent!**

**Target State:**
- All controllers: `@PathVariable String dataset` (no default)
- Request mapping: `/{dataset}/version/...`

---

## Deliverables

1. ✅ Refactor all controllers to use path-based dataset parameter
2. ✅ Update integration tests for new URL patterns
3. ✅ Add backward compatibility support (query param still works, deprecated)
4. ✅ Add `Content-Location` headers pointing to new URLs
5. ✅ All tests pass

---

## Affected Controllers

| Controller | Current Mapping | Target Mapping | Files to Update |
|------------|-----------------|----------------|-----------------|
| BranchController | `/version/branches` + `?dataset=` | `/{dataset}/version/branches` | Controller + 4 tests |
| CommitController | `/version/commits` + `?dataset=` | `/{dataset}/version/commits` | Controller + 3 tests |
| TagController | `/{dataset}/version/tags` | ✅ Already correct | Review only |
| MergeController | `/version/merge` + `?dataset=` | `/{dataset}/version/merge` | Controller + 5 tests |
| AdvancedOpsController | `/version/*` + `?dataset=` | `/{dataset}/version/*` | Controller + 8 tests |
| HistoryController | `/version/history` + `?dataset=` | `/{dataset}/version/history` | Controller + 3 tests |
| BatchOpsController | `/version/batch*` + `?dataset=` | `/{dataset}/version/batch*` | Controller + 2 tests |
| DatasetController | `/version/datasets` | `/datasets` + `/{name}` | Controller + 2 tests |

**Total:** 8 controllers, ~30 integration tests

---

## Task 1: Refactor BranchController

### Current Implementation

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

```java
@RestController
@RequestMapping("/version/branches")
public class BranchController {

  @GetMapping
  public ResponseEntity<List<BranchInfo>> listBranches(
      @RequestParam(defaultValue = "default") String dataset) {
    // ...
  }

  @PostMapping
  public ResponseEntity<BranchInfo> createBranch(
      @RequestParam(defaultValue = "default") String dataset,
      @RequestBody CreateBranchRequest request) {
    // ...
  }

  @GetMapping("/{name}")
  public ResponseEntity<BranchInfo> getBranch(
      @RequestParam(defaultValue = "default") String dataset,
      @PathVariable String name) {
    // ...
  }

  @DeleteMapping("/{name}")
  public ResponseEntity<Void> deleteBranch(
      @RequestParam(defaultValue = "default") String dataset,
      @PathVariable String name) {
    // ...
  }
}
```

### Target Implementation

```java
@RestController
@RequestMapping("/{dataset}/version/branches")
public class BranchController {

  @GetMapping
  public ResponseEntity<List<BranchInfo>> listBranches(
      @PathVariable String dataset) {

    List<BranchInfo> branches = branchService.listBranches(dataset);

    // Add Content-Location header with canonical URL
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.branches(dataset)
    );

    return ResponseEntity.ok().headers(headers).body(branches);
  }

  @PostMapping
  public ResponseEntity<BranchInfo> createBranch(
      @PathVariable String dataset,
      @RequestBody CreateBranchRequest request) {

    BranchInfo branch = branchService.createBranch(dataset, request);

    // Add Content-Location with new branch URL
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.branch(dataset, branch.name())
    );

    return ResponseEntity.status(HttpStatus.CREATED)
        .headers(headers)
        .body(branch);
  }

  @GetMapping("/{name}")
  public ResponseEntity<BranchInfo> getBranch(
      @PathVariable String dataset,
      @PathVariable String name) {

    BranchInfo branch = branchService.getBranch(dataset, name);

    // Add Content-Location and Link headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(
        headers,
        VersionControlUrls.branch(dataset, name)
    );
    ResponseHeaderBuilder.addCommitLink(
        headers,
        dataset,
        branch.commitId()
    );

    return ResponseEntity.ok().headers(headers).body(branch);
  }

  @DeleteMapping("/{name}")
  public ResponseEntity<Void> deleteBranch(
      @PathVariable String dataset,
      @PathVariable String name) {

    branchService.deleteBranch(dataset, name);
    return ResponseEntity.noContent().build();
  }
}
```

### Update Integration Tests

**File:** `src/test/java/org/chucc/vcserver/controller/BranchControllerIT.java`

**Before:**
```java
@Test
void listBranches_shouldReturnAllBranches() {
  String url = "/version/branches?dataset=default";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

**After:**
```java
@Test
void listBranches_newPattern_shouldReturnAllBranches() {
  // New pattern: dataset in path
  String url = "/default/version/branches";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().get("Content-Location"))
      .containsExactly("/default/version/branches");
}

@Test
void listBranches_oldPattern_shouldStillWork() {
  // Old pattern: dataset as query param (deprecated)
  String url = "/version/branches?dataset=default";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  // Should redirect or include new URL in Content-Location
  assertThat(response.getHeaders().get("Content-Location"))
      .containsExactly("/default/version/branches");
}
```

---

## Task 2: Add Backward Compatibility Layer

Create a separate controller for old query-param pattern:

### New File: `BranchControllerLegacy.java`

**Location:** `src/main/java/org/chucc/vcserver/controller/legacy/BranchControllerLegacy.java`

```java
package org.chucc.vcserver.controller.legacy;

import org.chucc.vcserver.controller.BranchController;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy backward compatibility controller for branch operations.
 * <p>
 * Supports old query-parameter style routing for dataset parameter.
 * This controller is <b>deprecated</b> and will be removed in v2.0.
 * </p>
 * <p>
 * Clients should migrate to new path-based routing:
 * <ul>
 *   <li>Old: {@code GET /version/branches?dataset=mydata}</li>
 *   <li>New: {@code GET /mydata/version/branches}</li>
 * </ul>
 * </p>
 *
 * @deprecated Use {@link BranchController} with path-based dataset parameter
 */
@Deprecated(since = "1.0", forRemoval = true)
@RestController
@RequestMapping("/version/branches")
public class BranchControllerLegacy {

  private final BranchController delegate;

  public BranchControllerLegacy(BranchController delegate) {
    this.delegate = delegate;
  }

  /**
   * List branches (legacy query-param style).
   *
   * @param dataset Dataset name (query parameter, deprecated)
   * @return Branches list with Content-Location pointing to new URL
   * @deprecated Use {@code GET /{dataset}/version/branches}
   */
  @Deprecated
  @GetMapping
  public ResponseEntity<?> listBranchesLegacy(
      @RequestParam(defaultValue = "default") String dataset) {

    // Delegate to new controller
    ResponseEntity<?> response = delegate.listBranches(dataset);

    // Add headers pointing to new URL
    HttpHeaders headers = new HttpHeaders();
    headers.putAll(response.getHeaders());
    headers.set("Deprecation", "true");
    headers.set("Link", String.format("<%s>; rel=\"canonical\"",
        VersionControlUrls.branches(dataset)));

    return ResponseEntity.status(response.getStatusCode())
        .headers(headers)
        .body(response.getBody());
  }

  // Similar methods for other operations...
}
```

**Benefits:**
- ✅ Old clients continue working
- ✅ Clear deprecation signals (`Deprecation` header)
- ✅ Points clients to new URL (`Link` header, `Content-Location`)
- ✅ Easy to remove in v2.0 (delete package)

---

## Task 3: Refactor Remaining Controllers

Apply the same pattern to:

1. **CommitController** - `/{dataset}/version/commits`
2. **MergeController** - `/{dataset}/version/merge`
3. **AdvancedOpsController** - `/{dataset}/version/reset`, `/revert`, etc.
4. **HistoryController** - `/{dataset}/version/history`, `/diff`
5. **BatchOpsController** - `/{dataset}/version/batch*`
6. **DatasetController** - Move from `/version/datasets` to `/datasets`

**Note:** TagController already uses path parameter - only needs review.

---

## Task 4: Update All Integration Tests

For each controller, update tests:

```java
@Test
void operation_newPattern_shouldWork() {
  String url = "/default/version/branches/main";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().get("Content-Location")).isNotNull();
}

@Test
void operation_oldPattern_shouldStillWork() {
  String url = "/version/branches/main?dataset=default";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().get("Deprecation")).containsExactly("true");
  assertThat(response.getHeaders().get("Content-Location"))
      .containsExactly("/default/version/branches/main");
}
```

**Affected Test Files (~30 files):**
- BranchControllerIT
- CommitControllerIT
- MergeControllerIT
- ResetIntegrationTest
- RevertIntegrationTest
- CherryPickIntegrationTest
- RebaseIntegrationTest
- SquashIntegrationTest
- HistoryControllerIT
- DiffControllerIT
- BatchOperationsIT
- DatasetCreationIT
- ...and more

---

## Task 5: Update OpenAPI Documentation

Update `@RequestMapping` and parameter annotations:

```java
@Operation(
    summary = "List all branches",
    description = "Returns list of all branches in the dataset."
)
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Branch list returned"),
    @ApiResponse(responseCode = "404", description = "Dataset not found")
})
@GetMapping
public ResponseEntity<List<BranchInfo>> listBranches(
    @Parameter(
        description = "Dataset name",
        example = "default",
        required = true
    )
    @PathVariable String dataset) {
  // ...
}
```

---

## Testing Strategy

### Step 1: Unit Tests
```bash
mvn -q test -Dtest=BranchControllerTest
```

### Step 2: Integration Tests (New Pattern)
```bash
mvn -q test -Dtest=BranchControllerIT
```

### Step 3: Integration Tests (Legacy Pattern)
```bash
mvn -q test -Dtest=BranchControllerLegacyIT
```

### Step 4: Full Build
```bash
mvn -q clean install
```

---

## Quality Gates

```bash
# Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# All tests
mvn -q clean install

# Expected: BUILD SUCCESS
```

---

## Completion Checklist

- [ ] BranchController refactored to path-based dataset
- [ ] CommitController refactored
- [ ] MergeController refactored
- [ ] AdvancedOpsController refactored
- [ ] HistoryController refactored
- [ ] BatchOpsController refactored
- [ ] DatasetController refactored
- [ ] TagController reviewed (already correct)
- [ ] Legacy controllers created for backward compatibility
- [ ] All integration tests updated
- [ ] New pattern tests pass
- [ ] Legacy pattern tests pass
- [ ] OpenAPI documentation updated
- [ ] `Content-Location` headers added
- [ ] `Link` headers added for deprecated endpoints
- [ ] All quality gates pass

---

## Migration Impact

### For Clients

**Breaking Change?** No (v1.0 maintains backward compatibility)

**Old URLs (still work):**
```
GET /version/branches?dataset=mydata
GET /version/commits?dataset=mydata
```

**New URLs (recommended):**
```
GET /mydata/version/branches
GET /mydata/version/commits
```

**Response headers guide clients:**
```http
Deprecation: true
Link: </mydata/version/branches>; rel="canonical"
Content-Location: /mydata/version/branches
```

### For Developers

**Code changes required:**
- Update integration tests to use new URLs
- Update any hardcoded URLs in code
- Use `VersionControlUrls` utility for URL construction

---

## Next Session

**Session 3:** [Versioned SPARQL Endpoints](./session-3-versioned-sparql-endpoints.md)
- Add `/{dataset}/version/branches/{name}/sparql`
- Add `/{dataset}/version/commits/{id}/sparql`
- Add `/{dataset}/version/tags/{name}/sparql`

---

**Estimated Time:** 4-5 hours
**Priority:** High
