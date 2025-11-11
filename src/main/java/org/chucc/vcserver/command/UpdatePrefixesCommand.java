package org.chucc.vcserver.command;

import java.util.Map;
import java.util.Optional;

/**
 * Command to update prefix mappings on a branch.
 * Supports PUT (replace all), PATCH (add/update), and DELETE operations.
 */
public record UpdatePrefixesCommand(
    String dataset,
    String branch,
    String author,
    Map<String, String> newPrefixes,
    Operation operation,
    Optional<String> message
) {
  /**
   * Operation type for prefix updates.
   */
  public enum Operation {
    /**
     * Replace all prefixes with new ones (deletes old, adds new).
     */
    PUT,

    /**
     * Add or update selected prefixes (preserves existing).
     */
    PATCH,

    /**
     * Remove selected prefixes.
     */
    DELETE
  }

  /**
   * Creates an update prefixes command.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param author the commit author
   * @param newPrefixes the prefix mappings to add/replace/delete
   * @param operation the operation type (PUT, PATCH, or DELETE)
   * @param message optional commit message
   */
  public UpdatePrefixesCommand {
    // Defensive copy to ensure immutability
    newPrefixes = Map.copyOf(newPrefixes);
  }
}
