package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Component;

/**
 * Handles ResetBranchCommand by validating the command and producing a BranchResetEvent.
 */
@Component
public class ResetBranchCommandHandler implements CommandHandler<ResetBranchCommand> {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public ResetBranchCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
  }

  @Override
  public VersionControlEvent handle(ResetBranchCommand command) {
    // Validate branch exists
    var branch = branchRepository.findByDatasetAndName(command.dataset(), command.branchName())
        .orElseThrow(() -> new IllegalArgumentException(
            "Branch not found: " + command.branchName()
                + " in dataset: " + command.dataset()));

    // Validate target commit exists
    CommitId toCommitId = new CommitId(command.toCommitId());
    if (!commitRepository.exists(command.dataset(), toCommitId)) {
      throw new IllegalArgumentException(
          "Target commit not found: " + command.toCommitId()
              + " in dataset: " + command.dataset());
    }

    // Produce event
    return new BranchResetEvent(
        command.dataset(),
        command.branchName(),
        branch.getCommitId().value(),
        command.toCommitId(),
        Instant.now());
  }
}
