package org.chucc.vcserver.exception;

import org.chucc.vcserver.dto.ConflictProblemDetail;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.util.ErrorResponseBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for Version Control Server exceptions.
 * Converts VcException instances to RFC 7807 problem+json responses.
 */
@ControllerAdvice
public class VcExceptionHandler {

  private static final MediaType PROBLEM_JSON =
      MediaType.parseMediaType("application/problem+json");

  /**
   * Handle merge conflict exceptions.
   *
   * @param ex the merge conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(MergeConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleMergeConflict(
      MergeConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle rebase conflict exceptions.
   *
   * @param ex the rebase conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(RebaseConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleRebaseConflict(
      RebaseConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle cherry-pick conflict exceptions.
   *
   * @param ex the cherry-pick conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(CherryPickConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ConflictProblemDetail> handleCherryPickConflict(
      CherryPickConflictException ex) {
    ConflictProblemDetail problem = new ConflictProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode(),
        ex.getConflicts()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle concurrent write conflict exceptions with detailed conflict information.
   *
   * @param ex the concurrent write conflict exception
   * @return RFC 7807 problem+json response with conflict details
   */
  @ExceptionHandler(ConcurrentWriteConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<?> handleConcurrentWriteConflict(
      ConcurrentWriteConflictException ex) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    // If conflicts are present, return ConflictProblemDetail
    if (!ex.getConflicts().isEmpty()) {
      ConflictProblemDetail problem = new ConflictProblemDetail(
          ex.getMessage(),
          ex.getStatus(),
          ex.getCode(),
          ex.getConflicts()
      );
      return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
    }

    // Otherwise, return simple ProblemDetail
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode()
    );
    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle precondition failed exceptions with detailed information.
   *
   * @param ex the precondition failed exception
   * @return RFC 7807 problem+json response with expected and actual values
   */
  @ExceptionHandler(PreconditionFailedException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handlePreconditionFailed(
      PreconditionFailedException ex) {
    ProblemDetail problem = ErrorResponseBuilder.buildPreconditionFailed(
        ex.getExpected(),
        ex.getActual()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle graph not found exceptions.
   *
   * @param ex the graph not found exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(GraphNotFoundException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleGraphNotFound(GraphNotFoundException ex) {
    // Extract graph IRI from message if available
    String message = ex.getMessage();
    String graphIri = message.substring(message.lastIndexOf(":") + 1).trim();

    ProblemDetail problem = ErrorResponseBuilder.buildGraphNotFound(graphIri);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
  }

  /**
   * Handle selector conflict exceptions with helpful hints.
   *
   * @param ex the selector conflict exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(SelectorConflictException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleSelectorConflict(SelectorConflictException ex) {
    ProblemDetail problem = ErrorResponseBuilder.buildSelectorConflict(ex.getMessage());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handle all VcException instances.
   *
   * @param ex the version control exception
   * @return RFC 7807 problem+json response
   */
  @ExceptionHandler(VcException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleVcException(VcException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        ex.getStatus(),
        ex.getCode()
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.valueOf(ex.getStatus()));
  }

  /**
   * Handle IllegalArgumentException (e.g., invalid timestamp format).
   *
   * @param ex the illegal argument exception
   * @return RFC 7807 problem+json response with 400 Bad Request
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail problem = new ProblemDetail(
        ex.getMessage(),
        HttpStatus.BAD_REQUEST.value(),
        "invalid_argument"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(PROBLEM_JSON);

    return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
  }
}
