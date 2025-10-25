package org.chucc.vcserver.exception;

/**
 * Exception thrown when attempting to create a dataset that already exists.
 */
public class DatasetAlreadyExistsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new DatasetAlreadyExistsException with the specified dataset name.
   *
   * @param datasetName the name of the dataset that already exists
   */
  public DatasetAlreadyExistsException(String datasetName) {
    super("Dataset already exists: " + datasetName);
  }
}
