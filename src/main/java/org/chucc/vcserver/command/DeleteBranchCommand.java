package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to delete a branch.
 *
 * @param dataset the dataset name
 * @param branchName the name of the branch to delete
 * @param author the author of the deletion operation
 */
public record DeleteBranchCommand(
    String dataset,
    String branchName,
    String author) implements Command {

  /**
   * Creates a new DeleteBranchCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public DeleteBranchCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
