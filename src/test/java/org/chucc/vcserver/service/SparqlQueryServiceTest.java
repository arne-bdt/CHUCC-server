package org.chucc.vcserver.service;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.chucc.vcserver.domain.ResultFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SparqlQueryServiceTest {

  private SparqlQueryService service;
  private Dataset dataset;

  @BeforeEach
  void setUp() {
    service = new SparqlQueryService();

    // Create test dataset with sample data
    dataset = DatasetFactory.create();
    Model model = dataset.getDefaultModel();

    // Add test triples
    model.add(
        ResourceFactory.createResource("http://example.org/subject1"),
        ResourceFactory.createProperty("http://example.org/predicate1"),
        ResourceFactory.createPlainLiteral("value1")
    );
    model.add(
        ResourceFactory.createResource("http://example.org/subject2"),
        ResourceFactory.createProperty("http://example.org/predicate2"),
        ResourceFactory.createPlainLiteral("value2")
    );
    model.add(
        ResourceFactory.createResource("http://example.org/person1"),
        RDF.type,
        ResourceFactory.createResource("http://example.org/Person")
    );
  }

  @Test
  void executeQuery_shouldExecuteSelectQuery() {
    // Given
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("head");
    assertThat(results).contains("results");
    assertThat(results).contains("bindings");
  }

  @Test
  void executeQuery_shouldExecuteAskQuery() {
    // Given
    String query = "ASK { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("\"boolean\":true");
  }

  @Test
  void executeQuery_shouldExecuteConstructQuery() {
    // Given
    String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.TURTLE);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("example.org");
  }

  @Test
  void executeQuery_shouldExecuteDescribeQuery() {
    // Given
    String query = "DESCRIBE <http://example.org/subject1>";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.TURTLE);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("example.org");
  }

  @Test
  void executeQuery_shouldFormatResultsAsJson() {
    // Given
    String query = "SELECT ?s WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).contains("{");
    assertThat(results).contains("\"head\"");
    assertThat(results).contains("\"results\"");
  }

  @Test
  void executeQuery_shouldFormatResultsAsXml() {
    // Given
    String query = "SELECT ?s WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.XML);

    // Then
    assertThat(results).contains("<?xml");
    assertThat(results).contains("<sparql");
    assertThat(results).contains("<results>");
  }

  @Test
  void executeQuery_shouldFormatResultsAsCsv() {
    // Given
    String query = "SELECT ?s WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.CSV);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("s");
  }

  @Test
  void executeQuery_shouldFormatResultsAsTsv() {
    // Given
    String query = "SELECT ?s WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.TSV);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("?s");
  }

  @Test
  void executeQuery_shouldThrowOnMalformedQuery() {
    // Given
    String malformedQuery = "SELECT * WHERE { ?s ?p }"; // Missing closing brace

    // When/Then
    assertThatThrownBy(() -> service.executeQuery(dataset, malformedQuery, ResultFormat.JSON))
        .isInstanceOf(QueryParseException.class);
  }

  @Test
  void executeQuery_shouldHandleAskQueryWithFalseResult() {
    // Given
    String query = "ASK { <http://nonexistent> <http://nonexistent> <http://nonexistent> }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).contains("\"boolean\":false");
  }

  @Test
  void executeQuery_shouldHandleAskQueryInXmlFormat() {
    // Given
    String query = "ASK { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.XML);

    // Then
    assertThat(results).contains("<?xml");
    assertThat(results).contains("<boolean>true</boolean>");
  }

  @Test
  void executeQuery_shouldHandleEmptySelectResults() {
    // Given
    String query = "SELECT ?s WHERE { ?s <http://nonexistent> ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).isNotNull();
    assertThat(results).contains("\"bindings\"");
    // Empty bindings array should have no result entries
    assertThat(results).doesNotContain("\"value\"");
  }

  @Test
  void executeQuery_shouldHandleConstructWithRdfXmlFormat() {
    // Given
    String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.RDF_XML);

    // Then
    assertThat(results).contains("rdf:RDF");
    assertThat(results).contains("xmlns");
  }

  @Test
  void executeQuery_shouldHandleFilterInSelectQuery() {
    // Given
    String query = "SELECT ?s WHERE { ?s ?p \"value1\" }";

    // When
    String results = service.executeQuery(dataset, query, ResultFormat.JSON);

    // Then
    assertThat(results).contains("subject1");
    assertThat(results).doesNotContain("subject2");
  }
}
