package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested dataset does not exist.
 * Maps to HTTP 404 Not Found with error code "dataset_not_found".
 */
public class DatasetNotFoundException extends VcException {
  
  private static final long serialVersionUID = 1L;

  private static final String ERROR_CODE = "dataset_not_found";

  /**
   * Constructor with message.
   *
   * @param message the error message
   */
  public DatasetNotFoundException(String message) {
    super(message, ERROR_CODE, HttpStatus.NOT_FOUND);
  }
}
