package org.chucc.vcserver.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.dto.ConflictItem;

/**
 * Utility for merge conflict detection.
 *
 * <p>Implements conservative conflict detection: any triple position (graph, subject, predicate)
 * that is modified by both branches is flagged as a conflict, regardless of the object value.
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
   * Detects conflicts between two RDFPatch changesets.
   *
   * <p>A conflict occurs when the same triple position (graph, subject, predicate)
   * is modified by both branches, regardless of the object value. This correctly
   * identifies conflicts when branches assign different values to the same position.
   *
   * @param intoChanges changes from base to "into" branch
   * @param fromChanges changes from base to "from" branch
   * @return list of conflicting positions with their modifications
   */
  public static List<ConflictItem> detectConflicts(RDFPatch intoChanges, RDFPatch fromChanges) {
    // Collect positions touched by each branch with their modifications
    Map<TriplePosition, Quad> intoTouched = new HashMap<>();
    collectPositions(intoChanges, intoTouched);

    Map<TriplePosition, Quad> fromTouched = new HashMap<>();
    collectPositions(fromChanges, fromTouched);

    // Find overlapping positions (conflicts)
    List<ConflictItem> conflicts = new ArrayList<>();
    for (Map.Entry<TriplePosition, Quad> entry : intoTouched.entrySet()) {
      TriplePosition position = entry.getKey();
      if (fromTouched.containsKey(position)) {
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

    return conflicts;
  }

  /**
   * Collects triple positions from an RDFPatch (both additions and deletions).
   *
   * @param patch the RDF patch
   * @param positions map of positions to representative quads
   */
  private static void collectPositions(RDFPatch patch, Map<TriplePosition, Quad> positions) {
    // Use RDFPatch visitor to collect all positions
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
    });
  }
}
