package org.chucc.vcserver.exception;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.chucc.vcserver.dto.ConflictItem;

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

  public CherryPickConflictException(String message, List<ConflictItem> conflicts) {
    super(message, "cherry_pick_conflict", 409);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  public CherryPickConflictException(String message, List<ConflictItem> conflicts,
                                     Throwable cause) {
    super(message, "cherry_pick_conflict", 409, cause);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : new ArrayList<>();
  }

  public List<ConflictItem> getConflicts() {
    return Collections.unmodifiableList(conflicts);
  }
}
