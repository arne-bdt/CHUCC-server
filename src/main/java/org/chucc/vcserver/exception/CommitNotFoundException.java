package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested commit does not exist.
 * Maps to HTTP 404 Not Found with error code "commit_not_found".
 */
public class CommitNotFoundException extends VcException {

  private static final String ERROR_CODE = "commit_not_found";

  /**
   * Constructor with commit ID.
   *
   * @param commitId the ID of the commit that was not found
   */
  public CommitNotFoundException(String commitId) {
    super("Commit not found: " + commitId, ERROR_CODE, HttpStatus.NOT_FOUND);
  }

  /**
   * Constructor with custom message.
   * The useCustomMessage parameter distinguishes this constructor from the other.
   *
   * @param message the error message
   * @param useCustomMessage dummy parameter to distinguish constructor signature
   */
  @SuppressWarnings("PMD.UnusedFormalParameter")
  public CommitNotFoundException(String message, boolean useCustomMessage) {
    super(message, ERROR_CODE, HttpStatus.NOT_FOUND);
  }
}
