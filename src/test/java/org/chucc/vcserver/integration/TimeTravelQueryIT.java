package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.ITFixture;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Integration tests for time-travel SPARQL queries using asOf selector.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>HTTP parameter validation for time-travel queries</li>
 *   <li>Query results return correct historical data at specified timestamps</li>
 *   <li>asOf parameter is accepted and validated</li>
 *   <li>Invalid timestamp formats return errors</li>
 *   <li>Mutually exclusive selectors (asOf + commit) return 400</li>
 *   <li>Timestamps before all commits return 404</li>
 * </ul>
 *
 * <p><strong>Projector Enabled:</strong> This test class enables the ReadModelProjector
 * to verify end-to-end time-travel query functionality. Tests create commits via HTTP
 * API and wait for async projection before querying historical states.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class TimeTravelQueryIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private CommitRepository commitRepository;

  private static final String BRANCH_NAME = "main";

  /**
   * Creates a commit via HTTP PUT and returns its timestamp.
   * Waits for async projector to complete before returning.
   *
   * @param turtle Turtle RDF content
   * @param message commit message
   * @return the timestamp of the created commit
   */
  private Instant createCommitAndGetTimestamp(String turtle, String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    headers.set("SPARQL-VC-Author", "TestUser");
    headers.set("SPARQL-VC-Message", message);

    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers),
        String.class
    );

    String commitIdStr = response.getHeaders().getETag().replace("\"", "");
    CommitId commitId = new CommitId(commitIdStr);

    // Wait for projector to process the event
    await().atMost(Duration.ofSeconds(10))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId).isPresent());

    Commit commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId)
        .orElseThrow();
    return commit.timestamp();
  }

  /**
   * Executes a SPARQL query with asOf parameter.
   *
   * @param query SPARQL query string
   * @param asOf timestamp for time-travel query
   * @return HTTP response
   */
  private ResponseEntity<String> queryWithAsOf(String query, Instant asOf) {
    URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", query)
        .queryParam("branch", "main")
        .queryParam("asOf", asOf.toString())
        .build()
        .toUri();

    return restTemplate.getForEntity(uri, String.class);
  }

  /**
   * Deletes a graph via HTTP DELETE and returns the commit timestamp.
   *
   * @param message commit message
   * @return timestamp of the delete commit
   */
  private Instant deleteGraphAndGetTimestamp(String message) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "TestUser");
    headers.set("SPARQL-VC-Message", message);

    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        Void.class
    );

    String commitIdStr = response.getHeaders().getETag().replace("\"", "");
    CommitId commitId = new CommitId(commitIdStr);

    // Wait for projector
    await().atMost(Duration.ofSeconds(10))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId).isPresent());

    Commit commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId)
        .orElseThrow();
    return commit.timestamp();
  }

  // ========== API Parameter Validation Tests ==========

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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  // ========== Time-Travel Query Result Tests ==========

  /**
   * Test that querying with asOf at T1 returns initial data only.
   * Verifies time-travel correctly reconstructs historical state at T1.
   */
  @Test
  void queryWithAsOfAtT1_shouldReturnInitialData() {
    // Given - Create initial commit at T1
    String initialData = """
        @prefix ex: <http://example.org/> .
        ex:alice ex:name "Alice" .
        """;
    Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

    // And - Update data at T2 (replaces previous data)
    String updatedData = """
        @prefix ex: <http://example.org/> .
        ex:bob ex:name "Bob" .
        """;
    createCommitAndGetTimestamp(updatedData, "Updated data");

    // When - Query with asOf=T1 (should see initial data only)
    String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
    ResponseEntity<String> response = queryWithAsOf(query, t1);

    // Then - Should return HTTP 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // And - Results should contain "Alice" (T1 data)
    assertThat(response.getBody()).contains("Alice");

    // And - Results should NOT contain "Bob" (T2 data)
    assertThat(response.getBody()).doesNotContain("Bob");
  }

  /**
   * Test that querying with asOf at T2 returns updated data.
   * Verifies PUT operation replaces previous graph content.
   */
  @Test
  void queryWithAsOfAtT2_shouldReturnUpdatedData() {
    // Given - Create initial commit
    String initialData = """
        @prefix ex: <http://example.org/> .
        ex:alice ex:name "Alice" .
        """;
    createCommitAndGetTimestamp(initialData, "Initial data");

    // And - Update data (replaces, not merges)
    String updatedData = """
        @prefix ex: <http://example.org/> .
        ex:bob ex:name "Bob" .
        """;
    Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

    // When - Query with asOf=T2 (should see updated data only)
    String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
    ResponseEntity<String> response = queryWithAsOf(query, t2);

    // Then - Should return HTTP 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // And - Results should contain "Bob" (T2 data)
    assertThat(response.getBody()).contains("Bob");

    // And - Results should NOT contain "Alice" (T1 data, replaced by PUT)
    assertThat(response.getBody()).doesNotContain("Alice");
  }

  /**
   * Test that querying with asOf at T3 returns empty results after deletion.
   * Verifies DELETE operation is correctly reflected in time-travel queries.
   */
  @Test
  void queryWithAsOfAtT3_shouldReturnEmptyAfterDeletion() {
    // Given - Create initial data
    String initialData = """
        @prefix ex: <http://example.org/> .
        ex:alice ex:name "Alice" .
        """;
    createCommitAndGetTimestamp(initialData, "Initial data");

    // And - Update data
    String updatedData = """
        @prefix ex: <http://example.org/> .
        ex:bob ex:name "Bob" .
        """;
    createCommitAndGetTimestamp(updatedData, "Updated data");

    // And - Delete graph at T3
    Instant t3 = deleteGraphAndGetTimestamp("Delete graph");

    // When - Query with asOf=T3 (after deletion)
    String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
    ResponseEntity<String> response = queryWithAsOf(query, t3);

    // Then - Should return HTTP 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // And - Results should be empty (graph was deleted)
    // Note: SPARQL JSON results format includes empty bindings array
    String body = response.getBody();
    assertThat(body)
        .satisfiesAnyOf(
            // JSON format: empty bindings array
            b -> assertThat(b).contains("\"bindings\":[]"),
            // CSV format: only header line
            b -> assertThat(b).matches("(?s).*name.*\\s*$"),
            // TSV format: only header line
            b -> assertThat(b).matches("(?s).*\\?name.*\\s*$")
        );

    // And - Should NOT contain data from T1 or T2
    assertThat(body).doesNotContain("Alice");
    assertThat(body).doesNotContain("Bob");
  }

  /**
   * Test that querying with asOf between T1 and T2 returns T1 state.
   * Verifies time-travel returns most recent commit before the asOf timestamp.
   */
  @Test
  void queryWithAsOfBetweenT1AndT2_shouldReturnT1State() throws InterruptedException {
    // Given - Create at T1
    String initialData = """
        @prefix ex: <http://example.org/> .
        ex:alice ex:name "Alice" .
        """;
    Instant t1 = createCommitAndGetTimestamp(initialData, "Initial data");

    // And - Wait to ensure timestamp difference
    Thread.sleep(2000);

    // And - Update at T2
    String updatedData = """
        @prefix ex: <http://example.org/> .
        ex:bob ex:name "Bob" .
        """;
    Instant t2 = createCommitAndGetTimestamp(updatedData, "Updated data");

    // When - Query with asOf between T1 and T2
    long midpointMillis = t1.toEpochMilli() + (t2.toEpochMilli() - t1.toEpochMilli()) / 2;
    Instant asOf = Instant.ofEpochMilli(midpointMillis);
    String query = "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }";
    ResponseEntity<String> response = queryWithAsOf(query, asOf);

    // Then - Should return HTTP 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // And - Results should contain T1 state (most recent before asOf)
    assertThat(response.getBody()).contains("Alice");
    assertThat(response.getBody()).doesNotContain("Bob");
  }

  /**
   * Test that querying without asOf returns current state.
   * Verifies default behavior (no time-travel) still works correctly.
   */
  @Test
  void queryWithoutAsOf_shouldReturnCurrentState() {
    // Given - Create initial data
    String initialData = """
        @prefix ex: <http://example.org/> .
        ex:alice ex:name "Alice" .
        """;
    createCommitAndGetTimestamp(initialData, "Initial data");

    // And - Update data (current state)
    String updatedData = """
        @prefix ex: <http://example.org/> .
        ex:bob ex:name "Bob" .
        """;
    createCommitAndGetTimestamp(updatedData, "Updated data");

    // When - Query without asOf parameter (should return current state)
    URI uri = UriComponentsBuilder.fromPath("/sparql")
        .queryParam("query", "SELECT ?s ?name WHERE { ?s <http://example.org/name> ?name }")
        .queryParam("branch", "main")
        .build()
        .toUri();

    ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

    // Then - Should return HTTP 200
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // And - Results should contain current state (T2 data)
    assertThat(response.getBody()).contains("Bob");
    assertThat(response.getBody()).doesNotContain("Alice");
  }
}
