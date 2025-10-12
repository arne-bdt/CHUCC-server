package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.chucc.vcserver.testutil.ITFixture;
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
 *
 * <p>These tests verify HTTP parameter validation for time-travel queries:
 * <ul>
 *   <li>asOf parameter is accepted and validated</li>
 *   <li>Invalid timestamp formats return errors</li>
 *   <li>Mutually exclusive selectors (asOf + commit) return 400</li>
 *   <li>Timestamps before all commits return 404</li>
 * </ul>
 *
 * <p>Note: Projector is DISABLED (default for API layer testing).
 * These tests verify the HTTP contract only, not query result correctness.</p>
 *
 * <p>When SPARQL query functionality is fully implemented, consider adding
 * additional tests that verify historical data is correctly returned.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class TimeTravelQueryIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  private static final String BRANCH_NAME = "main";

  /**
   * Test querying with asOf + branch parameter combination.
   * The selector combination is explicitly allowed per spec.
   * Tests with future timestamp (after all commits) should return latest commit state.
   */
  @Test
  void queryWithAsOf_shouldAcceptParameter() {
    // Given: Query with asOf parameter and branch (future timestamp to be after initial commit)
    String futureTimestamp = Instant.now().plus(Duration.ofDays(1)).toString();
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", futureTimestamp)
        .build()
        .toUri();

    // When: GET query with asOf + branch
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds (parameters accepted)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test querying with asOf but no branch.
   * Per spec, asOf can work without branch (uses default branch "main" for time-travel).
   */
  @Test
  void queryWithAsOfOnly_shouldAcceptParameter() {
    // Given: Query with asOf only (future timestamp, no branch specified, uses default "main")
    String futureTimestamp = Instant.now().plus(Duration.ofDays(1)).toString();
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("asOf", futureTimestamp)
        .build()
        .toUri();

    // When: GET query with asOf only
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds (uses default branch with asOf)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /**
   * Test querying with invalid asOf timestamp format returns error.
   */
  @Test
  void queryWithInvalidAsOf_shouldReturnError() {
    // Given: Query with invalid timestamp format
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
   * The timestamp is before the initial commit created by ITFixture.
   */
  @Test
  void queryWithAsOfBeforeAllCommits_shouldReturn404() {
    // Given: Query with timestamp far in the past (before fixture's initial commit)
    String url = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", "2020-01-01T00:00:00Z")
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
        .queryParam("commit", initialCommitId.value())
        .queryParam("asOf", "2026-01-02T00:00:00Z")
        .toUriString();

    // When: GET query with both asOf and commit
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: Should return 400 Bad Request (selector conflict)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /**
   * Test querying with asOf after all commits uses latest commit.
   * A future timestamp should successfully query the current state.
   */
  @Test
  void queryWithAsOfAfterAllCommits_shouldAcceptParameter() {
    // Given: Query with future timestamp (after all commits)
    String futureTimestamp = Instant.now().plus(Duration.ofDays(1)).toString();
    java.net.URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT * WHERE { ?s ?p ?o } LIMIT 10")
        .queryParam("branch", BRANCH_NAME)
        .queryParam("asOf", futureTimestamp)
        .build()
        .toUri();

    // When: GET query with asOf after all commits
    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then: Query succeeds using latest commit
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  /*
   * TODO: When SPARQL query functionality is fully implemented, add these tests:
   *
   * 1. queryWithAsOfAtT1_shouldReturnInitialData()
   *    - Create commits via API at T1
   *    - Query with asOf=T1
   *    - Verify result contains initial data
   *
   * 2. queryWithAsOfAtT2_shouldReturnUpdatedData()
   *    - Create at T1, update at T2 via API
   *    - Query with asOf=T2
   *    - Verify result contains updated data, not initial
   *
   * 3. queryWithAsOfAtT3_shouldReturnLatestData()
   *    - Create at T1, update at T2, delete at T3 via API
   *    - Query with asOf=T3
   *    - Verify result reflects deletion
   *
   * 4. queryWithAsOfBetweenT1AndT2_shouldReturnT1State()
   *    - Create at T1, update at T2 via API
   *    - Query with asOf=(T1 + 1 hour)
   *    - Verify result contains T1 state (most recent before asOf)
   *
   * 5. queryWithoutAsOf_shouldReturnCurrentState()
   *    - Verify default behavior still works (queries current branch HEAD)
   *
   * Note: These future tests should use Graph Store Protocol HTTP API
   * to create commits (not direct repository access), and should enable
   * projector with @TestPropertySource for verification.
   */
}
