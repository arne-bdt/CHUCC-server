package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.RefsListResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for RefService with pagination.
 */
@ExtendWith(MockitoExtension.class)
class RefServiceTest {

  @Mock
  private BranchRepository branchRepository;

  @Mock
  private TagRepository tagRepository;

  private RefService refService;

  @BeforeEach
  void setUp() {
    refService = new RefService(branchRepository, tagRepository);
  }

  @Test
  void getAllRefs_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 30 branches + 20 tags = 50 refs
    List<Branch> branches = IntStream.range(0, 30)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    List<Tag> tags = IntStream.range(0, 20)
        .mapToObj(i -> createTag("v" + i))
        .toList();

    when(branchRepository.findAllByDataset("test")).thenReturn(branches);
    when(tagRepository.findAllByDataset("test")).thenReturn(tags);

    // Act: Get second page (offset=10, limit=20)
    RefsListResponse response = refService.getAllRefs("test", 20, 10);

    // Assert
    assertThat(response.refs()).hasSize(20);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().hasMore()).isTrue();
  }

  @Test
  void getAllRefs_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 15 branches + 10 tags = 25 refs
    List<Branch> branches = IntStream.range(0, 15)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    List<Tag> tags = IntStream.range(0, 10)
        .mapToObj(i -> createTag("v" + i))
        .toList();

    when(branchRepository.findAllByDataset("test")).thenReturn(branches);
    when(tagRepository.findAllByDataset("test")).thenReturn(tags);

    // Act: Last page (offset=20, limit=10)
    RefsListResponse response = refService.getAllRefs("test", 10, 20);

    // Assert
    assertThat(response.refs()).hasSize(5); // Only 5 remaining
    assertThat(response.pagination().hasMore()).isFalse();
  }

  @Test
  void getAllRefs_emptyRepository_shouldReturnEmptyList() {
    // Arrange
    when(branchRepository.findAllByDataset("test")).thenReturn(List.of());
    when(tagRepository.findAllByDataset("test")).thenReturn(List.of());

    // Act
    RefsListResponse response = refService.getAllRefs("test", 100, 0);

    // Assert
    assertThat(response.refs()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  private Branch createBranch(String name) {
    return new Branch(name, CommitId.generate(), false, Instant.now(), Instant.now(), 1);
  }

  private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author", Instant.now());
  }
}
