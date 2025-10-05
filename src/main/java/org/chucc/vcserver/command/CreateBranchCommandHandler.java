package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Component;

/**
 * Handles CreateBranchCommand by validating the command and producing a BranchCreatedEvent.
 */
@Component
public class CreateBranchCommandHandler implements CommandHandler<CreateBranchCommand> {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public CreateBranchCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
  }

  @Override
  public VersionControlEvent handle(CreateBranchCommand command) {
    // Validate branch doesn't already exist
    if (branchRepository.exists(command.dataset(), command.branchName())) {
      throw new IllegalStateException(
          "Branch already exists: " + command.branchName()
              + " in dataset: " + command.dataset());
    }

    // Validate source commit exists
    CommitId fromCommitId = new CommitId(command.fromCommitId());
    if (!commitRepository.exists(command.dataset(), fromCommitId)) {
      throw new IllegalArgumentException(
          "Source commit not found: " + command.fromCommitId()
              + " in dataset: " + command.dataset());
    }

    // Produce event
    return new BranchCreatedEvent(
        command.dataset(),
        command.branchName(),
        command.fromCommitId(),
        Instant.now());
  }
}
