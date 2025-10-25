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

  /**
   * Producer configuration.
   */
  private Producer producer = new Producer();

  /**
   * Consumer configuration.
   */
  private Consumer consumer = new Consumer();

  /**
   * Snapshot store configuration.
   */
  private SnapshotStore snapshotStore = new SnapshotStore();

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

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Producer is a Spring configuration bean")
  public Producer getProducer() {
    return producer;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Producer is a Spring configuration bean")
  public void setProducer(Producer producer) {
    this.producer = producer;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Consumer is a Spring configuration bean")
  public Consumer getConsumer() {
    return consumer;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Consumer is a Spring configuration bean")
  public void setConsumer(Consumer consumer) {
    this.consumer = consumer;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "SnapshotStore is a Spring configuration bean")
  public SnapshotStore getSnapshotStore() {
    return snapshotStore;
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "SnapshotStore is a Spring configuration bean")
  public void setSnapshotStore(SnapshotStore snapshotStore) {
    this.snapshotStore = snapshotStore;
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

  /**
   * Producer configuration properties.
   */
  public static class Producer {
    private int retries = 3;
    private String acks = "1";
    private boolean enableIdempotence = false;
    private int maxInFlightRequests = 5;
    private String compressionType = "none";
    private int lingerMs = 0;
    private int batchSize = 16384;

    public int getRetries() {
      return retries;
    }

    public void setRetries(int retries) {
      this.retries = retries;
    }

    public String getAcks() {
      return acks;
    }

    public void setAcks(String acks) {
      this.acks = acks;
    }

    public boolean isEnableIdempotence() {
      return enableIdempotence;
    }

    public void setEnableIdempotence(boolean enableIdempotence) {
      this.enableIdempotence = enableIdempotence;
    }

    public int getMaxInFlightRequests() {
      return maxInFlightRequests;
    }

    public void setMaxInFlightRequests(int maxInFlightRequests) {
      this.maxInFlightRequests = maxInFlightRequests;
    }

    public String getCompressionType() {
      return compressionType;
    }

    public void setCompressionType(String compressionType) {
      this.compressionType = compressionType;
    }

    public int getLingerMs() {
      return lingerMs;
    }

    public void setLingerMs(int lingerMs) {
      this.lingerMs = lingerMs;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }
  }

  /**
   * Consumer configuration properties.
   */
  public static class Consumer {
    private int autoCommitIntervalMs = 1000;
    private boolean enableAutoCommit = true;
    private String isolationLevel = "read_uncommitted";
    private int maxPollRecords = 500;

    public int getAutoCommitIntervalMs() {
      return autoCommitIntervalMs;
    }

    public void setAutoCommitIntervalMs(int autoCommitIntervalMs) {
      this.autoCommitIntervalMs = autoCommitIntervalMs;
    }

    public boolean isEnableAutoCommit() {
      return enableAutoCommit;
    }

    public void setEnableAutoCommit(boolean enableAutoCommit) {
      this.enableAutoCommit = enableAutoCommit;
    }

    public String getIsolationLevel() {
      return isolationLevel;
    }

    public void setIsolationLevel(String isolationLevel) {
      this.isolationLevel = isolationLevel;
    }

    public int getMaxPollRecords() {
      return maxPollRecords;
    }

    public void setMaxPollRecords(int maxPollRecords) {
      this.maxPollRecords = maxPollRecords;
    }
  }

  /**
   * Snapshot store configuration properties.
   */
  public static class SnapshotStore {
    private int metadataCacheMaxSize = 100;
    private int pollTimeoutSeconds = 5;

    public int getMetadataCacheMaxSize() {
      return metadataCacheMaxSize;
    }

    public void setMetadataCacheMaxSize(int metadataCacheMaxSize) {
      this.metadataCacheMaxSize = metadataCacheMaxSize;
    }

    public int getPollTimeoutSeconds() {
      return pollTimeoutSeconds;
    }

    public void setPollTimeoutSeconds(int pollTimeoutSeconds) {
      this.pollTimeoutSeconds = pollTimeoutSeconds;
    }
  }
}
