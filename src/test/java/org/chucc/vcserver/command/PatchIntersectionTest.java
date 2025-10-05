package org.chucc.vcserver.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.junit.jupiter.api.Test;

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
}
