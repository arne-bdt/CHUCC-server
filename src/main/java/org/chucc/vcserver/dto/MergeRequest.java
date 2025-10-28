package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for merge operation (Phase 1: Core functionality).
 *
 * <p>This DTO supports fast-forward modes in Phase 1. Strategy and resolutions
 * parameters are reserved for later phases but ignored in Phase 1 implementation.
 */
public record MergeRequest(
    String into,
    String from,
    @JsonProperty(defaultValue = "allow") String fastForward,
    String strategy,
    Object resolutions
) {
  /**
   * Validates the merge request.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (into == null || into.isBlank()) {
      throw new IllegalArgumentException("Target branch (into) is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }

    String ff = normalizedFastForward();
    if (!java.util.List.of("allow", "only", "never").contains(ff)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + ff);
    }
  }

  /**
   * Returns the normalized fast-forward mode.
   *
   * @return fast-forward mode (defaults to "allow")
   */
  public String normalizedFastForward() {
    return fastForward != null ? fastForward : "allow";
  }

  /**
   * Returns the normalized strategy.
   * Phase 1: Always returns "three-way" (other strategies not yet implemented).
   *
   * @return merge strategy (defaults to "three-way")
   */
  public String normalizedStrategy() {
    return strategy != null ? strategy : "three-way";
  }
}
