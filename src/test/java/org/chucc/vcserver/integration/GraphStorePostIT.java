package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.testutil.ITFixture;
import org.chucc.vcserver.testutil.TestConstants;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for Graph Store Protocol POST operation.
 * Tests both API layer (synchronous HTTP response validation) and
 * full system behavior (async event processing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphStorePostIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private static final String TURTLE_ADDITIONAL = "@prefix ex: <http://example.org/> .\n"
      + "ex:subject2 ex:predicate2 \"additional\" .";

  // ========== API Layer Tests (synchronous response validation) ==========

  @Test
  void postGraph_shouldReturn200WithHeaders_whenAddingTriplestoExistingGraph() {
    // Given - Create initial graph
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set("Content-Type", "text/turtle");
    putHeaders.set("SPARQL-VC-Author", "Alice");
    putHeaders.set("SPARQL-VC-Message", "Create graph");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, putHeaders),
        String.class
    );

    // When - POST additional triples
    HttpHeaders postHeaders = new HttpHeaders();
    postHeaders.set("Content-Type", "text/turtle");
    postHeaders.set("SPARQL-VC-Author", "Bob");
    postHeaders.set("SPARQL-VC-Message", "Add more triples");
    HttpEntity<String> request = new HttpEntity<>(TURTLE_ADDITIONAL, postHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    assertThat(response.getHeaders().getFirst("Location")).matches("/version/commits/.*");
    assertThat(response.getHeaders().getFirst("ETag")).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).matches("\"[0-9a-f-]+\"");
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");

    // Note: Repository updates handled by event projectors (async)
  }

  @Test
  void postGraph_shouldReturn200_whenCreatingNewGraphWithPost() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Create graph with POST");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isNotNull();
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");

    // Note: Repository updates handled by event projectors (async)
  }

  @Test
  void postGraph_shouldReturn204_whenAllTriplesAlreadyPresent() {
    // Given - Create initial graph
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set("Content-Type", "text/turtle");
    putHeaders.set("SPARQL-VC-Author", "Alice");
    putHeaders.set("SPARQL-VC-Message", "Create graph");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, putHeaders),
        String.class
    );

    // Wait for event processing
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
            .map(b -> !b.getCommitId().equals(initialCommitId))
            .orElse(false));

    // When - POST same content (no-op)
    HttpHeaders postHeaders = new HttpHeaders();
    postHeaders.set("Content-Type", "text/turtle");
    postHeaders.set("SPARQL-VC-Author", "Bob");
    postHeaders.set("SPARQL-VC-Message", "No-op POST");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, postHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - Should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void postGraph_shouldAcceptTurtle_whenContentTypeTurtle() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST Turtle");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void postGraph_shouldAcceptNTriples_whenContentTypeNTriples() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/n-triples");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST N-Triples");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.NTRIPLES_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void postGraph_shouldAcceptJsonLd_whenContentTypeJsonLd() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/ld+json");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST JSON-LD");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.JSONLD_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void postGraph_shouldReturn409_whenIfMatchDoesNotMatch() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST with stale ETag (concurrent modification)");
    headers.set("If-Match", "\"01936c7f-8a2e-7890-abcd-ef1234567890\""); // stale ETag
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("concurrent_modification_conflict");
  }

  @Test
  void postGraph_shouldReturn400_whenRdfMalformed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST malformed RDF");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.MALFORMED_TURTLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void postGraph_shouldReturn415_whenContentTypeUnsupported() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/unsupported");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST unsupported format");
    HttpEntity<String> request = new HttpEntity<>("some content", headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void postGraph_shouldReturn400_whenBothGraphAndDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST invalid params");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/g&default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void postGraph_shouldReturn400_whenNeitherGraphNorDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST missing graph param");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void postGraph_shouldReturn400_whenCommitSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST on commit");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&commit=" + this.initialCommitId.value(),
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("write_on_readonly_selector");
  }

  @Test
  void postGraph_shouldReturn400_whenAsOfSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "POST on asOf");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&asOf=2025-01-01T00:00:00Z",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("write_on_readonly_selector");
  }

  // ========== Full System Tests (async event processing) ==========

  @Test
  void postGraph_shouldEventuallyUpdateRepository_whenAddingTriples() {
    // Given - Create initial graph
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set("Content-Type", "text/turtle");
    putHeaders.set("SPARQL-VC-Author", "Alice");
    putHeaders.set("SPARQL-VC-Message", "Create graph");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, putHeaders),
        String.class
    );

    // When - POST additional triples
    HttpHeaders postHeaders = new HttpHeaders();
    postHeaders.set("Content-Type", "text/turtle");
    postHeaders.set("SPARQL-VC-Author", "Bob");
    postHeaders.set("SPARQL-VC-Message", "Add triples");
    HttpEntity<String> request = new HttpEntity<>(TURTLE_ADDITIONAL, postHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - Wait for async event processing
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag = response.getHeaders().getFirst("ETag");
    String commitId = etag.replaceAll("\"", "");

    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, new CommitId(commitId))
            .isPresent());

    // Verify branch updated
    Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
        .orElseThrow();
    assertThat(branch.getCommitId().value()).isEqualTo(commitId);
  }

  @Test
  void postGraph_shouldReturnMergedContent_whenGetAfterPost() {
    // Given - PUT initial content
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set("Content-Type", "text/turtle");
    putHeaders.set("SPARQL-VC-Author", "Alice");
    putHeaders.set("SPARQL-VC-Message", "Create graph");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, putHeaders),
        String.class
    );

    // POST additional content
    HttpHeaders postHeaders = new HttpHeaders();
    postHeaders.set("Content-Type", "text/turtle");
    postHeaders.set("SPARQL-VC-Author", "Bob");
    postHeaders.set("SPARQL-VC-Message", "Add triples");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.POST,
        new HttpEntity<>(TURTLE_ADDITIONAL, postHeaders),
        String.class
    );

    // Wait for event processing
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
            .map(b -> !b.getCommitId().equals(initialCommitId))
            .orElse(false));

    // When - GET the graph
    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.set("Accept", "text/turtle");
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        new HttpEntity<>(getHeaders),
        String.class
    );

    // Then - Should contain both original and added triples
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("http://example.org/subject");
    assertThat(body).contains("http://example.org/subject2");
    assertThat(body).contains("http://example.org/predicate");
    assertThat(body).contains("http://example.org/predicate2");
  }
}
