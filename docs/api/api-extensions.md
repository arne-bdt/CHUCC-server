# API Extensions

This implementation includes extensions beyond the SPARQL 1.2 Protocol Version Control Extension specification.

## Extension Categories

Extensions are categorized to help users understand their scope and stability:

1. **Protocol Enhancements** - Optional features that enhance the official VC extension spec
2. **CHUCC-Specific Extensions** - Operational features not part of any protocol specification
3. **Operational Endpoints** - Administrative endpoints (Spring Boot Actuator)

---

## Protocol Enhancements

These extensions enhance the SPARQL 1.2 Protocol Version Control Extension but are **not** part of the official specification. They can be enabled/disabled via configuration.

### GET /version/diff

**Purpose**: Compare two commits and return the changeset between them.

**Endpoint**: `GET /version/diff?from={commitId}&to={commitId}`

**Response**: RDF Patch (`text/rdf-patch`)

**Query Parameters**:
- `from` (required): Source commit ID (UUIDv7)
- `to` (required): Target commit ID (UUIDv7)
- `dataset` (optional): Dataset name (default: "default")

**Status Codes**:
- `200 OK` - Returns RDF Patch with changes
- `204 No Content` - No differences between commits
- `400 Bad Request` - Invalid commit IDs
- `404 Not Found` - Commit not found or feature disabled

**Configuration**:
- Property: `vc.diff-enabled` (default: `true`)
- When disabled: Returns `404 Not Found`

**Compatibility**: Does not conflict with spec. Optional feature.

**Example**:
```bash
curl "http://localhost:8080/version/diff?from=01936d8f-...&to=01936d90-..."
```

---

### GET /version/blame

**Purpose**: Get last-writer attribution for a resource (which commit last modified a triple).

**Endpoint**: `GET /version/blame?subject={iri}`

**Response**: JSON with attribution information

**Query Parameters**:
- `subject` (required): Subject IRI to query
- `predicate` (optional): Predicate IRI filter
- `branch` or `commit` (optional): Ref selector (default: main branch)
- `dataset` (optional): Dataset name (default: "default")

**Response Format**:
```json
{
  "subject": "http://example.org/resource",
  "triples": [
    {
      "predicate": "http://example.org/prop",
      "object": "value",
      "commitId": "01936d8f-...",
      "author": "Alice <alice@example.org>",
      "timestamp": "2025-11-03T10:30:00Z"
    }
  ]
}
```

**Status Codes**:
- `200 OK` - Returns attribution data
- `404 Not Found` - Subject not found or feature disabled

**Configuration**:
- Property: `vc.blame-enabled` (default: depends on `vc.level`)
- When disabled: Returns `404 Not Found`

**Compatibility**: Optional feature that can be enabled/disabled.

**Example**:
```bash
curl "http://localhost:8080/version/blame?subject=http://example.org/resource"
```

---

## CHUCC-Specific Extensions

These extensions are **specific to CHUCC Server** and are not part of any protocol specification. They provide operational features for multi-dataset management and performance optimization.

### POST /version/datasets/{name}

**Purpose**: Create a new dataset with automatic Kafka topic provisioning.

**Endpoint**: `POST /version/datasets/{name}`

**Request Body** (all fields optional):
```json
{
  "description": "Optional dataset description",
  "initialGraph": "http://example.org/initial-graph",
  "kafka": {
    "partitions": 3,
    "replicationFactor": 2,
    "retentionMs": 604800000
  }
}
```

**Request Headers**:
- `SPARQL-VC-Author` (optional): Author name (default: "anonymous")
- `Content-Type`: `application/json`

**Response** (`202 Accepted`):
```json
{
  "name": "my-dataset",
  "description": "Optional dataset description",
  "mainBranch": "main",
  "initialCommitId": "01936d8f-...",
  "kafkaTopic": "vc.my-dataset.events",
  "timestamp": "2025-11-03T10:30:00Z"
}
```

**Response Headers**:
- `Location`: URL of the created dataset
- `SPARQL-VC-Status`: `pending` (eventual consistency)

**What Happens**:
1. Creates Kafka topic: `vc.{dataset}.events`
2. Creates DLQ topic: `vc.{dataset}.events.dlq` (7-day retention)
3. Creates initial empty commit with message "Initial commit"
4. Creates main branch (protected) pointing to initial commit
5. Publishes `DatasetCreatedEvent` to Kafka
6. Returns `202 Accepted` before read model updates

**Status Codes**:
- `202 Accepted` - Dataset creation accepted (eventual consistency)
- `400 Bad Request` - Invalid dataset name or Kafka config
- `409 Conflict` - Dataset already exists

**Configuration**: Cannot be disabled (core feature for multi-dataset support)

**Rationale**: Multi-tenant deployments require dynamic dataset provisioning. The official protocol assumes datasets are pre-configured.

**Example**:
```bash
curl -X POST http://localhost:8080/version/datasets/my-dataset \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Alice <alice@example.org>" \
  -d '{
    "description": "Production RDF dataset",
    "kafka": {
      "partitions": 5,
      "replicationFactor": 3
    }
  }'
```

---

### DELETE /version/datasets/{name}

**Purpose**: Delete an entire dataset including all branches, commits, and optionally Kafka topics.

**Endpoint**: `DELETE /version/datasets/{name}?confirmed=true&deleteKafkaTopic=false`

**Query Parameters**:
- `confirmed` (required): Must be `true` to prevent accidental deletion
- `deleteKafkaTopic` (optional): Delete Kafka topic (default: `false`, **DESTRUCTIVE**)

**Request Headers**:
- `X-Author` (optional): Author of deletion operation (default: "anonymous")

**Status Codes**:
- `204 No Content` - Dataset deleted successfully
- `400 Bad Request` - Deletion not confirmed (`confirmed=true` missing)
- `404 Not Found` - Dataset not found

**Configuration**: Cannot be disabled (core feature)

**Rationale**: Lifecycle management for multi-dataset deployments.

**⚠️ Warning**: Deleting Kafka topics is **irreversible**. Event history will be lost permanently.

**Example**:
```bash
# Delete dataset but keep Kafka topics
curl -X DELETE "http://localhost:8080/version/datasets/my-dataset?confirmed=true" \
  -H "X-Author: admin"

# Delete dataset AND Kafka topics (DESTRUCTIVE!)
curl -X DELETE "http://localhost:8080/version/datasets/my-dataset?confirmed=true&deleteKafkaTopic=true" \
  -H "X-Author: admin"
```

---

### POST /version/batch-graphs

**Purpose**: Execute batch graph operations atomically (GSP variant of `/version/batch`).

**Endpoint**: `POST /version/batch-graphs?dataset=default`

**Request Body**:
```json
{
  "operations": [
    {
      "type": "PUT",
      "graph": "http://example.org/g1",
      "content": "<rdf-data>",
      "contentType": "text/turtle"
    },
    {
      "type": "POST",
      "graph": "http://example.org/g2",
      "content": "<more-data>",
      "contentType": "application/rdf+xml"
    },
    {
      "type": "DELETE",
      "graph": "http://example.org/g3"
    }
  ],
  "mode": "single-commit"
}
```

**Query Parameters**:
- `dataset` (optional): Dataset name (default: "default")
- `branch` (optional): Target branch (default: "main")

**Request Headers**:
- `SPARQL-VC-Author` (optional): Author name
- `SPARQL-VC-Message` (optional): Commit message
- `Content-Type`: `application/json`

**Operation Types**:
- `PUT` - Replace graph content (creates if not exists)
- `POST` - Merge content into graph
- `PATCH` - Apply RDF Patch to graph
- `DELETE` - Delete entire graph

**Modes**:
- `single-commit` (default): All operations in one commit
- `multi-commit`: Each operation creates separate commit

**Response** (`200 OK` for single-commit):
```json
{
  "commitId": "01936d8f-...",
  "operationsApplied": 3,
  "graphsModified": ["http://example.org/g1", "http://example.org/g2"]
}
```

**Status Codes**:
- `200 OK` - Batch executed successfully (single-commit mode)
- `204 No Content` - No-op (no changes detected)
- `207 Multi-Status` - Partial success (multi-commit mode)
- `400 Bad Request` - Invalid operation format

**Configuration**: Cannot be disabled

**Rationale**: Performance optimization for bulk GSP operations. Complements `/version/batch` (which handles SPARQL Updates).

**Distinction**:
- `POST /version/batch` - Executes SPARQL QUERY and UPDATE operations
- `POST /version/batch-graphs` - Executes Graph Store Protocol operations (PUT, POST, DELETE, PATCH)

**Example**:
```bash
curl -X POST http://localhost:8080/version/batch-graphs \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Alice" \
  -d '{
    "operations": [
      {
        "type": "PUT",
        "graph": "http://example.org/data",
        "content": "@prefix ex: <http://example.org/> . ex:Alice ex:age 30 .",
        "contentType": "text/turtle"
      }
    ],
    "mode": "single-commit"
  }'
```

---

## Operational Endpoints (Actuator)

These endpoints follow Spring Boot Actuator conventions and provide administrative/monitoring capabilities. They should be secured in production environments.

### Kafka Health Monitoring

**GET /actuator/kafka/health-detailed**

Returns health status of all dataset Kafka topics.

**Response**:
```json
{
  "default": {
    "topic": "vc.default.events",
    "healthy": true,
    "partitions": 3,
    "lag": 0
  }
}
```

---

**GET /actuator/kafka/topics/{dataset}/health**

Returns health status for a specific dataset's Kafka topic.

---

**POST /actuator/kafka/topics/{dataset}/heal?confirm=true**

Manually trigger topic healing (recreate missing partitions, fix config).

**⚠️ Warning**: Requires `confirm=true` parameter.

---

### Materialized View Management

**POST /actuator/materialized-views/rebuild?dataset=default&branch=main&confirm=true**

Rebuild branch materialized graph from commit history.

**Query Parameters**:
- `dataset` (required): Dataset name
- `branch` (required): Branch name
- `confirm` (required): Must be `true`

**Response**:
```json
{
  "dataset": "default",
  "branch": "main",
  "commitsProcessed": 150,
  "triplesCount": 42000,
  "duration": "PT2.5S"
}
```

**Use Cases**:
- Recovery after projection errors
- Debugging materialized view inconsistencies
- Performance testing

**⚠️ Warning**: Rebuild can be expensive for branches with long commit history.

---

## Extension Summary

| Extension | Type | Can Disable? | Purpose |
|-----------|------|--------------|---------|
| GET /version/diff | Protocol Enhancement | Yes | Compare commits |
| GET /version/blame | Protocol Enhancement | Yes | Attribution tracking |
| POST /version/datasets/{name} | CHUCC-Specific | No | Create dataset |
| DELETE /version/datasets/{name} | CHUCC-Specific | No | Delete dataset |
| POST /version/batch-graphs | CHUCC-Specific | No | Batch GSP operations |
| /actuator/kafka/* | Operational | Via security | Kafka monitoring |
| /actuator/materialized-views/* | Operational | Via security | View management |

---

## Compatibility Notes

**All extensions**:
- Follow the same error format (`problem+json`) as the protocol spec
- Are clearly marked in API documentation
- Do not interfere with spec-compliant operations

**Protocol enhancements** (diff, blame):
- Return `404 Not Found` when disabled
- Controlled via `application.yml` properties

**CHUCC-specific extensions**:
- Cannot be disabled (core operational features)
- Required for multi-dataset deployments

**Operational endpoints**:
- Follow Spring Boot Actuator conventions
- Should be secured via `management.endpoints.web.exposure.include` property
- Not intended for public API exposure
