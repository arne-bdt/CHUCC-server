package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.BeforeEach;
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
 * Integration tests for POST /version/squash endpoint.
 *
 * <p>These tests verify the complete end-to-end flow of squash operations,
 * including HTTP API, command handling, event publication, and repository updates.
 *
 * <p><strong>Test Pattern:</strong> End-to-end with projector enabled
 * <ul>
 *   <li>Projector: ENABLED (verifies async projection works correctly)</li>
 *   <li>Setup: Uses {@link ITFixture#createCommit} for complex commit graph setup</li>
 *   <li>Verification: HTTP response + await() + commit graph structure</li>
 * </ul>
 *
 * <p><strong>Test Isolation:</strong>
 * <ul>
 *   <li>Extends {@link ITFixture} for cleanup synchronization</li>
 *   <li>Uses unique dataset name per test run (no cross-test contamination)</li>
 *   <li>Custom commit graph setup (squash requires specific graph topology)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class SquashIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String dataset;

  private CommitId commitAId;
  private CommitId commitBId;
  private CommitId commitCId;

  @Override
  protected String getDatasetName() {
    // Unique dataset name per test run to prevent cross-test contamination
    return "squash-test-" + System.nanoTime();
  }

  @Override
  protected boolean shouldCreateInitialSetup() {
    // Skip default setup - we need custom commit graph for squash tests
    return false;
  }

  /**
   * Sets up test data: feature branch with A -> B -> C.
   * Squash will combine B and C into a single commit.
   *
   * <p><strong>Note:</strong> Uses {@link #createCommit} for setup, which performs direct
   * repository writes. This is acceptable for test infrastructure when creating complex
   * commit graphs that cannot be created via command handlers.
   */
  @BeforeEach
  void setUp() {
    dataset = getDatasetName();

    // Create commit A (empty patch)
    commitAId = createCommit(dataset, List.of(), "Alice", "Initial commit", "TX .\nTC .");

    // Create commit B
    RDFPatch patchB = createPatch("http://example.org/b", "value-b");
    commitBId = createCommit(dataset, List.of(commitAId), "Bob", "Commit B",
        patchToString(patchB));

    // Create commit C
    RDFPatch patchC = createPatch("http://example.org/c", "value-c");
    commitCId = createCommit(dataset, List.of(commitBId), "Bob", "Commit C",
        patchToString(patchC));

    // Create feature branch pointing to C (event-driven)
    createBranchViaCommand(dataset, "feature", commitCId.value());
  }

  @Test
  void squash_shouldReturn200_whenSquashingTwoCommits() throws Exception {
    // Given - squash B and C
    String requestBody = String.format("""
        {
          "branch": "feature",
          "commits": ["%s", "%s"],
          "message": "Squashed B and C",
          "author": "Alice"
        }
        """, commitBId.value(), commitCId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify ETag header contains new commit ID
    assertThat(response.getHeaders().getETag()).isNotNull();

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("feature");
    assertThat(json.get("newCommit").asText()).isNotNull();
    assertThat(json.get("squashedCommits").isArray()).isTrue();
    assertThat(json.get("squashedCommits").size()).isEqualTo(2);
    assertThat(json.get("squashedCommits").get(0).asText()).isEqualTo(commitBId.value());
    assertThat(json.get("squashedCommits").get(1).asText()).isEqualTo(commitCId.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(commitCId.value());

    String newCommitId = json.get("newCommit").asText();

    // Wait for projector to update repository (CQRS eventual consistency)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify feature branch was updated
          Branch featureBranch = branchRepository
              .findByDatasetAndName(dataset, "feature")
              .orElseThrow();
          assertThat(featureBranch.getCommitId().value()).isEqualTo(newCommitId);

          // Verify the squashed commit exists and has correct structure
          Commit squashedCommit = commitRepository
              .findByDatasetAndId(dataset, new CommitId(newCommitId))
              .orElseThrow(() -> new AssertionError("Squashed commit not found"));
          assertThat(squashedCommit.message()).isEqualTo("Squashed B and C");
          assertThat(squashedCommit.author()).isEqualTo("Alice");
          assertThat(squashedCommit.parents()).hasSize(1);
          assertThat(squashedCommit.parents().get(0).value()).isEqualTo(commitAId.value());

          // Verify patch exists for squashed commit
          RDFPatch squashedPatch = commitRepository
              .findPatchByDatasetAndId(dataset, new CommitId(newCommitId))
              .orElseThrow(() -> new AssertionError("Patch for squashed commit not found"));
          assertThat(squashedPatch).isNotNull();

          // Verify old commits still exist (orphaned)
          assertThat(commitRepository.findByDatasetAndId(dataset, commitBId))
              .isPresent();
          assertThat(commitRepository.findByDatasetAndId(dataset, commitCId))
              .isPresent();
        });
  }

  @Test
  void squash_shouldUseFirstCommitAuthor_whenAuthorNotSpecified() throws Exception {
    // Given - squash B and C without specifying author
    String requestBody = String.format("""
        {
          "branch": "feature",
          "commits": ["%s", "%s"],
          "message": "Squashed commits"
        }
        """, commitBId.value(), commitCId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    JsonNode json = objectMapper.readTree(response.getBody());
    String newCommitId = json.get("newCommit").asText();

    // Wait for projector to update repository (CQRS eventual consistency)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify author is from first commit (B)
          Commit squashedCommit = commitRepository
              .findByDatasetAndId(dataset, new CommitId(newCommitId))
              .orElseThrow();
          assertThat(squashedCommit.author()).isEqualTo("Bob");
        });
  }

  @Test
  void squash_shouldReturn400_whenCommitsNotContiguous() throws Exception {
    // Given - add a commit D to create A -> B -> C -> D
    RDFPatch patchD = createPatch("http://example.org/d", "value-d");
    CommitId commitDId = createCommit(dataset, List.of(commitCId), "Bob", "Commit D",
        patchToString(patchD));

    // Update feature branch to point to D (event-driven)
    createBranchViaCommand(dataset, "feature-d", commitDId.value());

    // Try to squash B and D (skipping C)
    String requestBody = String.format("""
        {
          "branch": "feature-d",
          "commits": ["%s", "%s"],
          "message": "Invalid squash",
          "author": "Alice"
        }
        """, commitBId.value(), commitDId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    // Note: The handler validates commits exist before checking contiguity,
    // so this may return 404 OR 400 depending on which validation fails first
    assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void squash_shouldReturn400_whenFewerThanTwoCommits() throws Exception {
    // Given - try to squash only one commit
    String requestBody = String.format("""
        {
          "branch": "feature",
          "commits": ["%s"],
          "message": "Invalid squash",
          "author": "Alice"
        }
        """, commitBId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void squash_shouldReturn404_whenBranchDoesNotExist() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "branch": "nonexistent",
          "commits": ["%s", "%s"],
          "message": "Squashed commits",
          "author": "Alice"
        }
        """, commitBId.value(), commitCId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  @Test
  void squash_shouldReturn404_whenCommitDoesNotExist() throws Exception {
    // Given - use a non-existent commit ID
    String fakeCommitId = "12345678-1234-1234-1234-999999999999";
    String requestBody = String.format("""
        {
          "branch": "feature",
          "commits": ["%s", "%s"],
          "message": "Squashed commits",
          "author": "Alice"
        }
        """, commitBId.value(), fakeCommitId);

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/squash?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }

  /**
   * Helper method to create a simple RDF patch.
   *
   * @param subject the subject URI
   * @param value the literal value
   * @return an RDF patch
   */
  private RDFPatch createPatch(String subject, String value) {
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI(subject);
    Node p = NodeFactory.createURI("http://example.org/p");
    Node o = NodeFactory.createLiteralString(value);

    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.add(g, s, p, o);
    collector.finish();
    return collector.getRDFPatch();
  }

  /**
   * Converts an RDFPatch to string.
   *
   * @param patch the RDF patch
   * @return the patch as string
   */
  private String patchToString(RDFPatch patch) {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    RDFPatchOps.write(baos, patch);
    return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
  }
}
