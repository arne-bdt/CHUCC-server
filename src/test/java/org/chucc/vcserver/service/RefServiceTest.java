package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.chucc.vcserver.domain.Branch;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.domain.Tag;
import org.chucc.vcserver.dto.RefResponse;
import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RefService.
 */
class RefServiceTest {

  private RefService service;
  private BranchRepository branchRepository;
  private TagRepository tagRepository;

  private static final String DATASET_NAME = "test-dataset";

  @BeforeEach
  void setUp() {
    branchRepository = new BranchRepository();
    tagRepository = new TagRepository();
    service = new RefService(branchRepository, tagRepository);
  }

  @Test
  void getAllRefs_shouldReturnEmptyList_whenNoRefs() {
    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).isEmpty();
  }

  @Test
  void getAllRefs_shouldReturnBranchesOnly() {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Branch main = new Branch("main", commitId1);
    Branch develop = new Branch("develop", commitId2);

    branchRepository.save(DATASET_NAME, main);
    branchRepository.save(DATASET_NAME, develop);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(2);
    assertThat(refs).extracting(RefResponse::getType)
        .containsOnly("branch");
    assertThat(refs).extracting(RefResponse::getName)
        .containsExactlyInAnyOrder("main", "develop");
  }

  @Test
  void getAllRefs_shouldReturnTagsOnly() {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Tag tag1 = new Tag("v1.0.0", commitId1);
    Tag tag2 = new Tag("v2.0.0", commitId2);

    tagRepository.save(DATASET_NAME, tag1);
    tagRepository.save(DATASET_NAME, tag2);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(2);
    assertThat(refs).extracting(RefResponse::getType)
        .containsOnly("tag");
    assertThat(refs).extracting(RefResponse::getName)
        .containsExactlyInAnyOrder("v1.0.0", "v2.0.0");
  }

  @Test
  void getAllRefs_shouldReturnMixedBranchesAndTags() {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();

    Branch main = new Branch("main", commitId1);
    Branch develop = new Branch("develop", commitId2);
    Tag tag1 = new Tag("v1.0.0", commitId1);

    branchRepository.save(DATASET_NAME, main);
    branchRepository.save(DATASET_NAME, develop);
    tagRepository.save(DATASET_NAME, tag1);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(3);
    assertThat(refs).extracting(RefResponse::getType)
        .containsExactlyInAnyOrder("branch", "branch", "tag");
    assertThat(refs).extracting(RefResponse::getName)
        .containsExactlyInAnyOrder("main", "develop", "v1.0.0");
  }

  @Test
  void getAllRefs_shouldSortBranchesFirst_thenTags() {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();

    Branch main = new Branch("main", commitId1);
    Tag tag1 = new Tag("v1.0.0", commitId2);

    branchRepository.save(DATASET_NAME, main);
    tagRepository.save(DATASET_NAME, tag1);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(2);
    // First item should be a branch
    assertThat(refs.get(0).getType()).isEqualTo("branch");
    // Second item should be a tag
    assertThat(refs.get(1).getType()).isEqualTo("tag");
  }

  @Test
  void getAllRefs_shouldSortAlphabeticallyWithinType() {
    // Given
    CommitId commitId = CommitId.generate();

    Branch zulu = new Branch("zulu", commitId);
    Branch alpha = new Branch("alpha", commitId);
    Tag v2 = new Tag("v2.0.0", commitId);
    Tag v1 = new Tag("v1.0.0", commitId);

    branchRepository.save(DATASET_NAME, zulu);
    branchRepository.save(DATASET_NAME, alpha);
    tagRepository.save(DATASET_NAME, v2);
    tagRepository.save(DATASET_NAME, v1);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(4);
    assertThat(refs).extracting(RefResponse::getName)
        .containsExactly("alpha", "zulu", "v1.0.0", "v2.0.0");
  }

  @Test
  void getAllRefs_shouldIncludeTargetCommit() {
    // Given
    CommitId commitId = CommitId.generate();
    Branch main = new Branch("main", commitId);
    branchRepository.save(DATASET_NAME, main);

    // When
    var refs = service.getAllRefs(DATASET_NAME);

    // Then
    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getTargetCommit()).isEqualTo(commitId.toString());
  }

  @Test
  void getAllRefs_shouldIsolateDatasets() {
    // Given
    CommitId commitId1 = CommitId.generate();
    CommitId commitId2 = CommitId.generate();
    Branch main1 = new Branch("main", commitId1);
    Branch main2 = new Branch("main", commitId2);

    branchRepository.save("dataset1", main1);
    branchRepository.save("dataset2", main2);

    // When
    var refs1 = service.getAllRefs("dataset1");
    var refs2 = service.getAllRefs("dataset2");

    // Then
    assertThat(refs1).hasSize(1);
    assertThat(refs2).hasSize(1);
    assertThat(refs1.get(0).getTargetCommit()).isEqualTo(commitId1.toString());
    assertThat(refs2.get(0).getTargetCommit()).isEqualTo(commitId2.toString());
  }
}
