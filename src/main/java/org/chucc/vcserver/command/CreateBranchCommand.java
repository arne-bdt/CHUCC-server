package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to create a new branch from an existing commit.
 *
 * @param dataset the dataset name
 * @param branchName the name of the new branch
 * @param fromCommitId the commit ID to branch from (parent commit)
 */
public record CreateBranchCommand(
    String dataset,
    String branchName,
    String fromCommitId) implements Command {

  /**
   * Creates a new CreateBranchCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateBranchCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(fromCommitId, "From commit ID cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (fromCommitId.isBlank()) {
      throw new IllegalArgumentException("From commit ID cannot be blank");
    }
  }
}
