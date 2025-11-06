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
 * Integration tests for POST /version/rebase endpoint.
 *
 * <p>These tests verify the complete end-to-end flow of rebase operations,
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
 *   <li>Custom commit graph setup (rebase requires specific graph topology)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class RebaseIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private String dataset;

  private CommitId commitAId;
  private CommitId commitBId;
  private CommitId commitCId;
  private CommitId commitDId;
  private CommitId commitEId;

  @Override
  protected String getDatasetName() {
    // Unique dataset name per test run to prevent cross-test contamination
    return "rebase-test-" + System.nanoTime();
  }

  @Override
  protected boolean shouldCreateInitialSetup() {
    // Skip default setup - we need custom commit graph for rebase tests
    return false;
  }

  /**
   * Sets up test data: main branch with A -> B -> E, feature branch with A -> C -> D.
   * Rebase will move feature from A -> C -> D to E -> C' -> D'.
   *
   * <p><strong>Note:</strong> Uses {@link #createCommit} for setup, which performs direct
   * repository writes. This is acceptable for test infrastructure when creating complex
   * commit graphs that cannot be created via command handlers.
   */
  @BeforeEach
  void setUp() {
    dataset = getDatasetName();

    // Create commit A (common ancestor) - empty patch
    commitAId = createCommit(dataset, List.of(), "Alice", "Initial commit", "TX .\nTC .");

    // Create commit B on main
    RDFPatch patchB = createPatch("http://example.org/b", "value-b");
    commitBId = createCommit(dataset, List.of(commitAId), "Alice", "Main commit B",
        patchToString(patchB));

    // Create commit E on main
    RDFPatch patchE = createPatch("http://example.org/e", "value-e");
    commitEId = createCommit(dataset, List.of(commitBId), "Alice", "Main commit E",
        patchToString(patchE));

    // Create main branch pointing to E (event-driven)
    createBranchViaCommand(dataset, "main", commitEId.value());

    // Create commit C on feature branch
    RDFPatch patchC = createPatch("http://example.org/c", "value-c");
    commitCId = createCommit(dataset, List.of(commitAId), "Bob", "Feature commit C",
        patchToString(patchC));

    // Create commit D on feature branch
    RDFPatch patchD = createPatch("http://example.org/d", "value-d");
    commitDId = createCommit(dataset, List.of(commitCId), "Bob", "Feature commit D",
        patchToString(patchD));

    // Create feature branch pointing to D (event-driven)
    createBranchViaCommand(dataset, "feature", commitDId.value());
  }

  @Test
  void rebase_shouldReturn200_whenSuccessful() throws Exception {
    // Given - rebase feature onto main (from A)
    String requestBody = String.format("""
        {
          "branch": "feature",
          "onto": "%s",
          "from": "%s"
        }
        """, commitEId.value(), commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("feature");
    assertThat(json.get("newHead").asText()).isNotNull();
    assertThat(json.get("newCommits").isArray()).isTrue();
    assertThat(json.get("newCommits").size()).isEqualTo(2); // C' and D'
    assertThat(json.get("rebasedCount").asInt()).isEqualTo(2);

    // CRITICAL: Verify the actual rebase logic worked correctly
    String newHeadId = json.get("newHead").asText();
    String firstRebasedCommitId = json.get("newCommits").get(0).asText();
    String secondRebasedCommitId = json.get("newCommits").get(1).asText();

    // Wait for projector to update repository (CQRS eventual consistency)
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify feature branch was updated
          Branch featureBranch = branchRepository
              .findByDatasetAndName(dataset, "feature")
              .orElseThrow();
          assertThat(featureBranch.getCommitId().value())
              .isEqualTo(json.get("newHead").asText());

          // Verify the final commit (D') exists and has correct structure
          Commit finalCommit = commitRepository
              .findByDatasetAndId(dataset, new CommitId(newHeadId))
              .orElseThrow(() -> new AssertionError("New head commit not found"));
          assertThat(finalCommit.message()).isEqualTo("Feature commit D");
          assertThat(finalCommit.author()).isEqualTo("Bob");
          assertThat(finalCommit.parents()).hasSize(1);
          assertThat(finalCommit.parents().get(0).value()).isEqualTo(firstRebasedCommitId);

          // Verify the first rebased commit (C') exists and has correct parent
          Commit firstRebasedCommit = commitRepository
              .findByDatasetAndId(dataset, new CommitId(firstRebasedCommitId))
              .orElseThrow(() -> new AssertionError("First rebased commit not found"));
          assertThat(firstRebasedCommit.message()).isEqualTo("Feature commit C");
          assertThat(firstRebasedCommit.author()).isEqualTo("Bob");
          assertThat(firstRebasedCommit.parents()).hasSize(1);
          assertThat(firstRebasedCommit.parents().get(0).value())
              .isEqualTo(commitEId.value())
              .describedAs("First rebased commit should have E as parent");

          // Verify patches were preserved
          RDFPatch rebasedPatchC = commitRepository
              .findPatchByDatasetAndId(dataset, new CommitId(firstRebasedCommitId))
              .orElseThrow(() -> new AssertionError("Patch for C' not found"));
          assertThat(rebasedPatchC).isNotNull();

          RDFPatch rebasedPatchD = commitRepository
              .findPatchByDatasetAndId(dataset, new CommitId(secondRebasedCommitId))
              .orElseThrow(() -> new AssertionError("Patch for D' not found"));
          assertThat(rebasedPatchD).isNotNull();

          // Verify old commits still exist (rebase creates new commits)
          assertThat(commitRepository.findByDatasetAndId(dataset, commitCId))
              .isPresent();
          assertThat(commitRepository.findByDatasetAndId(dataset, commitDId))
              .isPresent();
        });
  }

  @Test
  void rebase_shouldRebaseBranchOntoAnotherBranch() throws Exception {
    // Given - rebase feature onto "main" (using branch name)
    String requestBody = String.format("""
        {
          "branch": "feature",
          "onto": "main",
          "from": "%s"
        }
        """, commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("rebasedCount").asInt()).isEqualTo(2);
  }

  @Test
  void rebase_shouldReturn400_whenBranchMissing() {
    // Given
    String requestBody = String.format("""
        {
          "onto": "%s",
          "from": "%s"
        }
        """, commitEId.value(), commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
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
  void rebase_shouldReturn400_whenOntoMissing() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "feature",
          "from": "%s"
        }
        """, commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
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
  void rebase_shouldReturn400_whenFromMissing() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "feature",
          "onto": "%s"
        }
        """, commitEId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
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
  void rebase_shouldReturn404_whenBranchNotFound() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "nonexistent",
          "onto": "%s",
          "from": "%s"
        }
        """, commitEId.value(), commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
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
  void rebase_shouldReturn400_whenAuthorMissing() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "feature",
          "onto": "%s",
          "from": "%s"
        }
        """, commitEId.value(), commitAId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    // No SPARQL-VC-Author header

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/rebase?dataset=" + dataset,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
