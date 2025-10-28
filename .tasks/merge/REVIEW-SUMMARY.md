# Merge Phase 2 - Review Summary (FINAL)

**Date:** 2025-01-28
**Reviewer:** Claude (Sonnet 4.5)
**Status:** Plan Finalized with RDF Terminology Corrections

---

## Critical Correction: RDF Terminology

**Issue identified:** Original plan used invalid RDF concept "(G, S, P) tuple"

**Valid RDF structures:**
- ✅ **Triple:** `(Subject, Predicate, Object)` - Statement within a graph
- ✅ **Quad:** `(Graph, Subject, Predicate, Object)` - Statement within a dataset
- ✅ **Graph:** Collection of triples
- ✅ **Dataset:** Collection of graphs

**Valid conflict scopes:**
- ✅ **Graph-level:** Treat each graph as semantic boundary
- ✅ **Dataset-level:** Treat entire dataset as semantic boundary
- ❌ **NOT valid:** "Triple-level", "Predicate-level", "Quad-level", "(G, S, P)-level"

**Documentation updated:**
- Added RDF Fundamentals section to [CLAUDE.md](../../.claude/CLAUDE.md)
- Removed all invalid terminology from Phase 2 plan
- Added terminology reminder to Phase 2 task file

---

## Final Scope of Phase 2

### Implemented Features

1. **Merge strategies:** "three-way", "ours", "theirs"
2. **Conflict scopes:** "graph" (default), "dataset"
3. **Configurable via API:** `strategy` and `conflictScope` parameters
4. **Both scopes fully implemented** in Phase 2 (not deferred)

### Conflict Resolution Semantics

**Graph-level (default):**
```
Conflict in Graph-A → Resolve Graph-A, merge non-conflicting graphs
```
- Flexible: Allows independent graph merging
- Best for: Multi-graph datasets with independent graphs

**Dataset-level:**
```
Conflict anywhere → Resolve entire dataset as single unit
```
- Conservative: Safe for inter-graph dependencies
- Best for: Single-graph datasets or tightly coupled graphs
- Git-like: Repository as atomic unit

---

## User Feedback Incorporated

### 1. Terminology Correction ✅

**User clarification:**
> "You write about '(G, S, P) tuples' but this is not a tuple in RDF. Within a graph, there are (S, P, O) triples. Within a dataset, there are (G, S, P, O) quads."

**Action taken:**
- Added RDF Fundamentals to CLAUDE.md
- Removed all invalid terminology references
- Updated Phase 2 plan with correct terminology

### 2. Conflict Scope: Graph vs Dataset ✅

**User clarification:**
> "In the case of a conflict, I want to treat ALL operations on that 'graph' as conflicting. So the other graphs that might exist in the same dataset are not listed as conflicting."

**Action taken:**
- Graph-level is default scope
- Dataset-level is alternative (conservative)
- Only these two scopes are valid

### 3. Configurability in Phase 2 ✅

**User decision:**
> "Configurability: Should this be Phase 2 or deferred? --> yes, please"

**Action taken:**
- Both scopes implemented in Phase 2 (not deferred)
- `conflictScope` parameter added to API
- Estimated time increased to 3-4 hours

### 4. Inter-Graph Dependencies

**User decision:**
> "Inter-graph dependencies: --> just ignore these for now. Supporting both conflict scopes is enough."

**Action taken:**
- No special inter-graph dependency handling
- Users can choose dataset-level scope if dependencies exist
- Documented in decision matrix

### 5. Phase 3 Status ✅

**User decision:**
> "Phase 3: Should we skip it or keep as optional? --> keep as optional"

**Action taken:**
- Phase 3 remains in task list as optional
- Marked clearly: "Evaluate Phase 2 before deciding"
- Not a blocker for Phase 1+2 completion

---

## Implementation Complexity Comparison

### Original Plan (Invalid Approach)

```java
// WRONG: (G, S, P) is not a valid RDF concept
ConflictKey = (Graph, Subject, Predicate)  // ❌
Filter operations matching this tuple       // ❌
```

**Problems:**
- Invalid RDF terminology
- Confusing semantics
- Unnecessary complexity

### Final Plan (Correct Approach)

```java
// CORRECT: Graph-level conflict scope
Set<Node> conflictingGraphs = extractConflictingGraphs(conflicts);  // ✅
Filter operations by graph membership                                // ✅

// CORRECT: Dataset-level conflict scope
if (conflictScope == "dataset") {
  keepAllFrom(winningBranch);  // ✅
}
```

**Benefits:**
- Aligned with RDF semantics
- Clear and simple
- Two valid scopes only

---

## Phase 2 API Examples

### Request with Graph-Level Scope (Default)

```http
POST /version/merge HTTP/1.1
Content-Type: application/json

{
  "into": "main",
  "from": "feature",
  "strategy": "ours",
  "conflictScope": "graph"
}
```

**Behavior:** Resolve conflicting graphs only, merge others

### Request with Dataset-Level Scope

```http
POST /version/merge HTTP/1.1
Content-Type: application/json

{
  "into": "main",
  "from": "feature",
  "strategy": "theirs",
  "conflictScope": "dataset"
}
```

**Behavior:** Resolve entire dataset as atomic unit

### Response

```json
{
  "result": "merged",
  "mergeCommit": "01933e4a...",
  "into": "main",
  "from": "feature",
  "strategy": "ours",
  "conflictScope": "graph",
  "conflictsResolved": 3
}
```

---

## Decision Matrix

| Scenario | Use Graph Scope | Use Dataset Scope |
|----------|-----------------|-------------------|
| Multi-graph dataset, independent graphs | ✅ Recommended | ❌ Too conservative |
| Single graph dataset | Either works | ✅ Simpler |
| Graphs with dependencies | ⚠️ May break dependencies | ✅ Safe |
| Want Git-like behavior | ❌ Too granular | ✅ Recommended |

---

## Success Criteria

### Phase 1 (4-5 hours)
- ✅ Fast-forward detection
- ✅ Three-way merge
- ✅ Conflict detection (returns 409)
- ✅ Common ancestor finding

### Phase 2 (3-4 hours) - **FINALIZED**
- ✅ "ours" and "theirs" strategies
- ✅ Graph-level conflict scope
- ✅ Dataset-level conflict scope
- ✅ Configurable via `conflictScope` parameter
- ✅ Correct RDF terminology throughout
- ✅ `conflictsResolved` count in response

### Phase 3 (3-4 hours) - **OPTIONAL**
- ⏭️ "manual" strategy with resolution array
- ⏭️ User-provided conflict resolutions
- ⏭️ **Recommendation:** Evaluate Phase 2 first

---

## Is It Over-Engineered?

### Phase 1: ✅ Essential
- Core merge functionality
- Standard Git-like features
- Required for version control

### Phase 2 (Revised): ✅ Well-Scoped
- Two valid RDF scopes (graph, dataset)
- Standard strategies (ours, theirs)
- Configurable but not complex
- **NOT over-engineered**

### Phase 3: ⚠️ Evaluate Carefully
- Manual resolution is complex
- May not be needed in practice
- Defer until proven necessary

---

## Time Estimates

| Phase | Estimated Time | Complexity | Status |
|-------|----------------|------------|--------|
| Phase 1 | 4-5 hours | Medium | Not started |
| Phase 2 | 3-4 hours | Medium | Not started |
| **Total** | **7-9 hours** | - | **Production-ready merge** |
| Phase 3 | 3-4 hours | High | Optional (defer) |

---

## Files Modified

### CLAUDE.md
- ✅ Added RDF Fundamentals section
- ✅ Explains Triples, Quads, Graphs, Datasets
- ✅ Documents valid conflict scopes
- ✅ Warns against invalid RDF concepts

### Phase 2 Task File
- ✅ Fixed all RDF terminology
- ✅ Added `conflictScope` parameter (not deferred)
- ✅ Implemented both graph and dataset scopes
- ✅ Updated time estimate: 3-4 hours
- ✅ Clear examples for both scopes

---

## Final Recommendations

### ✅ Implement Phase 1 + Phase 2

**Reasons:**
1. Both phases are essential for production use
2. Correct RDF semantics throughout
3. Configurable conflict scopes cover all use cases
4. Total time: 7-9 hours (reasonable)

### ⏭️ Defer Phase 3

**Reasons:**
1. Manual resolution is significantly more complex
2. May not be needed in practice
3. Ours/theirs with two scopes likely covers 95%+ of cases
4. Can add later without breaking changes

### 📚 Documentation Quality

**Achieved:**
- Clear RDF terminology
- No invalid concepts
- Practical examples
- Decision matrices
- Edge case coverage

---

## Conclusion

**The Phase 2 plan is now production-ready with correct RDF terminology.**

### Key Improvements from Review

1. ✅ **Fixed terminology:** Removed all "(G, S, P)" references
2. ✅ **Added fundamentals:** RDF concepts documented in CLAUDE.md
3. ✅ **Added configurability:** Both scopes in Phase 2 (not deferred)
4. ✅ **Simplified scope:** Only two valid options (graph, dataset)
5. ✅ **Removed complexity:** No inter-graph dependency handling
6. ✅ **Clear guidance:** Decision matrix for scope selection

### Next Steps

1. ✅ Documentation updated
2. ✅ Phase 2 plan finalized
3. ➡️ Ready to implement Phase 1
4. ➡️ Ready to implement Phase 2 after Phase 1
5. ⏭️ Evaluate Phase 3 after production usage

---

**Status:** Ready for implementation
**Estimated Total Time:** 7-9 hours for production-ready merge
**Terminology:** ✅ Correct RDF semantics
**Over-engineering:** ❌ No - minimal viable implementation
