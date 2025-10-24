package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for branch creation.
 * Returns information about the newly created branch.
 */
public record CreateBranchResponse(
    String name,
    @JsonProperty("headCommit") String headCommit,
    @JsonProperty("createdFrom") String createdFrom,
    @JsonProperty("protected") boolean isProtected
) {}
