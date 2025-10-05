# SPARQL 1.2 Protocol – Version Control Extension

**Extends:** [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/)

## 1. Purpose & Scope
This extension adds **commit-based version control** to the SPARQL 1.2 Protocol, enabling clients to run queries and updates **against a selected ref** (branch/tag) or **immutable commit**, and to create new commits using **RDF Patch** changesets. It defines selectors, headers, resources, error formats, and concurrency semantics compatible with the Graph Store Protocol (GSP) variant.

## 2. Core Concepts
- **Commit DAG** with UUIDv7 commit identifiers; zero, one, or two parents (merge).
- **Refs**: **branches** (mutable pointers) and **tags** (immutable pointers).
- **Selectors** (mutually exclusive unless noted):
    - `branch` — branch name
    - `commit` — commit id (UUIDv7 textual form)
    - `asOf` — RFC 3339 timestamp **inclusive**
- **Working state**: materialized dataset for a selector. Entailment/inference are out of scope.

## 3. Resources & Endpoints
All endpoints are relative to a dataset base URL.

### 3.1 Discovery
- `Link: <{base}/version>; rel="version-control"`
- `Accept-Patch: text/rdf-patch` (optional: `application/vnd.apache.jena.rdfpatch+thrift`)

### 3.2 Refs and Commits
- `GET /version/refs` — list branches/tags, their target commits.
- `GET /version/commits/{commitId}` — fetch commit metadata.
- `POST /version/commits` — create commit by applying RDF Patch against a **target ref**.
    - Selector: `branch` (required) **or** `commit` (detached commit). `asOf` allowed only with `branch` (selects base).
    - Body: `text/rdf-patch`.
- `GET /version/history` — list commits with optional filters (branch, limit, offset, since, until, author).

### 3.3 Merge
- `POST /version/merge` — merge two refs/commits into a target branch. Returns a merge commit or `409` with conflicts.
    - Body: `{"into": "branch-name", "from": "source-ref", "strategy": "three-way|ours|theirs|manual", "fastForward": "allow|only|never"}`

### 3.4 Advanced Operations
- `POST /version/cherry-pick` — apply a specific commit to a target branch.
    - Body: `{"commit": "commitId", "onto": "branch-name"}`
- `POST /version/revert` — create inverse commit that undoes changes from a specified commit.
    - Body: `{"commit": "commitId", "branch": "branch-name"}`
- `POST /version/reset` — move branch pointer to a different commit.
    - Body: `{"branch": "branch-name", "to": "commitId", "mode": "hard|soft|mixed"}`
- `POST /version/rebase` — reapply commits from one branch onto another.
    - Body: `{"branch": "branch-name", "onto": "target-ref", "from": "base-commit"}`
- `POST /version/squash` — combine multiple commits into a single commit.
    - Body: `{"branch": "branch-name", "commits": ["id1", "id2", ...], "message": "new message"}`

### 3.5 Tags
- `GET /version/tags` — list all tags.
- `POST /version/tags` — create a new tag.
    - Body: `{"name": "tag-name", "target": "commitId", "message": "optional annotation", "author": "optional"}`
- `GET /version/tags/{name}` — get tag details and target commit.
- `DELETE /version/tags/{name}` — delete a tag (if server policy allows).

### 3.6 Batch (SPARQL protocol surface)
- **Endpoint (Protocol-specific):** `POST /version/batch`
- Accepts an array of operations (query/update/applyPatch), executed atomically with a single resulting commit when any write occurs. This name is **distinct from GSP's** batch endpoint so both protocols can co-exist on one dataset.

## 4. Selectors (URL parameters)
- `branch={name}` | `commit={uuidv7}` | `asOf={rfc3339}` (**inclusive**)
- Precedence: exactly one of the three, unless documented otherwise (e.g., `asOf` only with `branch`). If multiple are provided illegally → `400` with `selector_conflict`.
- Parameter names are **case-sensitive** and **normative**: `branch`, `commit`, `asOf`.

## 5. Headers
**Request (writes):**
- `SPARQL-VC-Author: <display name or URI>` (SHOULD)
- `SPARQL-VC-Message: <commit message>` (SHOULD)

**Request (reads):**
- `SPARQL-VC-Commit: <commitId>` (MUST be supported by servers; clients MAY ignore)

**Response (discovery/status):**
- `SPARQL-Version-Control: true`
- `ETag: "<id>"` (see §6)

## 6. Concurrency, ETag, and Conditional Requests (Option A)
- Servers perform **semantic overlap detection** for conflicting triple/quad changes. On conflict: `409 Conflict` with problem+json (see §9).
- Servers **MAY** also use HTTP preconditions for early failure:
    - For **branch resources** (e.g., POST to `/version/commits` with `branch`), set **strong `ETag`** to the **current head-commit-id** of that branch. If request includes `If-Match: "<expectedCommitId>"` and it doesn’t match current head → **`412 Precondition Failed`**.
    - Preconditions are **advisory**; servers **MUST still** perform semantic overlap detection and may still return `409` after passing preconditions.
- For **commit resources**, `ETag` is the commit id (immutable).

## 7. Changesets
- Media types: `text/rdf-patch` (REQUIRED). Optional binary form `application/vnd.apache.jena.rdfpatch+thrift`.
- A **no-op patch** (applies cleanly but yields no dataset change) **MUST NOT** create a new commit → `204 No Content`.

## 8. Status Codes (common set)
- `200 OK` (reads), `201 Created` (new commit), `202 Accepted` (async apply), `204 No Content` (no-op),
- `400 Bad Request` (selector_conflict, schema errors), `406 Not Acceptable`, `409 Conflict` (overlap), `412 Precondition Failed` (ETag mismatch), `415 Unsupported Media Type`, `422 Unprocessable Entity` (patch applies syntactically but violates constraints), `500`.

## 9. Error Format (problem+json)
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "code": "concurrent_write_conflict",
  "conflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "_:b0 or IRI",
      "predicate": "IRI",
      "object": "term or value",
      "details": "overlapping add/delete on same quad"
    }
  ]
}
```
**Canonical `code` values** (shared): `selector_conflict`, `merge_conflict`, `concurrent_write_conflict`, `graph_not_found`, `tag_retarget_forbidden`, `rebase_conflict`, `cherry_pick_conflict`.

**Conflict Item Schema:** Each conflict item **MUST** include `subject`, `predicate`, `object`. The `graph` field is **REQUIRED** for dataset-scoped conflicts (aligning with GSP extension).

## 10. Interoperability with Graph Store Protocol Extension
When a server implements **both** this extension and the Graph Store Protocol Version Control Extension:
- Both protocols **MUST** share the same underlying commit DAG, branch refs, and tag refs.
- Commits created via GSP operations (PUT/POST/DELETE on graphs) appear in the history accessible via `GET /version/history` and `GET /version/commits/{id}`.
- Commits created via SPARQL Protocol operations (UPDATE, RDF Patch application) are visible to GSP clients when querying commit history.
- The `/version/` namespace is **shared**: both protocols use the same `/version/refs`, `/version/commits/{id}`, `/version/merge`, `/version/tags`, and advanced operation endpoints.
- Batch endpoints are **distinct**: `/version/batch` (Protocol) vs `/version/batch-graphs` (GSP) to avoid routing ambiguity.
- Selectors (`branch`, `commit`, `asOf`) work identically across both protocols.
- Conflict detection and resolution semantics are unified: the `graph` field is **REQUIRED** in conflict items for both protocols.

## 11. JSON Schemas (selected)
- **Conflict item**: `graph` **REQUIRED** (consistent across Protocol and GSP extensions).
- **Batch**: array with `type` ∈ {`query`,`update`,`applyPatch`}, plus selector fields as in §4.
- **Merge request**: `{"into": "branch", "from": "ref", "strategy": "three-way|ours|theirs|manual", "fastForward": "allow|only|never", "resolutions": [...]}`
- **Squash request**: `{"branch": "branch-name", "commits": ["id1", "id2"], "message": "combined message", "author": "..."}`

## 12. Security Considerations
Align author identity with authentication; protect merge/tag operations by policy; rate-limit large diffs; sanitize commit messages; log ref mutations.

## 13. IANA Considerations (Provisional)
- Header fields: `SPARQL-Version-Control`, `SPARQL-VC-Author`, `SPARQL-VC-Message`, `SPARQL-VC-Commit` (provisional).
- Link relation: `version-control` (provisional).
- Media type registration for optional binary RDF Patch (if standardized).

## 14. ABNF (normative snippets)
```
commit-id    = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG
branch       = 1*( ALPHA / DIGIT / "-" / "_" / "/" )
selector     = ( "branch=" branch ) / ( "commit=" commit-id ) / ( "asOf=" date-time )
```