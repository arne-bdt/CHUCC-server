package org.chucc.vcserver.command;

import java.util.Objects;
import org.chucc.vcserver.domain.CommitId;

/**
 * Command to delete a graph (DELETE operation).
 * Creates a new commit containing DELETE operations for all triples in the graph.
 *
 * @param dataset the dataset name
 * @param graphIri the graph IRI (null for default graph)
 * @param isDefaultGraph true if this is the default graph
 * @param branch the target branch name
 * @param baseCommit the resolved base commit ID
 * @param author the commit author
 * @param message the commit message
 * @param ifMatchEtag the optional If-Match ETag for precondition checking
 */
public record DeleteGraphCommand(
    String dataset,
    String graphIri,
    boolean isDefaultGraph,
    String branch,
    CommitId baseCommit,
    String author,
    String message,
    String ifMatchEtag) implements Command {

  /**
   * Creates a new DeleteGraphCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public DeleteGraphCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(baseCommit, "Base commit cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Message cannot be blank");
    }

    // Validate graph IRI constraint
    if (!isDefaultGraph && (graphIri == null || graphIri.isBlank())) {
      throw new IllegalArgumentException(
          "Graph IRI must be provided for named graphs");
    }
    if (isDefaultGraph && graphIri != null && !graphIri.isBlank()) {
      throw new IllegalArgumentException(
          "Graph IRI must be null for default graph");
    }
  }
}
