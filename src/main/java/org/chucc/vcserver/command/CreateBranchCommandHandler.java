package org.chucc.vcserver.command;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchAlreadyExistsException;
import org.chucc.vcserver.exception.RefNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles CreateBranchCommand by validating and producing a BranchCreatedEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class CreateBranchCommandHandler implements CommandHandler<CreateBranchCommand> {

  private static final Logger logger = LoggerFactory.getLogger(CreateBranchCommandHandler.class);

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;
  private final EventPublisher eventPublisher;

  /**
   * Constructs a CreateBranchCommandHandler.
   *
   * @param branchRepository the branch repository
   * @param commitRepository the commit repository
   * @param eventPublisher the event publisher
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public CreateBranchCommandHandler(
      BranchRepository branchRepository,
      CommitRepository commitRepository,
      EventPublisher eventPublisher) {
    this.branchRepository = branchRepository;
    this.commitRepository = commitRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public VersionControlEvent handle(CreateBranchCommand command) {
    logger.info("Creating branch: {} from {} in dataset: {}",
        command.branchName(), command.sourceRef(), command.dataset());

    // Validate branch doesn't already exist
    if (branchRepository.exists(command.dataset(), command.branchName())) {
      throw new BranchAlreadyExistsException(command.branchName());
    }

    // Resolve source ref to commit ID
    String headCommitId = resolveSourceRef(command.dataset(), command.sourceRef());

    // Validate branch name (will be done by Branch constructor in projector,
    // but we can do early validation here)
    try {
      new Branch(command.branchName(), CommitId.of(headCommitId));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid branch name: " + e.getMessage(), e);
    }

    // Produce event
    VersionControlEvent event = new BranchCreatedEvent(
        command.dataset(),
        command.branchName(),
        headCommitId,
        command.sourceRef(),
        command.isProtected(),
        command.author(),
        Instant.now()
    );

    // Publish event to Kafka (async, with proper error logging)
    eventPublisher.publish(event)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event {} to Kafka: {}",
                event.getClass().getSimpleName(), ex.getMessage(), ex);
          } else {
            logger.debug("Successfully published event {} to Kafka",
                event.getClass().getSimpleName());
          }
        });

    logger.info("Branch {} created from {} (commit: {}) in dataset {}",
        command.branchName(), command.sourceRef(), headCommitId, command.dataset());

    return event;
  }

  /**
   * Resolves a source ref (branch name or commit ID) to a commit ID.
   *
   * @param dataset the dataset name
   * @param sourceRef the source ref
   * @return the resolved commit ID
   * @throws RefNotFoundException if the ref cannot be resolved
   */
  private String resolveSourceRef(String dataset, String sourceRef) {
    // Try as branch first
    var branch = branchRepository.findByDatasetAndName(dataset, sourceRef);
    if (branch.isPresent()) {
      return branch.get().getCommitId().value();
    }

    // Try as commit ID
    try {
      CommitId commitId = CommitId.of(sourceRef);
      if (commitRepository.exists(dataset, commitId)) {
        return sourceRef;
      }
    } catch (IllegalArgumentException e) {
      // Not a valid commit ID format, continue to throw RefNotFoundException
    }

    throw new RefNotFoundException("Source ref not found: " + sourceRef);
  }
}
