package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.chucc.vcserver.domain.Commit;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.CommitMetadataDto;
import org.chucc.vcserver.repository.CommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for CommitService.
 */
@ExtendWith(MockitoExtension.class)
class CommitServiceTest {

  @Mock
  private CommitRepository commitRepository;

  @InjectMocks
  private CommitService commitService;

  @Test
  void getCommitMetadata_shouldReturnDto_whenCommitExists() {
    // Arrange
    CommitId id = CommitId.generate();
    CommitId parent = CommitId.generate();
    Commit commit = new Commit(
        id,
        List.of(parent),
        "Author <author@example.org>",
        "Test message",
        Instant.parse("2025-10-24T12:00:00Z"),
        42
    );

    when(commitRepository.findByDatasetAndId("default", id))
        .thenReturn(Optional.of(commit));

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "default",
        id.toString()
    );

    // Assert
    assertThat(result).isPresent();
    CommitMetadataDto dto = result.get();
    assertThat(dto.id()).isEqualTo(id.toString());
    assertThat(dto.message()).isEqualTo("Test message");
    assertThat(dto.author()).isEqualTo("Author <author@example.org>");
    assertThat(dto.timestamp()).isEqualTo(Instant.parse("2025-10-24T12:00:00Z"));
    assertThat(dto.parents()).containsExactly(parent.toString());
    assertThat(dto.patchSize()).isEqualTo(42);
  }

  @Test
  void getCommitMetadata_withNonExistentCommit_shouldReturnEmpty() {
    // Arrange
    CommitId id = CommitId.generate();
    when(commitRepository.findByDatasetAndId("default", id))
        .thenReturn(Optional.empty());

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "default",
        id.toString()
    );

    // Assert
    assertThat(result).isEmpty();
  }

  @Test
  void getCommitMetadata_withMultipleParents_shouldIncludeAll() {
    // Arrange
    CommitId id = CommitId.generate();
    CommitId parent1 = CommitId.generate();
    CommitId parent2 = CommitId.generate();
    Commit commit = new Commit(
        id,
        List.of(parent1, parent2),
        "Merger <merger@example.org>",
        "Merge commit",
        Instant.parse("2025-10-24T14:30:00Z"),
        10
    );

    when(commitRepository.findByDatasetAndId("test", id))
        .thenReturn(Optional.of(commit));

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "test",
        id.toString()
    );

    // Assert
    assertThat(result).isPresent();
    CommitMetadataDto dto = result.get();
    assertThat(dto.parents()).containsExactly(parent1.toString(), parent2.toString());
  }

  @Test
  void getCommitMetadata_withNoParents_shouldReturnEmptyList() {
    // Arrange
    CommitId id = CommitId.generate();
    Commit commit = new Commit(
        id,
        List.of(),
        "System",
        "Initial commit",
        Instant.parse("2025-10-24T10:00:00Z"),
        0
    );

    when(commitRepository.findByDatasetAndId("default", id))
        .thenReturn(Optional.of(commit));

    // Act
    Optional<CommitMetadataDto> result = commitService.getCommitMetadata(
        "default",
        id.toString()
    );

    // Assert
    assertThat(result).isPresent();
    CommitMetadataDto dto = result.get();
    assertThat(dto.parents()).isEmpty();
  }
}
