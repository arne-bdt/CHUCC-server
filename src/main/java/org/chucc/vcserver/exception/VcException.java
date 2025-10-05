package org.chucc.vcserver.exception;

/**
 * Base exception for Version Control Server errors.
 * Contains canonical error code and HTTP status code per SPARQL 1.2 Protocol §9.
 */
public class VcException extends RuntimeException {

  private final String code;
  private final int status;

  /**
   * Constructor with message, code, and status.
   *
   * @param message error message
   * @param code canonical error code
   * @param status HTTP status code
   */
  public VcException(String message, String code, int status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  /**
   * Constructor with message, code, status, and cause.
   *
   * @param message error message
   * @param code canonical error code
   * @param status HTTP status code
   * @param cause the cause
   */
  public VcException(String message, String code, int status, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
  }

  public String getCode() {
    return code;
  }

  public int getStatus() {
    return status;
  }
}
