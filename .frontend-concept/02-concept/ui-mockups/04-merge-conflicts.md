# Merge & Conflict Resolution - UI Mockup

**View**: Merge & Conflict Resolution
**Route**: `/merge/conflicts?merge={mergeId}`
**Inspiration**: Fork's 3-column merge conflict resolver + Git GUI tools

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs][Version][Time Travel][Datasets][Batch]            │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ ⚠️  Merge Conflicts: feature/auth → main                                     │
│ 3 graphs with conflicts • 2 graphs merged successfully                       │
│                                   [Abort Merge] [Resolve All ▾] [Commit...]│
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────────┬─────────────────────────────────────────────────────────────┐
│ CONFLICT LIST  │ ┌─ Graph: ex:users ──────────────────────────────────────┐ │
│ ────────────── │ │ Status: ⚠️  3 conflicts                                │ │
│ [Filter 🔍]    │ │ Resolution: [Choose Resolution ▾]                      │ │
│                │ └────────────────────────────────────────────────────────┘ │
│ ⚠️  ex:users    │ ────────────────────────────────────────────────────────── │
│    (3)      ★  │ ┌─ Three-Way Diff ───────────────────────────────────────┐ │
│                │ │ BASE (019abc...)    OURS (main)       THEIRS (feature) │ │
│ ⚠️  ex:schema   │ │ ─────────────────────────────────────────────────────│ │
│    (1)         │ │ ex:Alice            ex:Alice          ex:Alice        │ │
│                │ │   ex:age 30           ex:age 31 →      ex:age 32 →   │ │
│ ✅ ex:metadata  │ │                                                       │ │
│    (auto)      │ │ [○ Use OURS]       [○ Use THEIRS]    [● CUSTOM]      │ │
│                │ │                                                       │ │
│ ✅ ex:prefixes  │ │ ─────────────────────────────────────────────────────│ │
│    (auto)      │ │ Custom Resolution:                                    │ │
│                │ │ ┌──────────────────────────────────────────────────┐ │ │
│ [Show All]     │ │ │ ex:Alice ex:age 33 .  ← User edits               │ │ │
│                │ │ └──────────────────────────────────────────────────┘ │ │
│                │ │                                         [Accept]     │ │
│                │ └────────────────────────────────────────────────────────┘ │
│                │ ────────────────────────────────────────────────────────── │
│                │ ┌─ Conflict 2 of 3 ──────────────────────────────────────┐ │
│                │ │ BASE (019abc...)    OURS (main)       THEIRS (feature) │ │
│                │ │ ex:Bob              ex:Bob            [DELETED]        │ │
│                │ │   ex:role Admin       ex:role User →                   │ │
│                │ │                                                        │ │
│                │ │ [○ Keep (OURS)]    [○ Delete (THEIRS)]  [○ CUSTOM]   │ │
│                │ └────────────────────────────────────────────────────────┘ │
└────────────────┴─────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Merge Header Banner

**Layout**:
```
┌──────────────────────────────────────────────────────────────┐
│ ⚠️  Merge Conflicts: feature/auth → main                     │
│ 3 graphs with conflicts • 2 graphs merged successfully       │
│                   [Abort Merge] [Resolve All ▾] [Commit...] │
└──────────────────────────────────────────────────────────────┘
```

**Components**:
- **Status Banner**: `<InlineNotification kind="warning">` (Carbon)
  - Shows source branch → target branch
  - Conflict summary: "X graphs with conflicts • Y graphs merged successfully"
- **Abort Button**: `<Button kind="danger-ghost">` - Cancels merge, returns to previous state
- **Resolve All Dropdown**: `<OverflowMenu>` - Bulk resolution strategies:
  - Accept All Ours (prefer current branch)
  - Accept All Theirs (prefer incoming branch)
  - Auto-Resolve (use merge strategy from merge config)
- **Commit Button**: `<Button kind="primary">` - Disabled until all conflicts resolved

**State**:
```typescript
interface MergeState {
  mergeId: string;           // UUIDv7 from merge operation
  sourceBranch: string;      // "feature/auth"
  targetBranch: string;      // "main"
  baseCommit: string;        // Common ancestor
  ourCommit: string;         // Current branch HEAD
  theirCommit: string;       // Incoming branch HEAD
  strategy: MergeStrategy;   // 'three-way' | 'ours' | 'theirs' | 'manual'
  conflictedGraphs: string[];
  resolvedGraphs: string[];
  autoMergedGraphs: string[];
}
```

---

### 2.2 Conflict List Sidebar

**Structure**:
```
┌─ CONFLICT LIST ────┐
│ [Filter 🔍]        │
│ ──────────────────│
│ ⚠️  ex:users (3) ★  │ ← Selected, 3 conflicts
│                    │
│ ⚠️  ex:schema (1)   │
│                    │
│ ✅ ex:metadata      │ ← Auto-resolved
│    (auto)          │
│                    │
│ ✅ ex:prefixes      │ ← Auto-resolved
│    (auto)          │
│                    │
│ [Show All Graphs]  │ ← Toggle to show non-conflicted
└────────────────────┘
```

**Components**:
- **Filter Input**: `<Search size="sm">` - Filter by graph URI
- **Graph Items**: `<TreeNode>` with status icons:
  - ⚠️  Conflict (red icon) - Requires resolution
  - ✅ Resolved (green checkmark) - User resolved
  - ✅ Auto (green checkmark with "(auto)") - No conflicts, auto-merged
- **Conflict Count**: Number in parentheses (e.g., "(3)") shows conflict count
- **Selected Indicator**: Star icon (★) for currently viewed graph
- **Show All Toggle**: `<Toggle>` - Shows all graphs (including non-conflicted)

**Interactions**:
- **Click graph**: Load conflicts for that graph in main panel
- **Hover**: Show tooltip with full URI and conflict summary

---

### 2.3 Graph Conflict Header

**Layout**:
```
┌─ Graph: ex:users ──────────────────────────────────────┐
│ Status: ⚠️  3 conflicts                                │
│ Resolution: [Choose Resolution ▾]                      │
└────────────────────────────────────────────────────────┘
```

**Components**:
- **Graph URI**: `<h2>` with full URI
- **Status**: Icon + text showing conflict count
- **Bulk Resolution Dropdown**: `<Dropdown>` (Carbon)
  - Accept All OURS for this graph
  - Accept All THEIRS for this graph
  - Reset All (undo resolutions for this graph)

---

### 2.4 Three-Way Diff View (Individual Conflict)

**Layout**:
```
┌─ Three-Way Diff ───────────────────────────────────────┐
│ BASE (019abc...)    OURS (main)       THEIRS (feature) │
│ ─────────────────────────────────────────────────────  │
│ ex:Alice            ex:Alice          ex:Alice         │
│   ex:age 30           ex:age 31 →      ex:age 32 →    │ ← Conflict
│   ex:role User        ex:role User     ex:role Admin  │ ← Conflict
│                                                         │
│ [○ Use OURS]       [○ Use THEIRS]    [● CUSTOM]       │ ← Radio buttons
│                                                         │
│ ─────────────────────────────────────────────────────  │
│ Custom Resolution:                                      │
│ ┌──────────────────────────────────────────────────┐  │
│ │ ex:Alice ex:age 33 .                             │  │ ← User edits
│ │ ex:Alice ex:role Admin .                         │  │
│ └──────────────────────────────────────────────────┘  │
│                                         [Accept]       │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Three Columns**: Side-by-side diff (BASE | OURS | THEIRS)
  - **BASE**: Common ancestor state (gray background)
  - **OURS**: Current branch state (blue background)
  - **THEIRS**: Incoming branch state (green background)
- **Conflict Highlighting**:
  - Additions: Green background (OURS) or green underline (THEIRS)
  - Deletions: Red strikethrough
  - Modifications: Yellow background with arrow (→)
- **Resolution Radio Buttons**:
  - **Use OURS**: Accept current branch's changes
  - **Use THEIRS**: Accept incoming branch's changes
  - **CUSTOM**: Manual resolution (enables editor below)
- **Custom Editor**: `<CodeMirror>` with RDF Patch syntax highlighting
  - Pre-filled with "OURS" by default when CUSTOM selected
  - User can edit to create custom resolution
- **Accept Button**: `<Button kind="primary">` - Saves resolution for this conflict

**Conflict Types**:

1. **Modification Conflict** (both branches modified same triple):
   ```
   BASE:   ex:Alice ex:age 30 .
   OURS:   ex:Alice ex:age 31 .
   THEIRS: ex:Alice ex:age 32 .
   ```

2. **Add-Add Conflict** (both branches added different values):
   ```
   BASE:   [no triple]
   OURS:   ex:Alice ex:role User .
   THEIRS: ex:Alice ex:role Admin .
   ```

3. **Delete-Modify Conflict** (one deleted, one modified):
   ```
   BASE:   ex:Bob ex:role Admin .
   OURS:   ex:Bob ex:role User .
   THEIRS: [deleted]
   ```

4. **Add-Modify Conflict** (one added, one modified existing):
   ```
   BASE:   ex:Carol ex:age 25 .
   OURS:   ex:Carol ex:age 26 .
   THEIRS: ex:Carol ex:age 25 .
             ex:Carol ex:role Manager .  ← Added
   ```

---

### 2.5 Conflict Navigation

**Layout**:
```
┌─ Conflict 2 of 3 ──────────────────────────────────────┐
│ [◀ Previous]                            [Next Conflict ▶]│
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Conflict Counter**: Shows current conflict index (e.g., "Conflict 2 of 3")
- **Previous Button**: `<Button kind="ghost">` - Navigate to previous conflict
- **Next Button**: `<Button kind="primary">` - Navigate to next conflict (disabled on last)

**Keyboard Shortcuts**:
- `Ctrl+←`: Previous conflict
- `Ctrl+→`: Next conflict
- `Ctrl+1`: Select "Use OURS"
- `Ctrl+2`: Select "Use THEIRS"
- `Ctrl+3`: Select "CUSTOM"
- `Enter`: Accept current resolution

---

### 2.6 Commit Merge Modal

**Triggered when**: User clicks "Commit..." button after resolving all conflicts

**Layout**:
```
┌─ Commit Merge ─────────────────────────────────────────┐
│                                                         │
│ All conflicts resolved. Ready to commit merge.          │
│                                                         │
│ Merge: feature/auth → main                              │
│                                                         │
│ Commit Message:                                         │
│ ┌─────────────────────────────────────────────────────┐│
│ │ Merge branch 'feature/auth' into main               ││
│ │                                                     ││
│ │ Conflicts resolved:                                 ││
│ │ - ex:users (3 conflicts)                           ││
│ │ - ex:schema (1 conflict)                           ││
│ └─────────────────────────────────────────────────────┘│
│                                                         │
│ Graphs Changed: 4                                       │
│ • ex:users (+12 triples, -5 triples)                   │
│ • ex:schema (+3 triples, -1 triple)                    │
│ • ex:metadata (+5 triples)                             │
│ • ex:prefixes (+2 triples)                             │
│                                                         │
│                          [Cancel]  [Commit Merge]      │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Merge Summary**: Shows source → target branches
- **Commit Message Editor**: `<TextArea>` (Carbon) - Pre-filled, user can edit
- **Graphs Changed**: `<Accordion>` - Expandable list of changed graphs with stats
- **Commit Button**: `<Button kind="primary">` - Submits merge with resolutions

**On Commit**:
1. POST `/version/merge/commit?dataset={dataset}&merge={mergeId}`
2. Body: JSON with resolutions for each conflicted graph
3. Response: New commit ID
4. Redirect to `/version` with new merge commit selected
5. Toast notification: "Merge committed successfully"

---

### 2.7 Abort Merge Confirmation

**Triggered when**: User clicks "Abort Merge" button

**Layout**:
```
┌─ Abort Merge? ─────────────────────────────────────────┐
│                                                         │
│ ⚠️  Are you sure you want to abort this merge?          │
│                                                         │
│ All conflict resolutions will be discarded.            │
│ This action cannot be undone.                          │
│                                                         │
│                          [Cancel]  [Abort Merge]       │
└─────────────────────────────────────────────────────────┘
```

**On Abort**:
1. DELETE `/version/merge/{mergeId}?dataset={dataset}`
2. Redirect to `/version`
3. Toast notification: "Merge aborted"

---

## 3. Interaction Flows

### 3.1 Automatic Conflict Resolution

**Context**: User initiated merge via Version Control view, merge returned 409 Conflict

1. User redirected to `/merge/conflicts?merge={id}`
2. Merge header loads: "⚠️ Merge Conflicts: feature/auth → main"
3. Conflict list loads:
   - ⚠️ ex:users (3 conflicts)
   - ⚠️ ex:schema (1 conflict)
   - ✅ ex:metadata (auto-merged)
   - ✅ ex:prefixes (auto-merged)
4. First conflicted graph (ex:users) automatically selected
5. Three-way diff loads for first conflict

### 3.2 Resolve Single Conflict

1. User views conflict: `ex:Alice ex:age` modified in both branches (31 vs 32)
2. User clicks radio button "Use THEIRS" (accept age 32)
3. Button changes color to indicate selection
4. User clicks "Accept" button
5. Conflict marked as resolved (checkmark appears)
6. View automatically advances to next conflict
7. If last conflict: "Commit..." button becomes enabled

### 3.3 Custom Conflict Resolution

1. User views conflict: `ex:Alice ex:age` (31 vs 32)
2. User selects "CUSTOM" radio button
3. Custom editor appears, pre-filled with OURS value (31)
4. User edits to: `ex:Alice ex:age 33 .`
5. User clicks "Accept"
6. Custom resolution saved
7. View advances to next conflict

### 3.4 Bulk Resolution for Graph

1. User views graph "ex:users" with 3 conflicts
2. User clicks "Resolution: [Choose Resolution ▾]" dropdown
3. User selects "Accept All THEIRS for this graph"
4. All 3 conflicts in ex:users automatically resolved with THEIRS
5. Graph marked as ✅ Resolved in sidebar
6. View moves to next conflicted graph (ex:schema)

### 3.5 Commit Merge

1. User resolves all conflicts (sidebar shows all ✅)
2. "Commit..." button becomes enabled
3. User clicks "Commit..."
4. Modal opens with pre-filled commit message
5. User reviews changed graphs summary
6. User optionally edits commit message
7. User clicks "Commit Merge"
8. POST request sent with all resolutions
9. Modal closes, redirect to Version Control view
10. Toast notification: "Merge committed successfully"
11. Commit graph shows new merge commit

---

## 4. State Management

### 4.1 Merge State (Global Store)

```typescript
// stores/merge.ts
export interface MergeState {
  mergeId: string;
  sourceBranch: string;
  targetBranch: string;
  baseCommit: string;
  ourCommit: string;
  theirCommit: string;
  strategy: MergeStrategy;

  // Graph-level state
  graphs: Array<{
    uri: string;
    status: 'conflicted' | 'resolved' | 'auto-merged';
    conflictCount: number;
    conflicts: Conflict[];
  }>;

  // Resolution state
  resolutions: Map<string, Resolution>;  // Key: graph URI + conflict index

  // UI state
  selectedGraphUri: string | null;
  currentConflictIndex: number;
}

export interface Conflict {
  index: number;
  base: Triple[];      // Triples in common ancestor
  ours: Triple[];      // Triples in current branch
  theirs: Triple[];    // Triples in incoming branch
  type: 'modify-modify' | 'add-add' | 'delete-modify' | 'add-modify';
}

export interface Resolution {
  strategy: 'ours' | 'theirs' | 'custom';
  customPatch?: string;  // RDF Patch if strategy is 'custom'
}

export const mergeState = writable<MergeState | null>(null);
```

### 4.2 API Service

```typescript
// services/MergeService.ts
class MergeService {
  async getMergeConflicts(dataset: string, mergeId: string): Promise<MergeState> {
    const response = await fetch(`/version/merge/${mergeId}?dataset=${dataset}`);
    return response.json();
  }

  async resolveConflict(
    dataset: string,
    mergeId: string,
    graphUri: string,
    conflictIndex: number,
    resolution: Resolution
  ): Promise<void> {
    await fetch(`/version/merge/${mergeId}/resolve?dataset=${dataset}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        graph: graphUri,
        conflictIndex,
        resolution
      })
    });
  }

  async commitMerge(
    dataset: string,
    mergeId: string,
    commitMessage: string,
    resolutions: Map<string, Resolution>
  ): Promise<{ commitId: string }> {
    const response = await fetch(`/version/merge/${mergeId}/commit?dataset=${dataset}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'SPARQL-VC-Author': this.getCurrentAuthor()
      },
      body: JSON.stringify({
        message: commitMessage,
        resolutions: Object.fromEntries(resolutions)
      })
    });
    return response.json();
  }

  async abortMerge(dataset: string, mergeId: string): Promise<void> {
    await fetch(`/version/merge/${mergeId}?dataset=${dataset}`, {
      method: 'DELETE'
    });
  }
}
```

---

## 5. Advanced Features

### 5.1 Conflict Visualization Modes

**Table View** (Default):
```
┌─ Conflicts (Table) ────────────────────────────────────┐
│ #  Subject    Predicate  BASE     OURS      THEIRS     │
│ ────────────────────────────────────────────────────── │
│ 1  ex:Alice   ex:age     30       31 →      32 →      │
│ 2  ex:Alice   ex:role    User     User      Admin →   │
│ 3  ex:Bob     ex:role    Admin    User →    [DELETE]  │
└─────────────────────────────────────────────────────────┘
```

**Unified Diff View**:
```
┌─ Conflicts (Unified Diff) ─────────────────────────────┐
│ @@  ex:Alice                                           │
│ -   ex:age 30         (BASE)                           │
│ +   ex:age 31         (OURS)                           │
│ +   ex:age 32         (THEIRS) ← Conflict             │
│                                                        │
│ -   ex:role User      (BASE)                           │
│ +   ex:role Admin     (THEIRS) ← Conflict             │
└─────────────────────────────────────────────────────────┘
```

**Side-by-Side Diff View** (Default):
- Three columns as shown in main wireframe

**View Selector**:
```
Format: [Side-by-Side ▾]  (Table | Unified Diff | Side-by-Side)
```

### 5.2 Conflict Statistics

**Shown in sidebar or collapsible panel**:
```
┌─ Merge Statistics ─────────────────────────────────────┐
│ Total Graphs: 6                                         │
│ • Conflicted: 2 (33%)                                  │
│ • Auto-Merged: 4 (67%)                                 │
│                                                         │
│ Total Conflicts: 4                                      │
│ • Resolved: 2 (50%)                                    │
│ • Pending: 2 (50%)                                     │
│                                                         │
│ Resolution Methods:                                     │
│ • OURS: 1 conflict                                     │
│ • THEIRS: 0 conflicts                                  │
│ • CUSTOM: 1 conflict                                   │
└─────────────────────────────────────────────────────────┘
```

### 5.3 Conflict History

**Track resolution decisions for audit trail**:
```typescript
interface ConflictResolutionHistory {
  graphUri: string;
  conflictIndex: number;
  timestamp: Date;
  strategy: 'ours' | 'theirs' | 'custom';
  customPatch?: string;
  resolvedBy: string;  // Author
}
```

**Displayed in "History" tab**:
```
┌─ Resolution History ───────────────────────────────────┐
│ 10:45:30  ex:users #1   THEIRS    (Alice)             │
│ 10:46:12  ex:users #2   CUSTOM    (Alice)             │
│ 10:47:05  ex:schema #1  OURS      (Alice)             │
└─────────────────────────────────────────────────────────┘
```

### 5.4 Auto-Resolution Hints

**For common patterns, suggest resolutions**:
```
┌─ Suggested Resolution ─────────────────────────────────┐
│ 💡 Hint: THEIRS adds more recent data (timestamp).     │
│    Consider accepting THEIRS for this conflict.        │
│                                     [Accept Suggestion] │
└─────────────────────────────────────────────────────────┘
```

**Heuristics**:
- If THEIRS has more recent timestamp literal → Suggest THEIRS
- If OURS only changes formatting → Suggest THEIRS
- If conflict is in metadata graph → Suggest manual review

---

## 6. Responsive Behavior

### Mobile (320px - 671px)

- **Conflict List**: Collapsible drawer (hamburger menu)
- **Three-Way Diff**: Stacked vertical layout (BASE → OURS → THEIRS)
- **Resolution Buttons**: Full-width, stacked vertically
- **Custom Editor**: Full-screen overlay when editing

### Tablet (672px - 1055px)

- **Conflict List**: Collapsible sidebar (200px width)
- **Three-Way Diff**: Two-column layout (OURS | THEIRS), BASE shown in header
- **Navigation**: Compact buttons at bottom

---

## 7. Accessibility

### Keyboard Navigation

- **Tab Order**: Conflict List → Resolution Options → Custom Editor → Accept Button → Next Conflict
- **Ctrl+1/2/3**: Select OURS/THEIRS/CUSTOM radio button
- **Ctrl+Enter**: Accept current resolution
- **Ctrl+←/→**: Previous/Next conflict
- **Escape**: Close modal, abort operation (with confirmation)

### ARIA Labels

- **Conflict List**: `aria-label="List of conflicted graphs"`
- **Conflict Item**: `aria-label="Graph {uri}, {count} conflicts, {status}"`
- **Three-Way Diff**: `aria-label="Three-way conflict diff for {graph}"`
- **Resolution Radio**: `aria-label="Use {strategy} resolution"`

### Screen Reader Announcements

- "Conflict {index} of {total} loaded."
- "Resolution accepted. Moving to next conflict."
- "All conflicts resolved. Ready to commit merge."

---

## 8. Error Handling

### Error Scenarios

1. **Merge Session Expired**: 410 Gone from API
   - Show error: "Merge session expired. Please restart merge."
   - Offer button: "Return to Version Control"

2. **Invalid Resolution**: 400 Bad Request
   - Show inline error: "Custom resolution contains invalid RDF syntax."
   - Highlight error line in CodeMirror editor

3. **Network Failure**: Timeout or 5xx error
   - Show retry banner: "Failed to save resolution. Retry?"
   - Auto-save resolutions locally (localStorage) for recovery

4. **Concurrent Modification**: 409 Conflict
   - Show warning: "Another user modified this branch. Merge no longer valid."
   - Offer button: "Refresh Merge" (fetches latest state)

---

## 9. Performance

### Optimization Strategies

1. **Lazy Load Conflicts**: Load conflicts only when graph selected
2. **Virtual Scrolling**: For graphs with 100+ conflicts
3. **Debounced Validation**: Wait 500ms after typing in custom editor before validating
4. **Local Caching**: Cache resolutions in localStorage (recover on refresh)

### Performance Targets

- **Load Merge State**: < 500ms for merge with 10 conflicted graphs
- **Switch Graph**: < 200ms to load next graph's conflicts
- **Accept Resolution**: < 100ms to update UI state
- **Commit Merge**: < 2s to process all resolutions and create commit

---

## 10. Testing Considerations

### Unit Tests

- Conflict detection logic (modification, add-add, delete-modify)
- Resolution validation (valid RDF syntax for custom resolutions)
- Bulk resolution application (accept all OURS/THEIRS for graph)

### Integration Tests

- Load merge conflicts from API
- Resolve single conflict
- Bulk resolve all conflicts in graph
- Commit merge with resolutions
- Abort merge

### E2E Tests (Playwright)

1. **Full Merge Resolution Flow**:
   - Initiate merge from Version Control view
   - Get 409 Conflict response
   - Redirect to conflict resolver
   - Resolve first conflict with OURS
   - Resolve second conflict with CUSTOM
   - Commit merge
   - Verify merge commit appears in commit graph

2. **Abort Merge Flow**:
   - Start conflict resolution
   - Click "Abort Merge"
   - Confirm abort
   - Verify redirect to Version Control
   - Verify no merge commit created

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
