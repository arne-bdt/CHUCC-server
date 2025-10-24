package org.chucc.vcserver.exception;

/**
 * Exception thrown when a ref (branch or commit) cannot be found.
 */
public class RefNotFoundException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new RefNotFoundException with the specified error message.
   *
   * @param message the error message
   */
  public RefNotFoundException(String message) {
    super(message);
  }
}
