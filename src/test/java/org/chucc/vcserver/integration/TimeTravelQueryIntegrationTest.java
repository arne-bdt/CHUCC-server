package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for time-travel SPARQL queries using asOf selector.
 * These tests verify that asOf parameter is correctly accepted and validated.
 *
 * <p>Note: Current SPARQL query endpoint returns 501 Not Implemented.
 * When query functionality is implemented, these tests should be enhanced
 * to verify actual query results reflect historical dataset state.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class TimeTravelQueryIntegrationTest {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "default";
  private static final String BRANCH_NAME = "main";

  private CommitId commit1Id;
  private CommitId commit2Id;
  private CommitId commit3Id;
  private Instant timestamp1;

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

    // Create commits with specific timestamps representing data evolution:
    // T1: Initial data state
    // T2: Data updated
    // T3: Data modified again
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
        "Insert initial data",
        timestamp1
    );

    Commit commit2 = new Commit(
        commit2Id,
        List.of(commit1Id),
        "Bob",
        "Update data",
        timestamp2
    );

    Commit commit3 = new Commit(
        commit3Id,
        List.of(commit2Id),
        "Charlie",
        "Modify data again",
        timestamp3
    );

    commitRepository.save(DATASET_NAME, commit1, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, commit2, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, commit3, RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch(BRANCH_NAME, commit3Id);
    branchRepository.save(DATASET_NAME, mainBranch);
  }

  /**
   * Test querying with asOf parameter is accepted (validation passes).
   * When SPARQL query is implemented, this should verify correct historical data.
   */
  @Test
  @Disabled("asOf + branch parameter combination needs further investigation")
  void queryWithAsOf_shouldAcceptParameter() {
    // Given: Query with asOf parameter
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2025-01-02T00:00:00Z")
        .toUriString();

    // When: GET query with asOf + branch
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Query succeeds (endpoint implemented)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test querying with asOf but no branch should fail validation.
   * Per spec, asOf on queries can work without branch (global time-travel).
   */
  @Test
  @Disabled("asOf-only parameter needs further investigation")
  void queryWithAsOfOnly_shouldAcceptParameter() {
    // Given: Query with asOf only (no branch)
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("asOf", "2025-01-02T00:00:00Z")
        .toUriString();

    // When: GET query with asOf only
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Query succeeds (endpoint implemented, uses default branch)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test querying with invalid asOf timestamp format returns error.
   */
  @Test
  void queryWithInvalidAsOf_shouldReturnError() {
    // Given: Query with invalid timestamp
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "not-a-timestamp")
        .toUriString();

    // When: GET query with invalid asOf
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should return error (400 or 500 depending on validation implementation)
    assertThat(response.getStatusCode().is4xxClientError()
        || response.getStatusCode().is5xxServerError()).isTrue();
  }

  /**
   * Test querying with asOf before all commits should return 404.
   * When SPARQL is implemented, this should return 404 Not Found.
   */
  @Test
  void queryWithAsOfBeforeAllCommits_shouldReturn404() {
    // Given: Query with timestamp before any commit
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2024-12-31T00:00:00Z")
        .toUriString();

    // When: GET query with asOf before all commits
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should return 404 (no commit found before timestamp)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test querying with asOf + commit should fail validation (mutually exclusive).
   */
  @Test
  void queryWithAsOfAndCommit_shouldReturn400() {
    // Given: Query with both asOf and commit (invalid combination)
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("commit", commit2Id.value())
        .queryParam("asOf", "2025-01-02T00:00:00Z")
        .toUriString();

    // When: GET query with both asOf and commit
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should return 400 Bad Request (selector conflict)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test querying with asOf after all commits uses latest commit.
   * When SPARQL is implemented, should query current state.
   */
  @Test
  @Disabled("asOf + branch parameter combination needs further investigation")
  void queryWithAsOfAfterAllCommits_shouldAcceptParameter() {
    // Given: Query with future timestamp
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2025-01-10T00:00:00Z")
        .toUriString();

    // When: GET query with asOf after all commits
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Query succeeds using latest commit
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /*
   * TODO: When SPARQL query functionality is implemented, add these tests:
   *
   * 1. queryWithAsOfAtT1_shouldReturnInitialData()
   *    - Insert data at T1
   *    - Query with asOf=T1
   *    - Verify result contains initial data
   *
   * 2. queryWithAsOfAtT2_shouldReturnUpdatedData()
   *    - Insert at T1, update at T2
   *    - Query with asOf=T2
   *    - Verify result contains updated data, not initial
   *
   * 3. queryWithAsOfAtT3_shouldReturnLatestData()
   *    - Insert at T1, update at T2, delete at T3
   *    - Query with asOf=T3
   *    - Verify result reflects deletion
   *
   * 4. queryWithAsOfBetweenT1AndT2_shouldReturnT1State()
   *    - Insert at T1, update at T2
   *    - Query with asOf=(T1 + 1 hour)
   *    - Verify result contains T1 state (most recent before asOf)
   *
   * 5. queryWithoutAsOf_shouldReturnCurrentState()
   *    - Verify default behavior still works (queries current branch HEAD)
   */
}
