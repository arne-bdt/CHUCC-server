# Semantic Routing for CHUCC-Server

## Executive Summary

This document defines the semantic routing concept for CHUCC-server that makes datasets, branches, and commits **directly shareable via URL**. The design extends Apache Jena Fuseki's proven routing patterns with version control semantics.

**Status:** Approved for implementation (2025-11-06)

---

## Core Design Principles

1. **Fuseki-compatible base** - `/{dataset}/{service}` for current-state operations
2. **Version-aware extension** - `/{dataset}/version/{ref}/{service}` for versioned operations
3. **RESTful hierarchy** - Resources addressable by path, not query strings
4. **Shareable URLs** - Every resource has a canonical, bookmarkable URL
5. **Immutable commit URLs** - Perfect for academic citations and reproducibility
6. **Backward compatible** - Support old query-param style during migration

---

## Quick Comparison

### Apache Jena Fuseki Pattern
```
/myDataset/sparql          # Dataset as path segment
/myDataset/data            # Service type explicit
/myDataset/data?graph=...  # Named graphs as query params
```

### CHUCC Current Pattern (To Be Deprecated)
```
/sparql?dataset=mydata&branch=main              # Query param, not shareable
/data?dataset=mydata&graph=...&branch=main      # Verbose, not RESTful
```

### CHUCC Proposed Pattern (Target)
```
/mydata/version/branches/main/sparql            # Shareable, RESTful
/mydata/version/commits/01936d8f.../data        # Immutable, citable
/mydata/version/tags/v1.0/sparql                # Human-readable versions
```

---

## URL Structure Overview

### Hierarchy Pattern

```
/{dataset}                                      Dataset root
  /sparql                                       Current state query (main HEAD)
  /data                                         Current state GSP (main HEAD)
  /update                                       Update main branch
  /version                                      Version control root
    /refs                                       List all refs
    /branches                                   Branch management
      /{name}                                   Specific branch
        /sparql                                 Query branch
        /data                                   GSP operations on branch
        /update                                 Update branch
        /merge                                  Merge into branch
        /history                                Branch history
    /commits                                    Commit management
      /{id}                                     Specific commit (immutable)
        /sparql                                 Query commit state
        /data                                   GSP operations (read-only)
        /history                                History from commit
        /diff/{otherId}                         Diff between commits
    /tags                                       Tag management
      /{name}                                   Specific tag
        /sparql                                 Query tagged version
        /data                                   GSP operations (read-only)
```

---

## Core Routing Patterns

### 1. Dataset Management

```
POST   /datasets/{name}                         Create dataset
DELETE /datasets/{name}?confirmed=true          Delete dataset
GET    /datasets                                List datasets
GET    /datasets/{name}                         Dataset metadata
```

**Example:**
```bash
curl -X POST http://localhost:8080/datasets/biodiversity \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Alice <alice@example.org>" \
  -d '{"description": "Species data"}'
```

---

### 2. Current State Operations (Main Branch HEAD)

These endpoints operate on the **HEAD of the main branch** (default behavior):

```
GET/POST  /{dataset}/sparql                     Query current state
POST      /{dataset}/update                     Update (creates commit)
GET       /{dataset}/data?graph={iri}           Get graph
PUT       /{dataset}/data?graph={iri}           Replace graph
POST      /{dataset}/data?graph={iri}           Merge into graph
DELETE    /{dataset}/data?graph={iri}           Delete graph
PATCH     /{dataset}/data?graph={iri}           Apply RDF Patch
```

**Example:**
```bash
# Query current state of dataset
curl "http://localhost:8080/mydata/sparql?query=SELECT * WHERE { ?s ?p ?o }"

# Get named graph at current state
curl "http://localhost:8080/mydata/data?graph=http://example.org/metadata"
```

---

### 3. Branch Management

```
GET    /{dataset}/version/branches              List branches
POST   /{dataset}/version/branches              Create branch
GET    /{dataset}/version/branches/{name}       Branch metadata
DELETE /{dataset}/version/branches/{name}       Delete branch
```

**Example:**
```bash
# Get main branch details
curl http://localhost:8080/mydata/version/branches/main

# Response:
{
  "name": "main",
  "commitId": "01936d8f-1234-7890-abcd-ef1234567890",
  "protected": true,
  "timestamp": "2025-11-03T10:30:00Z"
}
```

**Shareable URL:**
```
https://chucc.example.org/mydata/version/branches/main
```

---

### 4. Commit Management

```
GET    /{dataset}/version/commits               List commits
POST   /{dataset}/version/commits               Create commit
GET    /{dataset}/version/commits/{id}          Commit metadata
```

**Example:**
```bash
# Get commit details
curl http://localhost:8080/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890

# Response:
{
  "id": "01936d8f-1234-7890-abcd-ef1234567890",
  "message": "Add species metadata",
  "author": "Alice <alice@example.org>",
  "timestamp": "2025-11-03T10:30:00Z",
  "parents": ["01936d8e-..."],
  "patchSize": 42
}
```

**Shareable URL (immutable):**
```
https://chucc.example.org/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890
```

---

### 5. Tag Management

```
GET    /{dataset}/version/tags                  List tags
POST   /{dataset}/version/tags                  Create tag
GET    /{dataset}/version/tags/{name}           Tag metadata
DELETE /{dataset}/version/tags/{name}           Delete tag
```

**Example:**
```bash
# Get tag details
curl http://localhost:8080/mydata/version/tags/v1.0

# Response:
{
  "name": "v1.0",
  "commitId": "01936d8f-...",
  "message": "First stable release",
  "annotated": true
}
```

**Shareable URL:**
```
https://chucc.example.org/mydata/version/tags/v1.0
```

---

### 6. Versioned SPARQL Operations

Query or update at a **specific version reference** (branch, commit, or tag):

```
GET/POST  /{dataset}/version/branches/{name}/sparql     Query branch
GET/POST  /{dataset}/version/commits/{id}/sparql        Query commit (immutable)
GET/POST  /{dataset}/version/tags/{name}/sparql         Query tag
POST      /{dataset}/version/branches/{name}/update     Update branch (only)
```

**Examples:**
```bash
# Query main branch HEAD
curl "http://localhost:8080/mydata/version/branches/main/sparql?query=SELECT * WHERE { ?s ?p ?o }"

# Query at specific commit (IMMUTABLE - perfect for citations!)
curl "http://localhost:8080/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/sparql?query=SELECT * WHERE { ?s ?p ?o }"

# Query at tagged version
curl "http://localhost:8080/mydata/version/tags/v1.0/sparql?query=SELECT * WHERE { ?s ?p ?o }"

# Update develop branch
curl -X POST http://localhost:8080/mydata/version/branches/develop/update \
  -H "Content-Type: application/sparql-update" \
  -H "SPARQL-VC-Author: Alice" \
  -d "INSERT DATA { <s> <p> <o> }"
```

**Key Restrictions:**
- ✅ Queries work on branches, commits, tags
- ✅ Updates only work on branches
- ❌ Cannot update commits (immutable)
- ❌ Cannot update tags (immutable)

---

### 7. Versioned Graph Store Protocol

GSP operations at a **specific version reference**:

```
GET    /{dataset}/version/{ref}/data?graph={iri}              Get graph at version
GET    /{dataset}/version/{ref}/data?default                  Get default graph
PUT    /{dataset}/version/branches/{name}/data?graph={iri}    Replace (branch only)
POST   /{dataset}/version/branches/{name}/data?graph={iri}    Merge (branch only)
DELETE /{dataset}/version/branches/{name}/data?graph={iri}    Delete (branch only)
PATCH  /{dataset}/version/branches/{name}/data?graph={iri}    Patch (branch only)
```

Where `{ref}` = `branches/{name}` | `commits/{id}` | `tags/{name}`

**Examples:**
```bash
# Get graph from main branch HEAD
curl "http://localhost:8080/mydata/version/branches/main/data?graph=http://example.org/metadata"

# Get graph from specific commit (IMMUTABLE!)
curl "http://localhost:8080/mydata/version/commits/01936d8f-1234/data?default"

# Update graph on develop branch
curl -X PUT "http://localhost:8080/mydata/version/branches/develop/data?graph=http://example.org/metadata" \
  -H "Content-Type: text/turtle" \
  -H "SPARQL-VC-Author: Alice" \
  -d "<s> <p> <o> ."
```

**Shareable Graph URLs:**
```
# Current state on main
https://chucc.example.org/mydata/version/branches/main/data?graph=http://example.org/metadata

# Immutable graph at commit (perfect for data citations!)
https://chucc.example.org/mydata/version/commits/01936d8f-1234/data?default

# Graph at tagged version
https://chucc.example.org/mydata/version/tags/v1.0/data?graph=http://example.org/metadata
```

---

### 8. Advanced Version Control Operations

```
POST  /{dataset}/version/branches/{target}/merge           Merge branches
POST  /{dataset}/version/branches/{name}/reset             Reset branch
POST  /{dataset}/version/branches/{name}/revert            Revert commits
POST  /{dataset}/version/branches/{name}/cherry-pick       Cherry-pick
POST  /{dataset}/version/branches/{name}/rebase            Rebase
POST  /{dataset}/version/branches/{name}/squash            Squash commits
GET   /{dataset}/version/branches/{name}/history           Branch history
GET   /{dataset}/version/commits/{id}/history              History from commit
GET   /{dataset}/version/commits/{id1}/diff/{id2}          Diff between commits
```

**Examples:**
```bash
# Merge feature branch into main
curl -X POST http://localhost:8080/mydata/version/branches/main/merge \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Alice" \
  -d '{"source": "feature-xyz", "strategy": "three-way"}'

# Get branch history
curl "http://localhost:8080/mydata/version/branches/main/history?limit=10"

# Diff between two commits
curl "http://localhost:8080/mydata/version/commits/01936d8f-1234/diff/01936d90-5678"
```

**Shareable Diff URL:**
```
https://chucc.example.org/mydata/version/commits/01936d8f-1234/diff/01936d90-5678
```

---

### 9. Batch Operations

```
POST  /{dataset}/version/branches/{name}/batch-graphs      Batch GSP operations
POST  /{dataset}/version/branches/{name}/batch             Batch SPARQL operations
```

**Example:**
```bash
curl -X POST http://localhost:8080/mydata/version/branches/develop/batch-graphs \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Alice" \
  -d '{
    "operations": [
      {"type": "PUT", "graph": "http://example.org/g1", "content": "...", "contentType": "text/turtle"},
      {"type": "DELETE", "graph": "http://example.org/g2"}
    ],
    "mode": "single-commit"
  }'
```

---

## Key Benefits

### For End Users

✅ **Shareable** - Copy URL, share with colleagues via email/Slack
✅ **Bookmarkable** - Save important dataset versions in browser
✅ **Discoverable** - Browse API hierarchy via URLs
✅ **Immutable** - Commit/tag URLs never change (perfect for citations)
✅ **Human-readable** - Understand URL structure at a glance

### For Researchers

✅ **Reproducible** - Share exact query + data version in papers
✅ **Citable** - Permanent URLs for dataset versions in publications
✅ **Auditable** - Track data lineage via URL history
✅ **Federated** - Reference external versioned datasets in SPARQL queries

### For Developers

✅ **Fuseki-compatible** - Familiar pattern for Jena users
✅ **Consistent** - Same pattern across all endpoints
✅ **Testable** - Easy to construct test URLs programmatically
✅ **RESTful** - Standard HTTP semantics (GET, PUT, POST, DELETE)

---

## URL Shareability Use Cases

### 1. Share a Dataset
```
https://chucc.example.org/biodiversity-data
```
→ **Use case:** Documentation, API discovery

---

### 2. Share a Branch
```
https://chucc.example.org/biodiversity-data/version/branches/main
```
→ **Use case:** Collaboration ("I'm working on branch X"), monitoring

---

### 3. Share a Commit (Immutable)
```
https://chucc.example.org/biodiversity-data/version/commits/01936d8f-1234-7890-abcd-ef1234567890
```
→ **Use case:** Academic citations, reproducibility, audit trails

**Example in research paper:**
```
Jones et al. (2025). "Biodiversity Analysis Using RDF."
Dataset version: https://chucc.example.org/biodiversity-data/version/commits/01936d8f-1234
```

---

### 4. Share a Query at Specific Version
```
https://chucc.example.org/biodiversity-data/version/commits/01936d8f-1234/sparql?query=SELECT * WHERE { ?s ?p ?o LIMIT 10 }
```
→ **Use case:** Reproducible research, bug reports, live documentation examples

---

### 5. Share a Graph at Specific Version
```
https://chucc.example.org/biodiversity-data/version/tags/v1.0/data?graph=http://example.org/species
```
→ **Use case:** Data exports, federated queries, data portability

---

### 6. Share a Diff Between Commits
```
https://chucc.example.org/biodiversity-data/version/commits/01936d8f-1234/diff/01936d90-5678
```
→ **Use case:** Code reviews, change notifications, audit reports

---

## Migration Strategy

### Phase 1: Dual-Mode Support (v1.0 - Current Target)

Support **both** old and new routing patterns:

**Old Pattern (Deprecated, still works):**
```
GET /sparql?dataset=mydata&branch=main&query=...
GET /data?dataset=mydata&commit=01936d8f...&graph=...
```

**New Pattern (Recommended):**
```
GET /mydata/version/branches/main/sparql?query=...
GET /mydata/version/commits/01936d8f.../data?graph=...
```

**Implementation approach:**
- Both patterns route to same handlers
- New pattern responses include `Content-Location` header with canonical URL
- Documentation emphasizes new pattern
- OpenAPI specs show both patterns (new marked as preferred)

---

### Phase 2: Deprecation Warnings (v1.5 - Future)

- Old pattern returns `Deprecation` header
- Response includes `Link` header pointing to new URL
- Documentation marks old pattern as deprecated

---

### Phase 3: Removal (v2.0 - Breaking Change)

- Old pattern removed completely
- Migration guide provided
- Major version bump (breaking change)

---

## Response Headers for New Pattern

All responses include navigational headers:

```http
Content-Location: /mydata/version/branches/main/data?graph=http://example.org/g1
Link: </mydata/version/commits/01936d8f-1234>; rel="version"
Link: </mydata/version/branches/main>; rel="branch"
ETag: "01936d8f-1234-7890-abcd-ef1234567890"
```

**Purpose:**
- `Content-Location` - Canonical URL of resource
- `Link` headers - Related resources (HATEOAS)
- `ETag` - Version identifier for optimistic locking

---

## Implementation Guidelines

### Controller Refactoring

**Current structure:**
- `@RequestParam String dataset` - Query parameter (inconsistent)
- `/{dataset}/version/tags` - Path parameter (TagController only)

**Target structure:**
- `@PathVariable String dataset` - Path parameter (all controllers)
- `/{dataset}/version/branches/{name}` - RESTful hierarchy

**Example transformation:**

**Before:**
```java
@GetMapping("/sparql")
public ResponseEntity<String> query(
    @RequestParam(defaultValue = "default") String dataset,
    @RequestParam(required = false) String branch,
    @RequestParam String query) {
  // ...
}
```

**After:**
```java
@GetMapping("/{dataset}/version/branches/{branch}/sparql")
public ResponseEntity<String> queryBranch(
    @PathVariable String dataset,
    @PathVariable String branch,
    @RequestParam String query) {
  // ...
}

// Backward compatibility endpoint (deprecated)
@GetMapping("/sparql")
@Deprecated
public ResponseEntity<String> queryLegacy(
    @RequestParam(defaultValue = "default") String dataset,
    @RequestParam(required = false) String branch,
    @RequestParam String query) {
  // Delegate to new endpoint
}
```

---

### URL Construction Helpers

Provide utility classes for building URLs:

```java
public class VersionControlUrls {

  public static String branch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s", dataset, branch);
  }

  public static String commit(String dataset, String commitId) {
    return String.format("/%s/version/commits/%s", dataset, commitId);
  }

  public static String tag(String dataset, String tag) {
    return String.format("/%s/version/tags/%s", dataset, tag);
  }

  public static String sparqlAtBranch(String dataset, String branch) {
    return String.format("/%s/version/branches/%s/sparql", dataset, branch);
  }

  public static String commitDiff(String dataset, String from, String to) {
    return String.format("/%s/version/commits/%s/diff/%s", dataset, from, to);
  }
}
```

**Usage:**
```java
String location = VersionControlUrls.commit(dataset, commitId);
response.addHeader("Content-Location", location);
```

---

### OpenAPI Documentation Updates

Update all `@Operation` annotations with new path structure:

```java
@Operation(
  summary = "Query dataset at specific branch",
  description = "Execute SPARQL query against branch HEAD. Supports all SPARQL 1.1 query forms."
)
@GetMapping("/{dataset}/version/branches/{branch}/sparql")
public ResponseEntity<String> queryBranch(...) { }
```

---

## Testing Strategy

### Integration Tests

Test both old and new patterns during migration:

```java
@Test
void queryBranch_newPattern_shouldWork() {
  // New pattern
  String url = "/mydata/version/branches/main/sparql?query=...";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getHeaders().get("Content-Location"))
    .contains("/mydata/version/branches/main");
}

@Test
void queryBranch_oldPattern_shouldStillWork() {
  // Old pattern (deprecated but still works)
  String url = "/sparql?dataset=mydata&branch=main&query=...";
  ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  // Should include Content-Location with new pattern
  assertThat(response.getHeaders().get("Content-Location"))
    .contains("/mydata/version/branches/main");
}
```

---

## Quick Reference: Complete URL Patterns

### Dataset Management
```
POST   /datasets/{name}                                      # Create dataset
DELETE /datasets/{name}?confirmed=true                       # Delete dataset
GET    /datasets                                             # List datasets
GET    /datasets/{name}                                      # Dataset metadata
```

### Current State (Main Branch HEAD)
```
GET/POST  /{dataset}/sparql                                  # Query
POST      /{dataset}/update                                  # Update
GET       /{dataset}/data?graph={iri}                        # Get graph
PUT       /{dataset}/data?graph={iri}                        # Replace graph
POST      /{dataset}/data?graph={iri}                        # Merge graph
DELETE    /{dataset}/data?graph={iri}                        # Delete graph
PATCH     /{dataset}/data?graph={iri}                        # Patch graph
```

### Version Control Management
```
GET    /{dataset}/version/refs                               # All refs
GET    /{dataset}/version/branches                           # List branches
POST   /{dataset}/version/branches                           # Create branch
GET    /{dataset}/version/branches/{name}                    # Branch details
DELETE /{dataset}/version/branches/{name}                    # Delete branch
GET    /{dataset}/version/commits                            # List commits
POST   /{dataset}/version/commits                            # Create commit
GET    /{dataset}/version/commits/{id}                       # Commit details
GET    /{dataset}/version/tags                               # List tags
POST   /{dataset}/version/tags                               # Create tag
GET    /{dataset}/version/tags/{name}                        # Tag details
DELETE /{dataset}/version/tags/{name}                        # Delete tag
```

### Versioned SPARQL
```
GET/POST  /{dataset}/version/branches/{name}/sparql         # Query branch
GET/POST  /{dataset}/version/commits/{id}/sparql            # Query commit
GET/POST  /{dataset}/version/tags/{name}/sparql             # Query tag
POST      /{dataset}/version/branches/{name}/update         # Update branch
```

### Versioned GSP
```
GET    /{dataset}/version/{ref}/data?graph={iri}            # Get graph at version
PUT    /{dataset}/version/branches/{name}/data?graph={iri}  # Replace (branch only)
POST   /{dataset}/version/branches/{name}/data?graph={iri}  # Merge (branch only)
DELETE /{dataset}/version/branches/{name}/data?graph={iri}  # Delete (branch only)
PATCH  /{dataset}/version/branches/{name}/data?graph={iri}  # Patch (branch only)
```

### Advanced Operations
```
POST  /{dataset}/version/branches/{target}/merge            # Merge
POST  /{dataset}/version/branches/{name}/reset              # Reset
POST  /{dataset}/version/branches/{name}/revert             # Revert
POST  /{dataset}/version/branches/{name}/cherry-pick        # Cherry-pick
POST  /{dataset}/version/branches/{name}/rebase             # Rebase
POST  /{dataset}/version/branches/{name}/squash             # Squash
GET   /{dataset}/version/branches/{name}/history            # History
GET   /{dataset}/version/commits/{id}/history               # History from commit
GET   /{dataset}/version/commits/{id1}/diff/{id2}           # Diff
POST  /{dataset}/version/branches/{name}/batch-graphs       # Batch GSP
POST  /{dataset}/version/branches/{name}/batch              # Batch SPARQL
```

---

## Related Documentation

- **[Full Routing Proposal](/tmp/routing-concept-proposal.md)** - Detailed 800+ line analysis
- **[API Extensions](../api/api-extensions.md)** - CHUCC-specific extensions
- **[CQRS + Event Sourcing](./cqrs-event-sourcing.md)** - Architecture fundamentals
- **[C4 Component Diagram](./c4-level3-component.md)** - System structure

---

## Status and Next Steps

**Current Status:** ✅ Approved for implementation (2025-11-06)

**Implementation Tasks:** See `./.tasks/routing-refactoring/` for detailed task breakdown

**Target Release:** v1.0 (with dual-mode support for backward compatibility)

---

**Document Version:** 1.0
**Date:** 2025-11-06
**Author:** Claude (Anthropic)
**Approver:** arne-bdt
