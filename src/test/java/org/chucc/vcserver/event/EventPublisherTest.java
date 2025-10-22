package org.chucc.vcserver.event;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.chucc.vcserver.config.KafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

  @Mock
  private KafkaTemplate<String, VersionControlEvent> kafkaTemplate;

  @Mock
  private SendResult<String, VersionControlEvent> sendResult;

  @Captor
  private ArgumentCaptor<ProducerRecord<String, VersionControlEvent>> recordCaptor;

  private KafkaProperties kafkaProperties;
  private EventPublisher eventPublisher;

  @BeforeEach
  @SuppressWarnings("unchecked") // ProducerRecord with generics, safe for mocking
  void setUp() {
    kafkaProperties = new KafkaProperties();
    kafkaProperties.setTopicTemplate("vc.{dataset}.events");
    eventPublisher = new EventPublisher(kafkaTemplate, kafkaProperties);

    // Mock successful send
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.completedFuture(sendResult));
  }

  @Test
  void testPublishBranchCreatedEvent() {
    BranchCreatedEvent event = new BranchCreatedEvent(
        "test-dataset",
        "main",
        "commit-123",
        Instant.now()
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.test-dataset.events", record.topic());
    assertEquals("test-dataset:main", record.key()); // Partitioned by aggregate ID
    assertEquals(event, record.value());

    // Verify headers
    assertHeaderExists(record, EventHeaders.DATASET, "test-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "BranchCreated");
    assertHeaderExists(record, EventHeaders.BRANCH, "main");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-123");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Branch");
    assertHeaderExists(record, "aggregateId", "test-dataset:main");
  }

  @Test
  void testPublishCommitCreatedEvent() {
    CommitCreatedEvent event = new CommitCreatedEvent(
        "my-dataset",
        "commit-456",
        List.of("parent-1"), null,
        "Test commit",
        "Alice <alice@example.com>",
        Instant.now(),
        "H 1 .\n"
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.my-dataset.events", record.topic());
    assertEquals("my-dataset:commit-456", record.key()); // Detached commit uses commit aggregate
    assertEquals(event, record.value());

    // Verify headers
    assertHeaderExists(record, EventHeaders.DATASET, "my-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "CommitCreated");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-456");
    assertHeaderExists(record, EventHeaders.CONTENT_TYPE, "text/rdf-patch; charset=utf-8");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Commit");
    assertHeaderExists(record, "aggregateId", "my-dataset:commit-456");
  }

  @Test
  void testPublishCommitCreatedEventWithBranch() {
    CommitCreatedEvent event = new CommitCreatedEvent(
        "my-dataset",
        "commit-789",
        List.of("commit-456"),
        "feature-x",  // Branch specified
        "Test commit on branch",
        "Alice <alice@example.com>",
        Instant.now(),
        "H 1 .\n"
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.my-dataset.events", record.topic());
    assertEquals("my-dataset:feature-x", record.key()); // Commit with branch uses branch aggregate
    assertEquals(event, record.value());

    // Verify headers
    assertHeaderExists(record, EventHeaders.DATASET, "my-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "CommitCreated");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-789");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Branch");
    assertHeaderExists(record, "aggregateId", "my-dataset:feature-x");
  }

  @Test
  void testPublishTagCreatedEvent() {
    TagCreatedEvent event = new TagCreatedEvent(
        "prod-dataset",
        "v1.0.0",
        "commit-789",
        Instant.now()
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.prod-dataset.events", record.topic());
    assertEquals("prod-dataset", record.key()); // Tags partitioned by dataset

    assertHeaderExists(record, EventHeaders.DATASET, "prod-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "TagCreated");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-789");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Dataset");
    assertHeaderExists(record, "aggregateId", "prod-dataset");
  }

  @Test
  void testPublishBranchResetEvent() {
    BranchResetEvent event = new BranchResetEvent(
        "test-dataset",
        "develop",
        "old-commit",
        "new-commit",
        Instant.now()
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.test-dataset.events", record.topic());
    assertEquals("test-dataset:develop", record.key()); // Partitioned by aggregate ID

    assertHeaderExists(record, EventHeaders.DATASET, "test-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "BranchReset");
    assertHeaderExists(record, EventHeaders.BRANCH, "develop");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "new-commit");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Branch");
    assertHeaderExists(record, "aggregateId", "test-dataset:develop");
  }

  @Test
  void testPublishRevertCreatedEvent() {
    RevertCreatedEvent event = new RevertCreatedEvent(
        "test-dataset",
        "revert-commit",
        "bad-commit",
        "main",
        "Revert bad changes",
        "Bob <bob@example.com>",
        Instant.now(),
        "H 1 .\n"
    );

    eventPublisher.publish(event);

    verify(kafkaTemplate).send(recordCaptor.capture());
    ProducerRecord<String, VersionControlEvent> record = recordCaptor.getValue();

    assertEquals("vc.test-dataset.events", record.topic());
    assertEquals("test-dataset:main", record.key()); // Reverts partitioned by branch aggregate

    assertHeaderExists(record, EventHeaders.DATASET, "test-dataset");
    assertHeaderExists(record, EventHeaders.EVENT_TYPE, "RevertCreated");
    assertHeaderExists(record, EventHeaders.COMMIT_ID, "revert-commit");
    assertHeaderExists(record, EventHeaders.CONTENT_TYPE, "text/rdf-patch; charset=utf-8");

    // Verify aggregate headers
    assertHeaderExists(record, "aggregateType", "Branch");
    assertHeaderExists(record, "aggregateId", "test-dataset:main");
  }

  private void assertHeaderExists(
      ProducerRecord<String, VersionControlEvent> record,
      String headerKey,
      String expectedValue) {
    Header header = record.headers().lastHeader(headerKey);
    assertEquals(expectedValue, new String(header.value(), StandardCharsets.UTF_8));
  }
}
