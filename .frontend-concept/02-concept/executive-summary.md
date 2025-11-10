# CHUCC Frontend Concept - Executive Summary

**Date**: 2025-11-10
**Version**: 1.0

---

## 1. Project Overview

The CHUCC frontend combines the **query-focused simplicity of Apache Jena Fuseki** with the **elegant version control UX of Fork Git Client**, creating a powerful interface for SPARQL query execution and RDF dataset version control.

**Technology Stack**:
- **Framework**: Svelte 5
- **Design System**: IBM Carbon Design System
- **Component Library**: CHUCC-SQUI (SPARQL query components)
- **Build Tool**: Vite
- **Testing**: Vitest + Playwright

---

## 2. Research Findings Summary

### 2.1 Fuseki UI Analysis (3,500 words)

**Strengths to Emulate**:
- ✅ Tab-based navigation (Query, Upload, Edit, Info)
- ✅ YASQE/YASR components (query editor + results)
- ✅ Split-pane layout (editor + results)
- ✅ Dataset selector dropdown
- ✅ Prefix management modal
- ✅ Simple, functional design

**Gaps to Address**:
- ❌ No version control
- ❌ Limited dataset management
- ❌ No batch operations
- ❌ Basic results display

### 2.2 Fork UX Analysis (5,000 words)

**Patterns to Adopt**:
- ✅ Three-pane layout (sidebar | graph | details)
- ✅ Colored branch graph with collapsible merges
- ✅ Drag-and-drop operations (merge, rebase, cherry-pick)
- ✅ Quick Launch command palette (Ctrl+P)
- ✅ Side-by-side diff viewer
- ✅ Three-column merge conflict resolver
- ✅ Interactive rebase with visual drag-to-reorder
- ✅ Right-click context menus

**Adaptations for RDF**:
- File tree → Graph list
- Text diff → RDF Patch viewer
- File conflicts → Triple/quad conflicts
- Working directory → Batch operation builder

### 2.3 Component Gap Analysis (6,000 words)

**Available Components**:
- Carbon: 165+ components (complete standard UI)
- CHUCC-SQUI: 8 components (SPARQL query + results)

**Missing Components**: 15+
- **CHUCC-SQUI (7 new)**: RDF Patch Viewer, RDF Diff Viewer, Graph Explorer, Commit SHA Badge, Author Input, 3-Pane Diff View, RDF Patch Language Mode
- **Frontend (11 new)**: Commit Graph (DAG), Branch Selector, Commit Detail, History Filter, Time Travel Controls, Conflict Summary/Resolver, Merge Modal, Dataset Wizard/Health/Switcher, Batch Builder/Preview, Quick Launch

---

## 3. Information Architecture

### 3.1 Primary Views (7)

1. **Query Workbench** (`/query`) - SPARQL query interface
2. **Graph Explorer** (`/graphs`) - Graph Store Protocol operations
3. **Version Control** (`/version`) - Commit history, branches, tags
4. **Merge & Conflicts** (`/merge/conflicts`) - Conflict resolution
5. **Time Travel** (`/time-travel`) - Historical queries (asOf)
6. **Dataset Manager** (`/datasets`) - Multi-dataset management
7. **Batch Operations** (`/batch`) - Atomic batch operations

### 3.2 Navigation Pattern

**Primary**: Horizontal tabs (Carbon `<HeaderNavigation>`)
**Secondary**: Contextual sidebars (branches/tags in Version Control, graphs in Graph Explorer)
**Tertiary**: Modals and panels (prefix manager, create dataset wizard)

### 3.3 Routing

```
/                          → /query (default)
/query                     → Query Workbench
/graphs                    → Graph Explorer
/version                   → Version Control
/merge/conflicts           → Merge Conflicts
/time-travel               → Time Travel Queries
/datasets                  → Dataset Manager
/batch                     → Batch Operations
/settings                  → Settings
```

**Query Parameters** (global):
- `dataset={name}` - Current dataset
- `branch={name}` - Current branch
- `commit={id}` - Selected commit
- `asOf={timestamp}` - Time-travel timestamp

---

## 4. UI Mockups

### 4.1 Query Workbench (Complete - 5,500 words)

**Layout**: Split-pane (editor top, results bottom)

**Key Features**:
- Multi-tab query editor (CodeMirror with SPARQL syntax)
- Virtual scrolling results table (10k+ rows)
- Branch/asOf selector for time-travel queries
- Prefix manager modal
- Query history sidebar
- Export (CSV, JSON, Turtle, etc.)

**State Management**:
- Local: Tabs, query text, results
- Global: Current dataset, author
- Persistence: localStorage (auto-save every 5s)

### 4.2 Graph Explorer

**Layout**: Sidebar (graph list) + Main (graph viewer)

**Key Features**:
- Named graph list (sidebar, collapsible tree)
- Triple table/tree view (CodeMirror or Carbon DataTable)
- Upload graph (file uploader + drag-drop)
- GSP operations (PUT, POST, DELETE, PATCH)
- Filter triples by subject/predicate/object

### 4.3 Version Control (Complete - 3,000 words)

**Layout**: Three-pane (sidebar | graph | details)

**Key Features**:
- **Sidebar**: Branches (local/remote), tags, filter input
- **Graph**: Colored DAG visualization (D3.js or Cytoscape.js)
  - Commit nodes with avatars
  - Branch lines (colored, 3px width)
  - Collapsible merge commits
  - Drag-and-drop for merge/cherry-pick
- **Details**: Commit metadata, RDF Patch viewer, actions
- **Modals**: Create branch, merge configuration, cherry-pick

**Interactions**:
- Click commit → show details
- Right-click commit → context menu (cherry-pick, revert, reset)
- Double-click commit → time-travel to that commit
- Drag branch → merge/rebase

### 4.4 Merge & Conflicts

**Layout**: Sidebar (conflict list) + Main (3-pane resolver)

**Key Features**:
- **Conflict Summary**: "12 graphs in conflict, 45 triples"
- **Conflict List**: Accordion (graph-by-graph)
- **3-Pane Resolver**: Base | Ours | Theirs
  - RDF syntax highlighting
  - One-click resolution buttons
  - Manual editing
- **Navigation**: Prev/Next conflict buttons
- **Graph-Level Resolution**: "Accept Ours for all in graph"

### 4.5 Time Travel

**Layout**: Query Workbench + Timeline controls

**Key Features**:
- **asOf Selector**: Date/time picker + commit selector
- **Timeline Slider**: Visual timeline with commits
- **Comparison Mode**: Side-by-side results (time A vs time B)
- **Query Indicator**: "Results as of 2025-11-10 10:30:00"

### 4.6 Dataset Manager

**Layout**: Table view

**Key Features**:
- **Dataset Table**: Name, description, main branch, created date, health
- **Create Wizard**: Step-by-step (name, description, Kafka config, initial graph)
- **Health Dashboard**: Kafka topic status, partition count, consumer lag
- **Actions**: Delete dataset (confirmation required)

### 4.7 Batch Operations

**Layout**: Operation builder (table + preview)

**Key Features**:
- **Operation List**: Table with type, graph, content, order
- **Add Operation**: Modal (type selector, graph input, content editor)
- **Reorder**: Drag handles or up/down buttons
- **Mode Selector**: Single-commit vs multi-commit
- **Preview**: Shows resulting RDF Patch
- **Execute**: Confirmation modal

---

## 5. Visual Design System

### 5.1 Color Palette (Carbon + Semantic)

**Carbon Themes**: White, G10 (default), G90, G100

**Semantic Colors**:
- **Branch Colors** (for graph):
  - main: Blue (#0f62fe)
  - dev: Green (#24a148)
  - feature/*: Orange (#ff832b)
  - bugfix/*: Red (#da1e28)
  - release/*: Purple (#8a3ffc)
- **Diff Colors**:
  - Added: Green (#24a148)
  - Deleted: Red (#da1e28)
  - Modified: Yellow (#f1c21b)
- **Status Colors**:
  - Success: Green (#24a148)
  - Warning: Yellow (#f1c21b)
  - Error: Red (#da1e28)
  - Info: Blue (#0f62fe)

### 5.2 Typography

- **Body**: IBM Plex Sans (14px regular)
- **Code**: IBM Plex Mono (14px regular)
- **Headings**: IBM Plex Sans (H1: 24px, H2: 20px, H3: 16px)
- **Captions**: IBM Plex Sans (12px)

### 5.3 Iconography

**Carbon Icons** (carbon-icons-svelte):
- Branch: `<Branch />` icon
- Tag: `<Tag />` icon
- Commit: Custom dot/circle SVG (10px diameter)
- Merge: Converging arrows
- Conflict: `<Warning />` icon
- Ahead/Behind: `<ArrowUp />` / `<ArrowDown />`
- Settings: `<Settings />` icon

### 5.4 Spacing (8px Grid)

- **Padding**: 8px, 16px, 24px, 32px
- **Margins**: 8px, 16px, 24px, 32px
- **Component Spacing**: 16px between components
- **Sidebar Width**: 240px (adjustable)
- **Commit Graph Row Height**: 32px (compact: 24px)

---

## 6. Component Architecture

### 6.1 Component Tree

```
App
├── Header (Carbon)
│   ├── Logo
│   ├── PrimaryNav (tabs)
│   ├── DatasetSelector (Dropdown)
│   └── AuthorSelector (ComboBox)
│
├── Router (client-side)
│   ├── QueryWorkbench
│   │   ├── QueryEditor (CHUCC-SQUI)
│   │   ├── ResultsTable (CHUCC-SQUI)
│   │   ├── PrefixManager (modal)
│   │   └── QueryHistory (sidebar)
│   │
│   ├── GraphExplorer
│   │   ├── GraphList (sidebar)
│   │   ├── GraphViewer (CHUCC-SQUI)
│   │   └── GraphUploader (modal)
│   │
│   ├── VersionControl
│   │   ├── BranchSidebar
│   │   ├── CommitGraph (D3.js/Cytoscape.js)
│   │   ├── CommitDetails (bottom panel)
│   │   │   └── RdfPatchViewer (CHUCC-SQUI)
│   │   └── Modals (CreateBranch, MergeConfig)
│   │
│   ├── MergeConflicts
│   │   ├── ConflictSidebar
│   │   └── ConflictResolver (3-pane)
│   │       └── RdfDiffViewer (CHUCC-SQUI)
│   │
│   ├── TimeTravelQuery
│   │   ├── TimelineSlider
│   │   ├── AsOfPicker
│   │   └── QueryWorkbench (enhanced)
│   │
│   ├── DatasetManager
│   │   ├── DatasetTable
│   │   ├── CreateDatasetWizard (modal)
│   │   └── HealthDashboard
│   │
│   └── BatchOperations
│       ├── OperationBuilder
│       ├── OperationList
│       └── BatchPreview
│
├── Footer
│   ├── StatusBar (dataset, branch, author)
│   └── NotificationArea (toasts)
│
└── GlobalModals
    ├── QuickLaunch (Ctrl+P)
    └── Settings
```

### 6.2 State Management (Svelte Stores)

**Global Stores**:
```typescript
// appStore.ts
export const appStore = writable({
  currentDataset: 'default',
  currentAuthor: 'Alice <alice@example.org>',
  availableDatasets: string[],
  theme: 'g10',
});

// queryStore.ts
export const queryStore = writable({
  tabs: QueryTab[],
  activeTabId: string,
  history: QueryHistoryItem[],
  savedQueries: SavedQuery[],
});

// versionStore.ts
export const versionStore = writable({
  commits: Commit[],
  branches: Branch[],
  tags: Tag[],
  selectedCommitId: string | null,
  collapsedMerges: Set<string>,
});
```

**Service Layer** (API calls):
```typescript
// services/versionControlService.ts
export class VersionControlService {
  async getCommits(dataset: string, branch?: string): Promise<Commit[]> { ... }
  async merge(dataset: string, from: string, into: string, strategy: MergeStrategy): Promise<MergeResult> { ... }
  async cherryPick(dataset: string, commitId: string, onto: string): Promise<Commit> { ... }
  async createBranch(dataset: string, name: string, base: string): Promise<Branch> { ... }
  // ...
}
```

---

## 7. Interaction Flows

### 7.1 Execute SPARQL Query with Time Travel

1. User navigates to Query Workbench (`/query`)
2. User selects dataset ("my-dataset") from header dropdown
3. User selects branch ("main") from editor toolbar
4. User clicks "asOf: Latest" → selects "Specific Date/Time"
5. Date picker opens, user selects "2025-11-10 10:00:00"
6. User types SPARQL query in editor
7. User presses Ctrl+Enter to execute
8. Query sent: `POST /query?dataset=my-dataset&branch=main&asOf=2025-11-10T10:00:00Z`
9. Results appear in table (with notification: "Results as of 2025-11-10 10:00:00")
10. User clicks export dropdown → "Download as CSV"

### 7.2 Create Branch and Commit Changes

1. User navigates to Version Control (`/version`)
2. User sees commit graph with current branch "main"
3. User clicks "Create Branch" button
4. Modal opens: User enters "feature/new-data", selects base "HEAD", checks "Checkout after creation"
5. User clicks "Create Branch" → `POST /version/refs/branches?dataset=default`
6. New branch appears in sidebar (highlighted as current)
7. User navigates to Graph Explorer (`/graphs`)
8. User selects "default" graph from sidebar
9. User clicks "Upload File" → uploads Turtle file
10. Modal shows: "Upload will create a commit. Enter commit message."
11. User enters message: "Add new user data"
12. User clicks "Upload" → `PUT /graphs/default` with file content
13. Server returns `202 Accepted` with commit ID
14. Toast notification: "Commit created: 019abc..."
15. User navigates back to Version Control → sees new commit in graph

### 7.3 Merge Branches with Conflict Resolution

1. User in Version Control view, branch "main"
2. User clicks "Merge..." dropdown → "Merge Branch"
3. Modal opens: From "feature/new-data", Into "main", Strategy "three-way"
4. User clicks "Merge" → `POST /version/merge?dataset=default`
5. Server returns `409 Conflict` with conflict data
6. User automatically redirected to `/merge/conflicts?merge={id}`
7. Conflict summary shows: "5 graphs in conflict, 12 triples"
8. User expands "default" graph in sidebar
9. 3-pane resolver shows: Base | Ours | Theirs
10. User sees conflicting triple: `ex:Alice ex:age "30"` (Ours) vs `"31"` (Theirs)
11. User clicks "Accept Theirs" button
12. Conflict resolved, UI updates (green checkmark)
13. User navigates to next conflict (4 remaining)
14. After resolving all, "Complete Merge" button enabled
15. User clicks "Complete Merge" → `POST /version/merge` (with resolution data)
16. Server returns `200 OK` with merge commit ID
17. User redirected to Version Control → sees merge commit in graph

### 7.4 Cherry-Pick Commit

1. User in Version Control view, sees commit "019abc..." on "feature/auth" branch
2. User right-clicks commit → "Cherry-pick to..."
3. Submenu shows branches: main, dev
4. User clicks "main"
5. `POST /version/cherry-pick?dataset=default` (body: `{commit: "019abc", onto: "main"}`)
6. **Success**: Toast "Commit cherry-picked to main", graph updates with new commit
7. **Conflict**: Redirect to conflict resolver (same as merge flow)

---

## 8. Accessibility (WCAG 2.1 AA)

### 8.1 Keyboard Navigation

**Global Shortcuts**:
- `Ctrl+P` / `⌘+P`: Quick Launch
- `Ctrl+1` ... `Ctrl+7`: Jump to primary nav tab
- `Ctrl+K` / `⌘+K`: Focus dataset selector
- `Esc`: Close modal/panel

**View-Specific Shortcuts**:
- Query Workbench: `Ctrl+Enter` (execute), `Ctrl+S` (save)
- Version Control: `↑/↓` (navigate commits), `Ctrl+M` (merge)
- Merge Conflicts: `Ctrl+O` (accept ours), `Ctrl+T` (accept theirs), `Ctrl+]` (next conflict)

### 8.2 Screen Reader Support

- ARIA labels on all interactive elements
- ARIA landmarks (`<nav>`, `<main>`, `<aside>`)
- ARIA live regions for dynamic content (results, notifications)
- Alt text for icons

### 8.3 Color Contrast

- Text: 4.5:1 minimum (body)
- UI elements: 3:1 minimum (buttons, borders)
- Links: Underlined (not color-only)
- Error states: Icon + color + text

---

## 9. Responsive Design

### 9.1 Breakpoints (Carbon)

| Breakpoint | Width | Behavior |
|------------|-------|----------|
| **sm** | 320-671px | Mobile: Stacked, collapsed sidebar |
| **md** | 672-1055px | Tablet: Sidebar collapsible, vertical split-pane |
| **lg** | 1056-1311px | Desktop: Full layout, sidebar visible |
| **xlg** | 1312-1583px | Wide desktop: Spacious layout |
| **max** | 1584px+ | Ultra-wide: Max content width (1600px) |

### 9.2 Mobile Adaptations

- **Header**: Logo + hamburger menu (hide tabs)
- **Sidebar**: Collapsed by default (slide-out overlay)
- **Split-panes**: Stack vertically (editor top, results bottom)
- **Tables**: Card view (one row = one card)
- **Commit Graph**: Simplified (fewer details, touch-friendly nodes)
- **3-Pane Resolver**: Accordion (one pane visible at a time)

---

## 10. Performance Optimizations

### 10.1 Virtual Scrolling

- Results table: Render ~50 rows at a time (handle 10k+ total)
- Commit graph: Render ~100 commits at a time (lazy-load more on scroll)
- Graph list: Render visible graphs only

### 10.2 Lazy Loading

- Commit history: Load 100 commits initially, paginate more
- Query history: Load last 50, load older on demand
- RDF Patch: Load on commit selection (not preloaded)

### 10.3 Debouncing

- Search inputs: Wait 300ms after typing
- Auto-save: Wait 5 seconds after last edit
- Filter inputs: Wait 500ms after typing

### 10.4 Code Splitting

- Route-based: Each view loaded on demand
- Component-based: Heavy components (graph visualizer) lazy-loaded

---

## 11. Testing Strategy

### 11.1 Unit Tests (Vitest)

- State management (stores, reducers)
- Utility functions (date formatting, SHA truncation)
- Service layer (API calls, mocked)

### 11.2 Component Tests (Playwright)

- Query editor interactions (type, execute, save)
- Commit graph interactions (click, right-click, drag)
- Conflict resolution workflow
- Branch creation and switching

### 11.3 Integration Tests (Playwright + CHUCC-server)

- End-to-end workflows (create commit → merge → resolve conflicts)
- Multi-view navigation (query → time-travel → version control)
- Dataset switching and management

### 11.4 Accessibility Tests

- axe-core integration (automated WCAG checks)
- Keyboard navigation tests (tab order, shortcuts)
- Screen reader tests (VoiceOver, NVDA)

---

## 12. Implementation Roadmap

### Phase 1: Core SPARQL (✅ Complete via CHUCC-SQUI)

- Query editor (CodeMirror + SPARQL syntax)
- Results table (virtual scrolling)
- Export functionality
- Prefix manager

### Phase 2: Basic Version Control (8 weeks)

**CHUCC-SQUI (2 weeks)**:
- RDF Patch Viewer (syntax highlighting)

**Frontend (6 weeks)**:
- Commit Graph Visualizer (D3.js or Cytoscape.js)
- Branch Selector (dropdown with status)
- Commit Detail View
- Dataset Switcher

### Phase 3: Advanced Version Control (6 weeks)

**CHUCC-SQUI (2 weeks)**:
- RDF Diff Viewer (side-by-side)

**Frontend (4 weeks)**:
- Merge Configuration Modal
- Time Travel Controls
- Cherry-Pick, Revert, Rebase operations

### Phase 4: Conflict Resolution (4 weeks)

**CHUCC-SQUI (1 week)**:
- 3-Pane Diff View (generic part)

**Frontend (3 weeks)**:
- Conflict Summary
- Conflict Resolver (with CHUCC-specific logic)

### Phase 5: Dataset Management (3 weeks)

**Frontend**:
- Dataset Creation Wizard
- Dataset Health Dashboard
- Dataset Deletion

### Phase 6: Batch Operations (3 weeks)

**Frontend**:
- Batch Operation Builder
- Batch Preview
- Execute batch operations

### Phase 7: Enhanced Browsing (4 weeks)

**CHUCC-SQUI (1 week)**:
- Graph Explorer (triple table/tree)

**Frontend (3 weeks)**:
- History Filter
- Quick Launch Palette (Ctrl+P)
- Query History enhancements

### Phase 8: Polish (2 weeks)

**CHUCC-SQUI**:
- Commit SHA Badge
- Author Input component

**Frontend**:
- Responsive design refinements
- Accessibility improvements
- Performance optimizations
- Documentation

**Total Timeline**: ~30 weeks (7.5 months)

---

## 13. Success Criteria

### 13.1 Functional Requirements

✅ All CHUCC-server endpoints accessible via UI
✅ Fuseki-level usability for SPARQL operations
✅ Fork-level elegance for version control
✅ WCAG 2.1 AA accessibility compliance
✅ Responsive (mobile, tablet, desktop)
✅ Handle 10,000+ result rows smoothly
✅ Support multi-dataset workflows

### 13.2 Performance Targets

- **Query Execution**: Results visible within 200ms (local server)
- **Graph Rendering**: 100 commits rendered within 500ms
- **Virtual Scrolling**: 60 FPS scrolling (10k+ rows)
- **Bundle Size**: < 500KB gzipped (initial load)
- **Time to Interactive**: < 3 seconds (first visit)

### 13.3 User Experience Goals

- **Learnability**: New users can execute queries within 5 minutes
- **Efficiency**: Power users can merge branches via drag-drop in < 10 seconds
- **Error Recovery**: Clear error messages with actionable steps
- **Satisfaction**: 4.5/5 user satisfaction rating (survey)

---

## 14. Next Steps

### 14.1 Immediate (Week 1-2)

1. ✅ Complete research (Fuseki, Fork, component gap) - **DONE**
2. ✅ Create information architecture - **DONE**
3. ✅ Create UI mockups (Query Workbench, Version Control) - **DONE**
4. ⏩ Create remaining mockups (Graphs, Merge, Time Travel, Datasets, Batch)
5. ⏩ Document component architecture
6. ⏩ Document interaction flows
7. ⏩ Define visual design system

### 14.2 Short-Term (Week 3-4)

1. Set up project repository (Svelte 5 + Vite + Carbon)
2. Configure testing (Vitest + Playwright)
3. Create design tokens (colors, spacing, typography)
4. Set up Storybook for component development
5. Begin Phase 2 (Basic Version Control)

### 14.3 Medium-Term (Month 2-3)

1. Implement core components (Query Workbench, Graph Explorer)
2. Implement basic version control (commit graph, branches)
3. Integrate CHUCC-SQUI components
4. Set up CI/CD pipeline
5. Begin user testing (stakeholder feedback)

### 14.4 Long-Term (Month 4-7)

1. Implement advanced version control (merge, rebase, squash)
2. Implement conflict resolution
3. Implement dataset management and batch operations
4. Conduct accessibility audit
5. Performance optimization
6. Production deployment

---

## 15. Risks & Mitigations

### Risk 1: Complex Commit Graph Visualization

**Risk**: D3.js/Cytoscape.js learning curve may slow development
**Mitigation**: Allocate 2 weeks for graph library evaluation and prototyping

### Risk 2: RDF Patch Syntax Highlighting

**Risk**: Custom CodeMirror language mode for RDF Patch may be complex
**Mitigation**: Leverage existing CodeMirror patterns, allocate 1 week for development

### Risk 3: Large Dataset Performance

**Risk**: 10,000+ result rows may cause UI lag
**Mitigation**: Virtual scrolling (already planned), Web Workers for syntax highlighting

### Risk 4: Mobile UX Compromises

**Risk**: Complex workflows (merge conflicts) difficult on mobile
**Mitigation**: Prioritize desktop UX, provide simplified mobile workflows for common tasks

### Risk 5: Accessibility Compliance

**Risk**: Custom graph visualizations may be hard to make accessible
**Mitigation**: Provide alternative table view for commit history, comprehensive keyboard navigation

---

## 16. Conclusion

The CHUCC frontend concept successfully combines:
- **Fuseki's simplicity** for SPARQL operations
- **Fork's elegance** for version control
- **CHUCC-SQUI's components** for reusable RDF UI
- **Carbon Design System** for professional, accessible UX

**Key Innovations**:
1. RDF-aware diff visualization (RDF Patch viewer)
2. Graph-level conflict resolution
3. Time-travel queries with visual timeline
4. Multi-dataset management with health monitoring
5. Atomic batch operations with preview

**Next Steps**:
1. Complete remaining mockup details
2. Stakeholder review and feedback
3. Begin Phase 2 implementation (Basic Version Control)

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)

**Total Documentation**: 30,000+ words across:
- Research (14,500 words): Fuseki analysis, Fork analysis, Component gap analysis
- Concept (15,500 words): Information architecture, UI mockups, Executive summary
