package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.event.BranchDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.ProtectedBranchException;
import org.chucc.vcserver.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles DeleteBranchCommand by validating the command and producing a BranchDeletedEvent.
 * Protects the main branch from deletion.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class DeleteBranchCommandHandler implements CommandHandler<DeleteBranchCommand> {

  private static final Logger logger = LoggerFactory.getLogger(DeleteBranchCommandHandler.class);
  private static final String MAIN_BRANCH = "main";

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;

  /**
   * Constructs a DeleteBranchCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public DeleteBranchCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository) {
    this.eventPublisher = eventPublisher;
    this.branchRepository = branchRepository;
  }

  @Override
  public VersionControlEvent handle(DeleteBranchCommand command) {
    logger.info("Deleting branch: {} in dataset: {}", command.branchName(), command.dataset());

    // Protect main branch from deletion
    if (MAIN_BRANCH.equals(command.branchName())) {
      throw new ProtectedBranchException("Cannot delete main branch");
    }

    // Validate branch exists
    Branch branch = branchRepository.findByDatasetAndName(command.dataset(), command.branchName())
        .orElseThrow(() -> new BranchNotFoundException(
            command.branchName() + " in dataset: " + command.dataset()));

    // Produce event
    VersionControlEvent event = new BranchDeletedEvent(
        command.dataset(),
        command.branchName(),
        branch.getCommitId().value(), // Record last commit for audit trail
        command.author(),
        Instant.now()
    );

    // Publish event to Kafka (fire-and-forget, async)
    eventPublisher.publish(event)
        .exceptionally(ex -> {
          logger.error("Failed to publish event {}: {}",
              event.getClass().getSimpleName(), ex.getMessage(), ex);
          return null;
        });

    logger.info("Branch {} deleted from dataset {} (was at commit {})",
        command.branchName(), command.dataset(), branch.getCommitId());

    return event;
  }
}
