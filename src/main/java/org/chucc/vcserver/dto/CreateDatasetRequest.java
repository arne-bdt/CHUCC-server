package org.chucc.vcserver.dto;

/**
 * Request DTO for creating a dataset.
 * Validates request fields before command creation.
 */
public record CreateDatasetRequest(
    String description,
    String initialGraph
) {
  /**
   * Validates the request fields.
   * All fields are optional, so no validation is required.
   * Dataset name validation happens in CreateDatasetCommand.
   */
  public void validate() {
    // All fields are optional - no validation needed
  }
}
