package org.chucc.vcserver.command;

import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchResetEvent;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for ResetBranchCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class ResetBranchCommandHandlerTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  private ResetBranchCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ResetBranchCommandHandler(branchRepository, commitRepository);
  }

  @Test
  void shouldProduceBranchResetEvent() {
    // Given
    String fromCommitId = "12345678-1234-1234-1234-111111111111";
    String toCommitId = "12345678-1234-1234-1234-222222222222";

    ResetBranchCommand command = new ResetBranchCommand(
        "test-dataset",
        "main",
        toCommitId);

    org.chucc.vcserver.domain.Branch branch = new org.chucc.vcserver.domain.Branch(
        "main", new org.chucc.vcserver.domain.CommitId(fromCommitId));

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(java.util.Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(BranchResetEvent.class, event.getClass());

    BranchResetEvent resetEvent = (BranchResetEvent) event;
    assertEquals("test-dataset", resetEvent.dataset());
    assertEquals("main", resetEvent.branchName());
    assertEquals(fromCommitId, resetEvent.fromCommitId());
    assertEquals(toCommitId, resetEvent.toCommitId());
    assertNotNull(resetEvent.timestamp());
  }

  @Test
  void shouldRejectIfBranchDoesNotExist() {
    // Given
    String toCommitId = "12345678-1234-1234-1234-222222222222";
    ResetBranchCommand command = new ResetBranchCommand(
        "test-dataset",
        "nonexistent-branch",
        toCommitId);

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent-branch"))
        .thenReturn(java.util.Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfTargetCommitDoesNotExist() {
    // Given
    String fromCommitId = "12345678-1234-1234-1234-111111111111";
    String toCommitId = "12345678-1234-1234-1234-999999999999";

    ResetBranchCommand command = new ResetBranchCommand(
        "test-dataset",
        "main",
        toCommitId);

    org.chucc.vcserver.domain.Branch branch = new org.chucc.vcserver.domain.Branch(
        "main", new org.chucc.vcserver.domain.CommitId(fromCommitId));

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(java.util.Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(false);

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }
}
