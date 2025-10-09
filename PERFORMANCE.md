# Performance Characteristics and Optimization Strategy

## Current Architecture

### Dataset Materialization
The system uses event sourcing with RDFPatch to maintain version history. Datasets are materialized by applying patches from commit history:

```
Initial State (empty) → Patch 1 → Patch 2 → ... → Patch N = Current State
```

### Caching Strategy
**Simple manual cache** using `ConcurrentHashMap` in `DatasetService`:
- **Cache key**: `(datasetName, commitId)`
- **Cache value**: Materialized `DatasetGraphInMemory`
- **Eviction**: Manual via `clearCache()` or `clearAllCaches()`
- **Thread-safety**: `ConcurrentHashMap` provides thread-safe operations

This simple approach is sufficient because:
1. **In-memory operations are fast**: RDFPatch application on in-memory graphs is efficient
2. **No memory pressure**: Datasets are small to medium sized (typical use case)
3. **Natural cache locality**: Repeated queries typically target the same commit (branch HEAD)

### Observability
**Metrics** (via Spring Boot Actuator + Micrometer):
- `dataset.cache.size` - Number of cached dataset graphs
- Available at: `http://localhost:8080/actuator/metrics/dataset.cache.size`

**Health checks**:
- Standard Spring Boot health endpoint: `http://localhost:8080/actuator/health`

## Performance Characteristics

### Expected Performance (Typical Hardware)

| Operation | Dataset Size | Commit Depth | Expected Time |
|-----------|--------------|--------------|---------------|
| GET graph (cached) | Any | Any | < 5ms |
| GET graph (cache miss, shallow) | 10K triples | 1-10 commits | < 50ms |
| GET graph (cache miss, deep) | 10K triples | 100 commits | < 500ms |
| POST/PUT graph | 10K triples | N/A | < 100ms |

**Note**: These are rough estimates. Actual performance depends on:
- Triple complexity (blank nodes, large literals)
- Patch size and complexity
- Available memory
- JVM heap settings

### Scalability Considerations

**Vertical Scaling**:
- More memory → Larger cache → Better performance
- More CPU cores → Better concurrent request handling

**Horizontal Scaling**:
- Current implementation: Single JVM instance (in-memory repositories)
- For distributed deployment, replace in-memory repositories with external stores (PostgreSQL, etc.)

## When to Optimize

### Decision Criteria for Advanced Caching

Consider implementing sophisticated caching (Caffeine, Redis, etc.) **only if**:

1. **Performance data shows bottleneck**
   - Metrics indicate high cache miss rates
   - GET operations consistently exceed target latency (e.g., > 100ms p95)

2. **Memory pressure exists**
   - Cache grows unbounded and causes OOM errors
   - Need eviction policies (LRU, TTL, size limits)

3. **Distributed caching needed**
   - Multiple application instances require shared cache
   - Cache coherency becomes important

### Optimization Options (Deferred)

If performance issues arise, consider these optimizations **in order**:

#### 1. Tune Existing Cache
- Add size limits to prevent unbounded growth
- Implement LRU eviction for the ConcurrentHashMap
- Add metrics for cache hit/miss rates

#### 2. Optimize Patch Application
- **Incremental materialization**: Cache parent graph, apply only child patches
- **Parallel processing**: Apply independent patches in parallel
- **Batch operations**: Group small patches before materializing

#### 3. Add Sophisticated Caching
- **Caffeine**: Local cache with advanced eviction policies
- **Redis**: Distributed cache for multi-instance deployments
- **Multi-level caching**: L1 (local) + L2 (distributed)

#### 4. Pre-computation Strategies
- **Snapshot commits**: Periodically save full materialized state (every N commits)
- **Background warming**: Pre-materialize frequently accessed commits
- **Query result caching**: Cache SPARQL query results (separate concern)

## Monitoring Recommendations

### Key Metrics to Track

1. **Cache effectiveness**:
   - `dataset.cache.size` - Current cache size
   - Future: Cache hit rate (if optimization becomes necessary)

2. **Request latency**:
   - HTTP request duration (via Spring Boot Actuator)
   - p50, p95, p99 latencies

3. **Resource usage**:
   - JVM heap usage
   - GC frequency and duration

### Performance Testing

Before optimizing, establish baselines:

```bash
# Example: Measure GET performance with deep history
curl -w "@curl-format.txt" \
  -H "Accept: text/turtle" \
  http://localhost:8080/datasets/mydata/graphs/http://example.org/g1
```

Create `curl-format.txt`:
```
time_total: %{time_total}s
time_namelookup: %{time_namelookup}s
time_connect: %{time_connect}s
time_starttransfer: %{time_starttransfer}s
```

## Current Status

- ✅ Observability infrastructure in place (Spring Boot Actuator)
- ✅ Simple caching implemented (ConcurrentHashMap)
- ✅ Basic metrics available (`dataset.cache.size`)
- ⚠️ Advanced caching **deferred** until performance data justifies complexity
- ⚠️ Performance testing **deferred** until real usage patterns emerge

## Philosophy

> **"Premature optimization is the root of all evil"** - Donald Knuth

This project follows the principle: **Measure first, optimize later**.

- Start simple (in-memory, simple cache)
- Add observability (metrics, logging)
- Collect real usage data
- Optimize based on data, not speculation

For most use cases, in-memory RDF operations with simple caching will be more than sufficient.
