package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Integration tests for Graph Store Protocol PUT operation.
 * Tests both API layer (synchronous HTTP response validation) and
 * full system behavior (async event processing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStorePutIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;
  private static final String TURTLE_CONTENT_UPDATED = "@prefix ex: <http://example.org/> .\n"
      + "ex:subject ex:predicate \"updated\" .";

  // ========== API Layer Tests (synchronous response validation) ==========

  @Test
  void putGraph_shouldReturn200WithHeaders_whenCreatingNewGraph() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Create new graph");

    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    assertThat(response.getHeaders().getFirst("Location")).matches("/version/commits/.*");
    assertThat(response.getHeaders().getFirst("ETag")).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).matches("\"[0-9a-f-]+\"");
    assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");

    // Note: Repository updates handled by event projectors (async)
  }

  @Test
  void putGraph_shouldReturn200_whenReplacingExistingGraph() {
    // Given - Create initial graph
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create graph");
    restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers1),
        String.class
    );

    // When - Replace graph
    HttpHeaders headers2 = new HttpHeaders();
    headers2.set("Content-Type", "text/turtle");
    headers2.set("SPARQL-VC-Author", "Bob");
    headers2.set("SPARQL-VC-Message", "Replace graph");
    HttpEntity<String> request = new HttpEntity<>(TURTLE_CONTENT_UPDATED, headers2);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isNotNull();

    // Note: Repository updates handled by event projectors (async)
  }

  // TODO: No-op detection test requires event processing implementation
  // @Test
  // void putGraph_shouldReturn204_whenNoChangesDetected() {
  //   // Given - Create initial graph
  //   HttpHeaders headers1 = new HttpHeaders();
  //   headers1.set("Content-Type", "text/turtle");
  //   headers1.set("SPARQL-VC-Author", "Alice");
  //   headers1.set("SPARQL-VC-Message", "Create graph");
  //   restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>(TURTLE_CONTENT, headers1),
  //       String.class
  //   );
  //
  //   // When - PUT same content again (no-op)
  //   HttpHeaders headers2 = new HttpHeaders();
  //   headers2.set("Content-Type", "text/turtle");
  //   headers2.set("SPARQL-VC-Author", "Bob");
  //   headers2.set("SPARQL-VC-Message", "No-op PUT");
  //   HttpEntity<String> request = new HttpEntity<>(TURTLE_CONTENT, headers2);
  //
  //   ResponseEntity<String> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       request,
  //       String.class
  //   );
  //
  //   // Then
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  // }

  @Test
  void putGraph_shouldAcceptTurtle_whenContentTypeTurtle() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT Turtle");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void putGraph_shouldAcceptNTriples_whenContentTypeNTriples() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/n-triples");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT N-Triples");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.NTRIPLES_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void putGraph_shouldAcceptJsonLd_whenContentTypeJsonLd() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/ld+json");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT JSON-LD");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.JSONLD_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void putGraph_shouldReturn409_whenIfMatchDoesNotMatch() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT with stale ETag (concurrent modification)");
    headers.set("If-Match", "\"01936c7f-8a2e-7890-abcd-ef1234567890\""); // stale ETag
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
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
  void putGraph_shouldReturn400_whenRdfMalformed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT malformed RDF");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.MALFORMED_TURTLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void putGraph_shouldReturn415_whenContentTypeUnsupported() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/unsupported");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT unsupported format");
    HttpEntity<String> request = new HttpEntity<>("some content", headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void putGraph_shouldReturn400_whenBothGraphAndDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT invalid params");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/g&default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void putGraph_shouldReturn400_whenNeitherGraphNorDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT missing graph param");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?branch=main",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void putGraph_shouldReturn400_whenCommitSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT on commit");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&commit=" + this.initialCommitId.value(),
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("write_on_readonly_selector");
  }

  @Test
  void putGraph_shouldReturn400_whenAsOfSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PUT on asOf");
    HttpEntity<String> request = new HttpEntity<>(TestConstants.TURTLE_SIMPLE, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&asOf=2025-01-01T00:00:00Z",
        HttpMethod.PUT,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("write_on_readonly_selector");
  }

  // ========== Full System Tests (async event processing) ==========
  // Note: Event publishing and projection not yet implemented
  // Tests below will be enabled when event processing is complete

  // TODO: Re-enable when event projectors are implemented
  // @Test
  // void putGraph_shouldEventuallyUpdateRepository_whenCreatingGraph() {
  //   // Given
  //   HttpHeaders headers = new HttpHeaders();
  //   headers.set("Content-Type", "text/turtle");
  //   headers.set("SPARQL-VC-Author", "Alice");
  //   headers.set("SPARQL-VC-Message", "Create graph via PUT");
  //   HttpEntity<String> request = new HttpEntity<>(TURTLE_CONTENT, headers);
  //
  //   // When
  //   ResponseEntity<String> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       request,
  //       String.class
  //   );
  //
  //   // Then - Wait for async event processing
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  //   String etag = response.getHeaders().getFirst("ETag");
  //   String commitId = etag.replaceAll("\"", "");
  //
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> commitRepository.findByDatasetAndId(DATASET_NAME, new CommitId(commitId))
  //           .isPresent());
  //
  //   // Verify branch updated
  //   Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //       .orElseThrow();
  //   assertThat(branch.getCommitId().value()).isEqualTo(commitId);
  // }

  // TODO: Re-enable when event projectors are implemented
  // @Test
  // void putGraph_shouldReturnNewContent_whenGetAfterPut() {
  //   // Given - PUT new content
  //   HttpHeaders putHeaders = new HttpHeaders();
  //   putHeaders.set("Content-Type", "text/turtle");
  //   putHeaders.set("SPARQL-VC-Author", "Alice");
  //   putHeaders.set("SPARQL-VC-Message", "Create graph");
  //   restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>(TURTLE_CONTENT, putHeaders),
  //       String.class
  //   );
  //
  //   // Wait for event processing
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //           .map(b -> !b.getCommitId().equals(initialCommitId))
  //           .orElse(false));
  //
  //   // When - GET the graph
  //   HttpHeaders getHeaders = new HttpHeaders();
  //   getHeaders.set("Accept", "text/turtle");
  //   ResponseEntity<String> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.GET,
  //       new HttpEntity<>(getHeaders),
  //       String.class
  //   );
  //
  //   // Then
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  //   String body = response.getBody();
  //   assertThat(body).isNotNull();
  //   assertThat(body).contains("http://example.org/subject");
  //   assertThat(body).contains("http://example.org/predicate");
  //   assertThat(body).contains("value");
  // }
}
