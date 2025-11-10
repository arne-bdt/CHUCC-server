# Query Workbench - UI Mockup

**View**: Query Workbench (SPARQL Query Interface)
**Route**: `/query`
**Inspiration**: Apache Jena Fuseki (simplicity, focus)

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [🔷 CHUCC]  [Query✓][Graphs][Version][Time Travel][Datasets][Batch]         │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ ┌─ Query Editor ─────────────────────────────────────────────────────────┐  │
│ │ [Tab 1: New Query *] [Tab 2: Saved Query] [+ New Tab]                  │  │
│ ├─────────────────────────────────────────────────────────────────────────┤  │
│ │  1 │ PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>        │  │
│ │  2 │ PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>             │  │
│ │  3 │                                                                    │  │
│ │  4 │ SELECT ?subject ?predicate ?object                                │  │
│ │  5 │ WHERE {                                                            │  │
│ │  6 │   ?subject ?predicate ?object .                                   │  │
│ │  7 │ }                                                                  │  │
│ │  8 │ LIMIT 100                                                          │  │
│ │  9 │                                                                    │  │
│ │ 10 │ ▊                                                                  │  │
│ │    │                                                                    │  │
│ └─────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│ [Branch: main ▾] [asOf: Latest ▾]                                            │
│ [📋 Prefixes] [📜 History] [💾 Save]     [▶ Execute Query] [⏹ Cancel]      │
│                                                                               │
│ ─────────────────────────────────────────────────────────────────────────────│
│                                                                               │
│ ┌─ Results (3 rows in 42ms) ────────────────────────────────────── [↓CSV]─┐ │
│ │ ┌─────────────────────┬────────────────────────┬──────────────────────┐ │ │
│ │ │ ?subject      [▲]   │ ?predicate        [▲] │ ?object         [▲] │ │ │
│ │ ├─────────────────────┼────────────────────────┼──────────────────────┤ │ │
│ │ │ ex:Alice            │ rdf:type               │ ex:Person            │ │ │
│ │ │ ex:Alice            │ ex:age                 │ 30                   │ │ │
│ │ │ ex:Bob              │ rdf:type               │ ex:Person            │ │ │
│ │ └─────────────────────┴────────────────────────┴──────────────────────┘ │ │
│ │ [◀] Page 1 of 1 [▶]                                           100 rows │ │
│ └───────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ Dataset: default  |  Branch: main  |  Author: Alice <alice@example.org>     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Header (Global)

**Component**: `<Header>` (Carbon)

**Contents**:
- **Logo** (left): `<HeaderName href="/">CHUCC</HeaderName>`
- **Primary Nav** (center): `<HeaderNavigation>`
  - Tab items: Query (active), Graphs, Version, Time Travel, Datasets, Batch
- **Dataset Selector** (right): `<Dropdown>` with search
  - Shows current dataset name
  - Dropdown shows: Recent datasets, All datasets (searchable)
- **Author Selector** (far right): `<ComboBox>`
  - Shows current author (from localStorage)
  - Editable, validates format: "Name <email@example.com>"

**State**:
```typescript
{
  currentDataset: string;      // "default"
  currentAuthor: string;        // "Alice <alice@example.org>"
  availableDatasets: string[]; // ["default", "my-dataset", ...]
}
```

---

### 2.2 Query Editor

**Component**: `<SparqlQueryEditor>` (from CHUCC-SQUI)

**Features**:
- **Tabs**: `<Tabs>` (Carbon)
  - Each tab = one query
  - Tab label shows: "New Query" or query name (if saved)
  - Dirty indicator: asterisk (*) if unsaved changes
  - Close button (×) on each tab
  - "+ New Tab" button at end
- **Editor**: CodeMirror 6 with SPARQL language mode
  - Line numbers (left)
  - Syntax highlighting (keywords, URIs, literals)
  - Autocomplete (Ctrl+Space)
  - Error underlining (red squiggly)
  - Word wrap (toggle in settings)
- **Toolbar** (below editor):
  - **Branch Selector**: `<Dropdown>` - Select branch for query execution
  - **asOf Selector**: `<Dropdown>` - "Latest" or date picker
  - **Prefixes Button**: Opens modal with prefix manager
  - **History Button**: Opens sidebar with query history
  - **Save Button**: Save query with name
  - **Execute Button**: Blue, prominent (Ctrl+Enter)
  - **Cancel Button**: Ghost style (stops running query)

**State**:
```typescript
{
  tabs: Array<{
    id: string;
    name: string;
    query: string;
    dirty: boolean;
    results?: QueryResults;
  }>;
  activeTabId: string;
  selectedBranch: string;   // "main"
  selectedAsOf: string | null; // null or ISO 8601 timestamp
}
```

**Interactions**:
- **Type in editor**: Auto-save to localStorage every 5 seconds
- **Ctrl+Enter**: Execute query in active tab
- **Ctrl+/**: Toggle line comment
- **Ctrl+S**: Save query (open save dialog if unnamed)
- **Click tab**: Switch active tab
- **Click × on tab**: Close tab (confirm if dirty)

---

### 2.3 Results Table

**Component**: `<ResultsTable>` (from CHUCC-SQUI with virtual scrolling)

**Features**:
- **Header Bar**:
  - **Title**: "Results (X rows in Yms)" or "No results"
  - **Export Menu**: `<OverflowMenu>` (right)
    - Download as: CSV, TSV, JSON, XML, Turtle, N-Triples, JSON-LD, RDF/XML
- **Table**: `<DataTable>` (Carbon) with virtual scrolling
  - Columns: One per SPARQL variable (e.g., ?subject, ?predicate, ?object)
  - Sortable columns (click header)
  - Resizable columns (drag divider)
  - Row hover: Highlight row
  - Cell types:
    - **URI**: `<Link>` with clickable icon → opens in Graph Explorer
    - **Literal**: Plain text with datatype tooltip
    - **Blank Node**: `_:b1` with gray styling
- **Pagination**: `<Pagination>` (Carbon)
  - Page size selector: 100, 500, 1000, All
  - Current page indicator
  - Previous/Next buttons

**State**:
```typescript
{
  results: {
    variables: string[];     // ["?subject", "?predicate", "?object"]
    bindings: Array<Record<string, RdfTerm>>; // [{?subject: {...}, ...}, ...]
    count: number;           // Total rows
    executionTime: number;   // Milliseconds
  } | null;
  currentPage: number;       // 1-indexed
  pageSize: number;          // 100, 500, 1000, -1 (all)
  sortColumn: string | null; // Variable name
  sortOrder: 'asc' | 'desc';
}
```

**Interactions**:
- **Click URI cell**: Navigate to Graph Explorer with graph/subject selected
- **Right-click cell**: Context menu (Copy value, Copy as N-Triples, etc.)
- **Click column header**: Sort by that column
- **Click export**: Download results in selected format
- **Change page size**: Re-render table with new page size

---

### 2.4 Prefix Manager (Modal)

**Component**: `<PrefixManager>` (Carbon `<ComposedModal>`)

**Layout**:
```
┌─ Manage Prefixes ──────────────────────────────────┐
│                                                     │
│ Common Prefixes:                                    │
│ ☑ rdf      http://www.w3.org/1999/02/22-...        │
│ ☑ rdfs     http://www.w3.org/2000/01/rdf-...       │
│ ☑ owl      http://www.w3.org/2002/07/owl#          │
│ ☑ xsd      http://www.w3.org/2001/XMLSchema#       │
│ ☐ foaf     http://xmlns.com/foaf/0.1/              │
│ ☐ dcterms  http://purl.org/dc/terms/               │
│                                                     │
│ Custom Prefixes:                                    │
│ ┌──────────┬──────────────────────────────────────┐│
│ │ Prefix   │ Namespace URI                        ││
│ ├──────────┼──────────────────────────────────────┤│
│ │ ex       │ http://example.org/                  ││
│ │ my       │ http://my.example.com/ns#            ││
│ │ [+ Add]  │                                      ││
│ └──────────┴──────────────────────────────────────┘│
│                                                     │
│                    [Cancel]  [Apply to Query]      │
└─────────────────────────────────────────────────────┘
```

**Features**:
- **Common Prefixes**: `<Checkbox>` list (pre-populated)
  - Check/uncheck to include in query
- **Custom Prefixes**: `<DataTable>`
  - Prefix column: `<TextInput>` (short name, e.g., "ex")
  - Namespace column: `<TextInput>` (full URI)
  - [+ Add] button: Adds new row
  - [×] button per row: Removes row
- **Actions**:
  - **Cancel**: Close modal without applying
  - **Apply to Query**: Insert PREFIX declarations at top of current query tab

**State**:
```typescript
{
  commonPrefixes: Array<{ prefix: string; uri: string; enabled: boolean }>;
  customPrefixes: Array<{ prefix: string; uri: string }>;
}
```

---

### 2.5 Query History (Sidebar Panel)

**Component**: `<QueryHistory>` (Carbon `<SideNav>` or `<Panel>`)

**Layout** (slides in from right):
```
┌─ Query History ─────────────────────┐
│ [Search queries...] [🔍]            │
│                                     │
│ ┌─ Today ─────────────────────────┐│
│ │ SELECT ?s ?p ?o WHERE { ... }   ││
│ │ 3 rows · 42ms · 10:30 AM        ││
│ │                                  ││
│ │ SELECT * WHERE { ... }           ││
│ │ 150 rows · 125ms · 9:45 AM      ││
│ └──────────────────────────────────┘│
│                                     │
│ ┌─ Yesterday ──────────────────────┐│
│ │ CONSTRUCT { ... } WHERE { ... }  ││
│ │ 50 triples · 200ms              ││
│ └──────────────────────────────────┘│
│                                     │
│ [Clear History]                     │
└─────────────────────────────────────┘
```

**Features**:
- **Search**: `<Search>` (filters queries by text)
- **Grouped by date**: Accordion sections (Today, Yesterday, This Week, Older)
- **Query items**: `<Tile>` (clickable)
  - First 50 characters of query (truncated with ...)
  - Metadata: Row count, execution time, timestamp
  - Hover: Show full query in tooltip
  - Click: Load query into new tab
  - Right-click: Delete from history

**State**:
```typescript
{
  history: Array<{
    id: string;
    query: string;
    timestamp: Date;
    executionTime: number;
    rowCount: number;
  }>;
  searchText: string;
}
```

---

### 2.6 Footer (Global)

**Component**: `<Footer>` (custom)

**Contents**:
- **Status Bar** (left): `<div>` with text
  - "Dataset: {name} | Branch: {branch} | Author: {author}"
- **Notification Area** (bottom-right): `<ToastNotification>` (Carbon)
  - Stacked toasts (up to 3 visible)

---

## 3. Interaction Patterns

### 3.1 Execute Query

**Flow**:
1. User types query in editor
2. User clicks "Execute Query" or presses Ctrl+Enter
3. Editor becomes read-only (dim overlay)
4. Execute button changes to "Cancel" (ghost style)
5. Loading spinner appears in results area
6. Query sent to server: `POST /query?dataset={}&branch={}&asOf={}`
7. On success:
   - Results appear in table
   - Toast notification: "Query executed successfully (Xms)"
   - Query added to history
8. On error:
   - Inline notification above results: "Query failed: {error message}"
   - Editor scrolls to error line (if syntax error)
   - Error line highlighted in red

**Error Handling**:
- **400 Bad Request**: Syntax error → highlight line in editor
- **404 Not Found**: Branch not found → show inline notification
- **408 Timeout**: Query took too long → show inline notification with "Increase timeout in settings" link

---

### 3.2 Switch Dataset

**Flow**:
1. User clicks dataset selector (header, right)
2. Dropdown opens with:
   - **Recent** section (last 5 datasets)
   - **All Datasets** section (searchable list)
3. User types to search or clicks dataset
4. Dataset changes globally
5. Current query tab re-executes automatically (if auto-refresh enabled in settings)
6. Toast notification: "Switched to dataset: {name}"

---

### 3.3 Time Travel Query

**Flow**:
1. User clicks "asOf: Latest" dropdown (below editor)
2. Options:
   - **Latest** (default)
   - **Specific Date/Time** → opens date picker modal
   - **At Commit** → opens commit selector modal
3. User selects date or commit
4. asOf indicator updates (e.g., "asOf: 2025-11-10 10:30:00")
5. User executes query
6. Query includes asOf parameter: `POST /query?asOf=2025-11-10T10:30:00Z`
7. Results show data as it existed at that time
8. Inline notification: "🕐 Results as of 2025-11-10 10:30:00 (commit 019abc...)"

---

### 3.4 Save Query

**Flow**:
1. User clicks "Save" button
2. Modal opens: "Save Query"
   - **Name**: `<TextInput>` (required)
   - **Description**: `<TextArea>` (optional)
   - **Tags**: `<TagInput>` (optional, comma-separated)
3. User enters name and clicks "Save"
4. Query saved to browser localStorage (or server if authenticated)
5. Tab label updates to query name
6. Dirty indicator (*) removed
7. Toast notification: "Query saved: {name}"

**Saved Queries Access**:
- New tab dropdown: Recent queries (last 10)
- Query history sidebar: Filter by "Saved only"

---

### 3.5 Multi-Tab Workflow

**Flow**:
1. User clicks "+ New Tab"
2. New tab created with empty editor
3. User can switch between tabs (click tab label)
4. Each tab maintains independent state:
   - Query text
   - Results
   - Execution status
5. Close tab: Click × (confirm if dirty)

---

## 4. State Management

### 4.1 Component State (Local)

**Query Editor Component**:
```typescript
interface QueryEditorState {
  tabs: QueryTab[];
  activeTabId: string;
  selectedBranch: string;
  selectedAsOf: string | null;
}

interface QueryTab {
  id: string;          // UUID
  name: string;        // "New Query" or saved name
  query: string;       // SPARQL query text
  dirty: boolean;      // Unsaved changes?
  executing: boolean;  // Currently running?
  results: QueryResults | null;
  error: string | null;
}
```

**Results Table Component**:
```typescript
interface ResultsState {
  bindings: Array<Record<string, RdfTerm>>;
  variables: string[];
  count: number;
  executionTime: number;
  currentPage: number;
  pageSize: number;
  sortColumn: string | null;
  sortOrder: 'asc' | 'desc';
}
```

---

### 4.2 Global State (Svelte Store)

**App Store** (shared across all views):
```typescript
interface AppState {
  currentDataset: string;
  currentAuthor: string;
  availableDatasets: string[];
  theme: 'white' | 'g10' | 'g90' | 'g100';
}
```

**Query Store** (specific to Query Workbench):
```typescript
interface QueryState {
  history: QueryHistoryItem[];
  savedQueries: SavedQuery[];
  prefixes: PrefixSet;
}
```

---

### 4.3 Persistence

**localStorage**:
- `chucc.currentDataset` → string
- `chucc.currentAuthor` → string
- `chucc.queryTabs` → JSON array of tabs (auto-save every 5s)
- `chucc.queryHistory` → JSON array (last 50 queries)
- `chucc.savedQueries` → JSON array
- `chucc.prefixes` → JSON object

**Session Restore**:
- On page load, restore tabs from localStorage
- If no tabs, create one default tab with example query

---

## 5. Responsive Behavior

### 5.1 Mobile (320px - 671px)

**Layout Changes**:
```
┌─────────────────────────────┐
│ [☰] CHUCC    [Dataset ▾]    │ ← Header (hamburger menu)
└─────────────────────────────┘
┌─────────────────────────────┐
│ [Tab 1 *] [Tab 2] [+]       │
│                             │
│ ┌─ Editor ─────────────────┐│
│ │ SELECT ?s ?p ?o         ││
│ │ WHERE { ... }            ││
│ │                          ││
│ └──────────────────────────┘│
│                             │
│ [Branch ▾] [asOf ▾]         │
│ [▶ Execute]                 │
│                             │
│ ─────────────────────────────│
│                             │
│ ┌─ Results ────────────────┐│
│ │ ?subject                 ││
│ │ ex:Alice                 ││
│ │                          ││
│ │ ?predicate               ││
│ │ rdf:type                 ││
│ │                          ││
│ │ ?object                  ││
│ │ ex:Person                ││
│ └──────────────────────────┘│
│ [◀] 1/1 [▶]                 │
└─────────────────────────────┘
```

**Changes**:
- **Header**: Logo + hamburger menu (hides tabs)
- **Tabs**: Horizontal scroll (swipe)
- **Editor**: Full width, reduced height (200px)
- **Toolbar**: Stacked vertically (branch, asOf, buttons)
- **Results**: Card view instead of table (one binding per card)
- **Pagination**: Simplified (just arrows + page number)

---

### 5.2 Tablet (672px - 1055px)

**Layout Changes**:
```
┌──────────────────────────────────────────────┐
│ [CHUCC] [Query][Graphs][Version]  [Dataset▾]│
└──────────────────────────────────────────────┘
┌──────────────────────────────────────────────┐
│ ┌─ Editor ───────────────────────────────┐   │
│ │ SELECT ?s ?p ?o WHERE { ... }         │   │
│ │                                        │   │
│ └────────────────────────────────────────┘   │
│ [Branch ▾] [asOf ▾]  [▶ Execute]             │
│ ──────────────────────────────────────────── │
│ ┌─ Results ──────────────────────────────┐   │
│ │ ?subject      │ ?predicate │ ?object   │   │
│ │ ex:Alice      │ rdf:type   │ ex:Person │   │
│ └────────────────────────────────────────┘   │
└──────────────────────────────────────────────┘
```

**Changes**:
- **Header**: All tabs visible, dataset selector visible
- **Editor**: Full width, medium height (300px)
- **Results**: Table view (all columns visible, horizontal scroll if needed)
- **Split-pane**: Vertical split (editor top, results bottom)

---

### 5.3 Desktop (1056px+)

**Full layout as shown in main wireframe**

**Optional Split Modes**:
- **Vertical Split** (default): Editor top, results bottom
- **Horizontal Split** (toggle): Editor left, results right (50/50 adjustable)

---

## 6. Accessibility Notes

### 6.1 Keyboard Navigation

**Shortcuts**:
- `Ctrl+Enter`: Execute query
- `Ctrl+S`: Save query
- `Ctrl+/`: Toggle line comment (in editor)
- `Ctrl+T`: New tab
- `Ctrl+W`: Close tab
- `Ctrl+Tab`: Next tab
- `Ctrl+Shift+Tab`: Previous tab
- `Ctrl+F`: Find in editor
- `Tab`: Focus next element
- `Shift+Tab`: Focus previous element

**Tab Order**:
1. Header (dataset selector, author)
2. Query tabs
3. Editor
4. Branch selector
5. asOf selector
6. Execute button
7. Results table
8. Pagination

---

### 6.2 Screen Reader Support

**ARIA Labels**:
- Editor: `aria-label="SPARQL query editor"`
- Results table: `aria-label="Query results, {X} rows"`
- Execute button: `aria-label="Execute SPARQL query, keyboard shortcut Control Enter"`
- Branch selector: `aria-label="Select branch, current: {branch}"`

**ARIA Live Regions**:
- Results area: `aria-live="polite"` (announces when results update)
- Notifications: `aria-live="assertive"` (toasts)

---

### 6.3 Color Contrast

- **Text**: #161616 on #f4f4f4 (21:1 ratio) ✅
- **Links**: #0f62fe (blue) with underline ✅
- **Buttons**: Carbon default (WCAG AA compliant) ✅
- **Syntax Highlighting**: High contrast mode available

---

## 7. Error States

### 7.1 No Dataset Selected

**Display**:
```
┌────────────────────────────────────────┐
│                                        │
│   📊 No dataset selected               │
│                                        │
│   Please select a dataset to execute   │
│   SPARQL queries.                      │
│                                        │
│   [Select Dataset ▾]                   │
│                                        │
└────────────────────────────────────────┘
```

---

### 7.2 Query Syntax Error

**Display**:
```
┌─ Results ──────────────────────────────┐
│ ⚠️ Query failed: Syntax error at line 5│
│                                        │
│ Expected WHERE clause after SELECT.    │
│                                        │
│ [View Documentation]                   │
└────────────────────────────────────────┘
```

**Editor Behavior**:
- Line 5 highlighted in red background
- Error tooltip on hover

---

### 7.3 Network Error

**Display**:
```
┌─ Results ──────────────────────────────┐
│ ❌ Unable to connect to CHUCC server   │
│                                        │
│ Please check your network connection   │
│ and try again.                         │
│                                        │
│ [Retry]                                │
└────────────────────────────────────────┘
```

---

## 8. Empty States

### 8.1 No Results

**Display**:
```
┌─ Results ──────────────────────────────┐
│                                        │
│   ℹ️  No results found                 │
│                                        │
│   Your query returned 0 rows.          │
│   Try modifying your WHERE clause.     │
│                                        │
└────────────────────────────────────────┘
```

---

### 8.2 No Query History

**Display** (in history sidebar):
```
┌─ Query History ────────────────────────┐
│                                        │
│   📜 No query history yet              │
│                                        │
│   Execute a query to see it here.      │
│                                        │
└────────────────────────────────────────┘
```

---

## 9. Performance Optimizations

### 9.1 Virtual Scrolling

**Results Table**:
- Only render visible rows (viewport + buffer)
- Render ~50 rows at a time
- Update on scroll (debounced)
- Benefits: Handle 10,000+ rows smoothly

---

### 9.2 Debounced Auto-Save

**Query Tabs**:
- Auto-save query text to localStorage every 5 seconds
- Debounce typing: Wait 500ms after last keystroke before saving
- Benefits: Reduces localStorage writes, smoother typing

---

### 9.3 Lazy Loading

**Query History**:
- Load last 50 queries initially
- Load older queries on scroll (paginated)
- Benefits: Faster initial render

---

## 10. Component API (Svelte)

### 10.1 QueryWorkbench Component

```svelte
<script lang="ts">
  import { appStore, queryStore } from '$lib/stores';
  import { SparqlQueryEditor, ResultsTable } from 'chucc-squi';

  let tabs: QueryTab[] = $state([{ id: '1', name: 'New Query', query: '', dirty: false }]);
  let activeTabId: string = $state('1');
  let selectedBranch: string = $state('main');
  let selectedAsOf: string | null = $state(null);

  async function executeQuery() {
    const tab = tabs.find(t => t.id === activeTabId);
    if (!tab) return;

    tab.executing = true;
    try {
      const response = await fetch('/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/sparql-query' },
        body: tab.query
      });
      tab.results = await response.json();
      queryStore.addToHistory({ query: tab.query, timestamp: new Date(), ... });
    } catch (error) {
      tab.error = error.message;
    } finally {
      tab.executing = false;
    }
  }
</script>

<QueryWorkbench>
  <SparqlQueryEditor
    bind:tabs
    bind:activeTabId
    on:execute={executeQuery}
  />

  {#if activeTab.results}
    <ResultsTable results={activeTab.results} />
  {/if}
</QueryWorkbench>
```

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
