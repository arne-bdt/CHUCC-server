package org.chucc.vcserver.testutil;

import java.time.Instant;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Base class for integration tests providing common setup and cleanup.
 * Handles repository cleanup, initial commit/branch creation, and Kafka setup.
 *
 * <p>Tests can extend this class to get automatic repository cleanup,
 * initial dataset setup, and Kafka Testcontainer configuration before each test.
 *
 * <p><strong>Event Projection:</strong> By default, the ReadModelProjector
 * (KafkaListener) is DISABLED in integration tests to ensure test isolation.
 * Most integration tests verify the HTTP API layer (command side) without
 * async event projection (query side).
 *
 * <p>Tests that specifically need to verify event projection should:
 * <ul>
 *   <li>Add {@code @TestPropertySource(properties = "projector.kafka-listener.enabled=true")}
 *   <li>Use {@code await().atMost(...)} to wait for async projection
 *   <li>See {@link org.chucc.vcserver.integration.GraphEventProjectorIT} for examples
 * </ul>
 */
public abstract class ITFixture {

  // Initialize Kafka container eagerly so it's started before @DynamicPropertySource runs
  protected static KafkaContainer kafkaContainer = KafkaTestContainers.createKafkaContainer();

  // Static lock for synchronizing test cleanup to prevent race conditions
  private static final Object CLEANUP_LOCK = new Object();

  @Autowired(required = false)
  protected BranchRepository branchRepository;

  @Autowired(required = false)
  protected CommitRepository commitRepository;

  @Autowired(required = false)
  protected TagRepository tagRepository;

  @Autowired(required = false)
  protected org.chucc.vcserver.repository.MaterializedBranchRepository materializedBranchRepo;

  @Autowired(required = false)
  protected org.springframework.context.ApplicationEventPublisher eventPublisher;

  @Autowired(required = false)
  protected org.chucc.vcserver.event.EventPublisher kafkaEventPublisher;

  @Autowired(required = false)
  protected org.chucc.vcserver.command.CreateDatasetCommandHandler createDatasetCommandHandler;

  @org.springframework.beans.factory.annotation.Value("${projector.kafka-listener.enabled:false}")
  protected boolean projectorEnabled;

  protected static final String DEFAULT_DATASET = "default";
  protected static final String TEST_DATASET = "test-dataset";
  protected static final String DEFAULT_BRANCH = "main";
  protected static final String DEFAULT_AUTHOR = "System";

  protected CommitId initialCommitId;

  /**
   * Configures Kafka bootstrap servers from Testcontainer.
   * Each test class gets a unique consumer group to ensure test isolation.
   *
   * @param registry dynamic property registry
   */
  @DynamicPropertySource
  static void configureKafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", () -> kafkaContainer.getBootstrapServers());
    registry.add("spring.kafka.bootstrap-servers", () -> kafkaContainer.getBootstrapServers());
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
  }

  /**
   * Checks if the ReadModelProjector (Kafka listener) is enabled for this test.
   *
   * @return true if projector is enabled, false otherwise
   */
  protected boolean isProjectorEnabled() {
    return projectorEnabled;
  }

  /**
   * Gets the dataset name for this test.
   * Override to use a different dataset name.
   *
   * @return dataset name (defaults to DEFAULT_DATASET)
   */
  protected String getDatasetName() {
    return DEFAULT_DATASET;
  }

  /**
   * Indicates whether to create an initial commit and branch.
   * Override and return false if test needs custom setup.
   *
   * @return true to create initial setup, false to skip
   */
  protected boolean shouldCreateInitialSetup() {
    return true;
  }

  /**
   * Gets the initial branch name.
   * Override to use a different branch name.
   *
   * @return branch name (defaults to "main")
   */
  protected String getInitialBranchName() {
    return DEFAULT_BRANCH;
  }

  /**
   * Sets up test fixture before each test.
   * Cleans repositories and creates initial commit/branch if needed.
   */
  @BeforeEach
  void setUpIntegrationTestFixture() {
    String dataset = getDatasetName();

    // Synchronized cleanup to prevent race conditions between concurrent tests
    // This prevents cache eviction/rebuild races when tests clean up repositories
    synchronized (CLEANUP_LOCK) {
      // Clean up materialized graph cache BEFORE deleting branches from repository
      // (we need branch list to know which graphs to delete)
      if (materializedBranchRepo != null && branchRepository != null) {
        List<org.chucc.vcserver.domain.Branch> branches =
            branchRepository.findAllByDataset(dataset);
        for (org.chucc.vcserver.domain.Branch branch : branches) {
          materializedBranchRepo.deleteBranch(dataset, branch.getName());
        }
      }

      // Clean up repositories
      if (branchRepository != null) {
        branchRepository.deleteAllByDataset(dataset);
      }
      if (commitRepository != null) {
        commitRepository.deleteAllByDataset(dataset);
      }
      if (tagRepository != null) {
        tagRepository.deleteAllByDataset(dataset);
      }

      // Create initial setup if requested
      if (shouldCreateInitialSetup() && commitRepository != null && branchRepository != null) {
        createInitialCommitAndBranch(dataset);

        // Create empty materialized graph for initial branch (only when projector disabled)
        // When projector is enabled, it will create the materialized graph via event processing
        if (materializedBranchRepo != null && !projectorEnabled) {
          materializedBranchRepo.createBranch(dataset, getInitialBranchName(),
              java.util.Optional.empty());
        }
      }
    }
  }

  /**
   * Creates an initial empty commit and main branch via event-driven approach (dual-mode).
   * This method supports both projector-enabled and projector-disabled tests.
   *
   * <p><strong>Mode 1 (Projector Disabled - Default):</strong>
   * <ul>
   *   <li>Publishes {@link org.chucc.vcserver.event.CommitCreatedEvent} to Kafka (no consumer)</li>
   *   <li>Directly saves to repositories for immediate test assertions</li>
   *   <li>Use case: Most integration tests (HTTP API layer testing)</li>
   * </ul>
   *
   * <p><strong>Mode 2 (Projector Enabled):</strong>
   * <ul>
   *   <li>Publishes {@link org.chucc.vcserver.event.CommitCreatedEvent} to Kafka</li>
   *   <li>ReadModelProjector consumes event and updates repositories</li>
   *   <li>Uses {@code await()} to wait for async projection to complete</li>
   *   <li>Use case: Tests verifying event projection (e.g., GraphEventProjectorIT)</li>
   * </ul>
   *
   * <p><strong>Example usage (projector-disabled test):</strong>
   * <pre>{@code
   * @SpringBootTest(webEnvironment = RANDOM_PORT)
   * @ActiveProfiles("it")
   * class MyApiTest extends ITFixture {
   *   @Override
   *   protected void createInitialCommitAndBranch(String dataset) {
   *     createInitialCommitAndBranchViaEvents(dataset);  // Works immediately, no await()
   *   }
   * }
   * }</pre>
   *
   * <p><strong>Example usage (projector-enabled test):</strong>
   * <pre>{@code
   * @SpringBootTest(webEnvironment = RANDOM_PORT)
   * @ActiveProfiles("it")
   * @TestPropertySource(properties = "projector.kafka-listener.enabled=true")
   * class GraphEventProjectorIT extends ITFixture {
   *   @Override
   *   protected void createInitialCommitAndBranch(String dataset) {
   *     createInitialCommitAndBranchViaEvents(dataset);  // Uses await() internally
   *   }
   * }
   * }</pre>
   *
   * @param dataset the dataset name
   */
  protected void createInitialCommitAndBranchViaEvents(String dataset) {
    // Create dataset using command handler, which automatically:
    // 1. Creates Kafka topics (vc.{dataset}.events and vc.{dataset}.events.dlq)
    // 2. Creates initial commit with empty patch
    // 3. Creates main branch pointing to initial commit
    // 4. Publishes DatasetCreatedEvent to Kafka
    if (createDatasetCommandHandler != null) {
      org.chucc.vcserver.command.CreateDatasetCommand command =
          new org.chucc.vcserver.command.CreateDatasetCommand(
              dataset,
              java.util.Optional.of("Integration test dataset"),
              DEFAULT_AUTHOR,
              java.util.Optional.empty(),  // No initial graph
              null                          // Use default Kafka config
          );

      org.chucc.vcserver.event.VersionControlEvent event = createDatasetCommandHandler.handle(command);
      if (event instanceof org.chucc.vcserver.event.DatasetCreatedEvent datasetEvent) {
        initialCommitId = CommitId.of(datasetEvent.initialCommitId());
      }
    }

    // Mode 2: Projector enabled - await() for async projection
    if (projectorEnabled) {
      org.awaitility.Awaitility.await()
          .atMost(java.time.Duration.ofSeconds(10))
          .untilAsserted(() -> {
            java.util.Optional<Commit> commit = commitRepository.findByDatasetAndId(dataset, initialCommitId);
            org.assertj.core.api.Assertions.assertThat(commit).isPresent();

            java.util.Optional<Branch> branch =
                branchRepository.findByDatasetAndName(dataset, getInitialBranchName());
            org.assertj.core.api.Assertions.assertThat(branch).isPresent();

            // Also verify materialized graph was created by projector
            if (materializedBranchRepo != null) {
              boolean graphExists = materializedBranchRepo.exists(dataset, getInitialBranchName());
              org.assertj.core.api.Assertions.assertThat(graphExists).isTrue();
            }
          });
    }
    // Note: When projector disabled, CreateDatasetCommandHandler already saved to repositories
    // No need for manual repository saves
  }

  /**
   * Creates an initial empty commit and main branch.
   * Also publishes a {@link org.chucc.vcserver.event.CommitCreatedEvent} for infrastructure testing.
   * The event is ignored by default (projector disabled) but allows for event-driven setup migration.
   *
   * @param dataset the dataset name
   */
  protected void createInitialCommitAndBranch(String dataset) {
    initialCommitId = CommitId.generate();
    org.apache.jena.rdfpatch.RDFPatch emptyPatch = RDFPatchOps.emptyPatch();
    Instant timestamp = Instant.now();

    Commit initialCommit = new Commit(
        initialCommitId,
        List.of(),
        DEFAULT_AUTHOR,
        "Initial commit",
        timestamp,
        0
    );

    // Direct repository saves (existing behavior preserved)
    commitRepository.save(dataset, initialCommit, emptyPatch);

    Branch mainBranch = new Branch(getInitialBranchName(), initialCommitId);
    branchRepository.save(dataset, mainBranch);

    // Publish event for infrastructure testing (only when projector disabled to avoid conflicts)
    // Session 1: Test event publishing mechanism without side effects
    if (eventPublisher != null && !projectorEnabled) {
      // Serialize patch to string
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      RDFPatchOps.write(out, emptyPatch);
      String patchString = out.toString(java.nio.charset.StandardCharsets.UTF_8);

      org.chucc.vcserver.event.CommitCreatedEvent event =
          new org.chucc.vcserver.event.CommitCreatedEvent(
              dataset,
              initialCommitId.toString(),
              List.of(),
              getInitialBranchName(),
              "Initial commit",
              DEFAULT_AUTHOR,
              timestamp,
              patchString,
              0
          );
      eventPublisher.publishEvent(event);
    }
  }

  /**
   * Creates a commit with the given patch content.
   *
   * @param dataset the dataset name
   * @param parents parent commit IDs
   * @param author commit author
   * @param message commit message
   * @param patchContent RDF patch content as string
   * @return the created commit ID
   */
  protected CommitId createCommit(
      String dataset,
      List<CommitId> parents,
      String author,
      String message,
      String patchContent) {
    CommitId commitId = CommitId.generate();
    Commit commit = new Commit(commitId, parents, author, message, Instant.now(),
        0);
    commitRepository.save(
        dataset,
        commit,
        RDFPatchOps.read(new java.io.ByteArrayInputStream(
            patchContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
    );
    return commitId;
  }

  /**
   * Creates a simple test patch with one triple.
   *
   * @param subject subject URI
   * @param predicate predicate URI
   * @param value object value
   * @return RDF patch content
   */
  protected String createSimplePatch(String subject, String predicate, String value) {
    return String.format(
        "TX .%nA <%s> <%s> \"%s\" .%nTC .",
        subject, predicate, value
    );
  }
}
