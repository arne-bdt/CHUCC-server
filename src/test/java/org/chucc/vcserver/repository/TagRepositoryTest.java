package org.chucc.vcserver.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TagRepository.
 */
class TagRepositoryTest {

  private TagRepository repository;

  @BeforeEach
  void setUp() {
    repository = new TagRepository();
  }

  @Test
  void findByDatasetAndName_shouldReturnEmpty_whenTagDoesNotExist() {
    assertTrue(repository.findByDatasetAndName("dataset1", "tag1").isEmpty());
  }

  @Test
  void save_shouldStoreTag() {
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag("v1.0.0", commitId);

    Tag saved = repository.save("dataset1", tag);

    assertEquals(tag, saved);
    assertTrue(repository.findByDatasetAndName("dataset1", "v1.0.0").isPresent());
  }

  @Test
  void findByDatasetAndName_shouldReturnTag_whenExists() {
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag("v1.0.0", commitId);
    repository.save("dataset1", tag);

    var found = repository.findByDatasetAndName("dataset1", "v1.0.0");

    assertTrue(found.isPresent());
    assertEquals(tag, found.get());
  }

  @Test
  void findAllByDataset_shouldReturnEmptyList_whenNoTags() {
    var tags = repository.findAllByDataset("dataset1");

    assertTrue(tags.isEmpty());
  }

  @Test
  void findAllByDataset_shouldReturnAllTags() {
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    repository.save("dataset1", tag1);
    repository.save("dataset1", tag2);

    var tags = repository.findAllByDataset("dataset1");

    assertEquals(2, tags.size());
    assertTrue(tags.contains(tag1));
    assertTrue(tags.contains(tag2));
  }

  @Test
  void findAllByDataset_shouldIsolateDatasets() {
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    repository.save("dataset1", tag1);
    repository.save("dataset2", tag2);

    var dataset1Tags = repository.findAllByDataset("dataset1");
    var dataset2Tags = repository.findAllByDataset("dataset2");

    assertEquals(1, dataset1Tags.size());
    assertEquals(1, dataset2Tags.size());
    assertTrue(dataset1Tags.contains(tag1));
    assertTrue(dataset2Tags.contains(tag2));
  }

  @Test
  void exists_shouldReturnFalse_whenTagDoesNotExist() {
    assertFalse(repository.exists("dataset1", "v1.0.0"));
  }

  @Test
  void exists_shouldReturnTrue_whenTagExists() {
    CommitId commitId = CommitId.generate();
    Tag tag = new Tag("v1.0.0", commitId);
    repository.save("dataset1", tag);

    assertTrue(repository.exists("dataset1", "v1.0.0"));
  }

  @Test
  void deleteAllByDataset_shouldRemoveAllTags() {
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    repository.save("dataset1", tag1);
    repository.save("dataset1", tag2);

    repository.deleteAllByDataset("dataset1");

    assertTrue(repository.findAllByDataset("dataset1").isEmpty());
  }

  @Test
  void deleteAllByDataset_shouldNotAffectOtherDatasets() {
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    repository.save("dataset1", tag1);
    repository.save("dataset2", tag2);

    repository.deleteAllByDataset("dataset1");

    assertTrue(repository.findAllByDataset("dataset1").isEmpty());
    assertEquals(1, repository.findAllByDataset("dataset2").size());
  }
}
