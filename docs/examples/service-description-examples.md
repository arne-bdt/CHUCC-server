# Service Description Examples

## Basic Usage

### Get Service Description

```bash
curl -H "Accept: text/turtle" http://localhost:8080/.well-known/void
```

**Response:**

```turtle
@prefix sd: <http://www.w3.org/ns/sparql-service-description#> .
@prefix vc: <http://chucc.org/ns/version-control#> .

<http://localhost:8080/sparql> a sd:Service ;
  sd:endpoint <http://localhost:8080/sparql> ;
  sd:supportedLanguage sd:SPARQL11Query ;
  sd:supportedLanguage sd:SPARQL11Update ;
  sd:feature vc:VersionControl .
```

## Discovering Datasets

### List Available Datasets

```sparql
PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?dataset WHERE {
  ?service a sd:Service ;
    sd:availableGraphs ?dataset .
  ?dataset a vc:VersionedDataset .
}
```

## Discovering Branches

### List Branches for a Dataset

```sparql
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?branchName ?head ?protected WHERE {
  <http://localhost:8080/default> vc:branch ?branch .
  ?branch vc:name ?branchName ;
          vc:head ?head ;
          vc:protected ?protected .
}
```

## Discovering Features

### Check Version Control Support

```sparql
PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>
PREFIX vc: <http://chucc.org/ns/version-control#>

ASK {
  ?service a sd:Service ;
    sd:feature vc:VersionControl .
}
```

## Programmatic Access

### Python Example (rdflib)

```python
from rdflib import Graph, Namespace, RDF

# Fetch service description
g = Graph()
g.parse("http://localhost:8080/.well-known/void", format="turtle")

# Define namespaces
SD = Namespace("http://www.w3.org/ns/sparql-service-description#")
VC = Namespace("http://chucc.org/ns/version-control#")

# Query datasets
for dataset in g.subjects(RDF.type, VC.VersionedDataset):
    print(f"Dataset: {dataset}")

    # Get branches
    for branch in g.objects(dataset, VC.branch):
        name = g.value(branch, VC.name)
        head = g.value(branch, VC.head)
        print(f"  Branch: {name} @ {head}")
```

### JavaScript Example (n3.js)

```javascript
const n3 = require('n3');
const fetch = require('node-fetch');

async function getServiceDescription() {
  const response = await fetch('http://localhost:8080/.well-known/void', {
    headers: { 'Accept': 'text/turtle' }
  });

  const turtle = await response.text();
  const parser = new n3.Parser();
  const quads = parser.parse(turtle);

  // Find all versioned datasets
  const VC = 'http://chucc.org/ns/version-control#';
  const datasets = quads
    .filter(q => q.predicate.value === 'http://www.w3.org/1999/02/22-rdf-syntax-ns#type'
                 && q.object.value === VC + 'VersionedDataset')
    .map(q => q.subject.value);

  console.log('Datasets:', datasets);
}
```

## Advanced Queries

### Discover All Version Control Features

```sparql
PREFIX sd: <http://www.w3.org/ns/sparql-service-description#>
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?feature WHERE {
  ?service a sd:Service ;
    sd:feature ?feature .
  FILTER(STRSTARTS(STR(?feature), STR(vc:)))
}
```

### Find Protected Branches

```sparql
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?dataset ?branchName WHERE {
  ?dataset a vc:VersionedDataset ;
    vc:branch ?branch .
  ?branch vc:name ?branchName ;
          vc:protected true .
}
```

### List All Tags

```sparql
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?dataset ?tagName ?commitId WHERE {
  ?dataset a vc:VersionedDataset ;
    vc:tag ?tag .
  ?tag vc:name ?tagName ;
       vc:commitId ?commitId .
}
```

### Get Dataset Default Branch

```sparql
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?dataset ?defaultBranch WHERE {
  ?dataset a vc:VersionedDataset ;
    vc:defaultBranch ?defaultBranch .
}
```

## Content Negotiation Examples

### Get Service Description in JSON-LD

```bash
curl -H "Accept: application/ld+json" http://localhost:8080/.well-known/void
```

### Get Service Description in RDF/XML

```bash
curl -H "Accept: application/rdf+xml" http://localhost:8080/.well-known/void
```

### Get Service Description in N-Triples

```bash
curl -H "Accept: application/n-triples" http://localhost:8080/.well-known/void
```

## Complete Example: Dataset Discovery Flow

```python
from rdflib import Graph, Namespace, RDF
import requests

# Step 1: Fetch service description
g = Graph()
g.parse("http://localhost:8080/.well-known/void", format="turtle")

# Define namespaces
SD = Namespace("http://www.w3.org/ns/sparql-service-description#")
VC = Namespace("http://chucc.org/ns/version-control#")

# Step 2: Find all versioned datasets
datasets = list(g.subjects(RDF.type, VC.VersionedDataset))
print(f"Found {len(datasets)} versioned datasets")

# Step 3: For each dataset, get branches
for dataset in datasets:
    print(f"\nDataset: {dataset}")

    # Get default branch
    default_branch = g.value(dataset, VC.defaultBranch)
    print(f"  Default branch: {default_branch}")

    # Get all branches
    for branch in g.objects(dataset, VC.branch):
        name = g.value(branch, VC.name)
        head = g.value(branch, VC.head)
        protected = g.value(branch, VC.protected)
        print(f"  Branch: {name}")
        print(f"    HEAD: {head}")
        print(f"    Protected: {protected}")

    # Get all tags
    for tag in g.objects(dataset, VC.tag):
        name = g.value(tag, VC.name)
        commit = g.value(tag, VC.commitId)
        print(f"  Tag: {name} -> {commit}")
```

## Integration with SPARQL Tools

### Apache Jena ARQ

```bash
# Query service description using ARQ
arq --data http://localhost:8080/.well-known/void \
    --query discover-datasets.rq
```

Where `discover-datasets.rq` contains:

```sparql
PREFIX vc: <http://chucc.org/ns/version-control#>

SELECT ?dataset ?defaultBranch WHERE {
  ?dataset a vc:VersionedDataset ;
    vc:defaultBranch ?defaultBranch .
}
```

### RDFLib Command Line

```bash
# Parse and query service description
rdfpipe http://localhost:8080/.well-known/void \
  | rdf2dot | dot -Tpng -o service-description.png
```

## Error Handling

### Check if Endpoint Exists

```bash
# Check HTTP status
curl -I http://localhost:8080/.well-known/void

# Expected: 200 OK
```

### Validate RDF Syntax

```bash
# Parse with rapper to validate
curl -s http://localhost:8080/.well-known/void | rapper -i turtle -o ntriples -
```

### Handle Missing Accept Header

```bash
# Default format is Turtle
curl http://localhost:8080/.well-known/void
```
