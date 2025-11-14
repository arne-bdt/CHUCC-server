# SHACL Validation Protocol - Summary

## Overview

This document provides a high-level summary of the SHACL Validation Protocol specification, highlighting its advanced features compared to Apache Jena Fuseki's basic SHACL endpoint.

---

## Key Features

### 1. Flexible Shapes Sources

**Your requirement:** *"It must be possible to use any graph within any dataset of the current CHUCC-server or even remote endpoints as SHACL shape graph."*

**Solution:**

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schema-registry",
    "graph": "http://example.org/shapes/product-v2",
    "branch": "main"
  }
}
```

**Supported sources:**
- ✅ Any graph in any local dataset
- ✅ Remote SPARQL endpoints
- ✅ Inline (Fuseki-compatible)
- ✅ Historical versions (branch/commit/asOf selectors)

### 2. Validation Result Persistence

**Your requirement:** *"I want to have the validation create a new graph or completely override an existing graph in any of the datasets in my CHUCC-server instance."*

**Solution:**

```json
{
  "results": {
    "return": true,
    "store": {
      "dataset": "validation-reports",
      "graph": "http://example.org/reports/2025-01-15",
      "overwrite": true
    }
  }
}
```

**Features:**
- ✅ Store in any dataset/graph
- ✅ Overwrite existing graphs (configurable)
- ✅ Return AND store simultaneously
- ✅ Creates version control commits (if dataset is version-controlled)

### 3. Cross-Dataset Validation

**Use case:** Shapes in one dataset, data in another, results in a third.

**Example:**

```json
{
  "shapes": {
    "source": "local",
    "dataset": "shapes-library",
    "graph": "http://example.org/schemas/person-shape"
  },
  "data": {
    "source": "local",
    "dataset": "production-data",
    "graphs": ["http://example.org/users"]
  },
  "results": {
    "store": {
      "dataset": "qa-reports",
      "graph": "http://example.org/reports/users/2025-01-15"
    }
  }
}
```

### 4. Historical Validation

**Use case:** Validate current data against historical shapes, or historical data against current shapes.

**Example:**

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/schemas/v1",
    "commit": "01936d8f-1234-7890-abcd-ef1234567890"
  },
  "data": {
    "source": "local",
    "dataset": "user-data",
    "graphs": ["union"],
    "branch": "main"
  }
}
```

### 5. Remote Endpoint Support

**Use case:** Validate data from external sources.

**Example:**

```json
{
  "shapes": {
    "source": "local",
    "dataset": "internal-schemas",
    "graph": "http://example.org/schemas/external-api"
  },
  "data": {
    "source": "remote",
    "endpoint": "https://external.example.org/sparql",
    "graphs": ["http://external.example.org/public-data"]
  }
}
```

---

## Comparison with Apache Jena Fuseki

| Feature | Fuseki | CHUCC SHACL Protocol |
|---------|--------|----------------------|
| **Shapes source** | Inline only | Inline, local, remote, historical |
| **Data source** | Current dataset | Multi-dataset, remote, historical |
| **Result handling** | Return only | Return, store, or both |
| **Cross-dataset** | ❌ No | ✅ Yes |
| **Version control** | ❌ No | ✅ Full support (branch/commit/asOf) |
| **Multiple graphs** | Single graph | Multiple graphs (separate, merged, or dataset) |
| **Result persistence** | ❌ No | ✅ Store in any dataset/graph |
| **Historical validation** | ❌ No | ✅ Validate against historical states |

---

## Typical Workflows

### Workflow 1: Continuous Quality Monitoring

**Setup:**
1. `schema-registry` - SHACL shapes for different data types
2. `production-db` - Live production data
3. `qa-reports` - Historical validation reports

**Daily validation:**

```bash
POST /production-db/shacl
{
  "shapes": {"dataset": "schema-registry", "graph": "...", "branch": "main"},
  "data": {"dataset": "production-db", "graphs": ["union"], "branch": "main"},
  "results": {
    "store": {
      "dataset": "qa-reports",
      "graph": "http://example.org/reports/daily/2025-01-15"
    }
  }
}
```

**Trend analysis:**

```sparql
SELECT ?date (COUNT(?violation) AS ?count)
WHERE {
  GRAPH ?reportGraph {
    ?report sh:result ?violation .
    ?violation sh:resultSeverity sh:Violation .
  }
  BIND(REPLACE(STR(?reportGraph), ".*daily/", "") AS ?date)
}
GROUP BY ?date
ORDER BY ?date
```

### Workflow 2: Schema Evolution Testing

**Use case:** Test if new data conforms to old schemas (backward compatibility).

```bash
POST /new-data/shacl
{
  "shapes": {
    "dataset": "schemas",
    "graph": "http://example.org/schemas/v1",
    "commit": "old-commit-id"
  },
  "data": {
    "dataset": "new-data",
    "graphs": ["union"],
    "branch": "main"
  },
  "results": {
    "store": {
      "dataset": "compatibility-tests",
      "graph": "http://example.org/tests/v1-vs-v2"
    }
  }
}
```

### Workflow 3: External Data Validation

**Use case:** Validate data from partners before importing.

```bash
POST /imports/shacl
{
  "shapes": {
    "dataset": "partner-contracts",
    "graph": "http://example.org/schemas/partner-api"
  },
  "data": {
    "source": "remote",
    "endpoint": "https://partner.example.org/sparql",
    "graphs": ["http://partner.example.org/export"]
  },
  "results": {
    "return": true,
    "store": {
      "dataset": "import-validation",
      "graph": "http://example.org/validation/partner/2025-01-15"
    }
  }
}
```

---

## Implementation Roadmap

### Phase 1: Core Validation (MVP)

**Endpoint:** `POST /{dataset}/shacl`

**Features:**
- ✅ Inline shapes (Fuseki compatibility)
- ✅ Local shapes from same dataset
- ✅ Validate single graph or union
- ✅ Return validation report

**Implementation steps:**
1. Create `ShaclValidationController`
2. Implement request/response DTOs
3. Integrate Apache Jena SHACL API
4. Add error handling (RFC 7807)
5. Write integration tests

**Estimated effort:** 2-3 days

### Phase 2: Cross-Dataset Support

**Features:**
- ✅ Shapes from any local dataset
- ✅ Data from any local dataset
- ✅ Multiple graph validation modes

**Implementation steps:**
1. Create `GraphReferenceResolver` service
2. Add dataset resolution logic
3. Implement validation modes (separate/merged/dataset)
4. Add cross-dataset integration tests

**Estimated effort:** 1-2 days

### Phase 3: Result Persistence

**Features:**
- ✅ Store validation reports in any dataset/graph
- ✅ Version control commit creation
- ✅ CQRS integration (202 Accepted)

**Implementation steps:**
1. Create `ValidationResultStorageService`
2. Implement commit creation for stored results
3. Add Kafka event publishing
4. Add projector support for validation events
5. Write projector tests

**Estimated effort:** 2-3 days

### Phase 4: Version Control Integration

**Features:**
- ✅ Branch/commit/asOf selectors for shapes
- ✅ Branch/commit/asOf selectors for data
- ✅ Historical validation

**Implementation steps:**
1. Integrate selector resolution
2. Add historical graph materialization
3. Test with version-controlled datasets
4. Add historical validation examples

**Estimated effort:** 1-2 days

### Phase 5: Remote Endpoint Support

**Features:**
- ✅ Remote shapes from SPARQL endpoints
- ✅ Remote data from SPARQL endpoints
- ✅ Security controls (SSRF prevention, timeouts)

**Implementation steps:**
1. Create `RemoteEndpointClient` service
2. Add URL validation and sanitization
3. Implement caching for remote shapes
4. Add security controls
5. Write security tests

**Estimated effort:** 2-3 days

### Phase 6: Advanced Features

**Features:**
- ✅ Batch validation
- ✅ Webhook notifications
- ✅ Performance optimization (caching, parallelization)

**Estimated effort:** 3-4 days

---

## Architecture Integration

### CQRS Pattern

**Command side (write operations):**

```
POST /dataset/shacl (with results.store)
    ↓
ShaclValidationCommandHandler
    ↓
Publish: ValidationCompletedEvent
    ↓
Return: 202 Accepted
```

**Query side (read operations):**

```
ValidationCompletedEvent
    ↓
ValidationResultProjector
    ↓
Store report graph
    ↓
Create commit (if version-controlled)
```

### Event Schema

```java
public class ValidationCompletedEvent extends BaseEvent {
  private String validationId;
  private String dataset;
  private String shapesSource;
  private List<String> dataGraphs;
  private String resultGraph;
  private boolean conforms;
  private Model validationReport;  // Jena Model
  private String author;
  private Instant timestamp;
}
```

### Components

```
┌─────────────────────────────────────────┐
│   ShaclValidationController             │
│   - POST /{dataset}/shacl                │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   ShaclValidationCommandHandler          │
│   - Resolve shapes graph                 │
│   - Resolve data graphs                  │
│   - Perform validation                   │
│   - Publish event (if storing)           │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   GraphReferenceResolver                 │
│   - Resolve local graph references       │
│   - Resolve remote graph references      │
│   - Apply version selectors              │
└──────────────┬──────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────┐
│   ShaclValidationEngine                  │
│   - Apache Jena SHACL integration        │
│   - Validation modes (separate/merged)   │
│   - Report generation                    │
└──────────────┬──────────────────────────┘
               │
               ↓ (if storing)
┌─────────────────────────────────────────┐
│   ValidationResultProjector              │
│   - Listen to ValidationCompletedEvent   │
│   - Store report graph                   │
│   - Create version control commit        │
└─────────────────────────────────────────┘
```

---

## Security Considerations

### Input Validation

**Required checks:**
- ✅ Dataset names match ABNF grammar
- ✅ Graph URIs are valid IRIs
- ✅ Selectors are mutually exclusive
- ✅ Remote endpoint URLs are validated

### Access Control

**Permissions required:**
- `READ` on shapes dataset/graph
- `READ` on data dataset/graph(s)
- `WRITE` on results dataset/graph (if storing)

### SSRF Prevention

**Remote endpoint protection:**
- ✅ Validate URL scheme (http/https only)
- ✅ Block private IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
- ✅ Block localhost/127.0.0.1
- ✅ Enforce timeouts (default: 30 seconds)
- ✅ Limit response size (default: 100MB)

### Resource Limits

**Prevent DoS:**
- ✅ Max shapes graph size: 10MB
- ✅ Max data graph size: 100MB
- ✅ Validation timeout: 60 seconds
- ✅ Max concurrent validations: 10

---

## Testing Strategy

### Unit Tests

**Components to test:**
- `GraphReferenceResolver` - All source types, selectors, error cases
- `ShaclValidationEngine` - Validation modes, report generation
- `ValidationResultStorageService` - Commit creation, overwrite logic

### Integration Tests

**Test scenarios:**
1. **Basic validation** (Fuseki-compatible)
   - Inline shapes, local data
   - Expected: 200 OK with validation report

2. **Cross-dataset validation**
   - Shapes from dataset A, data from dataset B
   - Expected: 200 OK with validation report

3. **Result storage**
   - Store results in dataset C
   - Expected: 202 Accepted, commit created

4. **Version control integration**
   - Historical shapes, current data
   - Expected: Correct shapes version used

5. **Error handling**
   - Missing dataset: 404
   - Missing graph: 404
   - Invalid shapes: 422
   - Conflicting selectors: 400

6. **Security tests**
   - SSRF attempt: 422
   - Timeout: 503
   - Access denied: 403

---

## Configuration

### Application Properties

```yaml
chucc:
  shacl:
    validation:
      # Resource limits
      max-shapes-size: 10485760  # 10MB
      max-data-size: 104857600   # 100MB
      timeout: 60000             # 60 seconds
      max-concurrent: 10

      # Remote endpoints
      remote:
        enabled: true
        timeout: 30000           # 30 seconds
        max-response-size: 104857600
        block-private-ips: true
        allowed-schemes:
          - http
          - https

      # Caching
      cache:
        enabled: true
        ttl: 3600                # 1 hour
        max-entries: 100

      # Result storage
      storage:
        default-overwrite: false
        enable-versioning: true
```

---

## API Examples

### Example 1: Basic Validation

**Request:**

```bash
curl -X POST http://localhost:8080/mydata/shacl \
  -H "Content-Type: application/json" \
  -H "Accept: text/turtle" \
  -d '{
    "shapes": {
      "source": "inline",
      "data": "@prefix sh: <http://www.w3.org/ns/shacl#> . ..."
    },
    "data": {
      "source": "local",
      "dataset": "mydata",
      "graphs": ["default"]
    }
  }'
```

**Response:**

```turtle
HTTP/1.1 200 OK
Content-Type: text/turtle

@prefix sh: <http://www.w3.org/ns/shacl#> .

[] a sh:ValidationReport ;
   sh:conforms true .
```

### Example 2: Cross-Dataset with Storage

**Request:**

```bash
curl -X POST http://localhost:8080/production/shacl \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: QA Bot <qa@example.org>" \
  -d '{
    "shapes": {
      "source": "local",
      "dataset": "schemas",
      "graph": "http://example.org/shapes/product",
      "branch": "main"
    },
    "data": {
      "source": "local",
      "dataset": "production",
      "graphs": ["union"]
    },
    "results": {
      "store": {
        "dataset": "qa-reports",
        "graph": "http://example.org/reports/2025-01-15",
        "overwrite": true
      }
    }
  }'
```

**Response:**

```json
HTTP/1.1 202 Accepted
Location: /qa-reports/data?graph=http://example.org/reports/2025-01-15
ETag: "01936d8f-1234-7890-abcd-ef1234567890"

{
  "message": "Validation completed and result stored",
  "commitId": "01936d8f-1234-7890-abcd-ef1234567890",
  "conforms": false,
  "resultsGraph": "http://example.org/reports/2025-01-15"
}
```

---

## Next Steps

1. **Review** this protocol specification
2. **Prioritize** features (suggest starting with Phase 1-3)
3. **Design** Java API (DTOs, services, controllers)
4. **Implement** incrementally with TDD
5. **Test** with real SHACL shapes and data
6. **Document** API usage in OpenAPI spec

---

## Questions for Consideration

1. **Default behavior**: Should `results.return` default to `true` or `false`?
2. **Graph naming**: Should there be conventions for validation result graph URIs?
3. **Retention**: Should old validation reports be automatically archived/deleted?
4. **Notifications**: Do you need webhook notifications when validation completes?
5. **Batch validation**: Is batch validation (multiple validations in one request) needed?
6. **Authentication**: How should remote endpoint authentication be handled?

---

**Status:** Ready for implementation
**Author:** Claude (AI Assistant)
**Date:** 2025-11-14
