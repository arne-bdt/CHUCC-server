package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when shapes graph is invalid or cannot be parsed.
 *
 * <p>Returns HTTP 422 Unprocessable Entity with error type 'invalid_shapes'.</p>
 */
public class InvalidShapesException extends VcException {

  private static final long serialVersionUID = 1L;

  /**
   * Construct exception with message.
   *
   * @param message error message
   */
  public InvalidShapesException(String message) {
    super(message, "invalid_shapes", HttpStatus.UNPROCESSABLE_ENTITY);
  }

  /**
   * Construct exception with message and cause.
   *
   * @param message error message
   * @param cause underlying exception
   */
  public InvalidShapesException(String message, Throwable cause) {
    super(message, "invalid_shapes", HttpStatus.UNPROCESSABLE_ENTITY, cause);
  }
}
