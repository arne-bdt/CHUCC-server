# Graph Explorer - UI Mockup

**View**: Graph Explorer (Graph Store Protocol Operations)
**Route**: `/graphs`
**Inspiration**: Fuseki's "edit" view + modern RDF browsers

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs✓][Version][Time Travel][Datasets][Batch]            │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ Query Context: [🌿 Branch: main (latest) ▾]                                 │
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────┬─────────────────────────────────────────────────────────────────┐
│ GRAPHS     │ ┌─ Graph: <http://example.org/users> ───────────────[Actions▾]┐│
│ ────────── │ │ URI: http://example.org/users                   [Copy URI] ││
│ [Filter]   │ │ Triples: 1,247                                              ││
│ [🔍]       │ │ Last Modified: 2025-11-10 10:30:00 (Commit 019abc...)     ││
│            │ │                                                             ││
│ Default    │ │ ┌─ View Options ──────────────────────────────────────────┐││
│ Graph      │ │ │ Format: [Table ▾]  Sort: [Subject ▾]  [⬇ Export...]  │││
│ (178)      │ │ └─────────────────────────────────────────────────────────┘││
│            │ │                                                             ││
│ Named      │ │ ┌─ Triples ───────────────────────────────────────────────┐││
│ Graphs     │ │ │ Subject           Predicate        Object               │││
│            │ │ │ ────────────────────────────────────────────────────────│││
│ ex:users   │ │ │ ex:Alice          rdf:type         ex:Person           │││
│ (1,247) ★  │ │ │ ex:Alice          ex:age           31                  │││
│            │ │ │ ex:Alice          ex:email         alice@example.org   │││
│ ex:meta    │ │ │ ex:Bob            rdf:type         ex:Person           │││
│ (45)       │ │ │ ex:Bob            ex:age           28                  │││
│            │ │ │ ...                                                     │││
│ ex:schema  │ │ └─────────────────────────────────────────────────────────┘││
│ (89)       │ │ [◀ Prev]  Page 1 of 42  [Next ▶]                          ││
│            │ └─────────────────────────────────────────────────────────────┘│
│ [+ Create] │ ────────────────────────────────────────────────────────────── │
│            │ ┌─ Actions ──────────────────────────────────────────────────┐ │
│            │ │ [+ Add Triple] [Upload File...] [Replace Graph]           │ │
│            │ │ [Delete Graph] [Download as...]                            │ │
│            │ └────────────────────────────────────────────────────────────┘ │
└────────────┴─────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Graph List Sidebar

**Structure**:
```
┌─ GRAPHS ────────────┐
│ [Filter 🔍]         │
│ ─────────────────── │
│ Default Graph       │
│ (178 triples) ○     │
│                     │
│ Named Graphs        │
│ ─────────────────── │
│ ex:users (1,247) ★  │ ← Selected
│ ex:metadata (45)    │
│ ex:schema (89)      │
│ ex:inferred (3,421) │
│                     │
│ [+ Create Graph]    │
└─────────────────────┘
```

**Components**:
- **Filter Input**: `<Search size="sm">` (Carbon) - Filter by graph URI
- **Graph List**: `<TreeView>` (Carbon) with two sections:
  - Default Graph (always present, may be empty)
  - Named Graphs (collapsible)
- **Graph Items**: `<TreeNode>` with:
  - Graph URI (shortened if long: `ex:users` instead of `http://example.org/users`)
  - Triple count in parentheses
  - Star icon (★) for selected graph
  - Circle icon (○) for default graph
- **Create Button**: `<Button kind="ghost">` - Opens "Create Graph" modal

**Features**:
- **Click graph**: Load graph content in main panel
- **Right-click graph**: Context menu
  - Rename (named graphs only)
  - Delete
  - Copy URI
  - Export as...
  - Duplicate
- **Hover**: Show full URI in tooltip
- **Empty state**: Show message "No named graphs. Click '+ Create Graph' to add one."

**State**:
```typescript
interface GraphListState {
  graphs: Array<{
    uri: string;           // Full URI
    shortName: string;     // Abbreviated name for display
    tripleCount: number;
    lastModified: Date;
    isDefault: boolean;
  }>;
  selectedGraphUri: string | null;
  filterText: string;
}
```

---

### 2.2 Graph Header

**Layout**:
```
┌─ Graph: <http://example.org/users> ───────────[Actions ▾]┐
│ URI: http://example.org/users                 [Copy URI] │
│ Triples: 1,247                                            │
│ Last Modified: 2025-11-10 10:30:00 (Commit 019abc...)   │
└───────────────────────────────────────────────────────────┘
```

**Components**:
- **Graph Title**: `<h2>` with full URI or "Default Graph"
- **Metadata**: `<StructuredList>` (Carbon) with:
  - URI (clickable link, opens in new tab)
  - Triple count
  - Last modified timestamp + commit link
- **Copy URI Button**: `<Button kind="ghost" size="sm">` - Copy to clipboard
- **Actions Dropdown**: `<OverflowMenu>` with:
  - Add Triple
  - Upload File (Turtle, RDF/XML, JSON-LD, N-Triples)
  - Replace Graph (uploads replace entire graph)
  - Delete Graph
  - Download as... (format submenu)

---

### 2.3 View Options Toolbar

**Layout**:
```
┌─ View Options ──────────────────────────────────────────┐
│ Format: [Table ▾]  Sort: [Subject ▾]  [⬇ Export...]   │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Format Selector**: `<Dropdown>` (Carbon)
  - Table (default) - Shows triples in table view
  - Tree - Hierarchical subject-centric view
  - Raw - Serialized RDF (Turtle, JSON-LD, etc.)
- **Sort Selector**: `<Dropdown>` (Carbon)
  - Subject (alphabetical)
  - Predicate (grouped by property)
  - Object (alphabetical)
  - Recently Modified (requires triple-level provenance)
- **Export Button**: `<Button kind="secondary">` - Downloads current graph

---

### 2.4 Triple Table View

**Layout**:
```
┌─ Triples ───────────────────────────────────────────────┐
│ Subject           Predicate        Object               │
│ ────────────────────────────────────────────────────────│
│ ex:Alice          rdf:type         ex:Person         [×]│ ← Hover shows delete
│ ex:Alice          ex:age           31                [×]│
│ ex:Alice          ex:email         alice@example.org [×]│
│ ex:Bob            rdf:type         ex:Person         [×]│
│ ex:Bob            ex:age           28                [×]│
│ ...                                                     │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **DataTable**: `<DataTable>` (Carbon) with virtual scrolling
- **Columns**:
  - Subject (URI or blank node, shortened for display)
  - Predicate (URI, shortened for display)
  - Object (URI, literal, or blank node)
  - Actions (hover to show delete icon)
- **Row Interactions**:
  - Click cell → Copy value to clipboard (toast notification)
  - Hover subject/predicate/object → Show full URI in tooltip
  - Click URI → Navigate to that resource (if exists in graph)
  - Click delete icon → Confirm modal, then DELETE via GSP PATCH

**Features**:
- **Virtual Scrolling**: Render only visible rows (~100 at a time)
- **Syntax Highlighting**: Different colors for URIs, literals, blank nodes
- **Multi-select**: Shift+Click to select range, Ctrl+Click for individual
- **Bulk Delete**: Select multiple triples, click "Delete Selected" button

**State**:
```typescript
interface TripleTableState {
  triples: Array<{
    subject: string;
    predicate: string;
    object: string;
    objectType: 'uri' | 'literal' | 'blankNode';
  }>;
  selectedTriples: Set<number>;  // Indices
  page: number;
  pageSize: number;
  totalTriples: number;
}
```

---

### 2.5 Tree View (Alternative Format)

**Layout**:
```
┌─ Triples (Tree View) ───────────────────────────────────┐
│ ▼ ex:Alice                                               │
│   ├─ rdf:type → ex:Person                               │
│   ├─ ex:age → 31                                         │
│   ├─ ex:email → "alice@example.org"                     │
│   └─ ex:knows → ex:Bob                                   │
│                                                          │
│ ▼ ex:Bob                                                 │
│   ├─ rdf:type → ex:Person                               │
│   └─ ex:age → 28                                         │
│                                                          │
│ ▼ ex:Company1                                            │
│   ├─ rdf:type → ex:Organization                         │
│   └─ ex:employee → ex:Alice                              │
│                  └─ ex:Bob                               │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **TreeView**: `<TreeView>` (Carbon) with collapsible nodes
- **Subject Nodes**: Expandable, show all predicates as children
- **Object Links**: Clickable if object is a subject elsewhere
- **Blank Nodes**: Indented sub-trees

**Features**:
- **Collapse/Expand**: Click subject to toggle
- **Follow Links**: Click object URI to navigate to that subject
- **Context Menu**: Right-click node for actions (add property, delete, etc.)

---

### 2.6 Raw View (Alternative Format)

**Layout**:
```
┌─ Triples (Raw: Turtle) ─────────────────────[Copy]──────┐
│ @prefix ex: <http://example.org/> .                     │
│ @prefix rdf: <http://www.w3.org/1999/02/22-rdf-...> .  │
│                                                          │
│ ex:Alice                                                 │
│     rdf:type ex:Person ;                                │
│     ex:age 31 ;                                          │
│     ex:email "alice@example.org" .                      │
│                                                          │
│ ex:Bob                                                   │
│     rdf:type ex:Person ;                                │
│     ex:age 28 .                                          │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **CodeMirror Editor**: Read-only mode with syntax highlighting
- **Format Selector**: `<Dropdown>` above editor
  - Turtle (default)
  - JSON-LD
  - RDF/XML
  - N-Triples
  - N-Quads (shows graph URI)
- **Copy Button**: Copy entire serialization to clipboard

---

### 2.7 Add Triple Modal

**Layout**:
```
┌─ Add Triple ─────────────────────────────────────────────┐
│                                                           │
│ Subject:                                                  │
│ ● URI: [http://example.org/Alice_______________]         │
│ ○ Blank Node: [_:b1_____________________________]         │
│                                                           │
│ Predicate (URI):                                          │
│ [http://example.org/age_________________________]         │
│ Common: [rdf:type▾] [rdfs:label▾] [foaf:name▾]          │
│                                                           │
│ Object:                                                   │
│ ● URI: [http://example.org/Person_______________]         │
│ ○ Literal: [Value____________] Lang:[___] Type:[xsd:...▾]│
│ ○ Blank Node: [_:b2_____________________________]         │
│                                                           │
│                              [Cancel]  [Add Triple]      │
└───────────────────────────────────────────────────────────┘
```

**Components**:
- **Subject Input**: Radio buttons + text input (URI or blank node)
- **Predicate Input**: Text input with autocomplete (common predicates)
  - Quick buttons for rdf:type, rdfs:label, foaf:name, etc.
- **Object Input**: Radio buttons + text input (URI, literal, or blank node)
  - If literal: Optional language tag + datatype selector
- **Validation**: Ensure valid URIs, non-empty values

**On Add**:
- POST `/graphs/{graphUri}` with Content-Type: application/n-triples
- Body: `<subject> <predicate> <object> .`
- Toast notification: "Triple added successfully"
- Table refreshes

---

### 2.8 Upload File Modal

**Layout**:
```
┌─ Upload Triples ─────────────────────────────────────────┐
│                                                           │
│ ┌─────────────────────────────────────────────────────┐ │
│ │  Drag and drop file here, or click to browse        │ │
│ │                                                      │ │
│ │              [📁 Browse Files]                       │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                           │
│ Format: [Auto-detect ▾]                                   │
│   (Turtle, JSON-LD, RDF/XML, N-Triples, N-Quads)        │
│                                                           │
│ Mode:                                                     │
│ ● Append (add triples to existing graph)                 │
│ ○ Replace (delete existing triples first)                │
│                                                           │
│ [Preview File]                   [Cancel]  [Upload]      │
└───────────────────────────────────────────────────────────┘
```

**Components**:
- **File Drop Zone**: `<FileUploader>` (Carbon)
- **Format Selector**: `<Dropdown>` - Auto-detect based on file extension
- **Mode Radio Buttons**: Append vs. Replace
- **Preview Button**: Opens modal showing first 50 triples

**On Upload**:
- **Append Mode**: POST `/graphs/{graphUri}` with Content-Type based on format
- **Replace Mode**: PUT `/graphs/{graphUri}` (replaces entire graph)
- Progress bar during upload
- Toast notification: "Uploaded X triples"

---

### 2.9 Delete Graph Confirmation

**Layout**:
```
┌─ Delete Graph? ──────────────────────────────────────────┐
│                                                           │
│ ⚠️  Are you sure you want to delete this graph?          │
│                                                           │
│ Graph: http://example.org/users                          │
│ Triples: 1,247                                            │
│                                                           │
│ This action will create a new commit and cannot be       │
│ undone without reverting to a previous commit.           │
│                                                           │
│ To confirm, type the graph name:                         │
│ [_____________________________________________]            │
│                                                           │
│                              [Cancel]  [Delete Graph]    │
└───────────────────────────────────────────────────────────┘
```

**Components**:
- **Warning Icon**: `<ErrorFilled>` (Carbon icon)
- **Graph Metadata**: Display URI and triple count
- **Confirmation Input**: User must type graph URI to confirm
- **Delete Button**: Disabled until confirmation text matches

**On Delete**:
- DELETE `/graphs/{graphUri}?dataset={dataset}`
- Request includes `SPARQL-VC-Author` header
- Creates commit with message "Delete graph {uri}"
- Navigate back to default graph or first named graph

---

## 3. Interaction Flows

### 3.1 Browse Graph

1. User clicks graph in sidebar (e.g., "ex:users")
2. Header loads with graph metadata
3. Triple table loads (paginated, first 100 triples)
4. User scrolls down → Virtual scrolling loads more rows
5. User changes format to "Tree" → View switches to tree format
6. User clicks subject URI → Filters table to show only that subject's triples

### 3.2 Add Single Triple

1. User clicks "+ Add Triple" button (or in Actions dropdown)
2. Modal opens with empty form
3. User enters subject URI: `http://example.org/Carol`
4. User enters predicate: `http://example.org/age` (or selects from common predicates)
5. User selects "Literal" for object, enters value: `25`, datatype: `xsd:integer`
6. User clicks "Add Triple"
7. POST request sent with N-Triples: `<http://example.org/Carol> <http://example.org/age> "25"^^<http://www.w3.org/2001/XMLSchema#integer> .`
8. Modal closes, toast notification: "Triple added successfully"
9. Table refreshes, new triple appears

### 3.3 Upload RDF File

1. User clicks "Upload File..." in Actions dropdown
2. Modal opens with file drop zone
3. User drags Turtle file into drop zone
4. Format auto-detected as "Turtle"
5. User selects "Append" mode
6. User clicks "Preview File" → Modal shows first 50 triples
7. User clicks "Upload"
8. POST request sent with Content-Type: text/turtle
9. Progress bar shows upload progress
10. Toast notification: "Uploaded 347 triples"
11. Table refreshes with new data

### 3.4 Delete Multiple Triples

1. User switches to Table view
2. User clicks first triple (selects row)
3. User holds Shift, clicks fifth triple (selects range)
4. Toolbar shows "5 triples selected"
5. User clicks "Delete Selected" button
6. Confirmation modal: "Delete 5 triples?"
7. User confirms
8. PATCH request sent with RDF Patch:
   ```
   TX .
   D <s1> <p1> <o1> .
   D <s2> <p2> <o2> .
   ...
   TC .
   ```
9. Toast notification: "Deleted 5 triples"
10. Table refreshes

### 3.5 Create New Graph

1. User clicks "+ Create Graph" button in sidebar
2. Modal opens:
   ```
   ┌─ Create Named Graph ──────────────────────────┐
   │ Graph URI:                                     │
   │ [http://example.org/new-graph______________]   │
   │                                                │
   │ Initialize with:                               │
   │ ● Empty graph                                  │
   │ ○ Upload file                                  │
   │ ○ Copy from existing graph [ex:users ▾]       │
   │                                                │
   │                      [Cancel]  [Create Graph] │
   └────────────────────────────────────────────────┘
   ```
3. User enters URI, selects "Empty graph"
4. User clicks "Create Graph"
5. PUT request to `/graphs/{newGraphUri}` with empty body
6. New graph appears in sidebar
7. Graph automatically selected (empty triple table shown)

### 3.6 Query Context Interaction

**Important**: Graph Explorer respects the unified query context selector.

1. User selects "Query Context: Branch: main (latest)"
2. Graph list shows graphs as they exist in latest commit on main branch
3. User switches context to "Specific Commit: 019abc..."
4. Graph list updates to show graphs as they existed in that commit
5. User views graph content → Shows historical triples (read-only if not on branch HEAD)
6. User switches back to "Branch: main (latest)" → Edit operations enabled again

**Read-Only vs. Edit Mode**:
- **Branch (latest)**: Full edit capabilities (add/delete triples, upload files, delete graphs)
- **Specific Commit**: Read-only mode (view only, no edit operations)
- **asOf (Time Travel)**: Read-only mode (view only)

**Visual Indicator for Read-Only Mode**:
```
┌─────────────────────────────────────────────────────────┐
│ ℹ️  Read-Only Mode: Viewing commit 019abc...            │
│    Switch to a branch context to make changes.          │
└─────────────────────────────────────────────────────────┘
```

---

## 4. State Management

### 4.1 Local State (Component-Level)

```typescript
// GraphExplorer.svelte
interface GraphExplorerState {
  // Sidebar
  graphs: Graph[];
  selectedGraphUri: string | null;
  filterText: string;

  // Main panel
  viewFormat: 'table' | 'tree' | 'raw';
  sortBy: 'subject' | 'predicate' | 'object';

  // Table view
  triples: Triple[];
  selectedTriples: Set<number>;
  page: number;
  pageSize: number;
  totalTriples: number;

  // Raw view
  rawFormat: 'turtle' | 'jsonld' | 'rdfxml' | 'ntriples';
  rawContent: string;

  // Loading states
  isLoadingGraphs: boolean;
  isLoadingTriples: boolean;
}
```

### 4.2 Global State (Svelte Stores)

```typescript
// stores/app.ts
export const currentDataset = writable<string>('default');
export const currentAuthor = writable<string>('');
export const queryContext = writable<QueryContext>({
  type: 'branch',
  branch: 'main'
});

// stores/graphs.ts
export const graphCache = writable<Map<string, Graph>>(new Map());
export const tripleCache = writable<Map<string, Triple[]>>(new Map());
```

### 4.3 API Service

```typescript
// services/GraphService.ts
class GraphService {
  async listGraphs(dataset: string, context: QueryContext): Promise<Graph[]> {
    const url = this.buildUrl(`/graphs`, dataset, context);
    const response = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' }
    });
    return response.json();
  }

  async getGraphTriples(
    graphUri: string,
    dataset: string,
    context: QueryContext,
    page: number,
    pageSize: number
  ): Promise<{ triples: Triple[], total: number }> {
    const url = this.buildUrl(`/graphs/${encodeURIComponent(graphUri)}`, dataset, context);
    const response = await fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      // Pagination via Accept header or query params (TBD based on API design)
    });
    return response.json();
  }

  async addTriple(graphUri: string, dataset: string, triple: Triple): Promise<void> {
    const url = `/graphs/${encodeURIComponent(graphUri)}?dataset=${dataset}`;
    const body = `<${triple.subject}> <${triple.predicate}> <${triple.object}> .`;
    await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/n-triples',
        'SPARQL-VC-Author': this.getCurrentAuthor()
      },
      body
    });
  }

  async deleteGraph(graphUri: string, dataset: string): Promise<void> {
    const url = `/graphs/${encodeURIComponent(graphUri)}?dataset=${dataset}`;
    await fetch(url, {
      method: 'DELETE',
      headers: {
        'SPARQL-VC-Author': this.getCurrentAuthor()
      }
    });
  }

  private buildUrl(path: string, dataset: string, context: QueryContext): string {
    let url = `${path}?dataset=${dataset}`;
    switch (context.type) {
      case 'branch':
        url += `&branch=${context.branch}`;
        break;
      case 'commit':
        url += `&commit=${context.commit}`;
        break;
      case 'asOf':
        url += `&asOf=${context.asOf}`;
        break;
    }
    return url;
  }
}
```

---

## 5. Responsive Behavior

### Mobile (320px - 671px)

- **Sidebar**: Collapsed by default (hamburger menu icon)
- **Graph List**: Slide-in drawer from left
- **Triple Table**: Stacked layout (one column per row)
  ```
  ┌───────────────────────┐
  │ Subject: ex:Alice     │
  │ Predicate: rdf:type   │
  │ Object: ex:Person     │
  │ [×] Delete            │
  ├───────────────────────┤
  │ Subject: ex:Alice     │
  │ Predicate: ex:age     │
  │ Object: 31            │
  │ [×] Delete            │
  └───────────────────────┘
  ```
- **Add Triple**: Full-screen modal
- **Actions**: Stacked buttons (vertical)

### Tablet (672px - 1055px)

- **Sidebar**: Collapsible (toggle button), 200px width
- **Triple Table**: Standard table with horizontal scroll if needed
- **Modals**: 80% screen width

### Desktop (1056px+)

- **Sidebar**: Fixed 240px width
- **Triple Table**: Full virtual scrolling
- **Modals**: Max 600px width, centered

---

## 6. Accessibility

### Keyboard Navigation

- **Tab Order**: Sidebar → Header → View Options → Triple Table → Actions
- **Arrow Keys**: Navigate table rows (↑/↓), columns (←/→)
- **Enter**: Select graph, open modal, submit form
- **Escape**: Close modal, clear selection
- **Ctrl+F**: Focus filter input
- **Ctrl+A**: Select all triples (in table view)
- **Ctrl+N**: Open "Add Triple" modal
- **Delete**: Delete selected triples (with confirmation)

### ARIA Labels

- **Graph List**: `aria-label="Available graphs in dataset"`
- **Graph Item**: `aria-label="Graph {uri}, {count} triples"`
- **Triple Table**: `aria-label="Triples in graph {uri}"`
- **Table Row**: `aria-label="Triple: {subject} {predicate} {object}"`
- **Action Buttons**: `aria-label="Add triple to graph"` etc.

### Screen Reader Announcements

- "Graph {uri} selected. {count} triples loaded."
- "{count} triples selected. Press Delete to remove."
- "Triple added successfully."
- "Switched to read-only mode. Viewing commit {id}."

### Color Contrast

- **Table Headers**: Carbon Gray 80 on White (#262626 on #FFFFFF) - 12.6:1 ratio
- **URIs**: Carbon Blue 60 (#0f62fe) - 4.5:1 ratio (WCAG AA)
- **Literals**: Carbon Green 50 (#24a148) - 4.5:1 ratio
- **Selected Row**: Carbon Blue 10 (#edf5ff) background

---

## 7. Performance

### Optimization Strategies

1. **Virtual Scrolling**: Render only visible triples (~100 rows)
   - Windowing library: `svelte-virtual` or custom implementation
   - 60 FPS target for 10,000+ triples

2. **Pagination**: Server-side pagination for large graphs
   - Default page size: 100 triples
   - Load next page on scroll to bottom
   - Cache loaded pages in memory

3. **Lazy Loading**: Load graph metadata first, triples on demand
   - Initial load: List of graphs with URIs and triple counts only
   - Triple data loaded only when graph selected

4. **Debounced Filter**: Wait 300ms after typing before filtering
   - Reduces API calls while user is typing

5. **Memoization**: Cache serialized graph content
   - Store Turtle/JSON-LD serialization in `rawContentCache`
   - Invalidate cache only when graph modified

### Performance Targets

- **Graph List Load**: < 200ms for 100 graphs
- **Triple Table Load**: < 500ms for first 100 triples
- **Scroll Performance**: 60 FPS with virtual scrolling
- **Add Triple**: < 300ms round-trip (POST + refresh)
- **File Upload**: < 2s for 1,000 triples, < 10s for 10,000 triples

---

## 8. Error Handling

### Error Scenarios

1. **Graph Not Found**: 404 from API
   - Show inline notification: "Graph {uri} not found in this context."
   - Offer to switch to latest branch context

2. **Read-Only Context**: User tries to edit in commit/asOf context
   - Disable edit buttons, show banner: "Read-only mode. Switch to branch context to edit."

3. **Invalid Triple**: Validation error on add
   - Show error message below input: "Subject must be a valid URI or blank node."

4. **Upload Failed**: Network error or invalid RDF format
   - Show error modal with details: "Upload failed: Syntax error at line 42."

5. **Delete Conflict**: Graph has dependencies
   - Show warning: "This graph is referenced by other graphs. Delete anyway?"

### Error Display

```typescript
// Inline notification component
<InlineNotification
  kind="error"
  title="Graph load failed"
  subtitle="Could not load triples for {graphUri}. Try refreshing."
  lowContrast
/>

// Toast notification
showToast('error', 'Triple addition failed: Invalid URI format');
```

---

## 9. Testing Considerations

### Unit Tests

- Graph list filtering logic
- Triple table sorting and pagination
- URI validation for add triple form
- Read-only mode logic based on query context

### Integration Tests

- Load graph list from API
- Select graph and load triples
- Add triple via POST
- Delete graph via DELETE
- Upload file and verify triples added

### E2E Tests (Playwright)

1. **Browse Graph Flow**:
   - Navigate to /graphs
   - Click named graph in sidebar
   - Verify triples displayed in table
   - Switch to tree view
   - Verify tree structure rendered

2. **Add Triple Flow**:
   - Click "+ Add Triple"
   - Fill in subject, predicate, object
   - Submit form
   - Verify triple appears in table

3. **Context Switching Flow**:
   - Select commit context
   - Verify read-only mode enabled
   - Switch back to branch context
   - Verify edit buttons re-enabled

---

## 10. Open Questions

1. **Pagination Strategy**: Server-side or client-side?
   - Recommendation: Server-side for graphs with > 1,000 triples

2. **Triple-Level Provenance**: Does API provide "last modified" per triple?
   - If yes: Enable "Sort by Recently Modified"
   - If no: Omit this sort option

3. **Blank Node Handling**: How to display and edit blank nodes?
   - Recommendation: Show as `_:b1` with unique ID, allow editing via tree view

4. **Large File Uploads**: Should we support chunked uploads?
   - Recommendation: Use standard multipart/form-data for files < 10MB, chunked for larger

5. **Graph Dependencies**: Does API provide "referenced by" information?
   - If yes: Show warning before deleting graphs with dependencies

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
