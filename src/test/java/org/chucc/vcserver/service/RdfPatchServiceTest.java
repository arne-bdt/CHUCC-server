package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for RdfPatchService.
 */
class RdfPatchServiceTest {

  private RdfPatchService service;

  @BeforeEach
  void setUp() {
    service = new RdfPatchService();
  }

  @Test
  void parsePatch_shouldParseValidPatch_whenValidSyntaxProvided() {
    // Given
    String patchText = """
        TX .
        A <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;

    // When
    RDFPatch patch = service.parsePatch(patchText);

    // Then
    assertThat(patch).isNotNull();
  }

  @Test
  void parsePatch_shouldThrowException_whenInvalidSyntaxProvided() {
    // Given
    String invalidPatch = "INVALID PATCH SYNTAX";

    // When / Then
    assertThatThrownBy(() -> service.parsePatch(invalidPatch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  void parsePatch_shouldThrowException_whenEmptyPatchProvided() {
    // Given
    String emptyPatch = "";

    // When / Then
    assertThatThrownBy(() -> service.parsePatch(emptyPatch))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  @Test
  @org.junit.jupiter.api.Disabled("Quad format filtering needs implementation - use triple format for now")
  void filterByGraph_shouldFilterToTargetGraph_whenMultipleGraphsPresent() {
    // Given - In RDF Patch format, quad format is: OPERATION GRAPH SUBJECT PREDICATE OBJECT
    String patchText = """
        TX .
        A <http://example.org/graph1> <http://example.org/s1> <http://example.org/p> "v1" .
        A <http://example.org/graph2> <http://example.org/s2> <http://example.org/p> "v2" .
        D <http://example.org/graph1> <http://example.org/s3> <http://example.org/p> "v3" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    RDFPatch filtered = service.filterByGraph(patch, "http://example.org/graph1");

    // Then
    assertThat(filtered).isNotNull();
    String filteredString = org.chucc.vcserver.util.GraphCommandUtil.serializePatch(filtered);

    // Verify patch is not empty - should have operations
    assertThat(filteredString).containsPattern("(A |D )");

    // Verify it contains operations with graph1
    long graph1Count = filteredString.lines()
        .filter(line -> line.contains("http://example.org/graph1"))
        .count();
    assertThat(graph1Count).isGreaterThan(0);

    // Verify it does NOT contain operations with graph2
    assertThat(filteredString).doesNotContain("http://example.org/graph2");
  }

  @Test
  void filterByGraph_shouldReturnEmptyPatch_whenNoMatchingGraph() {
    // Given
    String patchText = """
        TX .
        A <http://example.org/graph1> <http://example.org/s1> <http://example.org/p> "v1" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    RDFPatch filtered = service.filterByGraph(patch, "http://example.org/graph2");

    // Then
    assertThat(filtered).isNotNull();
    // Filtered patch should be empty (only TX/TC)
    String filteredString = org.chucc.vcserver.util.GraphCommandUtil.serializePatch(filtered);
    assertThat(filteredString).doesNotContain("A ");
    assertThat(filteredString).doesNotContain("D ");
  }

  @Test
  void canApply_shouldReturnTrue_whenPatchCanBeApplied() {
    // Given
    Model graph = ModelFactory.createDefaultModel();
    graph.add(
        ResourceFactory.createResource("http://example.org/s"),
        ResourceFactory.createProperty("http://example.org/p"),
        "existing"
    );

    String patchText = """
        TX .
        A <http://example.org/s> <http://example.org/p> "new" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    boolean canApply = service.canApply(graph, patch);

    // Then
    assertThat(canApply).isTrue();
  }

  @Test
  void canApply_shouldReturnFalse_whenDeletingNonExistentTriple() {
    // Given
    Model graph = ModelFactory.createDefaultModel();

    String patchText = """
        TX .
        D <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    boolean canApply = service.canApply(graph, patch);

    // Then
    assertThat(canApply).isFalse();
  }

  @Test
  void applyPatch_shouldApplyAddOperation_whenAddingTriple() {
    // Given
    Model graph = ModelFactory.createDefaultModel();

    String patchText = """
        TX .
        A <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    Model result = service.applyPatch(graph, patch);

    // Then
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.contains(
        ResourceFactory.createResource("http://example.org/s"),
        ResourceFactory.createProperty("http://example.org/p"),
        "value"
    )).isTrue();
  }

  @Test
  void applyPatch_shouldApplyDeleteOperation_whenDeletingTriple() {
    // Given
    Model graph = ModelFactory.createDefaultModel();
    graph.add(
        ResourceFactory.createResource("http://example.org/s"),
        ResourceFactory.createProperty("http://example.org/p"),
        "value"
    );

    String patchText = """
        TX .
        D <http://example.org/s> <http://example.org/p> "value" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    Model result = service.applyPatch(graph, patch);

    // Then
    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void applyPatch_shouldApplyMultipleOperations_whenMixedOperations() {
    // Given
    Model graph = ModelFactory.createDefaultModel();
    graph.add(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p"),
        "old"
    );

    String patchText = """
        TX .
        D <http://example.org/s1> <http://example.org/p> "old" .
        A <http://example.org/s1> <http://example.org/p> "new" .
        A <http://example.org/s2> <http://example.org/p> "value2" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When
    Model result = service.applyPatch(graph, patch);

    // Then
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.contains(
        ResourceFactory.createResource("http://example.org/s1"),
        ResourceFactory.createProperty("http://example.org/p"),
        "new"
    )).isTrue();
    assertThat(result.contains(
        ResourceFactory.createResource("http://example.org/s2"),
        ResourceFactory.createProperty("http://example.org/p"),
        "value2"
    )).isTrue();
  }
}
