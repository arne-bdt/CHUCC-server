package org.chucc.vcserver.command;

import java.time.Instant;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.stereotype.Component;

/**
 * Handles CreateTagCommand by validating the command and producing a TagCreatedEvent.
 */
@Component
public class CreateTagCommandHandler implements CommandHandler<CreateTagCommand> {

  private final CommitRepository commitRepository;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "CommitRepository is a Spring-managed bean and is intentionally shared")
  public CreateTagCommandHandler(CommitRepository commitRepository) {
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
    return new TagCreatedEvent(
        command.dataset(),
        command.tagName(),
        command.commitId(),
        Instant.now());
  }
}
