package org.chucc.vcserver.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for GraphIdentifier value object.
 */
class GraphIdentifierTest {

  @Test
  void defaultGraph_shouldCreateDefaultGraphInstance() {
    GraphIdentifier identifier = GraphIdentifier.defaultGraph();

    assertThat(identifier.isDefault()).isTrue();
    assertThat(identifier.isNamed()).isFalse();
  }

  @Test
  void named_shouldCreateNamedGraphWithValidIri() {
    String iri = "http://example.org/graph1";

    GraphIdentifier identifier = GraphIdentifier.named(iri);

    assertThat(identifier.isNamed()).isTrue();
    assertThat(identifier.isDefault()).isFalse();
    assertThat(identifier.iri()).isEqualTo(iri);
  }

  @Test
  void named_shouldAcceptValidIriWithUnicode() {
    String iri = "http://example.org/graph-\u00E9";

    GraphIdentifier identifier = GraphIdentifier.named(iri);

    assertThat(identifier.iri()).isEqualTo(iri);
  }

  @Test
  void named_shouldRejectNullIri() {
    assertThatThrownBy(() -> GraphIdentifier.named(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("IRI cannot be null");
  }

  @Test
  void named_shouldRejectBlankIri() {
    assertThatThrownBy(() -> GraphIdentifier.named(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IRI cannot be blank");

    assertThatThrownBy(() -> GraphIdentifier.named("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("IRI cannot be blank");
  }

  @Test
  void named_shouldRejectInvalidIri() {
    assertThatThrownBy(() -> GraphIdentifier.named("not a valid iri"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid IRI");
  }

  @Test
  void named_shouldRejectIriWithSpaces() {
    assertThatThrownBy(() -> GraphIdentifier.named("http://example.org/has spaces"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid IRI");
  }

  @Test
  void iri_shouldThrowExceptionForDefaultGraph() {
    GraphIdentifier identifier = GraphIdentifier.defaultGraph();

    assertThatThrownBy(identifier::iri)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Default graph has no IRI");
  }

  @Test
  void equals_shouldReturnTrueForEqualDefaultGraphs() {
    GraphIdentifier id1 = GraphIdentifier.defaultGraph();
    GraphIdentifier id2 = GraphIdentifier.defaultGraph();

    assertThat(id1).isEqualTo(id2);
  }

  @Test
  void equals_shouldReturnTrueForEqualNamedGraphs() {
    String iri = "http://example.org/graph1";
    GraphIdentifier id1 = GraphIdentifier.named(iri);
    GraphIdentifier id2 = GraphIdentifier.named(iri);

    assertThat(id1).isEqualTo(id2);
  }

  @Test
  void equals_shouldReturnFalseForDifferentNamedGraphs() {
    GraphIdentifier id1 = GraphIdentifier.named("http://example.org/graph1");
    GraphIdentifier id2 = GraphIdentifier.named("http://example.org/graph2");

    assertThat(id1).isNotEqualTo(id2);
  }

  @Test
  void equals_shouldReturnFalseForDefaultAndNamedGraphs() {
    GraphIdentifier defaultGraph = GraphIdentifier.defaultGraph();
    GraphIdentifier namedGraph = GraphIdentifier.named("http://example.org/graph1");

    assertThat(defaultGraph).isNotEqualTo(namedGraph);
  }

  @Test
  void hashCode_shouldBeConsistentForDefaultGraphs() {
    GraphIdentifier id1 = GraphIdentifier.defaultGraph();
    GraphIdentifier id2 = GraphIdentifier.defaultGraph();

    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  void hashCode_shouldBeConsistentForNamedGraphs() {
    String iri = "http://example.org/graph1";
    GraphIdentifier id1 = GraphIdentifier.named(iri);
    GraphIdentifier id2 = GraphIdentifier.named(iri);

    assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
  }

  @Test
  void toString_shouldReturnMeaningfulRepresentation() {
    GraphIdentifier defaultGraph = GraphIdentifier.defaultGraph();
    GraphIdentifier namedGraph = GraphIdentifier.named("http://example.org/graph1");

    assertThat(defaultGraph.toString()).contains("default");
    assertThat(namedGraph.toString()).contains("http://example.org/graph1");
  }

  @Test
  void named_shouldAcceptUrn() {
    String urn = "urn:example:graph1";

    GraphIdentifier identifier = GraphIdentifier.named(urn);

    assertThat(identifier.iri()).isEqualTo(urn);
  }

  @Test
  void named_shouldAcceptHttpsIri() {
    String iri = "https://example.org/secure-graph";

    GraphIdentifier identifier = GraphIdentifier.named(iri);

    assertThat(identifier.iri()).isEqualTo(iri);
  }

  @Test
  void named_shouldAcceptIriWithFragment() {
    String iri = "http://example.org/graph#main";

    GraphIdentifier identifier = GraphIdentifier.named(iri);

    assertThat(identifier.iri()).isEqualTo(iri);
  }

  @Test
  void named_shouldAcceptIriWithQueryString() {
    String iri = "http://example.org/graph?version=1";

    GraphIdentifier identifier = GraphIdentifier.named(iri);

    assertThat(identifier.iri()).isEqualTo(iri);
  }
}
