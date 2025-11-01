package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.CommitNotFoundException;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for DiffService.
 * Tests service logic in isolation using mocks.
 */
@ExtendWith(MockitoExtension.class)
class DiffServiceTest {

  @Mock
  private DatasetService datasetService;

  @Mock
  private CommitRepository commitRepository;

  @InjectMocks
  private DiffService diffService;

  @Test
  void diffCommits_whenFromCommitNotFound_shouldThrowCommitNotFoundException() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(false);

    // When/Then
    assertThatThrownBy(() -> diffService.diffCommits(dataset, fromCommit, toCommit))
        .isInstanceOf(CommitNotFoundException.class)
        .hasMessageContaining("Commit not found: " + fromCommit.value());

    // Verify early exit (didn't check toCommit or materialize)
    verify(commitRepository, never()).exists(dataset, toCommit);
    verify(datasetService, never()).materializeCommit(anyString(), any(CommitId.class));
  }

  @Test
  void diffCommits_whenToCommitNotFound_shouldThrowCommitNotFoundException() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(false);

    // When/Then
    assertThatThrownBy(() -> diffService.diffCommits(dataset, fromCommit, toCommit))
        .isInstanceOf(CommitNotFoundException.class)
        .hasMessageContaining("Commit not found: " + toCommit.value());

    // Verify didn't try to materialize
    verify(datasetService, never()).materializeCommit(anyString(), any(CommitId.class));
  }

  @Test
  void diffCommits_whenBothCommitsExist_shouldCallDatasetServiceTwice() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(true);

    DatasetGraph emptyDataset = DatasetGraphFactory.createTxnMem();
    when(datasetService.materializeCommit(dataset, fromCommit)).thenReturn(emptyDataset);
    when(datasetService.materializeCommit(dataset, toCommit)).thenReturn(emptyDataset);

    // When
    String result = diffService.diffCommits(dataset, fromCommit, toCommit);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).contains("TX"); // RDF Patch header

    verify(datasetService).materializeCommit(dataset, fromCommit);
    verify(datasetService).materializeCommit(dataset, toCommit);
  }

  @Test
  void diffCommits_whenSameCommit_shouldReturnEmptyPatch() {
    // Given
    String dataset = "test";
    CommitId commit = CommitId.generate();

    when(commitRepository.exists(dataset, commit)).thenReturn(true);

    DatasetGraph emptyDataset = DatasetGraphFactory.createTxnMem();
    when(datasetService.materializeCommit(dataset, commit)).thenReturn(emptyDataset);

    // When
    String result = diffService.diffCommits(dataset, commit, commit);

    // Then
    assertThat(result).isNotNull();
    assertThat(result).contains("TX"); // Has transaction markers
    assertThat(result).doesNotContain("A "); // No additions
    assertThat(result).doesNotContain("D "); // No deletions
  }

  @Test
  void diffCommits_whenCommitsHaveDifferences_shouldIncludeAdditionsAndDeletions() {
    // Given
    String dataset = "test";
    CommitId fromCommit = CommitId.generate();
    CommitId toCommit = CommitId.generate();

    when(commitRepository.exists(dataset, fromCommit)).thenReturn(true);
    when(commitRepository.exists(dataset, toCommit)).thenReturn(true);

    // Create datasets with differences
    DatasetGraph fromDataset = DatasetGraphFactory.createTxnMem();
    DatasetGraph toDataset = DatasetGraphFactory.createTxnMem();

    // Add a triple to fromDataset (will be deleted in diff)
    fromDataset.add(
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/default"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/s1"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/p1"),
        org.apache.jena.graph.NodeFactory.createLiteralString("old")
    );

    // Add a different triple to toDataset (will be addition in diff)
    toDataset.add(
        org.apache.jena.graph.NodeFactory.createURI("http://example.org/default"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/s2"),
        org.apache.jena.graph.NodeFactory.createURI("http://ex.org/p2"),
        org.apache.jena.graph.NodeFactory.createLiteralString("new")
    );

    when(datasetService.materializeCommit(dataset, fromCommit)).thenReturn(fromDataset);
    when(datasetService.materializeCommit(dataset, toCommit)).thenReturn(toDataset);

    // When
    String result = diffService.diffCommits(dataset, fromCommit, toCommit);

    // Then
    assertThat(result).contains("D "); // Has deletions
    assertThat(result).contains("A "); // Has additions
  }
}
