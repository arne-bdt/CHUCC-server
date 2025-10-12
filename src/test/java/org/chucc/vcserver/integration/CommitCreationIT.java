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
 * Integration tests for POST /version/commits endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CommitCreationIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";
  private static final String PATCH_BODY = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"value\" .\n"
      + "TC .";

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
        java.time.Instant.now()
    );
    commitRepository.save(DATASET_NAME, initialCommit,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  @Test
  void createCommit_shouldReturn201_whenValidPatchOnBranch() throws Exception {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice <mailto:alice@example.org>");
    headers.set("SPARQL-VC-Message", "Add new triple");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/json");

    // Verify Location header
    String location = response.getHeaders().getFirst("Location");
    assertThat(location).isNotNull();
    assertThat(location).startsWith("/version/commits/");

    // Verify ETag header
    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();
    assertThat(etag).matches("\"[0-9a-f-]+\"");

    // Verify response body
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.has("id")).isTrue();
    assertThat(json.has("parents")).isTrue();
    assertThat(json.has("author")).isTrue();
    assertThat(json.has("message")).isTrue();
    assertThat(json.has("timestamp")).isTrue();

    assertThat(json.get("author").asText()).isEqualTo("Alice <mailto:alice@example.org>");
    assertThat(json.get("message").asText()).isEqualTo("Add new triple");
    assertThat(json.get("parents").isArray()).isTrue();
    assertThat(json.get("parents").size()).isEqualTo(1);

    // Note: Branch update is handled by event projectors, not tested here
  }

  @Test
  void createCommit_shouldReturn400_whenBothBranchAndCommitProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&commit=some-id&dataset=" + DATASET_NAME,
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
  void createCommit_shouldReturn400_whenNeitherBranchNorCommitProvided() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?dataset=" + DATASET_NAME,
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
  void createCommit_shouldReturn415_whenInvalidContentType() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "application/json");

    HttpEntity<String> request = new HttpEntity<>("{}", headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void createCommit_shouldReturn400_whenAsOfWithCommitSelector() {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // Get the initial commit ID
    Branch mainBranch = branchRepository
        .findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    String commitId = mainBranch.getCommitId().value();

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?commit=" + commitId + "&asOf=2025-01-01T00:00:00Z&dataset="
            + DATASET_NAME,
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
  void createCommit_shouldUseDefaultDataset_whenNotProvided() throws Exception {
    // Setup: Create initial commit and branch in default dataset
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");

    CommitId initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        java.util.List.of(),
        "System",
        "Initial commit",
        java.time.Instant.now()
    );
    commitRepository.save("default", initialCommit,
        org.apache.jena.rdfpatch.RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save("default", mainBranch);

    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Test commit");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main",
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Cleanup
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");
  }
}
