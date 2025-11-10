# Phase 4: Version Control Vocabulary Extension

**Status:** ✅ COMPLETED (2025-11-10)
**Priority:** Medium
**Estimated Time:** 3-4 hours
**Actual Time:** ~4 hours
**Complexity:** Medium-High

---

## Overview

Define and implement a custom RDF vocabulary for CHUCC's version control features (branches, tags, commits). Extend service description to expose these features, making version control capabilities discoverable.

**Goal:** Make CHUCC's unique version control features discoverable through standard RDF vocabulary.

---

## Prerequisites

- ✅ Phase 3 completed (dataset integration working)
- ✅ Datasets and graphs exposed in service description
- ✅ Phase 1 vocabulary design document available

---

## Implementation Steps

### Step 1: Formalize Version Control Vocabulary

**Location:** `docs/api/version-control-vocabulary.md`

Create formal vocabulary specification document:

```markdown
# CHUCC Version Control Vocabulary

**Namespace:** `http://chucc.org/ns/version-control#`
**Prefix:** `vc:`
**Version:** 1.0
**Status:** Draft

## Overview

This vocabulary extends SPARQL 1.1 Service Description to describe version control
capabilities for RDF datasets. It enables discovery of branches, tags, commits, and
version control operations.

## Classes

### vc:VersionedDataset

**Type:** `rdfs:Class`
**Subclass of:** `sd:Dataset`
**Definition:** A dataset with version control capabilities (branches, tags, commits)

**Properties:**
- `vc:defaultBranch` - Name of the default branch (e.g., "main")
- `vc:branch` - A branch in this dataset
- `vc:tag` - A tag in this dataset

**Example:**
```turtle
<http://localhost:8080/default> a vc:VersionedDataset ;
  vc:defaultBranch "main" ;
  vc:branch [ ... ] .
```

### vc:Branch

**Type:** `rdfs:Class`
**Definition:** A named mutable reference to a commit

**Properties:**
- `vc:name` - Branch name (string)
- `vc:head` - Current HEAD commit ID
- `vc:protected` - Whether branch is protected from deletion (boolean)
- `vc:createdAt` - Branch creation timestamp
- `vc:updatedAt` - Last update timestamp

**Example:**
```turtle
[
  a vc:Branch ;
  vc:name "main" ;
  vc:head "01936d8f-1234-7890-abcd-ef1234567890" ;
  vc:protected true ;
  vc:createdAt "2025-01-15T10:30:00Z"^^xsd:dateTime
] .
```

### vc:Tag

**Type:** `rdfs:Class`
**Definition:** A named immutable reference to a commit

**Properties:**
- `vc:name` - Tag name (string)
- `vc:target` - Target commit ID
- `vc:createdAt` - Tag creation timestamp
- `vc:message` - Optional tag message

**Example:**
```turtle
[
  a vc:Tag ;
  vc:name "v1.0.0" ;
  vc:target "01936d8f-1234-7890-abcd-ef1234567890" ;
  vc:message "Release 1.0.0" ;
  vc:createdAt "2025-01-15T12:00:00Z"^^xsd:dateTime
] .
```

### vc:Commit

**Type:** `rdfs:Class`
**Definition:** A snapshot of the dataset at a point in time

**Properties:**
- `vc:commitId` - Unique commit identifier (UUIDv7)
- `vc:message` - Commit message
- `vc:author` - Commit author
- `vc:timestamp` - Commit timestamp
- `vc:parent` - Parent commit(s)

## Operations

### vc:VersionControl

**Type:** `sd:Feature`
**Definition:** Indicates that the service supports version control operations

### vc:TimeTravel

**Type:** `sd:Feature`
**Definition:** Indicates support for historical queries (asOf selector)

### vc:Merge

**Type:** `sd:Feature`
**Definition:** Indicates support for three-way merge operations

### vc:Rebase

**Type:** `sd:Feature`
**Definition:** Indicates support for rebase operations

## Full Example

```turtle
@prefix vc: <http://chucc.org/ns/version-control#> .
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<http://localhost:8080/default> a vc:VersionedDataset ;
  vc:defaultBranch "main" ;
  vc:branch [
    a vc:Branch ;
    vc:name "main" ;
    vc:head "01936d8f-1234-7890-abcd-ef1234567890" ;
    vc:protected true
  ] ;
  vc:branch [
    a vc:Branch ;
    vc:name "develop" ;
    vc:head "01936d90-5678-7890-abcd-ef1234567890" ;
    vc:protected false
  ] ;
  vc:tag [
    a vc:Tag ;
    vc:name "v1.0.0" ;
    vc:target "01936d8f-1234-7890-abcd-ef1234567890" ;
    vc:message "Initial release"
  ] .
```
```

---

### Step 2: Extend ServiceDescriptionService with Version Control

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

```java
private Resource addDataset(Model model, Resource service, String datasetName) {
  String datasetUri = baseUrl + "/" + datasetName;
  Resource dataset = model.createResource(datasetUri);

  // Link from service
  service.addProperty(
      model.createProperty(SD_NS + "availableGraphs"),
      dataset);

  // Dataset metadata
  dataset.addProperty(RDF.type, model.createResource(SD_NS + "Dataset"));
  dataset.addProperty(RDF.type, model.createResource(VC_NS + "VersionedDataset"));
  dataset.addProperty(
      model.createProperty("http://rdfs.org/ns/void#", "sparqlEndpoint"),
      model.createResource(datasetUri + "/sparql"));

  // Add version control metadata
  addVersionControlMetadata(model, dataset, datasetName);

  return dataset;
}

private void addVersionControlMetadata(Model model, Resource dataset, String datasetName) {
  // Default branch
  Optional<Branch> mainBranch = branchRepository.findById(datasetName, "main");
  if (mainBranch.isPresent()) {
    dataset.addProperty(
        model.createProperty(VC_NS + "defaultBranch"),
        model.createLiteral("main"));
  }

  // Add all branches
  List<Branch> branches = branchRepository.findAll(datasetName);
  for (Branch branch : branches) {
    Resource branchResource = createBranchResource(model, branch);
    dataset.addProperty(
        model.createProperty(VC_NS + "branch"),
        branchResource);
  }

  // Add all tags
  List<Tag> tags = tagRepository.findAll(datasetName);
  for (Tag tag : tags) {
    Resource tagResource = createTagResource(model, tag);
    dataset.addProperty(
        model.createProperty(VC_NS + "tag"),
        tagResource);
  }
}

private Resource createBranchResource(Model model, Branch branch) {
  Resource branchRes = model.createResource();
  branchRes.addProperty(RDF.type, model.createResource(VC_NS + "Branch"));
  branchRes.addProperty(
      model.createProperty(VC_NS + "name"),
      model.createLiteral(branch.name()));
  branchRes.addProperty(
      model.createProperty(VC_NS + "head"),
      model.createLiteral(branch.headCommit().value()));
  branchRes.addProperty(
      model.createProperty(VC_NS + "protected"),
      model.createTypedLiteral(branch.isProtected()));

  if (branch.createdAt() != null) {
    branchRes.addProperty(
        model.createProperty(VC_NS + "createdAt"),
        model.createTypedLiteral(branch.createdAt().toString(), XSDDatatype.XSDdateTime));
  }

  return branchRes;
}

private Resource createTagResource(Model model, Tag tag) {
  Resource tagRes = model.createResource();
  tagRes.addProperty(RDF.type, model.createResource(VC_NS + "Tag"));
  tagRes.addProperty(
      model.createProperty(VC_NS + "name"),
      model.createLiteral(tag.name()));
  tagRes.addProperty(
      model.createProperty(VC_NS + "target"),
      model.createLiteral(tag.target().value()));

  if (tag.message() != null) {
    tagRes.addProperty(
        model.createProperty(VC_NS + "message"),
        model.createLiteral(tag.message()));
  }

  if (tag.createdAt() != null) {
    tagRes.addProperty(
        model.createProperty(VC_NS + "createdAt"),
        model.createTypedLiteral(tag.createdAt().toString(), XSDDatatype.XSDdateTime));
  }

  return tagRes;
}

private void addExtensionFeatures(Model model, Resource service) {
  // Version control features
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(VC_NS + "VersionControl"));
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(VC_NS + "TimeTravel"));
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(VC_NS + "Merge"));
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(VC_NS + "Rebase"));
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(VC_NS + "CherryPick"));
}
```

---

### Step 3: Inject TagRepository Dependency

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

```java
private final BranchRepository branchRepository;
private final TagRepository tagRepository;
private final DatasetGraphRepository datasetGraphRepository;

public ServiceDescriptionService(
    BranchRepository branchRepository,
    TagRepository tagRepository,
    DatasetGraphRepository datasetGraphRepository,
    @Value("${server.base-url:http://localhost:8080}") String baseUrl) {
  this.branchRepository = branchRepository;
  this.tagRepository = tagRepository;
  this.datasetGraphRepository = datasetGraphRepository;
  this.baseUrl = baseUrl;
}
```

---

### Step 4: Add Integration Tests for Version Control Metadata

**Location:** `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

```java
@Test
void serviceDescription_shouldExposeVersionControlVocabulary() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:VersionedDataset");
  assertThat(response.getBody()).contains("vc:Branch");
}

@Test
void serviceDescription_shouldListBranches() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:branch");
  assertThat(response.getBody()).contains("vc:name");
  assertThat(response.getBody()).contains("main"); // Default branch
}

@Test
void serviceDescription_shouldDescribeBranchDetails() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:head");
  assertThat(response.getBody()).contains("vc:protected");
}

@Test
void serviceDescription_shouldListTags() {
  // Arrange: Create a tag
  CreateTagRequest tagRequest = new CreateTagRequest("v1.0", "Initial release");
  HttpHeaders headers = new HttpHeaders();
  headers.set("SPARQL-VC-Author", "Test <test@example.org>");

  restTemplate.postForEntity(
      "/version/tags?dataset=default&commit=<commitId>",
      new HttpEntity<>(tagRequest, headers),
      String.class);

  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:tag");
  assertThat(response.getBody()).contains("v1.0");
}

@Test
void serviceDescription_shouldDescribeVersionControlFeatures() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:VersionControl");
  assertThat(response.getBody()).contains("vc:TimeTravel");
  assertThat(response.getBody()).contains("vc:Merge");
}

@Test
void serviceDescription_shouldIndicateDefaultBranch() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("vc:defaultBranch");
  assertThat(response.getBody()).contains("\"main\"");
}
```

---

## Files to Create

1. **Documentation**
   - `docs/api/version-control-vocabulary.md` - Formal vocabulary specification

## Files to Modify

1. **Service**
   - `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

2. **Tests**
   - `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

---

## Testing Strategy

### Integration Tests

```bash
mvn -q test -Dtest=ServiceDescriptionIT
```

**New tests:**
- ✅ Service description exposes vc:VersionedDataset
- ✅ Service description lists branches
- ✅ Service description describes branch details (head, protected)
- ✅ Service description lists tags
- ✅ Service description describes version control features
- ✅ Service description indicates default branch

### Manual Testing

```bash
# Create test branch and tag
curl -X POST http://localhost:8080/version/branches?dataset=default \
  -H "Content-Type: application/json" \
  -d '{"name": "develop", "from": "main"}'

curl -X POST "http://localhost:8080/version/tags?dataset=default&commit=<commitId>" \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Test <test@example.org>" \
  -d '{"name": "v1.0", "message": "Release 1.0"}'

# Check service description
curl -H "Accept: text/turtle" http://localhost:8080/.well-known/void

# Should show:
# - vc:VersionedDataset
# - vc:branch with name "main" and "develop"
# - vc:tag with name "v1.0"
# - vc:VersionControl feature
```

---

## Success Criteria

- ✅ Version control vocabulary formally documented
- ✅ Service description exposes vc:VersionedDataset type
- ✅ Branches are listed with metadata (name, head, protected)
- ✅ Tags are listed with metadata (name, target, message)
- ✅ Default branch indicated
- ✅ Version control features described (Merge, Rebase, TimeTravel)
- ✅ All integration tests pass
- ✅ Valid RDF output with custom vocabulary
- ✅ Zero quality violations

---

## Build Commands

```bash
# Phase 1: Static analysis
mvn -q compile checkstyle:check spotbugs:check

# Phase 2: Incremental tests
mvn -q test -Dtest=ServiceDescriptionIT

# Phase 3: Full build
mvn -q clean install
```

---

## Next Phase

**Phase 5:** [Dynamic Capabilities](./05-dynamic-capabilities.md)
- Add feature detection based on actual implementation
- Supported SPARQL features (property paths, aggregation)
- Content type negotiation

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium-High
