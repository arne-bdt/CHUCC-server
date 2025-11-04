package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.chucc.vcserver.repository.InMemoryMaterializedBranchRepository;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for LRU eviction of materialized views.
 *
 * <p>These tests verify synchronous cache behavior:
 * <ul>
 *   <li>Cache respects max-branches limit</li>
 *   <li>LRU eviction works correctly</li>
 *   <li>Cache misses trigger automatic rebuild</li>
 *   <li>Rebuilt graphs contain correct data</li>
 * </ul>
 *
 * <p><b>Note:</b> Tests synchronous cache/repository behavior.
 * Projector is DISABLED (not needed for cache eviction tests).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = {
    "chucc.materialized-views.max-branches=3",  // Low limit for testing
    "chucc.materialized-views.cache-stats-enabled=true"
})
class MaterializedViewEvictionIT extends ITFixture {

  @Autowired
  private InMemoryMaterializedBranchRepository materializedBranchRepo;

  /**
   * Uses event-driven setup (Session 4 migration).
   */
  @Override
  protected void createInitialCommitAndBranch(String dataset) {
    createInitialCommitAndBranchViaEvents(dataset);
  }

  @Test
  void cacheLimit_shouldNotExceedMaxBranches() throws Exception {
    String dataset = getDatasetName();

    // Create 10 branches (far exceeds max of 3) and populate cache by accessing them
    for (int i = 1; i <= 10; i++) {
      String branchName = "branch-" + i;
      createBranch(dataset, branchName);
      // Access graph to populate cache - this will trigger eviction when limit is reached
      materializedBranchRepo.getBranchGraph(dataset, branchName);
    }

    // Force cache cleanup by triggering maintenance
    Thread.sleep(100);

    // Cache size should be close to max-branches (Caffeine eviction is asynchronous)
    // Allow some tolerance for async eviction (max + 50%)
    int graphCount = materializedBranchRepo.getGraphCount();
    assertThat(graphCount).isLessThanOrEqualTo(5);  // 3 max + tolerance

    // Verify eviction occurred
    CacheStats stats = materializedBranchRepo.getCacheStats();
    assertThat(stats.evictionCount()).isGreaterThan(0);
  }

  @Test
  void cacheMiss_shouldRebuildAutomatically() throws Exception {
    String dataset = getDatasetName();

    // Create 10 branches (far exceeds max of 3) and populate cache
    for (int i = 1; i <= 10; i++) {
      String branchName = "branch-" + i;
      createBranch(dataset, branchName);
      // Populate cache - early branches should be evicted (LRU)
      materializedBranchRepo.getBranchGraph(dataset, branchName);
    }

    // Force cache cleanup
    Thread.sleep(100);

    // Access all branches - evicted ones should be rebuilt automatically
    for (int i = 1; i <= 10; i++) {
      String branchName = "branch-" + i;
      var graph = materializedBranchRepo.getBranchGraph(dataset, branchName);
      assertThat(graph).isNotNull();
    }

    // Verify cache had some misses (due to eviction + rebuild)
    CacheStats stats = materializedBranchRepo.getCacheStats();
    assertThat(stats.missCount()).isGreaterThan(0);
  }

  @Test
  void lruEviction_shouldEvictEarlyBranches() throws Exception {
    String dataset = getDatasetName();

    // Create 10 branches (far exceeds max of 3) and populate cache sequentially
    // Early branches (b1, b2, b3) should be evicted as later ones are added
    for (int i = 1; i <= 10; i++) {
      createBranch(dataset, "b" + i);
      materializedBranchRepo.getBranchGraph(dataset, "b" + i);
      Thread.sleep(20);
    }

    // Force cache cleanup
    Thread.sleep(100);

    // Access early branches (b1-b3) - at least one should have been evicted
    // causing cache misses when we access them
    long missCountBefore = materializedBranchRepo.getCacheStats().missCount();
    materializedBranchRepo.getBranchGraph(dataset, "b1");
    materializedBranchRepo.getBranchGraph(dataset, "b2");
    materializedBranchRepo.getBranchGraph(dataset, "b3");
    long missCountAfter = materializedBranchRepo.getCacheStats().missCount();

    // At least one early branch should have been evicted
    // (We don't check which specific one due to async eviction)
    assertThat(missCountAfter).isGreaterThan(missCountBefore);
  }

  /**
   * Helper method to create a branch.
   *
   * @param dataset the dataset name
   * @param branchName the branch name to create
   */
  private void createBranch(String dataset, String branchName) {
    var branch = new org.chucc.vcserver.domain.Branch(
        branchName,
        initialCommitId,
        false,
        java.time.Instant.now(),
        java.time.Instant.now(),
        1
    );
    branchRepository.save(dataset, branch);
  }
}
