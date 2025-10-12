# Task 1: Migrate RdfParsingService to Graph API

**Status**: Not Started
**Priority**: High (Foundational - blocks other tasks)
**Estimated Effort**: 1-2 hours

## Goal

Migrate `RdfParsingService` from returning `Model` to returning `Graph` for improved performance and consistency with the Graph API.

## Context

`RdfParsingService` is the entry point for all RDF content parsing in the system. It's used by:
- All command handlers (PUT, POST, DELETE, PATCH, BATCH)
- Integration tests
- Anywhere RDF content needs to be parsed

**Current signature**:
```java
public Model parseRdf(String content, String contentType)
```

**Target signature**:
```java
public Graph parseRdf(String content, String contentType)
```

## Changes Required

### 1. Update RdfParsingService

**File**: `src/main/java/org/chucc/vcserver/service/RdfParsingService.java`

**Changes**:
```java
// Before
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

public Model parseRdf(String content, String contentType) {
    // ...
    if (content == null || content.isBlank()) {
        return ModelFactory.createDefaultModel();
    }

    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, reader, null, lang);
    return model;
}

// After
import org.apache.jena.graph.Graph;
import org.apache.jena.sparql.graph.GraphFactory;

public Graph parseRdf(String content, String contentType) {
    // ...
    if (content == null || content.isBlank()) {
        return GraphFactory.createDefaultGraph();
    }

    Graph graph = GraphFactory.createDefaultGraph();
    RDFDataMgr.read(graph, reader, null, lang);
    return graph;
}
```

**Note**: Verify that `RDFDataMgr.read()` accepts Graph (it should).

### 2. Update RdfParsingServiceTest

**File**: `src/test/java/org/chucc/vcserver/service/RdfParsingServiceTest.java`

**Changes**:
- Update all test methods to expect `Graph` instead of `Model`
- Replace `ModelFactory.createDefaultModel()` with `GraphFactory.createDefaultGraph()`
- Update assertions to work with Graph API
- Verify test data creation uses Graph

### 3. Update All Consumers

**Files to update** (run build to find all):
- `PutGraphCommandHandler`
- `PostGraphCommandHandler`
- `DeleteGraphCommandHandler` (if used)
- `PatchGraphCommandHandler` (if used)
- `BatchGraphsCommandHandler` (if used)
- Any other service or handler using RdfParsingService

**Pattern to update**:
```java
// Before
Model newGraph = rdfParsingService.parseRdf(content, contentType);

// After
Graph newGraph = rdfParsingService.parseRdf(content, contentType);
```

## Implementation Steps

### Step 1: Preparation
- [ ] Read `RdfParsingService.java` to understand current implementation
- [ ] Read `RdfParsingServiceTest.java` to understand test coverage
- [ ] Check if `RDFDataMgr.read()` supports Graph (verify Jena docs)

### Step 2: Update Service
- [ ] Update imports in `RdfParsingService.java`
- [ ] Change return type from `Model` to `Graph`
- [ ] Replace `ModelFactory.createDefaultModel()` with `GraphFactory.createDefaultGraph()`
- [ ] Update `RDFDataMgr.read()` call to use Graph
- [ ] Update Javadoc

### Step 3: Update Tests
- [ ] Update `RdfParsingServiceTest.java`
- [ ] Change expected return types
- [ ] Update test data creation
- [ ] Update assertions
- [ ] Run unit tests: `mvn -q test -Dtest=RdfParsingServiceTest`

### Step 4: Find Breaking Changes
- [ ] Run static analysis: `mvn -q compile checkstyle:check spotbugs:check`
- [ ] Run full build to find breaking changes: `mvn -q compile`
- [ ] Note all files that need updates

### Step 5: Update Consumers
- [ ] Update each command handler identified
- [ ] Update any other consumers
- [ ] Run tests for each updated file

### Step 6: Verification
- [ ] Run full test suite: `mvn -q test`
- [ ] Run full build: `mvn -q clean install`
- [ ] Verify zero violations

## Testing Checklist

- [ ] Unit tests pass: `RdfParsingServiceTest`
- [ ] Command handler tests pass
- [ ] Integration tests pass
- [ ] Checkstyle: 0 violations
- [ ] SpotBugs: 0 warnings
- [ ] PMD: 0 violations
- [ ] All 911+ tests pass

## Success Criteria

- ✅ `RdfParsingService.parseRdf()` returns `Graph`
- ✅ All tests pass
- ✅ No quality violations
- ✅ All consumers updated
- ✅ Javadoc updated

## Rollback Plan

If issues arise:
1. `git stash` or `git reset --hard HEAD`
2. Review what went wrong
3. Adjust strategy

## Notes

- This is a foundational change that will cascade to many files
- The build will help identify all consumers
- Graph API is more efficient, so this improves performance
- RDFDataMgr should handle Graph directly (verify in Jena docs)

## Next Task

After completion: **Task 2 - Migrate GraphSerializationService**
