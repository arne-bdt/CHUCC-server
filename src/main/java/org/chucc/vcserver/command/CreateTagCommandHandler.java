package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.repository.TagRepository;
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
  private final TagRepository tagRepository;

  /**
   * Constructs a CreateTagCommandHandler.
   *
   * @param eventPublisher the event publisher
   * @param commitRepository the commit repository
   * @param tagRepository the tag repository
   */
  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Repositories are Spring-managed beans and are intentionally shared")
  public CreateTagCommandHandler(
      EventPublisher eventPublisher,
      CommitRepository commitRepository,
      TagRepository tagRepository) {
    this.eventPublisher = eventPublisher;
    this.commitRepository = commitRepository;
    this.tagRepository = tagRepository;
  }

  @Override
  public VersionControlEvent handle(CreateTagCommand command) {
    // Validate tag doesn't already exist (tags are immutable)
    if (tagRepository.exists(command.dataset(), command.tagName())) {
      throw new IllegalStateException(
          "Tag already exists: " + command.tagName()
              + " in dataset: " + command.dataset());
    }

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
        command.message(),
        command.author(),
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
