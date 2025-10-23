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
 * Integration tests for POST /version/cherry-pick endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CherryPickIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";

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

  private CommitId branchACommit1Id;
  private CommitId branchACommit2Id;
  private CommitId branchBCommit1Id;
  private CommitId branchBCommit2Id;

  /**
   * Sets up two branches with different commit histories for cherry-pick testing.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create branch A with commits: commit1 -> commit2
    branchACommit1Id = CommitId.generate();
    Commit branchACommit1 = new Commit(
        branchACommit1Id,
        List.of(),
        "Alice",
        "Branch A - Initial commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, branchACommit1,
        RDFPatchOps.emptyPatch());

    // Create a non-empty patch for branchACommit2
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI("http://example.org/alice");
    Node p = NodeFactory.createURI("http://example.org/name");
    Node o = NodeFactory.createLiteralString("Alice");
    collector.add(g, s, p, o);
    collector.finish();
    RDFPatch branchACommit2Patch = collector.getRDFPatch();

    branchACommit2Id = CommitId.generate();
    Commit branchACommit2 = new Commit(
        branchACommit2Id,
        List.of(branchACommit1Id),
        "Alice",
        "Branch A - Add Alice data",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, branchACommit2, branchACommit2Patch);

    Branch branchA = new Branch("branch-a", branchACommit2Id);
    branchRepository.save(DATASET_NAME, branchA);

    // Create branch B with commits: commit1 -> commit2 (different from A)
    branchBCommit1Id = CommitId.generate();
    Commit branchBCommit1 = new Commit(
        branchBCommit1Id,
        List.of(),
        "Bob",
        "Branch B - Initial commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, branchBCommit1,
        RDFPatchOps.emptyPatch());

    branchBCommit2Id = CommitId.generate();
    Commit branchBCommit2 = new Commit(
        branchBCommit2Id,
        List.of(branchBCommit1Id),
        "Bob",
        "Branch B - Second commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, branchBCommit2,
        RDFPatchOps.emptyPatch());

    Branch branchB = new Branch("branch-b", branchBCommit2Id);
    branchRepository.save(DATASET_NAME, branchB);
  }

  @Test
  void cherryPick_shouldReturn201_whenSuccessful() throws Exception {
    // Given - cherry-pick commit from branch-a onto branch-b
    String requestBody = String.format("""
        {
          "commit": "%s",
          "onto": "branch-b"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");
    headers.set("SPARQL-VC-Message", "Cherry-pick Alice's changes");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
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
    assertThat(json.get("newCommit").asText()).isNotNull();
    assertThat(json.get("newCommit").asText()).isNotEmpty();
    assertThat(json.get("branch").asText()).isEqualTo("branch-b");
    assertThat(json.get("sourceCommit").asText()).isEqualTo(branchACommit2Id.value());

    // Verify Location header
    String location = response.getHeaders().getFirst("Location");
    assertThat(location).isNotNull();
    assertThat(location).contains("/version/commits/");

    // Verify ETag header
    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();
    assertThat(etag).isEqualTo("\"" + json.get("newCommit").asText() + "\"");
  }

  @Test
  void cherryPick_shouldUseDefaultMessage_whenMessageNotProvided() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "onto": "branch-b"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void cherryPick_shouldReturn409_whenConflictDetected() throws Exception {
    // Setup: Create a commit on branch-b that conflicts with branchACommit2
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI("http://example.org/alice");
    Node p = NodeFactory.createURI("http://example.org/name");
    Node o = NodeFactory.createLiteralString("Alice");  // Same triple, will conflict
    collector.delete(g, s, p, o);  // Deletes same triple that source adds
    collector.finish();
    RDFPatch conflictingPatch = collector.getRDFPatch();

    CommitId conflictingCommitId = CommitId.generate();
    Commit conflictingCommit = new Commit(
        conflictingCommitId,
        List.of(branchBCommit2Id),
        "Bob",
        "Conflicting commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, conflictingCommit, conflictingPatch);

    // Update branch-b to point to the conflicting commit
    Branch branchB = new Branch("branch-b", conflictingCommitId);
    branchRepository.save(DATASET_NAME, branchB);

    // Given - try to cherry-pick branchACommit2 onto branch-b
    String requestBody = String.format("""
        {
          "commit": "%s",
          "onto": "branch-b"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Verify conflict response structure
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(409);
    assertThat(json.get("code").asText()).isEqualTo("cherry_pick_conflict");
  }

  @Test
  void cherryPick_shouldReturn400_whenCommitMissing() {
    // Given
    String requestBody = """
        {
          "onto": "branch-b"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
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
  void cherryPick_shouldReturn400_whenOntoBranchMissing() {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
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
  void cherryPick_shouldReturn404_whenSourceCommitNotFound() {
    // Given
    String requestBody = """
        {
          "commit": "01936c81-0000-0000-0000-000000000000",
          "onto": "branch-b"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
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
  void cherryPick_shouldReturn404_whenTargetBranchNotFound() {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "onto": "nonexistent-branch"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Charlie");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
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
  void cherryPick_shouldReturn400_whenAuthorMissing() {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "onto": "branch-b"
        }
        """, branchACommit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    // No SPARQL-VC-Author header

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/cherry-pick?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");
  }
}
