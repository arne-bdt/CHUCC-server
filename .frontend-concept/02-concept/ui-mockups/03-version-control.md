# Version Control - UI Mockup

**View**: Version Control (Git-like Interface)
**Route**: `/version`
**Inspiration**: Fork Git Client (elegant DAG visualization)

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs][Version✓][Time Travel][Datasets][Batch]            │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ [🔍 Filter] [Ctrl+P Quick Launch]              [Create Branch] [Merge...] ▾ │
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────┬─────────────────────────────────────────────────────────────────┐
│ BRANCHES   │ ┌─ Commit Graph ─────────────────────────────────────────────┐ │
│ ────────── │ │    •───●───●  main (HEAD)                                  │ │
│ [Filter▾]  │ │   /        │                                               │ │
│            │ │  •         ● ← 019abc... Add user data (Alice, 2h ago)     │ │
│ Local      │ │  │        /                                                │ │
│ ★ main     │ │  •───●───●  feature/auth                                   │ │
│   feature/ │ │              ↑ Selected                                    │ │
│   auth     │ │  •─────●  bugfix/validation                                │ │
│   dev      │ │                                                            │ │
│            │ └────────────────────────────────────────────────────────────┘ │
│ Remote     │ ────────────────────────────────────────────────────────────── │
│  origin/   │ ┌─ Commit Details ───────────────────────────────────────────┐ │
│   main     │ │ Commit: 019abc... (UUIDv7)                    [Copy SHA]   │ │
│   dev      │ │ Author: Alice <alice@example.org>                          │ │
│            │ │ Date: 2025-11-10 10:30:00                                  │ │
│ TAGS       │ │ Parent: 019xyz...                                          │ │
│ ────────── │ │                                                            │ │
│ v1.0.0     │ │ Message: Add user data with age property                   │ │
│ v0.9.0     │ │                                                            │ │
│            │ │ Graphs Changed: 2                                          │ │
│ [Create]   │ │ • default (+15 triples, -3 triples)                        │ │
│            │ │ • users (+20 triples)                                      │ │
│            │ │                                                            │ │
│            │ │ ┌─ RDF Patch ───────────────────────────────────────────┐ │ │
│            │ │ │ TX .                                                   │ │ │
│            │ │ │ PA ex:Alice ex:age "31" .                              │ │ │
│            │ │ │ PD ex:Alice ex:age "30" .                              │ │ │
│            │ │ │ A ex:Bob ex:role ex:Admin .                            │ │ │
│            │ │ │ TC .                                                   │ │ │
│            │ │ └────────────────────────────────────────────────────────┘ │ │
└────────────┴─└────────────────────────────────────────────────────────────┘─┘
```

---

## 2. Component Breakdown

### 2.1 Toolbar

**Components**:
- **Filter Input**: `<Search>` (Carbon) - Filter branches/commits by name/message
- **Quick Launch Button**: `<Button>` - Opens command palette (Ctrl+P)
- **Create Branch Button**: `<Button>` - Opens modal
- **Merge Dropdown**: `<OverflowMenu>` - Merge, Rebase, Squash, Cherry-Pick, Revert, Reset

---

### 2.2 Sidebar (Branches & Tags)

**Structure**:
```
BRANCHES
├─ Filter: [Dropdown] (All, Local, Remote)
├─ Local Branches
│  ├─ ★ main (current, bold)
│  ├─ feature/auth
│  └─ dev
├─ Remote Branches (collapsible)
│  ├─ origin/main
│  └─ origin/dev
TAGS
├─ v1.0.0
└─ v0.9.0
[Create Tag] Button
```

**Features**:
- **Current Branch**: Star icon (★), bold text
- **Ahead/Behind Indicators**: ↑3 ↓2 (commits ahead/behind upstream)
- **Right-Click Context Menu**:
  - Checkout
  - Merge into current
  - Rebase current onto
  - Rename
  - Delete (confirm)
  - Set as upstream
- **Drag-and-Drop**:
  - Drag branch A onto branch B → Merge A into B (shows tooltip)
  - Alt+Drag → Rebase instead

---

### 2.3 Commit Graph (DAG Visualization)

**Technology**: D3.js or Cytoscape.js

**Visual Elements**:
- **Commit Nodes**: Circles (●) with color per branch
  - Diameter: 10px
  - Fill: Branch color
  - Border: 2px solid (darker shade)
  - Avatar: Gravatar icon (optional, 24px on hover)
- **Branch Lines**: SVG paths connecting commits
  - Width: 3px
  - Color: Branch-specific (blue for main, green for feature, etc.)
  - Merge commits: Converging lines
- **Commit Labels** (right side):
  - First line of commit message (truncated at 60 chars)
  - Author name + relative timestamp (e.g., "2h ago")
  - Branch labels (rounded badges with branch color)
- **Collapsible Merges**:
  - Click merge commit tip → collapse/expand
  - Keyboard: ← / → arrows
  - Context menu: "Collapse all branches"

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
`[Graph] [Timeline] [Calendar]`

**Interactions**:
- **Click commit**: Select (highlights commit, shows details below)
- **Double-click commit**: Navigate to Time Travel view with asOf=commitTimestamp
- **Right-click commit**: Context menu
  - Cherry-pick to...
  - Revert
  - Reset branch to here (hard/soft/mixed)
  - Create branch at this commit
  - Copy SHA
  - Compare with HEAD
- **Hover commit**: Show tooltip (full message, SHA, author, date)
- **Drag commit onto branch**: Cherry-pick

**State**:
```typescript
{
  commits: Array<{
    id: string;          // UUIDv7
    message: string;
    author: string;
    timestamp: Date;
    parents: string[];
    graphsChanged: string[];
    triplesAdded: number;
    triplesDeleted: number;
    branches: string[];  // Branch labels
    tags: string[];
  }>;
  selectedCommitId: string | null;
  collapsedMerges: Set<string>; // Commit IDs of collapsed merges
}
```

---

### 2.4 Commit Details Panel (Bottom)

**Layout**:
```
┌─ Commit Details ───────────────────────────────────────┐
│ Commit: 019abc... [Copy SHA]     Branch: main          │
│ Author: Alice <alice@example.org>                      │
│ Date: 2025-11-10 10:30:00                              │
│ Parent: 019xyz...  (click to navigate)                 │
│                                                         │
│ Message:                                                │
│ Add user data with age property                        │
│                                                         │
│ This commit adds age information for users and         │
│ updates the schema accordingly.                        │
│                                                         │
│ Graphs Changed: 2                                       │
│ • default (+15 triples, -3 triples)                     │
│ • users (+20 triples)                                   │
│                                                         │
│ ┌─ RDF Patch ──────────────────────────[Copy]────────┐ │
│ │ TX .                                               │ │
│ │ PA ex:Alice ex:age "31" .                          │ │
│ │ PD ex:Alice ex:age "30" .                          │ │
│ │ A ex:Bob ex:role ex:Admin .                        │ │
│ │ TC .                                               │ │
│ └────────────────────────────────────────────────────┘ │
│                                                         │
│ [Cherry-Pick] [Revert] [Compare with HEAD]            │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Metadata**: `<StructuredList>` (Carbon)
- **Message**: `<div>` with pre-wrap
- **Graphs Changed**: Collapsible `<Accordion>` (expand to see per-graph stats)
- **RDF Patch Viewer**: `<RdfPatchViewer>` (CHUCC-SQUI component)
  - Syntax highlighting
  - Line numbers
  - Collapsible sections (TX...TC)
  - Copy button
- **Actions**: `<ButtonSet>` (Carbon)

**Interactions**:
- **Click parent commit ID**: Navigate to that commit in graph
- **Click branch name**: Filter graph to show only that branch
- **Click graph name**: Navigate to Graph Explorer with that graph selected
- **Copy SHA button**: Copy full UUIDv7 to clipboard (toast notification)

---

### 2.5 Modals

#### Create Branch Modal

```
┌─ Create Branch ───────────────────────────────────────┐
│                                                        │
│ Branch Name: [feature/new-feature_____________]       │
│                                                        │
│ Base:                                                  │
│ ● Current HEAD (main @ 019abc...)                     │
│ ○ Specific Commit [019xyz...▾]                        │
│                                                        │
│ ☑ Checkout after creation                             │
│                                                        │
│                           [Cancel]  [Create Branch]   │
└────────────────────────────────────────────────────────┘
```

#### Merge Configuration Modal

```
┌─ Merge Branches ──────────────────────────────────────┐
│                                                        │
│ From: [feature/auth ▾]                                │
│ Into: [main ▾]                                        │
│                                                        │
│ Strategy:                                              │
│ ● three-way (default)                                 │
│ ○ ours (prefer current branch)                        │
│ ○ theirs (prefer incoming branch)                     │
│ ○ manual (resolve all conflicts manually)             │
│                                                        │
│ Fast-forward:                                          │
│ ● allow (fast-forward if possible)                    │
│ ○ only (abort if not fast-forward)                    │
│ ○ never (create merge commit)                         │
│                                                        │
│ Commit Message:                                        │
│ [Merge branch 'feature/auth' into main__________]     │
│                                                        │
│ [Preview Diff]               [Cancel]  [Merge]        │
└────────────────────────────────────────────────────────┘
```

**On Merge**:
- Success (200 OK): Toast "Merge successful", graph updates
- Conflict (409): Navigate to `/merge/conflicts` with merge ID

---

## 3. Interaction Flows

### 3.1 Create Branch

1. User clicks "Create Branch" button
2. Modal opens with form
3. User enters branch name (validates: no spaces, alphanumeric+/-/_)
4. User selects base (HEAD or specific commit)
5. User clicks "Create Branch"
6. POST `/version/refs/branches?dataset={dataset}`
7. Branch appears in sidebar
8. If "Checkout after creation" checked, branch becomes current

### 3.2 Merge Branches

1. User clicks "Merge..." dropdown → "Merge Branch"
2. Modal opens with configuration
3. User selects from/into branches
4. User selects strategy and fast-forward mode
5. User clicks "Merge"
6. POST `/version/merge?dataset={dataset}`
7. **Success** (200): Toast "Merge successful", graph refreshes
8. **Conflict** (409): Redirect to `/merge/conflicts?merge={id}`

### 3.3 Cherry-Pick Commit

1. User right-clicks commit in graph
2. Context menu: "Cherry-pick to..."
3. Submenu shows branches
4. User selects target branch
5. POST `/version/cherry-pick?dataset={dataset}`
6. **Success**: Toast "Commit cherry-picked to {branch}"
7. **Conflict**: Navigate to conflict resolver

### 3.4 Collapsible Graph

1. User sees complex merge history (many feature branches)
2. User right-clicks in graph → "Collapse all branches"
3. All merge commits collapse (feature branch commits hidden)
4. User clicks specific merge commit tip → expands that branch
5. Keyboard: ← collapses, → expands

---

## 4. Responsive Behavior

### Mobile (320px - 671px)

- **Sidebar**: Collapsed by default (hamburger menu)
- **Commit Graph**: Full width, simplified visualization (fewer details)
- **Commit Details**: Opens in modal instead of bottom panel
- **Actions**: Stacked vertically

### Tablet (672px - 1055px)

- **Sidebar**: Collapsible (toggle button), overlay when open
- **Commit Graph**: Adjustable width
- **Commit Details**: Bottom panel (collapsible)

---

## 5. Accessibility

- **Keyboard Navigation**:
  - `↑/↓`: Navigate commits
  - `Enter`: Select commit (show details)
  - `Ctrl+F`: Focus filter
  - `Ctrl+M`: Merge current branch
  - `Ctrl+N`: Create new branch
- **ARIA Labels**:
  - Commit graph: `aria-label="Commit history directed acyclic graph"`
  - Each commit node: `aria-label="Commit {SHA} by {author}, {message}"`
- **Screen Reader**: Announce commit selection ("Selected commit 019abc, message: Add user data")

---

## 6. Performance

- **Virtual Scrolling**: Graph renders visible commits only (~100 at a time)
- **Lazy Load History**: `GET /version/history?limit=100&offset=0` (paginated)
- **Incremental Graph**: Load more commits as user scrolls down
- **Debounced Filter**: Wait 300ms after typing before filtering

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
