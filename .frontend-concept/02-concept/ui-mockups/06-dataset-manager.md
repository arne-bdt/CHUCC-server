# Dataset Manager - UI Mockup

**View**: Dataset Manager (Multi-Tenant Management)
**Route**: `/datasets`
**Inspiration**: Database management UIs + Multi-tenant admin panels

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs][Version][Time Travel][Datasets✓][Batch]           │
│                                              [Dataset: (all) ▾] [Author ▾]  │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ 🗄️  Dataset Manager                                      [+ Create Dataset] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Filter 🔍]  [Sort: Name ▾]  [View: Cards ▾]           3 datasets total     │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ ┌─ default ──────────────────────┐ ┌─ production ───────────────────────┐  │
│ │ Description: Default dataset   │ │ Description: Production data       │  │
│ │                                │ │                                    │  │
│ │ 📊 Graphs: 5                   │ │ 📊 Graphs: 12                      │  │
│ │ 📝 Commits: 342                │ │ 📝 Commits: 1,847                  │  │
│ │ 🌿 Branches: 3                 │ │ 🌿 Branches: 8                     │  │
│ │ 📈 Size: 2.4 MB                │ │ 📈 Size: 15.7 MB                   │  │
│ │ 🕐 Created: 2025-01-15         │ │ 🕐 Created: 2024-06-10             │  │
│ │ 🔄 Last Updated: 2h ago        │ │ 🔄 Last Updated: 5 min ago         │  │
│ │                                │ │                                    │  │
│ │ ✅ Healthy                     │ │ ⚠️  1 warning (large size)         │  │
│ │                                │ │                                    │  │
│ │ [Open] [Settings] [Backup]    │ │ [Open] [Settings] [Backup]        │  │
│ └────────────────────────────────┘ └────────────────────────────────────┘  │
│                                                                              │
│ ┌─ testing ──────────────────────┐                                          │
│ │ Description: Test environment  │                                          │
│ │                                │                                          │
│ │ 📊 Graphs: 2                   │                                          │
│ │ 📝 Commits: 45                 │                                          │
│ │ 🌿 Branches: 2                 │                                          │
│ │ 📈 Size: 512 KB                │                                          │
│ │ 🕐 Created: 2025-11-01         │                                          │
│ │ 🔄 Last Updated: 3 days ago    │                                          │
│ │                                │                                          │
│ │ ✅ Healthy                     │                                          │
│ │                                │                                          │
│ │ [Open] [Settings] [Backup]    │                                          │
│ └────────────────────────────────┘                                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Header and Actions

**Layout**:
```
┌──────────────────────────────────────────────────────────┐
│ 🗄️  Dataset Manager                  [+ Create Dataset]  │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Title**: "Dataset Manager" with database icon
- **Create Button**: `<Button kind="primary">` - Opens dataset creation wizard
  - Primary action (encouraged for new datasets)

---

### 2.2 Toolbar

**Layout**:
```
┌──────────────────────────────────────────────────────────┐
│ [Filter 🔍]  [Sort: Name ▾]  [View: Cards ▾]   3 datasets│
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Filter Input**: `<Search>` (Carbon) - Filter by name or description
- **Sort Dropdown**: `<Dropdown>` options:
  - Name (A-Z)
  - Name (Z-A)
  - Created (newest first)
  - Created (oldest first)
  - Last Updated (most recent first)
  - Size (largest first)
  - Size (smallest first)
- **View Dropdown**: `<Dropdown>` options:
  - Cards (default) - Grid of cards
  - Table - Detailed table view
  - Compact - List view
- **Dataset Count**: Shows total number of datasets (e.g., "3 datasets")

---

### 2.3 Dataset Card (Card View)

**Layout**:
```
┌─ default ──────────────────────┐
│ Description: Default dataset   │
│                                │
│ 📊 Graphs: 5                   │
│ 📝 Commits: 342                │
│ 🌿 Branches: 3                 │
│ 📈 Size: 2.4 MB                │
│ 🕐 Created: 2025-01-15         │
│ 🔄 Last Updated: 2h ago        │
│                                │
│ ✅ Healthy                     │
│                                │
│ [Open] [Settings] [Backup]    │
└────────────────────────────────┘
```

**Components**:
- **Card**: `<Tile>` (Carbon)
- **Name**: `<h3>` - Dataset identifier
- **Description**: `<p>` - Brief description (truncated if long)
- **Metadata Icons**: Icon + text for each stat
  - 📊 Graphs: Number of named graphs
  - 📝 Commits: Total commit count
  - 🌿 Branches: Number of branches
  - 📈 Size: Disk usage (KB/MB/GB)
  - 🕐 Created: Creation date (relative or absolute)
  - 🔄 Last Updated: Last commit timestamp (relative: "2h ago")
- **Health Indicator**:
  - ✅ Healthy (green checkmark)
  - ⚠️ Warning (yellow warning icon + message)
  - ❌ Error (red error icon + message)
- **Action Buttons**: `<ButtonSet>` (Carbon)
  - **Open**: Navigate to dataset (default: Query Workbench)
  - **Settings**: Open settings modal
  - **Backup**: Open backup/export modal

**Health Indicators**:
- **Healthy**: All systems operational
- **Warning**: Non-critical issues (e.g., large size, old backups)
- **Error**: Critical issues (e.g., corrupted data, Kafka errors)

**State**:
```typescript
interface DatasetCard {
  name: string;
  description: string;
  stats: {
    graphCount: number;
    commitCount: number;
    branchCount: number;
    sizeBytes: number;
  };
  timestamps: {
    created: Date;
    lastUpdated: Date;
  };
  health: {
    status: 'healthy' | 'warning' | 'error';
    message?: string;
  };
}
```

---

### 2.4 Dataset Table View

**Alternative to card view**, showing more columns:

**Layout**:
```
┌─ Datasets (Table View) ────────────────────────────────────────────────────┐
│ Name        Description         Graphs  Commits  Size    Updated    Status │
│ ─────────────────────────────────────────────────────────────────────────│
│ default     Default dataset        5      342    2.4 MB  2h ago     ✅    │
│ production  Production data       12    1,847   15.7 MB  5 min ago  ⚠️    │
│ testing     Test environment       2       45    512 KB  3d ago     ✅    │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Components**:
- **DataTable**: `<DataTable>` (Carbon) with sortable columns
- **Row Actions**: Overflow menu (⋮) with Open, Settings, Backup, Delete
- **Selectable Rows**: Checkbox column for bulk operations
- **Pagination**: If > 20 datasets

---

### 2.5 Create Dataset Wizard

**Triggered when**: User clicks "+ Create Dataset" button

**Step 1: Basic Information**

```
┌─ Create Dataset (1/3) ─────────────────────────────────────┐
│                                                             │
│ Dataset Name:                                               │
│ [my-new-dataset___________________________________]         │
│ (lowercase, alphanumeric, hyphens only)                     │
│                                                             │
│ Description:                                                │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ My new dataset for...                                   ││
│ └─────────────────────────────────────────────────────────┘│
│ (optional, max 500 characters)                              │
│                                                             │
│                                     [Cancel]  [Next Step →]│
└─────────────────────────────────────────────────────────────┘
```

**Validation**:
- Name: Required, lowercase, alphanumeric + hyphens, 3-50 chars
- Description: Optional, max 500 chars

---

**Step 2: Initial Data (Optional)**

```
┌─ Create Dataset (2/3) ─────────────────────────────────────┐
│                                                             │
│ Initialize with data? (optional)                            │
│                                                             │
│ ● Empty dataset (recommended)                               │
│ ○ Upload RDF file                                           │
│   └─ [Choose File...] (Turtle, JSON-LD, RDF/XML, etc.)    │
│ ○ Clone from existing dataset                              │
│   └─ [Select Dataset ▾] [Branch: main ▾]                  │
│ ○ Import from URL                                           │
│   └─ [https://example.org/data.ttl______________]          │
│                                                             │
│                            [← Back]  [Cancel]  [Next Step →]│
└─────────────────────────────────────────────────────────────┘
```

**Options**:
- **Empty Dataset**: Creates dataset with initial commit (empty)
- **Upload File**: User selects local RDF file
- **Clone Dataset**: Copy all data from another dataset's branch
- **Import from URL**: Fetch RDF from remote URL

---

**Step 3: Configuration**

```
┌─ Create Dataset (3/3) ─────────────────────────────────────┐
│                                                             │
│ Kafka Configuration (advanced)                              │
│                                                             │
│ Topic Name: [vc.my-new-dataset.events_______]              │
│ (auto-generated, can customize)                             │
│                                                             │
│ Retention: [7 days ▾]                                       │
│ (1 day, 7 days, 30 days, 90 days, Forever)                │
│                                                             │
│ Partitions: [1 ▾]                                           │
│ (1, 3, 5, 10)                                              │
│                                                             │
│ Replication Factor: [1 ▾]                                   │
│ (1, 2, 3)                                                  │
│                                                             │
│ ☐ Create backup schedule (configure after creation)        │
│                                                             │
│                            [← Back]  [Cancel]  [Create]    │
└─────────────────────────────────────────────────────────────┘
```

**Configuration Options**:
- **Topic Name**: Kafka topic for events (auto-generated, editable)
- **Retention**: How long to keep events in Kafka
- **Partitions**: Kafka partitions for scalability
- **Replication Factor**: Kafka replication (for high availability)
- **Backup Schedule**: Optional checkbox (configure in settings later)

**On Create**:
1. POST `/version/datasets/{name}` with configuration
2. Server creates Kafka topics, initial commit, main branch
3. Response: 202 Accepted (async creation)
4. Redirect to dataset Query Workbench
5. Toast notification: "Dataset '{name}' created successfully"

---

### 2.6 Dataset Settings Modal

**Triggered when**: User clicks "Settings" button on dataset card

**Layout**:
```
┌─ Dataset Settings: default ────────────────────────────────┐
│                                                             │
│ [General] [Security] [Kafka] [Backup] [Danger Zone]       │
│ ───────────────────────────────────────────────────────────│
│                                                             │
│ General Settings                                            │
│                                                             │
│ Description:                                                │
│ ┌─────────────────────────────────────────────────────────┐│
│ │ Default dataset for development                         ││
│ └─────────────────────────────────────────────────────────┘│
│                                                             │
│ Default Branch: [main ▾]                                    │
│                                                             │
│ Author Format Validation:                                   │
│ ☑ Require email format for authors                         │
│                                                             │
│                                     [Cancel]  [Save Changes]│
└─────────────────────────────────────────────────────────────┘
```

**Tabs**:

1. **General**: Description, default branch, author validation
2. **Security**: Access control (future: user permissions)
3. **Kafka**: Topic configuration (read-only after creation, shows current settings)
4. **Backup**: Automated backup schedule
5. **Danger Zone**: Delete dataset (with confirmation)

---

**Security Tab**:

```
┌─ Dataset Settings: default ────────────────────────────────┐
│ [General] [Security] [Kafka] [Backup] [Danger Zone]       │
│ ───────────────────────────────────────────────────────────│
│                                                             │
│ Access Control (Future Feature)                             │
│                                                             │
│ ℹ️  Access control will be available in a future release.  │
│                                                             │
│ Current Status: Public (no authentication required)         │
│                                                             │
│ Planned Features:                                           │
│ • User-based permissions (read, write, admin)              │
│ • API key management                                        │
│ • OAuth2 integration                                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

**Backup Tab**:

```
┌─ Dataset Settings: default ────────────────────────────────┐
│ [General] [Security] [Kafka] [Backup] [Danger Zone]       │
│ ───────────────────────────────────────────────────────────│
│                                                             │
│ Automated Backups                                           │
│                                                             │
│ ☑ Enable automated backups                                 │
│                                                             │
│ Schedule: [Daily at 02:00 ▾]                                │
│ (Hourly, Daily, Weekly, Monthly)                           │
│                                                             │
│ Retention: [Keep last 7 backups ▾]                          │
│ (3, 7, 14, 30, All)                                        │
│                                                             │
│ Backup Location: [/backups/default/ ▾]                      │
│                                                             │
│ Last Backup: 2025-11-10 02:00:00 (10h ago)                 │
│ Status: ✅ Success (2.4 MB)                                 │
│                                                             │
│ [Run Backup Now]                                            │
│                                     [Cancel]  [Save Changes]│
└─────────────────────────────────────────────────────────────┘
```

---

**Danger Zone Tab**:

```
┌─ Dataset Settings: default ────────────────────────────────┐
│ [General] [Security] [Kafka] [Backup] [Danger Zone]       │
│ ───────────────────────────────────────────────────────────│
│                                                             │
│ ⚠️  Danger Zone                                             │
│                                                             │
│ Delete Dataset                                              │
│                                                             │
│ Permanently delete this dataset and all associated data:   │
│ • All graphs (5 graphs)                                    │
│ • All commits (342 commits)                                │
│ • All branches (3 branches)                                │
│ • Kafka topics (vc.default.events, vc.default.events.dlq) │
│                                                             │
│ ⚠️  This action CANNOT be undone!                           │
│                                                             │
│ [Delete Dataset...]                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Delete Confirmation Modal**:

```
┌─ Delete Dataset: default? ─────────────────────────────────┐
│                                                             │
│ ⚠️  Are you sure you want to delete this dataset?          │
│                                                             │
│ This will permanently delete:                               │
│ • 5 graphs                                                 │
│ • 342 commits                                              │
│ • 3 branches                                               │
│ • 2.4 MB of data                                           │
│ • Kafka topics: vc.default.events, vc.default.events.dlq  │
│                                                             │
│ To confirm, type the dataset name: default                  │
│ [_____________________________________________]              │
│                                                             │
│                              [Cancel]  [Delete Dataset]    │
└─────────────────────────────────────────────────────────────┘
```

**Delete Button**: Disabled until user types dataset name correctly.

---

### 2.7 Backup/Export Modal

**Triggered when**: User clicks "Backup" button on dataset card

**Layout**:
```
┌─ Backup Dataset: default ──────────────────────────────────┐
│                                                             │
│ Export Format:                                              │
│ ● Full backup (includes commit history)                    │
│ ○ Current state only (snapshot at HEAD)                    │
│                                                             │
│ Output Format: [Turtle ▾]                                   │
│ (Turtle, JSON-LD, RDF/XML, N-Quads, TriG)                 │
│                                                             │
│ Include:                                                    │
│ ☑ All named graphs                                         │
│ ☑ Default graph                                            │
│ ☑ Metadata (branches, tags)                                │
│                                                             │
│ Compression: [gzip ▾]                                       │
│ (None, gzip, zip)                                          │
│                                                             │
│ [Preview Export]                    [Cancel]  [Export]     │
└─────────────────────────────────────────────────────────────┘
```

**Export Options**:
- **Full Backup**: Exports commit history (RDF Patch events) + current state
- **Current State Only**: Exports only latest data (faster, smaller)
- **Output Format**: RDF serialization format
- **Compression**: Optional gzip or zip compression

**On Export**:
1. POST `/version/datasets/{name}/export` with options
2. Server generates export file
3. Browser downloads file: `{dataset}-backup-{timestamp}.ttl.gz`
4. Toast notification: "Export complete. Downloaded {size} MB."

---

## 3. Interaction Flows

### 3.1 Create Empty Dataset

1. User clicks "+ Create Dataset"
2. Wizard opens (Step 1)
3. User enters name: "my-project"
4. User enters description: "Dataset for my project"
5. User clicks "Next Step →"
6. Step 2: User selects "Empty dataset" (default)
7. User clicks "Next Step →"
8. Step 3: User reviews Kafka config (defaults are fine)
9. User clicks "Create"
10. POST request sent
11. Redirect to `/query?dataset=my-project`
12. Toast: "Dataset 'my-project' created successfully"

### 3.2 Clone Existing Dataset

1. User clicks "+ Create Dataset"
2. Step 1: User enters name: "staging"
3. Step 2: User selects "Clone from existing dataset"
4. User selects dataset: "production"
5. User selects branch: "main"
6. Step 3: User reviews config
7. User clicks "Create"
8. Server clones all data from production:main
9. Redirect to staging dataset
10. Toast: "Dataset 'staging' created with {N} triples from 'production'"

### 3.3 Delete Dataset

1. User clicks "Settings" on "testing" dataset card
2. Modal opens, user clicks "Danger Zone" tab
3. User clicks "Delete Dataset..." button
4. Confirmation modal opens
5. User types "testing" in confirmation input
6. "Delete Dataset" button becomes enabled
7. User clicks "Delete Dataset"
8. DELETE request sent
9. Modal closes
10. Dataset card removed from view
11. Toast: "Dataset 'testing' deleted successfully"

### 3.4 Export Dataset Backup

1. User clicks "Backup" on "default" dataset card
2. Backup modal opens
3. User selects "Full backup"
4. User selects format: "Turtle"
5. User enables compression: "gzip"
6. User clicks "Export"
7. Server generates export (shows progress bar)
8. Browser downloads file: `default-backup-20251110.ttl.gz`
9. Toast: "Export complete. Downloaded 2.4 MB."

### 3.5 View Dataset Health Details

1. User sees "production" card with ⚠️ warning
2. User clicks card (anywhere except buttons)
3. Health modal opens:
   ```
   ┌─ Dataset Health: production ───────────────────────┐
   │                                                     │
   │ ⚠️  1 Warning                                        │
   │                                                     │
   │ Large Size (15.7 MB)                                │
   │ └─ Consider archiving old data or splitting        │
   │    dataset into multiple smaller datasets.         │
   │                                                     │
   │ Recommendations:                                    │
   │ • Review commit history for large commits          │
   │ • Export and delete old branches                   │
   │ • Enable automated backups                         │
   │                                                     │
   │                                         [Close]    │
   └─────────────────────────────────────────────────────┘
   ```

---

## 4. State Management

### 4.1 Global State (Svelte Stores)

```typescript
// stores/datasets.ts
export interface DatasetInfo {
  name: string;
  description: string;
  stats: {
    graphCount: number;
    commitCount: number;
    branchCount: number;
    sizeBytes: number;
  };
  timestamps: {
    created: Date;
    lastUpdated: Date;
  };
  health: {
    status: 'healthy' | 'warning' | 'error';
    message?: string;
    details?: HealthDetail[];
  };
  kafkaConfig: {
    topicName: string;
    retentionDays: number;
    partitions: number;
    replicationFactor: number;
  };
}

export const datasets = writable<DatasetInfo[]>([]);
export const currentDataset = writable<string>('default');
```

### 4.2 API Service

```typescript
// services/DatasetService.ts
class DatasetService {
  async listDatasets(): Promise<DatasetInfo[]> {
    const response = await fetch('/version/datasets');
    return response.json();
  }

  async createDataset(config: CreateDatasetConfig): Promise<{ name: string }> {
    const response = await fetch(`/version/datasets/${config.name}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'SPARQL-VC-Author': config.author
      },
      body: JSON.stringify(config)
    });
    return response.json();
  }

  async deleteDataset(name: string): Promise<void> {
    await fetch(`/version/datasets/${name}`, {
      method: 'DELETE',
      headers: {
        'SPARQL-VC-Author': this.getCurrentAuthor()
      }
    });
  }

  async exportDataset(
    name: string,
    options: ExportOptions
  ): Promise<Blob> {
    const response = await fetch(`/version/datasets/${name}/export`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(options)
    });
    return response.blob();
  }

  async getDatasetHealth(name: string): Promise<HealthStatus> {
    const response = await fetch(`/version/datasets/${name}/health`);
    return response.json();
  }
}
```

---

## 5. Advanced Features

### 5.1 Dataset Templates

**Predefined dataset configurations for common use cases**:

```
┌─ Create Dataset from Template ─────────────────────────────┐
│                                                             │
│ Select Template:                                            │
│                                                             │
│ ● Blank Dataset                                             │
│   Empty dataset with default configuration                  │
│                                                             │
│ ○ FOAF Social Network                                       │
│   Pre-configured with FOAF ontology and sample data        │
│                                                             │
│ ○ Schema.org Structured Data                                │
│   Pre-configured with Schema.org vocabulary                 │
│                                                             │
│ ○ Dublin Core Metadata                                      │
│   Pre-configured with DC elements and terms                 │
│                                                             │
│                                     [Cancel]  [Next Step →]│
└─────────────────────────────────────────────────────────────┘
```

**Templates Include**:
- Pre-defined namespaces/prefixes
- Sample data (optional)
- Recommended graph structure
- Pre-configured validation rules

### 5.2 Dataset Metrics Dashboard

**Detailed analytics for dataset**:

```
┌─ Dataset Metrics: production ──────────────────────────────┐
│                                                             │
│ [Overview] [Commits] [Graphs] [Performance]                │
│ ───────────────────────────────────────────────────────────│
│                                                             │
│ Commit Activity (Last 30 Days)                              │
│ ┌─────────────────────────────────────────────────────────┐│
│ │       📊                                                ││
│ │      ████                                               ││
│ │     ██████                                              ││
│ │    ████████                                             ││
│ │   ██████████                                            ││
│ │  ████████████████                                       ││
│ │ ───────────────────────────────────────────────────────││
│ │ Week1  Week2  Week3  Week4                              ││
│ └─────────────────────────────────────────────────────────┘│
│                                                             │
│ Top Contributors:                                           │
│ 1. Alice (127 commits, 45%)                                │
│ 2. Bob (89 commits, 32%)                                   │
│ 3. Carol (65 commits, 23%)                                 │
│                                                             │
│ Largest Graphs:                                             │
│ 1. ex:users (8.2 MB, 52%)                                  │
│ 2. ex:products (4.1 MB, 26%)                               │
│ 3. ex:orders (2.3 MB, 15%)                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 Dataset Comparison

**Compare two datasets side-by-side**:

```
┌─ Compare Datasets ─────────────────────────────────────────┐
│                                                             │
│ Dataset A: [production ▾]    Dataset B: [staging ▾]        │
│                                                             │
│ Metric              Production    Staging       Diff        │
│ ──────────────────────────────────────────────────────────│
│ Graphs                   12           5       -7 (58%)     │
│ Commits               1,847         342   -1,505 (81%)     │
│ Branches                  8           3       -5 (63%)     │
│ Size (MB)              15.7         2.4     -13.3 (85%)    │
│                                                             │
│ Unique Graphs in Production: ex:orders, ex:products, ...   │
│ Unique Graphs in Staging: (none)                           │
│                                                             │
│ [Export Comparison Report]                      [Close]    │
└─────────────────────────────────────────────────────────────┘
```

### 5.4 Dataset Access Logs

**Audit trail of dataset operations** (future feature):

```
┌─ Access Logs: production ──────────────────────────────────┐
│ [Filter by User ▾] [Filter by Action ▾] [Date Range]      │
│                                                             │
│ Time        User    Action              Details            │
│ ──────────────────────────────────────────────────────────│
│ 10:45:23    Alice   Query Executed      SELECT * WHERE...  │
│ 10:30:12    Bob     Commit Created      Merge feature/auth │
│ 09:15:45    Carol   Graph Deleted       ex:old-data        │
│ 08:00:00    System  Automated Backup    Success (15.7 MB)  │
│                                                             │
│ [Export Logs]                     [◀ Prev] Page 1 [Next ▶]│
└─────────────────────────────────────────────────────────────┘
```

---

## 6. Responsive Behavior

### Mobile (320px - 671px)

- **Dataset Cards**: Full-width, stacked vertically
- **Toolbar**: Simplified (filter + view toggle only)
- **Settings Modal**: Full-screen overlay
- **Wizard**: Full-screen steps

### Tablet (672px - 1055px)

- **Dataset Cards**: 2 columns
- **Toolbar**: Horizontal layout
- **Settings Modal**: 80% screen width

---

## 7. Accessibility

### Keyboard Navigation

- **Ctrl+N**: Open "Create Dataset" wizard
- **Enter**: Open selected dataset (in Query Workbench)
- **Del**: Delete selected dataset (with confirmation)
- **Tab**: Navigate between cards/rows

### ARIA Labels

- **Dataset Card**: `aria-label="Dataset {name}, {graphCount} graphs, {status}"`
- **Create Button**: `aria-label="Create new dataset"`
- **Delete Button**: `aria-label="Delete dataset {name}"`

### Screen Reader Announcements

- "Dataset '{name}' created successfully."
- "{count} datasets found. Sorted by {criteria}."
- "Dataset '{name}' health status: {status}."

---

## 8. Performance

### Optimization Strategies

1. **Lazy Load Dataset Stats**: Load stats only for visible cards
2. **Debounced Filter**: Wait 300ms after typing before filtering
3. **Virtual Scrolling**: For table view with 100+ datasets

### Performance Targets

- **Load Dataset List**: < 500ms for 50 datasets
- **Create Dataset**: < 2s (async, returns quickly)
- **Delete Dataset**: < 5s
- **Export Dataset**: Varies (show progress bar)

---

## 9. Testing Considerations

### Unit Tests

- Dataset name validation (lowercase, alphanumeric, hyphens)
- Health status computation (warnings, errors)
- Sort and filter logic

### Integration Tests

- Create empty dataset
- Create dataset with initial data
- Delete dataset
- Export dataset
- Update dataset settings

### E2E Tests (Playwright)

1. **Create Dataset Flow**:
   - Click "Create Dataset"
   - Fill wizard (3 steps)
   - Submit and verify redirect
   - Verify dataset appears in list

2. **Delete Dataset Flow**:
   - Open settings for dataset
   - Navigate to Danger Zone
   - Type confirmation
   - Delete and verify removed

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
