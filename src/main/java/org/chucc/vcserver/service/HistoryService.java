package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.CommitHistoryInfo;
import org.chucc.vcserver.dto.HistoryResponse;
import org.chucc.vcserver.dto.PaginationInfo;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for managing commit history.
 * Provides filtering and pagination for commit history listings.
 */
@Service
public class HistoryService {

  private final CommitRepository commitRepository;
  private final BranchRepository branchRepository;

  /**
   * Constructor for HistoryService.
   *
   * @param commitRepository the commit repository
   * @param branchRepository the branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared"
  )
  public HistoryService(CommitRepository commitRepository, BranchRepository branchRepository) {
    this.commitRepository = commitRepository;
    this.branchRepository = branchRepository;
  }

  /**
   * Lists commit history with filters and pagination.
   *
   * @param dataset the dataset name
   * @param branch optional branch name to filter commits reachable from branch HEAD
   * @param since optional timestamp to filter commits after this time (inclusive)
   * @param until optional timestamp to filter commits before this time (inclusive)
   * @param author optional author string to filter commits (exact match, case-sensitive)
   * @param limit maximum number of results per page
   * @param offset number of results to skip for pagination
   * @return history response with commits and pagination metadata
   */
  public HistoryResponse listHistory(
      String dataset,
      String branch,
      Instant since,
      Instant until,
      String author,
      int limit,
      int offset) {

    // Step 1: Get all commits for dataset
    List<Commit> allCommits = commitRepository.findAllByDataset(dataset);

    // Step 2-5: Build filter chain (lazy evaluation - single pass)
    Stream<Commit> stream = allCommits.stream();

    // Filter by branch (if specified)
    if (branch != null) {
      Branch branchObj = branchRepository.findByDatasetAndName(dataset, branch)
          .orElseThrow(() -> new BranchNotFoundException(branch));

      Set<CommitId> reachableCommits = getReachableCommits(allCommits, branchObj.getCommitId());
      stream = stream.filter(c -> reachableCommits.contains(c.id()));
    }

    // Filter by date range
    if (since != null) {
      stream = stream.filter(c -> !c.timestamp().isBefore(since));
    }
    if (until != null) {
      stream = stream.filter(c -> !c.timestamp().isAfter(until));
    }

    // Filter by author
    if (author != null) {
      stream = stream.filter(c -> c.author().equals(author));
    }

    // Sort by timestamp descending (newest first) and collect once
    List<Commit> filteredCommits = stream
        .sorted(Comparator.comparing(Commit::timestamp).reversed())
        .collect(Collectors.toList());

    // Step 6: Apply pagination
    boolean hasMore = filteredCommits.size() > offset + limit;
    List<Commit> pageCommits = filteredCommits.stream()
        .skip(offset)
        .limit(limit)
        .collect(Collectors.toList());

    // Step 7: Convert to DTOs
    List<CommitHistoryInfo> commitInfos = pageCommits.stream()
        .map(this::toCommitHistoryInfo)
        .collect(Collectors.toList());

    PaginationInfo pagination = new PaginationInfo(limit, offset, hasMore);
    return new HistoryResponse(commitInfos, pagination);
  }

  /**
   * Gets all commits reachable from a starting commit by following parent links.
   * Uses breadth-first search to traverse the commit graph.
   * Optimized: loads all commits once, then traverses in-memory (avoids N+1 queries).
   *
   * @param allCommits all commits in the dataset (already loaded)
   * @param startCommit the starting commit ID
   * @return set of all reachable commit IDs
   */
  private Set<CommitId> getReachableCommits(List<Commit> allCommits, CommitId startCommit) {
    // Build in-memory index for O(1) lookups (avoids N+1 repository queries)
    Map<CommitId, Commit> commitMap = allCommits.stream()
        .collect(Collectors.toMap(Commit::id, Function.identity()));

    Set<CommitId> reachable = new HashSet<>();
    Queue<CommitId> queue = new LinkedList<>();
    queue.add(startCommit);

    while (!queue.isEmpty()) {
      CommitId current = queue.poll();

      if (reachable.contains(current)) {
        continue; // Already visited
      }

      reachable.add(current);

      // In-memory lookup (O(1) instead of repository query)
      Commit commit = commitMap.get(current);
      if (commit != null) {
        queue.addAll(commit.parents());
      }
    }

    return reachable;
  }

  /**
   * Converts a Commit domain object to CommitHistoryInfo DTO.
   *
   * @param commit the commit to convert
   * @return commit history info DTO
   */
  private CommitHistoryInfo toCommitHistoryInfo(Commit commit) {
    return new CommitHistoryInfo(
        commit.id().value(),
        commit.message(),
        commit.author(),
        commit.timestamp(),
        commit.parents().stream().map(CommitId::value).collect(Collectors.toList()),
        commit.patchSize()
    );
  }
}
