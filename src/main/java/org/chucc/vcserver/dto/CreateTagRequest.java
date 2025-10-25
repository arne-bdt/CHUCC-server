package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for creating a tag.
 * Tags are immutable references to specific commits.
 */
@Schema(description = "Request to create a new immutable tag")
public record CreateTagRequest(
    @Schema(description = "Tag name (alphanumeric, dots, hyphens, underscores allowed)",
        example = "v1.0.0")
    String name,

    @Schema(description = "Target commit ID (UUIDv7)",
        example = "01933e4a-9d4e-7000-8000-000000000003")
    String target,

    @Schema(description = "Optional annotation message", example = "Release version 1.0.0")
    @JsonProperty(required = false)
    String message,

    @Schema(description = "Optional author (falls back to X-Author header, then 'anonymous')",
        example = "Alice <alice@example.org>")
    @JsonProperty(required = false)
    String author
) {
  /**
   * Validates the request fields.
   * Called by controller before command creation.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Tag name is required");
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("Target commit ID is required");
    }
    if (!name.matches("[a-zA-Z0-9._-]+")) {
      throw new IllegalArgumentException(
          "Invalid tag name format (allowed: alphanumeric, dots, hyphens, underscores)");
    }
  }
}
