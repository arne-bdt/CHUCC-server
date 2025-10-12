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
 * Integration tests for Graph Store Protocol DELETE operation.
 * Tests both API layer (synchronous HTTP response validation) and
 * full system behavior (async event processing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreDeleteIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  // ========== API Layer Tests (synchronous response validation) ==========

  // TODO: DELETE existing graph test requires event processing implementation
  // @Test
  // void deleteGraph_shouldReturn204WithHeaders_whenDeletingExistingGraph() {
  //   // Given - Create initial graph
  //   HttpHeaders putHeaders = new HttpHeaders();
  //   putHeaders.set("Content-Type", "text/turtle");
  //   putHeaders.set("SPARQL-VC-Author", "Alice");
  //   putHeaders.set("SPARQL-VC-Message", "Create graph");
  //   restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>(TURTLE_SIMPLE, putHeaders),
  //       String.class
  //   );
  //
  //   // Wait for event processing
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //           .map(b -> !b.getCommitId().equals(initialCommitId))
  //           .orElse(false));
  //
  //   // When - DELETE the graph
  //   HttpHeaders deleteHeaders = new HttpHeaders();
  //   deleteHeaders.set("SPARQL-VC-Author", "Bob");
  //   deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
  //   HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);
  //
  //   ResponseEntity<Void> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.DELETE,
  //       request,
  //       Void.class
  //   );
  //
  //   // Then - API response verification (synchronous)
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  //   assertThat(response.getHeaders().getFirst("Location")).isNotNull();
  //   assertThat(response.getHeaders().getFirst("Location")).matches("/version/commits/.*");
  //   assertThat(response.getHeaders().getFirst("ETag")).isNotNull();
  //   assertThat(response.getHeaders().getFirst("ETag")).matches("\"[0-9a-f-]+\"");
  //   assertThat(response.getHeaders().getFirst("SPARQL-Version-Control")).isEqualTo("true");
  //
  //   // Note: Repository updates handled by event projectors (async)
  // }

  @Test
  void deleteGraph_shouldReturn404_whenGraphDoesNotExist() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Delete non-existent graph");
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/nonexistent&branch=main",
        HttpMethod.DELETE,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
    assertThat(response.getBody()).contains("graph_not_found");
  }

  // TODO: No-op detection test requires event processing implementation
  // @Test
  // void deleteGraph_shouldReturn204_whenGraphIsAlreadyEmpty() {
  //   // Given - Create empty graph
  //   HttpHeaders putHeaders = new HttpHeaders();
  //   putHeaders.set("Content-Type", "text/turtle");
  //   putHeaders.set("SPARQL-VC-Author", "Alice");
  //   putHeaders.set("SPARQL-VC-Message", "Create empty graph");
  //   restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>("", putHeaders),
  //       String.class
  //   );
  //
  //   // Wait for event processing
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //           .map(b -> !b.getCommitId().equals(initialCommitId))
  //           .orElse(false));
  //
  //   // When - DELETE the empty graph
  //   HttpHeaders deleteHeaders = new HttpHeaders();
  //   deleteHeaders.set("SPARQL-VC-Author", "Bob");
  //   deleteHeaders.set("SPARQL-VC-Message", "Delete empty graph");
  //   HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);
  //
  //   ResponseEntity<Void> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.DELETE,
  //       request,
  //       Void.class
  //   );
  //
  //   // Then - Should return 204 No Content (no-op)
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  // }

  @Test
  void deleteGraph_shouldReturn409_whenIfMatchDoesNotMatch() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Bob");
    headers.set("SPARQL-VC-Message", "Delete with stale ETag (concurrent modification)");
    headers.set("If-Match", "\"01936c7f-8a2e-7890-abcd-ef1234567890\""); // stale ETag
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
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
  void deleteGraph_shouldReturn400_whenBothGraphAndDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "DELETE invalid params");
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/g&default=true&branch=main",
        HttpMethod.DELETE,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void deleteGraph_shouldReturn400_whenNeitherGraphNorDefaultProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "DELETE missing graph param");
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?branch=main",
        HttpMethod.DELETE,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void deleteGraph_shouldReturn400_whenCommitSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "DELETE on commit");
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&commit=" + this.initialCommitId.value(),
        HttpMethod.DELETE,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("write_on_readonly_selector");
  }

  @Test
  void deleteGraph_shouldReturn400_whenAsOfSelectorUsed() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "DELETE on asOf");
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&asOf=2025-01-01T00:00:00Z",
        HttpMethod.DELETE,
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
  // void deleteGraph_shouldEventuallyUpdateRepository_whenDeletingGraph() {
  //   // Given - Create initial graph
  //   HttpHeaders putHeaders = new HttpHeaders();
  //   putHeaders.set("Content-Type", "text/turtle");
  //   putHeaders.set("SPARQL-VC-Author", "Alice");
  //   putHeaders.set("SPARQL-VC-Message", "Create graph");
  //   restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>(TURTLE_SIMPLE, putHeaders),
  //       String.class
  //   );
  //
  //   // Wait for PUT to process
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //           .map(b -> !b.getCommitId().equals(initialCommitId))
  //           .orElse(false));
  //
  //   // When - DELETE the graph
  //   HttpHeaders deleteHeaders = new HttpHeaders();
  //   deleteHeaders.set("SPARQL-VC-Author", "Bob");
  //   deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
  //   HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);
  //
  //   ResponseEntity<Void> response = restTemplate.exchange(
  //       "/data?default=true&branch=main",
  //       HttpMethod.DELETE,
  //       request,
  //       Void.class
  //   );
  //
  //   // Then - Wait for async event processing
  //   assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
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
  // void deleteGraph_shouldReturn404_whenGetAfterDelete() {
  //   // Given - Create graph
  //   HttpHeaders putHeaders = new HttpHeaders();
  //   putHeaders.set("Content-Type", "text/turtle");
  //   putHeaders.set("SPARQL-VC-Author", "Alice");
  //   putHeaders.set("SPARQL-VC-Message", "Create graph");
  //   restTemplate.exchange(
  //       "/data?graph=http://example.org/test&branch=main",
  //       HttpMethod.PUT,
  //       new HttpEntity<>(TURTLE_SIMPLE, putHeaders),
  //       String.class
  //   );
  //
  //   // Wait for PUT to process
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> branchRepository.findByDatasetAndName(DATASET_NAME, "main")
  //           .map(b -> !b.getCommitId().equals(initialCommitId))
  //           .orElse(false));
  //
  //   // DELETE the graph
  //   HttpHeaders deleteHeaders = new HttpHeaders();
  //   deleteHeaders.set("SPARQL-VC-Author", "Bob");
  //   deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
  //   restTemplate.exchange(
  //       "/data?graph=http://example.org/test&branch=main",
  //       HttpMethod.DELETE,
  //       new HttpEntity<>(deleteHeaders),
  //       Void.class
  //   );
  //
  //   // Wait for DELETE to process
  //   await().atMost(Duration.ofSeconds(5))
  //       .until(() -> {
  //         // Try to GET the graph
  //         HttpHeaders getHeaders = new HttpHeaders();
  //         getHeaders.set("Accept", "text/turtle");
  //         ResponseEntity<String> getResponse = restTemplate.exchange(
  //             "/data?graph=http://example.org/test&branch=main",
  //             HttpMethod.GET,
  //             new HttpEntity<>(getHeaders),
  //             String.class
  //         );
  //         // Should return empty graph (all triples deleted)
  //         return getResponse.getStatusCode() == HttpStatus.OK
  //             && getResponse.getBody().trim().isEmpty();
  //       });
  // }
}
