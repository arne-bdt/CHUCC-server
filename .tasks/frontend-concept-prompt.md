# CHUCC Frontend Concept - Deep Research Prompt

## Mission
Design a comprehensive web frontend concept for CHUCC-server (SPARQL 1.2 Protocol with Version Control Extension), combining the query/graph usability of Apache Jena Fuseki with the version control UX elegance of Fork Git Client, built on the CHUCC-SQUI component library.

---

## Context

### About CHUCC-Server
CHUCC Server implements the **SPARQL 1.2 Protocol with Version Control Extension**, providing:

**Core SPARQL Capabilities:**
- SPARQL 1.1 Query endpoint (SELECT, ASK, CONSTRUCT, DESCRIBE)
- SPARQL 1.1 Update endpoint (INSERT DATA, DELETE DATA, DELETE/INSERT)
- Graph Store Protocol (GSP): PUT, GET, POST, DELETE, PATCH operations on named graphs
- Batch operations for atomic multi-operation commits
- Service description endpoint (SPARQL 1.1 Service Description)

**Version Control Features (Git-like):**
- **Branches**: Mutable pointers to commits (create, list, delete, checkout)
- **Tags**: Immutable pointers to commits (annotated tags with messages)
- **Commits**: UUIDv7-based commit DAG with parent relationships
- **History**: Browsing commit history with filtering (branch, author, date range)
- **Time-Travel Queries**: Query historical states with `asOf` selector (RFC 3339 timestamp)
- **Merge Operations**: Three-way merge with conflict detection (strategies: three-way, ours, theirs, manual)
- **Advanced Operations**:
  - Cherry-pick: Apply specific commit to target branch
  - Revert: Create inverse commit (undo)
  - Reset: Move branch pointer (hard/soft/mixed modes)
  - Rebase: Reapply commits onto different base
  - Squash: Combine multiple commits into one
  - Diff: Compare two commits (returns RDF Patch)
  - Blame: Last-writer attribution for triples

**Multi-Dataset Support:**
- Create/delete datasets dynamically
- Each dataset has independent Kafka topics for event storage
- Dataset-level isolation

**Concurrency & Conflict Management:**
- Semantic overlap detection (triple/quad level)
- ETag-based optimistic concurrency control
- Strong consistency for reads (via `If-Match` preconditions)
- Structured conflict representation in merge failures

**Key Technical Details:**
- CQRS + Event Sourcing architecture (commands → events → projectors)
- Eventual consistency (202 Accepted for writes, async projection)
- RDFPatch format for changesets
- Apache Jena 5.5 for RDF processing (in-memory DatasetGraphInMemory)
- Apache Kafka for event store

**API Structure:**
```
# SPARQL Protocol
POST /query                           # SPARQL SELECT/ASK/CONSTRUCT/DESCRIBE
POST /update                          # SPARQL UPDATE

# Graph Store Protocol
GET/PUT/POST/DELETE/PATCH /{graph}   # GSP operations on named graphs
GET/PUT/POST/DELETE/PATCH /          # GSP operations on default graph

# Version Control - Refs & Commits
GET  /version/refs                    # List branches and tags
GET  /version/commits/{id}            # Get commit metadata
POST /version/commits                 # Create commit (RDF Patch body)
GET  /version/history                 # Browse commit history

# Version Control - Branches
POST /version/refs/branches           # Create branch
GET  /version/refs/branches/{name}    # Get branch info
DELETE /version/refs/branches/{name}  # Delete branch

# Version Control - Tags
GET  /version/tags                    # List tags
POST /version/tags                    # Create tag
GET  /version/tags/{name}             # Get tag info
DELETE /version/tags/{name}           # Delete tag

# Version Control - Merge & Advanced Operations
POST /version/merge                   # Merge two refs
POST /version/cherry-pick             # Cherry-pick commit
POST /version/revert                  # Revert commit
POST /version/reset                   # Reset branch pointer
POST /version/rebase                  # Rebase branch
POST /version/squash                  # Squash commits

# Version Control - Extensions (optional features)
GET  /version/diff                    # Compare commits (RDF Patch diff)
GET  /version/blame                   # Last-writer attribution

# Dataset Management (CHUCC-specific)
POST   /version/datasets/{name}       # Create dataset
DELETE /version/datasets/{name}       # Delete dataset
POST   /version/batch-graphs          # Batch GSP operations

# Batch Operations
POST /version/batch                   # Batch SPARQL operations
```

**Error Handling:**
- RFC 7807 Problem Details (application/problem+json)
- Structured conflict representation for merge failures
- HTTP status codes: 200, 202, 204, 400, 404, 406, 409, 412, 500, 503

**Documentation References:**
- Protocol spec: `/home/user/CHUCC-server/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md`
- API extensions: `/home/user/CHUCC-server/docs/api/api-extensions.md`
- OpenAPI spec: `/home/user/CHUCC-server/api/openapi.yaml`
- Architecture docs: `/home/user/CHUCC-server/docs/architecture/`

---

### About CHUCC-SQUI (Component Library)

**Technology Stack:**
- **Framework**: Svelte 5 + TypeScript 5.9
- **Design System**: IBM Carbon Design System (carbon-components-svelte)
- **Editor**: CodeMirror 6 with SPARQL syntax highlighting
- **Build**: Vite (dev server + bundler)
- **Testing**: Vitest (unit), Playwright (e2e)
- **SPARQL Client**: sparql-http library
- **Themes**: 4 Carbon themes (White, G10, G90, G100)
- **Accessibility**: WCAG 2.1 AA compliant

**Available Components:**
- Query editor with autocomplete and validation (CodeMirror-based)
- Results table with virtual scrolling (10,000+ row support)
- Multi-tab interface for simultaneous queries
- Endpoint selector with catalogue support
- Prefix management interface
- Export functionality (CSV, TSV, JSON, XML, Turtle, N-Triples, JSON-LD, RDF/XML)
- Theme selector (4 Carbon themes)
- Query history and templates (planned features)

**Carbon Design System Components Available:**
- Navigation (Header, SideNav, Breadcrumb)
- Data Display (DataTable, Tile, Accordion, Tabs)
- Forms (TextInput, TextArea, Dropdown, FileUploader, Button)
- Notifications (Toast, InlineNotification, Modal)
- Progress (Loading, ProgressBar, Skeleton)
- Icons (carbon-icons-svelte - extensive icon library)

**Component Structure:**
- Web component deployment possible
- Framework-agnostic integration
- Storybook documentation available

**Repository**: https://github.com/arne-bdt/CHUCC-SQUI

---

### UX Inspiration Sources

#### 1. Apache Jena Fuseki Web UI
**What to emulate:**
- Simple, functional SPARQL query interface
- Clear dataset selection and management
- Straightforward result display (table, raw formats)
- Quick access to common SPARQL operations
- Prefix management integration

**Analysis Task:** Research Fuseki's actual UI (screenshots, demos, or running instance) to identify:
- Layout patterns (header, sidebar, main content)
- Query editor placement and features
- Result rendering approaches
- Dataset/endpoint selection UX
- Navigation structure

#### 2. Fork Git Client (https://git-fork.com/)
**What to emulate:**
- Elegant commit graph visualization (DAG)
- Intuitive branch/tag management
- Clear diff visualization (side-by-side, unified)
- Smooth merge conflict resolution workflow
- Interactive rebase UI
- Blame view integration
- Stash management UI
- Commit list with filtering

**Analysis Task:** Study Fork's UX patterns:
- How does the commit graph render? (colors, lines, nodes)
- Branch switching UX (dropdown, search, keyboard shortcuts?)
- Diff viewer design (syntax highlighting, collapse/expand?)
- Merge conflict UI (3-pane view? inline markers?)
- History filtering (by author, date, branch)
- Overall information architecture

---

## Research Tasks

### Task 1: Deep Dive into Apache Jena Fuseki Web UI
**Goal:** Understand the query-centric, SPARQL-focused usability patterns.

**Sub-tasks:**
1. **Visual Analysis**: Find screenshots or run Fuseki locally to capture:
   - Main dashboard/landing page
   - Query interface layout
   - Dataset selection mechanism
   - Result display (table, JSON, XML, RDF serializations)
   - Update operation interface
   - File upload/import UI (if exists)

2. **Interaction Patterns**: Document:
   - How users navigate between datasets
   - Query history or saved queries (if supported)
   - Prefix management UI
   - Export/download result flows
   - Error display patterns

3. **Layout Structure**: Identify:
   - Header content (logo, navigation, settings)
   - Sidebar usage (if any)
   - Main content area organization
   - Responsive design patterns

4. **Gaps**: Note what Fuseki lacks that CHUCC needs:
   - No version control UI
   - Limited batch operation support
   - No advanced conflict resolution

**Deliverable:** Fuseki UI analysis document with:
- Annotated screenshots
- Interaction flow diagrams
- List of reusable patterns for CHUCC
- List of gaps/limitations

---

### Task 2: Deep Dive into Fork Git Client UX
**Goal:** Understand elegant version control visualization and interaction patterns.

**Sub-tasks:**
1. **Visual Analysis**: Capture (via screenshots or demo):
   - Main window layout (panes, sidebars, content areas)
   - Commit graph rendering (colors, lines, merge nodes)
   - Branch list UI (icons, current branch highlighting)
   - Tag list UI
   - Diff viewer (side-by-side vs unified)
   - Merge conflict resolution interface
   - Blame view
   - Interactive rebase UI

2. **Interaction Patterns**: Document:
   - How to create/delete/switch branches
   - How to merge branches (dialog, conflict handling)
   - How to cherry-pick commits
   - How to view commit details (metadata, diff, files changed)
   - How to search/filter history
   - Keyboard shortcuts for common operations
   - Undo/redo patterns (reflog access)

3. **Visual Language**: Identify:
   - Color coding conventions (branches, tags, local/remote)
   - Iconography (branch, tag, merge, conflict icons)
   - Typography hierarchy
   - Spacing and density patterns

4. **Gaps**: Note what Fork has that may not apply to RDF version control:
   - File tree view (RDF uses graphs, not files)
   - Working directory changes (CHUCC uses materialized views)
   - Staging area (CHUCC commits RDF patches directly)

**Deliverable:** Fork UX analysis document with:
- Annotated screenshots
- Interaction flow diagrams for key operations (merge, cherry-pick, rebase)
- Visual design system notes (colors, icons, typography)
- List of patterns to adapt for CHUCC

---

### Task 3: CHUCC-SQUI Component Inventory & Gap Analysis
**Goal:** Catalog available components and identify missing pieces.

**Sub-tasks:**
1. **Component Audit**: List all Carbon components available via carbon-components-svelte:
   - Navigation components
   - Data display components
   - Form components
   - Feedback components (toasts, modals, alerts)
   - Layout components
   - Icon components

2. **CHUCC-SQUI Custom Components**: Document existing:
   - SPARQL query editor (CodeMirror wrapper)
   - Results table (virtual scrolling)
   - Prefix manager
   - Export dialog
   - Theme selector
   - Tab manager

3. **Gap Analysis**: Identify components needed for version control UI:
   - **Missing from CHUCC-SQUI:**
     - Commit graph visualizer (DAG renderer)
     - Branch selector/dropdown with visual indicators
     - Tag list component
     - Diff viewer (RDF-aware, side-by-side or unified)
     - Merge conflict resolver (3-pane or inline markers)
     - Commit detail view (metadata + patch)
     - History filter/search component
     - Time-travel date picker (asOf selector)
     - Batch operation builder
     - Dataset switcher/manager
   - **Missing from Carbon Design System:**
     - Graph visualization (for commit DAG)
     - Split pane layout (for diff viewer)
     - RDF-aware syntax highlighting (may need CodeMirror extension)

4. **Component Placement Decision**: For each missing component, decide:
   - **Should it be in CHUCC-SQUI?** (reusable across RDF/SPARQL apps)
   - **Should it be in frontend project?** (CHUCC-specific, not reusable)

**Decision Criteria:**
- **CHUCC-SQUI candidates:** Generic RDF/SPARQL components (e.g., diff viewer, RDF syntax highlighter)
- **Frontend project candidates:** CHUCC-specific business logic (e.g., dataset manager with Kafka config)

**Deliverable:** Component gap analysis spreadsheet/table:
| Component | Required? | Available in Carbon? | Available in CHUCC-SQUI? | Needs Development? | Belongs in CHUCC-SQUI or Frontend? | Notes |
|-----------|-----------|---------------------|--------------------------|-------------------|-----------------------------------|-------|
| Commit Graph | Yes | No | No | Yes | Frontend (CHUCC-specific) | Use d3.js or cytoscape.js |
| ... | ... | ... | ... | ... | ... | ... |

---

## Synthesis Task

### Task 4: Design CHUCC Frontend Concept
**Goal:** Create a comprehensive frontend concept with UI mockups and component architecture.

**Requirements:**

#### 4.1 Information Architecture
Design the overall application structure:
- **Navigation hierarchy**: How do users move between features?
- **Primary views**:
  1. **Query Workbench** (Fuseki-inspired): SPARQL query/update interface
  2. **Graph Explorer** (Fuseki-inspired): Browse/edit named graphs (GSP operations)
  3. **Version Control** (Fork-inspired): Commits, branches, tags, history
  4. **Merge & Conflicts** (Fork-inspired): Merge operations, conflict resolution
  5. **Time Travel** (unique): Query historical states with asOf selector
  6. **Dataset Manager** (CHUCC-specific): Create/delete/configure datasets
  7. **Batch Operations** (CHUCC-specific): Build and execute batch requests
- **Secondary views**:
  - Settings/Configuration
  - User profile (author metadata)
  - Help/Documentation

#### 4.2 UI Mockups (Wireframes + High-Fidelity)
Create mockups for each primary view:

**1. Query Workbench**
- Layout: Header + Sidebar + Main (split: editor top, results bottom)
- Components:
  - Dataset selector (dropdown, top-left)
  - Branch/commit selector (dropdown, top-right, with "asOf" date picker)
  - SPARQL editor (CodeMirror, top pane)
  - Query type selector (SELECT, ASK, CONSTRUCT, DESCRIBE, UPDATE)
  - Execute button (prominent, with keyboard shortcut hint)
  - Results table (bottom pane, virtual scrolling)
  - Export menu (CSV, JSON, Turtle, etc.)
  - Prefix manager (modal or sidebar panel)
  - Query history (sidebar or modal)
- Interactions:
  - Split pane resize (drag divider)
  - Tab-based multi-query support
  - Autocomplete in editor
  - Error highlighting in editor
  - Click triple in results → show in Graph Explorer

**2. Graph Explorer (GSP Operations)**
- Layout: Header + Sidebar (graph list) + Main (graph view)
- Components:
  - Graph list (sidebar, with default graph + named graphs)
  - Graph content viewer (table or tree view of triples)
  - Add/delete graph buttons
  - Upload file to graph (file uploader)
  - Download graph (format selector)
  - Edit graph (inline or modal editor)
- Interactions:
  - Click graph in list → show content
  - Drag-drop file to upload
  - Filter triples by subject/predicate/object
  - Create named graph (modal with URI input)

**3. Version Control (Commits & History)**
- Layout: Header + Sidebar (branch/tag list) + Main (split: commit graph top, commit detail bottom)
- Components:
  - Branch list (sidebar, with current branch highlighted, star for main)
  - Tag list (sidebar, collapsible section)
  - Commit graph (main top pane, DAG visualization with lines and nodes)
  - Commit detail view (main bottom pane, shows: author, timestamp, message, parent commits, RDF patch diff)
  - Filter controls (by author, date range, branch)
  - Search bar (search commit messages)
  - Action buttons (cherry-pick, revert, reset, rebase, squash)
- Interactions:
  - Click commit in graph → show detail
  - Right-click commit → context menu (cherry-pick, revert, reset branch to here)
  - Click branch in sidebar → filter history to that branch
  - Create branch (button in sidebar, modal with name input and base commit selector)
  - Delete branch (trash icon, confirmation dialog)
  - Merge branch (button, modal with source/target selection and strategy chooser)

**4. Merge & Conflicts**
- Layout: Header + Main (3-pane or inline conflict markers)
- Components:
  - Conflict summary (top: "12 graphs in conflict, 45 triples")
  - Graph-by-graph conflict list (collapsible accordion)
  - Conflict resolution pane:
    - **Option A (3-pane)**: Base | Ours | Theirs (side-by-side)
    - **Option B (inline)**: Unified view with conflict markers
  - Resolution actions (per triple): Accept Ours, Accept Theirs, Edit Manually
  - Commit merge button (bottom, enabled when all conflicts resolved)
- Interactions:
  - Click conflict in list → show resolution pane
  - Click "Accept Ours" → apply to all triples in graph
  - Manual edit → text editor for triple
  - Preview merge result (before committing)

**5. Time Travel Queries**
- Layout: Query Workbench with enhanced time controls
- Components:
  - Timeline slider (bottom of screen, shows commits over time)
  - asOf date picker (calendar + time input)
  - "Query as of" indicator (prominent, shows current ref or timestamp)
  - Diff visualizer (compare query results at two time points)
- Interactions:
  - Drag slider → update asOf timestamp
  - Click commit on timeline → query at that commit
  - Compare mode (split results: time A vs time B)

**6. Dataset Manager**
- Layout: Header + Main (dataset list + detail panel)
- Components:
  - Dataset list (table: name, description, main branch, created date, Kafka topic)
  - Create dataset button (modal: name, description, Kafka config)
  - Delete dataset button (confirmation dialog with "deleteKafkaTopic" checkbox)
  - Dataset detail view (shows: commits count, branches count, tags count, graphs count)
- Interactions:
  - Click dataset in list → switch to that dataset
  - Create dataset → modal form → submit → 202 Accepted → toast notification
  - Delete dataset → confirmation → submit → 204 No Content → remove from list

**7. Batch Operations Builder**
- Layout: Header + Main (operation list + preview)
- Components:
  - Operation list (table: type, graph, content, order)
  - Add operation button (modal: choose type, fill details)
  - Remove operation button (per row)
  - Reorder operations (drag handles)
  - Mode selector (single-commit vs multi-commit)
  - Preview pane (shows resulting RDF Patch or commit structure)
  - Execute button (prominent)
- Interactions:
  - Add operation → modal form → append to list
  - Drag row → reorder
  - Click execute → POST /version/batch-graphs → show result (commit ID or multi-status)

#### 4.3 Component Architecture
Design the component hierarchy for the frontend:

**Example structure:**
```
App
├── Header
│   ├── Logo
│   ├── DatasetSelector
│   ├── RefSelector (branch/commit/asOf)
│   ├── ThemeSelector
│   └── UserMenu
├── Sidebar
│   ├── Navigation (Query, Graphs, Version Control, Merge, Time Travel, Datasets, Batch)
│   └── BranchList (in Version Control view)
├── MainContent
│   ├── QueryWorkbench
│   │   ├── QueryEditor (CodeMirror)
│   │   ├── ResultsTable
│   │   ├── ExportMenu
│   │   └── PrefixManager
│   ├── GraphExplorer
│   │   ├── GraphList
│   │   ├── GraphViewer (table or tree)
│   │   └── GraphUploader
│   ├── VersionControl
│   │   ├── CommitGraph (DAG visualizer)
│   │   ├── CommitDetail
│   │   ├── HistoryFilter
│   │   └── ActionButtons
│   ├── MergeConflicts
│   │   ├── ConflictSummary
│   │   ├── ConflictList
│   │   └── ConflictResolver (3-pane or inline)
│   ├── TimeTravelQuery
│   │   ├── Timeline
│   │   ├── AsOfPicker
│   │   └── DiffVisualizer
│   ├── DatasetManager
│   │   ├── DatasetList
│   │   ├── CreateDatasetModal
│   │   └── DatasetDetail
│   └── BatchOperations
│       ├── OperationList
│       ├── OperationEditor
│       └── Preview
└── Footer
    ├── StatusBar (shows: current dataset, branch, author)
    └── Notifications (toasts)
```

**State Management:**
- Global state: Current dataset, branch/commit, author, theme
- View state: Active view, query tabs, filter settings
- Data state: Commits, branches, tags, query results (cached)

**Routing:**
```
/query          → Query Workbench
/graphs         → Graph Explorer
/version        → Version Control
/merge          → Merge & Conflicts
/time-travel    → Time Travel Queries
/datasets       → Dataset Manager
/batch          → Batch Operations
/settings       → Settings
```

#### 4.4 Interaction Flows
Document key user flows:

**Flow 1: Execute SPARQL Query with Time Travel**
1. User navigates to Query Workbench
2. User selects dataset from dropdown
3. User selects branch from dropdown (or uses asOf date picker)
4. User types SPARQL query in editor (with autocomplete)
5. User clicks Execute (or Ctrl+Enter)
6. System sends POST /query with branch or asOf selector
7. Results appear in table below editor
8. User exports results as CSV

**Flow 2: Create Branch and Commit Changes**
1. User navigates to Version Control
2. User sees commit graph showing current state
3. User clicks "Create Branch" button in sidebar
4. Modal opens: user enters branch name, selects base commit (default: current HEAD)
5. User clicks Create → POST /version/refs/branches
6. New branch appears in sidebar, highlighted as current
7. User navigates to Graph Explorer
8. User uploads Turtle file to a named graph (GSP PUT)
9. System creates commit automatically (or user reviews and confirms)
10. Commit appears in graph immediately (optimistic update)

**Flow 3: Merge Branches with Conflict Resolution**
1. User navigates to Version Control
2. User clicks "Merge" button in toolbar
3. Modal opens: user selects source branch and target branch, chooses strategy (three-way)
4. User clicks Merge → POST /version/merge
5. System returns 409 Conflict with structured conflict data
6. User redirected to Merge & Conflicts view
7. System displays 12 conflicting graphs in accordion
8. User expands first graph, sees 5 conflicting triples in 3-pane view (Base | Ours | Theirs)
9. User clicks "Accept Ours" for 3 triples, "Accept Theirs" for 2 triples
10. User collapses graph, expands next graph, repeats
11. All conflicts resolved → "Commit Merge" button enabled
12. User clicks Commit Merge → POST /version/merge (with manual resolution data)
13. System returns 200 OK with merge commit ID
14. User redirected to Version Control, sees new merge commit in graph

**Flow 4: Cherry-Pick Commit**
1. User navigates to Version Control
2. User finds commit in history (using filter or search)
3. User right-clicks commit → selects "Cherry-Pick to..." from context menu
4. Modal opens: user selects target branch
5. User clicks Cherry-Pick → POST /version/cherry-pick
6. System returns 200 OK with new commit ID (or 409 if conflicts)
7. If conflicts → redirect to Merge & Conflicts view
8. If success → commit graph updates with new commit on target branch

#### 4.5 Visual Design System
Define the visual language:

**Color Palette:**
- Use Carbon theme colors (White, G10, G90, G100)
- Additional semantic colors:
  - Branch: Blue (#0f62fe)
  - Tag: Orange (#ff832b)
  - Commit: Green (#24a148)
  - Conflict: Red (#da1e28)
  - Current ref: Yellow (#f1c21b)

**Typography:**
- Carbon default: IBM Plex Sans (body), IBM Plex Mono (code)
- Hierarchy: H1 (24px), H2 (20px), H3 (16px), Body (14px), Caption (12px)

**Icons:**
- Use carbon-icons-svelte
- Custom icons needed:
  - Git branch (fork icon)
  - Git commit (dot/circle)
  - Git merge (converging arrows)
  - Git tag (tag icon)
  - RDF graph (network icon)

**Spacing:**
- Carbon spacing scale: 2px, 4px, 8px, 16px, 24px, 32px, 48px

**Layout Density:**
- Compact mode (for tables, lists)
- Normal mode (default)
- Comfortable mode (for accessibility)

#### 4.6 Accessibility
Ensure WCAG 2.1 AA compliance:
- Keyboard navigation (Tab, Arrow keys, Enter, Escape)
- Screen reader support (ARIA labels, landmarks, live regions)
- Focus indicators (visible outline)
- Color contrast (4.5:1 for text, 3:1 for UI components)
- Error messages (associated with form fields)

#### 4.7 Responsive Design
Support desktop, tablet, mobile:
- Breakpoints: 320px (mobile), 768px (tablet), 1024px (desktop)
- Mobile: Collapse sidebar to hamburger menu, stack editor and results vertically
- Tablet: Show sidebar, maintain split panes
- Desktop: Full layout with all panes visible

---

## Deliverables

### 1. Research Reports
- **Fuseki UI Analysis** (2-3 pages with screenshots)
- **Fork UX Analysis** (3-4 pages with screenshots)
- **Component Gap Analysis** (spreadsheet/table)

### 2. Frontend Concept Document
- **Executive Summary** (1 page)
- **Information Architecture** (sitemap, navigation hierarchy)
- **UI Mockups** (wireframes + high-fidelity mockups for 7 primary views)
- **Component Architecture** (component tree, state management, routing)
- **Interaction Flows** (4+ key flows with step-by-step diagrams)
- **Visual Design System** (color palette, typography, icons, spacing)
- **Accessibility Guidelines**
- **Responsive Design Strategy**

### 3. Component Placement Recommendations
- **Table format:**
  | Component | Description | Belongs in CHUCC-SQUI? | Belongs in Frontend? | Rationale |
  |-----------|-------------|------------------------|----------------------|-----------|
  | CommitGraph | DAG visualizer | No | Yes | CHUCC-specific UX, not reusable |
  | DiffViewer | RDF diff renderer | Yes | No | Generic RDF component, reusable |
  | ... | ... | ... | ... | ... |

### 4. Implementation Roadmap (High-Level)
- **Phase 1**: Core SPARQL features (Query Workbench, Graph Explorer)
- **Phase 2**: Basic version control (Commits, Branches, History)
- **Phase 3**: Advanced version control (Merge, Cherry-Pick, Rebase)
- **Phase 4**: Conflict resolution UI
- **Phase 5**: Time Travel queries
- **Phase 6**: Dataset management
- **Phase 7**: Batch operations
- **Phase 8**: Polish (accessibility, performance, docs)

---

## Success Criteria

The final concept should:
1. **Faithfully represent CHUCC-server capabilities** (all endpoints accessible via UI)
2. **Provide Fuseki-level usability** for SPARQL query/graph operations
3. **Provide Fork-level elegance** for version control operations
4. **Leverage CHUCC-SQUI components** maximally (minimize duplicate work)
5. **Identify clear component boundaries** (CHUCC-SQUI vs frontend project)
6. **Be implementable with Svelte 5 + Carbon Design System**
7. **Be accessible (WCAG 2.1 AA)**
8. **Be responsive (mobile, tablet, desktop)**
9. **Be visually consistent** (Carbon theme integration)
10. **Be developer-friendly** (clear component architecture, state management, routing)

---

## Suggested Research Approach

### Phase 1: Understand (Days 1-2)
1. Review CHUCC-server documentation thoroughly
2. Review CHUCC-SQUI repository and component library
3. Analyze Fuseki UI (screenshots, demo, local install)
4. Analyze Fork Git Client (screenshots, demo, trial version)

### Phase 2: Analyze (Days 3-4)
1. Create Fuseki UI analysis document
2. Create Fork UX analysis document
3. Create component gap analysis spreadsheet
4. Identify reusable patterns and anti-patterns

### Phase 3: Synthesize (Days 5-7)
1. Design information architecture
2. Sketch wireframes for 7 primary views
3. Refine wireframes into high-fidelity mockups
4. Design component architecture
5. Document interaction flows
6. Define visual design system

### Phase 4: Document (Days 8-9)
1. Compile frontend concept document
2. Create component placement recommendations
3. Create implementation roadmap
4. Review for completeness and clarity

### Phase 5: Iterate (Day 10)
1. Review deliverables against success criteria
2. Refine based on feedback
3. Finalize all documents

---

## Additional Notes

### RDF-Specific Considerations
- Triples/quads are not files → UI must visualize graph structure differently
- RDF serialization formats (Turtle, JSON-LD, N-Triples) → syntax highlighting needed
- Blank nodes → how to visualize in UI? (auto-generated labels, inline expansion)
- Prefixes → must be managed per query or globally per dataset

### Performance Considerations
- Virtual scrolling for large result sets (10k+ rows)
- Lazy loading for commit graph (paginate history)
- Debounced autocomplete in query editor
- Optimistic updates for commits (show immediately, sync async)

### Future Enhancements (Out of Scope)
- Collaborative editing (multi-user)
- Real-time conflict detection (WebSocket)
- Query templates/snippets library
- Visual SPARQL query builder (drag-drop)
- RDF graph visualization (force-directed layout)
- Custom merge strategies (user-defined rules)

---

## Questions to Address

1. **Commit Graph Visualization**: Which library to use? (d3.js, cytoscape.js, vis.js, custom SVG)
2. **Diff Viewer**: Side-by-side or unified? Syntax highlighting for RDF Patch? Collapse large diffs?
3. **Conflict Resolution**: 3-pane or inline markers? Per-triple or per-graph resolution?
4. **Time Travel UI**: Timeline slider vs date picker vs both? How to visualize commit history on timeline?
5. **Dataset Switching**: Dropdown or dedicated page? How to handle switching mid-query?
6. **Author Metadata**: User profile page or just header input? Store in browser localStorage?
7. **Offline Support**: Service worker for caching? IndexedDB for query history?
8. **Mobile UX**: Full feature parity or limited subset? Native app vs responsive web?

---

## Resources

### CHUCC-Server
- GitHub: (local repository)
- OpenAPI Spec: `/home/user/CHUCC-server/api/openapi.yaml`
- Protocol Docs: `/home/user/CHUCC-server/protocol/`
- Architecture Docs: `/home/user/CHUCC-server/docs/architecture/`

### CHUCC-SQUI
- GitHub: https://github.com/arne-bdt/CHUCC-SQUI
- Storybook: (check repo for deployed link)

### Design References
- Apache Jena Fuseki: https://jena.apache.org/documentation/fuseki2/
- Fork Git Client: https://git-fork.com/
- IBM Carbon Design System: https://carbondesignsystem.com/
- Carbon Components Svelte: https://carbon-components-svelte.onrender.com/

### Libraries
- d3.js: https://d3js.org/ (data visualization)
- cytoscape.js: https://js.cytoscape.org/ (graph visualization)
- CodeMirror 6: https://codemirror.net/ (code editor)
- Svelte 5: https://svelte.dev/ (framework)

---

## Final Output Format

Deliver all documents as:
- **Markdown** (for text reports)
- **PNG/SVG** (for mockups, diagrams)
- **CSV/Excel** (for component gap analysis)
- **Mermaid diagrams** (for flows, architecture)

Organize in a clear directory structure:
```
chucc-frontend-concept/
├── 01-research/
│   ├── fuseki-ui-analysis.md
│   ├── fork-ux-analysis.md
│   └── component-gap-analysis.xlsx
├── 02-concept/
│   ├── executive-summary.md
│   ├── information-architecture.md
│   ├── ui-mockups/
│   │   ├── 01-query-workbench.png
│   │   ├── 02-graph-explorer.png
│   │   ├── 03-version-control.png
│   │   ├── 04-merge-conflicts.png
│   │   ├── 05-time-travel.png
│   │   ├── 06-dataset-manager.png
│   │   └── 07-batch-operations.png
│   ├── component-architecture.md
│   ├── interaction-flows.md
│   ├── visual-design-system.md
│   ├── accessibility-guidelines.md
│   └── responsive-design.md
├── 03-recommendations/
│   ├── component-placement.xlsx
│   └── implementation-roadmap.md
└── README.md (overview of deliverables)
```

---

**Good luck! This is a complex, multi-faceted research and design task. Take your time, be thorough, and create something elegant and usable.**
