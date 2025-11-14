package org.chucc.vcserver.dto.shacl;

import jakarta.validation.constraints.Pattern;

/**
 * Options for SHACL validation.
 *
 * <p><b>Validation modes:</b></p>
 * <ul>
 *   <li><b>separately</b> - Validate each graph independently (default)</li>
 *   <li><b>union</b> - Combine graphs into union before validation
 *       (for cross-graph constraints)</li>
 * </ul>
 *
 * @param validateGraphs validation mode (default: separately)
 * @param targetNode URI of specific resource to validate (optional)
 * @param severity filter results by severity level (optional)
 */
public record ValidationOptions(
    @Pattern(regexp = "separately|union",
             message = "validateGraphs must be 'separately' or 'union'")
    String validateGraphs,  // Default: "separately"

    String targetNode,  // URI of specific resource to validate

    @Pattern(regexp = "Violation|Warning|Info",
             message = "severity must be 'Violation', 'Warning', or 'Info'")
    String severity  // Filter results by severity
) {

  /**
   * Compact constructor with defaults.
   */
  public ValidationOptions {
    if (validateGraphs == null) {
      validateGraphs = "separately";
    }
  }
}
