package org.chucc.vcserver.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.chucc.vcserver.dto.ConflictItem;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a concurrent write is detected and the operation is rejected.
 * Error code: concurrent_write_conflict
 * HTTP status: 409 Conflict
 */
public class ConcurrentWriteConflictException extends VcException {
  
  private static final long serialVersionUID = 1L;

  @SuppressFBWarnings(
      value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
      justification = "Exception is not serialized, only converted to JSON via exception handler"
  )
  private final transient List<ConflictItem> conflicts;

  /**
   * Constructs a new ConcurrentWriteConflictException with the specified message.
   *
   * @param message the detail message
   */
  public ConcurrentWriteConflictException(String message) {
    super(message, "concurrent_write_conflict", HttpStatus.CONFLICT);
    this.conflicts = new ArrayList<>();
  }

  /**
   * Constructs a new ConcurrentWriteConflictException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public ConcurrentWriteConflictException(String message, Throwable cause) {
    super(message, "concurrent_write_conflict", HttpStatus.CONFLICT, cause);
    this.conflicts = new ArrayList<>();
  }

  /**
   * Constructor with conflict details.
   *
   * @param message the error message
   * @param conflicts the list of conflicting items
   */
  public ConcurrentWriteConflictException(String message, List<ConflictItem> conflicts) {
    super(message, "concurrent_write_conflict", HttpStatus.CONFLICT);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  /**
   * Constructor with conflict details and cause.
   *
   * @param message the error message
   * @param conflicts the list of conflicting items
   * @param cause the underlying cause
   */
  public ConcurrentWriteConflictException(String message, List<ConflictItem> conflicts,
      Throwable cause) {
    super(message, "concurrent_write_conflict", HttpStatus.CONFLICT, cause);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  /**
   * Returns the list of conflicts.
   *
   * @return unmodifiable list of conflict items
   */
  public List<ConflictItem> getConflicts() {
    return Collections.unmodifiableList(conflicts);
  }
}
