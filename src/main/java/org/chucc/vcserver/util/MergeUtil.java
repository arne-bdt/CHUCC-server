package org.chucc.vcserver.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.dto.ConflictItem;

/**
 * Utility for merge conflict detection and resolution.
 *
 * <p>Implements conservative conflict detection: any triple position (graph, subject, predicate)
 * that is modified by both branches is flagged as a conflict, regardless of the object value.
 *
 * <p>Supports conflict resolution strategies:
 * <ul>
 *   <li>"ours" - Keep changes from "into" branch</li>
 *   <li>"theirs" - Keep changes from "from" branch</li>
 *   <li>Configurable conflict scope: "graph" (per-graph) or "dataset" (all-or-nothing)</li>
 * </ul>
 */
public final class MergeUtil {

  private MergeUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Represents a triple position (graph, subject, predicate) for conflict detection.
   * Two modifications to the same position (even with different objects) constitute a conflict.
   */
  private static final class TriplePosition {
    private final Node graph;
    private final Node subject;
    private final Node predicate;

    TriplePosition(Node graph, Node subject, Node predicate) {
      this.graph = graph;
      this.subject = subject;
      this.predicate = predicate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      TriplePosition that = (TriplePosition) o;
      return Objects.equals(graph, that.graph)
          && Objects.equals(subject, that.subject)
          && Objects.equals(predicate, that.predicate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(graph, subject, predicate);
    }
  }

  /**
   * Represents a prefix for conflict detection.
   * Two modifications to the same prefix with different IRIs constitute a conflict.
   */
  private static final class PrefixKey {
    private final String prefix;

    PrefixKey(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PrefixKey that = (PrefixKey) o;
      return Objects.equals(prefix, that.prefix);
    }

    @Override
    public int hashCode() {
      return Objects.hash(prefix);
    }
  }

  /**
   * Detects conflicts between two RDFPatch changesets (quads and prefixes).
   *
   * <p>Quad conflict: Same triple position (graph, subject, predicate) modified by both branches.
   *
   * <p>Prefix conflict: Same prefix mapped to different IRIs by both branches.
   *
   * @param intoChanges changes from base to "into" branch
   * @param fromChanges changes from base to "from" branch
   * @return list of conflicting positions and prefixes
   */
  public static List<ConflictItem> detectConflicts(RDFPatch intoChanges, RDFPatch fromChanges) {
    // Collect quad positions and prefixes touched by each branch
    Map<TriplePosition, Quad> intoQuadsTouched = new HashMap<>();
    Map<PrefixKey, String> intoPrefixes = new HashMap<>();
    collectPositionsAndPrefixes(intoChanges, intoQuadsTouched, intoPrefixes);

    Map<TriplePosition, Quad> fromQuadsTouched = new HashMap<>();
    Map<PrefixKey, String> fromPrefixes = new HashMap<>();
    collectPositionsAndPrefixes(fromChanges, fromQuadsTouched, fromPrefixes);

    List<ConflictItem> conflicts = new ArrayList<>();

    // Detect quad-level conflicts
    for (Map.Entry<TriplePosition, Quad> entry : intoQuadsTouched.entrySet()) {
      TriplePosition position = entry.getKey();
      if (fromQuadsTouched.containsKey(position)) {
        // Both branches modified this position - conflict!
        Quad intoQuad = entry.getValue();
        conflicts.add(new ConflictItem(
            intoQuad.getGraph().toString(),
            intoQuad.getSubject().toString(),
            intoQuad.getPredicate().toString(),
            intoQuad.getObject().toString(),
            "Modified by both branches"
        ));
      }
    }

    // Detect prefix conflicts
    for (Map.Entry<PrefixKey, String> entry : intoPrefixes.entrySet()) {
      PrefixKey key = entry.getKey();
      if (fromPrefixes.containsKey(key)) {
        String intoIri = entry.getValue();
        String fromIri = fromPrefixes.get(key);

        // Conflict if both branches changed prefix to different IRIs
        if (intoIri != null && fromIri != null && !intoIri.equals(fromIri)) {
          conflicts.add(new ConflictItem(
              "urn:x-arq:DefaultGraph",
              "PREFIX:" + key.prefix,
              "maps-to",
              intoIri + " (ours) vs " + fromIri + " (theirs)",
              "Prefix modified by both branches with different values"
          ));
        }
      }
    }

    return conflicts;
  }

  /**
   * Collects triple positions and prefixes from an RDFPatch.
   *
   * @param patch the RDF patch
   * @param positions map of quad positions to representative quads
   * @param prefixes map of prefix keys to their IRI values
   */
  private static void collectPositionsAndPrefixes(
      RDFPatch patch,
      Map<TriplePosition, Quad> positions,
      Map<PrefixKey, String> prefixes) {
    // Use RDFPatch visitor to collect all positions and prefixes
    patch.apply(new RdfChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        TriplePosition position = new TriplePosition(g, s, p);
        positions.put(position, new Quad(g, s, p, o));
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        TriplePosition position = new TriplePosition(g, s, p);
        positions.put(position, new Quad(g, s, p, o));
      }

      @Override
      public void addPrefix(Node gn, String prefix, String uriStr) {
        prefixes.put(new PrefixKey(prefix), uriStr);
      }

      @Override
      public void deletePrefix(Node gn, String prefix) {
        prefixes.put(new PrefixKey(prefix), null);  // Deletion marker
      }
    });
  }

  /**
   * Extracts conflicting graph names from quad-level conflicts.
   *
   * @param conflicts quad-level conflicts from detectConflicts()
   * @return set of conflicting graph nodes
   */
  public static Set<Node> extractConflictingGraphs(List<ConflictItem> conflicts) {
    Set<Node> graphs = new HashSet<>();

    for (ConflictItem conflict : conflicts) {
      Node graphNode = parseGraphNode(conflict.getGraph());
      graphs.add(graphNode);
    }

    return graphs;
  }

  /**
   * Parses a graph node string to a Jena Node.
   *
   * @param graphStr string representation of graph URI
   * @return Jena Node representing the graph
   */
  private static Node parseGraphNode(String graphStr) {
    if ("urn:x-arq:DefaultGraph".equals(graphStr)) {
      return Quad.defaultGraphIRI;
    }
    return NodeFactory.createURI(graphStr);
  }

  /**
   * Resolves conflicts using "ours" strategy (keep "into" branch changes).
   *
   * @param baseToInto changes from base to "into" branch
   * @param baseToFrom changes from base to "from" branch
   * @param conflicts detected conflicts
   * @param conflictScope "graph" (per-graph resolution) or "dataset" (all-or-nothing)
   * @return merged patch with "into" winning conflicts
   */
  public static RDFPatch resolveWithOurs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                         List<ConflictItem> conflicts, String conflictScope) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();

    if ("dataset".equals(conflictScope)) {
      // Dataset-level: keep ALL "into", discard ALL "from"
      applyAllOperations(collector, baseToInto);
    } else {
      // Graph-level: keep ALL "into", keep non-conflicting "from" graphs
      Set<Node> conflictingGraphs = extractConflictingGraphs(conflicts);
      applyAllOperations(collector, baseToInto);
      applyGraphFilteredOperations(collector, baseToFrom, conflictingGraphs, false);
    }

    collector.finish();
    return collector.getRDFPatch();
  }

  /**
   * Resolves conflicts using "theirs" strategy (keep "from" branch changes).
   *
   * @param baseToInto changes from base to "into" branch
   * @param baseToFrom changes from base to "from" branch
   * @param conflicts detected conflicts
   * @param conflictScope "graph" (per-graph resolution) or "dataset" (all-or-nothing)
   * @return merged patch with "from" winning conflicts
   */
  public static RDFPatch resolveWithTheirs(RDFPatch baseToInto, RDFPatch baseToFrom,
                                           List<ConflictItem> conflicts, String conflictScope) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();

    if ("dataset".equals(conflictScope)) {
      // Dataset-level: keep ALL "from", discard ALL "into"
      applyAllOperations(collector, baseToFrom);
    } else {
      // Graph-level: keep non-conflicting "into" graphs, keep ALL "from"
      Set<Node> conflictingGraphs = extractConflictingGraphs(conflicts);
      applyGraphFilteredOperations(collector, baseToInto, conflictingGraphs, false);
      applyAllOperations(collector, baseToFrom);
    }

    collector.finish();
    return collector.getRDFPatch();
  }

  /**
   * Applies all operations from a patch to the collector (quads and prefixes).
   *
   * @param collector collector to append operations to
   * @param patch patch to copy operations from
   */
  private static void applyAllOperations(RDFChangesCollector collector, RDFPatch patch) {
    patch.apply(new RdfChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        collector.add(g, s, p, o);
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        collector.delete(g, s, p, o);
      }

      @Override
      public void addPrefix(Node gn, String prefix, String uriStr) {
        collector.addPrefix(gn, prefix, uriStr);
      }

      @Override
      public void deletePrefix(Node gn, String prefix) {
        collector.deletePrefix(gn, prefix);
      }
    });
  }

  /**
   * Applies operations from a patch, filtering by graph (prefixes always included).
   *
   * @param collector collector to append operations to
   * @param patch patch to copy operations from
   * @param graphFilter set of graph nodes to filter
   * @param include if true, include only operations in graphFilter; if false, exclude them
   */
  private static void applyGraphFilteredOperations(
      RDFChangesCollector collector,
      RDFPatch patch,
      Set<Node> graphFilter,
      boolean include) {
    patch.apply(new RdfChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        boolean inFilter = graphFilter.contains(g);
        if (include ? inFilter : !inFilter) {
          collector.add(g, s, p, o);
        }
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        boolean inFilter = graphFilter.contains(g);
        if (include ? inFilter : !inFilter) {
          collector.delete(g, s, p, o);
        }
      }

      @Override
      public void addPrefix(Node gn, String prefix, String uriStr) {
        // Prefixes are dataset-level metadata, always include them
        collector.addPrefix(gn, prefix, uriStr);
      }

      @Override
      public void deletePrefix(Node gn, String prefix) {
        // Prefixes are dataset-level metadata, always include them
        collector.deletePrefix(gn, prefix);
      }
    });
  }
}
