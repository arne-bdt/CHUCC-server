# Task: Implement Named Graph Support

**Status:** Not Started
**Priority:** High
**Estimated Time:** 1-2 sessions (6-10 hours)
**Dependencies:** Requires proper RDF Patch quad handling for named graphs

---

## Context

Currently, CHUCC Server only supports default graph operations. The Graph Store Protocol specification defines operations for both default graphs and named graphs, but named graph support is not yet implemented.

**Current State:**
- Only default graph operations work (PUT, POST, GET, DELETE, PATCH on `?default=true`)
- Named graph operations are not supported (`?graph=http://example.org/graph1`)
- RDFPatch format needs proper quad handling (not just triple handling)

**Problem:**
- Users cannot work with multiple graphs within a dataset
- Graph Store Protocol is incomplete
- Test `GraphStoreGetIT.java:112` is commented out waiting for this feature

**Goal:**
Implement full named graph support across all Graph Store Protocol operations.

---

## Design Decisions

### 1. RDFPatch Format for Quads

**Current:** RDFPatch operations use triples (subject, predicate, object)
```
A <s> <p> <o> .
D <s> <p> <o> .
```

**Needed:** RDFPatch operations should support quads (subject, predicate, object, graph)
```
A <s> <p> <o> <g> .
D <s> <p> <o> <g> .
```

**Decision:** Extend RDFPatch handling to support quad operations:
- Default graph: Use `urn:x-arq:DefaultGraph` as graph name
- Named graphs: Use actual graph URI

### 2. Graph Parameter Handling

**URL Parameters:**
- `?default=true` - Operations on default graph
- `?graph=<uri>` - Operations on named graph
- Both parameters cannot be present simultaneously (400 Bad Request)
- One parameter must be present (400 Bad Request)

### 3. DatasetGraph Storage

Apache Jena's `DatasetGraphInMemory` already supports named graphs natively:
- `getDefaultGraph()` - Returns default graph
- `getGraph(Node graphName)` - Returns named graph
- `addGraph(Node graphName, Graph graph)` - Adds named graph
- `removeGraph(Node graphName)` - Removes named graph

**Implementation:** Use existing DatasetGraph API, update RDFPatch serialization.

---

## Implementation Plan

### Step 1: Extend RDFPatch Quad Handling

**Files to modify:**
- `GraphDiffService.java` - Generate quad patches instead of triple patches
- `RdfParsingService.java` - Parse graph parameter
- Event classes - Support graph name field

**Changes:**
1. Update `GraphDiffService.computeDiff()` to include graph name in patches
2. Modify patch generation to output quads when graph is specified
3. Update projectors to apply quad patches correctly

### Step 2: Update Graph Store Controllers

**Files to modify:**
- `GraphStoreController.java` - Handle `?graph=<uri>` parameter
- All command classes - Add graph name field

**Changes:**
1. Add `@RequestParam(required = false) String graph` parameter
2. Validate that exactly one of `default` or `graph` is present
3. Pass graph name to command handlers
4. Update response headers to include graph information

### Step 3: Update Event Model

**Files to modify:**
- All Graph Store event classes (GraphUpdatedEvent, etc.)

**Changes:**
1. Add `graphName` field to events (default: `urn:x-arq:DefaultGraph`)
2. Update event constructors
3. Ensure backward compatibility with existing events

### Step 4: Update Projectors

**Files to modify:**
- `ReadModelProjector.java` - Apply patches to correct graph

**Changes:**
1. Extract graph name from events
2. Apply patches to named graph instead of default graph
3. Handle graph creation/deletion

### Step 5: Add Integration Tests

**Files to create/modify:**
- `GraphStoreGetIT.java` - Uncomment line 112 test, add more named graph GET tests
- `GraphStorePutIT.java` - Add named graph PUT tests
- `GraphStorePostIT.java` - Add named graph POST tests
- `GraphStoreDeleteIT.java` - Add named graph DELETE tests
- `GraphStorePatchIT.java` - Add named graph PATCH tests

**Test scenarios:**
1. PUT to named graph (create)
2. GET from named graph
3. POST to named graph (merge)
4. DELETE named graph
5. PATCH named graph
6. Operations on multiple named graphs in same dataset
7. Error handling (both `default` and `graph` parameters, neither parameter, etc.)

---

## Tests

### Unit Tests

**GraphDiffServiceTest:**
```java
@Test
void computeDiff_shouldGenerateQuadPatch_whenGraphNameProvided() {
  // Given
  Model before = ModelFactory.createDefaultModel();
  Model after = ModelFactory.createDefaultModel();
  after.add(s, p, o);
  String graphName = "http://example.org/graph1";

  // When
  String patch = graphDiffService.computeDiff(before, after, graphName);

  // Then
  assertThat(patch).contains("A <s> <p> <o> <http://example.org/graph1> .");
}
```

### Integration Tests

**GraphStoreGetIT:**
```java
@Test
void getGraph_shouldReturn200_whenNamedGraphExistsAtBranchHead() {
  // Given - Create named graph via PUT
  HttpHeaders headers = new HttpHeaders();
  headers.set("Content-Type", "text/turtle");
  headers.set("SPARQL-VC-Author", "Alice");
  headers.set("SPARQL-VC-Message", "Create named graph");

  restTemplate.exchange(
      "/data?graph=http://example.org/graph1&branch=main",
      HttpMethod.PUT,
      new HttpEntity<>(TURTLE_SIMPLE, headers),
      String.class
  );

  // When - Get named graph
  ResponseEntity<String> response = restTemplate.exchange(
      "/data?graph=http://example.org/graph1&branch=main",
      HttpMethod.GET,
      new HttpEntity<>(new HttpHeaders()),
      String.class
  );

  // Then
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody()).contains(":subject");
}
```

---

## Success Criteria

- [ ] All Graph Store operations (GET, PUT, POST, DELETE, PATCH) work with named graphs
- [ ] Named graph parameter validated correctly (400 if both `default` and `graph` present)
- [ ] RDFPatch format includes graph information for quad operations
- [ ] Default graph still works (backward compatibility)
- [ ] Multiple named graphs can exist in same dataset
- [ ] Events include graph name field
- [ ] Projectors apply patches to correct graph
- [ ] All commented-out named graph tests uncommented and passing
- [ ] Zero Checkstyle, SpotBugs, PMD violations
- [ ] All existing tests still pass (~911 tests)
- [ ] Documentation updated (API docs, OpenAPI spec)

---

## Rollback Plan

If issues arise during implementation:

1. **Revert commits** - Each step should be a separate commit
2. **Disable named graph parameter** - Return 501 Not Implemented for `?graph=` parameter
3. **Keep default graph working** - Ensure no regression in default graph operations

The feature can be developed incrementally:
- Step 1-2: Basic infrastructure (can be committed separately)
- Step 3-4: Event model and projectors (can be committed separately)
- Step 5: Tests and validation (final commit)

---

## API Changes

### Before (Default Graph Only)
```
GET /data?default=true&branch=main
PUT /data?default=true&branch=main
```

### After (Default + Named Graphs)
```
# Default graph
GET /data?default=true&branch=main
PUT /data?default=true&branch=main

# Named graph
GET /data?graph=http://example.org/graph1&branch=main
PUT /data?graph=http://example.org/graph1&branch=main

# Error cases
GET /data?default=true&graph=http://example.org/g1  # 400 Bad Request
GET /data?branch=main                               # 400 Bad Request
```

---

## Performance Considerations

**Memory Impact:**
- Named graphs stored in same `DatasetGraphInMemory` instance
- No additional memory overhead (Jena already supports this)

**Event Size:**
- Graph name adds ~20-50 bytes per event
- Negligible impact on Kafka storage

**Query Performance:**
- No impact (DatasetGraph already indexed by graph name)

---

## Documentation Updates

After implementation:

1. **OpenAPI Specification** (`src/main/resources/openapi.yml`)
   - Add `graph` parameter to all Graph Store operations
   - Document mutual exclusivity of `default` and `graph`
   - Add examples for named graph operations

2. **API Extensions Document** (`docs/api/api-extensions.md`)
   - Document named graph support
   - Provide usage examples

3. **Architecture Documentation**
   - Update component diagrams if needed
   - Document quad handling in event sourcing

---

## Future Enhancements

After completing basic named graph support:

1. **Graph Management API**
   - List all graphs in dataset
   - Get graph metadata (triple count, etc.)

2. **Cross-Graph Queries**
   - SPARQL queries spanning multiple named graphs
   - `FROM NAMED` clause support

3. **Graph-Level Versioning**
   - Track changes per named graph
   - Independent graph histories

---

## References

- [SPARQL 1.2 Graph Store Protocol](https://www.w3.org/TR/sparql12-protocol/)
- [Apache Jena DatasetGraph API](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/sparql/core/DatasetGraph.html)
- [RDFPatch Specification](https://afs.github.io/rdf-patch/)
- Related test: `GraphStoreGetIT.java:112`

---

## Notes

**Why This Is Important:**
- Named graphs are a core RDF feature for organizing data
- Graph Store Protocol specification requires named graph support
- Enables multi-tenant scenarios (one graph per tenant)
- Allows semantic partitioning of data

**Complexity Level:** High
- Touches event model, projectors, controllers, and serialization
- Requires careful testing to avoid breaking existing functionality
- RDFPatch quad format may need custom implementation
