package org.chucc.vcserver.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.chucc.vcserver.dto.ConflictItem;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a merge operation cannot proceed due to conflicts.
 * Error code: merge_conflict
 * HTTP status: 409 Conflict
 */
public class MergeConflictException extends VcException {
  
  private static final long serialVersionUID = 1L;

  @SuppressFBWarnings(
      value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
      justification = "Exception is not serialized, only converted to JSON via exception handler"
  )
  private final transient List<ConflictItem> conflicts;

  /**
   * Constructs a new MergeConflictException with the specified message and conflicts.
   *
   * @param message the detail message
   * @param conflicts the list of conflict items
   */
  public MergeConflictException(String message, List<ConflictItem> conflicts) {
    super(message, "merge_conflict", HttpStatus.CONFLICT);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  /**
   * Constructs a new MergeConflictException with the specified message, conflicts, and cause.
   *
   * @param message the detail message
   * @param conflicts the list of conflict items
   * @param cause the cause of this exception
   */
  public MergeConflictException(String message, List<ConflictItem> conflicts,
                                Throwable cause) {
    super(message, "merge_conflict", HttpStatus.CONFLICT, cause);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  public List<ConflictItem> getConflicts() {
    return Collections.unmodifiableList(conflicts);
  }
}
