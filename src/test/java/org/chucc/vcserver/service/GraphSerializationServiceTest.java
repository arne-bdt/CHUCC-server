package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.graph.GraphFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for GraphSerializationService.
 */
class GraphSerializationServiceTest {

  private GraphSerializationService service;
  private Graph testGraph;

  @BeforeEach
  void setUp() {
    service = new GraphSerializationService();

    // Create a simple test graph
    testGraph = GraphFactory.createDefaultGraph();
    var subject = NodeFactory.createURI("http://example.org/subject");
    testGraph.add(Triple.create(subject, RDF.type.asNode(), RDFS.Class.asNode()));
    testGraph.add(Triple.create(subject, RDFS.label.asNode(),
        NodeFactory.createLiteralString("Test Subject")));
  }

  @Test
  void serializeGraph_shouldSerializeToTurtle() {
    String result = service.serializeGraph(testGraph, "text/turtle");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
    // Turtle uses "a" as shorthand for rdf:type
    assertThat(result).containsAnyOf("rdf:type", "a");
    assertThat(result).containsAnyOf("rdfs:Class", "<http://www.w3.org/2000/01/rdf-schema#Class>");
  }

  @Test
  void serializeGraph_shouldSerializeToNTriples() {
    String result = service.serializeGraph(testGraph, "application/n-triples");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
    assertThat(result).contains("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>");
  }

  @Test
  void serializeGraph_shouldSerializeToJsonLd() {
    String result = service.serializeGraph(testGraph, "application/ld+json");

    assertThat(result).isNotBlank();
    assertThat(result).contains("@");
    assertThat(result).contains("http://example.org/subject");
  }

  @Test
  void serializeGraph_shouldSerializeToRdfXml() {
    String result = service.serializeGraph(testGraph, "application/rdf+xml");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<rdf:RDF");
    assertThat(result).contains("http://example.org/subject");
  }

  @Test
  void serializeGraph_shouldSerializeToN3() {
    String result = service.serializeGraph(testGraph, "text/n3");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldDefaultToTurtleWhenAcceptIsNull() {
    String result = service.serializeGraph(testGraph, null);

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldDefaultToTurtleWhenAcceptIsBlank() {
    String result = service.serializeGraph(testGraph, "");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldHandleContentTypeWithQuality() {
    String result = service.serializeGraph(testGraph, "text/turtle;q=0.9");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldHandleContentTypeWithCharset() {
    String result = service.serializeGraph(testGraph, "text/turtle; charset=utf-8");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldThrow406ForUnsupportedFormat() {
    assertThatThrownBy(() -> service.serializeGraph(testGraph, "unsupported/format"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          ResponseStatusException rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
          assertThat(rse.getReason()).contains("Unsupported content type");
        });
  }

  @Test
  void serializeGraph_shouldHandleEmptyModel() {
    Graph emptyGraph = GraphFactory.createDefaultGraph();

    String result = service.serializeGraph(emptyGraph, "text/turtle");

    assertThat(result).isNotNull();
  }

  @Test
  void serializeGraph_shouldHandleAlternativeContentTypes() {
    // Test alternative MIME types for the same format
    String result1 = service.serializeGraph(testGraph, "application/x-turtle");
    String result2 = service.serializeGraph(testGraph, "text/plain");
    String result3 = service.serializeGraph(testGraph, "application/json");

    assertThat(result1).isNotBlank();
    assertThat(result2).isNotBlank();
    assertThat(result3).isNotBlank();
  }

  @Test
  void getContentType_shouldReturnCorrectContentTypeForTurtle() {
    String contentType = service.getContentType(org.apache.jena.riot.Lang.TURTLE);

    assertThat(contentType).isEqualTo("text/turtle");
  }

  @Test
  void getContentType_shouldReturnCorrectContentTypeForNTriples() {
    String contentType = service.getContentType(org.apache.jena.riot.Lang.NTRIPLES);

    assertThat(contentType).isEqualTo("application/n-triples");
  }

  @Test
  void getContentType_shouldReturnCorrectContentTypeForJsonLd() {
    String contentType = service.getContentType(org.apache.jena.riot.Lang.JSONLD);

    assertThat(contentType).isEqualTo("application/ld+json");
  }

  @Test
  void getContentType_shouldReturnDefaultForNull() {
    String contentType = service.getContentType(null);

    assertThat(contentType).isEqualTo("text/turtle");
  }
}
