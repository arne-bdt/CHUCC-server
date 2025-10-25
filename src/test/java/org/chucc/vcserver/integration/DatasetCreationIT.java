package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.chucc.vcserver.dto.CreateDatasetRequest;
import org.chucc.vcserver.dto.DatasetCreationResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for dataset creation with Kafka topic management.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
class DatasetCreationIT {

  @Container
  private static KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private BranchRepository branchRepository;

  @BeforeEach
  void setUp() {
    // Clean up any existing datasets
    branchRepository.deleteAllByDataset("test-dataset");
  }

  @Test
  void createDataset_shouldReturn202AndCreateKafkaTopic() throws Exception {
    // Arrange
    CreateDatasetRequest request = new CreateDatasetRequest(
        "Test dataset for integration testing",
        null
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");

    HttpEntity<CreateDatasetRequest> httpEntity = new HttpEntity<>(request, headers);

    // Act
    ResponseEntity<DatasetCreationResponse> response = restTemplate.postForEntity(
        "/version/datasets/test-dataset",
        httpEntity,
        DatasetCreationResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getFirst("SPARQL-VC-Status")).isEqualTo("pending");

    DatasetCreationResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.name()).isEqualTo("test-dataset");
    assertThat(body.description()).isEqualTo("Test dataset for integration testing");
    assertThat(body.mainBranch()).isEqualTo("main");
    assertThat(body.initialCommitId()).isNotNull();
    assertThat(body.kafkaTopic()).isEqualTo("vc.test-dataset.events");
    assertThat(body.timestamp()).isNotNull();

    // Verify Kafka topic was created
    verifyTopicExists("vc.test-dataset.events");
  }

  @Test
  void createDataset_withInvalidName_shouldReturn400() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser");

    HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

    // Act - Use invalid character (@) in dataset name
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/version/datasets/invalid@name",
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void createDataset_duplicate_shouldReturn409() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Author", "TestUser");

    HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

    // Act - Create first time
    ResponseEntity<DatasetCreationResponse> firstResponse = restTemplate.postForEntity(
        "/version/datasets/duplicate-test",
        httpEntity,
        DatasetCreationResponse.class
    );

    // Act - Try to create again
    ResponseEntity<String> secondResponse = restTemplate.postForEntity(
        "/version/datasets/duplicate-test",
        httpEntity,
        String.class
    );

    // Assert
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  /**
   * Verifies that a Kafka topic exists.
   */
  private void verifyTopicExists(String topicName) throws Exception {
    Map<String, Object> config = Map.of(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
    );

    try (AdminClient adminClient = AdminClient.create(config)) {
      var topics = adminClient.listTopics().names().get();
      assertThat(topics).contains(topicName);
    }
  }
}
