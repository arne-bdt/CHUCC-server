package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.dto.BranchInfo;
import org.chucc.vcserver.repository.BranchRepository;
import org.springframework.stereotype.Service;

/**
 * Service for branch operations.
 * Provides detailed branch information (unlike RefService which provides unified view).
 */
@Service
public class BranchService {

  private final BranchRepository branchRepository;

  /**
   * Constructs a BranchService.
   *
   * @param branchRepository the branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repository is Spring-managed bean and is intentionally shared")
  public BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  /**
   * Lists all branches in a dataset with full metadata.
   *
   * @param dataset the dataset name
   * @return list of branch information
   */
  public List<BranchInfo> listBranches(String dataset) {
    return branchRepository.findAllByDataset(dataset)
        .stream()
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getCommitId().value(),
            branch.isProtected(),
            branch.getCreatedAt(),
            branch.getLastUpdated(),
            branch.getCommitCount()
        ))
        .toList();
  }

  /**
   * Gets detailed information about a specific branch.
   *
   * @param dataset the dataset name
   * @param name the branch name
   * @return branch information if found
   */
  public Optional<BranchInfo> getBranchInfo(String dataset, String name) {
    return branchRepository.findByDatasetAndName(dataset, name)
        .map(branch -> new BranchInfo(
            branch.getName(),
            branch.getCommitId().value(),
            branch.isProtected(),
            branch.getCreatedAt(),
            branch.getLastUpdated(),
            branch.getCommitCount()
        ));
  }
}
