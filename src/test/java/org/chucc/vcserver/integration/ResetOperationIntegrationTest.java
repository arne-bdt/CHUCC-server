package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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

/**
 * Integration tests for POST /version/reset endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ResetOperationIntegrationTest {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";

  private CommitId commit1Id;
  private CommitId commit2Id;
  private CommitId commit3Id;

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
   * Sets up a branch with three commits for testing reset operations.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create commit chain: commit1 -> commit2 -> commit3
    commit1Id = CommitId.generate();
    Commit commit1 = new Commit(
        commit1Id,
        List.of(),
        "Alice",
        "Initial commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commit1,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    commit2Id = CommitId.generate();
    Commit commit2 = new Commit(
        commit2Id,
        List.of(commit1Id),
        "Bob",
        "Second commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commit2,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    commit3Id = CommitId.generate();
    Commit commit3 = new Commit(
        commit3Id,
        List.of(commit2Id),
        "Charlie",
        "Third commit",
        Instant.now()
    );
    commitRepository.save(DATASET_NAME, commit3,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    // Create main branch pointing to commit3
    Branch mainBranch = new Branch("main", commit3Id);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  @Test
  void resetBranch_shouldReturn200_whenHardResetToEarlierCommit() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s",
          "mode": "HARD"
        }
        """, commit1Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify ETag header
    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();
    assertThat(etag).isEqualTo("\"" + commit1Id.value() + "\"");

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("main");
    assertThat(json.get("newHead").asText()).isEqualTo(commit1Id.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(commit3Id.value());

    // Note: Branch pointer update is handled by event projectors,
    // not verified in this unit test
  }

  @Test
  void resetBranch_shouldReturn200_whenSoftResetToEarlierCommit() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s",
          "mode": "SOFT"
        }
        """, commit2Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("main");
    assertThat(json.get("newHead").asText()).isEqualTo(commit2Id.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(commit3Id.value());
  }

  @Test
  void resetBranch_shouldReturn200_whenMixedResetToEarlierCommit() throws Exception {
    // Given
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s",
          "mode": "MIXED"
        }
        """, commit1Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("main");
    assertThat(json.get("newHead").asText()).isEqualTo(commit1Id.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(commit3Id.value());
  }

  @Test
  void resetBranch_shouldReturn200_whenResetToSameCommit() throws Exception {
    // Given - reset to current head
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s",
          "mode": "HARD"
        }
        """, commit3Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Verify response shows same commit for both
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("main");
    assertThat(json.get("newHead").asText()).isEqualTo(commit3Id.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(commit3Id.value());
  }

  @Test
  void resetBranch_shouldReturn400_whenBranchNameMissing() {
    // Given
    String requestBody = """
        {
          "to": "01936c81-4567-7890-abcd-ef1234567890",
          "mode": "HARD"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
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
  void resetBranch_shouldReturn400_whenTargetCommitMissing() {
    // Given
    String requestBody = """
        {
          "branch": "main",
          "mode": "HARD"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
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
  void resetBranch_shouldReturn400_whenModeMissing() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s"
        }
        """, commit1Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
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
  void resetBranch_shouldReturn404_whenBranchNotFound() {
    // Given
    String requestBody = String.format("""
        {
          "branch": "nonexistent",
          "to": "%s",
          "mode": "HARD"
        }
        """, commit1Id.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
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
  void resetBranch_shouldReturn404_whenTargetCommitNotFound() {
    // Given
    String requestBody = """
        {
          "branch": "main",
          "to": "01936c81-0000-0000-0000-000000000000",
          "mode": "HARD"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset?dataset=" + DATASET_NAME,
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
  void resetBranch_shouldUseDefaultDataset_whenNotProvided() throws Exception {
    // Setup: Create branch in default dataset
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");

    CommitId defaultCommit1 = CommitId.generate();
    Commit commit1 = new Commit(
        defaultCommit1,
        List.of(),
        "Alice",
        "Initial commit",
        Instant.now()
    );
    commitRepository.save("default", commit1,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    CommitId defaultCommit2 = CommitId.generate();
    Commit commit2 = new Commit(
        defaultCommit2,
        List.of(defaultCommit1),
        "Bob",
        "Second commit",
        Instant.now()
    );
    commitRepository.save("default", commit2,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch("main", defaultCommit2);
    branchRepository.save("default", mainBranch);

    // Given
    String requestBody = String.format("""
        {
          "branch": "main",
          "to": "%s",
          "mode": "HARD"
        }
        """, defaultCommit1.value());

    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/reset",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("branch").asText()).isEqualTo("main");
    assertThat(json.get("newHead").asText()).isEqualTo(defaultCommit1.value());
    assertThat(json.get("previousHead").asText()).isEqualTo(defaultCommit2.value());

    // Cleanup
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");
  }
}
