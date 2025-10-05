package org.chucc.vcserver.exception;

/**
 * Thrown when a concurrent write is detected and the operation is rejected.
 * Error code: concurrent_write_conflict
 * HTTP status: 409 Conflict
 */
public class ConcurrentWriteConflictException extends VcException {

  public ConcurrentWriteConflictException(String message) {
    super(message, "concurrent_write_conflict", 409);
  }

  public ConcurrentWriteConflictException(String message, Throwable cause) {
    super(message, "concurrent_write_conflict", 409, cause);
  }
}
