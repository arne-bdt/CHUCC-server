# Task 03: GraphStoreController Skeleton and Discovery

## Objective
Create the GraphStoreController with OPTIONS endpoint for capability discovery and skeleton methods for all GSP operations.

## Background
The Graph Store Protocol uses `/data` endpoint with HTTP methods GET, PUT, POST, DELETE, HEAD, and PATCH (extension). OPTIONS provides discovery.

## Tasks

### 1. Create GraphStoreController
Create `src/main/java/org/chucc/vcserver/controller/GraphStoreController.java`:
- `@RestController`
- `@RequestMapping("/data")`
- Inject dependencies: VersionControlProperties, GraphParameterValidator, SelectorValidator

### 2. Implement OPTIONS Endpoint
```java
@RequestMapping(method = RequestMethod.OPTIONS)
public ResponseEntity<Void> options()
```
Return headers:
- `Allow: GET, PUT, POST, DELETE, HEAD, PATCH, OPTIONS`
- `Accept-Patch: text/rdf-patch`
- `SPARQL-Version-Control: 1.0`
- `Link: </version>; rel="version-control"`

### 3. Create Skeleton Methods
Add methods with 501 Not Implemented responses:
- `@GetMapping` - getGraph
- `@PutMapping` - putGraph
- `@PostMapping` - postGraph
- `@DeleteMapping` - deleteGraph
- `@RequestMapping(method = RequestMethod.HEAD)` - headGraph
- `@PatchMapping` - patchGraph

Each method should:
- Accept parameters: `graph`, `default`, `branch`, `commit`, `asOf`
- Accept headers: `SPARQL-VC-Author`, `SPARQL-VC-Message`, `If-Match`
- Validate parameters using GraphParameterValidator and SelectorValidator
- Return 501 with problem+json body

### 4. Add OpenAPI Annotations
- Use `@Tag`, `@Operation`, `@ApiResponse` annotations
- Document all parameters and response codes per spec
- Include ETag and Location headers in documentation

### 5. Write Integration Test for OPTIONS
Create `src/test/java/org/chucc/vcserver/integration/GraphStoreProtocolDiscoveryIT.java`:
- Test OPTIONS returns correct headers
- Test Allow header includes all methods
- Test Accept-Patch advertises text/rdf-patch
- Test Link header points to /version

## Acceptance Criteria
- [ ] GraphStoreController created with all method signatures
- [ ] OPTIONS endpoint returns correct discovery headers
- [ ] All methods validate parameters and return 501
- [ ] Integration test for OPTIONS passes
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 02 (validation utilities)

## Estimated Complexity
Low-Medium (3-4 hours)
