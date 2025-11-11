package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Response containing prefix mappings for a branch or commit.
 * Returns the current prefix map along with dataset and version metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description = "Prefix mappings for a branch or commit in the version control system",
    example = "{\"dataset\":\"mydata\",\"branch\":\"main\","
        + "\"commitId\":\"01936c7f-8a2e-7890-abcd-ef1234567890\","
        + "\"prefixes\":{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\","
        + "\"foaf\":\"http://xmlns.com/foaf/0.1/\"}}"
)
public record PrefixResponse(
    @Schema(
        description = "Dataset name",
        example = "mydata",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String dataset,

    @Schema(
        description = "Branch name (null for commit-based queries)",
        example = "main"
    )
    String branch,

    @Schema(
        description = "Commit ID at which prefixes are retrieved",
        example = "01936c7f-8a2e-7890-abcd-ef1234567890",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String commitId,

    @Schema(
        description = "Prefix mappings (prefix name → namespace IRI)",
        example = "{\"rdf\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\","
            + "\"foaf\":\"http://xmlns.com/foaf/0.1/\"}",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    Map<String, String> prefixes
) {
  /**
   * Creates a prefix response.
   *
   * @param dataset the dataset name
   * @param branch the branch name (nullable for commit queries)
   * @param commitId the commit ID
   * @param prefixes the prefix mappings (prefix name → namespace IRI)
   */
  public PrefixResponse {
    // Defensive copy to ensure immutability
    prefixes = Map.copyOf(prefixes);
  }
}
