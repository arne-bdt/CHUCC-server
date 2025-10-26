package org.chucc.vcserver.health;

/**
 * Health report for a single Kafka topic.
 *
 * @param dataset the dataset name
 * @param topicName the Kafka topic name
 * @param exists whether the topic exists
 * @param partitionCount actual partition count (null if topic doesn't exist)
 * @param healthy overall health status
 * @param message health message
 */
public record TopicHealthReport(
    String dataset,
    String topicName,
    boolean exists,
    Integer partitionCount,
    boolean healthy,
    String message
) {
  /**
   * Creates a healthy report for an existing topic.
   *
   * @param dataset the dataset name
   * @param topicName the topic name
   * @param partitionCount the partition count
   * @return healthy report
   */
  public static TopicHealthReport healthy(String dataset, String topicName, int partitionCount) {
    return new TopicHealthReport(
        dataset,
        topicName,
        true,
        partitionCount,
        true,
        "Topic exists and is accessible"
    );
  }

  /**
   * Creates an unhealthy report for a missing topic.
   *
   * @param dataset the dataset name
   * @param topicName the topic name
   * @return unhealthy report
   */
  public static TopicHealthReport missingTopic(String dataset, String topicName) {
    return new TopicHealthReport(
        dataset,
        topicName,
        false,
        null,
        false,
        "Topic does not exist"
    );
  }

  /**
   * Creates an error report.
   *
   * @param dataset the dataset name
   * @param topicName the topic name
   * @param error the error message
   * @return error report
   */
  public static TopicHealthReport error(String dataset, String topicName, String error) {
    String message = error != null
        ? "Error checking topic: " + error
        : "Error checking topic: unknown error";
    return new TopicHealthReport(
        dataset,
        topicName,
        false,
        null,
        false,
        message
    );
  }
}
