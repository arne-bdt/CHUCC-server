package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to merge two branches.
 *
 * @param dataset the dataset name
 * @param into target branch name
 * @param from source ref (branch name or commit ID)
 * @param fastForward fast-forward mode ("allow", "only", "never")
 * @param strategy conflict resolution strategy ("three-way", "ours", "theirs")
 * @param conflictScope conflict resolution scope ("graph", "dataset")
 * @param author author of merge commit
 * @param message merge commit message (optional)
 */
public record MergeCommand(
    String dataset,
    String into,
    String from,
    String fastForward,
    String strategy,
    String conflictScope,
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
    Objects.requireNonNull(strategy, "Strategy cannot be null");
    Objects.requireNonNull(conflictScope, "Conflict scope cannot be null");
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
    if (!java.util.List.of("three-way", "ours", "theirs").contains(strategy)) {
      throw new IllegalArgumentException("Invalid strategy: " + strategy);
    }
    if (!java.util.List.of("graph", "dataset").contains(conflictScope)) {
      throw new IllegalArgumentException("Invalid conflict scope: " + conflictScope);
    }
  }
}
