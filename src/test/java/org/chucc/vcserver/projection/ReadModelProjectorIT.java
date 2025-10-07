package org.chucc.vcserver.projection;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for ReadModelProjector with Testcontainers Kafka.
 * Tests end-to-end event consumption and projection.
 *
 * <p>Runs during Maven's integration-test phase via Failsafe plugin.
 * Uses Testcontainers for realistic Kafka testing with proper isolation.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
class ReadModelProjectorIT {

  @Container
  static final KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private ReadModelProjector projector;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private KafkaProperties kafkaProperties;

  private static final String DATASET = "test-dataset";

  @BeforeEach
  void setUp() throws Exception {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET);
    commitRepository.deleteAllByDataset(DATASET);

    // Ensure topic exists before publishing events
    ensureTopicExists(DATASET);
  }

  /**
   * Ensures the Kafka topic exists for the given dataset.
   * This is necessary because the Kafka listener uses a topic pattern,
   * and needs the topic to exist before it can subscribe.
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
      // Topic might already exist, which is fine
      if (!e.getMessage().contains("TopicExistsException")) {
        throw e;
      }
    }

    // Give Kafka listener time to discover the new topic
    Thread.sleep(1000);
  }

  @Test
  void shouldProjectCommitCreatedEvent() throws Exception {
    // Given
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    CommitId commitId = CommitId.of(commitIdStr);

    String rdfPatchStr = "TX .\nTC .";

    CommitCreatedEvent event = new CommitCreatedEvent(
        DATASET,
        commitIdStr,
        List.of(),
        "Test commit",
        "test-author",
        Instant.now(),
        rdfPatchStr
    );

    // When
    eventPublisher.publish(event).get();

    // Then - wait for event to be consumed and projected
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> commit = commitRepository.findByDatasetAndId(DATASET, commitId);
          assertThat(commit).isPresent();
          assertThat(commit.get().message()).isEqualTo("Test commit");
          assertThat(commit.get().author()).isEqualTo("test-author");
        });
  }

  @Test
  void shouldProjectBranchCreatedEvent() throws Exception {
    // Given
    String branchName = "feature-branch";
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    CommitId commitId = CommitId.of(commitIdStr);

    BranchCreatedEvent event = new BranchCreatedEvent(
        DATASET,
        branchName,
        commitIdStr,
        Instant.now()
    );

    // When
    eventPublisher.publish(event).get();

    // Then
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Branch> branch = branchRepository.findByDatasetAndName(DATASET, branchName);
          assertThat(branch).isPresent();
          assertThat(branch.get().getName()).isEqualTo(branchName);
          assertThat(branch.get().getCommitId()).isEqualTo(commitId);
        });
  }

  @Test
  void shouldProjectMultipleEventsInOrder() throws Exception {
    // Given - initial commit
    String commit1IdStr = "550e8400-e29b-41d4-a716-446655440000";
    String commit2IdStr = "660e8400-e29b-41d4-a716-446655440001";

    CommitCreatedEvent event1 = new CommitCreatedEvent(
        DATASET,
        commit1IdStr,
        List.of(),
        "Initial commit",
        "test-author",
        Instant.now(),
        "TX .\nTC ."
    );

    // When
    eventPublisher.publish(event1).get();

    // Create branch pointing to first commit
    BranchCreatedEvent branchEvent = new BranchCreatedEvent(
        DATASET,
        "main",
        commit1IdStr,
        Instant.now()
    );
    eventPublisher.publish(branchEvent).get();

    // Create second commit
    CommitCreatedEvent event2 = new CommitCreatedEvent(
        DATASET,
        commit2IdStr,
        List.of(commit1IdStr),
        "Second commit",
        "test-author",
        Instant.now().plusSeconds(1),
        "TX .\nTC ."
    );
    eventPublisher.publish(event2).get();

    // Then - verify all events were projected in order
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> commit1 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit1IdStr));
          Optional<Commit> commit2 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit2IdStr));
          Optional<Branch> branch = branchRepository.findByDatasetAndName(DATASET, "main");

          assertThat(commit1).isPresent();
          assertThat(commit2).isPresent();
          assertThat(branch).isPresent();

          // Verify commit chain
          assertThat(commit1.get().parents()).isEmpty();
          assertThat(commit2.get().parents()).containsExactly(CommitId.of(commit1IdStr));
        });
  }

  @Test
  void shouldRecoverStateOnStartup() throws Exception {
    // Given - publish events before projector starts
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";

    CommitCreatedEvent commitEvent = new CommitCreatedEvent(
        DATASET,
        commitIdStr,
        List.of(),
        "Test commit",
        "test-author",
        Instant.now(),
        "TX .\nTC ."
    );

    BranchCreatedEvent branchEvent = new BranchCreatedEvent(
        DATASET,
        "main",
        commitIdStr,
        Instant.now()
    );

    // Publish events
    eventPublisher.publish(commitEvent).get();
    eventPublisher.publish(branchEvent).get();

    // Wait for projection
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Branch> branch = branchRepository.findByDatasetAndName(DATASET, "main");
          assertThat(branch).isPresent();
        });

    // Then - verify state is correct
    Optional<CommitId> projectionState = projector.getProjectionState(DATASET, "main");
    assertThat(projectionState).isPresent();
    assertThat(projectionState.get()).isEqualTo(CommitId.of(commitIdStr));
  }
}
