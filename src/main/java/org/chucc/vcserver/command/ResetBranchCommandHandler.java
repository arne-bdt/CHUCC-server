package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles ResetBranchCommand by validating the command and producing a BranchResetEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class ResetBranchCommandHandler implements CommandHandler<ResetBranchCommand> {

  private static final Logger logger = LoggerFactory.getLogger(ResetBranchCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  /**
   * Constructs a ResetBranchCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public ResetBranchCommandHandler(
      EventPublisher eventPublisher,
      BranchRepository branchRepository,
      CommitRepository commitRepository) {
    this.eventPublisher = eventPublisher;
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
    VersionControlEvent event = new BranchResetEvent(
        command.dataset(),
        command.branchName(),
        branch.getCommitId().value(),
        command.toCommitId(),
        Instant.now());

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
}
