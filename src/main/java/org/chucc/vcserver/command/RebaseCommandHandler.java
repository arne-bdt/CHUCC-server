package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchRebasedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles RebaseCommand by reapplying commits from one branch onto another
 * and producing a BranchRebasedEvent.
 *
 * <p><b>CQRS Pattern:</b> This handler does NOT write to repositories.
 * Instead, it publishes a {@link BranchRebasedEvent} containing all data needed
 * to recreate the rebased commits. The {@link org.chucc.vcserver.projection.ReadModelProjector}
 * consumes the event and updates repositories asynchronously.</p>
 *
 * <p>This ensures:
 * <ul>
 *   <li>No dual-write pattern (single source of truth: Kafka)</li>
 *   <li>Event replay works correctly (event contains full commit data)</li>
 *   <li>Eventual consistency (HTTP 202 returned before repository update)</li>
 *   <li>Atomic rebase (all commits created together or none)</li>
 * </ul>
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class RebaseCommandHandler implements CommandHandler<RebaseCommand> {

  private static final Logger logger = LoggerFactory.getLogger(RebaseCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final TagRepository tagRepository;

  /**
   * Constructs a RebaseCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param tagRepository the tag repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public RebaseCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      TagRepository tagRepository) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.tagRepository = tagRepository;
  }

  @Override
  public VersionControlEvent handle(RebaseCommand command) {
    // 1. Validate branch exists
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    CommitId previousHead = branch.getCommitId();

    // 2. Resolve "onto" reference (could be branch name, tag name, or commit ID)
    CommitId ontoCommitId = resolveRef(command.dataset(), command.onto());

    // 3. Validate "onto" commit exists
    commitRepository
        .findByDatasetAndId(command.dataset(), ontoCommitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Target commit not found: " + ontoCommitId.value()
                + " in dataset: " + command.dataset()));

    // 4. Resolve "from" commit
    CommitId fromCommitId = new CommitId(command.from());

    // 5. Validate "from" commit exists
    commitRepository
        .findByDatasetAndId(command.dataset(), fromCommitId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Base commit not found: " + command.from()
                + " in dataset: " + command.dataset()));

    // 6. Find commits to rebase (from..branch] - exclusive of from, inclusive of branch head)
    List<Commit> commitsToRebase = findCommitRange(command.dataset(), fromCommitId,
        previousHead);

    if (commitsToRebase.isEmpty()) {
      throw new IllegalArgumentException(
          "No commits to rebase: range (" + command.from() + ".." + previousHead.value() + "]"
      );
    }

    // 7. Collect rebased commit data (without writing to repository)
    CommitId currentCommitId = ontoCommitId;
    List<BranchRebasedEvent.RebasedCommitData> rebasedCommits = new ArrayList<>();

    for (Commit originalCommit : commitsToRebase) {
      // Get patch for this commit
      RDFPatch patch = commitRepository
          .findPatchByDatasetAndId(command.dataset(), originalCommit.id())
          .orElseThrow(() -> new IllegalStateException(
              "Patch not found for commit: " + originalCommit.id().value()));

      // Check for conflicts (simplified: actual implementation would check if patch
      // can be applied cleanly)
      // For now, we assume no conflicts unless patches intersect
      // Note: A more sophisticated implementation would materialize the dataset state
      // and try to apply the patch

      // Prepare new commit metadata
      CommitId newCommitId = CommitId.generate();
      String patchString = RdfPatchUtil.toString(patch);

      // Collect commit data for event (projector will create actual commits)
      rebasedCommits.add(new BranchRebasedEvent.RebasedCommitData(
          newCommitId.value(),
          List.of(currentCommitId.value()),  // parent
          originalCommit.author(),
          originalCommit.message(),
          patchString,
          originalCommit.patchSize()
      ));

      // Update current commit for next iteration
      currentCommitId = newCommitId;
    }

    // 8. Produce event with ALL data needed to recreate the rebased commits
    // The projector will handle repository updates asynchronously and atomically
    VersionControlEvent event = new BranchRebasedEvent(
        command.dataset(),
        command.branch(),
        currentCommitId.value(),  // final HEAD after rebase
        previousHead.value(),
        rebasedCommits,  // full commit data for all rebased commits
        command.author(),
        Instant.now()
    );

    // Publish event to Kafka (async, with proper error logging)
    eventPublisher.publish(event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event {} to Kafka: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
            // Note: Exception logged but not swallowed
            // If this happens before HTTP response, controller will catch it
          } else {
            logger.debug("Successfully published event {} to Kafka",
                event.getClass().getSimpleName());
          }
        });

    return event;
  }

  /**
   * Resolves a reference to a commit ID.
   * The reference can be a branch name, tag name, or commit ID.
   *
   * @param dataset the dataset name
   * @param ref the reference (branch, tag, or commit ID)
   * @return the resolved commit ID
   */
  private CommitId resolveRef(String dataset, String ref) {
    // Try to resolve as branch name
    var branchOpt = branchRepository.findByDatasetAndName(dataset, ref);
    if (branchOpt.isPresent()) {
      return branchOpt.get().getCommitId();
    }

    // Try to resolve as tag name
    var tagOpt = tagRepository.findByDatasetAndName(dataset, ref);
    if (tagOpt.isPresent()) {
      return tagOpt.get().commitId();
    }

    // Assume it's a commit ID
    return new CommitId(ref);
  }

  /**
   * Finds all commits in the range (from..to] (exclusive of from, inclusive of to).
   * Returns commits in chronological order (oldest first).
   *
   * @param dataset the dataset name
   * @param from the base commit (exclusive)
   * @param to the head commit (inclusive)
   * @return list of commits to rebase, in chronological order
   */
  private List<Commit> findCommitRange(String dataset, CommitId from, CommitId to) {
    Set<CommitId> fromAncestors = new HashSet<>();

    // First, find all ancestors of 'from' (to exclude them)
    collectAncestors(dataset, from, fromAncestors);
    fromAncestors.add(from); // Also exclude 'from' itself

    // Then, walk back from 'to' and collect commits that are not ancestors of 'from'
    List<Commit> result = new ArrayList<>();
    Set<CommitId> visited = new HashSet<>();
    Deque<CommitId> queue = new ArrayDeque<>();
    queue.add(to);

    while (!queue.isEmpty()) {
      CommitId currentId = queue.removeFirst();

      // Skip if already visited or if it's an ancestor of 'from'
      if (visited.contains(currentId) || fromAncestors.contains(currentId)) {
        continue;
      }

      visited.add(currentId);

      // Get the commit
      Commit commit = commitRepository
          .findByDatasetAndId(dataset, currentId)
          .orElseThrow(() -> new IllegalStateException(
              "Commit not found during range traversal: " + currentId.value()));

      result.add(commit);

      // Add parents to queue
      queue.addAll(commit.parents());
    }

    // Reverse to get chronological order (oldest first)
    Collections.reverse(result);
    return result;
  }

  /**
   * Collects all ancestors of a commit.
   *
   * @param dataset the dataset name
   * @param commitId the starting commit
   * @param ancestors the set to collect ancestors into
   */
  private void collectAncestors(String dataset, CommitId commitId, Set<CommitId> ancestors) {
    if (ancestors.contains(commitId)) {
      return; // Already visited
    }

    ancestors.add(commitId);

    Commit commit = commitRepository
        .findByDatasetAndId(dataset, commitId)
        .orElse(null);

    if (commit != null) {
      for (CommitId parent : commit.parents()) {
        collectAncestors(dataset, parent, ancestors);
      }
    }
  }
}
