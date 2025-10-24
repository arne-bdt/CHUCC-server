package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when multiple mutually exclusive selectors are provided.
 * Error code: selector_conflict
 * HTTP status: 400 Bad Request
 */
public class SelectorConflictException extends VcException {
  
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new SelectorConflictException with the specified message.
   *
   * @param message the detail message
   */
  public SelectorConflictException(String message) {
    super(message, "selector_conflict", HttpStatus.BAD_REQUEST);
  }

  /**
   * Constructs a new SelectorConflictException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public SelectorConflictException(String message, Throwable cause) {
    super(message, "selector_conflict", HttpStatus.BAD_REQUEST, cause);
  }
}
