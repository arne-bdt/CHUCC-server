package org.chucc.vcserver.exception.kafka;

/**
 * Base exception for Kafka-related operations.
 * This exception is thrown when Kafka operations fail.
 */
public class KafkaOperationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a KafkaOperationException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public KafkaOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a KafkaOperationException with a message.
   *
   * @param message the error message
   */
  public KafkaOperationException(String message) {
    super(message);
  }
}
