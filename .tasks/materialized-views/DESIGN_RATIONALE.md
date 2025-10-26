# Design Rationale: Eager Cache Updates vs. MaterializedBranchRepository

**Date:** 2025-10-26
**Status:** Revised approach - simpler, more pragmatic

---

## Summary

The original materialized views plan has been **revised** to use a simpler, more pragmatic approach:

**Original Plan:** Create new `MaterializedBranchRepository` to store duplicate graphs
**Revised Plan:** Enhance existing `DatasetService` cache with eager updates

**Result:** Same performance benefit (10-20x faster), less code, no memory duplication

---

## Problem Statement

**Current Issue:**
- Branch HEAD queries build graphs **on-demand** (query-time)
- First query: 100-200ms (build from commit history)
- Subsequent queries: fast (cached)
- Cache eviction or restart: back to 100-200ms

**Goal:**
- Branch HEAD queries should be **instant** (<10ms)
- Graphs should be updated **eagerly** (write-time, not query-time)
- When commit arrives, update the graph immediately

---

## Original Plan Analysis

### What the Original Plan Proposed

**Task 01:** Create `MaterializedBranchRepository`
- New repository class (~300 LOC)
- In-memory `ConcurrentHashMap<String, DatasetGraph>`
- Methods: `getBranchGraph()`, `applyPatchToBranch()`, `createBranch()`, etc.

**Task 02:** Update `ReadModelProjector`
- Call `materializedBranchRepo.applyPatchToBranch()` when events arrive
- Maintain graphs alongside `CommitRepository` updates

**Task 03:** Update `DatasetService`
- Branch queries → use `MaterializedBranchRepository`
- Historical queries → use existing on-demand building

**Task 04:** Add monitoring and recovery

### Critical Issues Identified

#### 1. **Duplication with Existing Infrastructure**

The system **already has** materialized branch views!

**Evidence:**
```java
// CacheProperties.java:24
private boolean keepLatestPerBranch = true;  // Default: ENABLED
```

```java
// DatasetService.java:256-258
if (cacheProperties.isKeepLatestPerBranch() && isLatestCommit(datasetName, commitId)) {
  // For latest commits, use get() which ensures they stay in cache
  return datasetCache.get(key, k -> buildDatasetGraph(datasetName, commitId));
}
```

**Branch HEADs are already pinned in cache and never evicted!**

#### 2. **The Real Problem**

The task description says:
> "Graph state is cached (LRU) but may be evicted"

**This is misleading.** The actual problem is:

- ❌ Cache is **lazy** (builds on query)
- ✅ Should be **eager** (updates on write)

The cache retention mechanism **already works**. We just need to update graphs when commits arrive instead of when queries arrive.

#### 3. **Memory Duplication**

Original plan would create:
```java
// NEW: MaterializedBranchRepository
private final ConcurrentHashMap<String, DatasetGraph> branchGraphs;

// EXISTING: DatasetService
private final Cache<CacheKey, DatasetGraphInMemory> datasetCache;
```

**Result:** Same data stored twice = **2x memory usage!**

- 10 branches × 100K triples × 2 stores = ~400 MB instead of ~200 MB
- Original plan acknowledges "200 MB" but doesn't account for keeping BOTH stores

#### 4. **Architectural Confusion**

**"Repository" pattern** in this codebase:
- `CommitRepository` - persistence (event log)
- `BranchRepository` - persistence (metadata)
- `TagRepository` - persistence (metadata)
- `DatasetRepository` - persistence (dataset list)

**What MaterializedBranchRepository would be:**
- Not persistence (in-memory cache)
- Not event log (derived from events)
- Really a **cache/view layer**, not persistence

**Mixing metaphors makes code harder to understand.**

#### 5. **Expensive Operations**

```java
// Original plan: Full graph cloning
void createBranch(dataset, branch, Optional<String> parentBranch) {
  DatasetGraph clonedGraph = cloneDatasetGraph(parentGraph); // Full copy!
}
```

- Current system: shares parent commit's graph in cache (cheap)
- New system: clones entire graph (expensive for large graphs)

#### 6. **Integration Complexity**

After Tasks 01-03:
- `MaterializedBranchRepository` (new)
- `DatasetService` cache (existing)
- Two places to check for graphs
- Complex synchronization requirements
- Which one is the "source of truth" for current state?

---

## Revised Approach: Eager Cache Updates

### Key Insight

**We don't need a new store. We need eager updates to the existing cache.**

### What's Different

**Task 01:** Add `applyPatchToBranchCache()` method to DatasetService
- One new method (~50 LOC)
- Uses existing Caffeine cache
- Transactional patch application

**Task 02:** Update ReadModelProjector
- Call `applyPatchToBranchCache()` on commit events
- Same eager update semantics

**Task 03:** Performance benchmarks
- Validate 10-20x improvement
- Optimize cache configuration

**Task 04:** Monitoring and recovery
- Adapted for cache-based approach
- Cache rebuild instead of repository rebuild

### Comparison Table

| Aspect | Original Plan | Revised Plan |
|--------|---------------|--------------|
| **New Classes** | 3 (Repository + Exception + Tests) | 0 (enhance existing) |
| **Lines of Code** | ~500 LOC | ~100 LOC |
| **Memory Usage** | 2x (cache + repository) | 1x (cache only) |
| **Complexity** | High (two storage layers) | Low (one storage layer) |
| **Cache Duplication** | Yes | No |
| **Branch Cloning** | Full copy (expensive) | Shared snapshots (cheap) |
| **Integration Points** | 2 (cache + repository) | 1 (cache) |
| **Estimated Time** | 13-17 hours | 8-11 hours |
| **Performance Benefit** | 10-20x faster | 10-20x faster (same!) |

### Why This Works

**The cache already has everything we need:**
1. ✅ Branch HEAD pinning (`keepLatestPerBranch=true`)
2. ✅ LRU eviction for historical commits
3. ✅ Transactional graph operations (Jena)
4. ✅ Thread-safe concurrent access (Caffeine)

**Only missing:** Write-time updates instead of query-time updates.

**Solution:** Add `applyPatchToBranchCache()` method that the projector can call.

---

## CQRS Compliance

Both approaches are CQRS compliant, but the revised approach is clearer:

### Original Plan
- `CommitRepository` = read model (event log)
- `BranchRepository` = read model (metadata)
- `MaterializedBranchRepository` = read model (graphs)
- `DatasetService cache` = read model (graphs)
- **Issue:** Two read models for the same data (graphs)

### Revised Plan
- `CommitRepository` = read model (event log)
- `BranchRepository` = read model (metadata)
- `DatasetService cache` = read model (graphs)
- **Clear:** One read model per concern

**Pattern:** Projector maintains multiple read models (same as before)

---

## Benefits of Revised Approach

### 1. Simplicity
- Fewer classes
- Less code
- Easier to understand

### 2. Memory Efficiency
- No duplicate storage
- Same graphs in one place
- Lower memory footprint

### 3. Faster Implementation
- 8-11 hours vs. 13-17 hours
- Less testing surface
- Fewer integration points

### 4. Better Architecture
- Clear separation: cache is in DatasetService (where it belongs)
- No new "Repository" metaphor for cache
- Consistent with existing patterns

### 5. Reuses Optimizations
- Snapshot optimization still works
- LRU eviction for historical commits
- Cache statistics already tracked

---

## What Stays the Same

**Both approaches achieve:**
- ✅ 10-20x faster branch HEAD queries
- ✅ Instant query response (<10ms)
- ✅ CQRS compliance
- ✅ Backward compatibility (historical queries unchanged)
- ✅ Production-ready monitoring
- ✅ Recovery mechanisms

**The performance benefit is identical!**

---

## Decision Rationale

### Why Enhance Cache Instead of New Repository?

1. **Pragmatism:** Use what's already there
2. **Simplicity:** Less code, fewer classes
3. **Efficiency:** No memory duplication
4. **Clarity:** Cache stays in DatasetService where it belongs
5. **Speed:** Faster to implement

### When Would MaterializedBranchRepository Make Sense?

**If you needed:**
- Persistent storage (TDB2 backend)
- External graph database (GraphDB, Neptune)
- Separate lifecycle management
- Different eviction policies per graph

**But for in-memory graphs with existing cache:** Enhance the cache.

---

## Migration Path

If you later want to add persistent storage:

**Step 1:** Extract interface
```java
interface GraphStore {
  DatasetGraph getBranchGraph(dataset, branch);
  void applyPatch(dataset, branch, patch);
}
```

**Step 2:** Implement alternatives
```java
class CacheBasedGraphStore implements GraphStore { ... }
class TDB2GraphStore implements GraphStore { ... }
class ExternalGraphStore implements GraphStore { ... }
```

**Step 3:** Configure via Spring
```java
@Configuration
class GraphStoreConfig {
  @Bean
  GraphStore graphStore() {
    return switch (config.getType()) {
      case "cache" -> new CacheBasedGraphStore(...);
      case "tdb2" -> new TDB2GraphStore(...);
      case "external" -> new ExternalGraphStore(...);
    };
  }
}
```

**This refactoring is easy later if needed.**

---

## Conclusion

**Original plan:** Well-written tasks, good execution plan, but architecturally over-engineered.

**Revised plan:** Achieves same goal with simpler approach.

**Recommendation:** Proceed with revised approach (Tasks 01-04 in `.tasks/materialized-views/`).

**Key Learning:** Always check if infrastructure already exists before creating duplicates!

---

## References

- [DatasetService.java](../../src/main/java/org/chucc/vcserver/service/DatasetService.java) - Existing cache
- [CacheProperties.java](../../src/main/java/org/chucc/vcserver/config/CacheProperties.java) - Cache configuration
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - Event processing
- Original task files: `01-create-materialized-branch-repository.md` (revised)
