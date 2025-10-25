package org.chucc.vcserver.exception.kafka;

/**
 * Exception thrown when application lacks permissions for Kafka operations.
 * This is a fatal error that should not be retried.
 */
public class KafkaAuthorizationException extends KafkaOperationException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a KafkaAuthorizationException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public KafkaAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a KafkaAuthorizationException with a message.
   *
   * @param message the error message
   */
  public KafkaAuthorizationException(String message) {
    super(message);
  }
}
