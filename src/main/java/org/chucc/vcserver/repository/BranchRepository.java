package org.chucc.vcserver.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.springframework.stereotype.Repository;

/**
 * In-memory repository for managing branches.
 * Thread-safe implementation using ConcurrentHashMap for concurrent read/write operations.
 */
@Repository
public class BranchRepository {
  private final Map<String, Map<String, Branch>> datasetBranches = new ConcurrentHashMap<>();

  /**
   * Finds a branch by dataset and branch name.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return an Optional containing the branch if found, empty otherwise
   */
  public Optional<Branch> findByDatasetAndName(String datasetName, String branchName) {
    return Optional.ofNullable(datasetBranches.get(datasetName))
        .map(branches -> branches.get(branchName));
  }

  /**
   * Finds all branches for a dataset.
   *
   * @param datasetName the dataset name
   * @return a list of all branches in the dataset
   */
  public List<Branch> findAllByDataset(String datasetName) {
    return Optional.ofNullable(datasetBranches.get(datasetName))
        .map(branches -> List.copyOf(branches.values()))
        .orElse(List.of());
  }

  /**
   * Saves or updates a branch.
   *
   * @param datasetName the dataset name
   * @param branch the branch to save
   * @return the saved branch
   */
  public Branch save(String datasetName, Branch branch) {
    datasetBranches.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(branch.getName(), branch);
    return branch;
  }

  /**
   * Deletes a branch.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return true if the branch was deleted, false if it didn't exist
   */
  public boolean delete(String datasetName, String branchName) {
    Map<String, Branch> branches = datasetBranches.get(datasetName);
    return branches != null && branches.remove(branchName) != null;
  }

  /**
   * Updates the commit ID that a branch points to.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @param newCommitId the new commit ID
   * @return the updated branch
   * @throws IllegalArgumentException if the branch doesn't exist
   */
  public Branch updateBranchHead(String datasetName, String branchName, CommitId newCommitId) {
    Branch branch = findByDatasetAndName(datasetName, branchName)
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + branchName + " in dataset: " + datasetName));

    branch.updateCommit(newCommitId);
    return save(datasetName, branch);
  }

  /**
   * Checks if a branch exists.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return true if the branch exists
   */
  public boolean exists(String datasetName, String branchName) {
    return findByDatasetAndName(datasetName, branchName).isPresent();
  }

  /**
   * Deletes all branches for a dataset.
   *
   * @param datasetName the dataset name
   */
  public void deleteAllByDataset(String datasetName) {
    datasetBranches.remove(datasetName);
  }

  /**
   * Gets all dataset names that have at least one branch.
   *
   * @return list of dataset names
   */
  public List<String> findAllDatasetNames() {
    return List.copyOf(datasetBranches.keySet());
  }
}
