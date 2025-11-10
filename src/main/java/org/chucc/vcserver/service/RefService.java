package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.chucc.vcserver.dto.PaginationInfo;
import org.chucc.vcserver.dto.RefResponse;
import org.chucc.vcserver.dto.RefsListResponse;
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
   * Lists all refs (branches and tags) in a dataset with pagination.
   * Results are sorted with branches first (alphabetically), then tags (alphabetically).
   *
   * @param datasetName the dataset name
   * @param limit maximum number of results to return
   * @param offset number of results to skip
   * @return refs list response with pagination metadata
   */
  public RefsListResponse getAllRefs(String datasetName, int limit, int offset) {
    // Get all branches and tags
    List<RefResponse> allRefs = new ArrayList<>();

    // Add all branches
    branchRepository.findAllByDataset(datasetName).forEach(branch -> {
      allRefs.add(new RefResponse(
          "branch",
          branch.getName(),
          branch.getCommitId().toString()
      ));
    });

    // Add all tags
    tagRepository.findAllByDataset(datasetName).forEach(tag -> {
      allRefs.add(new RefResponse(
          "tag",
          tag.name(),
          tag.commitId().toString()
      ));
    });

    // Sort: branches first (alphabetically), then tags (alphabetically)
    allRefs.sort(Comparator
        .comparing(RefResponse::getType)
        .thenComparing(RefResponse::getName));

    // Calculate hasMore BEFORE applying pagination (matches HistoryService pattern)
    boolean hasMore = allRefs.size() > offset + limit;

    // Apply offset and limit
    List<RefResponse> refs = allRefs.stream()
        .skip(offset)
        .limit(limit)
        .toList();

    // Build pagination metadata
    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);

    return new RefsListResponse(refs, pagination);
  }
}
