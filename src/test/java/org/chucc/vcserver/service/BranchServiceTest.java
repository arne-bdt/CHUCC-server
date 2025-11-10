package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.dto.BranchListResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for BranchService pagination functionality.
 *
 * <p>Tests the pagination logic without requiring full Spring context:
 * <ul>
 *   <li>Pagination with offset and limit returns correct page
 *   <li>Last page correctly sets hasMore=false
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BranchServiceTest {

  @Mock
  private BranchRepository branchRepository;

  private BranchService branchService;

  @BeforeEach
  void setUp() {
    branchService = new BranchService(branchRepository);
  }

  @Test
  void listBranches_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 50 branches
    List<Branch> allBranches = IntStream.range(0, 50)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    when(branchRepository.findAllByDataset("test")).thenReturn(allBranches);

    // Act: Get second page (limit=20, offset=10)
    BranchListResponse response = branchService.listBranches("test", 20, 10);

    // Assert: Correct page returned
    assertThat(response.branches()).hasSize(20);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().hasMore()).isTrue();

    // Assert: First branch in page is at correct offset
    assertThat(response.branches().get(0).name()).isEqualTo("branch-10");
    assertThat(response.branches().get(19).name()).isEqualTo("branch-29");
  }

  @Test
  void listBranches_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 25 branches
    List<Branch> allBranches = IntStream.range(0, 25)
        .mapToObj(i -> createBranch("branch-" + i))
        .toList();
    when(branchRepository.findAllByDataset("test")).thenReturn(allBranches);

    // Act: Last page (limit=10, offset=20)
    BranchListResponse response = branchService.listBranches("test", 10, 20);

    // Assert: Only remaining items returned
    assertThat(response.branches()).hasSize(5); // Only 5 remaining (25 - 20)
    assertThat(response.pagination().limit()).isEqualTo(10);
    assertThat(response.pagination().offset()).isEqualTo(20);
    assertThat(response.pagination().hasMore()).isFalse();

    // Assert: Correct branches returned
    assertThat(response.branches().get(0).name()).isEqualTo("branch-20");
    assertThat(response.branches().get(4).name()).isEqualTo("branch-24");
  }

  @Test
  void listBranches_emptyRepository_shouldReturnEmptyList() {
    // Arrange: Empty repository
    when(branchRepository.findAllByDataset("test")).thenReturn(List.of());

    // Act
    BranchListResponse response = branchService.listBranches("test", 10, 0);

    // Assert
    assertThat(response.branches()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  /**
   * Helper method to create a test branch.
   *
   * @param name the branch name
   * @return a Branch instance
   */
  private Branch createBranch(String name) {
    return new Branch(
        name,
        CommitId.generate(),
        false,
        Instant.now(),
        Instant.now(),
        1
    );
  }
}
