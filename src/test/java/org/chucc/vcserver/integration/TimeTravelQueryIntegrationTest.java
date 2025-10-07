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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.util.UriComponentsBuilder;

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

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  private static final String DATASET_NAME = "test-dataset";
  private static final String BRANCH_NAME = "main";

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
  void queryWithAsOf_shouldAcceptParameter() {
    // Given: Query with asOf parameter
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2025-01-02T00:00:00Z")
        .queryParam("dataset", DATASET_NAME)
        .toUriString();

    // When: GET query with asOf + branch
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Parameter should be accepted (currently 501, will be 200 when implemented)
    // Validation should pass (not 400)
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test querying with asOf but no branch should fail validation.
   * Per spec, asOf on queries can work without branch (global time-travel).
   */
  @Test
  void queryWithAsOfOnly_shouldAcceptParameter() {
    // Given: Query with asOf only (no branch)
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("asOf", "2025-01-02T00:00:00Z")
        .queryParam("dataset", DATASET_NAME)
        .toUriString();

    // When: GET query with asOf only
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should accept (asOf without branch is valid for queries)
    // Validation should pass (not 400)
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test querying with invalid asOf timestamp format returns 400.
   */
  @Test
  void queryWithInvalidAsOf_shouldReturn400() {
    // Given: Query with invalid timestamp
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "not-a-timestamp")
        .queryParam("dataset", DATASET_NAME)
        .toUriString();

    // When: GET query with invalid asOf
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should return 400 Bad Request (when query is implemented)
    // For now, might return 501, but validation should eventually catch this
    // Note: This assertion will need to be updated when SPARQL is implemented
    assertThat(response.getStatusCode()).isIn(
        HttpStatus.BAD_REQUEST,
        HttpStatus.NOT_IMPLEMENTED
    );
  }

  /**
   * Test querying with asOf before all commits should return 404.
   * When SPARQL is implemented, this should return 404 Not Found.
   */
  @Test
  void queryWithAsOfBeforeAllCommits_shouldEventuallyReturn404() {
    // Given: Query with timestamp before any commit
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2024-12-31T00:00:00Z")
        .queryParam("dataset", DATASET_NAME)
        .toUriString();

    // When: GET query with asOf before all commits
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should eventually return 404 when SPARQL is implemented
    // For now returns 501
    assertThat(response.getStatusCode()).isIn(
        HttpStatus.NOT_FOUND,
        HttpStatus.NOT_IMPLEMENTED
    );
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
        .queryParam("dataset", DATASET_NAME)
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
  void queryWithAsOfAfterAllCommits_shouldAcceptParameter() {
    // Given: Query with future timestamp
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2025-01-10T00:00:00Z")
        .queryParam("dataset", DATASET_NAME)
        .toUriString();

    // When: GET query with asOf after all commits
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should accept parameter and use latest commit
    // When implemented, should return 200 OK
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
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
