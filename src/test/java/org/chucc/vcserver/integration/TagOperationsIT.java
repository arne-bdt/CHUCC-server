package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for tag detail and deletion operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class TagOperationsIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private TagRepository tagRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private VersionControlProperties vcProperties;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final String DATASET_NAME = "test-dataset";
  private static final String TAG_NAME = "v1.0.0";

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
   * Set up test data.
   */
  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    tagRepository.deleteAllByDataset(DATASET_NAME);
    branchRepository.deleteAllByDataset(DATASET_NAME);
    commitRepository.deleteAllByDataset(DATASET_NAME);

    // Create initial commit and branch for testing
    CommitId initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        List.of(),
        "System",
        "Initial commit",
        Instant.now(),
        0
    );
    commitRepository.save(DATASET_NAME, initialCommit, RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DATASET_NAME, mainBranch);

    // Ensure tag deletion is allowed by default
    vcProperties.setTagDeletionAllowed(true);
  }

  /**
   * Test GET /version/tags/{name} with existing tag.
   */
  @Test
  void testGetTag_whenTagExists_returns200WithDetails() throws Exception {
    // Create a test tag
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag(TAG_NAME, commitId);
    tagRepository.save(DATASET_NAME, tag);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("name").asText()).isEqualTo(TAG_NAME);
    assertThat(json.get("target").asText()).isEqualTo(commitId.value());
  }

  /**
   * Test GET /version/tags/{name} with non-existent tag.
   */
  @Test
  void testGetTag_whenTagDoesNotExist_returns404() throws Exception {
    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags/nonexistent",
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(404);
    assertThat(json.get("code").asText()).isEqualTo("tag_not_found");
  }

  /**
   * Test DELETE /version/tags/{name} successfully.
   */
  @Test
  void testDeleteTag_whenTagExists_returns204AndRemovesTag() throws Exception {
    // Create a test tag
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag(TAG_NAME, commitId);
    tagRepository.save(DATASET_NAME, tag);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        HttpMethod.DELETE,
        null,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Verify tag was deleted
    ResponseEntity<String> getResponse = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        String.class
    );
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test DELETE /version/tags/{name} with non-existent tag.
   */
  @Test
  void testDeleteTag_whenTagDoesNotExist_returns404() throws Exception {
    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags/nonexistent",
        HttpMethod.DELETE,
        null,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(404);
    assertThat(json.get("code").asText()).isEqualTo("tag_not_found");
  }

  /**
   * Test DELETE /version/tags/{name} when policy forbids deletion.
   */
  @Test
  void testDeleteTag_whenPolicyForbids_returns403() throws Exception {
    // Create a test tag
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag(TAG_NAME, commitId);
    tagRepository.save(DATASET_NAME, tag);

    // Disable tag deletion
    vcProperties.setTagDeletionAllowed(false);

    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        HttpMethod.DELETE,
        null,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(403);
    assertThat(json.get("code").asText()).isEqualTo("tag_deletion_forbidden");

    // Verify tag still exists
    assertThat(tagRepository.exists(DATASET_NAME, TAG_NAME)).isTrue();
  }

  /**
   * Test complete workflow: create, retrieve, delete, verify.
   */
  @Test
  void testTagLifecycle_createRetrieveDeleteVerify() throws Exception {
    // Create a test tag
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag(TAG_NAME, commitId);
    tagRepository.save(DATASET_NAME, tag);

    // Retrieve tag details
    ResponseEntity<String> getResponse = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        String.class
    );
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(getResponse.getBody());
    assertThat(json.get("name").asText()).isEqualTo(TAG_NAME);
    assertThat(json.get("target").asText()).isEqualTo(commitId.value());

    // Delete tag
    ResponseEntity<String> deleteResponse = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        HttpMethod.DELETE,
        null,
        String.class
    );
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Verify tag no longer exists
    ResponseEntity<String> verifyResponse = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags/" + TAG_NAME,
        String.class
    );
    assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /**
   * Test GET /version/tags returns empty list when no tags exist.
   */
  @Test
  void listTags_whenNoTagsExist_shouldReturn200WithEmptyList() throws Exception {
    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("tags").isArray()).isTrue();
    assertThat(json.get("tags").size()).isEqualTo(0);
  }

  /**
   * Test GET /version/tags returns all tags.
   */
  @Test
  void listTags_whenTagsExist_shouldReturn200WithAllTags() throws Exception {
    // Arrange: Create test tags
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    CommitId commit3 = CommitId.generate();

    Commit c1 = new Commit(commit1, List.of(), "Alice", "Commit 1", Instant.now(), 5);
    Commit c2 = new Commit(commit2, List.of(), "Bob", "Commit 2", Instant.now(), 10);
    Commit c3 = new Commit(commit3, List.of(), "Charlie", "Commit 3", Instant.now(), 15);

    commitRepository.save(DATASET_NAME, c1, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, c2, RDFPatchOps.emptyPatch());
    commitRepository.save(DATASET_NAME, c3, RDFPatchOps.emptyPatch());

    Tag tag1 = new Tag("v1.0.0", commit1, "Release 1.0.0", "Alice", Instant.now());
    Tag tag2 = new Tag("v1.1.0", commit2, null, "Bob", Instant.now());
    Tag tag3 = new Tag("beta", commit3, "Beta release", "Charlie", Instant.now());

    tagRepository.save(DATASET_NAME, tag1);
    tagRepository.save(DATASET_NAME, tag2);
    tagRepository.save(DATASET_NAME, tag3);

    // Act
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/" + DATASET_NAME + "/version/tags",
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("tags").isArray()).isTrue();
    assertThat(json.get("tags").size()).isEqualTo(3);

    // Verify tag details
    boolean foundV1 = false;
    boolean foundV11 = false;
    boolean foundBeta = false;

    for (JsonNode tag : json.get("tags")) {
      String name = tag.get("name").asText();
      if (name.equals("v1.0.0")) {
        foundV1 = true;
        assertThat(tag.get("target").asText()).isEqualTo(commit1.value());
        assertThat(tag.get("message").asText()).isEqualTo("Release 1.0.0");
        assertThat(tag.get("author").asText()).isEqualTo("Alice");
      } else if (name.equals("v1.1.0")) {
        foundV11 = true;
        assertThat(tag.get("target").asText()).isEqualTo(commit2.value());
        assertThat(tag.has("message")).isFalse(); // null values excluded
        assertThat(tag.get("author").asText()).isEqualTo("Bob");
      } else if (name.equals("beta")) {
        foundBeta = true;
        assertThat(tag.get("target").asText()).isEqualTo(commit3.value());
      }
    }

    assertThat(foundV1).isTrue();
    assertThat(foundV11).isTrue();
    assertThat(foundBeta).isTrue();
  }

  /**
   * Test POST /version/tags creates a new tag successfully.
   */
  @Test
  void createTag_withValidData_shouldReturn202AndCreateTag() throws Exception {
    // Arrange: Create a test commit
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    String requestBody = """
        {
          "name": "v2.0.0",
          "target": "%s",
          "message": "Major release",
          "author": "Alice <alice@example.org>"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().toString())
        .endsWith("/" + DATASET_NAME + "/version/tags/v2.0.0");
    assertThat(response.getHeaders().getFirst("SPARQL-VC-Status")).isEqualTo("pending");

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("name").asText()).isEqualTo("v2.0.0");
    assertThat(json.get("target").asText()).isEqualTo(targetCommit.value());
    assertThat(json.get("message").asText()).isEqualTo("Major release");
    assertThat(json.get("author").asText()).isEqualTo("Alice <alice@example.org>");
    assertThat(json.has("createdAt")).isTrue();
  }

  /**
   * Test POST /version/tags with missing required name field.
   */
  @Test
  void createTag_withMissingName_shouldReturn400() throws Exception {
    // Arrange
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    String requestBody = """
        {
          "target": "%s"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
  }

  /**
   * Test POST /version/tags with invalid tag name format.
   */
  @Test
  void createTag_withInvalidTagName_shouldReturn400() throws Exception {
    // Arrange
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    String requestBody = """
        {
          "name": "invalid tag name with spaces",
          "target": "%s"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
  }

  /**
   * Test POST /version/tags with non-existent target commit.
   */
  @Test
  void createTag_withNonExistentCommit_shouldReturn404() throws Exception {
    // Arrange
    String requestBody = """
        {
          "name": "v3.0.0",
          "target": "00000000-0000-0000-0000-000000000000"
        }
        """;

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(404);
  }

  /**
   * Test POST /version/tags with duplicate tag name (immutability enforcement).
   */
  @Test
  void createTag_withDuplicateName_shouldReturn409() throws Exception {
    // Arrange: Create initial commit and tag
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    Tag existingTag = new Tag("v1.0.0", targetCommit);
    tagRepository.save(DATASET_NAME, existingTag);

    String requestBody = """
        {
          "name": "v1.0.0",
          "target": "%s"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(409);
  }

  /**
   * Test POST /version/tags with X-Author header fallback.
   */
  @Test
  void createTag_withAuthorHeader_shouldUseHeaderValue() throws Exception {
    // Arrange
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    String requestBody = """
        {
          "name": "v4.0.0",
          "target": "%s",
          "message": "Release with header author"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Author", "Bob <bob@example.org>");

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("author").asText()).isEqualTo("Bob <bob@example.org>");
  }

  /**
   * Test POST /version/tags with optional message null.
   */
  @Test
  void createTag_withoutMessage_shouldCreateTagWithNullMessage() throws Exception {
    // Arrange
    CommitId targetCommit = CommitId.generate();
    Commit commit = new Commit(targetCommit, List.of(), "Test User", "Test commit", Instant.now(),
        10);
    commitRepository.save(DATASET_NAME, commit, RDFPatchOps.emptyPatch());

    String requestBody = """
        {
          "name": "v5.0.0",
          "target": "%s",
          "author": "Charlie"
        }
        """.formatted(targetCommit.value());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags",
        HttpMethod.POST,
        new HttpEntity<>(requestBody, headers),
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("name").asText()).isEqualTo("v5.0.0");
    assertThat(json.has("message")).isFalse(); // null values excluded
  }
}
