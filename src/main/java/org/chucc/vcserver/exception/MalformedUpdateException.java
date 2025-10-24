package org.chucc.vcserver.exception;

/**
 * Exception thrown when a SPARQL UPDATE query string is malformed or cannot be parsed.
 *
 * <p>This exception is thrown during SPARQL UPDATE execution when:</p>
 * <ul>
 *   <li>The update string has syntax errors</li>
 *   <li>The update string contains unsupported operations</li>
 *   <li>The update string is empty or invalid</li>
 * </ul>
 *
 * <p>This exception maps to HTTP 400 BAD REQUEST responses with RFC 7807 Problem Details.</p>
 */
public class MalformedUpdateException extends RuntimeException {
  
  private static final long serialVersionUID = 1L;

  /**
   * Creates a new malformed update exception with a message.
   *
   * @param message the error message
   */
  public MalformedUpdateException(String message) {
    super(message);
  }

  /**
   * Creates a new malformed update exception with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public MalformedUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}
