package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to delete a protected branch (e.g., main).
 * Maps to HTTP 403 Forbidden with error code "protected_branch".
 */
public class ProtectedBranchException extends VcException {

  private static final String ERROR_CODE = "protected_branch";

  /**
   * Constructor with message.
   *
   * @param message the error message
   */
  public ProtectedBranchException(String message) {
    super(message, ERROR_CODE, HttpStatus.FORBIDDEN);
  }
}
