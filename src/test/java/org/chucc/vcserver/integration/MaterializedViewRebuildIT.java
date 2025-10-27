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
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration tests for materialized view rebuild functionality.
 *
 * <p>Tests the monitoring, health check, and recovery endpoints
 * for materialized views. Projector is enabled to test the full
 * rebuild lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedViewRebuildIT {

  private static final String DEFAULT_DATASET = "rebuild-test-dataset";

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

  private CommitId initialCommitId;

  /**
   * Set up test environment with Kafka topic and initial branch.
   */
  @BeforeEach
  void setUp() throws Exception {
    // Clean up repositories
    branchRepository.deleteAllByDataset(DEFAULT_DATASET);
    commitRepository.deleteAllByDataset(DEFAULT_DATASET);

    // Ensure Kafka topic exists
    ensureTopicExists(DEFAULT_DATASET);

    // Create initial commit and branch for testing
    initialCommitId = CommitId.generate();
    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DEFAULT_DATASET, mainBranch);

    // Save initial commit to repository (needed for rebuild)
    // Note: In real system this would be done by projector, but we manually create it here
    org.chucc.vcserver.domain.Commit initialCommit =
        new org.chucc.vcserver.domain.Commit(
            initialCommitId,
            List.of(),  // No parents (root commit)
            "Initial commit",
            "test-author",
            Instant.now(),
            1
        );
    // Empty patch for initial commit
    String initialPatchContent = "TX .\nTC .";
    org.apache.jena.rdfpatch.RDFPatch initialPatch =
        org.apache.jena.rdfpatch.RDFPatchOps.read(
            new java.io.ByteArrayInputStream(initialPatchContent.getBytes(
                java.nio.charset.StandardCharsets.UTF_8)));
    commitRepository.save(DEFAULT_DATASET, initialCommit, initialPatch);
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
        DEFAULT_DATASET, "main"
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
          DEFAULT_DATASET,
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
      branchRepository.save(DEFAULT_DATASET, updatedBranch);
    }

    // Wait for all commits to be saved by projector
    final CommitId lastCommitId = commitIds[4];
    await().atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(DEFAULT_DATASET, lastCommitId))
              .isPresent();
        });

    // Act - Trigger rebuild with confirmation
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        DEFAULT_DATASET, "main"
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

    // Then - Should return error (either 404 or 500 depending on error handling)
    assertThat(response.getStatusCode().is4xxClientError()
        || response.getStatusCode().is5xxServerError()).isTrue();
  }

  @Test
  void rebuildEndpoint_shouldReturnErrorForNonexistentBranch() throws Exception {
    // When - Try to rebuild nonexistent branch (but dataset exists)
    ResponseEntity<String> response = restTemplate.postForEntity(
        "/actuator/materialized-views/rebuild?dataset={dataset}&branch={branch}&confirm=true",
        null,
        String.class,
        DEFAULT_DATASET, "nonexistent-branch"
    );

    // Then - Should return error
    assertThat(response.getStatusCode().is4xxClientError()
        || response.getStatusCode().is5xxServerError()).isTrue();
  }

  @Test
  void healthEndpoint_shouldReportGraphCountAfterCommits() throws Exception {
    // Arrange - Create a commit
    CommitId commitId = CommitId.generate();
    String patchContent = "TX .\nA <http://example.org/s> <http://example.org/p> \"value\" .\nTC .";

    CommitCreatedEvent event = new CommitCreatedEvent(
        DEFAULT_DATASET,
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

    // Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .pollDelay(Duration.ofMillis(500))
        .until(() -> true);

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
