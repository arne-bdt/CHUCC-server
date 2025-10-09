package org.chucc.vcserver.util;

import java.util.Objects;
import org.chucc.vcserver.domain.CommitId;

/**
 * Validator for graph command parameters with security-focused input validation.
 * Ensures all user-provided inputs are properly validated and sanitized.
 */
public final class GraphCommandValidator {

  private GraphCommandValidator() {
    // Utility class
  }

  /**
   * Validates common graph command parameters.
   * Performs null checks, blank checks, and security sanitization.
   *
   * @param dataset the dataset name
   * @param graphIri the graph IRI
   * @param isDefaultGraph true if this is the default graph
   * @param branch the branch name
   * @param baseCommit the base commit
   * @param rdfContent the RDF content
   * @param contentType the content type
   * @param author the author (will be sanitized)
   * @param message the message (will be sanitized)
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

    // Validate and sanitize dataset name
    InputSanitizer.sanitizeName(dataset);

    // Validate and sanitize branch name
    InputSanitizer.sanitizeName(branch);

    if (contentType.isBlank()) {
      throw new IllegalArgumentException("Content type cannot be blank");
    }

    // Validate and sanitize author (throws if invalid)
    InputSanitizer.sanitizeAuthor(author);

    // Validate and sanitize message (throws if invalid)
    InputSanitizer.sanitizeMessage(message);

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

