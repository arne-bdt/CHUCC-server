package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.TagRepository;
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
    // Clean up tags before each test
    tagRepository.deleteAllByDataset(DATASET_NAME);

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
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
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
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
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
}
