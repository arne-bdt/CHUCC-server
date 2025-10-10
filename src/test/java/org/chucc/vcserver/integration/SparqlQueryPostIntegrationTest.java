package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.testutil.IntegrationTestFixture;
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

/**
 * Integration tests for SPARQL Query via POST (POST /sparql with application/sparql-query).
 * Tests HTTP contract (command side) only - projector DISABLED.
 * Queries materialize datasets directly from commit history (sync operation).
 *
 * <p>Note: These tests verify POST query behavior matches GET query behavior.
 * Query body is sent in POST body instead of URL query parameter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SparqlQueryPostIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlQueryPost_shouldExecuteWithQueryContentType() {
    // Given: Query in POST body with application/sparql-query
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    headers.set("Accept", "application/sparql-results+json");
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Results returned with correct headers
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).contains("\"head\"", "\"results\"", "\"bindings\"");
  }

  @Test
  void sparqlQueryPost_shouldExecuteWithBranchSelector() {
    // Given: Query with branch selector in URL
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with branch parameter
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Results from main branch
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQueryPost_shouldExecuteWithCommitSelector() {
    // Given: Query with commit selector
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with commit parameter
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?commit=" + initialCommitId.value(),
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Results from specific commit with ETag
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag())
        .isEqualTo("\"" + initialCommitId.value() + "\"");
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQueryPost_shouldExecuteWithAsOfSelector() {
    // Given: Query with asOf selector (future timestamp)
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with asOf parameter (future date)
    String futureTimestamp = "2099-12-31T23:59:59Z";
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?asOf=" + futureTimestamp,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Results from latest commit before that timestamp
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQueryPost_shouldHandleVeryLongQuery() {
    // Given: Very long query (>2048 chars) that would exceed GET URL limit
    StringBuilder longQuery = new StringBuilder("SELECT * WHERE { ");
    // Generate enough variables to exceed 2048 chars
    for (int i = 0; i < 200; i++) {
      longQuery.append("?subject").append(i).append(" ?predicate").append(i)
          .append(" ?object").append(i).append(" . ");
    }
    longQuery.append("}");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(longQuery.toString(), headers);

    // When: POST very long query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Query succeeds via POST (would fail via GET due to URL length)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(longQuery.length()).isGreaterThan(2048); // Verify it's actually long
    assertThat(response.getBody()).contains("\"bindings\"");
  }

  @Test
  void sparqlQueryPost_shouldSupportJsonResultFormat() {
    // Given: Query with Accept: application/sparql-results+json
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    headers.set("Accept", "application/sparql-results+json");
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with JSON accept header
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: JSON results
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+json");
    assertThat(response.getBody()).contains("{", "\"head\"", "\"results\"");
  }

  @Test
  void sparqlQueryPost_shouldSupportXmlResultFormat() {
    // Given: Query with Accept: application/sparql-results+xml
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    headers.set("Accept", "application/sparql-results+xml");
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with XML accept header
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: XML results
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/sparql-results+xml");
    assertThat(response.getBody()).contains("<?xml", "<sparql");
  }

  @Test
  void sparqlQueryPost_shouldReturn400ForMalformedQuery() {
    // Given: Malformed query (missing closing brace)
    String malformedQuery = "SELECT * WHERE { ?s ?p";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(malformedQuery, headers);

    // When: POST malformed query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Malformed query error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("MALFORMED_QUERY");
  }

  @Test
  void sparqlQueryPost_shouldReturn400ForSelectorConflict() {
    // Given: Query with both branch and commit selectors (conflict)
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with conflicting selectors (valid UUID format for commit)
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main&commit=" + initialCommitId.value(),
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Selector conflict error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).containsIgnoringCase("selector_conflict");
  }

  @Test
  void sparqlQueryPost_shouldReturn404ForNonExistentBranch() {
    // Given: Query with non-existent branch
    String query = "SELECT * WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST with non-existent branch
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=nonexistent",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Not found error
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("NOT_FOUND");
  }

  @Test
  void sparqlQueryPost_shouldExecuteAskQuery() {
    // Given: ASK query (empty dataset returns false)
    String query = "ASK { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST ASK query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Boolean result
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"boolean\"");
  }

  @Test
  void sparqlQueryPost_shouldExecuteConstructQuery() {
    // Given: CONSTRUCT query with Turtle accept header
    String query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-query"));
    headers.set("Accept", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>(query, headers);

    // When: POST CONSTRUCT query
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Turtle result (empty for empty dataset)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("text/turtle");
  }
}
