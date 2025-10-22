# Task 2: Migrate GraphSerializationService to Graph API

**Status**: Completed
**Priority**: High (Foundational - blocks other tasks)
**Estimated Effort**: 1-2 hours
**Depends On**: Task 1 (RdfParsingService)

## Goal

Migrate `GraphSerializationService` from accepting `Model` to accepting `Graph` for improved performance and consistency with the Graph API.

## Context

`GraphSerializationService` is the exit point for all RDF content serialization in the system. It's used by:
- Controllers for returning RDF responses
- SPARQL query results formatting
- Anywhere RDF content needs to be serialized to string

**Current signature**:
```java
public String serializeGraph(Model model, String contentType)
```

**Target signature**:
```java
public String serializeGraph(Graph graph, String contentType)
```

## Changes Required

### 1. Update GraphSerializationService

**File**: `src/main/java/org/chucc/vcserver/service/GraphSerializationService.java`

**Changes**:
```java
// Before
import org.apache.jena.rdf.model.Model;

public String serializeGraph(Model model, String contentType) {
    Lang lang = RdfContentTypeUtil.determineLang(contentType);
    // ...
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, model, lang);
    return writer.toString();
}

// After
import org.apache.jena.graph.Graph;

public String serializeGraph(Graph graph, String contentType) {
    Lang lang = RdfContentTypeUtil.determineLang(contentType);
    // ...
    StringWriter writer = new StringWriter();
    RDFDataMgr.write(writer, graph, lang);
    return writer.toString();
}
```

**Note**: Verify that `RDFDataMgr.write()` accepts Graph (it should).

### 2. Update GraphSerializationServiceTest

**File**: `src/test/java/org/chucc/vcserver/service/GraphSerializationServiceTest.java`

**Changes**:
- Update all test methods to use `Graph` instead of `Model`
- Replace `ModelFactory.createDefaultModel()` with `GraphFactory.createDefaultGraph()`
- Update test data creation to use Graph API
- Update method calls to pass Graph

### 3. Update SparqlQueryService

**File**: `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java`

**Special case**: CONSTRUCT and DESCRIBE queries return `Model` from Jena API.

**Changes**:
```java
// In formatGraphResults method
private String formatGraphResults(Model model, ResultFormat format) {
    // Convert Model to Graph
    Graph graph = model.getGraph();

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    switch (format) {
        case TURTLE:
            RDFDataMgr.write(output, graph, Lang.TURTLE);
            break;
        // ... etc
    }
}
```

**Alternative approach**: Keep using Model in SparqlQueryService since Jena API returns Model. Only convert at the boundary if needed.

### 4. Update All Other Consumers

**Files to update** (run build to find all):
- Controllers (if they call GraphSerializationService directly)
- Any service that serializes graphs
- Integration tests

## Implementation Steps

### Step 1: Preparation
- [ ] Read `GraphSerializationService.java` to understand implementation
- [ ] Read `GraphSerializationServiceTest.java` to understand tests
- [ ] Check if `RDFDataMgr.write()` supports Graph (verify Jena docs)
- [ ] Check RDFWriter API for Graph support

### Step 2: Update Service
- [ ] Update imports in `GraphSerializationService.java`
- [ ] Change parameter type from `Model` to `Graph`
- [ ] Update `RDFDataMgr.write()` call
- [ ] Update Javadoc

### Step 3: Update Tests
- [ ] Update `GraphSerializationServiceTest.java`
- [ ] Change test data from Model to Graph
- [ ] Update method calls
- [ ] Update assertions
- [ ] Run unit tests: `mvn -q test -Dtest=GraphSerializationServiceTest`

### Step 4: Update SparqlQueryService
- [ ] Evaluate if Model is needed in SparqlQueryService
- [ ] Option A: Convert Model to Graph before serialization
- [ ] Option B: Keep Model in SPARQL service (defer to Task 7)
- [ ] Update tests if changed

### Step 5: Find Breaking Changes
- [ ] Run static analysis: `mvn -q compile checkstyle:check`
- [ ] Run full build: `mvn -q compile`
- [ ] Note all files that need updates

### Step 6: Update Consumers
- [ ] Update each consumer identified
- [ ] Run tests for each updated file

### Step 7: Verification
- [ ] Run full test suite: `mvn -q test`
- [ ] Run full build: `mvn -q clean install`
- [ ] Verify zero violations

## Testing Checklist

- [ ] Unit tests pass: `GraphSerializationServiceTest`
- [ ] SPARQL tests pass: `SparqlQueryServiceTest`
- [ ] Integration tests pass
- [ ] Checkstyle: 0 violations
- [ ] SpotBugs: 0 warnings
- [ ] PMD: 0 violations
- [ ] All 911+ tests pass

## Success Criteria

- ✅ `GraphSerializationService.serializeGraph()` accepts `Graph`
- ✅ All tests pass
- ✅ No quality violations
- ✅ All consumers updated
- ✅ Javadoc updated

## Notes

- RDFDataMgr and RDFWriter both support Graph natively
- This change pairs well with Task 1 (RdfParsingService)
- SparqlQueryService is a special case - may need conversion layer
- Consider creating GraphModelConverter utility if conversions are needed

## Next Task

After completion: **Task 3 - Migrate GraphDiffService**
