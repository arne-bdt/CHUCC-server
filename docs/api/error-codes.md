# Error Codes Reference

This document lists all error codes returned by the SPARQL 1.2 Protocol Version Control Extension.
All errors follow RFC 7807 Problem Details format with `application/problem+json` media type.

## Error Response Format

All error responses include:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Detailed explanation of the error",
  "instance": "/errors/uuid",
  "code": "error_code",
  "hint": "Suggestion for fixing the error",
  "additional_field": "Additional context-specific fields"
}
```

## Graph Store Protocol Errors

### selector_conflict

**HTTP Status:** 400 Bad Request
**Description:** Multiple mutually exclusive selector parameters were provided.

**Common Causes:**
- Both `graph` and `default=true` parameters provided
- Neither `graph` nor `default=true` provided
- Multiple version selectors (`branch`, `commit`, `asOf`) provided simultaneously

**Example:**
```http
GET /data?graph=http://example.org/g&default=true
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Parameters 'graph' and 'default' are mutually exclusive",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440000",
  "code": "selector_conflict",
  "hint": "Ensure exactly one of graph/default and one of branch/commit/asOf is provided"
}
```

**Resolution:**
- Provide exactly one of: `graph=<IRI>` or `default=true`
- Provide at most one of: `branch`, `commit`, or `asOf`

---

### graph_not_found

**HTTP Status:** 404 Not Found
**Description:** The requested named graph does not exist in the dataset.

**Common Causes:**
- Graph IRI does not exist
- Graph was deleted in a previous commit
- Typo in graph IRI

**Example:**
```http
GET /data?graph=http://example.org/nonexistent
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Graph not found: http://example.org/nonexistent",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440001",
  "code": "graph_not_found",
  "graphIri": "http://example.org/nonexistent"
}
```

**Resolution:**
- Verify the graph IRI is correct
- Check if the graph exists using HEAD request
- Use version selectors (`branch`, `commit`, `asOf`) to query historical state

---

### write_on_readonly_selector

**HTTP Status:** 400 Bad Request
**Description:** Attempted to perform a write operation using a read-only selector.

**Common Causes:**
- Using `commit` parameter with PUT/POST/DELETE/PATCH
- Using `asOf` parameter with PUT/POST/DELETE/PATCH

**Example:**
```http
PUT /data?default=true&commit=abc123
Content-Type: text/turtle

<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Cannot write to a specific commit. Use branch selector for write operations.",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440002",
  "code": "write_on_readonly_selector"
}
```

**Resolution:**
- Use `branch` parameter instead of `commit` or `asOf`
- Write operations create new commits on branches only

---

### invalid_argument

**HTTP Status:** 400 Bad Request
**Description:** A request parameter contains an invalid value.

**Common Causes:**
- Malformed timestamp in `asOf` parameter
- Invalid IRI syntax in `graph` parameter
- Invalid input exceeding length limits
- Invalid characters in dataset/branch names

**Example:**
```http
GET /data?default=true&asOf=not-a-timestamp
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid timestamp format. Expected ISO 8601 format.",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440003",
  "code": "invalid_argument",
  "hint": "Use ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ"
}
```

**Resolution:**
- Use ISO 8601 format for timestamps: `2025-10-01T12:00:00Z`
- Ensure IRIs conform to RFC 3986/3987
- Check input length limits:
  - Author: 256 characters max
  - Message: 4096 characters max
  - IRI: 2048 characters max

---

### precondition_failed

**HTTP Status:** 412 Precondition Failed
**Description:** The `If-Match` header does not match the current state.

**Common Causes:**
- Another client committed changes after you fetched the ETag
- Stale ETag from outdated client state
- Concurrent modifications

**Example:**
```http
PUT /data?default=true
If-Match: "old-commit-id"
Content-Type: text/turtle

<http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Precondition Failed",
  "status": 412,
  "detail": "If-Match precondition failed: expected old-commit-id, actual new-commit-id",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440004",
  "code": "precondition_failed",
  "expected": "old-commit-id",
  "actual": "new-commit-id",
  "hint": "Fetch the latest ETag and retry the operation"
}
```

**Resolution:**
- Fetch the latest state using GET/HEAD to get current ETag
- Merge your changes with the latest state
- Retry the operation with the updated `If-Match` header

---

### concurrent_write_conflict

**HTTP Status:** 409 Conflict
**Description:** Concurrent modification detected during write operation.

**Common Causes:**
- Multiple clients writing to the same branch simultaneously
- Client working from stale base commit
- Overlapping changes to the same triples

**Example:**
```http
PUT /data?default=true&branch=main
SPARQL-VC-Author: Alice
SPARQL-VC-Message: Update data
Content-Type: text/turtle

<http://ex.org/s> <http://ex.org/p> "new value" .
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Concurrent modification detected. Another commit was made after your base commit.",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440005",
  "code": "concurrent_write_conflict",
  "baseCommit": "commit-abc",
  "currentHead": "commit-xyz",
  "hint": "Fetch the latest state, merge changes, and retry"
}
```

**Resolution:**
- Fetch the latest branch HEAD
- Merge your changes with the current state
- Use `If-Match` header for optimistic locking
- Retry the operation with resolved state

---

### unsupported_media_type

**HTTP Status:** 415 Unsupported Media Type
**Description:** The `Content-Type` header specifies an unsupported format.

**Common Causes:**
- Wrong Content-Type for PATCH (must be `text/rdf-patch`)
- Unsupported RDF serialization format
- Missing or malformed Content-Type header

**Example:**
```http
PATCH /data?default=true
Content-Type: text/turtle

TX .
A <http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .
TC .
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Unsupported Media Type",
  "status": 415,
  "detail": "Unsupported Content-Type: text/turtle",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440006",
  "code": "unsupported_media_type",
  "contentType": "text/turtle",
  "supported": "text/rdf-patch"
}
```

**Resolution:**
- For PATCH: Use `Content-Type: text/rdf-patch`
- For PUT/POST: Use supported RDF formats:
  - `text/turtle`
  - `application/rdf+xml`
  - `application/n-triples`
  - `application/ld+json`
  - `text/n3`

---

### not_acceptable

**HTTP Status:** 406 Not Acceptable
**Description:** No representation available matching the `Accept` header.

**Common Causes:**
- Requesting unsupported serialization format
- Malformed Accept header
- Accept header doesn't match any supported formats

**Example:**
```http
GET /data?default=true
Accept: application/unsupported
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Not Acceptable",
  "status": 406,
  "detail": "No acceptable representation available for Accept: application/unsupported",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440007",
  "code": "not_acceptable",
  "accept": "application/unsupported",
  "supported": "text/turtle, application/rdf+xml, application/n-triples, application/ld+json, text/n3"
}
```

**Resolution:**
- Use supported Accept headers:
  - `text/turtle` (default)
  - `application/rdf+xml`
  - `application/n-triples`
  - `application/ld+json`
  - `text/n3`

---

### invalid_patch_syntax

**HTTP Status:** 400 Bad Request
**Description:** The RDF Patch has syntax errors.

**Common Causes:**
- Malformed RDF Patch syntax
- Missing TX/TC directives
- Invalid triple syntax within patch
- Incorrect patch operations (A/D)

**Example:**
```http
PATCH /data?default=true
Content-Type: text/rdf-patch

INVALID PATCH SYNTAX
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid RDF Patch syntax: [line: 1, col: 8] Code 'INVALID' not recognized",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440008",
  "code": "invalid_patch_syntax",
  "hint": "Check RDF Patch format per https://www.w3.org/TR/rdf-patch/"
}
```

**Resolution:**
- Follow RDF Patch specification format:
  ```
  TX .
  A <subject> <predicate> <object> .
  D <subject> <predicate> <object> .
  TC .
  ```
- Ensure TX/TC directives are present
- Verify triple syntax is valid

---

### patch_not_applicable

**HTTP Status:** 422 Unprocessable Entity
**Description:** The RDF Patch cannot be applied to the current graph state.

**Common Causes:**
- DELETE operation references non-existent triple
- ADD operation would create duplicate triple
- Patch assumes different base state

**Example:**
```http
PATCH /data?default=true
Content-Type: text/rdf-patch

TX .
D <http://ex.org/s> <http://ex.org/p> <http://ex.org/o> .
TC .
```

**Response:**
```json
{
  "type": "about:blank",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Patch cannot be applied to current graph state: DELETE operations may reference non-existent triples.",
  "instance": "/errors/550e8400-e29b-41d4-a716-446655440009",
  "code": "patch_not_applicable",
  "hint": "Ensure DELETE operations reference existing triples and ADD operations don't duplicate existing triples"
}
```

**Resolution:**
- Verify the graph state matches patch expectations
- Use GET to fetch current graph state
- Adjust patch operations to match actual state
- Consider using PUT/POST for simpler updates

---

## Version Control Errors

### branch_not_found

**HTTP Status:** 404 Not Found
**Description:** The specified branch does not exist.

**Common Causes:**
- Branch name typo
- Branch has been deleted
- Using commit ID instead of branch name

**Resolution:**
- List available branches using version control API
- Create branch if needed
- Verify branch name spelling

---

### commit_not_found

**HTTP Status:** 404 Not Found
**Description:** The specified commit does not exist.

**Common Causes:**
- Invalid commit ID
- Commit ID typo
- Commit from different dataset

**Resolution:**
- Verify commit ID is correct
- Use branch selector instead
- Check commit history

---

### merge_conflict

**HTTP Status:** 409 Conflict
**Description:** Merge operation failed due to conflicting changes.

**Additional Fields:**
- `conflicts`: Array of conflicting triple changes

**Resolution:**
- Review conflict details
- Manually resolve conflicts
- Create resolution commit

---

## Best Practices

### Error Handling

1. **Always check status codes**: Don't assume success
2. **Parse problem+json**: Extract `code` field for programmatic handling
3. **Show detail to users**: The `detail` field provides helpful explanations
4. **Use hints**: The `hint` field suggests resolution steps
5. **Log instance IDs**: Include `instance` ID when reporting issues

### Retry Logic

```javascript
async function retryWithBackoff(operation, maxRetries = 3) {
  for (let i = 0; i < maxRetries; i++) {
    try {
      return await operation();
    } catch (error) {
      if (error.status === 412 || error.status === 409) {
        // Precondition Failed or Conflict - fetch latest and retry
        const latestETag = await fetchLatestETag();
        operation.etag = latestETag;
        await new Promise(r => setTimeout(r, Math.pow(2, i) * 1000));
      } else {
        throw error; // Don't retry other errors
      }
    }
  }
  throw new Error('Max retries exceeded');
}
```

### Optimistic Locking Pattern

```javascript
// 1. Fetch current state and ETag
const response = await fetch('/data?default=true');
const etag = response.headers.get('ETag');
const currentData = await response.text();

// 2. Make modifications
const modifiedData = modifyData(currentData);

// 3. Write with If-Match
const writeResponse = await fetch('/data?default=true', {
  method: 'PUT',
  headers: {
    'Content-Type': 'text/turtle',
    'If-Match': etag,
    'SPARQL-VC-Author': 'Alice',
    'SPARQL-VC-Message': 'Update data'
  },
  body: modifiedData
});

// 4. Handle 412 Precondition Failed
if (writeResponse.status === 412) {
  // Refetch, reapply changes, retry
}
```

## Support

For issues or questions about error handling:
- Check this documentation first
- Review the SPARQL 1.2 Protocol specification
- Include the `instance` ID when reporting problems
- Provide full error response for debugging
