package org.chucc.vcserver.command;

import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.util.GraphCommandValidator;

/**
 * Command to merge RDF content into a graph (POST operation).
 * Creates a new commit containing only ADD operations for triples not already present.
 *
 * @param dataset the dataset name
 * @param graphIri the graph IRI (null for default graph)
 * @param isDefaultGraph true if this is the default graph
 * @param branch the target branch name
 * @param baseCommit the resolved base commit ID
 * @param rdfContent the serialized RDF content to merge
 * @param contentType the content type for parsing (e.g., "text/turtle")
 * @param author the commit author
 * @param message the commit message
 * @param ifMatchEtag the optional If-Match ETag for precondition checking
 */
public record PostGraphCommand(
    String dataset,
    String graphIri,
    boolean isDefaultGraph,
    String branch,
    CommitId baseCommit,
    String rdfContent,
    String contentType,
    String author,
    String message,
    String ifMatchEtag) implements Command {

  /**
   * Creates a new PostGraphCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public PostGraphCommand {
    GraphCommandValidator.validateGraphCommand(
        dataset, graphIri, isDefaultGraph, branch, baseCommit,
        rdfContent, contentType, author, message
    );
  }
}
