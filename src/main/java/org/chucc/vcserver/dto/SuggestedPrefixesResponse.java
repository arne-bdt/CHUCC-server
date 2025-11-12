package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response containing suggested prefix mappings for a branch.
 *
 * <p>Analyzes the dataset to discover common namespaces and suggests conventional
 * prefixes based on the prefix.cc database. Includes usage frequency and status
 * for each suggestion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Suggested prefix mappings based on dataset analysis",
    example = "{\"dataset\":\"mydata\",\"branch\":\"main\","
        + "\"suggestions\":["
        + "{\"prefix\":\"foaf\",\"iri\":\"http://xmlns.com/foaf/0.1/\","
        + "\"frequency\":42,\"status\":\"ALREADY_DEFINED\"},"
        + "{\"prefix\":\"schema\",\"iri\":\"http://schema.org/\","
        + "\"frequency\":38,\"status\":\"SUGGESTED\"}"
        + "]}"
)
public record SuggestedPrefixesResponse(
    @Schema(
        description = "Dataset name",
        example = "mydata",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String dataset,

    @Schema(
        description = "Branch name",
        example = "main",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String branch,

    @Schema(
        description = "List of suggested prefix mappings, sorted by frequency descending",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    List<PrefixSuggestion> suggestions
) {

  /**
   * Creates a suggested prefixes response.
   *
   * @param dataset the dataset name
   * @param branch the branch name
   * @param suggestions the list of suggested prefix mappings
   */
  public SuggestedPrefixesResponse {
    // Defensive copy to ensure immutability
    suggestions = List.copyOf(suggestions);
  }
}
