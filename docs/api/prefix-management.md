# Prefix Management in CHUCC Server

**Implementation Guide for [Prefix Management Protocol (PMP) v1.0](../../protocol/Prefix_Management_Protocol.md)**

---

## Table of Contents

1. [Overview](#overview)
2. [URL Structure](#url-structure)
3. [Version Control Integration](#version-control-integration)
4. [Operations](#operations)
5. [Time-Travel Queries](#time-travel-queries)
6. [Merge Behavior](#merge-behavior)
7. [RDFPatch Integration](#rdfpatch-integration)
8. [IDE Integration](#ide-integration)
9. [Implementation Architecture](#implementation-architecture)
10. [Examples](#examples)

---

## Overview

CHUCC Server implements the [Prefix Management Protocol (PMP)](../../protocol/Prefix_Management_Protocol.md) with **version control integration**. Prefix changes are treated as first-class commits, enabling:

✅ **Version history** - Track who changed prefixes and when
✅ **Time-travel** - View prefixes at any commit
✅ **Merge support** - Prefix changes merge automatically with branches
✅ **Audit trail** - Full history via event sourcing

**Key Principle:** Prefix modifications create commits (not in-place updates). This follows CHUCC's CQRS + Event Sourcing architecture.

---

## URL Structure

### Base Protocol Compatibility

CHUCC provides **both** generic and version-explicit endpoints:

```
# Generic endpoint (base protocol)
/datasets/{dataset}/prefixes

# Version-explicit endpoints (CHUCC-specific)
/version/datasets/{dataset}/branches/{branch}/prefixes
/version/datasets/{dataset}/commits/{commitId}/prefixes
```

### Generic Endpoint Behavior

The generic endpoint operates on the **HEAD of the default branch** (`main`):

```http
GET /datasets/mydata/prefixes
↓ (internally resolves to)
GET /version/datasets/mydata/branches/main/prefixes
```

**Use case:** Generic SPARQL clients that don't need version control awareness.

### Version-Explicit Endpoints (Recommended)

For full version control capabilities, use explicit endpoints:

```http
# Read prefixes from specific branch
GET /version/datasets/{dataset}/branches/{branch}/prefixes

# Modify prefixes on specific branch (creates commit)
PUT /version/datasets/{dataset}/branches/{branch}/prefixes
PATCH /version/datasets/{dataset}/branches/{branch}/prefixes
DELETE /version/datasets/{dataset}/branches/{branch}/prefixes?prefix=...

# Time-travel: Read prefixes at specific commit
GET /version/datasets/{dataset}/commits/{commitId}/prefixes
```

---

## Version Control Integration

### Prefix Changes Create Commits

**Unlike non-versioned servers** (which modify prefixes in-place), CHUCC treats prefix changes as **commit-creating operations**.

**Example:**
```http
PATCH /version/datasets/mydata/branches/main/prefixes
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "message": "Add geospatial prefixes",
  "prefixes": {
    "geo": "http://www.opengis.net/ont/geosparql#",
    "sf": "http://www.opengis.net/ont/sf#"
  }
}
```

**Response:**
```http
201 Created
Location: /version/datasets/mydata/commits/01JCDN4XYZ...
ETag: "01JCDN4XYZ..."
Content-Type: application/json

{
  "dataset": "mydata",
  "branch": "main",
  "commitId": "01JCDN4XYZ...",
  "message": "Add geospatial prefixes",
  "author": "Alice <alice@example.org>",
  "timestamp": "2025-11-06T10:30:00Z"
}
```

**What happened internally:**
1. Current prefixes retrieved from materialized branch
2. RDFPatch generated with `PA` directives
3. `CommitCreatedEvent` published to Kafka
4. ReadModelProjector updates branch HEAD
5. Response returned immediately (eventual consistency)

### Required Headers

All write operations **MUST** include:

```http
SPARQL-VC-Author: Alice <alice@example.org>
```

This is the commit author (same as other CHUCC version control operations).

### Branch Protection

Prefix modifications respect branch protection rules:

```http
PUT /version/datasets/mydata/branches/main/prefixes
→ 403 Forbidden (if main is protected)

{
  "type": "/problems/protected-branch",
  "title": "Protected Branch",
  "detail": "Branch 'main' is protected. Create a feature branch instead."
}
```

**Workaround:** Create feature branch, modify prefixes, merge via pull request.

---

## Operations

### GET - Retrieve Prefix Map

**Read current branch:**
```http
GET /version/datasets/mydata/branches/main/prefixes
Accept: application/json
```

**Response:**
```json
{
  "dataset": "mydata",
  "branch": "main",
  "commitId": "01JCDN3KXQ...",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "foaf": "http://xmlns.com/foaf/0.1/",
    "dct": "http://purl.org/dc/terms/"
  }
}
```

**Implementation:**
```java
// Read from materialized branch (LRU cache)
DatasetGraph dsg = materializedBranchRepository
    .getMaterializedBranch("mydata", "main");
PrefixMapping pm = dsg.getDefaultGraph().getPrefixMapping();
Map<String, String> prefixes = pm.getNsPrefixMap();
```

---

### PUT - Replace Entire Prefix Map

**Replace all prefixes:**
```http
PUT /version/datasets/mydata/branches/main/prefixes
Content-Type: application/json
SPARQL-VC-Author: Alice <alice@example.org>

{
  "message": "Simplify prefix map",
  "prefixes": {
    "ex": "http://example.org/",
    "schema": "http://schema.org/"
  }
}
```

**Generated RDFPatch:**
```
TX .
# Delete old prefixes
PD rdf: .
PD rdfs: .
PD foaf: .
PD dct: .
# Add new prefixes
PA ex: <http://example.org/> .
PA schema: <http://schema.org/> .
TC .
```

**Response:**
```http
201 Created
Location: /version/datasets/mydata/commits/01JCDN5ABC...
ETag: "01JCDN5ABC..."

{
  "commitId": "01JCDN5ABC...",
  "message": "Simplify prefix map"
}
```

---

### PATCH - Add/Update Selected Prefixes

**Merge-update (non-destructive):**
```http
PATCH /version/datasets/mydata/branches/main/prefixes
Content-Type: application/json
SPARQL-VC-Author: Bob <bob@example.org>

{
  "message": "Add Dublin Core terms",
  "prefixes": {
    "dct": "http://purl.org/dc/terms/",
    "dcmi": "http://purl.org/dc/dcmitype/"
  }
}
```

**Generated RDFPatch:**
```
TX .
PA dct: <http://purl.org/dc/terms/> .
PA dcmi: <http://purl.org/dc/dcmitype/> .
# Existing prefixes (ex, schema) unchanged
TC .
```

**Use case:** Add new prefixes without affecting existing ones.

---

### DELETE - Remove Prefixes

**Remove specific prefixes:**
```http
DELETE /version/datasets/mydata/branches/main/prefixes?prefix=temp&prefix=test
SPARQL-VC-Author: Alice <alice@example.org>
```

**Query parameters:**
- `prefix=temp` - Remove prefix `temp`
- `prefix=test` - Remove prefix `test`
- `message=Cleanup+temporary+prefixes` - Optional commit message (URL-encoded)

**Generated RDFPatch:**
```
TX .
PD temp: .
PD test: .
TC .
```

**Response:**
```http
201 Created
Location: /version/datasets/mydata/commits/01JCDN6DEF...

{
  "commitId": "01JCDN6DEF...",
  "message": "Cleanup temporary prefixes"
}
```

---

## Time-Travel Queries

### View Prefixes at Specific Commit

```http
GET /version/datasets/mydata/commits/01JCDN2XYZ.../prefixes
```

**Response:**
```json
{
  "dataset": "mydata",
  "commitId": "01JCDN2XYZ...",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}
```

**Implementation:**
```java
// Rebuild dataset at specific commit
DatasetGraph dsg = materializedViewRebuildService
    .rebuildAtCommit("mydata", "01JCDN2XYZ...");
PrefixMapping pm = dsg.getDefaultGraph().getPrefixMapping();
```

**Performance:**
- If commit is cached → instant (materialized view already built)
- If not cached → ~1s rebuild time (typical for 100 commits)

### Compare Prefixes Between Commits

```bash
# Use standard diff endpoint with ?prefixes-only flag
GET /version/datasets/mydata/commits/01JCDN2.../diff/01JCDN5...?prefixes-only=true
```

**Response:**
```json
{
  "added": {
    "geo": "http://www.opengis.net/ont/geosparql#"
  },
  "removed": {
    "temp": "http://example.org/temp/"
  },
  "modified": {
    "foaf": {
      "old": "http://xmlns.com/foaf/0.1/",
      "new": "http://example.org/my-foaf#"
    }
  }
}
```

**Note:** This endpoint is planned but not yet implemented. See `.tasks/pmp/05-diff-support.md`.

---

## Merge Behavior

### Automatic Merge

Prefix changes **merge automatically** via RDFPatch replay:

**Scenario:**
```
main:   PA rdf: <...> . PA foaf: <...> .
dev:    PA geo: <...> . PA dct: <...> .

Merge dev → main
Result: PA rdf: <...> . PA foaf: <...> . PA geo: <...> . PA dct: <...> .
```

**No conflicts** - Prefixes are additive.

### Prefix Conflicts

**Conflict occurs when same prefix has different IRIs:**

```
main:   PA foaf: <http://xmlns.com/foaf/0.1/> .
dev:    PA foaf: <http://example.org/my-foaf#> .

Merge dev → main → CONFLICT!
```

**Merge response:**
```http
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev",
  "message": "Merge dev into main"
}

→ 409 Conflict

{
  "type": "/problems/merge-conflict",
  "title": "Merge Conflict",
  "conflicts": [
    {
      "type": "prefix",
      "prefix": "foaf",
      "ours": "http://xmlns.com/foaf/0.1/",
      "theirs": "http://example.org/my-foaf#"
    }
  ]
}
```

### Conflict Resolution

**Option 1: Choose resolution strategy**
```http
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev",
  "resolutionStrategy": "ours"
}

→ 201 Created (keeps main's foaf prefix)
```

**Option 2: Manual resolution**
```http
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev",
  "resolutions": {
    "prefixes": {
      "foaf": "http://xmlns.com/foaf/0.1/"
    }
  }
}
```

**Option 3: Resolve via separate commit**
```http
# After failed merge, fix prefixes manually
PATCH /version/datasets/mydata/branches/main/prefixes
{
  "message": "Resolve foaf prefix conflict",
  "prefixes": {
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}

# Then retry merge
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev"
}
```

---

## RDFPatch Integration

### PA/PD Directives

CHUCC uses [RDFPatch](https://afs.github.io/rdf-patch/) to represent all changes, including prefix modifications:

**Directives:**
- `PA prefix: <IRI>` - Add prefix (or update if exists)
- `PD prefix:` - Delete prefix

### Example Commit

**Commit metadata:**
```json
{
  "commitId": "01JCDN4XYZ...",
  "message": "Add FOAF prefix",
  "author": "Alice <alice@example.org>",
  "timestamp": "2025-11-06T10:30:00Z",
  "patch": "TX .\nPA foaf: <http://xmlns.com/foaf/0.1/> .\nTC ."
}
```

**RDFPatch contents:**
```
TX .
PA foaf: <http://xmlns.com/foaf/0.1/> .
TC .
```

### Mixed Operations

Prefixes can be modified **together with RDF data** in a single commit:

```
TX .
# Add prefix
PA ex: <http://example.org/> .
# Add triples using that prefix
A <http://example.org/alice> <http://xmlns.com/foaf/0.1/name> "Alice" .
TC .
```

**Use case:** Import RDF/XML with namespace declarations → single commit contains both prefixes and data.

---

## IDE Integration

### Use Case: SPARQL Query Editor

**Workflow:**
1. User opens SPARQL editor
2. Editor fetches prefixes from CHUCC
3. Editor inserts `PREFIX` declarations into query template

**JavaScript example:**
```javascript
async function loadPrefixesIntoEditor(dataset, branch) {
  const response = await fetch(
    `http://chucc-server/version/datasets/${dataset}/branches/${branch}/prefixes`,
    { headers: { 'Accept': 'application/json' } }
  );

  const data = await response.json();

  // Generate PREFIX block
  const prefixBlock = Object.entries(data.prefixes)
    .map(([prefix, iri]) => `PREFIX ${prefix}: <${iri}>`)
    .join('\n');

  // Insert into editor
  editor.setValue(`${prefixBlock}\n\nSELECT * WHERE {\n  ?s ?p ?o .\n}\nLIMIT 10`);
}
```

### Use Case: RDF/XML Import with Prefix Suggestion

**Workflow:**
1. User uploads RDF/XML file
2. CHUCC extracts namespace declarations
3. IDE suggests adding prefixes via PATCH

**Example:**
```xml
<!-- Uploaded RDF/XML -->
<rdf:RDF
  xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  xmlns:foaf="http://xmlns.com/foaf/0.1/"
  xmlns:schema="http://schema.org/">
  <!-- ... -->
</rdf:RDF>
```

**Suggested action:**
```http
PATCH /version/datasets/mydata/branches/main/prefixes
{
  "message": "Add prefixes from imported RDF/XML",
  "prefixes": {
    "foaf": "http://xmlns.com/foaf/0.1/",
    "schema": "http://schema.org/"
  }
}
```

**VSCode Extension Example:**
```typescript
// Show quickfix: "Add discovered prefixes to dataset?"
const action = await vscode.window.showInformationMessage(
  'Found 2 new namespace prefixes in RDF/XML',
  'Add to Dataset',
  'Ignore'
);

if (action === 'Add to Dataset') {
  await fetch(`${chucc}/version/datasets/${dataset}/branches/${branch}/prefixes`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'SPARQL-VC-Author': getUserEmail()
    },
    body: JSON.stringify({
      message: 'Add prefixes from imported RDF/XML',
      prefixes: discoveredPrefixes
    })
  });
}
```

---

## Implementation Architecture

### Command Handler

**`UpdatePrefixesCommandHandler`** (to be implemented):

```java
@Component
public class UpdatePrefixesCommandHandler {

  private final MaterializedBranchRepository materializedBranchRepository;
  private final CreateCommitCommandHandler createCommitCommandHandler;

  public CommitMetadata handle(UpdatePrefixesCommand cmd) {
    // 1. Get current prefixes from materialized branch
    DatasetGraph currentDsg = materializedBranchRepository
        .getMaterializedBranch(cmd.dataset(), cmd.branch());
    Map<String, String> oldPrefixes = currentDsg.getDefaultGraph()
        .getPrefixMapping().getNsPrefixMap();

    // 2. Generate RDFPatch with PA/PD directives
    RDFPatch patch = buildPrefixPatch(
        oldPrefixes,
        cmd.newPrefixes(),
        cmd.operation()
    );

    // 3. Create commit via existing handler
    CreateCommitCommand commitCmd = new CreateCommitCommand(
        cmd.dataset(),
        cmd.branch(),
        cmd.message().orElse(generateDefaultMessage(cmd)),
        cmd.author(),
        patch
    );

    return createCommitCommandHandler.handle(commitCmd);
  }

  private RDFPatch buildPrefixPatch(
      Map<String, String> oldPrefixes,
      Map<String, String> newPrefixes,
      Operation operation) {

    RDFPatchBuilder builder = RDFPatchBuilder.create();
    builder.txnBegin();

    switch (operation) {
      case PUT -> {
        // Remove all old prefixes
        oldPrefixes.forEach((prefix, iri) -> builder.prefixDelete(prefix));
        // Add all new prefixes
        newPrefixes.forEach((prefix, iri) -> builder.prefixAdd(prefix, iri));
      }
      case PATCH -> {
        // Add/update only specified prefixes
        newPrefixes.forEach((prefix, iri) -> builder.prefixAdd(prefix, iri));
      }
      case DELETE -> {
        // Remove specified prefixes
        newPrefixes.keySet().forEach(builder::prefixDelete);
      }
    }

    builder.txnCommit();
    return builder.build();
  }
}
```

**Key insight:** Reuses existing `CreateCommitCommandHandler` - no new event types needed!

### REST Controller

**`PrefixManagementController`** (to be implemented):

```java
@RestController
@RequestMapping("/version/datasets/{dataset}")
public class PrefixManagementController {

  @GetMapping("/branches/{branch}/prefixes")
  public ResponseEntity<PrefixResponse> getCurrentPrefixes(
      @PathVariable String dataset,
      @PathVariable String branch) {

    DatasetGraph dsg = materializedBranchRepository
        .getMaterializedBranch(dataset, branch);

    Branch branchObj = branchRepository
        .findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(dataset, branch));

    Map<String, String> prefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    return ResponseEntity.ok(new PrefixResponse(
        dataset,
        branch,
        branchObj.headCommitId(),
        prefixes
    ));
  }

  @GetMapping("/commits/{commitId}/prefixes")
  public ResponseEntity<PrefixResponse> getPrefixesAtCommit(
      @PathVariable String dataset,
      @PathVariable String commitId) {

    DatasetGraph dsg = materializedViewRebuildService
        .rebuildAtCommit(dataset, commitId);

    Map<String, String> prefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    return ResponseEntity.ok(new PrefixResponse(
        dataset,
        null, // No branch (time-travel query)
        commitId,
        prefixes
    ));
  }

  @PutMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> replacePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestHeader("SPARQL-VC-Author") String author,
      @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        Operation.PUT,
        Optional.ofNullable(request.message())
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    URI location = URI.create(
        "/version/datasets/" + dataset + "/commits/" + commit.id()
    );

    return ResponseEntity
        .created(location)
        .eTag(commit.id())
        .body(new CommitResponse(dataset, branch, commit));
  }

  @PatchMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> updatePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestHeader("SPARQL-VC-Author") String author,
      @RequestBody UpdatePrefixesRequest request) {

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        request.prefixes(),
        Operation.PATCH,
        Optional.ofNullable(request.message())
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    return ResponseEntity
        .created(URI.create("/version/datasets/" + dataset + "/commits/" + commit.id()))
        .eTag(commit.id())
        .body(new CommitResponse(dataset, branch, commit));
  }

  @DeleteMapping("/branches/{branch}/prefixes")
  public ResponseEntity<CommitResponse> deletePrefixes(
      @PathVariable String dataset,
      @PathVariable String branch,
      @RequestParam List<String> prefix,
      @RequestParam(required = false) String message,
      @RequestHeader("SPARQL-VC-Author") String author) {

    // Convert prefix list to map (value doesn't matter for DELETE)
    Map<String, String> prefixesToDelete = prefix.stream()
        .collect(Collectors.toMap(p -> p, p -> ""));

    UpdatePrefixesCommand cmd = new UpdatePrefixesCommand(
        dataset,
        branch,
        author,
        prefixesToDelete,
        Operation.DELETE,
        Optional.ofNullable(message)
    );

    CommitMetadata commit = updatePrefixesCommandHandler.handle(cmd);

    return ResponseEntity
        .created(URI.create("/version/datasets/" + dataset + "/commits/" + commit.id()))
        .eTag(commit.id())
        .body(new CommitResponse(dataset, branch, commit));
  }
}
```

### Event Flow (Reuses Existing Architecture)

```
HTTP Request (PUT /prefixes)
    ↓
PrefixManagementController
    ↓
UpdatePrefixesCommandHandler
    ↓ (generates RDFPatch with PA/PD)
CreateCommitCommandHandler
    ↓
CommitCreatedEvent published to Kafka
    ↓
ReadModelProjector consumes event
    ↓
Updates materialized branch (applies PA/PD)
    ↓
Prefix map updated in memory
```

**No new events, projectors, or repositories needed!**

---

## Examples

### Example 1: Complete Workflow

**Step 1: Create dataset and check default prefixes**
```http
POST /version/datasets/mydata
SPARQL-VC-Author: Alice <alice@example.org>

→ 202 Accepted

GET /version/datasets/mydata/branches/main/prefixes

→ 200 OK
{
  "prefixes": {}
}
```

**Step 2: Add common prefixes**
```http
PATCH /version/datasets/mydata/branches/main/prefixes
SPARQL-VC-Author: Alice <alice@example.org>

{
  "message": "Add common prefixes",
  "prefixes": {
    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
    "xsd": "http://www.w3.org/2001/XMLSchema#",
    "foaf": "http://xmlns.com/foaf/0.1/"
  }
}

→ 201 Created
{
  "commitId": "01JCDN7GHI...",
  "message": "Add common prefixes"
}
```

**Step 3: Import RDF data**
```http
PUT /version/datasets/mydata/branches/main/data?default
Content-Type: application/rdf+xml
SPARQL-VC-Author: Alice <alice@example.org>

<rdf:RDF xmlns:foaf="http://xmlns.com/foaf/0.1/">
  <foaf:Person>
    <foaf:name>Alice</foaf:name>
  </foaf:Person>
</rdf:RDF>

→ 201 Created
```

**Step 4: Query with prefixes**
```sparql
PREFIX foaf: <http://xmlns.com/foaf/0.1/>

SELECT ?name WHERE {
  ?person foaf:name ?name .
}
```

**Step 5: View commit history (includes prefix changes)**
```http
GET /version/datasets/mydata/branches/main/commits

→ 200 OK
[
  {
    "commitId": "01JCDN8JKL...",
    "message": "Add RDF data",
    "author": "Alice <alice@example.org>"
  },
  {
    "commitId": "01JCDN7GHI...",
    "message": "Add common prefixes",
    "author": "Alice <alice@example.org>"
  },
  {
    "commitId": "01JCDN6ABC...",
    "message": "Initial commit",
    "author": "System"
  }
]
```

---

### Example 2: Branch Workflow

**Create feature branch and add ontology-specific prefixes**
```http
POST /version/datasets/mydata/branches
{
  "branchName": "add-geospatial",
  "sourceBranch": "main"
}

→ 201 Created

PATCH /version/datasets/mydata/branches/add-geospatial/prefixes
SPARQL-VC-Author: Bob <bob@example.org>

{
  "message": "Add geospatial prefixes",
  "prefixes": {
    "geo": "http://www.opengis.net/ont/geosparql#",
    "sf": "http://www.opengis.net/ont/sf#",
    "geof": "http://www.opengis.net/def/function/geosparql/"
  }
}

→ 201 Created

# Merge back to main
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "add-geospatial",
  "message": "Merge geospatial prefixes"
}

→ 201 Created (prefixes automatically merged)
```

---

### Example 3: Conflict Resolution

**Create conflicting prefix definitions**
```http
# On main: Define foaf
PATCH /version/datasets/mydata/branches/main/prefixes
{
  "prefixes": { "foaf": "http://xmlns.com/foaf/0.1/" }
}

# On dev: Define foaf differently
POST /version/datasets/mydata/branches
{ "branchName": "dev", "sourceBranch": "main" }

PATCH /version/datasets/mydata/branches/dev/prefixes
{
  "prefixes": { "foaf": "http://example.org/my-foaf#" }
}

# Try to merge
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev"
}

→ 409 Conflict
{
  "conflicts": [
    {
      "type": "prefix",
      "prefix": "foaf",
      "ours": "http://xmlns.com/foaf/0.1/",
      "theirs": "http://example.org/my-foaf#"
    }
  ]
}

# Resolve: Keep main's version
POST /version/datasets/mydata/branches/main/merge
{
  "sourceBranch": "dev",
  "resolutionStrategy": "ours"
}

→ 201 Created
```

---

## Performance Considerations

### Caching

Prefix maps are **cached as part of materialized branches**:
- LRU eviction (default: 100 branches)
- Rebuilt on-demand if evicted (~1s typical)
- No separate prefix cache needed

### Optimization: Prefix-Only Queries

For extremely large datasets, reading prefixes is **very fast** (no triple scanning):

```java
// O(1) - Just read prefix mapping
PrefixMapping pm = dsg.getDefaultGraph().getPrefixMapping();
Map<String, String> prefixes = pm.getNsPrefixMap();
```

**Performance:** <1ms (in-memory hash map lookup)

---

## Security Considerations

### Authorization

Prefix modifications require same permissions as graph modifications:
- Read permissions: Can GET prefixes
- Write permissions: Can PUT/PATCH/DELETE prefixes

### Audit Trail

All prefix changes are **auditable**:
- Stored in Kafka (permanent log)
- Commit metadata includes author and timestamp
- Can query: "Who changed the foaf prefix and when?"

---

## Future Enhancements

Planned features (see `.tasks/pmp/` for details):

1. ✅ **Prefix suggestions** - Analyze dataset and suggest conventional prefixes (Session 3: Completed 2025-11-12)
2. **Bulk operations** - Import/export entire prefix catalogs
3. **Prefix diff** - Compare prefix maps between commits
4. **Prefix templates** - Reusable prefix sets across datasets
5. **Conflict prevention** - Warn before creating conflicting prefix

**Note:** Comprehensive documentation for the prefix suggestions endpoint will be added in Session 4 (OpenAPI and Comprehensive Testing).

---

## Related Documentation

- [Prefix Management Protocol (PMP) v1.0](../../protocol/Prefix_Management_Protocol.md) - Generic protocol specification
- [CQRS + Event Sourcing Architecture](../architecture/cqrs-event-sourcing.md) - Why prefixes create commits
- [RDFPatch Specification](https://afs.github.io/rdf-patch/) - PA/PD directive details
- [Version Control API](./version-control.md) - Branch, commit, merge operations

---

## Support

For implementation questions, see `.tasks/pmp/README.md` or contact the development team.

---

**End of CHUCC Prefix Management Implementation Guide**
