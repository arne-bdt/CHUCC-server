# SPARQL 1.2 Graph Store Protocol – Version Control Extension

**Extends:** [SPARQL 1.2 Graph Store Protocol](https://www.w3.org/TR/sparql12-graph-store-protocol/)
**Aligned with:** SPARQL 1.2 Protocol – Version Control Extension

## A. Purpose & Scope
Adds commit-based version control to **GSP** operations over default and named graphs using RDF Patch. The model (commits/refs/selectors) is identical to the SPARQL Protocol extension.

## B. Graph Resources
Standard GSP endpoints (`/data`) are extended with version control selectors:
- `GET /data?graph={iri}` (or default graph with `default=true`) — retrieve graph at selected version
- `PUT /data?graph={iri}` — replace graph at selected ref (produces a new commit when applying to a branch)
- `POST /data?graph={iri}` — merge RDF into graph (additive; produces commit on branch)
- `PATCH /data?graph={iri}` — apply RDF Patch to the graph (produces commit on branch)
- `DELETE /data?graph={iri}` — delete graph (produces commit on branch)
- **Batch (GSP surface):** `POST /version/batch-graphs` (distinct from Protocol's `/version/batch`)

Selectors apply as URL params per §D.

**Note:** Direct and indirect graph identification from base GSP specification continue to work; version selectors are appended as additional parameters.

## C. Discovery & Media Types
- `Link: <{base}/version>; rel="version-control"`
- `Accept-Patch: text/rdf-patch` (required), optionally `application/vnd.apache.jena.rdfpatch+thrift` (binary RDF Patch)
- **PATCH operations** are an extension to the base GSP specification, enabled by the `Accept-Patch` header; servers advertising `Accept-Patch: text/rdf-patch` MUST accept PATCH requests with `Content-Type: text/rdf-patch` or the binary variant.
- For `PUT`/`POST`: standard RDF media types (Turtle, N-Triples, JSON-LD, etc.) per base GSP spec

## D. Selectors (URL parameters)
- Exactly one of `branch`, `commit`, `asOf` (inclusive) as in the Protocol §4. Same case-sensitive names.
- `default=true` (boolean) selects the default graph. This parameter is **normative** here (not appendix-only).

## E. Headers
**Request (writes):** `SPARQL-VC-Author`, `SPARQL-VC-Message` (SHOULD)

**Response:** `SPARQL-Version-Control: true`; `ETag` semantics below.

## F. Concurrency, ETag, and Conditional Requests (Option A)
- **Graph resources** expose a strong **`ETag` equal to the last-modifying commit id for that graph** under the selected ref/commit.
- Clients MAY send `If-Match: "<expectedCommitId>"` on writes to fail fast with `412` if the selected target (e.g., branch head for that graph) advanced.
- Servers MUST still perform **semantic overlap detection** across **quads (g,s,p,o)** when applying patches; on conflict return `409` with problem+json.

## G. Changesets & No-Op Rule
- `text/rdf-patch` REQUIRED for PATCH operations. Optional binary variant: `application/vnd.apache.jena.rdfpatch+thrift`.
- No-op application → **`204 No Content`**, MUST NOT create a commit.
- For PUT/POST with standard RDF formats, server computes changeset internally by diffing against current state. When the computed diff is empty (i.e., the new content is identical to the current graph state), the server MUST return **`204 No Content`** and MUST NOT create a commit.

## H. Status Codes

### Write Operations (PUT, POST, DELETE, PATCH)
- **`202 Accepted`** - Operation accepted, event published to Kafka for async processing
  - Response headers:
    - `Location`: URI of created commit (`/version/commits/{commitId}`)
    - `ETag`: Commit ID of new state
    - `SPARQL-VC-Status: pending` - Read model projection in progress (typically 50-200ms)
- **`204 No Content`** - No-op (no changes detected, no commit created)
- **`400 Bad Request`** - Invalid RDF content, malformed patch, or invalid parameters
- **`409 Conflict`** - Concurrent modification detected (semantic overlap in quads)
- **`412 Precondition Failed`** - `If-Match` header doesn't match current state
- **`415 Unsupported Media Type`** - Content-Type not supported
- **`422 Unprocessable Entity`** - Syntactically valid but semantically invalid request

### Read Operations (GET, HEAD, OPTIONS)
- **`200 OK`** - Success
- **`404 Not Found`** - Graph does not exist
- **`406 Not Acceptable`** - Requested format not available

### Eventual Consistency
Write operations use **eventual consistency** via event sourcing:
1. HTTP 202 returned when event published to Kafka (durable storage)
2. Read model updates asynchronously via event projection (typically 50-200ms)
3. Clients can:
   - Query immediately using commit selector: `?commit={commitId}` (bypasses read model, uses event store directly)
   - Query via branch selector after projection completes: `?branch={branch}` (uses read model)
   - Use `If-Match` header with ETag to detect if read model hasn't caught up yet (returns 412 if not ready)

## I. Error Format (problem+json)
Same base as Protocol §9, but **`graph` is REQUIRED** in each conflict item for GSP operations.

## J. Shared Version Control Operations
All version control operations defined in the Protocol extension are **shared** and accessible via the same `/version/` endpoints:
- **History:** `GET /version/history` — list commits (including those from GSP operations)
- **Commits:** `GET /version/commits/{id}` — view commit metadata
- **Refs:** `GET /version/refs` — list branches and tags
- **Merge:** `POST /version/merge` — merge branches (see Protocol §3.3)
- **Advanced ops:** cherry-pick, revert, reset, rebase, squash (see Protocol §3.4)
- **Tags:** create, list, delete tags (see Protocol §3.5)

## K. JSON Schemas (selected)
- **Conflict item (GSP):** `graph` **REQUIRED**; `subject`, `predicate`, `object` also required.
- **Batch-graphs payload:**
  ```json
  {
    "mode": "single|multiple",
    "branch": "branch-name",
    "author": "author-id",
    "message": "commit message",
    "operations": [
      {"method": "PUT", "graph": "http://ex.org/g1", "data": "...", "contentType": "text/turtle"},
      {"method": "PATCH", "graph": "http://ex.org/g2", "patch": "...", "contentType": "text/rdf-patch"},
      {"method": "DELETE", "graph": "http://ex.org/g3"}
    ]
  }
  ```

## L. Security Considerations

### L.1 General Security
Match Protocol §12.1. Additionally, ensure graph IRIs are validated/authorized per tenant/policy; avoid open redirect-like misuse via `graph` parameters.

### L.2 Naming Security
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

**For detailed security analysis, see:** `protocol/NAMING_CONVENTIONS.md` and `docs/architecture/naming-conventions-analysis.md`

## M. IANA Considerations
Same as Protocol §12.

## N. Interoperability with SPARQL Protocol Extension
When a server implements **both** GSP and SPARQL Protocol version control extensions:
- **Shared commit DAG:** All commits (from GSP operations or SPARQL updates) exist in a single, unified version history.
- **Unified refs:** Branches and tags are shared; a commit from a GSP PUT/POST/DELETE is visible when querying via SPARQL Protocol `/version/history`.
- **Consistent selectors:** The same `branch`, `commit`, `asOf` selectors work across both protocols to target the same version state.
- **Cross-protocol queries:** Clients can create a commit via GSP (e.g., PUT a graph) then immediately query that state via SPARQL Protocol using the returned commit ID.
- **Conflict semantics:** Both protocols use the same conflict detection and resolution mechanisms; the `graph` field is **REQUIRED** in all conflict representations.

This enables workflows like:
1. Create feature branch via Protocol: `POST /version/refs` with `{"name": "feature-x", "from": "main"}`
2. Update graphs via GSP: `PUT /data?graph=http://ex.org/g1&branch=feature-x`
3. Merge via shared endpoint: `POST /version/merge` with `{"into": "main", "from": "feature-x"}`
4. Query merged result via Protocol: `GET /sparql?query=...&branch=main`

## O. ABNF (normative snippets)
```
iri             = URI-reference        ; per RFC 3986/3987
selector        = ( "branch=" branch-name ) / ( "commit=" commit-id ) / ( "asOf=" date-time )
param-def       = "default=" ( "true" / "false" )

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
```

**Additional constraints (all identifier types):**
- Unicode NFC normalization REQUIRED
- Case-sensitive (preserve user input)
- Forward slash `/` is FORBIDDEN (prevents URL routing ambiguity in semantic routing)

**Rationale:**
- Dataset names map to Kafka topics: `vc.{dataset}.events`
- Identifiers become URL path segments: `/{dataset}/version/branches/{branch}/data`
- Character set consists only of RFC 3986 "unreserved characters" (no percent-encoding needed)
- Restrictions prevent path traversal, injection attacks, and Git notation conflicts

**For detailed naming conventions, see:** `protocol/NAMING_CONVENTIONS.md`

**Note:** These definitions are shared with the SPARQL 1.2 Protocol – Version Control Extension.

## P. URL Encoding and Semantic Routing

### P.1 Identifier Encoding in URLs

Dataset names, branch names, tag names, and commit IDs appear as **path segments** in versioned URIs according to the semantic routing pattern:

```
/{dataset}/version/branches/{branch}/data?graph={iri}
/{dataset}/version/commits/{commitId}/data?default
/{dataset}/version/tags/{tag}/data?graph={iri}
```

### P.2 Encoding Requirements

1. **Identifiers** (datasets, branches, tags) use a restricted character set (`[A-Za-z0-9._-]`) that consists **only of RFC 3986 unreserved characters**. These characters require **no percent-encoding** when used in URL path segments.

2. **Commit IDs** (UUIDv7 format) consist only of hexadecimal digits and hyphens, which also require no encoding.

3. **Graph IRIs** in query parameters (`?graph={iri}`) MUST be percent-encoded according to RFC 3986 rules.

4. **Clients MUST NOT percent-encode identifiers** (dataset, branch, tag, commit) when constructing URLs, as this would result in incorrect resource references.

5. **Servers MUST validate identifiers AFTER URL decoding** to prevent double-encoding attacks.

### P.3 Examples

**Correct (identifiers not encoded, graph IRI encoded):**
```http
GET /mydata/version/branches/feature-login/data?graph=http%3A%2F%2Fexample.org%2Femployees
GET /mydata/version/branches/release.v2/data?default
GET /mydata/version/tags/v1.0.0/data?graph=http%3A%2F%2Fexample.org%2Fmetadata
```

**Incorrect (identifiers needlessly encoded):**
```http
GET /mydata/version/branches/feature%2Dlogin/data?graph=...       # Wrong
GET /mydata/version/branches/release%2Ev2/data?default            # Wrong
```

### P.4 Rationale for Restricted Character Set

The forbidden forward slash (`/`) would create URL ambiguity:

```
# If branch name were "feature/login" (with slash):
GET /mydata/version/branches/feature/login/data?graph=...
                             └─────┬─────┘
                    Is this one path variable or two?

# URL routing sees:
/{dataset}/version/branches/{var1}/{var2}/data  # Ambiguous!

# Would require percent-encoding:
GET /mydata/version/branches/feature%2Flogin/data?graph=...  # Not human-readable
```

**Solution:** Use alternative separators for hierarchy:
- Dot notation: `feature.login` (like Java packages)
- Hyphen notation: `feature-login`
- Underscore notation: `feature_login`

### P.5 Security Note

The restricted character set prevents common URL-based attacks including:
- Path traversal (`../../../etc/passwd`)
- Null byte injection (`admin%00.txt`)
- Query injection (`branch?query=...`)
- Fragment injection (`branch#fragment`)
- Double-encoding attacks (`%252F` → `%2F` → `/`)

**For complete URL encoding guidelines, see:** SPARQL 1.2 Protocol – Version Control Extension §15.

## Q. Examples

### Example 1: Retrieve Graph at Branch Head
```http
GET /data?graph=http://example.org/employees&branch=main HTTP/1.1
Host: example.org
Accept: text/turtle
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/turtle
ETag: "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"
SPARQL-Version-Control: true

@prefix ex: <http://example.org/> .
ex:alice ex:role "Engineer" .
ex:bob ex:role "Manager" .
```

### Example 2: Replace Graph with New Content
```http
PUT /data?graph=http://example.org/employees&branch=main HTTP/1.1
Host: example.org
Content-Type: text/turtle
SPARQL-VC-Author: alice@example.org
SPARQL-VC-Message: Promote Bob to Director
If-Match: "01936b2e-3f47-7c89-a5b3-0a1e8c9d4f2a"

@prefix ex: <http://example.org/> .
ex:alice ex:role "Engineer" .
ex:bob ex:role "Director" .
```

**Response:**
```http
HTTP/1.1 200 OK
ETag: "01936b2f-8a5c-7d12-b4e3-1c2d3e4f5a6b"
Location: /version/commits/01936b2f-8a5c-7d12-b4e3-1c2d3e4f5a6b
SPARQL-Version-Control: true
```

### Example 3: Apply RDF Patch to Graph
```http
PATCH /data?graph=http://example.org/employees&branch=main HTTP/1.1
Host: example.org
Content-Type: text/rdf-patch
SPARQL-VC-Author: bob@example.org
SPARQL-VC-Message: Add new employee Charlie

A <http://example.org/charlie> <http://example.org/role> "Developer" <http://example.org/employees> .
```

**Response:**
```http
HTTP/1.1 200 OK
ETag: "01936b30-1f2a-7e34-c5d6-2d3e4f5a6b7c"
Location: /version/commits/01936b30-1f2a-7e34-c5d6-2d3e4f5a6b7c
```

### Example 4: Time-Travel Query
```http
GET /data?graph=http://example.org/employees&branch=main&asOf=2025-10-01T12:00:00Z HTTP/1.1
Host: example.org
Accept: text/turtle
```

**Response:** Returns graph state as of October 1, 2025.

### Example 5: Conflict on Concurrent Write
```http
PUT /data?graph=http://example.org/employees&branch=main HTTP/1.1
...
```

**Response:**
```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Concurrent write conflict",
  "status": 409,
  "code": "concurrent_write_conflict",
  "detail": "Branch 'main' advanced since your read",
  "conflicts": [
    {
      "graph": "http://example.org/employees",
      "subject": "http://example.org/bob",
      "predicate": "http://example.org/role",
      "object": "Director",
      "details": "Overlapping modification on same triple"
    }
  ]
}
```

---

## Appendix: Shared Canonical Semantics (Non-normative)
- Commit ids are UUIDv7 textual form; servers MUST validate version nibble.
- Selectors are **exclusive**; `asOf` is inclusive when used with `branch`.
- Overlap detection considers the **quad** identity; blank-node identity is in-scope as serialized in RDF Patch.
- Servers implementing **both** surfaces should keep `/version/batch` (Protocol) and `/version/batch-graphs` (GSP) distinct to avoid routing ambiguity while sharing the same commit store.