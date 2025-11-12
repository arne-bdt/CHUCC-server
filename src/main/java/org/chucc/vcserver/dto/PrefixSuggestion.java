package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Suggested prefix mapping for a namespace detected in the dataset.
 *
 * <p>Includes the conventional prefix name, namespace IRI, usage frequency,
 * and status indicating whether the prefix is already defined or newly suggested.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Suggested prefix mapping for a detected namespace",
    example = "{\"prefix\":\"foaf\","
        + "\"iri\":\"http://xmlns.com/foaf/0.1/\","
        + "\"frequency\":42,"
        + "\"status\":\"SUGGESTED\"}"
)
public record PrefixSuggestion(
    @Schema(
        description = "Conventional prefix name",
        example = "foaf",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String prefix,

    @Schema(
        description = "Namespace IRI",
        example = "http://xmlns.com/foaf/0.1/",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String iri,

    @Schema(
        description = "Number of times this namespace appears in the dataset",
        example = "42",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    int frequency,

    @Schema(
        description = "Status indicating if prefix is already defined or newly suggested",
        example = "SUGGESTED",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Status status
) {

  /**
   * Status of a prefix suggestion.
   */
  @Schema(
      description = "Status indicating whether a prefix is already defined or newly suggested"
  )
  public enum Status {
    /**
     * Prefix is suggested but not yet defined in the dataset.
     */
    @Schema(description = "Prefix is suggested but not yet defined")
    SUGGESTED,

    /**
     * Prefix is already defined in the dataset's prefix map.
     */
    @Schema(description = "Prefix is already defined in the dataset")
    ALREADY_DEFINED
  }

  /**
   * Creates a prefix suggestion.
   *
   * @param prefix the conventional prefix name
   * @param iri the namespace IRI
   * @param frequency the number of times this namespace appears
   * @param status the status (SUGGESTED or ALREADY_DEFINED)
   */
  public PrefixSuggestion {
    // Validation
    if (prefix == null || prefix.isBlank()) {
      throw new IllegalArgumentException("Prefix cannot be null or blank");
    }
    if (iri == null || iri.isBlank()) {
      throw new IllegalArgumentException("IRI cannot be null or blank");
    }
    if (frequency < 0) {
      throw new IllegalArgumentException("Frequency cannot be negative");
    }
    if (status == null) {
      throw new IllegalArgumentException("Status cannot be null");
    }
  }
}
