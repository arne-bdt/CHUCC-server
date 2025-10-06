package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import org.chucc.vcserver.config.VersionControlProperties;
import org.chucc.vcserver.dto.TagDetailResponse;
import org.chucc.vcserver.exception.TagDeletionForbiddenException;
import org.chucc.vcserver.exception.TagNotFoundException;
import org.chucc.vcserver.repository.TagRepository;
import org.springframework.stereotype.Service;

/**
 * Service for tag operations.
 */
@Service
public class TagService {

  private final TagRepository tagRepository;
  private final VersionControlProperties vcProperties;

  /**
   * Constructor for TagService.
   *
   * @param tagRepository the tag repository
   * @param vcProperties the version control properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references"
  )
  public TagService(TagRepository tagRepository, VersionControlProperties vcProperties) {
    this.tagRepository = tagRepository;
    this.vcProperties = vcProperties;
  }

  /**
   * Gets tag details.
   *
   * @param datasetName the dataset name
   * @param tagName the tag name
   * @return tag details if tag exists, empty otherwise
   */
  public Optional<TagDetailResponse> getTagDetails(String datasetName, String tagName) {
    return tagRepository.findByDatasetAndName(datasetName, tagName)
        .map(tag -> new TagDetailResponse(tag.name(), tag.commitId().value()));
  }

  /**
   * Deletes a tag.
   *
   * @param datasetName the dataset name
   * @param tagName the tag name
   * @throws TagNotFoundException if tag does not exist
   * @throws TagDeletionForbiddenException if server policy prohibits tag deletion
   */
  public void deleteTag(String datasetName, String tagName) {
    // Check if tag exists
    if (!tagRepository.findByDatasetAndName(datasetName, tagName).isPresent()) {
      throw new TagNotFoundException(tagName);
    }

    // Check server policy
    if (!vcProperties.isTagDeletionAllowed()) {
      throw new TagDeletionForbiddenException();
    }

    // Delete tag
    tagRepository.delete(datasetName, tagName);
  }
}
