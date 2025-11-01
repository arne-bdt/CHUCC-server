package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.HistoryResponse;
import org.chucc.vcserver.exception.BranchNotFoundException;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for HistoryService.
 * Tests filtering, pagination, and branch traversal logic.
 */
@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

  @Mock
  private CommitRepository commitRepository;

  @Mock
  private BranchRepository branchRepository;

  private HistoryService historyService;

  private static final String DATASET = "test-dataset";

  @BeforeEach
  void setUp() {
    historyService = new HistoryService(commitRepository, branchRepository);
  }

  @Test
  void listHistory_shouldReturnAllCommits_sortedByTimestampDescending() {
    // Given
    Instant now = Instant.now();
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Commit 1", now.minus(2, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Commit 2", now.minus(1, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit2Id),
        "Charlie", "Commit 3", now, 20);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, null, null, null, null, 100, 0);

    // Then
    assertThat(response.commits()).hasSize(3);
    // Verify sorted by timestamp descending (newest first)
    assertThat(response.commits().get(0).id()).isEqualTo(commit3Id.value());
    assertThat(response.commits().get(1).id()).isEqualTo(commit2Id.value());
    assertThat(response.commits().get(2).id()).isEqualTo(commit1Id.value());

    assertThat(response.pagination().limit()).isEqualTo(100);
    assertThat(response.pagination().offset()).isEqualTo(0);
    assertThat(response.pagination().hasMore()).isFalse();
  }

  @Test
  void listHistory_withBranchFilter_shouldReturnOnlyReachableCommits() {
    // Given: Commit graph:
    //   main: commit1 -> commit2 -> commit3
    //   feature: commit1 -> commit4
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();
    CommitId commit4Id = CommitId.generate();

    Instant now = Instant.now();
    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Initial", now.minus(3, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Main commit", now.minus(2, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit2Id),
        "Charlie", "Another main", now.minus(1, ChronoUnit.HOURS), 20);
    Commit commit4 = new Commit(commit4Id, List.of(commit1Id),
        "Dave", "Feature commit", now, 25);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3, commit4));
    when(branchRepository.findByDatasetAndName(DATASET, "main"))
        .thenReturn(Optional.of(new Branch("main", commit3Id)));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, "main", null, null, null, 100, 0);

    // Then: Should only return commits reachable from main (1, 2, 3)
    assertThat(response.commits()).hasSize(3);
    assertThat(response.commits().get(0).id()).isEqualTo(commit3Id.value());
    assertThat(response.commits().get(1).id()).isEqualTo(commit2Id.value());
    assertThat(response.commits().get(2).id()).isEqualTo(commit1Id.value());
  }

  @Test
  void listHistory_withNonExistentBranch_shouldThrowException() {
    // Given
    when(commitRepository.findAllByDataset(DATASET)).thenReturn(List.of());
    when(branchRepository.findByDatasetAndName(DATASET, "non-existent"))
        .thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> historyService.listHistory(
        DATASET, "non-existent", null, null, null, 100, 0))
        .isInstanceOf(BranchNotFoundException.class)
        .hasMessageContaining("non-existent");
  }

  @Test
  void listHistory_withSinceFilter_shouldFilterCommitsAfterTimestamp() {
    // Given
    Instant now = Instant.now();
    Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Old commit", now.minus(3, ChronoUnit.DAYS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Recent commit 1", now.minus(1, ChronoUnit.DAYS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit2Id),
        "Charlie", "Recent commit 2", now, 20);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, null, twoDaysAgo, null, null, 100, 0);

    // Then: Should only return commits since twoDaysAgo (commit2, commit3)
    assertThat(response.commits()).hasSize(2);
    assertThat(response.commits().get(0).id()).isEqualTo(commit3Id.value());
    assertThat(response.commits().get(1).id()).isEqualTo(commit2Id.value());
  }

  @Test
  void listHistory_withUntilFilter_shouldFilterCommitsBeforeTimestamp() {
    // Given
    Instant now = Instant.now();
    Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Old commit 1", now.minus(3, ChronoUnit.DAYS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Old commit 2", now.minus(2, ChronoUnit.DAYS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit2Id),
        "Charlie", "Recent commit", now, 20);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, null, null, oneDayAgo, null, 100, 0);

    // Then: Should only return commits until oneDayAgo (commit1, commit2)
    assertThat(response.commits()).hasSize(2);
    assertThat(response.commits().get(0).id()).isEqualTo(commit2Id.value());
    assertThat(response.commits().get(1).id()).isEqualTo(commit1Id.value());
  }

  @Test
  void listHistory_withAuthorFilter_shouldFilterCommitsByAuthor() {
    // Given
    Instant now = Instant.now();
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();

    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Alice's commit 1", now.minus(2, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Bob's commit", now.minus(1, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit2Id),
        "Alice", "Alice's commit 2", now, 20);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, null, null, null, "Alice", 100, 0);

    // Then: Should only return Alice's commits
    assertThat(response.commits()).hasSize(2);
    assertThat(response.commits().get(0).id()).isEqualTo(commit3Id.value());
    assertThat(response.commits().get(1).id()).isEqualTo(commit1Id.value());
    response.commits().forEach(commit ->
        assertThat(commit.author()).isEqualTo("Alice")
    );
  }

  @Test
  void listHistory_withPagination_shouldReturnCorrectPage() {
    // Given: 10 commits
    Instant now = Instant.now();
    List<Commit> commits = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) {
      CommitId commitId = CommitId.generate();
      Commit commit = new Commit(commitId, List.of(),
          "Author", "Commit " + i, now.minus(10 - i, ChronoUnit.HOURS), 10);
      commits.add(commit);
    }

    when(commitRepository.findAllByDataset(DATASET)).thenReturn(commits);

    // When: Request first page (limit=5, offset=0)
    HistoryResponse response1 = historyService.listHistory(
        DATASET, null, null, null, null, 5, 0);

    // Then: Should return first 5 commits with hasMore=true
    assertThat(response1.commits()).hasSize(5);
    assertThat(response1.pagination().hasMore()).isTrue();
    assertThat(response1.pagination().limit()).isEqualTo(5);
    assertThat(response1.pagination().offset()).isEqualTo(0);

    // When: Request second page (limit=5, offset=5)
    HistoryResponse response2 = historyService.listHistory(
        DATASET, null, null, null, null, 5, 5);

    // Then: Should return next 5 commits with hasMore=false
    assertThat(response2.commits()).hasSize(5);
    assertThat(response2.pagination().hasMore()).isFalse();
    assertThat(response2.pagination().limit()).isEqualTo(5);
    assertThat(response2.pagination().offset()).isEqualTo(5);
  }

  @Test
  void listHistory_emptyDataset_shouldReturnEmptyList() {
    // Given
    when(commitRepository.findAllByDataset(DATASET)).thenReturn(List.of());

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, null, null, null, null, 100, 0);

    // Then
    assertThat(response.commits()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  @Test
  void listHistory_withMergeCommit_shouldFollowAllParents() {
    // Given: Merge commit with two parents
    //   commit1 -> commit2 (main)
    //           -> commit3 (feature)
    //   commit4 (merge of commit2 and commit3)
    CommitId commit1Id = CommitId.generate();
    CommitId commit2Id = CommitId.generate();
    CommitId commit3Id = CommitId.generate();
    CommitId commit4Id = CommitId.generate();

    Instant now = Instant.now();
    Commit commit1 = new Commit(commit1Id, List.of(),
        "Alice", "Initial", now.minus(4, ChronoUnit.HOURS), 10);
    Commit commit2 = new Commit(commit2Id, List.of(commit1Id),
        "Bob", "Main", now.minus(3, ChronoUnit.HOURS), 15);
    Commit commit3 = new Commit(commit3Id, List.of(commit1Id),
        "Charlie", "Feature", now.minus(2, ChronoUnit.HOURS), 20);
    Commit commit4 = new Commit(commit4Id, List.of(commit2Id, commit3Id),
        "Dave", "Merge", now, 25);

    when(commitRepository.findAllByDataset(DATASET))
        .thenReturn(List.of(commit1, commit2, commit3, commit4));
    when(branchRepository.findByDatasetAndName(DATASET, "main"))
        .thenReturn(Optional.of(new Branch("main", commit4Id)));

    // When
    HistoryResponse response = historyService.listHistory(
        DATASET, "main", null, null, null, 100, 0);

    // Then: Should include all commits (merge follows both parent paths)
    assertThat(response.commits()).hasSize(4);
    assertThat(response.commits().get(0).id()).isEqualTo(commit4Id.value());
  }
}
