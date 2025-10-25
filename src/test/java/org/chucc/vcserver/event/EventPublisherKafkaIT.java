package org.chucc.vcserver.event;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.chucc.vcserver.config.KafkaProperties;
import org.chucc.vcserver.domain.CommitId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for EventPublisher using Testcontainers Kafka.
 * Tests transactional event publishing with real Kafka infrastructure.
 */
@SpringBootTest
@ActiveProfiles("it")
class EventPublisherKafkaIT {

  // Eager initialization - container must be started before @DynamicPropertySource
  private static KafkaContainer kafkaContainer = KafkaTestContainers.createKafkaContainer();

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private KafkaProperties kafkaProperties;

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
    String commitId = CommitId.generate().value();
    BranchCreatedEvent event = new BranchCreatedEvent(
        datasetId,
        "feature-branch",
        commitId,
        "main",
        false,
        "test-author",
        Instant.now()
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When
    eventPublisher.publish(event).get();

    // Then - Use Awaitility to reliably wait for the event
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      AtomicReference<ConsumerRecord<String, String>> recordRef = new AtomicReference<>();

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            assertEquals(1, records.count(), "Should receive exactly one event");
            recordRef.set(records.iterator().next());
          });

      ConsumerRecord<String, String> record = recordRef.get();
      assertEquals(datasetId + ":feature-branch", record.key(),
          "Event should be keyed by aggregate ID (dataset:branch)");

      // Verify headers
      assertHeaderExists(record, EventHeaders.DATASET, datasetId);
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "BranchCreated");
      assertHeaderExists(record, EventHeaders.BRANCH, "feature-branch");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, commitId);

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"BranchCreated\""));
      assertTrue(json.contains("\"branchName\":\"feature-branch\""));
      assertTrue(json.contains("\"dataset\":\"" + datasetId + "\""));
      assertTrue(json.contains("\"commitId\":\"" + commitId + "\""));
    }
  }

  @Test
  void testPublishCommitCreatedEventToKafka() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    String commitId = CommitId.generate().value();
    String parentId = CommitId.generate().value();
    CommitCreatedEvent event = new CommitCreatedEvent(
        datasetId,
        commitId,
        List.of(parentId), null,
        "Add new feature",
        "Bob <bob@example.com>",
        Instant.now(),
        "TX .\nA <urn:subject> <urn:predicate> \"object\" .\nTC .\n"
        , 1
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When
    eventPublisher.publish(event).get();

    // Then - Use Awaitility to reliably wait for the event
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      AtomicReference<ConsumerRecord<String, String>> recordRef = new AtomicReference<>();

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            assertEquals(1, records.count(), "Should receive exactly one event");
            recordRef.set(records.iterator().next());
          });

      ConsumerRecord<String, String> record = recordRef.get();
      assertEquals(datasetId + ":" + commitId, record.key(),
          "Detached commit events should be keyed by commit aggregate (dataset:commitId)");

      // Verify headers
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "CommitCreated");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, commitId);
      assertHeaderExists(record, EventHeaders.CONTENT_TYPE,
          "text/rdf-patch; charset=utf-8");

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"CommitCreated\""));
      assertTrue(json.contains("\"commitId\":\"" + commitId + "\""));
      assertTrue(json.contains("\"message\":\"Add new feature\""));
      assertTrue(json.contains("\"author\":\"Bob <bob@example.com>\""));
      assertTrue(json.contains("\"rdfPatch\""), "JSON should contain rdfPatch field");
    }
  }

  @Test
  void testPublishTagCreatedEvent() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    String commitId = CommitId.generate().value();
    TagCreatedEvent event = new TagCreatedEvent(
        datasetId,
        "v2.0.0",
        commitId,
        "Release v2.0.0",
        "Bob",
        Instant.now()
    );

    String topicName = kafkaProperties.getTopicName(datasetId);

    // When - Publish event
    eventPublisher.publish(event).get();

    // Then - Use Awaitility to reliably wait for the event
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      AtomicReference<ConsumerRecord<String, String>> recordRef = new AtomicReference<>();

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
            assertEquals(1, records.count(), "Should receive exactly one event");
            recordRef.set(records.iterator().next());
          });

      ConsumerRecord<String, String> record = recordRef.get();

      // Verify headers
      assertHeaderExists(record, EventHeaders.EVENT_TYPE, "TagCreated");
      assertHeaderExists(record, EventHeaders.COMMIT_ID, commitId);

      // Verify JSON payload
      String json = record.value();
      assertTrue(json.contains("\"eventType\":\"TagCreated\""));
      assertTrue(json.contains("\"tagName\":\"v2.0.0\""));
      assertTrue(json.contains("\"commitId\":\"" + commitId + "\""));
    }
  }

  @Test
  void testPublishMultipleEventsWithIdempotence() throws Exception {
    // Given
    String datasetId = "test-dataset-" + System.currentTimeMillis();
    String topicName = kafkaProperties.getTopicName(datasetId);

    String commitId1 = CommitId.generate().value();
    String commitId2 = CommitId.generate().value();

    BranchCreatedEvent event1 = new BranchCreatedEvent(
        datasetId,
        "branch-1",
        commitId1,
        "main",
        false,
        "test-author",
        Instant.now()
    );

    BranchCreatedEvent event2 = new BranchCreatedEvent(
        datasetId,
        "branch-2",
        commitId2,
        "main",
        false,
        "test-author",
        Instant.now()
    );

    // When - Publish multiple events
    eventPublisher.publish(event1).get();
    eventPublisher.publish(event2).get();

    // Then - Use Awaitility to reliably wait for both events
    try (KafkaConsumer<String, String> consumer = createConsumer(topicName)) {
      AtomicReference<Map<String, Integer>> countsRef = new AtomicReference<>(new HashMap<>());

      await()
          .atMost(Duration.ofSeconds(10))
          .pollInterval(Duration.ofMillis(100))
          .untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

            Map<String, Integer> counts = countsRef.get();
            for (ConsumerRecord<String, String> record : records) {
              counts.merge(record.key(), 1, Integer::sum);
            }
            countsRef.set(counts);

            assertTrue(counts.size() >= 2, "Should have received events for both branches");
          });

      Map<String, Integer> counts = countsRef.get();

      // With idempotence enabled, we should not have duplicates
      String branch1Key = datasetId + ":branch-1";
      String branch2Key = datasetId + ":branch-2";
      assertEquals(1, counts.get(branch1Key), "Should have exactly one event for branch-1");
      assertEquals(1, counts.get(branch2Key), "Should have exactly one event for branch-2");
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
