package org.chucc.vcserver.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a topic healing operation.
 *
 * @param dataset the dataset that was healed
 * @param success whether the healing was successful
 * @param actions list of actions performed (immutable)
 * @param error error message if healing failed
 * @param timestamp when the healing was performed
 */
public record HealingResult(
    String dataset,
    boolean success,
    List<String> actions,
    String error,
    Instant timestamp
) {
  /**
   * Compact canonical constructor with defensive copying.
   */
  public HealingResult {
    actions = actions != null ? List.copyOf(actions) : List.of();
  }

  /**
   * Creates a successful healing result.
   *
   * @param dataset the dataset name
   * @param actions list of actions performed
   * @return successful healing result
   */
  public static HealingResult success(String dataset, List<String> actions) {
    return new HealingResult(
        dataset,
        true,
        new ArrayList<>(actions),
        null,
        Instant.now()
    );
  }

  /**
   * Creates a successful result with a single action.
   *
   * @param dataset the dataset name
   * @param action the action performed
   * @return successful healing result
   */
  public static HealingResult success(String dataset, String action) {
    return success(dataset, List.of(action));
  }

  /**
   * Creates a failed healing result.
   *
   * @param dataset the dataset name
   * @param error the error message
   * @return failed healing result
   */
  public static HealingResult failure(String dataset, String error) {
    return new HealingResult(
        dataset,
        false,
        List.of(),
        error,
        Instant.now()
    );
  }

  /**
   * Creates a result indicating no action was needed.
   *
   * @param dataset the dataset name
   * @return result with no actions
   */
  public static HealingResult noActionNeeded(String dataset) {
    return success(dataset, "No action needed - topic is healthy");
  }
}
