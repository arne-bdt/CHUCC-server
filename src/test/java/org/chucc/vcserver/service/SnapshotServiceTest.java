package org.chucc.vcserver.service;

import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.EventPublisher;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SnapshotService.
 * Tests only the commit counting logic.
 * Kafka snapshot retrieval is tested in integration tests.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

  @Mock
  private DatasetService datasetService;

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private VersionControlProperties vcProperties;

  @Mock
  private SnapshotKafkaStore kafkaStore;

  private SnapshotService snapshotService;

  private static final String DATASET = "test-dataset";
  private static final String BRANCH = "main";

  @BeforeEach
  void setUp() {
    snapshotService = new SnapshotService(
        datasetService,
        branchRepository,
        commitRepository,
        eventPublisher,
        vcProperties,
        kafkaStore
    );
  }

  @Test
  void shouldIncrementCommitCountWhenRecordingCommit() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);
    CommitId commitId = CommitId.generate();

    // When
    snapshotService.recordCommit(DATASET, BRANCH, commitId);

    // Then
    assertThat(snapshotService.getCommitCount(DATASET, BRANCH)).isEqualTo(1);
  }

  @Test
  void shouldNotIncrementCountWhenSnapshotsDisabled() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(false);
    CommitId commitId = CommitId.generate();

    // When
    snapshotService.recordCommit(DATASET, BRANCH, commitId);

    // Then
    assertThat(snapshotService.getCommitCount(DATASET, BRANCH)).isEqualTo(0);
  }

  @Test
  void shouldNotTriggerSnapshotBeforeInterval() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);

    // When - record 99 commits
    for (int i = 0; i < 99; i++) {
      snapshotService.recordCommit(DATASET, BRANCH, CommitId.generate());
    }

    // Then - should not create snapshot yet
    assertThat(snapshotService.getCommitCount(DATASET, BRANCH)).isEqualTo(99);
    // Note: We can't verify async method wasn't called in unit test
  }

  @Test
  void shouldResetCounterWhenRequested() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);
    snapshotService.recordCommit(DATASET, BRANCH, CommitId.generate());
    snapshotService.recordCommit(DATASET, BRANCH, CommitId.generate());

    // When
    snapshotService.resetCounter(DATASET, BRANCH);

    // Then
    assertThat(snapshotService.getCommitCount(DATASET, BRANCH)).isEqualTo(0);
  }

  @Test
  void shouldClearAllCounters() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);
    snapshotService.recordCommit(DATASET, "branch1", CommitId.generate());
    snapshotService.recordCommit(DATASET, "branch2", CommitId.generate());
    snapshotService.recordCommit("other-dataset", "main", CommitId.generate());

    // When
    snapshotService.clearAllCounters();

    // Then
    assertThat(snapshotService.getCommitCount(DATASET, "branch1")).isEqualTo(0);
    assertThat(snapshotService.getCommitCount(DATASET, "branch2")).isEqualTo(0);
    assertThat(snapshotService.getCommitCount("other-dataset", "main")).isEqualTo(0);
  }

  @Test
  void shouldHandleMultipleBranchesIndependently() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);

    // When
    snapshotService.recordCommit(DATASET, "branch1", CommitId.generate());
    snapshotService.recordCommit(DATASET, "branch1", CommitId.generate());
    snapshotService.recordCommit(DATASET, "branch2", CommitId.generate());

    // Then
    assertThat(snapshotService.getCommitCount(DATASET, "branch1")).isEqualTo(2);
    assertThat(snapshotService.getCommitCount(DATASET, "branch2")).isEqualTo(1);
  }

  @Test
  void shouldHandleMultipleDatasetsIndependently() {
    // Given
    when(vcProperties.isSnapshotsEnabled()).thenReturn(true);
    when(vcProperties.getSnapshotInterval()).thenReturn(100);

    // When
    snapshotService.recordCommit("dataset1", BRANCH, CommitId.generate());
    snapshotService.recordCommit("dataset1", BRANCH, CommitId.generate());
    snapshotService.recordCommit("dataset2", BRANCH, CommitId.generate());

    // Then
    assertThat(snapshotService.getCommitCount("dataset1", BRANCH)).isEqualTo(2);
    assertThat(snapshotService.getCommitCount("dataset2", BRANCH)).isEqualTo(1);
  }

  @Test
  void shouldReturnZeroForNonExistentBranch() {
    // When/Then
    assertThat(snapshotService.getCommitCount(DATASET, "nonexistent")).isEqualTo(0);
  }
}
