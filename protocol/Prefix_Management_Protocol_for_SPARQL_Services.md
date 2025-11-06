# Prefix Management Protocol (PMP) for SPARQL Services

*(non-standard extension, designed to complement W3C SPARQL 1.2 Protocol and Graph Store HTTP Protocol)*

## 1. Introduction

This document defines an HTTP-based protocol for managing prefix declarations associated with a SPARQL dataset or with individual graphs inside that dataset.

SPARQL itself treats `PREFIX` declarations as query-local syntax and does not define a means to retrieve or modify server-side prefix mappings. Many SPARQL servers (e.g. Apache Jena / Fuseki) nonetheless maintain prefix maps at dataset level or per graph. This protocol exposes those maps in a simple, REST-style way so that SPARQL clients, UI editors, or admin tools can synchronize their prefix lists with the server.

This protocol is designed to be hosted alongside existing SPARQL endpoints and Graph Store Protocol endpoints. It does **not** change SPARQL query/update behavior.

## 2. Terminology

* **Prefix map**: an ordered mapping from a short prefix name (e.g. `rdf`) to an absolute IRI (e.g. `http://www.w3.org/1999/02/22-rdf-syntax-ns#`).
* **Dataset-level prefix map**: a prefix map that applies to the dataset as a whole.
* **Graph-level prefix map**: a prefix map associated with a specific named graph.
* **Service endpoint**: base URL where this protocol is available, e.g.

  * `https://example.org/fuseki/myDataset/prefixes`
  * The exact path is deployment-specific.

The protocol reuses the idea of addressing default vs. named graph from the Graph Store Protocol.

## 3. Service URL and Graph Selection

The Prefix Management Protocol is exposed at a single base endpoint:

```text
PREFIX-SERVICE := {dataset-base}/prefixes
```

The target of an operation is determined by query parameters:

* **dataset-level** (default): no graph-identifying parameter present.
* **default graph**: `default` parameter present (with any value, e.g. `?default`).
* **named graph**: `graph={IRI}` parameter present, where `{IRI}` is an absolute IRI, URL-encoded.

This mirrors GSP:

* `GET /prefixes` → dataset-level prefixes
* `GET /prefixes?default` → default graph prefixes (if supported)
* `GET /prefixes?graph=http%3A%2F%2Fexample.org%2Fgraph` → that graph’s prefixes

If a server does not distinguish dataset-level vs. default-graph prefix maps, it MAY return the same map for both.

## 4. Representations (Media Types)

Servers **MUST** support at least JSON for interchange.

### 4.1 `application/sparql-prefixes+json` (RECOMMENDED)

```json
{
  "context": "https://example.org/ns/sparql-prefixes#",
  "scope": "dataset | default | graph",
  "graph": "http://example.org/graph/…",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
  }
}
```

* `scope` indicates which map was addressed.
* `graph` MUST be present if `scope = "graph"`.

Servers MAY also support `application/json` with the same structure.

Clients **SHOULD** send `Accept: application/sparql-prefixes+json, application/json`.

## 5. Operations

The protocol defines four operations: **GET, PUT, PATCH, DELETE**. They parallel GSP semantics but operate on a prefix map instead of RDF data.

### 5.1 GET — Retrieve prefix map

**Request:**

```http
GET /prefixes HTTP/1.1
Accept: application/sparql-prefixes+json
```

**Response:**

* `200 OK` with the JSON representation of the current prefix map.
* `404 Not Found` if the addressed graph does not exist.

**Examples:**

Dataset-level:

```http
GET /prefixes
```

Named graph:

```http
GET /prefixes?graph=http%3A%2F%2Fexample.org%2FmyGraph
```

### 5.2 PUT — Replace entire prefix map

**Semantics:** Replace the addressed prefix map with exactly the prefixes provided in the request body.

**Request:**

```http
PUT /prefixes HTTP/1.1
Content-Type: application/sparql-prefixes+json

{
  "prefixes": {
    "ex": "http://example.org/",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  }
}
```

**Response:**

* `204 No Content` on success.
* `400 Bad Request` if payload is malformed or contains invalid prefix names/IRIs.
* `404 Not Found` if graph does not exist.
* `409 Conflict` if the server uses ETags and the client’s conditional request fails (see 7).

**Notes:**

* Any existing prefix not included in the PUT body is removed.
* This is useful for admin UIs that present a full editable table.

### 5.3 PATCH (or POST) — Add or update selected prefixes

We want a non-destructive way to add/modify a small set of prefixes. HTTP `PATCH` is the natural choice. Some environments don’t like `PATCH`; servers MAY also allow `POST` with the same body, treating it as “merge”.

**Request:**

```http
PATCH /prefixes HTTP/1.1
Content-Type: application/sparql-prefixes+json

{
  "prefixes": {
    "geo": "http://www.opengis.net/ont/geosparql#",
    "dct": "http://purl.org/dc/terms/"
  }
}
```

**Semantics:**

* For each given key:

  * If it does not exist, create it.
  * If it exists, overwrite it.
* Other prefixes stay unchanged.

**Response:**

* `204 No Content` on success.
* Same error conditions as PUT.

If using `POST` instead of `PATCH`, it should be:

```http
POST /prefixes?patch
Content-Type: application/sparql-prefixes+json
...
```

to make the intent explicit.

### 5.4 DELETE — Remove one or all prefixes

We need two flavors:

1. **Delete entire map** (rare, mostly admin):

   ```http
   DELETE /prefixes
   ```

   → Resets the map to empty. Server MAY also restore its internal defaults instead of emptying.

2. **Delete selected prefixes**:

   ```http
   DELETE /prefixes?prefix=ex&prefix=geo
   ```

   * multiple `prefix=` parameters allowed
   * if a named prefix does not exist, server MAY ignore silently or return `404` — but ignoring is friendlier.

**Responses:**

* `204 No Content` on success
* `404 Not Found` if graph doesn't exist

## 6. Prefix Name and IRI Constraints

Servers MUST apply at least:

* Prefix name: matches the SPARQL `PNAME_NS` rules (roughly, what you’d write before a colon).
* IRI: absolute IRI

If there is a violation, return:

```http
400 Bad Request
Content-Type: application/json

{ "error": "Invalid prefix name '1foo'" }
```

## 7. Concurrency and Conditional Requests (optional but nice)

To “complement seamlessly” the SPARQL/HTTP style, we can support ETags just like you asked for in your versioned protocols.

* `GET /prefixes` → `ETag: "abc123"`
* Client updates with:

  ```http
  PUT /prefixes
  If-Match: "abc123"
  ...
  ```
* If someone else modified in between → `412 Precondition Failed`

This is especially useful if your SPARQL UI fetches prefixes, lets the user edit them, and then writes them back.

## 8. Error Conditions

* **404 Not Found** — named graph doesn’t exist or dataset not found.
* **400 Bad Request** — invalid JSON, invalid prefix separator, blank IRI, etc.
* **415 Unsupported Media Type** — client sent something other than JSON.
* **409 Conflict / 412 Precondition Failed** — conditional update failed.
* **500 Internal Server Error** — server-side prefix storage failure.

Return an error object like:

```json
{
  "error": "InvalidPrefixIri",
  "message": "IRI must be absolute",
  "scope": "graph",
  "graph": "http://example.org/g"
}
```

## 9. Capabilities Discovery (optional)

To make editors smarter, define:

```http
GET /prefixes?capabilities
Accept: application/json
```

Response:

```json
{
  "pmpVersion": "1.0",
  "supportsDatasetLevel": true,
  "supportsGraphLevel": true,
  "supportsPatch": true,
  "maxPrefixes": 500
}
```

Clients can then adapt.

## 10. Interaction with SPARQL / GSP

* This protocol does **not** change how SPARQL queries are parsed — a query that has no `PREFIX` declarations still needs full IRIs or a local prefix list.
* This protocol is purely **management**. A server **may** use this prefix map when serializing RDF (e.g. Turtle responses), but that is an implementation choice.
* This protocol can live at `/prefixes` while SPARQL is at `/sparql` and GSP at `/data` on the same dataset.

## 11. Minimal Compliance Profile

**Level 1 (Read-only):**

* MUST support `GET /prefixes` (dataset-level)
* MUST return JSON with `prefixes` object
* SHOULD support `GET /prefixes?graph=...`

**Level 2 (Read/Write):**

* All of Level 1
* MUST support `PUT /prefixes`
* MUST support `PATCH /prefixes` or `POST /prefixes?patch`
* SHOULD support `DELETE /prefixes?prefix=...`
* SHOULD support conditional requests (ETag + If-Match)

---

If you want, I can now produce a concrete Fuseki-style Java sketch that:

* plugs into a dataset
* exposes `/prefixes`
* serializes exactly the JSON shape above.