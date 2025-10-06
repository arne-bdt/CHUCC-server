package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to cherry-pick a commit onto a target branch.
 *
 * @param dataset the dataset name
 * @param commitId the commit ID to cherry-pick
 * @param targetBranch the target branch to apply the cherry-pick to
 * @param message optional cherry-pick commit message (if null, a default message is generated)
 * @param author the author of the cherry-pick commit
 */
public record CherryPickCommand(
    String dataset,
    String commitId,
    String targetBranch,
    String message,
    String author) implements Command {

  /**
   * Creates a new CherryPickCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CherryPickCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");
    Objects.requireNonNull(targetBranch, "Target branch cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (commitId.isBlank()) {
      throw new IllegalArgumentException("Commit ID cannot be blank");
    }
    if (targetBranch.isBlank()) {
      throw new IllegalArgumentException("Target branch cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
