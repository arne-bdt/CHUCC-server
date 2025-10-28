package org.chucc.vcserver.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.Quad;
import org.chucc.vcserver.dto.ConflictItem;

/**
 * Utility for merge conflict detection.
 *
 * <p>Implements conservative conflict detection: any quad that appears in both branches'
 * changesets (either as addition or deletion) is flagged as a conflict.
 */
public final class MergeUtil {

  private MergeUtil() {
    // Utility class - prevent instantiation
  }

  /**
   * Detects conflicts between two RDFPatch changesets.
   *
   * <p>A conflict occurs when the same quad appears in both changesets
   * (either as addition or deletion). This is a conservative approach
   * that flags false positives (e.g., both branches making identical changes),
   * which can be resolved in Phase 2 with conflict resolution strategies.
   *
   * @param intoChanges changes from base to "into" branch
   * @param fromChanges changes from base to "from" branch
   * @return list of conflicting quads
   */
  public static List<ConflictItem> detectConflicts(RDFPatch intoChanges, RDFPatch fromChanges) {
    // Collect all quads touched by each branch
    Set<Quad> intoTouched = new HashSet<>();
    collectQuads(intoChanges, intoTouched);

    Set<Quad> fromTouched = new HashSet<>();
    collectQuads(fromChanges, fromTouched);

    // Find overlapping quads
    Set<Quad> overlapping = new HashSet<>(intoTouched);
    overlapping.retainAll(fromTouched);

    // Convert to conflict DTOs
    List<ConflictItem> conflicts = new ArrayList<>();
    for (Quad quad : overlapping) {
      conflicts.add(new ConflictItem(
          quad.getGraph().toString(),
          quad.getSubject().toString(),
          quad.getPredicate().toString(),
          quad.getObject().toString(),
          "Modified by both branches"
      ));
    }

    return conflicts;
  }

  /**
   * Collects all quads from an RDFPatch (both additions and deletions).
   *
   * @param patch the RDF patch
   * @param quads the set to collect quads into
   */
  private static void collectQuads(RDFPatch patch, Set<Quad> quads) {
    // Use RDFPatch visitor to collect all quads
    patch.apply(new RdfChangesAdapter() {
      @Override
      public void add(Node g, Node s, Node p, Node o) {
        quads.add(new Quad(g, s, p, o));
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        quads.add(new Quad(g, s, p, o));
      }
    });
  }
}
