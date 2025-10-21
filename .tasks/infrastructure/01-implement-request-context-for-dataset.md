# Task: Implement Request Context for Dataset Name

**Status:** Not Started
**Priority:** Low
**Estimated Time:** 1 session (2-3 hours)
**Dependencies:** None

---

## Context

Currently, the **dataset name is hardcoded as "default"** throughout the codebase:
- `GraphStoreController.java:40` - `private static final String DATASET_NAME = "default";`
- `BatchGraphsController.java:41` - `private static final String DATASET_NAME = "default";`
- `SparqlController.java:211` - `String datasetName = "default";`
- `BranchController.java:167` - `"default"  // TODO: Get from request context`

**Problem:**
- Hardcoded value prevents multi-dataset support
- Cannot serve different datasets for different requests
- Future multi-tenancy scenarios blocked
- Inconsistent pattern (some controllers use parameters, some hardcode)

**Goal:**
Implement a **RequestContext** mechanism to provide dataset name (and potentially other request-scoped data) to controllers and services.

---

## Current State Analysis

### Controllers with Hardcoded "default"

1. **GraphStoreController** - Hardcoded `DATASET_NAME = "default"`
2. **BatchGraphsController** - Hardcoded `DATASET_NAME = "default"`
3. **SparqlController** - Hardcoded `datasetName = "default"`
4. **BranchController** - Hardcoded `"default"` with TODO comment

### Controllers with Dataset as Parameter

1. **CommitController** - `@RequestParam(value = "dataset", defaultValue = "default")`
2. **DatasetController** - `@PathVariable String name`
3. **TagController** - `@PathVariable String dataset`
4. **RefsController** - `@RequestParam(value = "dataset", defaultValue = "default")`
5. **AdvancedOpsController** - `@RequestParam(value = "dataset", defaultValue = "default")`

**Observation:** API is inconsistent. Some endpoints expose dataset parameter, others don't.

---

## Design Decisions

### 1. Request Context Approach

**Option 1: Query Parameter** (Recommended for now)
- Add `?dataset=<name>` to all endpoints
- Consistent with existing pattern in CommitController, etc.
- Easy to implement
- Backward compatible (default value = "default")

**Option 2: Header-Based** (Future multi-tenancy)
```
X-Dataset: my-dataset
```
- Better for multi-tenancy
- Cleaner URLs
- Requires client changes

**Option 3: Spring Request Scope Bean**
- `@Component @Scope("request")` for RequestContext
- Populated by interceptor
- Available throughout request lifecycle
- More complex infrastructure

**Option 4: Path Parameter** (Not recommended)
```
/datasets/{dataset}/data
/datasets/{dataset}/sparql
```
- Breaking change to existing API
- Better REST semantics
- Major refactoring required

**Decision:**
- **Phase 1 (This Task):** Option 1 - Query parameter `?dataset=name`
- **Phase 2 (Future):** Option 3 - Spring Request Scope for multi-tenancy

### 2. Scope of Changes

**Affected Controllers:**
1. GraphStoreController - Remove hardcoded constant
2. BatchGraphsController - Remove hardcoded constant
3. SparqlController - Remove hardcoded variable
4. BranchController - Remove hardcoded "default"

**API Changes:**
- Add `@RequestParam(value = "dataset", defaultValue = "default") String dataset` to affected endpoints
- Update OpenAPI documentation
- Ensure backward compatibility (default value prevents breaking changes)

---

## Implementation Plan

### Step 1: Update GraphStoreController

**File:** `src/main/java/org/chucc/vcserver/controller/GraphStoreController.java`

**Changes:**
1. Remove `private static final String DATASET_NAME = "default";`
2. Add `@RequestParam(value = "dataset", defaultValue = "default") String dataset` to all methods
3. Replace `DATASET_NAME` references with `dataset` parameter
4. Update Javadoc

**Example:**
```java
// Before
@GetMapping
public ResponseEntity<String> getGraph(
    @RequestParam(required = false) String graph,
    @RequestParam(required = false) String branch
) {
  baseCommitId = selectorResolutionService.resolve(DATASET_NAME, effectiveBranch, null, null);
  // ...
}

// After
@GetMapping
public ResponseEntity<String> getGraph(
    @Parameter(description = "Dataset name")
    @RequestParam(value = "dataset", defaultValue = "default") String dataset,
    @RequestParam(required = false) String graph,
    @RequestParam(required = false) String branch
) {
  baseCommitId = selectorResolutionService.resolve(dataset, effectiveBranch, null, null);
  // ...
}
```

### Step 2: Update BatchGraphsController

**File:** `src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java`

**Changes:**
1. Remove `private static final String DATASET_NAME = "default";`
2. Add dataset parameter to affected methods
3. Update command creation to use parameter

**Example:**
```java
// Before
private static final String DATASET_NAME = "default";

@PostMapping
public ResponseEntity<BatchGraphsResponse> putGraphs(...) {
  PutGraphsCommand command = new PutGraphsCommand(
      DATASET_NAME,
      operations,
      // ...
  );
}

// After
@PostMapping
public ResponseEntity<BatchGraphsResponse> putGraphs(
    @Parameter(description = "Dataset name")
    @RequestParam(value = "dataset", defaultValue = "default") String dataset,
    // ... other params
) {
  PutGraphsCommand command = new PutGraphsCommand(
      dataset,
      operations,
      // ...
  );
}
```

### Step 3: Update SparqlController

**File:** `src/main/java/org/chucc/vcserver/controller/SparqlController.java`

**Changes:**
1. Remove `String datasetName = "default";` hardcoding
2. Add dataset parameter to `query()` and `update()` methods
3. Update method signatures

**Example:**
```java
// Before
@GetMapping
public ResponseEntity<String> query(...) {
  String datasetName = "default";
  CommitId targetCommit = selectorResolutionService.resolve(
      datasetName, branch, commit, asOf);
  // ...
}

// After
@GetMapping
public ResponseEntity<String> query(
    @Parameter(description = "Dataset name")
    @RequestParam(value = "dataset", defaultValue = "default") String dataset,
    // ... other params
) {
  CommitId targetCommit = selectorResolutionService.resolve(
      dataset, branch, commit, asOf);
  // ...
}
```

### Step 4: Update BranchController

**File:** `src/main/java/org/chucc/vcserver/controller/BranchController.java`

**Changes:**
1. Add dataset parameter to `deleteBranch()` method
2. Remove TODO comment
3. Update command creation

**Example:**
```java
// Before
@DeleteMapping("/{name}")
public ResponseEntity<Void> deleteBranch(
    @PathVariable String name,
    @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author
) {
  DeleteBranchCommand command = new DeleteBranchCommand(
      "default",  // TODO: Get from request context
      name,
      author
  );
  // ...
}

// After
@DeleteMapping("/{name}")
public ResponseEntity<Void> deleteBranch(
    @Parameter(description = "Dataset name")
    @RequestParam(value = "dataset", defaultValue = "default") String dataset,
    @PathVariable String name,
    @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author
) {
  DeleteBranchCommand command = new DeleteBranchCommand(
      dataset,
      name,
      author
  );
  // ...
}
```

### Step 5: Update Integration Tests

**Files to modify:**
- Tests that call affected endpoints should verify dataset parameter behavior

**New tests to add:**
```java
@Test
void getGraph_shouldUseDefaultDataset_whenDatasetParameterOmitted() {
  // When
  ResponseEntity<String> response = restTemplate.exchange(
      "/data?default=true&branch=main",  // No dataset parameter
      HttpMethod.GET,
      // ...
  );

  // Then
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  // Verify dataset "default" was used
}

@Test
void getGraph_shouldUseCustomDataset_whenDatasetParameterProvided() {
  // When
  ResponseEntity<String> response = restTemplate.exchange(
      "/data?dataset=my-dataset&default=true&branch=main",
      HttpMethod.GET,
      // ...
  );

  // Then - Should use "my-dataset" instead of "default"
  // (This test may fail if multi-dataset not fully implemented)
  // For now, verify parameter is accepted
}
```

### Step 6: Update OpenAPI Documentation

**File:** `src/main/resources/openapi.yml` (if exists) or controller annotations

**Changes:**
1. Add `dataset` query parameter to all affected endpoints
2. Document default value ("default")
3. Add examples showing usage

**Example:**
```yaml
/data:
  get:
    parameters:
      - name: dataset
        in: query
        description: Dataset name
        required: false
        schema:
          type: string
          default: "default"
        example: "my-dataset"
      - name: graph
        in: query
        # ...
```

---

## Tests

### Unit Tests

**ControllerTest pattern:**
```java
@WebMvcTest(GraphStoreController.class)
class GraphStoreControllerTest {

  @MockBean
  private DatasetService datasetService;

  @MockBean
  private SelectorResolutionService selectorResolutionService;

  @Autowired
  private MockMvc mockMvc;

  @Test
  void getGraph_shouldUseDefaultDataset_whenParameterOmitted() throws Exception {
    // When
    mockMvc.perform(get("/data")
        .param("default", "true")
        .param("branch", "main"))
        .andExpect(status().isOk());

    // Then
    verify(selectorResolutionService).resolve(
        eq("default"),  // Should use default
        eq("main"),
        isNull(),
        isNull()
    );
  }

  @Test
  void getGraph_shouldUseCustomDataset_whenParameterProvided() throws Exception {
    // When
    mockMvc.perform(get("/data")
        .param("dataset", "my-dataset")
        .param("default", "true")
        .param("branch", "main"))
        .andExpect(status().isOk());

    // Then
    verify(selectorResolutionService).resolve(
        eq("my-dataset"),  // Should use custom dataset
        eq("main"),
        isNull(),
        isNull()
    );
  }
}
```

### Integration Tests

**Update existing tests:**
- Ensure tests continue to work (backward compatibility)
- Add tests with explicit `?dataset=` parameter

**New tests:**
- Verify default value works
- Verify custom dataset parameter accepted (may not work fully until multi-dataset implemented)

---

## Success Criteria

- [ ] All hardcoded "default" dataset names removed from controllers
- [ ] Dataset parameter added to all affected endpoints
- [ ] Default value ("default") ensures backward compatibility
- [ ] BranchController TODO comment removed
- [ ] API remains backward compatible (existing clients work without changes)
- [ ] OpenAPI documentation updated
- [ ] Unit tests verify default and custom dataset behavior
- [ ] Integration tests pass
- [ ] Zero Checkstyle, SpotBugs, PMD violations
- [ ] All existing tests still pass (~911 tests)

---

## Rollback Plan

If issues arise:

1. **Revert commits** - Each controller should be a separate commit
2. **Feature flag** (if needed):
   ```yaml
   vc:
     multi-dataset:
       enabled: false  # Disable if problems occur
   ```
3. **No breaking changes** - Default value ensures backward compatibility

---

## Backward Compatibility

**Guaranteed backward compatibility:**
- Existing API calls without `?dataset=` parameter continue to work
- Default value `"default"` maintains current behavior
- No client changes required

**Example:**
```bash
# Before and After - Both work identically
curl "http://localhost:8080/data?default=true&branch=main"

# New capability - Custom dataset
curl "http://localhost:8080/data?dataset=my-dataset&default=true&branch=main"
```

---

## API Changes

### Before

```
GET /data?default=true&branch=main
GET /sparql?query=...&branch=main
POST /batch?branch=main
DELETE /version/branches/feature-x
```

### After (Backward Compatible)

```
# Default dataset (unchanged behavior)
GET /data?default=true&branch=main
GET /data?dataset=default&default=true&branch=main  # Explicit

# Custom dataset (new capability)
GET /data?dataset=my-dataset&default=true&branch=main
GET /sparql?dataset=my-dataset&query=...&branch=main
POST /batch?dataset=my-dataset&branch=main
DELETE /version/branches/feature-x?dataset=my-dataset
```

---

## Future Enhancements

### Phase 2: Request-Scoped Context (Future)

After basic dataset parameter implementation:

**Create RequestContext:**
```java
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
  private String dataset;
  private String user;
  private String tenant;  // Multi-tenancy

  // Getters/setters
}
```

**Populate via Interceptor:**
```java
@Component
public class RequestContextInterceptor implements HandlerInterceptor {

  @Autowired
  private RequestContext requestContext;

  @Override
  public boolean preHandle(HttpServletRequest request, ...) {
    String dataset = request.getParameter("dataset");
    if (dataset == null) dataset = "default";
    requestContext.setDataset(dataset);

    // Extract user from auth header
    String user = extractUser(request);
    requestContext.setUser(user);

    return true;
  }
}
```

**Use in Services:**
```java
@Service
public class SomeService {

  @Autowired
  private RequestContext requestContext;

  public void doSomething() {
    String dataset = requestContext.getDataset();
    // Use dataset without passing as parameter
  }
}
```

**Benefits:**
- No need to pass dataset through all layers
- Supports multi-tenancy (tenant-specific datasets)
- Can add authentication context
- Cleaner service method signatures

---

## Documentation Updates

After implementation:

1. **API Documentation** (`docs/api/openapi-guide.md`)
   - Document new `dataset` parameter on all endpoints
   - Provide usage examples
   - Explain default behavior

2. **Development Guidelines** (`.claude/CLAUDE.md`)
   - Update example API calls to show dataset parameter
   - Document when to use default vs custom dataset

3. **Architecture Documentation**
   - Document multi-dataset support (once fully implemented)
   - Explain dataset isolation

---

## Performance Considerations

**Impact:**
- Negligible - Just passing an extra parameter
- No performance degradation
- Enables future optimizations (per-dataset caching, etc.)

**Memory:**
- No additional memory usage
- Dataset name is already stored in commands/events

---

## References

- TODO comment: `BranchController.java:167`
- Related controllers: GraphStoreController, BatchGraphsController, SparqlController
- Existing pattern: CommitController, AdvancedOpsController (already use dataset parameter)

---

## Notes

**Why This Is Important:**
- Removes hardcoded values
- Enables future multi-dataset support
- Improves API consistency
- Provides foundation for multi-tenancy

**Complexity Level:** Low-Medium
- Straightforward parameter addition
- Many files to update (4 controllers)
- Requires comprehensive testing
- Documentation updates needed

**Scope Clarification:**
This task focuses on **accepting the dataset parameter**, not implementing full multi-dataset functionality (which requires repository/service changes). The goal is to **remove hardcoded values** and **prepare infrastructure** for future multi-dataset support.
