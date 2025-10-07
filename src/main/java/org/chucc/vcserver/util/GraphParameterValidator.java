package org.chucc.vcserver.util;

import org.chucc.vcserver.domain.GraphIdentifier;
import org.chucc.vcserver.exception.SelectorConflictException;

/**
 * Utility class for validating Graph Store Protocol parameters.
 * Validates graph IRI and default flag mutual exclusion per SPARQL 1.2 GSP spec.
 */
public final class GraphParameterValidator {

  private GraphParameterValidator() {
    // Utility class - prevent instantiation
  }

  /**
   * Validates that exactly one of graph IRI or default flag is provided.
   *
   * @param graph the graph IRI parameter (may be null or blank)
   * @param isDefault the default flag parameter (may be null or false)
   * @throws SelectorConflictException if both or neither are provided, or if IRI is invalid
   */
  public static void validateGraphParameter(String graph, Boolean isDefault) {
    boolean hasGraph = graph != null && !graph.isBlank();
    boolean hasDefault = isDefault != null && isDefault;

    if (hasGraph && hasDefault) {
      throw new SelectorConflictException(
          "Parameters 'graph' and 'default' are mutually exclusive"
      );
    }

    if (!hasGraph && !hasDefault) {
      throw new SelectorConflictException(
          "Either 'graph' parameter or 'default=true' must be provided"
      );
    }

    // Validate graph IRI if provided
    if (hasGraph) {
      try {
        validateGraphIri(graph);
      } catch (IllegalArgumentException e) {
        throw new SelectorConflictException(
            "Invalid graph IRI: " + e.getMessage(),
            e
        );
      }
    }
  }

  /**
   * Validates a graph IRI according to RFC 3986/3987.
   *
   * @param iri the IRI to validate
   * @throws IllegalArgumentException if the IRI is null, blank, or invalid
   */
  public static void validateGraphIri(String iri) {
    // Delegate to GraphIdentifier for validation
    GraphIdentifier.named(iri);
  }
}
