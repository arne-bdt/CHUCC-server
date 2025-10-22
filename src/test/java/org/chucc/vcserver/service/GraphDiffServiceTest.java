package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.graph.GraphFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GraphDiffService.
 */
class GraphDiffServiceTest {

  private GraphDiffService service;

  @BeforeEach
  void setUp() {
    service = new GraphDiffService();
  }

  @Test
  void computePutDiff_shouldGenerateDeletesAndAdds_whenBothGraphsNonEmpty() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("oldValue")
    ));

    Graph newGraph = GraphFactory.createDefaultGraph();
    newGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("newValue")
    ));

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("D <http://example.org/s1>");
    assertThat(patchString).contains("A <http://example.org/s2>");
  }

  @Test
  void computePutDiff_shouldGenerateOnlyDeletes_whenNewGraphEmpty() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    Graph newGraph = GraphFactory.createDefaultGraph();

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("D <http://example.org/s1>");
    assertThat(patchString).doesNotContain("A <");
  }

  @Test
  void computePutDiff_shouldGenerateOnlyAdds_whenOldGraphEmpty() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();

    Graph newGraph = GraphFactory.createDefaultGraph();
    newGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("A <http://example.org/s1>");
    assertThat(patchString).doesNotContain("D <");
  }

  @Test
  void computePutDiff_shouldGenerateEmptyPatch_whenGraphsIdentical() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    Graph newGraph = GraphFactory.createDefaultGraph();
    newGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computePutDiff_shouldGenerateEmptyPatch_whenBothGraphsEmpty() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    Graph newGraph = GraphFactory.createDefaultGraph();

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computePutDiff_shouldHandleDefaultGraph_whenGraphIriNull() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    Graph newGraph = GraphFactory.createDefaultGraph();

    // When
    RDFPatch patch = service.computePutDiff(oldGraph, newGraph, null);

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    // For default graph, quads should not have graph component
    assertThat(patchString).contains("D <http://example.org/s1>");
  }

  @Test
  void computePutDiff_shouldHandleMultipleTriples() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value1")
    ));
    oldGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("value2")
    ));

    Graph newGraph = GraphFactory.createDefaultGraph();
    newGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s3"),
        NodeFactory.createURI("http://example.org/p3"),
        NodeFactory.createLiteralString("value3")
    ));

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("D <http://example.org/s1>");
    assertThat(patchString).contains("D <http://example.org/s2>");
    assertThat(patchString).contains("A <http://example.org/s3>");
  }

  @Test
  void isPatchEmpty_shouldReturnTrue_whenPatchHasNoOperations() {
    // Given
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    // When
    boolean isEmpty = service.isPatchEmpty(emptyPatch);

    // Then
    assertThat(isEmpty).isTrue();
  }

  @Test
  void isPatchEmpty_shouldReturnFalse_whenPatchHasOperations() {
    // Given
    Graph oldGraph = GraphFactory.createDefaultGraph();
    Graph newGraph = GraphFactory.createDefaultGraph();
    newGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    RDFPatch patch = service.computePutDiff(oldGraph, newGraph, null);

    // When
    boolean isEmpty = service.isPatchEmpty(patch);

    // Then
    assertThat(isEmpty).isFalse();
  }

  @Test
  void computePostDiff_shouldGenerateOnlyAdds_whenNewTriplesPresent() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("existingValue")
    ));

    Graph newContent = GraphFactory.createDefaultGraph();
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("newValue")
    ));

    // When
    RDFPatch patch = service.computePostDiff(
        currentGraph, newContent, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("A <http://example.org/s2>");
    assertThat(patchString).doesNotContain("D <"); // No deletes
  }

  @Test
  void computePostDiff_shouldGenerateEmptyPatch_whenAllTriplesAlreadyPresent() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    Graph newContent = GraphFactory.createDefaultGraph();
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computePostDiff(
        currentGraph, newContent, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computePostDiff_shouldGenerateAdds_whenCurrentGraphEmpty() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();

    Graph newContent = GraphFactory.createDefaultGraph();
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computePostDiff(
        currentGraph, newContent, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("A <http://example.org/s1>");
    assertThat(patchString).doesNotContain("D <");
  }

  @Test
  void computePostDiff_shouldHandleDefaultGraph_whenGraphIriNull() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();

    Graph newContent = GraphFactory.createDefaultGraph();
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computePostDiff(currentGraph, newContent, null);

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    // For default graph, quads should not have graph component
    assertThat(patchString).contains("A <http://example.org/s1>");
  }

  @Test
  void computePostDiff_shouldHandlePartialOverlap() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("existingValue")
    ));

    Graph newContent = GraphFactory.createDefaultGraph();
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("existingValue")
    ));
    newContent.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("newValue")
    ));

    // When
    RDFPatch patch = service.computePostDiff(
        currentGraph, newContent, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    // Should only add the new triple, not the existing one
    assertThat(patchString).contains("A <http://example.org/s2>");
    assertThat(patchString).doesNotContain("D <"); // No deletes
    // Should not re-add existing triple
    long addCount = patchString.lines().filter(line -> line.startsWith("A ")).count();
    assertThat(addCount).isEqualTo(1);
  }

  @Test
  void computePostDiff_shouldGenerateEmptyPatch_whenNewContentEmpty() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    Graph newContent = GraphFactory.createDefaultGraph();

    // When
    RDFPatch patch = service.computePostDiff(
        currentGraph, newContent, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computeDeleteDiff_shouldGenerateOnlyDeletes_whenGraphHasTriples() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value1")
    ));
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("value2")
    ));

    // When
    RDFPatch patch = service.computeDeleteDiff(
        currentGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("D <http://example.org/s1>");
    assertThat(patchString).contains("D <http://example.org/s2>");
    assertThat(patchString).doesNotContain("A <"); // No adds
  }

  @Test
  void computeDeleteDiff_shouldGenerateEmptyPatch_whenGraphIsEmpty() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();

    // When
    RDFPatch patch = service.computeDeleteDiff(
        currentGraph, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computeDeleteDiff_shouldHandleDefaultGraph_whenGraphIriNull() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value")
    ));

    // When
    RDFPatch patch = service.computeDeleteDiff(currentGraph, null);

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    // For default graph, quads should not have graph component
    assertThat(patchString).contains("D <http://example.org/s1>");
  }

  @Test
  void computeDeleteDiff_shouldDeleteAllTriples_whenMultipleTriples() {
    // Given
    Graph currentGraph = GraphFactory.createDefaultGraph();
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s1"),
        NodeFactory.createURI("http://example.org/p1"),
        NodeFactory.createLiteralString("value1")
    ));
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s2"),
        NodeFactory.createURI("http://example.org/p2"),
        NodeFactory.createLiteralString("value2")
    ));
    currentGraph.add(Triple.create(
        NodeFactory.createURI("http://example.org/s3"),
        NodeFactory.createURI("http://example.org/p3"),
        NodeFactory.createLiteralString("value3")
    ));

    // When
    RDFPatch patch = service.computeDeleteDiff(
        currentGraph, "http://example.org/graph1");

    // Then
    assertThat(patch).isNotNull();
    String patchString = serializePatch(patch);
    assertThat(patchString).contains("D <http://example.org/s1>");
    assertThat(patchString).contains("D <http://example.org/s2>");
    assertThat(patchString).contains("D <http://example.org/s3>");
    // Count delete operations
    long deleteCount = patchString.lines().filter(line -> line.startsWith("D ")).count();
    assertThat(deleteCount).isEqualTo(3);
  }

  /**
   * Helper to serialize patch for assertion inspection.
   *
   * @param patch the patch to serialize
   * @return the serialized patch string
   */
  private String serializePatch(RDFPatch patch) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    RDFPatchOps.write(out, patch);
    return out.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
