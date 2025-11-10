# CHUCC Frontend - Component Gap Analysis

**Research Date**: 2025-11-10
**Purpose**: Identify available components and gaps for CHUCC frontend development

---

## Executive Summary

This analysis catalogs components available in **Carbon Design System (Svelte)** and **CHUCC-SQUI**, then identifies missing components needed for the CHUCC frontend. For each missing component, we determine whether it belongs in CHUCC-SQUI (reusable RDF/SPARQL component) or the CHUCC frontend project (CHUCC-specific business logic).

**Key Findings**:
- ✅ **Carbon provides**: 165+ UI components covering most common UI patterns
- ✅ **CHUCC-SQUI provides**: SPARQL query editor, results table, prefix manager, multi-tab interface
- ❌ **Missing**: 15+ specialized components for version control, RDF diff visualization, and conflict resolution
- 📊 **Recommendation**: 7 components → CHUCC-SQUI (reusable), 8 components → Frontend (CHUCC-specific)

---

## 1. Carbon Components Svelte (Available)

### 1.1 Layout & Structure
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Accordion` | Collapsible content sections | Conflict list, graph list |
| `Grid`, `Row`, `Column` | Responsive grid layout | Page layout |
| `Breadcrumb` | Navigation breadcrumb | Hierarchical navigation |
| `AspectRatio` | Maintain aspect ratio | Image diffs, visualizations |

### 1.2 Forms & Input
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `TextInput` | Single-line text | Branch name, commit message |
| `TextArea` | Multi-line text | Commit message, RDF Patch editing |
| `NumberInput` | Numeric input | Kafka config (partitions, retention) |
| `Dropdown` | Single-select dropdown | Dataset selector, branch selector |
| `MultiSelect` | Multi-select dropdown | Select multiple commits/graphs |
| `ComboBox` | Searchable dropdown | Branch search, dataset search |
| `Search` | Search input with icon | Filter branches, search commits |
| `Checkbox` | Boolean input | "Delete Kafka topic", "Fast-forward only" |
| `RadioButton` | Single choice from options | Merge strategy (three-way, ours, theirs) |
| `Toggle` | On/off switch | Enable/disable features |
| `DatePicker` | Date selection | asOf time-travel selector |
| `TimePicker` | Time selection | asOf time selector |
| `FileUploader` | File upload with drag-drop | Upload RDF files to graphs |
| `Switch` | Alternative to Toggle | Theme switcher |
| `PasswordInput` | Masked text input | API keys, credentials (if needed) |
| `Select` | Native select | Simple dropdowns |

**Verdict**: ✅ **Complete** - All form inputs needed are available

### 1.3 Data Display
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `DataTable` | Sortable, filterable table | Results table, commit list, dataset list |
| `StructuredList` | Key-value list | Commit metadata, branch info |
| `Table` | Basic HTML table | Simple data display |
| `Tabs` | Tabbed interface | Query tabs, dataset tabs |
| `TreeView` | Hierarchical tree | Branch hierarchy (future), namespace tree |

**Verdict**: ✅ **Complete** - Data display components available

### 1.4 Navigation
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Header` | Top navigation bar | App header with logo, dataset selector |
| `HeaderNav` | Header navigation items | Main nav (Query, Graphs, Version Control) |
| `SideNav` | Sidebar navigation | Branches, tags, graphs, stashes (version control view) |

**Verdict**: ✅ **Complete** - Navigation components available

### 1.5 Notifications & Feedback
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `InlineNotification` | Inline message | Success/error messages after operations |
| `ToastNotification` | Floating toast | Background operation completion |
| `ProgressBar` | Horizontal progress | Long-running operations (rebuild view) |
| `ProgressIndicator` | Stepped progress | Multi-step wizards (dataset creation) |
| `Loading` | Full-page spinner | Initial load |
| `InlineLoading` | Inline spinner | In-context loading |
| `SkeletonText` | Content placeholder | Commit list loading |
| `SkeletonPlaceholder` | Custom skeleton | Graph list loading |

**Verdict**: ✅ **Complete** - Feedback components available

### 1.6 Modals & Overlays
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Modal` | Simple modal dialog | Confirmations, simple forms |
| `ComposedModal` | Advanced modal with sections | Complex forms (create dataset, merge config) |
| `Popover` | Contextual popup | Help text, tooltips |
| `ContextMenu` | Right-click menu | Branch context menu, commit context menu |
| `OverflowMenu` | Dropdown menu (3-dot) | Additional actions |

**Verdict**: ✅ **Complete** - Modal components available

### 1.7 Buttons & Controls
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Button` | Standard button | All actions (merge, commit, execute) |
| `ButtonSet` | Button group | Modal actions (Cancel, Save) |
| `Link` | Hyperlink | Navigation, external links |
| `CopyButton` | Copy to clipboard | Copy commit SHA, copy RDF Patch |

**Verdict**: ✅ **Complete** - Button components available

### 1.8 Tiles & Cards
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Tile` | Content card | Dataset card, quick action cards |
| `ClickableTile` | Clickable card | Navigate to dataset/branch |
| `ExpandableTile` | Collapsible card | Expandable commit details |
| `SelectableTile` | Selectable card | Select merge strategy |
| `RadioTile` | Radio button as tile | Choose conflict resolution mode |

**Verdict**: ✅ **Complete** - Tile components available

### 1.9 Utility Components
| Component | Description | Use in CHUCC |
|-----------|-------------|--------------|
| `Tooltip` | Hover tooltip | Help text, info icons |
| `Tag` | Label/badge | Branch labels, status tags |
| `Badge` | Notification badge | Uncommitted changes count |
| `Theme` | Theme provider | App theme wrapper |
| `Content` | Content wrapper | Main content area |
| `Breakpoint` | Responsive breakpoint | Responsive layout logic |

**Verdict**: ✅ **Complete** - Utility components available

### 1.10 Summary: Carbon Components

**Total**: 165+ components
**Coverage**: ✅ Comprehensive - covers all standard UI patterns
**Gaps**: None for standard UI - specialized components needed for version control and RDF visualization

---

## 2. CHUCC-SQUI Components (Available)

### 2.1 Custom Components

| Component | Description | Technology | Reusable? |
|-----------|-------------|------------|-----------|
| **SparqlQueryUI** | Main web component wrapper | Svelte 5 + Carbon | ✅ Yes |
| **Query Editor** | SPARQL syntax highlighting, autocomplete | CodeMirror 6 | ✅ Yes |
| **Results Table** | Virtual scrolling table (10k+ rows) | Carbon DataTable + virtual scroll | ✅ Yes |
| **Endpoint Selector** | Dropdown with catalogue support | Carbon Dropdown | ✅ Yes |
| **Prefix Manager** | Manage namespace prefixes | Carbon Modal + Form | ✅ Yes |
| **Export Menu** | Export results in multiple formats | Carbon OverflowMenu | ✅ Yes |
| **Theme Selector** | Switch between 4 Carbon themes | Carbon Select | ✅ Yes |
| **Multi-Tab Interface** | Simultaneous queries in tabs | Carbon Tabs | ✅ Yes |

### 2.2 Internal Stores (Svelte Stores)

| Store | Purpose | Reusable? |
|-------|---------|-----------|
| `queryStore` | Query state management | ✅ Yes |
| `resultsStore` | Results state management | ✅ Yes |
| `endpointCatalogue` | Endpoint discovery | ✅ Yes |
| `tabStore` | Tab management | ✅ Yes |

### 2.3 Features

- **SPARQL 1.2 Protocol compliance**: Full support for SELECT, ASK, CONSTRUCT, DESCRIBE
- **Service Description support**: Auto-discovery of graph names
- **Real-time validation**: Compatibility warnings
- **Keyboard shortcuts**: Ctrl+Enter to execute
- **Accessibility**: WCAG 2.1 AA target
- **Offline support**: Standalone distribution without CDN dependencies
- **Configuration API**: Flexible `SquiConfig` for customization

### 2.4 Summary: CHUCC-SQUI

**Total Custom Components**: 8 (query-focused)
**Reusability**: ✅ High - designed for any SPARQL endpoint
**Coverage**: ✅ Complete for SPARQL query operations
**Gaps**: No version control UI, no RDF diff visualization, no conflict resolution

---

## 3. Missing Components for CHUCC Frontend

### 3.1 Version Control Components

#### 3.1.1 Commit Graph Visualizer (DAG)

**Description**: Visual representation of commit history as directed acyclic graph

**Features**:
- Colored branch lines (Fork-style)
- Commit nodes with avatars
- Collapsible merge commits
- Branch/tag labels inline
- Click to select commit → show details
- Right-click → context menu (cherry-pick, revert, reset)
- Drag-drop for cherry-pick

**Technology Options**:
- D3.js (data-driven, flexible)
- Cytoscape.js (graph-focused, complex layouts)
- Vis.js (good for timelines)
- Custom SVG/Canvas (full control)

**Reusable?**: ⚠️ Partially
- **Core graph rendering**: Could be generic "commit graph" component
- **CHUCC-specific**: UUIDv7 commit IDs, RDF patch metadata, dataset context

**Recommendation**: **Frontend Project**
- **Rationale**: Tightly coupled to CHUCC's commit structure and API

---

#### 3.1.2 Branch Selector with Visual Indicators

**Description**: Dropdown to select branch/commit/tag with visual status indicators

**Features**:
- Branch list with hierarchy
- Current branch highlighted (star icon)
- Ahead/behind indicators (↑3 ↓2)
- Worktree indicators (if checked out elsewhere)
- Search/filter branches
- Recently used branches at top
- Color-coded branch labels (match graph colors)

**Technology**: Carbon ComboBox + custom rendering

**Reusable?**: ⚠️ Partially
- **Generic part**: Searchable dropdown with icons
- **CHUCC-specific**: Ahead/behind logic, worktree status, dataset context

**Recommendation**: **Frontend Project**
- **Rationale**: Branch metadata tightly coupled to CHUCC API

---

#### 3.1.3 Commit Detail View

**Description**: Panel showing full commit metadata and changes

**Features**:
- Author, committer, timestamp
- Full commit message
- Parent commits (clickable links)
- Graphs changed (list)
- Triple counts (+/-) per graph
- RDF Patch viewer (below)

**Technology**: Carbon StructuredList + custom RDF Patch viewer

**Reusable?**: ⚠️ Partially
- **Generic part**: Metadata display
- **RDF-specific**: Graphs changed, triple counts, RDF Patch

**Recommendation**: **Split**
- **CHUCC-SQUI**: RDF Patch syntax highlighter (see 3.3.1)
- **Frontend**: Commit detail layout + API integration

---

#### 3.1.4 History Filter Component

**Description**: Filter commit history by author, date range, branch, message

**Features**:
- Author dropdown (with autocomplete)
- Date range picker (from/to)
- Branch filter (multi-select)
- Message search (text input)
- Clear all filters button

**Technology**: Carbon Form components + custom logic

**Reusable?**: ✅ Somewhat
- **Generic**: Filter UI pattern
- **CHUCC-specific**: Filter logic tied to CHUCC API

**Recommendation**: **Frontend Project**
- **Rationale**: Filter parameters are CHUCC-specific

---

#### 3.1.5 Time Travel Controls

**Description**: UI for selecting time point (asOf selector) and navigating commits

**Features**:
- Date + time picker (ISO 8601 format)
- Timeline slider (optional, shows commits over time)
- "Query as of" indicator (prominent display)
- Quick actions: "Latest", "1 day ago", "1 week ago"
- Commit selector (dropdown alternative to date)

**Technology**: Carbon DatePicker + TimePicker + custom slider

**Reusable?**: ⚠️ Partially
- **Generic**: Date/time picker
- **CHUCC-specific**: asOf selector semantics, commit timeline

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's asOf protocol extension

---

### 3.2 Merge & Conflict Resolution Components

#### 3.2.1 Conflict Resolver (3-Pane Layout)

**Description**: Three-column view for resolving merge conflicts (Base | Ours | Theirs)

**Features**:
- Three-pane layout (split evenly or adjustable)
- Syntax highlighting for RDF (Turtle format)
- Conflict markers (highlighted sections)
- Resolution buttons per conflict: "Accept Base", "Accept Ours", "Accept Theirs", "Edit Manually"
- Conflict navigation: "Prev", "Next"
- Conflict counter: "3 of 12 resolved"
- Graph-level resolution: "Accept Ours for all triples in graph"
- Preview merge result

**Technology**: Custom layout + CodeMirror or text editors

**Reusable?**: ⚠️ Partially
- **Generic**: Three-pane conflict resolver UI
- **RDF-specific**: Triple/quad conflict display, graph-level resolution

**Recommendation**: **Split**
- **CHUCC-SQUI**: Generic three-pane diff viewer with syntax highlighting
- **Frontend**: Conflict resolution logic + CHUCC API integration

---

#### 3.2.2 Conflict Summary Component

**Description**: Overview of all conflicts in a merge

**Features**:
- Total conflicts count: "12 graphs in conflict, 45 triples"
- Graph-by-graph breakdown (accordion or list)
- Click graph → jump to resolver
- Resolution status per graph: "3/5 resolved"
- "Resolve All" button with strategy selector

**Technology**: Carbon Accordion + custom logic

**Reusable?**: ❌ No
- **CHUCC-specific**: Graph-level conflict model from protocol

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's conflict structure

---

#### 3.2.3 Merge Configuration Modal

**Description**: Modal dialog for configuring merge operation

**Features**:
- Source branch selector (ComboBox)
- Target branch selector (ComboBox)
- Strategy selector (RadioButton: three-way, ours, theirs, manual)
- Fast-forward mode (RadioButton: allow, only, never)
- Author input (TextInput)
- Commit message (TextArea)
- Preview button (opens diff view)

**Technology**: Carbon ComposedModal + form components

**Reusable?**: ❌ No
- **CHUCC-specific**: Merge strategies and parameters from protocol

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC merge API

---

### 3.3 RDF Visualization Components

#### 3.3.1 RDF Patch Viewer (Syntax Highlighted)

**Description**: Syntax-highlighted viewer for RDF Patch format

**Features**:
- Syntax highlighting for RDF Patch directives (TX, TC, PA, PD, A, D, etc.)
- Line numbers
- Collapsible sections (e.g., collapse large additions)
- Search within patch (Ctrl+F)
- Copy patch to clipboard

**Technology**: CodeMirror 6 with custom RDF Patch language mode

**Reusable?**: ✅ Yes - RDF Patch is a standard format
- **Generic**: Any RDF application using RDF Patch
- **CHUCC-specific**: None

**Recommendation**: **CHUCC-SQUI**
- **Rationale**: Reusable RDF component, standard format
- **Priority**: High (core to version control diff display)

---

#### 3.3.2 RDF Diff Viewer (Side-by-Side)

**Description**: Side-by-side comparison of two RDF graphs/datasets

**Features**:
- Two-pane layout (before | after)
- Syntax highlighting (Turtle, JSON-LD, or N-Triples)
- Inline diff markers (green for added, red for deleted, yellow for modified)
- Triple-by-triple comparison
- Toggle between unified and side-by-side
- Spacebar to toggle mode

**Technology**: CodeMirror 6 with diff addon + custom RDF logic

**Reusable?**: ✅ Yes - Generic RDF diff visualization
- **Generic**: Compare any two RDF graphs
- **CHUCC-specific**: None

**Recommendation**: **CHUCC-SQUI**
- **Rationale**: Reusable RDF component
- **Priority**: High (essential for version control)

---

#### 3.3.3 Graph Explorer (Triple Table/Tree)

**Description**: Display triples in a named graph as table or tree structure

**Features**:
- **Table view**: Subject | Predicate | Object columns
- **Tree view**: Subject → Predicate → Object hierarchy
- Virtual scrolling for large graphs (10k+ triples)
- Filter by subject, predicate, object (text search)
- Click triple → show in detail panel
- Syntax highlighting for URIs, literals, blank nodes

**Technology**: Carbon DataTable (table) or TreeView (tree) + virtual scroll

**Reusable?**: ✅ Yes - Generic RDF graph viewer
- **Generic**: View any RDF graph
- **CHUCC-specific**: None

**Recommendation**: **CHUCC-SQUI**
- **Rationale**: Reusable RDF component
- **Priority**: Medium (enhances graph browsing, not core VC feature)

---

### 3.4 Dataset Management Components

#### 3.4.1 Dataset Creation Wizard

**Description**: Step-by-step wizard for creating new dataset

**Features**:
- **Step 1**: Dataset name (TextInput with validation)
- **Step 2**: Description (TextArea)
- **Step 3**: Kafka configuration (NumberInputs for partitions, replication, retention)
- **Step 4**: Initial graph (optional, FileUploader or TextArea)
- **Step 5**: Review and confirm
- Progress indicator (Carbon ProgressIndicator)

**Technology**: Carbon ProgressIndicator + Modal + Form components

**Reusable?**: ❌ No
- **CHUCC-specific**: Kafka config, dataset creation API

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's dataset creation endpoint

---

#### 3.4.2 Dataset Health Dashboard

**Description**: Display health status of datasets (Kafka topics, projection lag)

**Features**:
- Dataset list with health indicators (green/yellow/red)
- Per-dataset metrics:
  - Kafka topic status (healthy, degraded, down)
  - Partition count
  - Consumer lag (events not yet projected)
  - Last successful projection timestamp
- "Rebuild View" button (triggers manual projection)
- "Heal Topic" button (triggers topic repair)

**Technology**: Carbon DataTable + custom health indicators

**Reusable?**: ❌ No
- **CHUCC-specific**: Kafka health, projection monitoring

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's event sourcing architecture

---

#### 3.4.3 Dataset Switcher (Enhanced Dropdown)

**Description**: Advanced dataset selector with search, favorites, recent

**Features**:
- Searchable dropdown (Carbon ComboBox)
- Recently used datasets at top
- Favorites (star icon to favorite/unfavorite)
- Dataset metadata tooltip (description, commit count, branch count)
- "Create New Dataset" action at bottom

**Technology**: Carbon ComboBox + custom rendering

**Reusable?**: ❌ No
- **CHUCC-specific**: Dataset API, favorites stored per-user

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's multi-tenant architecture

---

### 3.5 Batch Operation Components

#### 3.5.1 Batch Operation Builder

**Description**: Visual builder for constructing atomic batch operations

**Features**:
- Operation list (table): Type | Graph | Content | Order
- Add operation button (opens modal)
- Remove operation button (trash icon per row)
- Reorder operations (drag handles, up/down buttons)
- Operation types: PUT, POST, PATCH, DELETE (for GSP)
- Mode selector: "single-commit" vs "multi-commit"
- Preview pane (shows resulting RDF Patch or commit structure)
- Execute button (prominent)

**Technology**: Carbon DataTable + drag-drop + modal forms

**Reusable?**: ❌ No
- **CHUCC-specific**: Batch operations API (`POST /version/batch-graphs`)

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC's batch operations endpoint

---

#### 3.5.2 Batch Operation Preview

**Description**: Preview the result of a batch operation before execution

**Features**:
- Shows resulting commit structure (for single-commit mode)
- Shows RDF Patch for each operation (for multi-commit mode)
- Triple counts per operation (+/- triples)
- Affected graphs list
- "Execute" button (green, prominent)
- "Cancel" button (secondary)

**Technology**: Custom component + RDF Patch viewer

**Reusable?**: ❌ No
- **CHUCC-specific**: Batch operation preview logic

**Recommendation**: **Frontend Project**
- **Rationale**: Specific to CHUCC API

---

### 3.6 Utility Components

#### 3.6.1 Quick Launch Command Palette

**Description**: Keyboard-driven command palette (Ctrl+P) for quick actions

**Features**:
- Fuzzy search for commands, branches, datasets
- Recent commands shown first
- Command categories: "Branch", "Commit", "Query", "Dataset"
- Keyboard navigation (↑/↓ arrows, Enter to execute)
- Close on Escape

**Technology**: Custom component (modal overlay + search + list)

**Reusable?**: ⚠️ Partially
- **Generic**: Command palette pattern
- **CHUCC-specific**: Commands tied to CHUCC API

**Recommendation**: **Frontend Project**
- **Rationale**: Commands are CHUCC-specific, though pattern is generic

---

#### 3.6.2 Commit SHA Badge (Copyable)

**Description**: Display commit SHA with one-click copy

**Features**:
- Short SHA (first 8 chars) displayed
- Tooltip shows full SHA on hover
- Click → copy full SHA to clipboard
- Visual feedback (checkmark animation)

**Technology**: Carbon Tag + CopyButton

**Reusable?**: ✅ Somewhat
- **Generic**: Copyable hash badge
- **CHUCC-specific**: UUIDv7 format

**Recommendation**: **CHUCC-SQUI**
- **Rationale**: Could be reused in other RDF/version control apps

---

#### 3.6.3 Author Selector / Input

**Description**: Input for selecting or entering commit author

**Features**:
- ComboBox with recent authors
- Free-text input for new authors
- Format validation (Name <email@example.com>)
- Persist in browser localStorage

**Technology**: Carbon ComboBox + validation

**Reusable?**: ✅ Yes
- **Generic**: Author input pattern common in version control

**Recommendation**: **Frontend Project** (but could be CHUCC-SQUI)
- **Rationale**: Simple enough to implement inline, not worth separate package

---

## 4. Component Placement Decision Matrix

### 4.1 Decision Criteria

**Belongs in CHUCC-SQUI if**:
- ✅ Reusable across RDF/SPARQL applications
- ✅ Generic RDF or SPARQL functionality
- ✅ No CHUCC-specific business logic or API calls
- ✅ Could be useful in other projects

**Belongs in Frontend Project if**:
- ✅ CHUCC-specific business logic
- ✅ Tightly coupled to CHUCC API endpoints
- ✅ Relies on CHUCC's architecture (event sourcing, Kafka, etc.)
- ✅ Not reusable outside CHUCC context

---

### 4.2 Decision Table

| Component | Reusable? | Generic RDF? | CHUCC API? | **Placement** | Priority |
|-----------|-----------|--------------|------------|---------------|----------|
| **Commit Graph Visualizer** | ⚠️ Partial | No | Yes | **Frontend** | High |
| **Branch Selector** | ⚠️ Partial | No | Yes | **Frontend** | High |
| **Commit Detail View** | ⚠️ Partial | Some | Yes | **Frontend** (use SQUI for RDF Patch) | High |
| **History Filter** | ❌ No | No | Yes | **Frontend** | Medium |
| **Time Travel Controls** | ❌ No | No | Yes | **Frontend** | High |
| **Conflict Resolver (3-Pane)** | ⚠️ Partial | Yes | Some | **Split** (SQUI: diff view, Frontend: logic) | High |
| **Conflict Summary** | ❌ No | No | Yes | **Frontend** | High |
| **Merge Configuration Modal** | ❌ No | No | Yes | **Frontend** | High |
| **RDF Patch Viewer** | ✅ Yes | Yes | No | **CHUCC-SQUI** | High |
| **RDF Diff Viewer (Side-by-Side)** | ✅ Yes | Yes | No | **CHUCC-SQUI** | High |
| **Graph Explorer (Table/Tree)** | ✅ Yes | Yes | No | **CHUCC-SQUI** | Medium |
| **Dataset Creation Wizard** | ❌ No | No | Yes | **Frontend** | Medium |
| **Dataset Health Dashboard** | ❌ No | No | Yes | **Frontend** | Medium |
| **Dataset Switcher** | ❌ No | No | Yes | **Frontend** | High |
| **Batch Operation Builder** | ❌ No | No | Yes | **Frontend** | Medium |
| **Batch Operation Preview** | ❌ No | No | Yes | **Frontend** | Medium |
| **Quick Launch Palette** | ⚠️ Partial | No | Yes | **Frontend** | Medium |
| **Commit SHA Badge** | ✅ Somewhat | No | No | **CHUCC-SQUI** | Low |
| **Author Input** | ✅ Yes | No | No | **Frontend** (simple) | Low |

---

### 4.3 Summary

**CHUCC-SQUI (7 components)**:
1. RDF Patch Viewer (syntax highlighting)
2. RDF Diff Viewer (side-by-side)
3. Graph Explorer (triple table/tree)
4. Commit SHA Badge (copyable)
5. (Part of) Conflict Resolver (3-pane diff view)
6. RDF Patch Syntax Highlighter (CodeMirror language mode)
7. Author Input (reusable)

**Frontend Project (11 components)**:
1. Commit Graph Visualizer (DAG)
2. Branch Selector (with status indicators)
3. Commit Detail View (layout + API)
4. History Filter Component
5. Time Travel Controls
6. Conflict Summary
7. Merge Configuration Modal
8. Dataset Creation Wizard
9. Dataset Health Dashboard
10. Dataset Switcher
11. Batch Operation Builder
12. Batch Operation Preview
13. Quick Launch Palette
14. (Part of) Conflict Resolver (conflict resolution logic)

---

## 5. Implementation Priorities

### 5.1 Phase 1: Core SPARQL (Already Complete via CHUCC-SQUI)
- ✅ Query Editor
- ✅ Results Table
- ✅ Export Menu
- ✅ Prefix Manager

### 5.2 Phase 2: Basic Version Control (Minimal Viable Product)
**CHUCC-SQUI**:
1. RDF Patch Viewer (High priority)

**Frontend**:
1. Commit Graph Visualizer (High priority)
2. Branch Selector (High priority)
3. Commit Detail View (High priority)
4. Dataset Switcher (High priority)

### 5.3 Phase 3: Advanced Version Control
**CHUCC-SQUI**:
1. RDF Diff Viewer (High priority)

**Frontend**:
1. Merge Configuration Modal (High priority)
2. Time Travel Controls (High priority)

### 5.4 Phase 4: Conflict Resolution
**CHUCC-SQUI**:
1. (Part of) Conflict Resolver - 3-pane diff view

**Frontend**:
1. Conflict Summary (High priority)
2. (Part of) Conflict Resolver - resolution logic

### 5.5 Phase 5: Dataset Management
**Frontend**:
1. Dataset Creation Wizard (Medium priority)
2. Dataset Health Dashboard (Medium priority)

### 5.6 Phase 6: Batch Operations
**Frontend**:
1. Batch Operation Builder (Medium priority)
2. Batch Operation Preview (Medium priority)

### 5.7 Phase 7: Enhanced Browsing
**CHUCC-SQUI**:
1. Graph Explorer (Medium priority)

**Frontend**:
1. History Filter Component (Medium priority)
2. Quick Launch Palette (Medium priority)

### 5.8 Phase 8: Polish
**CHUCC-SQUI**:
1. Commit SHA Badge (Low priority)
2. Author Input (Low priority)

---

## 6. Technology Stack Recommendations

### 6.1 For CHUCC-SQUI Components

**Graph Visualization**:
- ❌ Not in CHUCC-SQUI (commit graph is CHUCC-specific)

**Syntax Highlighting**:
- ✅ CodeMirror 6 (already used for SPARQL)
- ✅ Custom language modes for RDF Patch

**Diff Visualization**:
- ✅ CodeMirror 6 diff addon (merge view)
- ✅ Custom RDF-aware diff algorithm

**Data Display**:
- ✅ Carbon DataTable (already used for results)
- ✅ Virtual scrolling (already implemented)

---

### 6.2 For Frontend Components

**Graph Visualization**:
- **Option 1: D3.js** - Most flexible, steep learning curve
- **Option 2: Cytoscape.js** - Good for complex graphs, easier than D3
- **Option 3: Vis.js** - Simpler, good for timelines
- **Recommendation**: **Cytoscape.js** or **D3.js**
  - Cytoscape.js: If graph layout complexity is high
  - D3.js: If custom rendering and animations are needed

**State Management**:
- ✅ Svelte stores (simple, built-in)
- ⚠️ Consider Zustand or Pinia if complexity grows

**Routing**:
- ✅ SvelteKit (if using SvelteKit)
- ✅ Page.js or Navaid (lightweight alternatives)

**HTTP Client**:
- ✅ Fetch API (native)
- ✅ Axios (if advanced features needed)

---

## 7. Architectural Considerations

### 7.1 Component Communication

**Parent → Child**: Props (Svelte standard)
**Child → Parent**: Events (Svelte standard)
**Global State**: Svelte stores

**Example**:
```svelte
<!-- CommitGraph.svelte -->
<script>
  import { commitStore } from '$lib/stores';
  $: commits = $commitStore;
</script>

<CommitGraphVisualizer commits={commits} on:selectCommit={handleSelect} />
```

### 7.2 API Integration

**Pattern**: Service layer (TypeScript classes or functions)

**Example**:
```typescript
// services/versionControlService.ts
export class VersionControlService {
  async getCommits(dataset: string, branch: string): Promise<Commit[]> {
    const response = await fetch(`/version/history?dataset=${dataset}&branch=${branch}`);
    return response.json();
  }

  async merge(dataset: string, from: string, into: string, strategy: MergeStrategy): Promise<MergeResult> {
    const response = await fetch(`/version/merge?dataset=${dataset}`, {
      method: 'POST',
      body: JSON.stringify({ from, into, strategy })
    });
    return response.json();
  }
}
```

### 7.3 Error Handling

**Pattern**: RFC 7807 Problem Details parser

**Example**:
```typescript
// utils/errorHandler.ts
export function handleApiError(response: Response): ProblemDetails {
  if (response.headers.get('content-type')?.includes('application/problem+json')) {
    return response.json(); // Returns { type, title, status, detail, instance, ... }
  }
  throw new Error(`HTTP ${response.status}: ${response.statusText}`);
}
```

---

## 8. Testing Strategy

### 8.1 CHUCC-SQUI Components

**Unit Tests**: Vitest
- Test RDF Patch parser
- Test diff algorithm
- Test virtual scrolling logic

**Component Tests**: Playwright
- Test RDF Patch viewer rendering
- Test diff viewer interactions
- Test graph explorer filtering

**Visual Regression**: Storybook + Chromatic
- Ensure UI consistency across changes

---

### 8.2 Frontend Components

**Unit Tests**: Vitest
- Test state management (stores)
- Test service layer (API calls, mocked)
- Test utility functions

**Component Tests**: Playwright
- Test commit graph interactions
- Test branch selector search
- Test conflict resolution workflow

**Integration Tests**: Playwright
- Test end-to-end workflows (create commit, merge branches, resolve conflicts)
- Test against CHUCC-server (Testcontainers)

---

## 9. Accessibility Considerations

All custom components must meet **WCAG 2.1 AA** standards:

- ✅ **Keyboard navigation**: All interactions accessible via keyboard
- ✅ **ARIA labels**: Screen reader support
- ✅ **Focus indicators**: Visible focus states
- ✅ **Color contrast**: 4.5:1 for text, 3:1 for UI elements
- ✅ **Error messages**: Associated with form fields

**Example**:
```svelte
<button
  aria-label="Merge feature branch into main"
  on:click={handleMerge}
  on:keydown={(e) => e.key === 'Enter' && handleMerge()}
>
  Merge
</button>
```

---

## 10. Performance Considerations

### 10.1 Large Commit Histories

**Strategy**: Virtual scrolling + pagination
- Load 100 commits at a time
- Lazy-load commit details (only when selected)
- Debounce search/filter inputs

### 10.2 Large Diffs

**Strategy**: Lazy rendering + collapsing
- Render visible portion of diff first
- Collapse large additions/deletions (show summary, expand on demand)
- Use Web Workers for syntax highlighting (non-blocking)

### 10.3 Large Graphs

**Strategy**: Virtual scrolling + filtering
- Carbon DataTable already supports virtual scrolling
- Limit initial display to 1000 triples, paginate rest
- Filter by subject/predicate/object to reduce visible set

---

## 11. Recommendations

### 11.1 For CHUCC-SQUI Maintainers

**Add These Components** (in priority order):
1. **RDF Patch Viewer** (High) - Essential for version control
2. **RDF Diff Viewer** (High) - Essential for comparing commits
3. **Graph Explorer** (Medium) - Enhances graph browsing
4. **Commit SHA Badge** (Low) - Nice-to-have utility
5. **Author Input** (Low) - Reusable form component

**Technology**:
- CodeMirror 6 language mode for RDF Patch
- CodeMirror 6 merge view for side-by-side diff
- Virtual scrolling (already have expertise from results table)

**Testing**:
- Storybook stories for each component
- Playwright tests for interactions
- Publish to npm for easy integration

---

### 11.2 For CHUCC Frontend Developers

**Leverage Carbon & CHUCC-SQUI**:
- Use Carbon components for all standard UI (forms, modals, navigation)
- Use CHUCC-SQUI for SPARQL query and RDF visualization
- Build custom components only for CHUCC-specific features

**Focus on Version Control UX**:
- Prioritize commit graph, branch selector, conflict resolver
- Emulate Fork's elegance (drag-drop, colored graphs, Quick Launch)
- Emulate Fuseki's simplicity (tab-based, focused)

**Performance**:
- Virtual scrolling everywhere (commit list, results, graphs)
- Lazy-load details (commit diffs, RDF patches)
- Debounce search/filter inputs

**Accessibility**:
- Use Carbon's built-in accessibility features
- Add ARIA labels to custom components
- Test with keyboard navigation and screen readers

---

## 12. Conclusion

**Available Components**:
- ✅ Carbon: 165+ components (comprehensive standard UI)
- ✅ CHUCC-SQUI: 8 components (SPARQL query + results)

**Missing Components**:
- ❌ 15+ specialized components for version control and RDF visualization

**Placement**:
- **CHUCC-SQUI**: 7 reusable RDF/SPARQL components
- **Frontend**: 11 CHUCC-specific components

**Next Steps**:
1. ✅ Complete research (Tasks 1-3)
2. ⏩ Synthesize frontend concept with mockups (Task 4)
3. ⏩ Create implementation roadmap
4. ⏩ Begin Phase 1 development

---

## Appendix: Component API Sketches

### A.1 RDF Patch Viewer (CHUCC-SQUI)

```svelte
<script lang="ts">
  export let patch: string; // RDF Patch text
  export let readonly: boolean = true;
  export let showLineNumbers: boolean = true;
</script>

<RdfPatchViewer
  {patch}
  {readonly}
  {showLineNumbers}
  on:copy={(e) => console.log('Copied', e.detail)}
/>
```

### A.2 Commit Graph Visualizer (Frontend)

```svelte
<script lang="ts">
  import type { Commit } from '$lib/types';
  export let commits: Commit[];
  export let selectedCommitId: string | null = null;
</script>

<CommitGraph
  {commits}
  {selectedCommitId}
  on:select={(e) => handleSelectCommit(e.detail.commitId)}
  on:contextmenu={(e) => showContextMenu(e.detail.commitId, e.detail.mouseEvent)}
/>
```

### A.3 Conflict Resolver (Frontend + CHUCC-SQUI)

```svelte
<script lang="ts">
  import type { Conflict } from '$lib/types';
  import { RdfDiffViewer } from 'chucc-squi'; // From CHUCC-SQUI

  export let conflict: Conflict;
</script>

<ConflictResolver>
  <div class="three-pane">
    <RdfDiffViewer title="Base" content={conflict.base} />
    <RdfDiffViewer title="Ours" content={conflict.ours} />
    <RdfDiffViewer title="Theirs" content={conflict.theirs} />
  </div>

  <div class="actions">
    <Button on:click={() => resolve('base')}>Accept Base</Button>
    <Button on:click={() => resolve('ours')}>Accept Ours</Button>
    <Button on:click={() => resolve('theirs')}>Accept Theirs</Button>
  </div>
</ConflictResolver>
```

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
