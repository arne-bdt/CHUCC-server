package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to rebase a branch onto another reference.
 *
 * @param dataset the dataset name
 * @param branch the branch to rebase
 * @param onto the target ref (branch or commit) to rebase onto
 * @param from the base commit (exclusive) - commits after this will be rebased
 * @param author the author of the rebase operation
 */
public record RebaseCommand(
    String dataset,
    String branch,
    String onto,
    String from,
    String author) implements Command {

  /**
   * Creates a new RebaseCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public RebaseCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(onto, "Onto reference cannot be null");
    Objects.requireNonNull(from, "From commit cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (onto.isBlank()) {
      throw new IllegalArgumentException("Onto reference cannot be blank");
    }
    if (from.isBlank()) {
      throw new IllegalArgumentException("From commit cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
