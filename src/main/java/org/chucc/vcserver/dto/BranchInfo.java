package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Branch information DTO with full metadata.
 * Provides detailed information about a branch including protection status,
 * timestamps, and commit count.
 */
public record BranchInfo(
    String name,
    @JsonProperty("headCommit") String headCommitId,
    @JsonProperty("protected") boolean isProtected,
    Instant createdAt,
    Instant lastUpdated,
    int commitCount
) {}
