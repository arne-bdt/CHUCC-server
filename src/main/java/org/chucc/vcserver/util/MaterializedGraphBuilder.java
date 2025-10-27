package org.chucc.vcserver.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for building materialized graphs from commit history.
 *
 * <p>This class provides shared rebuild logic used by:
 * <ul>
 *   <li>{@link org.chucc.vcserver.service.MaterializedViewRebuildService} -
 *       Manual rebuild operations</li>
 *   <li>{@link org.chucc.vcserver.repository.InMemoryMaterializedBranchRepository} -
 *       On-demand rebuild after cache eviction</li>
 * </ul>
 *
 * <p>The rebuild process:
 * <ol>
 *   <li>Validate branch exists and get HEAD commit</li>
 *   <li>Build commit chain from root to HEAD</li>
 *   <li>Create new empty graph</li>
 *   <li>Apply all patches from commit history in order</li>
 * </ol>
 *
 * <p>Thread Safety: This class is stateless and thread-safe.
 * All methods are static and operate on provided repositories.
 */
public final class MaterializedGraphBuilder {

  private static final Logger logger = LoggerFactory.getLogger(MaterializedGraphBuilder.class);

  /**
   * Private constructor to prevent instantiation.
   */
  private MaterializedGraphBuilder() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Rebuild graph from commit history for a branch.
   *
   * <p>This method reconstructs the materialized graph by applying all patches
   * from the branch's commit history in chronological order.
   *
   * @param commitRepository the commit repository for fetching commits and patches
   * @param branchRepository the branch repository for branch lookups
   * @param dataset the dataset name
   * @param branch the branch name
   * @return rebuilt graph with all commits applied
   * @throws BranchNotFoundException if branch doesn't exist
   * @throws IllegalStateException if commit chain is broken or patches are missing
   * @throws RuntimeException if patch application fails
   */
  @SuppressFBWarnings(
      value = {"REC_CATCH_EXCEPTION", "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION"},
      justification = "RuntimeException wraps Jena errors for consistent error handling")
  public static DatasetGraph rebuildFromCommitHistory(
      CommitRepository commitRepository,
      BranchRepository branchRepository,
      String dataset,
      String branch) {

    logger.debug("Rebuilding graph for {}/{} from commit history", dataset, branch);

    // 1. Get branch and HEAD commit
    Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
        .orElseThrow(() -> new BranchNotFoundException(branch));

    CommitId headCommitId = branchObj.getCommitId();

    // 2. Build commit chain from root to HEAD
    List<Commit> commitChain = buildCommitChain(commitRepository, dataset, headCommitId);

    logger.debug("Rebuilding {} commits for {}/{}", commitChain.size(), dataset, branch);

    // 3. Create new empty graph
    // Use non-transactional graph to avoid nested transaction issues
    DatasetGraph graph = DatasetGraphFactory.create();

    // 4. Apply all patches in order
    for (Commit commit : commitChain) {
      Optional<RDFPatch> patchOpt = commitRepository.findPatchByDatasetAndId(
          dataset, commit.id());

      if (patchOpt.isEmpty()) {
        String errorMsg = String.format(
            "Missing patch for commit %s in dataset %s", commit.id(), dataset);
        logger.error(errorMsg);
        throw new IllegalStateException(errorMsg);
      }

      RDFPatch patch = patchOpt.get();

      // RDFPatchOps.applyChange manages its own transactions
      try {
        RDFPatchOps.applyChange(graph, patch);
      } catch (Exception e) {
        throw new RuntimeException("Failed to apply patch for commit " + commit.id(), e);
      }
    }

    logger.debug("Successfully rebuilt graph for {}/{} ({} commits applied)",
        dataset, branch, commitChain.size());

    return graph;
  }

  /**
   * Build commit chain from root to target commit.
   *
   * <p>Walks backward from the target commit to the root (commit with no parents),
   * then reverses the list to get chronological order.
   *
   * @param commitRepository the commit repository
   * @param dataset the dataset name
   * @param targetCommitId the target commit ID
   * @return list of commits from root to target (in chronological order)
   * @throws IllegalStateException if commit chain is broken
   */
  private static List<Commit> buildCommitChain(
      CommitRepository commitRepository,
      String dataset,
      CommitId targetCommitId) {

    List<Commit> chain = new ArrayList<>();
    CommitId currentId = targetCommitId;

    // Walk backward from HEAD to root
    while (currentId != null) {
      final CommitId commitIdForLambda = currentId;
      Commit commit = commitRepository.findByDatasetAndId(dataset, currentId)
          .orElseThrow(() -> new IllegalStateException("Missing commit: " + commitIdForLambda));

      chain.add(0, commit);  // Add at beginning (reverse order)

      // Move to parent (assume single parent for now)
      currentId = commit.parents().isEmpty() ? null : commit.parents().get(0);
    }

    return chain;
  }
}
