package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to revert a commit by creating a new commit that undoes its changes.
 *
 * @param dataset the dataset name
 * @param branchName the branch to create the revert commit on
 * @param commitId the commit ID to revert
 * @param message optional revert commit message (if null, a default message will be generated)
 * @param author the author of the revert commit
 */
public record RevertCommitCommand(
    String dataset,
    String branchName,
    String commitId,
    String message,
    String author) implements Command {

  /**
   * Creates a new RevertCommitCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public RevertCommitCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (commitId.isBlank()) {
      throw new IllegalArgumentException("Commit ID cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
