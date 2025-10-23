package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.TagRepository;
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
 * Integration test for GET /version/refs endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class RefsEndpointIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private TagRepository tagRepository;

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
    branchRepository.deleteAllByDataset(DATASET_NAME);
    tagRepository.deleteAllByDataset(DATASET_NAME);
  }

  @Test
  void listRefs_shouldReturnEmptyArray_whenNoRefs() throws Exception {
    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.has("refs")).isTrue();
    assertThat(json.get("refs").isArray()).isTrue();
    assertThat(json.get("refs").size()).isEqualTo(0);
  }

  @Test
  void listRefs_shouldReturnAllBranches() throws Exception {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Branch main = new Branch("main", commitId1);
    Branch develop = new Branch("develop", commitId2);

    branchRepository.save(DATASET_NAME, main);
    branchRepository.save(DATASET_NAME, develop);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("refs").size()).isEqualTo(2);

    // Verify first ref (develop - alphabetically first)
    JsonNode ref1 = json.get("refs").get(0);
    assertThat(ref1.get("type").asText()).isEqualTo("branch");
    assertThat(ref1.get("name").asText()).isEqualTo("develop");
    assertThat(ref1.get("targetCommit").asText()).isEqualTo(commitId2.toString());

    // Verify second ref (main)
    JsonNode ref2 = json.get("refs").get(1);
    assertThat(ref2.get("type").asText()).isEqualTo("branch");
    assertThat(ref2.get("name").asText()).isEqualTo("main");
    assertThat(ref2.get("targetCommit").asText()).isEqualTo(commitId1.toString());
  }

  @Test
  void listRefs_shouldReturnAllTags() throws Exception {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    tagRepository.save(DATASET_NAME, tag1);
    tagRepository.save(DATASET_NAME, tag2);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("refs").size()).isEqualTo(2);

    // Verify both tags
    JsonNode ref1 = json.get("refs").get(0);
    assertThat(ref1.get("type").asText()).isEqualTo("tag");
    assertThat(ref1.get("name").asText()).isEqualTo("v1.0.0");
    assertThat(ref1.get("targetCommit").asText()).isEqualTo(commitId1.toString());

    JsonNode ref2 = json.get("refs").get(1);
    assertThat(ref2.get("type").asText()).isEqualTo("tag");
    assertThat(ref2.get("name").asText()).isEqualTo("v2.0.0");
    assertThat(ref2.get("targetCommit").asText()).isEqualTo(commitId2.toString());
  }

  @Test
  void listRefs_shouldReturnMixedBranchesAndTags() throws Exception {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    CommitId commitId3 = CommitId.generate();

    Branch main = new Branch("main", commitId1);
    Branch develop = new Branch("develop", commitId2);
    Tag tag1 = new Tag("v1.0.0", commitId3);

    branchRepository.save(DATASET_NAME, main);
    branchRepository.save(DATASET_NAME, develop);
    tagRepository.save(DATASET_NAME, tag1);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("refs").size()).isEqualTo(3);

    // Verify branches come first (alphabetically sorted)
    JsonNode ref1 = json.get("refs").get(0);
    assertThat(ref1.get("type").asText()).isEqualTo("branch");
    assertThat(ref1.get("name").asText()).isEqualTo("develop");

    JsonNode ref2 = json.get("refs").get(1);
    assertThat(ref2.get("type").asText()).isEqualTo("branch");
    assertThat(ref2.get("name").asText()).isEqualTo("main");

    // Verify tags come after branches
    JsonNode ref3 = json.get("refs").get(2);
    assertThat(ref3.get("type").asText()).isEqualTo("tag");
    assertThat(ref3.get("name").asText()).isEqualTo("v1.0.0");
  }

  @Test
  void listRefs_shouldNotIncludeMessageField_whenNotSet() throws Exception {
    // Given
    CommitId commitId = CommitId.generate();
    Branch main = new Branch("main", commitId);
    branchRepository.save(DATASET_NAME, main);

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs?dataset=" + DATASET_NAME,
        String.class
    );

    // Then
    JsonNode json = objectMapper.readTree(response.getBody());
    JsonNode ref = json.get("refs").get(0);

    // The message field should not be present when null (due to @JsonInclude.NON_NULL)
    assertThat(ref.has("message")).isFalse();
  }

  @Test
  void listRefs_shouldIsolateDatasets() throws Exception {
    // Given - Create refs in different datasets
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();

    Branch main1 = new Branch("main", commitId1);
    Branch main2 = new Branch("main", commitId2);

    branchRepository.save("dataset1", main1);
    branchRepository.save("dataset2", main2);

    // When - Query dataset1
    ResponseEntity<String> response1 = restTemplate.getForEntity(
        "/version/refs?dataset=dataset1",
        String.class
    );

    // When - Query dataset2
    ResponseEntity<String> response2 = restTemplate.getForEntity(
        "/version/refs?dataset=dataset2",
        String.class
    );

    // Then - Each dataset should have only its own refs
    JsonNode json1 = objectMapper.readTree(response1.getBody());
    assertThat(json1.get("refs").size()).isEqualTo(1);
    assertThat(json1.get("refs").get(0).get("targetCommit").asText())
        .isEqualTo(commitId1.toString());

    JsonNode json2 = objectMapper.readTree(response2.getBody());
    assertThat(json2.get("refs").size()).isEqualTo(1);
    assertThat(json2.get("refs").get(0).get("targetCommit").asText())
        .isEqualTo(commitId2.toString());

    // Clean up
    branchRepository.deleteAllByDataset("dataset1");
    branchRepository.deleteAllByDataset("dataset2");
  }

  @Test
  void listRefs_shouldUseDefaultDataset_whenParamNotProvided() throws Exception {
    // Given - Create a branch in the default dataset
    CommitId commitId = CommitId.generate();
    Branch main = new Branch("main", commitId);
    branchRepository.save("default", main);

    // When - Call without dataset parameter
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/version/refs",
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("refs").size()).isEqualTo(1);
    assertThat(json.get("refs").get(0).get("name").asText()).isEqualTo("main");

    // Clean up
    branchRepository.deleteAllByDataset("default");
  }
}
