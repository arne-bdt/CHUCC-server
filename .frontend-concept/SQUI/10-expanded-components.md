# Expanded Component Roadmap

**Goal**: Transform CHUCC-SQUI into comprehensive SPARQL/RDF UI toolkit
**Architecture**: CHUCC Frontend as thin composition layer over protocol-level components

---

## Vision

CHUCC-SQUI provides **protocol-level components** implementing:
- SPARQL 1.2 Protocol
- Graph Store Protocol (GSP)
- SPARQL 1.2 Protocol Version Control Extension
- RDF visualization patterns

CHUCC Frontend becomes a **single-page application** that primarily composes these components with minimal application-specific logic.

---

## Component Catalog

### ✅ Version Control Protocol (In Progress)

#### QueryContextSelector ✅
**Status**: Task files created (01-09)
**Purpose**: Select query context (branch/commit/asOf)
**Reusability**: Any VC extension implementation

#### CommitGraph 🔴
**Priority**: P1 (High)
**Purpose**: Visualize commit DAG (directed acyclic graph)
**Features**:
- D3.js or Cytoscape.js visualization
- Colored branch lines
- Interactive nodes (click to view commit)
- Collapsible merge nodes
- Drag-and-drop operations (merge, cherry-pick)

**Protocol Mapping**: Commit structure (parents, branches, merges)

#### BranchList 🔴
**Priority**: P1 (High)
**Purpose**: Display and manage branches
**Features**:
- Tree view with protected/unprotected indicators
- Drag-and-drop merge/cherry-pick
- Right-click context menu (delete, rename, protect)
- HEAD indicator

**Protocol Mapping**: Branch metadata (name, HEAD, protected status)

#### RDFPatchViewer 🔴
**Priority**: P1 (High)
**Purpose**: Display RDF Patch format with syntax highlighting
**Features**:
- CodeMirror-based editor (read-only)
- Syntax highlighting (A/D/PA/PD/TX/TC)
- Line-by-line diff view
- Copy to clipboard

**Protocol Mapping**: RDF Patch format specification

#### ConflictResolver 🔴
**Priority**: P2 (Medium)
**Purpose**: Resolve three-way merge conflicts
**Features**:
- Three-column diff (BASE | OURS | THEIRS)
- Radio button selection (use ours/theirs/custom)
- Custom resolution editor
- Conflict statistics

**Protocol Mapping**: Merge conflict resolution

#### MergeConfigurator 🟡
**Priority**: P2 (Medium)
**Purpose**: Configure merge operations
**Features**:
- Strategy selector (three-way, ours, theirs, fast-forward)
- Conflict resolution level (dataset/graph/triple)
- Preview merge result
- Validation

**Protocol Mapping**: Merge strategy specification

---

### 🆕 Graph Store Protocol (GSP)

#### GraphList 🔴
**Priority**: P1 (High)
**Purpose**: Browse named graphs in dataset
**Features**:
- Tree view (default graph + named graphs)
- Filter by URI
- Sort by name/size/modified
- Triple count display
- Right-click context menu (delete, export, rename)

**Protocol Mapping**: GSP GET (list graphs)

#### TripleTable 🔴
**Priority**: P1 (High)
**Purpose**: Display RDF triples in table format
**Features**:
- Virtual scrolling (10k+ triples)
- Subject/Predicate/Object columns
- URI shortening (namespace prefixes)
- Syntax highlighting (URIs, literals, blank nodes)
- Multi-select for bulk operations
- Inline editing (add/modify/delete)

**Protocol Mapping**: GSP GET (graph content)

#### TripleEditor 🟡
**Priority**: P2 (Medium)
**Purpose**: Add/edit/delete individual triples
**Features**:
- Subject/Predicate/Object input fields
- URI validation
- Literal datatype/language selector
- Blank node support
- Auto-complete for common predicates

**Protocol Mapping**: GSP POST/DELETE/PATCH

#### GraphUploader 🟡
**Priority**: P2 (Medium)
**Purpose**: Upload RDF files to graphs
**Features**:
- Drag-and-drop file upload
- Format auto-detection (Turtle, JSON-LD, RDF/XML, N-Triples)
- Append vs. Replace mode
- Preview before upload
- Progress indicator

**Protocol Mapping**: GSP POST/PUT

---

### 🆕 SPARQL Protocol

#### SPARQLEditor 🔴
**Priority**: P1 (High)
**Purpose**: SPARQL query editor with syntax highlighting
**Features**:
- CodeMirror 6 integration
- SPARQL syntax highlighting
- Auto-complete (keywords, prefixes)
- Query validation
- Query history (localStorage)
- Keyboard shortcuts (Ctrl+Enter to execute)

**Protocol Mapping**: SPARQL Protocol UPDATE

#### ResultsTable 🔴
**Priority**: P1 (High)
**Purpose**: Display SPARQL SELECT results
**Features**:
- Virtual scrolling (10k+ rows)
- Column sorting
- Export (CSV, JSON, TSV)
- Pagination controls
- Copy cell value on click

**Protocol Mapping**: SPARQL Protocol SELECT results (XML/JSON formats)

#### ResultsGraph 🟡
**Priority**: P3 (Low)
**Purpose**: Visualize SPARQL CONSTRUCT/DESCRIBE results
**Features**:
- Force-directed graph layout
- Node/edge labels
- Interactive zoom/pan
- Click node to show details

**Protocol Mapping**: SPARQL Protocol CONSTRUCT/DESCRIBE results

#### PrefixManager 🟡
**Priority**: P2 (Medium)
**Purpose**: Manage namespace prefixes
**Features**:
- Prefix/URI pair editor
- Common prefix templates (foaf, dc, schema.org)
- Import/export prefix list
- Auto-insertion in queries

**Protocol Mapping**: SPARQL PREFIX declarations

---

### 🆕 RDF Visualization

#### RDFTreeView 🟡
**Priority**: P2 (Medium)
**Purpose**: Subject-centric hierarchical view of triples
**Features**:
- Collapsible tree structure
- Subject nodes expand to show predicates
- Object nodes link to other subjects
- Blank node indentation

**Reusability**: Generic RDF visualization

#### RDFDiffViewer 🟡
**Priority**: P2 (Medium)
**Purpose**: Compare two RDF graphs
**Features**:
- Side-by-side diff
- Added (green), deleted (red), modified (yellow) highlighting
- Filter by change type
- Export diff as RDF Patch

**Reusability**: Generic RDF diff visualization

#### NamespaceColorizer 🟢
**Priority**: P3 (Low)
**Purpose**: Color-code URIs by namespace
**Features**:
- Automatic color assignment per namespace
- Legend display
- Customizable color palette

**Reusability**: Generic RDF visualization enhancement

#### RDFPathNavigator 🟢
**Priority**: P3 (Low)
**Purpose**: Navigate RDF graph via predicate paths
**Features**:
- Path input (e.g., foaf:knows/foaf:name)
- Breadcrumb trail
- Forward/back navigation
- Bookmark paths

**Reusability**: Generic RDF navigation

---

## Component Organization

### Package Structure

```
chucc-squi/
├── sparql/                   # SPARQL Protocol components
│   ├── SPARQLEditor.svelte
│   ├── ResultsTable.svelte
│   ├── ResultsGraph.svelte
│   └── PrefixManager.svelte
│
├── graph-store/              # Graph Store Protocol components
│   ├── GraphList.svelte
│   ├── TripleTable.svelte
│   ├── TripleEditor.svelte
│   └── GraphUploader.svelte
│
├── version-control/          # Version Control Protocol components
│   ├── QueryContextSelector.svelte ✅
│   ├── QueryContextIndicator.svelte ✅
│   ├── QueryContextBreadcrumb.svelte ✅
│   ├── CommitGraph.svelte
│   ├── BranchList.svelte
│   ├── RDFPatchViewer.svelte
│   ├── ConflictResolver.svelte
│   └── MergeConfigurator.svelte
│
└── rdf/                      # RDF Visualization components
    ├── RDFTreeView.svelte
    ├── RDFDiffViewer.svelte
    ├── NamespaceColorizer.svelte
    └── RDFPathNavigator.svelte
```

### Export Structure

```typescript
// package.json exports
{
  "exports": {
    "./sparql": "./dist/sparql/index.js",
    "./graph-store": "./dist/graph-store/index.js",
    "./version-control": "./dist/version-control/index.js",
    "./rdf": "./dist/rdf/index.js"
  }
}
```

---

## Implementation Priority

### Phase 1 (Weeks 1-4): QueryContextSelector ✅
**Status**: In progress (tasks 01-09 created)
**Deliverable**: Working context selector

### Phase 2 (Weeks 5-8): Core SPARQL Components
**Components**:
- SPARQLEditor (P1)
- ResultsTable (P1)
- PrefixManager (P2)

**Rationale**: Enables basic query functionality in frontend

### Phase 3 (Weeks 9-12): Graph Store Components
**Components**:
- GraphList (P1)
- TripleTable (P1)
- TripleEditor (P2)
- GraphUploader (P2)

**Rationale**: Enables graph management in frontend

### Phase 4 (Weeks 13-16): Version Control Visualization
**Components**:
- CommitGraph (P1)
- BranchList (P1)
- RDFPatchViewer (P1)

**Rationale**: Completes version control UI

### Phase 5 (Weeks 17-20): Advanced Features
**Components**:
- ConflictResolver (P2)
- MergeConfigurator (P2)
- RDFTreeView (P2)
- RDFDiffViewer (P2)

**Rationale**: Enhanced functionality

### Phase 6 (Weeks 21-24): Polish & Extras
**Components**:
- ResultsGraph (P3)
- NamespaceColorizer (P3)
- RDFPathNavigator (P3)

**Rationale**: Nice-to-have features

---

## Benefits of Protocol-Level Components

### For CHUCC-SQUI
- ✅ Becomes comprehensive toolkit for SPARQL/RDF UIs
- ✅ Reference implementation of protocol specifications
- ✅ Reusable across any SPARQL/VC extension project
- ✅ Each component tested and documented independently

### For CHUCC Frontend
- ✅ Thin composition layer (routing + state management)
- ✅ 70%+ reduction in frontend code (protocol UIs outsourced)
- ✅ Faster development (import components, wire callbacks)
- ✅ Automatic updates (component improvements propagate)

### For Ecosystem
- ✅ Other SPARQL systems can adopt same components
- ✅ Community contributions benefit all users
- ✅ Standard-compliant UIs guaranteed

---

## Next Steps

1. Complete QueryContextSelector implementation (Tasks 01-09)
2. Create task files for Phase 2 components (SPARQLEditor, ResultsTable, PrefixManager)
3. Update component priority based on frontend development needs
4. Begin parallel development of high-priority components

---

**Document Version**: 1.0
**Created**: 2025-11-10
**Status**: Planning
