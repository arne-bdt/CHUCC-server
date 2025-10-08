package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.IntegrationTestFixture;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for Graph Store Protocol event projection.
 *
 * <p>Verifies that the ReadModelProjector can handle events from GSP operations:
 * 1. CommitCreatedEvent (from PUT, POST, DELETE, PATCH operations)
 * 2. BatchGraphsCompletedEvent (from batch graph operations)
 *
 * <p>This test manually publishes events to verify projector readiness.
 * When controllers are updated to publish events to Kafka, the complete
 * CQRS flow will work end-to-end.
 *
 * <p>Current state: Command handlers create events, but controllers don't
 * publish them to Kafka yet. This test verifies the projection logic works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
class GraphEventProjectorIT extends IntegrationTestFixture {

  @Container
  static final KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private org.chucc.vcserver.event.EventPublisher eventPublisher;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private KafkaProperties kafkaProperties;

  private static final String PATCH_CONTENT = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"value\" .\n"
      + "TC .";

  @Override
  protected boolean shouldCreateInitialSetup() {
    return false; // We'll create our own setup with Kafka topic
  }

  /**
   * Set up test environment with Kafka topic.
   */
  @BeforeEach
  void setUpWithKafka() throws Exception {
    // Clean up repositories
    branchRepository.deleteAllByDataset(DEFAULT_DATASET);
    commitRepository.deleteAllByDataset(DEFAULT_DATASET);

    // Ensure Kafka topic exists
    ensureTopicExists(DEFAULT_DATASET);

    // Create initial commit and branch for testing
    createInitialCommitAndBranch(DEFAULT_DATASET);
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
          (short) kafkaProperties.getReplicationFactor()
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

  // ========== PUT Graph Event Projection Tests ==========

  /**
   * Test that CommitCreatedEvent (from GSP PUT) is projected correctly.
   */
  @Test
  void putGraphEvent_shouldBeProjected() throws Exception {
    // Given - Create a CommitCreatedEvent (simulating PUT graph operation)
    CommitId commitId = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent event =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        java.util.List.of(initialCommitId.value()),
        "Add graph via PUT",
        "Alice",
        java.time.Instant.now(),
        PATCH_CONTENT
    );

    // When - Publish event to Kafka
    eventPublisher.publish(event).get();

    // Then - Wait for async event projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify commit was saved to repository
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().author()).isEqualTo("Alice");
          assertThat(commit.get().message()).isEqualTo("Add graph via PUT");
          assertThat(commit.get().parents()).containsExactly(initialCommitId);
        });
  }

  /**
   * Test that multiple events are projected in order.
   */
  @Test
  void multipleCommitEvents_shouldBeProjectedInOrder() throws Exception {
    // Given - Create multiple CommitCreatedEvents
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent event1 =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commit1Id.value(),
        java.util.List.of(initialCommitId.value()),
        "Commit 1",
        "Author1",
        java.time.Instant.now(),
        PATCH_CONTENT
    );

    org.chucc.vcserver.event.CommitCreatedEvent event2 =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commit2Id.value(),
        java.util.List.of(commit1Id.value()),
        "Commit 2",
        "Author2",
        java.time.Instant.now().plusMillis(1),
        PATCH_CONTENT
    );

    org.chucc.vcserver.event.CommitCreatedEvent event3 =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commit3Id.value(),
        java.util.List.of(commit2Id.value()),
        "Commit 3",
        "Author3",
        java.time.Instant.now().plusMillis(2),
        PATCH_CONTENT
    );

    // When - Publish events
    eventPublisher.publish(event1).get();
    eventPublisher.publish(event2).get();
    eventPublisher.publish(event3).get();

    // Then - All commits should be projected
    await().atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          assertThat(commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit1Id))
              .isPresent();
          assertThat(commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit2Id))
              .isPresent();
          assertThat(commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit3Id))
              .isPresent();
        });

    // Verify commit chain
    Commit commit3 = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit3Id)
        .orElseThrow();
    assertThat(commit3.parents()).containsExactly(commit2Id);

    Commit commit2 = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit2Id)
        .orElseThrow();
    assertThat(commit2.parents()).containsExactly(commit1Id);

    Commit commit1 = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit1Id)
        .orElseThrow();
    assertThat(commit1.parents()).containsExactly(initialCommitId);
  }

  // ========== POST Graph Event Projection Tests ==========

  /**
   * Test that CommitCreatedEvent (from GSP POST) is projected correctly.
   */
  @Test
  void postGraphEvent_shouldBeProjected() throws Exception {
    // Given - Create a CommitCreatedEvent (simulating POST graph operation)
    CommitId commitId = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent event =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        java.util.List.of(initialCommitId.value()),
        "Add triples via POST",
        "Bob",
        java.time.Instant.now(),
        PATCH_CONTENT
    );

    // When - Publish event
    eventPublisher.publish(event).get();

    // Then - Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().author()).isEqualTo("Bob");
          assertThat(commit.get().message()).isEqualTo("Add triples via POST");
        });
  }

  // ========== DELETE Graph Event Projection Tests ==========

  /**
   * Test that CommitCreatedEvent (from GSP DELETE) is projected correctly.
   */
  @Test
  void deleteGraphEvent_shouldBeProjected() throws Exception {
    // Given - Create a CommitCreatedEvent (simulating DELETE graph operation)
    CommitId commitId = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent event =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        java.util.List.of(initialCommitId.value()),
        "Delete graph",
        "Charlie",
        java.time.Instant.now(),
        "TX .\nD <http://example.org/s> <http://example.org/p> \"value\" .\nTC ."
    );

    // When - Publish event
    eventPublisher.publish(event).get();

    // Then - Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Delete graph");
        });
  }

  // ========== PATCH Graph Event Projection Tests ==========

  /**
   * Test that CommitCreatedEvent (from GSP PATCH) is projected correctly.
   */
  @Test
  void patchGraphEvent_shouldBeProjected() throws Exception {
    // Given - Create a CommitCreatedEvent (simulating PATCH graph operation)
    CommitId commitId = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent event =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        java.util.List.of(initialCommitId.value()),
        "Patch graph",
        "David",
        java.time.Instant.now(),
        PATCH_CONTENT
    );

    // When - Publish event
    eventPublisher.publish(event).get();

    // Then - Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Patch graph");
        });
  }

  // ========== Batch Operations Event Projection Tests ==========

  /**
   * Test that BatchGraphsCompletedEvent is projected correctly.
   */
  @Test
  void batchGraphsEvent_shouldBeProjected() throws Exception {
    // Given - Create a BatchGraphsCompletedEvent (simulating batch operation)
    CommitId commitId = CommitId.generate();

    org.chucc.vcserver.event.CommitCreatedEvent commitEvent =
        new org.chucc.vcserver.event.CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        java.util.List.of(initialCommitId.value()),
        "Batch update",
        "Batch Author",
        java.time.Instant.now(),
        PATCH_CONTENT
    );

    org.chucc.vcserver.event.BatchGraphsCompletedEvent batchEvent =
        new org.chucc.vcserver.event.BatchGraphsCompletedEvent(
        DEFAULT_DATASET,
        java.util.List.of(commitEvent),
        java.time.Instant.now()
    );

    // When - Publish batch event
    eventPublisher.publish(batchEvent).get();

    // Then - Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Batch update");
          assertThat(commit.get().author()).isEqualTo("Batch Author");
        });
  }
}
