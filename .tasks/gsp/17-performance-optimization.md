# Task 17: Performance Optimization and Caching

## Objective
Optimize performance for graph operations, especially GET operations and graph materialization.

## Background
Materializing graphs from commit history (event sourcing) can be expensive. Caching and optimization can improve performance.

## Tasks

### 1. Profile Current Performance
- Write performance test for GET operation
- Measure time to materialize graph from N commits
- Identify bottlenecks

### 2. Implement Graph Caching
Create or update caching layer:
- Cache materialized graphs by (commitId, graphIri)
- Use Spring Cache abstraction (@Cacheable)
- Configure cache eviction policy (LRU, size limit)

Update DatasetService:
- Add @Cacheable to getGraph() method
- Cache key: commitId + graphIri

### 3. Optimize RDF Patch Application
Review patch application logic:
- Batch operations when possible
- Use efficient Jena APIs
- Consider incremental materialization (apply patches on top of parent graph)

### 4. Add Metrics
Add metrics using Micrometer:
- Graph GET response time
- Graph materialization time
- Cache hit/miss rate
- Graph size (triple count)

### 5. Write Performance Tests
Create `src/test/java/org/chucc/vcserver/performance/GraphStorePerformanceTest.java`:
- Test GET performance with various history depths
- Test cache effectiveness
- Verify performance targets met (e.g., GET < 100ms for small graphs)

### 6. Document Performance Characteristics
Update documentation:
- Expected performance for various scenarios
- Cache configuration options
- Scaling considerations

## Acceptance Criteria
- [ ] Graph caching implemented
- [ ] Performance metrics added
- [ ] Performance tests pass with acceptable latency
- [ ] Cache hit rate > 80% in typical usage
- [ ] Documentation updated
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 16 (event projector)

## Estimated Complexity
Medium (5-6 hours)

## Notes
This task can be deferred if performance is acceptable without optimization.
