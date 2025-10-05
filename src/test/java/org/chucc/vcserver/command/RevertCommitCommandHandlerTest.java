package org.chucc.vcserver.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.RevertCreatedEvent;
import org.chucc.vcserver.event.VersionControlEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for RevertCommitCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class RevertCommitCommandHandlerTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  private RevertCommitCommandHandler handler;

  @BeforeEach
  void setUp() {
    handler = new RevertCommitCommandHandler(branchRepository, commitRepository);
  }

  @Test
  void shouldProduceRevertCreatedEvent() {
    // Given
    String branchHeadId = "12345678-1234-1234-1234-111111111111";
    String commitToRevert = "12345678-1234-1234-1234-222222222222";

    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        commitToRevert,
        null,
        "bob");

    CommitId branchHead = new CommitId(branchHeadId);
    Branch branch = new Branch("main", branchHead);
    RDFPatch originalPatch = RDFPatchOps.emptyPatch();

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.of(originalPatch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    assertNotNull(event);
    assertEquals(RevertCreatedEvent.class, event.getClass());

    RevertCreatedEvent revertEvent = (RevertCreatedEvent) event;
    assertEquals("test-dataset", revertEvent.dataset());
    assertNotNull(revertEvent.revertCommitId());
    assertTrue(revertEvent.message().contains(commitToRevert));
    assertEquals("bob", revertEvent.author());
    assertEquals(commitToRevert, revertEvent.revertedCommitId());
    assertNotNull(revertEvent.rdfPatch());
    assertNotNull(revertEvent.timestamp());
  }

  @Test
  void shouldUseCustomMessageWhenProvided() {
    // Given
    String branchHeadId = "12345678-1234-1234-1234-111111111111";
    String commitToRevert = "12345678-1234-1234-1234-222222222222";

    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        commitToRevert,
        "Custom revert message",
        "bob");

    CommitId branchHead = new CommitId(branchHeadId);
    Branch branch = new Branch("main", branchHead);
    RDFPatch originalPatch = RDFPatchOps.emptyPatch();

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.of(originalPatch));

    // When
    VersionControlEvent event = handler.handle(command);

    // Then
    RevertCreatedEvent revertEvent = (RevertCreatedEvent) event;
    assertEquals("Custom revert message", revertEvent.message());
  }

  @Test
  void shouldRejectIfBranchDoesNotExist() {
    // Given
    String commitToRevert = "12345678-1234-1234-1234-222222222222";

    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "nonexistent-branch",
        commitToRevert,
        null,
        "bob");

    when(branchRepository.findByDatasetAndName("test-dataset", "nonexistent-branch"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfCommitDoesNotExist() {
    // Given
    String branchHeadId = "12345678-1234-1234-1234-111111111111";
    String commitToRevert = "12345678-1234-1234-1234-999999999999";

    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        commitToRevert,
        null,
        "bob");

    CommitId branchHead = new CommitId(branchHeadId);
    Branch branch = new Branch("main", branchHead);

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(false);

    // When/Then
    assertThrows(IllegalArgumentException.class, () -> handler.handle(command));
  }

  @Test
  void shouldRejectIfPatchNotFound() {
    // Given
    String branchHeadId = "12345678-1234-1234-1234-111111111111";
    String commitToRevert = "12345678-1234-1234-1234-222222222222";

    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        commitToRevert,
        null,
        "bob");

    CommitId branchHead = new CommitId(branchHeadId);
    Branch branch = new Branch("main", branchHead);

    when(branchRepository.findByDatasetAndName("test-dataset", "main"))
        .thenReturn(Optional.of(branch));
    when(commitRepository.exists(eq("test-dataset"), any(CommitId.class))).thenReturn(true);
    when(commitRepository.findPatchByDatasetAndId(eq("test-dataset"), any(CommitId.class)))
        .thenReturn(Optional.empty());

    // When/Then
    assertThrows(IllegalStateException.class, () -> handler.handle(command));
  }
}
