package org.chucc.vcserver.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.chucc.vcserver.domain.Tag;
import org.springframework.stereotype.Repository;

/**
 * In-memory repository for managing tags.
 * Thread-safe implementation using ConcurrentHashMap for concurrent read/write operations.
 */
@Repository
public class TagRepository {
  private final Map<String, Map<String, Tag>> datasetTags = new ConcurrentHashMap<>();

  /**
   * Finds a tag by dataset and tag name.
   *
   * @param datasetName the dataset name
   * @param tagName the tag name
   * @return an Optional containing the tag if found, empty otherwise
   */
  public Optional<Tag> findByDatasetAndName(String datasetName, String tagName) {
    return Optional.ofNullable(datasetTags.get(datasetName))
        .map(tags -> tags.get(tagName));
  }

  /**
   * Finds all tags for a dataset.
   *
   * @param datasetName the dataset name
   * @return a list of all tags in the dataset
   */
  public List<Tag> findAllByDataset(String datasetName) {
    return Optional.ofNullable(datasetTags.get(datasetName))
        .map(tags -> List.copyOf(tags.values()))
        .orElse(List.of());
  }

  /**
   * Saves a tag.
   *
   * @param datasetName the dataset name
   * @param tag the tag to save
   * @return the saved tag
   */
  public Tag save(String datasetName, Tag tag) {
    datasetTags.computeIfAbsent(datasetName, k -> new ConcurrentHashMap<>())
        .put(tag.name(), tag);
    return tag;
  }

  /**
   * Checks if a tag exists.
   *
   * @param datasetName the dataset name
   * @param tagName the tag name
   * @return true if the tag exists
   */
  public boolean exists(String datasetName, String tagName) {
    return findByDatasetAndName(datasetName, tagName).isPresent();
  }

  /**
   * Deletes all tags for a dataset.
   *
   * @param datasetName the dataset name
   */
  public void deleteAllByDataset(String datasetName) {
    datasetTags.remove(datasetName);
  }
}
