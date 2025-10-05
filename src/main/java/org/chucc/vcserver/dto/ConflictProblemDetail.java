package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended ProblemDetail for conflict-related errors.
 * Includes a list of conflict items describing what conflicts occurred.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConflictProblemDetail extends ProblemDetail {

  private List<ConflictItem> conflicts;

  public ConflictProblemDetail() {
    super();
  }

  public ConflictProblemDetail(String title, int status, String code,
                               List<ConflictItem> conflicts) {
    super(title, status, code);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : null;
  }

  public ConflictProblemDetail(String type, String title, int status, String code,
                               List<ConflictItem> conflicts) {
    super(type, title, status, code);
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : null;
  }

  public List<ConflictItem> getConflicts() {
    return conflicts != null ? new ArrayList<>(conflicts) : null;
  }

  public void setConflicts(List<ConflictItem> conflicts) {
    this.conflicts = conflicts != null ? new ArrayList<>(conflicts) : null;
  }

  /**
   * Add a conflict item to the list.
   *
   * @param conflict the conflict item to add
   */
  public void addConflict(ConflictItem conflict) {
    if (this.conflicts == null) {
      this.conflicts = new ArrayList<>();
    }
    this.conflicts.add(conflict);
  }
}
