package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Integration tests for SPARQL UPDATE endpoint (POST /sparql with application/sparql-update).
 *
 * <p>These tests verify the HTTP contract for SPARQL UPDATE operations including:
 * <ul>
 *   <li>Successful updates create commits and return 200 with ETag and Location headers</li>
 *   <li>No-op updates return 204 No Content</li>
 *   <li>If-Match precondition checking returns 412 on mismatch</li>
 *   <li>Missing required headers return 400 Bad Request</li>
 *   <li>Malformed SPARQL UPDATE returns 400 Bad Request</li>
 *   <li>Multi-graph updates are supported</li>
 * </ul>
 *
 * <p>Note: Projector is DISABLED in these tests (default for API layer testing).
 * We only test the HTTP response, not repository state.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
public class SparqlUpdateIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void sparqlUpdate_shouldCreateCommit_whenUpdateModifiesData() {
    // Given
    String updateBody = "INSERT DATA { <http://example.org/s1> "
        + "<http://example.org/p1> \"value1\" }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Add test triple");
    headers.set("SPARQL-VC-Author", "testUser");
    headers.set("SPARQL-VC-Branch", "main");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString())
        .contains("/version/datasets/default/commits/");
    assertThat(response.getBody()).contains("Update accepted");
    assertThat(response.getBody()).contains("commitId");

    // Note: Repository updates handled by ReadModelProjector (disabled in this test)
  }

  @Test
  void sparqlUpdate_shouldReturn204_whenUpdateIsNoOp() {
    // Given: Delete a non-existent triple (no-op)
    String updateBody = "DELETE DATA { <http://example.org/nonexistent> "
        + "<http://example.org/p> \"value\" }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "No-op delete");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getBody()).isNull();
  }

  @Test
  void sparqlUpdate_shouldReturn412_whenIfMatchMismatch() {
    // Given: Use a valid CommitId format but wrong value
    String updateBody = "INSERT DATA { <http://example.org/s2> "
        + "<http://example.org/p2> \"value2\" }";

    // Use a properly formatted but incorrect commit ID
    String wrongCommitId = "01234567-89ab-cdef-0123-456789abcdef";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Test precondition");
    headers.set("SPARQL-VC-Author", "testUser");
    headers.set("If-Match", "\"" + wrongCommitId + "\"");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(response.getBody()).contains("If-Match precondition failed");
  }

  @Test
  void sparqlUpdate_shouldReturn400_whenMessageHeaderMissing() {
    // Given
    String updateBody = "INSERT DATA { <http://example.org/s3> "
        + "<http://example.org/p3> \"value3\" }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Author", "testUser");
    // Missing SPARQL-VC-Message header

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("SPARQL-VC-Message header is required");
  }

  @Test
  void sparqlUpdate_shouldReturn400_whenAuthorHeaderMissing() {
    // Given
    String updateBody = "INSERT DATA { <http://example.org/s4> "
        + "<http://example.org/p4> \"value4\" }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Test without author");
    // Missing SPARQL-VC-Author header

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("SPARQL-VC-Author header is required");
  }

  @Test
  void sparqlUpdate_shouldReturn400_whenUpdateIsMalformed() {
    // Given: Malformed UPDATE (missing closing brace)
    String updateBody = "INSERT DATA { <http://example.org/s5> "
        + "<http://example.org/p5> \"value5\"";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Malformed update");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("malformed");
  }

  @Test
  void sparqlUpdate_shouldHandleMultipleGraphs() {
    // Given: Update affecting multiple named graphs
    String updateBody = "INSERT DATA { "
        + "GRAPH <http://example.org/g1> { <http://example.org/s> "
        + "<http://example.org/p> \"value1\" } . "
        + "GRAPH <http://example.org/g2> { <http://example.org/s> "
        + "<http://example.org/p> \"value2\" } "
        + "}";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Multi-graph update");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
  }

  @Test
  void sparqlUpdate_shouldSupportDeleteInsertWhere() {
    // Given: DELETE/INSERT WHERE that modifies existing data in the SAME update
    // Note: In API-layer tests (projector disabled), updates must be self-contained
    // This test uses a compound update: INSERT then DELETE/INSERT in one operation

    String updateBody = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/oldPredicate> \"oldValue\" } ; "
        + "DELETE { ?s <http://example.org/oldPredicate> ?o } "
        + "INSERT { ?s <http://example.org/newPredicate> ?o } "
        + "WHERE { ?s <http://example.org/oldPredicate> ?o }";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/sparql-update"));
    headers.set("SPARQL-VC-Message", "Insert and rename predicate");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<String> request = new HttpEntity<>(updateBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/sparql",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getLocation()).isNotNull();
  }
}
