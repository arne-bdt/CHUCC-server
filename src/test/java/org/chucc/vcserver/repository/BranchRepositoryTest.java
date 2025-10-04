package org.chucc.vcserver.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BranchRepositoryTest {
  private BranchRepository repository;
  private static final String DATASET_NAME = "test-dataset";

  @BeforeEach
  void setUp() {
    repository = new BranchRepository();
  }

  @Test
  void shouldSaveAndFindBranch() {
    // Given
    CommitId commitId = CommitId.generate();
    Branch branch = new Branch("main", commitId);

    // When
    repository.save(DATASET_NAME, branch);

    // Then
    Optional<Branch> found = repository.findByDatasetAndName(DATASET_NAME, "main");
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("main");
    assertThat(found.get().getCommitId()).isEqualTo(commitId);
  }

  @Test
  void shouldReturnEmptyWhenBranchNotFound() {
    // When
    Optional<Branch> found = repository.findByDatasetAndName(DATASET_NAME, "nonexistent");

    // Then
    assertThat(found).isEmpty();
  }

  @Test
  void shouldFindAllBranchesInDataset() {
    // Given
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    repository.save(DATASET_NAME, new Branch("main", commit1));
    repository.save(DATASET_NAME, new Branch("develop", commit2));

    // When
    List<Branch> branches = repository.findAllByDataset(DATASET_NAME);

    // Then
    assertThat(branches).hasSize(2);
    assertThat(branches).extracting(Branch::getName)
        .containsExactlyInAnyOrder("main", "develop");
  }

  @Test
  void shouldReturnEmptyListWhenNoDataset() {
    // When
    List<Branch> branches = repository.findAllByDataset("nonexistent");

    // Then
    assertThat(branches).isEmpty();
  }

  @Test
  void shouldUpdateExistingBranch() {
    // Given
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    Branch branch = new Branch("main", commit1);
    repository.save(DATASET_NAME, branch);

    // When
    Branch updated = new Branch("main", commit2);
    repository.save(DATASET_NAME, updated);

    // Then
    Optional<Branch> found = repository.findByDatasetAndName(DATASET_NAME, "main");
    assertThat(found).isPresent();
    assertThat(found.get().getCommitId()).isEqualTo(commit2);
  }

  @Test
  void shouldDeleteBranch() {
    // Given
    CommitId commitId = CommitId.generate();
    Branch branch = new Branch("main", commitId);
    repository.save(DATASET_NAME, branch);

    // When
    boolean deleted = repository.delete(DATASET_NAME, "main");

    // Then
    assertThat(deleted).isTrue();
    assertThat(repository.findByDatasetAndName(DATASET_NAME, "main")).isEmpty();
  }

  @Test
  void shouldReturnFalseWhenDeletingNonexistentBranch() {
    // When
    boolean deleted = repository.delete(DATASET_NAME, "nonexistent");

    // Then
    assertThat(deleted).isFalse();
  }

  @Test
  void shouldUpdateBranchHead() {
    // Given
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    Branch branch = new Branch("main", commit1);
    repository.save(DATASET_NAME, branch);

    // When
    Branch updated = repository.updateBranchHead(DATASET_NAME, "main", commit2);

    // Then
    assertThat(updated.getCommitId()).isEqualTo(commit2);
    Optional<Branch> found = repository.findByDatasetAndName(DATASET_NAME, "main");
    assertThat(found).isPresent();
    assertThat(found.get().getCommitId()).isEqualTo(commit2);
  }

  @Test
  void shouldThrowExceptionWhenUpdatingNonexistentBranch() {
    // Given
    CommitId commitId = CommitId.generate();

    // When/Then
    assertThatThrownBy(() ->
        repository.updateBranchHead(DATASET_NAME, "nonexistent", commitId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch not found");
  }

  @Test
  void shouldCheckBranchExists() {
    // Given
    CommitId commitId = CommitId.generate();
    Branch branch = new Branch("main", commitId);
    repository.save(DATASET_NAME, branch);

    // When/Then
    assertThat(repository.exists(DATASET_NAME, "main")).isTrue();
    assertThat(repository.exists(DATASET_NAME, "nonexistent")).isFalse();
  }

  @Test
  void shouldDeleteAllBranchesInDataset() {
    // Given
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    repository.save(DATASET_NAME, new Branch("main", commit1));
    repository.save(DATASET_NAME, new Branch("develop", commit2));

    // When
    repository.deleteAllByDataset(DATASET_NAME);

    // Then
    assertThat(repository.findAllByDataset(DATASET_NAME)).isEmpty();
  }

  @Test
  void shouldIsolateBranchesBetweenDatasets() {
    // Given
    CommitId commit1 = CommitId.generate();
    CommitId commit2 = CommitId.generate();
    repository.save("dataset1", new Branch("main", commit1));
    repository.save("dataset2", new Branch("main", commit2));

    // When
    Optional<Branch> branch1 = repository.findByDatasetAndName("dataset1", "main");
    Optional<Branch> branch2 = repository.findByDatasetAndName("dataset2", "main");

    // Then
    assertThat(branch1).isPresent();
    assertThat(branch2).isPresent();
    assertThat(branch1.get().getCommitId()).isEqualTo(commit1);
    assertThat(branch2.get().getCommitId()).isEqualTo(commit2);
  }
}
