package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.sparql.core.DatasetGraph;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.CommitsSquashedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.util.RdfPatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles SquashCommand by combining multiple commits into a single commit
 * and producing a CommitsSquashedEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class SquashCommandHandler implements CommandHandler<SquashCommand> {

  private static final Logger logger = LoggerFactory.getLogger(SquashCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final DatasetService datasetService;

  /**
   * Constructs a SquashCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param datasetService the dataset service for materializing commit states
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public SquashCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      DatasetService datasetService) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.datasetService = datasetService;
  }

  @Override
  public VersionControlEvent handle(SquashCommand command) {
    // 1. Validate branch exists
    Branch branch = branchRepository
        .findByDatasetAndName(command.dataset(), command.branch())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branch()
                + " in dataset: " + command.dataset()));

    // 2. Validate commits exist and are contiguous
    List<Commit> commits = validateAndFetchCommits(command.dataset(), command.commitIds());

    // 3. Get the first commit and validate it has a parent
    Commit firstCommit = commits.get(0);
    if (firstCommit.parents().isEmpty()) {
      throw new IllegalArgumentException("Cannot squash initial commit");
    }
    CommitId baseCommitId = firstCommit.parents().get(0);

    // 4. Get the last commit
    Commit lastCommit = commits.get(commits.size() - 1);

    // 5. Verify last commit is branch HEAD (simplified implementation)
    if (!lastCommit.id().equals(branch.getCommitId())) {
      throw new UnsupportedOperationException(
          "Squashing non-HEAD commits not yet supported");
    }

    // 6. Get state before and after the squashed range
    DatasetGraph beforeState = datasetService.materializeCommit(
        command.dataset(), baseCommitId);
    DatasetGraph afterState = datasetService.materializeCommit(
        command.dataset(), lastCommit.id());

    // 7. Compute combined patch (diff between before and after)
    RDFPatch combinedPatch = RdfPatchUtil.diff(beforeState, afterState);

    // 8. Create new squashed commit
    CommitId newCommitId = CommitId.generate();
    String author = command.author() != null ? command.author() : firstCommit.author();
    Commit newCommit = new Commit(
        newCommitId,
        List.of(baseCommitId),
        author,
        command.message(),
        Instant.now()
    );

    // 9. Save new commit with combined patch
    commitRepository.save(command.dataset(), newCommit, combinedPatch);

    // 10. Update branch to point to new commit
    String previousHead = branch.getCommitId().value();
    branch.updateCommit(newCommitId);
    branchRepository.save(command.dataset(), branch);

    // 11. Produce event
    VersionControlEvent event = new CommitsSquashedEvent(
        command.dataset(),
        command.branch(),
        newCommitId.value(),
        command.commitIds(),
        author,
        command.message(),
        Instant.now(),
        previousHead
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
   * Validates that all commits exist and are contiguous in the branch history.
   *
   * @param dataset the dataset name
   * @param commitIds the list of commit IDs to validate
   * @return the list of commits in order
   * @throws IllegalArgumentException if any commit is missing or commits are not contiguous
   */
  private List<Commit> validateAndFetchCommits(String dataset, List<String> commitIds) {
    List<Commit> commits = new ArrayList<>();

    for (String commitIdStr : commitIds) {
      CommitId commitId = new CommitId(commitIdStr);
      Commit commit = commitRepository
          .findByDatasetAndId(dataset, commitId)
          .orElseThrow(() -> new IllegalArgumentException(
              "Commit not found: " + commitIdStr + " in dataset: " + dataset));
      commits.add(commit);
    }

    // Verify commits are contiguous in history
    for (int i = 1; i < commits.size(); i++) {
      Commit current = commits.get(i);
      Commit previous = commits.get(i - 1);

      if (current.parents().size() != 1
          || !current.parents().get(0).equals(previous.id())) {
        throw new IllegalArgumentException(
            "Commits must be contiguous in branch history");
      }
    }

    return commits;
  }
}
