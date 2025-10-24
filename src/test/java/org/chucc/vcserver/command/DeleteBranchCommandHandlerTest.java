package org.chucc.vcserver.command;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchDeletedEvent;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.ProtectedBranchException;
import org.chucc.vcserver.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeleteBranchCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class DeleteBranchCommandHandlerTest {

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BranchRepository branchRepository;

  private DeleteBranchCommandHandler handler;

  @BeforeEach
  void setUp() {
    // Mock EventPublisher.publish() to return completed future
    lenient().when(eventPublisher.publish(any()))
        .thenReturn(CompletableFuture.completedFuture(null));

    handler = new DeleteBranchCommandHandler(eventPublisher, branchRepository);
  }

  @Test
  void shouldProduceBranchDeletedEvent() {
    // Given
    String commitId = "12345678-1234-1234-1234-123456789abc";
    Branch branch = new Branch("feature-x", new CommitId(commitId));
    DeleteBranchCommand command = new DeleteBranchCommand(
        "test-dataset",
        "feature-x",
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "feature-x"))
        .thenReturn(Optional.of(branch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(BranchDeletedEvent.class, event.getClass());

    BranchDeletedEvent deletedEvent = (BranchDeletedEvent) event;
    assertEquals("test-dataset", deletedEvent.dataset());
    assertEquals("feature-x", deletedEvent.branchName());
    assertEquals(commitId, deletedEvent.lastCommitId());
    assertEquals("alice", deletedEvent.author());
    assertNotNull(deletedEvent.timestamp());
  }

  @Test
  void shouldRejectDeletionOfMainBranch() {
    // Given
    String commitId = "12345678-1234-1234-1234-123456789abc";
    // Create protected main branch using full constructor
    Branch mainBranch = new Branch(
        "main",
        new CommitId(commitId),
        true,  // protected
        java.time.Instant.now(),
        java.time.Instant.now(),
        1);
    DeleteBranchCommand command = new DeleteBranchCommand(
        "test-dataset",
        "main",
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(mainBranch));

    // When/Then
    ProtectedBranchException exception = assertThrows(
        ProtectedBranchException.class,
        () -> handler.handle(command));

    assertEquals("Cannot delete protected branch: main", exception.getMessage());
  }

  @Test
  void shouldRejectDeletionOfNonExistentBranch() {
    // Given
    DeleteBranchCommand command = new DeleteBranchCommand(
        "test-dataset",
        "non-existent",
        "alice");

    when(branchRepository.findByDatasetAndName("test-dataset", "non-existent"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(BranchNotFoundException.class, () -> handler.handle(command));
  }

  @Test
  void shouldAllowDeletionOfNonMainBranch() {
    // Given
    String commitId = "98765432-4321-4321-4321-987654321abc";
    Branch branch = new Branch("develop", new CommitId(commitId));
    DeleteBranchCommand command = new DeleteBranchCommand(
        "test-dataset",
        "develop",
        "bob");

    when(branchRepository.findByDatasetAndName("test-dataset", "develop"))
        .thenReturn(Optional.of(branch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    BranchDeletedEvent deletedEvent = (BranchDeletedEvent) event;
    assertEquals("develop", deletedEvent.branchName());
    assertEquals("bob", deletedEvent.author());
  }
}
