package org.chucc.vcserver.dto;

/**
 * Single write operation in a batch request.
 *
 * <p>Represents either a SPARQL Update query or an RDF Patch to be applied.
 * Each operation can optionally override the batch-level branch.
 *
 * @param type Operation type: "update" or "applyPatch"
 * @param sparql SPARQL Update query (required if type=update)
 * @param patch RDF Patch text (required if type=applyPatch)
 * @param branch Target branch (overrides request-level branch if specified)
 */
public record WriteOperation(
    String type,
    String sparql,
    String patch,
    String branch
) {
  /**
   * Validates the write operation.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Type is required");
    }

    if (!type.equals("update") && !type.equals("applyPatch")) {
      throw new IllegalArgumentException(
          "Invalid type: '" + type + "'. Must be 'update' or 'applyPatch'");
    }

    if ("update".equals(type)) {
      if (sparql == null || sparql.isBlank()) {
        throw new IllegalArgumentException("SPARQL query required for type 'update'");
      }
    }

    if ("applyPatch".equals(type)) {
      if (patch == null || patch.isBlank()) {
        throw new IllegalArgumentException("Patch content required for type 'applyPatch'");
      }
    }
  }
}
