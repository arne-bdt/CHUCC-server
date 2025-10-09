package org.chucc.vcserver.command;

import java.util.concurrent.CompletableFuture;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CreateTagCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class CreateTagCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private CommitRepository commitRepository;

  private CreateTagCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new CreateTagCommandHandler(eventPublisher, commitRepository);
  }

  @Test
  void shouldProduceTagCreatedEvent() {
    // Given
    String validCommitId = "12345678-1234-1234-1234-123456789abc";
    CreateTagCommand command = new CreateTagCommand(
        "test-dataset",
        "v1.0",
        validCommitId,
        "Release 1.0");

    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(TagCreatedEvent.class, event.getClass());

    TagCreatedEvent tagEvent = (TagCreatedEvent) event;
    assertEquals("test-dataset", tagEvent.dataset());
    assertEquals("v1.0", tagEvent.tagName());
    assertEquals(validCommitId, tagEvent.commitId());
    assertNotNull(tagEvent.timestamp());
  }

  @Test
  void shouldRejectIfCommitDoesNotExist() {
    // Given
    String validCommitId = "87654321-4321-4321-4321-fedcba987654";
    CreateTagCommand command = new CreateTagCommand(
        "test-dataset",
        "v1.0",
        validCommitId,
        "Release 1.0");

    when(commitRepository.exists("test-dataset", new CommitId(validCommitId))).thenReturn(false);

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }
}
