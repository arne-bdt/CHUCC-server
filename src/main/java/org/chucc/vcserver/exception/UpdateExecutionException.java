package org.chucc.vcserver.exception;

/**
 * Exception thrown when a SPARQL UPDATE operation fails during execution.
 *
 * <p>This exception is thrown when:</p>
 * <ul>
 *   <li>The update operation encounters a runtime error</li>
 *   <li>The dataset cannot be modified</li>
 *   <li>An unexpected error occurs during update execution</li>
 * </ul>
 *
 * <p>This exception maps to HTTP 500 INTERNAL SERVER ERROR responses with RFC 7807
 * Problem Details.</p>
 */
public class UpdateExecutionException extends RuntimeException {

  /**
   * Creates a new update execution exception with a message.
   *
   * @param message the error message
   */
  public UpdateExecutionException(String message) {
    super(message);
  }

  /**
   * Creates a new update execution exception with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public UpdateExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
