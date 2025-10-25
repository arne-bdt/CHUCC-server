package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for GET /version/commits/{id} endpoint.
 * Tests API layer only (projector disabled).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CommitMetadataIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private BranchRepository branchRepository;

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

  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create initial commit and branch
    CommitId initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        java.util.List.of(),
        "System",
        "Initial commit",
        java.time.Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, initialCommit,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DATASET_NAME, mainBranch);
  }


  @Test
  void getCommit_withoutDataset_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/01933e4a-9d4e-7000-8000-000000000003",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void getCommit_withNonExistentCommit_shouldReturn404() throws Exception {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/00000000-0000-0000-0000-000000000000?dataset=" + DATASET_NAME,
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("code").asText()).isEqualTo("COMMIT_NOT_FOUND");
  }

  @Test
  void getCommit_shouldReturnMetadataWithETag() throws Exception {
    // Arrange: Directly insert commit into repository (projector disabled)
    CommitId parentId = CommitId.generate();
    Commit parent = new Commit(
        parentId,
        java.util.List.of(),
        "Test User <test@example.org>",
        "Parent commit",
        java.time.Instant.now(),
        5
    );
    commitRepository.save(DATASET_NAME, parent, org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    CommitId testId = CommitId.generate();
    Commit testCommit = new Commit(
        testId,
        java.util.List.of(parentId),
        "Test User <test@example.org>",
        "Test commit",
        java.time.Instant.now(),
        10
    );
    commitRepository.save(DATASET_NAME, testCommit, org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/" + testId.toString() + "?dataset=" + DATASET_NAME,
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getETag()).isEqualTo("\"" + testId.toString() + "\"");

    JsonNode body = objectMapper.readTree(response.getBody());
    assertThat(body.get("id").asText()).isEqualTo(testId.toString());
    assertThat(body.get("message").asText()).isEqualTo("Test commit");
    assertThat(body.get("author").asText()).isEqualTo("Test User <test@example.org>");
    assertThat(body.has("timestamp")).isTrue();
    assertThat(body.get("parents").isArray()).isTrue();
    assertThat(body.get("parents").get(0).asText()).isEqualTo(parentId.toString());
    assertThat(body.get("patchSize").asInt()).isEqualTo(10);
  }

  @Test
  void getCommit_shouldIncludeParents() throws Exception {
    // Arrange: Directly insert two commits into repository (projector disabled)
    CommitId commit1Id = CommitId.generate();
    Commit commit1 = new Commit(
        commit1Id,
        java.util.List.of(),
        "Test User <test@example.org>",
        "First commit",
        java.time.Instant.now(),
        5
    );
    commitRepository.save(DATASET_NAME, commit1, org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    CommitId commit2Id = CommitId.generate();
    Commit commit2 = new Commit(
        commit2Id,
        java.util.List.of(commit1Id),
        "Test User <test@example.org>",
        "Second commit",
        java.time.Instant.now(),
        8
    );
    commitRepository.save(DATASET_NAME, commit2, org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits/" + commit2Id.toString() + "?dataset=" + DATASET_NAME,
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode body = objectMapper.readTree(response.getBody());
    JsonNode parents = body.get("parents");
    assertThat(parents.isArray()).isTrue();
    assertThat(parents.get(0).asText()).isEqualTo(commit1Id.toString());
  }
}
