package org.chucc.vcserver.repository;

import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;

/**
 * Repository for managing materialized DatasetGraph views per branch HEAD.
 *
 * <p>This repository maintains continuously updated, transactional RDF graphs
 * for each branch. Unlike on-demand materialization, these graphs are always
 * kept in sync with the latest commit on each branch.
 *
 * <p>Memory usage scales with the number of branches and triples per branch,
 * not with the depth of commit history (which is stored separately in
 * CommitRepository).
 *
 * @see org.chucc.vcserver.repository.CommitRepository for event-sourced commit history
 * @see org.apache.jena.sparql.core.DatasetGraph for Jena's transactional graph API
 */
public interface MaterializedBranchRepository {

  /**
   * Get the materialized graph for a branch HEAD, creating it if necessary.
   *
   * <p>If the graph doesn't exist, an empty DatasetGraph is created and stored.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return the transactional DatasetGraph for this branch (never null)
   */
  DatasetGraph getBranchGraph(String dataset, String branch);

  /**
   * Apply an RDF patch to a branch's materialized graph within a transaction.
   *
   * <p>The operation is atomic: either the patch is fully applied and committed,
   * or it is rolled back on error. Uses Jena's READ_WRITE transaction mode.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param patch the RDF patch to apply
   * @throws org.chucc.vcserver.exception.PatchApplicationException if patch application fails
   */
  void applyPatchToBranch(String dataset, String branch, RDFPatch patch);

  /**
   * Create a new branch by cloning the parent branch's graph or starting empty.
   *
   * <p>If parentBranch is provided, the parent's current graph state is cloned.
   * Otherwise, an empty DatasetGraph is created.
   *
   * @param dataset the dataset name
   * @param branch the new branch name
   * @param parentBranch optional parent branch to clone from
   * @throws org.chucc.vcserver.exception.BranchNotFoundException if parent branch doesn't exist
   */
  void createBranch(String dataset, String branch, Optional<String> parentBranch);

  /**
   * Delete a branch's materialized graph and release memory.
   *
   * <p>This operation is idempotent - calling it on a non-existent branch
   * has no effect.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   */
  void deleteBranch(String dataset, String branch);

  /**
   * Check if a materialized graph exists for a branch.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @return true if the branch has a materialized graph
   */
  boolean exists(String dataset, String branch);

  /**
   * Get the current number of materialized graphs.
   *
   * @return count of materialized graphs across all datasets
   */
  int getGraphCount();
}
