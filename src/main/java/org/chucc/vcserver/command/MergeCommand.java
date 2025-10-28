package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to merge two branches (Phase 1: Core functionality).
 *
 * @param dataset the dataset name
 * @param into target branch name
 * @param from source ref (branch name or commit ID)
 * @param fastForward fast-forward mode ("allow", "only", "never")
 * @param author author of merge commit
 * @param message merge commit message (optional)
 */
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String author,
    String message) implements Command {

  /**
   * Creates a new MergeCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public MergeCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(into, "Target branch (into) cannot be null");
    Objects.requireNonNull(from, "Source ref (from) cannot be null");
    Objects.requireNonNull(fastForward, "Fast-forward mode cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (into.isBlank()) {
      throw new IllegalArgumentException("Target branch (into) cannot be blank");
    }
    if (from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
    if (!java.util.List.of("allow", "only", "never").contains(fastForward)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + fastForward);
    }
  }
}
