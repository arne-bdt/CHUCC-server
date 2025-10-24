package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to create a new branch from an existing commit or branch.
 *
 * @param dataset the dataset name
 * @param branchName the name of the new branch
 * @param sourceRef the source ref (branch name or commit ID) to branch from
 * @param isProtected whether the branch is protected from deletion/force-push
 * @param author the author who creates the branch
 */
public record CreateBranchCommand(
    String dataset,
    String branchName,
    String sourceRef,
    boolean isProtected,
    String author) implements Command {

  /**
   * Creates a new CreateBranchCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateBranchCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(sourceRef, "Source ref cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (sourceRef.isBlank()) {
      throw new IllegalArgumentException("Source ref cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
