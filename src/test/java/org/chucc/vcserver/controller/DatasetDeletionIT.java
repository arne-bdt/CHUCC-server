package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for dataset deletion endpoint.
 *
 * <p>Verifies that the DELETE /version/datasets/{name} endpoint:
 * <ul>
 *   <li>Deletes datasets with confirmation (204 No Content)
 *   <li>Requires explicit confirmation (400 Bad Request)
 *   <li>Returns 404 for non-existent datasets
 *   <li>Publishes DatasetDeletedEvent to Kafka
 *   <li>Projects deletion to repositories (branches, commits)
 * </ul>
 *
 * <p><strong>Note:</strong> This test explicitly enables the ReadModelProjector
 * via {@code @TestPropertySource(properties = "projector.kafka-listener.enabled=true")}
 * because projector is disabled by default in integration tests for test isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class DatasetDeletionIT {

  private static final String TEST_DATASET = "test-deletion-dataset";

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
  private EventPublisher eventPublisher;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private KafkaProperties kafkaProperties;

  /**
   * Set up test environment with Kafka topic.
   */
  @BeforeEach
  void setUpWithKafka() throws Exception {
    // Clean up repositories
    branchRepository.deleteAllByDataset(TEST_DATASET);
    commitRepository.deleteAllByDataset(TEST_DATASET);

    // Ensure Kafka topic exists
    ensureTopicExists(TEST_DATASET);
  }

  /**
   * Ensures the Kafka topic exists for the given dataset.
   *
   * @param dataset the dataset name
   * @throws Exception if topic creation fails
   */
  private void ensureTopicExists(String dataset) throws Exception {
    String topicName = kafkaProperties.getTopicName(dataset);

    Map<String, Object> config = Map.of(
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
    );

    try (AdminClient adminClient = AdminClient.create(config)) {
      NewTopic newTopic = new NewTopic(
          topicName,
          kafkaProperties.getPartitions(),
          kafkaProperties.getReplicationFactor()
      );

      adminClient.createTopics(Collections.singletonList(newTopic)).all().get();
    } catch (Exception e) {
      // Topic might already exist
      if (!e.getMessage().contains("TopicExistsException")) {
        throw e;
      }
    }

    // Give Kafka listener time to discover the new topic
    Thread.sleep(1000);
  }

  /**
   * Test that DELETE with confirmation returns 204 and deletes all dataset data.
   */
  @Test
  void deleteDataset_withConfirmation_shouldReturnNoContentAndDeleteAllData() throws Exception {
    // Given - Create dataset with multiple branches
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();

    eventPublisher.publish(new BranchCreatedEvent(
        TEST_DATASET, "main", commit1.value(), Instant.now())).get();
    eventPublisher.publish(new BranchCreatedEvent(
        TEST_DATASET, "feature", commit2.value(), Instant.now())).get();

    // Wait for branches to be created
    await().atMost(Duration.ofSeconds(10))
        .until(() -> branchRepository.findAllByDataset(TEST_DATASET).size() == 2);

    // When - Delete dataset via HTTP with confirmation
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Author", "test-admin");

    ResponseEntity<Void> response = restTemplate.exchange(
        "/version/datasets/" + TEST_DATASET + "?confirmed=true",
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        Void.class
    );

    // Then - Verify HTTP response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Wait for projection to delete all branches
    await().atMost(Duration.ofSeconds(10))
        .until(() -> branchRepository.findAllByDataset(TEST_DATASET).isEmpty());

    // Verify all data deleted
    assertThat(branchRepository.findAllByDataset(TEST_DATASET)).isEmpty();
    assertThat(commitRepository.findAllByDataset(TEST_DATASET)).isEmpty();
  }

  /**
   * Test that DELETE without confirmation returns 400.
   */
  @Test
  void deleteDataset_withoutConfirmation_shouldReturn400() throws Exception {
    // Given - Create dataset with a branch
    CommitId commitId = CommitId.generate();
    eventPublisher.publish(new BranchCreatedEvent(
        TEST_DATASET, "main", commitId.value(), Instant.now())).get();

    await().atMost(Duration.ofSeconds(10))
        .until(() -> branchRepository.findAllByDataset(TEST_DATASET).size() == 1);

    // When - Try to delete without confirmation
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Author", "test-admin");

    ResponseEntity<String> response = restTemplate.exchange(
        "/version/datasets/" + TEST_DATASET + "?confirmed=false",
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        String.class
    );

    // Then - Verify HTTP response is 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Verify dataset still exists
    assertThat(branchRepository.findAllByDataset(TEST_DATASET)).hasSize(1);
  }

  /**
   * Test that DELETE on non-existent dataset returns 404.
   */
  @Test
  void deleteDataset_nonExistent_shouldReturn404() {
    // When - Try to delete non-existent dataset
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Author", "test-admin");

    ResponseEntity<String> response = restTemplate.exchange(
        "/version/datasets/non-existent-dataset?confirmed=true",
        HttpMethod.DELETE,
        new HttpEntity<>(headers),
        String.class
    );

    // Then - Verify HTTP response is 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
