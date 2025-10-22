package org.chucc.vcserver.event;

/**
 * Represents the identity of an aggregate for event partitioning.
 * All events for the same aggregate instance should use the same partition key
 * to ensure ordered processing in Kafka.
 *
 * <p>This abstraction follows the CQRS/Event Sourcing best practice:
 * "Key = Aggregate-ID" - all events for an aggregate instance land in one partition.
 *
 * @see <a href="https://kafka.apache.org/documentation/#design_partitioning">
 *   Kafka Partitioning</a>
 */
public interface AggregateIdentity {

  /**
   * Returns the aggregate type (e.g., "Branch", "Dataset", "Commit").
   * Used for logging and debugging.
   *
   * @return the aggregate type
   */
  String getAggregateType();

  /**
   * Returns the partition key for this aggregate instance.
   * Format: "{dataset}:{aggregateId}" or "{dataset}" for dataset-level aggregates.
   *
   * @return the partition key
   */
  String getPartitionKey();

  /**
   * Returns the dataset this aggregate belongs to.
   *
   * @return the dataset name
   */
  String getDataset();

  /**
   * Factory method: Create branch aggregate identity.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   * @return the branch aggregate identity
   */
  static AggregateIdentity branch(String dataset, String branchName) {
    return new BranchAggregate(dataset, branchName);
  }

  /**
   * Factory method: Create dataset aggregate identity.
   *
   * @param dataset the dataset name
   * @return the dataset aggregate identity
   */
  static AggregateIdentity dataset(String dataset) {
    return new DatasetAggregate(dataset);
  }

  /**
   * Factory method: Create commit aggregate identity (for detached commits).
   *
   * @param dataset the dataset name
   * @param commitId the commit ID
   * @return the commit aggregate identity
   */
  static AggregateIdentity commit(String dataset, String commitId) {
    return new CommitAggregate(dataset, commitId);
  }

  /**
   * Branch aggregate implementation.
   * All events for a branch (creates, resets, commits) use the same partition key.
   *
   * @param dataset the dataset name
   * @param branchName the branch name
   */
  record BranchAggregate(String dataset, String branchName) implements AggregateIdentity {
    @Override
    public String getAggregateType() {
      return "Branch";
    }

    @Override
    public String getPartitionKey() {
      return dataset + ":" + branchName;
    }

    @Override
    public String getDataset() {
      return dataset;
    }
  }

  /**
   * Dataset aggregate implementation.
   * Dataset-level events (delete dataset, batch operations) use dataset as key.
   *
   * @param dataset the dataset name
   */
  record DatasetAggregate(String dataset) implements AggregateIdentity {
    @Override
    public String getAggregateType() {
      return "Dataset";
    }

    @Override
    public String getPartitionKey() {
      return dataset;
    }

    @Override
    public String getDataset() {
      return dataset;
    }
  }

  /**
   * Commit aggregate implementation.
   * Used for detached commits not associated with a branch (rare).
   *
   * @param dataset the dataset name
   * @param commitId the commit ID
   */
  record CommitAggregate(String dataset, String commitId) implements AggregateIdentity {
    @Override
    public String getAggregateType() {
      return "Commit";
    }

    @Override
    public String getPartitionKey() {
      return dataset + ":" + commitId;
    }

    @Override
    public String getDataset() {
      return dataset;
    }
  }
}
