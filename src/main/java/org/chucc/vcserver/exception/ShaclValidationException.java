package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when SHACL validation fails (engine error, not validation report).
 *
 * <p>This indicates a technical failure (e.g., timeout, out of memory), not
 * a validation failure (which returns a normal sh:ValidationReport).</p>
 */
public class ShaclValidationException extends VcException {

  private static final long serialVersionUID = 1L;

  /**
   * Construct exception with message.
   *
   * @param message error message
   */
  public ShaclValidationException(String message) {
    super(message, "validation_error", HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Construct exception with message and cause.
   *
   * @param message error message
   * @param cause underlying exception
   */
  public ShaclValidationException(String message, Throwable cause) {
    super(message, "validation_error", HttpStatus.INTERNAL_SERVER_ERROR, cause);
  }
}
