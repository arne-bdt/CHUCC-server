package org.chucc.vcserver.event;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.annotation.Counted;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.filter.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Service for publishing version control events to Kafka.
 * Handles topic routing, header creation, and error handling.
 */
@Service
@SuppressWarnings("PMD.GuardLogStatement") // SLF4J parameterized logging is efficient
public class EventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

  private final KafkaTemplate<String, VersionControlEvent> kafkaTemplate;
  private final KafkaProperties kafkaProperties;

  /**
   * Constructs an EventPublisher with the specified Kafka template and properties.
   *
   * @param kafkaTemplate the Kafka template for sending events
   * @param kafkaProperties the Kafka configuration properties
   */
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
  @Counted(
      value = "event.published",
      description = "Events published count"
  )
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
   * Determines the partition key for an event using aggregate identity.
   * Events for the same aggregate instance use the same partition key
   * to ensure ordered processing.
   *
   * <p>This method implements the CQRS/Event Sourcing best practice:
   * "Key = Aggregate-ID" - ensuring all events for an aggregate instance land
   * in the same partition for guaranteed ordering.
   *
   * @param event the event
   * @return the partition key
   */
  private String getPartitionKey(VersionControlEvent event) {
    AggregateIdentity aggregateId = event.getAggregateIdentity();
    String partitionKey = aggregateId.getPartitionKey();

    logger.debug("Event {} for aggregate {} (type={}) using partition key: {}",
        event.getClass().getSimpleName(),
        aggregateId.getAggregateType(),
        aggregateId.getDataset(),
        partitionKey);

    return partitionKey;
  }

  /**
   * Adds appropriate headers to the Kafka record.
   *
   * @param headers the headers object to populate
   * @param event the event
   */
  private void addHeaders(Headers headers, VersionControlEvent event) {
    // Add eventId for deduplication (at-least-once → exactly-once)
    headers.add(new RecordHeader(EventHeaders.EVENT_ID,
        event.eventId().getBytes(StandardCharsets.UTF_8)));

    // Always add dataset and event type
    headers.add(new RecordHeader(EventHeaders.DATASET,
        event.dataset().getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader(EventHeaders.EVENT_TYPE,
        event.getClass().getSimpleName()
            .replace("Event", "")
            .getBytes(StandardCharsets.UTF_8)));

    // Add timestamp (UTC epoch milliseconds)
    headers.add(new RecordHeader(EventHeaders.TIMESTAMP,
        String.valueOf(Instant.now().toEpochMilli()).getBytes(StandardCharsets.UTF_8)));

    // Add correlation ID (from MDC, if available)
    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    if (correlationId != null) {
      headers.add(new RecordHeader(EventHeaders.CORRELATION_ID,
          correlationId.getBytes(StandardCharsets.UTF_8)));
    }

    // Add aggregate metadata for partition tracking
    AggregateIdentity aggregateId = event.getAggregateIdentity();
    headers.add(new RecordHeader("aggregateType",
        aggregateId.getAggregateType().getBytes(StandardCharsets.UTF_8)));
    headers.add(new RecordHeader("aggregateId",
        aggregateId.getPartitionKey().getBytes(StandardCharsets.UTF_8)));

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
      case BranchRebasedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branch().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.newHead().getBytes(StandardCharsets.UTF_8)));
      }
      case BranchDeletedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branchName().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.lastCommitId().getBytes(StandardCharsets.UTF_8)));
      }
      case DatasetDeletedEvent e -> {
        // Add header with number of branches deleted
        headers.add(new RecordHeader("branch-count",
            String.valueOf(e.deletedBranches().size()).getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader("commit-count",
            String.valueOf(e.deletedCommitCount()).getBytes(StandardCharsets.UTF_8)));
      }
      case DatasetCreatedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.mainBranch().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.initialCommitId().getBytes(StandardCharsets.UTF_8)));
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
      case CherryPickedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branch().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.newCommitId().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CONTENT_TYPE,
            EventHeaders.RDF_PATCH_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8)));
      }
      case CommitsSquashedEvent e -> {
        headers.add(new RecordHeader(EventHeaders.BRANCH,
            e.branch().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.COMMIT_ID,
            e.newCommitId().getBytes(StandardCharsets.UTF_8)));
        headers.add(new RecordHeader(EventHeaders.CONTENT_TYPE,
            EventHeaders.RDF_PATCH_CONTENT_TYPE.getBytes(StandardCharsets.UTF_8)));
      }
      case BatchGraphsCompletedEvent e -> {
        // Add header with number of commits created
        headers.add(new RecordHeader("commit-count",
            String.valueOf(e.commits().size()).getBytes(StandardCharsets.UTF_8)));
      }
    }
  }
}
