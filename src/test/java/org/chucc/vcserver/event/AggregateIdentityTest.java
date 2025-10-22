package org.chucc.vcserver.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for AggregateIdentity interface and its implementations.
 */
class AggregateIdentityTest {

  @Test
  void branchAggregate_shouldUseDatasetAndBranchAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.branch("default", "main");

    assertThat(aggregate.getAggregateType()).isEqualTo("Branch");
    assertThat(aggregate.getPartitionKey()).isEqualTo("default:main");
    assertThat(aggregate.getDataset()).isEqualTo("default");
  }

  @Test
  void datasetAggregate_shouldUseDatasetAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.dataset("my-dataset");

    assertThat(aggregate.getAggregateType()).isEqualTo("Dataset");
    assertThat(aggregate.getPartitionKey()).isEqualTo("my-dataset");
    assertThat(aggregate.getDataset()).isEqualTo("my-dataset");
  }

  @Test
  void commitAggregate_shouldUseDatasetAndCommitIdAsKey() {
    AggregateIdentity aggregate = AggregateIdentity.commit("default", "commit-123");

    assertThat(aggregate.getAggregateType()).isEqualTo("Commit");
    assertThat(aggregate.getPartitionKey()).isEqualTo("default:commit-123");
    assertThat(aggregate.getDataset()).isEqualTo("default");
  }

  @Test
  void sameBranchInDifferentDatasets_shouldHaveDifferentKeys() {
    AggregateIdentity aggregate1 = AggregateIdentity.branch("dataset-A", "main");
    AggregateIdentity aggregate2 = AggregateIdentity.branch("dataset-B", "main");

    assertThat(aggregate1.getPartitionKey()).isNotEqualTo(aggregate2.getPartitionKey());
    assertThat(aggregate1.getPartitionKey()).isEqualTo("dataset-A:main");
    assertThat(aggregate2.getPartitionKey()).isEqualTo("dataset-B:main");
  }

  @Test
  void differentBranchesInSameDataset_shouldHaveDifferentKeys() {
    AggregateIdentity aggregate1 = AggregateIdentity.branch("default", "main");
    AggregateIdentity aggregate2 = AggregateIdentity.branch("default", "feature-x");

    assertThat(aggregate1.getPartitionKey()).isNotEqualTo(aggregate2.getPartitionKey());
    assertThat(aggregate1.getPartitionKey()).isEqualTo("default:main");
    assertThat(aggregate2.getPartitionKey()).isEqualTo("default:feature-x");
  }

  @Test
  void sameBranchAggregate_shouldHaveSameKey() {
    AggregateIdentity aggregate1 = AggregateIdentity.branch("default", "main");
    AggregateIdentity aggregate2 = AggregateIdentity.branch("default", "main");

    assertThat(aggregate1.getPartitionKey()).isEqualTo(aggregate2.getPartitionKey());
    assertThat(aggregate1.getPartitionKey()).isEqualTo("default:main");
  }

  @Test
  void branchAggregate_withSpecialCharacters_shouldCreateValidKey() {
    AggregateIdentity aggregate = AggregateIdentity.branch("my-dataset", "feature/test-123");

    assertThat(aggregate.getPartitionKey()).isEqualTo("my-dataset:feature/test-123");
  }

  @Test
  void commitAggregate_withUuidCommitId_shouldCreateValidKey() {
    String uuid = "550e8400-e29b-41d4-a716-446655440000";
    AggregateIdentity aggregate = AggregateIdentity.commit("default", uuid);

    assertThat(aggregate.getPartitionKey())
        .isEqualTo("default:" + uuid);
  }
}
