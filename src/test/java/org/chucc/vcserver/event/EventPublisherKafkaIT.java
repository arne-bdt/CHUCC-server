package org.chucc.vcserver.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.chucc.vcserver.config.KafkaProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for EventPublisher using Testcontainers Kafka.
 * Tests transactional event publishing with real Kafka infrastructure.
 */
@SpringBootTest
class EventPublisherKafkaIT {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private KafkaProperties kafkaProperties;

  @BeforeAll
  static void startKafka() {
    // Use the official Apache Kafka image supported by Testcontainers
    kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
    kafkaContainer.start();
  }

  @AfterAll
  static void stopKafka() {
    if (kafkaContainer != null) {
      kafkaContainer.stop();
    }
  }

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Disable transactions for this test to avoid transactional producer issues
    registry.add("kafka.transactional-id-prefix", () -> "");
  }

  @Test
  void testPublishBranchCreatedEventToKafka() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    BranchCreatedEvent event = new BranchCreatedEvent(
        datasetId,
        "feature-branch",
        "commit-abc-123",
        Instant.now()
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When
    eventPublisher.publish(event).get();

    // Then - Consume the event from Kafka
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

      assertEquals(1, records.count(), "Should receive exactly one event");

      ConsumerRecord<String, String> record = records.iterator().next();
      assertEquals("feature-branch", record.key(),
          "Event should be keyed by branch name");

      // Verify headers
      assertHeaderExists(record, EventHeaders.DATASET, datasetId);
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "BranchCreated");
      assertHeaderExists(record, EventHeaders.BRANCH, "feature-branch");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-abc-123");

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"BranchCreated\""));
      assertTrue(json.contains("\"branchName\":\"feature-branch\""));
      assertTrue(json.contains("\"dataset\":\"" + datasetId + "\""));
      assertTrue(json.contains("\"commitId\":\"commit-abc-123\""));
    }
  }

  @Test
  void testPublishCommitCreatedEventToKafka() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    CommitCreatedEvent event = new CommitCreatedEvent(
        datasetId,
        "commit-456",
        List.of("parent-1"),
        "Add new feature",
        "Bob <bob@example.com>",
        Instant.now(),
        "H 2 .\nA <urn:subject> <urn:predicate> \"object\" .\n"
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When
    eventPublisher.publish(event).get();

    // Then - Wait a bit for the message to be available
    Thread.sleep(100);

    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      // Poll multiple times if needed - consumer may need time to rebalance
      ConsumerRecords<String, String> records = null;
      for (int i = 0; i < 5; i++) {
        records = consumer.poll(Duration.ofSeconds(2));
        if (!records.isEmpty()) {
          break;
        }
      }

      System.out.println("DEBUG: Received " + records.count() + " records for commit test");
      for (ConsumerRecord<String, String> r : records) {
        System.out.println("DEBUG: Record value: " + r.value());
      }

      assertEquals(1, records.count(), "Should receive exactly one event");

      ConsumerRecord<String, String> record = records.iterator().next();
      assertEquals(datasetId, record.key(),
          "Commit events should be keyed by dataset");

      // Verify headers
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "CommitCreated");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-456");
      assertHeaderExists(record, EventHeaders.CONTENT_TYPE,
          "text/rdf-patch; charset=utf-8");

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"CommitCreated\""));
      assertTrue(json.contains("\"commitId\":\"commit-456\""));
      assertTrue(json.contains("\"message\":\"Add new feature\""));
      assertTrue(json.contains("\"author\":\"Bob <bob@example.com>\""));
      assertTrue(json.contains("\"rdfPatch\""), "JSON should contain rdfPatch field");
    }
  }

  @Test
  void testPublishTagCreatedEvent() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    TagCreatedEvent event = new TagCreatedEvent(
        datasetId,
        "v2.0.0",
        "commit-789",
        Instant.now()
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When - Publish event
    eventPublisher.publish(event).get();

    // Then
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

      assertEquals(1, records.count(), "Should receive exactly one event");

      ConsumerRecord<String, String> record = records.iterator().next();

      // Verify headers
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "TagCreated");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, "commit-789");

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"TagCreated\""));
      assertTrue(json.contains("\"tagName\":\"v2.0.0\""));
      assertTrue(json.contains("\"commitId\":\"commit-789\""));
    }
  }

  @Test
  void testPublishMultipleEventsWithIdempotence() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    String topicName = kafkaProperties.getTopicName(datasetId);

    BranchCreatedEvent event1 = new BranchCreatedEvent(
        datasetId,
        "branch-1",
        "commit-1",
        Instant.now()
    );

    BranchCreatedEvent event2 = new BranchCreatedEvent(
        datasetId,
        "branch-2",
        "commit-2",
        Instant.now()
    );

    // When - Publish multiple events
    eventPublisher.publish(event1).get();
    eventPublisher.publish(event2).get();

    // Then - Verify both events are received
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

      assertTrue(records.count() >= 2, "Should receive at least two events");

      long branch1Count = 0;
      long branch2Count = 0;

      for (ConsumerRecord<String, String> record : records) {
        if (record.key().equals("branch-1")) {
          branch1Count++;
        } else if (record.key().equals("branch-2")) {
          branch2Count++;
        }
      }

      // With idempotence enabled, we should not have duplicates
      assertEquals(1, branch1Count, "Should have exactly one event for branch-1");
      assertEquals(1, branch2Count, "Should have exactly one event for branch-2");
    }
  }

  private KafkaConsumer<String, String> createConsumer(String topic) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        kafkaContainer.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
    props.put(JsonDeserializer.TRUSTED_PACKAGES, "org.chucc.vcserver.event");

    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(Collections.singletonList(topic));

    return consumer;
  }

  private void assertHeaderExists(
      ConsumerRecord<String, String> record,
      String headerKey,
      String expectedValue) {
    org.apache.kafka.common.header.Header header = record.headers().lastHeader(headerKey);
    assertNotNull(header, "Header " + headerKey + " should exist");
    assertEquals(expectedValue, new String(header.value(), StandardCharsets.UTF_8),
        "Header " + headerKey + " should have expected value");
  }
}
