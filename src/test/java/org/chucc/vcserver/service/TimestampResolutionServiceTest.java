package org.chucc.vcserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TimestampResolutionService.
 */
@ExtendWith(MockitoExtension.class)
class TimestampResolutionServiceTest {

  private static final String DATASET_NAME = "testDataset";
  private static final String BRANCH_NAME = "main";

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private BranchRepository branchRepository;

  @InjectMocks
  private TimestampResolutionService timestampResolutionService;

  private CommitId commit1Id;
  private CommitId commit2Id;
  private CommitId commit3Id;
  private Commit commit1;
  private Commit commit2;
  private Commit commit3;
  private Branch branch;

  /**
   * Set up test data.
   */
  @BeforeEach
  void setUp() {
    commit1Id = CommitId.generate();
    commit2Id = CommitId.generate();
    commit3Id = CommitId.generate();

    // Create commits with different timestamps
    // commit1: 2025-01-01T00:00:00Z
    // commit2: 2025-01-02T00:00:00Z (parent: commit1)
    // commit3: 2025-01-03T00:00:00Z (parent: commit2)
    commit1 = new Commit(
        commit1Id,
        List.of(),
        "author",
        "Initial commit",
        Instant.parse("2025-01-01T00:00:00Z"),
        0
    );

    commit2 = new Commit(
        commit2Id,
        List.of(commit1Id),
        "author",
        "Second commit",
        Instant.parse("2025-01-02T00:00:00Z"),
        0
    );

    commit3 = new Commit(
        commit3Id,
        List.of(commit2Id),
        "author",
        "Third commit",
        Instant.parse("2025-01-03T00:00:00Z"),
        0
    );

    branch = new Branch(BRANCH_NAME, commit3Id);
  }

  /**
   * Test resolving asOf with valid RFC 3339 timestamp.
   */
  @Test
  void testResolveAsOf_withValidTimestamp_parsesSuccessfully() {
    String asOf = "2025-01-02T12:00:00Z";
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit2Id))
        .thenReturn(Optional.of(commit2));

    CommitId result = timestampResolutionService.resolveAsOf(
        DATASET_NAME, BRANCH_NAME, asOf);

    assertEquals(commit2Id, result);
  }

  /**
   * Test resolving asOf with invalid timestamp format.
   */
  @Test
  void testResolveAsOf_withInvalidTimestamp_throwsIllegalArgumentException() {
    String invalidAsOf = "not-a-timestamp";

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> timestampResolutionService.resolveAsOf(DATASET_NAME, BRANCH_NAME, invalidAsOf)
    );

    assertEquals("Invalid RFC 3339 timestamp: not-a-timestamp", exception.getMessage());
  }

  /**
   * Test resolving asOf on branch when commit exists at exact timestamp.
   */
  @Test
  void testResolveAsOfOnBranch_exactTimestamp_returnsCommit() {
    String asOf = "2025-01-02T00:00:00Z";
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit2Id))
        .thenReturn(Optional.of(commit2));

    CommitId result = timestampResolutionService.resolveAsOf(
        DATASET_NAME, BRANCH_NAME, asOf);

    assertEquals(commit2Id, result);
  }

  /**
   * Test resolving asOf on branch when commit is before timestamp.
   */
  @Test
  void testResolveAsOfOnBranch_timestampBetweenCommits_returnsMostRecent() {
    String asOf = "2025-01-02T12:00:00Z"; // Between commit2 and commit3
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit2Id))
        .thenReturn(Optional.of(commit2));

    CommitId result = timestampResolutionService.resolveAsOf(
        DATASET_NAME, BRANCH_NAME, asOf);

    assertEquals(commit2Id, result);
  }

  /**
   * Test resolving asOf on branch when timestamp is after all commits.
   */
  @Test
  void testResolveAsOfOnBranch_timestampAfterAllCommits_returnsLatestCommit() {
    String asOf = "2025-01-05T00:00:00Z"; // After all commits
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));

    CommitId result = timestampResolutionService.resolveAsOf(
        DATASET_NAME, BRANCH_NAME, asOf);

    assertEquals(commit3Id, result);
  }

  /**
   * Test resolving asOf on branch when no commits exist before timestamp.
   */
  @Test
  void testResolveAsOfOnBranch_timestampBeforeAllCommits_throwsCommitNotFoundException() {
    String asOf = "2024-12-31T00:00:00Z"; // Before all commits
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit2Id))
        .thenReturn(Optional.of(commit2));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit1Id))
        .thenReturn(Optional.of(commit1));

    assertThrows(
        CommitNotFoundException.class,
        () -> timestampResolutionService.resolveAsOf(DATASET_NAME, BRANCH_NAME, asOf)
    );
  }

  /**
   * Test resolving asOf when branch does not exist.
   */
  @Test
  void testResolveAsOfOnBranch_branchNotFound_throwsBranchNotFoundException() {
    String asOf = "2025-01-02T00:00:00Z";
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.empty());

    assertThrows(
        BranchNotFoundException.class,
        () -> timestampResolutionService.resolveAsOf(DATASET_NAME, BRANCH_NAME, asOf)
    );
  }

  /**
   * Test resolving asOf globally (without branch) when commit exists.
   */
  @Test
  void testResolveAsOfGlobal_commitExists_returnsCommit() {
    String asOf = "2025-01-02T00:00:00Z";
    when(commitRepository.findLatestBeforeTimestamp(
        DATASET_NAME, Instant.parse(asOf)))
        .thenReturn(Optional.of(commit2));

    CommitId result = timestampResolutionService.resolveAsOf(DATASET_NAME, null, asOf);

    assertEquals(commit2Id, result);
  }

  /**
   * Test resolving asOf globally when no commit exists before timestamp.
   */
  @Test
  void testResolveAsOfGlobal_noCommitFound_throwsCommitNotFoundException() {
    String asOf = "2024-12-31T00:00:00Z";
    when(commitRepository.findLatestBeforeTimestamp(
        DATASET_NAME, Instant.parse(asOf)))
        .thenReturn(Optional.empty());

    assertThrows(
        CommitNotFoundException.class,
        () -> timestampResolutionService.resolveAsOf(DATASET_NAME, null, asOf)
    );
  }

  /**
   * Test resolving asOf on branch when commit chain is broken.
   */
  @Test
  void testResolveAsOfOnBranch_brokenCommitChain_throwsCommitNotFoundException() {
    String asOf = "2025-01-02T00:00:00Z";
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(branch));
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit3Id))
        .thenReturn(Optional.of(commit3));
    // commit2 is missing from repository
    when(commitRepository.findByDatasetAndId(DATASET_NAME, commit2Id))
        .thenReturn(Optional.empty());

    assertThrows(
        CommitNotFoundException.class,
        () -> timestampResolutionService.resolveAsOf(DATASET_NAME, BRANCH_NAME, asOf)
    );
  }
}
