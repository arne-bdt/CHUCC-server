package org.chucc.vcserver.command;

import java.util.concurrent.CompletableFuture;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
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
 * Unit tests for CreateBranchCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class CreateBranchCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  private CreateBranchCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future (lenient for tests that don't publish)
    lenient().when(eventPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));

    handler = new CreateBranchCommandHandler(eventPublisher, branchRepository, commitRepository);
  }

  @Test
  void shouldProduceBranchCreatedEvent() {
    // Given
    String validCommitId = "12345678-1234-1234-1234-123456789abc";
    CreateBranchCommand command = new CreateBranchCommand(
        "test-dataset",
        "feature-branch",
        validCommitId);

    when(branchRepository.exists("test-dataset", "feature-branch")).thenReturn(false);
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(BranchCreatedEvent.class, event.getClass());

    BranchCreatedEvent branchEvent = (BranchCreatedEvent) event;
    assertEquals("test-dataset", branchEvent.dataset());
    assertEquals("feature-branch", branchEvent.branchName());
    assertEquals(validCommitId, branchEvent.commitId());
    assertNotNull(branchEvent.timestamp());
  }

  @Test
  void shouldRejectIfBranchAlreadyExists() {
    // Given
    CreateBranchCommand command = new CreateBranchCommand(
        "test-dataset",
        "existing-branch",
        "commit-123");

    when(branchRepository.exists("test-dataset", "existing-branch")).thenReturn(true);

    // When/Then
    assertThrows(IllegalStateException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfSourceCommitDoesNotExist() {
    // Given
    String validCommitId = "12345678-1234-1234-1234-123456789abc";
    CreateBranchCommand command = new CreateBranchCommand(
        "test-dataset",
        "new-branch",
        validCommitId);

    when(branchRepository.exists("test-dataset", "new-branch")).thenReturn(false);
    when(commitRepository.exists("test-dataset", new CommitId(validCommitId))).thenReturn(false);

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }
}
