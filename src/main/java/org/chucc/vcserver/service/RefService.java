package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.chucc.vcserver.dto.RefResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing refs (branches and tags).
 * Provides unified access to all refs in a dataset.
 */
@Service
public class RefService {

  private final BranchRepository branchRepository;
  private final TagRepository tagRepository;

  /**
   * Constructor for RefService.
   *
   * @param branchRepository the branch repository
   * @param tagRepository the tag repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared"
  )
  public RefService(BranchRepository branchRepository, TagRepository tagRepository) {
    this.branchRepository = branchRepository;
    this.tagRepository = tagRepository;
  }

  /**
   * Gets all refs (branches and tags) for a dataset.
   * Results are sorted with branches first (alphabetically), then tags (alphabetically).
   *
   * @param datasetName the dataset name
   * @return a list of all refs
   */
  public List<RefResponse> getAllRefs(String datasetName) {
    List<RefResponse> refs = new ArrayList<>();

    // Add all branches
    branchRepository.findAllByDataset(datasetName).forEach(branch -> {
      refs.add(new RefResponse(
          "branch",
          branch.getName(),
          branch.getCommitId().toString()
      ));
    });

    // Add all tags
    tagRepository.findAllByDataset(datasetName).forEach(tag -> {
      refs.add(new RefResponse(
          "tag",
          tag.name(),
          tag.commitId().toString()
      ));
    });

    // Sort: branches first (alphabetically), then tags (alphabetically)
    refs.sort(Comparator
        .comparing(RefResponse::getType)
        .thenComparing(RefResponse::getName));

    return refs;
  }
}
