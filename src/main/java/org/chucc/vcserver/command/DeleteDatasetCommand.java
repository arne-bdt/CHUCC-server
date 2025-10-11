package org.chucc.vcserver.command;

import java.util.Objects;

/**
 * Command to delete an entire dataset.
 * Requires explicit confirmation to prevent accidental deletion.
 *
 * @param dataset the dataset name
 * @param author the author of the deletion operation
 * @param deleteKafkaTopic whether to delete the Kafka topic (destructive!)
 * @param confirmed whether deletion is explicitly confirmed (must be true)
 */
public record DeleteDatasetCommand(
    String dataset,
    String author,
    boolean deleteKafkaTopic,
    boolean confirmed) implements Command {

  /**
   * Creates a new DeleteDatasetCommand with validation.
   *
   * @throws IllegalArgumentException if any validation fails
   */
  public DeleteDatasetCommand {
    Objects.requireNonNull(dataset, "Dataset cannot be null");
    Objects.requireNonNull(author, "Author cannot be null");

    if (dataset.isBlank()) {
      throw new IllegalArgumentException("Dataset cannot be blank");
    }
    if (author.isBlank()) {
      throw new IllegalArgumentException("Author cannot be blank");
    }
  }
}
