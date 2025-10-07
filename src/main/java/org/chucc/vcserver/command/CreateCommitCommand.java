package org.chucc.vcserver.command;

import java.util.Map;
import java.util.Objects;

/**
 * Command to create a new commit on a branch.
 * Supports both SPARQL UPDATE and RDF Patch input.
 *
 * @param dataset the dataset name
 * @param branchName the branch to commit to
 * @param baseCommitId optional base commit ID (overrides branch HEAD if provided)
 * @param sparqlUpdate the SPARQL UPDATE query representing the changes (nullable)
 * @param patch the RDF Patch content (nullable)
 * @param message the commit message
 * @param author the commit author
 * @param metadata optional commit metadata
 */
public record CreateCommitCommand(
    String dataset,
    String branchName,
    String baseCommitId,
    String sparqlUpdate,
    String patch,
    String message,
    String author,
    Map<String, String> metadata) implements Command {

  /**
   * Creates a new CreateCommitCommand with validation.
   * Either sparqlUpdate or patch must be provided, but not both.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateCommitCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branchName, "Branch name cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branchName.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    // Validate that exactly one of sparqlUpdate or patch is provided
    boolean hasSparqlUpdate = sparqlUpdate != null && !sparqlUpdate.isBlank();
    boolean hasPatch = patch != null && !patch.isBlank();

    if (!hasSparqlUpdate && !hasPatch) {
      throw new IllegalArgumentException(
          "Either sparqlUpdate or patch must be provided");
    }
    if (hasSparqlUpdate && hasPatch) {
      throw new IllegalArgumentException(
          "Cannot provide both sparqlUpdate and patch");
    }

    // Ensure metadata is immutable
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  /**
   * Checks if this command uses an RDF Patch.
   *
   * @return true if patch is provided
   */
  public boolean hasPatch() {
    return patch != null && !patch.isBlank();
  }

  /**
   * Checks if this command uses a SPARQL Update.
   *
   * @return true if sparqlUpdate is provided
   */
  public boolean hasSparqlUpdate() {
    return sparqlUpdate != null && !sparqlUpdate.isBlank();
  }
}
