# CHUCC Frontend - Information Architecture

**Date**: 2025-11-10
**Version**: 1.0

---

## 1. Executive Summary

This document defines the information architecture for the CHUCC frontend, including:
- **Navigation hierarchy** - How users move between features
- **Routing structure** - URL patterns and navigation flows
- **Primary views** - 7 main application views
- **Content organization** - How information is structured and prioritized

**Design Philosophy**:
- **Tab-based primary navigation** (Fuseki pattern) for main feature areas
- **Sidebar for context** (Fork pattern) in version control views
- **Single-page app** with client-side routing
- **Progressive disclosure** - show simple first, advanced on demand

---

## 2. Site Map

```
CHUCC Frontend
│
├── Query Workbench (/query)
│   ├── Query Editor
│   ├── Results Viewer
│   ├── Prefix Manager (modal)
│   └── Query History (sidebar, optional)
│
├── Graph Explorer (/graphs)
│   ├── Graph List (sidebar)
│   ├── Graph Viewer (main)
│   ├── Upload Graph (modal)
│   └── Graph Operations (PUT, POST, DELETE, PATCH)
│
├── Version Control (/version)
│   ├── Commit Graph (main)
│   ├── Branches (sidebar)
│   ├── Tags (sidebar)
│   ├── Commit Details (bottom panel)
│   ├── History Filter (toolbar)
│   └── Advanced Operations (modals)
│       ├── Merge
│       ├── Cherry-Pick
│       ├── Revert
│       ├── Reset
│       ├── Rebase
│       └── Squash
│
├── Merge & Conflicts (/merge/conflicts)
│   ├── Conflict Summary
│   ├── Conflict List (sidebar)
│   ├── Conflict Resolver (main, 3-pane)
│   └── Merge Preview (modal)
│
├── Time Travel (/time-travel)
│   ├── Query Workbench (enhanced)
│   ├── Timeline Slider
│   ├── asOf Date Picker
│   ├── Commit Selector
│   └── Comparison Mode (side-by-side results)
│
├── Dataset Manager (/datasets)
│   ├── Dataset List (table)
│   ├── Dataset Detail (panel)
│   ├── Create Dataset (wizard modal)
│   ├── Delete Dataset (confirmation modal)
│   └── Dataset Health (dashboard)
│
├── Batch Operations (/batch)
│   ├── Operation Builder (main)
│   ├── Operation List (table)
│   ├── Add Operation (modal)
│   ├── Preview (panel)
│   └── Execute (confirmation)
│
└── Settings (/settings)
    ├── User Profile (author metadata)
    ├── Theme Preferences
    ├── Default Prefixes
    ├── Keyboard Shortcuts
    └── API Configuration
```

---

## 3. Navigation Hierarchy

### 3.1 Primary Navigation (Top-Level)

**Pattern**: Horizontal tabs in header (always visible)

**Structure**:
```
┌────────────────────────────────────────────────────────────┐
│ [CHUCC Logo]  [Query] [Graphs] [Version] [Time Travel]    │
│               [Datasets] [Batch]                [Settings] │
└────────────────────────────────────────────────────────────┘
```

**Navigation Items**:
1. **Query** - SPARQL query workbench (default landing page)
2. **Graphs** - Graph Store Protocol operations
3. **Version** - Version control (commits, branches, tags)
4. **Time Travel** - Historical queries with asOf selector
5. **Datasets** - Multi-dataset management
6. **Batch** - Atomic batch operations
7. **Settings** - User preferences (right-aligned)

**Active State**: Current tab highlighted with blue underline (Carbon style)

---

### 3.2 Secondary Navigation (Context-Specific)

#### 3.2.1 Query Workbench - No Secondary Nav
- Single-pane focus on query editing and results
- Actions via toolbar buttons (Execute, Export, Prefixes)

#### 3.2.2 Graph Explorer - Sidebar (Left)
- **Graph List**: Collapsible tree
  - Default Graph (always present)
  - Named Graphs (alphabetically sorted)
- **Actions**: Add, Delete, Upload (buttons at bottom)

#### 3.2.3 Version Control - Sidebar (Left) + Bottom Panel
- **Sidebar**:
  - Branches (collapsible section)
    - Local Branches (with current indicator)
    - Remote Branches (collapsible subsection)
  - Tags (collapsible section)
  - Filter Input (at top)
- **Bottom Panel**: Commit details (appears when commit selected)

#### 3.2.4 Merge & Conflicts - Sidebar (Left)
- **Conflict List**: Graph-by-graph accordion
  - Each graph shows: "3 of 5 conflicts resolved"
  - Click graph → show resolver in main panel

#### 3.2.5 Time Travel - Toolbar (Top)
- **Timeline Controls**: Date picker, slider, commit selector
- **Mode Toggle**: Single query vs. Comparison mode

#### 3.2.6 Dataset Manager - No Secondary Nav
- **Table View**: Dataset list with actions in rows

#### 3.2.7 Batch Operations - No Secondary Nav
- **Builder View**: Operation list with add/remove actions

---

### 3.3 Tertiary Navigation (Modals & Panels)

**Modals** (overlay entire app):
- Prefix Manager (from Query Workbench)
- Upload Graph (from Graph Explorer)
- Create Dataset Wizard (from Dataset Manager)
- Delete Dataset Confirmation (from Dataset Manager)
- Merge Configuration (from Version Control)
- Cherry-Pick / Revert / Reset / Rebase / Squash (from Version Control)
- Add Operation (from Batch Operations)

**Panels** (slide out from side or bottom):
- Query History (optional, from Query Workbench)
- Commit Details (from Version Control)
- Dataset Health (from Dataset Manager)

**Popovers** (contextual):
- Help tooltips (? icons)
- Branch info on hover
- Commit SHA on hover

---

## 4. Routing Structure

### 4.1 URL Patterns

**Base URL**: `https://chucc.example.com/`

**Routes**:
```
/                                    → Redirect to /query
/query                               → Query Workbench
/query?dataset={name}&branch={name}  → Query with pre-selected dataset/branch

/graphs                              → Graph Explorer
/graphs?dataset={name}&graph={uri}   → Graph Explorer with specific graph selected

/version                             → Version Control (default: current branch)
/version?dataset={name}&branch={name}     → Version Control for specific branch
/version/commits/{commitId}          → Version Control with commit selected

/merge/conflicts                     → Merge Conflicts Resolver
/merge/conflicts?dataset={name}&merge={id} → Specific merge conflict

/time-travel                         → Time Travel Queries
/time-travel?dataset={name}&asOf={timestamp} → Time Travel with asOf pre-set

/datasets                            → Dataset Manager
/datasets/{name}                     → Dataset Detail View

/batch                               → Batch Operations
/batch?dataset={name}                → Batch Operations for specific dataset

/settings                            → Settings
/settings/profile                    → User Profile
/settings/theme                      → Theme Preferences
/settings/shortcuts                  → Keyboard Shortcuts
```

### 4.2 Query Parameters

**Global Parameters** (applicable to most routes):
- `dataset={name}` - Selected dataset (default: "default")
- `branch={name}` - Selected branch (default: "main")
- `commit={commitId}` - Selected commit (UUIDv7)
- `asOf={timestamp}` - Time-travel timestamp (RFC 3339)

**View-Specific Parameters**:
- **Query**: `query={base64}` - Pre-filled query (for sharing)
- **Graphs**: `graph={uri}` - Selected named graph
- **Version**: `filter={text}` - Branch/commit filter
- **Merge**: `merge={id}` - Merge operation ID
- **Time Travel**: `compare={timestamp}` - Second timestamp for comparison
- **Batch**: `operation={json}` - Pre-configured operation

### 4.3 Hash Navigation (Within Views)

Use hash fragments for sub-view navigation (doesn't trigger page reload):

- `/version#branches` - Scroll to branches section
- `/version#tags` - Scroll to tags section
- `/settings#theme` - Jump to theme settings

---

## 5. Content Organization

### 5.1 Header (Global)

**Layout**:
```
┌─────────────────────────────────────────────────────────────────┐
│ [Logo] [Query][Graphs][Version][Time Travel][Datasets][Batch]  │
│                                              [Dataset▾] [⚙️]     │
└─────────────────────────────────────────────────────────────────┘
```

**Components** (left to right):
1. **Logo** (left): "CHUCC" logo + icon (click → home/query)
2. **Primary Nav Tabs** (center-left): Main feature areas
3. **Dataset Selector** (right): Dropdown showing current dataset
4. **Settings Icon** (far right): Gear icon → Settings page

**Sticky**: Header fixed at top (always visible on scroll)

---

### 5.2 Footer (Global)

**Layout**:
```
┌─────────────────────────────────────────────────────────────────┐
│ Dataset: my-dataset  |  Branch: main  |  Author: Alice         │
│ [Notification Toast Area]                                      │
└─────────────────────────────────────────────────────────────────┘
```

**Components**:
1. **Status Bar** (left): Current context (dataset, branch, author)
2. **Notification Area** (bottom-right): Toast notifications appear here

**Sticky**: Footer fixed at bottom

---

### 5.3 Main Content Area

**Layout Patterns**:

#### Pattern A: Single Pane (Query, Batch, Datasets)
```
┌────────────────────────────────────┐
│                                    │
│                                    │
│         Full-Width Content         │
│                                    │
│                                    │
└────────────────────────────────────┘
```

#### Pattern B: Sidebar + Main (Graphs, Version, Merge)
```
┌──────────┬─────────────────────────┐
│          │                         │
│ Sidebar  │     Main Content        │
│ (240px)  │                         │
│          │                         │
└──────────┴─────────────────────────┘
```

#### Pattern C: Sidebar + Main + Bottom Panel (Version Control)
```
┌──────────┬─────────────────────────┐
│          │                         │
│ Sidebar  │     Commit Graph        │
│          │                         │
├──────────┴─────────────────────────┤
│     Commit Details / Diff          │
│                                    │
└────────────────────────────────────┘
```

#### Pattern D: Three-Pane (Merge Conflicts)
```
┌──────────┬──────────┬──────────────┐
│          │          │              │
│ Conflict │   Base   │     Ours     │ Theirs (below)
│ List     │          │              │
│          │          │              │
└──────────┴──────────┴──────────────┘
```

---

## 6. Information Hierarchy

### 6.1 Priority Levels

**P1 (Critical)**: Always visible, primary user goal
- Query editor (Query Workbench)
- Commit graph (Version Control)
- Dataset selector (Header)
- Execute/Commit/Merge buttons

**P2 (Important)**: Visible but secondary
- Results table (Query Workbench)
- Branch list (Version Control)
- Commit details (Version Control)
- Graph list (Graph Explorer)

**P3 (Supporting)**: Hidden by default, shown on demand
- Prefix manager (modal)
- Query history (panel)
- Advanced operations (modals)
- Settings (separate page)

### 6.2 Visual Weight

**Heavy** (draws attention):
- Primary action buttons (blue, prominent)
- Current dataset/branch indicators (bold, highlighted)
- Active tab (blue underline)
- Error states (red)

**Medium** (standard):
- Secondary action buttons (ghost style)
- Navigation items (inactive tabs)
- Data tables (standard rows)

**Light** (de-emphasized):
- Help text, tooltips
- Timestamps, metadata
- Disabled buttons

---

## 7. Responsive Breakpoints

### 7.1 Breakpoints (Carbon Design System)

| Breakpoint | Width | Layout |
|------------|-------|--------|
| **Small (sm)** | 320px - 671px | Mobile (stacked, collapsed sidebar) |
| **Medium (md)** | 672px - 1055px | Tablet (sidebar collapsible, split-pane vertical) |
| **Large (lg)** | 1056px - 1311px | Desktop (full layout, sidebar visible) |
| **X-Large (xlg)** | 1312px - 1583px | Wide desktop (spacious layout) |
| **Max (max)** | 1584px+ | Ultra-wide (max content width) |

### 7.2 Responsive Behavior

#### Mobile (320px - 671px)
- **Header**: Logo + hamburger menu (hide tabs)
- **Sidebar**: Collapsed by default (slide-out on hamburger click)
- **Query Workbench**: Editor and results stacked vertically
- **Version Control**: Commit graph full-width, details in modal
- **Merge Conflicts**: Three-pane becomes accordion (one pane at a time)

#### Tablet (672px - 1055px)
- **Header**: Logo + all tabs visible
- **Sidebar**: Collapsible (toggle button), overlay when open
- **Query Workbench**: Editor and results side-by-side (50/50 split)
- **Version Control**: Sidebar + graph, details in bottom panel
- **Merge Conflicts**: Two-pane visible, third in tab/toggle

#### Desktop (1056px+)
- **Header**: Full layout
- **Sidebar**: Always visible, adjustable width
- **Query Workbench**: Editor and results side-by-side (adjustable split)
- **Version Control**: Full three-pane (sidebar | graph | details)
- **Merge Conflicts**: Full three-pane visible

---

## 8. Navigation Patterns

### 8.1 Cross-View Navigation

**From Query Workbench**:
- Click triple in results → navigate to Graph Explorer with graph/triple selected
- Right-click branch selector → "View History" → navigate to Version Control

**From Graph Explorer**:
- Right-click graph → "View History" → navigate to Version Control, filter by graph

**From Version Control**:
- Click commit → show commit details (bottom panel)
- Double-click commit → navigate to Time Travel with asOf set to commit timestamp
- Right-click commit → "Query at this commit" → navigate to Time Travel

**From Time Travel**:
- Click "View Commit" → navigate to Version Control with commit selected

**From Dataset Manager**:
- Click dataset → switch dataset globally, stay on current view
- Click "Query" button → navigate to Query Workbench with dataset selected

---

### 8.2 Context Preservation

**Global Context** (persisted across views):
- **Current Dataset**: Stored in global state, persists in URL
- **Current Branch**: Stored in global state, persists in URL
- **Author**: Stored in localStorage, shown in footer

**View-Specific Context** (not preserved when switching views):
- Query text (Query Workbench)
- Selected commit (Version Control)
- Conflict resolution state (Merge & Conflicts)

**Session Persistence**:
- Store in browser localStorage:
  - Last selected dataset
  - Last selected branch per dataset
  - Query history (last 50 queries)
  - User preferences (theme, author)

---

### 8.3 Breadcrumb Navigation

**Not Used** - Primary nav tabs replace breadcrumbs
- Tabs always show current location
- Dataset/branch context shown in header and footer

**Exception**: Multi-step wizards (e.g., Create Dataset)
- Use Carbon ProgressIndicator component to show wizard steps

---

## 9. Search & Filter

### 9.1 Global Search (Not Implemented in v1)

**Future Feature**: Search bar in header
- Search commits by message
- Search branches by name
- Search datasets by name/description
- Search queries in history

**v1 Workaround**: View-specific filters (e.g., branch filter in Version Control)

---

### 9.2 View-Specific Filters

| View | Filter Location | Filter Targets |
|------|-----------------|----------------|
| **Version Control** | Sidebar top | Branches, commits by message, author |
| **Graph Explorer** | Sidebar top | Named graphs by URI |
| **Dataset Manager** | Table toolbar | Datasets by name, description |
| **Batch Operations** | Table toolbar | Operations by type, graph |

**Filter UI**: Carbon Search component (icon + text input)
**Behavior**: Live filtering (no submit button)

---

## 10. Keyboard Navigation

### 10.1 Global Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+P` / `⌘+P` | Open Quick Launch palette |
| `Ctrl+K` / `⌘+K` | Focus dataset selector |
| `Ctrl+1` ... `Ctrl+7` | Jump to primary nav tab (Query, Graphs, etc.) |
| `Ctrl+,` / `⌘+,` | Open Settings |
| `Esc` | Close modal/panel |
| `?` | Show keyboard shortcuts help |

### 10.2 View-Specific Shortcuts

**Query Workbench**:
- `Ctrl+Enter` / `⌘+Enter` - Execute query
- `Ctrl+/` / `⌘+/` - Toggle comment (in editor)
- `Ctrl+S` / `⌘+S` - Save query to history

**Graph Explorer**:
- `Ctrl+U` / `⌘+U` - Upload graph
- `Del` - Delete selected graph

**Version Control**:
- `Ctrl+F` / `⌘+F` - Focus filter input
- `↑` / `↓` - Navigate commits
- `Enter` - View commit details
- `Ctrl+M` / `⌘+M` - Merge current branch

**Merge & Conflicts**:
- `Ctrl+O` / `⌘+O` - Accept Ours
- `Ctrl+T` / `⌘+T` - Accept Theirs
- `Ctrl+B` / `⌘+B` - Accept Base
- `Ctrl+]` - Next conflict
- `Ctrl+[` - Previous conflict

---

## 11. Progressive Disclosure

### 11.1 Simple → Advanced

**Default View**: Simple, focused on common tasks
- Query Workbench: Editor + results (no advanced options visible)
- Version Control: Commit graph + branches (no advanced operations visible)

**Advanced Features** (hidden by default):
- Advanced query options (timeout, format preferences) → Expandable section
- Version control advanced operations (rebase, squash) → Context menu or toolbar overflow
- Dataset Kafka configuration → Expandable section in wizard

**Disclosure Patterns**:
- **Expandable Sections**: Carbon Accordion for collapsible content
- **Context Menus**: Right-click for advanced actions
- **Overflow Menus**: Three-dot menu for secondary actions
- **Modals**: Complex operations open in dedicated modals

---

### 11.2 Progressive Depth

**Level 1** (Overview): Summary information
- Dataset list → Name, description, main branch

**Level 2** (Details): More metadata
- Click dataset row → Expand to show commit count, branch count, created date

**Level 3** (Full Context): Complete information
- Click "View Details" → Navigate to Dataset Detail page with health dashboard

---

## 12. Error States & Empty States

### 12.1 Error States

**No Dataset Selected**:
- Show: "Please select a dataset to continue"
- Action: Prominent dataset selector button

**No Branch Selected**:
- Show: "Please select a branch"
- Action: Branch selector dropdown

**Network Error**:
- Show: "Unable to connect to CHUCC server"
- Action: "Retry" button

**404 Not Found**:
- Show: "Dataset not found" or "Commit not found"
- Action: "Go back" or "View all datasets"

**409 Conflict**:
- Show: "Merge conflict detected"
- Action: "Resolve conflicts" button → navigate to /merge/conflicts

---

### 12.2 Empty States

**No Commits**:
- Show: "No commits yet. Create your first commit by modifying a graph."
- Action: "Go to Graph Explorer" button

**No Branches**:
- Show: "No branches. The main branch will be created automatically."

**No Tags**:
- Show: "No tags yet. Create a tag from a commit."
- Action: "Learn about tags" link

**No Conflicts**:
- Show: "✓ No conflicts. Ready to merge."
- Action: "Complete merge" button

**No Datasets**:
- Show: "No datasets yet. Create your first dataset."
- Action: "Create Dataset" button (prominent, blue)

---

## 13. Loading States

### 13.1 Loading Patterns

**Initial Load** (full page):
- Carbon Loading component (spinner + "Loading CHUCC...")

**Partial Load** (content area):
- Carbon InlineLoading component (spinner in context)

**Background Load** (non-blocking):
- ProgressBar at top of view (thin blue line)
- Toast notification when complete

**Skeleton Loading** (for tables/lists):
- Carbon SkeletonText for commit list, dataset list

---

### 13.2 Loading Indicators

| Context | Indicator | Duration |
|---------|-----------|----------|
| **Page Load** | Full-page spinner | Until first content renders |
| **Query Execution** | Inline spinner in results area | Until results or error |
| **Commit Graph Load** | Skeleton rows | Until first 100 commits load |
| **Branch List Load** | Skeleton items | Until branches load |
| **Merge Operation** | Progress modal (indeterminate) | Until success/conflict |
| **Dataset Creation** | Progress modal (stepped) | Until 202 Accepted |

---

## 14. Notification System

### 14.1 Notification Types

**Success** (Green):
- "Query executed successfully"
- "Commit created: 019abc..."
- "Branch merged successfully"
- "Dataset created: my-dataset"

**Warning** (Yellow):
- "Projection lag detected (5 seconds behind)"
- "Kafka topic health degraded"
- "Large result set (10,000+ rows)"

**Error** (Red):
- "Query failed: Syntax error at line 5"
- "Merge conflict: 12 graphs in conflict"
- "Dataset creation failed: Name already exists"

**Info** (Blue):
- "Update accepted (202). Changes pending projection."
- "Time-travel query executed at 2025-11-10T10:30:00Z"

---

### 14.2 Notification Placement

**Toast Notifications** (bottom-right):
- Auto-dismiss after 5 seconds (success, info)
- Manual dismiss (warning, error)
- Stack vertically if multiple
- Max 3 visible at once

**Inline Notifications** (context-specific):
- Above results table (Query Workbench)
- Above commit graph (Version Control)
- Above operation list (Batch Operations)

**Modal Notifications** (blocking):
- Confirmations (delete dataset, reset branch)
- Critical errors (server unreachable)

---

## 15. Accessibility (WCAG 2.1 AA)

### 15.1 Keyboard Navigation

- ✅ All functionality accessible via keyboard
- ✅ Tab order follows visual hierarchy
- ✅ Focus indicators visible (2px blue outline)
- ✅ Skip links for main content ("Skip to content")

### 15.2 Screen Reader Support

- ✅ ARIA labels on all interactive elements
- ✅ ARIA landmarks (navigation, main, complementary)
- ✅ ARIA live regions for dynamic content (notifications, results)
- ✅ Alt text for icons and images

### 15.3 Color Contrast

- ✅ Text: 4.5:1 minimum (body text)
- ✅ UI elements: 3:1 minimum (buttons, inputs)
- ✅ Links: Underlined (not color-only)
- ✅ Error states: Icon + color + text

---

## 16. Summary

**Navigation Philosophy**: Tab-based (Fuseki) + Sidebar for context (Fork)
**Routing**: Client-side SPA with query parameters for state
**Layout Patterns**: 4 patterns (single-pane, sidebar, sidebar+bottom, three-pane)
**Responsive**: Mobile-first, 5 breakpoints (sm, md, lg, xlg, max)
**Progressive Disclosure**: Simple by default, advanced on demand
**Accessibility**: Full WCAG 2.1 AA compliance

**Next Steps**:
1. Create detailed UI mockups for 7 primary views
2. Define component architecture and state management
3. Document interaction flows for key operations
4. Define visual design system

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
