package org.chucc.vcserver.dto;

/**
 * Request DTO for creating a dataset.
 * Validates request fields before command creation.
 */
public record CreateDatasetRequest(
    String description,
    String initialGraph,
    KafkaTopicConfig kafka
) {
  /**
   * Validates the request fields.
   * All fields are optional, so no validation is required.
   * Dataset name validation happens in CreateDatasetCommand.
   */
  public void validate() {
    // All fields are optional - no validation needed
  }

  /**
   * Custom Kafka topic configuration for the dataset.
   * All fields are optional - null values will use global defaults.
   *
   * @param partitions number of partitions (1-100, default from global config)
   * @param replicationFactor replication factor (1-5, default from global config)
   * @param retentionMs retention in milliseconds (-1 for infinite, minimum 1 hour)
   */
  public record KafkaTopicConfig(
      Integer partitions,
      Short replicationFactor,
      Long retentionMs
  ) {
  }
}
