package org.chucc.vcserver.service;

import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.*;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for SnapshotService with Testcontainers.
 * Tests end-to-end snapshot creation triggered by commit events.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class SnapshotServiceIT {

  @Container
  static final KafkaContainer kafka = KafkaTestContainers.createKafkaContainerNoReuse();

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    // Configure low snapshot interval for testing
    registry.add("vc.snapshot-interval", () -> "5");
    registry.add("vc.snapshots-enabled", () -> "true");
  }

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private SnapshotService snapshotService;

  @Autowired
  private BranchRepository branchRepository;

  @Autowired
  private CommitRepository commitRepository;

  @Autowired
  private VersionControlProperties vcProperties;

  @Autowired
  private TestSnapshotListener snapshotListener;

  private static final String DATASET = "snapshot-test-dataset";
  private static final String BRANCH = "main";

  @BeforeEach
  void setUp() {
    // Clean up repositories and counters
    branchRepository.deleteAllByDataset(DATASET);
    commitRepository.deleteAllByDataset(DATASET);
    snapshotService.clearAllCounters();
    snapshotListener.reset();

    // Create initial branch
    CommitId initialCommitId = CommitId.generate();
    Branch branch = new Branch(BRANCH, initialCommitId);
    branchRepository.save(DATASET, branch);
  }

  @Test
  void shouldCreateSnapshotAfterIntervalReached() throws Exception {
    // Given - snapshot interval is 5
    assertThat(vcProperties.getSnapshotInterval()).isEqualTo(5);

    // When - publish 5 commits
    for (int i = 0; i < 5; i++) {
      publishCommit(DATASET, BRANCH);
    }

    // Then - snapshot should be created after 5th commit
    await().atMost(Duration.ofSeconds(15))
        .untilAsserted(() -> {
          assertThat(snapshotListener.getSnapshotCount()).isGreaterThanOrEqualTo(1);
        });

    // Verify snapshot event contains N-Quads
    SnapshotCreatedEvent snapshot = snapshotListener.getLastSnapshot();
    assertThat(snapshot).isNotNull();
    assertThat(snapshot.dataset()).isEqualTo(DATASET);
    assertThat(snapshot.branchName()).isEqualTo(BRANCH);
    assertThat(snapshot.nquads()).isNotNull();
  }

  @Test
  void shouldCreateMultipleSnapshotsAtIntervals() throws Exception {
    // When - publish 15 commits (should trigger 3 snapshots at commits 5, 10, 15)
    for (int i = 0; i < 15; i++) {
      publishCommit(DATASET, BRANCH);
    }

    // Then - should have at least 3 snapshots
    await().atMost(Duration.ofSeconds(20))
        .untilAsserted(() -> {
          assertThat(snapshotListener.getSnapshotCount()).isGreaterThanOrEqualTo(3);
        });
  }

  @Test
  void shouldNotCreateSnapshotBeforeInterval() throws Exception {
    // When - publish only 4 commits (less than interval of 5)
    for (int i = 0; i < 4; i++) {
      publishCommit(DATASET, BRANCH);
    }

    // Then - wait a bit and verify no snapshot was created
    Thread.sleep(3000); // Give time for any potential snapshot creation
    assertThat(snapshotListener.getSnapshotCount()).isEqualTo(0);
  }

  private void publishCommit(String dataset, String branch) throws Exception {
    // Get current branch head
    CommitId currentHead = branchRepository.findByDatasetAndName(dataset, branch)
        .map(Branch::getCommitId)
        .orElseThrow();

    CommitId newCommitId = CommitId.generate();
    String rdfPatch = "TX .\nTC .";

    // Publish commit event
    CommitCreatedEvent commitEvent = new CommitCreatedEvent(
        dataset,
        newCommitId.value(),
        List.of(),
        "Test commit for snapshot",
        "test-author",
        Instant.now(),
        rdfPatch
    );

    eventPublisher.publish(commitEvent).get();

    // Publish branch reset event to update branch head
    BranchResetEvent branchEvent = new BranchResetEvent(
        dataset,
        branch,
        currentHead.value(),
        newCommitId.value(),
        Instant.now()
    );

    eventPublisher.publish(branchEvent).get();

    // Wait a bit for projection to complete
    Thread.sleep(300);
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    public TestSnapshotListener testSnapshotListener() {
      return new TestSnapshotListener();
    }
  }

  /**
   * Test listener to capture snapshot events.
   */
  static class TestSnapshotListener {
    private final AtomicInteger snapshotCount = new AtomicInteger(0);
    private volatile SnapshotCreatedEvent lastSnapshot;

    @KafkaListener(
        topicPattern = "vc\\..*\\.events",
        groupId = "snapshot-test-listener",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleEvent(VersionControlEvent event) {
      if (event instanceof SnapshotCreatedEvent snapshot) {
        snapshotCount.incrementAndGet();
        lastSnapshot = snapshot;
      }
    }

    public int getSnapshotCount() {
      return snapshotCount.get();
    }

    public SnapshotCreatedEvent getLastSnapshot() {
      return lastSnapshot;
    }

    public void reset() {
      snapshotCount.set(0);
      lastSnapshot = null;
    }
  }
}
