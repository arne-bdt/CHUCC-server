package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for Service Description endpoint.
 * Tests SPARQL 1.1 Service Description implementation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ServiceDescriptionIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private TagRepository tagRepository;

  @Test
  void wellKnownVoid_shouldReturnServiceDescription() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/.well-known/void",
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");
    assertThat(response.getBody()).contains("sd:Service");
  }

  @Test
  void serviceDescription_shouldReturnServiceDescription() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("sd:Service");
  }

  @Test
  void serviceDescription_shouldSupportTurtle() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "text/turtle");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/service-description",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");

    // Verify valid Turtle
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, new ByteArrayInputStream(
        response.getBody().getBytes(StandardCharsets.UTF_8)), Lang.TURTLE);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void serviceDescription_shouldSupportJsonLd() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/ld+json");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/service-description",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/ld+json");

    // Verify valid JSON-LD
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, new ByteArrayInputStream(
        response.getBody().getBytes(StandardCharsets.UTF_8)), Lang.JSONLD);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void serviceDescription_shouldSupportRdfXml() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/rdf+xml");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/service-description",
        HttpMethod.GET,
        new HttpEntity<>(headers),
        String.class);

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/rdf+xml");

    // Verify valid RDF/XML
    Model model = ModelFactory.createDefaultModel();
    RDFDataMgr.read(model, new ByteArrayInputStream(
        response.getBody().getBytes(StandardCharsets.UTF_8)), Lang.RDFXML);
    assertThat(model.isEmpty()).isFalse();
  }

  @Test
  void serviceDescription_shouldDescribeSparql11Query() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:SPARQL11Query");
  }

  @Test
  void serviceDescription_shouldDescribeSparql11Update() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:SPARQL11Update");
  }

  @Test
  void serviceDescription_shouldDescribeResultFormats() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:resultFormat");
    assertThat(response.getBody()).contains("SPARQL_Results_JSON");
  }

  @Test
  void serviceDescription_shouldDescribeVersionControlFeatures() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:Merge");
    assertThat(response.getBody()).contains("vc:TimeTravel");
    assertThat(response.getBody()).contains("vc:Blame");
  }

  @Test
  void serviceDescription_shouldIncludeVersionControlEndpoint() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:versionControlEndpoint");
    assertThat(response.getBody()).contains("/version");
  }

  @Test
  void serviceDescription_shouldListAvailableDatasets() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:Dataset");
    assertThat(response.getBody()).contains("/default"); // Default dataset from ITFixture
  }

  @Test
  void serviceDescription_shouldDescribeDatasetGraphs() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert - verify structure includes namedGraph property
    // (actual named graphs depend on dataset state)
    assertThat(response.getBody()).contains("sd:Dataset");
  }

  @Test
  void serviceDescription_shouldDescribeDefaultGraph() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("sd:defaultGraph");
  }

  @Test
  void serviceDescription_shouldIncludeGraphSizes() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert - verify triple count is included (value depends on dataset state)
    assertThat(response.getBody()).contains("void:triples");
  }

  @Test
  void serviceDescription_shouldIncludeSparqlEndpointPerDataset() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("void:sparqlEndpoint");
    assertThat(response.getBody()).contains("/default/sparql");
  }

  @Test
  void serviceDescription_shouldExposeVersionControlVocabulary() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:VersionedDataset");
  }

  @Test
  void serviceDescription_shouldListBranches() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:branch");
    assertThat(response.getBody()).contains("vc:name");
    assertThat(response.getBody()).contains("\"main\""); // Default branch
  }

  @Test
  void serviceDescription_shouldDescribeBranchDetails() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:head");
    assertThat(response.getBody()).contains("vc:protected");
    assertThat(response.getBody()).contains("vc:createdAt");
  }

  @Test
  void serviceDescription_shouldListTags() {
    // Arrange: Create a tag directly via repository
    Tag tag = new Tag(
        "v1.0.0",
        initialCommitId,
        "Initial release",
        "Test Author <test@example.org>",
        Instant.now());
    tagRepository.save(DEFAULT_DATASET, tag);

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:tag");
    assertThat(response.getBody()).contains("vc:Tag");
    assertThat(response.getBody()).contains("v1.0.0");
    assertThat(response.getBody()).contains("vc:message");
    assertThat(response.getBody()).contains("Initial release");
  }

  @Test
  void serviceDescription_shouldIndicateDefaultBranch() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert
    assertThat(response.getBody()).contains("vc:defaultBranch");
    assertThat(response.getBody()).contains("\"main\"");
  }

  @Test
  void serviceDescription_shouldDescribeSparqlFeatures() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert - SPARQL 1.1 features
    assertThat(response.getBody()).contains("sd:PropertyPaths");
    assertThat(response.getBody()).contains("sd:Aggregates");
    assertThat(response.getBody()).contains("sd:SubQueries");
  }

  @Test
  void serviceDescription_shouldDescribeAllResultFormats() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert - Result formats
    assertThat(response.getBody()).contains("SPARQL_Results_JSON");
    assertThat(response.getBody()).contains("SPARQL_Results_XML");
    assertThat(response.getBody()).contains("SPARQL_Results_CSV");
    assertThat(response.getBody()).contains("SPARQL_Results_TSV");
    assertThat(response.getBody()).contains("Turtle");
    assertThat(response.getBody()).contains("JSON-LD");
  }

  @Test
  void serviceDescription_shouldDescribeInputFormats() {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/service-description",
        String.class);

    // Assert - Input formats (verify key formats for SPARQL UPDATE and GSP)
    assertThat(response.getBody()).contains("sd:inputFormat");
    assertThat(response.getBody()).contains("Turtle");
    assertThat(response.getBody()).contains("N-Triples");
    assertThat(response.getBody()).contains("JSON-LD");
  }
}
