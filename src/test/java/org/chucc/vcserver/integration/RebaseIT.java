package org.chucc.vcserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.rdfpatch.changes.RDFChangesCollector;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /version/rebase endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class RebaseIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";

  private CommitId commitAId;
  private CommitId commitBId;
  private CommitId commitCId;
  private CommitId commitDId;
  private CommitId commitEId;

  @BeforeAll
  static void startKafka() {
    kafkaContainer = KafkaTestContainers.createKafkaContainer();
    // Container is started by KafkaTestContainers - shared across all tests
  }

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
  }

  /**
   * Sets up test data: main branch with A -> B -> E, feature branch with A -> C -> D.
   * Rebase will move feature from A -> C -> D to E -> C' -> D'.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create commit A (common ancestor)
    commitAId = CommitId.generate();
    Commit commitA = new Commit(
        commitAId,
        List.of(),
        "Alice",
        "Initial commit",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitA, RDFPatchOps.emptyPatch());

    // Create commit B on main
    RDFPatch patchB = createPatch("http://example.org/b", "value-b");
    commitBId = CommitId.generate();
    Commit commitB = new Commit(
        commitBId,
        List.of(commitAId),
        "Alice",
        "Main commit B",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitB, patchB);

    // Create commit E on main
    RDFPatch patchE = createPatch("http://example.org/e", "value-e");
    commitEId = CommitId.generate();
    Commit commitE = new Commit(
        commitEId,
        List.of(commitBId),
        "Alice",
        "Main commit E",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitE, patchE);

    // Create main branch pointing to E
    Branch mainBranch = new Branch("main", commitEId);
    branchRepository.save(DATASET_NAME, mainBranch);

    // Create commit C on feature branch
    RDFPatch patchC = createPatch("http://example.org/c", "value-c");
    commitCId = CommitId.generate();
    Commit commitC = new Commit(
        commitCId,
        List.of(commitAId),
        "Bob",
        "Feature commit C",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitC, patchC);

    // Create commit D on feature branch
    RDFPatch patchD = createPatch("http://example.org/d", "value-d");
    commitDId = CommitId.generate();
    Commit commitD = new Commit(
        commitDId,
        List.of(commitCId),
        "Bob",
        "Feature commit D",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitD, patchD);

    // Create feature branch pointing to D
    Branch featureBranch = new Branch("feature", commitDId);
    branchRepository.save(DATASET_NAME, featureBranch);
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
        "/version/rebase?dataset=" + DATASET_NAME,
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

    // Verify feature branch was updated
    Branch featureBranch = branchRepository
        .findByDatasetAndName(DATASET_NAME, "feature")
        .orElseThrow();
    assertThat(featureBranch.getCommitId().value()).isEqualTo(json.get("newHead").asText());

    // CRITICAL: Verify the actual rebase logic worked correctly
    String newHeadId = json.get("newHead").asText();
    String firstRebasedCommitId = json.get("newCommits").get(0).asText();
    String secondRebasedCommitId = json.get("newCommits").get(1).asText();

    // Verify the final commit (D') exists and has correct structure
    Commit finalCommit = commitRepository
        .findByDatasetAndId(DATASET_NAME, new CommitId(newHeadId))
        .orElseThrow(() -> new AssertionError("New head commit not found in repository"));
    assertThat(finalCommit.message()).isEqualTo("Feature commit D");
    assertThat(finalCommit.author()).isEqualTo("Bob");
    assertThat(finalCommit.parents()).hasSize(1);
    assertThat(finalCommit.parents().get(0).value()).isEqualTo(firstRebasedCommitId);

    // Verify the first rebased commit (C') exists and has correct parent
    Commit firstRebasedCommit = commitRepository
        .findByDatasetAndId(DATASET_NAME, new CommitId(firstRebasedCommitId))
        .orElseThrow(() -> new AssertionError("First rebased commit not found in repository"));
    assertThat(firstRebasedCommit.message()).isEqualTo("Feature commit C");
    assertThat(firstRebasedCommit.author()).isEqualTo("Bob");
    assertThat(firstRebasedCommit.parents()).hasSize(1);
    assertThat(firstRebasedCommit.parents().get(0).value())
        .isEqualTo(commitEId.value())
        .describedAs("First rebased commit should have E as parent (the 'onto' commit)");

    // Verify patches were preserved
    RDFPatch rebasedPatchC = commitRepository
        .findPatchByDatasetAndId(DATASET_NAME, new CommitId(firstRebasedCommitId))
        .orElseThrow(() -> new AssertionError("Patch for C' not found"));
    assertThat(rebasedPatchC).isNotNull();

    RDFPatch rebasedPatchD = commitRepository
        .findPatchByDatasetAndId(DATASET_NAME, new CommitId(secondRebasedCommitId))
        .orElseThrow(() -> new AssertionError("Patch for D' not found"));
    assertThat(rebasedPatchD).isNotNull();

    // Verify old commits still exist (rebase creates new commits, doesn't delete old ones)
    assertThat(commitRepository.findByDatasetAndId(DATASET_NAME, commitCId)).isPresent();
    assertThat(commitRepository.findByDatasetAndId(DATASET_NAME, commitDId)).isPresent();
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
        "/version/rebase?dataset=" + DATASET_NAME,
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
}
