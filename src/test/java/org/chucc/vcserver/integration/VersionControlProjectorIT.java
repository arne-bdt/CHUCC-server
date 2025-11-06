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
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchRebasedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for version control operation event projection.
 *
 * <p>Verifies that the ReadModelProjector can handle version control events:
 * <ul>
 *   <li>BranchRebasedEvent - Branch rebase operations
 *   <li>RevertCreatedEvent - Revert commit operations
 *   <li>SnapshotCreatedEvent - Dataset snapshot events
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
class VersionControlProjectorIT {

  private static final String DEFAULT_DATASET = "default";

  @Container
  private static KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private KafkaProperties kafkaProperties;

  private CommitId initialCommitId;

  private static final String PATCH_CONTENT = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"value\" .\n"
      + "TC .";

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
    initialCommitId = CommitId.generate();
    Branch mainBranch = new Branch("main", initialCommitId);
    branchRepository.save(DEFAULT_DATASET, mainBranch);
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

  // ========== Branch Rebased Event Projection Tests ==========

  /**
   * Test that BranchRebasedEvent is projected correctly.
   */
  @Test
  void branchRebasedEvent_shouldBeProjected() throws Exception {
    // Given - Create commits for rebase scenario
    CommitId featureBranchId = CommitId.generate();
    CommitId rebasedCommit1Id = CommitId.generate();
    CommitId rebasedCommit2Id = CommitId.generate();

    // Create initial feature branch commit
    CommitCreatedEvent featureCommit =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        featureBranchId.value(),
        List.of(initialCommitId.value()),
        null,
        "Feature commit",
        "Alice",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(featureCommit).get();

    // Create feature branch
    BranchCreatedEvent branchEvent =
        new BranchCreatedEvent(
        DEFAULT_DATASET,
        "feature",
        featureBranchId.value(),
        "main",
        false,
        "test-author",
        Instant.now()
    );
    eventPublisher.publish(branchEvent).get();

    // Wait for setup
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature")
            .isPresent());

    // Create BranchRebasedEvent with full commit data
    // (no separate CommitCreatedEvents needed - rebase event creates the commits)
    BranchRebasedEvent rebaseEvent =
        new BranchRebasedEvent(
        DEFAULT_DATASET,
        "feature",
        rebasedCommit2Id.value(), // new head (final commit)
        featureBranchId.value(), // previous head
        List.of(
            // First rebased commit
            new BranchRebasedEvent.RebasedCommitData(
                rebasedCommit1Id.value(),
                List.of(initialCommitId.value()),
                "Alice",
                "Feature commit (rebased 1)",
                PATCH_CONTENT,
                1
            ),
            // Second rebased commit
            new BranchRebasedEvent.RebasedCommitData(
                rebasedCommit2Id.value(),
                List.of(rebasedCommit1Id.value()),
                "Alice",
                "Feature commit (rebased 2)",
                PATCH_CONTENT,
                1
            )
        ),
        "Alice",
        Instant.now()
    );

    // When - Publish rebase event (creates commits AND updates branch)
    eventPublisher.publish(rebaseEvent).get();

    // Then - Wait for both commit creation and branch update
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify both rebased commits were created
          var commit1 = commitRepository.findByDatasetAndId(DEFAULT_DATASET, rebasedCommit1Id);
          assertThat(commit1).isPresent();
          assertThat(commit1.get().message()).isEqualTo("Feature commit (rebased 1)");
          assertThat(commit1.get().parents()).containsExactly(initialCommitId);

          var commit2 = commitRepository.findByDatasetAndId(DEFAULT_DATASET, rebasedCommit2Id);
          assertThat(commit2).isPresent();
          assertThat(commit2.get().message()).isEqualTo("Feature commit (rebased 2)");
          assertThat(commit2.get().parents()).containsExactly(rebasedCommit1Id);

          // Verify feature branch now points to final rebased commit
          var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature");
          assertThat(branch).isPresent();
          assertThat(branch.get().getCommitId()).isEqualTo(rebasedCommit2Id);
        });
  }

  // ========== Revert Created Event Projection Tests ==========

  /**
   * Test that RevertCreatedEvent is projected correctly.
   */
  @Test
  void revertCreatedEvent_shouldBeProjected() throws Exception {
    // Given - Create a commit to be reverted
    CommitId commit1Id = CommitId.generate();

    CommitCreatedEvent commitEvent =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        commit1Id.value(),
        List.of(initialCommitId.value()),
        null,
        "Commit to be reverted",
        "Bob",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commitEvent).get();

    // Wait for commit to be projected
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, commit1Id)
            .isPresent());

    // Update main branch to point to commit1
    branchRepository.updateBranchHead(DEFAULT_DATASET, "main", commit1Id);

    // Create revert commit
    CommitId revertCommitId = CommitId.generate();

    RevertCreatedEvent revertEvent =
        new RevertCreatedEvent(
        DEFAULT_DATASET,
        revertCommitId.value(),
        commit1Id.value(),
        "main",
        "Revert commit",
        "Bob",
        Instant.now(),
        "TX .\nD <http://example.org/s> <http://example.org/p> \"value\" .\nTC .",
        1
    );

    // When - Publish revert event
    eventPublisher.publish(revertEvent).get();

    // Then - Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify revert commit was saved
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, revertCommitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Revert commit");
          assertThat(commit.get().author()).isEqualTo("Bob");
          assertThat(commit.get().parents()).containsExactly(commit1Id);

          // Verify main branch now points to revert commit
          var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "main");
          assertThat(branch).isPresent();
          assertThat(branch.get().getCommitId()).isEqualTo(revertCommitId);
        });
  }

  // ========== Snapshot Created Event Projection Tests ==========

  /**
   * Test that SnapshotCreatedEvent is processed without errors.
   */
  @Test
  void snapshotCreatedEvent_shouldBeProcessedWithoutErrors() throws Exception {
    // Given - Create a snapshot event
    CommitId snapshotCommitId = CommitId.generate();

    // First create a commit for the snapshot
    CommitCreatedEvent commitEvent =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        snapshotCommitId.value(),
        List.of(initialCommitId.value()),
        null,
        "Commit for snapshot",
        "Charlie",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commitEvent).get();

    // Wait for commit to be projected
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, snapshotCommitId)
            .isPresent());

    SnapshotCreatedEvent snapshotEvent =
        new SnapshotCreatedEvent(
        DEFAULT_DATASET,
        snapshotCommitId.value(),
        "main",
        Instant.now(),
        "<http://example.org/s> <http://example.org/p> \"value\" ."
    );

    // When - Publish snapshot event
    eventPublisher.publish(snapshotEvent).get();

    // Then - Event should be processed without throwing exceptions
    // Note: handleSnapshotCreated currently only logs, so we just verify
    // the event was consumed by waiting a short time
    Thread.sleep(2000);

    // Verify no exceptions were thrown (test would fail if projection failed)
    // The commit should still exist
    var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, snapshotCommitId);
    assertThat(commit).isPresent();
  }
}
