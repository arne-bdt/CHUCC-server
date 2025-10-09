package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles CreateTagCommand by validating the command and producing a TagCreatedEvent.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class CreateTagCommandHandler implements CommandHandler<CreateTagCommand> {

  private static final Logger logger = LoggerFactory.getLogger(CreateTagCommandHandler.class);

  private final EventPublisher eventPublisher;
  private final CommitRepository commitRepository;

  /**
   * Constructs a CreateTagCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param commitRepository the commit repository
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "CommitRepository is a Spring-managed bean and is intentionally shared")
  public CreateTagCommandHandler(
      EventPublisher eventPublisher,
      CommitRepository commitRepository) {
    this.eventPublisher = eventPublisher;
    this.commitRepository = commitRepository;
  }

  @Override
  public VersionControlEvent handle(CreateTagCommand command) {
    // Validate target commit exists
    CommitId commitId = new CommitId(command.commitId());
    if (!commitRepository.exists(command.dataset(), commitId)) {
      throw new IllegalArgumentException(
          "Commit not found: " + command.commitId()
              + " in dataset: " + command.dataset());
    }

    // Produce event
    VersionControlEvent event = new TagCreatedEvent(
        command.dataset(),
        command.tagName(),
        command.commitId(),
        Instant.now());

    // Publish event to Kafka (fire-and-forget, async)
    eventPublisher.publish(event)
        .exceptionally(ex -> {
          logger.error("Failed to publish event {}: {}",
              event.getClass().getSimpleName(), ex.getMessage(), ex);
          return null;
        });

    return event;
  }
}
