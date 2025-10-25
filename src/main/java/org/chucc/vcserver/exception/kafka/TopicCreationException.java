package org.chucc.vcserver.exception.kafka;

/**
 * Exception thrown when Kafka topic creation fails for unspecified reasons.
 */
public class TopicCreationException extends KafkaOperationException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a TopicCreationException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public TopicCreationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a TopicCreationException with a message.
   *
   * @param message the error message
   */
  public TopicCreationException(String message) {
    super(message);
  }
}
