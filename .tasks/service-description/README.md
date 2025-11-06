# SPARQL 1.1 Service Description Implementation

**Status:** ⏳ Not Started
**Category:** Standards Compliance / Discoverability
**W3C Spec:** [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
**Total Estimated Time:** 12-16 hours

---

## Overview

Implement SPARQL 1.1 Service Description to provide machine-readable metadata about CHUCC-server's capabilities, datasets, and version control features. This enables programmatic discovery and better tooling integration.

**Goal:** Make CHUCC-server's capabilities discoverable through standard RDF vocabulary.

---

## Why Service Description for CHUCC?

### Benefits

1. **Standards Compliance**: W3C recommendation for SPARQL services
2. **Discovery**: Clients can programmatically discover:
   - Available datasets
   - Branches and tags per dataset
   - Supported SPARQL features (QUERY, UPDATE, GSP)
   - Version control extensions
   - Supported result formats
3. **Tooling Integration**: Many SPARQL tools consume service descriptions
4. **Self-Documenting**: Machine-readable API metadata
5. **Federation**: Helps with SPARQL `SERVICE` keyword

### Unique Value for CHUCC

CHUCC has features that benefit from discovery:
- **Dynamic datasets** (not just one dataset)
- **Version control primitives** (branches, tags, commits)
- **Time-travel queries** (historical access)
- **Version control operations** (merge, rebase, cherry-pick)

Service Description makes these **discoverable** rather than requiring documentation reading.

---

## Implementation Phases

### Phase 1: Research & Design (2-3 hours)
**File:** `01-research-and-design.md`

Research the spec, design vocabulary, and plan endpoint structure.

**Deliverables:**
- SD vocabulary model (standard + CHUCC extensions)
- Endpoint design (access mechanism)
- RDF format selection (Turtle, JSON-LD, RDF/XML)

---

### Phase 2: Core SD Vocabulary & Endpoint (3-4 hours)
**File:** `02-core-sd-endpoint.md`

Implement basic service description with static capabilities.

**Deliverables:**
- `GET /.well-known/void` endpoint (or `/service-description`)
- Static SD vocabulary support (SPARQL 1.1 Query, SPARQL 1.1 Update, GSP)
- Content negotiation (Turtle, JSON-LD, RDF/XML)
- Basic integration tests

**Example Response (Turtle):**
```turtle
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc: <http://chucc.org/ns/version-control#> .

<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:feature sd:UnionDefaultGraph ;
  sd:resultFormat <http://www.w3.org/ns/formats/SPARQL_Results_JSON> ;
  sd:extensionFunction vc:timeTravel .
```

---

### Phase 3: Dataset Integration (3-4 hours)
**File:** `03-dataset-integration.md`

Expose available datasets and their graphs via service description.

**Deliverables:**
- List all datasets via `DatasetRepository`
- Describe each dataset's named graphs
- Link to per-dataset endpoints
- Handle dataset creation/deletion (dynamic discovery)

**Example Addition (Turtle):**
```turtle
<http://localhost:8080/sparql> a sd:Service ;
  sd:availableGraphs <#graphs> .

<#graphs> a sd:GraphCollection ;
  sd:namedGraph [
    sd:name <http://example.org/graph1> ;
    sd:graph [ a sd:Graph ]
  ] .
```

---

### Phase 4: Version Control Vocabulary Extension (3-4 hours)
**File:** `04-version-control-extension.md`

Define custom vocabulary for CHUCC's version control features and expose branches/tags.

**Deliverables:**
- Define `vc:` namespace (version control vocabulary)
- Expose branches, tags, commits in service description
- Document the extension (similar to Protocol Extension doc)
- Link to version control endpoints

**Example Extension (Turtle):**
```turtle
@prefix vc: <http://chucc.org/ns/version-control#> .

<http://localhost:8080/default> a vc:VersionedDataset ;
  vc:defaultBranch "main" ;
  vc:branch [
    vc:name "main" ;
    vc:head "01936d8f-1234-7890-abcd-ef1234567890" ;
    vc:protected true
  ] ;
  vc:tag [
    vc:name "v1.0" ;
    vc:target "01936d8f-1234-7890-abcd-ef1234567890"
  ] .
```

---

### Phase 5: Dynamic Capabilities (1-2 hours)
**File:** `05-dynamic-capabilities.md`

Add feature detection based on actual implementation.

**Deliverables:**
- Supported SPARQL features (property paths, aggregation, subqueries)
- Supported content types (result formats, input formats)
- Extension functions (if any beyond standard SPARQL)

---

### Phase 6: Documentation (1 hour)
**File:** `06-documentation.md`

Document the Service Description implementation and vocabulary extension.

**Deliverables:**
- Update API documentation
- Create vocabulary reference document
- Add examples to OpenAPI spec
- Update C4 diagrams if needed

---

## Task Organization

Each phase has its own markdown file with:
1. **Overview** - What needs to be implemented
2. **Requirements** - Spec references
3. **Implementation Steps** - Detailed guide
4. **Success Criteria** - Definition of done
5. **Testing Strategy** - Test approach
6. **Files to Create/Modify** - Complete file list

---

## Endpoint Access Mechanism

**Options:**
1. `GET /.well-known/void` (VOIID standard)
2. `GET /service-description` (explicit endpoint)
3. `GET /sparql` (with specific query for service description)

**Recommendation:** Use `GET /.well-known/void` for standards compliance, with fallback to `/service-description`.

---

## RDF Vocabularies

### Standard Vocabularies (from W3C spec)

- **SD**: Service Description vocabulary (`http://www.w3.org/ns/sparql-service-description#`)
- **VOID**: Vocabulary of Interlinked Datasets (`http://rdfs.org/ns/void#`)

### CHUCC Extension Vocabulary

- **VC**: Version Control vocabulary (`http://chucc.org/ns/version-control#`)
  - Classes: `vc:VersionedDataset`, `vc:Branch`, `vc:Tag`, `vc:Commit`
  - Properties: `vc:branch`, `vc:tag`, `vc:defaultBranch`, `vc:head`, `vc:protected`

---

## Content Negotiation

Support multiple RDF serializations:
- **Turtle** (text/turtle) - Default, human-readable
- **JSON-LD** (application/ld+json) - JSON-based, machine-readable
- **RDF/XML** (application/rdf+xml) - Legacy compatibility
- **N-Triples** (application/n-triples) - Simple format

---

## Testing Strategy

### Unit Tests
- Test service description generation logic
- Test vocabulary serialization
- Test content negotiation

### Integration Tests
- Test `GET /.well-known/void` endpoint
- Test Turtle/JSON-LD/RDF-XML responses
- Test dataset discovery
- Test version control metadata exposure

### Read-Only Operation
- No CQRS commands/events needed (read-only)
- Query existing repositories directly
- Returns immediately with synchronized data

---

## Quality Gates

- ✅ All endpoints return valid RDF
- ✅ Content negotiation works correctly
- ✅ Service description reflects actual capabilities
- ✅ Integration tests pass
- ✅ Zero quality violations (Checkstyle, SpotBugs, PMD, compiler warnings)
- ✅ Full build passes: `mvn -q clean install`

---

## Dependencies

- Apache Jena (already in project) - RDF processing
- Spring Boot (already in project) - REST endpoints
- Existing repositories (BranchRepository, TagRepository, DatasetRepository)

---

## References

- [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
- [VOID Vocabulary](https://www.w3.org/TR/void/)
- [CHUCC Protocol Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [C4 Component Diagram](../../docs/architecture/c4-level3-component.md)

---

## Success Criteria

When all phases complete:
- ✅ Service description endpoint accessible
- ✅ Datasets discoverable programmatically
- ✅ Branches and tags exposed via custom vocabulary
- ✅ Version control capabilities documented in RDF
- ✅ Multiple RDF formats supported
- ✅ Standards-compliant W3C implementation
- ✅ Documentation complete

---

## Next Steps

1. Start with Phase 1: Research & Design
2. Read the W3C spec thoroughly
3. Design vocabulary extensions
4. Create detailed implementation tasks

---

**Last Updated:** 2025-11-06
**Status:** Ready to start
