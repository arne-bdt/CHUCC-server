# Time Travel - UI Mockup

**View**: Time Travel Queries
**Route**: `/time-travel`
**Inspiration**: Git time-travel interfaces + Temporal database UIs

---

## 1. ASCII Wireframe (Desktop - 1280px)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [CHUCC]  [Query][Graphs][Version][Time Travel✓][Datasets][Batch]           │
│                                              [Dataset: default ▾] [Author ▾] │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────┐
│ 🕐 Time Travel: Query Historical Data                                        │
│ ─────────────────────────────────────────────────────────────────────────────│
│ Select Time: [2025-11-10 ⏰ 10:30:00 ▾]          [⏮ Earliest] [Latest ⏭]   │
│              └─ Branch: main @ commit 019abc...                             │
└──────────────────────────────────────────────────────────────────────────────┘
┌──────────────┬───────────────────────────────────────────────────────────────┐
│ TIMELINE     │ ┌─ Query Editor ───────────────────────────────────────────┐ │
│ ────────────│ │ SELECT ?person ?age                                      │ │
│ [Play ▶]    │ │ WHERE {                                                  │ │
│ [Speed: 1x▾]│ │   ?person rdf:type ex:Person .                           │ │
│              │ │   ?person ex:age ?age .                                  │ │
│ 2025-11-10   │ │ }                                                        │ │
│ 14:00 ●─────│ └──────────────────────────────────────────────────────────┘ │
│              │ [▶ Execute at Selected Time]                                │
│ 12:00 ●─────│ ─────────────────────────────────────────────────────────────│
│       ↑ YOU │ ┌─ Results @ 2025-11-10 10:30:00 ─────────────────────────┐ │
│ 10:30 ●─────│ │ ℹ️  Queried at: 2025-11-10T10:30:00Z (commit 019abc...) │ │
│              │ │                                                          │ │
│ 08:00 ●─────│ │ person              age                                  │ │
│              │ │ ──────────────────────────────────────────────────────  │ │
│ 06:00 ●─────│ │ ex:Alice            30                                   │ │
│              │ │ ex:Bob              28                                   │ │
│ 2025-11-09   │ │ ex:Carol            25                                   │ │
│ 18:00 ●─────│ │                                                          │ │
│              │ └──────────────────────────────────────────────────────────┘ │
│ 12:00 ●─────│ ─────────────────────────────────────────────────────────────│
│              │ ┌─ Change History ─────────────────────────────────────────┐ │
│ 06:00 ●─────│ │ Showing changes within ± 1 hour of selected time         │ │
│              │ │                                                          │ │
│ [Filter ⏱]  │ │ 10:45  +A  ex:Carol ex:age 25  (commit 019def...)       │ │
│              │ │ 10:30  PA  ex:Alice ex:age 30 → 31  (commit 019abc...)  │ │
│ [Commits]    │ │ 10:15  +A  ex:Bob ex:role User  (commit 019xyz...)      │ │
│ [Graphs]     │ │ 09:50  -D  ex:OldUser ex:age 40  (commit 019uvw...)     │ │
│ [Operations] │ └──────────────────────────────────────────────────────────┘ │
└──────────────┴───────────────────────────────────────────────────────────────┘
```

---

## 2. Component Breakdown

### 2.1 Time Selection Header

**Layout**:
```
┌──────────────────────────────────────────────────────────┐
│ 🕐 Time Travel: Query Historical Data                    │
│ ─────────────────────────────────────────────────────────│
│ Select Time: [2025-11-10 ⏰ 10:30:00 ▾]  [⏮ Earliest] [Latest ⏭] │
│              └─ Branch: main @ commit 019abc...         │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Time Picker**: `<DateTimePicker>` (Carbon)
  - Date selector (calendar popup)
  - Time selector (hours:minutes:seconds)
  - Timezone indicator (based on user preference or server time)
- **Commit Info**: Shows which commit corresponds to selected time
  - Format: "Branch: {branch} @ commit {shortSHA}"
  - Click commit SHA → Navigate to Version Control view with that commit selected
- **Quick Navigation Buttons**:
  - **Earliest**: Jump to first commit timestamp
  - **Latest**: Jump to latest commit timestamp (HEAD)

**Features**:
- **Real-time Validation**: Show warning if no commits exist at selected time
- **Smart Defaults**: Default to "now" (latest commit) when view loads
- **Commit Snapping**: Option to snap to nearest commit timestamp

**Understanding Time Travel**:

Time travel in CHUCC queries specific commits in your dataset's history.
When you select a timestamp:
1. The system finds the commit at or before that time
2. Your query executes against that commit's snapshot
3. There is no data "between" commits

Think of commits as bookmarks in time - the timeline shows your commit
history in chronological order, not a continuous stream.

**Why this matters**: Selecting "10:30:00" executes at the commit created
at or just before 10:30, not at some interpolated state.

**State**:
```typescript
interface TimeTravelState {
  selectedTime: Date;            // ISO 8601 timestamp
  correspondingCommit: string;   // Commit ID (UUIDv7)
  correspondingBranch: string;   // Branch name (if HEAD of branch)
  earliestTime: Date;            // First commit timestamp
  latestTime: Date;              // Latest commit timestamp
}
```

---

### 2.2 Timeline Sidebar

**Structure**:
```
┌─ TIMELINE ────────┐
│ [Play ▶]          │ ← Animation controls
│ [Speed: 1x ▾]     │
│                   │
│ 2025-11-10        │ ← Day dividers
│ 14:00 ●───────    │ ← Commit markers
│                   │
│ 12:00 ●───────    │
│       ↑ YOU       │ ← Current selection
│ 10:30 ●───────    │
│                   │
│ 08:00 ●───────    │
│                   │
│ 06:00 ●───────    │
│                   │
│ 2025-11-09        │
│ 18:00 ●───────    │
│                   │
│ 12:00 ●───────    │
│                   │
│ [Filter ⏱]       │ ← Timeline filters
│ [Commits]         │
│ [Graphs]          │
│ [Operations]      │
└───────────────────┘
```

**Components**:
- **Play Button**: `<Button kind="ghost">` - Starts timeline animation
  - Animates through commits over time
  - Query results update in real-time
- **Speed Selector**: `<Dropdown>` - Animation speed (0.5x, 1x, 2x, 5x, 10x)
- **Timeline Track**: Vertical list of commit markers
  - Each marker: Circle (●) with timestamp
  - Selected marker: Highlighted with arrow (↑ YOU)
  - Day dividers: Dates shown above first commit of each day
- **Commit Markers**:
  - Click marker → Jump to that commit time
  - Hover marker → Show tooltip (commit message, author, SHA)
  - Line length represents "size" of commit (more changes = longer line)
- **Timeline Filters**: `<Accordion>` (collapsible)
  - **Commits**: Show only merge commits, regular commits, etc.
  - **Graphs**: Filter by graph URI (e.g., only show commits affecting ex:users)
  - **Operations**: Filter by operation type (add, delete, modify)

**Interactions**:
- **Scroll Timeline**: Mouse wheel or drag scrollbar to see older/newer commits
- **Click Marker**: Jump to that commit, update query results
- **Play Animation**: Auto-advance through commits every N seconds (based on speed)

**State**:
```typescript
interface TimelineState {
  commits: Array<{
    id: string;
    timestamp: Date;
    message: string;
    author: string;
    graphsChanged: string[];
    changeCount: number;  // For visual "size" of marker
  }>;
  selectedCommitId: string;
  isPlaying: boolean;
  playbackSpeed: number;  // 0.5, 1, 2, 5, 10
  filters: {
    commitTypes: Set<string>;   // 'merge', 'regular'
    graphs: Set<string>;         // Graph URIs
    operations: Set<string>;     // 'add', 'delete', 'modify'
  };
}
```

---

### 2.3 Query Editor

**Identical to Query Workbench editor**, but:
- **Context Indicator**: Shows selected time prominently
- **Read-Only Query Text**: Optional mode to prevent editing (focus on time-travel)
- **Execute Button**: "Execute at Selected Time" (emphasizes temporal aspect)

**Layout**:
```
┌─ Query Editor ───────────────────────────────────────────┐
│ SELECT ?person ?age                                      │
│ WHERE {                                                  │
│   ?person rdf:type ex:Person .                           │
│   ?person ex:age ?age .                                  │
│ }                                                        │
└──────────────────────────────────────────────────────────┘
[▶ Execute at Selected Time]
```

**Features**:
- **Query History**: Dropdown showing recent queries executed in time-travel mode
- **Quick Queries**: Predefined queries for common time-travel scenarios:
  - "Show all triples added in last hour"
  - "Show all deleted triples in selected time range"
  - "Compare current vs. selected time"

---

### 2.4 Results Panel

**Layout**:
```
┌─ Results @ 2025-11-10 10:30:00 ─────────────────────────┐
│ ℹ️  Queried at: 2025-11-10T10:30:00Z (commit 019abc...) │
│                                                          │
│ person              age                                  │
│ ──────────────────────────────────────────────────────  │
│ ex:Alice            30                                   │
│ ex:Bob              28                                   │
│ ex:Carol            25                                   │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Timestamp Indicator**: Prominent display of queried timestamp
- **Commit Link**: Click commit SHA → Navigate to Version Control
- **Results Table**: Identical to Query Workbench results table
  - Virtual scrolling for 10k+ rows
  - Export functionality (CSV, JSON, etc.)

**Additional Features**:
- **Compare with Now**: Button to execute same query at latest time and show diff
- **Pin Results**: Keep results from specific time while exploring others

---

### 2.5 Change History Panel

**Layout**:
```
┌─ Change History ─────────────────────────────────────────┐
│ Showing changes within ± 1 hour of selected time        │
│                                                          │
│ 10:45  +A  ex:Carol ex:age 25  (commit 019def...)       │
│ 10:30  PA  ex:Alice ex:age 30 → 31  (commit 019abc...)  │
│ 10:15  +A  ex:Bob ex:role User  (commit 019xyz...)      │
│ 09:50  -D  ex:OldUser ex:age 40  (commit 019uvw...)     │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Time Range Selector**: `<Dropdown>` - Show changes within:
  - ± 15 minutes
  - ± 1 hour (default)
  - ± 6 hours
  - ± 1 day
  - Custom range
- **Change List**: `<DataTable>` with columns:
  - **Time**: Timestamp of change (relative format: "15 min ago")
  - **Operation**: Icon + abbreviation
    - +A: Addition (green plus icon)
    - -D: Deletion (red minus icon)
    - PA: Patch Addition (yellow arrow icon)
    - PD: Patch Deletion (yellow arrow icon)
  - **Triple**: Subject, predicate, object (condensed format)
  - **Commit**: Short SHA (clickable)
- **Hover Triple**: Show full triple in tooltip
- **Click Change**: Highlight that triple in results (if present in current query)

**Features**:
- **Filter by Operation**: Show only additions, deletions, or modifications
- **Filter by Graph**: Show changes only in specific graph
- **Filter by Subject**: Show changes only for specific resource

**State**:
```typescript
interface ChangeHistoryState {
  timeRange: { before: number; after: number };  // Minutes
  changes: Array<{
    timestamp: Date;
    operation: 'A' | 'D' | 'PA' | 'PD';
    subject: string;
    predicate: string;
    object: string;
    graph: string;
    commit: string;
  }>;
  filters: {
    operations: Set<string>;
    graphs: Set<string>;
    subjects: Set<string>;
  };
}
```

---

### 2.6 Time Travel Comparison Modal

**Triggered when**: User clicks "Compare with Now" button

**Layout**:
```
┌─ Time Travel Comparison ────────────────────────────────┐
│                                                          │
│ Compare: [2025-11-10 10:30:00] ⟷ [2025-11-10 14:00:00] │
│          (Selected Time)          (Latest)              │
│                                                          │
│ Query: SELECT ?person ?age WHERE { ... }                │
│                                                          │
│ ┌─ Changes ───────────────────────────────────────────┐ │
│ │ Added (1):                                          │ │
│ │ • ex:Carol, age 25                                  │ │
│ │                                                     │ │
│ │ Modified (1):                                       │ │
│ │ • ex:Alice, age 30 → 31                            │ │
│ │                                                     │ │
│ │ Removed (1):                                        │ │
│ │ • ex:OldUser, age 40                               │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                          │
│ [Export Diff]                              [Close]      │
└──────────────────────────────────────────────────────────┘
```

**Components**:
- **Time Range Selector**: Shows two timestamps being compared
- **Query Display**: Shows the SPARQL query used
- **Changes Summary**: `<Accordion>` with three sections:
  - **Added**: Bindings present in later time but not earlier
  - **Modified**: Bindings with different values
  - **Removed**: Bindings present in earlier time but not later
- **Export Button**: Download diff as JSON, CSV, or RDF Patch

---

### 2.7 Timeline Animation Controls

**Layout**:
```
┌─ Animation ────────────────────────────────────────────┐
│ [⏸ Pause]  [⏮ Previous]  [⏭ Next]  [Speed: 2x ▾]      │
│                                                        │
│ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬●▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬  45%                    │
│                                                        │
│ 10:30:00 / 14:00:00                                    │
└────────────────────────────────────────────────────────┘
```

**Shown when**: User clicks "Play" button in timeline sidebar

**Components**:
- **Pause Button**: Stop animation
- **Previous/Next Buttons**: Jump to previous/next commit
- **Speed Selector**: Adjust playback speed
- **Progress Bar**: `<ProgressIndicator>` - Shows animation progress
- **Time Display**: Current time / End time

**Animation Behavior**:
1. Start at selected time
2. Advance to next commit in timeline
3. Execute query at new time
4. Update results panel
5. Wait N seconds (based on speed)
6. Repeat until reaching latest commit or user pauses

---

## 3. Interaction Flows

### 3.1 Basic Time Travel Query

1. User navigates to `/time-travel`
2. View loads with default time: Latest commit (now)
3. User enters query: `SELECT ?s ?p ?o WHERE { ?s ?p ?o . } LIMIT 100`
4. User clicks time picker, selects "2025-11-09 18:00:00"
5. Commit info updates: "Branch: main @ commit 019xyz..."
6. User clicks "Execute at Selected Time"
7. Results load: Data as it existed at 18:00 yesterday
8. Results header shows: "ℹ️ Queried at: 2025-11-09T18:00:00Z (commit 019xyz...)"

**Note**: When user selects a timestamp, the server resolves it to a
commit (e.g., "18:00:00" → commit 019xyz...). The query results show
which commit was used for transparency.

### 3.2 Timeline Animation

1. User clicks "Play ▶" button in timeline sidebar
2. Animation controls appear at top of timeline
3. Timeline starts advancing through commits (1 per second at 1x speed)
4. For each commit:
   - Timeline marker highlights current commit
   - Query auto-executes at that commit time
   - Results panel updates
   - Change history updates to show ± 1 hour around current time
5. User clicks "Pause" to stop at interesting point
6. User clicks "Previous" to go back one commit
7. User adjusts speed to 5x, clicks "Play" to resume
8. Animation reaches latest commit, automatically stops

### 3.3 Compare with Now

1. User queries at historical time: 2025-11-09 12:00
2. Results show 3 people: Alice (30), Bob (28), Carol (25)
3. User clicks "Compare with Now" button
4. Modal opens showing comparison
5. Diff shows:
   - Modified: Alice age 30 → 31
   - Added: David age 35
   - Removed: (none)
6. User clicks "Export Diff" → Downloads JSON:
   ```json
   {
     "from": "2025-11-09T12:00:00Z",
     "to": "2025-11-10T14:00:00Z",
     "added": [{"person": "ex:David", "age": 35}],
     "modified": [{"person": "ex:Alice", "age": {"from": 30, "to": 31}}],
     "removed": []
   }
   ```

### 3.4 Filter Timeline by Graph

1. User wants to see only commits affecting "ex:users" graph
2. User expands "Graphs" filter in timeline sidebar
3. User checks "ex:users" checkbox
4. Timeline updates to show only commits with changes to ex:users
5. Other commits are hidden (grayed out)
6. User clicks commit marker → Query executes at that time
7. Change history shows only changes to ex:users graph

### 3.5 Navigate from Version Control

**Integration with Version Control view**:

1. User is in Version Control view (`/version`)
2. User double-clicks commit in commit graph
3. Redirected to `/time-travel?asOf={commitTimestamp}`
4. Time Travel view loads with:
   - Time picker set to commit timestamp
   - Timeline scrolled to that commit
   - Default query pre-filled: `SELECT * WHERE { ?s ?p ?o } LIMIT 100`
5. User executes query to see data at that point in time

---

## 4. State Management

### 4.1 Global State (Svelte Stores)

```typescript
// stores/timeTravel.ts
export interface TimeTravelState {
  selectedTime: Date;
  correspondingCommit: string;
  timeline: Timeline;
  query: string;
  results: QueryResults | null;
  changeHistory: Change[];
  isAnimating: boolean;
  animationSpeed: number;
}

export const timeTravelState = writable<TimeTravelState>({
  selectedTime: new Date(),
  correspondingCommit: '',
  timeline: { commits: [], filters: {} },
  query: 'SELECT * WHERE { ?s ?p ?o } LIMIT 100',
  results: null,
  changeHistory: [],
  isAnimating: false,
  animationSpeed: 1
});
```

### 4.2 API Service

```typescript
// services/TimeTravelService.ts
class TimeTravelService {
  async queryAtTime(
    query: string,
    dataset: string,
    asOf: string  // ISO 8601 timestamp
  ): Promise<QueryResults> {
    const url = `/query?dataset=${dataset}&asOf=${encodeURIComponent(asOf)}`;
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/sparql-query' },
      body: query
    });
    return response.json();
  }

  async getCommitAtTime(dataset: string, asOf: string): Promise<Commit> {
    const url = `/version/history?dataset=${dataset}&asOf=${asOf}&limit=1`;
    const response = await fetch(url);
    const history = await response.json();
    return history.commits[0];
  }

  async getChangeHistory(
    dataset: string,
    centerTime: string,
    rangeMins: number
  ): Promise<Change[]> {
    const url = `/version/history?dataset=${dataset}` +
      `&after=${this.subtractMinutes(centerTime, rangeMins)}` +
      `&before=${this.addMinutes(centerTime, rangeMins)}`;
    const response = await fetch(url);
    const history = await response.json();
    return this.parseChangesFromHistory(history);
  }

  async compareTimeRanges(
    query: string,
    dataset: string,
    time1: string,
    time2: string
  ): Promise<Comparison> {
    // Execute query at both times
    const results1 = await this.queryAtTime(query, dataset, time1);
    const results2 = await this.queryAtTime(query, dataset, time2);

    // Compute diff
    return this.computeResultsDiff(results1, results2);
  }
}
```

---

## 5. Advanced Features

### 5.1 Time Range Queries

**Aggregate changes over time range**:

```
┌─ Time Range Query ─────────────────────────────────────┐
│ Query Type: [Changes Over Time ▾]                      │
│                                                        │
│ Start Time: [2025-11-09 ⏰ 12:00:00]                   │
│ End Time:   [2025-11-10 ⏰ 12:00:00]                   │
│                                                        │
│ Aggregation: [Count changes per hour ▾]                │
│                                                        │
│ [Execute Range Query]                                  │
└────────────────────────────────────────────────────────┘
```

**Results**:
```
┌─ Changes Over Time (Hourly) ───────────────────────────┐
│ Hour         Additions    Deletions    Modifications   │
│ ────────────────────────────────────────────────────── │
│ 12:00-13:00      15           3             7          │
│ 13:00-14:00       8           1             4          │
│ 14:00-15:00      22           5            12          │
│ ...                                                    │
└────────────────────────────────────────────────────────┘
```

**Chart Visualization**:
- Line chart showing changes over time
- X-axis: Time
- Y-axis: Number of changes
- Multiple lines: Additions (green), Deletions (red), Modifications (yellow)

### 5.2 Temporal SPARQL Extensions

**Support for temporal predicates**:

```sparql
# Find all resources that existed at specific time
SELECT ?s WHERE {
  ?s rdf:type ex:Person .
  FILTER EXISTS { ?s ?p ?o . }
  # Implicit: asOf parameter filters results
}

# Show value changes over time (hypothetical syntax)
SELECT ?s ?age ?timestamp WHERE {
  ?s ex:age ?age .
  BIND (TIMESTAMP() AS ?timestamp)  # Hypothetical temporal function
}
```

**Note**: Actual temporal SPARQL syntax depends on SPARQL 1.2 Protocol extension spec.

### 5.3 Bookmark Historical States

**Allow users to save interesting time points**:

```
┌─ Bookmarks ────────────────────────────────────────────┐
│ [+ Add Bookmark]                                        │
│                                                        │
│ 📌 Before merge (2025-11-09 18:00)                     │
│ 📌 After schema change (2025-11-10 10:30)              │
│ 📌 Production snapshot (2025-11-10 12:00)              │
└────────────────────────────────────────────────────────┘
```

**Features**:
- Click bookmark → Jump to that time
- Edit bookmark name and description
- Share bookmark URL with team (includes time + query)

### 5.4 Time Travel from Query Results

**Context menu in Query Workbench results**:

```
Right-click triple in results:
┌────────────────────────┐
│ Copy Value             │
│ Filter by Subject      │
│ Show Change History    │ ← NEW
│ Time Travel to Origin  │ ← NEW
└────────────────────────┘
```

**Show Change History**:
- Opens Change History panel
- Shows all commits that touched this triple
- Highlights add/modify/delete operations

**Time Travel to Origin**:
- Finds earliest commit containing this triple
- Navigates to Time Travel view at that commit time

---

## 6. Responsive Behavior

### Mobile (320px - 671px)

- **Timeline**: Horizontal swiper at top (instead of vertical sidebar)
- **Time Picker**: Full-screen modal overlay
- **Results**: Stacked card layout (not table)
- **Change History**: Collapsible accordion below results

### Tablet (672px - 1055px)

- **Timeline**: Collapsible sidebar (200px width)
- **Split View**: Query editor + results (vertical split)
- **Change History**: Bottom panel (collapsible)

---

## 7. Accessibility

### Keyboard Navigation

- **Ctrl+T**: Focus time picker
- **Ctrl+Enter**: Execute query at selected time
- **Ctrl+Space**: Play/Pause timeline animation
- **Ctrl+←/→**: Previous/Next commit in timeline
- **Ctrl+Shift+C**: Open comparison modal

### ARIA Labels

- **Time Picker**: `aria-label="Select time for historical query"`
- **Timeline Marker**: `aria-label="Commit {SHA} at {time}, {message}"`
- **Play Button**: `aria-label="Play timeline animation"`

### Screen Reader Announcements

- "Selected time: {time}. Commit: {SHA}."
- "Query executed at historical time. {N} results found."
- "Timeline animation started. Speed: {N}x."

---

## 8. Performance

### Optimization Strategies

1. **Lazy Load Timeline**: Load commits in chunks (100 at a time)
2. **Debounced Query**: Wait 500ms after time selection before auto-executing
3. **Cache Query Results**: Store results per (query, time) pair
4. **Virtual Timeline**: Render only visible commit markers (~50 at a time)

### Performance Targets

- **Load Timeline**: < 500ms for 1,000 commits
- **Execute Query**: < 500ms at historical time (same as current query performance)
- **Change History**: < 300ms to load ± 1 hour changes
- **Animation Frame Rate**: 60 FPS during timeline animation

---

## 9. Error Handling

### Error Scenarios

1. **No Commit at Time**: Selected time has no corresponding commit
   - Show warning: "No data exists at this time. Select a different time."
   - Suggest nearest commit time

2. **Query Timeout**: Historical query takes too long
   - Show progress indicator: "Loading historical data..."
   - Offer cancel button

3. **Invalid Time Range**: Start time after end time in comparison
   - Show inline error: "Start time must be before end time."

---

## 10. Testing Considerations

### Unit Tests

- Time selection logic (snap to nearest commit)
- Timeline filtering (by graph, operation type)
- Comparison diff computation (added, modified, removed)

### Integration Tests

- Query at historical time via API
- Load commit history and build timeline
- Execute comparison between two times

### E2E Tests (Playwright)

1. **Basic Time Travel Flow**:
   - Navigate to /time-travel
   - Select historical time
   - Execute query
   - Verify results show historical data

2. **Timeline Animation Flow**:
   - Click Play button
   - Verify timeline advances through commits
   - Verify results update for each commit
   - Click Pause
   - Verify animation stops

3. **Comparison Flow**:
   - Execute query at historical time
   - Click "Compare with Now"
   - Verify diff modal shows changes
   - Export diff

---

**Document Version**: 1.0
**Last Updated**: 2025-11-10
**Author**: Claude (AI Research Agent)
