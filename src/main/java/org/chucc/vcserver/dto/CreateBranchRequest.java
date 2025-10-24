package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a branch.
 * Validates request fields before command creation.
 */
public record CreateBranchRequest(
    String name,
    String from,
    @JsonProperty(defaultValue = "false") Boolean isProtected
) {
  /**
   * Validates the request fields.
   * Branch name validation delegates to Branch domain entity (NFC normalization + regex).
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Branch name is required");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("Source ref (from) is required");
    }
    // Note: Branch name pattern validation happens in Branch constructor
    // Pattern: ^[A-Za-z0-9._\-]+$ + Unicode NFC normalization
  }

  /**
   * Gets the protected flag, defaulting to false if null.
   *
   * @return the protected flag as a primitive boolean
   */
  public boolean getProtectedFlag() {
    return isProtected != null && isProtected;
  }
}
