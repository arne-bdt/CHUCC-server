package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.command.PatchIntersection;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.ConflictItem;
import org.chucc.vcserver.exception.ConcurrentWriteConflictException;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Service;

/**
 * Service for detecting conflicts in concurrent writes.
 * Checks if patches conflict with intermediate commits between a base commit and branch head.
 */
@Service
public class ConflictDetectionService {

  private final CommitRepository commitRepository;

  /**
   * Constructs a ConflictDetectionService.
   *
   * @param commitRepository the commit repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "CommitRepository is a Spring-managed bean and is intentionally shared")
  public ConflictDetectionService(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Checks for concurrent writes by detecting if the branch head has advanced
   * beyond the base commit and whether the new patch conflicts with intermediate changes.
   *
   * @param dataset the dataset name
   * @param currentHead the current branch head commit ID
   * @param baseCommit the base commit ID from the command
   * @param newPatch the new patch to be applied
   * @throws ConcurrentWriteConflictException if conflicts are detected
   */
  public void checkForConcurrentWrites(String dataset, CommitId currentHead,
      CommitId baseCommit, RDFPatch newPatch) {
    // If branch head hasn't advanced, no concurrent writes
    if (currentHead.equals(baseCommit)) {
      return;
    }

    // Get all commits from baseCommit (exclusive) to currentHead (inclusive)
    List<Commit> intermediateCommits = getCommitRange(dataset, baseCommit, currentHead);

    if (intermediateCommits.isEmpty()) {
      return;
    }

    // Check each intermediate commit's patch for conflicts
    List<ConflictItem> allConflicts = new ArrayList<>();
    for (Commit commit : intermediateCommits) {
      RDFPatch intermediatePatch = commitRepository
          .findPatchByDatasetAndId(dataset, commit.id())
          .orElse(null);

      if (intermediatePatch != null) {
        List<ConflictItem> conflicts = PatchIntersection.detectConflicts(
            newPatch, intermediatePatch);
        allConflicts.addAll(conflicts);
      }
    }

    // If conflicts found, throw exception with details
    if (!allConflicts.isEmpty()) {
      throw new ConcurrentWriteConflictException(
          "Concurrent write conflict detected: branch has advanced since base commit "
              + baseCommit.value(),
          allConflicts
      );
    }
  }

  /**
   * Gets all commits in the range (from..to] (exclusive of from, inclusive of to).
   * Returns commits in chronological order (oldest first).
   *
   * @param dataset the dataset name
   * @param from the base commit (exclusive)
   * @param to the head commit (inclusive)
   * @return list of commits in chronological order
   */
  private List<Commit> getCommitRange(String dataset, CommitId from, CommitId to) {
    if (from.equals(to)) {
      return new ArrayList<>();
    }

    List<Commit> result = new ArrayList<>();
    CommitId current = to;

    // Walk back from 'to' until we reach 'from'
    while (current != null && !current.equals(from)) {
      Commit commit = commitRepository
          .findByDatasetAndId(dataset, current)
          .orElse(null);

      if (commit == null) {
        break;
      }

      result.add(commit);

      // For linear history, take the first parent
      // (Graph operations always create linear commits)
      if (commit.parents().isEmpty()) {
        break;
      }
      current = commit.parents().get(0);
    }

    // Reverse to get chronological order (oldest first)
    java.util.Collections.reverse(result);
    return result;
  }
}
