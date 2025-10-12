# Task 3: Migrate GraphDiffService to Graph API

**Status**: Not Started
**Priority**: High (High impact - used by all command handlers)
**Estimated Effort**: 2-3 hours
**Depends On**: Task 1 (RdfParsingService), Task 2 (GraphSerializationService)

## Goal

Migrate `GraphDiffService` from using `Model` to using `Graph` for improved performance. This service is critical for all graph modification operations.

## Context

`GraphDiffService` computes RDF diffs for:
- PUT operations (replace entire graph)
- POST operations (additive merge)
- DELETE operations (remove entire graph)

This service is used by **all command handlers** and is a critical path for performance.

**Current Pattern**:
```java
public RDFPatch computePutDiff(Model oldGraph, Model newGraph, String graphIri) {
    for (Statement stmt : oldGraph.listStatements().toList()) {
        if (!newGraph.contains(stmt)) {
            collector.delete(graphNode,
                stmt.getSubject().asNode(),
                stmt.getPredicate().asNode(),
                stmt.getObject().asNode());
        }
    }
}
```

**Target Pattern**:
```java
public RDFPatch computePutDiff(Graph oldGraph, Graph newGraph, String graphIri) {
    oldGraph.find().forEachRemaining(triple -> {
        if (!newGraph.contains(triple)) {
            collector.delete(graphNode,
                triple.getSubject(),
                triple.getPredicate(),
                triple.getObject());
        }
    });
}
```

## Performance Benefits

**Expected improvements**:
- 20-30% faster for typical graphs
- Fewer object allocations (no Statement wrappers)
- Direct triple iteration
- More efficient containment checks

## Changes Required

### 1. Update GraphDiffService

**File**: `src/main/java/org/chucc/vcserver/service/GraphDiffService.java`

**Method 1: computePutDiff**
```java
// Before
public RDFPatch computePutDiff(Model oldGraph, Model newGraph, String graphIri) {
    // ...
    for (Statement stmt : oldGraph.listStatements().toList()) {
        if (!newGraph.contains(stmt)) {
            collector.delete(
                graphNode,
                stmt.getSubject().asNode(),
                stmt.getPredicate().asNode(),
                stmt.getObject().asNode()
            );
        }
    }

    for (Statement stmt : newGraph.listStatements().toList()) {
        if (!oldGraph.contains(stmt)) {
            collector.add(
                graphNode,
                stmt.getSubject().asNode(),
                stmt.getPredicate().asNode(),
                stmt.getObject().asNode()
            );
        }
    }
}

// After
public RDFPatch computePutDiff(Graph oldGraph, Graph newGraph, String graphIri) {
    // ...
    // Delete triples in old but not in new
    oldGraph.find().forEachRemaining(triple -> {
        if (!newGraph.contains(triple)) {
            collector.delete(
                graphNode,
                triple.getSubject(),
                triple.getPredicate(),
                triple.getObject()
            );
        }
    });

    // Add triples in new but not in old
    newGraph.find().forEachRemaining(triple -> {
        if (!oldGraph.contains(triple)) {
            collector.add(
                graphNode,
                triple.getSubject(),
                triple.getPredicate(),
                triple.getObject()
            );
        }
    });
}
```

**Method 2: computePostDiff**
```java
public RDFPatch computePostDiff(Graph currentGraph, Graph newContent, String graphIri) {
    // ... same pattern as computePutDiff
    newContent.find().forEachRemaining(triple -> {
        if (!currentGraph.contains(triple)) {
            collector.add(graphNode,
                triple.getSubject(),
                triple.getPredicate(),
                triple.getObject());
        }
    });
}
```

**Method 3: computeDeleteDiff**
```java
public RDFPatch computeDeleteDiff(Graph currentGraph, String graphIri) {
    // ...
    currentGraph.find().forEachRemaining(triple -> {
        collector.delete(graphNode,
            triple.getSubject(),
            triple.getPredicate(),
            triple.getObject());
    });
}
```

### 2. Update GraphDiffServiceTest

**File**: `src/test/java/org/chucc/vcserver/service/GraphDiffServiceTest.java`

This file has **20+ test methods** to update.

**Pattern to update**:
```java
// Before
Model oldGraph = ModelFactory.createDefaultModel();
oldGraph.add(
    ResourceFactory.createResource("http://example.org/s1"),
    ResourceFactory.createProperty("http://example.org/p1"),
    "value"
);

// After
Graph oldGraph = GraphFactory.createDefaultGraph();
oldGraph.add(
    NodeFactory.createURI("http://example.org/s1"),
    NodeFactory.createURI("http://example.org/p1"),
    NodeFactory.createLiteral("value")
);
```

**Alternative approach** (using Triple):
```java
Graph oldGraph = GraphFactory.createDefaultGraph();
oldGraph.add(new Triple(
    NodeFactory.createURI("http://example.org/s1"),
    NodeFactory.createURI("http://example.org/p1"),
    NodeFactory.createLiteral("value")
));
```

### 3. Update All Command Handlers

**Files to update**:
- `PutGraphCommandHandler.java`
- `PostGraphCommandHandler.java`
- `DeleteGraphCommandHandler.java`
- `PatchGraphCommandHandler.java`
- `BatchGraphsCommandHandler.java`

**Pattern**:
```java
// Before
Model oldGraph = ...;
Model newGraph = ...;
RDFPatch patch = graphDiffService.computePutDiff(oldGraph, newGraph, graphIri);

// After
Graph oldGraph = ...;
Graph newGraph = ...;
RDFPatch patch = graphDiffService.computePutDiff(oldGraph, newGraph, graphIri);
```

### 4. Update Command Handler Tests

Update tests for all command handlers to use Graph.

## Implementation Steps

### Step 1: Preparation
- [ ] Read `GraphDiffService.java` carefully
- [ ] Understand current algorithm
- [ ] Read `GraphDiffServiceTest.java` to understand test coverage
- [ ] Review Graph API documentation for `find()`, `contains()`

### Step 2: Update Service Implementation
- [ ] Update imports (add Graph, Node, Triple; remove Model, Statement)
- [ ] Update `computePutDiff()` signature and implementation
- [ ] Update `computePostDiff()` signature and implementation
- [ ] Update `computeDeleteDiff()` signature and implementation
- [ ] Update Javadoc for all methods

### Step 3: Update Unit Tests (GraphDiffServiceTest)
- [ ] Replace `ModelFactory` with `GraphFactory`
- [ ] Replace `ResourceFactory` with `NodeFactory`
- [ ] Update test data creation for all 20+ tests
- [ ] Update method calls
- [ ] Run tests: `mvn -q test -Dtest=GraphDiffServiceTest`

### Step 4: Find Breaking Changes
- [ ] Run compile: `mvn -q compile`
- [ ] Note all command handlers that break
- [ ] Note all tests that break

### Step 5: Update Command Handlers
- [ ] Update `PutGraphCommandHandler.java`
  - Run tests: `mvn -q test -Dtest=PutGraphCommandHandlerTest`
- [ ] Update `PostGraphCommandHandler.java`
  - Run tests: `mvn -q test -Dtest=PostGraphCommandHandlerTest`
- [ ] Update `DeleteGraphCommandHandler.java`
  - Run tests: `mvn -q test -Dtest=DeleteGraphCommandHandlerTest`
- [ ] Update `PatchGraphCommandHandler.java`
  - Run tests: `mvn -q test -Dtest=PatchGraphCommandHandlerTest`
- [ ] Update `BatchGraphsCommandHandler.java`
  - Run tests: `mvn -q test -Dtest=BatchGraphsCommandHandlerTest`

### Step 6: Update Command Handler Tests
- [ ] Update test data creation in all handler tests
- [ ] Update variable types
- [ ] Run tests for each handler

### Step 7: Verification
- [ ] Run all unit tests: `mvn -q test`
- [ ] Run integration tests
- [ ] Run full build: `mvn -q clean install`
- [ ] Verify zero violations

### Step 8: Performance Measurement (Optional but Recommended)
- [ ] Create benchmark test for GraphDiffService
- [ ] Measure performance before/after
- [ ] Document improvement

## Testing Checklist

- [ ] Unit tests pass: `GraphDiffServiceTest` (20+ tests)
- [ ] Command handler tests pass (5 handlers)
- [ ] Integration tests pass
- [ ] Checkstyle: 0 violations
- [ ] SpotBugs: 0 warnings
- [ ] PMD: 0 violations
- [ ] All 911+ tests pass

## Success Criteria

- ✅ All `GraphDiffService` methods use `Graph`
- ✅ All tests pass
- ✅ No quality violations
- ✅ All command handlers updated
- ✅ Performance improvement measured (optional)
- ✅ Javadoc updated

## Performance Measurement

Create a simple benchmark:

```java
@Test
void benchmarkComputePutDiff() {
    Graph oldGraph = createLargeGraph(10000); // 10K triples
    Graph newGraph = createLargeGraph(10000);

    long start = System.nanoTime();
    RDFPatch patch = service.computePutDiff(oldGraph, newGraph, null);
    long end = System.nanoTime();

    System.out.println("Time: " + (end - start) / 1_000_000 + "ms");
}
```

## Notes

- This is the highest-impact performance change
- Graph API is significantly faster for iteration
- No Statement wrapper overhead
- Direct node access
- Consider performance testing with large graphs

## Next Task

After completion: **Task 4 - Migrate DatasetService**
