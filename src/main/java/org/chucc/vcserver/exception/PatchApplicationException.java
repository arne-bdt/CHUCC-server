package org.chucc.vcserver.exception;

/**
 * Exception thrown when RDF patch application fails.
 *
 * <p>This typically indicates:
 * <ul>
 * <li>Invalid patch syntax</li>
 * <li>Patch operations that violate RDF constraints</li>
 * <li>I/O errors during graph updates</li>
 * </ul>
 */
public class PatchApplicationException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified detail message.
   *
   * @param message the detail message
   */
  public PatchApplicationException(String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public PatchApplicationException(String message, Throwable cause) {
    super(message, cause);
  }
}
