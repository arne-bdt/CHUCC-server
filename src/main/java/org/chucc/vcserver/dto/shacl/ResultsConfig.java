package org.chucc.vcserver.dto.shacl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration for validation results.
 *
 * <p>Results can be:</p>
 * <ul>
 *   <li>Returned immediately in HTTP response (returnReport=true)</li>
 *   <li>Stored in a graph for historical analysis (store config provided)</li>
 *   <li>Both returned and stored</li>
 * </ul>
 *
 * @param returnReport whether to return report in HTTP response (default: true)
 * @param store storage configuration (optional)
 */
public record ResultsConfig(
    Boolean returnReport,  // Default: true

    @Valid
    StoreConfig store
) {

  /**
   * Compact constructor with defaults.
   */
  public ResultsConfig {
    if (returnReport == null) {
      returnReport = true;
    }
  }

  /**
   * Configuration for storing validation results.
   *
   * @param dataset target dataset name
   * @param graph target graph URI
   * @param overwrite whether to overwrite existing graph (default: false)
   */
  public record StoreConfig(
      @Pattern(regexp = "^[A-Za-z0-9._-]{1,249}$", message = "Invalid dataset name")
      String dataset,

      String graph,

      Boolean overwrite  // Default: false
  ) {

    /**
     * Compact constructor with defaults.
     */
    public StoreConfig {
      if (overwrite == null) {
        overwrite = false;
      }
    }
  }
}
