package org.chucc.vcserver.command;

import java.util.Objects;
import java.util.Optional;

/**
 * Command to create a new dataset with automatic Kafka topic creation.
 *
 * @param dataset the dataset name (must be Kafka-compatible)
 * @param description optional description of the dataset
 * @param author the author who creates the dataset
 * @param initialGraph optional initial graph IRI (default if not specified)
 */
public record CreateDatasetCommand(
    String dataset,
    Optional<String> description,
    String author,
    Optional<String> initialGraph) implements Command {

  /**
   * Maximum length for dataset names (Kafka topic naming limit).
   */
  private static final int MAX_DATASET_NAME_LENGTH = 249;

  /**
   * Convenience constructor without optional parameters.
   *
   * @param dataset the dataset name
   * @param author the author
   */
  public CreateDatasetCommand(String dataset, String author) {
    this(dataset, Optional.empty(), author, Optional.empty());
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

    // Check for invalid characters
    if (!name.matches("^[a-zA-Z0-9._-]+$")) {
      throw new IllegalArgumentException(
          "Dataset name contains invalid characters. "
              + "Allowed: a-z, A-Z, 0-9, . (dot), _ (underscore), - (hyphen)");
    }
  }
}
