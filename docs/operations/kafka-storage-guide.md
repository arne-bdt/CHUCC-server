# Kafka Storage & Data Management Guide

**Target Audience:** Junior developers and DevOps engineers with basic Kafka knowledge

**Last Updated:** 2025-10-10

## Table of Contents

1. [Overview](#overview)
2. [Kafka Topics & Configuration](#kafka-topics--configuration)
3. [Data Storage Architecture](#data-storage-architecture)
4. [RDF Patch Logic](#rdf-patch-logic)
5. [Event Replay & Re-hydration](#event-replay--re-hydration)
6. [Snapshots](#snapshots)
7. [Data Deletion & Lifecycle](#data-deletion--lifecycle)
8. [Memory Management](#memory-management)
9. [What Happens on Restart](#what-happens-on-restart)
10. [Potential Memory Issues](#potential-memory-issues)
11. [Configuration Examples](#configuration-examples)
12. [Troubleshooting](#troubleshooting)

---

## Overview

CHUCC Server uses **Apache Kafka** as its **source of truth** for all data changes. This is fundamentally different from traditional database-backed applications:

```
Traditional Architecture:
┌──────────────┐
│   Database   │ ← Source of truth
└──────────────┘

CHUCC Architecture:
┌──────────────┐
│    Kafka     │ ← Source of truth (event log)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Memory     │ ← Derived from Kafka (can be rebuilt)
└──────────────┘
```

**Key Concept**: Everything in memory (repositories, caches) can be **rebuilt from Kafka**. If the application crashes and loses all in-memory data, it can fully recover by replaying events from Kafka.

---

## Kafka Topics & Configuration

### Topic Naming Convention

CHUCC creates one Kafka topic per dataset using this template:

```
vc.{dataset}.events
```

**Examples**:
- Dataset `default` → Topic `vc.default.events`
- Dataset `mydata` → Topic `vc.mydata.events`
- Dataset `ontology-v2` → Topic `vc.ontology-v2.events`

### Topic Configuration

Each topic is created with these settings:

| Setting | Value | Purpose |
|---------|-------|---------|
| **Partitions** | 3 | Enables parallel processing |
| **Replication Factor** | 1 (dev), 3 (prod) | Fault tolerance |
| **Retention** | `-1` (infinite) | Keep events forever |
| **Cleanup Policy** | `delete` | Append-only (no compaction) |
| **Compression** | `snappy` | Reduce storage & network |
| **Min In-Sync Replicas** | 1 | Write availability |

### Dead Letter Queue (DLQ) Configuration

**Added:** 2025-10-28

CHUCC automatically creates DLQ topics for error handling:

| Topic Type | Naming Pattern | Retention | Purpose |
|------------|---------------|-----------|---------|
| **Event Topic** | `vc.{dataset}.events` | Infinite | Primary event stream |
| **DLQ Topic** | `vc.{dataset}.events.dlq` | 7 days | Failed/poison events |

**DLQ Topic Settings:**
```yaml
Partitions: Same as source topic (3)
Replication Factor: Same as source topic (1 dev, 3 prod)
Retention: 7 days (604800000 ms)
Cleanup Policy: delete (append-only)
```

**When Events Go to DLQ:**
1. Projection fails (e.g., malformed RDF patch, repository error)
2. Retry with exponential backoff (10 attempts over ~3 minutes)
3. After max retries, event sent to DLQ
4. Original topic processing continues (consumer not blocked)

**DLQ Operations:**

```bash
# List DLQ topics
kafka-topics.sh --list --bootstrap-server localhost:9092 | grep dlq

# Check DLQ messages
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic vc.default.events.dlq \
  --from-beginning

# Replay DLQ message (after fixing issue)
# 1. Fix application bug
# 2. Republish event to main topic (manual tooling required)
```

**Monitoring DLQ:**
- Health endpoint: `GET /actuator/health` (shows DLQ message count)
- Metrics: `chucc.projection.events.total{status="error"}` (error event rate)
- Alert when DLQ receives messages (operational issue)

### Configuration Files

**application.yml** (base settings):
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
  topic-template: vc.{dataset}.events
  partitions: 3
  replication-factor: 1
  retention-ms: -1         # -1 = infinite retention
  compaction: false        # Append-only
```

**application-dev.yml** (development):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: vc-server-dev
      auto-offset-reset: earliest    # Read from beginning on startup
```

**application-prod.yml** (production):
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
    consumer:
      group-id: vc-server-prod
      auto-offset-reset: earliest
    producer:
      acks: all              # Wait for all replicas
      retries: 3             # Retry on failure
```

### Key Kafka Settings Explained

**1. Infinite Retention (`retention-ms: -1`)**
- Events are **never deleted** from Kafka
- Enables complete audit trail
- Allows rebuilding state from scratch at any time
- **Warning**: Storage grows forever (plan for this!)

**2. No Log Compaction (`compaction: false`)**
- All events preserved in order
- Even if a branch is reset 100 times, all 100 events are kept
- **Why**: Need complete history for audit trail and time-travel queries

**3. Auto Offset Reset (`earliest`)**
- On first startup or offset loss, start reading from **beginning** of topic
- Critical for rebuilding state after crash
- Without this, new instances would miss historical events

**4. Consumer Group ID**
- All instances of the application share same group ID
- Kafka ensures each event processed by exactly one instance
- Enables horizontal scaling

---

## Data Storage Architecture

CHUCC uses a **hybrid storage model**:

```
┌─────────────────────────────────────────────────────────────┐
│                        CHUCC Server                          │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                   Kafka (Disk)                        │  │
│  │  Topic: vc.default.events                             │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │ Partition 0: [Event1, Event2, Event3, ...]     │  │  │
│  │  │ Partition 1: [Event4, Event5, ...]             │  │  │
│  │  │ Partition 2: [Event6, Event7, ...]             │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  │  ✅ Source of Truth                                    │  │
│  │  ✅ Durable (survives crashes)                         │  │
│  │  ✅ Append-only (immutable)                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                           │                                  │
│                           │ On Startup: Replay Events        │
│                           ▼                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              In-Memory Repositories                   │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ BranchRepository (ConcurrentHashMap)           │  │  │
│  │  │   Map<dataset, Map<branchName, Branch>>        │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ CommitRepository (ConcurrentHashMap)           │  │  │
│  │  │   Map<dataset, Map<commitId, Commit>>          │  │  │
│  │  │   Map<dataset, Map<commitId, RDFPatch>>        │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ DatasetService Cache (ConcurrentHashMap)       │  │  │
│  │  │   Map<dataset, Map<commitId, DatasetGraph>>    │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │  ⚠️  Derived Data (can be lost)                       │  │
│  │  ⚠️  Rebuilt from Kafka on startup                    │  │
│  │  ⚠️  NOT persisted to disk                            │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### What's Stored Where

**Kafka (Persistent - Disk)**:
- ✅ All events (BranchCreated, CommitCreated, BranchReset, etc.)
- ✅ RDF Patches (changes to graphs)
- ✅ Commit metadata (author, message, timestamp, parents)
- ✅ Snapshot events (full dataset states - if enabled)
- ✅ **Never deleted** (infinite retention)

**In-Memory Repositories (Volatile - RAM)**:
- ⚠️ Branch pointers (which commit each branch points to)
- ⚠️ Commit objects (metadata + RDF patches)
- ⚠️ Materialized dataset graphs (current state)
- ⚠️ **Lost on restart** (rebuilt from Kafka)

**Nothing is Stored on Local Disk** (except Kafka's own data):
- ❌ No database files
- ❌ No persistent cache
- ❌ No checkpoints or snapshots on disk

---

## RDF Patch Logic

### What is an RDF Patch?

An **RDF Patch** is a textual representation of **changes** to an RDF graph. Instead of storing full graphs, CHUCC stores deltas (diffs).

**Example**: Adding triples to a graph

```turtle
# Original graph (empty)

# Add operation (RDF Patch)
A <http://example.org/subject1> <http://example.org/predicate1> <http://example.org/object1> .
A <http://example.org/subject2> <http://example.org/predicate2> "literal value" .

# Resulting graph
<http://example.org/subject1> <http://example.org/predicate1> <http://example.org/object1> .
<http://example.org/subject2> <http://example.org/predicate2> "literal value" .
```

**Example**: Deleting and adding triples

```turtle
# Original graph
<http://example.org/s1> <http://example.org/p1> "old value" .

# RDF Patch (delete old, add new)
D <http://example.org/s1> <http://example.org/p1> "old value" .
A <http://example.org/s1> <http://example.org/p1> "new value" .

# Resulting graph
<http://example.org/s1> <http://example.org/p1> "new value" .
```

**RDF Patch Format**: See [RDF Patch Specification](https://afs.github.io/rdf-patch/)

### How Patches are Stored in Kafka

Each commit event contains:
1. **Commit metadata**: ID, author, message, timestamp, parent IDs
2. **RDF Patch**: Text representation of changes

**Example CommitCreatedEvent in Kafka**:
```json
{
  "eventType": "CommitCreated",
  "dataset": "default",
  "commitId": "01932c5c-8f7a-7890-b123-456789abcdef",
  "parents": ["01932c5c-7890-1234-5678-9abcdef01234"],
  "branch": "main",
  "author": "alice@example.com",
  "message": "Update product catalog",
  "timestamp": "2025-10-10T10:30:00Z",
  "rdfPatch": "A <http://example.org/product123> <http://example.org/price> \"29.99\" .\nA <http://example.org/product123> <http://example.org/inStock> \"true\" .\n"
}
```

### How State is Rebuilt from Patches

To get the current state of a graph, CHUCC:

1. **Walks commit history backwards** to find all ancestor commits
2. **Applies patches in chronological order**:
   ```
   Empty graph → Patch 1 → Patch 2 → ... → Patch N = Current state
   ```

**Example** (3 commits):
```
Commit 1 (initial):
  Patch: A <s1> <p1> <o1> .
  Graph: { <s1> <p1> <o1> }

Commit 2 (add more):
  Patch: A <s2> <p2> <o2> .
  Graph: { <s1> <p1> <o1>, <s2> <p2> <o2> }

Commit 3 (delete + add):
  Patch: D <s1> <p1> <o1> .
         A <s3> <p3> <o3> .
  Graph: { <s2> <p2> <o2>, <s3> <p3> <o3> }
```

**Implementation** (simplified):
```java
DatasetGraph graph = new DatasetGraphInMemory();

// Get commit and its ancestors
List<Commit> history = getCommitHistory(commitId);

// Apply patches in order
for (Commit commit : history) {
  RDFPatch patch = commitRepository.getPatch(commit.id());
  RDFPatchOps.applyChange(graph, patch);  // Modify graph in-place
}

return graph;  // Current state
```

---

## Event Replay & Re-hydration

### What is Event Replay?

**Event replay** is the process of **rebuilding all in-memory state** by reading and processing events from Kafka.

### When Does Replay Happen?

**Automatically on startup**:
- Application starts
- `ReadModelProjector` component starts
- Kafka consumer begins reading from **earliest offset**
- All events consumed and processed in order
- Repositories populated

**Flow**:
```
Application Startup
  ↓
ReadModelProjector starts
  ↓
Subscribe to topic pattern: vc.*.events
  ↓
Kafka: Find all topics matching pattern
  ↓
For each topic: Start reading from offset 0 (earliest)
  ↓
For each event:
  - Parse event
  - Update repository (BranchRepository, CommitRepository)
  - Apply patch to DatasetService cache
  ↓
Catch up to latest offset
  ↓
Application ready to serve requests
```

### How Replay Works

**Kafka Consumer Configuration**:
```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest  # Start from beginning
      group-id: vc-server-prod     # Consumer group
```

**ReadModelProjector** (event consumer):
```java
@Service
public class ReadModelProjector {

  @KafkaListener(
    topicPattern = "vc\\..*\\.events",           // Match all dataset topics
    groupId = "${spring.kafka.consumer.group-id}",
    autoStartup = "${projector.kafka-listener.enabled:true}"
  )
  public void handleEvent(VersionControlEvent event) {
    switch (event) {
      case CommitCreatedEvent e -> {
        // 1. Save commit metadata
        Commit commit = new Commit(e.commitId(), e.parents(), e.author(), ...);
        commitRepository.save(e.dataset(), commit, parsePatch(e.rdfPatch()));

        // 2. Update branch pointer
        if (e.branch() != null) {
          branchRepository.updateBranchHead(e.dataset(), e.branch(), e.commitId());
        }
      }
      case BranchCreatedEvent e -> {
        Branch branch = new Branch(e.branchName(), e.commitId());
        branchRepository.save(e.dataset(), branch);
      }
      case BranchResetEvent e -> {
        branchRepository.updateBranchHead(e.dataset(), e.branchName(), e.toCommitId());
      }
      // ... other event types
    }
  }
}
```

### Event Ordering Guarantees

**Kafka partitioning** ensures ordered processing:
- Events for same branch → same partition (by partition key)
- Events within partition processed in order
- Events across partitions may interleave

**Partition key strategy**:
```java
private String getPartitionKey(VersionControlEvent event) {
  return switch (event) {
    case BranchCreatedEvent e -> e.branchName();      // Branch events → same partition
    case BranchResetEvent e -> e.branchName();
    case CommitCreatedEvent e -> event.dataset();     // Commit events → dataset partition
    case SnapshotCreatedEvent e -> e.branchName();    // Snapshots → branch partition
    // ...
  };
}
```

**Why this matters**: Branch operations (create, reset, update) are processed in order, ensuring consistency.

### Performance of Replay

**Time to replay** depends on:
- Number of events (commits)
- Size of RDF patches
- Number of datasets/branches

**Example timings**:
| Events | Datasets | Replay Time |
|--------|----------|-------------|
| 100 | 1 | < 1 second |
| 1,000 | 5 | ~3 seconds |
| 10,000 | 10 | ~30 seconds |
| 100,000 | 50 | ~5 minutes |

**Note**: Replay is done once on startup. After catching up, only new events are processed (real-time).

---

## Snapshots

### What are Snapshots?

A **snapshot** is a **full copy** of a dataset at a specific commit, stored as an event in Kafka.

**Purpose**:
- Speed up recovery (don't need to replay all events)
- Reduce startup time for large datasets
- **Current status**: Created but not yet used for recovery (future enhancement)

### Snapshot Configuration

**application.yml**:
```yaml
vc:
  snapshots-enabled: true         # Enable/disable snapshots
  snapshot-interval: 100          # Create snapshot every N commits
```

### How Snapshots Work

**Triggering**:
```java
// After each commit
snapshotService.recordCommit(dataset, branch, commitId);

// If count % 100 == 0
if (commitCount % snapshotInterval == 0) {
  snapshotService.createSnapshotAsync(dataset, branch, commitId);
}
```

**Snapshot creation** (async):
```java
@Async("snapshotExecutor")
public void createSnapshotAsync(String dataset, String branch, CommitId commitId) {
  // 1. Materialize full dataset at this commit
  Dataset fullDataset = datasetService.getDataset(new DatasetRef(dataset, commitId));

  // 2. Serialize to N-Quads (text format)
  String nquads = serializeToNquads(fullDataset);

  // 3. Create snapshot event
  SnapshotCreatedEvent event = new SnapshotCreatedEvent(
    dataset, commitId, branch, Instant.now(), nquads
  );

  // 4. Publish to Kafka
  eventPublisher.publish(event);
}
```

**Example SnapshotCreatedEvent**:
```json
{
  "eventType": "SnapshotCreated",
  "dataset": "default",
  "commitId": "01932c5c-8f7a-7890-b123-456789abcdef",
  "branchName": "main",
  "timestamp": "2025-10-10T12:00:00Z",
  "nquads": "<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> <http://example.org/g1> .\n<http://example.org/s2> <http://example.org/p2> \"literal\" <http://example.org/g1> .\n..."
}
```

### Current Snapshot Behavior

**What happens now**:
- ✅ Snapshots are created every N commits
- ✅ Snapshots are published to Kafka
- ✅ Snapshots are logged by `ReadModelProjector`
- ❌ Snapshots are **NOT used** for faster recovery (yet)

**Future enhancement**: On startup, check for latest snapshot before commit, load snapshot, then replay events since snapshot.

---

## Data Deletion & Lifecycle

### What Happens When You Delete a Branch?

**Current status**: **Branch deletion is NOT IMPLEMENTED** (returns 501 Not Implemented)

**If it were implemented**, here's what would happen:

**HTTP Request**:
```http
DELETE /version/branches/feature-x
```

**Expected behavior** (not yet implemented):
1. Controller validates branch exists
2. Command handler creates `BranchDeletedEvent`
3. Event published to Kafka
4. Projector removes branch from `BranchRepository`
5. HTTP response: 204 No Content

**What would be stored/deleted**:

| Location | Action | Deletable? |
|----------|--------|------------|
| **Kafka** | `BranchDeletedEvent` added | ❌ Never deleted |
| **BranchRepository (memory)** | Branch object removed | ✅ Deleted |
| **CommitRepository (memory)** | Commits remain | ❌ NOT deleted |
| **DatasetService cache** | Cached graphs remain | ❌ NOT deleted |

**Key point**: **Commits are never deleted**, even if branch is deleted. This is intentional:
- Commits may be referenced by other branches
- Commits may be parent of other commits
- Complete audit trail preserved

### What Happens When You Delete a Dataset?

**Current status**: **Dataset deletion is NOT IMPLEMENTED**

**If it were implemented**, expected behavior:

1. Delete all branches in dataset
2. Clear in-memory repositories for that dataset
3. Do **NOT** delete Kafka topic or events

**What would be stored/deleted**:

| Location | Action | Deletable? |
|----------|--------|------------|
| **Kafka topic** | Remains unchanged | ❌ NOT deleted (design decision) |
| **All events** | Remain in Kafka | ❌ NOT deleted (audit trail) |
| **BranchRepository** | All branches removed | ✅ Deleted |
| **CommitRepository** | All commits removed | ✅ Deleted |
| **DatasetService cache** | All graphs removed | ✅ Deleted |

**Why keep Kafka topic?**
- Preserve audit trail
- Allow recovery if deletion was accidental
- Comply with regulations requiring data retention

**If you really need to delete**:
- Manually delete Kafka topic using Kafka admin tools
- **Warning**: This is irreversible and breaks audit trail!

### What Happens When You Delete a Graph (Named Graph)?

**This IS implemented**:

**HTTP Request**:
```http
DELETE /data?graph=http://example.org/graph1&branch=main
```

**What happens**:
1. Load current graph
2. Create RDF Patch that deletes all triples in graph
3. Create `CommitCreatedEvent` with delete patch
4. Publish event to Kafka
5. Return 204 No Content

**Example delete patch**:
```turtle
# RDF Patch: Delete all triples from graph
D <http://example.org/s1> <http://example.org/p1> <http://example.org/o1> <http://example.org/graph1> .
D <http://example.org/s2> <http://example.org/p2> "literal" <http://example.org/graph1> .
```

**What's stored/deleted**:

| Location | Action | Deletable? |
|----------|--------|------------|
| **Kafka** | New commit with delete patch | ❌ Event never deleted |
| **CommitRepository** | New commit added | ❌ Commit never deleted |
| **DatasetService cache** | Graph removed (triples deleted) | ✅ Triples removed |

**Key point**: Graph deletion creates a **new commit** (like `git rm`). The triples are removed from current state, but history shows they existed.

### Summary: What Can Be Deleted?

| Item | Can Delete? | Where Deleted | Notes |
|------|-------------|---------------|-------|
| **Kafka events** | ❌ Never | N/A | Infinite retention by design |
| **Kafka topics** | ⚠️ Manual only | Admin tools | Not recommended |
| **Branches (memory)** | ⚠️ Not implemented | Would be BranchRepository | Future feature |
| **Commits (memory)** | ❌ Never | N/A | Referenced by history |
| **Graphs (triples)** | ✅ Yes | Creates delete commit | Via DELETE /data |
| **Dataset cache** | ✅ Yes | Manual method | `datasetService.clearCache()` |

---

## Memory Management

### What's Stored in Memory?

**1. BranchRepository** (`Map<dataset, Map<branchName, Branch>>`):
- Size: Small (typically 1-10 branches per dataset)
- Growth: Bounded by number of branches
- Memory per branch: ~100 bytes

**2. CommitRepository** (`Map<dataset, Map<commitId, Commit + RDFPatch>>`):
- Size: Large (grows with commit history)
- Growth: Unbounded (no eviction)
- Memory per commit: ~1-10 KB (depends on patch size)

**3. DatasetService Cache** (`Map<dataset, Map<commitId, DatasetGraph>>`):
- Size: Largest consumer of memory
- Growth: Unbounded (no eviction)
- Memory per graph: ~100 KB - 10 MB (depends on dataset size)

### Memory Growth Examples

**Scenario 1: Small dataset, active development**
- 1 dataset, 2 branches, 1000 commits
- Memory usage:
  - Branches: 2 × 100 bytes = 200 bytes
  - Commits: 1000 × 5 KB = 5 MB
  - Cached graphs: ~10 graphs × 1 MB = 10 MB
  - **Total: ~15 MB** (very manageable)

**Scenario 2: Large dataset, long history**
- 1 dataset, 5 branches, 100,000 commits
- Memory usage:
  - Branches: 5 × 100 bytes = 500 bytes
  - Commits: 100,000 × 5 KB = 500 MB
  - Cached graphs: ~50 graphs × 5 MB = 250 MB
  - **Total: ~750 MB** (still okay for most servers)

**Scenario 3: Multiple large datasets**
- 10 datasets, 5 branches each, 10,000 commits each
- Memory usage:
  - Branches: 50 × 100 bytes = 5 KB
  - Commits: 100,000 × 5 KB = 500 MB
  - Cached graphs: ~100 graphs × 5 MB = 500 MB
  - **Total: ~1 GB** (need to watch this)

### Current Eviction Strategy

**Short answer**: **None**.

**Long answer**:
- `BranchRepository`: No eviction (branches rarely deleted)
- `CommitRepository`: No eviction (commits never deleted by design)
- `DatasetService`: No eviction, but manual `clearCache()` available

**Why no eviction?**
- Philosophy: "Premature optimization is the root of all evil"
- In-memory operations are fast enough for expected workloads
- No performance issues observed yet
- Simple code is better until data proves otherwise

**Monitoring**:
```bash
# Check cache size
curl http://localhost:8080/actuator/metrics/dataset.cache.size

# Check JVM memory
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

### Manual Cache Cleanup

**Clear cache for specific dataset**:
```java
datasetService.clearCache("mydata");
```

**Clear all caches**:
```java
datasetService.clearAllCaches();
```

**Note**: These methods are **not exposed via HTTP API** (would require custom endpoint).

---

## What Happens on Restart

### Startup Sequence

```
1. Application starts
   ↓
2. Spring Boot initializes beans
   ↓
3. Kafka admin connects to Kafka
   ↓
4. Topics discovered: vc.*.events
   ↓
5. ReadModelProjector starts @KafkaListener
   ↓
6. Kafka consumer subscribes to topic pattern
   ↓
7. Consumer starts reading from earliest offset
   ↓
8. FOR EACH EVENT:
     - Deserialize event
     - Update BranchRepository
     - Update CommitRepository
     - (DatasetService cache built on-demand)
   ↓
9. Consumer catches up to latest offset
   ↓
10. Application ready (HTTP endpoints enabled)
    ↓
11. Application continues processing new events in real-time
```

### What's Recovered

✅ **Fully recovered** from Kafka:
- All branches and their current HEAD commits
- All commits and their metadata
- All RDF patches

⚠️ **Built on-demand** (not pre-loaded):
- Materialized dataset graphs (cached on first query)

❌ **Lost forever** (not recoverable):
- Previous in-memory cache state
- Any data not stored in Kafka

### How Long Does Startup Take?

Depends on event count:

| Total Events | Startup Time |
|--------------|--------------|
| 100 | < 1 second |
| 1,000 | ~2 seconds |
| 10,000 | ~10 seconds |
| 100,000 | ~1 minute |
| 1,000,000 | ~10 minutes |

**Bottleneck**: Processing each event (applying patches, updating repositories)

**Future optimization**: Use snapshots to skip early events

### Monitoring Startup Progress

**Logs**:
```
INFO  ReadModelProjector - Received event: CommitCreated for dataset: default
INFO  ReadModelProjector - Received event: BranchCreated for dataset: default
...
INFO  KafkaMessageListenerContainer - partitions assigned: [vc.default.events-0]
```

**Check if caught up**:
```bash
# Check consumer lag (should be 0 or low when ready)
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group vc-server-prod --describe

# Output:
# TOPIC              PARTITION  CURRENT-OFFSET  LAG
# vc.default.events  0          1523            0     ← Caught up!
# vc.default.events  1          1489            0
# vc.default.events  2          1501            0
```

---

## Potential Memory Issues

### When Will Memory Run Out?

**Scenario 1: Too many commits**
- **Symptom**: `OutOfMemoryError: Java heap space`
- **Cause**: `CommitRepository` holding 1M+ commits with RDF patches
- **Calculation**: 1,000,000 commits × 5 KB = 5 GB
- **Solution**: Increase JVM heap (`-Xmx8g`) or implement commit eviction

**Scenario 2: Too many cached graphs**
- **Symptom**: `OutOfMemoryError: Java heap space`
- **Cause**: `DatasetService` caching 100+ large graphs
- **Calculation**: 100 graphs × 50 MB = 5 GB
- **Solution**: Increase heap or implement LRU eviction

**Scenario 3: Large RDF patches**
- **Symptom**: Slow replays, high memory during startup
- **Cause**: Commits with huge patches (e.g., 1 million triples)
- **Calculation**: 1000 commits × 50 MB = 50 GB
- **Solution**: Avoid bulk operations, use batch commits, increase heap

### Warning Signs

Monitor these metrics:

**1. JVM heap usage** (should stay below 80%):
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/jvm.memory.max
```

**2. Garbage collection frequency** (high GC = memory pressure):
```bash
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

**3. Cache size** (unbounded growth):
```bash
curl http://localhost:8080/actuator/metrics/dataset.cache.size
```

### Mitigation Strategies

**1. Increase JVM heap**:
```bash
java -Xmx4g -Xms2g -jar chucc-server.jar
```

**2. Use snapshots** (future):
- Enable snapshots to reduce replay time
- Smaller window of events to process

**3. Partition by time** (future):
- Rotate Kafka topics yearly (vc.default.2024.events, vc.default.2025.events)
- Only replay recent topics on startup

**4. Implement cache eviction** (future):
- Use Caffeine cache with max size + LRU eviction
- Evict old commits from `CommitRepository` (keep metadata, evict patches)

**5. External storage** (major change):
- Store commits in PostgreSQL instead of memory
- Use Kafka only for events
- Cache only hot data

### Recommended Heap Sizes

| Workload | Recommended Heap |
|----------|------------------|
| **Small** (< 10k commits, 1-2 datasets) | 512 MB - 1 GB |
| **Medium** (< 100k commits, 5-10 datasets) | 2 GB - 4 GB |
| **Large** (< 1M commits, 50+ datasets) | 8 GB - 16 GB |
| **Very Large** (> 1M commits) | ⚠️ Implement eviction first |

---

## Configuration Examples

### Development Setup (Local Kafka)

**application-dev.yml**:
```yaml
kafka:
  bootstrap-servers: localhost:9092
  topic-template: vc.{dataset}.events
  partitions: 1             # Single partition for simplicity
  replication-factor: 1
  retention-ms: -1

spring:
  kafka:
    consumer:
      group-id: vc-server-dev
      auto-offset-reset: earliest

logging:
  level:
    org.chucc.vcserver.projection: DEBUG  # Verbose projector logs
    org.apache.kafka: WARN
```

**Start Kafka** (Docker):
```bash
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  apache/kafka:latest
```

### Production Setup (Kafka Cluster)

**application-prod.yml**:
```yaml
kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}  # kafka-1:9092,kafka-2:9092,kafka-3:9092
  topic-template: vc.{dataset}.events
  partitions: 6             # More partitions for parallelism
  replication-factor: 3     # Fault tolerance
  retention-ms: -1

spring:
  kafka:
    consumer:
      group-id: vc-server-prod
      auto-offset-reset: earliest
      enable-auto-commit: true
      auto-commit-interval-ms: 1000
    producer:
      acks: all             # Wait for all replicas
      retries: 3
      compression-type: snappy

logging:
  level:
    org.chucc.vcserver.projection: INFO  # Less verbose
    org.apache.kafka: ERROR
```

**Environment variables**:
```bash
KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
SPRING_PROFILES_ACTIVE=prod
```

### High-Volume Setup (Optimize for Throughput)

**application.yml**:
```yaml
kafka:
  partitions: 12            # High parallelism

spring:
  kafka:
    consumer:
      max-poll-records: 500       # Process more events per poll
      fetch-min-size: 16384       # Batch fetches
    producer:
      batch-size: 32768           # Larger batches
      linger-ms: 10               # Wait 10ms to batch
      compression-type: lz4       # Faster compression

vc:
  snapshots-enabled: true
  snapshot-interval: 50           # More frequent snapshots
```

---

## Troubleshooting

### Problem: "Application slow to start"

**Symptoms**:
- Application takes 5+ minutes to start
- Logs show continuous event processing

**Diagnosis**:
```bash
# Check total events in Kafka
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic vc.default.events

# Output: vc.default.events:0:152389  ← High offset = many events
```

**Solutions**:
1. **Enable snapshots** (future): Skip early events
2. **Increase consumer throughput**: `max-poll-records: 1000`
3. **Increase heap**: `-Xmx8g` for faster processing
4. **Partition by time**: Create new topics for new data

---

### Problem: "OutOfMemoryError on startup"

**Symptoms**:
- Application crashes during event replay
- Error: `java.lang.OutOfMemoryError: Java heap space`

**Diagnosis**:
```bash
# Check event count
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group vc-server-prod --describe

# Check heap settings
java -XX:+PrintFlagsFinal -version | grep HeapSize
```

**Solutions**:
1. **Increase heap**: `java -Xmx8g -jar chucc-server.jar`
2. **Check for huge patches**: Look for commits with massive RDF changes
3. **Split datasets**: Use multiple smaller datasets instead of one large one

---

### Problem: "Events not being consumed"

**Symptoms**:
- Writes succeed (PUT returns 200)
- Reads show stale data
- Consumer lag increasing

**Diagnosis**:
```bash
# Check consumer is running
curl http://localhost:8080/actuator/health

# Check consumer lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group vc-server-prod --describe

# Expected:
# LAG = 0 (caught up)

# Problem:
# LAG = 1523 (falling behind!)
```

**Root causes**:
1. **Projector disabled**: Check `projector.kafka-listener.enabled=true`
2. **Kafka unreachable**: Check network, Kafka health
3. **Consumer crashed**: Check logs for exceptions
4. **Processing too slow**: Check for long-running event handlers

**Solutions**:
1. Enable projector if disabled
2. Fix Kafka connectivity
3. Check logs for exceptions in `ReadModelProjector`
4. Scale horizontally (add more instances)

---

### Problem: "Repository data lost after restart"

**Symptoms**:
- Application restarts
- All branches/commits gone
- HTTP 404 errors

**Diagnosis**:
- Check if Kafka topics exist
- Check if consumer is reading from earliest

**Root cause**:
- Kafka topics deleted
- Or: `auto-offset-reset: latest` (wrong!)

**Solution**:
```yaml
# Correct configuration
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest  # ← Must be earliest!
```

---

### Problem: "Disk full on Kafka broker"

**Symptoms**:
- Kafka writes failing
- Application can't publish events
- Error: `No space left on device`

**Root cause**:
- Infinite retention + many events = disk full

**Solutions**:
1. **Add more disk** to Kafka brokers
2. **Enable time-based retention** (if audit trail not required):
   ```yaml
   kafka:
     retention-ms: 2592000000  # 30 days
   ```
3. **Delete old topics** (if data no longer needed):
   ```bash
   kafka-topics.sh --bootstrap-server localhost:9092 \
     --delete --topic vc.old-dataset.events
   ```

---

## Summary

### Key Takeaways

1. **Kafka is the source of truth**
   - All events stored permanently (infinite retention)
   - In-memory data is derived (can be rebuilt)

2. **One topic per dataset**
   - Template: `vc.{dataset}.events`
   - 3 partitions, infinite retention, no compaction

3. **Event replay on startup**
   - Automatic: consumer reads from earliest offset
   - Rebuilds all repositories in memory
   - Time depends on event count

4. **RDF Patches**
   - Changes stored as diffs (not full graphs)
   - Applied sequentially to build state
   - Stored as text in commit events

5. **Snapshots (future)**
   - Created every N commits
   - Not yet used for recovery
   - Will speed up startup

6. **Deletions**
   - Branch/dataset deletion: NOT implemented
   - Graph deletion: Creates delete commit (implemented)
   - Kafka events: NEVER deleted

7. **Memory management**
   - No automatic eviction
   - Unbounded growth in repositories
   - Manual cleanup: `clearCache()`
   - Monitor heap usage

8. **On restart**
   - All in-memory data lost
   - Fully rebuilt from Kafka
   - Startup time: ~10 sec per 10k events

9. **Memory limits**
   - Plan for: ~5 KB per commit
   - Increase heap as needed
   - Implement eviction for large workloads

10. **Configuration**
    - Use `earliest` offset reset
    - Enable projector
    - Tune heap size

### When to Worry

⚠️ **Memory**:
- JVM heap usage > 80%
- Frequent OutOfMemoryErrors
- GC taking > 10% CPU time

⚠️ **Performance**:
- Startup taking > 2 minutes
- Query latency > 500ms
- Consumer lag > 1000

⚠️ **Storage**:
- Kafka disk usage > 80%
- Growing at > 10 GB/day

### Next Steps

For further reading:
- [CQRS + Event Sourcing Guide](../architecture/cqrs-event-sourcing.md)
- [Performance Optimization](performance.md)
- [Architecture Overview](../architecture/README.md)

For help:
- Check logs in `logs/application.log`
- Monitor metrics at `/actuator/metrics`
- Check Kafka consumer groups
