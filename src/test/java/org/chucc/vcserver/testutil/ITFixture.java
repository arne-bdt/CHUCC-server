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

  @Autowired(required = false)
  protected BranchRepository branchRepository;

  @Autowired(required = false)
  protected CommitRepository commitRepository;

  @Autowired(required = false)
  protected TagRepository tagRepository;

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
    }
  }

  /**
   * Creates an initial empty commit and main branch.
   *
   * @param dataset the dataset name
   */
  protected void createInitialCommitAndBranch(String dataset) {
    initialCommitId = CommitId.generate();
    Commit initialCommit = new Commit(
        initialCommitId,
        List.of(),
        DEFAULT_AUTHOR,
        "Initial commit",
        Instant.now()
    );
    commitRepository.save(dataset, initialCommit, RDFPatchOps.emptyPatch());

    Branch mainBranch = new Branch(getInitialBranchName(), initialCommitId);
    branchRepository.save(dataset, mainBranch);
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
    Commit commit = new Commit(commitId, parents, author, message, Instant.now());
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
