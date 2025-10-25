package org.chucc.vcserver.exception.kafka;

/**
 * Exception thrown when Kafka cluster is unavailable or unreachable.
 * This is a transient error that may be retried.
 */
public class KafkaUnavailableException extends KafkaOperationException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a KafkaUnavailableException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public KafkaUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a KafkaUnavailableException with a message.
   *
   * @param message the error message
   */
  public KafkaUnavailableException(String message) {
    super(message);
  }
}
