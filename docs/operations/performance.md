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

| Operation | Configuration | Expected Time | Notes |
|-----------|--------------|---------------|-------|
| Event replay (startup) | concurrency=1 | ~100 events/sec | Sequential processing |
| Event replay (startup) | concurrency=6 | ~600 events/sec | 6 datasets in parallel |
| Event projection (steady) | Any | < 10ms/event | Trickle rate, concurrency less important |

### Parallel Event Processing

**Configuration** (Added 2025-10-28):
- `kafka.consumer.concurrency` - Number of concurrent consumer threads
- Default: 1 (sequential processing in dev)
- Production: 6 (parallel processing across 6 dataset topics)
- Range: 1-100

**How It Works:**
Multiple Kafka consumer threads process events from different dataset topics concurrently:
- Each dataset has its own Kafka topic (e.g., `vc.dataset1.events`, `vc.dataset2.events`)
- With concurrency=6, up to 6 dataset topics can be processed in parallel
- Events within a single dataset remain strictly ordered (per-partition guarantee)
- Improves event replay performance during startup

**When to Adjust:**

*Increase concurrency* if:
- Multiple datasets exist (N datasets → N concurrency for full parallelism)
- Startup recovery is slow (replaying events from earliest offset)
- CPU cores are underutilized during event processing

*Decrease concurrency* if:
- Single dataset (concurrency > 1 provides no benefit)
- Memory pressure exists (each consumer has overhead)
- Contention on shared resources observed

**Performance Impact:**
- Startup time: 6x faster with 6 datasets and concurrency=6 (parallel replay)
- Steady-state: Minimal impact (events trickle in slowly)
- Trade-off: More memory/CPU for parallel processing

**Configuration Examples:**

Development:
```yaml
kafka:
  consumer:
    concurrency: 1  # Sequential for simpler debugging
```

Production:
```yaml
kafka:
  consumer:
    concurrency: 6  # Parallel across 6 datasets
```

Environment variable override:
```bash
export KAFKA_CONSUMER_CONCURRENCY=6
```

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

4. **Event processing performance** (Added 2025-10-28):
   - `chucc.projection.event.duration{event_type, dataset, status}` - Event processing time
     - Labels: `event_type` (e.g., CommitCreatedEvent), `dataset`, `status` (success/error)
     - Use to identify slow projector handlers
     - Query example: `histogram_quantile(0.95, chucc_projection_event_duration_seconds)`
   - `chucc.projection.events.total{event_type, dataset, status}` - Event processing count
     - Labels: Same as above, plus `error_type` on failures
     - Use to track throughput and error rates
   - `chucc.projection.retries.total{topic, attempt}` - Retry counts
     - Use to identify problematic events or transient failures

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
