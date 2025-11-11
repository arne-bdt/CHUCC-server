package org.chucc.vcserver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request to update prefix mappings on a branch.
 * Used for PUT, PATCH, and DELETE operations.
 */
@Schema(
    description = "Request to update prefix mappings via PUT, PATCH, or DELETE operations",
    example = "{\"message\":\"Add RDF prefixes\","
        + "\"prefixes\":{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\","
        + "\"rdfs\":\"http://www.w3.org/2000/01/rdf-schema#\"}}"
)
public record UpdatePrefixesRequest(
    @Schema(
        description = "Optional commit message (auto-generated if not provided)",
        example = "Add RDF prefixes"
    )
    String message,

    @Schema(
        description = "Prefix mappings to add, replace, or delete (prefix name → namespace IRI)",
        example = "{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\","
            + "\"rdfs\":\"http://www.w3.org/2000/01/rdf-schema#\"}",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Prefixes map is required")
    Map<String, String> prefixes
) {
  /**
   * Creates an update prefixes request.
   *
   * @param message optional commit message
   * @param prefixes prefix mappings to add/replace/delete
   */
  public UpdatePrefixesRequest {
    // Defensive copy to ensure immutability
    prefixes = Map.copyOf(prefixes);
  }
}
