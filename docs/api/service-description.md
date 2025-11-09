# SPARQL 1.1 Service Description

**Status:** Design Complete
**Implementation:** Phase 2
**W3C Spec:** [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
**VOID Spec:** [Vocabulary of Interlinked Datasets](https://www.w3.org/TR/void/)

---

## Overview

CHUCC-server implements SPARQL 1.1 Service Description to provide machine-readable metadata about:
- Available datasets and their graphs
- Supported SPARQL features (Query, Update, GSP)
- Version control capabilities (branches, tags, commits)
- Extension features (merge, rebase, time-travel)
- Supported result and patch formats

This enables **programmatic discovery** of service capabilities without requiring manual documentation reading.

---

## Endpoint Structure

### Option 1: Well-Known URI (Primary)

**Endpoint:** `GET /.well-known/void`
**Standard:** [RFC 5785 - Well-Known URIs](https://tools.ietf.org/html/rfc5785)

This is the **primary** endpoint following VOID convention for dataset metadata discovery.

**Example:**
```http
GET /.well-known/void HTTP/1.1
Host: localhost:8080
Accept: text/turtle
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/turtle

@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc: <http://chucc.org/ns/version-control#> .

<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:feature vc:Merge ;
  vc:versionControlEndpoint <http://localhost:8080/version> .
```

### Option 2: Explicit Endpoint (Fallback)

**Endpoint:** `GET /service-description`

Provides the same information via an explicit, discoverable URL.

**Example:**
```http
GET /service-description HTTP/1.1
Host: localhost:8080
Accept: application/ld+json
```

### Hybrid Approach (Recommended)

Implement **both** endpoints:
- `GET /.well-known/void` → Primary, standards-compliant
- `GET /service-description` → Fallback, explicit

Both return identical RDF content using content negotiation.

---

## Content Negotiation

CHUCC-server supports multiple RDF serialization formats via HTTP content negotiation:

| Format | MIME Type | Priority | Use Case |
|--------|-----------|----------|----------|
| **Turtle** | `text/turtle` | **Default** | Human-readable, compact |
| **JSON-LD** | `application/ld+json` | High | JSON-based, machine-readable |
| **RDF/XML** | `application/rdf+xml` | Medium | Legacy compatibility |
| **N-Triples** | `application/n-triples` | Low | Simple, verbose |

### Content Negotiation Examples

**Request Turtle (default):**
```http
GET /.well-known/void HTTP/1.1
Host: localhost:8080
Accept: text/turtle
```

**Request JSON-LD:**
```http
GET /.well-known/void HTTP/1.1
Host: localhost:8080
Accept: application/ld+json
```

**Request RDF/XML:**
```http
GET /.well-known/void HTTP/1.1
Host: localhost:8080
Accept: application/rdf+xml
```

**No Accept header (default to Turtle):**
```http
GET /.well-known/void HTTP/1.1
Host: localhost:8080
```

---

## Version Control Vocabulary

CHUCC extends SPARQL 1.1 Service Description with a custom vocabulary for version control features.

**Namespace:** `http://chucc.org/ns/version-control#`
**Prefix:** `vc:`
**Vocabulary File:** `src/main/resources/vc-vocabulary.ttl`

### Classes

| Class | Description |
|-------|-------------|
| `vc:VersionedDataset` | An RDF dataset with version control capabilities |
| `vc:Branch` | A mutable named reference to a commit |
| `vc:Tag` | An immutable named reference to a commit |
| `vc:Commit` | An immutable snapshot of the dataset |

### Dataset Properties

| Property | Domain | Range | Description |
|----------|--------|-------|-------------|
| `vc:defaultBranch` | `vc:VersionedDataset` | `xsd:string` | The name of the default branch (e.g., "main") |
| `vc:branch` | `vc:VersionedDataset` | `vc:Branch` | A branch in this dataset |
| `vc:tag` | `vc:VersionedDataset` | `vc:Tag` | A tag in this dataset |

### Branch Properties

| Property | Domain | Range | Description |
|----------|--------|-------|-------------|
| `vc:name` | `vc:Branch` or `vc:Tag` | `xsd:string` | The name of the branch/tag |
| `vc:head` | `vc:Branch` | `vc:Commit` | The commit this branch points to |
| `vc:protected` | `vc:Branch` | `xsd:boolean` | Whether the branch is protected |
| `vc:createdAt` | `vc:Branch` or `vc:Tag` | `xsd:dateTime` | Creation timestamp |
| `vc:lastUpdated` | `vc:Branch` | `xsd:dateTime` | Last update timestamp |
| `vc:commitCount` | `vc:Branch` | `xsd:nonNegativeInteger` | Total commits on this branch |

### Tag Properties

| Property | Domain | Range | Description |
|----------|--------|-------|-------------|
| `vc:target` | `vc:Tag` | `vc:Commit` | The commit this tag points to (immutable) |
| `vc:message` | `vc:Tag` | `xsd:string` | Optional annotation message |
| `vc:author` | `vc:Tag` or `vc:Commit` | `xsd:string` | Author name/email |

### Commit Properties

| Property | Domain | Range | Description |
|----------|--------|-------|-------------|
| `vc:commitId` | `vc:Commit` | `xsd:string` | Unique commit identifier (UUIDv7) |
| `vc:parent` | `vc:Commit` | `vc:Commit` | Parent commit (0+ occurrences) |
| `vc:commitMessage` | `vc:Commit` | `xsd:string` | Commit message |
| `vc:timestamp` | `vc:Commit` | `xsd:dateTime` | Commit timestamp |
| `vc:patchSize` | `vc:Commit` | `xsd:nonNegativeInteger` | Number of RDF Patch operations |

### Extension Features

CHUCC supports the following version control operations as `sd:Feature` instances:

| Feature | Description |
|---------|-------------|
| `vc:Merge` | Three-way merge with conflict detection |
| `vc:Rebase` | Reapply commits onto a different base |
| `vc:CherryPick` | Apply a specific commit to another branch |
| `vc:TimeTravel` | Query historical state using `asOf` selector |
| `vc:Blame` | Last-writer attribution for triples |
| `vc:Diff` | Compute differences as RDF Patch |
| `vc:Squash` | Combine multiple commits into one |
| `vc:Reset` | Move branch pointer to different commit |
| `vc:Revert` | Create inverse commit to undo changes |

---

## Service Description Templates

### Basic Service Description

**Describes:** Global SPARQL service with version control support

```turtle
@prefix sd:   <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc:   <http://chucc.org/ns/version-control#> .
@prefix void: <http://rdfs.org/ns/void#> .

<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;

  # Supported SPARQL languages
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:supportedLanguage sd:SPARQL11Update ;

  # Standard SPARQL features
  sd:feature sd:UnionDefaultGraph ;
  sd:feature sd:BasicFederatedQuery ;

  # Version control extensions
  sd:feature vc:Merge ;
  sd:feature vc:Rebase ;
  sd:feature vc:CherryPick ;
  sd:feature vc:TimeTravel ;
  sd:feature vc:Blame ;
  sd:feature vc:Diff ;

  # Result formats
  sd:resultFormat <http://www.w3.org/ns/formats/SPARQL_Results_JSON> ;
  sd:resultFormat <http://www.w3.org/ns/formats/SPARQL_Results_XML> ;
  sd:resultFormat <http://www.w3.org/ns/formats/Turtle> ;

  # Version control endpoint
  vc:versionControlEndpoint <http://localhost:8080/version> ;
  vc:patchFormat <http://www.w3.org/ns/formats/RDF_Patch> .
```

### Versioned Dataset Description

**Describes:** A specific dataset with branches and tags

```turtle
@prefix sd:      <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc:      <http://chucc.org/ns/version-control#> .
@prefix void:    <http://rdfs.org/ns/void#> .
@prefix dcterms: <http://purl.org/dc/terms/> .

<http://localhost:8080/datasets/mydata> a vc:VersionedDataset ;
  dcterms:title "My Versioned Dataset" ;
  dcterms:description "Example dataset with version control" ;
  void:sparqlEndpoint <http://localhost:8080/mydata/sparql> ;

  # Default branch
  vc:defaultBranch "main" ;

  # Branches
  vc:branch [
    vc:name "main" ;
    vc:head <urn:uuid:01936d8f-1234-7890-abcd-ef1234567890> ;
    vc:protected true ;
    vc:createdAt "2025-11-09T10:00:00Z"^^xsd:dateTime ;
    vc:lastUpdated "2025-11-09T15:30:00Z"^^xsd:dateTime ;
    vc:commitCount 42
  ] ;

  vc:branch [
    vc:name "dev" ;
    vc:head <urn:uuid:01936d8f-5678-7890-abcd-ef1234567890> ;
    vc:protected false ;
    vc:createdAt "2025-11-09T11:00:00Z"^^xsd:dateTime ;
    vc:lastUpdated "2025-11-09T16:00:00Z"^^xsd:dateTime ;
    vc:commitCount 15
  ] ;

  # Tags
  vc:tag [
    vc:name "v1.0" ;
    vc:target <urn:uuid:01936d8f-1234-7890-abcd-ef1234567890> ;
    vc:message "First stable release" ;
    vc:author "Alice <alice@example.org>" ;
    vc:createdAt "2025-11-09T14:00:00Z"^^xsd:dateTime
  ] ;

  vc:tag [
    vc:name "v1.1" ;
    vc:target <urn:uuid:01936d8f-9abc-7890-abcd-ef1234567890> ;
    vc:message "Bug fixes and performance improvements" ;
    vc:author "Bob <bob@example.org>" ;
    vc:createdAt "2025-11-09T16:30:00Z"^^xsd:dateTime
  ] .
```

### Commit Description

**Describes:** Individual commit metadata

```turtle
@prefix vc:  <http://chucc.org/ns/version-control#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<urn:uuid:01936d8f-1234-7890-abcd-ef1234567890> a vc:Commit ;
  vc:commitId "01936d8f-1234-7890-abcd-ef1234567890" ;
  vc:parent <urn:uuid:01936d8f-0000-7890-abcd-ef1234567890> ;
  vc:commitMessage "Add new triples for users" ;
  vc:author "Alice <alice@example.org>" ;
  vc:timestamp "2025-11-09T15:30:00Z"^^xsd:dateTime ;
  vc:patchSize 25 .

<urn:uuid:01936d8f-9999-7890-abcd-ef1234567890> a vc:Commit ;
  vc:commitId "01936d8f-9999-7890-abcd-ef1234567890" ;
  vc:parent <urn:uuid:01936d8f-1111-7890-abcd-ef1234567890> ;
  vc:parent <urn:uuid:01936d8f-2222-7890-abcd-ef1234567890> ;
  vc:commitMessage "Merge branch 'feature' into main" ;
  vc:author "Bob <bob@example.org>" ;
  vc:timestamp "2025-11-09T16:00:00Z"^^xsd:dateTime ;
  vc:patchSize 42 .
```

### Complete Example (All Datasets)

**Describes:** Service with multiple versioned datasets

```turtle
@prefix sd:      <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc:      <http://chucc.org/ns/version-control#> .
@prefix void:    <http://rdfs.org/ns/void#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .

# Service Description
<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:supportedLanguage sd:SPARQL11Update ;
  sd:feature sd:UnionDefaultGraph ;
  sd:feature vc:Merge ;
  sd:feature vc:TimeTravel ;
  sd:feature vc:Blame ;
  vc:versionControlEndpoint <http://localhost:8080/version> .

# Dataset 1: Default
<http://localhost:8080/datasets/default> a vc:VersionedDataset ;
  dcterms:title "Default Dataset" ;
  void:sparqlEndpoint <http://localhost:8080/default/sparql> ;
  vc:defaultBranch "main" ;
  vc:branch [
    vc:name "main" ;
    vc:head <urn:uuid:01936d8f-1234-7890-abcd-ef1234567890> ;
    vc:protected true ;
    vc:createdAt "2025-11-09T10:00:00Z"^^xsd:dateTime ;
    vc:lastUpdated "2025-11-09T15:30:00Z"^^xsd:dateTime ;
    vc:commitCount 100
  ] .

# Dataset 2: Users
<http://localhost:8080/datasets/users> a vc:VersionedDataset ;
  dcterms:title "User Data" ;
  void:sparqlEndpoint <http://localhost:8080/users/sparql> ;
  vc:defaultBranch "main" ;
  vc:branch [
    vc:name "main" ;
    vc:head <urn:uuid:01936d8f-5678-7890-abcd-ef1234567890> ;
    vc:protected true ;
    vc:createdAt "2025-11-09T11:00:00Z"^^xsd:dateTime ;
    vc:lastUpdated "2025-11-09T16:00:00Z"^^xsd:dateTime ;
    vc:commitCount 50
  ] ;
  vc:tag [
    vc:name "production" ;
    vc:target <urn:uuid:01936d8f-5678-7890-abcd-ef1234567890> ;
    vc:createdAt "2025-11-09T14:00:00Z"^^xsd:dateTime
  ] .
```

---

## JSON-LD Example

The same service description in JSON-LD format:

```json
{
  "@context": {
    "sd": "http://www.w3.org/ns/sparql-service-description#",
    "vc": "http://chucc.org/ns/version-control#",
    "void": "http://rdfs.org/ns/void#",
    "dcterms": "http://purl.org/dc/terms/",
    "xsd": "http://www.w3.org/2001/XMLSchema#"
  },
  "@id": "http://localhost:8080/sparql",
  "@type": "sd:Service",
  "sd:endpoint": {
    "@id": "http://localhost:8080/sparql"
  },
  "sd:supportedLanguage": [
    { "@id": "sd:SPARQL11Query" },
    { "@id": "sd:SPARQL11Update" }
  ],
  "sd:feature": [
    { "@id": "sd:UnionDefaultGraph" },
    { "@id": "vc:Merge" },
    { "@id": "vc:TimeTravel" },
    { "@id": "vc:Blame" }
  ],
  "sd:resultFormat": [
    { "@id": "http://www.w3.org/ns/formats/SPARQL_Results_JSON" },
    { "@id": "http://www.w3.org/ns/formats/SPARQL_Results_XML" }
  ],
  "vc:versionControlEndpoint": {
    "@id": "http://localhost:8080/version"
  }
}
```

---

## Implementation Design

### Component Architecture

```
ServiceDescriptionController
├── GET /.well-known/void
└── GET /service-description
    │
    ├─> ServiceDescriptionService
    │   ├─> buildServiceDescription()
    │   ├─> buildDatasetDescriptions()
    │   └─> serializeModel(format)
    │
    └─> Repositories (read-only)
        ├─> DatasetRepository
        ├─> BranchRepository
        └─> TagRepository
```

### Key Design Decisions

**1. Read-Only Operation**
- No CQRS commands/events needed
- Query repositories directly
- Returns immediately with synchronized data

**2. Dynamic Discovery**
- Query `DatasetRepository` for all datasets
- Query `BranchRepository` and `TagRepository` per dataset
- Build RDF model dynamically

**3. Caching Strategy**
- Cache service description for 60 seconds
- Invalidate cache on dataset/branch/tag creation
- Per-dataset caching (not global)

**4. Content Negotiation**
- Use Apache Jena `RDFDataMgr.write()`
- Map `Accept` header to Jena `Lang` enum
- Default to Turtle if no `Accept` header

**5. URI Structure**
- Service URI: `http://{host}/sparql`
- Dataset URI: `http://{host}/datasets/{name}`
- Commit URI: `urn:uuid:{commitId}`

---

## Access Mechanism Summary

| Endpoint | Method | Content-Type | Purpose |
|----------|--------|--------------|---------|
| `/.well-known/void` | GET | Turtle/JSON-LD/RDF-XML/N-Triples | Primary service description |
| `/service-description` | GET | Turtle/JSON-LD/RDF-XML/N-Triples | Fallback service description |

**Discovery Headers:**
```http
Link: </.well-known/void>; rel="service-description"
Link: <http://localhost:8080/version>; rel="version-control"
Accept-Patch: text/rdf-patch
```

---

## Testing Strategy

### Unit Tests

**ServiceDescriptionService:**
- `buildServiceDescription_shouldIncludeBasicProperties()`
- `buildDatasetDescription_shouldIncludeVersionControlMetadata()`
- `serializeModel_shouldSupportAllFormats()`

**RDF Validation:**
- `generatedRdf_shouldBeValidTurtle()`
- `generatedRdf_shouldBeValidJsonLd()`
- `vocabularyClasses_shouldHaveCorrectTypes()`

### Integration Tests

**ServiceDescriptionIT:**
- `wellKnownVoid_shouldReturnServiceDescription()`
- `serviceDescription_shouldReturnServiceDescription()`
- `contentNegotiation_shouldSupportTurtle()`
- `contentNegotiation_shouldSupportJsonLd()`
- `contentNegotiation_shouldSupportRdfXml()`
- `serviceDescription_shouldIncludeAllDatasets()`
- `serviceDescription_shouldIncludeBranchesAndTags()`

---

## Integration with Existing APIs

**Discovery via existing endpoints:**

```http
GET /default/sparql HTTP/1.1
Host: localhost:8080

HTTP/1.1 200 OK
Link: </.well-known/void>; rel="service-description"
Link: </version>; rel="version-control"
```

**Service description link in error responses:**

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "code": "invalid_selector",
  "detail": "Cannot specify both 'branch' and 'commit' selectors",
  "links": {
    "service-description": "/.well-known/void"
  }
}
```

---

## References

- [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
- [VOID Vocabulary](https://www.w3.org/TR/void/)
- [RFC 5785 - Well-Known URIs](https://tools.ietf.org/html/rfc5785)
- [CHUCC Protocol Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [Vocabulary File](../../src/main/resources/vc-vocabulary.ttl)

---

## Next Steps

**Phase 2: Implementation**
1. Create `ServiceDescriptionController`
2. Create `ServiceDescriptionService`
3. Implement RDF model building
4. Implement content negotiation
5. Add integration tests
6. Update OpenAPI spec

See [02-core-sd-endpoint.md](../../.tasks/service-description/02-core-sd-endpoint.md) for implementation details.

---

**Last Updated:** 2025-11-09
**Status:** Design Complete
