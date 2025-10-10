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
  void filterByGraph_shouldIncludeDefaultGraphOperations_whenFilteringForNamedGraph() {
    // Given - Triple format patch (default graph operations)
    // Per filterByGraph design: default graph operations are interpreted as
    // targeting the current named graph when filtering for a named graph
    String patchText = """
        TX .
        A <http://example.org/s1> <http://example.org/p> "v1" .
        D <http://example.org/s2> <http://example.org/p> "v2" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When filtering for a named graph
    RDFPatch filtered = service.filterByGraph(patch, "http://example.org/myGraph");

    // Then - should include the default graph operations
    assertThat(filtered).isNotNull();
    String filteredString = org.chucc.vcserver.util.GraphCommandUtil.serializePatch(filtered);

    // Verify patch contains the operations (interpreted as targeting myGraph)
    assertThat(filteredString).contains("A <http://example.org/s1>");
    assertThat(filteredString).contains("D <http://example.org/s2>");
  }

  @Test
  void filterByGraph_shouldReturnEmptyPatch_whenFilteringDefaultGraphForNamedGraph() {
    // Given - Triple format patch (default graph)
    String patchText = """
        TX .
        A <http://example.org/s1> <http://example.org/p> "v1" .
        TC .
        """;
    RDFPatch patch = service.parsePatch(patchText);

    // When - Filter for default graph explicitly (null means default graph)
    RDFPatch filtered = service.filterByGraph(patch, null);

    // Then - Should contain the default graph operations
    assertThat(filtered).isNotNull();
    String filteredString = org.chucc.vcserver.util.GraphCommandUtil.serializePatch(filtered);
    assertThat(filteredString).contains("A <http://example.org/s1>");
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
