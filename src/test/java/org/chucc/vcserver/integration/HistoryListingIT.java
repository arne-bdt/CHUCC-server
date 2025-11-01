package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for GET /version/history endpoint.
 * Tests commit history listing with filters and pagination.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class HistoryListingIT {

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
    commitRepository.deleteAllByDataset(DATASET_NAME);
    branchRepository.deleteAllByDataset(DATASET_NAME);
  }

  @Test
  void listHistory_shouldReturnAllCommits_sortedByTimestampDescending() throws Exception {
    // Given: Create 3 commits with different timestamps
    Instant now = Instant.now();
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, java.util.List.of(),
        "Alice <alice@example.org>", "Initial commit",
        now.minus(2, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, java.util.List.of(commit1Id),
        "Bob <bob@example.org>", "Fix bug",
        now.minus(1, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, java.util.List.of(commit2Id),
        "Alice <alice@example.org>", "Add new feature",
        now, 42);

    commitRepository.save(DATASET_NAME, commit1, null);
    commitRepository.save(DATASET_NAME, commit2, null);
    commitRepository.save(DATASET_NAME, commit3, null);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.has("commits")).isTrue();
    assertThat(json.get("commits").isArray()).isTrue();
    assertThat(json.get("commits").size()).isEqualTo(3);

    // Verify sorted by timestamp descending (newest first)
    assertThat(json.get("commits").get(0).get("id").asText()).isEqualTo(commit3Id.value());
    assertThat(json.get("commits").get(1).get("id").asText()).isEqualTo(commit2Id.value());
    assertThat(json.get("commits").get(2).get("id").asText()).isEqualTo(commit1Id.value());

    // Verify commit details
    JsonNode firstCommit = json.get("commits").get(0);
    assertThat(firstCommit.get("message").asText()).isEqualTo("Add new feature");
    assertThat(firstCommit.get("author").asText()).isEqualTo("Alice <alice@example.org>");
    assertThat(firstCommit.get("patchSize").asInt()).isEqualTo(42);
    assertThat(firstCommit.get("parents").isArray()).isTrue();
    assertThat(firstCommit.get("parents").size()).isEqualTo(1);
    assertThat(firstCommit.get("parents").get(0).asText()).isEqualTo(commit2Id.value());

    // Verify pagination metadata
    assertThat(json.has("pagination")).isTrue();
    assertThat(json.get("pagination").get("limit").asInt()).isEqualTo(100);
    assertThat(json.get("pagination").get("offset").asInt()).isEqualTo(0);
    assertThat(json.get("pagination").get("hasMore").asBoolean()).isFalse();
  }

  @Test
  void listHistory_withBranchFilter_shouldReturnOnlyReachableCommits() throws Exception {
    // Given: Create a commit history with two branches
    //        main:    commit1 -> commit2 -> commit3
    //        feature: commit1 -> commit4
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();
    CommitId commit4Id = CommitId.generate();

    Instant now = Instant.now();
    Commit commit1 = new Commit(commit1Id, java.util.List.of(),
        "Alice <alice@example.org>", "Initial commit",
        now.minus(3, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, java.util.List.of(commit1Id),
        "Bob <bob@example.org>", "Main branch commit",
        now.minus(2, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, java.util.List.of(commit2Id),
        "Alice <alice@example.org>", "Another main commit",
        now.minus(1, ChronoUnit.HOURS), 20);
    Commit commit4 = new Commit(commit4Id, java.util.List.of(commit1Id),
        "Charlie <charlie@example.org>", "Feature branch commit",
        now, 25);

    commitRepository.save(DATASET_NAME, commit1, null);
    commitRepository.save(DATASET_NAME, commit2, null);
    commitRepository.save(DATASET_NAME, commit3, null);
    commitRepository.save(DATASET_NAME, commit4, null);

    // Create branches
    branchRepository.save(DATASET_NAME, new Branch("main", commit3Id));
    branchRepository.save(DATASET_NAME, new Branch("feature", commit4Id));

    // When: Query main branch history
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&branch=main",
        String.class
    );

    // Then: Should only return commits reachable from main (commit1, commit2, commit3)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(3);
    assertThat(json.get("commits").get(0).get("id").asText()).isEqualTo(commit3Id.value());
    assertThat(json.get("commits").get(1).get("id").asText()).isEqualTo(commit2Id.value());
    assertThat(json.get("commits").get(2).get("id").asText()).isEqualTo(commit1Id.value());

    // Verify commit4 (from feature branch) is not included
    boolean hasCommit4 = false;
    for (JsonNode commit : json.get("commits")) {
      if (commit.get("id").asText().equals(commit4Id.value())) {
        hasCommit4 = true;
        break;
      }
    }
    assertThat(hasCommit4).isFalse();
  }

  @Test
  void listHistory_withDateFilters_shouldFilterCorrectly() throws Exception {
    // Given: Create commits with different timestamps
    Instant now = Instant.now();
    Instant yesterday = now.minus(1, ChronoUnit.DAYS);
    Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
    Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();
    CommitId commit4Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, java.util.List.of(),
        "Alice <alice@example.org>", "Commit 1", threeDaysAgo, 10);
    Commit commit2 = new Commit(commit2Id, java.util.List.of(commit1Id),
        "Alice <alice@example.org>", "Commit 2", twoDaysAgo, 15);
    Commit commit3 = new Commit(commit3Id, java.util.List.of(commit2Id),
        "Alice <alice@example.org>", "Commit 3", yesterday, 20);
    Commit commit4 = new Commit(commit4Id, java.util.List.of(commit3Id),
        "Alice <alice@example.org>", "Commit 4", now, 25);

    commitRepository.save(DATASET_NAME, commit1, null);
    commitRepository.save(DATASET_NAME, commit2, null);
    commitRepository.save(DATASET_NAME, commit3, null);
    commitRepository.save(DATASET_NAME, commit4, null);

    // When: Query with since filter (last 2 days)
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&since=" + twoDaysAgo.toString(),
        String.class
    );

    // Then: Should return commits from the last 2 days (commit2, commit3, commit4)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(3);
    assertThat(json.get("commits").get(0).get("id").asText()).isEqualTo(commit4Id.value());
    assertThat(json.get("commits").get(1).get("id").asText()).isEqualTo(commit3Id.value());
    assertThat(json.get("commits").get(2).get("id").asText()).isEqualTo(commit2Id.value());
  }

  @Test
  void listHistory_withAuthorFilter_shouldFilterCorrectly() throws Exception {
    // Given: Create commits with different authors
    Instant now = Instant.now();
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, java.util.List.of(),
        "Alice <alice@example.org>", "Alice's commit 1",
        now.minus(2, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, java.util.List.of(commit1Id),
        "Bob <bob@example.org>", "Bob's commit",
        now.minus(1, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, java.util.List.of(commit2Id),
        "Alice <alice@example.org>", "Alice's commit 2",
        now, 20);

    commitRepository.save(DATASET_NAME, commit1, null);
    commitRepository.save(DATASET_NAME, commit2, null);
    commitRepository.save(DATASET_NAME, commit3, null);

    // When: Query with author filter
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&author=Alice <alice@example.org>",
        String.class
    );

    // Then: Should only return Alice's commits
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(2);
    assertThat(json.get("commits").get(0).get("id").asText()).isEqualTo(commit3Id.value());
    assertThat(json.get("commits").get(1).get("id").asText()).isEqualTo(commit1Id.value());

    // Verify all returned commits are from Alice
    for (JsonNode commit : json.get("commits")) {
      assertThat(commit.get("author").asText()).isEqualTo("Alice <alice@example.org>");
    }
  }

  @Test
  void listHistory_withPagination_shouldReturnLinkHeader() throws Exception {
    // Given: Create 150 commits
    Instant now = Instant.now();
    for (int i = 0; i < 150; i++) {
      CommitId commitId = CommitId.generate();
      Commit commit = new Commit(commitId, java.util.List.of(),
          "Alice <alice@example.org>", "Commit " + i,
          now.minus(150 - i, ChronoUnit.MINUTES), 10);
      commitRepository.save(DATASET_NAME, commit, null);
    }

    // When: Request first page (limit=100)
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&limit=100&offset=0",
        String.class
    );

    // Then: Should return 100 commits with hasMore=true and Link header
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(100);
    assertThat(json.get("pagination").get("hasMore").asBoolean()).isTrue();
    assertThat(json.get("pagination").get("limit").asInt()).isEqualTo(100);
    assertThat(json.get("pagination").get("offset").asInt()).isEqualTo(0);

    // Verify Link header (RFC 5988)
    assertThat(response.getHeaders().containsKey("Link")).isTrue();
    String linkHeader = response.getHeaders().getFirst("Link");
    assertThat(linkHeader).contains("offset=100");
    assertThat(linkHeader).contains("limit=100");
    assertThat(linkHeader).contains("rel=\"next\"");
  }

  @Test
  void listHistory_secondPage_shouldReturnRemainingCommits() throws Exception {
    // Given: Create 150 commits
    Instant now = Instant.now();
    for (int i = 0; i < 150; i++) {
      CommitId commitId = CommitId.generate();
      Commit commit = new Commit(commitId, java.util.List.of(),
          "Alice <alice@example.org>", "Commit " + i,
          now.minus(150 - i, ChronoUnit.MINUTES), 10);
      commitRepository.save(DATASET_NAME, commit, null);
    }

    // When: Request second page (offset=100, limit=100)
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&limit=100&offset=100",
        String.class
    );

    // Then: Should return 50 commits with hasMore=false and no Link header
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(50);
    assertThat(json.get("pagination").get("hasMore").asBoolean()).isFalse();
    assertThat(json.get("pagination").get("limit").asInt()).isEqualTo(100);
    assertThat(json.get("pagination").get("offset").asInt()).isEqualTo(100);

    // Verify no Link header when hasMore=false
    assertThat(response.getHeaders().containsKey("Link")).isFalse();
  }

  @Test
  void listHistory_missingDatasetParameter_shouldReturn400() {
    // When: Call without dataset parameter
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history",
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listHistory_nonExistentBranch_shouldReturn404() {
    // When: Query with non-existent branch
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&branch=non-existent",
        String.class
    );

    // Then: Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void listHistory_emptyDataset_shouldReturnEmptyArray() throws Exception {
    // When: Query dataset with no commits
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME,
        String.class
    );

    // Then: Should return empty array with 200 OK
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("commits").size()).isEqualTo(0);
    assertThat(json.get("pagination").get("hasMore").asBoolean()).isFalse();
  }

  @Test
  void listHistory_invalidDateFormat_shouldReturn400() {
    // When: Query with invalid date format
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/history?dataset=" + DATASET_NAME + "&since=invalid-date",
        String.class
    );

    // Then: Should return 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
