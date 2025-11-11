# Consistency Review: Time-Travel vs Commit-History

**Issue**: Documentation treats time-travel and commit-history as separate concepts when they're fundamentally the same thing.

**Root Cause**: asOf queries resolve to commits server-side. Timeline IS commit history visualized chronologically.

---

## Files Needing Updates

### 1. SQUI Specification

**File**: `.frontend-concept/03-chucc-squi-spec/query-context-selector-spec.md`

**Line 59-66** (AsOfContext interface):

**Current**:
```typescript
/**
 * Query data as it existed at a specific timestamp (time-travel).
 * Maps to: ?asOf={timestamp}
 */
export interface AsOfContext {
  type: 'asOf';
  asOf: string;    // ISO 8601 timestamp: "2025-11-10T10:30:00Z"
}
```

**Proposed**:
```typescript
/**
 * Query data as it existed at a specific timestamp.
 *
 * **Implementation Note**: The server resolves this timestamp to the
 * commit at or before the specified time. There is no data "between"
 * commits - each commit is an immutable snapshot.
 *
 * Maps to: ?asOf={timestamp}
 * Server resolves to: commit ID (UUIDv7) at or before timestamp
 */
export interface AsOfContext {
  type: 'asOf';
  asOf: string;    // ISO 8601 timestamp: "2025-11-10T10:30:00Z"

  // Optional: Resolved commit ID (populated after server resolution)
  _resolvedCommit?: string;  // UUIDv7
}
```

---

### 2. Time Travel Mockup

**File**: `.frontend-concept/02-concept/ui-mockups/05-time-travel.md`

**Section 2.1** (Time Selection Header) - Add clarity note:

**After line 86**, add:

```markdown
**Understanding Time Travel**:

Time travel in CHUCC queries specific commits in your dataset's history.
When you select a timestamp:
1. The system finds the commit at or before that time
2. Your query executes against that commit's snapshot
3. There is no data "between" commits

Think of commits as bookmarks in time - the timeline shows your commit
history in chronological order, not a continuous stream.

**Why this matters**: Selecting "10:30:00" executes at the commit created
at or just before 10:30, not at some interpolated state.
```

---

**Section 3.1** (Browse Graph) - Add note:

**After line 361** ("User selects..."), add:

```markdown
**Note**: When user selects a timestamp, the server resolves it to a
commit (e.g., "10:30:00" → commit 019abc...). The query results show
which commit was used: "Queried at: 2025-11-10T10:30:00Z (commit 019abc...)"
```

---

### 3. Version Control Mockup

**File**: `.frontend-concept/02-concept/ui-mockups/03-version-control.md`

**Section 2.3** (Commit Graph) - Add view toggle:

**After line 125**, add:

```markdown
**Visualization Modes**:

The commit graph can be displayed in multiple ways:

1. **Graph View** (default) - DAG visualization
   - Shows branch structure and merges
   - Colored lines for different branches
   - Best for understanding relationships

2. **Timeline View** - Chronological list
   - Shows commits ordered by timestamp
   - Similar to Time Travel view
   - Best for historical analysis
   - Each marker is clickable to view commit details

3. **Calendar View** (future) - Heatmap
   - Shows commit activity over time
   - Color intensity = number of changes
   - Best for activity analysis

Toggle between views with buttons above the graph:
[Graph] [Timeline] [Calendar]
```

---

### 4. Information Architecture

**File**: `.frontend-concept/02-concept/01-information-architecture.md`

**Section 2.1** (Primary Views) - Update Time Travel description:

**Current** (around line 50-60):
```
- /time-travel - Time Travel Queries
  Purpose: Query historical data at specific timestamps
```

**Proposed**:
```
- /time-travel - Time Travel Queries
  Purpose: Query commit history with chronological visualization
  Note: Provides timeline view of commits for historical analysis.
        Queries resolve to specific commits (no data "between" commits).
  Alternative: Consider merging with /version as visualization mode
```

---

### 5. Executive Summary

**File**: `.frontend-concept/02-concept/executive-summary.md`

**Section 3.3** (Time Travel View) - Add clarification:

**Find and update** the Time Travel section (search for "Time Travel"):

**Add after description**:
```markdown
**Architecture Note**: Time travel queries execute at specific commits.
The asOf selector finds the commit at or before the specified timestamp.
The timeline visualization shows commit history chronologically -
essentially the same data as the Version Control view's commit graph,
but laid out by time instead of by branch structure.

**Design Decision**: We maintain separate views because:
- Version Control focuses on branch operations (merge, rebase, etc.)
- Time Travel focuses on historical query analysis (animation, comparison)

Alternative: These could be merged into a single view with visualization modes.
```

---

### 6. SQUI Tasks (Multiple Files)

**Files**:
- `.frontend-concept/SQUI/02-types.md`
- `.frontend-concept/SQUI/03-utils.md`
- `.frontend-concept/SQUI/04-main-component.md`

**Update**: Add comments to AsOfContext usage explaining resolution:

**Example in 02-types.md** (line ~80):
```typescript
/**
 * Query data as it existed at a specific timestamp.
 *
 * **Server-Side Resolution**: The backend finds the commit at or
 * before this timestamp. The resolved commit ID is what actually
 * gets queried.
 *
 * **UI Consideration**: Users select times, but they're querying
 * commits. Show resolved commit in UI: "asOf: 10:30 (commit 019abc...)"
 */
export interface AsOfContext {
  type: 'asOf';
  asOf: string;  // ISO 8601 timestamp
}
```

---

### 7. Frontend Integration Guide

**File**: `.frontend-concept/SQUI/12-frontend-integration.md`

**Section 3** (Integration Patterns) - Add asOf resolution pattern:

**Add new section 3.4**:

```markdown
### Pattern 4: AsOf Resolution

**Challenge**: asOf context resolves to commit server-side. Should UI show resolved commit?

**Option A - Show both** (recommended):
```svelte
<script>
  let context = { type: 'asOf', asOf: '2025-11-10T10:30:00Z' };
  let resolvedCommit = null;

  async function handleExecute() {
    const response = await executeQuery(query, dataset, context);
    // Server includes resolved commit in response headers
    resolvedCommit = response.headers.get('X-Resolved-Commit');
  }
</script>

<QueryContextIndicator {context} />
{#if resolvedCommit}
  <small>(resolved to commit {resolvedCommit.slice(0, 7)})</small>
{/if}
```

**Option B - Convert to CommitContext after resolution**:
```svelte
async function handleExecute() {
  if (context.type === 'asOf') {
    // Convert to commit context after resolution
    const commit = await resolveAsOf(context.asOf);
    context = { type: 'commit', commit };
  }
  await executeQuery(query, dataset, context);
}
```

**Recommendation**: Use Option A. Keep asOf as user input but show resolved commit for transparency.
```

---

## Summary of Changes Needed

| File | Lines | Change | Priority |
|------|-------|--------|----------|
| query-context-selector-spec.md | 59-66 | Add resolution note to AsOfContext | High |
| 05-time-travel.md | 86+ | Add "Understanding Time Travel" section | High |
| 05-time-travel.md | 361+ | Note about server resolution | Medium |
| 03-version-control.md | 125+ | Add visualization modes (timeline view) | Medium |
| 01-information-architecture.md | 50-60 | Update Time Travel description | High |
| executive-summary.md | TBD | Add architecture note about commit resolution | High |
| 02-types.md | ~80 | Add comment to AsOfContext | High |
| 12-frontend-integration.md | New | Add asOf resolution pattern | Medium |

---

## Architectural Decision Required

**Question**: Should Time Travel remain a separate view, or merge with Version Control?

### Option A: Merge into Version Control
**Pros**:
- Single source of truth
- Users understand commits are fundamental
- Less code duplication

**Cons**:
- Version Control view becomes complex (operations + analysis)
- May confuse users who just want to query history

### Option B: Keep Separate Views
**Pros**:
- Separation of concerns (operations vs analysis)
- Each view stays focused
- Time Travel can have specialized features (animation, comparison)

**Cons**:
- Duplicate visualizations
- Users may not understand they're the same data

### Option C: Hybrid Approach
- Version Control is primary view (branch operations + commit graph)
- Time Travel opens Version Control in "timeline mode" with query panel
- Deep linking: `/version?view=timeline&asOf=...`

**Recommendation**: **Option C** - Best of both worlds

---

## Next Steps

1. **Decide**: Should Time Travel merge with Version Control?
2. **Update**: Apply consistency changes from table above
3. **Clarify**: Add resolution notes throughout docs
4. **Test**: Verify understanding with frontend developers

---

**Created**: 2025-11-10
**Status**: Awaiting decision on view consolidation
