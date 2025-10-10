# C4 Model - Level 2: Container

**Container diagram showing the high-level technology choices and how containers communicate**

## Overview

This document describes the **Container View** (C4 Level 2) - showing the major technology building blocks (containers) that make up CHUCC Server and how they interact.

**Note**: In C4 terminology, a "container" is a separately runnable/deployable unit (application, database, file system, etc.), NOT a Docker container.

---

## Container Diagram (Textual)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  RDF Client Applications (Browser, CLI tools, Semantic Web Apps)            │
│                                                                             │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │
                              │ HTTP/HTTPS
                              │ (REST API)
                              │ • SPARQL Protocol
                              │ • Graph Store Protocol
                              │ • Version Control API
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                          CHUCC Server Container                             │
│                            (Spring Boot Application)                        │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Web Layer (Spring MVC)                                               │  │
│  │  • GraphStoreController                                               │  │
│  │  • SparqlController                                                   │  │
│  │  • BranchController, TagController, CommitController                  │  │
│  │  • MergeController, AdvancedOpsController                             │  │
│  │  • Error handling (RFC 7807 Problem Details)                          │  │
│  │  • Content negotiation (Turtle, JSON-LD, N-Triples, RDF/XML)          │  │
│  │                                                                       │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                      │
│                                      │ Commands                             │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Command Side (Write Model) - CQRS                                    │  │
│  │  • Command Handlers (business logic)                                  │  │
│  │  • Domain Services (RdfDiffService, ConflictDetectionService, etc.)   │  │
│  │  • Event creation and publishing                                      │  │
│  │  • Validation and business rules                                      │  │
│  │                                                                       │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                      │
│                                      │ Events (async)                       │
│                                      │                                      │
│  ┌───────────────────────────────────┴───────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Event Publishing (Kafka Producer)                                    │  │
│  │  • EventPublisher                                                     │  │
│  │  • JSON serialization                                                 │  │
│  │  • Async publish with CompletableFuture                               │  │
│  │  • Partition by dataset name                                          │  │
│  │                                                                       │  │
│  └─────────────────────────────────┬─────────────────────────────────────┘  │
│                                    │                                        │
│                                    │                                        │
└────────────────────────────────────┼────────────────────────────────────────┘
                                     │ Kafka Protocol
                                     │ (Producer API)
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                     Apache Kafka Cluster                                    │
│                     (Event Store + Message Bus)                             │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Topics (per dataset)                                               │    │
│  │  • default-events (partition key: dataset name)                     │    │
│  │  • research-events                                                  │    │
│  │  • <dataset>-events                                                 │    │
│  │                                                                     │    │
│  │  Event Types:                                                       │    │
│  │  • CommitCreatedEvent, BranchCreatedEvent, TagCreatedEvent          │    │
│  │  • MergedEvent, RevertCreatedEvent, CherryPickedEvent               │    │
│  │  • CommitsSquashedEvent, BranchRebasedEvent, BranchResetEvent       │    │
│  │  • SnapshotCreatedEvent                                             │    │
│  │                                                                     │    │
│  │  Configuration:                                                     │    │
│  │  • Retention: Infinite (event sourcing)                             │    │
│  │  • Replication: Configurable (default 1 for dev)                    │    │
│  │  • Partitions: Configurable (default 3)                             │    │
│  │  • Compression: GZIP                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ Kafka Protocol
                                      │ (Consumer API)
                                      │
┌─────────────────────────────────────┴───────────────────────────────────────┐
│                          CHUCC Server Container                             │
│                            (Spring Boot Application)                        │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Event Consumption (Kafka Consumer)                                   │  │
│  │  • ReadModelProjector (@KafkaListener)                                │  │
│  │  • 10 event handlers (one per event type)                             │  │
│  │  • JSON deserialization                                               │  │
│  │  • Consumer group: chucc-server-projector                             │  │
│  │  • Configurable auto-start (disabled in tests)                        │  │
│  │                                                                       │  │
│  └───────────────────────────────────┬───────────────────────────────────┘  │
│                                      │                                      │
│                                      │ Updates                              │
│                                      ▼                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                                                                       │  │
│  │  Query Side (Read Model) - CQRS                                       │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │  In-Memory Repositories                                         │  │  │
│  │  │                                                                 │  │  │
│  │  │  • CommitRepository                                             │  │  │
│  │  │    - ConcurrentHashMap<CommitId, Commit>                        │  │  │
│  │  │    - Stores commits with metadata and patches                   │  │  │
│  │  │                                                                 │  │  │
│  │  │  • BranchRepository                                             │  │  │
│  │  │    - ConcurrentHashMap<BranchName, Branch>                      │  │  │
│  │  │    - Branch points to latest CommitId                           │  │  │
│  │  │                                                                 │  │  │
│  │  │  • TagRepository                                                │  │  │
│  │  │    - ConcurrentHashMap<TagName, Tag>                            │  │  │
│  │  │    - Tags are immutable pointers to commits                     │  │  │
│  │  │                                                                 │  │  │
│  │  │  • DatasetGraphRepository                                       │  │  │
│  │  │    - ConcurrentHashMap<CommitId, DatasetGraph>                  │  │  │
│  │  │    - Materialized RDF graphs at specific commits                │  │  │
│  │  │    - Apache Jena DatasetGraphInMemory                           │  │  │
│  │  │                                                                 │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                       │  │
│  │  Query Services                                                       │  │
│  │  • SelectorResolutionService (branch/commit/asOf → CommitId)          │  │
│  │  • DatasetService (materialize graphs at commit)                      │  │
│  │  • HistoryQueryService (query commit history with filters)            │  │
│  │                                                                       │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  Technology Stack:                                                          │
│  • Java 21                                                                  │
│  • Spring Boot 3.5 (Dependency Injection, MVC, Kafka Integration)           │
│  • Apache Jena 5.5 (RDF parsing, serialization, SPARQL, RDFPatch)           │
│  • Spring Kafka (Producer + Consumer)                                       │
│  • Jackson (JSON serialization)                                             │
│  • Swagger/OpenAPI 3 (API documentation)                                    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Containers

### 1. CHUCC Server (Spring Boot Application)

**Type**: Web Application

**Technology**: Java 21 + Spring Boot 3.5

**Responsibilities**:
- Accept HTTP requests from clients
- Execute commands (write operations)
- Publish events to Kafka
- Consume events from Kafka
- Update in-memory read models
- Execute queries (read operations)
- Serialize/deserialize RDF data

**Components** (See Level 3 for detail):
- **Web Layer**: Controllers, error handling, content negotiation
- **Command Side**: Command handlers, domain services, validation
- **Event Publishing**: Kafka producer, event serialization
- **Event Consumption**: Kafka consumer, projectors
- **Query Side**: Repositories, query services
- **Domain Model**: Commit, Branch, Tag, RdfPatch

**Deployment**:
- **Packaging**: Executable JAR
- **Container**: Docker (optional)
- **Port**: 8080 (HTTP)
- **JVM**: Java 21+
- **Memory**: 2-4 GB heap (depends on dataset size)
- **CPU**: 2-4 cores recommended

**Configuration**:
- `application.yml`: Main configuration
- `application-it.yml`: Integration test configuration
- `application-dev.yml`: Development configuration
- Environment variables: Override properties

**Dependencies**:
```xml
<!-- Core -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- RDF Processing -->
<dependency>
  <groupId>org.apache.jena</groupId>
  <artifactId>apache-jena-libs</artifactId>
  <version>5.5.0</version>
</dependency>

<!-- Kafka -->
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>

<!-- OpenAPI -->
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

**Scaling**:
- **Horizontal**: Multiple instances share Kafka topics
- **Vertical**: Increase JVM heap for larger datasets
- **Stateless**: No local state (rebuilds from Kafka on restart)

---

### 2. Apache Kafka Cluster (Event Store + Message Bus)

**Type**: Message Broker + Event Store

**Technology**: Apache Kafka 3.6+

**Responsibilities**:
- Store events durably (infinite retention)
- Deliver events to consumers (ReadModelProjector)
- Maintain ordering per partition
- Support replay (event sourcing)

**Topics**:
- **Naming**: `<dataset-name>-events`
- **Examples**: `default-events`, `research-events`
- **Dynamic creation**: Created automatically on first publish
- **Partition key**: Dataset name (ensures ordering per dataset)

**Configuration**:
```yaml
kafka:
  bootstrap-servers: localhost:9092
  topic-prefix: ""
  partitions: 3
  replication-factor: 1

  producer:
    key-serializer: StringSerializer
    value-serializer: JsonSerializer
    compression-type: gzip
    acks: all  # Ensure durability
    enable-idempotence: true

  consumer:
    key-deserializer: StringDeserializer
    value-deserializer: JsonDeserializer
    group-id: chucc-server-projector
    auto-offset-reset: earliest  # Replay from beginning
    enable-auto-commit: true
```

**Event Retention**:
- **Policy**: Infinite (`retention.ms=-1`)
- **Reason**: Event sourcing requires full history
- **Compaction**: Not used (need full event log)
- **Cleanup**: Manual topic deletion only

**Deployment Options**:

**Development**:
- Single broker on localhost:9092
- Docker Compose or Testcontainers
- No replication (replication-factor=1)

**Production**:
- Kafka cluster (3+ brokers recommended)
- Replication factor 2-3
- ZooKeeper or KRaft mode
- Monitoring (JMX metrics)

**Alternatives Considered**:
- **RabbitMQ**: Not suitable for event sourcing (no infinite retention)
- **Amazon SQS**: Cloud-only, limited retention
- **PostgreSQL**: Simpler but less scalable
- **EventStoreDB**: Specialized but adds dependency

**Why Kafka**:
- ✅ Durable, distributed event store
- ✅ High throughput, low latency
- ✅ Scalable with partitions
- ✅ Exactly-once semantics
- ✅ Industry standard for event sourcing

---

### 3. In-Memory Repositories (Data Store)

**Type**: In-Memory Data Structure

**Technology**: Java ConcurrentHashMap + Apache Jena DatasetGraphInMemory

**Location**: Inside CHUCC Server container (not external)

**Repositories**:

**CommitRepository**:
- **Structure**: `ConcurrentHashMap<CommitId, Commit>`
- **Data**: Commit metadata + RDFPatch
- **Size**: Grows with commits (~1-10 KB per commit)
- **Indexed by**: CommitId (UUIDv7)

**BranchRepository**:
- **Structure**: `ConcurrentHashMap<(Dataset, BranchName), Branch>`
- **Data**: Branch name + pointer to CommitId
- **Size**: Grows with branches (~100 bytes per branch)
- **Mutable**: Branch pointers update on commits

**TagRepository**:
- **Structure**: `ConcurrentHashMap<(Dataset, TagName), Tag>`
- **Data**: Tag name + pointer to CommitId
- **Size**: Grows with tags (~100 bytes per tag)
- **Immutable**: Tags never change

**DatasetGraphRepository**:
- **Structure**: `ConcurrentHashMap<CommitId, DatasetGraph>`
- **Data**: Materialized RDF graphs
- **Technology**: Apache Jena DatasetGraphInMemory
- **Size**: Largest repository (depends on RDF dataset size)
- **Eviction**: LRU cache (configurable, optional)

**Durability**:
- ❌ **Not durable**: Data lost on restart
- ✅ **Rebuildable**: Replay events from Kafka to rebuild state
- ⚙️ **Snapshotting** (optional future): Periodic snapshots to disk

**Why In-Memory**:
- ✅ Fast reads (microseconds)
- ✅ Fast writes (microseconds)
- ✅ Simple (no external database)
- ✅ Stateless (rebuild from events)
- ✅ Like Apache Jena Fuseki (familiar to users)

**Trade-offs**:
- ❌ Limited by JVM heap
- ❌ Data lost on restart
- ✅ Can rebuild from Kafka events

**Future Enhancements**:
- Periodic snapshots to disk
- LRU cache for DatasetGraphRepository
- External database option for large datasets

---

## Container Interactions

### 1. Client → CHUCC Server (HTTP)

**Protocol**: HTTP/1.1

**Request Types**:
- **GraphStore Protocol**: PUT, GET, POST, DELETE, PATCH, HEAD, OPTIONS on `/data`
- **SPARQL Protocol**: GET, POST on `/sparql`
- **Version Control**: GET, POST, PUT, DELETE on `/version/*`

**Request Headers**:
- `Content-Type`: RDF format (text/turtle, application/ld+json, etc.)
- `Accept`: Desired response format
- `If-Match`: ETag for conditional requests
- `SPARQL-VC-Branch`: Target branch for writes
- `SPARQL-VC-Author`: Commit author
- `SPARQL-VC-Message`: Commit message

**Response Headers**:
- `ETag`: Commit ID (strong ETag)
- `Location`: URI of created resource
- `Content-Type`: Response format
- `Link`: RFC 5988 links (pagination, relations)

**Error Responses**:
- **Format**: RFC 7807 Problem Details (application/problem+json)
- **Status codes**: 400, 404, 406, 409, 412, 415, 422, 500

### 2. CHUCC Server → Kafka (Producer)

**Protocol**: Kafka Producer API

**Operation**: Publish events asynchronously

**Flow**:
```java
// Command handler creates event
CommitCreatedEvent event = new CommitCreatedEvent(...);

// Publish to Kafka
CompletableFuture<RecordMetadata> future = eventPublisher.publish(event);

// HTTP response returns immediately (don't wait)
return ResponseEntity.ok()
    .eTag("\"" + commitId.value() + "\"")
    .build();
```

**Configuration**:
- **Topic**: `<dataset>-events` (e.g., `default-events`)
- **Key**: Dataset name (String)
- **Value**: JSON-serialized event
- **Partition**: hash(key) % partitions
- **Acks**: `all` (wait for all replicas)
- **Compression**: GZIP
- **Idempotence**: Enabled (exactly-once)

**Error Handling**:
- **Retry**: Automatic retry with exponential backoff
- **Failure**: Log error, return 500 to client
- **Monitoring**: Kafka producer metrics

### 3. Kafka → CHUCC Server (Consumer)

**Protocol**: Kafka Consumer API

**Operation**: Consume events and update read models

**Flow**:
```java
@KafkaListener(
    topics = "${kafka.topic-prefix}#{dataset}-events",
    groupId = "chucc-server-projector",
    autoStartup = "${projector.kafka-listener.enabled:true}"
)
public void handleCommitCreatedEvent(CommitCreatedEvent event) {
    // Update repositories
    commitRepository.save(event.dataset(), commit, patch);
    branchRepository.updateBranchHead(event.dataset(), branch, commitId);
    datasetGraphRepository.buildGraph(event.dataset(), commitId);
}
```

**Configuration**:
- **Consumer group**: `chucc-server-projector`
- **Auto-offset-reset**: `earliest` (replay from beginning)
- **Auto-commit**: Enabled (after successful processing)
- **Concurrency**: Single-threaded per partition (ordering)

**Startup Behavior**:
- On first start: Consume from offset 0 (replay all events)
- On restart: Resume from last committed offset
- Result: In-memory repositories match event log

**Test Isolation**:
- **Development/Production**: Projector enabled by default
- **Integration tests**: Projector disabled by default
- **Override**: `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`

---

## Data Flow Examples

### Write Operation (PUT /data)

```
1. Client → CHUCC Server (HTTP)
   PUT /data?graph=http://example.org/g1&branch=main
   Content-Type: text/turtle
   SPARQL-VC-Author: Alice
   SPARQL-VC-Message: Update graph

   Body: <s> <p> "value" .

2. CHUCC Server (Command Side)
   - Parse Turtle to Jena Model
   - Resolve selector: branch=main → commitId
   - Compute RDF diff: old state vs new state
   - Create CommitCreatedEvent

3. CHUCC Server → Kafka (Producer)
   Topic: default-events
   Key: "default"
   Value: {
     "type": "CommitCreatedEvent",
     "dataset": "default",
     "commitId": "01JCDN...",
     "parents": ["01JCDM..."],
     "message": "Update graph",
     "author": "Alice",
     "timestamp": "2025-01-15T10:30:00Z",
     "patch": "TX .\nA <s> <p> \"value\" .\nTC ."
   }

4. CHUCC Server → Client (HTTP Response)
   200 OK
   ETag: "01JCDN..."
   Location: /version/commits/01JCDN...

   [Response returned BEFORE step 5]

5. Kafka → CHUCC Server (Consumer)
   ReadModelProjector.handleCommitCreatedEvent()
   - commitRepository.save()
   - branchRepository.updateBranchHead()
   - datasetGraphRepository.buildGraph()

6. Read models updated (async, after HTTP response)
```

**Key Insight**: HTTP response (step 4) happens BEFORE repositories updated (step 6).

### Read Operation (GET /data)

```
1. Client → CHUCC Server (HTTP)
   GET /data?graph=http://example.org/g1&branch=main
   Accept: text/turtle

2. CHUCC Server (Query Side)
   - SelectorResolutionService.resolve("main")
     → Branch main → CommitId 01JCDN...
   - DatasetGraphRepository.getGraph("default", "01JCDN...", "http://example.org/g1")
     → DatasetGraph (Jena in-memory graph)
   - Serialize to Turtle

3. CHUCC Server → Client (HTTP Response)
   200 OK
   ETag: "01JCDN..."
   Content-Type: text/turtle

   Body: <s> <p> "value" .
```

**Key Insight**: Read from materialized in-memory graph (fast, synchronous).

---

## Technology Choices

### Why Spring Boot?

**Benefits**:
- ✅ Dependency injection (loose coupling)
- ✅ Auto-configuration (Kafka, Jackson, etc.)
- ✅ MVC framework (controllers, error handling)
- ✅ Testing support (SpringBootTest, MockMvc)
- ✅ Production-ready (actuators, metrics)
- ✅ Large ecosystem

**Alternatives**:
- **Quarkus**: Faster startup, but smaller ecosystem
- **Micronaut**: Compile-time DI, but less mature
- **Vert.x**: Async/reactive, but steeper learning curve

### Why Apache Jena?

**Benefits**:
- ✅ Industry-standard RDF library
- ✅ SPARQL 1.1 support (query + update)
- ✅ RDFPatch support (event format)
- ✅ Multiple serialization formats
- ✅ DatasetGraphInMemory (in-memory graphs)
- ✅ Well-tested, mature

**Alternatives**:
- **Eclipse RDF4J**: Similar features, chosen Jena for familiarity
- **Oxigraph**: Fast, but less feature-complete

### Why Apache Kafka?

**Benefits**:
- ✅ Durable event store (infinite retention)
- ✅ High throughput, low latency
- ✅ Partitioning for scalability
- ✅ Exactly-once semantics
- ✅ Industry standard for event sourcing
- ✅ Replayable (rebuild state)

**Alternatives**:
- **PostgreSQL Event Store**: Simpler, but less scalable
- **EventStoreDB**: Specialized, but adds dependency
- **RabbitMQ**: Not suitable (limited retention)

### Why In-Memory Storage?

**Benefits**:
- ✅ Fast reads (microseconds)
- ✅ Fast writes (microseconds)
- ✅ Simple (no external database)
- ✅ Stateless (rebuild from events)
- ✅ Like Apache Jena Fuseki (familiar)

**Alternatives**:
- **PostgreSQL**: Durable, but slower
- **MongoDB**: Flexible schema, but adds complexity
- **Redis**: Fast, but not RDF-native

**Trade-off**: Dataset size limited by JVM heap.

---

## Deployment Scenarios

### Development (Single Machine)

```yaml
# docker-compose.yml
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092

  chucc-server:
    build: .
    ports:
      - "8080:8080"
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

### Production (Kubernetes)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: chucc-server
spec:
  replicas: 3  # Horizontal scaling
  template:
    spec:
      containers:
      - name: chucc-server
        image: chucc-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-cluster:9092"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
```

---

## References

- [C4 Model Website](https://c4model.com/)
- [Level 1: Context Diagram](c4-level1-context.md)
- [Level 3: Component Diagram](c4-level3-component.md)
- [CQRS & Event Sourcing Guide](cqrs-event-sourcing.md)
- [Architecture Overview](README.md)

---

## Summary

**CHUCC Server** is a single Spring Boot application that:
- Exposes HTTP REST API
- Publishes events to Apache Kafka (producer)
- Consumes events from Apache Kafka (consumer)
- Maintains in-memory read models
- Uses Apache Jena for RDF processing

**Key Containers**:
1. **CHUCC Server** (Spring Boot) - Business logic and API
2. **Apache Kafka** (Message Broker) - Event store and message bus
3. **In-Memory Repositories** (Inside CHUCC) - Read models

**Key Technologies**:
- Java 21 + Spring Boot 3.5
- Apache Jena 5.5 (RDF)
- Apache Kafka (Events)
- In-Memory Storage (Fast)

**Deployment**: Self-contained JAR, Docker container, or Kubernetes deployment.
