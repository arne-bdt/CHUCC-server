package org.chucc.vcserver.exception;

/**
 * Thrown when multiple mutually exclusive selectors are provided.
 * Error code: selector_conflict
 * HTTP status: 400 Bad Request
 */
public class SelectorConflictException extends VcException {

  public SelectorConflictException(String message) {
    super(message, "selector_conflict", 400);
  }

  public SelectorConflictException(String message, Throwable cause) {
    super(message, "selector_conflict", 400, cause);
  }
}
