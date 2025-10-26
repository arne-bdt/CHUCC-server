package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.health.AllTopicsHealthReport;
import org.chucc.vcserver.health.HealingResult;
import org.chucc.vcserver.health.TopicHealthReport;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for Kafka health check and healing endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
class KafkaHealthControllerIT {

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
  void setUp() throws Exception {
    // Clean up repository
    branchRepository.deleteAllByDataset("test-health");

    // Clean up Kafka topics
    deleteAllTopics();
  }

  /**
   * Deletes all Kafka topics to ensure test isolation.
   */
  private void deleteAllTopics() throws Exception {
    Map<String, Object> config = Map.of(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
    );

    try (AdminClient adminClient = AdminClient.create(config)) {
      var topics = adminClient.listTopics().names().get();
      if (!topics.isEmpty()) {
        adminClient.deleteTopics(topics).all().get();
      }
    }
  }

  @Test
  void getDetailedHealth_withNoDatasets_shouldReturnHealthy() {
    // Act
    ResponseEntity<AllTopicsHealthReport> response = restTemplate.getForEntity(
        "/actuator/kafka/health-detailed",
        AllTopicsHealthReport.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    AllTopicsHealthReport report = response.getBody();
    assertThat(report).isNotNull();
    assertThat(report.totalDatasets()).isZero();
    assertThat(report.overallHealthy()).isTrue();
  }

  @Test
  void getTopicHealth_topicExists_shouldReturnHealthy() throws Exception {
    // Arrange - Create dataset with topic
    createDatasetWithTopic("test-health");

    // Act
    ResponseEntity<TopicHealthReport> response = restTemplate.getForEntity(
        "/actuator/kafka/topics/test-health/health",
        TopicHealthReport.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TopicHealthReport report = response.getBody();
    assertThat(report).isNotNull();
    assertThat(report.exists()).isTrue();
    assertThat(report.healthy()).isTrue();
    assertThat(report.topicName()).isEqualTo("vc.test-health.events");
  }

  @Test
  void getTopicHealth_topicMissing_shouldReturnUnhealthy() {
    // Arrange - Create dataset without topic
    createDatasetWithoutTopic("test-health");

    // Act
    ResponseEntity<TopicHealthReport> response = restTemplate.getForEntity(
        "/actuator/kafka/topics/test-health/health",
        TopicHealthReport.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TopicHealthReport report = response.getBody();
    assertThat(report).isNotNull();
    assertThat(report.exists()).isFalse();
    assertThat(report.healthy()).isFalse();
    assertThat(report.message()).contains("does not exist");
  }

  @Test
  void healTopic_withoutConfirmation_shouldReturnBadRequest() {
    // Arrange
    createDatasetWithoutTopic("test-health");

    // Act - Attempt healing without confirmation
    ResponseEntity<HealingResult> response = restTemplate.postForEntity(
        "/actuator/kafka/topics/test-health/heal",
        null,
        HealingResult.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    HealingResult result = response.getBody();
    assertThat(result).isNotNull();
    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("Confirmation required");
  }

  @Test
  void healTopic_missingTopic_shouldRecreateTopic() throws Exception {
    // Arrange - Create dataset without topic
    createDatasetWithoutTopic("test-health");

    // Act - Heal with confirmation
    ResponseEntity<HealingResult> response = restTemplate.postForEntity(
        "/actuator/kafka/topics/test-health/heal?confirm=true",
        null,
        HealingResult.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    HealingResult result = response.getBody();
    assertThat(result).isNotNull();
    assertThat(result.success()).isTrue();
    assertThat(result.actions()).isNotEmpty();
    assertThat(result.actions().get(0)).contains("Recreated missing topic");

    // Verify topic was created
    verifyTopicExists("vc.test-health.events");
  }

  @Test
  void healTopic_topicAlreadyExists_shouldReturnNoActionNeeded() throws Exception {
    // Arrange - Create dataset with topic
    createDatasetWithTopic("test-health");

    // Act - Attempt healing
    ResponseEntity<HealingResult> response = restTemplate.postForEntity(
        "/actuator/kafka/topics/test-health/heal?confirm=true",
        null,
        HealingResult.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    HealingResult result = response.getBody();
    assertThat(result).isNotNull();
    assertThat(result.success()).isTrue();
    assertThat(result.actions()).hasSize(1);
    assertThat(result.actions().get(0)).contains("No action needed");
  }

  /**
   * Creates a dataset with branches but without a Kafka topic.
   */
  private void createDatasetWithoutTopic(String dataset) {
    // Create branch without creating topic
    org.chucc.vcserver.domain.Branch branch = new org.chucc.vcserver.domain.Branch(
        "main",
        org.chucc.vcserver.domain.CommitId.generate()
    );
    branchRepository.save(dataset, branch);
  }

  /**
   * Creates a dataset with both branches and a Kafka topic.
   */
  private void createDatasetWithTopic(String dataset) throws Exception {
    // Create branch
    createDatasetWithoutTopic(dataset);

    // Create topic
    String topicName = "vc." + dataset + ".events";
    Map<String, Object> config = Map.of(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
    );

    try (AdminClient adminClient = AdminClient.create(config)) {
      NewTopic newTopic = new NewTopic(topicName, 3, (short) 1);
      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
    }
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
