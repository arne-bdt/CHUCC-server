package org.chucc.vcserver.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.dto.ConflictItem;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MergeUtil conflict detection and resolution.
 */
class MergeUtilTest {

  @Test
  void detectConflicts_withPrefixConflict_shouldDetectConflict() {
    // Arrange: Create two patches with conflicting prefixes
    RDFPatch intoPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    RDFPatch fromPatch = createPrefixPatch("foaf", "http://example.org/my-foaf#");

    // Act
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(intoPatch, fromPatch);

    // Assert
    assertThat(conflicts).hasSize(1);
    ConflictItem conflict = conflicts.get(0);
    assertThat(conflict.getGraph()).isEqualTo("urn:x-arq:DefaultGraph");
    assertThat(conflict.getSubject()).isEqualTo("PREFIX:foaf");
    assertThat(conflict.getPredicate()).isEqualTo("maps-to");
    assertThat(conflict.getObject())
        .contains("http://xmlns.com/foaf/0.1/")
        .contains("http://example.org/my-foaf#");
    assertThat(conflict.getDetails())
        .isEqualTo("Prefix modified by both branches with different values");
  }

  @Test
  void detectConflicts_withIdenticalPrefixChanges_shouldNotConflict() {
    // Arrange: Create two patches with identical prefix changes
    RDFPatch intoPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    RDFPatch fromPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");

    // Act
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(intoPatch, fromPatch);

    // Assert: No conflict when both branches make identical changes
    assertThat(conflicts).isEmpty();
  }

  @Test
  void detectConflicts_withDifferentPrefixes_shouldNotConflict() {
    // Arrange: Create two patches with different prefixes
    RDFPatch intoPatch = createPrefixPatch("foaf", "http://xmlns.com/foaf/0.1/");
    RDFPatch fromPatch = createPrefixPatch("dc", "http://purl.org/dc/elements/1.1/");

    // Act
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(intoPatch, fromPatch);

    // Assert: No conflict when branches modify different prefixes
    assertThat(conflicts).isEmpty();
  }

  @Test
  void detectConflicts_withQuadConflict_shouldDetectConflict() {
    // Arrange: Create two patches modifying same triple position
    Node g = NodeFactory.createURI("http://example.org/g1");
    Node s = NodeFactory.createURI("http://example.org/s1");
    Node p = NodeFactory.createURI("http://example.org/p1");
    Node o1 = NodeFactory.createLiteralString("value1");
    Node o2 = NodeFactory.createLiteralString("value2");

    RDFPatch intoPatch = createQuadPatch(g, s, p, o1);
    RDFPatch fromPatch = createQuadPatch(g, s, p, o2);

    // Act
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(intoPatch, fromPatch);

    // Assert: Quad conflict detected
    assertThat(conflicts).hasSize(1);
    ConflictItem conflict = conflicts.get(0);
    assertThat(conflict.getGraph()).isEqualTo("http://example.org/g1");
    assertThat(conflict.getSubject()).isEqualTo("http://example.org/s1");
    assertThat(conflict.getPredicate()).isEqualTo("http://example.org/p1");
  }

  @Test
  void detectConflicts_withBothPrefixAndQuadConflicts_shouldDetectBoth() {
    // Arrange: Create patches with both prefix and quad conflicts
    RDFPatch intoPatch = createPrefixAndQuadPatch(
        "foaf", "http://xmlns.com/foaf/0.1/",
        NodeFactory.createURI("http://example.org/g1"),
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value1")
    );

    RDFPatch fromPatch = createPrefixAndQuadPatch(
        "foaf", "http://example.org/my-foaf#",
        NodeFactory.createURI("http://example.org/g1"),
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value2")
    );

    // Act
    List<ConflictItem> conflicts = MergeUtil.detectConflicts(intoPatch, fromPatch);

    // Assert: Both conflicts detected
    assertThat(conflicts).hasSize(2);

    // Verify prefix conflict
    ConflictItem prefixConflict = conflicts.stream()
        .filter(c -> c.getSubject().startsWith("PREFIX:"))
        .findFirst()
        .orElseThrow();
    assertThat(prefixConflict.getSubject()).isEqualTo("PREFIX:foaf");

    // Verify quad conflict
    ConflictItem quadConflict = conflicts.stream()
        .filter(c -> !c.getSubject().startsWith("PREFIX:"))
        .findFirst()
        .orElseThrow();
    assertThat(quadConflict.getGraph()).isEqualTo("http://example.org/g1");
  }

  // Helper methods

  private RDFPatch createPrefixPatch(String prefix, String iri) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.addPrefix(null, prefix, iri);
    collector.finish();
    return collector.getRDFPatch();
  }

  private RDFPatch createQuadPatch(Node g, Node s, Node p, Node o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }

  private RDFPatch createPrefixAndQuadPatch(
      String prefix, String iri,
      Node g, Node s, Node p, Node o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.addPrefix(null, prefix, iri);
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }
}
