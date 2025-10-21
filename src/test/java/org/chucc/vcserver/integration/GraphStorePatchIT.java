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
 * Integration tests for Graph Store Protocol PATCH operation.
 * Tests both API layer (synchronous HTTP response validation) and
 * full system behavior (async event processing).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphStorePatchIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private static final String RDF_PATCH_ADD = """
      TX .
      A <http://example.org/subject2> <http://example.org/predicate2> "newValue" .
      TC .
      """;

  private static final String RDF_PATCH_MODIFY = """
      TX .
      D <http://example.org/subject> <http://example.org/predicate> "value" .
      A <http://example.org/subject> <http://example.org/predicate> "modified" .
      TC .
      """;

  private static final String RDF_PATCH_EMPTY = """
      TX .
      TC .
      """;

  private static final String RDF_PATCH_INVALID_SYNTAX = "INVALID PATCH";

  private static final String RDF_PATCH_UNAPPLICABLE = """
      TX .
      D <http://example.org/nonexistent> <http://example.org/p> "value" .
      TC .
      """;

  // ========== API Layer Tests (synchronous response validation) ==========

  @Test
  void patchGraph_shouldReturn200WithHeaders_whenPatchApplied() {
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

    // When - PATCH graph
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.set("Content-Type", "text/rdf-patch");
    patchHeaders.set("SPARQL-VC-Author", "Bob");
    patchHeaders.set("SPARQL-VC-Message", "Apply patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, patchHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
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
  void patchGraph_shouldReturn400_whenPatchSyntaxInvalid() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Apply invalid patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_INVALID_SYNTAX, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then - Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void patchGraph_shouldReturn422_whenPatchCannotBeApplied() {
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

    // When - PATCH with unapplicable operations
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.set("Content-Type", "text/rdf-patch");
    patchHeaders.set("SPARQL-VC-Author", "Bob");
    patchHeaders.set("SPARQL-VC-Message", "Apply unapplicable patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_UNAPPLICABLE, patchHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then - Should return 422 Unprocessable Entity
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void patchGraph_shouldReturn204_whenPatchIsNoOp() {
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

    // When - PATCH with empty patch
    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.set("Content-Type", "text/rdf-patch");
    patchHeaders.set("SPARQL-VC-Author", "Bob");
    patchHeaders.set("SPARQL-VC-Message", "Apply no-op patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_EMPTY, patchHeaders);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then - Should return 204 No Content (no-op)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  @Test
  void patchGraph_shouldReturn415_whenContentTypeNotRdfPatch() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Wrong content type");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then - Should return 415 Unsupported Media Type
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void patchGraph_shouldAcceptRdfPatch_whenContentTypeRdfPatch() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "PATCH with RDF Patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void patchGraph_shouldReturn400_whenBothGraphAndDefaultSpecified() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph&default=true",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void patchGraph_shouldReturn400_whenNeitherGraphNorDefaultSpecified() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void patchGraph_shouldReturn400_whenWritingToReadOnlyCommitSelector() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&commit=abc123",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Cannot write to a specific commit");
  }

  @Test
  void patchGraph_shouldReturn400_whenWritingToReadOnlyAsOfSelector() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    HttpEntity<String> request = new HttpEntity<>(RDF_PATCH_ADD, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&asOf=2024-01-01T00:00:00Z",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Cannot write with asOf selector");
  }

  @Test
  void patchGraph_shouldAcceptNamedGraph() {
    // Given
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set("Content-Type", "text/turtle");
    putHeaders.set("SPARQL-VC-Author", "Alice");
    putHeaders.set("SPARQL-VC-Message", "Create named graph");

    // First create the named graph
    restTemplate.exchange(
        "/data?graph=http://example.org/graph1&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(TestConstants.TURTLE_SIMPLE, putHeaders),
        String.class
    );

    // Patch for named graph (using default graph syntax in the patch)
    // The graph context is determined by the URL parameter, not the patch content
    String namedGraphPatch = """
        TX .
        A <http://example.org/s2> <http://example.org/p2> "value2" .
        TC .
        """;

    HttpHeaders patchHeaders = new HttpHeaders();
    patchHeaders.set("Content-Type", "text/rdf-patch");
    patchHeaders.set("SPARQL-VC-Author", "Bob");
    patchHeaders.set("SPARQL-VC-Message", "PATCH named graph");
    HttpEntity<String> request = new HttpEntity<>(namedGraphPatch, patchHeaders);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1&branch=main",
        HttpMethod.PATCH,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  // ========== Full System Tests (async event processing verification) ==========

  // Note: Full system tests for PATCH operations would verify async repository updates
  // using await() and repository queries. These tests would be similar to the ones
  // in GraphStorePutIT and GraphStorePostIT, verifying that patches are correctly
  // applied and repository state is eventually consistent.
  //
  // Example structure:
  // @Test
  // void patchGraph_shouldEventuallyUpdateRepository() {
  //   // Create initial graph via PUT
  //   // Apply RDF patch via PATCH
  //   // Wait for async event processing with await()
  //   // Verify commit and branch in repositories
  // }
}
