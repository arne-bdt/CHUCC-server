package org.chucc.vcserver.util;

import java.util.Map;
import java.util.UUID;
import org.chucc.vcserver.dto.ProblemDetail;
import org.springframework.http.HttpStatus;

/**
 * Utility for building consistent RFC 7807 problem+json error responses.
 * Provides methods to create ProblemDetail objects with standardized structure.
 */
public final class ErrorResponseBuilder {

  private ErrorResponseBuilder() {
    // Utility class - prevent instantiation
  }

  /**
   * Builds a ProblemDetail with minimal information.
   *
   * @param status HTTP status code
   * @param code canonical error code
   * @param detail detailed error message
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildProblem(HttpStatus status, String code, String detail) {
    return buildProblem(status, code, detail, null);
  }

  /**
   * Builds a ProblemDetail with additional properties.
   *
   * @param status HTTP status code
   * @param code canonical error code
   * @param detail detailed error message
   * @param extras additional properties to include (may be null)
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildProblem(
      HttpStatus status,
      String code,
      String detail,
      Map<String, Object> extras) {

    // Generate instance ID for tracking
    String instance = "/errors/" + UUID.randomUUID();

    ProblemDetail problem = new ProblemDetail(
        "about:blank",
        status.getReasonPhrase(),
        status.value(),
        detail,
        instance,
        code
    );

    // Add any extra properties
    if (extras != null) {
      problem.setExtras(extras);
    }

    return problem;
  }

  /**
   * Builds a ProblemDetail for validation errors.
   *
   * @param detail detailed validation error message
   * @param hint hint for fixing the error
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildValidationError(String detail, String hint) {
    ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "invalid_argument", detail);
    if (hint != null) {
      problem.setExtras(Map.of("hint", hint));
    }
    return problem;
  }

  /**
   * Builds a ProblemDetail for selector conflicts.
   *
   * @param detail detailed error message
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildSelectorConflict(String detail) {
    return buildProblem(HttpStatus.BAD_REQUEST, "selector_conflict", detail, Map.of(
        "hint", "Ensure exactly one of graph/default and one of branch/commit/asOf is provided"
    ));
  }

  /**
   * Builds a ProblemDetail for graph not found errors.
   *
   * @param graphIri the IRI of the graph that was not found
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildGraphNotFound(String graphIri) {
    return buildProblem(
        HttpStatus.NOT_FOUND,
        "graph_not_found",
        "Graph not found: " + graphIri,
        Map.of("graphIri", graphIri)
    );
  }

  /**
   * Builds a ProblemDetail for concurrent modification conflicts (If-Match precondition failed).
   * Returns HTTP 409 Conflict to indicate version control concurrent write scenario.
   *
   * @param expected the expected commit ID from If-Match
   * @param actual the actual current HEAD commit ID
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildPreconditionFailed(String expected, String actual) {
    return buildProblem(
        HttpStatus.CONFLICT,
        "concurrent_modification_conflict",
        "If-Match precondition failed: expected " + expected + ", actual " + actual,
        Map.of(
            "expected", expected,
            "actual", actual,
            "hint", "Fetch the latest ETag and retry the operation"
        )
    );
  }

  /**
   * Builds a ProblemDetail for concurrent write conflicts.
   *
   * @param baseCommit the base commit the client was working from
   * @param currentHead the current HEAD commit
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildConcurrentWriteConflict(String baseCommit, String currentHead) {
    return buildProblem(
        HttpStatus.CONFLICT,
        "concurrent_write_conflict",
        "Concurrent modification detected. "
            + "Another commit was made after your base commit.",
        Map.of(
            "baseCommit", baseCommit,
            "currentHead", currentHead,
            "hint", "Fetch the latest state, merge changes, and retry"
        )
    );
  }

  /**
   * Builds a ProblemDetail for invalid RDF Patch syntax.
   *
   * @param detail parse error details
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildInvalidPatchSyntax(String detail) {
    return buildProblem(
        HttpStatus.BAD_REQUEST,
        "invalid_patch_syntax",
        "Invalid RDF Patch syntax: " + detail,
        Map.of("hint", "Check RDF Patch format per https://www.w3.org/TR/rdf-patch/")
    );
  }

  /**
   * Builds a ProblemDetail for patches that cannot be applied.
   *
   * @param detail reason why patch cannot be applied
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildPatchNotApplicable(String detail) {
    return buildProblem(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "patch_not_applicable",
        "Patch cannot be applied to current graph state: " + detail,
        Map.of(
            "hint", "Ensure DELETE operations reference existing triples "
                + "and ADD operations don't duplicate existing triples"
        )
    );
  }

  /**
   * Builds a ProblemDetail for unsupported media type errors.
   *
   * @param contentType the unsupported content type
   * @param supported comma-separated list of supported types
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildUnsupportedMediaType(String contentType, String supported) {
    return buildProblem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "unsupported_media_type",
        "Unsupported Content-Type: " + contentType,
        Map.of(
            "contentType", contentType,
            "supported", supported
        )
    );
  }

  /**
   * Builds a ProblemDetail for not acceptable errors.
   *
   * @param accept the requested Accept header
   * @param supported comma-separated list of supported formats
   * @return the constructed ProblemDetail
   */
  public static ProblemDetail buildNotAcceptable(String accept, String supported) {
    return buildProblem(
        HttpStatus.NOT_ACCEPTABLE,
        "not_acceptable",
        "No acceptable representation available for Accept: " + accept,
        Map.of(
            "accept", accept,
            "supported", supported
        )
    );
  }
}
