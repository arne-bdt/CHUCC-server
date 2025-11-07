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
 * Integration tests for POST /version/revert endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class RevertIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";

  private CommitId initialCommitId;
  private CommitId commitToRevertId;

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
   * Sets up a branch with commits for revert testing.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create initial commit
    initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        List.of(),
        "Alice",
        "Initial commit",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, initialCommit, RDFPatchOps.emptyPatch());

    // Create a commit with data changes (this will be reverted)
    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    Node g = NodeFactory.createURI("http://example.org/g");
    Node s = NodeFactory.createURI("http://example.org/alice");
    Node p = NodeFactory.createURI("http://example.org/name");
    Node o = NodeFactory.createLiteralString("Alice");
    collector.add(g, s, p, o);
    collector.finish();
    RDFPatch patchToRevert = collector.getRDFPatch();

    commitToRevertId = CommitId.generate();
    Commit commitToRevert = new Commit(
        commitToRevertId,
        List.of(initialCommitId),
        "Alice",
        "Add Alice data",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, commitToRevert, patchToRevert);

    // Create main branch pointing to commitToRevert
    Branch mainBranch = new Branch("main", commitToRevertId);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  @Test
  void revertCommit_shouldReturn201_andCreateRevertCommit() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "main"
        }
        """, commitToRevertId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");
    headers.set("SPARQL-VC-Message", "Revert problematic change");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - verify response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify Location header
    String locationHeader = response.getHeaders().getFirst("Location");
    assertThat(locationHeader).isNotNull();
    assertThat(locationHeader).contains("/" + DATASET_NAME + "/version/commits/");

    // Verify ETag header
    String etagHeader = response.getHeaders().getFirst("ETag");
    assertThat(etagHeader).isNotNull();
    assertThat(etagHeader).matches("\"[0-9a-f-]+\"");

    // Parse response body
    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
    String newCommitId = jsonResponse.get("newCommit").asText();
    String branch = jsonResponse.get("branch").asText();
    String revertedCommit = jsonResponse.get("revertedCommit").asText();

    assertThat(newCommitId).isNotNull().isNotBlank();
    assertThat(branch).isEqualTo("main");
    assertThat(revertedCommit).isEqualTo(commitToRevertId.value());
  }

  @Test
  void revertCommit_shouldReturn201_withDefaultMessage() throws Exception {
    // Given - no custom message provided
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "main"
        }
        """, commitToRevertId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Parse response
    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
    String newCommitId = jsonResponse.get("newCommit").asText();
    assertThat(newCommitId).isNotNull().isNotBlank();
  }

  @Test
  void revertCommit_shouldReturn404_whenCommitNotFound() throws Exception {
    // Given
    String nonExistentCommitId = "01936c99-9999-7890-abcd-ef1234567890";
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "main"
        }
        """, nonExistentCommitId);

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
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
  void revertCommit_shouldReturn404_whenBranchNotFound() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "nonexistent"
        }
        """, commitToRevertId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
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
  void revertCommit_shouldReturn400_whenAuthorMissing() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "main"
        }
        """, commitToRevertId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    // No SPARQL-VC-Author header

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Verify error details
    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
    assertThat(jsonResponse.get("code").asText()).isEqualTo("MISSING_AUTHOR");
  }

  @Test
  void revertCommit_shouldReturn400_whenCommitIdBlank() throws Exception {
    // Given
    String requestBody = """
        {
          "commit": "",
          "branch": "main"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
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
  void revertCommit_shouldReturn400_whenBranchNameBlank() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": ""
        }
        """, commitToRevertId.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/revert",
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
  void revertCommit_shouldUseDefaultDataset_whenNotProvided() throws Exception {
    // Given - set up data in default dataset
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");

    CommitId defaultCommitId = CommitId.generate();
    Commit defaultCommit = new Commit(
        defaultCommitId,
        List.of(),
        "Alice",
        "Default dataset commit",
        Instant.now(),
        0
    );
    commitRepository.save("default", defaultCommit, RDFPatchOps.emptyPatch());

    RDFChangesCollector collector = new RDFChangesCollector();
    collector.start();
    collector.add(
        NodeFactory.createURI("http://example.org/g"),
        NodeFactory.createURI("http://example.org/s"),
        NodeFactory.createURI("http://example.org/p"),
        NodeFactory.createLiteralString("test")
    );
    collector.finish();

    CommitId commitToRevert = CommitId.generate();
    Commit commit = new Commit(
        commitToRevert,
        List.of(defaultCommitId),
        "Alice",
        "Commit to revert",
        Instant.now(),
        0
    );
    commitRepository.save("default", commit, collector.getRDFPatch());

    Branch mainBranch = new Branch("main", commitToRevert);
    branchRepository.save("default", mainBranch);

    String requestBody = String.format("""
        {
          "commit": "%s",
          "branch": "main"
        }
        """, commitToRevert.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");
    headers.set("SPARQL-VC-Author", "Bob");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When - dataset now required in path
    ResponseEntity<String> response = restTemplate.exchange(
        "/default/version/revert",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode jsonResponse = objectMapper.readTree(response.getBody());
    assertThat(jsonResponse.get("branch").asText()).isEqualTo("main");
  }
}
