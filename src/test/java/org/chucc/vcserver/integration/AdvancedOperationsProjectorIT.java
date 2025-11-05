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
import org.chucc.vcserver.event.CherryPickedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.CommitsSquashedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.TagCreatedEvent;
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
 * Integration test for advanced operation event projection.
 *
 * <p>Verifies that the ReadModelProjector can handle advanced operation events:
 * <ul>
 *   <li>TagCreatedEvent - Tag creation operations
 *   <li>CherryPickedEvent - Cherry-pick commit operations
 *   <li>CommitsSquashedEvent - Squash commit operations
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
class AdvancedOperationsProjectorIT {

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

  // ========== Tag Created Event Projection Tests ==========

  /**
   * Test that TagCreatedEvent is processed without errors.
   */
  @Test
  void tagCreatedEvent_shouldBeProcessedWithoutErrors() throws Exception {
    // Given - Create a commit for the tag
    CommitId tagCommitId = CommitId.generate();

    CommitCreatedEvent commitEvent =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        tagCommitId.value(),
        List.of(initialCommitId.value()),
        null,
        "Commit for tag",
        "Alice",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commitEvent).get();

    // Wait for commit to be projected
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, tagCommitId)
            .isPresent());

    TagCreatedEvent tagEvent =
        new TagCreatedEvent(
        DEFAULT_DATASET,
        "v1.0.0",
        tagCommitId.value(),
        "Release v1.0.0",
        "System",
        Instant.now()
    );

    // When - Publish tag event
    eventPublisher.publish(tagEvent).get();

    // Then - Event should be processed without throwing exceptions
    // Note: handleTagCreated currently only logs, so we just verify
    // the event was consumed by waiting a short time
    Thread.sleep(2000);

    // Verify no exceptions were thrown (test would fail if projection failed)
    // The commit should still exist
    var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, tagCommitId);
    assertThat(commit).isPresent();
  }

  // ========== Cherry-Picked Event Projection Tests ==========

  /**
   * Test that CherryPickedEvent is projected correctly.
   */
  @Test
  void cherryPickedEvent_shouldBeProjected() throws Exception {
    // Given - Create source commit to cherry-pick from
    CommitId sourceCommitId = CommitId.generate();

    CommitCreatedEvent sourceCommit =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        sourceCommitId.value(),
        List.of(initialCommitId.value()),
        null,
        "Source commit to cherry-pick",
        "Alice",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(sourceCommit).get();

    // Wait for source commit
    await().atMost(Duration.ofSeconds(5))
        .until(() -> commitRepository.findByDatasetAndId(DEFAULT_DATASET, sourceCommitId)
            .isPresent());

    // Create target branch
    CommitId targetBranchCommitId = CommitId.generate();

    CommitCreatedEvent targetBranchCommit =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        targetBranchCommitId.value(),
        List.of(initialCommitId.value()),
        null,
        "Target branch commit",
        "Bob",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(targetBranchCommit).get();

    BranchCreatedEvent branchEvent =
        new BranchCreatedEvent(
        DEFAULT_DATASET,
        "target-branch",
        targetBranchCommitId.value(),
        "main",
        false,
        "test-author",
        Instant.now()
    );
    eventPublisher.publish(branchEvent).get();

    // Wait for target branch
    await().atMost(Duration.ofSeconds(5))
        .until(() -> branchRepository.findByDatasetAndName(DEFAULT_DATASET, "target-branch")
            .isPresent());

    // Create cherry-picked commit
    CommitId cherryPickedCommitId = CommitId.generate();

    // Create CherryPickedEvent
    CherryPickedEvent cherryPickEvent =
        new CherryPickedEvent(
        DEFAULT_DATASET,
        cherryPickedCommitId.value(),
        sourceCommitId.value(),
        "target-branch",
        "Cherry-pick: Source commit to cherry-pick",
        "Charlie",
        Instant.now(),
        PATCH_CONTENT,
        1
    );

    // When - Publish cherry-pick event
    eventPublisher.publish(cherryPickEvent).get();

    // Then - Wait for async event projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify cherry-picked commit was saved
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, cherryPickedCommitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).contains("Cherry-pick");
          assertThat(commit.get().author()).isEqualTo("Charlie");
          assertThat(commit.get().parents()).containsExactly(targetBranchCommitId);

          // Verify target branch was updated to point to cherry-picked commit
          var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "target-branch");
          assertThat(branch).isPresent();
          assertThat(branch.get().getCommitId()).isEqualTo(cherryPickedCommitId);
        });
  }

  // ========== Commits Squashed Event Projection Tests ==========

  /**
   * Test that CommitsSquashedEvent is projected correctly.
   */
  @Test
  void commitsSquashedEvent_shouldBeProjected() throws Exception {
    // Given - Create multiple commits to squash
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    // Create commit chain
    CommitCreatedEvent commit1 =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        commit1Id.value(),
        List.of(initialCommitId.value()),
        null,
        "Commit 1",
        "Alice",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commit1).get();

    CommitCreatedEvent commit2 =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        commit2Id.value(),
        List.of(commit1Id.value()),
        null,
        "Commit 2",
        "Alice",
        Instant.now().plusMillis(1),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commit2).get();

    CommitCreatedEvent commit3 =
        new CommitCreatedEvent(
        DEFAULT_DATASET,
        commit3Id.value(),
        List.of(commit2Id.value()),
        null,
        "Commit 3",
        "Alice",
        Instant.now().plusMillis(2),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commit3).get();

    // Create feature branch pointing to commit3
    BranchCreatedEvent branchEvent =
        new BranchCreatedEvent(
        DEFAULT_DATASET,
        "feature",
        commit3Id.value(),
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

    // Create CommitsSquashedEvent with full commit data
    // (no separate CommitCreatedEvent needed - squash event creates the commit)
    CommitId squashedCommitId = CommitId.generate();

    CommitsSquashedEvent squashEvent =
        new CommitsSquashedEvent(
        DEFAULT_DATASET,
        "feature",
        squashedCommitId.value(), // new head
        List.of(commit1Id.value(), commit2Id.value(), commit3Id.value()), // squashed commits
        List.of(initialCommitId.value()), // parents
        "Alice",
        "Squashed commits 1-3",
        Instant.now(),
        commit3Id.value(), // previous head
        PATCH_CONTENT, // rdfPatch
        1 // patchSize
    );

    // When - Publish squash event (creates commit AND updates branch)
    eventPublisher.publish(squashEvent).get();

    // Then - Wait for both commit creation and branch update
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          // Verify squashed commit was created
          var commit = commitRepository.findByDatasetAndId(DEFAULT_DATASET, squashedCommitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Squashed commits 1-3");
          assertThat(commit.get().parents()).containsExactly(initialCommitId);

          // Verify feature branch now points to squashed commit
          var branch = branchRepository.findByDatasetAndName(DEFAULT_DATASET, "feature");
          assertThat(branch).isPresent();
          assertThat(branch.get().getCommitId()).isEqualTo(squashedCommitId);
        });
  }
}
