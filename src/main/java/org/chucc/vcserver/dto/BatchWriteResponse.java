package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for batch write operations endpoint.
 *
 * <p>Returns information about the single commit created from the batch of operations.
 * Follows the CQRS async pattern with status "accepted" and eventual consistency.
 *
 * @param status Status of the batch operation ("accepted")
 * @param commitId ID of the created commit
 * @param branch Target branch name
 * @param operationCount Number of operations combined into the commit
 * @param message Commit message
 */
public record BatchWriteResponse(
    String status,
    String commitId,
    String branch,
    @JsonProperty("operationCount") int operationCount,
    String message
) {
  /**
   * Creates a successful batch write response with status "accepted".
   *
   * @param commitId Created commit ID
   * @param branch Target branch
   * @param operationCount Number of operations
   * @param message Commit message
   * @return response DTO with status "accepted"
   */
  public static BatchWriteResponse accepted(
      String commitId,
      String branch,
      int operationCount,
      String message) {
    return new BatchWriteResponse("accepted", commitId, branch, operationCount, message);
  }
}
