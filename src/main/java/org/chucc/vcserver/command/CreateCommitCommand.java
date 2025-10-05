package org.chucc.vcserver.command;

import java.util.Map;
import java.util.Objects;

/**
 * Command to create a new commit on a branch.
 *
 * @param dataset the dataset name
 * @param branchName the branch to commit to
 * @param sparqlUpdate the SPARQL UPDATE query representing the changes
 * @param message the commit message
 * @param author the commit author
 * @param metadata optional commit metadata
 */
public record CreateCommitCommand(
    String dataset,
    String branchName,
    String sparqlUpdate,
    String message,
    String author,
    Map<String, String> metadata) implements Command {

  /**
   * Creates a new CreateCommitCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateCommitCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(sparqlUpdate, "SPARQL Update cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (sparqlUpdate.isBlank()) {
      throw new IllegalArgumentException("SPARQL Update cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    // Ensure metadata is immutable
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
