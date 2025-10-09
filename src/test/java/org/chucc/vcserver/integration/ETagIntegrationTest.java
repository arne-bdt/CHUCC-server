package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
 * Integration tests for ETag and If-Match precondition support.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ETagIntegrationTest {

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
  void createCommit_shouldReturnETag_whenCommitCreated() throws Exception {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Add new triple");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    String etag = response.getHeaders().getFirst("ETag");
    assertThat(etag).isNotNull();
    assertThat(etag).matches("\"[0-9a-f-]+\"");

    // Verify commit ID in response body matches ETag
    JsonNode json = objectMapper.readTree(response.getBody());
    String commitId = json.get("id").asText();
    assertThat(etag).isEqualTo("\"" + commitId + "\"");
  }

  @Test
  void createCommit_shouldSucceed_whenIfMatchMatchesCurrent() throws Exception {
    // Get current HEAD commit ID
    Branch branch = branchRepository.findByDatasetAndName(DATASET_NAME, "main")
        .orElseThrow();
    String currentHead = branch.getCommitId().toString();

    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Add triple with precondition");
    headers.set("If-Match", "\"" + currentHead + "\"");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void createCommit_shouldReturn412_whenIfMatchDoesNotMatch() throws Exception {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "Should fail");
    headers.set("If-Match", "\"01936c7f-8a2e-7890-abcd-ef1234567890\""); // stale ETag

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
    assertThat(response.getHeaders().getContentType().toString())
        .contains("application/problem+json");

    // Verify error response includes expected and actual values
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(412);
    assertThat(json.get("code").asText()).isEqualTo("precondition_failed");
    assertThat(json.get("title").asText())
        .contains("If-Match precondition failed")
        .contains("01936c7f-8a2e-7890-abcd-ef1234567890");
  }

  @Test
  void createCommit_shouldSucceed_whenNoIfMatchHeader() throws Exception {
    // Given
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Alice");
    headers.set("SPARQL-VC-Message", "No precondition");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=main&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then - API response verification (synchronous)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

}
