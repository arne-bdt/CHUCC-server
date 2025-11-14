# SHACL Validation Protocol for RDF Services

**Status:** Draft Specification
**Version:** 0.1
**Date:** 2025-11-14

## Abstract

This document defines a protocol for SHACL (Shapes Constraint Language) validation in RDF services. Unlike Apache Jena Fuseki's basic SHACL endpoint, this protocol provides comprehensive support for:
- Using shapes graphs from any dataset (local or remote)
- Validating data graphs across multiple datasets
- Storing validation results for later analysis
- Integration with version control systems

The protocol is designed to complement the SPARQL 1.2 Protocol and Graph Store Protocol, enabling advanced validation workflows in distributed RDF environments.

---

## 1. Purpose & Scope

This protocol enables SHACL validation with the following capabilities:

1. **Flexible shapes sources**: Shapes can be sourced from any graph in any dataset (local or remote), not just inline request bodies
2. **Multi-dataset validation**: Validate graphs across different datasets in a single request
3. **Result persistence**: Store validation results in specified graphs for historical analysis and auditing
4. **Version control integration**: Validate against historical states using branch/commit/tag selectors
5. **Remote endpoint support**: Use shapes from remote SPARQL endpoints

**Out of scope**: This protocol does not define SHACL semantics, inference rules, or extension mechanisms. It focuses solely on the HTTP API for validation operations.

---

## 2. Core Concepts

### 2.1 SHACL Validation Components

A SHACL validation operation consists of three components:

1. **Shapes Graph**: RDF graph containing SHACL constraint definitions
2. **Data Graph**: RDF graph(s) to be validated
3. **Validation Report**: RDF graph containing validation results (conforming to SHACL specification)

### 2.2 Graph References

Graphs can be referenced in multiple ways:

- **Local graph**: `{"dataset": "mydata", "graph": "http://example.org/shapes"}`
- **Default graph**: `{"dataset": "mydata", "graph": "default"}`
- **Union graph**: `{"dataset": "mydata", "graph": "union"}` (all named graphs merged)
- **Remote endpoint**: `{"endpoint": "http://remote.example.org/sparql", "graph": "http://example.org/shapes"}`
- **Inline**: Shapes provided directly in request body (legacy compatibility)

### 2.3 Version Control Integration

When used with version-controlled datasets, graph references support selectors:

- **Branch**: `{"dataset": "mydata", "graph": "http://example.org/shapes", "branch": "main"}`
- **Commit**: `{"dataset": "mydata", "graph": "http://example.org/shapes", "commit": "01936d8f-1234-7890-abcd-ef1234567890"}`
- **As-of timestamp**: `{"dataset": "mydata", "graph": "http://example.org/shapes", "asOf": "2025-01-15T10:30:00Z"}`

Selectors are **mutually exclusive** (only one per graph reference).

---

## 3. Resources & Endpoints

All endpoints are relative to a dataset base URL `/{dataset}`.

### 3.1 Discovery

Servers supporting SHACL validation SHOULD advertise this capability via:

```http
Link: </{dataset}/shacl>; rel="shacl-validation"
```

### 3.2 Primary Endpoint

#### `POST /{dataset}/shacl`

Validates data graph(s) against a shapes graph.

**Request Headers:**
- `Content-Type: application/json` (request body format)
- `Accept: text/turtle` (or other RDF format for validation report)
- `SPARQL-VC-Author: <author>` (OPTIONAL, for stored results)

**Request Body Schema:**

```json
{
  "shapes": {
    "source": "inline" | "local" | "remote",
    "dataset": "dataset-name",
    "graph": "graph-uri | default | union",
    "endpoint": "http://remote.example.org/sparql",
    "branch": "branch-name",
    "commit": "commit-id",
    "asOf": "RFC3339-timestamp",
    "data": "...RDF content..."
  },
  "data": {
    "source": "local" | "remote",
    "dataset": "dataset-name",
    "graphs": ["graph-uri", "default", "union"],
    "endpoint": "http://remote.example.org/sparql",
    "branch": "branch-name",
    "commit": "commit-id",
    "asOf": "RFC3339-timestamp"
  },
  "options": {
    "validateGraphs": "separately" | "merged" | "dataset",
    "targetNode": "http://example.org/resource",
    "severity": "Violation" | "Warning" | "Info"
  },
  "results": {
    "return": true,
    "store": {
      "dataset": "validation-results",
      "graph": "http://example.org/reports/2025-01-15",
      "overwrite": true | false
    }
  }
}
```

**Field Descriptions:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `shapes.source` | string | Yes | `"inline"`, `"local"`, or `"remote"` |
| `shapes.dataset` | string | If source=local | Dataset name containing shapes graph |
| `shapes.graph` | string | If source=local/remote | Graph URI, `"default"`, or `"union"` |
| `shapes.endpoint` | string | If source=remote | SPARQL endpoint URL |
| `shapes.data` | string | If source=inline | RDF content (Turtle, JSON-LD, etc.) |
| `shapes.branch/commit/asOf` | string | No | Version selector (mutually exclusive) |
| `data.source` | string | Yes | `"local"` or `"remote"` |
| `data.dataset` | string | If source=local | Dataset name containing data |
| `data.graphs` | array | Yes | List of graph URIs, `"default"`, or `"union"` |
| `data.endpoint` | string | If source=remote | SPARQL endpoint URL |
| `data.branch/commit/asOf` | string | No | Version selector (mutually exclusive) |
| `options.validateGraphs` | string | No | `"separately"` (default), `"merged"`, or `"dataset"` |
| `options.targetNode` | string | No | Validate only specific resource URI |
| `options.severity` | string | No | Filter results by severity level |
| `results.return` | boolean | No | Return report in response (default: true) |
| `results.store` | object | No | Store report in specified graph |
| `results.store.overwrite` | boolean | No | Overwrite existing graph (default: false) |

**Response (Validation Only):**

```http
HTTP/1.1 200 OK
Content-Type: text/turtle
ETag: "validation-{timestamp}-{hash}"

# Validation report in requested RDF format
@prefix sh: <http://www.w3.org/ns/shacl#> .
...
```

**Response (Validation + Storage):**

```http
HTTP/1.1 202 Accepted
Location: /{results.store.dataset}/data?graph={results.store.graph}
ETag: "{commit-id}"
Content-Type: application/json

{
  "message": "Validation completed and result stored",
  "commitId": "01936d8f-1234-7890-abcd-ef1234567890",
  "conforms": false,
  "resultsGraph": "http://example.org/reports/2025-01-15"
}
```

---

## 4. Validation Modes

### 4.1 Separate Graph Validation

When `options.validateGraphs = "separately"` (default):

- Each graph in `data.graphs` is validated independently
- Validation report contains separate `sh:ValidationResult` entries per graph
- Useful when graphs represent independent datasets

**Example Request:**

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
    "graphs": [
      "http://example.org/users",
      "http://example.org/employees"
    ]
  },
  "options": {
    "validateGraphs": "separately"
  }
}
```

### 4.2 Merged Graph Validation

When `options.validateGraphs = "merged"`:

- All graphs in `data.graphs` are merged into a single RDF graph
- Validation performed on merged graph
- Useful when data is logically partitioned across graphs

### 4.3 Dataset Validation

When `options.validateGraphs = "dataset"`:

- Validates the entire dataset as a collection of named graphs
- Preserves quad structure (graph context maintained)
- Useful for validating dataset-level constraints

---

## 5. Use Cases & Examples

### 5.1 Basic Validation (Fuseki-Compatible)

**POST** `/{dataset}/shacl`

```json
{
  "shapes": {
    "source": "inline",
    "data": "@prefix sh: <http://www.w3.org/ns/shacl#> . ..."
  },
  "data": {
    "source": "local",
    "dataset": "mydata",
    "graphs": ["default"]
  }
}
```

### 5.2 Cross-Dataset Validation

Validate data in one dataset against shapes in another:

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schema-registry",
    "graph": "http://example.org/schemas/product-v2"
  },
  "data": {
    "source": "local",
    "dataset": "product-catalog",
    "graphs": ["union"]
  },
  "results": {
    "return": true,
    "store": {
      "dataset": "validation-reports",
      "graph": "http://example.org/reports/product-catalog/2025-01-15",
      "overwrite": true
    }
  }
}
```

**Workflow:**
1. Shapes sourced from `schema-registry` dataset
2. Data sourced from `product-catalog` dataset (all named graphs merged)
3. Validation report returned in response
4. Report also stored in `validation-reports` dataset for historical analysis

### 5.3 Historical Validation

Validate current data against historical shapes:

```json
{
  "shapes": {
    "source": "local",
    "dataset": "schemas",
    "graph": "http://example.org/schemas/person",
    "commit": "01936d8f-1234-7890-abcd-ef1234567890"
  },
  "data": {
    "source": "local",
    "dataset": "user-data",
    "graphs": ["http://example.org/users"],
    "branch": "main"
  }
}
```

### 5.4 Remote Endpoint Validation

Validate data from remote endpoint:

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

### 5.5 Continuous Validation Workflow

Store validation results for trend analysis:

```json
{
  "shapes": {
    "source": "local",
    "dataset": "quality-rules",
    "graph": "http://example.org/qa/data-quality-v1",
    "branch": "main"
  },
  "data": {
    "source": "local",
    "dataset": "production-db",
    "graphs": ["union"],
    "branch": "main"
  },
  "results": {
    "return": false,
    "store": {
      "dataset": "qa-reports",
      "graph": "http://example.org/reports/daily/2025-01-15",
      "overwrite": false
    }
  }
}
```

**Scheduled workflow:**
1. Run validation daily via cron job
2. Store results in dated graphs
3. Query historical reports to track data quality trends
4. Alert on regression (increasing violations)

---

## 6. Status Codes

### Success Responses

| Code | Scenario | Response Body |
|------|----------|---------------|
| `200 OK` | Validation completed, results returned | Validation report (RDF) |
| `202 Accepted` | Validation completed, results stored | Commit metadata (JSON) |
| `204 No Content` | Validation completed, conforming data, results not returned | None |

### Client Errors

| Code | Scenario | Error Code |
|------|----------|------------|
| `400 Bad Request` | Malformed request body | `invalid_request` |
| `400 Bad Request` | Invalid graph reference | `invalid_graph_reference` |
| `400 Bad Request` | Conflicting selectors (branch + commit) | `selector_conflict` |
| `404 Not Found` | Dataset does not exist | `dataset_not_found` |
| `404 Not Found` | Shapes graph does not exist | `shapes_graph_not_found` |
| `404 Not Found` | Data graph does not exist | `data_graph_not_found` |
| `406 Not Acceptable` | Requested RDF format not available | `format_not_available` |
| `409 Conflict` | Result graph exists (overwrite=false) | `graph_exists` |
| `422 Unprocessable Entity` | Invalid SHACL shapes graph | `invalid_shapes` |
| `422 Unprocessable Entity` | Remote endpoint unreachable | `remote_endpoint_error` |

### Server Errors

| Code | Scenario | Error Code |
|------|----------|------------|
| `500 Internal Server Error` | Validation engine failure | `validation_error` |
| `503 Service Unavailable` | Remote endpoint timeout | `remote_timeout` |

---

## 7. Error Format (RFC 7807)

All errors use `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Shapes Graph Not Found",
  "status": 404,
  "code": "shapes_graph_not_found",
  "detail": "The shapes graph 'http://example.org/shapes' does not exist in dataset 'schema-registry'",
  "instance": "/mydata/shacl",
  "invalidParams": [
    {
      "name": "shapes.graph",
      "reason": "Graph not found in specified dataset"
    }
  ]
}
```

**Canonical `code` values:**

- `invalid_request`
- `invalid_graph_reference`
- `selector_conflict`
- `dataset_not_found`
- `shapes_graph_not_found`
- `data_graph_not_found`
- `format_not_available`
- `graph_exists`
- `invalid_shapes`
- `remote_endpoint_error`
- `validation_error`
- `remote_timeout`

---

## 8. Validation Report Format

Validation reports MUST conform to the [SHACL specification](https://www.w3.org/TR/shacl/#validation-report).

**Minimal conforming report:**

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

[] a sh:ValidationReport ;
   sh:conforms true .
```

**Report with violations:**

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix ex: <http://example.org/> .

[] a sh:ValidationReport ;
   sh:conforms false ;
   sh:result [
     a sh:ValidationResult ;
     sh:resultSeverity sh:Violation ;
     sh:focusNode ex:Alice ;
     sh:resultPath ex:age ;
     sh:value "thirty" ;
     sh:resultMessage "Value 'thirty' is not an integer" ;
     sh:sourceConstraintComponent sh:DatatypeConstraintComponent ;
     sh:sourceShape ex:PersonShape
   ] .
```

**Extended metadata** (server-specific):

Servers MAY add metadata to reports:

```turtle
[] a sh:ValidationReport ;
   sh:conforms false ;
   # Standard SHACL properties
   sh:result [ ... ] ;
   # Server extensions
   ex:validatedAt "2025-01-15T10:30:00Z"^^xsd:dateTime ;
   ex:shapesSource "http://example.org/schemas/person" ;
   ex:dataSource "http://example.org/users" ;
   ex:validationDuration "PT0.234S"^^xsd:duration .
```

---

## 9. Security Considerations

### 9.1 Access Control

- **Shapes graphs**: Servers SHOULD enforce read permissions on shapes graphs
- **Data graphs**: Servers MUST enforce read permissions on data graphs
- **Result storage**: Servers MUST enforce write permissions on result graphs
- **Remote endpoints**: Servers SHOULD validate and sanitize remote endpoint URLs

### 9.2 Denial of Service

- **Resource limits**: Servers SHOULD limit validation complexity (graph size, shape count)
- **Timeouts**: Servers SHOULD enforce validation timeouts (recommended: 30 seconds)
- **Remote endpoints**: Servers SHOULD cache remote shapes graphs and apply timeouts

### 9.3 Information Disclosure

- **Error messages**: Error messages MUST NOT disclose sensitive data from graphs
- **Validation reports**: Reports SHOULD only include information necessary for debugging
- **Graph existence**: Servers MAY return `403 Forbidden` instead of `404 Not Found` for access-controlled graphs

### 9.4 Remote Endpoint Security

- **URL validation**: Servers MUST validate remote endpoint URLs (protocol, domain)
- **SSRF prevention**: Servers SHOULD block requests to private IP ranges
- **TLS verification**: Servers MUST verify TLS certificates for HTTPS endpoints
- **Authentication**: Servers MAY support authentication for remote endpoints

---

## 10. Version Control Integration

When used with version-controlled datasets (see SPARQL 1.2 Protocol – Version Control Extension):

### 10.1 Selector Semantics

- **Branch selector**: Validates against current branch head (mutable reference)
- **Commit selector**: Validates against immutable commit snapshot
- **AsOf selector**: Validates against dataset state at specified timestamp (inclusive)

### 10.2 Result Storage Commits

When `results.store` is specified and the target dataset is version-controlled:

- Storing results creates a new commit
- Commit message: `"SHACL validation: {conforms/violations} against {shapes-graph}"`
- Author: From `SPARQL-VC-Author` header or default
- Response includes `commitId` in body and `ETag` header

### 10.3 Eventual Consistency

Write operations (storing results) follow CQRS pattern:

- `202 Accepted` returned immediately after event published to Kafka
- Read model updated asynchronously (typically 50-200ms)
- Clients can query results using:
  - `?commit={commitId}` for immediate access (materialized synchronously)
  - `?branch={name}` after projection completes

---

## 11. Interoperability

### 11.1 Fuseki Compatibility Mode

For compatibility with Apache Jena Fuseki SHACL endpoint:

**POST** `/{dataset}/shacl?graph={graphName}`

- Request body: Shapes graph (Turtle, JSON-LD, etc.)
- Response: Validation report
- Equivalent to:

```json
{
  "shapes": {
    "source": "inline",
    "data": "<request-body>"
  },
  "data": {
    "source": "local",
    "dataset": "{dataset}",
    "graphs": ["{graphName}"]
  }
}
```

### 11.2 SPARQL Protocol Integration

Validation results can be queried via SPARQL:

```sparql
# Query stored validation reports
PREFIX sh: <http://www.w3.org/ns/shacl#>

SELECT ?report ?conforms ?violationCount
WHERE {
  GRAPH <http://example.org/reports/2025-01-15> {
    ?report a sh:ValidationReport ;
            sh:conforms ?conforms .
    OPTIONAL {
      SELECT (COUNT(?result) AS ?violationCount)
      WHERE {
        ?report sh:result ?result .
        ?result sh:resultSeverity sh:Violation .
      }
    }
  }
}
```

### 11.3 Graph Store Protocol Integration

Validation result graphs are standard RDF graphs:

- **GET** `/{dataset}/data?graph={reportGraph}` – Retrieve report
- **DELETE** `/{dataset}/data?graph={reportGraph}` – Delete report
- **PUT** `/{dataset}/data?graph={reportGraph}` – Replace report (not recommended)

---

## 12. Implementation Notes

### 12.1 Performance Optimization

**Shapes graph caching:**
- Cache parsed shapes graphs (especially from remote endpoints)
- Invalidate cache when shapes graph changes (version-controlled datasets)
- Cache key: `{dataset}:{graph}:{selector}:{etag}`

**Incremental validation:**
- For version-controlled datasets, validate only changed triples
- Use RDFPatch to identify changes between commits
- Useful for continuous integration workflows

**Parallel validation:**
- When `validateGraphs = "separately"`, validate graphs in parallel
- Thread pool size configurable per server capacity

### 12.2 Validation Report Storage

**Graph naming conventions:**
- `http://example.org/reports/{dataset}/{YYYY-MM-DD}` – Daily reports
- `http://example.org/reports/{dataset}/{commit-id}` – Per-commit reports
- `http://example.org/reports/{dataset}/{shapes-name}/{timestamp}` – Detailed tracking

**Metadata graphs:**
- Store validation metadata separately from reports
- Link reports to shapes version, data version, validation timestamp

**Retention policies:**
- Implement retention policies for historical reports
- Archive or delete reports older than N days

### 12.3 Error Handling

**Partial failures:**
- If validating multiple graphs and one fails, return partial results
- Include error information in response body

**Remote endpoint failures:**
- Implement retry logic with exponential backoff
- Cache last successful fetch of remote shapes
- Fail gracefully with descriptive error messages

---

## 13. ABNF Grammar

```abnf
; Graph reference
graph-ref       = local-graph-ref / remote-graph-ref / inline-ref

local-graph-ref = dataset-ref graph-uri [selector]
dataset-ref     = "dataset" "=" dataset-name
graph-uri       = "graph" "=" ( iri / "default" / "union" )
selector        = branch-sel / commit-sel / asof-sel

branch-sel      = "branch" "=" branch-name
commit-sel      = "commit" "=" commit-id
asof-sel        = "asOf" "=" rfc3339-timestamp

remote-graph-ref = endpoint-ref graph-uri
endpoint-ref    = "endpoint" "=" iri

inline-ref      = "source" "=" "inline" "data" "=" rdf-content

; Identifiers (from Version Control Extension)
dataset-name    = 1*249identifier-char
branch-name     = 1*255identifier-char
commit-id       = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG
identifier-char = ALPHA / DIGIT / "." / "_" / "-"

; Validation options
validate-mode   = "separately" / "merged" / "dataset"
severity-level  = "Violation" / "Warning" / "Info"
```

---

## 14. JSON Schema (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["shapes", "data"],
  "properties": {
    "shapes": {
      "type": "object",
      "required": ["source"],
      "properties": {
        "source": {
          "type": "string",
          "enum": ["inline", "local", "remote"]
        },
        "dataset": {
          "type": "string",
          "pattern": "^[A-Za-z0-9._-]{1,249}$"
        },
        "graph": {
          "type": "string"
        },
        "endpoint": {
          "type": "string",
          "format": "uri"
        },
        "data": {
          "type": "string"
        },
        "branch": {
          "type": "string",
          "pattern": "^[A-Za-z0-9._-]{1,255}$"
        },
        "commit": {
          "type": "string",
          "pattern": "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$"
        },
        "asOf": {
          "type": "string",
          "format": "date-time"
        }
      },
      "oneOf": [
        {
          "properties": {
            "source": {"const": "inline"}
          },
          "required": ["data"]
        },
        {
          "properties": {
            "source": {"const": "local"}
          },
          "required": ["dataset", "graph"]
        },
        {
          "properties": {
            "source": {"const": "remote"}
          },
          "required": ["endpoint", "graph"]
        }
      ]
    },
    "data": {
      "type": "object",
      "required": ["source", "graphs"],
      "properties": {
        "source": {
          "type": "string",
          "enum": ["local", "remote"]
        },
        "dataset": {
          "type": "string",
          "pattern": "^[A-Za-z0-9._-]{1,249}$"
        },
        "graphs": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "minItems": 1
        },
        "endpoint": {
          "type": "string",
          "format": "uri"
        },
        "branch": {
          "type": "string",
          "pattern": "^[A-Za-z0-9._-]{1,255}$"
        },
        "commit": {
          "type": "string",
          "pattern": "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$"
        },
        "asOf": {
          "type": "string",
          "format": "date-time"
        }
      }
    },
    "options": {
      "type": "object",
      "properties": {
        "validateGraphs": {
          "type": "string",
          "enum": ["separately", "merged", "dataset"],
          "default": "separately"
        },
        "targetNode": {
          "type": "string",
          "format": "uri"
        },
        "severity": {
          "type": "string",
          "enum": ["Violation", "Warning", "Info"]
        }
      }
    },
    "results": {
      "type": "object",
      "properties": {
        "return": {
          "type": "boolean",
          "default": true
        },
        "store": {
          "type": "object",
          "required": ["dataset", "graph"],
          "properties": {
            "dataset": {
              "type": "string",
              "pattern": "^[A-Za-z0-9._-]{1,249}$"
            },
            "graph": {
              "type": "string"
            },
            "overwrite": {
              "type": "boolean",
              "default": false
            }
          }
        }
      }
    }
  }
}
```

---

## 15. Example Implementations

### 15.1 Client Example (Python)

```python
import requests

# Cross-dataset validation with result storage
payload = {
    "shapes": {
        "source": "local",
        "dataset": "schema-registry",
        "graph": "http://example.org/schemas/product-v2",
        "branch": "main"
    },
    "data": {
        "source": "local",
        "dataset": "product-catalog",
        "graphs": ["union"]
    },
    "results": {
        "return": True,
        "store": {
            "dataset": "validation-reports",
            "graph": "http://example.org/reports/2025-01-15",
            "overwrite": True
        }
    }
}

response = requests.post(
    "http://localhost:8080/product-catalog/shacl",
    json=payload,
    headers={
        "Accept": "text/turtle",
        "SPARQL-VC-Author": "QA Bot <qa@example.org>"
    }
)

if response.status_code == 202:
    commit_id = response.json()["commitId"]
    print(f"Validation stored in commit {commit_id}")
elif response.status_code == 200:
    report = response.text
    print(report)
```

### 15.2 Server Pseudocode

```java
@PostMapping("/{dataset}/shacl")
public ResponseEntity<?> validateShacl(
    @PathVariable String dataset,
    @RequestBody ValidationRequest request
) {
    // 1. Resolve shapes graph
    Graph shapesGraph = resolveShapesGraph(request.getShapes());

    // 2. Resolve data graph(s)
    List<Graph> dataGraphs = resolveDataGraphs(request.getData());

    // 3. Perform validation
    ValidationReport report = validateGraphs(
        shapesGraph,
        dataGraphs,
        request.getOptions()
    );

    // 4. Handle results
    if (request.getResults().isStore()) {
        // Store asynchronously via CQRS
        String commitId = storeValidationReport(
            request.getResults().getStore(),
            report
        );

        return ResponseEntity.accepted()
            .header("ETag", commitId)
            .body(Map.of(
                "commitId", commitId,
                "conforms", report.conforms()
            ));
    } else {
        // Return validation report
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/turtle"))
            .body(serializeReport(report));
    }
}
```

---

## 16. Future Extensions

### 16.1 Batch Validation

Validate multiple datasets in a single request:

```json
{
  "batch": [
    {
      "shapes": {...},
      "data": {...},
      "results": {...}
    },
    {
      "shapes": {...},
      "data": {...},
      "results": {...}
    }
  ]
}
```

### 16.2 Webhook Notifications

Notify external systems when validation completes:

```json
{
  "shapes": {...},
  "data": {...},
  "results": {...},
  "webhook": {
    "url": "https://example.org/validation-complete",
    "method": "POST",
    "headers": {
      "Authorization": "Bearer ..."
    }
  }
}
```

### 16.3 Scheduled Validation

Create recurring validation jobs:

```json
{
  "schedule": "0 2 * * *",  // cron expression
  "shapes": {...},
  "data": {...},
  "results": {...}
}
```

---

## 17. References

- [SHACL Specification](https://www.w3.org/TR/shacl/)
- [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/)
- [SPARQL 1.2 Graph Store Protocol](https://www.w3.org/TR/sparql12-graph-store-protocol/)
- [SPARQL 1.2 Protocol – Version Control Extension](./SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [RFC 7807 – Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc7807.html)
- [Apache Jena SHACL](https://jena.apache.org/documentation/shacl/)

---

## Appendix A: Comparison with Fuseki

| Feature | Fuseki SHACL Endpoint | This Protocol |
|---------|----------------------|---------------|
| Shapes source | Inline only | Inline, local graphs, remote endpoints |
| Data source | Current dataset only | Multi-dataset, remote endpoints |
| Result storage | Return only | Return and/or store in any graph |
| Version control | Not supported | Full branch/commit/asOf support |
| Cross-dataset | Not supported | Validate across datasets |
| Historical validation | Not supported | Validate against historical states |
| Validation modes | Single graph | Separate, merged, or dataset-level |
| Result persistence | Not supported | Store for trend analysis |

---

## Appendix B: Complete Example Workflow

**Scenario:** Continuous data quality monitoring

**Setup:**
1. `schema-registry` dataset: Contains SHACL shapes for different data types
2. `production-db` dataset: Production data with frequent updates
3. `qa-reports` dataset: Historical validation reports

**Daily validation job:**

```bash
curl -X POST http://localhost:8080/production-db/shacl \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: QA Bot <qa@example.org>" \
  -d '{
    "shapes": {
      "source": "local",
      "dataset": "schema-registry",
      "graph": "http://example.org/schemas/user-data-v3",
      "branch": "main"
    },
    "data": {
      "source": "local",
      "dataset": "production-db",
      "graphs": ["union"],
      "branch": "main"
    },
    "options": {
      "validateGraphs": "merged"
    },
    "results": {
      "return": false,
      "store": {
        "dataset": "qa-reports",
        "graph": "http://example.org/reports/daily/2025-01-15",
        "overwrite": false
      }
    }
  }'
```

**Trend analysis query:**

```sparql
PREFIX sh: <http://www.w3.org/ns/shacl#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT ?date (COUNT(?violation) AS ?violationCount)
WHERE {
  GRAPH ?reportGraph {
    ?report a sh:ValidationReport ;
            sh:result ?violation .
    ?violation sh:resultSeverity sh:Violation .
  }

  BIND(REPLACE(STR(?reportGraph),
       "http://example.org/reports/daily/", "") AS ?date)
}
GROUP BY ?date
ORDER BY ?date
```

**Result:** Track data quality trends over time, alert on regressions.

---

**End of Specification**
