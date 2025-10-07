package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
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
  private Model testModel;

  @BeforeEach
  void setUp() {
    service = new GraphSerializationService();

    // Create a simple test model
    testModel = ModelFactory.createDefaultModel();
    Resource subject = testModel.createResource("http://example.org/subject");
    subject.addProperty(RDF.type, RDFS.Class);
    subject.addProperty(RDFS.label, "Test Subject");
  }

  @Test
  void serializeGraph_shouldSerializeToTurtle() {
    String result = service.serializeGraph(testModel, "text/turtle");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
    // Turtle uses "a" as shorthand for rdf:type
    assertThat(result).containsAnyOf("rdf:type", "a");
    assertThat(result).containsAnyOf("rdfs:Class", "<http://www.w3.org/2000/01/rdf-schema#Class>");
  }

  @Test
  void serializeGraph_shouldSerializeToNTriples() {
    String result = service.serializeGraph(testModel, "application/n-triples");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
    assertThat(result).contains("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>");
  }

  @Test
  void serializeGraph_shouldSerializeToJsonLd() {
    String result = service.serializeGraph(testModel, "application/ld+json");

    assertThat(result).isNotBlank();
    assertThat(result).contains("@");
    assertThat(result).contains("http://example.org/subject");
  }

  @Test
  void serializeGraph_shouldSerializeToRdfXml() {
    String result = service.serializeGraph(testModel, "application/rdf+xml");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<rdf:RDF");
    assertThat(result).contains("http://example.org/subject");
  }

  @Test
  void serializeGraph_shouldSerializeToN3() {
    String result = service.serializeGraph(testModel, "text/n3");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldDefaultToTurtleWhenAcceptIsNull() {
    String result = service.serializeGraph(testModel, null);

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldDefaultToTurtleWhenAcceptIsBlank() {
    String result = service.serializeGraph(testModel, "");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldHandleContentTypeWithQuality() {
    String result = service.serializeGraph(testModel, "text/turtle;q=0.9");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldHandleContentTypeWithCharset() {
    String result = service.serializeGraph(testModel, "text/turtle; charset=utf-8");

    assertThat(result).isNotBlank();
    assertThat(result).contains("<http://example.org/subject>");
  }

  @Test
  void serializeGraph_shouldThrow406ForUnsupportedFormat() {
    assertThatThrownBy(() -> service.serializeGraph(testModel, "unsupported/format"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> {
          ResponseStatusException rse = (ResponseStatusException) ex;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
          assertThat(rse.getReason()).contains("Unsupported content type");
        });
  }

  @Test
  void serializeGraph_shouldHandleEmptyModel() {
    Model emptyModel = ModelFactory.createDefaultModel();

    String result = service.serializeGraph(emptyModel, "text/turtle");

    assertThat(result).isNotNull();
  }

  @Test
  void serializeGraph_shouldHandleAlternativeContentTypes() {
    // Test alternative MIME types for the same format
    String result1 = service.serializeGraph(testModel, "application/x-turtle");
    String result2 = service.serializeGraph(testModel, "text/plain");
    String result3 = service.serializeGraph(testModel, "application/json");

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
