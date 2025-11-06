# Phase 1: Research & Design

**Status:** ⏳ Not Started
**Priority:** High (Foundation)
**Estimated Time:** 2-3 hours
**Complexity:** Medium

---

## Overview

Research SPARQL 1.1 Service Description specification, design vocabulary extensions for CHUCC's version control features, and plan the endpoint structure and RDF formats.

**Goal:** Create a solid foundation before implementation.

---

## Objectives

1. Understand SPARQL 1.1 Service Description spec thoroughly
2. Design vocabulary extension for version control features
3. Plan endpoint structure and access mechanisms
4. Select RDF serialization formats
5. Design content negotiation strategy

---

## Task 1: Study W3C Specification

### Reading List

1. **[SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)**
   - Section 2: Access Mechanism
   - Section 3: Service Description Vocabulary
   - Section 4: Basic Descriptive Properties
   - Section 5: Graph Descriptions
   - Section 6: Extension Descriptions

2. **[VOID Vocabulary](https://www.w3.org/TR/void/)**
   - Understanding dataset descriptions
   - Linksets and partitions

3. **Related Standards**
   - [SPARQL 1.1 Protocol](https://www.w3.org/TR/sparql11-protocol/)
   - [Content Negotiation](https://www.w3.org/Protocols/rfc2616/rfc2616-sec12.html)

### Key Questions to Answer

- ✅ Where should the service description be accessible?
- ✅ What access mechanisms are recommended?
- ✅ What properties are mandatory vs. optional?
- ✅ How to describe multiple datasets?
- ✅ How to describe custom extension functions/features?
- ✅ What RDF formats should be supported?

---

## Task 2: Design Version Control Vocabulary

### Namespace Definition

**Proposed Namespace:** `http://chucc.org/ns/version-control#`
**Prefix:** `vc:`

### Classes to Define

```turtle
@prefix vc: <http://chucc.org/ns/version-control#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .

# Classes
vc:VersionedDataset rdfs:subClassOf sd:Dataset ;
  rdfs:label "Versioned Dataset" ;
  rdfs:comment "A dataset with version control capabilities (branches, tags, commits)" .

vc:Branch a rdfs:Class ;
  rdfs:label "Branch" ;
  rdfs:comment "A named reference to a commit (mutable)" .

vc:Tag a rdfs:Class ;
  rdfs:label "Tag" ;
  rdfs:comment "A named reference to a commit (immutable)" .

vc:Commit a rdfs:Class ;
  rdfs:label "Commit" ;
  rdfs:comment "A snapshot of the dataset at a point in time" .

# Properties
vc:branch a rdf:Property ;
  rdfs:domain vc:VersionedDataset ;
  rdfs:range vc:Branch ;
  rdfs:label "branch" ;
  rdfs:comment "A branch in this versioned dataset" .

vc:tag a rdf:Property ;
  rdfs:domain vc:VersionedDataset ;
  rdfs:range vc:Tag ;
  rdfs:label "tag" ;
  rdfs:comment "A tag in this versioned dataset" .

vc:defaultBranch a rdf:Property ;
  rdfs:domain vc:VersionedDataset ;
  rdfs:range xsd:string ;
  rdfs:label "default branch" ;
  rdfs:comment "The name of the default branch" .

vc:head a rdf:Property ;
  rdfs:domain vc:Branch ;
  rdfs:range vc:Commit ;
  rdfs:label "head commit" ;
  rdfs:comment "The commit this branch currently points to" .

vc:protected a rdf:Property ;
  rdfs:domain vc:Branch ;
  rdfs:range xsd:boolean ;
  rdfs:label "protected" ;
  rdfs:comment "Whether this branch is protected from deletion" .

vc:target a rdf:Property ;
  rdfs:domain vc:Tag ;
  rdfs:range vc:Commit ;
  rdfs:label "target commit" ;
  rdfs:comment "The commit this tag points to" .

vc:name a rdf:Property ;
  rdfs:domain [ a owl:Class ; owl:unionOf (vc:Branch vc:Tag) ] ;
  rdfs:range xsd:string ;
  rdfs:label "name" ;
  rdfs:comment "The name of the branch or tag" .
```

### Operations to Describe

Consider describing these version control operations as extension features:
- `vc:Merge` - Three-way merge capability
- `vc:Rebase` - Rebase capability
- `vc:CherryPick` - Cherry-pick capability
- `vc:TimeTravel` - Historical queries (asOf selector)
- `vc:Blame` - Last-writer attribution

---

## Task 3: Plan Endpoint Structure

### Option 1: Well-Known URI (Recommended)

**Endpoint:** `GET /.well-known/void`
**Standard:** [RFC 5785 - Well-Known URIs](https://tools.ietf.org/html/rfc5785)

**Pros:**
- Standards-compliant (VOID convention)
- Widely recognized by SPARQL tools
- Discoverable location

**Cons:**
- Requires serving at root path (Spring routing config)

### Option 2: Explicit Endpoint

**Endpoint:** `GET /service-description`

**Pros:**
- Easier to implement (no special routing)
- Clear, self-documenting URL

**Cons:**
- Less discoverable
- Not standard convention

### Option 3: Hybrid Approach (Best of Both)

Implement both:
- `GET /.well-known/void` → primary endpoint
- `GET /service-description` → alias/fallback

### Recommendation

✅ **Use hybrid approach**: Primary at `/.well-known/void`, fallback at `/service-description`

---

## Task 4: Content Negotiation Strategy

### Supported Formats

| Format | MIME Type | Priority | Notes |
|--------|-----------|----------|-------|
| Turtle | `text/turtle` | Default | Human-readable, compact |
| JSON-LD | `application/ld+json` | High | JSON-based, machine-readable |
| RDF/XML | `application/rdf+xml` | Medium | Legacy compatibility |
| N-Triples | `application/n-triples` | Low | Simple, verbose |

### Implementation

Use Apache Jena's `RDFDataMgr.write()` with content negotiation:

```java
Model model = createServiceDescriptionModel();
String acceptHeader = request.getHeader("Accept");
Lang format = RDFLanguages.contentTypeToLang(acceptHeader);
if (format == null) {
  format = Lang.TURTLE; // Default
}
response.setContentType(format.getHeaderString());
RDFDataMgr.write(response.getOutputStream(), model, format);
```

---

## Task 5: Design Service Description Structure

### High-Level Structure

```turtle
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc: <http://chucc.org/ns/version-control#> .
@prefix void: <http://rdfs.org/ns/void#> .

# Service Description
<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;

  # Language Support
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:supportedLanguage sd:SPARQL11Update ;

  # Features
  sd:feature sd:UnionDefaultGraph ;
  sd:feature sd:BasicFederatedQuery ;

  # Result Formats
  sd:resultFormat <http://www.w3.org/ns/formats/SPARQL_Results_JSON> ;
  sd:resultFormat <http://www.w3.org/ns/formats/SPARQL_Results_XML> ;

  # Datasets
  sd:availableGraphs <#graphs> ;

  # Version Control Extensions
  vc:versionedDataset <http://localhost:8080/default> .

# Dataset Description
<http://localhost:8080/default> a vc:VersionedDataset ;
  void:sparqlEndpoint <http://localhost:8080/default/sparql> ;
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

# Graph Collection
<#graphs> a sd:GraphCollection ;
  sd:namedGraph [
    sd:name <http://example.org/graph1> ;
    sd:graph [ a sd:Graph ]
  ] .
```

---

## Deliverables

At the end of this phase, you should have:

1. ✅ **Vocabulary Design Document** (Turtle file)
   - Classes, properties, examples
   - File: `src/main/resources/vc-vocabulary.ttl`

2. ✅ **Endpoint Design Decision**
   - Access mechanism chosen
   - Content negotiation strategy

3. ✅ **Service Description Template**
   - Example Turtle representation
   - Structure for different serializations

4. ✅ **Implementation Plan**
   - Component architecture (Controller, Service, Model)
   - Integration points with existing code

---

## Files to Create

1. **Vocabulary Definition**
   - `src/main/resources/vc-vocabulary.ttl` - Version control vocabulary in Turtle

2. **Design Documentation**
   - `docs/api/service-description.md` - Service Description documentation

---

## Success Criteria

- ✅ W3C spec thoroughly understood
- ✅ Version control vocabulary designed and documented
- ✅ Endpoint structure decided
- ✅ Content negotiation strategy planned
- ✅ Service description template created
- ✅ Ready to implement Phase 2

---

## References

- [SPARQL 1.1 Service Description](https://www.w3.org/TR/sparql11-service-description/)
- [VOID Vocabulary](https://www.w3.org/TR/void/)
- [RFC 5785 - Well-Known URIs](https://tools.ietf.org/html/rfc5785)
- [Apache Jena Documentation](https://jena.apache.org/documentation/)

---

## Next Phase

**Phase 2:** [Core SD Endpoint](./02-core-sd-endpoint.md)
- Implement basic service description endpoint
- Static capability description
- Content negotiation

---

**Estimated Time:** 2-3 hours
**Complexity:** Medium (mostly research and design)
