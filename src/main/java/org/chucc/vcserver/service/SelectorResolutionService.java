package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.springframework.stereotype.Service;

/**
 * Service for resolving selector parameters to commit IDs.
 * Handles branch, commit, and asOf selectors per SPARQL 1.2 Protocol §4.
 */
@Service
public class SelectorResolutionService {

  private static final String DEFAULT_BRANCH = "main";

  private final BranchRepository branchRepository;
  private final TimestampResolutionService timestampResolutionService;

  /**
   * Constructor for SelectorResolutionService.
   *
   * @param branchRepository the branch repository
   * @param timestampResolutionService the timestamp resolution service
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references"
  )
  public SelectorResolutionService(
      BranchRepository branchRepository,
      TimestampResolutionService timestampResolutionService) {
    this.branchRepository = branchRepository;
    this.timestampResolutionService = timestampResolutionService;
  }

  /**
   * Resolve selector parameters to a commit ID.
   * Selectors are processed in this precedence order:
   * 1. Direct commit reference (highest priority)
   * 2. asOf timestamp (with or without branch)
   * 3. Branch HEAD
   * 4. Default branch HEAD (when no selectors provided)
   *
   * @param datasetName the dataset name
   * @param branch the branch selector (may be null or blank)
   * @param commit the commit selector (may be null or blank)
   * @param asOf the asOf selector (may be null or blank)
   * @return the resolved commit ID
   * @throws BranchNotFoundException if specified branch does not exist
   * @throws IllegalStateException if default branch does not exist
   */
  public CommitId resolve(
      String datasetName,
      String branch,
      String commit,
      String asOf) {
    // Validation (mutual exclusion) should already be done by caller

    // 1. Direct commit reference (highest priority)
    if (commit != null && !commit.isBlank()) {
      return CommitId.of(commit);
    }

    // 2. asOf timestamp (with or without branch)
    if (asOf != null && !asOf.isBlank()) {
      String branchName = (branch != null && !branch.isBlank()) ? branch : null;
      return timestampResolutionService.resolveAsOf(datasetName, branchName, asOf);
    }

    // 3. Branch HEAD
    if (branch != null && !branch.isBlank()) {
      Branch b = branchRepository.findByDatasetAndName(datasetName, branch)
          .orElseThrow(() -> new BranchNotFoundException(branch));
      return b.getCommitId();
    }

    // 4. Default branch HEAD
    Branch defaultBranch = branchRepository.findByDatasetAndName(datasetName, DEFAULT_BRANCH)
        .orElseThrow(() -> new IllegalStateException(
            "No default branch '" + DEFAULT_BRANCH + "' found in dataset: " + datasetName));
    return defaultBranch.getCommitId();
  }
}
