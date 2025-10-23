package org.chucc.vcserver.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.chucc.vcserver.dto.ConflictItem;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a cherry-pick operation encounters conflicts.
 * Error code: cherry_pick_conflict
 * HTTP status: 409 Conflict
 */
public class CherryPickConflictException extends VcException {

  @SuppressFBWarnings(
      value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
      justification = "Exception is not serialized, only converted to JSON via exception handler"
  )
  private final transient List<ConflictItem> conflicts;

  /**
   * Constructs a new CherryPickConflictException with the specified message and conflicts.
   *
   * @param message the detail message
   * @param conflicts the list of conflict items
   */
  public CherryPickConflictException(String message, List<ConflictItem> conflicts) {
    super(message, "cherry_pick_conflict", HttpStatus.CONFLICT);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  /**
   * Constructs a new CherryPickConflictException with the specified message, conflicts, and cause.
   *
   * @param message the detail message
   * @param conflicts the list of conflict items
   * @param cause the cause of this exception
   */
  public CherryPickConflictException(String message, List<ConflictItem> conflicts,
                                     Throwable cause) {
    super(message, "cherry_pick_conflict", HttpStatus.CONFLICT, cause);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  public List<ConflictItem> getConflicts() {
    return Collections.unmodifiableList(conflicts);
  }
}
