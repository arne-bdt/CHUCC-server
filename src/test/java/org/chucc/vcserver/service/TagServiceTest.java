package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.TagDetailResponse;
import org.chucc.vcserver.dto.TagListResponse;
import org.chucc.vcserver.exception.TagDeletionForbiddenException;
import org.chucc.vcserver.exception.TagNotFoundException;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TagService.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  private static final String DATASET_NAME = "testDataset";
  private static final String TAG_NAME = "v1.0.0";
  private static final CommitId COMMIT_ID = CommitId.generate();

  @Mock
  private TagRepository tagRepository;

  @Mock
  private VersionControlProperties vcProperties;

  @InjectMocks
  private TagService tagService;

  private Tag testTag;

  /**
   * Set up test data.
   */
  @BeforeEach
  void setUp() {
    testTag = new Tag(TAG_NAME, COMMIT_ID);
  }

  /**
   * Test getting tag details when tag exists.
   */
  @Test
  void testGetTagDetails_whenTagExists_returnsDetails() {
    when(tagRepository.findByDatasetAndName(DATASET_NAME, TAG_NAME))
        .thenReturn(Optional.of(testTag));

    Optional<TagDetailResponse> result = tagService.getTagDetails(DATASET_NAME, TAG_NAME);

    assertTrue(result.isPresent());
    TagDetailResponse response = result.get();
    assertEquals(TAG_NAME, response.getName());
    assertEquals(COMMIT_ID.value(), response.getTarget());
  }

  /**
   * Test getting tag details when tag does not exist.
   */
  @Test
  void testGetTagDetails_whenTagDoesNotExist_returnsEmpty() {
    when(tagRepository.findByDatasetAndName(DATASET_NAME, TAG_NAME))
        .thenReturn(Optional.empty());

    Optional<TagDetailResponse> result = tagService.getTagDetails(DATASET_NAME, TAG_NAME);

    assertFalse(result.isPresent());
  }

  /**
   * Test deleting tag when it exists and deletion is allowed.
   */
  @Test
  void testDeleteTag_whenExistsAndAllowed_deletesSuccessfully() {
    when(tagRepository.findByDatasetAndName(DATASET_NAME, TAG_NAME))
        .thenReturn(Optional.of(testTag));
    when(vcProperties.isTagDeletionAllowed()).thenReturn(true);

    tagService.deleteTag(DATASET_NAME, TAG_NAME);

    verify(tagRepository).delete(DATASET_NAME, TAG_NAME);
  }

  /**
   * Test deleting tag when it does not exist.
   */
  @Test
  void testDeleteTag_whenTagDoesNotExist_throwsNotFoundException() {
    when(tagRepository.findByDatasetAndName(DATASET_NAME, TAG_NAME))
        .thenReturn(Optional.empty());

    assertThrows(TagNotFoundException.class, () ->
        tagService.deleteTag(DATASET_NAME, TAG_NAME));

    verify(tagRepository, never()).delete(any(), any());
  }

  /**
   * Test deleting tag when policy forbids deletion.
   */
  @Test
  void testDeleteTag_whenPolicyForbids_throwsForbiddenException() {
    when(tagRepository.findByDatasetAndName(DATASET_NAME, TAG_NAME))
        .thenReturn(Optional.of(testTag));
    when(vcProperties.isTagDeletionAllowed()).thenReturn(false);

    assertThrows(TagDeletionForbiddenException.class, () ->
        tagService.deleteTag(DATASET_NAME, TAG_NAME));

    verify(tagRepository, never()).delete(any(), any());
  }

  /**
   * Test listing tags with pagination returns correct page.
   */
  @Test
  void listTags_withPagination_shouldReturnCorrectPage() {
    // Arrange: Mock 50 tags
    List<Tag> allTags = IntStream.range(0, 50)
        .mapToObj(i -> createTag("v1.0." + i))
        .toList();
    when(tagRepository.findAllByDataset("test")).thenReturn(allTags);

    // Act: Get second page (limit=20, offset=10)
    TagListResponse response = tagService.listTags("test", 20, 10);

    // Assert
    assertThat(response.tags()).hasSize(20);
    assertThat(response.pagination().limit()).isEqualTo(20);
    assertThat(response.pagination().offset()).isEqualTo(10);
    assertThat(response.pagination().hasMore()).isTrue();
    assertThat(response.tags().get(0).name()).isEqualTo("v1.0.10");
  }

  /**
   * Test listing tags on last page has hasMore false.
   */
  @Test
  void listTags_lastPage_hasMoreShouldBeFalse() {
    // Arrange: Mock 25 tags
    List<Tag> allTags = IntStream.range(0, 25)
        .mapToObj(i -> createTag("v1.0." + i))
        .toList();
    when(tagRepository.findAllByDataset("test")).thenReturn(allTags);

    // Act: Last page (offset=20, limit=10)
    TagListResponse response = tagService.listTags("test", 10, 20);

    // Assert
    assertThat(response.tags()).hasSize(5); // Only 5 remaining
    assertThat(response.pagination().hasMore()).isFalse();
  }

  /**
   * Test listing tags from empty repository returns empty list.
   */
  @Test
  void listTags_emptyRepository_shouldReturnEmptyList() {
    // Arrange
    when(tagRepository.findAllByDataset("test")).thenReturn(List.of());

    // Act
    TagListResponse response = tagService.listTags("test", 100, 0);

    // Assert
    assertThat(response.tags()).isEmpty();
    assertThat(response.pagination().hasMore()).isFalse();
  }

  /**
   * Helper method to create a Tag for testing.
   *
   * @param name the tag name
   * @return the created tag
   */
  private Tag createTag(String name) {
    return new Tag(name, CommitId.generate(), "Message", "author", Instant.now());
  }
}
