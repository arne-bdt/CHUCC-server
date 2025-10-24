package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for Version Control Server errors.
 * Contains canonical error code and HTTP status code per SPARQL 1.2 Protocol §9.
 */
public class VcException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;
  private final int status;

  /**
   * Constructor with message, code, and status.
   *
   * @param message error message
   * @param code canonical error code
   * @param status HTTP status
   */
  public VcException(String message, String code, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status.value();
  }

  /**
   * Constructor with message, code, status, and cause.
   *
   * @param message error message
   * @param code canonical error code
   * @param status HTTP status
   * @param cause the cause
   */
  public VcException(String message, String code, HttpStatus status, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status.value();
  }

  public String getCode() {
    return code;
  }

  public int getStatus() {
    return status;
  }
}
