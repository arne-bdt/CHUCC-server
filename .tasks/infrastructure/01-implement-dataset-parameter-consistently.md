# Task: Implement Dataset Parameter Consistently Across All Endpoints

**Status:** Not Started
**Priority:** Medium
**Estimated Time:** 2-3 hours
**Dependencies:** None

---

## Context

Currently, **dataset names are inconsistently handled** across controllers:

### Controllers with Hardcoded "default" (Need Updates)
1. **[BatchGraphsController.java:41](src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java#L41)** - `private static final String DATASET_NAME = "default";`
2. **[GraphStoreController.java:40](src/main/java/org/chucc/vcserver/controller/GraphStoreController.java#L40)** - `private static final String DATASET_NAME = "default";`
3. **[SparqlController.java:211](src/main/java/org/chucc/vcserver/controller/SparqlController.java#L211)** - `String datasetName = "default";`
4. **[SparqlController.java:389](src/main/java/org/chucc/vcserver/controller/SparqlController.java#L389)** - `final String datasetName = "default";`

### Controllers Already Using Dataset Parameter (Reference Pattern)
1. **AdvancedOpsController** - `@RequestParam(defaultValue = "default") String dataset`
2. **BranchController** - `@RequestParam(defaultValue = "default") String dataset` ✅ (Fixed - was in original task)
3. **CommitController** - `@RequestParam(defaultValue = "default") String dataset`
4. **RefsController** - `@RequestParam(value = "dataset", defaultValue = "default") String datasetName`

**Note:** BranchController was listed in the original cancelled task as needing updates, but has since been fixed.

---

## Problem

**API Inconsistency:**
- Some endpoints accept `?dataset=name` parameter, others don't
- Hardcoded values prevent multi-dataset support
- Users cannot specify which dataset to operate on for Graph Store Protocol and SPARQL endpoints
- Future multi-tenancy scenarios are blocked

**Examples:**
```bash
# ✅ Works - BranchController accepts dataset parameter
curl "http://localhost:3030/version/branches?dataset=mydata"

# ❌ Cannot specify - GraphStoreController uses hardcoded "default"
curl "http://localhost:3030/data?graph=http://example.org/g1&dataset=mydata"
# ^ dataset parameter is ignored

# ❌ Cannot specify - SparqlController uses hardcoded "default"
curl "http://localhost:3030/sparql?query=SELECT+*+WHERE+{+?s+?p+?o+}&dataset=mydata"
# ^ dataset parameter is ignored
```

**API Changes:**

### Before (Current State)
```bash
# Cannot specify custom dataset
GET /data?default=true&branch=main
GET /sparql?query=...&branch=main
POST /batch?branch=main
```

### After (Backward Compatible)
```bash
# Default dataset (unchanged behavior)
GET /data?default=true&branch=main
GET /data?dataset=default&default=true&branch=main  # Explicit

# Custom dataset (new capability)
GET /data?dataset=my-dataset&default=true&branch=main
GET /sparql?dataset=my-dataset&query=...&branch=main
POST /batch?dataset=my-dataset&branch=main
```

---

## Goal

Make all controllers consistently accept an optional `?dataset=name` query parameter with a default value of "default" for backward compatibility.

**Benefits:**
- ✅ Consistent API across all endpoints
- ✅ Removes hardcoded values
- ✅ Enables future multi-dataset support
- ✅ Foundation for multi-tenancy
- ✅ Backward compatible (default value = "default")

---

## Design Decisions

### 1. Implementation Approach

**Chosen Approach: Query Parameter**

Add `@RequestParam(defaultValue = "default") String dataset` to all affected endpoints.

**Why this approach:**
- ✅ Consistent with existing pattern in AdvancedOpsController, BranchController, etc.
- ✅ Easy to implement
- ✅ Backward compatible (default value prevents breaking changes)
- ✅ No client changes required (optional parameter)
- ✅ Works with both GET and POST requests

**Alternative approaches considered:**
- ❌ Header-based (`X-Dataset: name`) - Requires client changes, better for later
- ❌ Path parameter (`/datasets/{name}/sparql`) - Breaking API change
- ❌ Spring Request Scope bean - Over-engineering for current needs

### 2. Parameter Naming

**Chosen: `dataset`** (lowercase, singular)

**Consistency check:**
- ✅ AdvancedOpsController uses: `dataset`
- ✅ BranchController uses: `dataset`
- ✅ CommitController uses: `dataset`
- ⚠️ RefsController uses: `datasetName` (should we fix this too?)

**Decision:** Use `dataset` consistently (like the majority). Update RefsController in a separate micro-fix.

---

## Implementation Plan

### Step 1: Update BatchGraphsController (10 min)

**File:** [src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java](src/main/java/org/chucc/vcserver/controller/BatchGraphsController.java)

**Changes:**
1. Remove line 41: `private static final String DATASET_NAME = "default";`
2. Add parameter to `batchGraphs()` method:
   ```java
   // Before
   private static final String DATASET_NAME = "default";

   @PostMapping
   public ResponseEntity<BatchGraphsResponse> batchGraphs(
       @RequestBody BatchGraphsRequest request,
       @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author) {
     PutGraphsCommand command = new PutGraphsCommand(
         DATASET_NAME,
         operations,
         // ...
     );
   }

   // After
   @PostMapping
   public ResponseEntity<BatchGraphsResponse> batchGraphs(
       @Parameter(description = "Dataset name")
       @RequestParam(defaultValue = "default") String dataset,
       @RequestBody BatchGraphsRequest request,
       @RequestHeader(value = "X-Author", defaultValue = "anonymous") String author) {
     PutGraphsCommand command = new PutGraphsCommand(
         dataset,
         operations,
         // ...
     );
   }
   ```
3. Replace all uses of `DATASET_NAME` with `dataset` parameter

**Affected methods:**
- `batchGraphs()` - Main endpoint method

---

### Step 2: Update GraphStoreController (15 min)

**File:** [src/main/java/org/chucc/vcserver/controller/GraphStoreController.java](src/main/java/org/chucc/vcserver/controller/GraphStoreController.java)

**Changes:**
1. Remove line 40: `private static final String DATASET_NAME = "default";`
2. Add parameter to ALL Graph Store Protocol methods:
   ```java
   @GetMapping
   public ResponseEntity<?> getGraph(
       @RequestParam(defaultValue = "default") String dataset,
       @RequestParam(value = "graph", required = false) String graphUri,
       @RequestParam(value = "default", required = false) String defaultParam,
       @RequestHeader(value = "Accept", defaultValue = "text/turtle") String accept) {
   ```
3. Replace all uses of `DATASET_NAME` with `dataset` parameter

**Affected methods:**
- `getGraph()` - GET endpoint
- `putGraph()` - PUT endpoint
- `postGraph()` - POST endpoint
- `deleteGraph()` - DELETE endpoint
- `patchGraph()` - PATCH endpoint
- `headGraph()` - HEAD endpoint

---

### Step 3: Update SparqlController (20 min)

**File:** [src/main/java/org/chucc/vcserver/controller/SparqlController.java](src/main/java/org/chucc/vcserver/controller/SparqlController.java)

**Changes:**
1. Add parameter to `handleQuery()` method (line 211):
   ```java
   @GetMapping(produces = RESULT_TYPES)
   public ResponseEntity<?> handleQuery(
       @RequestParam(defaultValue = "default") String dataset,
       @RequestParam(value = "query", required = false) String queryString,
       @RequestParam(value = "default-graph-uri", required = false) List<String> defaultGraphUris,
       // ... other params
   ```
2. Replace line 211: `String datasetName = "default";` → use `dataset` parameter

3. Add parameter to `handleUpdate()` method (line 389):
   ```java
   @PostMapping(consumes = {"application/sparql-update", "text/plain"})
   public ResponseEntity<?> handleUpdate(
       @RequestParam(defaultValue = "default") String dataset,
       @RequestHeader(value = "Content-Type", required = false) String contentType,
       @RequestBody String updateString,
       // ... other params
   ```
4. Replace line 389: `final String datasetName = "default";` → use `dataset` parameter

**Affected methods:**
- `handleQuery()` - SPARQL Query endpoint (GET)
- `handleUpdate()` - SPARQL Update endpoint (POST)

---

### Step 4: Update Tests (30 min)

**Test Files to Review:**

1. **[BatchGraphsControllerTest.java](src/test/java/org/chucc/vcserver/controller/BatchGraphsControllerTest.java)**
   - Add test: `batchGraphs_withCustomDataset_shouldUseProvidedDataset()`
   - Verify default behavior still works

2. **[GraphStoreControllerTest.java](src/test/java/org/chucc/vcserver/controller/GraphStoreControllerTest.java)** (if exists)
   - Add tests for custom dataset parameter on each GSP operation
   - Verify default behavior still works

3. **[SparqlControllerTest.java](src/test/java/org/chucc/vcserver/controller/SparqlControllerTest.java)** (if exists)
   - Add test: `handleQuery_withCustomDataset_shouldUseProvidedDataset()`
   - Add test: `handleUpdate_withCustomDataset_shouldUseProvidedDataset()`
   - Verify default behavior still works

4. **Integration Tests:**
   - Search for any integration tests that might need updates
   - Verify backward compatibility (no dataset parameter should still work)

**Test Pattern:**

**Unit Test Pattern (with MockMvc):**
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

**Integration Test Pattern (with TestRestTemplate):**
```java
@Test
void operation_withCustomDataset_shouldUseProvidedDataset() {
  // Arrange
  String customDataset = "test-dataset";

  // Act
  ResponseEntity<?> response = restTemplate.exchange(
    "/endpoint?dataset=" + customDataset + "&...",
    HttpMethod.GET,
    entity,
    String.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  // Verify command/event contains customDataset
}

@Test
void operation_withoutDatasetParameter_shouldUseDefaultDataset() {
  // Act - No dataset parameter
  ResponseEntity<?> response = restTemplate.exchange(
    "/endpoint?...",
    HttpMethod.GET,
    entity,
    String.class
  );

  // Assert - Should work with "default" dataset
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
}
```

---

### Step 5: Update OpenAPI Documentation (20 min)

**File:** [api/openapi.yaml](api/openapi.yaml)

**Changes:**

Add `dataset` parameter to all affected endpoints:

```yaml
parameters:
  - name: dataset
    in: query
    description: |
      The dataset to operate on. Defaults to "default".
      This parameter enables multi-dataset support.
    required: false
    schema:
      type: string
      default: default
    example: my-dataset
```

**Affected Endpoints:**
1. `/data` (all GSP operations: GET, PUT, POST, DELETE, PATCH, HEAD)
2. `/sparql` (GET - query, POST - update)
3. `/batch` (POST - batch operations)

**Documentation Updates:**
- Add examples showing usage with custom dataset
- Document backward compatibility (parameter is optional)
- Update description to mention multi-dataset support

---

### Step 6: Build and Quality Checks (30 min)

**Phase 1: Static Analysis**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check
```

**Expected Issues:**
- None (straightforward parameter addition)

**Phase 2: Run Affected Tests**
```bash
mvn -q test -Dtest=BatchGraphsControllerTest,GraphStoreControllerTest,SparqlControllerTest
```

**Phase 3: Full Build**
```bash
mvn -q clean install
```

**Success Criteria:**
- ✅ All ~913+ tests pass
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations

---

## Testing Strategy

### Unit Tests

For each updated controller, verify:

1. **Default Behavior (Backward Compatibility)**
   - Request without `?dataset=` parameter works
   - Uses "default" dataset internally
   - Existing clients not affected

2. **Custom Dataset Parameter**
   - Request with `?dataset=mydata` uses "mydata"
   - Parameter is passed to command handlers
   - Events contain correct dataset name

3. **Edge Cases**
   - Empty dataset parameter: `?dataset=`
   - Whitespace dataset: `?dataset= `
   - Special characters: `?dataset=my-dataset-123`

### Integration Tests

Verify end-to-end flow:

1. **Graph Store Protocol with Custom Dataset**
   ```bash
   curl "http://localhost:3030/data?dataset=test-ds&graph=http://ex.org/g1"
   ```

2. **SPARQL Query with Custom Dataset**
   ```bash
   curl "http://localhost:3030/sparql?dataset=test-ds&query=SELECT+*+WHERE+{+?s+?p+?o+}"
   ```

3. **Batch Operations with Custom Dataset**
   ```bash
   curl -X POST "http://localhost:3030/batch?dataset=test-ds" -d '{...}'
   ```

### Manual Testing

After implementation, test via curl/Postman:

```bash
# Test 1: Graph Store Protocol with custom dataset
curl -X PUT "http://localhost:3030/data?dataset=my-dataset&default=true" \
  -H "Content-Type: text/turtle" \
  -d "<http://example.org/s> <http://example.org/p> <http://example.org/o> ."

# Test 2: Query with custom dataset
curl "http://localhost:3030/sparql?dataset=my-dataset&query=SELECT+*+WHERE+{+?s+?p+?o+}"

# Test 3: Update with custom dataset
curl -X POST "http://localhost:3030/sparql?dataset=my-dataset" \
  -H "Content-Type: application/sparql-update" \
  -d "INSERT DATA { <http://ex.org/s> <http://ex.org/p> 'test' . }"

# Test 4: Backward compatibility (no dataset parameter)
curl "http://localhost:3030/sparql?query=SELECT+*+WHERE+{+?s+?p+?o+}"
```

---

## Success Criteria

- [ ] BatchGraphsController accepts `dataset` parameter
- [ ] GraphStoreController accepts `dataset` parameter (all 6 GSP operations)
- [ ] SparqlController accepts `dataset` parameter (query + update)
- [ ] All hardcoded `"default"` values removed from these controllers
- [ ] Unit tests added for custom dataset parameter
- [ ] Unit tests verify backward compatibility (no parameter = "default")
- [ ] Integration tests pass
- [ ] OpenAPI documentation updated
- [ ] All 913+ tests pass
- [ ] Zero quality violations (Checkstyle, SpotBugs, PMD)
- [ ] Backward compatible (existing clients work without changes)

---

## Backward Compatibility

**Critical:** This change MUST be backward compatible.

**How we ensure compatibility:**
- ✅ Parameter has `defaultValue = "default"`
- ✅ Parameter is NOT marked as `required = true`
- ✅ Existing requests without `?dataset=` work exactly as before
- ✅ No changes to response format
- ✅ No changes to command/event structure (dataset field already exists)

**Migration Path:**
- No migration needed
- Clients can start using `?dataset=` when they need it
- Existing clients continue working without changes

---

## Rollback Plan

If issues arise after implementation:

**1. Revert Commits**
- Each controller should be a separate commit for granular rollback
- Use `git revert <commit-hash>` to undo specific changes

**2. Feature Flag (Optional)**
If needed, add a feature flag to disable the new parameter handling:
```yaml
# application.yml
vc:
  multi-dataset:
    enabled: false  # Disable custom dataset parameter handling
```

**3. Backward Compatibility Guarantee**
- No rollback needed for API clients
- Default value ensures existing behavior unchanged
- Zero breaking changes

**Rollback Commands:**
```bash
# Revert individual controller changes
git revert <commit-hash-for-sparql-controller>
git revert <commit-hash-for-graphstore-controller>
git revert <commit-hash-for-batch-controller>

# Or revert all at once (if single commit)
git revert <commit-hash>
```

---

## Future Enhancements

After this task is complete, future work could include:

1. **Full Multi-Dataset Support**
   - Update DatasetService to handle multiple dataset names
   - Update repositories to support dataset-scoped queries
   - Add dataset creation/deletion endpoints

2. **Dataset Management API**
   - `POST /datasets/{name}` - Create dataset
   - `GET /datasets` - List datasets
   - `DELETE /datasets/{name}` - Delete dataset

3. **Multi-Tenancy**
   - Header-based dataset selection (`X-Dataset: name`)
   - Authentication and authorization per dataset
   - Dataset isolation and access control

4. **Phase 2: Request Scope Bean** (Future Architecture)

   After basic dataset parameter implementation, consider adding a request-scoped context:

   **Create RequestContext Bean:**
   ```java
   @Component
   @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
   public class RequestContext {
     private String dataset;
     private String user;
     private String tenant;  // Multi-tenancy support

     // Getters/setters
     public String getDataset() { return dataset; }
     public void setDataset(String dataset) { this.dataset = dataset; }
     public String getUser() { return user; }
     public void setUser(String user) { this.user = user; }
   }
   ```

   **Populate via Interceptor:**
   ```java
   @Component
   public class RequestContextInterceptor implements HandlerInterceptor {

     @Autowired
     private RequestContext requestContext;

     @Override
     public boolean preHandle(HttpServletRequest request,
                               HttpServletResponse response,
                               Object handler) {
       // Extract dataset from request parameter
       String dataset = request.getParameter("dataset");
       if (dataset == null) dataset = "default";
       requestContext.setDataset(dataset);

       // Extract user from auth header (future)
       String user = extractUserFromAuthHeader(request);
       requestContext.setUser(user);

       return true;
     }

     private String extractUserFromAuthHeader(HttpServletRequest request) {
       String authHeader = request.getHeader("Authorization");
       // Parse JWT or Basic Auth
       return authHeader != null ? parseUser(authHeader) : "anonymous";
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
       String user = requestContext.getUser();
       // Use without passing through all layers
     }
   }
   ```

   **Benefits:**
   - ✅ No need to pass dataset through all method signatures
   - ✅ Supports multi-tenancy (tenant-specific datasets)
   - ✅ Can include authentication context
   - ✅ Cleaner service method signatures
   - ✅ Available throughout entire request lifecycle

---

## References

- Existing pattern: [AdvancedOpsController.java:136](src/main/java/org/chucc/vcserver/controller/AdvancedOpsController.java#L136)
- Existing pattern: [BranchController.java:165](src/main/java/org/chucc/vcserver/controller/BranchController.java#L165)
- Existing pattern: [CommitController.java:126](src/main/java/org/chucc/vcserver/controller/CommitController.java#L126)
- OpenAPI spec: [api/openapi.yaml](api/openapi.yaml)
- Development guide: [.claude/CLAUDE.md](.claude/CLAUDE.md)

---

## Notes

**Complexity Level:** Low-Medium
- Straightforward parameter addition
- Multiple files to update (3 controllers, ~8 methods)
- Requires comprehensive testing
- Documentation updates needed

**Estimated Time Breakdown:**
- BatchGraphsController: 10 min
- GraphStoreController: 15 min (6 methods)
- SparqlController: 20 min (2 methods)
- Tests: 30 min
- OpenAPI docs: 20 min
- Build & quality checks: 30 min
- **Total: ~2 hours**

**Risk Level:** Low
- Backward compatible change
- No breaking changes
- Simple parameter addition
- Well-established pattern in codebase

**This task is safe to implement** and provides good foundation for future multi-dataset/multi-tenancy features.
