package org.chucc.vcserver.integration;

import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SPARQL Query endpoint (GET /sparql).
 * Tests HTTP contract (command side) only - projector DISABLED.
 * Queries materialize datasets directly from commit history (sync operation).
 *
 * <p>Note: These tests query against the initial empty commit created by the test fixture.
 * Full end-to-end tests with data require GSP PUT integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlQueryIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlQuery_shouldExecuteWithBranchSelector() {
    // When: Query via SPARQL against initial empty commit
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Results returned with correct headers (empty results)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).contains("\"head\"", "\"results\"", "\"bindings\"");
  }

  @Test
  void sparqlQuery_shouldExecuteWithCommitSelector() {
    // When: Query specific commit (initial commit)
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("commit", initialCommitId.value())
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Results from that commit with ETag
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag())
        .isEqualTo("\"" + initialCommitId.value() + "\"");
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQuery_shouldReturn400ForSelectorConflict() {
    // When: Query with both branch and commit selectors
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .queryParam("commit", "abc123")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Selector conflict error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).containsIgnoringCase("selector_conflict");
  }

  @Test
  void sparqlQuery_shouldReturn400ForMalformedQuery() {
    // When: Query with syntax error (missing closing brace)
    String malformedQuery = "SELECT * WHERE { ?s ?p";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", malformedQuery)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Malformed query error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("MALFORMED_QUERY");
  }

  @Test
  void sparqlQuery_shouldSupportJsonResultFormat() {
    // When: Query with Accept: application/sparql-results+json
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(
        MediaType.parseMediaType("application/sparql-results+json")));

    HttpEntity<Void> request = new HttpEntity<>(headers);
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then: JSON results
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getBody()).contains("{", "\"head\"", "\"results\"");
  }

  @Test
  void sparqlQuery_shouldSupportXmlResultFormat() {
    // When: Query with Accept: application/sparql-results+xml
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(
        MediaType.parseMediaType("application/sparql-results+xml")));

    HttpEntity<Void> request = new HttpEntity<>(headers);
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then: XML results
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+xml");
    assertThat(response.getBody()).contains("<?xml", "<sparql");
  }

  @Test
  void sparqlQuery_shouldExecuteAskQuery() {
    // When: ASK query (empty dataset returns false)
    String query = "ASK { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Boolean result
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).contains("\"boolean\"");
  }

  @Test
  void sparqlQuery_shouldExecuteConstructQuery() {
    // When: CONSTRUCT query with Turtle accept header
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(java.util.List.of(MediaType.parseMediaType("text/turtle")));

    HttpEntity<Void> request = new HttpEntity<>(headers);
    String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        request,
        String.class
    );

    // Then: Turtle result (empty for empty dataset)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");
  }

  @Test
  void sparqlQuery_shouldHandleEmptyResults() {
    // When: Query for non-existent triples
    String query = "SELECT * WHERE { ?s <http://nonexistent> ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Empty results (but successful)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQuery_shouldReturn404ForNonExistentBranch() {
    // When: Query with non-existent branch
    String query = "SELECT * WHERE { ?s ?p ?o }";
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "nonexistent")
        .build()
        .toUri();
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Not found error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("NOT_FOUND");
  }
}
