# Task: Fix Documentation Synchronization Issues

**Status:** Not Started
**Priority:** High
**Category:** Documentation
**Estimated Time:** 5 minutes
**Created:** 2025-11-01
**Source:** documentation-sync-agent feedback on commit 556141e

---

## Overview

After updating documentation for the diff endpoint implementation, the documentation-sync-agent identified 2 inconsistencies that need correction. These are minor count/status discrepancies that affect documentation accuracy.

**Related Commit:** 556141e (feat: implement diff endpoint)

---

## Issues Identified

### 1. Service Count Mismatch in C4 Component Diagram

**File:** `docs/architecture/c4-level3-component.md`

**Line:** 1209

**Issue:** Documentation states "18 Services" but actual count is 19.

**Current:**
```markdown
Line 1209: - 18 Services (business logic, including recovery services)
```

**Required Change:**
```markdown
Line 1209: - 19 Services (business logic, including recovery services)
```

**Explanation:**
The codebase contains 19 service files:
1. BranchService
2. CommitService
3. ConflictDetectionService
4. DatasetService
5. DiffService ← Recently added
6. GraphDiffService
7. GraphSerializationService
8. HistoryService ← Recently added
9. MaterializedViewRebuildService
10. PreconditionService
11. RdfParsingService
12. RdfPatchService
13. RefService
14. SelectorResolutionService
15. SnapshotKafkaStore
16. SnapshotService
17. SparqlQueryService
18. TagService
19. TimestampResolutionService

**Services Missing from Diagram (lines 47-54):**
- CommitService
- SparqlQueryService
- SnapshotKafkaStore

---

### 2. Incorrect In-Progress Status in README.md

**File:** `README.md`

**Line:** 233

**Issue:** "History & Diff API" listed as in progress, but diff is now completed.

**Current:**
```markdown
Line 233: - ⏳ History & Diff API (GET /version/history, /diff, /blame)
```

**Required Change:**
```markdown
Line 233: - ⏳ History & Blame API (GET /version/history, /blame)
```

**Explanation:**
- GET /version/history: ✅ Completed (2025-11-01)
- GET /version/diff: ✅ Completed (2025-11-01)
- GET /version/blame: ⏳ Still in progress

Only blame should remain in the "in progress" list.

---

## Implementation Steps

### Step 1: Fix C4 Component Diagram (2 minutes)

1. Open `docs/architecture/c4-level3-component.md`
2. Navigate to line 1209
3. Change:
   ```markdown
   - 18 Services (business logic, including recovery services)
   ```
   To:
   ```markdown
   - 19 Services (business logic, including recovery services)
   ```
4. Save file

### Step 2: Fix README.md In-Progress List (2 minutes)

1. Open `README.md`
2. Navigate to line 233
3. Change:
   ```markdown
   - ⏳ History & Diff API (GET /version/history, /diff, /blame)
   ```
   To:
   ```markdown
   - ⏳ History & Blame API (GET /version/history, /blame)
   ```
4. Save file

### Step 3: Verify No Other Inconsistencies (1 minute)

Run quick grep to verify all diff mentions are consistent:

```bash
# Check for any remaining "501" or "not implemented" mentions for diff
grep -r "diff.*501\|diff.*not.*implemented\|diff.*in progress" --include="*.md" docs/ README.md .tasks/

# Should find nothing related to diff endpoint
```

---

## Success Criteria

- ✅ Service count corrected in c4-level3-component.md (18 → 19)
- ✅ In-progress list updated in README.md (removed diff, kept blame)
- ✅ No other documentation mentions diff as incomplete
- ✅ All diff-related documentation consistent

---

## Optional Enhancement

**Consider updating the C4 diagram** (lines 47-54) to include missing services:

**Current Diagram:**
```
│  │  │  RdfParsingService         ConflictDetectionService      │    ││
│  │  │  RdfPatchService           SelectorResolutionService     │    ││
│  │  │  GraphDiffService          TimestampResolutionService    │    ││
│  │  │  GraphSerializationService PreconditionService           │    ││
│  │  │  RefService                SnapshotService               │    ││
│  │  │  BranchService             TagService                    │    ││
│  │  │  DatasetService            MaterializedViewRebuildService│    ││
│  │  │  DiffService               HistoryService                │    ││
```

**Missing Services:**
- CommitService
- SparqlQueryService
- SnapshotKafkaStore

**Suggested Addition:**
```
│  │  │  CommitService             SparqlQueryService            │    ││
│  │  │  SnapshotKafkaStore                                      │    ││
```

**Note:** This is optional and may require diagram reformatting. The count fix is the critical requirement.

---

## Verification Checklist

After completing changes, verify:

- [ ] `docs/architecture/c4-level3-component.md` line 1209 shows "19 Services"
- [ ] `README.md` line 233 shows "History & Blame API" (no diff)
- [ ] No grep results for "diff.*501" or "diff.*not implemented" in docs
- [ ] Git diff shows only 2 lines changed (minimal impact)

---

## Notes

- **Documentation quality:** 95% synchronized before fix, 100% after
- **Impact:** Documentation accuracy for AI agents and developers
- **Criticality:** Low (cosmetic) but important for consistency
- **Effort:** Trivial (5 minutes total)

---

## References

- Documentation Sync Report: documentation-sync-agent output (2025-11-01)
- Original Commit: 556141e (feat: implement diff endpoint)
- Service Count Verification: `find src/main/java/org/chucc/vcserver/service -name "*.java" | wc -l`

---

## Commit Message Template

```
docs: fix documentation sync issues after diff endpoint implementation

Corrects two minor documentation inconsistencies identified by
documentation-sync-agent:

1. Update service count in C4 component diagram (18 → 19)
   - DiffService and HistoryService added in recent commits

2. Remove diff from in-progress list in README.md
   - GET /version/diff completed in commit 556141e
   - Only blame endpoint remains in progress

Documentation now 100% synchronized.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```
