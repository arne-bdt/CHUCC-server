package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Locale;

/**
 * Request DTO for merge operation.
 *
 * <p>Supports fast-forward modes, conflict resolution strategies,
 * and configurable conflict scope (graph-level or dataset-level).
 */
public record MergeRequest(
    String into,
    String from,
    @JsonProperty(defaultValue = "allow") String fastForward,
    @JsonProperty(defaultValue = "three-way") String strategy,
    @JsonProperty(defaultValue = "graph") String conflictScope,
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

    // Validate fastForward
    String ff = normalizedFastForward();
    if (!java.util.List.of("allow", "only", "never").contains(ff)) {
      throw new IllegalArgumentException("Invalid fastForward mode: " + ff);
    }

    // Validate strategy
    if (strategy != null) {
      String strat = strategy.toLowerCase(Locale.ROOT);
      if (!java.util.List.of("three-way", "ours", "theirs").contains(strat)) {
        throw new IllegalArgumentException(
            "Invalid strategy: " + strategy + ". Valid values: three-way, ours, theirs");
      }
    }

    // Validate conflictScope
    if (conflictScope != null) {
      String scope = conflictScope.toLowerCase(Locale.ROOT);
      if (!java.util.List.of("graph", "dataset").contains(scope)) {
        throw new IllegalArgumentException(
            "Invalid conflictScope: " + conflictScope + ". Valid values: graph, dataset");
      }
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
   *
   * @return merge strategy (defaults to "three-way")
   */
  public String normalizedStrategy() {
    return strategy != null ? strategy.toLowerCase(Locale.ROOT) : "three-way";
  }

  /**
   * Returns the normalized conflict scope.
   *
   * @return conflict scope (defaults to "graph")
   */
  public String normalizedConflictScope() {
    return conflictScope != null ? conflictScope.toLowerCase(Locale.ROOT) : "graph";
  }
}
