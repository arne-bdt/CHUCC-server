# Model API to Graph API Migration

## Overview

This document outlines the refactoring plan to migrate from Apache Jena's Model API to the Graph API throughout the CHUCC server codebase.

**Goal**: Improve performance and efficiency by using the lower-level Graph API instead of the higher-level Model API.

## Why Graph API?

The Graph API is more efficient for our use cases because:

1. **Performance**: Direct triple iteration without Statement wrapper objects
2. **Memory efficiency**: Less object overhead (Triple vs Statement)
3. **Direct node access**: Work directly with Nodes instead of RDFNode wrappers
4. **Better alignment**: Our internal DatasetGraph already uses Graph API
5. **Proven pattern**: RdfPatchUtil already successfully uses Graph API

## Model API vs Graph API Comparison

### Model API (Current)
```java
Model model = ModelFactory.createDefaultModel();
for (Statement stmt : model.listStatements().toList()) {
    if (!otherModel.contains(stmt)) {
        Node s = stmt.getSubject().asNode();
        Node p = stmt.getPredicate().asNode();
        Node o = stmt.getObject().asNode();
    }
}
```

### Graph API (Target)
```java
Graph graph = GraphFactory.createDefaultGraph();
graph.find().forEachRemaining(triple -> {
    if (!otherGraph.contains(triple)) {
        Node s = triple.getSubject();
        Node p = triple.getPredicate();
        Node o = triple.getObject();
    }
});
```

## Current Usage Analysis

### Files Using Model API (24 files, 179 occurrences)

**Services (8 files)**:
- `RdfParsingService` - Parses RDF into Model
- `GraphSerializationService` - Serializes Model to RDF formats
- `DatasetService` - Returns Model from Graph (conversion layer)
- `GraphDiffService` - Computes diffs between Models
- `SparqlQueryService` - Gets Model from CONSTRUCT/DESCRIBE queries
- `RdfPatchService` - RDF patch operations
- `ConflictDetectionService` - (assumed usage)
- `PreconditionService` - (assumed usage)

**Command Handlers (5 files)**:
- `PutGraphCommandHandler` - Uses Model for PUT operations
- `PostGraphCommandHandler` - Uses Model for POST operations
- `DeleteGraphCommandHandler` - Uses Model for DELETE operations
- `PatchGraphCommandHandler` - Uses Model for PATCH operations
- `BatchGraphsCommandHandler` - Uses Model for batch operations

**Utilities (2 files)**:
- `GraphCommandUtil` - Utility methods returning Model
- `RdfContentTypeUtil` - (possible usage)

**Controllers (1 file)**:
- `GraphStoreController` - (possible usage)

**Tests (8+ files)**:
- Command handler tests
- Service tests
- Integration tests

## Migration Strategy

### Phase 1: Core Services (High Impact)

**Priority 1: GraphDiffService**
- **Impact**: Used by all command handlers
- **Changes**:
  - Change method signatures: `Model → Graph`
  - Replace `model.listStatements()` with `graph.find()`
  - Replace `model.contains(stmt)` with `graph.contains(triple)`
  - Remove Statement wrapping/unwrapping
- **Performance gain**: Significant (used in every graph operation)

**Priority 2: RdfParsingService**
- **Impact**: Entry point for all RDF content
- **Changes**:
  - Change return type: `Model parseRdf()` → `Graph parseRdf()`
  - Parse into Graph directly using RDFDataMgr
  - Update Javadoc
- **Performance gain**: Moderate (eliminates Model wrapper)

**Priority 3: GraphSerializationService**
- **Impact**: Exit point for all RDF content
- **Changes**:
  - Change parameter type: `Model` → `Graph`
  - Use RDFDataMgr with Graph directly
  - Update Javadoc
- **Performance gain**: Moderate (eliminates Model wrapper)

### Phase 2: Utility Layer (Medium Impact)

**Priority 4: GraphCommandUtil**
- **Impact**: Used by all command handlers
- **Changes**:
  - Change `getCurrentGraph()` return type: `Model → Graph`
  - Update method signatures throughout
  - Update Javadoc
- **Performance gain**: Moderate (reduces conversions)

**Priority 5: DatasetService**
- **Impact**: Core service used throughout
- **Changes**:
  - Change `getGraph()` return type: `Model → Graph`
  - Change `getDefaultGraph()` return type: `Model → Graph`
  - Remove `ModelFactory.createModelForGraph()` conversions
  - Keep internal DatasetGraph unchanged (already uses Graph)
- **Note**: SparqlQueryService still needs Model for CONSTRUCT/DESCRIBE
- **Performance gain**: High (eliminates many conversions)

### Phase 3: Command Handlers (Medium Impact)

**Priority 6: All Command Handlers**
- Files to update:
  - PutGraphCommandHandler
  - PostGraphCommandHandler
  - DeleteGraphCommandHandler
  - PatchGraphCommandHandler
  - BatchGraphsCommandHandler
- **Changes**:
  - Update to use Graph instead of Model
  - Update service method calls
  - Update variable types
- **Performance gain**: Moderate (indirect - benefits from service changes)

### Phase 4: SPARQL Service (Special Case)

**Priority 7: SparqlQueryService**
- **Note**: CONSTRUCT and DESCRIBE queries return Model from Jena API
- **Strategy**:
  - Keep Model return from `execConstruct()` and `execDescribe()`
  - Convert to Graph immediately: `model.getGraph()`
  - Update serialization to work with Graph
  - Consider if we need Model at all (may not)
- **Performance gain**: Small (Jena API constraint)

### Phase 5: Tests (High Volume)

**Priority 8: Service Tests**
- Update all service tests to use Graph API
- Replace `ModelFactory.createDefaultModel()` with `GraphFactory.createDefaultGraph()`
- Replace `ResourceFactory` usage with direct Node creation
- Update test utilities

**Priority 9: Command Handler Tests**
- Update all command handler tests
- Use Graph API for test data creation
- Update assertions

**Priority 10: Integration Tests**
- Update integration tests
- Verify end-to-end functionality
- Update test fixtures

## Implementation Order

### Step-by-step Execution Plan

1. **Step 1: RdfParsingService** (foundational)
   - Change return type to Graph
   - Update implementation
   - Update tests
   - Run build to find breaking changes

2. **Step 2: GraphSerializationService** (foundational)
   - Change parameter type to Graph
   - Update implementation
   - Update tests
   - Run build to find breaking changes

3. **Step 3: GraphDiffService** (high impact)
   - Change all method signatures to use Graph
   - Rewrite diff algorithms to use Graph API
   - Update tests extensively
   - Verify performance improvement

4. **Step 4: DatasetService** (core infrastructure)
   - Change getGraph() and getDefaultGraph() return types
   - Update internal methods
   - Update tests
   - Run build to find cascading changes

5. **Step 5: GraphCommandUtil** (utility layer)
   - Update getCurrentGraph() return type
   - Update all helper methods
   - Update tests

6. **Step 6: All Command Handlers** (batch update)
   - Update all 5 command handlers in parallel
   - Update corresponding tests
   - Run build for each handler

7. **Step 7: SparqlQueryService** (special case)
   - Evaluate if Model is truly needed
   - Add conversion layer if needed
   - Update tests

8. **Step 8: Remaining Services**
   - RdfPatchService (if needed)
   - ConflictDetectionService (if needed)
   - PreconditionService (if needed)

9. **Step 9: Integration Tests**
   - Update all integration tests
   - Full regression testing
   - Performance benchmarking

10. **Step 10: Cleanup**
    - Remove unused Model imports
    - Verify no Model usage remains (except boundaries)
    - Run full build
    - Update documentation

## API Boundary Considerations

### Where Model API Should Remain

1. **HTTP Request/Response Handling**
   - If controllers need Model, keep conversion at boundary
   - Consider if Graph is sufficient

2. **SPARQL Query Results**
   - CONSTRUCT/DESCRIBE return Model from Jena
   - Convert to Graph immediately if needed

3. **External Libraries**
   - Check if any external libraries require Model
   - Add conversion layer if needed

### Conversion Utilities

Create conversion utilities for boundary cases:

```java
public final class GraphModelConverter {

  public static Graph modelToGraph(Model model) {
    return model.getGraph();
  }

  public static Model graphToModel(Graph graph) {
    return ModelFactory.createModelForGraph(graph);
  }
}
```

## Testing Strategy

### For Each Service/Handler Update

1. **Unit Tests**
   - Update test data creation (Model → Graph)
   - Update test assertions
   - Verify behavior unchanged

2. **Integration Tests**
   - Run integration test suite after each major change
   - Verify end-to-end functionality
   - Check for regressions

3. **Performance Tests**
   - Benchmark before/after for GraphDiffService
   - Measure memory usage improvements
   - Document performance gains

## Success Criteria

- ✅ All tests pass (911+ tests)
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Performance improvement measured (especially GraphDiffService)
- ✅ Memory usage improvement measured
- ✅ No Model usage in internal code (only at boundaries)
- ✅ All documentation updated

## Risk Mitigation

### Potential Risks

1. **Blank Node Handling**
   - Model and Graph handle blank nodes differently
   - **Mitigation**: Extensive testing with blank nodes

2. **Statement vs Triple Semantics**
   - Statement includes model context, Triple doesn't
   - **Mitigation**: Verify no code relies on Statement context

3. **Breaking Changes**
   - Public APIs may change
   - **Mitigation**: Update all call sites systematically

4. **Performance Regression**
   - Incorrect usage could hurt performance
   - **Mitigation**: Benchmark critical paths

### Rollback Plan

- Each step is independent and can be reverted
- Git commits should be atomic per step
- Tag before starting: `git tag before-model-to-graph-migration`

## Performance Expectations

### Expected Improvements

1. **GraphDiffService**: 20-30% faster (fewer object allocations)
2. **Memory**: 15-25% reduction (fewer wrapper objects)
3. **Large graphs (10K+ triples)**: More significant improvements

### Measurement Points

- Benchmark GraphDiffService with various graph sizes
- Measure memory usage in DatasetService cache
- Profile command handler execution times

## Documentation Updates

After completion, update:

1. **Architecture documentation** - Note Graph API usage
2. **Developer guide** - Add Graph API best practices
3. **Javadoc** - Update all affected classes
4. **Performance documentation** - Document improvements

## Estimated Effort

- **Phase 1 (Core Services)**: 2-3 sessions
- **Phase 2 (Utilities)**: 1 session
- **Phase 3 (Command Handlers)**: 2 sessions
- **Phase 4 (SPARQL)**: 1 session
- **Phase 5 (Tests)**: 3-4 sessions
- **Total**: 9-11 coding sessions

**Note**: Each session assumes ~2-3 hours of focused work + testing + build verification.

## Next Steps

1. Review this plan with stakeholders
2. Create task breakdown for each step
3. Begin with Step 1: RdfParsingService
4. Measure baseline performance metrics
5. Execute plan step-by-step
6. Document results and lessons learned
