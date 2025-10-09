# C4 Model - Level 1: System Context

**System Context diagram showing CHUCC Server and its external dependencies**

## Overview

This document describes the **System Context** (C4 Level 1) - the highest level view of CHUCC Server showing the system boundary and external actors/systems it interacts with.

---

## System Context Diagram (Textual)

```
┌────────────────────────────────────────────────────────────────┐
│                                                                │
│   ┌──────────────────────────────────────────────────────┐   │
│   │                                                        │   │
│   │  RDF Client Applications                              │   │
│   │  (SPARQL tools, semantic web apps, data pipelines)   │   │
│   │                                                        │   │
│   └───────────────────┬────────────────────────────────────┘   │
│                       │                                        │
│                       │ HTTP/HTTPS                             │
│                       │ (SPARQL Protocol +                     │
│                       │  Graph Store Protocol +                │
│                       │  Version Control Extension)            │
│                       │                                        │
│                       ▼                                        │
│   ┌─────────────────────────────────────────────────────┐    │
│   │                                                       │    │
│   │              CHUCC Server                             │    │
│   │                                                       │    │
│   │  SPARQL 1.2 Protocol + Version Control Extension     │    │
│   │  • Graph Store Protocol (CRUD on RDF graphs)         │    │
│   │  • Version Control (branches, tags, commits)         │    │
│   │  • Time-travel queries (asOf selector)               │    │
│   │  • CQRS + Event Sourcing architecture                │    │
│   │                                                       │    │
│   └────────┬──────────────────────────────┬───────────────┘    │
│            │                              │                    │
│            │ Publishes events             │ Stores events     │
│            │ (Kafka Producer)             │ (Kafka Topics)    │
│            │                              │                    │
│            ▼                              ▼                    │
│   ┌─────────────────────────────────────────────────────┐    │
│   │                                                       │    │
│   │              Apache Kafka                             │    │
│   │                                                       │    │
│   │  • Event Store (source of truth)                     │    │
│   │  • Message Bus (async event delivery)                │    │
│   │  • Topics: <dataset>-events                          │    │
│   │  • Partition key: dataset name                       │    │
│   │  • Retention: infinite (event sourcing)              │    │
│   │                                                       │    │
│   └───────────────────────────────────────────────────────┘    │
│                                                                │
│  Internet / Organization Network                              │
└────────────────────────────────────────────────────────────────┘
```

---

## Actors and External Systems

### 1. RDF Client Applications (Person/System)

**Type**: External Actor (Person using system)

**Description**:
Any client application or user that needs to store, query, and version control RDF data. This includes:
- SPARQL query tools (e.g., Apache Jena command-line tools)
- Semantic web applications
- Data integration pipelines
- Knowledge graph management systems
- Research data management systems
- Collaborative ontology editors

**Relationship to CHUCC Server**:
- **Uses**: HTTP/HTTPS protocol
- **Protocols**: SPARQL 1.2 Protocol, Graph Store Protocol, Version Control Extension
- **Operations**:
  - Query RDF data (GET /sparql)
  - Update RDF data (POST /sparql)
  - Manage graphs (PUT/POST/DELETE/PATCH /data)
  - Manage branches (POST /version/branches, PUT /version/branches/{name})
  - Create tags (POST /version/tags)
  - Merge branches (POST /version/merge)
  - Cherry-pick commits (POST /version/cherry-pick)
  - Query history (GET /version/history)
  - Time-travel queries (?asOf=timestamp)

**Key Needs**:
- Reliable RDF storage with Git-like version control
- Collaborative editing with conflict detection
- Historical queries (time-travel)
- Branching and merging workflows
- Atomic operations on multiple graphs

**Example Use Cases**:
1. **Research Data Management**: Scientists collaboratively curate knowledge graphs with version control
2. **Ontology Development**: Teams develop ontologies with branching and merging
3. **Data Integration**: Pipelines load RDF data with commit history
4. **Semantic Web Applications**: Apps query versioned RDF data with time-travel

---

### 2. Apache Kafka (External System)

**Type**: External System (Data Store + Message Bus)

**Description**:
Distributed event streaming platform that serves dual roles:
1. **Event Store**: Persistent, append-only log of all system events
2. **Message Bus**: Asynchronous event delivery to internal projectors

**Relationship to CHUCC Server**:
- **CHUCC Server → Kafka (Producer)**:
  - Publishes events when state changes occur
  - Topics: One per dataset (`default-events`, `research-events`, etc.)
  - Partition key: Dataset name (ensures ordering per dataset)
  - Message format: JSON-serialized events

- **Kafka → CHUCC Server (Consumer)**:
  - ReadModelProjector consumes events to update read models
  - Consumer group: `chucc-server-projector`
  - Offset management: Auto-commit after successful projection
  - Error handling: Retry with exponential backoff

**Key Configuration**:
- **Topics**: Created dynamically per dataset
- **Partitions**: Configurable (default: 3)
- **Replication**: Configurable (default: 1 for development)
- **Retention**: Infinite (`retention.ms=-1`) - Event sourcing requires full history
- **Compression**: GZIP for efficiency
- **Idempotence**: Enabled for exactly-once semantics

**Event Types Stored** (10 event types):
1. `CommitCreatedEvent` - New commit with RDF patch
2. `BranchCreatedEvent` - New branch created
3. `BranchResetEvent` - Branch pointer moved
4. `BranchRebasedEvent` - Branch rebased onto new base
5. `TagCreatedEvent` - Immutable tag created
6. `CherryPickedEvent` - Commit cherry-picked to branch
7. `CommitsSquashedEvent` - Multiple commits combined
8. `RevertCreatedEvent` - Commit reverted
9. `MergedEvent` - Branches merged
10. `SnapshotCreatedEvent` - Dataset snapshot created

**Why Kafka?**:
- **Durability**: Events survive server restarts
- **Auditability**: Complete history of all changes
- **Replayability**: Rebuild state from events
- **Scalability**: Horizontal scaling with partitions
- **Decoupling**: Write side independent of read side

**Alternatives Considered**:
- **PostgreSQL Event Store**: Simpler but less scalable
- **EventStoreDB**: Specialized but adds dependency
- **In-Memory Only**: Not durable, chosen Kafka for durability

---

## System: CHUCC Server

**Type**: Software System

**Full Name**: Collaborative Hub for Unified Content Control

**Description**:
A version-controlled RDF graph store implementing SPARQL 1.2 Protocol with Git-like operations. Enables collaborative editing of semantic data with branching, merging, conflict detection, and time-travel queries.

### Core Capabilities

**1. Graph Store Protocol (GSP)**
- Create/replace graphs (PUT)
- Merge triples into graphs (POST)
- Delete graphs (DELETE)
- Apply RDF Patches (PATCH)
- Query graphs (GET)
- Check graph existence (HEAD)
- Discover capabilities (OPTIONS)
- Batch operations (POST /version/batch-graphs)

**2. Version Control**
- Branch management (create, list, get, reset)
- Tag management (create, list, get - immutable)
- Commit history (list with filters, pagination)
- Merge operations (three-way merge with conflict detection)
- Revert (undo commit with inverse patch)
- Cherry-pick (apply commit to different branch)
- Squash (combine multiple commits)
- Rebase (replay commits on new base)

**3. SPARQL Protocol**
- Query execution (GET /sparql) - Partially implemented
- Update execution (POST /sparql) - Not implemented
- Selector support (branch, commit, asOf)
- Content negotiation (JSON, XML, CSV, TSV)

**4. Time-Travel**
- Query historical states with `asOf` selector
- Inclusive semantics (commit time ≤ timestamp)
- Millisecond precision
- UUIDv7 commit ID ordering for efficiency

### Architectural Style

**CQRS + Event Sourcing**:
- **Commands** create events (write model)
- **Events** stored in Kafka (source of truth)
- **Projectors** update repositories (read model)
- **Queries** read from repositories (fast reads)

**Benefits**:
- Complete audit trail
- Time-travel capabilities
- Horizontal scalability
- Separation of concerns
- Eventual consistency

### Technology Stack

- **Platform**: Java 21 + Spring Boot 3.5
- **RDF Processing**: Apache Jena 5.5
- **Event Store**: Apache Kafka
- **Event Format**: RDFPatch (W3C format)
- **Storage**: In-memory (DatasetGraphInMemory)
- **API**: REST (HTTP/JSON)

### Deployment Context

**Typical Deployment**:
- Container (Docker) or JAR
- Single instance or cluster
- External Kafka cluster
- Port: 8080 (HTTP)
- Memory: 2-4 GB (depends on dataset size)

**Scaling**:
- Horizontal: Multiple instances (need external cache for coordination)
- Vertical: Increase memory for larger datasets
- Kafka: Scale partitions for write throughput

---

## Key Interactions

### 1. Client → CHUCC Server → Kafka (Write Flow)

```
Client                  CHUCC Server              Kafka
  │                          │                      │
  │  PUT /data?branch=main   │                      │
  ├─────────────────────────>│                      │
  │                          │                      │
  │                          │ 1. Validate request  │
  │                          │ 2. Compute RDF diff  │
  │                          │ 3. Create event      │
  │                          │                      │
  │                          │ Publish event        │
  │                          ├─────────────────────>│
  │                          │                      │
  │                          │ Ack (async)          │
  │                          │<─────────────────────┤
  │                          │                      │
  │  200 OK + ETag           │                      │
  │<─────────────────────────┤                      │
  │                          │                      │
  │                          │  [Later: Projector   │
  │                          │   consumes event]    │
  │                          │<─────────────────────┤
  │                          │                      │
```

**Key Points**:
- HTTP response returns IMMEDIATELY after publishing event
- Repository updates happen ASYNCHRONOUSLY
- Client gets ETag for optimistic concurrency control

### 2. Client → CHUCC Server (Read Flow)

```
Client                  CHUCC Server
  │                          │
  │  GET /data?branch=main   │
  ├─────────────────────────>│
  │                          │
  │                          │ 1. Resolve selector
  │                          │    (branch → commitId)
  │                          │ 2. Query repository
  │                          │ 3. Serialize RDF
  │                          │
  │  200 OK + ETag + RDF     │
  │<─────────────────────────┤
  │                          │
```

**Key Points**:
- Reads are SYNCHRONOUS
- Query materialized read model (fast)
- ETag allows conditional requests

### 3. CHUCC Server ↔ Kafka (Event Sourcing)

**Publishing Events (Write Side)**:
```java
// Command handler creates event
CommitCreatedEvent event = new CommitCreatedEvent(...);

// Publish to Kafka (async)
CompletableFuture<RecordMetadata> future = eventPublisher.publish(event);

// Event stored in topic: "default-events"
// Partition: hash(dataset) % partitions
// Key: dataset name
// Value: JSON-serialized event
```

**Consuming Events (Read Side)**:
```java
// ReadModelProjector consumes from Kafka
@KafkaListener(topics = "#{'${kafka.topic-prefix}' + '-events'}")
public void handleCommitCreatedEvent(CommitCreatedEvent event) {
  // Update repositories (read model)
  commitRepository.save(event.dataset(), commit, patch);
  branchRepository.updateBranchHead(event.dataset(), branch, commitId);
  datasetGraphRepository.buildGraph(event.dataset(), commitId);
}
```

**Event Replay**:
- On startup: Consume all events from beginning
- Rebuild state: Apply events in order
- Result: In-memory repositories match event log

---

## External Dependencies

### Required External Systems

1. **Apache Kafka** (Required)
   - Version: 3.6+
   - Purpose: Event store and message bus
   - Deployment: Can run locally (development) or as cluster (production)
   - Configuration: Bootstrap servers in `application.yml`

### Optional External Systems

None - System is self-contained once Kafka is available.

### External Protocols

1. **SPARQL 1.2 Protocol** (W3C Specification)
   - Query: GET /sparql
   - Update: POST /sparql
   - Selectors: branch, commit, asOf

2. **Graph Store Protocol** (W3C Specification)
   - CRUD operations on RDF graphs
   - Endpoint: /data

3. **Version Control Extension** (Custom Specification)
   - Git-like operations on RDF data
   - Endpoints: /version/*
   - See: `.claude/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md`

4. **RFC 7807 Problem Details** (Error Handling)
   - All errors return application/problem+json
   - Structured error responses

---

## System Boundaries

### Inside System Boundary (Owned by CHUCC Server)
- HTTP endpoints and controllers
- Command handlers and business logic
- Event publishing
- Event consumption and projection
- In-memory repositories
- RDF processing (parsing, serialization, diff)
- Version control logic (merge, conflict detection)

### Outside System Boundary (External)
- Client applications
- Apache Kafka cluster
- Network infrastructure
- Monitoring/logging systems (future)

### Crossing Boundary (Interactions)
- HTTP requests/responses (Client ↔ CHUCC)
- Kafka events (CHUCC → Kafka → CHUCC)

---

## Quality Attributes

### Reliability
- **Durability**: Events persisted in Kafka survive restarts
- **Consistency**: Eventual consistency via event sourcing
- **Idempotence**: Duplicate events handled safely

### Performance
- **Write Latency**: ~10-50ms (includes Kafka publish)
- **Read Latency**: ~1-10ms (in-memory reads)
- **Throughput**: Depends on Kafka configuration

### Scalability
- **Horizontal**: Add instances, share Kafka topics
- **Vertical**: Increase memory for larger datasets
- **Partitioning**: Kafka partitions per dataset

### Security
- **Authentication**: Not implemented (future: OAuth2/JWT)
- **Authorization**: Not implemented (future: ACLs)
- **Transport**: HTTP (future: HTTPS recommended)

### Maintainability
- **Event Sourcing**: Complete audit trail aids debugging
- **CQRS**: Separate read/write concerns
- **Testing**: 823 tests with proper isolation

---

## Constraints

### Technical Constraints
1. **In-Memory Storage**: Dataset size limited by JVM heap
2. **Single Dataset**: Currently "default" dataset hardcoded
3. **Kafka Required**: System depends on external Kafka
4. **Java 21**: Requires Java 21+ runtime

### Business Constraints
1. **Event Retention**: Events stored forever (storage cost)
2. **Eventual Consistency**: Reads may lag writes slightly
3. **No Authentication**: Currently open to all clients

### Deployment Constraints
1. **JVM Required**: Cannot run in environments without Java
2. **Network Access**: Clients need HTTP access
3. **Kafka Access**: Server needs network access to Kafka

---

## Future External Systems

### Planned Integrations
1. **Prometheus**: Metrics export (future)
2. **Grafana**: Monitoring dashboards (future)
3. **OAuth2 Provider**: Authentication (future)
4. **External Database**: Optional persistence for snapshots (future)

---

## References

- [C4 Model Website](https://c4model.com/)
- [Level 2: Container Diagram](c4-level2-container.md)
- [Level 3: Component Diagram](c4-level3-component.md)
- [Architecture Overview](README.md)

---

## Summary

**CHUCC Server** is a self-contained system that:
- Accepts HTTP requests from RDF clients
- Publishes events to Apache Kafka
- Consumes its own events to update read models
- Provides Git-like version control for RDF data

**Key External Dependencies**:
- Apache Kafka (required) - Event store and message bus

**System Boundary**:
- Inside: HTTP API, business logic, event processing, in-memory storage
- Outside: Clients, Kafka, network infrastructure
