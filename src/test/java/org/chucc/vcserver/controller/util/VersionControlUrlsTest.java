package org.chucc.vcserver.controller.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.domain.CommitId;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VersionControlUrls}.
 */
class VersionControlUrlsTest {

  @Test
  void dataset_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.dataset("mydata"))
        .isEqualTo("/mydata");
  }

  @Test
  void datasetMetadata_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.datasetMetadata("mydata"))
        .isEqualTo("/datasets/mydata");
  }

  @Test
  void branch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.branch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main");
  }

  @Test
  void branches_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.branches("mydata"))
        .isEqualTo("/mydata/version/branches");
  }

  @Test
  void branchHistory_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.branchHistory("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/history");
  }

  @Test
  void commit_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.commit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890");
  }

  @Test
  void commitWithObject_shouldBuildCorrectUrl() {
    CommitId commitId = CommitId.of("01936d8f-1234-7890-abcd-ef1234567890");
    assertThat(VersionControlUrls.commit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890");
  }

  @Test
  void commits_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.commits("mydata"))
        .isEqualTo("/mydata/version/commits");
  }

  @Test
  void commitHistory_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.commitHistory("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/history");
  }

  @Test
  void commitDiff_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.commitDiff("mydata", "abc", "def"))
        .isEqualTo("/mydata/version/commits/abc/diff/def");
  }

  @Test
  void tag_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.tag("mydata", "v1.0"))
        .isEqualTo("/mydata/version/tags/v1.0");
  }

  @Test
  void tags_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.tags("mydata"))
        .isEqualTo("/mydata/version/tags");
  }

  @Test
  void sparqlAtBranch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.sparqlAtBranch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/sparql");
  }

  @Test
  void sparqlAtCommit_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.sparqlAtCommit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/sparql");
  }

  @Test
  void sparqlAtTag_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.sparqlAtTag("mydata", "v1.0"))
        .isEqualTo("/mydata/version/tags/v1.0/sparql");
  }

  @Test
  void updateAtBranch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.updateAtBranch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/update");
  }

  @Test
  void sparql_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.sparql("mydata"))
        .isEqualTo("/mydata/sparql");
  }

  @Test
  void update_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.update("mydata"))
        .isEqualTo("/mydata/update");
  }

  @Test
  void dataAtBranch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.dataAtBranch("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/data");
  }

  @Test
  void dataAtCommit_shouldBuildCorrectUrl() {
    String commitId = "01936d8f-1234-7890-abcd-ef1234567890";
    assertThat(VersionControlUrls.dataAtCommit("mydata", commitId))
        .isEqualTo("/mydata/version/commits/01936d8f-1234-7890-abcd-ef1234567890/data");
  }

  @Test
  void dataAtTag_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.dataAtTag("mydata", "v1.0"))
        .isEqualTo("/mydata/version/tags/v1.0/data");
  }

  @Test
  void data_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.data("mydata"))
        .isEqualTo("/mydata/data");
  }

  @Test
  void merge_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.merge("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/merge");
  }

  @Test
  void reset_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.reset("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/reset");
  }

  @Test
  void revert_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.revert("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/revert");
  }

  @Test
  void cherryPick_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.cherryPick("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/cherry-pick");
  }

  @Test
  void rebase_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.rebase("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/rebase");
  }

  @Test
  void squash_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.squash("mydata", "main"))
        .isEqualTo("/mydata/version/branches/main/squash");
  }

  @Test
  void batchGraphs_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.batchGraphs("mydata", "develop"))
        .isEqualTo("/mydata/version/branches/develop/batch-graphs");
  }

  @Test
  void batch_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.batch("mydata", "develop"))
        .isEqualTo("/mydata/version/branches/develop/batch");
  }

  @Test
  void refs_shouldBuildCorrectUrl() {
    assertThat(VersionControlUrls.refs("mydata"))
        .isEqualTo("/mydata/version/refs");
  }
}
