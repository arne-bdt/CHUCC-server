package org.chucc.vcserver.command;

import java.util.Optional;
import org.chucc.vcserver.domain.CommitId;

/**
 * Command to execute a SPARQL UPDATE operation on a versioned dataset.
 *
 * <p>SPARQL UPDATE operations modify the RDF graph by executing INSERT DATA,
 * DELETE DATA, INSERT/DELETE WHERE, or other update forms. Each successful
 * update creates a new commit in the version control system.</p>
 *
 * <p>This command follows the CQRS pattern where the command handler will:</p>
 * <ol>
 *   <li>Resolve the branch HEAD commit</li>
 *   <li>Validate preconditions (If-Match header)</li>
 *   <li>Materialize the dataset at the current HEAD</li>
 *   <li>Execute the SPARQL UPDATE operation</li>
 *   <li>Compute the RDF diff (patch)</li>
 *   <li>Create and publish a CommitCreatedEvent</li>
 * </ol>
 *
 * @param dataset the dataset name
 * @param branch the branch name to update
 * @param updateString the SPARQL UPDATE query string
 * @param author the commit author
 * @param message the commit message
 * @param expectedHead optional expected HEAD commit (for If-Match precondition)
 */
public record SparqlUpdateCommand(
    String dataset,
    String branch,
    String updateString,
    String author,
    String message,
    Optional<CommitId> expectedHead
) implements Command {

  /**
   * Creates a new SPARQL UPDATE command.
   *
   * @param dataset the dataset name
   * @param branch the branch name to update
   * @param updateString the SPARQL UPDATE query string
   * @param author the commit author
   * @param message the commit message
   * @param expectedHead optional expected HEAD commit (for If-Match precondition)
   * @throws IllegalArgumentException if any required parameter is null or blank
   */
  public SparqlUpdateCommand {
    if (dataset == null || dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset must not be null or blank");
    }
    if (branch == null || branch.isBlank()) {
      throw new IllegalArgumentException("Branch must not be null or blank");
    }
    if (updateString == null || updateString.isBlank()) {
      throw new IllegalArgumentException("Update string must not be null or blank");
    }
    if (author == null || author.isBlank()) {
      throw new IllegalArgumentException("Author must not be null or blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Message must not be null or blank");
    }
    if (expectedHead == null) {
      throw new IllegalArgumentException("Expected head must not be null (use Optional.empty())");
    }
  }
}
