package org.chucc.vcserver.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Command to squash multiple commits into a single commit.
 * The commits must be contiguous in the branch history.
 *
 * @param dataset the dataset name
 * @param branch the branch containing the commits
 * @param commitIds the commit IDs to squash (in order, must be contiguous)
 * @param message the commit message for the squashed commit
 * @param author the author of the squashed commit (null uses first commit's author)
 */
public record SquashCommand(
    String dataset,
    String branch,
    List<String> commitIds,
    String message,
    String author) implements Command {

  /**
   * Creates a new SquashCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public SquashCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(commitIds, "Commit IDs cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (commitIds.isEmpty()) {
      throw new IllegalArgumentException("Commit IDs cannot be empty");
    }
    if (commitIds.size() < 2) {
      throw new IllegalArgumentException("Must specify at least 2 commits to squash");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }

    // Validate each commit ID is non-null and non-blank
    for (int i = 0; i < commitIds.size(); i++) {
      String commitId = commitIds.get(i);
      if (commitId == null || commitId.isBlank()) {
        throw new IllegalArgumentException("Commit ID at index " + i
            + " cannot be null or blank");
      }
    }

    // Create defensive copy
    commitIds = Collections.unmodifiableList(new ArrayList<>(commitIds));
  }
}
