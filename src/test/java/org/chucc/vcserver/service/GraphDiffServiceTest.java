package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
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
    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "oldValue"
    );

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "newValue"
    );

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
    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    Model newGraph = ModelFactory.createDefaultModel();

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
    Model oldGraph = ModelFactory.createDefaultModel();

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

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
    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computePutDiff_shouldGenerateEmptyPatch_whenBothGraphsEmpty() {
    // Given
    Model oldGraph = ModelFactory.createDefaultModel();
    Model newGraph = ModelFactory.createDefaultModel();

    // When
    RDFPatch patch = service.computePutDiff(
        oldGraph, newGraph, "http://example.org/graph1");

    // Then
    assertThat(service.isPatchEmpty(patch)).isTrue();
  }

  @Test
  void computePutDiff_shouldHandleDefaultGraph_whenGraphIriNull() {
    // Given
    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    Model newGraph = ModelFactory.createDefaultModel();

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
    Model oldGraph = ModelFactory.createDefaultModel();
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value1"
    );
    oldGraph.add(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p2"),
        "value2"
    );

    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s3"),
        ResourceFactory.createProperty("http://example.org/p3"),
        "value3"
    );

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
    Model oldGraph = ModelFactory.createDefaultModel();
    Model newGraph = ModelFactory.createDefaultModel();
    newGraph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p1"),
        "value"
    );

    RDFPatch patch = service.computePutDiff(oldGraph, newGraph, null);

    // When
    boolean isEmpty = service.isPatchEmpty(patch);

    // Then
    assertThat(isEmpty).isFalse();
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
