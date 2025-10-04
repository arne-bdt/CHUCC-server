package org.chucc.vcserver.domain;

import java.util.Objects;

/**
 * Value object representing a reference to a dataset and a specific version/branch.
 * Can refer to either a branch name or a specific commit ID.
 */
public record DatasetRef(String datasetName, String ref) {

  /**
   * Creates a new DatasetRef with validation.
   *
   * @param datasetName the dataset name (must be non-null and non-blank)
   * @param ref the reference (branch name or commit ID, must be non-null and non-blank)
   * @throws IllegalArgumentException if validation fails
   */
  public DatasetRef {
    Objects.requireNonNull(datasetName, "Dataset name cannot be null");
    Objects.requireNonNull(ref, "Ref cannot be null");

    if (datasetName.isBlank()) {
      throw new IllegalArgumentException("Dataset name cannot be blank");
    }
    if (ref.isBlank()) {
      throw new IllegalArgumentException("Ref cannot be blank");
    }
  }

  /**
   * Creates a DatasetRef pointing to a branch.
   *
   * @param datasetName the dataset name
   * @param branchName the branch name
   * @return a new DatasetRef
   */
  public static DatasetRef forBranch(String datasetName, String branchName) {
    return new DatasetRef(datasetName, branchName);
  }

  /**
   * Creates a DatasetRef pointing to a specific commit.
   *
   * @param datasetName the dataset name
   * @param commitId the commit ID
   * @return a new DatasetRef
   */
  public static DatasetRef forCommit(String datasetName, CommitId commitId) {
    return new DatasetRef(datasetName, commitId.value());
  }

  @Override
  public String toString() {
    return datasetName + "@" + ref;
  }
}
