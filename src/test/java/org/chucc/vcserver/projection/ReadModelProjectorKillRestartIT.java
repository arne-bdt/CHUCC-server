package org.chucc.vcserver.projection;

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
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for ReadModelProjector kill/restart scenario.
 * Tests that the projector can recover state after being stopped and restarted,
 * verifying that state matches the produced event sequence.
 *
 * <p>Runs during Maven's integration-test phase via Failsafe plugin.
 * Uses Testcontainers for realistic Kafka testing with proper isolation.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class ReadModelProjectorKillRestartIT {

  @Container
  static final KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
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
  private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

  private static final String DATASET = "kill-restart-dataset";

  @BeforeEach
  void setUp() {
    // Clean up repositories before each test
    branchRepository.deleteAllByDataset(DATASET);
    commitRepository.deleteAllByDataset(DATASET);
  }

  @Test
  void shouldRecoverStateAfterKillAndRestart() throws Exception {
    // Given - Phase 1: Initial events
    String commit1Id = "550e8400-e29b-41d4-a716-446655440000";
    String commit2Id = "660e8400-e29b-41d4-a716-446655440001";

    // Publish first commit
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        DATASET,
        commit1Id,
        List.of(), null,
        "Initial commit",
        "test-author",
        Instant.now(),
        "TX .\nTC ."
    );
    eventPublisher.publish(event1).get();

    // Create main branch
    BranchCreatedEvent branchEvent = new BranchCreatedEvent(
        DATASET,
        "main",
        commit1Id,
        Instant.now()
    );
    eventPublisher.publish(branchEvent).get();

    // Wait for events to be consumed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Branch> branch = branchRepository.findByDatasetAndName(DATASET, "main");
          assertThat(branch).isPresent();
          assertThat(branch.get().getCommitId()).isEqualTo(CommitId.of(commit1Id));
        });

    // When - Simulate "kill" by stopping the Kafka listeners
    kafkaListenerEndpointRegistry.getAllListenerContainers()
        .forEach(container -> container.stop());

    // Wait for containers to stop
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          boolean allStopped = kafkaListenerEndpointRegistry.getAllListenerContainers()
              .stream()
              .allMatch(container -> !container.isRunning());
          assertThat(allStopped).isTrue();
        });

    // Publish events while projector is "down"
    CommitCreatedEvent event2 = new CommitCreatedEvent(
        DATASET,
        commit2Id,
        List.of(commit1Id), null,
        "Second commit (while stopped)",
        "test-author",
        Instant.now().plusSeconds(1),
        "TX .\nTC ."
    );
    eventPublisher.publish(event2).get();

    // Verify event2 has NOT been processed yet
    Optional<Commit> commit2BeforeRestart = commitRepository.findByDatasetAndId(
        DATASET, CommitId.of(commit2Id));
    assertThat(commit2BeforeRestart).isEmpty();

    // Simulate "restart" by restarting the Kafka listeners
    kafkaListenerEndpointRegistry.getAllListenerContainers()
        .forEach(container -> container.start());

    // Wait for containers to start
    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          boolean allRunning = kafkaListenerEndpointRegistry.getAllListenerContainers()
              .stream()
              .allMatch(container -> container.isRunning());
          assertThat(allRunning).isTrue();
        });

    // Then - Verify that the missed event was consumed after restart
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> commit2 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit2Id));
          assertThat(commit2).isPresent();
          assertThat(commit2.get().message()).isEqualTo("Second commit (while stopped)");
          assertThat(commit2.get().parents()).containsExactly(CommitId.of(commit1Id));
        });

    // Verify complete state matches expected sequence
    Optional<Commit> commit1 = commitRepository.findByDatasetAndId(
        DATASET, CommitId.of(commit1Id));
    Optional<Commit> commit2Final = commitRepository.findByDatasetAndId(
        DATASET, CommitId.of(commit2Id));
    Optional<Branch> branch = branchRepository.findByDatasetAndName(DATASET, "main");

    assertThat(commit1).isPresent();
    assertThat(commit2Final).isPresent();
    assertThat(branch).isPresent();

    // Verify commit chain
    assertThat(commit1.get().parents()).isEmpty();
    assertThat(commit2Final.get().parents()).containsExactly(CommitId.of(commit1Id));

    // Verify projection state
    Optional<CommitId> projectionState = projector.getProjectionState(DATASET, "main");
    assertThat(projectionState).isPresent();
    assertThat(projectionState.get()).isEqualTo(CommitId.of(commit1Id));
  }

  @Test
  void shouldConsumeFromEarliestOnFirstStart() throws Exception {
    // Given - Publish events before any consumer has started
    // (simulating recovery from earliest offset)
    String commit1Id = "770e8400-e29b-41d4-a716-446655440000";
    String commit2Id = "880e8400-e29b-41d4-a716-446655440001";
    String commit3Id = "990e8400-e29b-41d4-a716-446655440002";

    // Stop listeners first
    kafkaListenerEndpointRegistry.getAllListenerContainers()
        .forEach(container -> container.stop());

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          boolean allStopped = kafkaListenerEndpointRegistry.getAllListenerContainers()
              .stream()
              .allMatch(container -> !container.isRunning());
          assertThat(allStopped).isTrue();
        });

    // Publish all events while consumer is stopped
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        DATASET, commit1Id, List.of(), "main", "Commit 1", "author", Instant.now(), "TX .\nTC .");
    CommitCreatedEvent event2 = new CommitCreatedEvent(
        DATASET, commit2Id, List.of(commit1Id), "main", "Commit 2", "author",
        Instant.now().plusSeconds(1), "TX .\nTC .");
    CommitCreatedEvent event3 = new CommitCreatedEvent(
        DATASET, commit3Id, List.of(commit2Id), "main", "Commit 3", "author",
        Instant.now().plusSeconds(2), "TX .\nTC .");

    eventPublisher.publish(event1).get();
    eventPublisher.publish(event2).get();
    eventPublisher.publish(event3).get();

    // When - Start consumer (should consume from earliest)
    kafkaListenerEndpointRegistry.getAllListenerContainers()
        .forEach(container -> container.start());

    await().atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> {
          boolean allRunning = kafkaListenerEndpointRegistry.getAllListenerContainers()
              .stream()
              .allMatch(container -> container.isRunning());
          assertThat(allRunning).isTrue();
        });

    // Then - Verify all events were consumed in order
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          Optional<Commit> c1 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit1Id));
          Optional<Commit> c2 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit2Id));
          Optional<Commit> c3 = commitRepository.findByDatasetAndId(
              DATASET, CommitId.of(commit3Id));

          assertThat(c1).isPresent();
          assertThat(c2).isPresent();
          assertThat(c3).isPresent();

          // Verify commit chain
          assertThat(c1.get().parents()).isEmpty();
          assertThat(c2.get().parents()).containsExactly(CommitId.of(commit1Id));
          assertThat(c3.get().parents()).containsExactly(CommitId.of(commit2Id));
        });
  }
}
