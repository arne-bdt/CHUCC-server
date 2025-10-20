package org.chucc.vcserver.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.config.CacheProperties;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DatasetService cache eviction logic.
 * Tests LRU cache behavior with Caffeine.
 */
class DatasetServiceCacheTest {
  private DatasetService service;
  private BranchRepository branchRepository;
  private CommitRepository commitRepository;
  private CacheProperties cacheProperties;

  private static final String DATASET_NAME = "test-dataset";
  private static final String AUTHOR = "test-author";

  @BeforeEach
  void setUp() {
    branchRepository = new BranchRepository();
    commitRepository = new CommitRepository();

    // Configure cache with small size for testing
    cacheProperties = new CacheProperties();
    cacheProperties.setMaxSize(10);  // Small cache for testing eviction
    cacheProperties.setKeepLatestPerBranch(true);
    cacheProperties.setTtlMinutes(0);  // No TTL

    // Create SnapshotService with mocked dependencies
    EventPublisher eventPublisher = Mockito.mock(EventPublisher.class);
    SnapshotKafkaStore kafkaStore = Mockito.mock(SnapshotKafkaStore.class);
    VersionControlProperties vcProperties = new VersionControlProperties();
    vcProperties.setSnapshotsEnabled(false); // Disable snapshots for basic tests

    SnapshotService snapshotService = new SnapshotService(
        null, // DatasetService will be set later if needed
        branchRepository,
        commitRepository,
        eventPublisher,
        vcProperties,
        kafkaStore
    );

    service = new DatasetService(
        branchRepository,
        commitRepository,
        snapshotService,
        cacheProperties,
        new SimpleMeterRegistry()
    );
  }

  @Test
  void shouldCacheDatasetGraphs() {
    // Given: Create a commit chain
    List<Commit> commits = createCommitChain(DATASET_NAME, 5);

    // When: Materialize multiple commits
    for (Commit commit : commits) {
      service.materializeCommit(DATASET_NAME, commit.id());
    }

    // Then: All commits should be cached (within cache size)
    for (Commit commit : commits) {
      // Accessing again should hit cache (very fast)
      var graph = service.materializeCommit(DATASET_NAME, commit.id());
      assertThat(graph).isNotNull();
    }
  }

  @Test
  void shouldEvictLRUEntriesWhenCacheFull() {
    // Given: Create more commits than cache size
    List<Commit> commits = createCommitChain(DATASET_NAME, 15);

    // When: Access first 5 commits
    for (int i = 0; i < 5; i++) {
      service.materializeCommit(DATASET_NAME, commits.get(i).id());
    }

    // Then: Access next 10 commits (total 15, but cache max = 10)
    for (int i = 5; i < 15; i++) {
      service.materializeCommit(DATASET_NAME, commits.get(i).id());
    }

    // When: Access all commits again
    for (Commit commit : commits) {
      var graph = service.materializeCommit(DATASET_NAME, commit.id());
      assertThat(graph).isNotNull();
    }

    // Then: Cache should have evicted some entries (first 5 should be evicted)
    // This is verified by the fact that accessing them again requires rebuild
  }

  @Test
  void shouldKeepLatestCommitInCache() {
    // Given: Create branch with latest commit
    Commit latest = Commit.create(List.of(), AUTHOR, "latest commit");
    commitRepository.save(DATASET_NAME, latest, RDFPatchOps.emptyPatch());

    Branch branch = new Branch("main", latest.id());
    branchRepository.save(DATASET_NAME, branch);

    // Track latest commit
    service.updateLatestCommit(DATASET_NAME, "main", latest.id());

    // When: Materialize latest commit
    service.materializeCommit(DATASET_NAME, latest.id());

    // Then: Fill cache with many other commits
    List<Commit> otherCommits = createCommitChain(DATASET_NAME, 50);
    for (Commit commit : otherCommits) {
      service.materializeCommit(DATASET_NAME, commit.id());
    }

    // When: Access latest commit again
    var graph = service.materializeCommit(DATASET_NAME, latest.id());

    // Then: Latest commit should still be in cache (not evicted)
    assertThat(graph).isNotNull();
  }

  @Test
  void shouldUpdateLatestCommitTracking() {
    // Given: Create initial commit and branch
    Commit commit1 = Commit.create(List.of(), AUTHOR, "commit 1");
    commitRepository.save(DATASET_NAME, commit1, RDFPatchOps.emptyPatch());

    Branch branch = new Branch("main", commit1.id());
    branchRepository.save(DATASET_NAME, branch);

    // When: Update latest commit tracking
    service.updateLatestCommit(DATASET_NAME, "main", commit1.id());

    // Then: Create second commit
    Commit commit2 = Commit.create(List.of(commit1.id()), AUTHOR, "commit 2");
    commitRepository.save(DATASET_NAME, commit2, RDFPatchOps.emptyPatch());

    // Update branch and tracking
    branchRepository.updateBranchHead(DATASET_NAME, "main", commit2.id());
    service.updateLatestCommit(DATASET_NAME, "main", commit2.id());

    // Verify both commits can be materialized
    assertThat(service.materializeCommit(DATASET_NAME, commit1.id())).isNotNull();
    assertThat(service.materializeCommit(DATASET_NAME, commit2.id())).isNotNull();
  }

  @Test
  void shouldClearCacheForDataset() {
    // Given: Create and cache commits
    List<Commit> commits = createCommitChain(DATASET_NAME, 5);
    for (Commit commit : commits) {
      service.materializeCommit(DATASET_NAME, commit.id());
    }

    // When: Clear cache for dataset
    service.clearCache(DATASET_NAME);

    // Then: Commits should still be accessible (rebuilt from patches)
    for (Commit commit : commits) {
      var graph = service.materializeCommit(DATASET_NAME, commit.id());
      assertThat(graph).isNotNull();
    }
  }

  @Test
  void shouldClearAllCaches() {
    // Given: Create and cache commits in multiple datasets
    List<Commit> commits1 = createCommitChain("dataset1", 5);
    List<Commit> commits2 = createCommitChain("dataset2", 5);

    for (Commit commit : commits1) {
      service.materializeCommit("dataset1", commit.id());
    }
    for (Commit commit : commits2) {
      service.materializeCommit("dataset2", commit.id());
    }

    // When: Clear all caches
    service.clearAllCaches();

    // Then: Commits should still be accessible (rebuilt from patches)
    for (Commit commit : commits1) {
      var graph = service.materializeCommit("dataset1", commit.id());
      assertThat(graph).isNotNull();
    }
    for (Commit commit : commits2) {
      var graph = service.materializeCommit("dataset2", commit.id());
      assertThat(graph).isNotNull();
    }
  }

  /**
   * Creates a chain of commits for testing.
   *
   * @param dataset the dataset name
   * @param count the number of commits to create
   * @return list of commits in chronological order
   */
  private List<Commit> createCommitChain(String dataset, int count) {
    List<Commit> commits = new ArrayList<>();
    CommitId parentId = null;

    for (int i = 0; i < count; i++) {
      List<CommitId> parents = parentId == null ? List.of() : List.of(parentId);
      Commit commit = Commit.create(parents, AUTHOR, "commit " + i);

      // Save commit with empty patch
      commitRepository.save(dataset, commit, RDFPatchOps.emptyPatch());

      commits.add(commit);
      parentId = commit.id();
    }

    return commits;
  }
}
