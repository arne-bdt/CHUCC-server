package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.exception.PreconditionFailedException;
import org.chucc.vcserver.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PreconditionService}.
 */
class PreconditionServiceTest {

  private BranchRepository branchRepository;
  private PreconditionService preconditionService;

  @BeforeEach
  void setUp() {
    branchRepository = mock(BranchRepository.class);
    preconditionService = new PreconditionService(branchRepository);
  }

  @Test
  void checkIfMatch_shouldPassWhenETagMatches() {
    String datasetName = "default";
    String branchName = "main";
    String commitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String etag = "\"01936c7f-8a2e-7890-abcd-ef1234567890\"";

    Branch branch = new Branch(branchName, new CommitId(commitId));
    when(branchRepository.findByDatasetAndName(datasetName, branchName))
        .thenReturn(Optional.of(branch));

    assertThatCode(() -> preconditionService.checkIfMatch(datasetName, branchName, etag))
        .doesNotThrowAnyException();
  }

  @Test
  void checkIfMatch_shouldThrowWhenETagDoesNotMatch() {
    String datasetName = "default";
    String branchName = "main";
    String currentCommitId = "01936c81-2222-7890-abcd-ef1234567890";
    String expectedCommitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String etag = "\"" + expectedCommitId + "\"";

    Branch branch = new Branch(branchName, new CommitId(currentCommitId));
    when(branchRepository.findByDatasetAndName(datasetName, branchName))
        .thenReturn(Optional.of(branch));

    assertThatThrownBy(() -> preconditionService.checkIfMatch(datasetName, branchName, etag))
        .isInstanceOf(PreconditionFailedException.class)
        .hasMessageContaining("If-Match precondition failed")
        .hasMessageContaining(expectedCommitId)
        .hasMessageContaining(currentCommitId);
  }

  @Test
  void checkIfMatch_shouldPassWhenIfMatchIsNull() {
    String datasetName = "default";
    String branchName = "main";

    assertThatCode(() -> preconditionService.checkIfMatch(datasetName, branchName, null))
        .doesNotThrowAnyException();

    verify(branchRepository, never()).findByDatasetAndName(anyString(), anyString());
  }

  @Test
  void checkIfMatch_shouldPassWhenIfMatchIsBlank() {
    String datasetName = "default";
    String branchName = "main";

    assertThatCode(() -> preconditionService.checkIfMatch(datasetName, branchName, ""))
        .doesNotThrowAnyException();
    assertThatCode(() -> preconditionService.checkIfMatch(datasetName, branchName, "   "))
        .doesNotThrowAnyException();

    verify(branchRepository, never()).findByDatasetAndName(anyString(), anyString());
  }

  @Test
  void checkIfMatch_shouldHandleETagWithoutQuotes() {
    String datasetName = "default";
    String branchName = "main";
    String commitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String etag = "01936c7f-8a2e-7890-abcd-ef1234567890"; // without quotes

    Branch branch = new Branch(branchName, new CommitId(commitId));
    when(branchRepository.findByDatasetAndName(datasetName, branchName))
        .thenReturn(Optional.of(branch));

    assertThatCode(() -> preconditionService.checkIfMatch(datasetName, branchName, etag))
        .doesNotThrowAnyException();
  }

  @Test
  void checkIfMatch_shouldThrowWhenBranchNotFound() {
    String datasetName = "default";
    String branchName = "nonexistent";
    String etag = "\"01936c7f-8a2e-7890-abcd-ef1234567890\"";

    when(branchRepository.findByDatasetAndName(datasetName, branchName))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> preconditionService.checkIfMatch(datasetName, branchName, etag))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch not found")
        .hasMessageContaining(branchName)
        .hasMessageContaining(datasetName);
  }

  @Test
  void checkIfMatch_shouldExtractExpectedAndActualFromException() {
    String datasetName = "default";
    String branchName = "main";
    String currentCommitId = "01936c81-2222-7890-abcd-ef1234567890";
    String expectedCommitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String etag = "\"" + expectedCommitId + "\"";

    Branch branch = new Branch(branchName, new CommitId(currentCommitId));
    when(branchRepository.findByDatasetAndName(datasetName, branchName))
        .thenReturn(Optional.of(branch));

    try {
      preconditionService.checkIfMatch(datasetName, branchName, etag);
    } catch (PreconditionFailedException e) {
      assertThat(e.getExpected()).isEqualTo(expectedCommitId);
      assertThat(e.getActual()).isEqualTo(currentCommitId);
    }
  }
}
