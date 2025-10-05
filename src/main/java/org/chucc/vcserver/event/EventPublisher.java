package org.chucc.vcserver.event;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.chucc.vcserver.config.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Service for publishing version control events to Kafka.
 * Handles topic routing, header creation, and error handling.
 * Supports transactional publishing for exactly-once semantics.
 */
@Service
public class EventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

  private final KafkaTemplate<String, VersionControlEvent> kafkaTemplate;
  private final KafkaProperties kafkaProperties;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Both KafkaTemplate and KafkaProperties are Spring beans,"
          + " thread-safe and immutable after initialization")
  public EventPublisher(
      KafkaTemplate<String, VersionControlEvent> kafkaTemplate,
      KafkaProperties kafkaProperties) {
    this.kafkaTemplate = kafkaTemplate;
    this.kafkaProperties = kafkaProperties;
  }

  /**
   * Publishes a version control event to the appropriate Kafka topic.
   *
   * @param event the event to publish
   * @return a CompletableFuture with the send result
   */
  public CompletableFuture<SendResult<String, VersionControlEvent>> publish(
      VersionControlEvent event) {
    String topic = kafkaProperties.getTopicName(event.dataset());
    String key = getPartitionKey(event);

    ProducerRecord<String, VersionControlEvent> record =
        new ProducerRecord<>(topic, null, key, event);

    addHeaders(record.headers(), event);

    logger.info("Publishing event {} to topic {} with key {}",
        event.getClass().getSimpleName(), topic, key);

    return kafkaTemplate.send(record)
        .whenComplete((result, ex) -> {
          if (ex != null) {
            logger.error("Failed to publish event to topic {}: {}",
                topic, ex.getMessage(), ex);
          } else {
            logger.debug("Event published successfully to topic {} partition {} offset {}",
                topic, result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
          }
        });
  }

  /**
   * Publishes a version control event within a Kafka transaction.
   * This ensures exactly-once semantics and atomic publishing.
   *
   * @param event the event to publish
   * @return a CompletableFuture with the send result
   */
  public CompletableFuture<SendResult<String, VersionControlEvent>> publishTransactional(
      VersionControlEvent event) {
    return kafkaTemplate.executeInTransaction(ops -> {
      String topic = kafkaProperties.getTopicName(event.dataset());
      String key = getPartitionKey(event);

      ProducerRecord<String, VersionControlEvent> record =
          new ProducerRecord<>(topic, null, key, event);

      addHeaders(record.headers(), event);

      logger.info("Publishing transactional event {} to topic {} with key {}",
          event.getClass().getSimpleName(), topic, key);

      return ops.send(record)
          .whenComplete((result, ex) -> {
            if (ex != null) {
              logger.error("Failed to publish transactional event to topic {}: {}",
                  topic, ex.getMessage(), ex);
            } else {
              logger.debug("Transactional event published successfully to topic {} "
                      + "partition {} offset {}",
                  topic, result.getRecordMetadata().partition(),
                  result.getRecordMetadata().offset());
            }
          });
    });
  }

  /**
   * Determines the partition key for an event.
   * Events are partitioned by branch name to maintain ordering within branches.
   *
   * @param event the event
   * @return the partition key
   */
  private String getPartitionKey(VersionControlEvent event) {
    return switch (event) {
      case BranchCreatedEvent e -> e.branchName();
      case BranchResetEvent e -> e.branchName();
      case CommitCreatedEvent e -> event.dataset(); // Commits partition by dataset
      case TagCreatedEvent e -> event.dataset(); // Tags partition by dataset
      case RevertCreatedEvent e -> event.dataset(); // Reverts partition by dataset
      case SnapshotCreatedEvent e -> e.branchName(); // Snapshots partition by branch
    };
  }

  /**
   * Adds appropriate headers to the Kafka record.
   *
   * @param headers the headers object to populate
   * @param event the event
   */
  private void addHeaders(Headers headers, VersionControlEvent event) {
    // Always add dataset and event type
    headers.add(new RecordHeader(EventHeaders.DATASET,
        event.dataset().getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader(EventHeaders.EVENT_TYPE,
        event.getClass().getSimpleName()
            .replace("Event", "")
            .getBytes(StandardCharsets.UTF_8)));

    // Add event-specific headers
    switch (event) {
      case BranchCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branchName().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.commitId().getBytes(StandardCharsets.UTF_8)));
      }
      case BranchResetEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branchName().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.toCommitId().getBytes(StandardCharsets.UTF_8)));
      }
      case CommitCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.commitId().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CONTENT_TYPE,
            EventHeaders.RDF_PATCH_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8)));
      }
      case TagCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.commitId().getBytes(StandardCharsets.UTF_8)));
      }
      case RevertCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.revertCommitId().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CONTENT_TYPE,
            EventHeaders.RDF_PATCH_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8)));
      }
      case SnapshotCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branchName().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.commitId().getBytes(StandardCharsets.UTF_8)));
      }
    }
  }
}
