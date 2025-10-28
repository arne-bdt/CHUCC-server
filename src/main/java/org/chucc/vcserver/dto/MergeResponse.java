package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for merge operation.
 * Provides information about the merge result including merge type, commit IDs,
 * conflict resolution statistics, and conflict scope used.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergeResponse(
    String result,
    String mergeCommit,
    String into,
    String from,
    String headCommit,
    Boolean fastForward,
    String strategy,
    String conflictScope,
    Integer conflictsResolved
) {
  /**
   * Creates a fast-forward merge response.
   * No merge commit is created; the branch pointer simply moves forward.
   *
   * @param into target branch
   * @param from source ref
   * @param headCommit the commit ID that HEAD now points to
   * @return fast-forward response
   */
  public static MergeResponse fastForward(String into, String from, String headCommit) {
    return new MergeResponse(
        "fast-forward",
        null,
        into,
        from,
        headCommit,
        true,
        "fast-forward",
        null,
        0
    );
  }

  /**
   * Creates a merge commit response.
   * A new merge commit was created with two parent commits.
   *
   * @param into target branch
   * @param from source ref
   * @param mergeCommit ID of created merge commit
   * @param strategy merge strategy used
   * @param conflictScope conflict resolution scope used
   * @param conflictsResolved number of conflicts auto-resolved
   * @return merge response
   */
  public static MergeResponse merged(String into, String from, String mergeCommit,
                                     String strategy, String conflictScope,
                                     int conflictsResolved) {
    return new MergeResponse(
        "merged",
        mergeCommit,
        into,
        from,
        null,
        false,
        strategy != null ? strategy : "three-way",
        conflictScope != null ? conflictScope : "graph",
        conflictsResolved
    );
  }
}
