package org.chucc.vcserver.dto.shacl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * SHACL validation request.
 *
 * <p>Complete request structure:</p>
 * <pre>
 * {
 *   "shapes": {GraphReference},
 *   "data": {DataReference},
 *   "options": {ValidationOptions},  // optional
 *   "results": {ResultsConfig}       // optional
 * }
 * </pre>
 *
 * @param shapes shapes graph reference
 * @param data data graph(s) reference
 * @param options validation options (optional, defaults applied)
 * @param results results configuration (optional, defaults applied)
 */
public record ValidationRequest(
    @NotNull(message = "shapes is required")
    @Valid
    GraphReference shapes,

    @NotNull(message = "data is required")
    @Valid
    DataReference data,

    @Valid
    ValidationOptions options,

    @Valid
    ResultsConfig results
) {

  /**
   * Compact constructor with defaults.
   */
  public ValidationRequest {
    if (options == null) {
      options = new ValidationOptions(null, null, null);
    }
    if (results == null) {
      results = new ResultsConfig(true, null);
    }
  }
}
