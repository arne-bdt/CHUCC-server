# Phase 3: Dataset Integration

**Status:** ✅ Completed
**Priority:** High
**Estimated Time:** 3-4 hours
**Actual Time:** ~3 hours
**Complexity:** Medium

---

## Overview

Integrate CHUCC's dynamic dataset system into service description. Expose available datasets, their named graphs, and dataset-specific endpoints. This makes datasets discoverable programmatically rather than requiring prior knowledge.

**Goal:** Enable clients to discover what datasets exist and what graphs they contain.

---

## Prerequisites

- ✅ Phase 2 completed (core endpoint working)
- ✅ `ServiceDescriptionService` and `ServiceDescriptionController` exist
- ✅ Basic service description returns valid RDF

---

## Implementation Steps

### Step 1: Extend ServiceDescriptionService for Datasets

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

**Modify existing service to add dataset discovery:**

```java
@Service
public class ServiceDescriptionService {

  private final BranchRepository branchRepository;
  private final DatasetGraphRepository datasetGraphRepository;
  private final String baseUrl;

  public ServiceDescriptionService(
      BranchRepository branchRepository,
      DatasetGraphRepository datasetGraphRepository,
      @Value("${server.base-url:http://localhost:8080}") String baseUrl) {
    this.branchRepository = branchRepository;
    this.datasetGraphRepository = datasetGraphRepository;
    this.baseUrl = baseUrl;
  }

  public Model generateServiceDescription() {
    Model model = ModelFactory.createDefaultModel();
    model.setNsPrefix("sd", SD_NS);
    model.setNsPrefix("vc", VC_NS);
    model.setNsPrefix("void", "http://rdfs.org/ns/void#");

    // Create service resource
    Resource service = model.createResource(baseUrl + "/sparql");
    service.addProperty(RDF.type, model.createResource(SD_NS + "Service"));

    // ... existing code ...

    // Add datasets
    addDatasets(model, service);

    return model;
  }

  private void addDatasets(Model model, Resource service) {
    // Get all datasets (find unique dataset names from branches)
    Set<String> datasetNames = branchRepository.findAllDatasets();

    for (String datasetName : datasetNames) {
      Resource dataset = addDataset(model, service, datasetName);
      addGraphsForDataset(model, dataset, datasetName);
    }
  }

  private Resource addDataset(Model model, Resource service, String datasetName) {
    String datasetUri = baseUrl + "/" + datasetName;
    Resource dataset = model.createResource(datasetUri);

    // Link from service
    service.addProperty(
        model.createProperty(SD_NS + "availableGraphs"),
        dataset);

    // Dataset metadata
    dataset.addProperty(RDF.type, model.createResource(SD_NS + "Dataset"));
    dataset.addProperty(
        model.createProperty("http://rdfs.org/ns/void#", "sparqlEndpoint"),
        model.createResource(datasetUri + "/sparql"));

    return dataset;
  }

  private void addGraphsForDataset(Model model, Resource dataset, String datasetName) {
    // Get main branch HEAD commit
    Optional<Branch> mainBranch = branchRepository.findById(datasetName, "main");
    if (mainBranch.isEmpty()) {
      return;
    }

    CommitId headCommit = mainBranch.get().headCommit();

    // Get all named graphs at HEAD
    Optional<DatasetGraph> dsg = datasetGraphRepository.getGraph(datasetName, headCommit);
    if (dsg.isEmpty()) {
      return;
    }

    // Add named graphs
    Iterator<Node> graphNames = dsg.get().listGraphNodes();
    while (graphNames.hasNext()) {
      Node graphNode = graphNames.next();
      if (!graphNode.equals(Quad.defaultGraphIRI)) {
        Graph graph = dsg.get().getGraph(graphNode);
        long tripleCount = graph.size();

        Resource namedGraphDesc = model.createResource();
        namedGraphDesc.addProperty(
            model.createProperty(SD_NS + "name"),
            model.createResource(graphNode.getURI()));

        Resource graphResource = model.createResource();
        graphResource.addProperty(RDF.type, model.createResource(SD_NS + "Graph"));
        graphResource.addProperty(
            model.createProperty("http://rdfs.org/ns/void#", "triples"),
            model.createTypedLiteral(tripleCount));

        namedGraphDesc.addProperty(
            model.createProperty(SD_NS + "graph"),
            graphResource);

        dataset.addProperty(
            model.createProperty(SD_NS + "namedGraph"),
            namedGraphDesc);
      }
    }

    // Add default graph (always present)
    Graph defaultGraph = dsg.get().getDefaultGraph();
    long defaultTripleCount = defaultGraph.size();

    Resource defaultGraphDesc = model.createResource();
    Resource defaultGraphResource = model.createResource();
    defaultGraphResource.addProperty(RDF.type, model.createResource(SD_NS + "Graph"));
    defaultGraphResource.addProperty(
        model.createProperty("http://rdfs.org/ns/void#", "triples"),
        model.createTypedLiteral(defaultTripleCount));

    defaultGraphDesc.addProperty(
        model.createProperty(SD_NS + "graph"),
        defaultGraphResource);

    dataset.addProperty(
        model.createProperty(SD_NS + "defaultGraph"),
        defaultGraphDesc);
  }
}
```

---

### Step 2: Add Dataset Discovery to BranchRepository

**Location:** `src/main/java/org/chucc/vcserver/repository/BranchRepository.java`

**Add method to find all datasets:**

```java
/**
 * Finds all dataset names.
 *
 * @return Set of dataset names
 */
public Set<String> findAllDatasets() {
  return branches.keySet().stream()
      .map(CompositeKey::key1)
      .collect(Collectors.toSet());
}
```

---

### Step 3: Add Integration Tests for Dataset Discovery

**Location:** `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

**Add new tests:**

```java
@Test
void serviceDescription_shouldListAvailableDatasets() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("sd:Dataset");
  assertThat(response.getBody()).contains("/default"); // Default dataset
}

@Test
void serviceDescription_shouldDescribeDatasetGraphs() {
  // Arrange: Add a named graph to default dataset
  String rdf = "<http://example.org/s> <http://example.org/p> \"test\" .";
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.parseMediaType("text/turtle"));

  HttpEntity<String> request = new HttpEntity<>(rdf, headers);
  restTemplate.exchange(
      "/data?graph=http://example.org/test-graph&branch=main&dataset=default",
      HttpMethod.PUT,
      request,
      String.class);

  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("sd:namedGraph");
  assertThat(response.getBody()).contains("http://example.org/test-graph");
}

@Test
void serviceDescription_shouldDescribeDefaultGraph() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("sd:defaultGraph");
}

@Test
void serviceDescription_shouldIncludeGraphSizes() {
  // Arrange: Add some triples to default graph
  String rdf = "<http://example.org/s> <http://example.org/p> \"test\" .";
  HttpHeaders headers = new HttpHeaders();
  headers.setContentType(MediaType.parseMediaType("text/turtle"));

  HttpEntity<String> request = new HttpEntity<>(rdf, headers);
  restTemplate.exchange(
      "/data?branch=main&dataset=default",
      HttpMethod.PUT,
      request,
      String.class);

  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("void:triples");
  assertThat(response.getBody()).matches(".*void:triples\\s+\"?[1-9]\\d*\"?.*"); // At least 1 triple
}

@Test
void serviceDescription_shouldIncludeSparqlEndpointPerDataset() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert
  assertThat(response.getBody()).contains("void:sparqlEndpoint");
  assertThat(response.getBody()).contains("/default/sparql");
}
```

---

### Step 4: Update OpenAPI Documentation

**Location:** `src/main/java/org/chucc/vcserver/controller/ServiceDescriptionController.java`

**Update operation descriptions:**

```java
@Operation(
    summary = "Get service description (well-known URI)",
    description = "Returns SPARQL 1.1 Service Description in RDF format with content negotiation. "
        + "Describes service capabilities, supported features, available datasets, and named graphs. "
        + "Datasets are dynamically discovered from the current repository state.")
```

---

## Example Output

### Turtle Format

```turtle
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc: <http://chucc.org/ns/version-control#> .
@prefix void: <http://rdfs.org/ns/void#> .

<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:supportedLanguage sd:SPARQL11Update ;
  sd:availableGraphs <http://localhost:8080/default> ;
  sd:availableGraphs <http://localhost:8080/mydata> .

<http://localhost:8080/default> a sd:Dataset ;
  void:sparqlEndpoint <http://localhost:8080/default/sparql> ;
  sd:defaultGraph [
    sd:graph [
      a sd:Graph ;
      void:triples 42
    ]
  ] ;
  sd:namedGraph [
    sd:name <http://example.org/metadata> ;
    sd:graph [
      a sd:Graph ;
      void:triples 128
    ]
  ] ;
  sd:namedGraph [
    sd:name <http://example.org/data> ;
    sd:graph [
      a sd:Graph ;
      void:triples 1537
    ]
  ] .

<http://localhost:8080/mydata> a sd:Dataset ;
  void:sparqlEndpoint <http://localhost:8080/mydata/sparql> ;
  sd:defaultGraph [
    sd:graph [
      a sd:Graph ;
      void:triples 0
    ]
  ] .
```

---

## Files to Modify

1. **Service**
   - `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

2. **Repository**
   - `src/main/java/org/chucc/vcserver/repository/BranchRepository.java`

3. **Controller**
   - `src/main/java/org/chucc/vcserver/controller/ServiceDescriptionController.java` (OpenAPI docs)

4. **Tests**
   - `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

---

## Testing Strategy

### Integration Tests

Run new tests:

```bash
mvn -q test -Dtest=ServiceDescriptionIT
```

**New tests:**
- ✅ Service description lists available datasets
- ✅ Service description describes named graphs
- ✅ Service description includes default graph
- ✅ Service description includes graph sizes (void:triples)
- ✅ Service description includes SPARQL endpoint per dataset

### Manual Testing

```bash
# Create a test dataset
curl -X POST http://localhost:8080/version/datasets/test-dataset \
  -H "Content-Type: application/json" \
  -H "SPARQL-VC-Author: Test <test@example.org>" \
  -d '{"description": "Test dataset"}'

# Add a named graph
curl -X PUT "http://localhost:8080/data?graph=http://example.org/test&branch=main&dataset=test-dataset" \
  -H "Content-Type: text/turtle" \
  -d "@prefix ex: <http://example.org/> . ex:subject ex:predicate \"test\" ."

# Check service description
curl -H "Accept: text/turtle" http://localhost:8080/.well-known/void

# Should show:
# - Service endpoint
# - test-dataset as sd:Dataset
# - http://example.org/test as sd:namedGraph
```

---

## Success Criteria

- ✅ Service description lists all available datasets
- ✅ Each dataset shows its SPARQL endpoint
- ✅ Named graphs are listed per dataset with URIs
- ✅ Default graph is described
- ✅ Graph sizes (void:triples) included for all graphs
- ✅ Dataset discovery is dynamic (reflects current state)
- ✅ All integration tests pass
- ✅ Valid RDF output
- ✅ Zero quality violations

---

## Performance Considerations

**Dataset Discovery:**
- O(n) where n = number of branches (to find unique datasets)
- Acceptable for typical use (10-100 datasets)

**Graph Enumeration:**
- O(m) where m = number of named graphs
- Only enumerates HEAD of main branch (snapshot)
- Cached via DatasetGraphRepository

**Optimization (if needed):**
- Cache service description (refresh on dataset creation/deletion)
- Implement `@Cacheable` with TTL

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

**Phase 4:** [Version Control Extension](./04-version-control-extension.md)
- Define custom vocabulary for branches, tags, commits
- Expose version control metadata in service description
- Document the vocabulary extension

---

**Estimated Time:** 3-4 hours
**Complexity:** Medium
