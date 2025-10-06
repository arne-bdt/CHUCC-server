package org.chucc.vcserver.exception;

import org.chucc.vcserver.dto.ConflictProblemDetail;
import org.chucc.vcserver.dto.ProblemDetail;
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
}
