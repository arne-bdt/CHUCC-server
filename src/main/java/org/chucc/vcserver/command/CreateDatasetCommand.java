package org.chucc.vcserver.command;

import java.util.Objects;
import java.util.Optional;
import org.chucc.vcserver.dto.CreateDatasetRequest;

/**
 * Command to create a new dataset with automatic Kafka topic creation.
 *
 * @param dataset the dataset name (must be Kafka-compatible)
 * @param description optional description of the dataset
 * @param author the author who creates the dataset
 * @param initialGraph optional initial graph IRI (default if not specified)
 * @param kafkaConfig optional Kafka topic configuration (null uses global defaults)
 */
public record CreateDatasetCommand(
    String dataset,
    Optional<String> description,
    String author,
    Optional<String> initialGraph,
    CreateDatasetRequest.KafkaTopicConfig kafkaConfig) implements Command {

  /**
   * Maximum length for dataset names (Kafka topic naming limit).
   */
  private static final int MAX_DATASET_NAME_LENGTH = 249;

  /**
   * Maximum partition count per topic.
   */
  private static final int MAX_PARTITION_COUNT = 100;

  /**
   * Maximum replication factor.
   */
  private static final int MAX_REPLICATION_FACTOR = 5;

  /**
   * Minimum retention in milliseconds (1 hour).
   */
  private static final long MIN_RETENTION_MS = 3600000;

  /**
   * Convenience constructor without optional parameters.
   *
   * @param dataset the dataset name
   * @param author the author
   */
  public CreateDatasetCommand(String dataset, String author) {
    this(dataset, Optional.empty(), author, Optional.empty(), null);
  }

  /**
   * Creates a new CreateDatasetCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public CreateDatasetCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");
    Objects.requireNonNull(description, "Description cannot be null (use Optional.empty())");
    Objects.requireNonNull(initialGraph, "Initial graph cannot be null (use Optional.empty())");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }

    // Validate dataset name for Kafka compatibility
    validateDatasetName(dataset);

    // Validate Kafka configuration if provided
    if (kafkaConfig != null) {
      validateKafkaConfig(kafkaConfig);
    }
  }

  /**
   * Validates that the dataset name is compatible with Kafka topic naming rules.
   * Kafka topics cannot contain: / \ , space \0 \n \r \t
   * and must not be "." or ".."
   *
   * @param name the dataset name to validate
   * @throws IllegalArgumentException if the name is invalid
   */
  private static void validateDatasetName(String name) {
    if (name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException("Dataset name cannot be '.' or '..'");
    }

    if (name.length() > MAX_DATASET_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "Dataset name too long (max " + MAX_DATASET_NAME_LENGTH + " characters)");
    }

    // Reject names starting with underscore (reserved for internal use)
    if (name.startsWith("_")) {
      throw new IllegalArgumentException(
          "Dataset name cannot start with '_' (reserved for internal use)");
    }

    // Check for invalid characters
    if (!name.matches("^[a-zA-Z0-9._-]+$")) {
      throw new IllegalArgumentException(
          "Dataset name contains invalid characters. "
              + "Allowed: a-z, A-Z, 0-9, . (dot), _ (underscore), - (hyphen)");
    }
  }

  /**
   * Validates Kafka topic configuration.
   *
   * @param config the Kafka configuration to validate
   * @throws IllegalArgumentException if configuration is invalid
   */
  private static void validateKafkaConfig(CreateDatasetRequest.KafkaTopicConfig config) {
    // Validate partition count
    if (config.partitions() != null) {
      if (config.partitions() < 1 || config.partitions() > MAX_PARTITION_COUNT) {
        throw new IllegalArgumentException(
            "Partition count must be between 1 and " + MAX_PARTITION_COUNT
                + ", got: " + config.partitions());
      }
    }

    // Validate replication factor
    if (config.replicationFactor() != null) {
      if (config.replicationFactor() < 1 || config.replicationFactor() > MAX_REPLICATION_FACTOR) {
        throw new IllegalArgumentException(
            "Replication factor must be between 1 and " + MAX_REPLICATION_FACTOR
                + ", got: " + config.replicationFactor());
      }
    }

    // Validate retention
    if (config.retentionMs() != null) {
      if (config.retentionMs() != -1 && config.retentionMs() < MIN_RETENTION_MS) {
        throw new IllegalArgumentException(
            "Retention must be at least 1 hour (" + MIN_RETENTION_MS
                + "ms) or -1 for infinite, got: " + config.retentionMs());
      }
    }
  }
}
