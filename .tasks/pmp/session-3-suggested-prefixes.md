# Session 3: Suggested Prefixes (Namespace Discovery)

**Status:** Not Started
**Estimated Time:** 2-3 hours
**Priority:** Low (Nice-to-Have)
**Dependencies:** Session 1 (Core Implementation)

---

## Overview

Analyze dataset to discover common namespaces and suggest conventional prefixes. Helps users after importing RDF/XML or discovering new ontologies.

**Goal:** Support `GET /version/datasets/{name}/branches/{branch}/prefixes/suggested`

---

## Requirements

### Endpoint to Implement

```http
GET /version/datasets/{dataset}/branches/{branch}/prefixes/suggested
Accept: application/json

→ 200 OK
{
  "dataset": "mydata",
  "branch": "main",
  "suggestions": [
    {
      "prefix": "foaf",
      "iri": "http://xmlns.com/foaf/0.1/",
      "frequency": 42,
      "status": "already_defined"
    },
    {
      "prefix": "schema",
      "iri": "http://schema.org/",
      "frequency": 38,
      "status": "suggested"
    },
    {
      "prefix": "dbo",
      "iri": "http://dbpedia.org/ontology/",
      "frequency": 15,
      "status": "suggested"
    }
  ]
}
```

---

## Implementation Steps

### Step 1: Create DTOs (20 minutes)

```java
package org.chucc.vcserver.dto;

/**
 * Suggested prefix for a namespace.
 */
public record PrefixSuggestion(
    String prefix,
    String iri,
    int frequency,  // How many times namespace appears
    Status status
) {
  public enum Status {
    SUGGESTED,       // Not yet defined
    ALREADY_DEFINED  // Already in prefix map
  }
}

/**
 * Response containing prefix suggestions.
 */
public record SuggestedPrefixesResponse(
    String dataset,
    String branch,
    List<PrefixSuggestion> suggestions
) {
  public SuggestedPrefixesResponse {
    suggestions = List.copyOf(suggestions);
  }
}
```

---

### Step 2: Create Conventional Prefix Database (30 minutes)

```java
package org.chucc.vcserver.service;

import java.util.Map;

/**
 * Database of conventional prefixes for common namespaces.
 *
 * <p>Subset of prefix.cc database.
 */
public class ConventionalPrefixes {

  private static final Map<String, String> CONVENTIONAL_PREFIXES = Map.ofEntries(
      // Core RDF/OWL/RDFS
      Map.entry("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf"),
      Map.entry("http://www.w3.org/2000/01/rdf-schema#", "rdfs"),
      Map.entry("http://www.w3.org/2002/07/owl#", "owl"),
      Map.entry("http://www.w3.org/2001/XMLSchema#", "xsd"),

      // Popular ontologies
      Map.entry("http://xmlns.com/foaf/0.1/", "foaf"),
      Map.entry("http://purl.org/dc/terms/", "dct"),
      Map.entry("http://purl.org/dc/elements/1.1/", "dc"),
      Map.entry("http://schema.org/", "schema"),
      Map.entry("http://www.w3.org/2004/02/skos/core#", "skos"),

      // Geospatial
      Map.entry("http://www.opengis.net/ont/geosparql#", "geo"),
      Map.entry("http://www.opengis.net/ont/sf#", "sf"),
      Map.entry("http://www.w3.org/2003/01/geo/wgs84_pos#", "wgs84"),

      // DBpedia
      Map.entry("http://dbpedia.org/ontology/", "dbo"),
      Map.entry("http://dbpedia.org/resource/", "dbr"),
      Map.entry("http://dbpedia.org/property/", "dbp"),

      // PROV
      Map.entry("http://www.w3.org/ns/prov#", "prov"),

      // Time
      Map.entry("http://www.w3.org/2006/time#", "time"),

      // Add more as needed...
  );

  /**
   * Gets conventional prefix for a namespace.
   *
   * @param namespace the namespace IRI
   * @return the conventional prefix, or null if unknown
   */
  public static String getConventionalPrefix(String namespace) {
    return CONVENTIONAL_PREFIXES.get(namespace);
  }

  /**
   * Checks if namespace has a conventional prefix.
   *
   * @param namespace the namespace IRI
   * @return true if conventional prefix exists
   */
  public static boolean hasConventionalPrefix(String namespace) {
    return CONVENTIONAL_PREFIXES.containsKey(namespace);
  }
}
```

---

### Step 3: Create PrefixSuggestionService (60 minutes)

```java
package org.chucc.vcserver.service;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.dto.PrefixSuggestion;
import org.chucc.vcserver.dto.PrefixSuggestion.Status;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.stereotype.Service;

/**
 * Service for suggesting prefix mappings based on dataset analysis.
 */
@Service
public class PrefixSuggestionService {

  private final MaterializedBranchRepository materializedBranchRepository;

  public PrefixSuggestionService(MaterializedBranchRepository materializedBranchRepository) {
    this.materializedBranchRepository = materializedBranchRepository;
  }

  /**
   * Analyzes dataset and suggests conventional prefixes.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return list of prefix suggestions, sorted by frequency descending
   */
  public List<PrefixSuggestion> analyzeBranch(String dataset, String branch) {
    DatasetGraph dsg = materializedBranchRepository.getMaterializedBranch(dataset, branch);

    // 1. Get current prefixes
    Map<String, String> currentPrefixes = dsg.getDefaultGraph()
        .getPrefixMapping()
        .getNsPrefixMap();

    // Invert map for lookup (IRI → prefix)
    Map<String, String> iriToPrefixMap = currentPrefixes.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    // 2. Scan dataset for namespace patterns
    Map<String, Integer> namespaceFrequency = scanForNamespaces(dsg);

    // 3. Match against conventional prefixes
    List<PrefixSuggestion> suggestions = new ArrayList<>();

    for (Map.Entry<String, Integer> entry : namespaceFrequency.entrySet()) {
      String namespace = entry.getKey();
      int frequency = entry.getValue();

      String conventionalPrefix = ConventionalPrefixes.getConventionalPrefix(namespace);
      if (conventionalPrefix != null) {
        Status status = iriToPrefixMap.containsKey(namespace)
            ? Status.ALREADY_DEFINED
            : Status.SUGGESTED;

        suggestions.add(new PrefixSuggestion(
            conventionalPrefix,
            namespace,
            frequency,
            status
        ));
      }
    }

    // 4. Sort by frequency descending
    suggestions.sort(Comparator.comparingInt(PrefixSuggestion::frequency).reversed());

    return suggestions;
  }

  /**
   * Scans dataset for namespace patterns.
   *
   * @param dsg the dataset graph
   * @return map of namespace → frequency
   */
  private Map<String, Integer> scanForNamespaces(DatasetGraph dsg) {
    Map<String, Integer> namespaceFrequency = new HashMap<>();

    // Scan all graphs
    dsg.listGraphNodes().forEachRemaining(graphName -> {
      dsg.getGraph(graphName).find().forEachRemaining(triple -> {
        extractNamespace(triple.getSubject()).ifPresent(ns ->
            namespaceFrequency.merge(ns, 1, Integer::sum));
        extractNamespace(triple.getPredicate()).ifPresent(ns ->
            namespaceFrequency.merge(ns, 1, Integer::sum));
        extractNamespace(triple.getObject()).ifPresent(ns ->
            namespaceFrequency.merge(ns, 1, Integer::sum));
      });
    });

    // Scan default graph
    dsg.getDefaultGraph().find().forEachRemaining(triple -> {
      extractNamespace(triple.getSubject()).ifPresent(ns ->
          namespaceFrequency.merge(ns, 1, Integer::sum));
      extractNamespace(triple.getPredicate()).ifPresent(ns ->
          namespaceFrequency.merge(ns, 1, Integer::sum));
      extractNamespace(triple.getObject()).ifPresent(ns ->
          namespaceFrequency.merge(ns, 1, Integer::sum));
    });

    return namespaceFrequency;
  }

  /**
   * Extracts namespace from a node (if it's a URI).
   *
   * @param node the RDF node
   * @return optional namespace
   */
  private Optional<String> extractNamespace(Node node) {
    if (!node.isURI()) {
      return Optional.empty();
    }

    String uri = node.getURI();

    // Extract namespace (everything up to last # or /)
    int hashIndex = uri.lastIndexOf('#');
    int slashIndex = uri.lastIndexOf('/');
    int splitIndex = Math.max(hashIndex, slashIndex);

    if (splitIndex > 0) {
      return Optional.of(uri.substring(0, splitIndex + 1));
    }

    return Optional.empty();
  }
}
```

---

### Step 4: Add Controller Endpoint (20 minutes)

```java
/**
 * Suggests prefix mappings based on dataset analysis.
 *
 * @param dataset the dataset name
 * @param branch the branch name
 * @return prefix suggestions
 */
@GetMapping("/branches/{branch}/prefixes/suggested")
public ResponseEntity<SuggestedPrefixesResponse> suggestPrefixes(
    @PathVariable String dataset,
    @PathVariable String branch) {

  List<PrefixSuggestion> suggestions = prefixSuggestionService
      .analyzeBranch(dataset, branch);

  SuggestedPrefixesResponse response = new SuggestedPrefixesResponse(
      dataset,
      branch,
      suggestions
  );

  return ResponseEntity.ok(response);
}
```

---

### Step 5: Write Tests (40 minutes)

```java
@Test
void suggestPrefixes_shouldReturnSuggestions() {
  // Arrange: Add triples with known namespaces
  String rdfPatch = """
      TX .
      A <http://example.org/alice> <http://xmlns.com/foaf/0.1/name> "Alice" .
      A <http://example.org/bob> <http://xmlns.com/foaf/0.1/name> "Bob" .
      TC .
      """;
  createCommitWithPatch(rdfPatch);

  // Act
  ResponseEntity<SuggestedPrefixesResponse> response = restTemplate.exchange(
      "/version/datasets/default/branches/main/prefixes/suggested",
      HttpMethod.GET,
      null,
      SuggestedPrefixesResponse.class
  );

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(response.getBody()).isNotNull();
  assertThat(response.getBody().suggestions())
      .anyMatch(s -> s.prefix().equals("foaf")
          && s.iri().equals("http://xmlns.com/foaf/0.1/")
          && s.status() == Status.SUGGESTED);
}

@Test
void suggestPrefixes_shouldMarkAlreadyDefined() {
  // Arrange: Define foaf prefix
  definePrefixes(Map.of("foaf", "http://xmlns.com/foaf/0.1/"));

  // Add triples using foaf namespace
  createCommitWithTriples(...);

  // Act
  SuggestedPrefixesResponse response = getSuggestions();

  // Assert
  assertThat(response.suggestions())
      .anyMatch(s -> s.prefix().equals("foaf")
          && s.status() == Status.ALREADY_DEFINED);
}

@Test
void suggestPrefixes_shouldSortByFrequency() {
  // Arrange: Add triples with multiple namespaces
  // 10x foaf, 5x schema, 2x dbo
  createCommitWithMultipleNamespaces();

  // Act
  List<PrefixSuggestion> suggestions = getSuggestions().suggestions();

  // Assert
  assertThat(suggestions.get(0).prefix()).isEqualTo("foaf");  // Most frequent
  assertThat(suggestions.get(1).prefix()).isEqualTo("schema");
  assertThat(suggestions.get(2).prefix()).isEqualTo("dbo");
}
```

---

## Success Criteria

### Functional
- ✅ Analyzes dataset for namespace patterns
- ✅ Suggests conventional prefixes (prefix.cc subset)
- ✅ Marks already-defined prefixes
- ✅ Sorts by frequency descending
- ✅ Returns empty list if no suggestions

### Performance
- ✅ Completes in <500ms for typical datasets
- ✅ No memory issues for large datasets

### Quality
- ✅ All tests pass (8+ new tests)
- ✅ Zero quality violations

---

## Files to Create

```
src/main/java/org/chucc/vcserver/
  ├── dto/
  │   ├── PrefixSuggestion.java              # NEW
  │   └── SuggestedPrefixesResponse.java     # NEW
  └── service/
      ├── ConventionalPrefixes.java          # NEW
      └── PrefixSuggestionService.java       # NEW

src/main/java/org/chucc/vcserver/controller/
  └── PrefixManagementController.java        # MODIFY (add endpoint)

src/test/java/org/chucc/vcserver/integration/
  └── PrefixManagementIT.java                # MODIFY (add 8+ tests)
```

---

## Optional Enhancements

1. **Threshold filtering**: Only suggest namespaces with frequency ≥ N
2. **Custom prefixes**: Allow users to define their own conventional prefixes
3. **Prefix.cc API**: Fetch live data from prefix.cc instead of static map
4. **Conflict detection**: Warn if suggested prefix already used for different namespace

---

## Next Steps

After completing:
1. ✅ Commit changes
2. ✅ Proceed to [Session 4: OpenAPI and Tests](./session-4-openapi-and-tests.md)
