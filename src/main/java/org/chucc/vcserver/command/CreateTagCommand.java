package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to create a new tag pointing to a commit.
 *
 * @param dataset the dataset name
 * @param tagName the name of the new tag
 * @param commitId the commit ID to tag
 * @param message optional tag message
 */
public record CreateTagCommand(
    String dataset,
    String tagName,
    String commitId,
    String message) implements Command {

  /**
   * Creates a new CreateTagCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateTagCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(tagName, "Tag name cannot be null");
    Objects.requireNonNull(commitId, "Commit ID cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (tagName.isBlank()) {
      throw new IllegalArgumentException("Tag name cannot be blank");
    }
    if (commitId.isBlank()) {
      throw new IllegalArgumentException("Commit ID cannot be blank");
    }
  }
}
