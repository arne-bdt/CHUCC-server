package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when attempting to delete a protected dataset (e.g., default).
 * Maps to HTTP 403 Forbidden with error code "protected_dataset".
 */
public class ProtectedDatasetException extends VcException {

  private static final String ERROR_CODE = "protected_dataset";

  /**
   * Constructor with message.
   *
   * @param message the error message
   */
  public ProtectedDatasetException(String message) {
    super(message, ERROR_CODE, HttpStatus.FORBIDDEN.value());
  }
}
