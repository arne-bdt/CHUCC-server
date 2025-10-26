package org.chucc.vcserver.health;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Health report for all managed Kafka topics.
 *
 * @param timestamp when the health check was performed
 * @param totalDatasets total number of datasets
 * @param healthyTopics number of healthy topics
 * @param unhealthyTopics number of unhealthy topics
 * @param topics individual topic health reports (immutable)
 * @param overallHealthy true if all topics are healthy
 */
public record AllTopicsHealthReport(
    Instant timestamp,
    int totalDatasets,
    int healthyTopics,
    int unhealthyTopics,
    List<TopicHealthReport> topics,
    boolean overallHealthy
) {
  /**
   * Compact canonical constructor with defensive copying.
   */
  public AllTopicsHealthReport {
    topics = topics != null ? List.copyOf(topics) : List.of();
  }

  /**
   * Creates a health report from individual topic reports.
   *
   * @param topicReports the individual topic health reports
   * @return combined health report
   */
  public static AllTopicsHealthReport from(List<TopicHealthReport> topicReports) {
    long healthyCount = topicReports.stream().filter(TopicHealthReport::healthy).count();
    int totalCount = topicReports.size();
    int unhealthyCount = totalCount - (int) healthyCount;

    return new AllTopicsHealthReport(
        Instant.now(),
        totalCount,
        (int) healthyCount,
        unhealthyCount,
        new ArrayList<>(topicReports),
        unhealthyCount == 0
    );
  }
}
