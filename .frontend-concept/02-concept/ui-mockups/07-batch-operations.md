# Batch Operations - UI Mockup

**View**: Batch Operations Builder
**Route**: `/batch`
**Inspiration**: SQL query builders + Git staging area + Postman request collections

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs][Version][Time Travel][Datasets][Batch✓]           │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ ⚙️  Batch Operations Builder                                                 │
└──────────────────────────────────────────────────────────────────────────────┘
┌────────────────┬─────────────────────────────────────────────────────────────┐
│ OPERATIONS     │ ┌─ Build Batch ──────────────────────────────────────────┐ │
│ ────────────── │ │ [+ Add Operation ▾]  [Import from File]  [Templates ▾]│ │
│ [+ Add Op]     │ └────────────────────────────────────────────────────────┘ │
│                │ ────────────────────────────────────────────────────────── │
│ 1. ✏️  Add     │ ┌─ Operation 1: Add Triple ──────────────────────────────┐ │
│    Graph:      │ │ Type: Add Triple                            [↑][↓][×] │ │
│    ex:users    │ │                                                        │ │
│                │ │ Graph: [ex:users ▾]                                    │ │
│ 2. ✏️  Modify  │ │                                                        │ │
│    Graph:      │ │ Subject:   [http://example.org/Alice___________]      │ │
│    ex:users    │ │ Predicate: [http://example.org/department______]      │ │
│                │ │ Object:    ● URI [http://example.org/Sales_____]      │ │
│ 3. 🗑️  Delete  │ │            ○ Literal [___] Lang:[__] Type:[___]      │ │
│    Graph:      │ │                                                        │ │
│    ex:old      │ │ [Validate]                                             │ │
│                │ └────────────────────────────────────────────────────────┘ │
│ [Clear All]    │ ────────────────────────────────────────────────────────── │
│                │ ┌─ Operation 2: Modify Triple ───────────────────────────┐ │
│ [Templates]    │ │ Type: Modify Triple (Patch)                 [↑][↓][×] │ │
│ • Add Users    │ │                                                        │ │
│ • Delete Graph │ │ Graph: [ex:users ▾]                                    │ │
│ • Update Props │ │                                                        │ │
│                │ │ Find Triple:                                           │ │
│ [History]      │ │ Subject:   [ex:Alice_________________________]         │ │
│ • 2h ago       │ │ Predicate: [ex:age___________________________]         │ │
│ • Yesterday    │ │ Old Object: "30"                                       │ │
│                │ │                                                        │ │
│                │ │ New Object: ● Literal ["31"] Type:[xsd:integer ▾]     │ │
│                │ │             ○ URI [___________________________]         │ │
│                │ └────────────────────────────────────────────────────────┘ │
│                │ ────────────────────────────────────────────────────────── │
│                │ ┌─ Operation 3: Delete Graph ────────────────────────────┐ │
│                │ │ Type: Delete Graph                          [↑][↓][×] │ │
│                │ │                                                        │ │
│                │ │ Graph: [ex:old-data ▾]                                │ │
│                │ │                                                        │ │
│                │ │ ⚠️  This will delete all triples in this graph.        │ │
│                │ └────────────────────────────────────────────────────────┘ │
│                │ ────────────────────────────────────────────────────────── │
│                │ ┌─ Preview & Execute ────────────────────────────────────┐ │
│                │ │ [Preview RDF Patch] [Validate All] [Execute Batch...] │ │
│                │ └────────────────────────────────────────────────────────┘ │
└────────────────┴─────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Toolbar

**Layout**:
```
┌─ Build Batch ──────────────────────────────────────────┐
│ [+ Add Operation ▾]  [Import from File]  [Templates ▾]│
└────────────────────────────────────────────────────────┘
```

**Components**:
- **Add Operation Dropdown**: `<OverflowMenu>` (Carbon) with options:
  - Add Triple
  - Add Multiple Triples
  - Modify Triple (Patch)
  - Delete Triple
  - Delete Graph
  - Upload to Graph
  - SPARQL Update (advanced)
- **Import Button**: `<Button>` - Upload JSON or RDF Patch file with batch operations
- **Templates Dropdown**: `<Dropdown>` - Pre-defined operation sequences:
  - Add Multiple Users
  - Delete Old Data
  - Update Properties in Bulk
  - Clone Graph

---

### 2.2 Operations Sidebar

**Structure**:
```
┌─ OPERATIONS ────────┐
│ [+ Add Op]          │
│                     │
│ 1. ✏️  Add          │ ← Selected
│    Graph: ex:users  │
│                     │
│ 2. ✏️  Modify       │
│    Graph: ex:users  │
│                     │
│ 3. 🗑️  Delete       │
│    Graph: ex:old    │
│                     │
│ [Clear All]         │
│                     │
│ [Templates]         │
│ • Add Users         │
│ • Delete Graph      │
│ • Update Props      │
│                     │
│ [History]           │
│ • 2h ago (3 ops)    │
│ • Yesterday (5 ops) │
└─────────────────────┘
```

**Components**:
- **Add Operation Button**: `<Button kind="ghost">` - Opens dropdown
- **Operation List**: `<TreeView>` with items:
  - Icon (✏️ for add/modify, 🗑️ for delete)
  - Operation type (shortened: "Add", "Modify", "Delete")
  - Target graph (if applicable)
  - Selected item highlighted with background color
- **Clear All Button**: `<Button kind="danger-ghost">` - Removes all operations (with confirmation)
- **Templates Section**: Collapsible list of saved templates
  - Click template → Load operations into builder
- **History Section**: Recent batch executions
  - Click history item → Load operations for re-execution

**Features**:
- **Drag to Reorder**: Drag operation items to change execution order
- **Click Operation**: Load operation details in main panel (for editing)
- **Right-Click**: Context menu (duplicate, delete, move up/down)

---

### 2.3 Add Triple Operation

**Layout**:
```
┌─ Operation 1: Add Triple ──────────────────────────────┐
│ Type: Add Triple                            [↑][↓][×] │
│                                                        │
│ Graph: [ex:users ▾]                                    │
│                                                        │
│ Subject:   [http://example.org/Alice___________]      │
│ Predicate: [http://example.org/department______]      │
│ Object:    ● URI [http://example.org/Sales_____]      │
│            ○ Literal [___] Lang:[__] Type:[___]      │
│                                                        │
│ [Validate]                                             │
└────────────────────────────────────────────────────────┘
```

**Components**:
- **Operation Header**:
  - Type label: "Add Triple"
  - Control buttons: Move Up (↑), Move Down (↓), Remove (×)
- **Graph Selector**: `<Dropdown>` - Select target graph
- **Subject Input**: `<TextInput>` - URI or blank node
- **Predicate Input**: `<TextInput>` - URI
- **Object Input**: Radio buttons + text input
  - URI: Text input for URI
  - Literal: Text input + optional language tag + datatype selector
- **Validate Button**: `<Button kind="ghost">` - Check URI syntax, datatype validity

**Validation**:
- Subject: Valid URI or blank node format (_:b1)
- Predicate: Valid URI (required)
- Object: Valid URI, literal, or blank node

---

### 2.4 Modify Triple Operation (Patch)

**Layout**:
```
┌─ Operation 2: Modify Triple ───────────────────────────┐
│ Type: Modify Triple (Patch)                 [↑][↓][×] │
│                                                        │
│ Graph: [ex:users ▾]                                    │
│                                                        │
│ Find Triple:                                           │
│ Subject:   [ex:Alice_________________________]         │
│ Predicate: [ex:age___________________________]         │
│ Old Object: "30"                                       │
│                                                        │
│ New Object: ● Literal ["31"] Type:[xsd:integer ▾]     │
│             ○ URI [___________________________]         │
└────────────────────────────────────────────────────────┘
```

**Components**:
- **Find Triple**: Specify which triple to modify (S, P, old O)
- **New Object**: New value to replace old object
- **Graph Selector**: Target graph

**RDF Patch Translation**:
```
PA <ex:Alice> <ex:age> "31"^^<xsd:integer> .
PD <ex:Alice> <ex:age> "30"^^<xsd:integer> .
```

---

### 2.5 Delete Triple Operation

**Layout**:
```
┌─ Operation 3: Delete Triple ───────────────────────────┐
│ Type: Delete Triple                         [↑][↓][×] │
│                                                        │
│ Graph: [ex:users ▾]                                    │
│                                                        │
│ Subject:   [ex:Alice_________________________]         │
│ Predicate: [ex:age___________________________]         │
│ Object:    [30_______________________________]         │
│                                                        │
│ [Validate]                                             │
└────────────────────────────────────────────────────────┘
```

**RDF Patch Translation**:
```
D <ex:Alice> <ex:age> "30"^^<xsd:integer> .
```

---

### 2.6 Delete Graph Operation

**Layout**:
```
┌─ Operation 4: Delete Graph ────────────────────────────┐
│ Type: Delete Graph                          [↑][↓][×] │
│                                                        │
│ Graph: [ex:old-data ▾]                                │
│                                                        │
│ ⚠️  This will delete all triples in this graph.        │
│                                                        │
│ Current Triple Count: 1,247 triples                   │
└────────────────────────────────────────────────────────┘
```

**Features**:
- Shows current triple count (fetched from API)
- Warning message about destructive operation

---

### 2.7 Add Multiple Triples Operation

**Layout**:
```
┌─ Operation 5: Add Multiple Triples ────────────────────┐
│ Type: Add Multiple Triples                  [↑][↓][×] │
│                                                        │
│ Graph: [ex:users ▾]                                    │
│                                                        │
│ Input Format: [N-Triples ▾]                            │
│                                                        │
│ ┌──────────────────────────────────────────────────┐  │
│ │ <ex:Alice> <ex:age> "31"^^<xsd:integer> .        │  │
│ │ <ex:Alice> <ex:dept> <ex:Sales> .                │  │
│ │ <ex:Bob> <ex:age> "28"^^<xsd:integer> .          │  │
│ │ <ex:Bob> <ex:dept> <ex:Engineering> .            │  │
│ └──────────────────────────────────────────────────┘  │
│                                                        │
│ [Validate Syntax]  [Parse from CSV]                   │
└────────────────────────────────────────────────────────┘
```

**Features**:
- **Input Format Selector**: N-Triples, Turtle, JSON-LD
- **Syntax Highlighting**: CodeMirror editor with RDF syntax
- **Validate Button**: Check syntax before adding to batch
- **Parse from CSV**: Modal to map CSV columns to S/P/O

---

### 2.8 SPARQL Update Operation (Advanced)

**Layout**:
```
┌─ Operation 6: SPARQL Update ───────────────────────────┐
│ Type: SPARQL Update (Advanced)              [↑][↓][×] │
│                                                        │
│ ⚠️  Advanced: Direct SPARQL UPDATE query               │
│                                                        │
│ ┌──────────────────────────────────────────────────┐  │
│ │ PREFIX ex: <http://example.org/>                 │  │
│ │                                                  │  │
│ │ DELETE { ?person ex:age ?oldAge }                │  │
│ │ INSERT { ?person ex:age ?newAge }                │  │
│ │ WHERE {                                          │  │
│ │   ?person ex:age ?oldAge .                       │  │
│ │   FILTER(?oldAge > 30)                           │  │
│ │   BIND(?oldAge + 1 AS ?newAge)                   │  │
│ │ }                                                │  │
│ └──────────────────────────────────────────────────┘  │
│                                                        │
│ [Validate SPARQL]  [Preview Affected Triples]         │
└────────────────────────────────────────────────────────┘
```

**Features**:
- **SPARQL Syntax Highlighting**: CodeMirror with SPARQL mode
- **Validate Button**: Check SPARQL syntax
- **Preview Button**: Shows which triples would be affected (dry-run)

---

### 2.9 Preview Panel

**Triggered when**: User clicks "Preview RDF Patch" button

**Layout**:
```
┌─ Batch Preview ────────────────────────────────────────┐
│                                                         │
│ Operation Count: 3                                      │
│ Estimated Changes: +5 triples, -2 triples, ~1 triple   │
│                                                         │
│ ┌─ RDF Patch ──────────────────────────[Copy]────────┐ │
│ │ TX .                                                │ │
│ │ # Operation 1: Add triple                           │ │
│ │ A <ex:Alice> <ex:department> <ex:Sales> ex:users .  │ │
│ │                                                     │ │
│ │ # Operation 2: Modify triple (patch)                │ │
│ │ PA <ex:Alice> <ex:age> "31"^^<xsd:integer> ex:...  │ │
│ │ PD <ex:Alice> <ex:age> "30"^^<xsd:integer> ex:...  │ │
│ │                                                     │ │
│ │ # Operation 3: Delete graph                         │ │
│ │ DG <ex:old-data> .                                  │ │
│ │                                                     │ │
│ │ TC .                                                │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ [Export Patch]                         [Close] [Execute]│
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Summary**: Operation count and estimated changes
- **RDF Patch Viewer**: CodeMirror with syntax highlighting
  - Shows complete RDF Patch that will be sent to server
  - Comments (# lines) show operation descriptions
- **Copy Button**: Copy patch to clipboard
- **Export Button**: Download as .rdfp file
- **Execute Button**: Proceed to execution with commit message

---

### 2.10 Execute Batch Modal

**Triggered when**: User clicks "Execute Batch..." button

**Layout**:
```
┌─ Execute Batch Operations ─────────────────────────────┐
│                                                         │
│ You are about to execute 3 operations:                  │
│ • Add 1 triple to ex:users                             │
│ • Modify 1 triple in ex:users                          │
│ • Delete graph ex:old-data                             │
│                                                         │
│ Commit Message:                                         │
│ ┌─────────────────────────────────────────────────────┐│
│ │ Batch: Add department, update age, cleanup old data ││
│ └─────────────────────────────────────────────────────┘│
│                                                         │
│ Author: [Alice <alice@example.org> ▾]                  │
│                                                         │
│ Options:                                                │
│ ☐ Create backup before executing (recommended)         │
│ ☐ Rollback on first error (atomic execution)           │
│                                                         │
│                          [Cancel]  [Execute Batch]     │
└─────────────────────────────────────────────────────────┘
```

**Components**:
- **Operation Summary**: Bulleted list of operations
- **Commit Message**: `<TextArea>` - Pre-filled, user can edit
- **Author Selector**: `<Dropdown>` - Select or enter author
- **Options**:
  - **Backup**: Create snapshot before execution (rollback if needed)
  - **Atomic**: Stop on first error (vs. continue on error)
- **Execute Button**: `<Button kind="primary">` - Submits batch

**On Execute**:
1. POST `/batch?dataset={dataset}` with RDF Patch + options
2. Server processes batch atomically (within single commit)
3. Progress modal shows execution status
4. On success: Redirect to Version Control with new commit selected
5. Toast notification: "Batch executed successfully. 3 operations applied."

---

### 2.11 Execution Progress Modal

**Shown during batch execution**:

**Layout**:
```
┌─ Executing Batch... ───────────────────────────────────┐
│                                                         │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬●▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬  45%                      │
│                                                         │
│ ✅ Operation 1: Add triple (complete)                   │
│ ⏳ Operation 2: Modify triple (in progress...)          │
│ ⏸️  Operation 3: Delete graph (pending)                │
│                                                         │
│ [Cancel Batch]                                          │
└─────────────────────────────────────────────────────────┘
```

**Features**:
- Progress bar shows overall completion
- Each operation has status icon (✅ done, ⏳ in progress, ⏸️ pending, ❌ failed)
- Cancel button (if server supports cancellation)

---

### 2.12 Batch Templates

**Predefined operation sequences**:

```
┌─ Batch Templates ──────────────────────────────────────┐
│ [+ Create Template]                        [Manage...] │
│                                                         │
│ Add Multiple Users (3 operations)                      │
│ └─ Add user triples, set properties, assign roles     │
│ [Load Template]                                         │
│                                                         │
│ Delete Old Data (2 operations)                         │
│ └─ Delete triples older than date, cleanup graphs     │
│ [Load Template]                                         │
│                                                         │
│ Update Properties in Bulk (5 operations)               │
│ └─ Modify property values across multiple subjects     │
│ [Load Template]                                         │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Features**:
- **Load Template**: Populates builder with template operations
- **Create Template**: Save current operations as reusable template
- **Manage**: Edit, rename, delete templates

---

## 3. Interaction Flows

### 3.1 Build and Execute Simple Batch

1. User navigates to `/batch`
2. User clicks "+ Add Operation" → "Add Triple"
3. Operation 1 form appears
4. User fills in: Graph=ex:users, S=ex:Alice, P=ex:department, O=ex:Sales (URI)
5. User clicks "+ Add Operation" → "Modify Triple"
6. Operation 2 form appears
7. User fills in: Graph=ex:users, S=ex:Alice, P=ex:age, Old O="30", New O="31"
8. User clicks "Preview RDF Patch"
9. Preview modal shows combined RDF Patch
10. User clicks "Execute" in preview modal
11. Execute modal appears with commit message pre-filled
12. User clicks "Execute Batch"
13. Progress modal shows operations executing
14. On completion: Redirect to `/version` with new commit
15. Toast: "Batch executed successfully. 2 operations applied."

### 3.2 Use Template

1. User clicks "Templates" in sidebar
2. User clicks "Add Multiple Users" template
3. Builder loads with 3 pre-configured operations:
   - Add triple: ex:Carol rdf:type ex:Person
   - Add triple: ex:Carol ex:age 25
   - Add triple: ex:Carol ex:role ex:Developer
4. User reviews operations (can edit if needed)
5. User clicks "Execute Batch..."
6. Execute modal appears
7. User clicks "Execute Batch"
8. Batch executes, creating one commit with all changes

### 3.3 Import from File

1. User clicks "Import from File" button
2. File picker modal opens
3. User selects `batch-operations.json` file:
   ```json
   {
     "operations": [
       {
         "type": "add-triple",
         "graph": "ex:users",
         "subject": "ex:David",
         "predicate": "rdf:type",
         "object": {"type": "uri", "value": "ex:Person"}
       },
       {
         "type": "modify-triple",
         "graph": "ex:users",
         "subject": "ex:Alice",
         "predicate": "ex:age",
         "oldObject": {"type": "literal", "value": "30", "datatype": "xsd:integer"},
         "newObject": {"type": "literal", "value": "31", "datatype": "xsd:integer"}
       }
     ]
   }
   ```
4. Parser validates JSON
5. Operations populate builder
6. User reviews and executes

### 3.4 Error Handling During Execution

1. User executes batch with 3 operations
2. Operation 1: Success ✅
3. Operation 2: Error ❌ (invalid URI format)
4. Execution modal shows:
   ```
   ✅ Operation 1: Add triple (success)
   ❌ Operation 2: Modify triple (failed)
      Error: Invalid URI format in object
   ⏸️  Operation 3: Delete graph (not executed)
   ```
5. If "Rollback on first error" was checked:
   - Entire batch rolled back
   - No commit created
   - Toast: "Batch failed. All changes rolled back."
6. If "Continue on error" (default):
   - Operation 1 changes committed
   - Operation 2 skipped
   - Operation 3 executed
   - Toast: "Batch completed with 1 error. 2 operations applied."

### 3.5 Reorder Operations

1. User has 3 operations in builder:
   - Op 1: Delete graph ex:old
   - Op 2: Add triple to ex:old
   - Op 3: Modify triple in ex:users
2. User realizes Op 1 will delete graph before Op 2 adds to it (error!)
3. User drags Op 2 above Op 1 in sidebar
4. New order:
   - Op 1: Add triple to ex:old
   - Op 2: Delete graph ex:old
   - Op 3: Modify triple in ex:users
5. User executes batch successfully

---

## 4. State Management

### 4.1 Local State (Component-Level)

```typescript
// BatchBuilder.svelte
interface BatchBuilderState {
  operations: Operation[];
  selectedOperationIndex: number;
  previewPatch: string | null;
  isExecuting: boolean;
  executionProgress: ExecutionProgress | null;
}

interface Operation {
  id: string;  // UUID for React key
  type: 'add-triple' | 'modify-triple' | 'delete-triple' | 'delete-graph' | 'sparql-update';

  // Common fields
  graph?: string;

  // Add/Delete triple fields
  subject?: string;
  predicate?: string;
  object?: RdfObject;

  // Modify triple fields
  oldObject?: RdfObject;
  newObject?: RdfObject;

  // SPARQL update field
  sparqlUpdate?: string;
}

interface RdfObject {
  type: 'uri' | 'literal' | 'blankNode';
  value: string;
  language?: string;     // For literals
  datatype?: string;     // For literals
}
```

### 4.2 API Service

```typescript
// services/BatchService.ts
class BatchService {
  async executeBatch(
    dataset: string,
    operations: Operation[],
    commitMessage: string,
    author: string,
    options: BatchOptions
  ): Promise<ExecutionResult> {
    // Convert operations to RDF Patch
    const rdfPatch = this.buildRdfPatch(operations);

    const response = await fetch(`/batch?dataset=${dataset}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/rdf-patch',
        'SPARQL-VC-Author': author,
        'SPARQL-VC-Message': commitMessage
      },
      body: rdfPatch
    });

    return response.json();
  }

  private buildRdfPatch(operations: Operation[]): string {
    let patch = 'TX .\n';

    for (const op of operations) {
      patch += `# Operation: ${op.type}\n`;

      switch (op.type) {
        case 'add-triple':
          patch += `A ${this.formatNode(op.subject)} ` +
                   `${this.formatNode(op.predicate)} ` +
                   `${this.formatObject(op.object)} ` +
                   `${this.formatNode(op.graph)} .\n`;
          break;

        case 'modify-triple':
          patch += `PA ${this.formatNode(op.subject)} ` +
                   `${this.formatNode(op.predicate)} ` +
                   `${this.formatObject(op.newObject)} ` +
                   `${this.formatNode(op.graph)} .\n`;
          patch += `PD ${this.formatNode(op.subject)} ` +
                   `${this.formatNode(op.predicate)} ` +
                   `${this.formatObject(op.oldObject)} ` +
                   `${this.formatNode(op.graph)} .\n`;
          break;

        case 'delete-triple':
          patch += `D ${this.formatNode(op.subject)} ` +
                   `${this.formatNode(op.predicate)} ` +
                   `${this.formatObject(op.object)} ` +
                   `${this.formatNode(op.graph)} .\n`;
          break;

        case 'delete-graph':
          patch += `DG ${this.formatNode(op.graph)} .\n`;
          break;
      }

      patch += '\n';
    }

    patch += 'TC .\n';
    return patch;
  }

  async previewBatch(operations: Operation[]): Promise<string> {
    return this.buildRdfPatch(operations);
  }

  async validateOperation(operation: Operation): Promise<ValidationResult> {
    // Client-side validation (URI syntax, datatype, etc.)
    const errors: string[] = [];

    if (operation.subject && !this.isValidUri(operation.subject)) {
      errors.push('Invalid subject URI');
    }

    // ... more validation

    return { valid: errors.length === 0, errors };
  }
}
```

---

## 5. Advanced Features

### 5.1 CSV Import Wizard

**Map CSV columns to RDF triples**:

```
┌─ Import from CSV ──────────────────────────────────────┐
│ Step 1: Upload CSV                                      │
│ ┌─────────────────────────────────────────────────────┐│
│ │ name,age,department                                 ││
│ │ Alice,31,Sales                                      ││
│ │ Bob,28,Engineering                                  ││
│ │ Carol,25,Marketing                                  ││
│ └─────────────────────────────────────────────────────┘│
│                                                         │
│ Step 2: Map Columns to RDF                              │
│ Graph: [ex:users ▾]                                     │
│                                                         │
│ Subject Pattern: [http://example.org/{name}____]        │
│ (Use {columnName} placeholders)                         │
│                                                         │
│ Property Mappings:                                      │
│ • Column "age" → Predicate [ex:age] Type [xsd:integer] │
│ • Column "department" → Predicate [ex:dept] Type [URI]  │
│                                                         │
│ [Preview Triples]                    [Cancel]  [Import] │
└─────────────────────────────────────────────────────────┘
```

**Generated Operations**:
- Creates one "Add Triple" operation per cell
- User can review and edit before executing

### 5.2 Dry-Run Mode

**Execute batch without committing**:

```
┌─ Execute Batch (Dry Run) ──────────────────────────────┐
│                                                         │
│ ☑ Dry-run mode: Preview changes without committing    │
│                                                         │
│ [Execute Dry Run]                                       │
└─────────────────────────────────────────────────────────┘
```

**Result**:
```
┌─ Dry Run Results ──────────────────────────────────────┐
│ ✅ All operations validated successfully                │
│                                                         │
│ Changes that would be applied:                          │
│ • +5 triples to ex:users                               │
│ • -2 triples from ex:old                               │
│ • ~1 triple modified in ex:users                       │
│                                                         │
│ [Execute for Real]                           [Close]    │
└─────────────────────────────────────────────────────────┘
```

### 5.3 Batch History

**View recent batch executions**:

```
┌─ Batch History ────────────────────────────────────────┐
│ Time        Operations  Status   Commit                │
│ ────────────────────────────────────────────────────── │
│ 2h ago          3       ✅       019abc...             │
│ Yesterday       5       ✅       019xyz...             │
│ 3 days ago      2       ❌       (failed)              │
└─────────────────────────────────────────────────────────┘
```

**Features**:
- Click history item → Load operations into builder (re-execute)
- Show failure details for failed batches

### 5.4 Conditional Operations

**Execute operation only if condition met** (future feature):

```
┌─ Operation 7: Add Triple (Conditional) ────────────────┐
│ Type: Add Triple (Conditional)              [↑][↓][×] │
│                                                        │
│ Condition: SPARQL ASK query                            │
│ ┌──────────────────────────────────────────────────┐  │
│ │ ASK WHERE {                                      │  │
│ │   ex:Alice ex:age ?age .                         │  │
│ │   FILTER(?age > 30)                              │  │
│ │ }                                                │  │
│ └──────────────────────────────────────────────────┘  │
│                                                        │
│ If TRUE: Add triple below                              │
│ Subject:   [ex:Alice_________________________]         │
│ Predicate: [ex:category______________________]         │
│ Object:    [ex:Senior________________________]         │
│                                                        │
│ If FALSE: Skip this operation                          │
└────────────────────────────────────────────────────────┘
```

---

## 6. Responsive Behavior

### Mobile (320px - 671px)

- **Operations Sidebar**: Collapsible drawer (hamburger menu)
- **Operation Forms**: Full-width, stacked vertically
- **Preview Modal**: Full-screen overlay

### Tablet (672px - 1055px)

- **Operations Sidebar**: Collapsible (200px width)
- **Operation Forms**: 80% width with scrolling

---

## 7. Accessibility

### Keyboard Navigation

- **Ctrl+N**: Add new operation
- **Ctrl+↑/↓**: Move selected operation up/down
- **Delete**: Remove selected operation (with confirmation)
- **Ctrl+P**: Preview RDF Patch
- **Ctrl+Enter**: Execute batch

### ARIA Labels

- **Operation List**: `aria-label="List of batch operations"`
- **Operation Item**: `aria-label="Operation {index}: {type}, target {graph}"`
- **Execute Button**: `aria-label="Execute {count} batch operations"`

### Screen Reader Announcements

- "Operation {index} added: {type}."
- "Operation {index} removed."
- "Batch executed successfully. {count} operations applied."

---

## 8. Performance

### Optimization Strategies

1. **Lazy Validation**: Validate operations only when needed (on preview/execute)
2. **Debounced Input**: Wait 300ms after typing before validating URIs
3. **Chunked Execution**: For very large batches (100+ ops), process in chunks

### Performance Targets

- **Load Builder**: < 200ms
- **Add Operation**: < 50ms (instant UI update)
- **Preview Patch**: < 100ms for 50 operations
- **Execute Batch**: Varies (show progress for long-running batches)

---

## 9. Error Handling

### Error Scenarios

1. **Invalid URI**: Show inline error below input field
2. **Duplicate Operation**: Warn user if adding identical operation
3. **Graph Not Found**: Show error when selecting non-existent graph
4. **Execution Timeout**: Show progress modal with option to wait or cancel
5. **Partial Failure**: Show which operations succeeded/failed

---

## 10. Testing Considerations

### Unit Tests

- Operation validation logic (URI syntax, datatype)
- RDF Patch generation from operations
- Reordering operations

### Integration Tests

- Build batch and preview patch
- Execute batch and verify commit created
- Import batch from JSON file
- Export batch to JSON file

### E2E Tests (Playwright)

1. **Build and Execute Batch Flow**:
   - Add 3 operations
   - Preview patch
   - Execute batch
   - Verify commit created

2. **Use Template Flow**:
   - Load template
   - Review operations
   - Execute batch

3. **Error Handling Flow**:
   - Add invalid operation
   - Attempt to execute
   - Verify error message displayed

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
