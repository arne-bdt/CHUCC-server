package org.chucc.vcserver.command;

import java.util.ArrayList;
import java.util.List;
import org.chucc.vcserver.domain.CommitId;

/**
 * Command to execute batch graph operations (PUT, POST, PATCH, DELETE) atomically.
 * Creates one or more commits containing all graph changes.
 *
 * @param dataset the dataset name
 * @param branch the target branch name
 * @param baseCommit the resolved base commit ID
 * @param operations the list of graph operations to execute
 * @param author the commit author
 * @param message the commit message
 * @param mode the batch mode ("single" or "multiple")
 */
public record BatchGraphsCommand(
    String dataset,
    String branch,
    CommitId baseCommit,
    List<GraphOperation> operations,
    String author,
    String message,
    String mode) implements Command {

  /**
   * Creates a new BatchGraphsCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public BatchGraphsCommand {
    if (dataset == null || dataset.isEmpty()) {
      throw new IllegalArgumentException("Dataset cannot be null or empty");
    }
    if (branch == null || branch.isEmpty()) {
      throw new IllegalArgumentException("Branch cannot be null or empty");
    }
    if (baseCommit == null) {
      throw new IllegalArgumentException("Base commit cannot be null");
    }
    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("Operations cannot be null or empty");
    }
    if (author == null || author.isEmpty()) {
      throw new IllegalArgumentException("Author cannot be null or empty");
    }
    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException("Message cannot be null or empty");
    }
    if (mode == null || mode.isEmpty()) {
      throw new IllegalArgumentException("Mode cannot be null or empty");
    }
    if (!"single".equals(mode) && !"multiple".equals(mode)) {
      throw new IllegalArgumentException("Mode must be 'single' or 'multiple'");
    }

    // Defensive copy
    operations = new ArrayList<>(operations);
  }

  /**
   * Returns a defensive copy of the operations list.
   *
   * @return a new list containing the operations
   */
  @Override
  public List<GraphOperation> operations() {
    return new ArrayList<>(operations);
  }

  /**
   * Represents a single graph operation in the batch.
   *
   * @param method the HTTP method (PUT, POST, PATCH, DELETE)
   * @param graphIri the graph IRI (null for default graph)
   * @param isDefaultGraph true if this is the default graph
   * @param rdfContent the RDF content (for PUT/POST)
   * @param contentType the content type (for PUT/POST/PATCH)
   * @param patch the RDF Patch content (for PATCH)
   */
  public record GraphOperation(
      String method,
      String graphIri,
      boolean isDefaultGraph,
      String rdfContent,
      String contentType,
      String patch) {

    /**
     * Creates a new GraphOperation with validation.
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public GraphOperation {
      if (method == null || method.isEmpty()) {
        throw new IllegalArgumentException("Method cannot be null or empty");
      }
      if (!"PUT".equals(method) && !"POST".equals(method)
          && !"PATCH".equals(method) && !"DELETE".equals(method)) {
        throw new IllegalArgumentException("Method must be PUT, POST, PATCH, or DELETE");
      }

      // Validate based on method
      if ("PUT".equals(method) || "POST".equals(method)) {
        if (rdfContent == null || rdfContent.isEmpty()) {
          throw new IllegalArgumentException(
              "RDF content cannot be null or empty for " + method);
        }
        if (contentType == null || contentType.isEmpty()) {
          throw new IllegalArgumentException(
              "Content type cannot be null or empty for " + method);
        }
      } else if ("PATCH".equals(method)) {
        if (patch == null || patch.isEmpty()) {
          throw new IllegalArgumentException("Patch cannot be null or empty for PATCH");
        }
        if (!isDefaultGraph && (graphIri == null || graphIri.isEmpty())) {
          throw new IllegalArgumentException("Graph IRI cannot be null or empty for PATCH");
        }
      } else if ("DELETE".equals(method)) {
        if (!isDefaultGraph && (graphIri == null || graphIri.isEmpty())) {
          throw new IllegalArgumentException("Graph IRI cannot be null or empty for DELETE");
        }
      }
    }
  }
}
