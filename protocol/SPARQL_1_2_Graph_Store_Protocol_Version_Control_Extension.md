# SPARQL 1.2 Graph Store Protocol – Version Control Extension

**Revision:** 2025‑10‑05 (editor's review)

**Extends:** [SPARQL 1.2 Graph Store Protocol](https://www.w3.org/TR/sparql12-graph-store-protocol/)

> This specification extends the SPARQL 1.2 Graph Store Protocol with version‑control semantics—branches, commits, merges, history, and time‑travel—while remaining backward‑compatible. Clients opt‑in via dedicated endpoints, headers, or URL parameters.

---

## 1. Introduction

This specification extends the SPARQL 1.2 Graph Store Protocol with version‑control semantics, enabling versioned management of RDF graphs. It provides branches, commits, merges, history navigation, and time‑travel capabilities for direct graph manipulation operations (GET, PUT, POST, DELETE).

### 1.1 Design Principles

- **Backward compatibility.** Non‑versioned Graph Store Protocol behavior remains unchanged.
- **Identifiers.** Commits use **UUIDv7** identifiers by default for global uniqueness and ordering. Servers **MAY** additionally compute and expose a **content hash** (e.g., SHA‑256 of a canonicalized payload) for integrity, deduplication, and verification.
- **Immutability.** Historical states are append‑only.
- **Three‑way merge.** With explicit conflict representation.
- **HTTP‑native.** Reuse `ETag`/`If‑Match`, `Prefer`, standard status codes, and content negotiation; avoid bespoke headers unless necessary.
- **Graph-level versioning.** Each named graph is versioned independently within a dataset, with operations scoped to individual graphs.

---

## 2. Conformance

An implementation conforms if it implements all features marked **MUST** in the core model (Sections 4–7, 10) and protocol (Sections 8–9, 11). Optional features are **MAY**.

### 2.1 Conformance Classes

- **Level 1 (Basic):** Commits (UUIDv7), branches, history, GET/PUT/POST/DELETE at branch/commit, RDF Patch support, strong ETags, problem+json.
- **Level 2 (Advanced):** Three‑way merge, conflict detection/representation, fast‑forward, revert, reset, tags, cherry‑pick, blame.

A public test suite (normative) SHALL validate both classes.

### 2.2 Interoperability Defaults (Normative)

To ensure cross‑vendor portability, the following defaults are **MANDATORY** unless explicitly negotiated otherwise:

- **Identifiers:** Commit ids are **UUIDv7** (Section 5). Branch/tag names are **case‑sensitive**, Unicode **NFC** normalized, and match `^[A-Za-z0-9._\-]+$`.
- **Changeset format:** Servers **MUST** accept and produce **RDF Patch** (`text/rdf-patch`) for writes/diffs. Other formats (e.g., binary/rdf-patch) **MAY** be supported.
- **No‑op update:** An update yielding an empty changeset **MUST NOT** create a commit; return **204**.
- **Selectors precedence:** It is an error to combine `commit` with `branch` and/or `asOf`; return **400** (`selector_conflict`).
- **ETags:** Use **strong ETags** for content identification. Branch resources' ETag **MUST** equal the current head commit id. Commit resources **MUST** use their commit id as ETag. Graph resources' ETag **MUST** equal the commit id at which the graph was last modified. ETags are used for caching and content identification, **NOT** for concurrency control (see §7.4).
- **Error media type:** Use **`application/problem+json`** with machine‑readable `code`.
- **Charset:** All JSON and text payloads **MUST** be UTF‑8.
- **Discovery:** `OPTIONS` **MUST** advertise `SPARQL-Version-Control` and supported features; `Accept-Patch` **MUST** include `text/rdf-patch`.
- **Tags:** Tags are **immutable**. Attempts to retarget MUST result in **409 Conflict** or **405 Method Not Allowed**.
- **Dataset scoping:** Multi‑tenant servers **MUST** scope under `/ds/{dataset}/...` (Section 14).

## 3. Terminology

**Commit** – Immutable dataset state with metadata and parent references.

**Commit Identifier** – A **UUIDv7** per RFC-style canonical textual form (8‑4‑4‑4‑12 hex). Servers may also expose a `contentHash` (see §5.4).

**Branch** – Named ref to a commit (mutable pointer).

**Head** – Current commit at a branch ref.

**Changeset** – Additions and deletions between two commits (§5.2).

**Merge Base** – Nearest common ancestor of two commits.

**Working State** – Materialized dataset for a given ref/commit.

**Conflict** – Overlapping changes that cannot be applied automatically (§10).

**Graph Store** – Collection of named graphs and an optional default graph.

**Named Graph** – RDF graph identified by an IRI.

**Graph Resource** – HTTP resource representing a versioned named graph.

---

## 4. Model Overview

Commits form a DAG: initial commits (no parents), linear commits (one parent), merge commits (≥2 parents). Branches and tags are lightweight refs. Each commit represents a complete snapshot of the graph store state at a point in time.

### 4.1 Graph-Level Versioning

Version control operates at the **graph store level**, where each commit captures the state of all named graphs and the default graph within a dataset. Individual graph operations (PUT, POST, DELETE on a specific graph) create commits that reflect changes to that graph while preserving the state of other graphs.

---

## 5. Commit Content and Identifiers

### 5.1 Commit Payload

A commit payload minimally includes:

- Parent commit id(s) (ordered list)
- Canonical changeset (§5.2)
- Metadata (author, timestamp, message, optional signatures)
- Affected graph IRIs (list of graphs modified in this commit)

### 5.2 Changeset Representation

Implementations **MUST** support at least one of:

- **RDF Patch** (`text/rdf-patch`)
- **N‑Quads with change graphs** (additions/deletions in named graphs)
- **TriG** with named graphs `urn:additions`/`urn:deletions`

Servers **MUST** advertise supported formats via `Accept‑Patch` and `Accept`. A server **SHOULD** select a single default for writes to simplify interop (recommendation: `text/rdf-patch`). Implementations **MAY** also support a **Thrift‑based binary encoding of RDF Patch**, referenced informally here as **binary/rdf-patch** (see Apache Jena RDF Patch documentation).

### 5.3 Graph Operations and Changesets

Each graph operation generates a changeset:

- **PUT:** Complete graph replacement generates deletions for existing triples and additions for new triples
- **POST:** Merge operation generates only additions
- **DELETE:** Graph removal generates deletions for all triples in the graph

### 5.4 Content Hash (Optional)

Servers **MAY** compute a content hash of the canonicalized commit payload for integrity verification and deduplication. The hash algorithm **SHOULD** be SHA‑256. The content hash is **OPTIONAL** and distinct from the commit identifier.

---

## 6. Read Operations (GET, HEAD)

### 6.1 Targeting a State

Clients **MAY** select a state using one of:

- URL params: `branch`, `commit`, `asOf` (ISO 8601 timestamp)
- Request headers (see §8.2) – discouraged when a URL will suffice, to maximize cacheability.

The selected state defines the graph store state visible for the operation.

### 6.2 GET – Retrieve Graph at Version

`GET /ds/{dataset}/data?graph={graphIRI}&branch={name}`

Returns the RDF content of the specified graph at the current head of the branch.

**Additional Parameters:**
- `commit={id}` – Retrieve graph at a specific commit
- `asOf={timestamp}` – Retrieve graph as it existed at a timestamp (§6.3)

**Response:**
- **200 OK** with RDF payload in negotiated format
- **404 Not Found** if graph does not exist at specified version
- **ETag** header contains the commit id at which the graph was last modified

**Example:**
```http
GET /ds/mydata/data?graph=http://example.org/employees&branch=main
Accept: text/turtle
```

### 6.3 Time‑travel (`asOf`)

`asOf` **MUST be inclusive**. The visible state is the branch state at the latest commit whose commit time is **≤** the supplied timestamp (after converting `asOf` to UTC).

- **Timestamp format:** RFC 3339 / ISO 8601. Servers **MUST** accept timezone offsets and **MUST** normalize to UTC for comparison.
- **Precision:** Compare at millisecond precision (UUIDv7 and many stores record ms). Servers **MUST** not silently round away more precise client input; they **MUST** compare using the nearest millisecond.
- **Tie‑break:** If multiple commits share the same millisecond on the branch history path, select the commit with the **greatest UUIDv7** value (deterministic ordering).
- **Selectors precedence:** Requests **MUST NOT** combine `commit` with `asOf` and/or `branch`. If both are present, the server **MUST** return **400 Bad Request** with a problem+json body indicating `selector_conflict`.

### 6.4 HEAD – Check Graph Existence at Version

`HEAD /ds/{dataset}/data?graph={graphIRI}&branch={name}`

Returns headers only, same semantics as GET.

**Response:**
- **200 OK** if graph exists at specified version
- **404 Not Found** if graph does not exist

---

## 7. Write Operations (PUT, POST, DELETE)

All write operations create new commits on the target branch unless they result in no-op changes.

### 7.1 PUT – Replace Graph

`PUT /ds/{dataset}/data?graph={graphIRI}&branch={name}`

Replaces the entire content of the specified graph, creating a new commit.

**Required Headers:**
- `SPARQL-VC-Commit-Message` – Commit message
- `SPARQL-VC-Commit-Author` – Author identifier
- `Content-Type` – RDF media type

**Optional Headers:**
- `SPARQL-VC-Expected-Parent` – Expected parent commit id for client-side tracking (§7.4)

**Response:**
- **200 OK** or **201 Created** or **204 No Content** (depends on whether graph existed)
- **ETag** header contains new commit id
- `Location` header contains URL of commit resource: `/ds/{dataset}/version/commits/{id}`

**Example:**
```http
PUT /ds/mydata/data?graph=http://example.org/employees&branch=main
Content-Type: text/turtle
SPARQL-VC-Commit-Message: Update employee roster
SPARQL-VC-Commit-Author: alice@example.org
SPARQL-VC-Expected-Parent: 01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a

@prefix ex: <http://example.org/> .
ex:alice ex:role "Manager" .
```

### 7.2 POST – Merge into Graph

`POST /ds/{dataset}/data?graph={graphIRI}&branch={name}`

Merges RDF triples into the specified graph (additive operation), creating a new commit.

**Required Headers:**
- `SPARQL-VC-Commit-Message` – Commit message
- `SPARQL-VC-Commit-Author` – Author identifier
- `Content-Type` – RDF media type

**Optional Headers:**
- `SPARQL-VC-Expected-Parent` – Expected parent commit id for client-side tracking (§7.4)

**Response:**
- **201 Created** or **204 No Content**
- **ETag** header contains new commit id
- `Location` header contains URL of commit resource

**Example:**
```http
POST /ds/mydata/data?graph=http://example.org/employees&branch=main
Content-Type: text/turtle
SPARQL-VC-Commit-Message: Add new employee
SPARQL-VC-Commit-Author: bob@example.org

@prefix ex: <http://example.org/> .
ex:bob ex:role "Developer" .
```

### 7.3 DELETE – Remove Graph

`DELETE /ds/{dataset}/data?graph={graphIRI}&branch={name}`

Removes the specified graph entirely, creating a new commit.

**Required Headers:**
- `SPARQL-VC-Commit-Message` – Commit message
- `SPARQL-VC-Commit-Author` – Author identifier

**Optional Headers:**
- `SPARQL-VC-Expected-Parent` – Expected parent commit id for client-side tracking (§7.4)

**Response:**
- **200 OK** or **204 No Content**
- **ETag** header contains new commit id
- `Location` header contains URL of commit resource

**Example:**
```http
DELETE /ds/mydata/data?graph=http://example.org/archived&branch=main
SPARQL-VC-Commit-Message: Remove archived data
SPARQL-VC-Commit-Author: alice@example.org
```

### 7.4 Concurrent Write Detection

Servers **MUST** detect conflicting concurrent writes by analyzing overlapping triple changes in RDF patches:

- When a client writes to a graph (PUT/POST/DELETE), the server computes the changeset (RDF patch) from the current head.
- Before committing, the server checks if any other commits were added to the branch since the client's operation began.
- If concurrent commits exist, the server **MUST** analyze whether the changesets have **overlapping triples** (same subject, predicate, object, and graph).
- **Overlapping triple detection:**
  - Two changesets overlap if they both add, delete, or modify the same quad (s, p, o, g).
  - A delete in one changeset overlaps with an add or delete of the same quad in another.
  - An add in one changeset overlaps with a delete or add of the same quad in another.
  - For graph-level operations, the server **MUST** consider the graph IRI when detecting overlaps.
- If overlaps are detected, the server **MUST** return **409 Conflict** with a problem+json body listing the conflicting triples (§12.3).
- If no overlaps exist, the server **MAY** automatically create the commit (auto-merge) or **MAY** still require explicit merge operation (server policy).

Clients **MAY** include `SPARQL-VC-Expected-Parent` header with the commit id they based their changes on. This is **OPTIONAL** and used only for client-side tracking; conflict detection is based on RDF patch analysis, not parent expectations.

### 7.5 No‑Op Updates

If an update yields an empty changeset, servers **MUST** return **204** and **MUST NOT** create a new commit. A problem+json body is not required; servers **MAY** include `X-Changes: none`.

### 7.6 Batch Graph Operations

`POST /ds/{dataset}/version/batch-graphs` atomically applies a sequence of graph operations (PUT/POST/DELETE); either a single combined commit or multiple commits, as requested by `{"mode":"single"|"multiple"}`.

**Request Body:**
```json
{
  "mode": "single",
  "author": "alice@example.org",
  "message": "Batch update of multiple graphs",
  "operations": [
    {
      "method": "PUT",
      "graph": "http://example.org/graph1",
      "data": "...",
      "contentType": "text/turtle"
    },
    {
      "method": "POST",
      "graph": "http://example.org/graph2",
      "data": "...",
      "contentType": "text/turtle"
    },
    {
      "method": "DELETE",
      "graph": "http://example.org/graph3"
    }
  ]
}
```

**Response:**
- **200 OK** with commit metadata if `mode=single`
- **200 OK** with array of commit metadata if `mode=multiple`
- **400 Bad Request** if operations are invalid

---

## 8. Resources and Endpoints

### 8.1 Resource Model

Graph Store endpoints are extended with version control:

- `/ds/{dataset}/data` – Graph Store Protocol endpoint (with version parameters)
- `/ds/{dataset}/version/branches` – list/create branches
- `/ds/{dataset}/version/branches/{name}` – get/delete/reset a branch
- `/ds/{dataset}/version/commits/{id}` – commit metadata
- `/ds/{dataset}/version/commits/{id}/changes` – materialized changeset
- `/ds/{dataset}/version/commits/{id}/graphs/{graphIRI}` – specific graph at commit
- `/ds/{dataset}/version/history` – history navigation
- `/ds/{dataset}/version/merge` – merge operation
- `/ds/{dataset}/version/tags` – tags

### 8.2 Headers (Registered Names)

To avoid collisions, this spec defines the `SPARQL-VC-*` header namespace. Header names are **case-insensitive** per HTTP:

- `SPARQL-VC-Branch` – target branch (read/write)
- `SPARQL-VC-Commit` – target commit (read)
- `SPARQL-VC-Commit-Message` – required on writes
- `SPARQL-VC-Commit-Author` – required on writes

Servers **SHOULD** also support URL params for cacheable reads.

### 8.3 URL Parameters

- `branch`, `commit`, `asOf` (camelCase). Percent‑encode names.
- `graph` – IRI of the named graph (percent-encoded)
- `default` – flag to target the default graph instead of a named graph
- Branch/tag names are **case‑sensitive** and Unicode NFC, matching `^[A-Za-z0-9._\-]+$`.
- `commit` MUST be a UUIDv7 string when referring to a commit identifier.
- It is invalid to provide `commit` together with `branch` and/or `asOf` (see §6.3).

---

## 9. History and Introspection

### 9.1 Get Commit Info

`GET /ds/{dataset}/version/commits/{id}` → commit metadata, parents, message, author, timestamp, refs, affected graphs.

**Response includes:**
```json
{
  "id": "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a",
  "parents": ["01936b2d-1a23-7b45-9c67-8d9e0f1a2b3c"],
  "author": "alice@example.org",
  "timestamp": "2025-10-05T14:30:00Z",
  "message": "Update employee roster",
  "affectedGraphs": ["http://example.org/employees"]
}
```

### 9.2 Get Graph at Commit

`GET /ds/{dataset}/version/commits/{id}/graphs/{graphIRI}`

Returns the content of a specific graph at a specific commit.

**Response:**
- **200 OK** with RDF payload
- **404 Not Found** if graph did not exist at that commit

### 9.3 History Listing

`GET /ds/{dataset}/version/history?branch=...&limit=&offset=&since=&until=&author=&graph={graphIRI}`

Returns a list of commits matching the filters. When `graph` parameter is provided, only commits that modified the specified graph are returned.

RFC 5988 `Link` pagination is used.

### 9.4 Diff

`GET /ds/{dataset}/version/diff?from={id}&to={id}&graph={graphIRI}`

Returns a changeset in a negotiated format. When `graph` parameter is provided, only changes affecting that graph are returned.

### 9.5 Blame / Annotate

`GET /ds/{dataset}/version/blame?graph={graphIRI}&subject={IRI}`

Returns last‑writer attribution per triple in the specified graph.

---

## 10. Merging

### 10.1 Algorithm (Normative)

1. Compute merge base.
2. Diff base→target (ours) and base→source (theirs).
3. Apply non‑overlapping changes across all graphs.
4. For overlapping changes, emit **conflicts** with structured payload (see §10.3).

### 10.2 Strategies

- `three-way` (default)
- `ours`/`theirs` (auto‑resolve conflicts by policy)
- `manual` (client supplies resolutions)

### 10.3 Conflict Representation (JSON)

```json
{
  "subject": "<IRI>",
  "predicate": "<IRI>",
  "graph": "<IRI>",
  "type": "modify-modify|delete-modify|add-modify",
  "base": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"},
  "ours": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"},
  "theirs": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"}
}
```

Note: The `graph` field is **required** for Graph Store Protocol conflicts (unlike the SPARQL Protocol extension where it is optional).

### 10.4 Fast‑Forward

Servers **MAY** fast‑forward when target is an ancestor of source. Default policy is `fastForward=allow`. A `fastForward=allow|only|never` parameter controls behavior. Servers **MUST** document behavior when `only` is requested but not possible (respond **409 Conflict**).

### 10.5 Reset & Revert

- **Reset:** Move a branch ref to another commit (`hard`/`soft`).
- **Revert:** Create a new commit that inverts a given commit's changes.

---

## 11. Tags

Tags are immutable named refs to commits. Creation, listing, lookup, and deletion follow the branch patterns, but mutation of a tag target after creation is **MUST NOT** (immutable by default). Servers **MAY** support annotated tags with message and author.

---

## 12. Errors, Status, and Problem Details

### 12.1 Status Codes

- **200 OK** – Successful GET/PUT/POST/DELETE with representation
- **201 Created** – New graph/branch/tag resource created
- **202 Accepted** – Operation accepted but not yet completed
- **204 No Content** – Successful update without body
- **400 Bad Request** – Invalid parameters (e.g., `selector_conflict`)
- **401/403** – Authentication/authorization failures
- **404** – Unknown dataset/branch/commit/tag/graph
- **405** – Method not allowed (e.g., tag retarget)
- **406** – Not acceptable (content negotiation failure)
- **409** – Merge conflict, concurrent write conflict (overlapping triples), tag retarget attempt, or `fastForward=only` not possible
- **415** – Unsupported media type
- **422** – Semantically invalid (e.g., malformed RDF Patch)
- **429** – Rate limited
- **500** – Server error

### 12.2 Error Media Type and Fields

Servers **SHOULD** adopt RFC 7807 Problem Details (`application/problem+json`) with `type`, `title`, `detail`, and `instance`. Include machine-readable `code` (e.g., `branch_not_found`, `merge_conflict`, `concurrent_write_conflict`, `selector_conflict`, `graph_not_found`).

### 12.3 Examples

**Graph not found:**
```json
{
  "type": "about:blank",
  "title": "Graph not found",
  "status": 404,
  "code": "graph_not_found",
  "detail": "Graph http://example.org/missing does not exist at commit 01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"
}
```

**Merge conflict:**
```json
{
  "type": "about:blank",
  "title": "Merge conflict",
  "status": 409,
  "code": "merge_conflict",
  "detail": "Conflicting changes to foaf:age for ex:John in graph http://example.org/employees",
  "conflicts": [
    {
      "subject": "http://example.org/John",
      "predicate": "http://xmlns.com/foaf/0.1/age",
      "graph": "http://example.org/employees",
      "type": "modify-modify",
      "base": {"object": "30", "datatype": "http://www.w3.org/2001/XMLSchema#integer"},
      "ours": {"object": "31", "datatype": "http://www.w3.org/2001/XMLSchema#integer"},
      "theirs": {"object": "32", "datatype": "http://www.w3.org/2001/XMLSchema#integer"}
    }
  ]
}
```

**Concurrent write conflict:**
```json
{
  "type": "about:blank",
  "title": "Concurrent write conflict",
  "status": 409,
  "code": "concurrent_write_conflict",
  "detail": "Another commit was made to branch 'main' that modifies overlapping triples in graph http://example.org/employees",
  "expectedParent": "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a",
  "actualHead": "01936b2f-5d89-7e12-b3c4-5f6a7b8c9d0e",
  "conflicts": [
    {
      "subject": "http://example.org/alice",
      "predicate": "http://example.org/role",
      "graph": "http://example.org/employees",
      "yourChange": {"operation": "add", "object": "Manager"},
      "concurrentChange": {"operation": "add", "object": "Director"}
    }
  ]
}
```

---

## 13. Metadata Vocabulary (vc:)

A simple RDF vocabulary for commits/branches/tags is defined under `http://www.w3.org/ns/sparql/version-control#`.

Servers **SHOULD** expose commit metadata as RDF; the metadata **MUST** include at least:
- `vc:commitId` (UUIDv7)
- `vc:affectedGraph` (multi-valued, IRIs of modified graphs)
- `prov:wasGeneratedBy`
- `prov:endedAtTime`

Signature references **MAY** be included.

**Example:**
```turtle
@prefix vc: <http://www.w3.org/ns/sparql/version-control#> .
@prefix prov: <http://www.w3.org/ns/prov#> .

<urn:commit:01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a> a vc:Commit ;
  vc:commitId "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a" ;
  vc:parent <urn:commit:01936b2d-1a23-7b45-9c67-8d9e0f1a2b3c> ;
  vc:affectedGraph <http://example.org/employees> ;
  prov:wasGeneratedBy <urn:activity:update-roster> ;
  prov:endedAtTime "2025-10-05T14:30:00Z"^^xsd:dateTime ;
  vc:message "Update employee roster" ;
  vc:author "alice@example.org" .
```

---

## 14. Multi‑Tenancy and Dataset Scoping

Servers hosting multiple datasets **MUST** scope refs under a dataset root: `/ds/{dataset}/...`. Dataset identifiers follow the same rules as branch names (case‑sensitive, NFC, `^[A-Za-z0-9._\-]+$`).

Graph IRIs are scoped within datasets. The same graph IRI in different datasets represents distinct graphs with independent version histories.

---

## 15. JSON Schemas (Informative)

### 15.1 Conflict Object

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-gsp-vc/conflict.json",
  "type": "object",
  "required": ["subject", "predicate", "graph", "type", "ours", "theirs"],
  "properties": {
    "subject": {"type": "string"},
    "predicate": {"type": "string"},
    "graph": {"type": "string"},
    "type": {"enum": ["modify-modify", "delete-modify", "add-modify"]},
    "base": {"$ref": "#/$defs/node"},
    "ours": {"$ref": "#/$defs/node"},
    "theirs": {"$ref": "#/$defs/node"}
  },
  "$defs": {
    "node": {
      "type": "object",
      "required": ["object"],
      "properties": {
        "object": {"type": "string"},
        "datatype": {"type": ["string", "null"]},
        "lang": {"type": ["string", "null"]}
      }
    }
  }
}
```

### 15.2 Batch Graph Operations Request

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-gsp-vc/batch-graphs.json",
  "type": "object",
  "required": ["mode", "author", "message", "operations"],
  "properties": {
    "mode": {"enum": ["single", "multiple"], "default": "single"},
    "author": {"type": "string"},
    "message": {"type": "string"},
    "branch": {"type": "string", "default": "main"},
    "operations": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["method", "graph"],
        "properties": {
          "method": {"enum": ["PUT", "POST", "DELETE"]},
          "graph": {"type": "string", "format": "uri"},
          "data": {"type": "string"},
          "contentType": {"type": "string"}
        }
      }
    }
  }
}
```

### 15.3 Merge Request

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-gsp-vc/merge.json",
  "type": "object",
  "required": ["into", "from"],
  "properties": {
    "strategy": {"enum": ["three-way", "ours", "theirs", "manual"], "default": "three-way"},
    "fastForward": {"enum": ["allow", "only", "never"], "default": "allow"},
    "into": {"type": "string"},
    "from": {"type": "string"},
    "resolutions": {
      "type": "array",
      "items": {"$ref": "https://example.org/sparql-gsp-vc/conflict.json"}
    }
  }
}
```

---

## 16. ABNF (Normative)

```abnf
; UUID textual form (8-4-4-4-12 hex). Version-specific validation happens at runtime.
commit-id   = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG
ref-name    = 1*( ALPHA / DIGIT / "." / "-" / "_" )

dataset-id  = ref-name
branch-name = ref-name
tag-name    = ref-name
graph-iri   = absolute-URI  ; Per RFC 3986
```

---

## 17. Examples

### 17.1 Create Branch

```http
POST /ds/mydata/version/branches
Content-Type: application/json

{
  "name": "feature-x",
  "from": "main"
}
```

**Response:**
```http
HTTP/1.1 201 Created
Location: /ds/mydata/version/branches/feature-x
ETag: "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"
```

### 17.2 Replace Graph (PUT)

```http
PUT /ds/mydata/data?graph=http://example.org/employees&branch=feature-x
Content-Type: text/turtle
SPARQL-VC-Commit-Message: Update employee data
SPARQL-VC-Commit-Author: alice@example.org
SPARQL-VC-Expected-Parent: 01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a

@prefix ex: <http://example.org/> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

ex:alice foaf:name "Alice Smith" ;
  ex:role "Manager" ;
  foaf:age 31 .
```

**Response:**
```http
HTTP/1.1 200 OK
ETag: "01936b2f-1234-7abc-def0-9a8b7c6d5e4f"
Location: /ds/mydata/version/commits/01936b2f-1234-7abc-def0-9a8b7c6d5e4f
```

### 17.3 Retrieve Graph at Version

```http
GET /ds/mydata/data?graph=http://example.org/employees&commit=01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a
Accept: text/turtle
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/turtle
ETag: "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"

@prefix ex: <http://example.org/> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .

ex:alice foaf:name "Alice Smith" ;
  ex:role "Developer" ;
  foaf:age 30 .
```

### 17.4 Time Travel Query

```http
GET /ds/mydata/data?graph=http://example.org/employees&branch=main&asOf=2025-10-01T00:00:00Z
Accept: text/turtle
```

### 17.5 Merge Branches

```http
POST /ds/mydata/version/merge
Content-Type: application/json

{
  "into": "main",
  "from": "feature-x",
  "strategy": "three-way",
  "fastForward": "allow"
}
```

**Response (success):**
```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "01936b30-5678-9def-0123-456789abcdef"

{
  "commitId": "01936b30-5678-9def-0123-456789abcdef",
  "fastForward": false,
  "conflicts": []
}
```

**Response (conflict):**
```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Merge conflict",
  "status": 409,
  "code": "merge_conflict",
  "detail": "Conflicting changes detected",
  "conflicts": [
    {
      "subject": "http://example.org/alice",
      "predicate": "http://xmlns.com/foaf/0.1/age",
      "graph": "http://example.org/employees",
      "type": "modify-modify",
      "base": {"object": "30", "datatype": "http://www.w3.org/2001/XMLSchema#integer"},
      "ours": {"object": "31", "datatype": "http://www.w3.org/2001/XMLSchema#integer"},
      "theirs": {"object": "32", "datatype": "http://www.w3.org/2001/XMLSchema#integer"}
    }
  ]
}
```

### 17.6 Batch Graph Operations

```http
POST /ds/mydata/version/batch-graphs
Content-Type: application/json

{
  "mode": "single",
  "author": "alice@example.org",
  "message": "Update multiple graphs atomically",
  "branch": "main",
  "operations": [
    {
      "method": "PUT",
      "graph": "http://example.org/employees",
      "data": "@prefix ex: <http://example.org/> . ex:alice ex:role \"Manager\" .",
      "contentType": "text/turtle"
    },
    {
      "method": "POST",
      "graph": "http://example.org/departments",
      "data": "@prefix ex: <http://example.org/> . ex:engineering ex:size 50 .",
      "contentType": "text/turtle"
    },
    {
      "method": "DELETE",
      "graph": "http://example.org/archived"
    }
  ]
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json
ETag: "01936b31-abcd-ef01-2345-6789abcdef01"

{
  "commitId": "01936b31-abcd-ef01-2345-6789abcdef01",
  "affectedGraphs": [
    "http://example.org/employees",
    "http://example.org/departments",
    "http://example.org/archived"
  ]
}
```

---

## 18. Backwards Compatibility and Discovery

- **Non‑versioned behavior:** `/ds/{dataset}/data` without version selectors operates on the default branch.
- **Feature discovery:** `OPTIONS /ds/{dataset}/data` includes:
  - `SPARQL-Version-Control: 1.0`
  - `Link: </ds/{dataset}/version>; rel="version-control"`
  - `Accept-Patch: text/rdf-patch`
  - Lists supported graph store operations and version control features

**Example:**
```http
OPTIONS /ds/mydata/data
```

**Response:**
```http
HTTP/1.1 204 No Content
Allow: GET, HEAD, PUT, POST, DELETE, OPTIONS
Accept-Patch: text/rdf-patch, application/n-quads
SPARQL-Version-Control: 1.0
Link: </ds/mydata/version>; rel="version-control"
```

---

## 19. Security Considerations

### 19.1 Access Control

- Servers **MUST** enforce access control at both the dataset and graph levels.
- Version control operations (branch creation, merging, etc.) **SHOULD** require appropriate permissions.
- Historical data access **MUST** respect the same access control rules as current data.

### 19.2 Commit Author Authentication

- Servers **SHOULD** validate that the `SPARQL-VC-Commit-Author` header matches the authenticated user.
- Servers **MAY** support digital signatures on commits for non-repudiation.

### 19.3 Data Integrity

- Servers **SHOULD** use content hashes to detect corruption or tampering.
- ETags **MUST** be strong validators tied to commit identifiers.

### 19.4 Resource Exhaustion

- Servers **SHOULD** implement limits on:
  - History depth for queries
  - Diff size
  - Number of branches/tags per dataset
  - Commit metadata size

---

## 20. References

### Normative

- [SPARQL 1.2 Graph Store Protocol](https://www.w3.org/TR/sparql12-graph-store-protocol/)
- [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) – Key words for RFCs
- [RFC 3986](https://www.rfc-editor.org/rfc/rfc3986) – URI Generic Syntax
- [RFC 7231](https://www.rfc-editor.org/rfc/rfc7231) – HTTP/1.1 Semantics and Content
- [RFC 7232](https://www.rfc-editor.org/rfc/rfc7232) – HTTP/1.1 Conditional Requests
- [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) – Problem Details for HTTP APIs
- [RFC 3339](https://www.rfc-editor.org/rfc/rfc3339) – Date and Time on the Internet
- [RDF 1.1 Concepts](https://www.w3.org/TR/rdf11-concepts/)
- [RDF Patch](https://afs.github.io/rdf-patch/) – Apache Jena RDF Patch

### Informative

- [PROV-O](https://www.w3.org/TR/prov-o/) – Provenance Ontology
- [URDNA2015](https://www.w3.org/TR/rdf-canon/) – RDF Dataset Canonicalization
- [UUIDv7 Draft](https://datatracker.ietf.org/doc/draft-ietf-uuidrev-rfc4122bis/)

---

## Appendix A: Change Log (editorial)

- Initial draft based on SPARQL 1.2 Protocol Version Control Extension
- Adapted for Graph Store Protocol operations (GET, PUT, POST, DELETE, HEAD)
- Added graph-level versioning semantics
- Extended conflict representation to require graph field
- Added batch graph operations endpoint
- Added graph-specific history and introspection endpoints
- Aligned with SPARQL 1.2 Graph Store Protocol status codes (406, 415)
- Added security considerations for graph-level access control

---

## Appendix B: Design Rationale

### B.1 Graph-Level vs Triple-Level Versioning

This specification adopts **graph-level versioning** where commits capture the state of entire graphs (and the graph store as a whole). This design choice:

- Simplifies implementation by reusing existing graph store operations
- Aligns with the Graph Store Protocol's resource model (graphs as resources)
- Supports efficient storage through graph-level changesets
- Enables fine-grained history tracking per graph via `affectedGraphs` metadata

Alternative approaches (triple-level versioning, quad-level versioning) were considered but deemed more complex for the common use cases.

### B.2 Relationship to SPARQL Protocol Extension

This Graph Store Protocol extension is designed to be **compatible and complementary** with the SPARQL 1.2 Protocol Version Control Extension:

- Both use the same commit model (UUIDv7, DAG structure, RDF Patch)
- Both share the same version control endpoints (`/version/*`)
- Commits created via Graph Store operations are visible via SPARQL queries and vice versa
- A server implementing both extensions provides a unified version control layer

The Graph Store Protocol extension focuses on **direct HTTP manipulation** of graphs, while the SPARQL Protocol extension focuses on **declarative queries and updates**.

### B.3 Default Graph Handling

Operations targeting the default graph use the `default` URL parameter instead of `graph`:

```http
PUT /ds/mydata/data?default&branch=main
```

This follows the Graph Store Protocol convention for default graph access.
