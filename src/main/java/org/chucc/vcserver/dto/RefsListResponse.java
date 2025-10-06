package org.chucc.vcserver.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for a list of refs (branches and tags).
 */
public class RefsListResponse {

  private List<RefResponse> refs;

  /**
   * Default constructor for JSON deserialization.
   */
  public RefsListResponse() {
    this.refs = new ArrayList<>();
  }

  /**
   * Constructor with refs list.
   *
   * @param refs the list of refs
   */
  public RefsListResponse(List<RefResponse> refs) {
    this.refs = new ArrayList<>(refs);
  }

  /**
   * Gets the refs list (defensive copy).
   *
   * @return a copy of the refs list
   */
  public List<RefResponse> getRefs() {
    return new ArrayList<>(refs);
  }

  /**
   * Sets the refs list (defensive copy).
   *
   * @param refs the refs list
   */
  public void setRefs(List<RefResponse> refs) {
    this.refs = new ArrayList<>(refs);
  }
}
