package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to reset a branch to point to a different commit.
 *
 * @param dataset the dataset name
 * @param branchName the branch to reset
 * @param toCommitId the commit ID to reset the branch to
 */
public record ResetBranchCommand(
    String dataset,
    String branchName,
    String toCommitId) implements Command {

  /**
   * Creates a new ResetBranchCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public ResetBranchCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(toCommitId, "To commit ID cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (toCommitId.isBlank()) {
      throw new IllegalArgumentException("To commit ID cannot be blank");
    }
  }
}
