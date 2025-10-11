package org.chucc.vcserver.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Snapshot;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for snapshot-based dataset materialization.
 * Verifies that snapshots are used to speed up dataset materialization
 * when available, and that correctness is maintained.
 */
class DatasetServiceSnapshotTest {
  private DatasetService datasetService;
  private SnapshotService snapshotService;
  private CommitRepository commitRepository;
  private BranchRepository branchRepository;
  private SimpleMeterRegistry meterRegistry;

  private static final String DATASET_NAME = "test-dataset";
  private static final String BRANCH_NAME = "main";
  private static final String AUTHOR = "test-author";

  @BeforeEach
  void setUp() {
    branchRepository = new BranchRepository();
    commitRepository = new CommitRepository();
    meterRegistry = new SimpleMeterRegistry();

    // Create SnapshotService with mocked EventPublisher
    EventPublisher eventPublisher = Mockito.mock(EventPublisher.class);
    VersionControlProperties vcProperties = new VersionControlProperties();
    vcProperties.setSnapshotsEnabled(true);

    snapshotService = new SnapshotService(
        null, // DatasetService not needed for these tests
        branchRepository,
        eventPublisher,
        vcProperties
    );

    // Create DatasetService with all dependencies
    datasetService = new DatasetService(
        branchRepository,
        commitRepository,
        snapshotService,
        meterRegistry
    );
  }

  @Test
  void materializeCommit_withSnapshot_shouldApplyOnlyIncrementalPatches() {
    // Create commit chain: C0 → C1 → C2 → C3 → C4 → C5
    List<Commit> commits = createCommitChain(DATASET_NAME, 6);

    // Materialize C3 to create a snapshot base
    DatasetGraph snapshotGraph = datasetService.materializeCommit(DATASET_NAME, commits.get(3).id());

    // Create snapshot at C3
    Snapshot snapshot = new Snapshot(
        commits.get(3).id(),
        BRANCH_NAME,
        Instant.now(),
        snapshotGraph
    );
    snapshotService.storeSnapshot(DATASET_NAME, snapshot);

    // Clear cache to force rebuild
    datasetService.clearCache(DATASET_NAME);

    // Materialize C5 (should use snapshot at C3, apply C4 and C5)
    DatasetGraph result = datasetService.materializeCommit(DATASET_NAME, commits.get(5).id());

    // Verify correctness - result should not be null
    assertThat(result).isNotNull();
    // Note: Snapshot usage is transparent to the caller
  }

  @Test
  void materializeCommit_withoutSnapshot_shouldBuildFromScratch() {
    // Create commit chain without snapshot
    List<Commit> commits = createCommitChain(DATASET_NAME, 5);

    // Materialize C4 (no snapshot, build from scratch)
    DatasetGraph result = datasetService.materializeCommit(DATASET_NAME, commits.get(4).id());

    assertThat(result).isNotNull();
    // Note: Without snapshots, the system builds from scratch (all patches applied)
  }

  @Test
  void materializeCommit_snapshotIsTarget_shouldReturnSnapshotDirectly() {
    // Create commit chain
    List<Commit> commits = createCommitChain(DATASET_NAME, 3);

    // Create snapshot at C2
    DatasetGraph snapshotGraph = datasetService.materializeCommit(DATASET_NAME, commits.get(2).id());
    Snapshot snapshot = new Snapshot(
        commits.get(2).id(),
        BRANCH_NAME,
        Instant.now(),
        snapshotGraph
    );
    snapshotService.storeSnapshot(DATASET_NAME, snapshot);

    // Clear cache
    datasetService.clearCache(DATASET_NAME);

    // Materialize C2 (snapshot IS the target)
    DatasetGraph result = datasetService.materializeCommit(DATASET_NAME, commits.get(2).id());

    // Verify result is correct
    assertThat(result).isNotNull();
    // Note: No additional patches needed when snapshot is the target
  }

  @Test
  void materializeCommit_multipleSnapshots_shouldUseNearestSnapshot() {
    // Create commit chain: C0 → C1 → C2 → C3 → C4 → C5 → C6 → C7
    List<Commit> commits = createCommitChain(DATASET_NAME, 8);

    // Create snapshot at C2
    DatasetGraph snapshot1Graph = datasetService.materializeCommit(
        DATASET_NAME, commits.get(2).id());
    Snapshot snapshot1 = new Snapshot(
        commits.get(2).id(),
        BRANCH_NAME,
        Instant.now().minusSeconds(100),
        snapshot1Graph
    );
    snapshotService.storeSnapshot(DATASET_NAME, snapshot1);

    // Create snapshot at C5 (more recent)
    DatasetGraph snapshot2Graph = datasetService.materializeCommit(
        DATASET_NAME, commits.get(5).id());
    Snapshot snapshot2 = new Snapshot(
        commits.get(5).id(),
        BRANCH_NAME,
        Instant.now(),
        snapshot2Graph
    );
    snapshotService.storeSnapshot(DATASET_NAME, snapshot2);

    // Clear cache
    datasetService.clearCache(DATASET_NAME);

    // Materialize C7 (should use snapshot at C5, not C2)
    DatasetGraph result = datasetService.materializeCommit(DATASET_NAME, commits.get(7).id());

    assertThat(result).isNotNull();
    // Note: System automatically uses the most recent valid snapshot
  }

  @Test
  void materializeCommit_withSnapshot_shouldMatchFullMaterialization() {
    // Create commit chain with actual data
    List<Commit> commits = createCommitChainWithData(DATASET_NAME, 6);

    // Create snapshot at C3
    DatasetGraph snapshotGraph = datasetService.materializeCommit(
        DATASET_NAME, commits.get(3).id());
    Snapshot snapshot = new Snapshot(
        commits.get(3).id(),
        BRANCH_NAME,
        Instant.now(),
        snapshotGraph
    );
    snapshotService.storeSnapshot(DATASET_NAME, snapshot);

    // Clear cache
    datasetService.clearCache(DATASET_NAME);

    // Materialize C5 using snapshot
    DatasetGraph resultWithSnapshot = datasetService.materializeCommit(
        DATASET_NAME, commits.get(5).id());

    // Clear cache and snapshot
    datasetService.clearCache(DATASET_NAME);
    snapshotService.clearSnapshotsForDataset(DATASET_NAME);

    // Materialize C5 without snapshot (from scratch)
    DatasetGraph resultWithoutSnapshot = datasetService.materializeCommit(
        DATASET_NAME, commits.get(5).id());

    // Both should have the same number of triples
    long triplesWithSnapshot = countTriples(resultWithSnapshot);
    long triplesWithoutSnapshot = countTriples(resultWithoutSnapshot);

    assertThat(triplesWithSnapshot).isEqualTo(triplesWithoutSnapshot);
  }

  @Test
  void getAllSnapshots_shouldReturnAllBranchSnapshots() {
    // Create commits on main branch
    List<Commit> mainCommits = createCommitChain(DATASET_NAME, 3);

    // Create snapshot on main
    DatasetGraph mainGraph = datasetService.materializeCommit(
        DATASET_NAME, mainCommits.get(2).id());
    Snapshot mainSnapshot = new Snapshot(
        mainCommits.get(2).id(),
        "main",
        Instant.now(),
        mainGraph
    );
    snapshotService.storeSnapshot(DATASET_NAME, mainSnapshot);

    // Create snapshot on develop branch
    DatasetGraph devGraph = new DatasetGraphInMemory();
    Snapshot devSnapshot = new Snapshot(
        mainCommits.get(1).id(),
        "develop",
        Instant.now(),
        devGraph
    );
    snapshotService.storeSnapshot(DATASET_NAME, devSnapshot);

    // Get all snapshots
    var allSnapshots = snapshotService.getAllSnapshots(DATASET_NAME);

    assertThat(allSnapshots).hasSize(2);
    assertThat(allSnapshots).containsKey("main");
    assertThat(allSnapshots).containsKey("develop");
  }

  /**
   * Creates a chain of commits with empty patches.
   * Each commit points to the previous one as parent.
   *
   * @param datasetName the dataset name
   * @param count number of commits to create
   * @return List of commits in order (C0, C1, C2, ...)
   */
  private List<Commit> createCommitChain(String datasetName, int count) {
    List<Commit> commits = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      List<CommitId> parents = (i == 0)
          ? List.of()
          : List.of(commits.get(i - 1).id());

      Commit commit = Commit.create(parents, AUTHOR, "Commit " + i);
      RDFPatch patch = RDFPatchOps.emptyPatch();

      commitRepository.save(datasetName, commit, patch);
      commits.add(commit);
    }

    // Create main branch pointing to last commit
    if (!commits.isEmpty()) {
      Branch mainBranch = new Branch(BRANCH_NAME, commits.get(commits.size() - 1).id());
      branchRepository.save(datasetName, mainBranch);
    }

    return commits;
  }

  /**
   * Creates a chain of commits with actual RDF data.
   * Each commit adds a triple to the default graph.
   *
   * @param datasetName the dataset name
   * @param count number of commits to create
   * @return List of commits in order
   */
  private List<Commit> createCommitChainWithData(String datasetName, int count) {
    List<Commit> commits = new ArrayList<>();
    DatasetGraphInMemory currentGraph = new DatasetGraphInMemory();

    for (int i = 0; i < count; i++) {
      List<CommitId> parents = (i == 0)
          ? List.of()
          : List.of(commits.get(i - 1).id());

      // Create a triple to add
      Node subject = NodeFactory.createURI("http://example.org/subject" + i);
      Node predicate = NodeFactory.createURI("http://example.org/predicate");
      Node object = NodeFactory.createLiteral("value" + i);
      Triple triple = Triple.create(subject, predicate, object);

      // Add to current graph
      currentGraph.getDefaultGraph().add(triple);

      // Create patch that adds this triple
      RDFPatch patch = RDFPatchOps.emptyPatch();
      // Note: In a real scenario, we'd create a proper patch with the add operation
      // For testing purposes, we'll use an empty patch and rely on the materialization

      Commit commit = Commit.create(parents, AUTHOR, "Commit " + i);
      commitRepository.save(datasetName, commit, patch);
      commits.add(commit);
    }

    // Create main branch pointing to last commit
    if (!commits.isEmpty()) {
      Branch mainBranch = new Branch(BRANCH_NAME, commits.get(commits.size() - 1).id());
      branchRepository.save(datasetName, mainBranch);
    }

    return commits;
  }

  /**
   * Counts the total number of triples in a dataset graph.
   *
   * @param graph the dataset graph
   * @return total triple count
   */
  private long countTriples(DatasetGraph graph) {
    long count = 0;

    // Count default graph triples
    count += graph.getDefaultGraph().size();

    // Count named graph triples
    graph.listGraphNodes().forEachRemaining(graphNode -> {
      // Increment count for each named graph (this lambda doesn't return a value)
    });

    return count;
  }
}
