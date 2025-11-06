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
- `GET /version/history` — list commits with optional filters (branch, since, until, author).

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

## 8. Status Codes

### SPARQL Update Operations
- **`202 Accepted`** - Update accepted, event published to Kafka for async processing
  - Headers: `Location` (commit URI), `ETag` (commit ID), `SPARQL-VC-Status: pending`
  - Body: `{"message":"Update accepted","commitId":"{id}"}`
- **`204 No Content`** - No-op (no changes detected)
- **`400 Bad Request`** - Malformed SPARQL Update
- **`409 Conflict`** - Concurrent modification detected
- **`412 Precondition Failed`** - If-Match mismatch

### SPARQL Query Operations
- **`200 OK`** - Query success
- **`400 Bad Request`** - Malformed query
- **`406 Not Acceptable`** - Format not available

### Version Control Operations
- **`202 Accepted`** - Branch/tag/merge operation accepted
- **`204 No Content`** - Delete succeeded
- **`409 Conflict`** - Merge conflict
- **`422 Unprocessable Entity`** - Invalid operation (e.g., branch exists)

**Eventual Consistency:** Write operations return 202 Accepted when event stored in Kafka. Read model updates asynchronously (typically 50-200ms). Use commit selector `?commit={id}` for immediate queries or branch selector `?branch={name}` after projection completes.

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

**Conflict Item Schema:** Each conflict item **MUST** include `subject`, `predicate`, `object`, and `graph` (required for both graph-scoped and dataset-scoped conflicts, aligning with GSP extension).

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
- **Conflict item**: `graph`, `subject`, `predicate`, and `object` all **REQUIRED** (consistent across Protocol and GSP extensions).
- **Batch**: array with `type` ∈ {`query`,`update`,`applyPatch`}, plus selector fields as in §4.
- **Merge request**: `{"into": "branch", "from": "ref", "strategy": "three-way|ours|theirs|manual", "fastForward": "allow|only|never", "resolutions": [...]}`
- **Squash request**: `{"branch": "branch-name", "commits": ["id1", "id2"], "message": "combined message", "author": "..."}`

## 12. Security Considerations

### 12.1 General Security
Align author identity with authentication; protect merge/tag operations by policy; rate-limit large diffs; sanitize commit messages; log ref mutations.

### 12.2 Naming Security
**Identifier validation** is critical to prevent security vulnerabilities:

1. **Path Traversal Prevention**: The character set FORBIDS forward slash `/` and parent directory references `..`, preventing attacks like `/../../../etc/passwd`.

2. **URL Injection Prevention**: Reserved characters (`?`, `#`, `@`, `:`) are forbidden to prevent query injection, fragment injection, and authority hijacking.

3. **Unicode Security**: Unicode NFC normalization is REQUIRED to prevent homograph attacks (e.g., Latin 'a' vs. Cyrillic 'а' U+0430).

4. **Git Notation Conflicts**: Characters with special Git meaning (`^`, `~`, `@{`) are forbidden to prevent confusion with Git reflog/ancestry notation.

5. **Kafka Topic Security**: Dataset names must comply with Kafka topic naming rules (max 249 characters, no `/\, \0\n\r\t`). Names starting with `_` SHOULD be rejected (reserved for internal topics).

**Servers MUST:**
- Validate identifiers AFTER URL decoding (prevent double-encoding attacks)
- Reject identifiers not in Unicode NFC form
- Reject reserved names (`.`, `..`)
- Enforce maximum length limits

**Clients MUST:**
- Normalize identifiers to Unicode NFC before sending
- Use only allowed characters (`[A-Za-z0-9._-]`)
- NOT percent-encode identifiers (they consist only of unreserved characters)

## 13. IANA Considerations (Provisional)
- Header fields: `SPARQL-Version-Control`, `SPARQL-VC-Author`, `SPARQL-VC-Message` (provisional).
- Link relation: `version-control` (provisional).
- Media type registration for optional binary RDF Patch (if standardized).

## 14. ABNF (normative snippets)
```
commit-id       = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG
                ; Servers MUST validate the version nibble = 7 for commit IDs

dataset-name    = 1*249identifier-char
                ; Maximum 249 characters (Kafka topic name limit)
                ; Cannot be "." or ".."
                ; SHOULD NOT start with "_" (reserved for internal Kafka topics)

branch-name     = 1*255identifier-char
                ; Maximum 255 characters (recommended)
                ; Cannot be "." or ".."
                ; SHOULD NOT start or end with "."
                ; SHOULD NOT start with "_" (reserved for internal use)

tag-name        = 1*255identifier-char
                ; Maximum 255 characters (recommended)
                ; Cannot be "." or ".."
                ; SHOULD NOT start or end with "."
                ; SHOULD NOT start with "_" (reserved for internal use)
                ; Tags are immutable once created

identifier-char = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z (case-sensitive)
DIGIT           = %x30-39             ; 0-9

selector        = ( "branch=" branch-name ) / ( "commit=" commit-id ) / ( "asOf=" date-time )
```

**Additional constraints (all identifier types):**
- Unicode NFC normalization REQUIRED
- Case-sensitive (preserve user input)
- Forward slash `/` is FORBIDDEN (prevents URL routing ambiguity in semantic routing)

**Rationale:**
- Dataset names map to Kafka topics: `vc.{dataset}.events`
- Identifiers become URL path segments: `/{dataset}/version/branches/{branch}/sparql`
- Character set consists only of RFC 3986 "unreserved characters" (no percent-encoding needed)
- Restrictions prevent path traversal, injection attacks, and Git notation conflicts

**For detailed naming conventions, see:** `protocol/NAMING_CONVENTIONS.md`

## 15. URL Encoding and Semantic Routing

### 15.1 Identifier Encoding in URLs

Dataset names, branch names, tag names, and commit IDs appear as **path segments** in versioned URIs according to the semantic routing pattern:

```
/{dataset}/version/branches/{branch}/sparql
/{dataset}/version/commits/{commitId}/data
/{dataset}/version/tags/{tag}/sparql
```

### 15.2 Encoding Requirements

1. **Identifiers** (datasets, branches, tags) use a restricted character set (`[A-Za-z0-9._-]`) that consists **only of RFC 3986 unreserved characters**. These characters require **no percent-encoding** when used in URL path segments.

2. **Commit IDs** (UUIDv7 format) consist only of hexadecimal digits and hyphens, which also require no encoding.

3. **Clients MUST NOT percent-encode these identifiers** when constructing URLs, as this would result in incorrect resource references.

4. **Servers MUST validate identifiers AFTER URL decoding** to prevent double-encoding attacks.

### 15.3 Examples

**Correct (no encoding needed):**
```http
GET /mydata/version/branches/feature-login/sparql
GET /mydata/version/branches/release.v2/sparql
GET /mydata/version/tags/v1.0.0/sparql
GET /mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/data
```

**Incorrect (unnecessary encoding):**
```http
GET /mydata/version/branches/feature%2Dlogin/sparql       # Wrong: hyphen needlessly encoded
GET /mydata/version/branches/release%2Ev2/sparql          # Wrong: period needlessly encoded
```

### 15.4 Rationale for Restricted Character Set

The forbidden forward slash (`/`) would create URL ambiguity:

```
# If branch name were "feature/login" (with slash):
GET /mydata/version/branches/feature/login/sparql
                             └─────┬─────┘
                    Is this one path variable or two?

# URL routing sees:
/{dataset}/version/branches/{var1}/{var2}/sparql  # Ambiguous!

# Would require percent-encoding:
GET /mydata/version/branches/feature%2Flogin/sparql  # Not human-readable
```

**Solution:** Use alternative separators for hierarchy:
- Dot notation: `feature.login` (like Java packages)
- Hyphen notation: `feature-login`
- Underscore notation: `feature_login`

### 15.5 Security Note

The restricted character set prevents common URL-based attacks including:
- Path traversal (`../../../etc/passwd`)
- Null byte injection (`admin%00.txt`)
- Query injection (`branch?query=...`)
- Fragment injection (`branch#fragment`)
- Double-encoding attacks (`%252F` → `%2F` → `/`)