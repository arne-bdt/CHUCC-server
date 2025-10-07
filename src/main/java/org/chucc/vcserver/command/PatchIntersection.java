package org.chucc.vcserver.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.dto.ConflictItem;

/**
 * Utility for detecting conflicts via patch intersection.
 * Two patches intersect (have a conflict) if they modify the same triples.
 */
public final class PatchIntersection {

  private PatchIntersection() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks if two RDF patches intersect (have overlapping changes).
   * Patches intersect if:
   * - Both add the same triple
   * - Both delete the same triple
   * - One adds and one deletes the same triple
   *
   * @param patch1 the first patch
   * @param patch2 the second patch
   * @return true if the patches have any overlapping changes
   */
  public static boolean intersects(RDFPatch patch1, RDFPatch patch2) {
    if (patch1 == null || patch2 == null) {
      return false;
    }

    Set<QuadKey> patch1Affected = extractAffectedQuads(patch1);
    Set<QuadKey> patch2Affected = extractAffectedQuads(patch2);

    // Check for intersection
    for (QuadKey key : patch1Affected) {
      if (patch2Affected.contains(key)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Detects conflicts between two RDF patches and returns detailed conflict information.
   * Conflicts occur when both patches modify the same quads (graph, subject, predicate, object).
   *
   * @param patch1 the first patch
   * @param patch2 the second patch
   * @return list of conflict items describing the overlapping changes
   */
  public static List<ConflictItem> detectConflicts(RDFPatch patch1, RDFPatch patch2) {
    if (patch1 == null || patch2 == null) {
      return new ArrayList<>();
    }

    Set<QuadKey> patch1Affected = extractAffectedQuads(patch1);
    Set<QuadKey> patch2Affected = extractAffectedQuads(patch2);

    List<ConflictItem> conflicts = new ArrayList<>();

    // Find intersection
    for (QuadKey key : patch1Affected) {
      if (patch2Affected.contains(key)) {
        conflicts.add(quadKeyToConflictItem(key));
      }
    }

    return conflicts;
  }

  /**
   * Extracts all quads (graph name + triple) affected by a patch.
   *
   * @param patch the RDF patch
   * @return set of affected quad keys
   */
  private static Set<QuadKey> extractAffectedQuads(RDFPatch patch) {
    Set<QuadKey> affected = new HashSet<>();

    patch.apply(new org.apache.jena.rdfpatch.RDFChanges() {
      @Override
      public void start() {
        // No-op
      }

      @Override
      public void finish() {
        // No-op
      }

      @Override
      public void header(String field, Node value) {
        // No-op
      }

      @Override
      public void add(Node g, Node s, Node p, Node o) {
        affected.add(new QuadKey(g, s, p, o));
      }

      @Override
      public void delete(Node g, Node s, Node p, Node o) {
        affected.add(new QuadKey(g, s, p, o));
      }

      @Override
      public void addPrefix(Node gn, String prefix, String uriStr) {
        // Prefix changes don't cause conflicts
      }

      @Override
      public void deletePrefix(Node gn, String prefix) {
        // Prefix changes don't cause conflicts
      }

      @Override
      public void txnBegin() {
        // No-op
      }

      @Override
      public void txnCommit() {
        // No-op
      }

      @Override
      public void txnAbort() {
        // No-op
      }

      @Override
      public void segment() {
        // No-op
      }
    });

    return affected;
  }

  /**
   * Converts a QuadKey to a ConflictItem.
   *
   * @param key the quad key
   * @return the conflict item
   */
  private static ConflictItem quadKeyToConflictItem(QuadKey key) {
    String graph = nodeToString(key.g());
    String subject = nodeToString(key.s());
    String predicate = nodeToString(key.p());
    String object = nodeToString(key.o());

    return new ConflictItem(
        graph,
        subject,
        predicate,
        object,
        "Overlapping modification"
    );
  }

  /**
   * Converts a Jena Node to a string representation.
   *
   * @param node the node
   * @return the string representation
   */
  private static String nodeToString(Node node) {
    if (node == null) {
      return "urn:x-arq:DefaultGraph"; // Default graph representation
    }
    if (node.isURI()) {
      return node.getURI();
    }
    if (node.isLiteral()) {
      // Format literal with quotes and optional language/datatype
      String lexical = node.getLiteralLexicalForm();
      String lang = node.getLiteralLanguage();
      String datatype = node.getLiteralDatatypeURI();

      if (lang != null && !lang.isEmpty()) {
        return "\"" + lexical + "\"@" + lang;
      } else if (datatype != null && !datatype.isEmpty()) {
        return "\"" + lexical + "\"^^<" + datatype + ">";
      } else {
        return "\"" + lexical + "\"";
      }
    }
    if (node.isBlank()) {
      return "_:" + node.getBlankNodeLabel();
    }
    return node.toString();
  }

  /**
   * Key representing a quad (graph name + subject + predicate + object).
   * Used for detecting overlapping changes.
   */
  private record QuadKey(Node g, Node s, Node p, Node o) {
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof QuadKey other)) {
        return false;
      }
      return nodesEqual(g, other.g)
          && nodesEqual(s, other.s)
          && nodesEqual(p, other.p)
          && nodesEqual(o, other.o);
    }

    @Override
    public int hashCode() {
      int result = nodeHashCode(g);
      result = 31 * result + nodeHashCode(s);
      result = 31 * result + nodeHashCode(p);
      result = 31 * result + nodeHashCode(o);
      return result;
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals") // Identity check for optimization
    private static boolean nodesEqual(Node n1, Node n2) {
      if (n1 == n2) {
        return true;
      }
      if (n1 == null || n2 == null) {
        return false;
      }
      return n1.equals(n2);
    }

    private static int nodeHashCode(Node n) {
      return n == null ? 0 : n.hashCode();
    }
  }
}
