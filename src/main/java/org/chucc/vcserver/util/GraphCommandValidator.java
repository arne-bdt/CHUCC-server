package org.chucc.vcserver.util;

import java.util.Objects;
import org.chucc.vcserver.domain.CommitId;

/**
 * Validator for graph command parameters.
 */
public final class GraphCommandValidator {

  private GraphCommandValidator() {
    // Utility class
  }

  /**
   * Validates common graph command parameters.
   *
   * @param dataset the dataset name
   * @param graphIri the graph IRI
   * @param isDefaultGraph true if this is the default graph
   * @param branch the branch name
   * @param baseCommit the base commit
   * @param rdfContent the RDF content
   * @param contentType the content type
   * @param author the author
   * @param message the message
   * @throws IllegalArgumentException if validation fails
   */
  public static void validateGraphCommand(
      String dataset,
      String graphIri,
      boolean isDefaultGraph,
      String branch,
      CommitId baseCommit,
      String rdfContent,
      String contentType,
      String author,
      String message) {

    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(branch, "Branch cannot be null");
    Objects.requireNonNull(baseCommit, "Base commit cannot be null");
    Objects.requireNonNull(rdfContent, "RDF content cannot be null");
    Objects.requireNonNull(contentType, "Content type cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(message, "Message cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (branch.isBlank()) {
      throw new IllegalArgumentException("Branch cannot be blank");
    }
    if (contentType.isBlank()) {
      throw new IllegalArgumentException("Content type cannot be blank");
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
