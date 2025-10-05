package org.chucc.vcserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka event streaming.
 */
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {
  /**
   * Kafka bootstrap servers.
   */
  private String bootstrapServers = "localhost:9092";

  /**
   * Topic name template for version control events.
   * Use {dataset} as a placeholder for the dataset name.
   */
  private String topicTemplate = "vc.{dataset}.events";

  /**
   * Number of partitions for event topics.
   */
  private int partitions = 3;

  /**
   * Replication factor for event topics.
   */
  private short replicationFactor = 1;

  /**
   * Retention period in milliseconds (-1 for infinite).
   */
  private long retentionMs = -1;

  /**
   * Whether to enable log compaction (false for append-only).
   */
  private boolean compaction = false;

  /**
   * Transactional ID prefix for producers.
   */
  private String transactionalIdPrefix = "vc-tx-";

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getTopicTemplate() {
    return topicTemplate;
  }

  public void setTopicTemplate(String topicTemplate) {
    this.topicTemplate = topicTemplate;
  }

  public int getPartitions() {
    return partitions;
  }

  public void setPartitions(int partitions) {
    this.partitions = partitions;
  }

  public short getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(short replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public long getRetentionMs() {
    return retentionMs;
  }

  public void setRetentionMs(long retentionMs) {
    this.retentionMs = retentionMs;
  }

  public boolean isCompaction() {
    return compaction;
  }

  public void setCompaction(boolean compaction) {
    this.compaction = compaction;
  }

  public String getTransactionalIdPrefix() {
    return transactionalIdPrefix;
  }

  public void setTransactionalIdPrefix(String transactionalIdPrefix) {
    this.transactionalIdPrefix = transactionalIdPrefix;
  }

  /**
   * Gets the topic name for a specific dataset.
   *
   * @param dataset the dataset name
   * @return the topic name
   */
  public String getTopicName(String dataset) {
    return topicTemplate.replace("{dataset}", dataset);
  }
}
