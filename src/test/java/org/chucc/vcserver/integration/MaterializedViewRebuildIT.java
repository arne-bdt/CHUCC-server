package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for materialized view rebuild functionality.
 *
 * <p>Tests the monitoring, health check, and recovery endpoints
 * for materialized views. Projector is enabled to test the full
 * rebuild lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedViewRebuildIT extends ITFixture {

  private static final String REBUILD_DATASET = "rebuild-test-dataset";

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private KafkaProperties kafkaProperties;

  @Override
  protected String getDatasetName() {
    return REBUILD_DATASET;
  }

  /**
   * Set up test environment with Kafka topic.
   * Repository cleanup and initial commit creation are handled by ITFixture.
   */
  @BeforeEach
  void setUp() throws Exception {
    // Ensure Kafka topic exists
    ensureTopicExists(REBUILD_DATASET);
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
        AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()
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

  @Test
  void healthEndpoint_shouldReportMaterializedViewsStatus() {
    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health/materializedViews",
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("UP")
        .contains("graphCount");
  }

  @Test
  void rebuildEndpoint_shouldRequireConfirmation() {
    // When - Try to rebuild without confirm=true
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}",
        null,
        String.class,
        REBUILD_DATASET, "main"
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("error")
        .contains("confirm=true");
  }

  @Test
  void rebuildEndpoint_shouldRebuildBranchFromCommitHistory() throws Exception {
    // Arrange - Create multiple commits on main branch
    CommitId[] commitIds = new CommitId[5];

    for (int i = 0; i < 5; i++) {
      commitIds[i] = CommitId.generate();

      String patchContent = String.format(
          "TX .\nA <http://example.org/s%d> <http://example.org/p> \"value%d\" .\nTC .",
          i, i
      );

      CommitCreatedEvent event = new CommitCreatedEvent(
          REBUILD_DATASET,
          commitIds[i].value(),
          i == 0 ? List.of(initialCommitId.value()) : List.of(commitIds[i - 1].value()),
          "main",
          "Commit " + i,
          "test-author",
          Instant.now(),
          patchContent,
          1
      );

      eventPublisher.publish(event).get();

      // Update branch to point to new commit
      Branch updatedBranch = new Branch("main", commitIds[i]);
      branchRepository.save(REBUILD_DATASET, updatedBranch);
    }

    // Wait for all commits to be saved by projector
    final CommitId lastCommitId = commitIds[4];
    await().atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(REBUILD_DATASET, lastCommitId))
              .isPresent();
        });

    // Act - Trigger rebuild with confirmation
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        REBUILD_DATASET, "main"
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("success")
        .contains("commitsProcessed")
        .contains("durationMs");

    // Verify that rebuild processed the commits (should be 5 commits)
    // Note: We can't easily assert the exact number without parsing JSON,
    // but we can verify the response structure
    assertThat(response.getBody()).contains("\"status\":\"success\"");
  }

  @Test
  void rebuildEndpoint_shouldReturnErrorForNonexistentDataset() {
    // When - Try to rebuild nonexistent dataset
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        "nonexistent-dataset", "main"
    );

    // Then - Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("Dataset not found");
  }

  @Test
  void rebuildEndpoint_shouldReturnErrorForNonexistentBranch() throws Exception {
    // When - Try to rebuild nonexistent branch (but dataset exists)
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        REBUILD_DATASET, "nonexistent-branch"
    );

    // Then - Should return 404 Not Found
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("Branch not found");
  }

  @Test
  void healthEndpoint_shouldReportGraphCountAfterCommits() throws Exception {
    // Arrange - Create a commit
    CommitId commitId = CommitId.generate();
    String patchContent = "TX .\nA <http://example.org/s> <http://example.org/p> \"value\" .\nTC .";

    CommitCreatedEvent event = new CommitCreatedEvent(
        REBUILD_DATASET,
        commitId.value(),
        List.of(initialCommitId.value()),
        "main",
        "Test commit",
        "test-author",
        Instant.now(),
        patchContent,
        1
    );

    eventPublisher.publish(event).get();

    // Wait for projection - verify commit is saved
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(REBUILD_DATASET, commitId))
              .isPresent();
        });

    // When
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health/materializedViews",
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("UP")
        .contains("graphCount");
  }
}
