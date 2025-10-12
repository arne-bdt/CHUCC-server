package org.chucc.vcserver.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.CommitId;
import org.chucc.vcserver.event.SnapshotCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

/**
 * Repository for querying snapshot events from Kafka.
 * Encapsulates all Kafka consumer logic for snapshot retrieval.
 * This class should only be used in production code and integration tests.
 * Unit tests should mock this component.
 */
@Component
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class SnapshotKafkaStore {
  private static final Logger logger = LoggerFactory.getLogger(SnapshotKafkaStore.class);

  private final KafkaProperties kafkaProperties;

  /**
   * Constructs a SnapshotKafkaStore.
   *
   * @param kafkaProperties the kafka properties
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "KafkaProperties is a Spring-managed bean, thread-safe and immutable")
  public SnapshotKafkaStore(KafkaProperties kafkaProperties) {
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Metadata about a snapshot (without the actual graph data).
   * Contains information needed to fetch the full snapshot from Kafka.
   *
   * @param commitId the commit ID
   * @param branchName the branch name
   * @param timestamp the snapshot timestamp
   * @param topic the Kafka topic
   * @param partition the Kafka partition
   * @param offset the Kafka offset
   */
  public record SnapshotInfo(
      CommitId commitId,
      String branchName,
      Instant timestamp,
      String topic,
      int partition,
      long offset
  ) {}

  /**
   * Loads snapshot metadata from Kafka (without deserializing graphs).
   * Only extracts commit ID, branch name, timestamp, and Kafka position.
   *
   * @param datasetName the dataset name
   * @return List of snapshot metadata
   */
  @SuppressWarnings("PMD.CloseResource") // KafkaConsumer closed in try-with-resources
  public List<SnapshotInfo> findSnapshotMetadata(String datasetName) {
    List<SnapshotInfo> metadata = new ArrayList<>();

    // Create a consumer to read all events (polymorphic)
    // Match configuration from KafkaConfig.consumerFactory()
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "snapshot-query-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "org.chucc.vcserver.event");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "org.chucc.vcserver.event.VersionControlEvent");
    props.put(JsonDeserializer.TYPE_MAPPINGS,
        "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
        + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
        + "TagCreated:org.chucc.vcserver.event.TagCreatedEvent,"
        + "BranchReset:org.chucc.vcserver.event.BranchResetEvent,"
        + "RevertCreated:org.chucc.vcserver.event.RevertCreatedEvent,"
        + "SnapshotCreated:org.chucc.vcserver.event.SnapshotCreatedEvent,"
        + "CherryPicked:org.chucc.vcserver.event.CherryPickedEvent,"
        + "BranchRebased:org.chucc.vcserver.event.BranchRebasedEvent,"
        + "CommitsSquashed:org.chucc.vcserver.event.CommitsSquashedEvent,"
        + "BatchGraphsCompleted:org.chucc.vcserver.event.BatchGraphsCompletedEvent,"
        + "BranchDeleted:org.chucc.vcserver.event.BranchDeletedEvent,"
        + "DatasetDeleted:org.chucc.vcserver.event.DatasetDeletedEvent");

    try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props)) {
      String topic = kafkaProperties.getTopicName(datasetName);
      consumer.subscribe(List.of(topic));

      // Poll until we've read all available records
      boolean hasMore = true;
      while (hasMore) {
        ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(2));

        if (records.isEmpty()) {
          hasMore = false;
        } else {
          for (ConsumerRecord<String, Object> record : records) {
            Object event = record.value();

            // Only process SnapshotCreatedEvents, ignore other event types
            if (event instanceof SnapshotCreatedEvent snapshotEvent) {
              metadata.add(new SnapshotInfo(
                  CommitId.of(snapshotEvent.commitId()),
                  snapshotEvent.branchName(),
                  snapshotEvent.timestamp(),
                  record.topic(),
                  record.partition(),
                  record.offset()
              ));
            }
          }
        }
      }

      logger.debug("Loaded {} snapshot metadata entries from Kafka for dataset {}",
          metadata.size(), datasetName);

      return metadata;
    }
  }

  /**
   * Fetches a specific snapshot event from Kafka at the given position.
   *
   * @param info the snapshot metadata with Kafka position
   * @return the snapshot created event
   */
  @SuppressWarnings("PMD.CloseResource") // KafkaConsumer closed in try-with-resources
  public SnapshotCreatedEvent fetchSnapshotEvent(SnapshotInfo info) {
    // Match configuration from KafkaConfig.consumerFactory()
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "snapshot-fetch-" + UUID.randomUUID());
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "org.chucc.vcserver.event");
    props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");
    props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "org.chucc.vcserver.event.VersionControlEvent");
    props.put(JsonDeserializer.TYPE_MAPPINGS,
        "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
        + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
        + "TagCreated:org.chucc.vcserver.event.TagCreatedEvent,"
        + "BranchReset:org.chucc.vcserver.event.BranchResetEvent,"
        + "RevertCreated:org.chucc.vcserver.event.RevertCreatedEvent,"
        + "SnapshotCreated:org.chucc.vcserver.event.SnapshotCreatedEvent,"
        + "CherryPicked:org.chucc.vcserver.event.CherryPickedEvent,"
        + "BranchRebased:org.chucc.vcserver.event.BranchRebasedEvent,"
        + "CommitsSquashed:org.chucc.vcserver.event.CommitsSquashedEvent,"
        + "BatchGraphsCompleted:org.chucc.vcserver.event.BatchGraphsCompletedEvent,"
        + "BranchDeleted:org.chucc.vcserver.event.BranchDeletedEvent,"
        + "DatasetDeleted:org.chucc.vcserver.event.DatasetDeletedEvent");

    try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props)) {
      // Manually assign partition and seek to offset
      org.apache.kafka.common.TopicPartition partition =
          new org.apache.kafka.common.TopicPartition(info.topic(), info.partition());
      consumer.assign(List.of(partition));
      consumer.seek(partition, info.offset());

      // Fetch the record
      ConsumerRecords<String, Object> records = consumer.poll(Duration.ofSeconds(5));

      for (ConsumerRecord<String, Object> record : records) {
        if (record.offset() == info.offset()) {
          Object event = record.value();
          if (event instanceof SnapshotCreatedEvent snapshotEvent) {
            logger.debug("Fetched snapshot event from Kafka at offset {}", info.offset());
            return snapshotEvent;
          }
          throw new IllegalStateException(
              "Event at offset " + info.offset() + " is not a SnapshotCreatedEvent: "
              + event.getClass().getName());
        }
      }

      throw new IllegalStateException(
          "Could not fetch snapshot event at " + info.topic()
          + ":" + info.partition() + ":" + info.offset());
    }
  }
}
