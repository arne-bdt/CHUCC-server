package org.chucc.vcserver.exception.kafka;

/**
 * Exception thrown when Kafka configuration is invalid.
 * Examples: replication factor exceeds available brokers, invalid topic settings.
 */
public class InvalidKafkaConfigurationException extends KafkaOperationException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs an InvalidKafkaConfigurationException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public InvalidKafkaConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs an InvalidKafkaConfigurationException with a message.
   *
   * @param message the error message
   */
  public InvalidKafkaConfigurationException(String message) {
    super(message);
  }
}
