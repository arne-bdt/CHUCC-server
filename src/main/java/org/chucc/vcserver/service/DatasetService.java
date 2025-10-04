package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.mem.DatasetGraphInMemory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.DatasetRef;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing Jena in-memory datasets with version control.
 * Provides datasets for specific branches or commit snapshots.
 * Thread-safe for concurrent read operations.
 */
@Service
public class DatasetService {
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  // Cache of materialized dataset graphs per commit
  private final Map<String, Map<CommitId, DatasetGraphInMemory>> datasetCache =
      new ConcurrentHashMap<>();

  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public DatasetService(BranchRepository branchRepository, CommitRepository commitRepository) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
  }

  /**
   * Gets a Jena Dataset for a given dataset reference.
   * Resolves branch references to their current HEAD commit.
   *
   * @param datasetRef the dataset reference (dataset name + ref)
   * @return a Dataset instance materialized to the specified version
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getDataset(DatasetRef datasetRef) {
    CommitId commitId = resolveRef(datasetRef);
    DatasetGraph datasetGraph = getOrCreateDatasetGraph(datasetRef.datasetName(), commitId);

    // Return a read-only view for safety
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Gets a mutable Dataset for a given dataset reference.
   * Used for write operations that will create new commits.
   *
   * @param datasetRef the dataset reference
   * @return a mutable Dataset instance
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  public Dataset getMutableDataset(DatasetRef datasetRef) {
    CommitId commitId = resolveRef(datasetRef);
    DatasetGraph datasetGraph = getOrCreateDatasetGraph(datasetRef.datasetName(), commitId);
    return DatasetFactory.wrap(datasetGraph);
  }

  /**
   * Creates a new dataset with an initial empty commit on the main branch.
   *
   * @param datasetName the dataset name
   * @param author the author of the initial commit
   * @return the created branch
   */
  public Branch createDataset(String datasetName, String author) {
    // Create initial empty commit
    Commit initialCommit = Commit.create(
        java.util.List.of(),
        author,
        "Initial commit"
    );

    // Create empty patch for initial commit
    RDFPatch emptyPatch = RDFPatchOps.emptyPatch();

    // Save commit and patch
    commitRepository.save(datasetName, initialCommit, emptyPatch);

    // Create main branch pointing to initial commit
    Branch mainBranch = new Branch("main", initialCommit.id());
    branchRepository.save(datasetName, mainBranch);

    // Initialize empty dataset graph for this commit
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();
    cacheDatasetGraph(datasetName, initialCommit.id(), datasetGraph);

    return mainBranch;
  }

  /**
   * Resolves a reference (branch name or commit ID) to a commit ID.
   *
   * @param datasetRef the dataset reference
   * @return the resolved commit ID
   * @throws IllegalArgumentException if the reference cannot be resolved
   */
  private CommitId resolveRef(DatasetRef datasetRef) {
    String ref = datasetRef.ref();

    // Try to parse as commit ID first
    try {
      CommitId commitId = CommitId.of(ref);
      if (commitRepository.exists(datasetRef.datasetName(), commitId)) {
        return commitId;
      }
    } catch (IllegalArgumentException e) {
      // Not a valid commit ID, try as branch name
    }

    // Try to resolve as branch name
    return branchRepository.findByDatasetAndName(datasetRef.datasetName(), ref)
        .map(Branch::getCommitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Cannot resolve reference: " + ref + " in dataset: " + datasetRef.datasetName()));
  }

  /**
   * Gets or creates a DatasetGraphInMemory for a specific commit.
   * Uses caching to avoid rebuilding the same dataset multiple times.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return the dataset graph materialized to the specified commit
   */
  private DatasetGraphInMemory getOrCreateDatasetGraph(String datasetName, CommitId commitId) {
    return datasetCache.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(commitId, id -> buildDatasetGraph(datasetName, id));
  }

  /**
   * Builds a DatasetGraphInMemory by applying all patches from the commit history.
   *
   * @param datasetName the dataset name
   * @param commitId the target commit ID
   * @return the materialized dataset graph
   */
  private DatasetGraphInMemory buildDatasetGraph(String datasetName, CommitId commitId) {
    DatasetGraphInMemory datasetGraph = new DatasetGraphInMemory();

    // Get the commit
    Commit commit = commitRepository.findByDatasetAndId(datasetName, commitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Commit not found: " + commitId + " in dataset: " + datasetName));

    // Build the graph by applying patches from commit history
    applyPatchHistory(datasetName, commit, datasetGraph);

    return datasetGraph;
  }

  /**
   * Recursively applies patches from commit history to build the dataset state.
   *
   * @param datasetName the dataset name
   * @param commit the current commit
   * @param datasetGraph the dataset graph to apply patches to
   */
  private void applyPatchHistory(String datasetName, Commit commit,
                                   DatasetGraphInMemory datasetGraph) {
    // First, recursively process parent commits
    for (CommitId parentId : commit.parents()) {
      Commit parentCommit = commitRepository.findByDatasetAndId(datasetName, parentId)
          .orElseThrow(() -> new IllegalArgumentException(
              "Parent commit not found: " + parentId));
      applyPatchHistory(datasetName, parentCommit, datasetGraph);
    }

    // Then apply this commit's patch
    commitRepository.findPatchByDatasetAndId(datasetName, commit.id())
        .ifPresent(patch -> RDFPatchOps.applyChange(datasetGraph, patch));
  }

  /**
   * Caches a dataset graph for a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @param datasetGraph the dataset graph to cache
   */
  private void cacheDatasetGraph(String datasetName, CommitId commitId,
                                   DatasetGraphInMemory datasetGraph) {
    datasetCache.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(commitId, datasetGraph);
  }

  /**
   * Clears the dataset cache for a specific dataset.
   *
   * @param datasetName the dataset name
   */
  public void clearCache(String datasetName) {
    datasetCache.remove(datasetName);
  }

  /**
   * Clears all dataset caches.
   */
  public void clearAllCaches() {
    datasetCache.clear();
  }
}
