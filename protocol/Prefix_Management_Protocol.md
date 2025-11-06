# Prefix Management Protocol (PMP) for SPARQL Services

**Version:** 1.0
**Status:** Draft
**Date:** 2025-11-06

---

## 1. Introduction

This document defines an HTTP-based protocol for managing prefix declarations associated with a SPARQL dataset.

SPARQL treats `PREFIX` declarations as query-local syntax and does not define a means to retrieve or modify server-side prefix mappings. Many SPARQL servers (e.g., Apache Jena Fuseki, Virtuoso) maintain prefix maps that are used when serializing RDF data (e.g., Turtle, JSON-LD). This protocol exposes those mappings in a simple, REST-style way so that SPARQL clients, UI editors, and administrative tools can synchronize their prefix lists with the server.

**Design Goals:**
- Simple REST semantics (GET, PUT, PATCH, DELETE)
- Implementation-agnostic (works for any SPARQL server)
- Integrates naturally with RDFPatch for transactional consistency
- Supports both versioned and non-versioned implementations

**Scope:** This protocol manages **dataset-level prefix maps only**. Graph-level prefix maps are not supported due to RDFPatch constraints.

---

## 2. Terminology

- **Prefix map**: An ordered mapping from a short prefix name (e.g., `rdf`) to an absolute IRI (e.g., `http://www.w3.org/1999/02/22-rdf-syntax-ns#`)
- **Dataset-level prefix map**: The prefix map associated with a SPARQL dataset as a whole
- **Service endpoint**: Base URL where this protocol is available (deployment-specific)

---

## 3. Service Endpoint

The Prefix Management Protocol is exposed at:

```
{dataset-base}/prefixes
```

**Example:**
```
https://example.org/sparql/myDataset/prefixes
```

The exact URL structure is deployment-specific.

---

## 4. Representation Format

### 4.1 Media Type

Servers **MUST** support `application/json`.

### 4.2 JSON Structure

**Request body (PUT/PATCH):**
```json
{
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

**Optional: Include commit message (for version-controlled servers):**
```json
{
  "message": "Add FOAF ontology prefixes",
  "prefixes": {
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

**Response body (GET):**
```json
{
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
  }
}
```

---

## 5. Operations

### 5.1 GET - Retrieve Prefix Map

**Request:**
```http
GET /prefixes HTTP/1.1
Accept: application/json
```

**Response:**
- `200 OK` - Returns current prefix map as JSON
- `404 Not Found` - Dataset does not exist

**Example:**
```http
GET /myDataset/prefixes

200 OK
Content-Type: application/json

{
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

---

### 5.2 PUT - Replace Entire Prefix Map

**Semantics:** Replace the entire prefix map with the provided mappings. Any existing prefix not included in the request body is removed.

**Request:**
```http
PUT /prefixes HTTP/1.1
Content-Type: application/json

{
  "prefixes": {
    "ex": "http://example.org/",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  }
}
```

**Response:**
- `204 No Content` - Prefix map updated in-place (non-versioned server)
- `201 Created` - Commit created (version-controlled server)
- `400 Bad Request` - Invalid prefix name or IRI
- `404 Not Found` - Dataset does not exist

**Version-controlled server response:**
```http
201 Created
Location: /version/datasets/myDataset/commits/01JCDN...
ETag: "01JCDN..."
Content-Type: application/json

{
  "commitId": "01JCDN...",
  "message": "Replace prefix map"
}
```

---

### 5.3 PATCH - Add or Update Selected Prefixes

**Semantics:** Add new prefixes or update existing ones. Prefixes not mentioned in the request remain unchanged.

**Request:**
```http
PATCH /prefixes HTTP/1.1
Content-Type: application/json

{
  "prefixes": {
    "geo": "http://www.opengis.net/ont/geosparql#",
    "dct": "http://purl.org/dc/terms/"
  }
}
```

**Response:**
- `204 No Content` - Prefixes updated in-place (non-versioned server)
- `201 Created` - Commit created (version-controlled server)
- `400 Bad Request` - Invalid prefix name or IRI
- `404 Not Found` - Dataset does not exist

---

### 5.4 DELETE - Remove Prefixes

**Semantics:** Remove one or more prefixes from the prefix map.

**Request:**
```http
DELETE /prefixes?prefix=ex&prefix=temp HTTP/1.1
```

**Query parameters:**
- `prefix` - Name of prefix to remove (can be repeated for multiple prefixes)

**Response:**
- `204 No Content` - Prefixes removed in-place (non-versioned server)
- `201 Created` - Commit created (version-controlled server)
- `404 Not Found` - Dataset does not exist

**Note:** If a specified prefix does not exist, servers SHOULD ignore it silently (idempotent operation).

---

## 6. Prefix Name and IRI Constraints

### 6.1 Prefix Name Validation

Prefix names **MUST** conform to SPARQL `PNAME_NS` rules (the part before the colon in `PREFIX rdf:`).

**Valid examples:** `rdf`, `foaf`, `ex`, `my-ontology`
**Invalid examples:** `1foo`, `rdf:`, `http://example.org/`

### 6.2 IRI Validation

IRIs **MUST** be absolute IRIs (not relative).

**Valid:** `http://example.org/`, `https://xmlns.com/foaf/0.1/`
**Invalid:** `../relative`, `example.org` (missing scheme)

### 6.3 Error Response

If validation fails, return:

```http
400 Bad Request
Content-Type: application/json

{
  "error": "InvalidPrefixName",
  "message": "Prefix name '1foo' does not match SPARQL PNAME_NS rules"
}
```

---

## 7. RDFPatch Integration

### 7.1 Background

[RDFPatch](https://afs.github.io/rdf-patch/) is a standardized format for representing changes to RDF datasets. It includes directives for prefix management:
- `PA prefix: <IRI>` - Add prefix
- `PD prefix:` - Delete prefix

### 7.2 Implementation Guidance

Servers that use RDFPatch **SHOULD** translate prefix operations to PA/PD directives within transactions.

**Example: PUT operation**

Request:
```json
{
  "prefixes": {
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

Translates to RDFPatch:
```
TX .
PD rdf: .
PD rdfs: .
PA foaf: <http://xmlns.com/foaf/0.1/> .
TC .
```

**Benefits:**
- Transactional consistency (all-or-nothing)
- Atomic with RDF data changes
- Automatic versioning (if server supports version control)
- Merge/replay capabilities

---

## 8. Version-Controlled Implementations

### 8.1 Behavior

Servers with version control (branches, commits, etc.) **SHOULD**:
- Treat prefix modifications as commit-creating operations
- Return `201 Created` (instead of `204 No Content`)
- Include `Location` header pointing to created commit
- Include commit metadata in response body

### 8.2 Response Format

```http
201 Created
Location: /version/datasets/myDataset/commits/01JCDN...
ETag: "01JCDN..."
Content-Type: application/json

{
  "commitId": "01JCDN...",
  "message": "Add geospatial prefixes",
  "author": "Alice <alice@example.org>",
  "timestamp": "2025-11-06T10:30:00Z"
}
```

### 8.3 Commit Messages

Version-controlled servers **MAY** accept an optional `message` field:

```json
{
  "message": "Add FOAF ontology prefixes",
  "prefixes": {
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

If not provided, servers should generate a default message (e.g., "Update prefixes").

### 8.4 Time-Travel and History

Version-controlled servers **MAY** expose additional endpoints for:
- Retrieving prefixes at specific commits
- Viewing prefix change history
- Comparing prefix maps between branches

These capabilities are **implementation-specific** and not defined in this protocol.

---

## 9. Error Conditions

| Status Code | Meaning |
|-------------|---------|
| `200 OK` | GET successful |
| `201 Created` | Modification created commit (versioned) |
| `204 No Content` | Modification applied in-place (non-versioned) |
| `400 Bad Request` | Invalid prefix name, IRI, or malformed JSON |
| `404 Not Found` | Dataset or graph does not exist |
| `415 Unsupported Media Type` | Client sent non-JSON content |
| `500 Internal Server Error` | Server-side storage failure |

---

## 10. Security Considerations

### 10.1 Authentication

Servers **SHOULD** require authentication for write operations (PUT, PATCH, DELETE).

### 10.2 Authorization

Servers **SHOULD** enforce authorization policies:
- Who can read prefix maps?
- Who can modify prefix maps?
- Are certain prefixes protected from deletion?

### 10.3 Injection Attacks

Servers **MUST** validate:
- Prefix names conform to PNAME_NS rules
- IRIs are well-formed absolute IRIs
- JSON structure is valid

Reject malicious input before processing.

---

## 11. Interaction with SPARQL and GSP

### 11.1 Query Behavior

This protocol **does NOT change** how SPARQL queries are parsed or executed. A query without `PREFIX` declarations still requires full IRIs.

**Server behavior is implementation-specific:**
- Some servers may inject prefixes into queries automatically
- Some servers only use prefixes for serialization
- Clients should not assume automatic prefix injection

### 11.2 Serialization

Servers **MAY** use the prefix map when serializing RDF data (e.g., Turtle responses from Graph Store Protocol). This improves readability but is not required.

### 11.3 Coexistence

This protocol can coexist with SPARQL Protocol and Graph Store Protocol on the same dataset:
- SPARQL Protocol: `/sparql`
- Graph Store Protocol: `/data`
- Prefix Management: `/prefixes`

---

## 12. Conformance Levels

### Level 1: Read-Only (Minimal)

- **MUST** support `GET /prefixes`
- **MUST** return JSON with `prefixes` object
- **MUST** validate prefix names and IRIs

### Level 2: Read/Write (Full)

- All of Level 1
- **MUST** support `PUT /prefixes`
- **MUST** support `PATCH /prefixes`
- **SHOULD** support `DELETE /prefixes?prefix=...`
- **SHOULD** support RDFPatch integration (PA/PD directives)

---

## 13. Examples

### Example 1: Non-Versioned Server (Fuseki-like)

```http
GET /myDataset/prefixes
→ 200 OK
  { "prefixes": { "rdf": "..." } }

PUT /myDataset/prefixes
  { "prefixes": { "foaf": "..." } }
→ 204 No Content
```

### Example 2: Version-Controlled Server (CHUCC-like)

```http
GET /version/datasets/mydata/branches/main/prefixes
→ 200 OK
  { "prefixes": { "rdf": "..." } }

PUT /version/datasets/mydata/branches/main/prefixes
  { "message": "Add FOAF", "prefixes": { "foaf": "..." } }
→ 201 Created
  Location: /version/datasets/mydata/commits/01JCDN...
  { "commitId": "01JCDN...", "message": "Add FOAF" }
```

---

## 14. References

- [SPARQL 1.1 Query Language](https://www.w3.org/TR/sparql11-query/)
- [SPARQL 1.1 Graph Store HTTP Protocol](https://www.w3.org/TR/sparql11-http-rdf-update/)
- [RDFPatch](https://afs.github.io/rdf-patch/)
- [RFC 7231: HTTP/1.1 Semantics and Content](https://tools.ietf.org/html/rfc7231)
- [RFC 3987: Internationalized Resource Identifiers (IRIs)](https://tools.ietf.org/html/rfc3987)

---

## Appendix A: Comparison with Original Proposal

This protocol simplifies the original [non-standard draft](./Prefix_Management_Protocol_for_SPARQL_Services.md) by:

1. **Removed graph-level support** - Dataset-level only (RDFPatch constraint)
2. **Removed capabilities endpoint** - Clients can probe with OPTIONS or try operations
3. **Removed custom media type** - Standard `application/json` sufficient
4. **Removed scope field** - Always dataset-level, no ambiguity
5. **Removed ETag concurrency** - Optional for implementations, not core protocol
6. **Simplified error format** - Implementation-specific (use RFC 7807 recommended)
7. **Added RDFPatch integration** - Natural fit for transactional servers
8. **Added version control notes** - Guidance for versioned implementations

**Result:** 70% shorter protocol with broader applicability.

---

## Appendix B: Future Extensions

Potential future extensions (not part of v1.0):

- **Prefix suggestions** - Analyze dataset and suggest common prefixes
- **Bulk operations** - Upload/download entire prefix catalogs
- **Prefix validation** - Check for conflicts or duplicate namespaces
- **Prefix templates** - Share prefix sets across datasets
- **Federation** - Discover prefixes from federated endpoints

---

**End of Prefix Management Protocol v1.0**
