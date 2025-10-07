package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for resolving asOf timestamps to commit IDs.
 * Enables time-travel queries and operations by finding the most recent
 * commit at or before a specified timestamp.
 */
@Service
public class TimestampResolutionService {

  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;

  /**
   * Constructor for TimestampResolutionService.
   *
   * @param commitRepository the commit repository
   * @param branchRepository the branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references"
  )
  public TimestampResolutionService(
      CommitRepository commitRepository,
      BranchRepository branchRepository) {
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
  }

  /**
   * Resolve asOf timestamp to a commit ID.
   *
   * @param datasetName the dataset name
   * @param branchName optional branch name (null for any commit)
   * @param asOf RFC 3339 timestamp string
   * @return commit ID at or before timestamp
   * @throws CommitNotFoundException if no commit exists at/before timestamp
   * @throws BranchNotFoundException if branch does not exist
   * @throws IllegalArgumentException if timestamp format is invalid
   */
  public CommitId resolveAsOf(String datasetName, String branchName, String asOf) {
    Instant timestamp = parseRfc3339(asOf);

    if (branchName != null && !branchName.isBlank()) {
      // asOf + branch: walk branch history
      return resolveAsOfOnBranch(datasetName, branchName, timestamp);
    } else {
      // asOf only: find any commit at/before timestamp
      return resolveAsOfGlobal(datasetName, timestamp);
    }
  }

  /**
   * Resolve asOf timestamp on a specific branch.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @param timestamp the timestamp to resolve
   * @return commit ID at or before timestamp on the branch
   * @throws BranchNotFoundException if branch does not exist
   * @throws CommitNotFoundException if no commit found at/before timestamp
   */
  private CommitId resolveAsOfOnBranch(
      String datasetName,
      String branchName,
      Instant timestamp) {
    Branch branch = branchRepository.findByDatasetAndName(datasetName, branchName)
        .orElseThrow(() -> new BranchNotFoundException(branchName));

    // Walk commit history from branch HEAD backwards
    CommitId currentId = branch.getCommitId();
    Commit bestMatch = null;

    while (currentId != null) {
      final CommitId commitIdForLambda = currentId;
      Commit commit = commitRepository.findByDatasetAndId(datasetName, currentId)
          .orElseThrow(() -> new CommitNotFoundException(commitIdForLambda.toString()));

      // Check if commit is at or before timestamp (inclusive)
      if (!commit.timestamp().isAfter(timestamp)) {
        // Found a match - this is the most recent one
        bestMatch = commit;
        break;
      }

      // Move to parent (first parent in case of merge)
      if (!commit.parents().isEmpty()) {
        currentId = commit.parents().get(0);
      } else {
        currentId = null;
      }
    }

    if (bestMatch == null) {
      throw new CommitNotFoundException(
          "No commit found on branch " + branchName + " at or before " + timestamp,
          true
      );
    }

    return bestMatch.id();
  }

  /**
   * Resolve asOf timestamp globally (across all commits).
   *
   * @param datasetName the dataset name
   * @param timestamp the timestamp to resolve
   * @return commit ID at or before timestamp
   * @throws CommitNotFoundException if no commit found at/before timestamp
   */
  private CommitId resolveAsOfGlobal(String datasetName, Instant timestamp) {
    Optional<Commit> commit = commitRepository.findLatestBeforeTimestamp(
        datasetName, timestamp);
    return commit
        .map(Commit::id)
        .orElseThrow(() -> new CommitNotFoundException(
            "No commit found at or before " + timestamp,
            true
        ));
  }

  /**
   * Parse RFC 3339 timestamp string.
   *
   * @param timestamp the timestamp string
   * @return parsed Instant
   * @throws IllegalArgumentException if timestamp format is invalid
   */
  private Instant parseRfc3339(String timestamp) {
    try {
      return Instant.parse(timestamp);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Invalid RFC 3339 timestamp: " + timestamp, e);
    }
  }
}
