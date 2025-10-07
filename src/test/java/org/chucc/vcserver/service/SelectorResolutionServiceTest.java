package org.chucc.vcserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SelectorResolutionService.
 */
@ExtendWith(MockitoExtension.class)
class SelectorResolutionServiceTest {

  private static final String DATASET_NAME = "testDataset";
  private static final String BRANCH_NAME = "feature";
  private static final String DEFAULT_BRANCH = "main";

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private TimestampResolutionService timestampResolutionService;

  @InjectMocks
  private SelectorResolutionService selectorResolutionService;

  private CommitId mainHeadId;
  private CommitId featureHeadId;
  private CommitId directCommitId;
  private CommitId asOfCommitId;
  private Branch mainBranch;
  private Branch featureBranch;

  /**
   * Set up test data.
   */
  @BeforeEach
  void setUp() {
    mainHeadId = CommitId.generate();
    featureHeadId = CommitId.generate();
    directCommitId = CommitId.generate();
    asOfCommitId = CommitId.generate();

    mainBranch = new Branch(DEFAULT_BRANCH, mainHeadId);
    featureBranch = new Branch(BRANCH_NAME, featureHeadId);
  }

  /**
   * Test resolving with direct commit selector.
   */
  @Test
  void testResolve_withCommitSelector_returnsCommitId() {
    String commitValue = directCommitId.value();

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, null, commitValue, null);

    assertEquals(directCommitId, result);
  }

  /**
   * Test resolving with branch selector returns branch HEAD.
   */
  @Test
  void testResolve_withBranchSelector_returnsBranchHead() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(featureBranch));

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, BRANCH_NAME, null, null);

    assertEquals(featureHeadId, result);
  }

  /**
   * Test resolving with asOf selector delegates to TimestampResolutionService.
   */
  @Test
  void testResolve_withAsOfOnly_delegatesToTimestampResolution() {
    String asOf = "2025-01-15T10:30:00Z";
    when(timestampResolutionService.resolveAsOf(DATASET_NAME, null, asOf))
        .thenReturn(asOfCommitId);

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, null, null, asOf);

    assertEquals(asOfCommitId, result);
    verify(timestampResolutionService).resolveAsOf(DATASET_NAME, null, asOf);
  }

  /**
   * Test resolving with branch + asOf delegates to TimestampResolutionService.
   */
  @Test
  void testResolve_withBranchAndAsOf_delegatesToTimestampResolution() {
    String asOf = "2025-01-15T10:30:00Z";
    when(timestampResolutionService.resolveAsOf(DATASET_NAME, BRANCH_NAME, asOf))
        .thenReturn(asOfCommitId);

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, BRANCH_NAME, null, asOf);

    assertEquals(asOfCommitId, result);
    verify(timestampResolutionService).resolveAsOf(DATASET_NAME, BRANCH_NAME, asOf);
  }

  /**
   * Test resolving with no selectors returns default branch HEAD.
   */
  @Test
  void testResolve_withNoSelectors_returnsDefaultBranchHead() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, DEFAULT_BRANCH))
        .thenReturn(Optional.of(mainBranch));

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, null, null, null);

    assertEquals(mainHeadId, result);
  }

  /**
   * Test resolving when branch does not exist.
   */
  @Test
  void testResolve_branchNotFound_throwsBranchNotFoundException() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.empty());

    assertThrows(
        BranchNotFoundException.class,
        () -> selectorResolutionService.resolve(DATASET_NAME, BRANCH_NAME, null, null)
    );
  }

  /**
   * Test resolving when default branch does not exist.
   */
  @Test
  void testResolve_defaultBranchNotFound_throwsIllegalStateException() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, DEFAULT_BRANCH))
        .thenReturn(Optional.empty());

    assertThrows(
        IllegalStateException.class,
        () -> selectorResolutionService.resolve(DATASET_NAME, null, null, null)
    );
  }

  /**
   * Test that commit selector takes precedence over other selectors.
   */
  @Test
  void testResolve_commitSelectorTakesPrecedence_ignoresOtherSelectors() {
    // Even if branch and asOf are provided, commit should take precedence
    // (though validation should prevent this combination)
    String commitValue = directCommitId.value();

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, BRANCH_NAME, commitValue, "2025-01-15T10:30:00Z");

    assertEquals(directCommitId, result);
  }

  /**
   * Test resolving with blank branch selector treated as null.
   */
  @Test
  void testResolve_withBlankBranch_treatedAsNull() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, DEFAULT_BRANCH))
        .thenReturn(Optional.of(mainBranch));

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, "", null, null);

    assertEquals(mainHeadId, result);
  }

  /**
   * Test resolving with blank commit selector treated as null.
   */
  @Test
  void testResolve_withBlankCommit_treatedAsNull() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(featureBranch));

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, BRANCH_NAME, "", null);

    assertEquals(featureHeadId, result);
  }

  /**
   * Test resolving with blank asOf selector treated as null.
   */
  @Test
  void testResolve_withBlankAsOf_treatedAsNull() {
    when(branchRepository.findByDatasetAndName(DATASET_NAME, BRANCH_NAME))
        .thenReturn(Optional.of(featureBranch));

    CommitId result = selectorResolutionService.resolve(
        DATASET_NAME, BRANCH_NAME, null, "");

    assertEquals(featureHeadId, result);
  }
}
