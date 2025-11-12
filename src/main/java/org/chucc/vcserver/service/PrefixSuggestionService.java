package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.dto.PrefixSuggestion;
import org.chucc.vcserver.dto.PrefixSuggestion.Status;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
import org.springframework.stereotype.Service;

/**
 * Service for analyzing datasets and suggesting conventional prefix mappings.
 *
 * <p>Scans the dataset to discover namespace patterns and suggests conventional
 * prefixes based on the prefix.cc database. Suggestions are sorted by usage
 * frequency (descending).
 */
@Service
public class PrefixSuggestionService {

  private final MaterializedBranchRepository materializedBranchRepository;

  /**
   * Creates a prefix suggestion service.
   *
   * @param materializedBranchRepository the materialized branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "MaterializedBranchRepository is a Spring-managed singleton bean")
  public PrefixSuggestionService(MaterializedBranchRepository materializedBranchRepository) {
    this.materializedBranchRepository = materializedBranchRepository;
  }

  /**
   * Analyzes a branch and suggests conventional prefix mappings.
   *
   * <p>Scans all triples in the dataset to discover namespace patterns,
   * matches them against conventional prefixes, and returns suggestions
   * sorted by frequency (descending).
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return list of prefix suggestions, sorted by frequency descending
   */
  public List<PrefixSuggestion> analyzeBranch(String dataset, String branch) {
    DatasetGraph dsg = materializedBranchRepository.getBranchGraph(dataset, branch);

    // 1. Get current prefixes from the default graph
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
   * Scans the dataset to discover namespace patterns.
   *
   * <p>Examines all URIs in the dataset (subjects, predicates, objects)
   * and extracts their namespace prefixes.
   *
   * @param dsg the dataset graph
   * @return map of namespace → frequency
   */
  private Map<String, Integer> scanForNamespaces(DatasetGraph dsg) {
    Map<String, Integer> namespaceFrequency = new HashMap<>();

    // Scan all named graphs
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
   * Extracts the namespace from an RDF node (if it's a URI).
   *
   * <p>For URIs like {@code http://xmlns.com/foaf/0.1/name}, extracts
   * the namespace {@code http://xmlns.com/foaf/0.1/}.
   *
   * <p>Namespace is everything up to and including the last {@code #} or {@code /}.
   *
   * @param node the RDF node
   * @return optional namespace, or empty if node is not a URI
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
