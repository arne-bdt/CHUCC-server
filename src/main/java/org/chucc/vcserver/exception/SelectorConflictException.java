package org.chucc.vcserver.exception;

/**
 * Thrown when multiple mutually exclusive selectors are provided.
 * Error code: selector_conflict
 * HTTP status: 400 Bad Request
 */
public class SelectorConflictException extends VcException {

  /**
   * Constructs a new SelectorConflictException with the specified message.
   *
   * @param message the detail message
   */
  public SelectorConflictException(String message) {
    super(message, "selector_conflict", 400);
  }

  /**
   * Constructs a new SelectorConflictException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public SelectorConflictException(String message, Throwable cause) {
    super(message, "selector_conflict", 400, cause);
  }
}
