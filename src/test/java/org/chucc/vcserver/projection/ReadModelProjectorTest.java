package org.chucc.vcserver.projection;

import org.apache.jena.rdfpatch.RDFPatch;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.BranchCreatedEvent;
import org.chucc.vcserver.event.BranchResetEvent;
import org.chucc.vcserver.event.CommitCreatedEvent;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.chucc.vcserver.service.DatasetService;
import org.chucc.vcserver.service.SnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReadModelProjector.
 * Tests event handling and state tracking logic.
 */
@ExtendWith(MockitoExtension.class)
class ReadModelProjectorTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private DatasetService datasetService;

  @Mock
  private SnapshotService snapshotService;

  private ReadModelProjector projector;

  @BeforeEach
  void setUp() {
    projector = new ReadModelProjector(branchRepository, commitRepository, datasetService,
        snapshotService);
  }

  @Test
  void handleCommitCreatedEvent_shouldSaveCommitAndPatch() {
    // Given
    String dataset = "test-dataset";
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    String rdfPatchStr = "TX .";

    CommitCreatedEvent event = new CommitCreatedEvent(
        dataset,
        commitIdStr,
        List.of(), null,
        "Test commit",
        "test-author",
        Instant.now(),
        rdfPatchStr
    );

    // When
    projector.handleCommitCreated(event);

    // Then
    verify(commitRepository).save(eq(dataset), any(Commit.class), any(RDFPatch.class));
  }

  @Test
  void handleBranchCreatedEvent_shouldSaveBranch() {
    // Given
    String dataset = "test-dataset";
    String branchName = "feature-branch";
    String commitIdStr = "550e8400-e29b-41d4-a716-446655440000";

    BranchCreatedEvent event = new BranchCreatedEvent(
        dataset,
        branchName,
        commitIdStr,
        Instant.now()
    );

    // When
    projector.handleBranchCreated(event);

    // Then
    verify(branchRepository).save(eq(dataset), any(Branch.class));
  }

  @Test
  void handleBranchResetEvent_shouldUpdateBranchHead() {
    // Given
    String dataset = "test-dataset";
    String branchName = "main";
    String fromCommitIdStr = "550e8400-e29b-41d4-a716-446655440000";
    String toCommitIdStr = "660e8400-e29b-41d4-a716-446655440001";

    // Stub branch existence check
    Branch existingBranch = new Branch(branchName, CommitId.of(fromCommitIdStr));
    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.of(existingBranch));

    BranchResetEvent event = new BranchResetEvent(
        dataset,
        branchName,
        fromCommitIdStr,
        toCommitIdStr,
        Instant.now()
    );

    // When
    projector.handleBranchReset(event);

    // Then
    verify(branchRepository).updateBranchHead(
        eq(dataset),
        eq(branchName),
        eq(CommitId.of(toCommitIdStr))
    );
  }

  @Test
  void getProjectionState_shouldReturnCurrentState() {
    // Given
    String dataset = "test-dataset";
    String branchName = "main";
    CommitId commitId = CommitId.of("550e8400-e29b-41d4-a716-446655440000");
    Branch branch = new Branch(branchName, commitId);

    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.of(branch));

    // When
    Optional<CommitId> result = projector.getProjectionState(dataset, branchName);

    // Then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(commitId);
  }

  @Test
  void getProjectionState_shouldReturnEmptyWhenBranchNotFound() {
    // Given
    String dataset = "test-dataset";
    String branchName = "non-existent";

    when(branchRepository.findByDatasetAndName(dataset, branchName))
        .thenReturn(Optional.empty());

    // When
    Optional<CommitId> result = projector.getProjectionState(dataset, branchName);

    // Then
    assertThat(result).isEmpty();
  }
}
