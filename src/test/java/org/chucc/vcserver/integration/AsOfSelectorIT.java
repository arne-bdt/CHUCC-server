package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatchOps;
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
 * Integration tests for asOf selector support.
 * Tests time-travel commit operations using the asOf parameter.
 *
 * <p>These are API layer integration tests that verify HTTP responses.
 * Repository updates are handled by event projectors asynchronously.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class AsOfSelectorIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

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

  private static final String DATASET_NAME = "test-dataset";
  private static final String BRANCH_NAME = "main";
  private static final String PATCH_BODY = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"asof-test\" .\n"
      + "TC .";

  private CommitId commit1Id;
  private CommitId commit2Id;
  private CommitId commit3Id;
  private Instant timestamp1;
  private Instant timestamp2;
  private Instant timestamp3;

  /**
   * Set up test data with commits at different times.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create commits with specific timestamps
    timestamp1 = Instant.parse("2025-01-01T00:00:00Z");
    timestamp2 = Instant.parse("2025-01-02T00:00:00Z");
    timestamp3 = Instant.parse("2025-01-03T00:00:00Z");

    commit1Id = CommitId.generate();
    commit2Id = CommitId.generate();
    commit3Id = CommitId.generate();

    Commit commit1 = new Commit(
        commit1Id,
        List.of(),
        "Alice",
        "First commit",
        timestamp1,
        0
    );

    Commit commit2 = new Commit(
        commit2Id,
        List.of(commit1Id),
        "Bob",
        "Second commit",
        timestamp2,
        0
    );

    Commit commit3 = new Commit(
        commit3Id,
        List.of(commit2Id),
        "Charlie",
        "Third commit",
        timestamp3,
        0
    );

    commitRepository.save(DATASET_NAME, commit1, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, commit2, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, commit3, RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch(BRANCH_NAME, commit3Id);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  /**
   * Test creating commit with asOf + branch selector.
   * API layer test: verifies HTTP response only.
   */
  @Test
  void createCommit_withAsOfAndBranch_shouldReturn201() throws Exception {
    // Given: Request to create commit at historical point (Jan 2nd)
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Dave");
    headers.set("SPARQL-VC-Message", "Commit on historical base");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with asOf + branch
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=2025-01-02T12:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should create commit successfully
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Verify response body structure
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("id").asText()).isNotNull();
    assertThat(json.get("message").asText()).isEqualTo("Commit on historical base");
    assertThat(json.get("author").asText()).isEqualTo("Dave");

    // Verify Location header
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
    assertThat(response.getHeaders().getFirst("ETag")).isNotNull();

    // Note: Repository updates handled by event projectors (see ReadModelProjectorIT)
  }

  /**
   * Test asOf with exact timestamp match.
   */
  @Test
  void createCommit_withExactTimestamp_shouldReturn201() throws Exception {
    // Given: Request with exact timestamp of commit2
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Eve");
    headers.set("SPARQL-VC-Message", "Commit at exact timestamp");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with exact timestamp,
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=2025-01-02T00:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should create commit successfully
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getFirst("Location")).isNotNull();
  }

  /**
   * Test asOf with timestamp after all commits returns latest commit.
   */
  @Test
  void createCommit_withFutureTimestamp_shouldReturn201() throws Exception {
    // Given: Request with future timestamp,
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Frank");
    headers.set("SPARQL-VC-Message", "Commit with future timestamp");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with future timestamp,
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=2025-01-10T00:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should use commit3 (latest) as base
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  /**
   * Test asOf with timestamp before all commits returns 404.
   */
  @Test
  void createCommit_withTimestampBeforeAllCommits_shouldReturn404() {
    // Given: Request with timestamp before any commit
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Grace");
    headers.set("SPARQL-VC-Message", "Commit with early timestamp");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with timestamp before all commits
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=2024-12-31T00:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test asOf with invalid timestamp format returns 400.
   */
  @Test
  void createCommit_withInvalidTimestamp_shouldReturn400() {
    // Given: Request with invalid timestamp format
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Henry");
    headers.set("SPARQL-VC-Message", "Commit with invalid timestamp");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with invalid timestamp,
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=not-a-timestamp&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test asOf without branch returns 400 per spec.
   */
  @Test
  void createCommit_withAsOfWithoutBranch_shouldReturn400() {
    // Given: Request with asOf but no branch
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Ivy");
    headers.set("SPARQL-VC-Message", "Commit with asOf only");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with asOf only
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?asOf=2025-01-02T00:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test asOf with non-existent branch returns 404.
   */
  @Test
  void createCommit_withAsOfAndNonExistentBranch_shouldReturn404() {
    // Given: Request with asOf and non-existent branch
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Jack");
    headers.set("SPARQL-VC-Message", "Commit on non-existent branch");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with non-existent branch
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=nonexistent&asOf=2025-01-02T00:00:00Z&dataset="
            + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test asOf between two commits returns 201.
   */
  @Test
  void createCommit_withTimestampBetweenCommits_shouldReturn201() throws Exception {
    // Given: Request with timestamp between commit1 and commit2
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/rdf-patch");
    headers.set("SPARQL-VC-Author", "Kate");
    headers.set("SPARQL-VC-Message", "Commit between timestamps");

    HttpEntity<String> request = new HttpEntity<>(PATCH_BODY, headers);

    // When: POST with timestamp between commits
    ResponseEntity<String> response = restTemplate.exchange(
        "/version/commits?branch=" + BRANCH_NAME
            + "&asOf=2025-01-01T12:00:00Z&dataset=" + DATASET_NAME,
        HttpMethod.POST,
        request,
        String.class
    );

    // Then: Should use commit1 as base
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }
}
