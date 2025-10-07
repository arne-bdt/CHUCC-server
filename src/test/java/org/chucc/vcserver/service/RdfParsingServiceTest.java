package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for RdfParsingService.
 */
class RdfParsingServiceTest {

  private RdfParsingService service;

  @BeforeEach
  void setUp() {
    service = new RdfParsingService();
  }

  @Test
  void parseRdf_shouldParseTurtle_whenValidTurtleProvided() {
    // Given
    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    // When
    Model model = service.parseRdf(turtle, "text/turtle");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.isEmpty()).isFalse();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldParseNTriples_whenValidNTriplesProvided() {
    // Given
    String ntriples = "<http://example.org/s> <http://example.org/p> \"value\" .";

    // When
    Model model = service.parseRdf(ntriples, "application/n-triples");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldParseJsonLd_whenValidJsonLdProvided() {
    // Given
    String jsonLd = """
        {
          "@context": "http://schema.org/",
          "@type": "Person",
          "name": "John Doe"
        }
        """;

    // When
    Model model = service.parseRdf(jsonLd, "application/ld+json");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void parseRdf_shouldParseRdfXml_whenValidRdfXmlProvided() {
    // Given
    String rdfXml = """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns:ex="http://example.org/">
          <rdf:Description rdf:about="http://example.org/subject">
            <ex:predicate>value</ex:predicate>
          </rdf:Description>
        </rdf:RDF>
        """;

    // When
    Model model = service.parseRdf(rdfXml, "application/rdf+xml");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldParseN3_whenValidN3Provided() {
    // Given
    String n3 = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    // When
    Model model = service.parseRdf(n3, "text/n3");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldReturnEmptyModel_whenEmptyContentProvided() {
    // Given
    String empty = "";

    // When
    Model model = service.parseRdf(empty, "text/turtle");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.isEmpty()).isTrue();
  }

  @Test
  void parseRdf_shouldThrow415_whenUnsupportedContentTypeProvided() {
    // Given
    String content = "some content";

    // When/Then
    assertThatThrownBy(() -> service.parseRdf(content, "application/unsupported"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("415")
        .hasMessageContaining("Unsupported Media Type");
  }

  @Test
  void parseRdf_shouldThrow400_whenInvalidSyntaxProvided() {
    // Given
    String invalidTurtle = "@prefix ex: <invalid syntax";

    // When/Then
    assertThatThrownBy(() -> service.parseRdf(invalidTurtle, "text/turtle"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400")
        .hasMessageContaining("Bad Request");
  }

  @Test
  void parseRdf_shouldHandleContentTypeWithParameters() {
    // Given
    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    // When
    Model model = service.parseRdf(turtle, "text/turtle; charset=utf-8");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldHandleAlternativeContentTypes() {
    // Given
    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    // When - test application/x-turtle alternative
    Model model = service.parseRdf(turtle, "application/x-turtle");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldDefaultToTurtle_whenNullContentTypeProvided() {
    // Given
    String turtle = """
        @prefix ex: <http://example.org/> .
        ex:subject ex:predicate "value" .
        """;

    // When
    Model model = service.parseRdf(turtle, null);

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(1);
  }

  @Test
  void parseRdf_shouldPreserveTriples_whenParsingComplexGraph() {
    // Given
    String turtle = """
        @prefix ex: <http://example.org/> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

        ex:subject1 ex:predicate1 "value1" .
        ex:subject2 ex:predicate2 "value2" .
        ex:subject3 rdf:type ex:Class .
        """;

    // When
    Model model = service.parseRdf(turtle, "text/turtle");

    // Then
    assertThat(model).isNotNull();
    assertThat(model.size()).isEqualTo(3);
    assertThat(model.contains(
        (org.apache.jena.rdf.model.Resource) null,
        RDF.type,
        (org.apache.jena.rdf.model.RDFNode) null)).isTrue();
  }
}
