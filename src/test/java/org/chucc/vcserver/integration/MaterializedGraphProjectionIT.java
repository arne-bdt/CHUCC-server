package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchDeletedEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.MaterializedBranchRepository;
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
 * Integration tests for materialized graph projection.
 *
 * <p>These tests verify that events are correctly projected
 * to materialized branch graphs by the ReadModelProjector.
 *
 * <p>Projector is enabled via {@code @TestPropertySource}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class MaterializedGraphProjectionIT {

  private static final String DEFAULT_DATASET = "default";
  private static final String PATCH_CONTENT = "TX .\n"
      + "A <http://example.org/s> <http://example.org/p> \"value\" .\n"
      + "TC .";

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
  private MaterializedBranchRepository materializedBranchRepo;

  @Autowired
  private KafkaProperties kafkaProperties;

  private CommitId initialCommitId;

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

    // Save initial commit to repository (required for cache rebuild)
    // Empty patch for initial commit
    String emptyPatch = "TX .\nTC .";
    RDFPatch patch = RDFPatchOps.read(
        new java.io.ByteArrayInputStream(emptyPatch.getBytes()));

    Commit initialCommit = new Commit(
        initialCommitId,
        Collections.emptyList(),
        "test-system",
        "Initial commit",
        Instant.now(),
        0  // patchSize for empty patch
    );
    commitRepository.save(DEFAULT_DATASET, initialCommit, patch);

    // Create and save main branch
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

  @Test
  void commitCreatedEvent_shouldProjectToMaterializedGraph() throws Exception {
    // Given
    CommitId commitId = CommitId.generate();

    CommitCreatedEvent event = new CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        List.of(initialCommitId.value()),
        "main",
        "Add test data",
        "test-author",
        Instant.now(),
        PATCH_CONTENT,
        1
    );

    // When
    eventPublisher.publish(event).get();

    // Then - Wait for projection to materialized graph
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph graph = materializedBranchRepo.getBranchGraph(DEFAULT_DATASET, "main");

          graph.begin(ReadWrite.READ);
          try {
            Node s = NodeFactory.createURI("http://example.org/s");
            Node p = NodeFactory.createURI("http://example.org/p");
            Node o = NodeFactory.createLiteralString("value");

            boolean contains = graph.getDefaultGraph().contains(s, p, o);
            assertThat(contains)
                .as("Materialized graph should contain projected triple")
                .isTrue();
          } finally {
            graph.end();
          }
        });
  }

  @Test
  void branchCreatedEvent_shouldInitializeMaterializedGraph() throws Exception {
    // Given
    String newBranch = "feature";

    BranchCreatedEvent event = new BranchCreatedEvent(
        DEFAULT_DATASET,
        newBranch,
        initialCommitId.value(),
        initialCommitId.value(),  // sourceRef is the commit ID for new branches
        false,  // Not protected
        "test-author",
        Instant.now()
    );

    // When
    eventPublisher.publish(event).get();

    // Then - Wait for materialized graph initialization
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(materializedBranchRepo.exists(DEFAULT_DATASET, newBranch))
              .as("Materialized graph should exist for new branch")
              .isTrue();
        });
  }

  @Test
  void branchCreatedEventFromParent_shouldCloneMaterializedGraph() throws Exception {
    // Given - First add data to main branch
    CommitId commitId = CommitId.generate();
    CommitCreatedEvent commitEvent = new CommitCreatedEvent(
        DEFAULT_DATASET,
        commitId.value(),
        List.of(initialCommitId.value()),
        "main",
        "Add data to main",
        "test-author",
        Instant.now(),
        PATCH_CONTENT,
        1
    );
    eventPublisher.publish(commitEvent).get();

    // Wait for main branch to have data
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph graph = materializedBranchRepo.getBranchGraph(DEFAULT_DATASET, "main");
          graph.begin(ReadWrite.READ);
          try {
            assertThat(graph.isEmpty()).isFalse();
          } finally {
            graph.end();
          }
        });

    // When - Create feature branch from main
    String newBranch = "feature-from-main";
    BranchCreatedEvent branchEvent = new BranchCreatedEvent(
        DEFAULT_DATASET,
        newBranch,
        commitId.value(),
        "refs/heads/main",  // Source ref indicates parent branch
        false,
        "test-author",
        Instant.now()
    );
    eventPublisher.publish(branchEvent).get();

    // Then - Feature branch should have cloned data
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          DatasetGraph featureGraph = materializedBranchRepo.getBranchGraph(
              DEFAULT_DATASET, newBranch);

          featureGraph.begin(ReadWrite.READ);
          try {
            Node s = NodeFactory.createURI("http://example.org/s");
            Node p = NodeFactory.createURI("http://example.org/p");
            Node o = NodeFactory.createLiteralString("value");

            boolean contains = featureGraph.getDefaultGraph().contains(s, p, o);
            assertThat(contains)
                .as("Feature branch should have cloned data from main")
                .isTrue();
          } finally {
            featureGraph.end();
          }
        });
  }

  @Test
  void branchDeletedEvent_shouldRemoveMaterializedGraph() throws Exception {
    // Given - Create branch first
    String branchToDelete = "delete-me";
    BranchCreatedEvent createEvent = new BranchCreatedEvent(
        DEFAULT_DATASET,
        branchToDelete,
        initialCommitId.value(),
        initialCommitId.value(),  // sourceRef is the commit ID
        false,
        "test-author",
        Instant.now()
    );
    eventPublisher.publish(createEvent).get();

    // Wait for creation
    await().atMost(Duration.ofSeconds(10))
        .until(() -> materializedBranchRepo.exists(DEFAULT_DATASET, branchToDelete));

    // When - Delete the branch
    BranchDeletedEvent deleteEvent = new BranchDeletedEvent(
        DEFAULT_DATASET,
        branchToDelete,
        initialCommitId.value(),
        "test-author",
        Instant.now()
    );
    eventPublisher.publish(deleteEvent).get();

    // Then - Materialized graph should be removed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertThat(materializedBranchRepo.exists(DEFAULT_DATASET, branchToDelete))
              .as("Materialized graph should be deleted with branch")
              .isFalse();
        });
  }
}
