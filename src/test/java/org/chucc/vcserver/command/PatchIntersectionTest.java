package org.chucc.vcserver.command;

import java.util.List;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.dto.ConflictItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for PatchIntersection utility.
 */
class PatchIntersectionTest {

  @Test
  void shouldDetectIntersectionWhenBothAddSameTriple() {
    // Given
    RDFPatch patch1 = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertTrue(PatchIntersection.intersects(patch1, patch2));
  }

  @Test
  void shouldDetectIntersectionWhenBothDeleteSameTriple() {
    // Given
    RDFPatch patch1 = createPatchWithDelete("http://example.org/s", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithDelete("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertTrue(PatchIntersection.intersects(patch1, patch2));
  }

  @Test
  void shouldDetectIntersectionWhenOneAddsAndOneDeletes() {
    // Given
    RDFPatch patch1 = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithDelete("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertTrue(PatchIntersection.intersects(patch1, patch2));
  }

  @Test
  void shouldNotDetectIntersectionWhenDifferentTriples() {
    // Given
    RDFPatch patch1 = createPatchWithAdd("http://example.org/s1", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithAdd("http://example.org/s2", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertFalse(PatchIntersection.intersects(patch1, patch2));
  }

  @Test
  void shouldNotDetectIntersectionWithEmptyPatches() {
    // Given
    RDFPatch emptyPatch1 = RDFPatchOps.emptyPatch();
    RDFPatch emptyPatch2 = RDFPatchOps.emptyPatch();

    // When/Then
    assertFalse(PatchIntersection.intersects(emptyPatch1, emptyPatch2));
  }

  @Test
  void shouldNotDetectIntersectionWhenOneIsEmpty() {
    // Given
    RDFPatch patch = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    // When/Then
    assertFalse(PatchIntersection.intersects(patch, emptyPatch));
    assertFalse(PatchIntersection.intersects(emptyPatch, patch));
  }

  @Test
  void shouldHandleNullPatches() {
    // Given
    RDFPatch patch = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertFalse(PatchIntersection.intersects(null, patch));
    assertFalse(PatchIntersection.intersects(patch, null));
    assertFalse(PatchIntersection.intersects(null, null));
  }

  // Helper methods

  private RDFPatch createPatchWithAdd(String s, String p, String o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    collector.add(null,
        NodeFactory.createURI(s),
        NodeFactory.createURI(p),
        NodeFactory.createURI(o));
    collector.txnCommit();
    return collector.getRDFPatch();
  }

  private RDFPatch createPatchWithDelete(String s, String p, String o) {
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.txnBegin();
    collector.delete(null,
        NodeFactory.createURI(s),
        NodeFactory.createURI(p),
        NodeFactory.createURI(o));
    collector.txnCommit();
    return collector.getRDFPatch();
  }

  // Tests for detectConflicts method

  @Test
  void shouldReturnConflictDetailsWhenPatchesIntersect() {
    // Given
    RDFPatch patch1 = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When
    List<ConflictItem> conflicts = PatchIntersection.detectConflicts(patch1, patch2);

    // Then
    assertEquals(1, conflicts.size());
    ConflictItem conflict = conflicts.get(0);
    assertEquals("urn:x-arq:DefaultGraph", conflict.getGraph());
    assertEquals("http://example.org/s", conflict.getSubject());
    assertEquals("http://example.org/p", conflict.getPredicate());
    assertEquals("http://example.org/o", conflict.getObject());
    assertEquals("Overlapping modification", conflict.getDetails());
  }

  @Test
  void shouldReturnEmptyListWhenPatchesDoNotIntersect() {
    // Given
    RDFPatch patch1 = createPatchWithAdd("http://example.org/s1", "http://example.org/p",
        "http://example.org/o");
    RDFPatch patch2 = createPatchWithAdd("http://example.org/s2", "http://example.org/p",
        "http://example.org/o");

    // When
    List<ConflictItem> conflicts = PatchIntersection.detectConflicts(patch1, patch2);

    // Then
    assertTrue(conflicts.isEmpty());
  }

  @Test
  void shouldReturnEmptyListForNullPatches() {
    // Given
    RDFPatch patch = createPatchWithAdd("http://example.org/s", "http://example.org/p",
        "http://example.org/o");

    // When/Then
    assertTrue(PatchIntersection.detectConflicts(null, patch).isEmpty());
    assertTrue(PatchIntersection.detectConflicts(patch, null).isEmpty());
    assertTrue(PatchIntersection.detectConflicts(null, null).isEmpty());
  }

  @Test
  void shouldDetectMultipleConflicts() {
    // Given - Create patches with multiple overlapping changes
    RDFChangesCollector collector1 = new RDFChangesCollector();
    collector1.txnBegin();
    collector1.add(null,
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createURI("http://example.org/o1"));
    collector1.add(null,
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createURI("http://example.org/o2"));
    collector1.txnCommit();
    RDFPatch patch1 = collector1.getRDFPatch();

    RDFChangesCollector collector2 = new RDFChangesCollector();
    collector2.txnBegin();
    collector2.delete(null,
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createURI("http://example.org/o1"));
    collector2.add(null,
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createURI("http://example.org/o2"));
    collector2.txnCommit();
    RDFPatch patch2 = collector2.getRDFPatch();

    // When
    List<ConflictItem> conflicts = PatchIntersection.detectConflicts(patch1, patch2);

    // Then
    assertEquals(2, conflicts.size());
  }

  @Test
  void shouldIncludeGraphFieldInConflicts() {
    // Given - Create patch with named graph
    RDFChangesCollector collector1 = new RDFChangesCollector();
    collector1.txnBegin();
    collector1.add(NodeFactory.createURI("http://example.org/graph"),
        NodeFactory.createURI("http://example.org/s"),
        NodeFactory.createURI("http://example.org/p"),
        NodeFactory.createURI("http://example.org/o"));
    collector1.txnCommit();
    RDFPatch patch1 = collector1.getRDFPatch();

    RDFChangesCollector collector2 = new RDFChangesCollector();
    collector2.txnBegin();
    collector2.add(NodeFactory.createURI("http://example.org/graph"),
        NodeFactory.createURI("http://example.org/s"),
        NodeFactory.createURI("http://example.org/p"),
        NodeFactory.createURI("http://example.org/o"));
    collector2.txnCommit();
    RDFPatch patch2 = collector2.getRDFPatch();

    // When
    List<ConflictItem> conflicts = PatchIntersection.detectConflicts(patch1, patch2);

    // Then
    assertEquals(1, conflicts.size());
    ConflictItem conflict = conflicts.get(0);
    assertEquals("http://example.org/graph", conflict.getGraph());
  }
}
