package org.chucc.vcserver.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.apache.jena.rdfpatch.RDFPatch;
import org.apache.jena.rdfpatch.RDFPatchOps;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommitRepositoryTest {
  private CommitRepository repository;
  private static final String DATASET_NAME = "test-dataset";

  @BeforeEach
  void setUp() {
    repository = new CommitRepository();
  }

  @Test
  void shouldSaveAndFindCommit() {
    // Given
    Commit commit = Commit.create(List.of(), "author", "message");
    RDFPatch patch = RDFPatchOps.emptyPatch();

    // When
    repository.save(DATASET_NAME, commit, patch);

    // Then
    Optional<Commit> found = repository.findByDatasetAndId(DATASET_NAME, commit.id());
    assertThat(found).isPresent();
    assertThat(found.get()).isEqualTo(commit);
  }

  @Test
  void shouldSaveAndFindPatch() {
    // Given
    Commit commit = Commit.create(List.of(), "author", "message");
    RDFPatch patch = RDFPatchOps.emptyPatch();

    // When
    repository.save(DATASET_NAME, commit, patch);

    // Then
    Optional<RDFPatch> found = repository.findPatchByDatasetAndId(DATASET_NAME, commit.id());
    assertThat(found).isPresent();
    assertThat(found.get()).isEqualTo(patch);
  }

  @Test
  void shouldReturnEmptyWhenCommitNotFound() {
    // Given
    CommitId nonexistentId = CommitId.generate();

    // When
    Optional<Commit> found = repository.findByDatasetAndId(DATASET_NAME, nonexistentId);

    // Then
    assertThat(found).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenPatchNotFound() {
    // Given
    CommitId nonexistentId = CommitId.generate();

    // When
    Optional<RDFPatch> found = repository.findPatchByDatasetAndId(DATASET_NAME, nonexistentId);

    // Then
    assertThat(found).isEmpty();
  }

  @Test
  void shouldFindAllCommitsInDataset() {
    // Given
    Commit commit1 = Commit.create(List.of(), "author1", "message1");
    Commit commit2 = Commit.create(List.of(), "author2", "message2");
    RDFPatch patch = RDFPatchOps.emptyPatch();

    repository.save(DATASET_NAME, commit1, patch);
    repository.save(DATASET_NAME, commit2, patch);

    // When
    List<Commit> commits = repository.findAllByDataset(DATASET_NAME);

    // Then
    assertThat(commits).hasSize(2);
    assertThat(commits).containsExactlyInAnyOrder(commit1, commit2);
  }

  @Test
  void shouldReturnEmptyListWhenNoDataset() {
    // When
    List<Commit> commits = repository.findAllByDataset("nonexistent");

    // Then
    assertThat(commits).isEmpty();
  }

  @Test
  void shouldCheckCommitExists() {
    // Given
    Commit commit = Commit.create(List.of(), "author", "message");
    RDFPatch patch = RDFPatchOps.emptyPatch();
    repository.save(DATASET_NAME, commit, patch);

    // When/Then
    assertThat(repository.exists(DATASET_NAME, commit.id())).isTrue();
    assertThat(repository.exists(DATASET_NAME, CommitId.generate())).isFalse();
  }

  @Test
  void shouldDeleteAllCommitsAndPatchesInDataset() {
    // Given
    Commit commit = Commit.create(List.of(), "author", "message");
    RDFPatch patch = RDFPatchOps.emptyPatch();
    repository.save(DATASET_NAME, commit, patch);

    // When
    repository.deleteAllByDataset(DATASET_NAME);

    // Then
    assertThat(repository.findAllByDataset(DATASET_NAME)).isEmpty();
    assertThat(repository.findByDatasetAndId(DATASET_NAME, commit.id())).isEmpty();
    assertThat(repository.findPatchByDatasetAndId(DATASET_NAME, commit.id())).isEmpty();
  }

  @Test
  void shouldIsolateCommitsBetweenDatasets() {
    // Given
    Commit commit1 = Commit.create(List.of(), "author1", "message1");
    Commit commit2 = Commit.create(List.of(), "author2", "message2");
    RDFPatch patch = RDFPatchOps.emptyPatch();

    repository.save("dataset1", commit1, patch);
    repository.save("dataset2", commit2, patch);

    // When
    Optional<Commit> found1 = repository.findByDatasetAndId("dataset1", commit1.id());
    Optional<Commit> found2 = repository.findByDatasetAndId("dataset2", commit2.id());
    Optional<Commit> notFound1 = repository.findByDatasetAndId("dataset1", commit2.id());
    Optional<Commit> notFound2 = repository.findByDatasetAndId("dataset2", commit1.id());

    // Then
    assertThat(found1).isPresent();
    assertThat(found2).isPresent();
    assertThat(notFound1).isEmpty();
    assertThat(notFound2).isEmpty();
  }
}
