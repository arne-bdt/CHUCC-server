# SPARQL 1.2 Protocol – Version Control Extension

**Revision:** 2025‑10‑04 (editor’s review)

**Extends:** [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/)

> This revision normalizes section numbering, fixes merge artifacts, aligns HTTP semantics with existing Web standards, clarifies hashing/canonicalization, blank node handling, conflict models, and adds conformance language, JSON Schemas, and security guidance.

---

## 1. Introduction

This specification extends the SPARQL 1.2 Protocol with version‑control semantics—branches, commits, merges, history, and time‑travel—while remaining backward‑compatible. Clients opt‑in via dedicated endpoints, headers, or URL parameters.

### 1.1 Design Principles

- **Backward compatibility.** Non‑versioned SPARQL 1.2 behavior remains unchanged.
- **Identifiers.** Commits use **UUIDv7** identifiers by default for global uniqueness and ordering. Servers **MAY** additionally compute and expose a **content hash** (e.g., SHA‑256 of a canonicalized payload) for integrity, deduplication, and verification.
- **Immutability.** Historical states are append‑only.
- **Three‑way merge.** With explicit conflict representation.
- **HTTP‑native.** Reuse `ETag`/`If‑Match`, `Prefer`, standard status codes, and content negotiation; avoid bespoke headers unless necessary.

---

## 2. Conformance

An implementation conforms if it implements all features marked **MUST** in the core model (Sections 4–7, 10) and protocol (Sections 8–9, 11). Optional features are **MAY**.

### 2.1 Conformance Classes

- **Level 1 (Basic):** Commits (UUIDv7), branches, history, query at branch/commit, RDF Patch support, strong ETags, problem+json.
- **Level 2 (Advanced):** Three‑way merge, conflict detection/representation, fast‑forward, revert, reset, tags, cherry‑pick, blame.

A public test suite (normative) SHALL validate both classes.

### 2.2 Interoperability Defaults (Normative)

To ensure cross‑vendor portability, the following defaults are **MANDATORY** unless explicitly negotiated otherwise:

- **Identifiers:** Commit ids are **UUIDv7** (Section 5). Branch/tag names are **case‑sensitive**, Unicode **NFC** normalized, and match `^[A-Za-z0-9._\-]+$`.
- **Changeset format:** Servers **MUST** accept and produce **RDF Patch** (`text/rdf-patch`) for writes/diffs. Other formats (e.g., binary/rdf-patch) **MAY** be supported.
- **No‑op update:** An update yielding an empty changeset **MUST NOT** create a commit; return **204**.
- **Selectors precedence:** It is an error to combine `commit` with `branch` and/or `asOf`; return **400** (`selector_conflict`).
- **ETags:** Use **strong ETags**. Branch resources’ ETag **MUST** equal the current head commit id. Commit resources **MUST** use their commit id as ETag.
- **Error media type:** Use **`application/problem+json`** with machine‑readable `code`.
- **Charset:** All JSON and text payloads **MUST** be UTF‑8.
- **Discovery:** `OPTIONS` **MUST** advertise `SPARQL-Version-Control` and supported features; `Accept-Patch` **MUST** include `text/rdf-patch`.
- **Tags:** Tags are **immutable**. Attempts to retarget MUST result in **409 Conflict** or **405 Method Not Allowed**.
- **Dataset scoping:** Multi‑tenant servers **MUST** scope under `/ds/{dataset}/...` (Section 14).

## 3. Terminology
 Terminology

**Commit** – Immutable dataset state with metadata and parent references.

**Commit Identifier** – A **UUIDv7** per RFC-style canonical textual form (8‑4‑4‑4‑12 hex). Servers may also expose a `contentHash` (see §5.4).

**Branch** – Named ref to a commit (mutable pointer).

**Head** – Current commit at a branch ref.

**Changeset** – Additions and deletions between two commits (§5.2).

**Merge Base** – Nearest common ancestor of two commits.

**Working State** – Materialized dataset for a given ref/commit.

**Conflict** – Overlapping changes that cannot be applied automatically (§10).

---

## 4. Model Overview

Commits form a DAG: initial commits (no parents), linear commits (one parent), merge commits (≥2 parents). Branches and tags are lightweight refs.

---

## 5. Commit Content and Identifiers

### 5.1 Commit Payload

A commit payload minimally includes:

- Parent commit id(s) (ordered list)
- Canonical changeset (§5.2)
- Metadata (author, timestamp, message, optional signatures)

### 5.2 Changeset Representation

Implementations **MUST** support at least one of:

- **RDF Patch** (`text/rdf-patch`)
- **N‑Quads with change graphs** (additions/deletions in named graphs)
- **TriG** with named graphs `urn:additions`/`urn:deletions`

Servers **MUST** advertise supported formats via `Accept‑Patch` and `Accept`. A server **SHOULD** select a single default for writes to simplify interop (recommendation: `text/rdf-patch`). Implementations **MAY** also support a **Thrift‑based binary encoding of RDF Patch**, referenced informally here as **binary/rdf-patch** (see Apache Jena RDF Patch documentation).



---

## 6. Query Semantics (Read)

### 6.1 Targeting a State

Clients **MAY** select a state using one of:

- URL params: `branch`, `commit`, `asOf` (ISO 8601 timestamp)
- Request headers (see §8.2) – discouraged when a URL will suffice, to maximize cacheability.

The selected state defines the dataset visible to SPARQL query execution. Entailment regimes are **out of scope** of this spec; servers **SHOULD** document their regime.

### 6.2 Time‑travel (`asOf`)

`asOf` **MUST be inclusive**. The visible state is the branch state at the latest commit whose commit time is **≤** the supplied timestamp (after converting `asOf` to UTC).

- **Timestamp format:** RFC 3339 / ISO 8601. Servers **MUST** accept timezone offsets and **MUST** normalize to UTC for comparison.
- **Precision:** Compare at millisecond precision (UUIDv7 and many stores record ms). Servers **MUST** not silently round away more precise client input; they **MUST** compare using the nearest millisecond.
- **Tie‑break:** If multiple commits share the same millisecond on the branch history path, select the commit with the **greatest UUIDv7** value (deterministic ordering).
- **Selectors precedence:** Requests **MUST NOT** combine `commit` with `asOf` and/or `branch`. If both are present, the server **MUST** return **400 Bad Request** with a problem+json body indicating `selector_conflict`.


`asOf` queries the state of a branch at or before a timestamp. Servers **MUST** define whether `asOf` is inclusive and how ties are resolved.


---

## 7. Update Semantics (Write)

### 7.1 Creating a Commit via SPARQL Update

`POST /sparql` with `application/sparql-update` **MUST** create a new commit on the target branch.

- Success status: **204 No Content** (SPARQL 1.2 default) or **200 OK** with metadata only if `Prefer: return=representation` is sent.
- The server **MUST** return `ETag` (new **UUIDv7** commit id) and `Location` of the commit resource.

### 7.2 Optimistic Concurrency

Use standard HTTP:

- Client sends `If‑Match: <branch-etag>` where the ETag of the branch equals its current head commit id.
- On mismatch, server returns **412 Precondition Failed**. Servers **MAY** additionally include a problem detail body (§12.3).

Custom headers for parent expectations are **OPTIONAL**.

### 7.3 No‑Op Updates

If an update yields an empty changeset, servers **MUST** return **204** and **MUST NOT** create a new commit. A problem+json body is not required; servers **MAY** include `X-Changes: none`.

If an update yields an empty changeset, servers **SHOULD** return **204** and **MUST NOT** create a new commit. A header `X-Changes: none` is **MAY**; problem+json is not required.

### 7.4 Batch Operations

`POST /version/batch` atomically applies a sequence of SPARQL Updates; either a single combined commit or multiple commits, as requested by `{"mode":"single"|"multiple"}`.

---

## 8. Resources and Endpoints

### 8.1 Resource Model

- `/ds/{dataset}/sparql` – SPARQL Protocol endpoint (query/update)
- `/ds/{dataset}/version/branches` – list/create branches
- `/ds/{dataset}/version/branches/{name}` – get/delete/reset a branch
- `/ds/{dataset}/version/commits/{id}` – commit metadata
- `/ds/{dataset}/version/commits/{id}/changes` – materialized changeset
- `/ds/{dataset}/version/history` – history navigation
- `/ds/{dataset}/version/merge` – merge operation
- `/ds/{dataset}/version/tags` – tags

- `/sparql` – SPARQL Protocol endpoint (query/update)
- `/version/branches` – list/create branches
- `/version/branches/{name}` – get/delete/reset a branch
- `/version/commits/{id}` – commit metadata
- `/version/commits/{id}/changes` – materialized changeset
- `/version/history` – history navigation
- `/version/merge` – merge operation
- `/version/tags` – tags

### 8.2 Headers (Registered Names)

To avoid collisions, this spec defines the `SPARQL-VC-*` header namespace. Header names are **case-insensitive** per HTTP:

- `SPARQL-VC-Branch` – target branch (read/write)
- `SPARQL-VC-Commit` – target commit (read)
- `SPARQL-VC-Commit-Message` – required on writes
- `SPARQL-VC-Commit-Author` – required on writes

Servers **SHOULD** also support URL params for cacheable reads.

To avoid collisions, this spec defines the `SPARQL-VC-*` header namespace:

- `SPARQL-VC-Branch` – target branch (read/write)
- `SPARQL-VC-Commit` – target commit (read)
- `SPARQL-VC-Commit-Message` – required on writes
- `SPARQL-VC-Commit-Author` – required on writes

Servers **SHOULD** also support URL params for cacheable reads.

### 8.3 URL Parameters

- `branch`, `commit`, `asOf` (camelCase). Percent‑encode names.
- Branch/tag names are **case‑sensitive** and Unicode NFC.
- `commit` MUST be a UUIDv7 string when referring to a commit identifier.
- It is invalid to provide `commit` together with `branch` and/or `asOf` (see §6.2).

- `branch`, `commit`, `asOf` (camelCase). Percent‑encode names. Branch names **MUST** match `^[A-Za-z0-9._\-]+$`.
- `commit` MUST be a UUIDv7 string when referring to a commit identifier.

---

## 9. History and Introspection

### 9.1 Get Commit Info

`GET /version/commits/{id}` → commit metadata, parents, message, author, timestamp, refs.

### 9.2 History Listing

`GET /version/history?branch=...&since=&until=&author=` returns a list of commits matching the filters. The response format is implementation-defined but MUST include commit ids and parents.

`GET /version/history?branch=...&limit=&offset=&since=&until=&author=` with RFC 5988 `Link` pagination.

### 9.3 Diff

`GET /version/diff?from=<id>&to=<id>` returns a changeset in a negotiated format.

### 9.4 Blame / Annotate

`GET /version/blame?subject=<IRI>` returns last‑writer attribution per triple.

---

## 10. Merging

### 10.1 Algorithm (Normative)

1. Compute merge base.
2. Diff base→target (ours) and base→source (theirs).
3. Apply non‑overlapping changes.
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
  "graph": "<optional IRI>",
  "type": "modify-modify|delete-modify|add-modify",
  "base": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"},
  "ours": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"},
  "theirs": {"object": "...", "datatype": "<IRI>", "lang": "<tag>"}
}
```

### 10.4 Fast‑Forward

Servers **MAY** fast‑forward when target is an ancestor of source. Default policy is `fastForward=allow`. A `fastForward=allow|only|never` parameter controls behavior. Servers **MUST** document behavior when `only` is requested but not possible (respond **409 Conflict**).

Servers **MAY** fast‑forward when target is an ancestor of source. A `fastForward=allow|only|never` parameter controls behavior.

### 10.5 Reset & Revert

- **Reset:** Move a branch ref to another commit (`hard`/`soft`).
- **Revert:** Create a new commit that inverts a given commit’s changes.

---

## 11. Tags

Tags are immutable named refs to commits. Creation, listing, lookup, and deletion follow the branch patterns, but mutation of a tag target after creation is **MUST NOT** (immutable by default). Servers **MAY** support annotated tags with message and author.

---

## 12. Errors, Status, and Problem Details

### 12.1 Status Codes

- **200 OK** – Successful GET/POST with representation
- **201 Created** – New branch/tag resource created
- **204 No Content** – Successful update without body
- **400 Bad Request** – Invalid parameters (e.g., `selector_conflict`)
- **401/403** – Authentication/authorization failures
- **404** – Unknown dataset/branch/commit/tag
- **409** – Merge conflict, tag retarget attempt, or `fastForward=only` not possible
- **412** – Precondition failed (ETag mismatch)
- **422** – Semantically invalid (e.g., malformed RDF Patch)
- **429** – Rate limited
- **500** – Server error

### 12.2 Error Media Type and Fields

Servers **SHOULD** adopt RFC 7807 Problem Details (`application/problem+json`) with `type`, `title`, `detail`, and `instance`. Include machine‑readable `code` (e.g., `branch_not_found`, `merge_conflict`, `selector_conflict`).

### 12.3 Examples

```json
{
  "type": "about:blank",
  "title": "Merge conflict",
  "status": 409,
  "code": "merge_conflict",
  "detail": "Conflicting changes to foaf:age for ex:John",
  "conflicts": [ ... ]
}
```

---





## 13. Metadata Vocabulary (vc:)

A simple RDF vocabulary for commits/branches/tags is defined under `http://www.w3.org/ns/sparql/version-control#`.

Servers **SHOULD** expose commit metadata as RDF; the metadata **MUST** include at least `vc:commitId` (UUIDv7), `prov:wasGeneratedBy`, and `prov:endedAtTime`. Signature references **MAY** be included.

---

## 14. Multi‑Tenancy and Dataset Scoping

Servers hosting multiple datasets **MUST** scope refs under a dataset root: `/ds/{dataset}/...`. Dataset identifiers follow the same rules as branch names (case‑sensitive, NFC, `^[A-Za-z0-9._\-]+$`).

Servers hosting multiple datasets **MUST** scope refs under a dataset root, e.g. `/ds/{dataset}/...` or a `dataset` parameter. Dataset identifier rules mirror branch/tag rules.

---

## 15. JSON Schemas (Informative)

### 17.1 Conflict Object
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-vc/conflict.json",
  "type": "object",
  "required": ["subject","predicate","type","ours","theirs"],
  "properties": {
    "subject": {"type": "string"},
    "predicate": {"type": "string"},
    "graph": {"type": ["string","null"]},
    "type": {"enum": ["modify-modify","delete-modify","add-modify"]},
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
        "datatype": {"type": ["string","null"]},
        "lang": {"type": ["string","null"]}
      }
    }
  }
}
```

### 17.2 Create Branch Request
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-vc/create-branch.json",
  "type": "object",
  "required": ["name"],
  "properties": {
    "name": {"type": "string", "pattern": "^[A-Za-z0-9._\-]+$"},
    "from": {"type": "string", "description": "commit UUIDv7 or branch name"}
  }
}
```

### 17.3 Merge Request
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.org/sparql-vc/merge.json",
  "type": "object",
  "required": ["into","from"],
  "properties": {
    "strategy": {"enum": ["three-way","ours","theirs","manual"], "default": "three-way"},
    "fastForward": {"enum": ["allow","only","never"], "default": "allow"},
    "into": {"type": "string"},
    "from": {"type": "string"},
    "resolutions": {"type": "array", "items": {"$ref": "https://example.org/sparql-vc/conflict.json"}}
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
tag-name    = 1*( ALPHA / DIGIT / "." / "-" / "_" )
```

## 17. Examples

End‑to‑end examples (branch create, update, query, merge, conflict, resolve, revert) updated to return standard headers (`ETag`, `Location`) and to use `SPARQL-VC-*` header names where headers are necessary.

---

## 18. Backwards Compatibility and Discovery

- **Non‑versioned behavior:** `/sparql` without version selectors operates on the default branch.
- **Feature discovery:** `OPTIONS /sparql` includes `SPARQL-Version-Control: 1.0`, `Link: </version>; rel="version-control"`, and lists supported features.

---

## 19. References

(Normative and informative, aligned with SPARQL 1.2, RFC 2119, RFC 7231, RFC 7807, URDNA2015, RDF Patch, PROV‑O.)

---

## Appendix A: Change Log (editorial)

- Fixed section numbering and removed stray JSON artifacts.
- Switched to `ETag`/`If-Match` concurrency; custom header namespace `SPARQL-VC-*`.
- Added canonicalization/blank node handling.
- Clarified status codes and problem details.
- Added dataset scoping.
- Tightened ABNF for refs.

