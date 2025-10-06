package org.chucc.vcserver.service;

import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.util.EtagUtil;
import org.springframework.stereotype.Service;

/**
 * Service for checking HTTP preconditions (If-Match headers) for version control operations.
 * <p>
 * Implements optimistic concurrency control per SPARQL 1.2 Protocol §6.
 * Precondition checks fail fast (412) before applying patches, while semantic
 * conflict detection (409) still occurs afterward.
 * </p>
 */
@Service
public class PreconditionService {

  private final BranchRepository branchRepository;

  /**
   * Constructs a PreconditionService.
   *
   * @param branchRepository repository for accessing branch information
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "BranchRepository is a Spring-managed bean and is intentionally shared")
  public PreconditionService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  /**
   * Checks If-Match precondition for branch write operations.
   * <p>
   * Returns silently if check passes or if no precondition is specified.
   * Throws PreconditionFailedException if check fails.
   * </p>
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @param ifMatchHeader the If-Match header value (may be null)
   * @throws PreconditionFailedException if the precondition fails
   * @throws IllegalArgumentException if the branch is not found
   */
  public void checkIfMatch(String datasetName, String branchName, String ifMatchHeader) {
    // No precondition specified - allow operation
    if (ifMatchHeader == null || ifMatchHeader.isBlank()) {
      return;
    }

    // Find the branch
    Branch branch = branchRepository.findByDatasetAndName(datasetName, branchName)
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + branchName + " in dataset: " + datasetName));

    // Get current HEAD commit
    String currentHead = branch.getCommitId().toString();

    // Parse expected HEAD from ETag
    String expectedHead = EtagUtil.parseEtag(ifMatchHeader);

    // Check if they match
    if (!currentHead.equals(expectedHead)) {
      throw new PreconditionFailedException(expectedHead, currentHead);
    }
  }
}

