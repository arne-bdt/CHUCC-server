package org.chucc.vcserver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.TagDetailResponse;
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
}
