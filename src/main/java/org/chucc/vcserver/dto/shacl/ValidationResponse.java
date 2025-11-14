package org.chucc.vcserver.dto.shacl;

/**
 * SHACL validation response (when storing results).
 *
 * <p>Returned with HTTP 202 Accepted when results are stored.</p>
 *
 * @param message success message
 * @param commitId version control commit ID
 * @param conforms whether validation passed
 * @param resultsGraph graph URI where results were stored
 */
public record ValidationResponse(
    String message,
    String commitId,
    Boolean conforms,
    String resultsGraph
) {
}
