package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.chucc.vcserver.domain.Branch;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for concurrent graph operations.
 * Verifies conflict detection when multiple clients attempt to modify the same graph
 * using If-Match headers for optimistic concurrency control.
 *
 * <p><strong>Note:</strong> This test explicitly enables the ReadModelProjector
 * via {@code @TestPropertySource(properties = "projector.kafka-listener.enabled=true")}
 * because tests use {@code await()} to wait for branch HEAD updates from async event
 * processing. This is required to test full concurrent operation scenarios.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>PUT operations detect concurrent writes (409 Conflict)</li>
 *   <li>POST operations detect concurrent writes (409 Conflict)</li>
 *   <li>PATCH operations detect concurrent writes (409 Conflict)</li>
 *   <li>DELETE operations detect concurrent writes (409 Conflict)</li>
 *   <li>RFC 7807 problem+json responses for conflicts</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class ConcurrentGraphOperationsIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private static final String GRAPH_IRI = "http://example.org/graph1";

  @Test
  void putGraph_shouldDetectConcurrentWrites() {
    // Step 1: Create initial graph state
    String initialData = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"initial\" .";
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create initial graph");

    HttpEntity<String> request1 = new HttpEntity<>(initialData, headers1);
    ResponseEntity<String> response1 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request1,
        String.class
    );

    if (response1.getStatusCode() != HttpStatus.ACCEPTED) {
      System.out.println("Error response body: " + response1.getBody());
    }
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag1 = response1.getHeaders().getFirst("ETag");
    assertThat(etag1).isNotNull();

    // Wait for async event processing to update branch HEAD
    final String expectedEtag1 = etag1;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag1.replace("\"", ""));
        });

    // Step 2: Make first concurrent modification (advances HEAD)
    String updatedData1 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"updated1\" .";
    HttpHeaders headers2 = new HttpHeaders();
    headers2.set("Content-Type", "text/turtle");
    headers2.set("SPARQL-VC-Author", "Bob");
    headers2.set("SPARQL-VC-Message", "First concurrent update");
    headers2.set("If-Match", etag1);

    HttpEntity<String> request2 = new HttpEntity<>(updatedData1, headers2);
    ResponseEntity<String> response2 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request2,
        String.class
    );

    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag2 = response2.getHeaders().getFirst("ETag");

    // Wait for async event processing to update branch HEAD
    final String expectedEtag2 = etag2;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag2.replace("\"", ""));
        });

    // Step 3: Make second concurrent modification using old ETag (should conflict)
    String updatedData2 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"updated2\" .";
    HttpHeaders headers3 = new HttpHeaders();
    headers3.set("Content-Type", "text/turtle");
    headers3.set("SPARQL-VC-Author", "Charlie");
    headers3.set("SPARQL-VC-Message", "Second concurrent update");
    headers3.set("If-Match", etag1); // Using old ETag

    HttpEntity<String> request3 = new HttpEntity<>(updatedData2, headers3);
    ResponseEntity<String> response3 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request3,
        String.class
    );

    // Assert: Conflict detected
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response3.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void postGraph_shouldDetectConcurrentWrites() {
    // Step 1: Create initial graph state
    String initialData = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"initial\" .";
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create initial graph");

    HttpEntity<String> request1 = new HttpEntity<>(initialData, headers1);
    ResponseEntity<String> response1 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request1,
        String.class
    );

    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag1 = response1.getHeaders().getFirst("ETag");
    assertThat(etag1).isNotNull();

    // Wait for async event processing
    final String expectedEtag1Post = etag1;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag1Post.replace("\"", ""));
        });

    // Step 2: Make first concurrent POST (advances HEAD)
    String additionalData1 = "@prefix ex: <http://example.org/> . ex:s2 ex:p2 \"added1\" .";
    HttpHeaders headers2 = new HttpHeaders();
    headers2.set("Content-Type", "text/turtle");
    headers2.set("SPARQL-VC-Author", "Bob");
    headers2.set("SPARQL-VC-Message", "First concurrent merge");
    headers2.set("If-Match", etag1);

    HttpEntity<String> request2 = new HttpEntity<>(additionalData1, headers2);
    ResponseEntity<String> response2 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.POST,
        request2,
        String.class
    );

    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag2Post = response2.getHeaders().getFirst("ETag");

    // Wait for async event processing
    final String expectedEtag2Post = etag2Post;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag2Post.replace("\"", ""));
        });

    // Step 3: Make second concurrent POST using old ETag (should conflict)
    String additionalData2 = "@prefix ex: <http://example.org/> . ex:s3 ex:p3 \"added2\" .";
    HttpHeaders headers3 = new HttpHeaders();
    headers3.set("Content-Type", "text/turtle");
    headers3.set("SPARQL-VC-Author", "Charlie");
    headers3.set("SPARQL-VC-Message", "Second concurrent merge");
    headers3.set("If-Match", etag1); // Using old ETag

    HttpEntity<String> request3 = new HttpEntity<>(additionalData2, headers3);
    ResponseEntity<String> response3 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.POST,
        request3,
        String.class
    );

    // Assert: Conflict detected
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response3.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void patchGraph_shouldDetectConcurrentWrites() {
    // Step 1: Create initial graph state
    String initialData = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"initial\" .";
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create initial graph");

    HttpEntity<String> request1 = new HttpEntity<>(initialData, headers1);
    ResponseEntity<String> response1 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request1,
        String.class
    );

    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag1 = response1.getHeaders().getFirst("ETag");
    assertThat(etag1).isNotNull();

    // Wait for async event processing
    final String expectedEtag1Patch = etag1;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag1Patch.replace("\"", ""));
        });

    // Step 2: Make first concurrent PATCH (advances HEAD)
    // Use triple format (no graph specified) since endpoint specifies the target graph
    String patch1 = """
        TX .
        A <http://example.org/s2> <http://example.org/p2> "patched1" .
        TC .
        """;
    HttpHeaders headers2 = new HttpHeaders();
    headers2.set("Content-Type", "text/rdf-patch");
    headers2.set("SPARQL-VC-Author", "Bob");
    headers2.set("SPARQL-VC-Message", "First concurrent patch");
    headers2.set("If-Match", etag1);

    HttpEntity<String> request2 = new HttpEntity<>(patch1, headers2);
    ResponseEntity<String> response2 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PATCH,
        request2,
        String.class
    );

    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag2Patch = response2.getHeaders().getFirst("ETag");

    // Wait for async event processing
    final String expectedEtag2Patch = etag2Patch;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag2Patch.replace("\"", ""));
        });

    // Step 3: Make second concurrent PATCH using old ETag (should conflict)
    // Use triple format (no graph specified) since endpoint specifies the target graph
    String patch2 = """
        TX .
        A <http://example.org/s3> <http://example.org/p3> "patched2" .
        TC .
        """;
    HttpHeaders headers3 = new HttpHeaders();
    headers3.set("Content-Type", "text/rdf-patch");
    headers3.set("SPARQL-VC-Author", "Charlie");
    headers3.set("SPARQL-VC-Message", "Second concurrent patch");
    headers3.set("If-Match", etag1); // Using old ETag

    HttpEntity<String> request3 = new HttpEntity<>(patch2, headers3);
    ResponseEntity<String> response3 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PATCH,
        request3,
        String.class
    );

    // Assert: Conflict detected
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response3.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void deleteGraph_shouldDetectConcurrentWrites() {
    // Step 1: Create initial graph state
    String initialData = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"initial\" .";
    HttpHeaders headers1 = new HttpHeaders();
    headers1.set("Content-Type", "text/turtle");
    headers1.set("SPARQL-VC-Author", "Alice");
    headers1.set("SPARQL-VC-Message", "Create initial graph");

    HttpEntity<String> request1 = new HttpEntity<>(initialData, headers1);
    ResponseEntity<String> response1 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request1,
        String.class
    );

    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag1 = response1.getHeaders().getFirst("ETag");
    assertThat(etag1).isNotNull();

    // Wait for async event processing
    final String expectedEtag1Delete = etag1;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag1Delete.replace("\"", ""));
        });

    // Step 2: Make first concurrent modification (advances HEAD)
    String updatedData = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 \"updated\" .";
    HttpHeaders headers2 = new HttpHeaders();
    headers2.set("Content-Type", "text/turtle");
    headers2.set("SPARQL-VC-Author", "Bob");
    headers2.set("SPARQL-VC-Message", "Update graph");
    headers2.set("If-Match", etag1);

    HttpEntity<String> request2 = new HttpEntity<>(updatedData, headers2);
    ResponseEntity<String> response2 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.PUT,
        request2,
        String.class
    );

    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    String etag2Delete = response2.getHeaders().getFirst("ETag");

    // Wait for async event processing
    final String expectedEtag2Delete = etag2Delete;
    await().atMost(Duration.ofSeconds(5))
        .until(() -> {
          Branch branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main")
              .orElse(null);
          return branch != null
              && branch.getCommitId().value().equals(expectedEtag2Delete.replace("\"", ""));
        });

    // Step 3: Try to delete using old ETag (should conflict)
    HttpHeaders headers3 = new HttpHeaders();
    headers3.set("SPARQL-VC-Author", "Charlie");
    headers3.set("SPARQL-VC-Message", "Delete graph");
    headers3.set("If-Match", etag1); // Using old ETag

    HttpEntity<String> request3 = new HttpEntity<>(null, headers3);
    ResponseEntity<String> response3 = restTemplate.exchange(
        "/data?graph=" + GRAPH_IRI + "&branch=main",
        HttpMethod.DELETE,
        request3,
        String.class
    );

    // Assert: Conflict detected
    assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response3.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }
}
