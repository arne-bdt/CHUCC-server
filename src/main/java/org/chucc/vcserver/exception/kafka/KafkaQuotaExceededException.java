package org.chucc.vcserver.exception.kafka;

/**
 * Exception thrown when Kafka quota or policy limits are exceeded.
 * This may occur when too many topics/partitions are created.
 */
public class KafkaQuotaExceededException extends KafkaOperationException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a KafkaQuotaExceededException with a message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public KafkaQuotaExceededException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a KafkaQuotaExceededException with a message.
   *
   * @param message the error message
   */
  public KafkaQuotaExceededException(String message) {
    super(message);
  }
}
