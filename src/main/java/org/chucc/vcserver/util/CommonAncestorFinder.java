package org.chucc.vcserver.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.repository.CommitRepository;

/**
 * Utility for finding the lowest common ancestor (LCA) of two commits.
 *
 * <p>Uses a breadth-first search algorithm to find the first common ancestor
 * by expanding from both commits simultaneously.
 */
public final class CommonAncestorFinder {

  private final CommitRepository commitRepository;

  /**
   * Constructs a CommonAncestorFinder.
   *
   * @param commitRepository the commit repository for traversing commit history
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2",
      justification = "CommitRepository is a Spring-managed bean, not a mutable data structure")
  public CommonAncestorFinder(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Finds the lowest common ancestor of two commits.
   *
   * <p>Uses bidirectional BFS to find the first commit reachable from both
   * commit1 and commit2 by following parent links.
   *
   * @param datasetName dataset name
   * @param commit1Id first commit ID
   * @param commit2Id second commit ID
   * @return LCA commit ID, or empty if no common ancestor exists
   */
  public Optional<String> findCommonAncestor(String datasetName,
                                              String commit1Id, String commit2Id) {
    if (commit1Id.equals(commit2Id)) {
      return Optional.of(commit1Id);
    }

    // BFS from both commits simultaneously
    Set<String> visited1 = new HashSet<>();
    Queue<String> queue1 = new LinkedList<>();
    Queue<String> queue2 = new LinkedList<>();

    queue1.add(commit1Id);
    queue2.add(commit2Id);
    visited1.add(commit1Id);
    Set<String> visited2 = new HashSet<>();
    visited2.add(commit2Id);

    while (!queue1.isEmpty() || !queue2.isEmpty()) {
      // Expand from commit1
      if (!queue1.isEmpty()) {
        String current = queue1.poll();
        if (visited2.contains(current)) {
          return Optional.of(current); // Found common ancestor
        }

        Optional<Commit> commit = commitRepository.findByDatasetAndId(
            datasetName, CommitId.of(current));
        if (commit.isPresent()) {
          for (CommitId parentId : commit.get().parents()) {
            if (visited1.add(parentId.value())) {
              queue1.add(parentId.value());
            }
          }
        }
      }

      // Expand from commit2
      if (!queue2.isEmpty()) {
        String current = queue2.poll();
        if (visited1.contains(current)) {
          return Optional.of(current); // Found common ancestor
        }

        Optional<Commit> commit = commitRepository.findByDatasetAndId(
            datasetName, CommitId.of(current));
        if (commit.isPresent()) {
          for (CommitId parentId : commit.get().parents()) {
            if (visited2.add(parentId.value())) {
              queue2.add(parentId.value());
            }
          }
        }
      }
    }

    return Optional.empty(); // No common ancestor found
  }

  /**
   * Checks if commit1 is an ancestor of commit2 (commit2 is reachable from commit1).
   *
   * @param datasetName dataset name
   * @param ancestorId potential ancestor commit ID
   * @param descendantId potential descendant commit ID
   * @return true if ancestorId is ancestor of descendantId
   */
  public boolean isAncestor(String datasetName, String ancestorId, String descendantId) {
    if (ancestorId.equals(descendantId)) {
      return true;
    }

    Set<String> visited = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(descendantId);
    visited.add(descendantId);

    while (!queue.isEmpty()) {
      String current = queue.poll();

      Optional<Commit> commit = commitRepository.findByDatasetAndId(
          datasetName, CommitId.of(current));
      if (commit.isEmpty()) {
        continue;
      }

      for (CommitId parentId : commit.get().parents()) {
        if (parentId.value().equals(ancestorId)) {
          return true;
        }
        if (visited.add(parentId.value())) {
          queue.add(parentId.value());
        }
      }
    }

    return false;
  }
}
