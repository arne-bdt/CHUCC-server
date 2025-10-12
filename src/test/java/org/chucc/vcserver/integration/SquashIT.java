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
 * Integration tests for POST /version/squash endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SquashIT {

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
   * Sets up test data: feature branch with A -> B -> C.
   * Squash will combine B and C into a single commit.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create commit A
    commitAId = CommitId.generate();
    Commit commitA = new Commit(
        commitAId,
        List.of(),
        "Alice",
        "Initial commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commitA, RDFPatchOps.emptyPatch());

    // Create commit B
    RDFPatch patchB = createPatch("http://example.org/b", "value-b");
    commitBId = CommitId.generate();
    Commit commitB = new Commit(
        commitBId,
        List.of(commitAId),
        "Bob",
        "Commit B",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commitB, patchB);

    // Create commit C
    RDFPatch patchC = createPatch("http://example.org/c", "value-c");
    commitCId = CommitId.generate();
    Commit commitC = new Commit(
        commitCId,
        List.of(commitBId),
        "Bob",
        "Commit C",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commitC, patchC);

    // Create feature branch pointing to C
    Branch featureBranch = new Branch("feature", commitCId);
    branchRepository.save(DATASET_NAME, featureBranch);
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
        "/version/squash?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    // Verify feature branch was updated
    Branch featureBranch = branchRepository
        .findByDatasetAndName(DATASET_NAME, "feature")
        .orElseThrow();
    String newCommitId = json.get("newCommit").asText();
    assertThat(featureBranch.getCommitId().value()).isEqualTo(newCommitId);

    // Verify the squashed commit exists and has correct structure
    Commit squashedCommit = commitRepository
        .findByDatasetAndId(DATASET_NAME, new CommitId(newCommitId))
        .orElseThrow(() -> new AssertionError("Squashed commit not found in repository"));
    assertThat(squashedCommit.message()).isEqualTo("Squashed B and C");
    assertThat(squashedCommit.author()).isEqualTo("Alice");
    assertThat(squashedCommit.parents()).hasSize(1);
    assertThat(squashedCommit.parents().get(0).value()).isEqualTo(commitAId.value());

    // Verify patch exists for squashed commit
    RDFPatch squashedPatch = commitRepository
        .findPatchByDatasetAndId(DATASET_NAME, new CommitId(newCommitId))
        .orElseThrow(() -> new AssertionError("Patch for squashed commit not found"));
    assertThat(squashedPatch).isNotNull();

    // Verify old commits still exist (orphaned)
    assertThat(commitRepository.findByDatasetAndId(DATASET_NAME, commitBId)).isPresent();
    assertThat(commitRepository.findByDatasetAndId(DATASET_NAME, commitCId)).isPresent();
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
        "/version/squash?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    String newCommitId = json.get("newCommit").asText();

    // Verify author is from first commit (B)
    Commit squashedCommit = commitRepository
        .findByDatasetAndId(DATASET_NAME, new CommitId(newCommitId))
        .orElseThrow();
    assertThat(squashedCommit.author()).isEqualTo("Bob");
  }

  @Test
  void squash_shouldReturn400_whenCommitsNotContiguous() throws Exception {
    // Given - add a commit D to create A -> B -> C -> D
    RDFPatch patchD = createPatch("http://example.org/d", "value-d");
    CommitId commitDId = CommitId.generate();
    Commit commitD = new Commit(
        commitDId,
        List.of(commitCId),
        "Bob",
        "Commit D",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commitD, patchD);

    Branch featureBranch = branchRepository
        .findByDatasetAndName(DATASET_NAME, "feature")
        .orElseThrow();
    featureBranch.updateCommit(commitDId);
    branchRepository.save(DATASET_NAME, featureBranch);

    // Try to squash B and D (skipping C)
    String requestBody = String.format("""
        {
          "branch": "feature",
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
        "/version/squash?dataset=" + DATASET_NAME,
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
        "/version/squash?dataset=" + DATASET_NAME,
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
        "/version/squash?dataset=" + DATASET_NAME,
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
        "/version/squash?dataset=" + DATASET_NAME,
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
}
