package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when dataset deletion is not explicitly confirmed.
 * Maps to HTTP 400 Bad Request with error code "unconfirmed_deletion".
 */
public class UnconfirmedDeletionException extends VcException {

  private static final String ERROR_CODE = "unconfirmed_deletion";

  /**
   * Constructor with message.
   *
   * @param message the error message
   */
  public UnconfirmedDeletionException(String message) {
    super(message, ERROR_CODE, HttpStatus.BAD_REQUEST.value());
  }
}
