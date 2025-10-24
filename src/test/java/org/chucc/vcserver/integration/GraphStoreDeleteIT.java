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
 * Integration tests for Graph Store Protocol DELETE operation.
 * Tests both API layer (synchronous HTTP response validation) and
 * full system behavior (async event processing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphStoreDeleteIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  // ========== API Layer Tests (synchronous response validation) ==========

  @Test
  void deleteGraph_shouldReturn204WithHeaders_whenDeletingExistingGraph() {
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

    // When - DELETE the graph
    HttpHeaders deleteHeaders = new HttpHeaders();
    deleteHeaders.set("SPARQL-VC-Author", "Bob");
    deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
    HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);

    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        request,
        Void.class
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

  @Test
  void deleteGraph_shouldReturn204_whenGraphIsAlreadyEmpty() {
    // Given - Create graph with content, then delete it to make it empty
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

    // Wait for PUT to process
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
            .map(b -> !b.getCommitId().equals(initialCommitId))
            .orElse(false));

    // DELETE to make graph empty
    HttpHeaders firstDeleteHeaders = new HttpHeaders();
    firstDeleteHeaders.set("SPARQL-VC-Author", "Alice");
    firstDeleteHeaders.set("SPARQL-VC-Message", "Make graph empty");
    ResponseEntity<Void> firstDeleteResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        new HttpEntity<>(firstDeleteHeaders),
        Void.class
    );
    String firstDeleteCommitId = firstDeleteResponse.getHeaders().getFirst("ETag")
        .replaceAll("\"", "");

    // Wait for first DELETE to process
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET,
            new CommitId(firstDeleteCommitId)).isPresent());

    // When - DELETE the already-empty graph (no-op)
    HttpHeaders deleteHeaders = new HttpHeaders();
    deleteHeaders.set("SPARQL-VC-Author", "Bob");
    deleteHeaders.set("SPARQL-VC-Message", "Delete empty graph");
    HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);

    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        request,
        Void.class
    );

    // Then - Should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

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

  @Test
  void deleteGraph_shouldEventuallyUpdateRepository_whenDeletingGraph() {
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

    // Wait for PUT to process
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
            .map(b -> !b.getCommitId().equals(initialCommitId))
            .orElse(false));

    // When - DELETE the graph
    HttpHeaders deleteHeaders = new HttpHeaders();
    deleteHeaders.set("SPARQL-VC-Author", "Bob");
    deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
    HttpEntity<Void> request = new HttpEntity<>(deleteHeaders);

    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        request,
        Void.class
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
  void deleteGraph_shouldReturnEmptyGraph_whenGetAfterDelete() {
    // Given - Create graph on default graph
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

    // Wait for PUT to process
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
            .map(b -> !b.getCommitId().equals(initialCommitId))
            .orElse(false));

    // DELETE the graph
    HttpHeaders deleteHeaders = new HttpHeaders();
    deleteHeaders.set("SPARQL-VC-Author", "Bob");
    deleteHeaders.set("SPARQL-VC-Message", "Delete graph");
    ResponseEntity<Void> deleteResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        new HttpEntity<>(deleteHeaders),
        Void.class
    );

    // Wait for DELETE to process
    String deleteCommitId = deleteResponse.getHeaders().getFirst("ETag")
        .replaceAll("\"", "");
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET,
            new CommitId(deleteCommitId)).isPresent());

    // When - GET the graph after deletion
    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.set("Accept", "text/turtle");
    ResponseEntity<String> getResponse = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.GET,
        new HttpEntity<>(getHeaders),
        String.class
    );

    // Then - Should return empty graph (all triples deleted)
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = getResponse.getBody();
    assertThat(body == null || body.trim().isEmpty()).isTrue();
  }
}
